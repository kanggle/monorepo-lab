package com.example.admin.integration;

import com.example.admin.application.exception.DownstreamFailureException;
import com.example.admin.application.orgnode.CeilingView;
import com.example.admin.application.orgnode.OrgNodeView;
import com.example.admin.application.port.OrgNodePort;
import com.example.admin.infrastructure.persistence.rbac.OrgNodeSubtreeResolver;
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
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-BE-492 / ADR-MONO-047 D5 — the org plane end-to-end against a real MySQL (Flyway
 * V0041 seed + V0042 scope-driver column and its CHECK), the real RBAC aspect, the real
 * {@code AdminGrantScopeEvaluator}, and the real audit writer.
 *
 * <p>{@link OrgNodePort} is the seam: account-service owns the tree, so it is mocked here.
 * That lets the test drive the two behaviours that matter and cannot be provoked against a
 * live authority — an <b>unreachable</b> authority, and a node whose ceiling permits nothing.
 *
 * <p>Tree: {@code root ── biz ── (tenant acme-wms)}, plus a disjoint root {@code other}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@Tag("integration")
class OrgNodeAdminIntegrationTest extends AbstractIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)
            .withStartupTimeout(Duration.ofMinutes(3));

    static OperatorJwtTestFixture jwt;
    static String signingKeyPem;

    // Task-scoped operator UUIDs (house convention: ...-00000be0<task><nn>).
    //
    // These MUST NOT collide with any other integration test's operators: the whole suite
    // shares one MySQL container. And "collide" is case-INSENSITIVE — admin_operators is
    // utf8mb4_unicode_ci, so '...0000a1' and '...0000A1' are the same row. An earlier draft
    // used ...0000000000a1, which silently reused OperatorAdminIntegrationTest's
    // ...0000000000A1 operator: the seed hit its "already exists" branch, SUPER_ADMIN
    // inherited that operator's concrete tenant_id instead of '*', and every platform-scope
    // assertion failed — but only when the full suite ran, never in isolation.
    static final String SUPER_UUID = "00000000-0000-7000-8000-00000be04921";
    static final String ORG_ADMIN_UUID = "00000000-0000-7000-8000-00000be04922";
    static final String PLAIN_UUID = "00000000-0000-7000-8000-00000be04923";
    static final String IN_SUBTREE_UUID = "00000000-0000-7000-8000-00000be04924";
    static final String OUT_SUBTREE_UUID = "00000000-0000-7000-8000-00000be04925";
    static final String GRANT_TARGET_UUID = "00000000-0000-7000-8000-00000be04926";

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
        registry.add("admin.auth-service.base-url", () -> "http://localhost:18086");
        registry.add("admin.account-service.base-url", () -> "http://localhost:18086");
        registry.add("admin.security-service.base-url", () -> "http://localhost:18086");
    }

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired OrgNodeSubtreeResolver subtreeResolver;

    @MockitoBean OrgNodePort orgNodePort;

    private static OrgNodeView node(String id, String parentId) {
        return new OrgNodeView(id, parentId, "n-" + id, 1, CeilingView.unbounded(), Instant.EPOCH, Instant.EPOCH);
    }

    @BeforeEach
    void seed() {
        // The subtree resolver caches successes; a leftover entry would leak across tests.
        subtreeResolver.invalidateAll();

        when(orgNodePort.list()).thenReturn(List.of(
                node("root", null), node("biz", "root"), node("other", null)));
        when(orgNodePort.get("biz")).thenReturn(node("biz", "root"));
        when(orgNodePort.subtreeTenantIds("biz")).thenReturn(List.of("acme-wms"));
        when(orgNodePort.effectiveCeiling(anyString())).thenReturn(CeilingView.unbounded());

        seedOperator(SUPER_UUID, "*", "orgnode-super@example.com", "SUPER_ADMIN");
        seedOperator(ORG_ADMIN_UUID, "acme-hq", "orgnode-admin@example.com", null);
        seedOperator(PLAIN_UUID, "acme-hq", "orgnode-plain@example.com", "SUPPORT_READONLY");
        seedOperator(IN_SUBTREE_UUID, "acme-wms", "orgnode-in@example.com", "SUPPORT_READONLY");
        seedOperator(OUT_SUBTREE_UUID, "globex", "orgnode-out@example.com", "SUPPORT_READONLY");
        seedOperator(GRANT_TARGET_UUID, "acme-hq", "orgnode-target@example.com", null);

        // ORG_ADMIN @ biz. tenant_id mirrors the operator's OWN tenant (BE-289 WI-2);
        // org_node_id is the scope driver.
        jdbcTemplate.update("""
                INSERT IGNORE INTO admin_operator_roles (operator_id, role_id, tenant_id, org_node_id, granted_at, granted_by)
                SELECT o.id, r.id, o.tenant_id, 'biz', NOW(6), NULL
                  FROM admin_operators o CROSS JOIN admin_roles r
                 WHERE o.operator_id = ? AND r.name = 'ORG_ADMIN'
                """, ORG_ADMIN_UUID);
    }

    // ── V0041 seed (AC-1) ────────────────────────────────────────────────────────

    @Test
    @DisplayName("V0041: ORG_ADMIN → exactly {org.manage, operator.manage, tenant.admin.delegate, group.manage}")
    void orgAdminSeededWithExactPermissions() {
        List<String> perms = jdbcTemplate.queryForList("""
                SELECT p.permission_key FROM admin_role_permissions p
                JOIN admin_roles r ON r.id = p.role_id
                WHERE r.name = 'ORG_ADMIN' ORDER BY p.permission_key
                """, String.class);
        // group.manage added by TASK-BE-520 / ADR-MONO-046 V0045 (operator-group
        // management — ORG_ADMIN already holds operator.manage, so it mirrors that reach).
        assertThat(perms).containsExactlyInAnyOrder("org.manage", "operator.manage", "tenant.admin.delegate", "group.manage");

        // v1 plane separation, deliberate: the entitlement plane stays with TENANT_BILLING_ADMIN,
        // and partnership.manage is a customer-tenant relationship key (ADR-045 D2) that not even
        // SUPER_ADMIN holds. Widening either to make a test pass is forbidden by the task.
        assertThat(perms).doesNotContain("subscription.manage", "partnership.manage");
    }

    @Test
    @DisplayName("V0041: SUPER_ADMIN gains org.manage (sole ROOT creator)")
    void superAdminGainsOrgManage() {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM admin_role_permissions p JOIN admin_roles r ON r.id = p.role_id
                WHERE r.name = 'SUPER_ADMIN' AND p.permission_key = 'org.manage'
                """, Integer.class);
        assertThat(count).isEqualTo(1);
    }

    // ── V0042 shape (AC-2) ──────────────────────────────────────────────────────

    @Test
    @DisplayName("V0042: a platform grant may not also carry an org_node_id — rejected by the DB CHECK")
    void platformGrantWithNodeIsRejectedByCheckConstraint() {
        // The app layer also rejects this (AdminOperatorRoleJpaEntity.createNodeScoped), but the
        // DB must too: a hand-written seed / ops row would otherwise create an ambiguous grant
        // that the '*' pre-scan silently resolves to platform scope, node ignored.
        // MySQL raises errno 3819 (SQLSTATE HY000) for a CHECK violation, which Spring maps to
        // UncategorizedSQLException — NOT DataIntegrityViolationException (that is for FK/unique).
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO admin_operator_roles (operator_id, role_id, tenant_id, org_node_id, granted_at)
                SELECT o.id, r.id, '*', 'biz', NOW(6)
                  FROM admin_operators o CROSS JOIN admin_roles r
                 WHERE o.operator_id = ? AND r.name = 'SUPPORT_LOCK'
                """, GRANT_TARGET_UUID))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ck_admin_operator_roles_node_not_platform");
    }

    // ── deny-default (AC-7) ─────────────────────────────────────────────────────

    @Test
    @DisplayName("an operator without org.manage → 403 PERMISSION_DENIED on every org-node endpoint")
    void withoutOrgManage_isDenied() throws Exception {
        mockMvc.perform(get("/api/admin/org-nodes").header("Authorization", bearer(PLAIN_UUID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    // ── reach (AC-3, AC-4) ──────────────────────────────────────────────────────

    @Test
    @DisplayName("ORG_ADMIN @ biz sees subtree(biz) only; SUPER_ADMIN sees every node")
    void listNodes_isReachScoped() throws Exception {
        mockMvc.perform(get("/api/admin/org-nodes").header("Authorization", bearer(ORG_ADMIN_UUID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].orgNodeId").value("biz"));

        mockMvc.perform(get("/api/admin/org-nodes").header("Authorization", bearer(SUPER_UUID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(3));
    }

    @Test
    @DisplayName("a node outside the actor's reach → 404 ORG_NODE_NOT_FOUND (403 would leak existence) + DENIED row")
    void outOfReachNode_is404AndAudited() throws Exception {
        mockMvc.perform(get("/api/admin/org-nodes/other").header("Authorization", bearer(ORG_ADMIN_UUID)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORG_NODE_NOT_FOUND"));

        // admin_actions.operator_id is a BIGINT FK to admin_operators.id; actor_id is the
        // VARCHAR column holding the operator UUID (TASK-MONO-023c).
        Integer denied = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM admin_actions
                WHERE actor_id = ? AND outcome = 'DENIED' AND target_type = 'ORG_NODE' AND target_id = 'other'
                """, Integer.class, ORG_ADMIN_UUID);
        assertThat(denied).isPositive();
    }

    @Test
    @DisplayName("ORG_ADMIN @ biz may not edit biz's OWN ceiling → 403 ORG_NODE_SELF_CEILING_DENIED (AWS SCP parity)")
    void selfCeilingEdit_is403() throws Exception {
        mockMvc.perform(put("/api/admin/org-nodes/biz/ceiling")
                        .header("Authorization", bearer(ORG_ADMIN_UUID))
                        .header("X-Operator-Reason", "tighten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mode":"BOUNDED","domains":["wms"]}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ORG_NODE_SELF_CEILING_DENIED"));
    }

    @Test
    @DisplayName("ROOT creation (parentId=null) is SUPER_ADMIN-only → ORG_ADMIN gets 403")
    void rootCreation_isPlatformOnly() throws Exception {
        mockMvc.perform(post("/api/admin/org-nodes")
                        .header("Authorization", bearer(ORG_ADMIN_UUID))
                        .header("X-Operator-Reason", "new corp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"New Corp","parentId":null,"ceiling":{"mode":"UNBOUNDED"}}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    // ── the subtree driver on the D2 administration surface (AC-3, AC-4) ────────

    @Test
    @DisplayName("ORG_ADMIN @ biz administers a tenant INSIDE the subtree (200) and none outside it (403)")
    void orgAdminReachesSubtreeTenantsOnly() throws Exception {
        // acme-wms is under biz → in scope.
        mockMvc.perform(patch("/api/admin/operators/" + IN_SUBTREE_UUID + "/status")
                        .header("Authorization", bearer(ORG_ADMIN_UUID))
                        .header("X-Operator-Reason", "offboarding")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"SUSPENDED"}
                                """))
                .andExpect(status().isOk());

        // globex is not under biz → out of scope, and the D2 gate (not the org axis) denies it.
        mockMvc.perform(patch("/api/admin/operators/" + OUT_SUBTREE_UUID + "/status")
                        .header("Authorization", bearer(ORG_ADMIN_UUID))
                        .header("X-Operator-Reason", "offboarding")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"SUSPENDED"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_SCOPE_DENIED"));
    }

    @Test
    @DisplayName("AC-5 fail-closed: account-service down → ORG_ADMIN loses reach; SUPER_ADMIN keeps platform reach")
    void authorityDown_orgAdminLosesReach_superAdminUnaffected() throws Exception {
        subtreeResolver.invalidateAll();
        when(orgNodePort.subtreeTenantIds("biz"))
                .thenThrow(new DownstreamFailureException("account-service unavailable", null));

        // The subtree resolves to the EMPTY set — never '*', never all tenants. The company
        // admin is cut off rather than silently promoted.
        mockMvc.perform(patch("/api/admin/operators/" + IN_SUBTREE_UUID + "/status")
                        .header("Authorization", bearer(ORG_ADMIN_UUID))
                        .header("X-Operator-Reason", "offboarding")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"SUSPENDED"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_SCOPE_DENIED"));

        // The '*' pre-scan never touches the subtree resolver, so a SUPER_ADMIN is untouched
        // by the outage. (An in-loop short-circuit would have stripped its platform reach.)
        mockMvc.perform(patch("/api/admin/operators/" + OUT_SUBTREE_UUID + "/status")
                        .header("Authorization", bearer(SUPER_UUID))
                        .header("X-Operator-Reason", "offboarding")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"SUSPENDED"}
                                """))
                .andExpect(status().isOk());
    }

    // ── grant / revoke (AC-6) ───────────────────────────────────────────────────

    @Test
    @DisplayName("SUPER_ADMIN grants ORG_ADMIN @ biz: node-scoped row written, tenant_id mirrors the target's own tenant")
    void grantNodeAdmin_writesNodeScopedRow() throws Exception {
        mockMvc.perform(post("/api/admin/org-nodes/biz/admins")
                        .header("Authorization", bearer(SUPER_UUID))
                        .header("X-Operator-Reason", "delegate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"operatorId":"%s","roleName":"ORG_ADMIN"}
                                """.formatted(GRANT_TARGET_UUID)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orgNodeId").value("biz"));

        String tenantId = jdbcTemplate.queryForObject("""
                SELECT aor.tenant_id FROM admin_operator_roles aor
                JOIN admin_operators o ON o.id = aor.operator_id
                JOIN admin_roles r ON r.id = aor.role_id
                WHERE o.operator_id = ? AND r.name = 'ORG_ADMIN' AND aor.org_node_id = 'biz'
                """, String.class, GRANT_TARGET_UUID);
        assertThat(tenantId).isEqualTo("acme-hq");

        Integer audited = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM admin_actions
                WHERE action_code = 'ORG_ADMIN_GRANT' AND target_type = 'ORG_NODE' AND target_id = 'biz'
                """, Integer.class);
        assertThat(audited).isPositive();
    }

    @Test
    @DisplayName("AC-6: SUPER_ADMIN is never mintable at a node → 403 ROLE_GRANT_FORBIDDEN")
    void grantSuperAdmin_isForbidden() throws Exception {
        // The actor here is the ORG_ADMIN (a non-platform actor), so RoleGrantGuard applies.
        mockMvc.perform(post("/api/admin/org-nodes/biz/admins")
                        .header("Authorization", bearer(ORG_ADMIN_UUID))
                        .header("X-Operator-Reason", "escalate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"operatorId":"%s","roleName":"SUPER_ADMIN"}
                                """.formatted(GRANT_TARGET_UUID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ROLE_GRANT_FORBIDDEN"));
    }

    @Test
    @DisplayName("AC-6: a node whose effective ceiling permits nothing refuses the grant → 422")
    void grantOnEmptyCeilingNode_is422() throws Exception {
        when(orgNodePort.effectiveCeiling("biz")).thenReturn(CeilingView.bounded(List.of()));

        mockMvc.perform(post("/api/admin/org-nodes/biz/admins")
                        .header("Authorization", bearer(SUPER_UUID))
                        .header("X-Operator-Reason", "delegate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"operatorId":"%s","roleName":"ORG_ADMIN"}
                                """.formatted(GRANT_TARGET_UUID)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ORG_ADMIN_GRANT_OUT_OF_CEILING"));
    }

    @Test
    @DisplayName("AC-6 fail-closed: an unresolvable ceiling denies the grant rather than falling back to UNBOUNDED")
    void grantWithUnresolvableCeiling_is422() throws Exception {
        when(orgNodePort.effectiveCeiling("biz"))
                .thenThrow(new DownstreamFailureException("account-service unavailable", null));

        mockMvc.perform(post("/api/admin/org-nodes/biz/admins")
                        .header("Authorization", bearer(SUPER_UUID))
                        .header("X-Operator-Reason", "delegate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"operatorId":"%s","roleName":"ORG_ADMIN"}
                                """.formatted(GRANT_TARGET_UUID)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ORG_ADMIN_GRANT_OUT_OF_CEILING"));
    }

    @Test
    @DisplayName("reads write no audit row (BE-486 read-path convention)")
    void readsAreNotAudited() throws Exception {
        Integer before = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM admin_actions WHERE target_type = 'ORG_NODE'", Integer.class);

        mockMvc.perform(get("/api/admin/org-nodes").header("Authorization", bearer(SUPER_UUID)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/org-nodes/biz").header("Authorization", bearer(SUPER_UUID)))
                .andExpect(status().isOk());

        Integer after = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM admin_actions WHERE target_type = 'ORG_NODE'", Integer.class);
        assertThat(after).isEqualTo(before);
    }

    // ── fixtures ────────────────────────────────────────────────────────────────

    private String bearer(String operatorUuid) {
        return "Bearer " + jwt.operatorToken(operatorUuid);
    }

    private void seedOperator(String uuid, String tenantId, String email, String roleName) {
        Integer existing = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM admin_operators WHERE operator_id = ?", Integer.class, uuid);
        if (existing == null || existing == 0) {
            jdbcTemplate.update("""
                    INSERT INTO admin_operators
                      (operator_id, tenant_id, email, password_hash, display_name, status,
                       created_at, updated_at, version)
                    VALUES (?, ?, ?, 'x', ?, 'ACTIVE', NOW(6), NOW(6), 0)
                    """, uuid, tenantId, email, "Test Op");
        } else {
            // Re-activate: a previous test in THIS class may have SUSPENDED this operator.
            jdbcTemplate.update(
                    "UPDATE admin_operators SET status = 'ACTIVE' WHERE operator_id = ?", uuid);
        }
        // Fail loudly if some other test class owns this operator id (case-insensitive collation!).
        // Without this, a colliding SUPER_ADMIN silently inherits a concrete tenant_id instead of
        // '*' and the platform-scope assertions below fail for a reason that looks nothing like
        // the real cause.
        String actualTenant = jdbcTemplate.queryForObject(
                "SELECT tenant_id FROM admin_operators WHERE operator_id = ?", String.class, uuid);
        assertThat(actualTenant)
                .as("operator %s must be owned by this test class (tenant_id skew ⇒ uuid collision)", uuid)
                .isEqualTo(tenantId);
        if (roleName != null) {
            jdbcTemplate.update("""
                    INSERT IGNORE INTO admin_operator_roles (operator_id, role_id, tenant_id, granted_at, granted_by)
                    SELECT o.id, r.id, o.tenant_id, NOW(6), NULL
                      FROM admin_operators o CROSS JOIN admin_roles r
                     WHERE o.operator_id = ? AND r.name = ?
                    """, uuid, roleName);
        }
    }
}
