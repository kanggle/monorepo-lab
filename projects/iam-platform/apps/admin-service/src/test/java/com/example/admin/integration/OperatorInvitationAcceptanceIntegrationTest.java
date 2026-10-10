package com.example.admin.integration;

import com.example.admin.application.OperatorInvitationTokens;
import com.example.admin.support.OperatorJwtTestFixture;
import com.example.testsupport.integration.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-MONO-772 S3 — the operator-invitation acceptance against real MySQL (V0050 + {@code admin_operators} ·
 * grants · assignment · {@code admin_actions}), with WireMock as account-service (tenant reads, the invitation
 * mail, 🔴 {@code verified-email:match}) and as auth-service's JWKS (the user OIDC token of onboarding and the
 * token exchange).
 *
 * <ul>
 *   <li>🔴 <b>AC-1</b> ({@link #ac1_controlGroup_refusalsWriteNothing_thenOnlyTheVerifiedOwner}) — ONE invitation:
 *       the invitee's own account before verifying · an account with another email · a site account · the same
 *       invitation past its expiry · then the verified owner (the only 200, {@code oidc_subject} written) · then
 *       another account reusing it · then the owner resubmitting (200 {@code alreadyAccepted}) · a cancelled
 *       invitation. Every refusal is asserted to have written no operator row, no grant, no assignment, no audit
 *       row, and to have left the invitation PENDING.</li>
 *   <li>🔴 <b>AC-6</b> ({@link #ac6_selfOnboardedOrgAdmin_invites_inviteeBecomesOperator}) — a self-onboarded
 *       organisation's admin invites, the invitee accepts, and the S4 console-eligibility read and the operator
 *       token exchange then answer «yes» for that account; then OD-1 (a second company → 409) and S1-11 (that
 *       account's self-onboarding → 409 before any tenant is created).</li>
 *   <li>D3 re-check, concurrent acceptance (exactly one operator), preview.</li>
 * </ul>
 *
 * <p>What this cannot reach: the verified-email predicate itself runs in account-service — here it is WireMock's
 * answer (account-service's own IT, {@code OperatorInvitationSupportIntegrationTest}, runs the predicate on a
 * real pool account), and the IdP pages are auth-service's ({@code OperatorInvitationPageSliceTest}).
 *
 * <p>Skipped when Docker is unavailable (AbstractIntegrationTest) — CI Linux is authoritative.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@Tag("integration")
@org.springframework.test.annotation.DirtiesContext(
        classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class OperatorInvitationAcceptanceIntegrationTest extends AbstractIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    private static final String OIDC_ISSUER = "http://localhost:8081";
    private static final String CONSOLE_CLIENT = "platform-console-web";
    private static final String JWKS_KID = "auth-test-kid-s3";
    private static final String MAIL_PATH = "/internal/notifications/operator-invitation";
    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String TENANT_X = "s3-tenant-x";
    private static final String ADMIN_X = "00000000-0000-7000-8000-0000000c53a1";

    static WireMockServer wireMock;
    static OperatorJwtTestFixture jwt;
    static String adminSigningKeyPem;
    static KeyPair authKeyPair;

    @BeforeAll
    static void setupShared() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        authKeyPair = gen.generateKeyPair();
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
        registry.add("admin.oidc.jwks-uri", () -> wireMock.baseUrl() + "/internal/auth/jwks");
        registry.add("admin.oidc.issuer", () -> OIDC_ISSUER);
        registry.add("admin.oidc.audience", () -> CONSOLE_CLIENT);
        registry.add("admin.account-service.base-url", wireMock::baseUrl);
        registry.add("admin.auth-service.base-url", wireMock::baseUrl);
        registry.add("admin.security-service.base-url", wireMock::baseUrl);
        registry.add("iam.internal-client.token-uri", () -> wireMock.baseUrl() + "/oauth2/token");
    }

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;

    @BeforeEach
    void stubs() {
        wireMock.resetAll();
        wireMock.stubFor(WireMock.post(urlEqualTo("/oauth2/token"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"access_token\":\"test-jwt\",\"expires_in\":300,\"token_type\":\"Bearer\"}")));
        wireMock.stubFor(WireMock.get(urlPathEqualTo("/internal/auth/jwks"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody(jwksJson((RSAPublicKey) authKeyPair.getPublic()))));
        wireMock.stubFor(WireMock.post(urlPathMatching("/internal/tenants/[^/]+/identities:resolveOrCreate"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"identityId\":\"identity-s3\",\"outcome\":\"RESOLVED\"}")));
        wireMock.stubFor(WireMock.post(urlPathEqualTo(MAIL_PATH)).willReturn(aResponse().withStatus(204)));
        stubTenant(TENANT_X, "ACTIVE");
        seedTenantAdmin(ADMIN_X, TENANT_X);
    }

    // ── stubs · seeds ───────────────────────────────────────────────────────

    private static String jwksJson(RSAPublicKey key) {
        return "{\"keys\":[{\"kty\":\"RSA\",\"use\":\"sig\",\"alg\":\"RS256\",\"kid\":\"" + JWKS_KID
                + "\",\"n\":\"" + b64Url(key.getModulus().toByteArray()) + "\",\"e\":\""
                + b64Url(key.getPublicExponent().toByteArray()) + "\"}]}";
    }

    private static String b64Url(byte[] bytes) {
        if (bytes.length > 1 && bytes[0] == 0) {
            byte[] s = new byte[bytes.length - 1];
            System.arraycopy(bytes, 1, s, 0, s.length);
            bytes = s;
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String userOidcToken(String sub) {
        Instant now = Instant.now();
        return Jwts.builder().header().keyId(JWKS_KID).and()
                .subject(sub).issuer(OIDC_ISSUER).audience().add(CONSOLE_CLIENT).and()
                .issuedAt(Date.from(now)).expiration(Date.from(now.plus(15, ChronoUnit.MINUTES)))
                .signWith(authKeyPair.getPrivate(), Jwts.SIG.RS256).compact();
    }

    private void stubTenant(String tenantId, String status) {
        wireMock.stubFor(WireMock.get(urlPathEqualTo("/internal/tenants/" + tenantId))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"tenantId":"%s","displayName":"S3 %s","tenantType":"B2B_ENTERPRISE","status":"%s",
                                 "createdAt":"2026-10-10T00:00:00Z","updatedAt":"2026-10-10T00:00:00Z"}
                                """.formatted(tenantId, tenantId, status))));
    }

    /** account-service's verdict for one account (the predicate itself is account-service's — see class javadoc). */
    private void stubMatch(String accountId, int status, String code) {
        String body = status == 200
                ? "{\"accountId\":\"" + accountId + "\",\"emailVerifiedAt\":\"2026-10-10T00:00:00Z\"}"
                : "{\"code\":\"" + code + "\",\"message\":\"x\"}";
        wireMock.stubFor(WireMock.post(urlPathEqualTo("/internal/accounts/" + accountId + "/verified-email:match"))
                .willReturn(aResponse().withStatus(status).withHeader("Content-Type", "application/json").withBody(body)));
    }

    private void seedTenantAdmin(String operatorId, String tenantId) {
        Integer existing = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM admin_operators WHERE operator_id = ?", Integer.class, operatorId);
        if (existing == null || existing == 0) {
            jdbcTemplate.update("""
                    INSERT INTO admin_operators
                      (operator_id, tenant_id, email, password_hash, display_name,
                       status, oidc_subject, created_at, updated_at, version)
                    VALUES (?, ?, ?, NULL, ?, 'ACTIVE', NULL, NOW(6), NOW(6), 0)
                    """, operatorId, tenantId, operatorId + "@example.com", "S3 admin " + tenantId);
        }
        jdbcTemplate.update("""
                INSERT IGNORE INTO admin_operator_roles (operator_id, role_id, tenant_id, granted_at, granted_by)
                SELECT o.id, r.id, ?, NOW(6), NULL
                  FROM admin_operators o JOIN admin_roles r ON r.name = 'TENANT_ADMIN'
                 WHERE o.operator_id = ?
                """, tenantId, operatorId);
    }

    private static String uniqueEmail() {
        return "s3-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    }

    private static String newAccountId() {
        return UUID.randomUUID().toString();
    }

    // ── calls ───────────────────────────────────────────────────────────────

    /** Issues an invitation through the S2 management API and returns {invitationId, raw token from the mail}. */
    private String[] invite(String actor, String tenantId, String email, String role) throws Exception {
        String json = mockMvc.perform(post("/api/admin/operator-invitations")
                        .header("Authorization", "Bearer " + jwt.operatorToken(actor))
                        .header("X-Operator-Reason", "s3-it")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","displayName":"S3 피초대자","roles":["%s"],"tenantId":"%s"}
                                """.formatted(email, role, tenantId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String invitationId = JSON.readTree(json).get("invitationId").asText();
        List<LoggedRequest> mails = wireMock.findAll(postRequestedFor(urlPathEqualTo(MAIL_PATH)));
        String token = JSON.readTree(mails.get(mails.size() - 1).getBodyAsString()).get("token").asText();
        return new String[]{invitationId, token};
    }

    private ResultActions accept(String token, String accountId) throws Exception {
        return mockMvc.perform(post("/internal/operator-invitations/accept")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\",\"accountId\":\"" + accountId + "\"}"));
    }

    private ResultActions preview(String token) throws Exception {
        return mockMvc.perform(post("/internal/operator-invitations/preview")
                .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"" + token + "\"}"));
    }

    private boolean consoleEligible(String accountId) throws Exception {
        String json = mockMvc.perform(get("/internal/operators/console-eligibility").param("accountId", accountId))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JSON.readTree(json).get("eligible").asBoolean();
    }

    // ── what a refusal must not have written ────────────────────────────────

    private int count(String sql, Object... args) {
        Integer n = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return n == null ? 0 : n;
    }

    private int operatorsWithSubject(String accountId) {
        return count("SELECT COUNT(*) FROM admin_operators WHERE oidc_subject = ?", accountId);
    }

    private int operatorsFor(String tenantId, String email) {
        return count("SELECT COUNT(*) FROM admin_operators WHERE tenant_id = ? AND email = ?", tenantId, email);
    }

    private int grantsFor(String tenantId, String email) {
        return count("""
                SELECT COUNT(*) FROM admin_operator_roles r JOIN admin_operators o ON o.id = r.operator_id
                 WHERE o.tenant_id = ? AND o.email = ?""", tenantId, email);
    }

    private int acceptAuditRows(String invitationId) {
        return count("SELECT COUNT(*) FROM admin_actions WHERE action_code = 'OPERATOR_INVITATION_ACCEPT' AND target_id = ?",
                invitationId);
    }

    private String invitationStatus(String invitationId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM operator_invitation WHERE invitation_id = ?", String.class, invitationId);
    }

    private void assertNothingWritten(String invitationId, String email, String... accountIds) {
        assertThat(operatorsFor(TENANT_X, email)).as("no operator row").isZero();
        assertThat(grantsFor(TENANT_X, email)).as("no role grant").isZero();
        for (String a : accountIds) {
            assertThat(operatorsWithSubject(a)).as("no oidc_subject = %s", a).isZero();
        }
        assertThat(acceptAuditRows(invitationId)).as("no audit row for a refusal (S1-12)").isZero();
        assertThat(invitationStatus(invitationId)).as("the invitation stays PENDING").isEqualTo("PENDING");
    }

    // ── AC-1 ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("🔴 AC-1 대조군: 같은 초대 — 미인증 본인 · 다른 이메일 · 사이트 계정 · 만료 → 거절(아무것도 안 씀) → 인증된 본인만 성공(oidc_subject) → 다른 계정 재사용 409 · 본인 재제출 200 · 취소된 초대 404")
    void ac1_controlGroup_refusalsWriteNothing_thenOnlyTheVerifiedOwner() throws Exception {
        ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger)
                org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> logs =
                new ch.qos.logback.core.read.ListAppender<>();
        logs.start();
        root.addAppender(logs);
        try {
            controlGroupBody(logs);
        } finally {
            root.detachAppender(logs);
        }
    }

    private void controlGroupBody(
            ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> logs) throws Exception {
        String email = uniqueEmail();
        String invitee = newAccountId();      // the invitee's own pool account — unverified first, then verified
        String otherEmail = newAccountId();   // a pool account with another address
        String siteAccount = newAccountId();  // a site account (account-service: 404 — not a pool account)
        String rival = newAccountId();        // someone else trying the link after it was used

        String[] inv = invite(ADMIN_X, TENANT_X, email, "TENANT_ADMIN");
        String invitationId = inv[0];
        String token = inv[1];

        // The invitee's account: NOT verified on the first ask, verified once they open the verification mail.
        String path = "/internal/accounts/" + invitee + "/verified-email:match";
        wireMock.stubFor(WireMock.post(urlPathEqualTo(path)).inScenario("verify").whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(403).withHeader("Content-Type", "application/json")
                        .withBody("{\"code\":\"EMAIL_NOT_VERIFIED\",\"message\":\"x\"}")));
        wireMock.stubFor(WireMock.post(urlPathEqualTo(path)).inScenario("verify").whenScenarioStateIs("verified")
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"accountId\":\"" + invitee + "\",\"emailVerifiedAt\":\"2026-10-10T00:00:00Z\"}")));
        stubMatch(otherEmail, 403, "ACCOUNT_EMAIL_MISMATCH");
        stubMatch(siteAccount, 404, "ACCOUNT_NOT_FOUND");

        // ① the invitee's account before verifying → 403 EMAIL_NOT_VERIFIED
        accept(token, invitee).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
        // ② a pool account with another address → 403
        accept(token, otherEmail).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("OPERATOR_INVITATION_EMAIL_MISMATCH"));
        // ③ a site account → 403
        accept(token, siteAccount).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("OPERATOR_INVITATION_ACCOUNT_NOT_ELIGIBLE"));
        assertNothingWritten(invitationId, email, invitee, otherEmail, siteAccount);

        // The invitee verifies. ④ the SAME invitation past its expiry → 410, even for the now-verified owner.
        wireMock.setScenarioState("verify", "verified");
        jdbcTemplate.update("UPDATE operator_invitation SET expires_at = NOW(6) - INTERVAL 1 MINUTE WHERE invitation_id = ?",
                invitationId);
        accept(token, invitee).andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("OPERATOR_INVITATION_EXPIRED"));
        assertNothingWritten(invitationId, email, invitee);
        jdbcTemplate.update("UPDATE operator_invitation SET expires_at = NOW(6) + INTERVAL 1 DAY WHERE invitation_id = ?",
                invitationId);

        // ⑤ the verified owner → 200, and only now is anything written.
        String operatorId = JSON.readTree(accept(token, invitee)
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.tenantId").value(TENANT_X))
                        .andExpect(jsonPath("$.roles[0]").value("TENANT_ADMIN"))
                        .andExpect(jsonPath("$.alreadyAccepted").value(false))
                        .andReturn().getResponse().getContentAsString())
                .get("operatorId").asText();

        Map<String, Object> op = jdbcTemplate.queryForMap(
                "SELECT id, tenant_id, email, password_hash, status, oidc_subject FROM admin_operators WHERE operator_id = ?",
                operatorId);
        assertThat(op.get("oidc_subject")).as("🔴 F1 — the login door").isEqualTo(invitee);
        assertThat(op.get("tenant_id")).isEqualTo(TENANT_X);
        assertThat(op.get("email")).isEqualTo(email);
        assertThat(op.get("password_hash")).isNull();
        assertThat(op.get("status")).isEqualTo("ACTIVE");
        assertThat(jdbcTemplate.queryForList("SELECT tenant_id FROM admin_operator_roles WHERE operator_id = ?",
                String.class, op.get("id"))).containsExactly(TENANT_X);
        assertThat(jdbcTemplate.queryForList("SELECT tenant_id FROM operator_tenant_assignment WHERE operator_id = ?",
                String.class, op.get("id"))).containsExactly(TENANT_X);
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT status, accepted_account_id, accepted_operator_id FROM operator_invitation WHERE invitation_id = ?",
                invitationId);
        assertThat(row.get("status")).isEqualTo("ACCEPTED");
        assertThat(row.get("accepted_account_id")).isEqualTo(invitee);
        assertThat(((Number) row.get("accepted_operator_id")).longValue()).isEqualTo(((Number) op.get("id")).longValue());
        Map<String, Object> audit = jdbcTemplate.queryForMap("""
                SELECT operator_id, permission_used, reason, target_type, target_tenant_id, outcome, downstream_detail
                  FROM admin_actions WHERE action_code = 'OPERATOR_INVITATION_ACCEPT' AND target_id = ?""", invitationId);
        assertThat(((Number) audit.get("operator_id")).longValue()).isEqualTo(((Number) op.get("id")).longValue());
        assertThat(audit.get("permission_used")).isEqualTo("<self_invitation_accept>");
        assertThat(audit.get("reason")).isEqualTo("<self_invitation_accept>");
        assertThat(audit.get("target_type")).isEqualTo("OPERATOR_INVITATION");
        assertThat(audit.get("target_tenant_id")).isEqualTo(TENANT_X);
        assertThat(audit.get("outcome")).isEqualTo("SUCCESS");
        assertThat(audit.get("downstream_detail")).isEqualTo("accountId=" + invitee);

        // ⑥ reused by another account → 409, still exactly one operator
        accept(token, rival).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OPERATOR_INVITATION_ALREADY_USED"));
        assertThat(operatorsWithSubject(rival)).isZero();
        assertThat(operatorsFor(TENANT_X, email)).isEqualTo(1);

        // ⑦ the owner resubmits (double submit / back button) → 200 alreadyAccepted, the first result
        accept(token, invitee).andExpect(status().isOk())
                .andExpect(jsonPath("$.alreadyAccepted").value(true))
                .andExpect(jsonPath("$.operatorId").value(operatorId));
        assertThat(acceptAuditRows(invitationId)).isEqualTo(1);

        // ⑧ a cancelled invitation → 404, nothing written
        String email2 = uniqueEmail();
        String[] cancelled = invite(ADMIN_X, TENANT_X, email2, "TENANT_ADMIN");
        mockMvc.perform(post("/api/admin/operator-invitations/" + cancelled[0] + ":cancel")
                        .header("Authorization", "Bearer " + jwt.operatorToken(ADMIN_X))
                        .header("X-Operator-Reason", "s3-it"))
                .andExpect(status().isOk());
        String verifiedOther = newAccountId();
        stubMatch(verifiedOther, 200, null);
        accept(cancelled[1], verifiedOther).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("OPERATOR_INVITATION_NOT_FOUND"));
        assertThat(operatorsFor(TENANT_X, email2)).isZero();
        assertThat(operatorsWithSubject(verifiedOther)).isZero();

        // R4 — neither token nor hash in the log, and the capture is not empty (our refusal lines are in it).
        StringBuilder logged = new StringBuilder();
        for (var event : logs.list) {
            logged.append(event.getFormattedMessage()).append('\n');
        }
        assertThat(logged.toString()).contains("operator-invitation accept refused")
                .doesNotContain(token).doesNotContain(OperatorInvitationTokens.sha256Hex(token))
                .doesNotContain(cancelled[1]);
    }

    // ── AC-6 ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("🔴 AC-6: 셀프 온보딩한 새 조직의 관리자가 초대 → 피초대자(인증된 풀 계정) 수락 → 콘솔 적격 true · 토큰 교환 200 / 그 계정의 두 번째 회사 409 · 그 계정의 셀프 온보딩 409(테넌트 생성 전)")
    void ac6_selfOnboardedOrgAdmin_invites_inviteeBecomesOperator() throws Exception {
        String founder = newAccountId();
        String founderEmail = uniqueEmail();
        String newOrg = "s3-org-" + UUID.randomUUID().toString().substring(0, 8);
        stubAccountDetail(founder, founderEmail);
        stubTenantCreate(newOrg);
        stubTenant(newOrg, "ACTIVE");

        // 1. self-service onboarding → the founder is the new org's first TENANT_ADMIN.
        mockMvc.perform(post("/api/admin/onboarding/organizations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subjectToken\":\"" + userOidcToken(founder) + "\",\"tenantId\":\"" + newOrg
                                + "\",\"organizationName\":\"S3 Org\"}"))
                .andExpect(status().isCreated());
        String founderOperator = jdbcTemplate.queryForObject(
                "SELECT operator_id FROM admin_operators WHERE oidc_subject = ?", String.class, founder);

        // 2. the founder invites a colleague (S2 path).
        String inviteeEmail = uniqueEmail();
        String invitee = newAccountId();
        String[] inv = invite(founderOperator, newOrg, inviteeEmail, "TENANT_ADMIN");
        preview(inv[1]).andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(newOrg))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.expired").value(false))
                .andExpect(jsonPath("$.maskedEmail").value(inviteeEmail.charAt(0) + "*****@example.com"));

        // Before accepting, the colleague's pool account has no console (S4's closed path, the control).
        assertThat(consoleEligible(invitee)).isFalse();

        // 3. the colleague (site-less pool signup → verified — account-service's half) accepts.
        stubMatch(invitee, 200, null);
        accept(inv[1], invitee).andExpect(status().isOk()).andExpect(jsonPath("$.tenantId").value(newOrg));

        // 4. … and is now an operator the console admits: S4's eligibility read, and the operator token exchange.
        assertThat(consoleEligible(invitee)).isTrue();
        mockMvc.perform(post("/api/admin/auth/token-exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"grant_type\":\"urn:ietf:params:oauth:grant-type:token-exchange\","
                                + "\"subject_token\":\"" + userOidcToken(invitee) + "\","
                                + "\"subject_token_type\":\"urn:ietf:params:oauth:token-type:access_token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("admin"));

        // 5. 🔴 OD-1 — a second company's invitation to the same person → 409, no second operator.
        String[] second = invite(ADMIN_X, TENANT_X, inviteeEmail, "TENANT_ADMIN");
        accept(second[1], invitee).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OPERATOR_ALREADY_PROVISIONED"));
        assertThat(operatorsWithSubject(invitee)).isEqualTo(1);
        assertThat(invitationStatus(second[0])).isEqualTo("PENDING");

        // 6. 🔴 S1-11 — that account's self-onboarding → 409 BEFORE any tenant is created.
        stubAccountDetail(invitee, inviteeEmail);
        int tenantCreatesBefore = wireMock.findAll(postRequestedFor(urlEqualTo("/internal/tenants"))).size();
        mockMvc.perform(post("/api/admin/onboarding/organizations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subjectToken\":\"" + userOidcToken(invitee) + "\",\"tenantId\":\"s3-never-"
                                + UUID.randomUUID().toString().substring(0, 6) + "\",\"organizationName\":\"Never\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OPERATOR_ALREADY_PROVISIONED"));
        assertThat(wireMock.findAll(postRequestedFor(urlEqualTo("/internal/tenants")))).hasSize(tenantCreatesBefore);
    }

    private void stubAccountDetail(String accountId, String email) {
        wireMock.stubFor(WireMock.get(urlPathEqualTo("/internal/accounts/" + accountId))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"" + accountId + "\",\"email\":\"" + email
                                + "\",\"status\":\"ACTIVE\",\"createdAt\":\"2026-10-10T00:00:00Z\","
                                + "\"profile\":{\"displayName\":\"S3 Founder\",\"phoneMasked\":null}}")));
    }

    private void stubTenantCreate(String tenantId) {
        wireMock.stubFor(WireMock.post(urlEqualTo("/internal/tenants"))
                .willReturn(aResponse().withStatus(201).withHeader("Content-Type", "application/json")
                        .withBody("{\"tenantId\":\"" + tenantId + "\",\"displayName\":\"S3 Org\","
                                + "\"tenantType\":\"B2B_ENTERPRISE\",\"status\":\"ACTIVE\","
                                + "\"createdAt\":\"2026-10-10T00:00:00Z\",\"updatedAt\":\"2026-10-10T00:00:00Z\"}")));
    }

    // ── D3 · concurrency ────────────────────────────────────────────────────

    @Test
    @DisplayName("🔴 D3 재판정: 초대 뒤 초대자의 권한이 회수됨 → 인증된 본인 수락도 409 INVALIDATED · 아무것도 안 씀")
    void inviterLostTheGrant_invalidated() throws Exception {
        String inviter = UUID.randomUUID().toString();
        seedTenantAdmin(inviter, TENANT_X);
        String email = uniqueEmail();
        String[] inv = invite(inviter, TENANT_X, email, "TENANT_ADMIN");

        jdbcTemplate.update("DELETE FROM admin_operator_roles WHERE operator_id = (SELECT id FROM admin_operators WHERE operator_id = ?)",
                inviter);
        String invitee = newAccountId();
        stubMatch(invitee, 200, null);

        accept(inv[1], invitee).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OPERATOR_INVITATION_INVALIDATED"));
        assertNothingWritten(inv[0], email, invitee);
    }

    @Test
    @DisplayName("동시 수락 두 계정 → 정확히 하나만 200 · 운영자 행 하나 · 진 쪽은 409 ALREADY_USED")
    void concurrentAccept_exactlyOneWins() throws Exception {
        String email = uniqueEmail();
        String[] inv = invite(ADMIN_X, TENANT_X, email, "TENANT_ADMIN");
        String a = newAccountId();
        String b = newAccountId();
        stubMatch(a, 200, null);
        stubMatch(b, 200, null);

        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<MvcResult>> results = new ArrayList<>();
            for (String account : List.of(a, b)) {
                results.add(pool.submit(() -> {
                    go.await();
                    return accept(inv[1], account).andReturn();
                }));
            }
            go.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<MvcResult> f : results) {
                statuses.add(f.get(30, TimeUnit.SECONDS).getResponse().getStatus());
            }
            assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        } finally {
            pool.shutdownNow();
        }
        assertThat(operatorsFor(TENANT_X, email)).isEqualTo(1);
        assertThat(operatorsWithSubject(a) + operatorsWithSubject(b)).isEqualTo(1);
        assertThat(acceptAuditRows(inv[0])).isEqualTo(1);
    }

    @Test
    @DisplayName("preview: 아무것도 쓰지 않는다 · 수락 뒤엔 status=ACCEPTED · 모르는 토큰 404")
    void preview_readsOnly() throws Exception {
        String email = uniqueEmail();
        String[] inv = invite(ADMIN_X, TENANT_X, email, "TENANT_ADMIN");
        JsonNode before = JSON.readTree(preview(inv[1]).andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantDisplayName").value("S3 " + TENANT_X))
                .andReturn().getResponse().getContentAsString());
        assertThat(before.get("status").asText()).isEqualTo("PENDING");
        assertThat(before.toString()).doesNotContain(email).doesNotContain(inv[1]);
        assertThat(invitationStatus(inv[0])).isEqualTo("PENDING");

        String invitee = newAccountId();
        stubMatch(invitee, 200, null);
        accept(inv[1], invitee).andExpect(status().isOk());
        preview(inv[1]).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACCEPTED"));
        preview("not-a-real-token").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("OPERATOR_INVITATION_NOT_FOUND"));
    }
}
