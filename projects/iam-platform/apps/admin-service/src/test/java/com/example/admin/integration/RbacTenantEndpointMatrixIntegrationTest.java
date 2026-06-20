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
import java.time.Duration;
import java.util.Base64;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-BE-408 (AC-4): RBAC role × endpoint matrix integration test for the
 * tenant lifecycle surface, the table-driven verification rbac.md
 * §"Testing Expectations" requires.
 *
 * <p>Two actors gated against the {@code tenant.manage} permission:
 * <ul>
 *   <li>{@code superAdmin} — SUPER_ADMIN role (holds {@code tenant.manage} via the
 *       V0024 seed) → GET list / GET one / POST / PATCH all succeed.</li>
 *   <li>{@code supportOp} — SUPPORT_LOCK role (holds {@code audit.read} etc. but
 *       NOT {@code tenant.manage}) → every tenant endpoint is 403
 *       {@code PERMISSION_DENIED} and writes a DENIED {@code admin_actions} row
 *       carrying {@code permission_used='tenant.manage'}.</li>
 * </ul>
 *
 * <p>The DENY half is the load-bearing regression guard: an allow-only test
 * would not detect a dropped {@code @RequiresPermission(tenant.manage)}
 * annotation. Mirrors {@link AdminAuditTenantScopeIntegrationTest}'s harness
 * (operator JWT mint + {@code admin_operator_roles} DB seed + 403/200 assert +
 * DENIED audit row) plus a WireMock account-service stub for the allow path.
 *
 * <p>MySQL + Redis Testcontainers, {@code @Tag("integration")} — excluded from
 * the Docker-free default {@code :test} run; CI Linux is authoritative.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@Tag("integration")
class RbacTenantEndpointMatrixIntegrationTest extends AbstractIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)
            .withStartupTimeout(Duration.ofMinutes(3));

    static WireMockServer wireMock;
    static OperatorJwtTestFixture jwt;
    static String signingKeyPem;

    // Distinct UUID prefixes to avoid collision with other IT classes.
    //
    // CRITICAL: AbstractIntegrationTest shares ONE MySQL container across every
    // integration test class in the JVM and NEVER truncates between classes
    // (see its class javadoc). admin_operators.operator_id rows therefore persist
    // across classes, and each class's seedOperator only INSERTs the operator row
    // when absent (idempotent guard) — but always re-runs the role binding.
    //
    // The original ...081/...082 prefixes COLLIDED with
    // OperatorAdminScopeConfinementIntegrationTest, which seeds
    //   ...081 → SUPER_ADMIN  and  ...082 → SUPER_ADMIN (tenant-x).
    // That class runs first (alphabetical), so by the time this class ran, ...082
    // already carried a SUPER_ADMIN binding (which holds tenant.manage via V0024).
    // This class then ADDED a SUPPORT_LOCK binding on top; loadPermissions unions
    // BOTH roles → tenant.manage present → the RBAC aspect ALLOWED the supposed
    // SUPPORT_LOCK deny actor, the controller body ran, and the deny tests saw
    // TENANT_SCOPE_DENIED instead of PERMISSION_DENIED. Use a prefix no other IT
    // class touches so SUPPORT_OP carries ONLY SUPPORT_LOCK.
    static final String SUPER_ADMIN_UUID = "00000000-0000-7000-8000-0000000000E1";
    static final String SUPPORT_OP_UUID  = "00000000-0000-7000-8000-0000000000E2";

    @BeforeAll
    static void setupShared() throws IOException {
        jwt = new OperatorJwtTestFixture();
        java.security.PrivateKey pk = extractPrivateKey(jwt);
        signingKeyPem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(pk.getEncoded())
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
        // The tenant client fetches a client_credentials Bearer token (TASK-BE-318b).
        registry.add("iam.internal-client.token-uri", () -> wireMock.baseUrl() + "/oauth2/token");
    }

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;

    private static final String TENANT_RESPONSE = """
            {
              "tenantId": "matrix-test",
              "displayName": "Matrix Test",
              "tenantType": "B2B_ENTERPRISE",
              "status": "ACTIVE",
              "createdAt": "2026-06-20T09:00:00Z",
              "updatedAt": "2026-06-20T09:00:00Z"
            }
            """;

    @BeforeEach
    void resetAndSeed() {
        wireMock.resetAll();
        wireMock.stubFor(WireMock.post(WireMock.urlEqualTo("/oauth2/token"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"access_token\":\"test-jwt\",\"expires_in\":300,\"token_type\":\"Bearer\"}")));
        seedOperator(SUPER_ADMIN_UUID, "*", "matrix-super@example.com", "SUPER_ADMIN");
        seedOperator(SUPPORT_OP_UUID, "tenant-a", "matrix-support@example.com", "SUPPORT_LOCK");
    }

    // ── ALLOW half: SUPER_ADMIN holds tenant.manage ────────────────────────────

    @Test
    @DisplayName("[allow] SUPER_ADMIN GET /api/admin/tenants → 200")
    void superAdmin_listTenants_returns200() throws Exception {
        wireMock.stubFor(WireMock.get(urlPathEqualTo("/internal/tenants"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"items":[{"tenantId":"matrix-test","displayName":"Matrix Test",
                                  "tenantType":"B2B_ENTERPRISE","status":"ACTIVE",
                                  "createdAt":"2026-06-20T09:00:00Z","updatedAt":"2026-06-20T09:00:00Z"}],
                                 "page":0,"size":20,"totalElements":1,"totalPages":1}
                                """)));

        mockMvc.perform(get("/api/admin/tenants")
                        .header("Authorization", bearerToken(SUPER_ADMIN_UUID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("[allow] SUPER_ADMIN GET /api/admin/tenants/{id} → 200")
    void superAdmin_getTenant_returns200() throws Exception {
        wireMock.stubFor(WireMock.get(urlPathEqualTo("/internal/tenants/matrix-test"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(TENANT_RESPONSE)));

        mockMvc.perform(get("/api/admin/tenants/matrix-test")
                        .header("Authorization", bearerToken(SUPER_ADMIN_UUID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value("matrix-test"));
    }

    @Test
    @DisplayName("[allow] SUPER_ADMIN POST /api/admin/tenants → 201")
    void superAdmin_createTenant_returns201() throws Exception {
        wireMock.stubFor(WireMock.post(urlPathEqualTo("/internal/tenants"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody(TENANT_RESPONSE)));

        mockMvc.perform(post("/api/admin/tenants")
                        .header("Authorization", bearerToken(SUPER_ADMIN_UUID))
                        .header("X-Operator-Reason", "matrix allow case")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantId":"matrix-test","displayName":"Matrix Test","tenantType":"B2B_ENTERPRISE"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tenantId").value("matrix-test"));
    }

    // ── DENY half: SUPPORT_LOCK lacks tenant.manage (load-bearing) ──────────────

    @Test
    @DisplayName("[deny] SUPPORT_LOCK GET /api/admin/tenants → 403 PERMISSION_DENIED + DENIED audit row")
    void supportOp_listTenants_returns403_andDeniedAuditRow() throws Exception {
        int before = deniedRowCount(SUPPORT_OP_UUID);

        mockMvc.perform(get("/api/admin/tenants")
                        .header("Authorization", bearerToken(SUPPORT_OP_UUID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));

        assertDeniedTenantManageRow(SUPPORT_OP_UUID, before);
    }

    @Test
    @DisplayName("[deny] SUPPORT_LOCK GET /api/admin/tenants/{id} → 403 PERMISSION_DENIED + DENIED audit row")
    void supportOp_getTenant_returns403_andDeniedAuditRow() throws Exception {
        int before = deniedRowCount(SUPPORT_OP_UUID);

        mockMvc.perform(get("/api/admin/tenants/matrix-test")
                        .header("Authorization", bearerToken(SUPPORT_OP_UUID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));

        assertDeniedTenantManageRow(SUPPORT_OP_UUID, before);
    }

    @Test
    @DisplayName("[deny] SUPPORT_LOCK POST /api/admin/tenants → 403 PERMISSION_DENIED + DENIED audit row")
    void supportOp_createTenant_returns403_andDeniedAuditRow() throws Exception {
        int before = deniedRowCount(SUPPORT_OP_UUID);

        mockMvc.perform(post("/api/admin/tenants")
                        .header("Authorization", bearerToken(SUPPORT_OP_UUID))
                        .header("X-Operator-Reason", "matrix deny case")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantId":"matrix-test","displayName":"Matrix Test","tenantType":"B2B_ENTERPRISE"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));

        assertDeniedTenantManageRow(SUPPORT_OP_UUID, before);
    }

    @Test
    @DisplayName("[deny] SUPPORT_LOCK PATCH /api/admin/tenants/{id} → 403 PERMISSION_DENIED + DENIED audit row")
    void supportOp_patchTenant_returns403_andDeniedAuditRow() throws Exception {
        int before = deniedRowCount(SUPPORT_OP_UUID);

        mockMvc.perform(patch("/api/admin/tenants/matrix-test")
                        .header("Authorization", bearerToken(SUPPORT_OP_UUID))
                        .header("X-Operator-Reason", "matrix deny case")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"SUSPENDED"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));

        assertDeniedTenantManageRow(SUPPORT_OP_UUID, before);
    }

    // ── helpers ─────────────────────────────────────────────────────────────────

    private String bearerToken(String operatorUuid) {
        return "Bearer " + jwt.operatorToken(operatorUuid);
    }

    private int deniedRowCount(String operatorUuid) {
        Integer c = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM admin_actions WHERE outcome = 'DENIED' AND actor_id = ?",
                Integer.class, operatorUuid);
        return c == null ? 0 : c;
    }

    /**
     * Asserts exactly one new DENIED row was written for the operator and that the
     * most recent one records {@code permission_used='tenant.manage'} — proving the
     * RBAC aspect denied on the tenant.manage gate (not some other path).
     */
    private void assertDeniedTenantManageRow(String operatorUuid, int beforeCount) {
        assertThat(deniedRowCount(operatorUuid)).isEqualTo(beforeCount + 1);
        String permissionUsed = jdbcTemplate.queryForObject(
                "SELECT permission_used FROM admin_actions " +
                        "WHERE outcome = 'DENIED' AND actor_id = ? " +
                        "ORDER BY started_at DESC LIMIT 1",
                String.class, operatorUuid);
        assertThat(permissionUsed).isEqualTo("tenant.manage");
    }

    /**
     * Deterministic seed: inserts the operator (idempotent) then binds EXACTLY the
     * given role. The role set is reset first so the operator carries ONLY
     * {@code roleName} — defense-in-depth against the cross-class operator-row
     * persistence described on {@link #SUPPORT_OP_UUID} (the shared, un-truncated
     * MySQL means a UUID reused by another class could otherwise leave a stray
     * binding that contaminates this class's permission union).
     */
    private void seedOperator(String uuid, String tenantId, String email, String roleName) {
        Integer existing = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM admin_operators WHERE operator_id = ?",
                Integer.class, uuid);
        if (existing == null || existing == 0) {
            jdbcTemplate.update("""
                    INSERT INTO admin_operators
                      (operator_id, tenant_id, email, password_hash, display_name, status,
                       created_at, updated_at, version)
                    VALUES (?, ?, ?, 'x', ?, 'ACTIVE', NOW(6), NOW(6), 0)
                    """,
                    uuid, tenantId, email, "Matrix Op");
        }
        // Reset role bindings for this operator so the actor carries EXACTLY one
        // role — guarantees the SUPPORT_LOCK deny actor never accidentally unions a
        // tenant.manage-bearing role left over from a prior class on the same UUID.
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
