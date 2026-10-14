package com.example.admin.integration;

import com.example.admin.support.OperatorJwtTestFixture;
import com.example.testsupport.integration.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
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
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-BE-633 — one admin-role row per SOURCE (V0053), against the real migrated MySQL schema, the real JPA merge
 * behaviour and the real HTTP surface. Unit tests cannot see either of the two defects this class pins: both are
 * properties of the PRIMARY KEY and of {@code SimpleJpaRepository.save} → {@code EntityManager.merge} on an entity
 * with an assigned composite id.
 *
 * <p><b>AC-0 — written to be RED on the pre-V0053 code</b> (the «measure first» half; the first assertion of each
 * test that touches the defect is schema-neutral — it reads {@code group_origin} / row counts, columns that exist
 * before and after V0053 — so on the old code it fails as an ASSERTION at the defect, not as an SQL error):
 * <ul>
 *   <li>(a) G1 · G2 grant the same role, the member leaves G1 → the role must stay. Old code: G2's fan-out was an
 *       idempotent skip (G1 owned the one row), so leaving G1 deleted it — {@code GroupFanOutService} materialise
 *       {@code existsById(3-column key)} → skip, and {@code deleteByGroupOriginAndOperatorId}.</li>
 *   <li>(b) a direct role PATCH over an existing group row of the same (operator, role, tenant) must not turn the
 *       group row into a direct one. Old code: {@code deleteDirectByOperatorIdAndTenantId} spares the group row,
 *       then {@code saveAll} of an entity whose 3-column id equals it is {@code merge} (no {@code Persistable}), so
 *       the group row's {@code group_origin} is overwritten with NULL — no audit names the source change.</li>
 *   <li>(c) a group's fan-out membership into company U removed by a NON-export path (TENANT_ASSIGNMENT revoke ·
 *       member removal · group deletion) must not leave the person's direct U role behind without a membership
 *       (TASK-MONO-784 D-1). Old code: {@code assignments.deleteByGroupOrigin…} deletes it unconditionally.</li>
 * </ul>
 *
 * <p>Every operator / tenant / group name is unique to this class (the IT suite shares one MySQL container).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@Tag("integration")
class AdminRoleSourceIntegrationTest extends AbstractIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)
            .withStartupTimeout(Duration.ofMinutes(3));

    static OperatorJwtTestFixture jwt;
    static String signingKeyPem;
    static final ObjectMapper MAPPER = new ObjectMapper();

    static final String SUPER = "00000000-0000-7000-8000-000000633a00";
    static final String ROLE = "SUPPORT_LOCK";
    static final String ADMIN_ROLE = "TENANT_ADMIN";

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
        // Nothing listens here: these flows make no downstream call.
        registry.add("admin.auth-service.base-url", () -> "http://localhost:18089");
        registry.add("admin.account-service.base-url", () -> "http://localhost:18089");
        registry.add("admin.security-service.base-url", () -> "http://localhost:18089");
    }

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;

    // ── AC-0 (a) · AC-1 — two groups, the same role ─────────────────────────────

    @Test
    @DisplayName("🔴 AC-0(a)/AC-1: G1 · G2 give the same role → leave G1 → role stays (one row per source, listed once) → leave G2 → gone")
    void twoGroupsSameRole_leaveOrder() throws Exception {
        String t = "be633-t1";
        String m = "00000000-0000-7000-8000-000000633a11";
        seedSuper();
        seedOperator(m, t);
        String g1 = createGroup(t, "be633 t1 G1");
        String g2 = createGroup(t, "be633 t1 G2");
        grantRole(g1, ROLE);
        grantRole(g2, ROLE);

        addMember(g1, m).andExpect(jsonPath("$.fannedOutGrants").value(1));
        // the role is not NEW to the member — the count says so — but G2's own row is written (BE-633)
        addMember(g2, m).andExpect(jsonPath("$.fannedOutGrants").value(0));
        assertThat(origins(m, ROLE, t)).as("one row per group").containsExactlyInAnyOrder(gid(g1), gid(g2));
        assertThat(sourceKeys(m, ROLE, t)).containsExactlyInAnyOrder("GROUP:" + gid(g1), "GROUP:" + gid(g2));

        // readers de-duplicate: /me and the '*' operator list name the role once
        assertThat(meRoles(m, t)).containsOnlyOnce(ROLE);
        assertThat(listedRolesAllTenants(m)).containsOnlyOnce(ROLE);

        removeMember(g1, m).andExpect(status().isNoContent());
        assertThat(holds(m, ROLE, t)).as("🔴 AC-0(a): G2 still grants the role — leaving G1 must not take it").isTrue();
        assertThat(origins(m, ROLE, t)).containsExactly(gid(g2));

        removeMember(g2, m).andExpect(status().isNoContent());
        assertThat(holds(m, ROLE, t)).as("no source left").isFalse();
    }

    @Test
    @DisplayName("🔴 Edge 1: direct + G1 + G2 → delete G1 → two sources stay → revoke G2's grant → direct stays → direct PATCHed away → gone")
    void directPlusTwoGroups_deleteGroup_revokeGrant_patch() throws Exception {
        String t = "be633-t2";
        String m = "00000000-0000-7000-8000-000000633a21";
        seedSuper();
        seedOperator(m, t);
        patchRoles(m, t, "[\"" + ROLE + "\"]").andExpect(status().isOk());
        String g1 = createGroup(t, "be633 t2 G1");
        String g2 = createGroup(t, "be633 t2 G2");
        grantRole(g1, ROLE);
        String g2Grant = grantRole(g2, ROLE);
        addMember(g1, m).andExpect(jsonPath("$.fannedOutGrants").value(0));
        addMember(g2, m).andExpect(jsonPath("$.fannedOutGrants").value(0));
        assertThat(origins(m, ROLE, t)).containsExactlyInAnyOrder("-", gid(g1), gid(g2));

        deleteGroup(g1).andExpect(status().isNoContent());
        assertThat(origins(m, ROLE, t)).as("the direct and G2 sources remain").containsExactlyInAnyOrder("-", gid(g2));

        revokeGrant(g2, g2Grant).andExpect(status().isNoContent());
        assertThat(origins(m, ROLE, t)).as("the direct source remains").containsExactly("-");

        patchRoles(m, t, "[]").andExpect(status().isOk());
        assertThat(holds(m, ROLE, t)).isFalse();
    }

    // ── AC-1 — direct + group, both orders ──────────────────────────────────────

    @Test
    @DisplayName("🔴 AC-1: direct then group → group removed → direct stays; group re-added → direct PATCHed away → group stays")
    void directThenGroup() throws Exception {
        String t = "be633-t3";
        String m = "00000000-0000-7000-8000-000000633a31";
        seedSuper();
        seedOperator(m, t);
        patchRoles(m, t, "[\"" + ROLE + "\"]").andExpect(status().isOk());
        String g = createGroup(t, "be633 t3 G");
        grantRole(g, ROLE);
        addMember(g, m).andExpect(jsonPath("$.fannedOutGrants").value(0));

        removeMember(g, m).andExpect(status().isNoContent());
        assertThat(origins(m, ROLE, t)).as("group revoke leaves the direct grant").containsExactly("-");

        addMember(g, m).andExpect(jsonPath("$.fannedOutGrants").value(0));
        patchRoles(m, t, "[]").andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value(ROLE)); // the group's role is still reported in T
        assertThat(holds(m, ROLE, t))
                .as("🔴 the group still grants the role — removing the direct grant must not take it").isTrue();
        assertThat(origins(m, ROLE, t)).containsExactly(gid(g));
    }

    @Test
    @DisplayName("🔴 AC-0(b)/AC-1/AC-2: group then direct PATCH → BOTH sources (group row untouched, no silent conversion) → direct removed → group stays → group removed → gone")
    void groupThenDirect_noSilentConversion() throws Exception {
        String t = "be633-t4";
        String m = "00000000-0000-7000-8000-000000633a41";
        seedSuper();
        seedOperator(m, t);
        String g = createGroup(t, "be633 t4 G");
        grantRole(g, ROLE);
        addMember(g, m).andExpect(jsonPath("$.fannedOutGrants").value(1));
        String groupRowBefore = groupRow(m, ROLE, t, gid(g));
        assertThat(groupRowBefore).as("precondition: the group row").isNotNull();

        patchRoles(m, t, "[\"" + ROLE + "\"]").andExpect(status().isOk())
                .andExpect(jsonPath("$.roles.length()").value(1))
                .andExpect(jsonPath("$.roles[0]").value(ROLE));

        assertThat(groupRow(m, ROLE, t, gid(g)))
                .as("🔴 AC-0(b)/AC-2: the group row is byte-unchanged — not merged into a direct row").isEqualTo(groupRowBefore);
        assertThat(origins(m, ROLE, t)).as("AC-2: the two sources coexist").containsExactlyInAnyOrder("-", gid(g));
        assertThat(sourceKeys(m, ROLE, t)).containsExactlyInAnyOrder("DIRECT", "GROUP:" + gid(g));

        patchRoles(m, t, "[]").andExpect(status().isOk());
        assertThat(origins(m, ROLE, t)).as("direct removed, group stays").containsExactly(gid(g));

        removeMember(g, m).andExpect(status().isNoContent());
        assertThat(holds(m, ROLE, t)).isFalse();
    }

    @Test
    @DisplayName("AC-2 sibling: a direct PATCH over an org-node row of the same role leaves the node row (and its scope) intact")
    void directOverNodeRow_coexists() throws Exception {
        String t = "be633-t5";
        String m = "00000000-0000-7000-8000-000000633a51";
        seedSuper();
        seedOperator(m, t);
        jdbc.update("""
                INSERT INTO admin_operator_roles
                  (operator_id, role_id, tenant_id, org_node_id, granted_at, granted_by, source_key)
                SELECT o.id, r.id, ?, 'be633-node', NOW(6), NULL, 'NODE'
                  FROM admin_operators o CROSS JOIN admin_roles r
                 WHERE o.operator_id = ? AND r.name = 'ORG_ADMIN'
                """, t, m);

        patchRoles(m, t, "[\"ORG_ADMIN\"]").andExpect(status().isOk());

        assertThat(jdbc.queryForList("""
                SELECT CONCAT_WS('|', b.source_key, COALESCE(b.org_node_id, '-'))
                  FROM admin_operator_roles b
                  JOIN admin_operators o ON o.id = b.operator_id
                  JOIN admin_roles r ON r.id = b.role_id
                 WHERE o.operator_id = ? AND r.name = 'ORG_ADMIN' AND b.tenant_id = ?
                """, String.class, m, t)).containsExactlyInAnyOrder("DIRECT|-", "NODE|be633-node");

        patchRoles(m, t, "[]").andExpect(status().isOk());
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM admin_operator_roles b JOIN admin_operators o ON o.id = b.operator_id
                 WHERE o.operator_id = ? AND b.org_node_id = 'be633-node'
                """, Integer.class, m)).as("the node grant is not the PATCH's to remove").isEqualTo(1);
    }

    // ── Edge 2 — the last-administrator guard (784) and «another source remains» ─

    @Test
    @DisplayName("🔴 Edge 2: the only admin holds TENANT_ADMIN via G1 + G2 → leaving G1 is allowed (G2 remains) → leaving G2 is 409")
    void lastAdmin_otherSourceRemains() throws Exception {
        String t = "be633-t6";
        String m = "00000000-0000-7000-8000-000000633a61";
        seedSuper();
        seedOperator(m, t);
        String g1 = createGroup(t, "be633 t6 G1");
        String g2 = createGroup(t, "be633 t6 G2");
        grantRole(g1, ADMIN_ROLE);
        grantRole(g2, ADMIN_ROLE);
        addMember(g1, m);
        addMember(g2, m);

        removeMember(g1, m).andExpect(status().isNoContent());
        assertThat(holds(m, ADMIN_ROLE, t)).isTrue();

        removeMember(g2, m).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TENANT_LAST_ADMIN_REQUIRED"));
        assertThat(origins(m, ADMIN_ROLE, t)).as("refused — nothing written").containsExactly(gid(g2));
    }

    @Test
    @DisplayName("🔴 Edge 2: the only admin holds TENANT_ADMIN direct + via G → PATCH removing the direct grant is allowed; then the group revoke is 409")
    void lastAdmin_directRemovedWhileGroupRemains() throws Exception {
        String t = "be633-t7";
        String m = "00000000-0000-7000-8000-000000633a71";
        seedSuper();
        seedOperator(m, t);
        patchRoles(m, t, "[\"" + ADMIN_ROLE + "\"]").andExpect(status().isOk());
        String g = createGroup(t, "be633 t7 G");
        String grant = grantRole(g, ADMIN_ROLE);
        addMember(g, m);

        patchRoles(m, t, "[]").andExpect(status().isOk());
        assertThat(origins(m, ADMIN_ROLE, t)).containsExactly(gid(g));

        revokeGrant(g, grant).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TENANT_LAST_ADMIN_REQUIRED"));
        deleteGroup(g).andExpect(status().isConflict());
        assertThat(holds(m, ADMIN_ROLE, t)).isTrue();
    }

    // ── AC-0 (c) · AC-3 — the 784 D-1 invariant after the group-path revokes ──

    @Test
    @DisplayName("🔴 AC-0(c)/AC-3: G's TENANT_ASSIGNMENT(U) revoked → a person with a direct U role keeps U as a DIRECT membership; a person without one loses U")
    void ac3_tenantAssignmentRevoke() throws Exception {
        Ac3 s = ac3Fixture("1");
        revokeGrant(s.group, s.taGrant).andExpect(status().isNoContent());
        s.assertInvariantKept("GROUP_GRANT_REVOKE");
    }

    @Test
    @DisplayName("🔴 AC-0(c)/AC-3: the person leaves G → keeps U as a DIRECT membership (direct U role); the control person leaves too and loses U")
    void ac3_memberRemoval() throws Exception {
        Ac3 s = ac3Fixture("2");
        removeMember(s.group, s.withRole).andExpect(status().isNoContent());
        removeMember(s.group, s.without).andExpect(status().isNoContent());
        s.assertInvariantKept("GROUP_MEMBER_REMOVE");
    }

    @Test
    @DisplayName("🔴 AC-0(c)/AC-3: G deleted → the person keeps U as a DIRECT membership (direct U role); the control person loses U")
    void ac3_groupDeletion() throws Exception {
        Ac3 s = ac3Fixture("3");
        deleteGroup(s.group).andExpect(status().isNoContent());
        s.assertInvariantKept("GROUP_DELETE");
    }

    /** G (home company H) gives two persons a membership of U; one of them gets a direct role in U through it. */
    private Ac3 ac3Fixture(String n) throws Exception {
        String home = "be633-h" + n;
        String u = "be633-u" + n;
        String withRole = "00000000-0000-7000-8000-000000633b" + n + "1";
        String without = "00000000-0000-7000-8000-000000633b" + n + "2";
        seedSuper();
        seedOperator(withRole, home);
        seedOperator(without, home);
        String g = createGroup(home, "be633 ac3 G" + n);
        String taGrant = grantTenantAssignment(g, u);
        addMember(g, withRole).andExpect(jsonPath("$.fannedOutGrants").value(1));
        addMember(g, without).andExpect(jsonPath("$.fannedOutGrants").value(1));
        assertThat(membershipOrigin(withRole, u)).as("precondition: U membership from G").isEqualTo(gid(g));
        // the U role exists only because of G's membership (PATCH requires the target to belong to U)
        patchRoles(withRole, u, "[\"" + ROLE + "\"]").andExpect(status().isOk());
        assertThat(orphanRoleRows(withRole)).as("precondition: invariant holds").isZero();
        return new Ac3(g, taGrant, withRole, without, u);
    }

    private final class Ac3 {
        final String group;
        final String taGrant;
        final String withRole;
        final String without;
        final String u;

        Ac3(String group, String taGrant, String withRole, String without, String u) {
            this.group = group;
            this.taGrant = taGrant;
            this.withRole = withRole;
            this.without = without;
            this.u = u;
        }

        void assertInvariantKept(String actionCode) {
            assertThat(orphanRoleRows(withRole))
                    .as("🔴 AC-3 (784 D-1): no non-'*' role row outside the person's memberships").isZero();
            assertThat(holds(withRole, ROLE, u)).as("the direct U role is not the group's to take").isTrue();
            assertThat(membershipOrigin(withRole, u)).as("U kept as a DIRECT membership").isEqualTo("-");
            assertThat(membershipOrigin(without, u)).as("control: no U role → G's U membership goes").isNull();
            assertThat(orphanRoleRows(without)).isZero();
            String detail = jdbc.queryForObject("""
                    SELECT downstream_detail FROM admin_actions
                     WHERE action_code = ? AND outcome = 'SUCCESS' AND target_id = ?
                       AND downstream_detail LIKE '%keptAsDirect=%'
                    """, String.class, actionCode, group);
            assertThat(detail).as("the source change is audited, never silent").contains("keptAsDirect=1");
        }
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
                        .header("X-Operator-Reason", "be633 group")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\":\"" + tenant + "\",\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return MAPPER.readTree(body).get("groupId").asText();
    }

    /** @return the grant id */
    private String grantRole(String groupId, String role) throws Exception {
        return grant(groupId, "{\"roles\":[\"" + role + "\"]}");
    }

    /** @return the grant id */
    private String grantTenantAssignment(String groupId, String tenant) throws Exception {
        return grant(groupId, "{\"tenantAssignments\":[{\"tenantId\":\"" + tenant + "\"}]}");
    }

    private String grant(String groupId, String json) throws Exception {
        String body = mockMvc.perform(post("/api/admin/groups/" + groupId + "/grants")
                        .header("Authorization", bearer(SUPER))
                        .header("X-Operator-Reason", "be633 grant")
                        .header("Idempotency-Key", "be633-grant-" + System.nanoTime())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return MAPPER.readTree(body).get("items").get(0).get("grantId").asText();
    }

    private ResultActions addMember(String groupId, String operator) throws Exception {
        return mockMvc.perform(post("/api/admin/groups/" + groupId + "/members")
                        .header("Authorization", bearer(SUPER))
                        .header("X-Operator-Reason", "be633 member")
                        .header("Idempotency-Key", "be633-member-" + System.nanoTime())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"operatorId\":\"" + operator + "\"}"))
                .andExpect(status().isCreated());
    }

    private ResultActions removeMember(String groupId, String operator) throws Exception {
        return mockMvc.perform(delete("/api/admin/groups/" + groupId + "/members/" + operator)
                .header("Authorization", bearer(SUPER))
                .header("X-Operator-Reason", "be633 leave"));
    }

    private ResultActions revokeGrant(String groupId, String grantId) throws Exception {
        return mockMvc.perform(delete("/api/admin/groups/" + groupId + "/grants/" + grantId)
                .header("Authorization", bearer(SUPER))
                .header("X-Operator-Reason", "be633 revoke"));
    }

    private ResultActions deleteGroup(String groupId) throws Exception {
        return mockMvc.perform(delete("/api/admin/groups/" + groupId)
                .header("Authorization", bearer(SUPER))
                .header("X-Operator-Reason", "be633 disband"));
    }

    private ResultActions patchRoles(String target, String tenant, String rolesJson) throws Exception {
        return mockMvc.perform(patch("/api/admin/operators/" + target + "/roles")
                .header("Authorization", bearer(SUPER))
                .header("X-Operator-Reason", "be633 roles")
                .header("X-Tenant-Id", tenant)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roles\": " + rolesJson + "}"));
    }

    /** {@code GET /api/admin/me} as the operator itself, narrowed to {@code tenant}. */
    private List<String> meRoles(String operator, String tenant) throws Exception {
        String body = mockMvc.perform(get("/api/admin/me")
                        .header("Authorization", bearer(operator))
                        .header("X-Tenant-Id", tenant))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return texts(MAPPER.readTree(body).get("roles"));
    }

    /** The operator's {@code roles} in the {@code '*'} operator list (every row of the operator). */
    private List<String> listedRolesAllTenants(String operator) throws Exception {
        String body = mockMvc.perform(get("/api/admin/operators")
                        .param("tenantId", "*").param("size", "100")
                        .header("Authorization", bearer(SUPER)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        for (JsonNode item : MAPPER.readTree(body).get("content")) {
            if (operator.equals(item.get("operatorId").asText())) {
                return texts(item.get("roles"));
            }
        }
        throw new AssertionError("operator " + operator + " not on the first page of the '*' list");
    }

    private static List<String> texts(JsonNode array) {
        List<String> out = new ArrayList<>();
        if (array != null) {
            array.forEach(n -> out.add(n.asText()));
        }
        return out;
    }

    private String bearer(String operatorUuid) {
        return "Bearer " + jwt.operatorToken(operatorUuid);
    }

    // ── fixture / reads ───────────────────────────────────────────────────────

    /** An ordinary operator of {@code home} with its home membership (no role); {@code '*'} → no membership. */
    private void seedOperator(String uuid, String home) {
        jdbc.update("""
                INSERT IGNORE INTO admin_operators
                  (operator_id, tenant_id, email, password_hash, display_name, status,
                   created_at, updated_at, version)
                VALUES (?, ?, ?, 'x', 'BE-633 Op', 'ACTIVE', NOW(6), NOW(6), 0)
                """, uuid, home, uuid + "@be633.example.com");
        assertThat(jdbc.queryForObject("SELECT tenant_id FROM admin_operators WHERE operator_id = ?",
                String.class, uuid)).as("operator %s owned by this class", uuid).isEqualTo(home);
        MembershipFixture.addHomeMembership(jdbc, uuid);
    }

    private String gid(String groupId) {
        return String.valueOf(jdbc.queryForObject(
                "SELECT id FROM operator_group WHERE group_id = ?", Long.class, groupId));
    }

    private boolean holds(String uuid, String role, String tenant) {
        return rowCount(uuid, role, tenant) > 0;
    }

    private int rowCount(String uuid, String role, String tenant) {
        Integer n = jdbc.queryForObject("""
                SELECT COUNT(*) FROM admin_operator_roles b
                  JOIN admin_operators o ON o.id = b.operator_id
                  JOIN admin_roles r ON r.id = b.role_id
                 WHERE o.operator_id = ? AND r.name = ? AND b.tenant_id = ?
                """, Integer.class, uuid, role, tenant);
        return n == null ? 0 : n;
    }

    /**
     * Schema-neutral (pre- and post-V0053): the {@code group_origin} of each row of (operator, role, tenant),
     * {@code "-"} for a non-group row.
     */
    private List<String> origins(String uuid, String role, String tenant) {
        return jdbc.queryForList("""
                SELECT COALESCE(CAST(b.group_origin AS CHAR), '-') FROM admin_operator_roles b
                  JOIN admin_operators o ON o.id = b.operator_id
                  JOIN admin_roles r ON r.id = b.role_id
                 WHERE o.operator_id = ? AND r.name = ? AND b.tenant_id = ?
                """, String.class, uuid, role, tenant);
    }

    /** Post-V0053: the {@code source_key} of each row of (operator, role, tenant). */
    private List<String> sourceKeys(String uuid, String role, String tenant) {
        return jdbc.queryForList("""
                SELECT b.source_key FROM admin_operator_roles b
                  JOIN admin_operators o ON o.id = b.operator_id
                  JOIN admin_roles r ON r.id = b.role_id
                 WHERE o.operator_id = ? AND r.name = ? AND b.tenant_id = ?
                """, String.class, uuid, role, tenant);
    }

    /** Schema-neutral snapshot of the one group row of {@code groupInternalId}, or {@code null}. */
    private String groupRow(String uuid, String role, String tenant, String groupInternalId) {
        List<String> rows = jdbc.queryForList("""
                SELECT CONCAT_WS('|', DATE_FORMAT(b.granted_at, '%Y-%m-%d %H:%i:%s.%f'),
                                 COALESCE(b.granted_by, '-'), b.group_origin, COALESCE(b.org_node_id, '-'))
                  FROM admin_operator_roles b
                  JOIN admin_operators o ON o.id = b.operator_id
                  JOIN admin_roles r ON r.id = b.role_id
                 WHERE o.operator_id = ? AND r.name = ? AND b.tenant_id = ? AND b.group_origin = ?
                """, String.class, uuid, role, tenant, Long.valueOf(groupInternalId));
        assertThat(rows).hasSizeLessThanOrEqualTo(1);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** The membership's {@code group_origin} ({@code "-"} = direct), or {@code null} when there is none. */
    private String membershipOrigin(String uuid, String tenant) {
        List<String> rows = jdbc.queryForList("""
                SELECT COALESCE(CAST(a.group_origin AS CHAR), '-') FROM operator_tenant_assignment a
                  JOIN admin_operators o ON o.id = a.operator_id
                 WHERE o.operator_id = ? AND a.tenant_id = ?
                """, String.class, uuid, tenant);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** TASK-MONO-784 D-1: the operator's non-'*' role rows whose tenant is none of its memberships. */
    private int orphanRoleRows(String uuid) {
        Integer n = jdbc.queryForObject("""
                SELECT COUNT(*) FROM admin_operator_roles b
                  JOIN admin_operators o ON o.id = b.operator_id
                 WHERE o.operator_id = ? AND b.tenant_id <> '*'
                   AND NOT EXISTS (SELECT 1 FROM operator_tenant_assignment a
                                    WHERE a.operator_id = b.operator_id AND a.tenant_id = b.tenant_id)
                """, Integer.class, uuid);
        return n == null ? 0 : n;
    }
}
