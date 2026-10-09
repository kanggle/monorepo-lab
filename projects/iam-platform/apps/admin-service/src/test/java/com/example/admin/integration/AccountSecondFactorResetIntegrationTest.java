package com.example.admin.integration;

import com.example.admin.support.OperatorJwtTestFixture;
import com.example.testsupport.integration.AbstractIntegrationTest;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-MONO-771 S6 (OD-6) — {@code POST /api/admin/accounts/{accountId}/2fa/reset} against real MySQL (the REAL
 * V0049 seed and the real {@code PermissionEvaluator}) + a WireMock auth-service.
 *
 * <p>The permission CONTROL GROUP is the point of this class: the slice test mocks the evaluator, so only here does
 * «which seed role holds the key» meet the endpoint.
 * <ul>
 *   <li>V0049: {@code account.2fa_reset} is held by exactly {@code SUPER_ADMIN} and {@code SECURITY_ANALYST};</li>
 *   <li>{@code SUPER_ADMIN} ('*') and {@code SECURITY_ANALYST} ('*') → 200, auth-service called once, SUCCESS row;</li>
 *   <li>{@code TENANT_ADMIN} · {@code SUPPORT_READONLY} · {@code SUPPORT_LOCK} · {@code TENANT_BILLING_ADMIN} →
 *       403 {@code PERMISSION_DENIED}, auth-service never called, DENIED row with the key;</li>
 *   <li>a {@code SECURITY_ANALYST} whose home is a customer tenant → 403 {@code TENANT_SCOPE_DENIED} (the inline
 *       platform-scope gate), auth-service never called;</li>
 *   <li>auth-service 404 {@code TOTP_NOT_ENROLLED} → 404 {@code TOTP_NOT_ENROLLED} + FAILURE row.</li>
 * </ul>
 *
 * <p>Skipped when Docker is unavailable (AbstractIntegrationTest) — CI Linux is authoritative.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@Tag("integration")
// Own WireMock @DynamicPropertySource ⇒ a context nobody shares; close it after the class (S5's heap-pressure note
// on TenantEntryPolicyIntegrationTest).
@org.springframework.test.annotation.DirtiesContext(
        classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class AccountSecondFactorResetIntegrationTest extends AbstractIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    static WireMockServer wireMock;
    static OperatorJwtTestFixture jwt;
    static String adminSigningKeyPem;

    private static final String TARGET_ACCOUNT = "0199de70-0000-7000-8000-0000000c5b99";
    private static final String RESET_PATH = "/internal/auth/accounts/" + TARGET_ACCOUNT + "/second-factor/reset";

    // One operator per role so the perm-cache and role unions never mix (RbacTenantEndpointMatrixIntegrationTest note).
    private static final String SUPER_ADMIN_UUID = "00000000-0000-7000-8000-0000000c5b01";
    private static final String ANALYST_UUID = "00000000-0000-7000-8000-0000000c5b02";
    private static final String TENANT_ANALYST_UUID = "00000000-0000-7000-8000-0000000c5b03";
    private static final String TENANT_ADMIN_UUID = "00000000-0000-7000-8000-0000000c5b04";
    private static final String SUPPORT_READONLY_UUID = "00000000-0000-7000-8000-0000000c5b05";
    private static final String SUPPORT_LOCK_UUID = "00000000-0000-7000-8000-0000000c5b06";
    private static final String BILLING_ADMIN_UUID = "00000000-0000-7000-8000-0000000c5b07";

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
        wireMock.stubFor(WireMock.post(urlPathEqualTo(RESET_PATH))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"accountId\":\"" + TARGET_ACCOUNT
                                + "\",\"resetAt\":\"2026-10-09T03:00:00Z\",\"wasConfirmed\":true}")));

        seedOperator(SUPER_ADMIN_UUID, "*", "SUPER_ADMIN");
        seedOperator(ANALYST_UUID, "*", "SECURITY_ANALYST");
        seedOperator(TENANT_ANALYST_UUID, "s6-tenant", "SECURITY_ANALYST");
        seedOperator(TENANT_ADMIN_UUID, "s6-tenant", "TENANT_ADMIN");
        seedOperator(SUPPORT_READONLY_UUID, "*", "SUPPORT_READONLY");
        seedOperator(SUPPORT_LOCK_UUID, "*", "SUPPORT_LOCK");
        seedOperator(BILLING_ADMIN_UUID, "s6-tenant", "TENANT_BILLING_ADMIN");
    }

    private ResultActions reset(String operatorUuid) throws Exception {
        return mockMvc.perform(post("/api/admin/accounts/" + TARGET_ACCOUNT + "/2fa/reset")
                .header("Authorization", "Bearer " + jwt.operatorToken(operatorUuid))
                .header("X-Operator-Reason", "s6-it")
                .header("Idempotency-Key", "s6-" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"본인 확인: 신분증 대조\",\"ticketId\":\"S6-IT\"}"));
    }

    @Test
    @DisplayName("V0049: account.2fa_reset 보유 역할 = 정확히 SUPER_ADMIN · SECURITY_ANALYST (OD-6)")
    void seed_holders() {
        List<String> holders = jdbcTemplate.queryForList("""
                SELECT r.name FROM admin_role_permissions p JOIN admin_roles r ON r.id = p.role_id
                 WHERE p.permission_key = 'account.2fa_reset' ORDER BY r.name
                """, String.class);
        assertThat(holders).containsExactly("SECURITY_ANALYST", "SUPER_ADMIN");
    }

    @ParameterizedTest(name = "[allow] {0}")
    @ValueSource(strings = {SUPER_ADMIN_UUID, ANALYST_UUID})
    @DisplayName("[allow] 플랫폼 SUPER_ADMIN · SECURITY_ANALYST → 200 · auth 1회 호출 · SUCCESS 행")
    void platformRoles_allowed(String operatorUuid) throws Exception {
        reset(operatorUuid)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(TARGET_ACCOUNT))
                .andExpect(jsonPath("$.operatorId").value(operatorUuid))
                .andExpect(jsonPath("$.auditId").isNotEmpty());

        wireMock.verify(1, postRequestedFor(urlPathEqualTo(RESET_PATH))
                .withHeader("X-Operator-ID", equalTo(operatorUuid)));
        assertThat(jdbcTemplate.queryForObject("""
                SELECT downstream_detail FROM admin_actions
                 WHERE actor_id = ? AND action_code = 'ACCOUNT_2FA_RESET' AND outcome = 'SUCCESS'
                   AND target_type = 'ACCOUNT' AND target_id = ? AND permission_used = 'account.2fa_reset'
                 ORDER BY started_at DESC LIMIT 1
                """, String.class, operatorUuid, TARGET_ACCOUNT)).isEqualTo("wasConfirmed=true");
    }

    @ParameterizedTest(name = "[deny] {0}")
    @ValueSource(strings = {TENANT_ADMIN_UUID, SUPPORT_READONLY_UUID, SUPPORT_LOCK_UUID, BILLING_ADMIN_UUID})
    @DisplayName("🔴 [deny] TENANT_ADMIN · SUPPORT_READONLY · SUPPORT_LOCK · TENANT_BILLING_ADMIN → 403 PERMISSION_DENIED · auth 0회 · DENIED 행")
    void otherRoles_denied(String operatorUuid) throws Exception {
        int before = deniedRows(operatorUuid);

        reset(operatorUuid)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));

        wireMock.verify(0, postRequestedFor(urlPathEqualTo(RESET_PATH)));
        assertThat(deniedRows(operatorUuid)).isEqualTo(before + 1);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT permission_used FROM admin_actions
                 WHERE outcome = 'DENIED' AND actor_id = ? ORDER BY started_at DESC LIMIT 1
                """, String.class, operatorUuid)).isEqualTo("account.2fa_reset");
    }

    @Test
    @DisplayName("🔴 키는 있으나 홈이 고객 테넌트인 SECURITY_ANALYST → 403 TENANT_SCOPE_DENIED · auth 0회")
    void tenantHomedAnalyst_scopeDenied() throws Exception {
        reset(TENANT_ANALYST_UUID)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_SCOPE_DENIED"));

        wireMock.verify(0, postRequestedFor(urlPathEqualTo(RESET_PATH)));
    }

    @Test
    @DisplayName("auth 404 TOTP_NOT_ENROLLED → 404 TOTP_NOT_ENROLLED · FAILURE 행")
    void notEnrolled_404_failureRow() throws Exception {
        wireMock.stubFor(WireMock.post(urlPathEqualTo(RESET_PATH))
                .willReturn(aResponse().withStatus(404).withHeader("Content-Type", "application/json")
                        .withBody("{\"code\":\"TOTP_NOT_ENROLLED\",\"message\":\"none\"}")));

        reset(SUPER_ADMIN_UUID)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TOTP_NOT_ENROLLED"));

        assertThat(jdbcTemplate.queryForObject("""
                SELECT downstream_detail FROM admin_actions
                 WHERE actor_id = ? AND action_code = 'ACCOUNT_2FA_RESET' AND outcome = 'FAILURE'
                 ORDER BY started_at DESC LIMIT 1
                """, String.class, SUPER_ADMIN_UUID)).isEqualTo("TOTP_NOT_ENROLLED");
    }

    private int deniedRows(String operatorUuid) {
        Integer c = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM admin_actions WHERE outcome = 'DENIED' AND actor_id = ?",
                Integer.class, operatorUuid);
        return c == null ? 0 : c;
    }

    /** Inserts the operator (idempotent) and binds EXACTLY {@code roleName}, granted at the operator's home tenant. */
    private void seedOperator(String uuid, String tenantId, String roleName) {
        Integer existing = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM admin_operators WHERE operator_id = ?", Integer.class, uuid);
        if (existing == null || existing == 0) {
            jdbcTemplate.update("""
                    INSERT INTO admin_operators
                      (operator_id, tenant_id, email, password_hash, display_name,
                       status, created_at, updated_at, version)
                    VALUES (?, ?, ?, NULL, ?, 'ACTIVE', NOW(6), NOW(6), 0)
                    """, uuid, tenantId, uuid + "@example.com", "S6 " + roleName);
        }
        jdbcTemplate.update("""
                DELETE ar FROM admin_operator_roles ar
                  JOIN admin_operators o ON o.id = ar.operator_id
                 WHERE o.operator_id = ?
                """, uuid);
        jdbcTemplate.update("""
                INSERT IGNORE INTO admin_operator_roles (operator_id, role_id, tenant_id, granted_at, granted_by)
                SELECT o.id, r.id, o.tenant_id, NOW(6), NULL
                  FROM admin_operators o CROSS JOIN admin_roles r
                 WHERE o.operator_id = ? AND r.name = ?
                """, uuid, roleName);
    }
}
