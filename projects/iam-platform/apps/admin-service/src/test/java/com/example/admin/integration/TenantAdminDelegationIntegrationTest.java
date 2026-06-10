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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ADR-MONO-024 § 3.3 step 2b (TASK-BE-347) — the delegated-administration
 * behavioral surface: assign/unassign confinement + grant-menu no-escalation.
 *
 * <p>Actor: a real {@code TENANT_ADMIN @ tenant-x} (holds {@code operator.manage}
 * + {@code tenant.admin.delegate} via a grant row {@code tenant_id='tenant-x'}).
 * SUPER_ADMIN ({@code '*'}) is the net-zero control.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@Tag("integration")
class TenantAdminDelegationIntegrationTest extends AbstractIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)
            .withStartupTimeout(Duration.ofMinutes(3));

    static OperatorJwtTestFixture jwt;
    static String signingKeyPem;

    // All-digit UUIDs in a task-347 band — avoids the case-INSENSITIVE MySQL
    // collation collision with other IT classes' uppercase-hex UUIDs (e.g.
    // OperatorAdminIntegrationTest's '...0000000000A1'/'...B1'/'...B2'), which
    // share the per-JVM MySQL container and would otherwise be matched by the
    // idempotent seed's case-insensitive operator_id lookup.
    static final String SUPER_ADMIN_UUID = "00000000-0000-7000-8000-000000034700";
    static final String TENANT_X_ADMIN_UUID = "00000000-0000-7000-8000-000000034701";

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
    void seedAdmins() {
        seedOperator(SUPER_ADMIN_UUID, "*", "super-2b@example.com", "SUPER_ADMIN");
        seedOperator(TENANT_X_ADMIN_UUID, "tenant-x", "tx-admin-2b@example.com", "TENANT_ADMIN");
    }

    // ── Part A: assign / unassign confinement ──────────────────────────────────

    @Test
    @DisplayName("assign: TENANT_ADMIN@tenant-x assigns to tenant-x → 201; tenant-y → 403; duplicate → 409")
    void assign_confinedAndIdempotencyGuarded() throws Exception {
        String target = uuid("11");
        seedOperator(target, "tenant-x", "assign-target-b1@example.com", "SUPPORT_READONLY");

        // in-scope (tenant-x) → 201
        mockMvc.perform(post(assignmentPath(target, "tenant-x"))
                        .header("Authorization", bearer(TENANT_X_ADMIN_UUID))
                        .header("X-Operator-Reason", "onboarding"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tenantId").value("tenant-x"));

        // duplicate → 409
        mockMvc.perform(post(assignmentPath(target, "tenant-x"))
                        .header("Authorization", bearer(TENANT_X_ADMIN_UUID))
                        .header("X-Operator-Reason", "onboarding"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ASSIGNMENT_ALREADY_EXISTS"));

        // out-of-scope (tenant-y) → 403
        mockMvc.perform(post(assignmentPath(target, "tenant-y"))
                        .header("Authorization", bearer(TENANT_X_ADMIN_UUID))
                        .header("X-Operator-Reason", "onboarding"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_SCOPE_DENIED"));
    }

    @Test
    @DisplayName("unassign: removes the row (204); absent → 404; tenant-y → 403")
    void unassign_confinedAndNotFound() throws Exception {
        String target = uuid("12");
        seedOperator(target, "tenant-x", "assign-target-b2@example.com", "SUPPORT_READONLY");

        // create then delete → 204
        mockMvc.perform(post(assignmentPath(target, "tenant-x"))
                        .header("Authorization", bearer(TENANT_X_ADMIN_UUID))
                        .header("X-Operator-Reason", "onboarding"))
                .andExpect(status().isCreated());
        mockMvc.perform(delete(assignmentPath(target, "tenant-x"))
                        .header("Authorization", bearer(TENANT_X_ADMIN_UUID))
                        .header("X-Operator-Reason", "offboarding"))
                .andExpect(status().isNoContent());

        // delete again → 404
        mockMvc.perform(delete(assignmentPath(target, "tenant-x"))
                        .header("Authorization", bearer(TENANT_X_ADMIN_UUID))
                        .header("X-Operator-Reason", "offboarding"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ASSIGNMENT_NOT_FOUND"));

        // tenant-y delete → 403 (confinement before existence check)
        mockMvc.perform(delete(assignmentPath(target, "tenant-y"))
                        .header("Authorization", bearer(TENANT_X_ADMIN_UUID))
                        .header("X-Operator-Reason", "offboarding"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_SCOPE_DENIED"));
    }

    @Test
    @DisplayName("net-zero: SUPER_ADMIN ('*') may assign to any tenant → 201")
    void superAdmin_assignsAnyTenant() throws Exception {
        String target = uuid("13");
        seedOperator(target, "tenant-y", "assign-target-b3@example.com", "SUPPORT_READONLY");
        mockMvc.perform(post(assignmentPath(target, "tenant-y"))
                        .header("Authorization", bearer(SUPER_ADMIN_UUID))
                        .header("X-Operator-Reason", "onboarding"))
                .andExpect(status().isCreated());
    }

    // ── Part B: grant-menu no-escalation ───────────────────────────────────────

    @Test
    @DisplayName("grant-menu deny: TENANT_ADMIN granting SUPER_ADMIN / SUPPORT_LOCK / TENANT_BILLING_ADMIN → 403 ROLE_GRANT_FORBIDDEN")
    void grantMenu_deniesEscalation() throws Exception {
        String target = uuid("14");
        seedOperator(target, "tenant-x", "grant-target-b4@example.com", "SUPPORT_READONLY");

        for (String role : new String[]{"SUPER_ADMIN", "SUPPORT_LOCK", "TENANT_BILLING_ADMIN"}) {
            mockMvc.perform(patch(rolesPath(target))
                            .header("Authorization", bearer(TENANT_X_ADMIN_UUID))
                            .header("X-Operator-Reason", "rotation")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"roles\": [\"" + role + "\"]}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ROLE_GRANT_FORBIDDEN"));
        }
    }

    @Test
    @DisplayName("grant-menu admit (sub-delegation): TENANT_ADMIN (holds tenant.admin.delegate) grants TENANT_ADMIN → 200")
    void grantMenu_admitsInTenantSubDelegation() throws Exception {
        String target = uuid("15");
        seedOperator(target, "tenant-x", "grant-target-b5@example.com", "SUPPORT_READONLY");

        mockMvc.perform(patch(rolesPath(target))
                        .header("Authorization", bearer(TENANT_X_ADMIN_UUID))
                        .header("X-Operator-Reason", "appoint-peer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\": [\"TENANT_ADMIN\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("TENANT_ADMIN"));
    }

    @Test
    @DisplayName("net-zero: SUPER_ADMIN grant menu unconstrained — may grant SUPER_ADMIN → 200")
    void superAdmin_grantMenuUnconstrained() throws Exception {
        String target = uuid("16");
        seedOperator(target, "tenant-x", "grant-target-b6@example.com", "SUPPORT_READONLY");

        mockMvc.perform(patch(rolesPath(target))
                        .header("Authorization", bearer(SUPER_ADMIN_UUID))
                        .header("X-Operator-Reason", "rotation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\": [\"SUPER_ADMIN\"]}"))
                .andExpect(status().isOk());
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    /** All-digit task-347 band (see UUID constants note) — {@code twoDigit} is "11".."16". */
    private static String uuid(String twoDigit) {
        return "00000000-0000-7000-8000-0000000347" + twoDigit;
    }

    private static String assignmentPath(String operatorId, String tenantId) {
        return "/api/admin/operators/" + operatorId + "/assignments/" + tenantId;
    }

    private static String rolesPath(String operatorId) {
        return "/api/admin/operators/" + operatorId + "/roles";
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
