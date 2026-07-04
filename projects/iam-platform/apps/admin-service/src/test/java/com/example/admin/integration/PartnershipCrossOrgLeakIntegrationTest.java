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
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.time.Duration;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-BE-477 / ADR-MONO-045 — the keystone cross-org-leak IT (the D7 proof) at the
 * admin-service confinement boundary. Real MySQL (Testcontainers) + MockMvc; V0039/40
 * validated under {@code ddl-auto=validate}.
 *
 * <p>Actors: host tenant A = {@code acme-corp}; partner tenant B = {@code globex}; a
 * third tenant C = {@code initech}. The B-operator is a {@code TENANT_ADMIN @ globex}
 * AND a participant of the ACTIVE acme↔globex partnership (delegated = {wms,scm}×
 * {WMS_OP,SCM_PLANNER}; participant narrowed to {wms}×{WMS_OP}).
 *
 * <ul>
 *   <li><b>AC-4a</b> check(B-op, acme-corp) → assigned + delegatedScope = the
 *       triple-intersection {wms}×{WMS_OP} (NOT the host's full scope; NEVER scm/
 *       SCM_PLANNER).</li>
 *   <li><b>AC-4b</b> the SAME B-op has EMPTY effectiveAdminScope in A → 403
 *       {@code TENANT_SCOPE_DENIED} on an {@code /api/admin/**} mutation targeting an
 *       A operator (its admin scope is {globex}, never acme — the partnership does
 *       NOT widen admin scope).</li>
 *   <li><b>AC-4c</b> check(B-op, initech) → not assigned (no partnership / third
 *       tenant).</li>
 *   <li><b>AC-5</b> after :terminate (via the A-admin) — and after participant DELETE
 *       — check(B-op, acme-corp) → not assigned at the next request; ONE
 *       {@code partnership.terminated} outbox row.</li>
 *   <li><b>AC-6</b> net-zero: check(B-op, globex) [home] → assigned with NO
 *       delegatedScope block (normal-assignment shape byte-unchanged).</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@Tag("integration")
class PartnershipCrossOrgLeakIntegrationTest extends AbstractIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)
            .withStartupTimeout(Duration.ofMinutes(3));

    static OperatorJwtTestFixture jwt;
    static String signingKeyPem;

    static final String HOST = "acme-corp";
    static final String PARTNER = "globex";
    static final String THIRD = "initech";

    static final String A_ADMIN_UUID = "00000000-0000-7000-8000-0000000004a1";
    static final String B_OP_UUID = "00000000-0000-7000-8000-0000000004b1";
    static final String B_OP_SUBJECT = "00000000-0000-7000-8000-0000000004b2";
    static final String A_TARGET_UUID = "00000000-0000-7000-8000-0000000004a9";
    static final String PID = "00000000-0000-7000-8000-00000004pp01";

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
        registry.add("admin.auth-service.base-url", () -> "http://localhost:18087");
        registry.add("admin.account-service.base-url", () -> "http://localhost:18087");
        registry.add("admin.security-service.base-url", () -> "http://localhost:18087");
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seed() {
        // Test isolation: AbstractIntegrationTest does not roll back HTTP/jdbcTemplate
        // mutations, and AC-5 tests mutate/terminate the partnership. Delete first
        // (FK CASCADE removes participants) so every test starts from a fresh ACTIVE
        // partnership + participant.
        jdbcTemplate.update("DELETE FROM tenant_partnership WHERE partnership_id = ?", PID);
        // A-admin: TENANT_ADMIN @ acme (holds partnership.manage via V0040).
        seedOperator(A_ADMIN_UUID, null, HOST, "a-admin@example.com");
        seedRole(A_ADMIN_UUID, "TENANT_ADMIN");
        // B-op: TENANT_ADMIN @ globex (admin scope {globex}) + a participant of the partnership.
        seedOperator(B_OP_UUID, B_OP_SUBJECT, PARTNER, "b-op@example.com");
        seedRole(B_OP_UUID, "TENANT_ADMIN");
        // An A-side target operator (the object of the B-op's would-be admin mutation).
        seedOperator(A_TARGET_UUID, null, HOST, "a-target@example.com");
        seedRole(A_TARGET_UUID, "SUPPORT_READONLY");
        // ACTIVE partnership acme↔globex; delegated {wms,scm}×{WMS_OP,SCM_PLANNER}.
        seedPartnership(PID, HOST, PARTNER, "ACTIVE",
                "{\"domains\":[\"wms\",\"scm\"],\"roles\":[\"WMS_OP\",\"SCM_PLANNER\"]}");
        // B-op is a participant narrowed to {wms}×{WMS_OP}.
        seedParticipant(PID, B_OP_UUID, "{\"domains\":[\"wms\"],\"roles\":[\"WMS_OP\"]}");
    }

    // ── AC-4a ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("AC-4a: check(B-op, hostA) → assigned + delegatedScope = delegated ∩ participant (NEVER host's full scope)")
    void crossOrg_derivedScope_isTripleIntersection() throws Exception {
        check(B_OP_SUBJECT, HOST).andExpect(status().isOk())
                .andExpect(jsonPath("$.assigned").value(true))
                .andExpect(jsonPath("$.delegatedScope.domains.length()").value(1))
                .andExpect(jsonPath("$.delegatedScope.domains[0]").value("wms"))
                .andExpect(jsonPath("$.delegatedScope.roles.length()").value(1))
                .andExpect(jsonPath("$.delegatedScope.roles[0]").value("WMS_OP"));
    }

    // ── AC-4b ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("AC-4b: B-op has EMPTY admin scope in A → 403 TENANT_SCOPE_DENIED on an /api/admin/** mutation targeting A")
    void crossOrg_adminPlane_denied() throws Exception {
        mockMvc.perform(patch("/api/admin/operators/" + A_TARGET_UUID + "/roles")
                        .header("Authorization", "Bearer " + jwt.operatorToken(B_OP_UUID))
                        .header("X-Operator-Reason", "attempt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[\"SUPPORT_READONLY\"]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_SCOPE_DENIED"));
    }

    // ── AC-4c ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("AC-4c: check(B-op, thirdTenantC) → not assigned")
    void crossOrg_thirdTenant_notAssigned() throws Exception {
        check(B_OP_SUBJECT, THIRD).andExpect(status().isOk())
                .andExpect(jsonPath("$.assigned").value(false))
                .andExpect(jsonPath("$.delegatedScope").doesNotExist());
    }

    // ── AC-5 ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("AC-5: :terminate (A-admin) → next check(B-op, hostA) not assigned; ONE partnership.terminated outbox row")
    void cascadeRevoke_onTerminate() throws Exception {
        // Before: reachable.
        check(B_OP_SUBJECT, HOST).andExpect(jsonPath("$.assigned").value(true));

        Integer before = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM admin_outbox WHERE event_type = 'partnership.terminated' AND partition_key = ?",
                Integer.class, PID);

        mockMvc.perform(post("/api/admin/partnerships/" + PID + ":terminate")
                        .header("Authorization", "Bearer " + jwt.operatorToken(A_ADMIN_UUID))
                        .header("X-Tenant-Id", HOST)
                        .header("X-Operator-Reason", "ending partnership")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("TERMINATED"));

        // After: derivation gone at the next request (no per-operator sweep).
        check(B_OP_SUBJECT, HOST).andExpect(jsonPath("$.assigned").value(false))
                .andExpect(jsonPath("$.delegatedScope").doesNotExist());

        Integer after = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM admin_outbox WHERE event_type = 'partnership.terminated' AND partition_key = ?",
                Integer.class, PID);
        assertThat(after).isEqualTo(before + 1); // ONE-SHOT (not per-operator)
    }

    @Test
    @DisplayName("AC-5: participant DELETE (offboard) → next check(B-op, hostA) not assigned (no A-side action)")
    void cascadeRevoke_onParticipantRemoval() throws Exception {
        check(B_OP_SUBJECT, HOST).andExpect(jsonPath("$.assigned").value(true));

        jdbcTemplate.update("""
                DELETE p FROM tenant_partnership_participant p
                 JOIN tenant_partnership tp ON tp.id = p.partnership_id
                 JOIN admin_operators o ON o.id = p.operator_id
                WHERE tp.partnership_id = ? AND o.operator_id = ?
                """, PID, B_OP_UUID);

        check(B_OP_SUBJECT, HOST).andExpect(jsonPath("$.assigned").value(false));
    }

    @Test
    @DisplayName("AC-5: SUSPENDED partnership → check(B-op, hostA) not assigned (findActive excludes non-ACTIVE)")
    void cascadeRevoke_onSuspend() throws Exception {
        jdbcTemplate.update("UPDATE tenant_partnership SET status = 'SUSPENDED' WHERE partnership_id = ?", PID);
        check(B_OP_SUBJECT, HOST).andExpect(jsonPath("$.assigned").value(false));
    }

    // ── AC-6 ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("AC-6: net-zero — check(B-op, home globex) → assigned with NO delegatedScope block")
    void netZero_homeTenant_noDelegatedScope() throws Exception {
        check(B_OP_SUBJECT, PARTNER).andExpect(status().isOk())
                .andExpect(jsonPath("$.assigned").value(true))
                .andExpect(jsonPath("$.delegatedScope").doesNotExist());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private ResultActions check(String subject, String tenantId) throws Exception {
        return mockMvc.perform(get("/internal/operator-assignments/check")
                .param("oidcSubject", subject)
                .param("tenantId", tenantId));
    }

    private void seedOperator(String uuid, String oidcSubject, String tenantId, String email) {
        Integer existing = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM admin_operators WHERE operator_id = ?", Integer.class, uuid);
        if (existing == null || existing == 0) {
            jdbcTemplate.update("""
                    INSERT INTO admin_operators
                      (operator_id, tenant_id, email, password_hash, display_name, status,
                       oidc_subject, created_at, updated_at, version)
                    VALUES (?, ?, ?, 'x', ?, 'ACTIVE', ?, NOW(6), NOW(6), 0)
                    """, uuid, tenantId, email, "Test Op", oidcSubject);
        }
    }

    private void seedRole(String uuid, String roleName) {
        jdbcTemplate.update("""
                INSERT IGNORE INTO admin_operator_roles (operator_id, role_id, tenant_id, granted_at, granted_by)
                SELECT o.id, r.id, o.tenant_id, NOW(6), NULL
                  FROM admin_operators o CROSS JOIN admin_roles r
                 WHERE o.operator_id = ? AND r.name = ?
                """, uuid, roleName);
    }

    private void seedPartnership(String partnershipId, String host, String partner,
                                 String status, String delegatedJson) {
        jdbcTemplate.update("""
                INSERT INTO tenant_partnership
                  (partnership_id, host_tenant_id, partner_tenant_id, status, delegated_scope,
                   invited_by, accepted_by, invited_at, accepted_at, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, CAST(? AS JSON), NULL, NULL, NOW(6), NOW(6), NOW(6), NOW(6), 0)
                """, partnershipId, host, partner, status, delegatedJson);
    }

    private void seedParticipant(String partnershipId, String operatorUuid, String participantJson) {
        jdbcTemplate.update("""
                INSERT INTO tenant_partnership_participant
                  (partnership_id, operator_id, participant_scope, assigned_at, assigned_by)
                SELECT tp.id, o.id, CAST(? AS JSON), NOW(6), NULL
                  FROM tenant_partnership tp CROSS JOIN admin_operators o
                 WHERE tp.partnership_id = ? AND o.operator_id = ?
                """, participantJson, partnershipId, operatorUuid);
    }
}
