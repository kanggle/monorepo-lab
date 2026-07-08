package com.example.admin.integration;

import com.example.admin.domain.rbac.Permission;
import com.example.admin.support.OperatorJwtTestFixture;
import com.example.testsupport.integration.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
 * TASK-BE-486 — Testcontainers coverage for the read-only role/permission
 * catalog surface ({@code GET /api/admin/roles}, {@code GET /api/admin/permissions}).
 * Boots the full Spring context against a real MySQL + Redis and reads the RBAC
 * catalog seeded by Flyway (V0006 roles/perms, V0022 operator.manage, V0033
 * TENANT_ADMIN, V0040 partnership.manage).
 *
 * <p>Exercises the {@code operator.manage} gate against the REAL
 * {@code PermissionEvaluator}: a SUPER_ADMIN (holds operator.manage) reads the
 * catalog; a SUPPORT_READONLY operator (no operator.manage) is denied 403.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class RoleCatalogIntegrationTest extends AbstractIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)
            .withStartupTimeout(Duration.ofMinutes(3));

    static OperatorJwtTestFixture jwt;
    static String signingKeyPem;

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

    private static final String SUPER_ADMIN_UUID = "00000000-0000-7000-8000-0000000004E6";
    private static final String SUPPORT_RO_UUID  = "00000000-0000-7000-8000-0000000004E7";

    private String superAdminToken() {
        return "Bearer " + jwt.operatorToken(SUPER_ADMIN_UUID);
    }

    private String supportReadonlyToken() {
        return "Bearer " + jwt.operatorToken(SUPPORT_RO_UUID);
    }

    @BeforeEach
    void seedOperators() {
        seedOperatorWithRole(SUPER_ADMIN_UUID, "*", "be486-super@example.com",
                "BE486 Super", "SUPER_ADMIN");
        seedOperatorWithRole(SUPPORT_RO_UUID, "fan-platform", "be486-ro@example.com",
                "BE486 RO", "SUPPORT_READONLY");
    }

    // ------------------------------------------------ GET /api/admin/roles

    @Test
    @DisplayName("GET /api/admin/roles → 200, global scope, each seed role with its permission set (SUPER_ADMIN holds operator.manage)")
    void listRoles_returnsCatalogWithPermissionSets() throws Exception {
        mockMvc.perform(get("/api/admin/roles").header("Authorization", superAdminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scope").value("global"))
                // stable seed order: SUPER_ADMIN is role id 1 (V0006 first insert).
                .andExpect(jsonPath("$.roles[0].name").value("SUPER_ADMIN"))
                // SUPER_ADMIN carries operator.manage (V0022) among its permission set.
                .andExpect(jsonPath("$.roles[0].permissions[?(@ == 'operator.manage')]").exists())
                .andExpect(jsonPath("$.roles[0].permissions[?(@ == 'account.lock')]").exists())
                // every seed role is present.
                .andExpect(jsonPath("$.roles[?(@.name == 'SUPPORT_READONLY')]").exists())
                .andExpect(jsonPath("$.roles[?(@.name == 'SUPPORT_LOCK')]").exists())
                .andExpect(jsonPath("$.roles[?(@.name == 'SECURITY_ANALYST')]").exists())
                .andExpect(jsonPath("$.roles[?(@.name == 'TENANT_ADMIN')]").exists());
    }

    @Test
    @DisplayName("GET /api/admin/roles without operator.manage (SUPPORT_READONLY) → 403 PERMISSION_DENIED")
    void listRoles_withoutOperatorManage_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/roles").header("Authorization", supportReadonlyToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    @Test
    @DisplayName("GET /api/admin/roles without operator JWT → 401 TOKEN_INVALID")
    void listRoles_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/admin/roles"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
    }

    // ------------------------------------------ GET /api/admin/permissions

    @Test
    @DisplayName("GET /api/admin/permissions → 200, full canonical catalog (rbac.md § Permission Keys)")
    void listPermissions_returnsFullCatalog() throws Exception {
        mockMvc.perform(get("/api/admin/permissions").header("Authorization", superAdminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scope").value("global"))
                .andExpect(jsonPath("$.permissions.length()").value(Permission.catalog().size()))
                .andExpect(jsonPath("$.permissions[?(@ == 'operator.manage')]").exists())
                .andExpect(jsonPath("$.permissions[?(@ == 'partnership.manage')]").exists())
                .andExpect(jsonPath("$.permissions[?(@ == 'tenant.admin.delegate')]").exists());
    }

    @Test
    @DisplayName("GET /api/admin/permissions without operator.manage → 403 PERMISSION_DENIED")
    void listPermissions_withoutOperatorManage_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/permissions").header("Authorization", supportReadonlyToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    /**
     * Idempotent insert of a standalone operator bound to a single seed role, so
     * each case owns its own fixture and the real PermissionEvaluator resolves the
     * expected permission union.
     */
    private void seedOperatorWithRole(String operatorUuid, String tenantId, String email,
                                      String displayName, String roleName) {
        Integer existing = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM admin_operators WHERE operator_id = ?",
                Integer.class, operatorUuid);
        if (existing == null || existing == 0) {
            jdbcTemplate.update("""
                    INSERT INTO admin_operators
                      (operator_id, tenant_id, email, password_hash, display_name, status,
                       created_at, updated_at, version)
                    VALUES (?, ?, ?, 'x', ?, 'ACTIVE', NOW(6), NOW(6), 0)
                    """,
                    operatorUuid, tenantId, email, displayName);
        }
        jdbcTemplate.update("""
                INSERT IGNORE INTO admin_operator_roles (operator_id, role_id, granted_at, granted_by, tenant_id)
                SELECT o.id, r.id, NOW(6), NULL, o.tenant_id
                  FROM admin_operators o CROSS JOIN admin_roles r
                 WHERE o.operator_id = ? AND r.name = ?
                """, operatorUuid, roleName);
    }
}
