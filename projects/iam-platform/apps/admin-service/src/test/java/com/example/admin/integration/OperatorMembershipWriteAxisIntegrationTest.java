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
 * TASK-MONO-784 S4 — the write axis against the real migrated schema (ADR-MONO-082 b · admin-api.md § 행동 테넌트
 * · § DELETE …/assignments/{tenantId} · § PATCH …/roles · § PATCH …/status): <b>AC-2</b> (leave one company),
 * <b>AC-4</b> (row-wide R3 · B rows byte-unchanged), <b>AC-5</b> (the next request decides per company).
 *
 * <p>Every tenant / operator id is unique to this class (the IT suite shares one MySQL container).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@Tag("integration")
class OperatorMembershipWriteAxisIntegrationTest extends AbstractIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)
            .withStartupTimeout(Duration.ofMinutes(3));

    static OperatorJwtTestFixture jwt;
    static String signingKeyPem;

    static final String A = "s4w-alpha";
    static final String B = "s4w-beta";
    static final String HOST_C = "s4w-gamma";
    static final String U = "s4w-u";
    static final String V = "s4w-v";
    static final String FANNED = "00000000-0000-7000-8000-000000078417";

    static final String SUPER = "00000000-0000-7000-8000-000000078410";
    static final String ADMIN_A = "00000000-0000-7000-8000-000000078411";
    static final String PERSON = "00000000-0000-7000-8000-000000078412";
    static final String PERSON_SUBJECT = "00000000-0000-7000-8000-000000078413";
    static final String TWO_CO = "00000000-0000-7000-8000-000000078414";
    static final String ONE_CO = "00000000-0000-7000-8000-000000078415";
    static final String CACHE_OP = "00000000-0000-7000-8000-000000078416";
    static final String PID_PARTNER_A = "00000000-0000-7000-8000-0000784pp0a1";
    static final String PID_PARTNER_B = "00000000-0000-7000-8000-0000784pp0b1";

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
        // Nothing listens here: any admin → account / auth / security call from these flows would fail loudly.
        registry.add("admin.auth-service.base-url", () -> "http://localhost:18088");
        registry.add("admin.account-service.base-url", () -> "http://localhost:18088");
        registry.add("admin.security-service.base-url", () -> "http://localhost:18088");
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    // ── AC-2 ──────────────────────────────────────────────────────────────────

    /**
     * 🔴 AC-2 (ADR-082 D3): the same person, operator of A and B, is removed from A. In the same test: A's assume
     * and A's management API are refused, B's assume and B's management API still work, the person's account is
     * untouched — and, first, the «last administrator» refusal writes nothing.
     */
    @Test
    @DisplayName("🔴 AC-2: leave A → A assume · A admin refused / B assume · B admin OK / account untouched; last admin self-exit 409 first")
    void ac2_leaveOneCompany() throws Exception {
        seedOperator(SUPER, "*", "s4w-super@example.com", null);
        grant(SUPER, "SUPER_ADMIN", "*");
        seedOperator(ADMIN_A, A, "s4w-admin-a@example.com", null);
        grant(ADMIN_A, "TENANT_ADMIN", A);
        member(ADMIN_A, A);
        seedOperator(PERSON, A, "s4w-person@example.com", PERSON_SUBJECT);
        grant(PERSON, "SUPPORT_READONLY", A);
        grant(PERSON, "SUPPORT_READONLY", B);
        member(PERSON, A);
        member(PERSON, B);
        // a group of A with a role fan-out onto the person, and two participations (partner A · partner B)
        String groupId = createGroupWithRole(A, "s4w 팀", "SUPPORT_LOCK");
        addGroupMember(groupId, PERSON);
        seedPartnership(PID_PARTNER_A, HOST_C, A);
        seedPartnership(PID_PARTNER_B, HOST_C, B);
        seedParticipant(PID_PARTNER_A, PERSON);
        seedParticipant(PID_PARTNER_B, PERSON);
        List<String> personBBefore = roleRows(PERSON, B);

        // 🔴 the last administrator of A removes themself → 409, nothing written
        exit(ADMIN_A, ADMIN_A, A).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TENANT_LAST_ADMIN_REQUIRED"));
        assertThat(count("SELECT COUNT(*) FROM operator_tenant_assignment a JOIN admin_operators o ON o.id = a.operator_id "
                + "WHERE o.operator_id = ? AND a.tenant_id = ?", ADMIN_A, A)).isEqualTo(1);
        assertThat(roleRows(ADMIN_A, A)).hasSize(1);

        // before: the person reaches both companies
        assume(PERSON_SUBJECT, A).andExpect(jsonPath("$.assigned").value(true));
        auditRead(PERSON, A).andExpect(status().isOk());

        exit(ADMIN_A, PERSON, A).andExpect(status().isNoContent());

        // failure side first: company A is closed — assume and the management API
        assume(PERSON_SUBJECT, A).andExpect(status().isOk()).andExpect(jsonPath("$.assigned").value(false));
        auditRead(PERSON, A).andExpect(status().isForbidden());
        // company B is untouched — assume and the management API still work
        assume(PERSON_SUBJECT, B).andExpect(status().isOk()).andExpect(jsonPath("$.assigned").value(true));
        auditRead(PERSON, B).andExpect(status().isOk());

        // every side of A went; B byte-unchanged
        assertThat(roleRows(PERSON, A)).as("A role rows of every origin").isEmpty();
        assertThat(roleRows(PERSON, B)).as("B role rows byte-unchanged").isEqualTo(personBBefore);
        assertThat(count("SELECT COUNT(*) FROM operator_group_member m JOIN admin_operators o ON o.id = m.operator_id "
                + "WHERE o.operator_id = ?", PERSON)).as("A group membership").isZero();
        assertThat(participations(PERSON)).containsExactly(PID_PARTNER_B);
        String detail = jdbc.queryForObject("""
                SELECT downstream_detail FROM admin_actions
                 WHERE actor_id = ? AND action_code = 'OPERATOR_ASSIGNMENT_DELETE' AND outcome = 'SUCCESS'
                   AND target_id = ?
                """, String.class, ADMIN_A, PERSON);
        assertThat(detail).isEqualTo("roleRows=2,groupMemberships=1,participations=1,groupAssignments=0");

        // the person's account and operator row are untouched (ADR-080 D5 · ADR-082 D3 · D-10): no account call
        // was made (nothing listens on the account-service URL), no account action audited, the row is ACTIVE
        // with the same login link — store and fan sign-in are unaffected.
        assertThat(jdbc.queryForObject("SELECT CONCAT_WS('|', status, oidc_subject, tenant_id) FROM admin_operators "
                + "WHERE operator_id = ?", String.class, PERSON)).isEqualTo("ACTIVE|" + PERSON_SUBJECT + "|" + A);
        assertThat(count("SELECT COUNT(*) FROM admin_actions WHERE actor_id = ? AND action_code LIKE 'ACCOUNT_%'",
                ADMIN_A)).isZero();
    }

    /**
     * 🔴 Owner decision 2026-10-13 UTC «U 역할 행도 함께 지움»: a group G of company A also fans out into other
     * companies. Leaving A removes everything G produced for the person in EVERY company — its assignment to U and
     * its role row in U — while the person's own rows stay byte-unchanged: the direct role in U, and company V whose
     * membership the person held directly before G granted it (G's V fan-out was an idempotent skip).
     */
    @Test
    @DisplayName("🔴 owner 2026-10-13: leave A → A-group G's assignment + role row in U gone; the person's direct U role and direct V membership untouched")
    void ac2_groupFanOutIntoOtherCompanies_goesWithTheGroup() throws Exception {
        seedOperator(SUPER, "*", "s4w-super@example.com", null);
        grant(SUPER, "SUPER_ADMIN", "*");
        seedOperator(ADMIN_A, A, "s4w-admin-a@example.com", null);
        grant(ADMIN_A, "TENANT_ADMIN", A);
        member(ADMIN_A, A);
        seedOperator(FANNED, A, "s4w-fanned@example.com", null);
        member(FANNED, A);
        member(FANNED, V);                         // V: held directly, before the group
        grant(FANNED, "SUPPORT_READONLY", V);
        grant(FANNED, "SECURITY_ANALYST", U);      // the person's own (direct) U role
        String groupId = createGroupWithTenantAssignments(A, "s4w 팀 U", U, V);
        addGroupMember(groupId, FANNED);          // fans out U only (V already held → skip)
        Long groupInternalId = jdbc.queryForObject(
                "SELECT id FROM operator_group WHERE group_id = ?", Long.class, groupId);
        // a role row G produced in U (the shape a pre-S4 fan-out left behind — post-S4 role fan-out lands in A)
        jdbc.update("""
                INSERT INTO admin_operator_roles (operator_id, role_id, tenant_id, granted_at, granted_by, group_origin)
                SELECT o.id, r.id, ?, NOW(6), NULL, ?
                  FROM admin_operators o CROSS JOIN admin_roles r
                 WHERE o.operator_id = ? AND r.name = 'SUPPORT_LOCK'
                """, U, groupInternalId, FANNED);
        assertThat(groupAssignment(FANNED, U)).as("precondition: G's U membership").isEqualTo(String.valueOf(groupInternalId));
        assertThat(groupAssignment(FANNED, V)).as("precondition: V held directly").isEqualTo("-");
        List<String> vRolesBefore = roleRows(FANNED, V);

        exit(ADMIN_A, FANNED, A).andExpect(status().isNoContent());

        assertThat(groupAssignment(FANNED, U)).as("G's U assignment gone").isNull();
        assertThat(roleRows(FANNED, U)).as("G's U role row gone, the direct U role stays")
                .singleElement().asString().startsWith("SECURITY_ANALYST|" + U + "|").endsWith("|-|-|-");
        assertThat(groupAssignment(FANNED, V)).as("direct V membership untouched").isEqualTo("-");
        assertThat(roleRows(FANNED, V)).as("V byte-unchanged").isEqualTo(vRolesBefore);
    }

    // ── AC-4 ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("🔴 AC-4 (R3): two memberships → company admin's suspend 403 OPERATOR_HAS_OTHER_MEMBERSHIPS · '*' 200 · one membership = as today")
    void ac4_rowWideRule() throws Exception {
        seedOperator(SUPER, "*", "s4w-super@example.com", null);
        grant(SUPER, "SUPER_ADMIN", "*");
        seedOperator(ADMIN_A, A, "s4w-admin-a@example.com", null);
        grant(ADMIN_A, "TENANT_ADMIN", A);
        member(ADMIN_A, A);
        seedOperator(TWO_CO, A, "s4w-two@example.com", null);
        grant(TWO_CO, "SUPPORT_READONLY", A);
        grant(TWO_CO, "SUPPORT_READONLY", B);
        member(TWO_CO, A);
        member(TWO_CO, B);
        seedOperator(ONE_CO, A, "s4w-one@example.com", null);
        grant(ONE_CO, "SUPPORT_READONLY", A);
        member(ONE_CO, A);

        // failure side first
        patchStatus(ADMIN_A, TWO_CO, "SUSPENDED").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("OPERATOR_HAS_OTHER_MEMBERSHIPS"));
        assertThat(operatorStatus(TWO_CO)).isEqualTo("ACTIVE");

        // the platform scope may
        patchStatus(SUPER, TWO_CO, "SUSPENDED").andExpect(status().isOk());
        patchStatus(SUPER, TWO_CO, "ACTIVE").andExpect(status().isOk());

        // control: a single-company operator — the company admin may, exactly as before 784
        patchStatus(ADMIN_A, ONE_CO, "SUSPENDED").andExpect(status().isOk());
        patchStatus(ADMIN_A, ONE_CO, "ACTIVE").andExpect(status().isOk());
    }

    @Test
    @DisplayName("🔴 AC-4 · F6: role edit in A (X-Tenant-Id: A) leaves the person's B rows byte-unchanged")
    void ac4_roleEditInA_leavesBByteUnchanged() throws Exception {
        seedOperator(ADMIN_A, A, "s4w-admin-a@example.com", null);
        grant(ADMIN_A, "TENANT_ADMIN", A);
        member(ADMIN_A, A);
        seedOperator(TWO_CO, A, "s4w-two@example.com", null);
        grant(TWO_CO, "SUPPORT_READONLY", A);
        grant(TWO_CO, "SUPPORT_READONLY", B);
        member(TWO_CO, A);
        member(TWO_CO, B);
        List<String> bBefore = roleRows(TWO_CO, B);

        roles(ADMIN_A, TWO_CO, A, "[]").andExpect(status().isOk())
                .andExpect(jsonPath("$.roles.length()").value(0));

        assertThat(roleRows(TWO_CO, A)).isEmpty();
        assertThat(roleRows(TWO_CO, B)).as("B rows byte-unchanged").isEqualTo(bBefore);
        assertThat(count("SELECT COUNT(*) FROM operator_tenant_assignment a JOIN admin_operators o ON o.id = a.operator_id "
                + "WHERE o.operator_id = ? AND a.tenant_id = ?", TWO_CO, A)).as("[] keeps the membership (R2)").isEqualTo(1);
    }

    // ── AC-5 ──────────────────────────────────────────────────────────────────

    /**
     * AC-5: after a change in one company, that company's decision changes on the very next request while the other
     * company's stays. The last cell proves the permission-union cache was dropped on «이 회사에서 내보내기»: with
     * a stale cache the aspect would still pass and the gate answer {@code TENANT_SCOPE_DENIED}; a fresh union
     * has no {@code audit.read} left and the aspect answers {@code PERMISSION_DENIED}.
     */
    @Test
    @DisplayName("AC-5: role change in A → A's decision flips next request, B's stays; export from B → cache dropped (PERMISSION_DENIED)")
    void ac5_perCompanyDecisionNextRequest() throws Exception {
        seedOperator(SUPER, "*", "s4w-super@example.com", null);
        grant(SUPER, "SUPER_ADMIN", "*");
        seedOperator(ADMIN_A, A, "s4w-admin-a@example.com", null);
        grant(ADMIN_A, "TENANT_ADMIN", A);
        member(ADMIN_A, A);
        seedOperator(CACHE_OP, A, "s4w-cache@example.com", null);
        grant(CACHE_OP, "SUPPORT_READONLY", A);
        grant(CACHE_OP, "SUPPORT_READONLY", B);
        member(CACHE_OP, A);
        member(CACHE_OP, B);

        // warm: both companies answer (the permission-union cache now holds audit.read)
        auditRead(CACHE_OP, A).andExpect(status().isOk());
        auditRead(CACHE_OP, B).andExpect(status().isOk());

        roles(ADMIN_A, CACHE_OP, A, "[]").andExpect(status().isOk());

        auditRead(CACHE_OP, A).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_SCOPE_DENIED"));
        auditRead(CACHE_OP, B).andExpect(status().isOk());

        exit(SUPER, CACHE_OP, B).andExpect(status().isNoContent());
        auditRead(CACHE_OP, B).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    // ── requests ──────────────────────────────────────────────────────────────

    private ResultActions exit(String actor, String target, String tenant) throws Exception {
        return mockMvc.perform(delete("/api/admin/operators/" + target + "/assignments/" + tenant)
                .header("Authorization", bearer(actor))
                .header("X-Operator-Reason", "s4 exit"));
    }

    private ResultActions roles(String actor, String target, String tenant, String rolesJson) throws Exception {
        return mockMvc.perform(patch("/api/admin/operators/" + target + "/roles")
                .header("Authorization", bearer(actor))
                .header("X-Operator-Reason", "s4 roles")
                .header("X-Tenant-Id", tenant)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roles\": " + rolesJson + "}"));
    }

    private ResultActions patchStatus(String actor, String target, String newStatus) throws Exception {
        return mockMvc.perform(patch("/api/admin/operators/" + target + "/status")
                .header("Authorization", bearer(actor))
                .header("X-Operator-Reason", "s4 status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\": \"" + newStatus + "\"}"));
    }

    private ResultActions auditRead(String actor, String tenant) throws Exception {
        return mockMvc.perform(get("/api/admin/audit").param("tenantId", tenant).param("source", "admin")
                .header("Authorization", bearer(actor)).header("X-Operator-Reason", "s4 read"));
    }

    private ResultActions assume(String subject, String tenant) throws Exception {
        return mockMvc.perform(get("/internal/operator-assignments/check")
                .param("oidcSubject", subject).param("tenantId", tenant));
    }

    private String createGroupWithRole(String tenant, String name, String role) throws Exception {
        String body = mockMvc.perform(post("/api/admin/groups")
                        .header("Authorization", bearer(SUPER))
                        .header("X-Operator-Reason", "s4 group")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\":\"" + tenant + "\",\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String groupId = body.replaceAll("(?s).*\"groupId\"\\s*:\\s*\"([^\"]+)\".*", "$1");
        mockMvc.perform(post("/api/admin/groups/" + groupId + "/grants")
                        .header("Authorization", bearer(SUPER))
                        .header("X-Operator-Reason", "s4 grant")
                        .header("Idempotency-Key", "s4w-grant-" + System.nanoTime())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[\"" + role + "\"]}"))
                .andExpect(status().isCreated());
        return groupId;
    }

    private String createGroupWithTenantAssignments(String tenant, String name, String... assignTo) throws Exception {
        String body = mockMvc.perform(post("/api/admin/groups")
                        .header("Authorization", bearer(SUPER))
                        .header("X-Operator-Reason", "s4 group")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\":\"" + tenant + "\",\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String groupId = body.replaceAll("(?s).*\"groupId\"\\s*:\\s*\"([^\"]+)\".*", "$1");
        StringBuilder grants = new StringBuilder();
        for (String t : assignTo) {
            if (grants.length() > 0) grants.append(',');
            grants.append("{\"tenantId\":\"").append(t).append("\"}");
        }
        mockMvc.perform(post("/api/admin/groups/" + groupId + "/grants")
                        .header("Authorization", bearer(SUPER))
                        .header("X-Operator-Reason", "s4 grant")
                        .header("Idempotency-Key", "s4w-grant-" + System.nanoTime())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantAssignments\":[" + grants + "]}"))
                .andExpect(status().isCreated());
        return groupId;
    }

    /** The assignment row's {@code group_origin} ({@code "-"} = direct), or {@code null} when there is no row. */
    private String groupAssignment(String uuid, String tenant) {
        List<String> rows = jdbc.queryForList("""
                SELECT COALESCE(CAST(a.group_origin AS CHAR), '-') FROM operator_tenant_assignment a
                  JOIN admin_operators o ON o.id = a.operator_id
                 WHERE o.operator_id = ? AND a.tenant_id = ?
                """, String.class, uuid, tenant);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private void addGroupMember(String groupId, String operator) throws Exception {
        mockMvc.perform(post("/api/admin/groups/" + groupId + "/members")
                        .header("Authorization", bearer(SUPER))
                        .header("X-Operator-Reason", "s4 member")
                        .header("Idempotency-Key", "s4w-member-" + System.nanoTime())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"operatorId\":\"" + operator + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fannedOutGrants").value(1));
    }

    private String bearer(String operatorUuid) {
        return "Bearer " + jwt.operatorToken(operatorUuid);
    }

    // ── fixture ───────────────────────────────────────────────────────────────

    private void seedOperator(String uuid, String home, String email, String oidcSubject) {
        jdbc.update("""
                INSERT IGNORE INTO admin_operators
                  (operator_id, tenant_id, email, password_hash, display_name, status, oidc_subject,
                   created_at, updated_at, version)
                VALUES (?, ?, ?, 'x', 'S4 Op', 'ACTIVE', ?, NOW(6), NOW(6), 0)
                """, uuid, home, email, oidcSubject);
        jdbc.update("UPDATE admin_operators SET status = 'ACTIVE' WHERE operator_id = ?", uuid);
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

    private void seedPartnership(String partnershipId, String host, String partner) {
        jdbc.update("""
                INSERT IGNORE INTO tenant_partnership
                  (partnership_id, host_tenant_id, partner_tenant_id, status, delegated_scope,
                   invited_by, accepted_by, invited_at, accepted_at, created_at, updated_at, version)
                VALUES (?, ?, ?, 'ACTIVE', CAST('{"domains":["wms"],"roles":["WMS_OP"]}' AS JSON),
                        NULL, NULL, NOW(6), NOW(6), NOW(6), NOW(6), 0)
                """, partnershipId, host, partner);
    }

    private void seedParticipant(String partnershipId, String operatorUuid) {
        jdbc.update("""
                INSERT IGNORE INTO tenant_partnership_participant
                  (partnership_id, operator_id, participant_scope, assigned_at, assigned_by)
                SELECT tp.id, o.id, NULL, NOW(6), NULL
                  FROM tenant_partnership tp CROSS JOIN admin_operators o
                 WHERE tp.partnership_id = ? AND o.operator_id = ?
                """, partnershipId, operatorUuid);
    }

    /** Every column that matters of one operator's role rows in one tenant — the «byte-unchanged» snapshot. */
    private List<String> roleRows(String uuid, String tenant) {
        return jdbc.queryForList("""
                SELECT CONCAT_WS('|', r.name, b.tenant_id, DATE_FORMAT(b.granted_at, '%Y-%m-%d %H:%i:%s.%f'),
                                 COALESCE(b.granted_by, '-'), COALESCE(b.group_origin, '-'), COALESCE(b.org_node_id, '-'))
                  FROM admin_operator_roles b
                  JOIN admin_operators o ON o.id = b.operator_id
                  JOIN admin_roles r ON r.id = b.role_id
                 WHERE o.operator_id = ? AND b.tenant_id = ?
                 ORDER BY r.name
                """, String.class, uuid, tenant);
    }

    private List<String> participations(String uuid) {
        return jdbc.queryForList("""
                SELECT tp.partnership_id FROM tenant_partnership_participant p
                  JOIN tenant_partnership tp ON tp.id = p.partnership_id
                  JOIN admin_operators o ON o.id = p.operator_id
                 WHERE o.operator_id = ?
                """, String.class, uuid);
    }

    private String operatorStatus(String uuid) {
        return jdbc.queryForObject("SELECT status FROM admin_operators WHERE operator_id = ?", String.class, uuid);
    }

    private int count(String sql, Object... args) {
        Integer n = jdbc.queryForObject(sql, Integer.class, args);
        return n == null ? 0 : n;
    }
}
