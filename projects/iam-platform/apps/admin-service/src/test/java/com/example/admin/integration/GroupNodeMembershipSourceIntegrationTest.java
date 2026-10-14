package com.example.admin.integration;

import com.example.admin.application.orgnode.CeilingView;
import com.example.admin.application.orgnode.OrgNodeView;
import com.example.admin.application.port.OrgNodePort;
import com.example.admin.infrastructure.persistence.rbac.OrgNodeSubtreeResolver;
import com.example.admin.support.OperatorJwtTestFixture;
import com.example.testsupport.integration.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-BE-636 — the paths TASK-BE-633 left single-sourced, against the real migrated MySQL schema and the real HTTP
 * surface (copies {@link AdminRoleSourceIntegrationTest}'s method).
 *
 * <p><b>AC-0 — written to be RED on the pre-BE-636 code</b>. The first assertion at each defect reads only columns
 * and rows that exist before AND after any per-source membership schema change (row counts of
 * {@code operator_tenant_assignment} / {@code operator_domain_set_grant} / {@code admin_operator_roles}, and
 * {@code group_origin}), so on the old code each fails as an ASSERTION at the defect, never as an SQL error:
 * <ul>
 *   <li>① G1 · G2 both give tenant U by TENANT_ASSIGNMENT; revoking G1 (grant revoke · member removal · group
 *       deletion) must leave U to the member. Old code: G2's fan-out was a single-owner skip
 *       ({@code GroupFanOutService} materialise {@code assignments.existsById((operator, tenant))} → skip), so G1's
 *       revoke deleted the one row.</li>
 *   <li>② the person holds ONLY domain permission set rows in U (no role row) and U comes from G's TENANT_ASSIGNMENT;
 *       G's revoke must not leave a set row outside the person's memberships (784 D-1 sibling). Old code: the BE-633
 *       «keep as direct» rule counts role rows only ({@code operatorRoles.existsByOperatorIdAndTenantId}).</li>
 *   <li>③ the target of an org-node grant whose home tenant is NOT one of its memberships (since TASK-MONO-784 —
 *       built here through «이 회사에서 내보내기» of the home company while a member of another): after the grant,
 *       no non-{@code '*'} role row of the target lies outside its memberships. Old code: the node row is written to
 *       {@code target.getTenantId()} (the home).</li>
 * </ul>
 *
 * <p>Every operator / tenant / group / node id is unique to this class (the IT suite shares one MySQL container).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@Tag("integration")
class GroupNodeMembershipSourceIntegrationTest extends AbstractIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)
            .withStartupTimeout(Duration.ofMinutes(3));

    static OperatorJwtTestFixture jwt;
    static String signingKeyPem;
    static final ObjectMapper MAPPER = new ObjectMapper();

    static final String SUPER = "00000000-0000-7000-8000-000000636a00";
    /** A system preset (V0052) — a set row needs no tenant-owned set and no entitlement call. */
    static final String PRESET = "wms-inventory-read";
    static final String NODE = "be636-node";

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
        // Nothing listens here: these flows make no downstream call (the org tree is the mocked OrgNodePort).
        registry.add("admin.auth-service.base-url", () -> "http://localhost:18090");
        registry.add("admin.account-service.base-url", () -> "http://localhost:18090");
        registry.add("admin.security-service.base-url", () -> "http://localhost:18090");
    }

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired OrgNodeSubtreeResolver subtreeResolver;

    /** account-service owns the org tree — the seam, as in {@code OrgNodeAdminIntegrationTest}. */
    @MockitoBean OrgNodePort orgNodePort;

    @BeforeEach
    void orgTree() {
        subtreeResolver.invalidateAll();
        OrgNodeView node = new OrgNodeView(NODE, null, "n-" + NODE, 1, CeilingView.unbounded(),
                Instant.EPOCH, Instant.EPOCH);
        when(orgNodePort.list()).thenReturn(List.of(node));
        when(orgNodePort.get(NODE)).thenReturn(node);
        when(orgNodePort.subtreeTenantIds(NODE)).thenReturn(List.of());
        when(orgNodePort.effectiveCeiling(anyString())).thenReturn(CeilingView.unbounded());
    }

    // ── ① AC-1 — two groups give the same tenant ──────────────────────────────

    /** Home H; G1 · G2 of H both carry TENANT_ASSIGNMENT(U); M joins G1 FIRST (G1 owns the old single row), then G2. */
    private TwoGroups twoGroupsFixture(String n) throws Exception {
        String home = "be636-h1" + n;
        String u = "be636-u1" + n;
        String m = "00000000-0000-7000-8000-000000636b" + n + "1";
        seedSuper();
        seedOperator(m, home);
        String g1 = createGroup(home, "be636 two G1-" + n);
        String g2 = createGroup(home, "be636 two G2-" + n);
        String g1Grant = grantTenantAssignment(g1, u);
        String g2Grant = grantTenantAssignment(g2, u);
        addMember(g1, m).andExpect(jsonPath("$.fannedOutGrants").value(1));
        addMember(g2, m); // the count is the fix's to define (BE-633 counted «new to the member» only)
        assertThat(membershipCount(m, u)).as("precondition: M is a member of U").isPositive();
        assertThat(membershipOrigins(m, u)).as("precondition: G1 gives U").contains(gid(g1));
        assertThat(orphanRoleRows(m)).isZero();
        return new TwoGroups(m, u, g1, g2, g1Grant, g2Grant);
    }

    private record TwoGroups(String m, String u, String g1, String g2, String g1Grant, String g2Grant) {}

    @Test
    @DisplayName("🔴 ①/AC-1: G1 · G2 give U → G1's TENANT_ASSIGNMENT revoked → U kept → G2's revoked → U gone")
    void ac1_twoGroups_tenantAssignmentRevoke() throws Exception {
        TwoGroups s = twoGroupsFixture("1");

        revokeGrant(s.g1(), s.g1Grant()).andExpect(status().isNoContent());
        assertThat(membershipCount(s.m(), s.u()))
                .as("🔴 ①: G2 still gives U — revoking G1's TENANT_ASSIGNMENT must not take it").isPositive();

        revokeGrant(s.g2(), s.g2Grant()).andExpect(status().isNoContent());
        assertThat(membershipCount(s.m(), s.u())).as("no source of U left").isZero();
    }

    @Test
    @DisplayName("🔴 ①/AC-1: G1 · G2 give U → M leaves G1 → U kept → M leaves G2 → U gone")
    void ac1_twoGroups_memberRemoval() throws Exception {
        TwoGroups s = twoGroupsFixture("2");

        removeMember(s.g1(), s.m()).andExpect(status().isNoContent());
        assertThat(membershipCount(s.m(), s.u()))
                .as("🔴 ①: G2 still gives U — leaving G1 must not take it").isPositive();

        removeMember(s.g2(), s.m()).andExpect(status().isNoContent());
        assertThat(membershipCount(s.m(), s.u())).as("no source of U left").isZero();
    }

    @Test
    @DisplayName("🔴 ①/AC-1: G1 · G2 give U → G1 deleted → U kept → G2 deleted → U gone")
    void ac1_twoGroups_groupDeletion() throws Exception {
        TwoGroups s = twoGroupsFixture("3");

        deleteGroup(s.g1()).andExpect(status().isNoContent());
        assertThat(membershipCount(s.m(), s.u()))
                .as("🔴 ①: G2 still gives U — deleting G1 must not take it").isPositive();

        deleteGroup(s.g2()).andExpect(status().isNoContent());
        assertThat(membershipCount(s.m(), s.u())).as("no source of U left").isZero();
    }

    @Test
    @DisplayName("AC-1 control: a DIRECT membership of U + G1's TENANT_ASSIGNMENT(U) → G1's revoked → the direct membership survives")
    void ac1_control_directPlusGroup() throws Exception {
        String home = "be636-h14";
        String u = "be636-u14";
        String m = "00000000-0000-7000-8000-000000636b41";
        seedSuper();
        seedOperator(m, home);
        assign(m, u).andExpect(status().isCreated());
        String g1 = createGroup(home, "be636 ctl G1");
        String g1Grant = grantTenantAssignment(g1, u);
        addMember(g1, m);

        revokeGrant(g1, g1Grant).andExpect(status().isNoContent());
        assertThat(directMembershipCount(m, u)).as("the direct membership is not the group's to take").isEqualTo(1);
    }

    @Test
    @DisplayName("AC-1 edge: DIRECT + G1 + G2 give U → both groups' TENANT_ASSIGNMENT revoked → the direct membership stays")
    void ac1_edge_directPlusTwoGroups() throws Exception {
        String home = "be636-h15";
        String u = "be636-u15";
        String m = "00000000-0000-7000-8000-000000636b51";
        seedSuper();
        seedOperator(m, home);
        assign(m, u).andExpect(status().isCreated());
        String g1 = createGroup(home, "be636 edge G1");
        String g2 = createGroup(home, "be636 edge G2");
        String g1Grant = grantTenantAssignment(g1, u);
        String g2Grant = grantTenantAssignment(g2, u);
        addMember(g1, m);
        addMember(g2, m);

        revokeGrant(g1, g1Grant).andExpect(status().isNoContent());
        assertThat(membershipCount(m, u)).isPositive();
        revokeGrant(g2, g2Grant).andExpect(status().isNoContent());
        assertThat(directMembershipCount(m, u)).as("the direct source of U remains").isEqualTo(1);
        assertThat(membershipCount(m, u)).as("only the direct source of U remains").isEqualTo(1);
    }

    // ── ② AC-2 — domain permission set rows without a membership ──────────────

    /**
     * Home H; G of H carries TENANT_ASSIGNMENT(U); two people join G and so become members of U. One of them then
     * holds a DIRECT domain permission set in U — and NO admin role row anywhere in U. The other (control) holds
     * nothing in U.
     *
     * <p>The set row is inserted by fixture: the API path ({@code POST /api/admin/operators/{id}/domain-permission-sets}
     * with {@code X-Tenant-Id: U}) reaches exactly this state — its only membership check is «U ∈ the target's
     * memberships», which a group-given membership satisfies ({@code OperatorDomainAccessUseCase.requireMembership})
     * — but it calls account-service for the entitled domains, which this class does not stand up.
     */
    private SetOnly setOnlyFixture(String n) throws Exception {
        String home = "be636-h2" + n;
        String u = "be636-u2" + n;
        String withSet = "00000000-0000-7000-8000-000000636c" + n + "1";
        String without = "00000000-0000-7000-8000-000000636c" + n + "2";
        seedSuper();
        seedOperator(withSet, home);
        seedOperator(without, home);
        String g = createGroup(home, "be636 set G" + n);
        String taGrant = grantTenantAssignment(g, u);
        addMember(g, withSet).andExpect(jsonPath("$.fannedOutGrants").value(1));
        addMember(g, without).andExpect(jsonPath("$.fannedOutGrants").value(1));
        assertThat(membershipOrigins(withSet, u)).as("precondition: U membership from G").containsExactly(gid(g));
        jdbc.update("""
                INSERT INTO operator_domain_set_grant
                  (operator_id, tenant_id, set_id, source_key, group_origin, granted_by, granted_at)
                SELECT o.id, ?, s.id, 'DIRECT', NULL, NULL, NOW(6)
                  FROM admin_operators o, domain_permission_set s
                 WHERE o.operator_id = ? AND s.system_key = ?
                """, u, withSet, PRESET);
        assertThat(setRows(withSet, u)).as("precondition: a direct set row in U").isEqualTo(1);
        assertThat(roleRowsIn(withSet, u)).as("precondition: NO role row in U — set rows only").isZero();
        assertThat(orphanSetRows(withSet)).as("precondition: invariant holds").isZero();
        return new SetOnly(g, taGrant, withSet, without, u);
    }

    private record SetOnly(String group, String taGrant, String withSet, String without, String u) {}

    /** D-1 first (decision-neutral), then the default decision (BE-633 owner decision 2: keep as direct). */
    private void assertNoOrphanSetRows(SetOnly s) {
        assertThat(orphanSetRows(s.withSet()))
                .as("🔴 ② (784 D-1 sibling): no domain permission set row outside the person's memberships").isZero();
        assertThat(membershipCount(s.withSet(), s.u()))
                .as("default decision (BE-633 owner decision 2): U kept as a membership — the set row backs it")
                .isPositive();
        assertThat(setRows(s.withSet(), s.u())).as("the direct set row is not the group's to take").isEqualTo(1);
        assertThat(membershipCount(s.without(), s.u())).as("control: nothing in U → G's U membership goes").isZero();
        assertThat(orphanSetRows(s.without())).isZero();
    }

    @Test
    @DisplayName("🔴 ②/AC-2: set rows only in U (via G's TENANT_ASSIGNMENT) → G's TENANT_ASSIGNMENT revoked → no set row without a membership")
    void ac2_setOnly_tenantAssignmentRevoke() throws Exception {
        SetOnly s = setOnlyFixture("1");
        revokeGrant(s.group(), s.taGrant()).andExpect(status().isNoContent());
        assertNoOrphanSetRows(s);
    }

    @Test
    @DisplayName("🔴 ②/AC-2: set rows only in U (via G) → the person leaves G → no set row without a membership")
    void ac2_setOnly_memberRemoval() throws Exception {
        SetOnly s = setOnlyFixture("2");
        removeMember(s.group(), s.withSet()).andExpect(status().isNoContent());
        removeMember(s.group(), s.without()).andExpect(status().isNoContent());
        assertNoOrphanSetRows(s);
    }

    @Test
    @DisplayName("🔴 ②/AC-2: set rows only in U (via G) → G deleted → no set row without a membership")
    void ac2_setOnly_groupDeletion() throws Exception {
        SetOnly s = setOnlyFixture("3");
        deleteGroup(s.group()).andExpect(status().isNoContent());
        assertNoOrphanSetRows(s);
    }

    /**
     * A fourth path the ticket does not list: «이 회사에서 내보내기» of H takes G (a group of H) with it, and
     * {@code OperatorCompanyExitCascade} keeps G's U membership as direct only when a ROLE row remains in U — the same
     * role-only rule, so the set-only person's U membership goes and the U set row is left behind.
     */
    @Test
    @DisplayName("🔴 ②/AC-2 (exit path): set rows only in U (via G of H) → the person is removed from H → no set row without a membership")
    void ac2_setOnly_companyExitOfGroupTenant() throws Exception {
        SetOnly s = setOnlyFixture("4");
        String home = "be636-h24";

        exit(s.withSet(), home).andExpect(status().isNoContent());

        assertThat(orphanSetRows(s.withSet()))
                .as("🔴 ② (784 D-1 sibling): no domain permission set row outside the person's memberships").isZero();
        assertThat(membershipCount(s.withSet(), s.u()))
                .as("default decision (owner 2026-10-13 «U 소속을 직접 소속으로 남김»): U kept — the set row backs it")
                .isPositive();
        assertThat(setRows(s.withSet(), s.u())).as("U is not the exited company — its set row stays").isEqualTo(1);
    }

    // ── ③ AC-3 — a node grant to a target whose home is not a membership ──────

    /**
     * An operator of home H that is a member of U, then is removed from H through «이 회사에서 내보내기»
     * ({@code DELETE …/assignments/H}). The operator row keeps {@code tenant_id = H} (TASK-MONO-784 D-10 — the exit
     * never touches the operator row), so its home is no longer one of its memberships. Built through the real API.
     */
    private String homeNotMembershipFixture(String n) throws Exception {
        String home = "be636-h3" + n;
        String u = "be636-u3" + n;
        String x = "00000000-0000-7000-8000-000000636d" + n + "1";
        seedSuper();
        seedOperator(x, home);
        assign(x, u).andExpect(status().isCreated());
        exit(x, home).andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("SELECT tenant_id FROM admin_operators WHERE operator_id = ?",
                String.class, x)).as("precondition: the home stays H (D-10)").isEqualTo(home);
        assertThat(membershipCount(x, home)).as("precondition: H is NOT a membership").isZero();
        assertThat(membershipCount(x, u)).as("precondition: U is a membership").isEqualTo(1);
        assertThat(orphanRoleRows(x)).as("precondition: invariant holds").isZero();
        return x;
    }

    @Test
    @DisplayName("🔴 ③/AC-3: node grant to a target whose home ∉ memberships → afterwards no role row outside its memberships (decision-neutral)")
    void ac3_nodeGrant_homeNotMembership_invariant() throws Exception {
        String x = homeNotMembershipFixture("1");

        // The HTTP status is deliberately not asserted here: this one holds under any fix direction.
        grantNodeAdmin(x);

        assertThat(orphanRoleRows(x))
                .as("🔴 ③ (784 D-1): no non-'*' role row of the target outside its memberships after a node grant")
                .isZero();
    }

    @Test
    @DisplayName("🔴 ③/AC-3 (owner decision 2026-10-14: refuse): node grant to a target whose home ∉ memberships → 422, no row, no membership added")
    void ac3_nodeGrant_homeNotMembership_refused() throws Exception {
        String x = homeNotMembershipFixture("2");
        String home = "be636-h32";

        int status = grantNodeAdmin(x).andReturn().getResponse().getStatus();

        assertThat(nodeRows(x)).as("🔴 ③ refuse: no node row written").isZero();
        assertThat(status).as("🔴 ③ refuse: 422").isEqualTo(422);
        assertThat(membershipCount(x, home)).as("refuse: no membership added").isZero();
    }

    @Test
    @DisplayName("AC-3 control: node grant to an ordinary operator (home = a membership) → 201, row at the home, invariant holds")
    void ac3_control_homeIsMembership() throws Exception {
        String home = "be636-h33";
        String x = "00000000-0000-7000-8000-000000636d31";
        seedSuper();
        seedOperator(x, home);

        grantNodeAdmin(x).andExpect(status().isCreated());

        assertThat(nodeRows(x)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT b.tenant_id FROM admin_operator_roles b JOIN admin_operators o ON o.id = b.operator_id
                 WHERE o.operator_id = ? AND b.org_node_id = ?
                """, String.class, x, NODE)).as("usual case byte-unchanged: the row lives at the home").isEqualTo(home);
        assertThat(orphanRoleRows(x)).isZero();
    }

    // ── requests ──────────────────────────────────────────────────────────────

    private void seedSuper() {
        seedOperator(SUPER, "*");
        jdbc.update("""
                INSERT IGNORE INTO admin_operator_roles (operator_id, role_id, tenant_id, granted_at, granted_by)
                SELECT o.id, r.id, '*', NOW(6), NULL
                  FROM admin_operators o CROSS JOIN admin_roles r
                 WHERE o.operator_id = ? AND r.name = 'SUPER_ADMIN'
                """, SUPER);
    }

    private String createGroup(String tenant, String name) throws Exception {
        String body = mockMvc.perform(post("/api/admin/groups")
                        .header("Authorization", bearer(SUPER))
                        .header("X-Operator-Reason", "be636 group")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\":\"" + tenant + "\",\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return MAPPER.readTree(body).get("groupId").asText();
    }

    /** @return the grant id */
    private String grantTenantAssignment(String groupId, String tenant) throws Exception {
        String body = mockMvc.perform(post("/api/admin/groups/" + groupId + "/grants")
                        .header("Authorization", bearer(SUPER))
                        .header("X-Operator-Reason", "be636 grant")
                        .header("Idempotency-Key", "be636-grant-" + System.nanoTime())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantAssignments\":[{\"tenantId\":\"" + tenant + "\"}]}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return MAPPER.readTree(body).get("items").get(0).get("grantId").asText();
    }

    private ResultActions addMember(String groupId, String operator) throws Exception {
        return mockMvc.perform(post("/api/admin/groups/" + groupId + "/members")
                        .header("Authorization", bearer(SUPER))
                        .header("X-Operator-Reason", "be636 member")
                        .header("Idempotency-Key", "be636-member-" + System.nanoTime())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"operatorId\":\"" + operator + "\"}"))
                .andExpect(status().isCreated());
    }

    private ResultActions removeMember(String groupId, String operator) throws Exception {
        return mockMvc.perform(delete("/api/admin/groups/" + groupId + "/members/" + operator)
                .header("Authorization", bearer(SUPER))
                .header("X-Operator-Reason", "be636 leave"));
    }

    private ResultActions revokeGrant(String groupId, String grantId) throws Exception {
        return mockMvc.perform(delete("/api/admin/groups/" + groupId + "/grants/" + grantId)
                .header("Authorization", bearer(SUPER))
                .header("X-Operator-Reason", "be636 revoke"));
    }

    private ResultActions deleteGroup(String groupId) throws Exception {
        return mockMvc.perform(delete("/api/admin/groups/" + groupId)
                .header("Authorization", bearer(SUPER))
                .header("X-Operator-Reason", "be636 disband"));
    }

    /** {@code POST /api/admin/operators/{id}/assignments/{tenant}} — a DIRECT membership. */
    private ResultActions assign(String operator, String tenant) throws Exception {
        return mockMvc.perform(post("/api/admin/operators/" + operator + "/assignments/" + tenant)
                .header("Authorization", bearer(SUPER))
                .header("X-Operator-Reason", "be636 assign"));
    }

    /** «이 회사에서 내보내기» — {@code DELETE /api/admin/operators/{id}/assignments/{tenant}}. */
    private ResultActions exit(String operator, String tenant) throws Exception {
        return mockMvc.perform(delete("/api/admin/operators/" + operator + "/assignments/" + tenant)
                .header("Authorization", bearer(SUPER))
                .header("X-Operator-Reason", "be636 exit"));
    }

    private ResultActions grantNodeAdmin(String target) throws Exception {
        return mockMvc.perform(post("/api/admin/org-nodes/" + NODE + "/admins")
                .header("Authorization", bearer(SUPER))
                .header("X-Operator-Reason", "be636 delegate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"operatorId\":\"" + target + "\",\"roleName\":\"ORG_ADMIN\"}"));
    }

    private String bearer(String operatorUuid) {
        return "Bearer " + jwt.operatorToken(operatorUuid);
    }

    // ── fixture / reads (schema-neutral: no column a per-source membership change could add) ──

    /** An ordinary operator of {@code home} with its home membership (no role); {@code '*'} → no membership. */
    private void seedOperator(String uuid, String home) {
        jdbc.update("""
                INSERT IGNORE INTO admin_operators
                  (operator_id, tenant_id, email, password_hash, display_name, status,
                   created_at, updated_at, version)
                VALUES (?, ?, ?, 'x', 'BE-636 Op', 'ACTIVE', NOW(6), NOW(6), 0)
                """, uuid, home, uuid + "@be636.example.com");
        assertThat(jdbc.queryForObject("SELECT tenant_id FROM admin_operators WHERE operator_id = ?",
                String.class, uuid)).as("operator %s owned by this class", uuid).isEqualTo(home);
        MembershipFixture.addHomeMembership(jdbc, uuid);
    }

    private String gid(String groupId) {
        return String.valueOf(jdbc.queryForObject(
                "SELECT id FROM operator_group WHERE group_id = ?", Long.class, groupId));
    }

    /** Rows of {@code operator_tenant_assignment} for (operator, tenant) — any source. */
    private int membershipCount(String uuid, String tenant) {
        return count("""
                SELECT COUNT(*) FROM operator_tenant_assignment a JOIN admin_operators o ON o.id = a.operator_id
                 WHERE o.operator_id = ? AND a.tenant_id = ?
                """, uuid, tenant);
    }

    /** Direct ({@code group_origin IS NULL}) membership rows for (operator, tenant). */
    private int directMembershipCount(String uuid, String tenant) {
        return count("""
                SELECT COUNT(*) FROM operator_tenant_assignment a JOIN admin_operators o ON o.id = a.operator_id
                 WHERE o.operator_id = ? AND a.tenant_id = ? AND a.group_origin IS NULL
                """, uuid, tenant);
    }

    /** The {@code group_origin} of each membership row of (operator, tenant), {@code "-"} for a direct one. */
    private List<String> membershipOrigins(String uuid, String tenant) {
        return jdbc.queryForList("""
                SELECT COALESCE(CAST(a.group_origin AS CHAR), '-') FROM operator_tenant_assignment a
                  JOIN admin_operators o ON o.id = a.operator_id
                 WHERE o.operator_id = ? AND a.tenant_id = ?
                """, String.class, uuid, tenant);
    }

    private int roleRowsIn(String uuid, String tenant) {
        return count("""
                SELECT COUNT(*) FROM admin_operator_roles b JOIN admin_operators o ON o.id = b.operator_id
                 WHERE o.operator_id = ? AND b.tenant_id = ?
                """, uuid, tenant);
    }

    private int nodeRows(String uuid) {
        return count("""
                SELECT COUNT(*) FROM admin_operator_roles b JOIN admin_operators o ON o.id = b.operator_id
                 WHERE o.operator_id = ? AND b.org_node_id = ?
                """, uuid, NODE);
    }

    private int setRows(String uuid, String tenant) {
        return count("""
                SELECT COUNT(*) FROM operator_domain_set_grant g JOIN admin_operators o ON o.id = g.operator_id
                 WHERE o.operator_id = ? AND g.tenant_id = ?
                """, uuid, tenant);
    }

    /** TASK-MONO-784 D-1: the operator's non-'*' role rows whose tenant is none of its memberships. */
    private int orphanRoleRows(String uuid) {
        return count("""
                SELECT COUNT(*) FROM admin_operator_roles b
                  JOIN admin_operators o ON o.id = b.operator_id
                 WHERE o.operator_id = ? AND b.tenant_id <> '*'
                   AND NOT EXISTS (SELECT 1 FROM operator_tenant_assignment a
                                    WHERE a.operator_id = b.operator_id AND a.tenant_id = b.tenant_id)
                """, uuid);
    }

    /** D-1 sibling: the operator's non-'*' domain permission set rows whose tenant is none of its memberships. */
    private int orphanSetRows(String uuid) {
        return count("""
                SELECT COUNT(*) FROM operator_domain_set_grant g
                  JOIN admin_operators o ON o.id = g.operator_id
                 WHERE o.operator_id = ? AND g.tenant_id <> '*'
                   AND NOT EXISTS (SELECT 1 FROM operator_tenant_assignment a
                                    WHERE a.operator_id = g.operator_id AND a.tenant_id = g.tenant_id)
                """, uuid);
    }

    private int count(String sql, Object... args) {
        Integer n = jdbc.queryForObject(sql, Integer.class, args);
        return n == null ? 0 : n;
    }
}
