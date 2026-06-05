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
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-BE-307 — end-to-end integration test for
 * {@code PATCH /api/admin/operators/{operatorId}/profile} (admin-on-behalf-of).
 *
 * <p>Sister of {@link OperatorProfileIntegrationTest} (BE-306 self-serve).
 * Boots admin-service against real MySQL + Kafka (Testcontainers) + Redis,
 * with a WireMock stub replacing account-service's internal tenant endpoints
 * (used by the {@link AdminActionAuditor#resolveOperator} lookup). Verifies:
 * <ul>
 *   <li>IT-1: platform-scope SUPER_ADMIN (tenant='*') sets a cross-tenant
 *       target → 204; column landed; audit row has {@code actor_id = caller},
 *       {@code target_id != caller}, {@code permission_used="operator.manage"},
 *       {@code reason} = caller-typed string (NOT {@code <self_profile_update>}).</li>
 *   <li>IT-2: same-tenant SUPER_ADMIN sets a same-tenant target → 204; column
 *       landed (verifies non-platform scope still works within tenant).</li>
 *   <li>IT-3: cross-tenant non-platform caller → 403 TENANT_SCOPE_DENIED;
 *       column unchanged; no audit row.</li>
 *   <li>IT-4: self via admin path (caller targets own operator_id) → 400
 *       SELF_PROFILE_UPDATE_FORBIDDEN_VIA_ADMIN_PATH; column unchanged.</li>
 *   <li>IT-5: missing X-Operator-Reason → 400 REASON_REQUIRED; column
 *       unchanged.</li>
 * </ul>
 *
 * <p>Skipped automatically when Docker is unavailable
 * ({@code AbstractIntegrationTest} DockerAvailableCondition).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class OperatorProfileAdminIntegrationTest extends AbstractIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    static WireMockServer wireMock;
    static OperatorJwtTestFixture jwt;
    static String signingKeyPem;

    // Per-test caller + target UUIDs — hermetic by construction (admin_actions
    // is append-only; V0010 trigger rejects DELETE). Mirror of BE-306
    // OperatorProfileIntegrationTest pattern.
    private static final String CALLER_IT1 = "00000000-0000-7000-8000-00000be07101";
    private static final String TARGET_IT1 = "00000000-0000-7000-8000-00000be07102";
    private static final String CALLER_IT2 = "00000000-0000-7000-8000-00000be07201";
    private static final String TARGET_IT2 = "00000000-0000-7000-8000-00000be07202";
    private static final String CALLER_IT3 = "00000000-0000-7000-8000-00000be07301";
    private static final String TARGET_IT3 = "00000000-0000-7000-8000-00000be07302";
    private static final String CALLER_IT4 = "00000000-0000-7000-8000-00000be07401";
    private static final String CALLER_IT5 = "00000000-0000-7000-8000-00000be07501";
    private static final String TARGET_IT5 = "00000000-0000-7000-8000-00000be07502";

    @BeforeAll
    static void setupShared() throws IOException {
        jwt = new OperatorJwtTestFixture();

        java.security.PrivateKey pk = extractPrivateKey(jwt);
        signingKeyPem = "-----BEGIN PRIVATE KEY-----\n"
                + java.util.Base64.getMimeEncoder(64, "\n".getBytes())
                        .encodeToString(pk.getEncoded())
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
            var field = OperatorJwtTestFixture.class.getDeclaredField("keyPair");
            field.setAccessible(true);
            java.security.KeyPair kp = (java.security.KeyPair) field.get(fixture);
            return kp.getPrivate();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("admin.jwt.active-signing-kid", () -> "test-key-001");
        registry.add("admin.jwt.signing-keys.test-key-001", () -> signingKeyPem);
        registry.add("admin.jwt.issuer", () -> "admin-service");
        registry.add("admin.jwt.expected-token-type", () -> "admin");
        registry.add("admin.auth-service.base-url", wireMock::baseUrl);
        registry.add("admin.account-service.base-url", wireMock::baseUrl);
        registry.add("admin.security-service.base-url", wireMock::baseUrl);
        // TASK-BE-318b: GAP client_credentials token endpoint → same WireMock (stubbed in stubTenantList).
        registry.add("iam.internal-client.token-uri", () -> wireMock.baseUrl() + "/oauth2/token");
    }

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;

    /** Minimal tenant list — the registry round-trip is not used in BE-307 ITs. */
    private static final String EMPTY_TENANTS = """
            {"items": [], "page":0, "size":100, "totalElements":0, "totalPages":0}
            """;

    @BeforeEach
    void stubTenantList() {
        wireMock.resetAll();
        // TASK-BE-318b: account client fetches a GAP client_credentials Bearer token first.
        wireMock.stubFor(WireMock.post(WireMock.urlEqualTo("/oauth2/token"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"access_token\":\"test-jwt\",\"expires_in\":300,\"token_type\":\"Bearer\"}")));
        wireMock.stubFor(WireMock.get(urlPathEqualTo("/internal/tenants"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(EMPTY_TENANTS)));
    }

    /**
     * Seed an operator with a specific tenant. Idempotent — if the row exists,
     * resets {@code finance_default_account_id} to NULL and the tenant to the
     * requested value so each test starts from a known baseline.
     * Operators seeded as SUPER_ADMIN by binding the role separately. We DON'T
     * bind the role here for simplicity — the {@code RequiresPermissionAspect}
     * uses {@code PermissionEvaluator} which checks the role binding via
     * SQL, so we instead grant the role on first seed.
     */
    private void seedOperator(String operatorUuid, String tenantId) {
        Integer existing = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM admin_operators WHERE operator_id = ?",
                Integer.class, operatorUuid);
        if (existing == null || existing == 0) {
            jdbcTemplate.update("""
                    INSERT INTO admin_operators
                      (operator_id, tenant_id, email, password_hash, display_name, status,
                       created_at, updated_at, version)
                    VALUES (?, ?, ?, 'x', ?, 'ACTIVE', NOW(6), NOW(6), 0)
                    """, operatorUuid, tenantId, operatorUuid + "@example.com",
                    "Op " + operatorUuid.substring(operatorUuid.length() - 6));
        } else {
            jdbcTemplate.update("""
                    UPDATE admin_operators
                       SET finance_default_account_id = NULL, tenant_id = ?
                     WHERE operator_id = ?
                    """, tenantId, operatorUuid);
        }
    }

    /** Bind SUPER_ADMIN role to the operator (idempotent). */
    private void grantSuperAdmin(String operatorUuid) {
        Long operatorPk = jdbcTemplate.queryForObject(
                "SELECT id FROM admin_operators WHERE operator_id = ?",
                Long.class, operatorUuid);
        Long roleId = jdbcTemplate.queryForObject(
                "SELECT id FROM admin_roles WHERE name = 'SUPER_ADMIN'",
                Long.class);
        String tenantId = jdbcTemplate.queryForObject(
                "SELECT tenant_id FROM admin_operators WHERE id = ?",
                String.class, operatorPk);

        Integer existing = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM admin_operator_roles WHERE operator_id = ? AND role_id = ?",
                Integer.class, operatorPk, roleId);
        if (existing == null || existing == 0) {
            jdbcTemplate.update("""
                    INSERT INTO admin_operator_roles
                      (operator_id, role_id, granted_at, granted_by, tenant_id)
                    VALUES (?, ?, NOW(6), NULL, ?)
                    """, operatorPk, roleId, tenantId);
        }
    }

    private String token(String operatorUuid) {
        return "Bearer " + jwt.operatorToken(operatorUuid);
    }

    // -----------------------------------------------------------------------

    @Test
    @DisplayName("IT-1 platform-scope caller sets cross-tenant target → 204; audit row carries operator.manage + caller-typed reason; target_id != caller")
    void it1_platformScopeAdmin_setsCrossTenantTarget_writesColumnAndAuditRow() throws Exception {
        seedOperator(CALLER_IT1, "*");
        grantSuperAdmin(CALLER_IT1);
        seedOperator(TARGET_IT1, "wms");

        String newValue = "01928c4a-7e9f-7c00-9a40-d2b1f5e8a307";
        String reason = "onboarding bulk-provision";

        mockMvc.perform(patch("/api/admin/operators/{operatorId}/profile", TARGET_IT1)
                        .header("Authorization", token(CALLER_IT1))
                        .header("X-Operator-Reason", reason)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"operatorContext":{"defaultAccountId":"%s"}}
                                """.formatted(newValue)))
                .andExpect(status().isNoContent());

        // Column UPDATE landed on TARGET, not on CALLER
        String targetPersisted = jdbcTemplate.queryForObject(
                "SELECT finance_default_account_id FROM admin_operators WHERE operator_id = ?",
                String.class, TARGET_IT1);
        assertThat(targetPersisted).isEqualTo(newValue);

        String callerPersisted = jdbcTemplate.queryForObject(
                "SELECT finance_default_account_id FROM admin_operators WHERE operator_id = ?",
                String.class, CALLER_IT1);
        assertThat(callerPersisted)
                .as("caller's column MUST remain untouched — the admin path mutates the TARGET only")
                .isNull();

        // Audit row: actor_id = caller, target_id = TARGET, permission = operator.manage,
        // reason = caller-typed string (NOT <self_profile_update>).
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT action_code, actor_id, target_type, target_id, outcome, " +
                "       downstream_detail, reason, permission_used " +
                "FROM admin_actions WHERE actor_id = ? AND action_code = 'OPERATOR_PROFILE_UPDATE'",
                CALLER_IT1);
        assertThat(rows)
                .as("exactly one OPERATOR_PROFILE_UPDATE row must exist for the IT-1 caller")
                .hasSize(1);
        Map<String, Object> row = rows.get(0);
        assertThat(row.get("action_code")).isEqualTo("OPERATOR_PROFILE_UPDATE");
        assertThat(row.get("actor_id")).isEqualTo(CALLER_IT1);
        assertThat(row.get("target_type")).isEqualTo("OPERATOR");
        assertThat(row.get("target_id"))
                .as("audit target_id MUST be the TARGET (not the caller)")
                .isEqualTo(TARGET_IT1)
                .isNotEqualTo(CALLER_IT1);
        assertThat(row.get("outcome")).isEqualTo("SUCCESS");
        assertThat(row.get("downstream_detail"))
                .as("audit detail MUST be null — the new value is NOT logged (R4/A3)")
                .isNull();
        assertThat(row.get("reason"))
                .as("admin path uses caller-typed X-Operator-Reason, NOT the <self_profile_update> constant")
                .isEqualTo(reason);
        assertThat(row.get("permission_used"))
                .as("admin path stamps the concrete grantable operator.manage permission, NOT the <self_action> sentinel")
                .isEqualTo("operator.manage");
    }

    @Test
    @DisplayName("IT-2 same-tenant SUPER_ADMIN sets same-tenant target → 204")
    void it2_sameTenantAdmin_setsSameTenantTarget_writesColumn() throws Exception {
        seedOperator(CALLER_IT2, "wms");
        grantSuperAdmin(CALLER_IT2);
        seedOperator(TARGET_IT2, "wms");

        String newValue = "01928c4a-7e9f-7c00-9a40-d2b1f5e8a999";
        mockMvc.perform(patch("/api/admin/operators/{operatorId}/profile", TARGET_IT2)
                        .header("Authorization", token(CALLER_IT2))
                        .header("X-Operator-Reason", "same-tenant correction")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"operatorContext":{"defaultAccountId":"%s"}}
                                """.formatted(newValue)))
                .andExpect(status().isNoContent());

        String targetPersisted = jdbcTemplate.queryForObject(
                "SELECT finance_default_account_id FROM admin_operators WHERE operator_id = ?",
                String.class, TARGET_IT2);
        assertThat(targetPersisted).isEqualTo(newValue);
    }

    @Test
    @DisplayName("IT-3 cross-tenant non-platform caller → 403 TENANT_SCOPE_DENIED; column unchanged")
    void it3_crossTenantNonPlatform_returns403_columnUnchanged() throws Exception {
        seedOperator(CALLER_IT3, "wms");
        grantSuperAdmin(CALLER_IT3);
        seedOperator(TARGET_IT3, "scm");

        mockMvc.perform(patch("/api/admin/operators/{operatorId}/profile", TARGET_IT3)
                        .header("Authorization", token(CALLER_IT3))
                        .header("X-Operator-Reason", "attempted cross-tenant write")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"operatorContext":{"defaultAccountId":"01928c4a-7e9f-7c00-9a40-d2b1f5e8a000"}}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_SCOPE_DENIED"));

        // Column unchanged
        String persisted = jdbcTemplate.queryForObject(
                "SELECT finance_default_account_id FROM admin_operators WHERE operator_id = ?",
                String.class, TARGET_IT3);
        assertThat(persisted)
                .as("cross-tenant denied write MUST NOT mutate the target column")
                .isNull();

        // No SUCCESS OPERATOR_PROFILE_UPDATE row inserted for this caller
        // (DENIED rows may be present from recordCrossTenantDenied, but the
        // SUCCESS row must NOT exist).
        Integer successCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM admin_actions WHERE actor_id = ? " +
                "AND action_code = 'OPERATOR_PROFILE_UPDATE' AND outcome = 'SUCCESS'",
                Integer.class, CALLER_IT3);
        assertThat(successCount).isZero();
    }

    @Test
    @DisplayName("IT-4 self via admin path → 400 SELF_PROFILE_UPDATE_FORBIDDEN_VIA_ADMIN_PATH; column unchanged")
    void it4_selfViaAdminPath_returns400_columnUnchanged() throws Exception {
        seedOperator(CALLER_IT4, "*");
        grantSuperAdmin(CALLER_IT4);

        mockMvc.perform(patch("/api/admin/operators/{operatorId}/profile", CALLER_IT4)
                        .header("Authorization", token(CALLER_IT4))
                        .header("X-Operator-Reason", "trying to self-edit via admin path")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"operatorContext":{"defaultAccountId":"01928c4a-7e9f-7c00-9a40-d2b1f5e8a000"}}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SELF_PROFILE_UPDATE_FORBIDDEN_VIA_ADMIN_PATH"));

        String persisted = jdbcTemplate.queryForObject(
                "SELECT finance_default_account_id FROM admin_operators WHERE operator_id = ?",
                String.class, CALLER_IT4);
        assertThat(persisted)
                .as("self-via-admin rejection MUST NOT mutate the caller's own column")
                .isNull();
    }

    @Test
    @DisplayName("IT-5 missing X-Operator-Reason → 400 REASON_REQUIRED; column unchanged")
    void it5_missingReason_returns400_columnUnchanged() throws Exception {
        seedOperator(CALLER_IT5, "*");
        grantSuperAdmin(CALLER_IT5);
        seedOperator(TARGET_IT5, "wms");

        mockMvc.perform(patch("/api/admin/operators/{operatorId}/profile", TARGET_IT5)
                        .header("Authorization", token(CALLER_IT5))
                        // NO X-Operator-Reason
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"operatorContext":{"defaultAccountId":"01928c4a-7e9f-7c00-9a40-d2b1f5e8a000"}}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REASON_REQUIRED"));

        String persisted = jdbcTemplate.queryForObject(
                "SELECT finance_default_account_id FROM admin_operators WHERE operator_id = ?",
                String.class, TARGET_IT5);
        assertThat(persisted)
                .as("reason-required rejection MUST NOT mutate the target column")
                .isNull();
    }
}
