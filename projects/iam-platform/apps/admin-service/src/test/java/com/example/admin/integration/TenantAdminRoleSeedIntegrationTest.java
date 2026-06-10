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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ADR-MONO-024 § 3.3 step 2a (TASK-BE-346) — coverage for the two seeded
 * delegated-administration roles and that step-1 (TASK-BE-345) D2 confinement
 * already governs a REAL {@code TENANT_ADMIN} grant.
 *
 * <ol>
 *   <li><b>seed</b>: {@code TENANT_ADMIN} maps to exactly
 *       {@code {operator.manage, tenant.admin.delegate}}; {@code TENANT_BILLING_ADMIN}
 *       to exactly {@code {subscription.manage}}.</li>
 *   <li><b>real-role confinement</b>: an operator granted {@code TENANT_ADMIN} with a
 *       grant row {@code tenant_id='tenant-x'} may PATCH a tenant-x operator's roles
 *       (200) but is denied (403 {@code TENANT_SCOPE_DENIED}) for a tenant-y operator.</li>
 * </ol>
 *
 * <p>The in-scope PATCH grants {@code TENANT_ADMIN} (perms ⊆ the actor's own), which
 * the step-2b (TASK-BE-347) grant-menu admits as in-tenant sub-delegation; the
 * tenant gate (step-1) is what this test isolates (tenant-x 200 vs tenant-y 403).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@Tag("integration")
class TenantAdminRoleSeedIntegrationTest extends AbstractIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)
            .withStartupTimeout(Duration.ofMinutes(3));

    static OperatorJwtTestFixture jwt;
    static String signingKeyPem;

    static final String TENANT_X_ADMIN_UUID = "00000000-0000-7000-8000-000000000091";
    static final String TARGET_X_UUID = "00000000-0000-7000-8000-000000000092";
    static final String TARGET_Y_UUID = "00000000-0000-7000-8000-000000000093";

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

    @Test
    @DisplayName("seed: TENANT_ADMIN → {operator.manage, tenant.admin.delegate}; TENANT_BILLING_ADMIN → {subscription.manage}")
    void rolesSeededWithExactPermissions() {
        List<String> tenantAdminPerms = jdbcTemplate.queryForList("""
                SELECT p.permission_key FROM admin_role_permissions p
                JOIN admin_roles r ON r.id = p.role_id
                WHERE r.name = 'TENANT_ADMIN' ORDER BY p.permission_key
                """, String.class);
        assertThat(tenantAdminPerms).containsExactlyInAnyOrder("operator.manage", "tenant.admin.delegate");

        List<String> billingPerms = jdbcTemplate.queryForList("""
                SELECT p.permission_key FROM admin_role_permissions p
                JOIN admin_roles r ON r.id = p.role_id
                WHERE r.name = 'TENANT_BILLING_ADMIN' ORDER BY p.permission_key
                """, String.class);
        assertThat(billingPerms).containsExactly("subscription.manage");

        // Plane separation: subscription.manage is NOT on TENANT_ADMIN.
        assertThat(tenantAdminPerms).doesNotContain("subscription.manage");
        // tenant.admin.delegate is TENANT_ADMIN-only (NOT on SUPER_ADMIN).
        Integer superAdminDelegate = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM admin_role_permissions p
                JOIN admin_roles r ON r.id = p.role_id
                WHERE r.name = 'SUPER_ADMIN' AND p.permission_key = 'tenant.admin.delegate'
                """, Integer.class);
        assertThat(superAdminDelegate).isZero();
    }

    @Test
    @DisplayName("real TENANT_ADMIN @ tenant-x: PATCH tenant-x operator → 200; tenant-y operator → 403 (step-1 confinement)")
    void realTenantAdmin_confinedByStep1() throws Exception {
        seedOperator(TENANT_X_ADMIN_UUID, "tenant-x", "tx-admin-real@example.com", "TENANT_ADMIN");
        seedOperator(TARGET_X_UUID, "tenant-x", "target-x-2a@example.com", "SUPPORT_READONLY");
        seedOperator(TARGET_Y_UUID, "tenant-y", "target-y-2a@example.com", "SUPPORT_READONLY");

        // in-scope (tenant-x → tenant-x): 200. Grant TENANT_ADMIN (perms ⊆ own) so
        // the step-2b grant-menu admits it; this isolates the step-1 tenant gate.
        mockMvc.perform(patch("/api/admin/operators/" + TARGET_X_UUID + "/roles")
                        .header("Authorization", bearer(TENANT_X_ADMIN_UUID))
                        .header("X-Operator-Reason", "rotation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roles": ["TENANT_ADMIN"]}
                                """))
                .andExpect(status().isOk());

        // out-of-scope (tenant-x admin → tenant-y operator): 403 TENANT_SCOPE_DENIED
        // (the step-1 tenant gate fires before the grant-menu, so the role is moot).
        mockMvc.perform(patch("/api/admin/operators/" + TARGET_Y_UUID + "/roles")
                        .header("Authorization", bearer(TENANT_X_ADMIN_UUID))
                        .header("X-Operator-Reason", "rotation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roles": ["TENANT_ADMIN"]}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_SCOPE_DENIED"));
    }

    private String bearer(String operatorUuid) {
        return "Bearer " + jwt.operatorToken(operatorUuid);
    }

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
