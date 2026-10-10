package com.example.admin.integration;

import com.example.admin.application.OperatorInvitationTokens;
import com.example.admin.support.OperatorJwtTestFixture;
import com.example.testsupport.integration.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.security.KeyPair;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-MONO-772 S2 — the operator-invitation lifecycle against real MySQL (V0050 table, generated
 * {@code pending_key}) with a WireMock account-service (tenant read + the invitation-mail endpoint):
 *
 * <ul>
 *   <li>create → list → resend (the hash rotates · the old one is gone) → cancel → cancel again (no-op) →
 *       resend after cancel (409), with one audit row per real transition;</li>
 *   <li>🔴 R4: the stored hash is SHA-256 of the token that went out in the mail; neither the token nor the hash
 *       appears in any response or in the application log;</li>
 *   <li>a second PENDING for the same (tenant, email) → 409, and the DB unique key refuses it on its own; a
 *       cancelled one frees the slot;</li>
 *   <li>🔴 D2 — a TENANT_ADMIN of tenant-x inviting into tenant-y → 403, no row; acting on tenant-x's invitation
 *       from tenant-y → 404 (enumeration-safe);</li>
 *   <li>🔴 D3 — a TENANT_ADMIN inviting with SUPPORT_LOCK / SUPER_ADMIN (outside its grant menu) → 403, no row;</li>
 *   <li>'*' → 400 · unknown tenant → 404 · reused Idempotency-Key → 409 · existing operator → 409 · mail failure
 *       → 201 with delivery FAILED_*.</li>
 * </ul>
 *
 * <p>Skipped when Docker is unavailable (AbstractIntegrationTest) — CI Linux is authoritative.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@Tag("integration")
// Own WireMock in @DynamicPropertySource → a context no other class shares; close it after the class (the
// TenantEntryPolicyIntegrationTest lesson: resident contexts were the suspected cause of a heap-pressure error).
@org.springframework.test.annotation.DirtiesContext(
        classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class OperatorInvitationIntegrationTest extends AbstractIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    static WireMockServer wireMock;
    static OperatorJwtTestFixture jwt;
    static String adminSigningKeyPem;

    private static final String TENANT_X = "s2-tenant-x";
    private static final String TENANT_Y = "s2-tenant-y";
    private static final String ADMIN_X = "00000000-0000-7000-8000-0000000c52a1";
    private static final String ADMIN_Y = "00000000-0000-7000-8000-0000000c52a2";
    private static final String MAIL_PATH = "/internal/notifications/operator-invitation";
    private static final ObjectMapper JSON = new ObjectMapper();

    @BeforeAll
    static void setupShared() {
        jwt = new OperatorJwtTestFixture();
        java.security.PrivateKey adminPk = extractPrivateKey(jwt);
        adminSigningKeyPem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(adminPk.getEncoded())
                + "\n-----END PRIVATE KEY-----\n";
        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();
        WireMock.configureFor("localhost", wireMock.port());
    }

    @AfterAll
    static void tearDownShared() {
        if (wireMock != null) wireMock.stop();
    }

    private static java.security.PrivateKey extractPrivateKey(OperatorJwtTestFixture fixture) {
        try {
            var f = OperatorJwtTestFixture.class.getDeclaredField("keyPair");
            f.setAccessible(true);
            return ((KeyPair) f.get(fixture)).getPrivate();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("admin.jwt.active-signing-kid", () -> "test-key-001");
        registry.add("admin.jwt.signing-keys.test-key-001", () -> adminSigningKeyPem);
        registry.add("admin.jwt.issuer", () -> "admin-service");
        registry.add("admin.jwt.expected-token-type", () -> "admin");
        registry.add("admin.account-service.base-url", wireMock::baseUrl);
        registry.add("admin.auth-service.base-url", wireMock::baseUrl);
        registry.add("admin.security-service.base-url", wireMock::baseUrl);
        registry.add("iam.internal-client.token-uri", () -> wireMock.baseUrl() + "/oauth2/token");
    }

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seed() {
        wireMock.resetAll();
        wireMock.stubFor(WireMock.post(WireMock.urlEqualTo("/oauth2/token"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"access_token\":\"test-jwt\",\"expires_in\":300,\"token_type\":\"Bearer\"}")));
        stubTenant(TENANT_X);
        stubTenant(TENANT_Y);
        wireMock.stubFor(WireMock.get(urlPathEqualTo("/internal/tenants/s2-ghost"))
                .willReturn(aResponse().withStatus(404).withHeader("Content-Type", "application/json")
                        .withBody("{\"code\":\"TENANT_NOT_FOUND\",\"message\":\"no\"}")));
        stubMail(204, null);

        seedTenantAdmin(ADMIN_X, TENANT_X);
        seedTenantAdmin(ADMIN_Y, TENANT_Y);
    }

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("DELETE FROM operator_invitation WHERE tenant_id LIKE 's2-%'");
    }

    private void stubTenant(String tenantId) {
        wireMock.stubFor(WireMock.get(urlPathEqualTo("/internal/tenants/" + tenantId))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"tenantId":"%s","displayName":"S2","tenantType":"B2B_ENTERPRISE","status":"ACTIVE",
                                 "createdAt":"2026-10-10T00:00:00Z","updatedAt":"2026-10-10T00:00:00Z"}
                                """.formatted(tenantId))));
    }

    private void stubMail(int status, String code) {
        var response = aResponse().withStatus(status);
        if (code != null) {
            response = response.withHeader("Content-Type", "application/json")
                    .withBody("{\"code\":\"" + code + "\",\"message\":\"x\"}");
        }
        wireMock.stubFor(WireMock.post(urlPathEqualTo(MAIL_PATH)).willReturn(response));
    }

    /** One TENANT_ADMIN per tenant: admin_operator_roles is keyed (operator_id, role_id) — see the S5 IT note. */
    private void seedTenantAdmin(String operatorId, String tenantId) {
        Integer existing = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM admin_operators WHERE operator_id = ?", Integer.class, operatorId);
        if (existing == null || existing == 0) {
            jdbcTemplate.update("""
                    INSERT INTO admin_operators
                      (operator_id, tenant_id, email, password_hash, display_name,
                       status, oidc_subject, created_at, updated_at, version)
                    VALUES (?, ?, ?, NULL, ?, 'ACTIVE', NULL, NOW(6), NOW(6), 0)
                    """, operatorId, tenantId, operatorId + "@example.com", "S2 admin " + tenantId);
        }
        jdbcTemplate.update("""
                INSERT IGNORE INTO admin_operator_roles (operator_id, role_id, tenant_id, granted_at, granted_by)
                SELECT o.id, r.id, ?, NOW(6), NULL
                  FROM admin_operators o JOIN admin_roles r ON r.name = 'TENANT_ADMIN'
                 WHERE o.operator_id = ?
                """, tenantId, operatorId);
    }

    private static String bearer(String operatorId) {
        return "Bearer " + jwt.operatorToken(operatorId);
    }

    private static String uniqueEmail() {
        return "s2-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    }

    private ResultActions create(String actor, String tenantId, String email, String rolesJson, String key)
            throws Exception {
        return mockMvc.perform(post("/api/admin/operator-invitations")
                .header("Authorization", bearer(actor))
                .header("X-Operator-Reason", "s2-it")
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","displayName":"S2 초대","roles":%s,"tenantId":"%s"}
                        """.formatted(email, rolesJson, tenantId)));
    }

    private ResultActions action(String actor, String invitationId, String verb) throws Exception {
        return mockMvc.perform(post("/api/admin/operator-invitations/" + invitationId + ":" + verb)
                .header("Authorization", bearer(actor))
                .header("X-Operator-Reason", "s2-it"));
    }

    private String tokenHash(String invitationId) {
        return jdbcTemplate.queryForObject(
                "SELECT token_hash FROM operator_invitation WHERE invitation_id = ?", String.class, invitationId);
    }

    private int invitationRows(String tenantId, String email) {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM operator_invitation WHERE tenant_id = ? AND email = ?",
                Integer.class, tenantId, email);
        return n == null ? 0 : n;
    }

    private int auditRows(String actionCode, String invitationId) {
        Integer n = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM admin_actions
                 WHERE action_code = ? AND target_type = 'OPERATOR_INVITATION' AND target_id = ?
                   AND outcome = 'SUCCESS' AND permission_used = 'operator.manage'
                """, Integer.class, actionCode, invitationId);
        return n == null ? 0 : n;
    }

    /** The raw token of the LAST invitation mail account-service received. */
    private String lastMailedToken() throws Exception {
        List<LoggedRequest> mails = wireMock.findAll(postRequestedFor(urlPathEqualTo(MAIL_PATH)));
        assertThat(mails).isNotEmpty();
        JsonNode body = JSON.readTree(mails.get(mails.size() - 1).getBodyAsString());
        return body.get("token").asText();
    }

    @Test
    @DisplayName("🔴 수명주기: 발급 → 목록 → 재발송(해시 회전·옛 해시 소멸) → 취소 → 재취소 no-op → 취소 뒤 재발송 409 · 토큰/해시 비노출")
    void lifecycle() throws Exception {
        // Log events captured at the root logger, not stdout: the `test` profile's logback-spring.xml may attach no
        // console appender, and «the token is not in an empty capture» would prove nothing (non-vacuity asserted below).
        ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger)
                org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> logs =
                new ch.qos.logback.core.read.ListAppender<>();
        logs.start();
        root.addAppender(logs);
        try {
            lifecycleBody(logs);
        } finally {
            root.detachAppender(logs);
        }
    }

    private void lifecycleBody(
            ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> logs) throws Exception {
        String email = uniqueEmail();
        String createJson = create(ADMIN_X, TENANT_X, email.toUpperCase(), "[\"TENANT_ADMIN\"]", UUID.randomUUID().toString())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.expired").value(false))
                .andExpect(jsonPath("$.invitedBy").value(ADMIN_X))
                .andExpect(jsonPath("$.delivery.status").value("SENT"))
                .andExpect(jsonPath("$.auditId").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String invitationId = JSON.readTree(createJson).get("invitationId").asText();

        // R4: the row holds SHA-256 of exactly the token the mail carried — and the response carries neither.
        String token1 = lastMailedToken();
        String hash1 = tokenHash(invitationId);
        assertThat(hash1).hasSize(64).isEqualTo(OperatorInvitationTokens.sha256Hex(token1));
        assertThat(createJson).doesNotContain(token1).doesNotContain(hash1);
        assertThat(auditRows("OPERATOR_INVITATION_CREATE", invitationId)).isEqualTo(1);
        // The mail went to the normalised address, after the row committed (a later read sees the delivery).
        assertThat(JSON.readTree(wireMock.findAll(postRequestedFor(urlPathEqualTo(MAIL_PATH))).get(0).getBodyAsString())
                .get("to").asText()).isEqualTo(email);

        String listJson = mockMvc.perform(get("/api/admin/operator-invitations").header("Authorization", bearer(ADMIN_X)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(listJson).contains(invitationId).doesNotContain(token1).doesNotContain(hash1);

        String resendJson = action(ADMIN_X, invitationId, "resend")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.delivery.status").value("SENT"))
                .andReturn().getResponse().getContentAsString();
        String token2 = lastMailedToken();
        String hash2 = tokenHash(invitationId);
        assertThat(token2).isNotEqualTo(token1);
        assertThat(hash2).isNotEqualTo(hash1).isEqualTo(OperatorInvitationTokens.sha256Hex(token2));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM operator_invitation WHERE token_hash = ?", Integer.class, hash1)).isZero();
        assertThat(invitationRows(TENANT_X, email)).isEqualTo(1); // same row, not a new one
        assertThat(resendJson).doesNotContain(token2).doesNotContain(hash2);
        assertThat(auditRows("OPERATOR_INVITATION_RESEND", invitationId)).isEqualTo(1);

        action(ADMIN_X, invitationId, "cancel")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelledAt").isNotEmpty());
        action(ADMIN_X, invitationId, "cancel")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        assertThat(auditRows("OPERATOR_INVITATION_CANCEL", invitationId)).isEqualTo(1); // the no-op wrote none

        action(ADMIN_X, invitationId, "resend")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OPERATOR_INVITATION_NOT_PENDING"));

        // R4: neither token (nor a hash) reached the application log — and the capture is not empty: the use case's
        // own INFO line about this invitation is in it.
        StringBuilder logged = new StringBuilder();
        for (var event : logs.list) {
            logged.append(event.getFormattedMessage()).append('\n');
        }
        assertThat(logged.toString()).contains(invitationId)
                .doesNotContain(token1).doesNotContain(token2).doesNotContain(hash1).doesNotContain(hash2);
    }

    @Test
    @DisplayName("대기 중 초대 중복 → 409 ALREADY_PENDING · DB 유니크 키가 스스로 막는다 · 취소하면 자리가 빈다")
    void duplicatePending() throws Exception {
        String email = uniqueEmail();
        String json = create(ADMIN_X, TENANT_X, email, "[\"TENANT_ADMIN\"]", UUID.randomUUID().toString())
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String invitationId = JSON.readTree(json).get("invitationId").asText();

        create(ADMIN_X, TENANT_X, email, "[\"TENANT_ADMIN\"]", UUID.randomUUID().toString())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OPERATOR_INVITATION_ALREADY_PENDING"));
        assertThat(invitationRows(TENANT_X, email)).isEqualTo(1);

        // The race the pre-check cannot see: a raw second PENDING row is refused by uk_operator_invitation_pending_key.
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO operator_invitation
                  (invitation_id, tenant_id, email, display_name, roles, token_hash, status, expires_at,
                   invited_by, created_at, updated_at, version)
                SELECT ?, ?, ?, 'x', 'TENANT_ADMIN', ?, 'PENDING', NOW(6) + INTERVAL 7 DAY, o.id, NOW(6), NOW(6), 0
                  FROM admin_operators o WHERE o.operator_id = ?
                """, UUID.randomUUID().toString(), TENANT_X, email, "f".repeat(64), ADMIN_X))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_operator_invitation_pending_key");

        action(ADMIN_X, invitationId, "cancel").andExpect(status().isOk());
        create(ADMIN_X, TENANT_X, email, "[\"TENANT_ADMIN\"]", UUID.randomUUID().toString())
                .andExpect(status().isCreated());
        assertThat(invitationRows(TENANT_X, email)).isEqualTo(2); // one CANCELLED (pending_key NULL) + one PENDING
    }

    @Test
    @DisplayName("🔴 D3: TENANT_ADMIN 이 부여 메뉴 밖 역할(SUPPORT_LOCK · SUPER_ADMIN)로 초대 → 403 ROLE_GRANT_FORBIDDEN, 행 없음 · 대조군 TENANT_ADMIN 은 201")
    void noEscalation() throws Exception {
        String email = uniqueEmail();
        create(ADMIN_X, TENANT_X, email, "[\"SUPPORT_LOCK\"]", UUID.randomUUID().toString())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ROLE_GRANT_FORBIDDEN"));
        create(ADMIN_X, TENANT_X, email, "[\"SUPER_ADMIN\"]", UUID.randomUUID().toString())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ROLE_GRANT_FORBIDDEN"));
        assertThat(invitationRows(TENANT_X, email)).isZero();
        wireMock.verify(0, postRequestedFor(urlPathEqualTo(MAIL_PATH)));

        create(ADMIN_X, TENANT_X, email, "[\"TENANT_ADMIN\"]", UUID.randomUUID().toString())
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("🔴 D2: 범위 밖 테넌트로 초대 → 403 TENANT_SCOPE_DENIED, 행 없음 · 남의 테넌트 초대 취소/재발송/목록 → 404/404/403")
    void tenantScope() throws Exception {
        String email = uniqueEmail();
        create(ADMIN_X, TENANT_Y, email, "[\"TENANT_ADMIN\"]", UUID.randomUUID().toString())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_SCOPE_DENIED"));
        assertThat(invitationRows(TENANT_Y, email)).isZero();

        String json = create(ADMIN_X, TENANT_X, email, "[\"TENANT_ADMIN\"]", UUID.randomUUID().toString())
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String invitationId = JSON.readTree(json).get("invitationId").asText();
        String hashBefore = tokenHash(invitationId);

        action(ADMIN_Y, invitationId, "cancel")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("OPERATOR_INVITATION_NOT_FOUND"));
        action(ADMIN_Y, invitationId, "resend")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("OPERATOR_INVITATION_NOT_FOUND"));
        assertThat(tokenHash(invitationId)).isEqualTo(hashBefore);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM operator_invitation WHERE invitation_id = ?", String.class, invitationId))
                .isEqualTo("PENDING");
        mockMvc.perform(get("/api/admin/operator-invitations?tenantId=" + TENANT_X).header("Authorization", bearer(ADMIN_Y)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_SCOPE_DENIED"));
    }

    @Test
    @DisplayName("'*' → 400 · 미등록 테넌트 → 404 (행 없음) · 같은 Idempotency-Key → 409 · 이미 운영자인 이메일 → 409")
    void refusals() throws Exception {
        String email = uniqueEmail();
        create(ADMIN_X, "*", email, "[\"TENANT_ADMIN\"]", UUID.randomUUID().toString())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        // An actor in scope for s2-ghost, so the 404 is the tenant check's and not the scope 403.
        String ghostAdmin = "00000000-0000-7000-8000-0000000c52a3";
        seedTenantAdmin(ghostAdmin, "s2-ghost");
        create(ghostAdmin, "s2-ghost", email, "[\"TENANT_ADMIN\"]", UUID.randomUUID().toString())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TENANT_NOT_FOUND"));
        assertThat(invitationRows("s2-ghost", email)).isZero();

        String key = UUID.randomUUID().toString();
        create(ADMIN_X, TENANT_X, email, "[\"TENANT_ADMIN\"]", key).andExpect(status().isCreated());
        create(ADMIN_X, TENANT_X, uniqueEmail(), "[\"TENANT_ADMIN\"]", key)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_CONFLICT"));

        // ADMIN_X itself is an operator of tenant-x: inviting its own address there is an email conflict.
        create(ADMIN_X, TENANT_X, ADMIN_X + "@example.com", "[\"TENANT_ADMIN\"]", UUID.randomUUID().toString())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OPERATOR_EMAIL_CONFLICT"));
    }

    @Test
    @DisplayName("메일 실패는 오류가 아니다: 503 → 201 + FAILED_TRANSIENT · 422 UNDELIVERABLE → 201 + FAILED_PERMANENT (행 남음)")
    void mailFailures() throws Exception {
        stubMail(503, "INVITATION_EMAIL_SEND_FAILED");
        String email1 = uniqueEmail();
        create(ADMIN_X, TENANT_X, email1, "[\"TENANT_ADMIN\"]", UUID.randomUUID().toString())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.delivery.status").value("FAILED_TRANSIENT"));
        assertThat(invitationRows(TENANT_X, email1)).isEqualTo(1);
        wireMock.verify(1, postRequestedFor(urlPathEqualTo(MAIL_PATH))); // no retry

        stubMail(422, "INVITATION_EMAIL_UNDELIVERABLE");
        String email2 = uniqueEmail();
        create(ADMIN_X, TENANT_X, email2, "[\"TENANT_ADMIN\"]", UUID.randomUUID().toString())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.delivery.status").value("FAILED_PERMANENT"));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT last_delivery_status FROM operator_invitation WHERE tenant_id = ? AND email = ?",
                String.class, TENANT_X, email2)).isEqualTo("FAILED_PERMANENT");
    }
}
