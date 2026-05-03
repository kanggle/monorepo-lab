package com.example.admin.integration;

import com.example.admin.support.OperatorJwtTestFixture;
import com.example.testsupport.integration.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-BE-249 integration test for tenant-scope enforcement on the audit endpoint.
 *
 * <p>Three actors:
 * <ul>
 *   <li>{@code tenantA_op} — operator in "tenant-a"</li>
 *   <li>{@code tenantB_op} — operator in "tenant-b"</li>
 *   <li>{@code superAdmin} — SUPER_ADMIN with tenantId='*' (platform-scope)</li>
 * </ul>
 *
 * <p>Scenarios verified:
 * <ol>
 *   <li>tenantA operator queries {@code GET /api/admin/audit?tenantId=tenant-b} → 403 TENANT_SCOPE_DENIED</li>
 *   <li>SUPER_ADMIN queries {@code GET /api/admin/audit?tenantId=*} → 200 (platform-scope rows)</li>
 *   <li>tenantA operator queries own tenant (no tenantId param) → 200</li>
 * </ol>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@Tag("integration")
class AdminAuditTenantScopeIntegrationTest extends AbstractIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)
            .withStartupTimeout(Duration.ofMinutes(3));

    static OperatorJwtTestFixture jwt;
    static String signingKeyPem;

    // UUIDs for each test actor — use distinct prefixes to avoid collision with other IT classes
    static final String TENANT_A_OP_UUID = "00000000-0000-7000-8000-000000000071";
    static final String TENANT_B_OP_UUID = "00000000-0000-7000-8000-000000000072";
    static final String SUPER_ADMIN_UUID  = "00000000-0000-7000-8000-000000000073";

    @BeforeAll
    static void setupShared() throws IOException {
        jwt = new OperatorJwtTestFixture();
        java.security.PrivateKey pk = extractPrivateKey(jwt);
        signingKeyPem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(pk.getEncoded())
                + "\n-----END PRIVATE KEY-----\n";
    }

    @AfterAll
    static void tearDownShared() {
        // containers managed by @Testcontainers
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
        registry.add("admin.auth-service.base-url", () -> "http://localhost:18085");
        registry.add("admin.account-service.base-url", () -> "http://localhost:18085");
        registry.add("admin.security-service.base-url", () -> "http://localhost:18085");
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seedActors() {
        seedOperator(TENANT_A_OP_UUID, "tenant-a", "tenant-a-op@example.com", false);
        seedOperator(TENANT_B_OP_UUID, "tenant-b", "tenant-b-op@example.com", false);
        seedOperator(SUPER_ADMIN_UUID, "*", "super-audit@example.com", true);
    }

    /**
     * Scenario 1: tenantA operator requests audit data for tenantB → 403 TENANT_SCOPE_DENIED.
     *
     * <p>TASK-BE-262: also asserts the cross-tenant deny audit row is written
     * (best-effort write; admin_actions row outcome=DENIED, tenant_id=operator's,
     * downstream_detail captures the attempted target tenant).
     */
    @Test
    @DisplayName("1. tenantA operator queries tenantId=tenant-b → 403 TENANT_SCOPE_DENIED + DENIED audit row")
    void tenantA_queriesTenantB_returns403() throws Exception {
        // operator_id in admin_actions is a BIGINT FK to admin_operators.id (internal PK).
        // actor_id is the VARCHAR(100) column that stores the operator UUID string.
        // TASK-MONO-023c fix: use actor_id for UUID-based lookup.
        Integer beforeCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM admin_actions WHERE outcome = 'DENIED' AND actor_id = ?",
                Integer.class, TENANT_A_OP_UUID);

        mockMvc.perform(get("/api/admin/audit")
                        .header("Authorization", bearerToken(TENANT_A_OP_UUID))
                        .header("X-Operator-Reason", "audit-test")
                        .param("source", "admin")
                        .param("tenantId", "tenant-b"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_SCOPE_DENIED"));

        Integer afterCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM admin_actions WHERE outcome = 'DENIED' AND actor_id = ?",
                Integer.class, TENANT_A_OP_UUID);
        org.assertj.core.api.Assertions.assertThat(afterCount).isEqualTo(beforeCount + 1);

        String latestDetail = jdbcTemplate.queryForObject(
                "SELECT downstream_detail FROM admin_actions " +
                        "WHERE outcome = 'DENIED' AND actor_id = ? " +
                        "ORDER BY started_at DESC LIMIT 1",
                String.class, TENANT_A_OP_UUID);
        org.assertj.core.api.Assertions.assertThat(latestDetail)
                .contains("attempted_tenant_id=tenant-b");
    }

    /**
     * Scenario 2: SUPER_ADMIN requests cross-tenant audit (tenantId=*) → 200.
     */
    @Test
    @DisplayName("2. SUPER_ADMIN queries tenantId=* → 200")
    void superAdmin_queryPlatformScope_returns200() throws Exception {
        mockMvc.perform(get("/api/admin/audit")
                        .header("Authorization", bearerToken(SUPER_ADMIN_UUID))
                        .header("X-Operator-Reason", "cross-tenant-audit")
                        .param("source", "admin")
                        .param("tenantId", "*"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0));
    }

    /**
     * Scenario 3: tenantA operator queries without tenantId param (defaults to own tenant) → 200.
     */
    @Test
    @DisplayName("3. tenantA operator queries own tenant (no tenantId param) → 200")
    void tenantA_queriesOwnTenant_returns200() throws Exception {
        mockMvc.perform(get("/api/admin/audit")
                        .header("Authorization", bearerToken(TENANT_A_OP_UUID))
                        .header("X-Operator-Reason", "own-audit")
                        .param("source", "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private String bearerToken(String operatorUuid) {
        return "Bearer " + jwt.operatorToken(operatorUuid);
    }

    /**
     * Idempotent seed: inserts operator + (optionally) SUPER_ADMIN role binding.
     */
    private void seedOperator(String uuid, String tenantId, String email, boolean grantSuperAdmin) {
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
                    uuid, tenantId, email, "Test Op");
        }

        if (grantSuperAdmin) {
            // Bind SUPER_ADMIN role so permission checks pass for the super admin
            jdbcTemplate.update("""
                    INSERT IGNORE INTO admin_operator_roles (operator_id, role_id, tenant_id, granted_at, granted_by)
                    SELECT o.id, r.id, o.tenant_id, NOW(6), NULL
                      FROM admin_operators o CROSS JOIN admin_roles r
                     WHERE o.operator_id = ? AND r.name = 'SUPER_ADMIN'
                    """, uuid);
        } else {
            // Bind AUDIT_READ-capable role so the base permission check passes.
            // SUPPORT_LOCK has audit.read per V0022 seeding; use it for normal operators.
            jdbcTemplate.update("""
                    INSERT IGNORE INTO admin_operator_roles (operator_id, role_id, tenant_id, granted_at, granted_by)
                    SELECT o.id, r.id, o.tenant_id, NOW(6), NULL
                      FROM admin_operators o CROSS JOIN admin_roles r
                     WHERE o.operator_id = ? AND r.name = 'SUPPORT_LOCK'
                    """, uuid);
        }
    }
}
