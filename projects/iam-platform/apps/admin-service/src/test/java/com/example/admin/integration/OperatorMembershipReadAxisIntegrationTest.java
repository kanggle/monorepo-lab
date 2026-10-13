package com.example.admin.integration;

import com.example.admin.support.OperatorJwtTestFixture;
import com.example.testsupport.integration.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeAll;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-MONO-784 S3 — <b>AC-1</b> (control group, permission leak) and the read-axis cells of the slice, against
 * the real migrated schema (ADR-MONO-082 b · admin-api.md § 행동 테넌트).
 *
 * <p>Fixture (784 AC-0 F8 — {@code TENANT_ADMIN} alone carries no {@code audit.read}, so company A also gets
 * {@code SUPPORT_READONLY} for A's audit query to be the passing half): one operator row, home A, role rows
 * {@code TENANT_ADMIN} + {@code SUPPORT_READONLY} + {@code SUPPORT_LOCK} @ A, memberships A and B — B with no
 * role row (rider R2). Every tenant name is unique to this class.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@Tag("integration")
class OperatorMembershipReadAxisIntegrationTest extends AbstractIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)
            .withStartupTimeout(Duration.ofMinutes(3));

    static OperatorJwtTestFixture jwt;
    static String signingKeyPem;

    static final String TWO_CO_OP = "00000000-0000-7000-8000-000000078401";
    static final String SINGLE_CO_OP = "00000000-0000-7000-8000-000000078402";
    static final String ACTOR_IN_B = "00000000-0000-7000-8000-000000078403";
    static final String ADMIN_OF_B = "00000000-0000-7000-8000-000000078404";

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
    JdbcTemplate jdbc;

    /**
     * 🔴 AC-1, failure side asserted first. Before S3 both reads in B passed — B is a membership, and the read
     * gate was «home ∪ assignments» — so the role held in A answered in B (inheritance). Then, in the same test,
     * the same reads in A pass: the denial is about B, not about the operator.
     */
    @Test
    @DisplayName("🔴 AC-1: TENANT_ADMIN @A, member of B with no role → B's operator list · audit 403; A's same reads 200")
    void ac1_roleInA_doesNotReachB() throws Exception {
        seedTwoCompanyOperator();

        // failure side first
        mockMvc.perform(get("/api/admin/operators").param("tenantId", "ac1-beta")
                        .header("Authorization", bearer(TWO_CO_OP)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_SCOPE_DENIED"));
        mockMvc.perform(get("/api/admin/audit").param("tenantId", "ac1-beta").param("source", "admin")
                        .header("Authorization", bearer(TWO_CO_OP)).header("X-Operator-Reason", "ac1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_SCOPE_DENIED"));

        // passing side — same operator, same reads, company A
        mockMvc.perform(get("/api/admin/operators").param("tenantId", "ac1-alpha")
                        .header("Authorization", bearer(TWO_CO_OP)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/audit").param("tenantId", "ac1-alpha").param("source", "admin")
                        .header("Authorization", bearer(TWO_CO_OP)).header("X-Operator-Reason", "ac1"))
                .andExpect(status().isOk());
    }

    /**
     * The lock-write cell (784 AC-0 F2): the account writes reused the read gate, so before S3 a lock in a
     * company where the operator was merely assigned passed the tenant gate. Refused before any downstream call.
     */
    @Test
    @DisplayName("🔴 F2: SUPPORT_LOCK @A cannot lock in B (member, no role) — 403 TENANT_SCOPE_DENIED, no IN_PROGRESS row")
    void lockInB_refusedByTheGrantScope() throws Exception {
        seedTwoCompanyOperator();
        Integer before = jdbc.queryForObject(
                "SELECT COUNT(*) FROM admin_actions WHERE actor_id = ? AND action_code = 'ACCOUNT_LOCK' "
                        + "AND outcome <> 'DENIED'", Integer.class, TWO_CO_OP);

        mockMvc.perform(post("/api/admin/accounts/acc-784/lock")
                        .header("Authorization", bearer(TWO_CO_OP))
                        .header("X-Tenant-Id", "ac1-beta")
                        .header("X-Operator-Reason", "ac1-lock")
                        .header("Idempotency-Key", "ac1-lock-" + System.nanoTime()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_SCOPE_DENIED"));

        Integer after = jdbc.queryForObject(
                "SELECT COUNT(*) FROM admin_actions WHERE actor_id = ? AND action_code = 'ACCOUNT_LOCK' "
                        + "AND outcome <> 'DENIED'", Integer.class, TWO_CO_OP);
        assertThat(after).as("the refusal happens before the A10 IN_PROGRESS row").isEqualTo(before);
    }

    /**
     * Control: a single-company operator's omitted tenant resolves to exactly what the old «home» default gave
     * — its one grant-scope tenant — and its audit row is stamped with the same tenant as before.
     */
    @Test
    @DisplayName("control: single-company operator, tenantId omitted → its own tenant, meta-audit row tenant = home (byte-identical)")
    void singleCompany_omittedTenant_unchanged() throws Exception {
        seedOperator(SINGLE_CO_OP, "ac1-gamma", "ac1-gamma@example.com");
        grant(SINGLE_CO_OP, "SUPPORT_READONLY", "ac1-gamma");
        member(SINGLE_CO_OP, "ac1-gamma");

        mockMvc.perform(get("/api/admin/audit").param("source", "admin")
                        .header("Authorization", bearer(SINGLE_CO_OP)).header("X-Operator-Reason", "ctl"))
                .andExpect(status().isOk());

        List<String> rows = jdbc.queryForList("""
                SELECT CONCAT_WS('|', tenant_id, target_tenant_id) FROM admin_actions
                 WHERE actor_id = ? AND action_code = 'AUDIT_QUERY' AND outcome = 'SUCCESS'
                """, String.class, SINGLE_CO_OP);
        assertThat(rows).containsExactly("ac1-gamma|ac1-gamma");
    }

    /**
     * D-13: the action a two-company operator performs in B is written with {@code tenant_id = B}, so B's own
     * administrator sees it in B's audit (before S3 it was the actor's home A — invisible to B).
     */
    @Test
    @DisplayName("D-13: B's action's audit row shows in B's query (actor home A, member + SUPPORT_READONLY in both)")
    void actionInB_isInBsAudit() throws Exception {
        seedOperator(ACTOR_IN_B, "ac1-delta", "ac1-delta-actor@example.com");
        grant(ACTOR_IN_B, "SUPPORT_READONLY", "ac1-delta");
        grant(ACTOR_IN_B, "SUPPORT_READONLY", "ac1-epsilon");
        member(ACTOR_IN_B, "ac1-delta");
        member(ACTOR_IN_B, "ac1-epsilon");
        seedOperator(ADMIN_OF_B, "ac1-epsilon", "ac1-epsilon-admin@example.com");
        grant(ADMIN_OF_B, "SUPPORT_READONLY", "ac1-epsilon");
        member(ADMIN_OF_B, "ac1-epsilon");

        // the actor acts in B (an audit query is itself an audited action)
        mockMvc.perform(get("/api/admin/audit").param("tenantId", "ac1-epsilon").param("source", "admin")
                        .header("Authorization", bearer(ACTOR_IN_B)).header("X-Operator-Reason", "d13"))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForList("""
                SELECT tenant_id FROM admin_actions
                 WHERE actor_id = ? AND action_code = 'AUDIT_QUERY' AND outcome = 'SUCCESS'
                """, String.class, ACTOR_IN_B)).containsExactly("ac1-epsilon");

        // B's administrator sees exactly that row (its own meta row is written after the read)
        mockMvc.perform(get("/api/admin/audit").param("tenantId", "ac1-epsilon").param("source", "admin")
                        .param("actionCode", "AUDIT_QUERY")
                        .header("Authorization", bearer(ADMIN_OF_B)).header("X-Operator-Reason", "d13"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].operatorId").value(ACTOR_IN_B));
    }

    // ── fixture ───────────────────────────────────────────────────────────────

    private void seedTwoCompanyOperator() {
        seedOperator(TWO_CO_OP, "ac1-alpha", "ac1-two-co@example.com");
        grant(TWO_CO_OP, "TENANT_ADMIN", "ac1-alpha");
        grant(TWO_CO_OP, "SUPPORT_READONLY", "ac1-alpha"); // F8: TENANT_ADMIN has no audit.read
        grant(TWO_CO_OP, "SUPPORT_LOCK", "ac1-alpha");
        member(TWO_CO_OP, "ac1-alpha");
        member(TWO_CO_OP, "ac1-beta"); // R2: a membership with no role row
    }

    private String bearer(String operatorUuid) {
        return "Bearer " + jwt.operatorToken(operatorUuid);
    }

    private void seedOperator(String uuid, String home, String email) {
        jdbc.update("""
                INSERT IGNORE INTO admin_operators
                  (operator_id, tenant_id, email, password_hash, display_name, status, created_at, updated_at, version)
                VALUES (?, ?, ?, 'x', 'AC1 Op', 'ACTIVE', NOW(6), NOW(6), 0)
                """, uuid, home, email);
    }

    private void grant(String uuid, String role, String tenant) {
        jdbc.update("""
                INSERT IGNORE INTO admin_operator_roles (operator_id, role_id, tenant_id, granted_at, granted_by)
                SELECT o.id, r.id, ?, NOW(6), NULL
                  FROM admin_operators o CROSS JOIN admin_roles r
                 WHERE o.operator_id = ? AND r.name = ?
                """, tenant, uuid, role);
    }

    private void member(String uuid, String tenant) {
        jdbc.update("""
                INSERT IGNORE INTO operator_tenant_assignment
                  (operator_id, tenant_id, granted_at, granted_by, permission_set_id)
                SELECT o.id, ?, NOW(6), NULL, NULL FROM admin_operators o WHERE o.operator_id = ?
                """, tenant, uuid);
    }
}
