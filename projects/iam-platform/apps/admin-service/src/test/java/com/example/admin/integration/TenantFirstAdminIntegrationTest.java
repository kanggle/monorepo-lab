package com.example.admin.integration;

import com.example.admin.support.OperatorJwtTestFixture;
import com.example.testsupport.integration.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.extension.ResponseDefinitionTransformerV2;
import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import org.junit.jupiter.api.AfterAll;
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
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.patchRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-MONO-773 S4 — the {@code SUPER_ADMIN} tenant-creation path: «tenant + first admin» as one bundle
 * (admin-api.md § Tenant Lifecycle · § Operator Invitation «첫 관리자 초대» · internal/auth-to-admin.md § accept
 * «첫 관리자 초대일 때» · tenant-events.md · admin-service data-model.md § operator_invitation).
 *
 * <p>Real MySQL (the S4 migration: {@code first_admin} · {@code first_admin_pending_key}); WireMock is account-service.
 * 🔴 The WireMock {@code POST /internal/tenants} stub models S2's real behaviour — it answers {@code PENDING} only when
 * the request carries {@code initialStatus = PENDING} and {@code ACTIVE} otherwise — so a stub can never make the
 * old code look correct.
 *
 * <p>🔴 <b>The ADR-044 D3 invariant</b> («no {@code ACTIVE} tenant with zero {@code TENANT_ADMIN}») is measured at the
 * only moment a {@code PENDING} tenant can become {@code ACTIVE}: {@link ActivationProbe} is a WireMock transformer
 * that, while account-service is being asked {@code PATCH /internal/tenants/{id} status=ACTIVE}, counts — on its own
 * connection, so it sees committed rows only — the {@code ACTIVE} operators holding a {@code TENANT_ADMIN} role row in
 * that tenant. Moving the activation call before the admin commit (auth-to-admin.md § accept 9 before 8) turns
 * {@link #assertEveryActivationSawAnAdmin} red.
 *
 * <p>Two measurement obligations of S1 live here: (Q4) {@link #measure_starOperatorAssumesPendingTenant} and the
 * deploy-order window (admin-api § POST /tenants «호환») — {@link #post_superAdmin_pendingTenantAndFirstAdminInvitation}
 * asserts {@code 201} BEFORE {@code status = PENDING}, so on the pre-S4 code its red line is the measurement «the old
 * request DTO ignores {@code firstAdmin}: 201 · {@code ACTIVE} · no invitation».
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
class TenantFirstAdminIntegrationTest extends AbstractIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    private static final String MAIL_PATH = "/internal/notifications/operator-invitation";
    private static final String ALL_DOMAINS_FULL = "all-domains-full";
    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String SUPER_ADMIN_UUID = "00000000-0000-7000-8000-0000000c5401";
    private static final String REGULAR_OP_UUID = "00000000-0000-7000-8000-0000000c5402";

    static WireMockServer wireMock;
    static OperatorJwtTestFixture jwt;
    static String adminSigningKeyPem;

    @BeforeAll
    static void setupShared() {
        jwt = new OperatorJwtTestFixture();
        java.security.PrivateKey adminPk = extractPrivateKey(jwt);
        adminSigningKeyPem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(adminPk.getEncoded())
                + "\n-----END PRIVATE KEY-----\n";
        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort()
                .extensions(new ActivationProbe()));
        wireMock.start();
        WireMock.configureFor("localhost", wireMock.port());
    }

    @AfterAll
    static void tearDownShared() {
        if (wireMock != null) wireMock.stop();
        ActivationProbe.jdbc = null;
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
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void stubsAndSeeds() {
        ActivationProbe.jdbc = jdbc;
        wireMock.resetAll();
        wireMock.stubFor(WireMock.post(urlEqualTo("/oauth2/token"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"access_token\":\"test-jwt\",\"expires_in\":300,\"token_type\":\"Bearer\"}")));
        wireMock.stubFor(WireMock.post(urlPathMatching("/internal/tenants/[^/]+/identities:resolveOrCreate"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"identityId\":\"identity-773s4\",\"outcome\":\"CREATED\"}")));
        wireMock.stubFor(WireMock.post(urlPathEqualTo(MAIL_PATH)).willReturn(aResponse().withStatus(204)));
        seedSuperAdmin();
        seedRegularOperator();
    }

    // ═══ POST /api/admin/tenants — tenant + first-admin invitation ═════════════════

    @Test
    @DisplayName("🔴 POST(SUPER_ADMIN) + firstAdmin → 201 · 테넌트 PENDING(account 에 initialStatus=PENDING) · 첫 관리자 초대 한 행(first_admin · 역할 둘 · invited_by=actor) · 감사 TENANT_CREATE + OPERATOR_INVITATION_CREATE · tenant.created status=PENDING · 메일 한 통 · 응답에 토큰 없음 · 같은 슬러그 재요청 409 = 초대 · 메일 그대로 (🔵 옛 코드에서의 빨강 = 배포 순서 창 측정)")
    void post_superAdmin_pendingTenantAndFirstAdminInvitation() throws Exception {
        String slug = slug();
        stubCreate(slug, "B2B_ENTERPRISE");
        stubTenantGet(slug, "PENDING");

        String body = createTenant(slug, "B2B_ENTERPRISE", "  Owner." + slug + "@Acme.Example ", "홍길동")
                // 🔵 deploy-order measurement (admin-api § POST /tenants «호환»): the pre-S4 request DTO must NOT 400 on
                //    the unknown `firstAdmin` — this line passes on the old code …
                .andExpect(status().isCreated())
                // … and THIS is the old code's answer: an ACTIVE tenant (no initialStatus sent) and no invitation.
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.tenantId").value(slug))
                .andExpect(jsonPath("$.firstAdmin.state").value("INVITED"))
                .andExpect(jsonPath("$.firstAdmin.email").value("owner." + slug + "@acme.example"))
                .andExpect(jsonPath("$.firstAdminInvitation.firstAdmin").value(true))
                .andExpect(jsonPath("$.firstAdminInvitation.status").value("PENDING"))
                .andExpect(jsonPath("$.firstAdminInvitation.tenantId").value(slug))
                .andReturn().getResponse().getContentAsString();

        JsonNode json = JSON.readTree(body);
        String invitationId = json.at("/firstAdmin/invitationId").asText();
        assertThat(json.at("/firstAdminInvitation/invitationId").asText()).isEqualTo(invitationId);
        assertThat(json.at("/firstAdmin/expiresAt").asText()).isNotBlank();
        List<String> roles = new ArrayList<>();
        json.at("/firstAdminInvitation/roles").forEach(n -> roles.add(n.asText()));
        assertThat(roles).containsExactlyInAnyOrder("TENANT_ADMIN", "TENANT_BILLING_ADMIN");

        // the account-service call asked for PENDING — a create without it is how an ACTIVE tenant would be born
        assertThat(wireMock.findAll(postRequestedFor(urlEqualTo("/internal/tenants"))
                .withRequestBody(matchingJsonPath("$.tenantId", equalTo(slug)))
                .withRequestBody(matchingJsonPath("$.initialStatus", equalTo("PENDING"))))).hasSize(1);

        // one first-admin invitation row, from the actor, the two fixed roles
        Map<String, Object> row = jdbc.queryForMap("""
                SELECT CAST(i.first_admin AS UNSIGNED) AS first_admin, i.status, i.email, i.display_name, i.roles, o.operator_id AS inviter
                  FROM operator_invitation i JOIN admin_operators o ON o.id = i.invited_by
                 WHERE i.invitation_id = ?""", invitationId);
        assertThat(((Number) row.get("first_admin")).intValue()).isEqualTo(1);
        assertThat(row.get("status")).isEqualTo("PENDING");
        assertThat(row.get("email")).isEqualTo("owner." + slug + "@acme.example");
        assertThat(row.get("display_name")).isEqualTo("홍길동");
        assertThat(row.get("roles")).isEqualTo("TENANT_ADMIN,TENANT_BILLING_ADMIN");
        assertThat(row.get("inviter")).isEqualTo(SUPER_ADMIN_UUID);
        assertThat(count("SELECT COUNT(*) FROM operator_invitation WHERE tenant_id = ?", slug)).isEqualTo(1);

        // audit — both rows, SUCCESS, on the new tenant
        assertThat(count("""
                SELECT COUNT(*) FROM admin_actions WHERE action_code = 'TENANT_CREATE' AND target_id = ?
                   AND outcome = 'SUCCESS'""", slug)).isEqualTo(1);
        assertThat(count("""
                SELECT COUNT(*) FROM admin_actions WHERE action_code = 'OPERATOR_INVITATION_CREATE' AND target_id = ?
                   AND target_tenant_id = ? AND outcome = 'SUCCESS'""", invitationId, slug)).isEqualTo(1);
        // tenant.created carries status = PENDING (tenant-events.md)
        assertThat(outboxPayload("tenant.created", slug).at("/payload/status").asText()).isEqualTo("PENDING");

        // one mail, to the invited address; the token never in the response (772 R4)
        List<LoggedRequest> mails = mailsFor(slug);
        assertThat(mails).hasSize(1);
        String token = JSON.readTree(mails.get(0).getBodyAsString()).get("token").asText();
        assertThat(body).doesNotContain(token);

        // no operator yet, and nothing ever asked account-service to activate
        assertThat(activeTenantAdmins(jdbc, slug)).isZero();
        assertThat(activationCalls(slug)).isZero();

        // a second POST of the same slug (account-service 409) → 409, the invitation and mail are not repeated
        wireMock.stubFor(WireMock.post(urlEqualTo("/internal/tenants")).atPriority(0)
                .withRequestBody(matchingJsonPath("$.tenantId", equalTo(slug)))
                .willReturn(aResponse().withStatus(409).withHeader("Content-Type", "application/json")
                        .withBody("{\"code\":\"TENANT_ALREADY_EXISTS\",\"message\":\"exists\"}")));
        createTenant(slug, "B2B_ENTERPRISE", "other." + slug + "@acme.example", "김대표")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TENANT_ALREADY_EXISTS"));
        assertThat(count("SELECT COUNT(*) FROM operator_invitation WHERE tenant_id = ?", slug)).isEqualTo(1);
        assertThat(mailsFor(slug)).hasSize(1);
    }

    @Test
    @DisplayName("🔴 B2C 도 같은 규칙: B2C_CONSUMER + firstAdmin → PENDING · 첫 관리자 초대")
    void post_b2c_sameFirstAdminRule() throws Exception {
        String slug = slug();
        stubCreate(slug, "B2C_CONSUMER");
        stubTenantGet(slug, "PENDING");
        createTenant(slug, "B2C_CONSUMER", "b2c." + slug + "@example.com", "B2C 관리자")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.tenantType").value("B2C_CONSUMER"))
                .andExpect(jsonPath("$.firstAdmin.state").value("INVITED"));
        assertThat(count("SELECT COUNT(*) FROM operator_invitation WHERE tenant_id = ? AND first_admin = TRUE", slug))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("🔴 firstAdmin 필수(breaking): 누락(B2B · B2C) · 이메일 형식 · displayName 빈/65자 → 400 VALIDATION_ERROR · account 생성 호출 0 · 초대 0")
    void post_firstAdminRequiredAndValidated_400_nothingCreated() throws Exception {
        String slug = slug();
        stubCreate(slug, "B2B_ENTERPRISE");
        stubCreate(slug, "B2C_CONSUMER");

        for (String type : List.of("B2B_ENTERPRISE", "B2C_CONSUMER")) {
            mockMvc.perform(post("/api/admin/tenants")
                            .header("Authorization", bearer(SUPER_ADMIN_UUID))
                            .header("X-Operator-Reason", "s4-it")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"tenantId\":\"" + slug + "\",\"displayName\":\"S4\",\"tenantType\":\"" + type + "\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
        for (String[] bad : new String[][]{
                {"not-an-email", "이름"},
                {"ok." + slug + "@example.com", ""},
                {"ok." + slug + "@example.com", "가".repeat(65)}}) {
            createTenant(slug, "B2B_ENTERPRISE", bad[0], bad[1])
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
        assertThat(tenantCreateCalls(slug)).as("nothing reached account-service").isZero();
        assertThat(count("SELECT COUNT(*) FROM operator_invitation WHERE tenant_id = ?", slug)).isZero();
    }

    @Test
    @DisplayName("🔴 보상: 초대 쓰기 실패(DB 거절 주입) → 5xx · 테넌트 PENDING→SUSPENDED 보상 요청 · ACTIVE 요청 0 · 초대 · tenant.created 0")
    void post_invitationWriteFails_compensatedBySuspend_neverActive() throws Exception {
        String slug = slug();
        stubCreate(slug, "B2B_ENTERPRISE");
        stubTenantGet(slug, "PENDING");
        stubPatch(slug, "SUSPENDED", 200, "SUSPENDED");
        // inject the failure at the invitation INSERT, for this slug only (dropped in finally)
        jdbc.execute("DROP TRIGGER IF EXISTS s4_fail_first_admin_invite");
        jdbc.execute("""
                CREATE TRIGGER s4_fail_first_admin_invite BEFORE INSERT ON operator_invitation FOR EACH ROW
                BEGIN
                  IF NEW.tenant_id = '%s' THEN
                    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 's4 injected invitation write failure';
                  END IF;
                END""".formatted(slug));
        try {
            createTenant(slug, "B2B_ENTERPRISE", "comp." + slug + "@example.com", "보상")
                    .andExpect(status().is5xxServerError());
        } finally {
            jdbc.execute("DROP TRIGGER IF EXISTS s4_fail_first_admin_invite");
        }
        assertThat(wireMock.findAll(patchRequestedFor(urlPathEqualTo("/internal/tenants/" + slug))
                .withRequestBody(matchingJsonPath("$.status", equalTo("SUSPENDED")))))
                .as("the just-created PENDING tenant is compensated by SUSPEND").isNotEmpty();
        assertThat(activationCalls(slug)).as("never ACTIVE").isZero();
        assertThat(count("SELECT COUNT(*) FROM operator_invitation WHERE tenant_id = ?", slug)).isZero();
        assertThat(count("SELECT COUNT(*) FROM admin_outbox WHERE event_type = 'tenant.created' AND partition_key = ?",
                slug)).isZero();
        assertThat(mailsFor(slug)).isEmpty();
    }

    // ═══ data-model — first_admin · first_admin_pending_key ═══════════════════════════

    @Test
    @DisplayName("🔴 data-model: first_admin BOOLEAN NOT NULL DEFAULT FALSE · first_admin_pending_key 생성 칸 + UNIQUE → 테넌트당 대기 첫 관리자 초대 하나(주소 무관) · 일반 대기 초대와 공존 · 취소된 것 뒤 새 대기 허용")
    void migration_firstAdminColumns_onePendingPerTenant() {
        Map<String, Object> firstAdmin = column("operator_invitation", "first_admin");
        assertThat(firstAdmin).as("V00xx adds operator_invitation.first_admin").isNotNull();
        assertThat(firstAdmin.get("IS_NULLABLE")).isEqualTo("NO");
        assertThat(String.valueOf(firstAdmin.get("COLUMN_DEFAULT"))).isIn("0", "b'0'", "FALSE", "false");
        Map<String, Object> key = column("operator_invitation", "first_admin_pending_key");
        assertThat(key).as("V00xx adds operator_invitation.first_admin_pending_key").isNotNull();
        assertThat(String.valueOf(key.get("EXTRA"))).containsIgnoringCase("GENERATED");
        assertThat(count("""
                SELECT COUNT(*) FROM information_schema.STATISTICS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'operator_invitation'
                   AND COLUMN_NAME = 'first_admin_pending_key' AND NON_UNIQUE = 0""")).isEqualTo(1);

        String tenant = slug();
        long inviter = internalId(SUPER_ADMIN_UUID);
        insertInvitation(tenant, "a." + tenant + "@example.com", true, "PENDING", inviter);
        // a second PENDING first-admin invitation for the same tenant, another address → the DB refuses
        assertThatThrownBy(() -> insertInvitation(tenant, "b." + tenant + "@example.com", true, "PENDING", inviter))
                .isInstanceOf(DataIntegrityViolationException.class);
        // an ORDINARY pending invitation next to it is fine (the key is per tenant for first-admin rows only)
        insertInvitation(tenant, "c." + tenant + "@example.com", false, "PENDING", inviter);
        // once the first one is CANCELLED, a new PENDING first-admin invitation is admitted
        jdbc.update("UPDATE operator_invitation SET status = 'CANCELLED', cancelled_at = NOW(6) "
                + "WHERE tenant_id = ? AND email = ?", tenant, "a." + tenant + "@example.com");
        insertInvitation(tenant, "d." + tenant + "@example.com", true, "PENDING", inviter);
        // pre-773 rows default to an ordinary invitation
        assertThat(count("SELECT COUNT(*) FROM operator_invitation WHERE first_admin IS NULL")).isZero();
    }

    // ═══ acceptance — 첫 관리자 초대일 때 ═══════════════════════════════════════════

    @Test
    @DisplayName("🔴 AC-4: 첫 관리자 수락(새 계정) → 200 역할 둘 · 운영자 행(홈=새 테넌트) · 배정 · ORIGIN all-domains-full 한 행 · 커밋 뒤 account PENDING→ACTIVE(그 순간 TENANT_ADMIN ≥1) · TENANT_ACTIVATE 감사 · tenant.activated(FIRST_ADMIN_ACCEPTED) · 요약 ACCEPTED · preview firstAdmin=true")
    void accept_firstAdmin_newAccount_activatesAfterAdminCommit() throws Exception {
        Created c = createPending("B2B_ENTERPRISE");
        preview(c.token()).andExpect(status().isOk()).andExpect(jsonPath("$.firstAdmin").value(true));

        String account = UUID.randomUUID().toString();
        stubMatch(account);
        stubPatch(c.slug(), "ACTIVE", 200, "ACTIVE");

        String operatorId = JSON.readTree(accept(c.token(), account)
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.tenantId").value(c.slug()))
                        .andExpect(jsonPath("$.alreadyAccepted").value(false))
                        .andReturn().getResponse().getContentAsString())
                .get("operatorId").asText();

        long id = internalId(operatorId);
        Map<String, Object> op = jdbc.queryForMap(
                "SELECT tenant_id, oidc_subject, status FROM admin_operators WHERE id = ?", id);
        assertThat(op.get("tenant_id")).isEqualTo(c.slug());
        assertThat(op.get("oidc_subject")).isEqualTo(account);
        assertThat(op.get("status")).isEqualTo("ACTIVE");
        assertThat(roleNames(id, c.slug())).containsExactly("TENANT_ADMIN", "TENANT_BILLING_ADMIN");
        assertThat(count("SELECT COUNT(*) FROM operator_tenant_assignment WHERE operator_id = ? AND tenant_id = ?",
                id, c.slug())).isEqualTo(1);
        // OD-5 — the founding admin's ORIGIN set, exactly one row, this tenant only
        List<Map<String, Object>> origin = originRows(id);
        assertThat(origin).hasSize(1);
        assertThat(origin.get(0).get("tenant_id")).isEqualTo(c.slug());
        assertThat(origin.get(0).get("system_key")).isEqualTo(ALL_DOMAINS_FULL);
        assertThat(origin.get(0).get("granted_by")).isNull();
        assertThat(origin.get(0).get("group_origin")).isNull();
        assertThat(count("SELECT COUNT(*) FROM operator_domain_set_grant WHERE operator_id = ?", id)).isEqualTo(1);
        assertThat(invitationStatus(c.invitationId())).isEqualTo("ACCEPTED");

        // 🔴 activation happened exactly once, and only AFTER the admin rows were committed (ADR-044 D3)
        assertThat(activationCalls(c.slug())).isEqualTo(1);
        assertEveryActivationSawAnAdmin(c.slug());

        Map<String, Object> audit = jdbc.queryForMap("""
                SELECT operator_id, permission_used, target_type, target_id, target_tenant_id, outcome
                  FROM admin_actions WHERE action_code = 'TENANT_ACTIVATE' AND target_id = ?""", c.slug());
        assertThat(((Number) audit.get("operator_id")).longValue()).isEqualTo(id);
        assertThat(audit.get("permission_used")).isEqualTo("<self_invitation_accept>");
        assertThat(audit.get("target_type")).isEqualTo("TENANT");
        assertThat(audit.get("target_tenant_id")).isEqualTo(c.slug());
        assertThat(audit.get("outcome")).isEqualTo("SUCCESS");
        JsonNode activated = outboxPayload("tenant.activated", c.slug());
        assertThat(activated.at("/payload/previousStatus").asText()).isEqualTo("PENDING");
        assertThat(activated.at("/payload/currentStatus").asText()).isEqualTo("ACTIVE");
        assertThat(activated.at("/payload/trigger").asText()).isEqualTo("FIRST_ADMIN_ACCEPTED");
        assertThat(activated.at("/payload/actor/id").asText()).isEqualTo(operatorId);

        stubTenantGet(c.slug(), "ACTIVE");
        tenantGet(c.slug()).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.firstAdmin.state").value("ACCEPTED"));
    }

    @Test
    @DisplayName("🔴 R4: 활성화(account) 실패 → 503 · 관리자 행 · ORIGIN · ACCEPTED 는 이미 커밋(관리자 있는 PENDING) · TENANT_ACTIVATE · tenant.activated 0 · 요약 ACCEPTED → 같은 링크 재제출 = 200 alreadyAccepted + 활성화 재시도 · 이벤트 한 번")
    void accept_activationFails_adminCommitted_resubmitRetries() throws Exception {
        Created c = createPending("B2B_ENTERPRISE");
        String account = UUID.randomUUID().toString();
        stubMatch(account);
        stubPatch(c.slug(), "ACTIVE", 503, null);

        accept(c.token(), account).andExpect(status().isServiceUnavailable());

        long id = internalIdBySubject(account);
        assertThat(roleNames(id, c.slug())).containsExactly("TENANT_ADMIN", "TENANT_BILLING_ADMIN");
        assertThat(originRows(id)).hasSize(1);
        assertThat(invitationStatus(c.invitationId())).isEqualTo("ACCEPTED");
        assertThat(count("SELECT COUNT(*) FROM admin_actions WHERE action_code = 'TENANT_ACTIVATE' AND target_id = ?",
                c.slug())).isZero();
        assertThat(count("SELECT COUNT(*) FROM admin_outbox WHERE event_type = 'tenant.activated' AND partition_key = ?",
                c.slug())).isZero();
        assertEveryActivationSawAnAdmin(c.slug());
        tenantGet(c.slug()).andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.firstAdmin.state").value("ACCEPTED"));

        stubPatch(c.slug(), "ACTIVE", 200, "ACTIVE");
        int before = activationCalls(c.slug());
        accept(c.token(), account).andExpect(status().isOk())
                .andExpect(jsonPath("$.alreadyAccepted").value(true));
        assertThat(activationCalls(c.slug()) - before).as("the resubmit retried the activation once").isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM admin_actions WHERE action_code = 'TENANT_ACTIVATE' AND target_id = ?",
                c.slug())).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM admin_outbox WHERE event_type = 'tenant.activated' AND partition_key = ?",
                c.slug())).isEqualTo(1);
        assertThat(roleNames(id, c.slug())).as("no second admin write").containsExactly("TENANT_ADMIN", "TENANT_BILLING_ADMIN");
        assertEveryActivationSawAnAdmin(c.slug());
    }

    @Test
    @DisplayName("🔴 복구 PATCH: 수락됐지만 PENDING(활성화만 실패) → SUPER_ADMIN PATCH status=ACTIVE = 200 · TENANT_ACTIVATE(actor) · tenant.activated(OPERATOR_COMPLETED)")
    void recovery_acceptedButPending_superAdminPatchCompletes() throws Exception {
        Created c = createPending("B2B_ENTERPRISE");
        String account = UUID.randomUUID().toString();
        stubMatch(account);
        stubPatch(c.slug(), "ACTIVE", 503, null);
        accept(c.token(), account).andExpect(status().isServiceUnavailable());

        stubPatch(c.slug(), "ACTIVE", 200, "ACTIVE");
        patchStatus(c.slug(), "ACTIVE").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));

        Map<String, Object> audit = jdbc.queryForMap("""
                SELECT operator_id, target_tenant_id, outcome FROM admin_actions
                 WHERE action_code = 'TENANT_ACTIVATE' AND target_id = ?""", c.slug());
        assertThat(((Number) audit.get("operator_id")).longValue()).isEqualTo(internalId(SUPER_ADMIN_UUID));
        assertThat(audit.get("outcome")).isEqualTo("SUCCESS");
        JsonNode activated = outboxPayload("tenant.activated", c.slug());
        assertThat(activated.at("/payload/trigger").asText()).isEqualTo("OPERATOR_COMPLETED");
        assertThat(activated.at("/payload/actor/id").asText()).isEqualTo(SUPER_ADMIN_UUID);
        assertEveryActivationSawAnAdmin(c.slug());
    }

    @Test
    @DisplayName("🔴 784 b + OD-3: 이미 상한(3)인 회사 운영자가 첫 관리자 초대 수락 → 같은 행에 소속 추가(상한 안 봄) · 새 테넌트에만 역할 둘 · 배정 · ORIGIN · 기존 행 바이트 불변")
    void accept_firstAdmin_existingOperatorAtCap_membershipAdded() throws Exception {
        String account = UUID.randomUUID().toString();
        String email = "cap." + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        long id = seedFacetOwning(account, email, slug(), slug(), slug());
        List<Map<String, Object>> rolesBefore = jdbc.queryForList(
                "SELECT * FROM admin_operator_roles WHERE operator_id = ? ORDER BY tenant_id, role_id", id);
        List<Map<String, Object>> setsBefore = jdbc.queryForList(
                "SELECT * FROM operator_domain_set_grant WHERE operator_id = ? ORDER BY tenant_id, set_id", id);
        Map<String, Object> rowBefore = operatorRow(id);

        Created c = createPending("B2B_ENTERPRISE", email);
        stubMatch(account);
        stubPatch(c.slug(), "ACTIVE", 200, "ACTIVE");
        accept(c.token(), account).andExpect(status().isOk())
                .andExpect(jsonPath("$.operatorId").value(rowBefore.get("operator_id")));

        assertThat(roleNames(id, c.slug())).containsExactly("TENANT_ADMIN", "TENANT_BILLING_ADMIN");
        assertThat(count("SELECT COUNT(*) FROM operator_tenant_assignment WHERE operator_id = ? AND tenant_id = ?",
                id, c.slug())).isEqualTo(1);
        assertThat(jdbc.queryForList("""
                SELECT CONCAT(g.source_key, '/', s.system_key) FROM operator_domain_set_grant g
                  JOIN domain_permission_set s ON s.id = g.set_id
                 WHERE g.operator_id = ? AND g.tenant_id = ?""", String.class, id, c.slug()))
                .containsExactly("ORIGIN/" + ALL_DOMAINS_FULL);
        assertThat(jdbc.queryForList("SELECT * FROM admin_operator_roles WHERE operator_id = ? AND tenant_id <> ? "
                + "ORDER BY tenant_id, role_id", id, c.slug())).isEqualTo(rolesBefore);
        assertThat(jdbc.queryForList("SELECT * FROM operator_domain_set_grant WHERE operator_id = ? AND tenant_id <> ? "
                + "ORDER BY tenant_id, set_id", id, c.slug())).isEqualTo(setsBefore);
        assertThat(operatorRow(id)).isEqualTo(rowBefore);
        assertEveryActivationSawAnAdmin(c.slug());
    }

    @Test
    @DisplayName("🔴 첫 관리자 초대는 PENDING 테넌트에서만 이긴다: 테넌트가 SUSPENDED · ACTIVE 면 409 INVALIDATED · 아무것도 안 씀 · 활성화 요청 0")
    void accept_firstAdmin_tenantNotPending_invalidated() throws Exception {
        Created c = createPending("B2B_ENTERPRISE");
        String account = UUID.randomUUID().toString();
        stubMatch(account);
        for (String s : List.of("SUSPENDED", "ACTIVE")) {
            stubTenantGet(c.slug(), s);
            accept(c.token(), account).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("OPERATOR_INVITATION_INVALIDATED"));
        }
        assertThat(count("SELECT COUNT(*) FROM admin_operators WHERE oidc_subject = ?", account)).isZero();
        assertThat(invitationStatus(c.invitationId())).isEqualTo("PENDING");
        assertThat(activationCalls(c.slug())).isZero();
    }

    @Test
    @DisplayName("대조군(AC-4 · S1-4): PENDING 테넌트로 가는 일반 초대는 발급되지만 수락에서 409 INVALIDATED · 아무것도 안 씀 · 활성화 요청 0")
    void control_ordinaryInvitationIntoPendingTenant_refusedAtAcceptance() throws Exception {
        String slug = slug();
        stubTenantGet(slug, "PENDING");
        String email = "ordinary." + slug + "@example.com";
        String invitationId = JSON.readTree(mockMvc.perform(post("/api/admin/operator-invitations")
                                .header("Authorization", bearer(SUPER_ADMIN_UUID))
                                .header("X-Operator-Reason", "s4-it")
                                .header("Idempotency-Key", UUID.randomUUID().toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"email":"%s","displayName":"일반","roles":["TENANT_ADMIN"],"tenantId":"%s"}
                                        """.formatted(email, slug)))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString())
                .get("invitationId").asText();
        String token = lastMailToken(slug);
        String account = UUID.randomUUID().toString();
        stubMatch(account);

        accept(token, account).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OPERATOR_INVITATION_INVALIDATED"));
        assertThat(count("SELECT COUNT(*) FROM admin_operators WHERE oidc_subject = ?", account)).isZero();
        assertThat(invitationStatus(invitationId)).isEqualTo("PENDING");
        assertThat(activationCalls(slug)).isZero();
    }

    @Test
    @DisplayName("🔴 일반 발급 API 는 첫 관리자 초대를 만들 수 없다: 본문 firstAdmin=true 는 무시 · item firstAdmin=false · 행 first_admin=FALSE")
    void ordinaryIssuance_cannotMintAFirstAdminInvitation() throws Exception {
        String slug = slug();
        stubTenantGet(slug, "PENDING");
        String body = mockMvc.perform(post("/api/admin/operator-invitations")
                        .header("Authorization", bearer(SUPER_ADMIN_UUID))
                        .header("X-Operator-Reason", "s4-it")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"x.%s@example.com","displayName":"X","roles":["TENANT_ADMIN"],"tenantId":"%s",
                                 "firstAdmin":true}
                                """.formatted(slug, slug)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.firstAdmin").value(false))
                .andReturn().getResponse().getContentAsString();
        String invitationId = JSON.readTree(body).get("invitationId").asText();
        assertThat(count("SELECT COUNT(*) FROM operator_invitation WHERE invitation_id = ? AND first_admin = FALSE",
                invitationId)).isEqualTo(1);
    }

    // ═══ PATCH — PENDING rules · 정리 · 재활성 ═══════════════════════════════════════

    @Test
    @DisplayName("🔴 PENDING(첫 관리자 미수락) → PATCH status=ACTIVE = 409 TENANT_FIRST_ADMIN_PENDING · account 활성화 요청 0 · 감사 0")
    void patch_pendingToActive_withoutAcceptedFirstAdmin_409() throws Exception {
        String slug = slug();
        stubTenantGet(slug, "PENDING");
        stubPatch(slug, "ACTIVE", 200, "ACTIVE");
        patchStatus(slug, "ACTIVE").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TENANT_FIRST_ADMIN_PENDING"));
        assertThat(activationCalls(slug)).isZero();
        assertThat(count("SELECT COUNT(*) FROM admin_actions WHERE target_id = ? AND action_code LIKE 'TENANT_%'", slug))
                .isZero();
    }

    @Test
    @DisplayName("대조군: PATCH status=PENDING → 400 VALIDATION_ERROR (대기는 POST 만 만든다)")
    void control_patchToPending_400() throws Exception {
        String slug = slug();
        stubTenantGet(slug, "ACTIVE");
        patchStatus(slug, "PENDING").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("🔴 정리(초대 행 없는 PENDING): PATCH SUSPENDED → 200 · tenant.suspended previousStatus=PENDING")
    void cleanup_pendingWithoutInvitation_eventSaysPreviousPending() throws Exception {
        String slug = slug();
        stubTenantGet(slug, "PENDING");
        stubPatch(slug, "SUSPENDED", 200, "SUSPENDED");
        patchStatus(slug, "SUSPENDED").andExpect(status().isOk());
        assertThat(outboxPayload("tenant.suspended", slug).at("/payload/previousStatus").asText())
                .isEqualTo("PENDING");
    }

    @Test
    @DisplayName("🔴 OD-2 정리: PENDING + 대기 첫 관리자 초대 → PATCH SUSPENDED = 초대 CANCELLED(cancelled_by=actor) + OPERATOR_INVITATION_CANCEL → account SUSPENDED → TENANT_SUSPEND + tenant.suspended(PENDING) · 옛 링크 404 · 그 뒤 PATCH ACTIVE = 409 TENANT_FIRST_ADMIN_PENDING")
    void cleanup_cancelsInvitationThenSuspends_andRefusesReactivation() throws Exception {
        Created c = createPending("B2B_ENTERPRISE");
        stubPatch(c.slug(), "SUSPENDED", 200, "SUSPENDED");

        patchStatus(c.slug(), "SUSPENDED").andExpect(status().isOk());

        Map<String, Object> inv = jdbc.queryForMap("""
                SELECT i.status, o.operator_id AS cancelled_by FROM operator_invitation i
                  JOIN admin_operators o ON o.id = i.cancelled_by WHERE i.invitation_id = ?""", c.invitationId());
        assertThat(inv.get("status")).isEqualTo("CANCELLED");
        assertThat(inv.get("cancelled_by")).isEqualTo(SUPER_ADMIN_UUID);
        assertThat(count("""
                SELECT COUNT(*) FROM admin_actions WHERE action_code = 'OPERATOR_INVITATION_CANCEL' AND target_id = ?""",
                c.invitationId())).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM admin_actions WHERE action_code = 'TENANT_SUSPEND' AND target_id = ? "
                + "AND outcome = 'SUCCESS'", c.slug())).isEqualTo(1);
        assertThat(outboxPayload("tenant.suspended", c.slug()).at("/payload/previousStatus").asText())
                .isEqualTo("PENDING");

        String account = UUID.randomUUID().toString();
        stubMatch(account);
        accept(c.token(), account).andExpect(status().isNotFound());

        // re-activation of a cleaned-up tenant (a first-admin row exists, none ACCEPTED) → 409
        stubTenantGet(c.slug(), "SUSPENDED");
        stubPatch(c.slug(), "ACTIVE", 200, "ACTIVE");
        patchStatus(c.slug(), "ACTIVE").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TENANT_FIRST_ADMIN_PENDING"));
        assertThat(activationCalls(c.slug())).isZero();
    }

    @Test
    @DisplayName("🔴 정리 순서: account 정지 실패 → 503 · 초대는 이미 CANCELLED · 테넌트 PENDING(요약 NONE) → 같은 PATCH 재시도 = 200 · tenant.suspended 한 번")
    void cleanup_accountFails_invitationAlreadyCancelled_retryContinues() throws Exception {
        Created c = createPending("B2B_ENTERPRISE");
        stubPatch(c.slug(), "SUSPENDED", 503, null);
        patchStatus(c.slug(), "SUSPENDED").andExpect(status().isServiceUnavailable());
        assertThat(invitationStatus(c.invitationId())).isEqualTo("CANCELLED");
        tenantGet(c.slug()).andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.firstAdmin.state").value("NONE"))
                .andExpect(jsonPath("$.firstAdmin.invitationId").value(nullValue()));

        stubPatch(c.slug(), "SUSPENDED", 200, "SUSPENDED");
        patchStatus(c.slug(), "SUSPENDED").andExpect(status().isOk());
        assertThat(count("SELECT COUNT(*) FROM admin_outbox WHERE event_type = 'tenant.suspended' AND partition_key = ?",
                c.slug())).isEqualTo(1);
        assertThat(count("""
                SELECT COUNT(*) FROM admin_actions WHERE action_code = 'OPERATOR_INVITATION_CANCEL' AND target_id = ?""",
                c.invitationId())).isEqualTo(1);
    }

    @Test
    @DisplayName("대조군: 첫 관리자 초대 행 없는 SUSPENDED 테넌트(773 이전 · 셀프 경로)의 PATCH ACTIVE 는 지금 그대로 200 · tenant.reactivated")
    void control_pre773SuspendedTenant_reactivates() throws Exception {
        String slug = slug();
        stubTenantGet(slug, "SUSPENDED");
        stubPatch(slug, "ACTIVE", 200, "ACTIVE");
        patchStatus(slug, "ACTIVE").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));
        assertThat(count("SELECT COUNT(*) FROM admin_outbox WHERE event_type = 'tenant.reactivated' AND partition_key = ?",
                slug)).isEqualTo(1);
    }

    // ═══ GET — firstAdmin summary ════════════════════════════════════════════════

    @Test
    @DisplayName("🔴 요약: 첫 관리자 초대 행 없는 테넌트 → 목록 · 단건 firstAdmin = null")
    void summary_tenantWithoutFirstAdminRows_isNull() throws Exception {
        String legacy = slug();
        stubTenantGet(legacy, "ACTIVE");
        stubList(List.of(tenantJson(legacy, "B2C_CONSUMER", "ACTIVE")));
        mockMvc.perform(get("/api/admin/tenants").header("Authorization", bearer(SUPER_ADMIN_UUID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].tenantId").value(legacy))
                .andExpect(jsonPath("$.items[0].firstAdmin", nullValue()));
        tenantGet(legacy).andExpect(status().isOk()).andExpect(jsonPath("$.firstAdmin", nullValue()));
    }

    @Test
    @DisplayName("🔴 요약(읽을 때 판정): INVITED · EXPIRED(만료 지남) · NONE(취소 — 테넌트는 PENDING 그대로, 정지 요청 0) · null(행 없음) — 목록 한 페이지")
    void summary_states_onList() throws Exception {
        Created invited = createPending("B2B_ENTERPRISE");
        Created expired = createPending("B2B_ENTERPRISE");
        Created none = createPending("B2C_CONSUMER");
        String legacy = slug();
        jdbc.update("UPDATE operator_invitation SET expires_at = NOW(6) - INTERVAL 1 MINUTE WHERE invitation_id = ?",
                expired.invitationId());
        mockMvc.perform(post("/api/admin/operator-invitations/" + none.invitationId() + ":cancel")
                        .header("Authorization", bearer(SUPER_ADMIN_UUID))
                        .header("X-Operator-Reason", "s4-it"))
                .andExpect(status().isOk());
        assertThat(wireMock.findAll(patchRequestedFor(urlPathEqualTo("/internal/tenants/" + none.slug()))))
                .as("cancelling the first-admin invitation does not suspend the tenant").isEmpty();

        stubList(List.of(
                tenantJson(invited.slug(), "B2B_ENTERPRISE", "PENDING"),
                tenantJson(expired.slug(), "B2B_ENTERPRISE", "PENDING"),
                tenantJson(none.slug(), "B2C_CONSUMER", "PENDING"),
                tenantJson(legacy, "B2B_ENTERPRISE", "ACTIVE")));
        mockMvc.perform(get("/api/admin/tenants").header("Authorization", bearer(SUPER_ADMIN_UUID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].firstAdmin.state").value("INVITED"))
                .andExpect(jsonPath("$.items[0].firstAdmin.invitationId").value(invited.invitationId()))
                .andExpect(jsonPath("$.items[1].firstAdmin.state").value("EXPIRED"))
                .andExpect(jsonPath("$.items[2].firstAdmin.state").value("NONE"))
                .andExpect(jsonPath("$.items[2].firstAdmin.email", nullValue()))
                .andExpect(jsonPath("$.items[3].firstAdmin", nullValue()));
    }

    // ═══ POST /api/admin/tenants/{tenantId}/first-admin-invitation ═══════════════════

    @Test
    @DisplayName("🔴 재초대: ACTIVE · SUSPENDED 테넌트 → 409 TENANT_NOT_PENDING · 아무것도 안 씀")
    void reinvite_notPending_409() throws Exception {
        for (String s : List.of("ACTIVE", "SUSPENDED")) {
            String slug = slug();
            stubTenantGet(slug, s);
            reinvite(SUPER_ADMIN_UUID, slug, "new." + slug + "@example.com", true)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("TENANT_NOT_PENDING"));
            assertThat(count("SELECT COUNT(*) FROM operator_invitation WHERE tenant_id = ?", slug)).isZero();
        }
    }

    @Test
    @DisplayName("🔴 재초대: 일반 운영자 403 · Idempotency-Key 누락 400 · 대기 첫 관리자 초대가 있으면 409 ALREADY_PENDING → :cancel 뒤 다른 주소 201(firstAdmin · 역할 둘 · 감사 permission_used=tenant.manage) · 요약 INVITED(새 초대)")
    void reinvite_flow() throws Exception {
        Created c = createPending("B2B_ENTERPRISE");
        String other = "second." + c.slug() + "@example.com";

        reinvite(REGULAR_OP_UUID, c.slug(), other, true).andExpect(status().isForbidden());
        reinvite(SUPER_ADMIN_UUID, c.slug(), other, false).andExpect(status().isBadRequest());
        reinvite(SUPER_ADMIN_UUID, c.slug(), other, true).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OPERATOR_INVITATION_ALREADY_PENDING"));

        mockMvc.perform(post("/api/admin/operator-invitations/" + c.invitationId() + ":cancel")
                        .header("Authorization", bearer(SUPER_ADMIN_UUID))
                        .header("X-Operator-Reason", "s4-it"))
                .andExpect(status().isOk());
        String body = reinvite(SUPER_ADMIN_UUID, c.slug(), other, true)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.firstAdmin").value(true))
                .andExpect(jsonPath("$.email").value(other))
                .andExpect(jsonPath("$.tenantId").value(c.slug()))
                .andReturn().getResponse().getContentAsString();
        String newId = JSON.readTree(body).get("invitationId").asText();
        List<String> roles = new ArrayList<>();
        JSON.readTree(body).get("roles").forEach(n -> roles.add(n.asText()));
        assertThat(roles).containsExactlyInAnyOrder("TENANT_ADMIN", "TENANT_BILLING_ADMIN");
        assertThat(jdbc.queryForObject("""
                SELECT permission_used FROM admin_actions WHERE action_code = 'OPERATOR_INVITATION_CREATE' AND target_id = ?""",
                String.class, newId)).isEqualTo("tenant.manage");
        assertThat(count("""
                SELECT COUNT(*) FROM operator_invitation WHERE tenant_id = ? AND first_admin = TRUE AND status = 'PENDING'""",
                c.slug())).isEqualTo(1);
        tenantGet(c.slug()).andExpect(jsonPath("$.firstAdmin.state").value("INVITED"))
                .andExpect(jsonPath("$.firstAdmin.invitationId").value(newId));
    }

    // ═══ S1 Q4 measurement — '*' operator · PENDING tenant ═══════════════════════════

    @Test
    @DisplayName("🔵 측정(S1 Q4): '*' 운영자의 PENDING 테넌트 assume — admin assignment-check 는 테넌트 상태를 읽지 않는다 → assigned=true (콘솔 스위처엔 안 뜬다 · 결정은 소유자)")
    void measure_starOperatorAssumesPendingTenant() throws Exception {
        String slug = slug();
        stubTenantGet(slug, "PENDING");
        String account = UUID.randomUUID().toString();
        String operatorId = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO admin_operators
                  (operator_id, tenant_id, email, password_hash, display_name, status, oidc_subject,
                   created_at, updated_at, version)
                VALUES (?, '*', ?, NULL, 'S4 star', 'ACTIVE', ?, NOW(6), NOW(6), 0)""",
                operatorId, operatorId + "@example.com", account);
        jdbc.update("""
                INSERT INTO admin_operator_roles (operator_id, role_id, tenant_id, granted_at, granted_by)
                SELECT o.id, r.id, '*', NOW(6), NULL FROM admin_operators o JOIN admin_roles r ON r.name = 'SUPER_ADMIN'
                 WHERE o.operator_id = ?""", operatorId);

        mockMvc.perform(get("/internal/operator-assignments/check")
                        .param("oidcSubject", account).param("tenantId", slug))
                .andExpect(status().isOk())
                // 🔵 a MEASUREMENT pin, not a requirement (admin-api § 전이 매트릭스 «읽는 곳» 표 ⚪ row): if the owner
                //    decides a PENDING tenant must not be assumable, this flips to false together with that change.
                .andExpect(jsonPath("$.assigned").value(true));
        assertThat(wireMock.findAll(WireMock.getRequestedFor(urlPathEqualTo("/internal/tenants/" + slug))))
                .as("the assume gate never reads the tenant's status").isEmpty();
    }

    // ═══ helpers ════════════════════════════════════════════════════════════════

    record Created(String slug, String invitationId, String token) {}

    private Created createPending(String tenantType) throws Exception {
        return createPending(tenantType, null);
    }

    /** POST with firstAdmin; asserts the S4 answer (PENDING · INVITED) and returns the slug, invitation and mailed token. */
    private Created createPending(String tenantType, String email) throws Exception {
        String slug = slug();
        stubCreate(slug, tenantType);
        stubTenantGet(slug, "PENDING");
        String address = email != null ? email : "owner." + slug + "@example.com";
        String body = createTenant(slug, tenantType, address, "첫 관리자")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.firstAdmin.state").value("INVITED"))
                .andReturn().getResponse().getContentAsString();
        String invitationId = JSON.readTree(body).at("/firstAdmin/invitationId").asText();
        return new Created(slug, invitationId, lastMailToken(slug));
    }

    private ResultActions createTenant(String slug, String tenantType, String email, String displayName) throws Exception {
        return mockMvc.perform(post("/api/admin/tenants")
                .header("Authorization", bearer(SUPER_ADMIN_UUID))
                .header("X-Operator-Reason", "s4-it")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(Map.of(
                        "tenantId", slug,
                        "displayName", "S4 " + slug,
                        "tenantType", tenantType,
                        "firstAdmin", Map.of("email", email, "displayName", displayName)))));
    }

    private ResultActions patchStatus(String slug, String status) throws Exception {
        return mockMvc.perform(patch("/api/admin/tenants/" + slug)
                .header("Authorization", bearer(SUPER_ADMIN_UUID))
                .header("X-Operator-Reason", "s4-it")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"" + status + "\"}"));
    }

    private ResultActions tenantGet(String slug) throws Exception {
        return mockMvc.perform(get("/api/admin/tenants/" + slug).header("Authorization", bearer(SUPER_ADMIN_UUID)));
    }

    private ResultActions reinvite(String actor, String slug, String email, boolean withKey) throws Exception {
        var req = post("/api/admin/tenants/" + slug + "/first-admin-invitation")
                .header("Authorization", bearer(actor))
                .header("X-Operator-Reason", "s4-it")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"displayName\":\"김대표\"}");
        if (withKey) {
            req = req.header("Idempotency-Key", UUID.randomUUID().toString());
        }
        return mockMvc.perform(req);
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

    private static String bearer(String operatorId) {
        return "Bearer " + jwt.operatorToken(operatorId);
    }

    private static String slug() {
        return "s4-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    private static String tenantJson(String slug, String type, String status) {
        return """
                {"tenantId":"%s","displayName":"S4 %s","tenantType":"%s","status":"%s",
                 "createdAt":"2026-10-15T00:00:00Z","updatedAt":"2026-10-15T00:00:00Z"}""".formatted(slug, slug, type, status);
    }

    /** S2's real behaviour: PENDING only when asked for it (initialStatus), ACTIVE otherwise. */
    private void stubCreate(String slug, String type) {
        wireMock.stubFor(WireMock.post(urlEqualTo("/internal/tenants")).atPriority(1)
                .withRequestBody(matchingJsonPath("$.tenantId", equalTo(slug)))
                .withRequestBody(matchingJsonPath("$.initialStatus", equalTo("PENDING")))
                .willReturn(aResponse().withStatus(201).withHeader("Content-Type", "application/json")
                        .withBody(tenantJson(slug, type, "PENDING"))));
        wireMock.stubFor(WireMock.post(urlEqualTo("/internal/tenants")).atPriority(2)
                .withRequestBody(matchingJsonPath("$.tenantId", equalTo(slug)))
                .willReturn(aResponse().withStatus(201).withHeader("Content-Type", "application/json")
                        .withBody(tenantJson(slug, type, "ACTIVE"))));
    }

    private void stubTenantGet(String slug, String status) {
        wireMock.stubFor(WireMock.get(urlPathEqualTo("/internal/tenants/" + slug))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody(tenantJson(slug, "B2B_ENTERPRISE", status))));
    }

    /** {@code PATCH /internal/tenants/{slug}} with {@code status = target} → {@code httpStatus} (and the tenant at {@code resulting}). */
    private void stubPatch(String slug, String target, int httpStatus, String resulting) {
        String body = httpStatus < 300
                ? tenantJson(slug, "B2B_ENTERPRISE", resulting)
                : "{\"code\":\"DOWNSTREAM_ERROR\",\"message\":\"s4 injected\"}";
        wireMock.stubFor(WireMock.patch(urlPathEqualTo("/internal/tenants/" + slug))
                .withRequestBody(matchingJsonPath("$.status", equalTo(target)))
                .willReturn(aResponse().withStatus(httpStatus).withHeader("Content-Type", "application/json")
                        .withBody(body)));
    }

    private void stubList(List<String> items) {
        wireMock.stubFor(WireMock.get(urlPathEqualTo("/internal/tenants"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"items\":[" + String.join(",", items) + "],\"page\":0,\"size\":20,"
                                + "\"totalElements\":" + items.size() + ",\"totalPages\":1}")));
    }

    private void stubMatch(String accountId) {
        wireMock.stubFor(WireMock.post(urlPathEqualTo("/internal/accounts/" + accountId + "/verified-email:match"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"accountId\":\"" + accountId + "\",\"emailVerifiedAt\":\"2026-10-15T00:00:00Z\"}")));
    }

    private int tenantCreateCalls(String slug) {
        return wireMock.findAll(postRequestedFor(urlEqualTo("/internal/tenants"))
                .withRequestBody(matchingJsonPath("$.tenantId", equalTo(slug)))).size();
    }

    private int activationCalls(String slug) {
        return wireMock.findAll(patchRequestedFor(urlPathEqualTo("/internal/tenants/" + slug))
                .withRequestBody(matchingJsonPath("$.status", equalTo("ACTIVE")))).size();
    }

    private List<LoggedRequest> mailsFor(String slug) {
        return wireMock.findAll(postRequestedFor(urlPathEqualTo(MAIL_PATH))
                .withRequestBody(matchingJsonPath("$.tenantId", equalTo(slug))));
    }

    private String lastMailToken(String slug) throws Exception {
        List<LoggedRequest> mails = mailsFor(slug);
        assertThat(mails).as("an invitation mail for %s", slug).isNotEmpty();
        return JSON.readTree(mails.get(mails.size() - 1).getBodyAsString()).get("token").asText();
    }

    private JsonNode outboxPayload(String eventType, String slug) throws Exception {
        List<String> rows = jdbc.queryForList(
                "SELECT payload FROM admin_outbox WHERE event_type = ? AND partition_key = ? ORDER BY created_at",
                String.class, eventType, slug);
        assertThat(rows).as("one %s outbox row for %s", eventType, slug).hasSize(1);
        return JSON.readTree(rows.get(0));
    }

    /** 🔴 ADR-044 D3 — every activation request for {@code slug} was made while an ACTIVE TENANT_ADMIN was committed. */
    private static void assertEveryActivationSawAnAdmin(String slug) {
        List<ActivationProbe.Observation> seen = ActivationProbe.SEEN.stream()
                .filter(o -> o.tenantId().equals(slug)).toList();
        assertThat(seen).as("the probe observed the activation request(s) of %s", slug).isNotEmpty();
        assertThat(seen).as("no PENDING → ACTIVE request while the tenant had zero ACTIVE TENANT_ADMIN")
                .allSatisfy(o -> assertThat(o.activeTenantAdmins()).isGreaterThanOrEqualTo(1));
    }

    static int activeTenantAdmins(JdbcTemplate jdbc, String tenantId) {
        Integer n = jdbc.queryForObject("""
                SELECT COUNT(DISTINCT o.id) FROM admin_operator_roles x
                  JOIN admin_roles r ON r.id = x.role_id JOIN admin_operators o ON o.id = x.operator_id
                 WHERE x.tenant_id = ? AND r.name = 'TENANT_ADMIN' AND o.status = 'ACTIVE'""", Integer.class, tenantId);
        return n == null ? 0 : n;
    }

    private int count(String sql, Object... args) {
        Integer n = jdbc.queryForObject(sql, Integer.class, args);
        return n == null ? 0 : n;
    }

    private Map<String, Object> column(String table, String column) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT IS_NULLABLE, COLUMN_DEFAULT, EXTRA, DATA_TYPE FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?""", table, column);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private void insertInvitation(String tenant, String email, boolean firstAdmin, String status, long inviter) {
        jdbc.update("""
                INSERT INTO operator_invitation
                  (invitation_id, tenant_id, email, display_name, roles, token_hash, status, expires_at, invited_by,
                   first_admin, created_at, updated_at, version)
                VALUES (?, ?, ?, 'S4', 'TENANT_ADMIN,TENANT_BILLING_ADMIN', ?, ?, NOW(6) + INTERVAL 7 DAY, ?, ?,
                        NOW(6), NOW(6), 0)""",
                UUID.randomUUID().toString(), tenant, email,
                (UUID.randomUUID().toString() + UUID.randomUUID()).replace("-", "").substring(0, 64),
                status, inviter, firstAdmin);
    }

    private String invitationStatus(String invitationId) {
        return jdbc.queryForObject("SELECT status FROM operator_invitation WHERE invitation_id = ?", String.class,
                invitationId);
    }

    private long internalId(String operatorId) {
        Long id = jdbc.queryForObject("SELECT id FROM admin_operators WHERE operator_id = ?", Long.class, operatorId);
        return id == null ? -1L : id;
    }

    private long internalIdBySubject(String accountId) {
        Long id = jdbc.queryForObject("SELECT id FROM admin_operators WHERE oidc_subject = ?", Long.class, accountId);
        return id == null ? -1L : id;
    }

    private List<String> roleNames(long id, String tenantId) {
        return jdbc.queryForList("""
                SELECT r.name FROM admin_operator_roles x JOIN admin_roles r ON r.id = x.role_id
                 WHERE x.operator_id = ? AND x.tenant_id = ? ORDER BY r.name""", String.class, id, tenantId);
    }

    private List<Map<String, Object>> originRows(long id) {
        return jdbc.queryForList("""
                SELECT g.tenant_id, s.system_key, g.granted_by, g.group_origin
                  FROM operator_domain_set_grant g JOIN domain_permission_set s ON s.id = g.set_id
                 WHERE g.operator_id = ? AND g.source_key = 'ORIGIN'""", id);
    }

    private Map<String, Object> operatorRow(long id) {
        return jdbc.queryForMap("""
                SELECT operator_id, tenant_id, email, display_name, status, oidc_subject, identity_id,
                       password_hash, updated_at, version
                  FROM admin_operators WHERE id = ?""", id);
    }

    /** An ACTIVE company operator linked to {@code accountId} owning (TENANT_ADMIN + membership) every given tenant. */
    private long seedFacetOwning(String accountId, String email, String home, String... more) {
        String operatorId = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO admin_operators
                  (operator_id, tenant_id, email, password_hash, display_name, status, oidc_subject,
                   created_at, updated_at, version)
                VALUES (?, ?, ?, NULL, 'S4 facet', 'ACTIVE', ?, NOW(6), NOW(6), 0)""",
                operatorId, home, email, accountId);
        long id = internalId(operatorId);
        List<String> owned = new ArrayList<>(List.of(home));
        owned.addAll(List.of(more));
        for (String t : owned) {
            jdbc.update("""
                    INSERT INTO admin_operator_roles (operator_id, role_id, tenant_id, granted_at, granted_by)
                    SELECT ?, r.id, ?, NOW(6), NULL FROM admin_roles r WHERE r.name = 'TENANT_ADMIN'""", id, t);
            jdbc.update("""
                    INSERT INTO operator_tenant_assignment (operator_id, tenant_id, granted_at, granted_by)
                    VALUES (?, ?, NOW(6), NULL)""", id, t);
        }
        return id;
    }

    private void seedSuperAdmin() {
        Integer existing = jdbc.queryForObject(
                "SELECT COUNT(*) FROM admin_operators WHERE operator_id = ?", Integer.class, SUPER_ADMIN_UUID);
        if (existing == null || existing == 0) {
            jdbc.update("""
                    INSERT INTO admin_operators
                      (operator_id, tenant_id, email, password_hash, display_name, status, created_at, updated_at, version)
                    VALUES (?, '*', ?, 'x', 'S4 Super Admin', 'ACTIVE', NOW(6), NOW(6), 0)""",
                    SUPER_ADMIN_UUID, "s4-superadmin@example.com");
        }
        jdbc.update("""
                INSERT IGNORE INTO admin_operator_roles (operator_id, role_id, tenant_id, granted_at, granted_by)
                SELECT o.id, r.id, '*', NOW(6), NULL FROM admin_operators o CROSS JOIN admin_roles r
                 WHERE o.operator_id = ? AND r.name = 'SUPER_ADMIN'""", SUPER_ADMIN_UUID);
    }

    private void seedRegularOperator() {
        Integer existing = jdbc.queryForObject(
                "SELECT COUNT(*) FROM admin_operators WHERE operator_id = ?", Integer.class, REGULAR_OP_UUID);
        if (existing == null || existing == 0) {
            jdbc.update("""
                    INSERT INTO admin_operators
                      (operator_id, tenant_id, email, password_hash, display_name, status, created_at, updated_at, version)
                    VALUES (?, 's4-regular-home', ?, 'x', 'S4 Regular', 'ACTIVE', NOW(6), NOW(6), 0)""",
                    REGULAR_OP_UUID, "s4-regular@example.com");
        }
    }

    /**
     * WireMock transformer — records, for every {@code PATCH /internal/tenants/{id}} asking {@code status = ACTIVE}, how
     * many ACTIVE operators held a {@code TENANT_ADMIN} role row in that tenant AT THAT MOMENT. It runs on WireMock's
     * thread with its own pooled connection, so it sees committed rows only — exactly what «관리자 먼저 · 활성화
     * 나중» (auth-to-admin.md § accept 8 → 9) promises.
     */
    static final class ActivationProbe implements ResponseDefinitionTransformerV2 {
        record Observation(String tenantId, int activeTenantAdmins) {}

        static volatile JdbcTemplate jdbc;
        static final List<Observation> SEEN = new CopyOnWriteArrayList<>();

        @Override
        public ResponseDefinition transform(ServeEvent serveEvent) {
            LoggedRequest r = serveEvent.getRequest();
            String prefix = "/internal/tenants/";
            JdbcTemplate db = jdbc;
            // equals, not ==: WireMock's RequestMethod.fromString returns a NEW instance per request (3.9.1), so an
            // identity check against the RequestMethod.PATCH constant never matched and the probe recorded nothing.
            if (db != null && RequestMethod.PATCH.equals(r.getMethod()) && r.getUrl().startsWith(prefix)) {
                try {
                    JsonNode body = JSON.readTree(r.getBodyAsString());
                    if (body != null && "ACTIVE".equals(body.path("status").asText())) {
                        String tenant = r.getUrl().substring(prefix.length());
                        int q = tenant.indexOf('?');
                        if (q >= 0) tenant = tenant.substring(0, q);
                        SEEN.add(new Observation(tenant, activeTenantAdmins(db, tenant)));
                    }
                } catch (Exception e) {
                    SEEN.add(new Observation("<probe-error:" + e.getClass().getSimpleName() + ">", -1));
                }
            }
            return serveEvent.getResponseDefinition();
        }

        @Override
        public String getName() {
            return "s4-activation-probe";
        }
    }
}
