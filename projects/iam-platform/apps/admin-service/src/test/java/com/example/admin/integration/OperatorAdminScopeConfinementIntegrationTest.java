package com.example.admin.integration;

import com.example.admin.support.OperatorJwtTestFixture;
import com.example.testsupport.integration.AbstractIntegrationTest;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ADR-MONO-024 D2 / D7 step 1 (TASK-BE-345) — integration coverage for the
 * target-tenant scope confinement on the operator-management surface.
 *
 * <p>Three actors:
 * <ul>
 *   <li>{@code superAdmin} — SUPER_ADMIN, grant row {@code tenant_id='*'} →
 *       admin-grant scope {@code {'*'}} (net-zero: passes for every tenant).</li>
 *   <li>{@code tenantXAdmin} — holds {@code operator.manage} via a grant row with
 *       {@code tenant_id='tenant-x'} → admin-grant scope {@code {'tenant-x'}}. This
 *       simulates the future {@code TENANT_ADMIN @ tenant-x} (step 2 seeds the real
 *       role; step 1 proves the confinement mechanism with a tenant-scoped grant).</li>
 *   <li>target operators in {@code tenant-x} and {@code tenant-y}.</li>
 * </ul>
 *
 * <p>Asserts:
 * <ol>
 *   <li><b>NET-ZERO</b>: SUPER_ADMIN patches a tenant-x AND a tenant-y operator → 200.</li>
 *   <li><b>in-scope</b>: tenant-x admin patches the tenant-x operator → 200.</li>
 *   <li><b>confinement</b>: tenant-x admin patches the tenant-y operator → 403
 *       {@code TENANT_SCOPE_DENIED} + a DENIED {@code admin_actions} row recording the
 *       attempted tenant; no role mutation occurs.</li>
 *   <li><b>create confinement</b>: tenant-x admin creates in tenant-y → 403; in tenant-x → 201.</li>
 * </ol>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@Tag("integration")
class OperatorAdminScopeConfinementIntegrationTest extends AbstractIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)
            .withStartupTimeout(Duration.ofMinutes(3));

    static OperatorJwtTestFixture jwt;
    static String signingKeyPem;

    static final String SUPER_ADMIN_UUID  = "00000000-0000-7000-8000-000000000081";
    static final String TENANT_X_ADMIN_UUID = "00000000-0000-7000-8000-000000000082";
    static final String TARGET_X_UUID = "00000000-0000-7000-8000-000000000083";
    static final String TARGET_Y_UUID = "00000000-0000-7000-8000-000000000084";

    @BeforeAll
    static void setupShared() throws IOException {
        jwt = new OperatorJwtTestFixture();
        java.security.PrivateKey pk = extractPrivateKey(jwt);
        signingKeyPem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(pk.getEncoded())
                + "\n-----END PRIVATE KEY-----\n";
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
        // operator.manage holders
        seedOperator(SUPER_ADMIN_UUID, "*", "super-conf@example.com", "SUPER_ADMIN");
        seedOperator(TENANT_X_ADMIN_UUID, "tenant-x", "tx-admin@example.com", "SUPER_ADMIN");
        // targets (any role; they are the object of the PATCH, not the actor)
        seedOperator(TARGET_X_UUID, "tenant-x", "target-x@example.com", "SUPPORT_READONLY");
        seedOperator(TARGET_Y_UUID, "tenant-y", "target-y@example.com", "SUPPORT_READONLY");
    }

    @Test
    @DisplayName("NET-ZERO: SUPER_ADMIN ('*') patches tenant-x AND tenant-y operators → 200")
    void superAdmin_netZero_patchesAnyTenant() throws Exception {
        patchRoles(SUPER_ADMIN_UUID, TARGET_X_UUID).andExpect(status().isOk());
        patchRoles(SUPER_ADMIN_UUID, TARGET_Y_UUID).andExpect(status().isOk());
    }

    @Test
    @DisplayName("in-scope: tenant-x admin patches the tenant-x operator → 200")
    void tenantXAdmin_inScope_patchesTenantXOperator() throws Exception {
        patchRoles(TENANT_X_ADMIN_UUID, TARGET_X_UUID)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operatorId").value(TARGET_X_UUID));
    }

    @Test
    @DisplayName("confinement: tenant-x admin patches the tenant-y operator → 403 TENANT_SCOPE_DENIED + DENIED row")
    void tenantXAdmin_outOfScope_deniedForTenantYOperator() throws Exception {
        Integer before = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM admin_actions WHERE outcome = 'DENIED' AND actor_id = ?",
                Integer.class, TENANT_X_ADMIN_UUID);

        patchRoles(TENANT_X_ADMIN_UUID, TARGET_Y_UUID)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_SCOPE_DENIED"));

        Integer after = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM admin_actions WHERE outcome = 'DENIED' AND actor_id = ?",
                Integer.class, TENANT_X_ADMIN_UUID);
        assertThat(after).isEqualTo(before + 1);

        String detail = jdbcTemplate.queryForObject(
                "SELECT downstream_detail FROM admin_actions " +
                        "WHERE outcome = 'DENIED' AND actor_id = ? ORDER BY started_at DESC LIMIT 1",
                String.class, TENANT_X_ADMIN_UUID);
        assertThat(detail).contains("attempted_tenant_id=tenant-y");

        // No role mutation occurred: target-y still holds exactly its seeded SUPPORT_READONLY.
        Integer targetYRoleCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM admin_operator_roles b
                JOIN admin_operators o ON o.id = b.operator_id
                WHERE o.operator_id = ?
                """, Integer.class, TARGET_Y_UUID);
        assertThat(targetYRoleCount).isEqualTo(1);
    }

    @Test
    @DisplayName("create confinement: tenant-x admin creates in tenant-y → 403; in tenant-x → 201")
    void tenantXAdmin_createConfinement() throws Exception {
        String emailY = "new-y-" + System.currentTimeMillis() + "@example.com";
        mockMvc.perform(post("/api/admin/operators")
                        .header("Authorization", bearer(TENANT_X_ADMIN_UUID))
                        .header("X-Operator-Reason", "provisioning")
                        .header("Idempotency-Key", "conf-create-y-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","displayName":"New Y","password":"StrongPass1!",
                                 "roles":["SUPPORT_READONLY"],"tenantId":"tenant-y"}
                                """.formatted(emailY)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_SCOPE_DENIED"));

        String emailX = "new-x-" + System.currentTimeMillis() + "@example.com";
        mockMvc.perform(post("/api/admin/operators")
                        .header("Authorization", bearer(TENANT_X_ADMIN_UUID))
                        .header("X-Operator-Reason", "provisioning")
                        .header("Idempotency-Key", "conf-create-x-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","displayName":"New X","password":"StrongPass1!",
                                 "roles":["SUPPORT_READONLY"],"tenantId":"tenant-x"}
                                """.formatted(emailX)))
                .andExpect(status().isCreated());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private org.springframework.test.web.servlet.ResultActions patchRoles(String actorUuid, String targetUuid)
            throws Exception {
        return mockMvc.perform(patch("/api/admin/operators/" + targetUuid + "/roles")
                .header("Authorization", bearer(actorUuid))
                .header("X-Operator-Reason", "rotation")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"roles": ["SUPPORT_LOCK"]}
                        """));
    }

    private String bearer(String operatorUuid) {
        return "Bearer " + jwt.operatorToken(operatorUuid);
    }

    /**
     * Idempotent seed: operator row + a single role binding whose
     * {@code tenant_id} mirrors the operator's home tenant (so a tenant-scoped
     * grant yields an admin-grant scope of that tenant; a {@code '*'} home yields
     * the platform scope).
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
                    uuid, tenantId, email, "Test Op");
        }
        jdbcTemplate.update("""
                INSERT IGNORE INTO admin_operator_roles (operator_id, role_id, tenant_id, granted_at, granted_by)
                SELECT o.id, r.id, o.tenant_id, NOW(6), NULL
                  FROM admin_operators o CROSS JOIN admin_roles r
                 WHERE o.operator_id = ? AND r.name = ?
                """, uuid, roleName);
    }
}
