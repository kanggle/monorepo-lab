package com.example.admin.integration;

import com.example.testsupport.integration.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
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

import java.time.Duration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-BE-327 (ADR-MONO-020 § 3.3 step 2, D2) — integration test proving the
 * {@code GET /internal/operator-assignments/check} endpoint resolves the real
 * {@code TenantScopeResolver} dual-read (BE-326) end-to-end:
 * <ul>
 *   <li>legacy home tenant → assigned</li>
 *   <li>seeded {@code operator_tenant_assignment} row → assigned</li>
 *   <li>unrelated tenant → not assigned</li>
 *   <li>{@code '*'} platform-scope operator → assigned to any tenant</li>
 *   <li>unknown oidc subject → not assigned (fail-closed)</li>
 * </ul>
 *
 * <p>The {@code "test"} profile activates the {@code InternalApiFilter} dev/test
 * bypass so the endpoint is reachable without a real GAP JWT (the production
 * fail-closed chain is covered by the slice test). No {@code admin_actions} row
 * is written by the read endpoint.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@Tag("integration")
class OperatorAssignmentCheckIntegrationTest extends AbstractIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)
            .withStartupTimeout(Duration.ofMinutes(3));

    static final String OP_UUID = "00000000-0000-7000-8000-0000000000c1";
    static final String OP_SUBJECT = "00000000-0000-7000-8000-0000000000d1";
    static final String SUPER_UUID = "00000000-0000-7000-8000-0000000000c2";
    static final String SUPER_SUBJECT = "00000000-0000-7000-8000-0000000000d2";
    static final String UNKNOWN_SUBJECT = "00000000-0000-7000-8000-0000000000ff";

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        // admin.jwt.* is supplied by application-test.yml (active-signing-kid=v1);
        // do NOT override it here. This IT reaches /internal/** via the test-profile
        // InternalApiFilter bypass, not via an operator JWT.
        registry.add("admin.auth-service.base-url", () -> "http://localhost:18086");
        registry.add("admin.account-service.base-url", () -> "http://localhost:18086");
        registry.add("admin.security-service.base-url", () -> "http://localhost:18086");
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seed() {
        // Home tenant = acme-corp; assignment row → globex with a populated
        // org_scope subtree (TASK-BE-338). The acme-corp home tenant itself has
        // NO explicit assignment row → org_scope resolves to null (net-zero).
        seedOperator(OP_UUID, OP_SUBJECT, "acme-corp", "assign-op@example.com");
        seedAssignment(OP_UUID, "globex", "[\"dept-sales\"]");
        // Platform-scope operator.
        seedOperator(SUPER_UUID, SUPER_SUBJECT, "*", "super-assign@example.com");
    }

    @Test
    @DisplayName("legacy home tenant → assigned=true")
    void homeTenant_assigned() throws Exception {
        check(OP_SUBJECT, "acme-corp").andExpect(status().isOk())
                .andExpect(jsonPath("$.assigned").value(true));
    }

    @Test
    @DisplayName("seeded assignment row tenant → assigned=true")
    void assignedTenant_assigned() throws Exception {
        check(OP_SUBJECT, "globex").andExpect(status().isOk())
                .andExpect(jsonPath("$.assigned").value(true));
    }

    @Test
    @DisplayName("BE-338: seeded org_scope subtree → assigned=true + orgScope=[dept-sales]")
    void assignedTenant_returnsOrgScope() throws Exception {
        check(OP_SUBJECT, "globex").andExpect(status().isOk())
                .andExpect(jsonPath("$.assigned").value(true))
                .andExpect(jsonPath("$.orgScope[0]").value("dept-sales"));
    }

    @Test
    @DisplayName("BE-338: legacy home tenant (no explicit row) → assigned=true + orgScope ABSENT (net-zero, ⟺ [\"*\"])")
    void homeTenant_orgScopeNull() throws Exception {
        // admin-service serializes with @JsonInclude(NON_NULL), so a null orgScope is
        // OMITTED from the JSON — NOT rendered as `"orgScope": null`. Absent ⟺ null ⟺
        // ["*"] (net-zero): the auth-service AdminAssignmentClient parses an absent/null
        // orgScope to null → TenantClaimTokenCustomizer injects ["*"]. doesNotExist() is
        // the correct net-zero assertion (the prior .value(nullValue()) required the path
        // to exist, which the NON_NULL omission breaks → PathNotFoundException).
        check(OP_SUBJECT, "acme-corp").andExpect(status().isOk())
                .andExpect(jsonPath("$.assigned").value(true))
                .andExpect(jsonPath("$.orgScope").doesNotExist());
    }

    @Test
    @DisplayName("unrelated tenant → assigned=false")
    void unrelatedTenant_notAssigned() throws Exception {
        check(OP_SUBJECT, "initech").andExpect(status().isOk())
                .andExpect(jsonPath("$.assigned").value(false));
    }

    @Test
    @DisplayName("'*' platform-scope operator → assigned to any tenant")
    void platformScope_assignedToAny() throws Exception {
        check(SUPER_SUBJECT, "initech").andExpect(status().isOk())
                .andExpect(jsonPath("$.assigned").value(true));
    }

    @Test
    @DisplayName("unknown oidc subject → assigned=false (fail-closed)")
    void unknownSubject_notAssigned() throws Exception {
        check(UNKNOWN_SUBJECT, "acme-corp").andExpect(status().isOk())
                .andExpect(jsonPath("$.assigned").value(false));
    }

    // ── TASK-MONO-751 — admin_operators.confined_tenant_id (V0046) on the real column ──
    // Owner decision 2026-10-03 «데모 운영자는 팬 전용으로»: a '*' operator confined to
    // fan-platform assumes fan-platform only. Same context on purpose.

    static final String CONFINED_UUID = "00000000-0000-7000-8000-0000000751c1";
    static final String CONFINED_SUBJECT = "00000000-0000-7000-8000-0000000751d1";

    private void seedConfinedPlatformOperator() {
        seedOperator(CONFINED_UUID, CONFINED_SUBJECT, "*", "confined-751@example.com");
        jdbcTemplate.update("UPDATE admin_operators SET confined_tenant_id = 'fan-platform' WHERE operator_id = ?",
                CONFINED_UUID);
    }

    @Test
    @DisplayName("TASK-MONO-751: confined '*' operator → fan-platform assigned=true")
    void confinedPlatformOperator_fanPlatform_assigned() throws Exception {
        seedConfinedPlatformOperator();
        check(CONFINED_SUBJECT, "fan-platform").andExpect(status().isOk())
                .andExpect(jsonPath("$.assigned").value(true));
    }

    @Test
    @DisplayName("TASK-MONO-751: confined '*' operator → ecommerce / initech assigned=false; unconfined '*' still true (control)")
    void confinedPlatformOperator_otherTenants_refused() throws Exception {
        seedConfinedPlatformOperator();
        check(CONFINED_SUBJECT, "ecommerce").andExpect(status().isOk())
                .andExpect(jsonPath("$.assigned").value(false));
        check(CONFINED_SUBJECT, "initech").andExpect(status().isOk())
                .andExpect(jsonPath("$.assigned").value(false));
        check(SUPER_SUBJECT, "ecommerce").andExpect(status().isOk())
                .andExpect(jsonPath("$.assigned").value(true));
    }

    // ── TASK-BE-618 — GET /internal/operators/facet (same context on purpose: no new Spring context) ──

    static final String FACET_OP_UUID = "00000000-0000-7000-8000-0000000006c1";
    static final String FACET_SUBJECT = "00000000-0000-7000-8000-0000000006d1";
    static final String FACET_IDENTITY = "00000000-0000-7000-8000-0000000006e1";
    static final String SUSPENDED_OP_UUID = "00000000-0000-7000-8000-0000000006c2";
    static final String SUSPENDED_SUBJECT = "00000000-0000-7000-8000-0000000006d2";

    @Test
    @DisplayName("TASK-BE-618 facet: oidc_subject = accountId → operatorFaceted=true")
    void facet_bySubject() throws Exception {
        seedFacetOperators();
        facet(FACET_SUBJECT, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.operatorFaceted").value(true));
    }

    @Test
    @DisplayName("TASK-BE-618 facet: identity_id = identityId (다른 accountId) → operatorFaceted=true")
    void facet_byIdentity() throws Exception {
        seedFacetOperators();
        facet(UNKNOWN_SUBJECT, FACET_IDENTITY).andExpect(status().isOk())
                .andExpect(jsonPath("$.operatorFaceted").value(true));
    }

    @Test
    @DisplayName("TASK-BE-618 facet: 상태 무관 — SUSPENDED 운영자의 subject 도 true")
    void facet_suspendedOperatorStillFaceted() throws Exception {
        seedFacetOperators();
        facet(SUSPENDED_SUBJECT, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.operatorFaceted").value(true));
    }

    @Test
    @DisplayName("TASK-BE-618 facet: 어느 축도 안 맞으면 false (대조군)")
    void facet_noMatch() throws Exception {
        seedFacetOperators();
        facet(UNKNOWN_SUBJECT, "00000000-0000-7000-8000-0000000006ff").andExpect(status().isOk())
                .andExpect(jsonPath("$.operatorFaceted").value(false));
        facet(UNKNOWN_SUBJECT, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.operatorFaceted").value(false));
    }

    private void seedFacetOperators() {
        seedOperator(FACET_OP_UUID, FACET_SUBJECT, "acme-corp", "facet-op-618@example.com");
        jdbcTemplate.update("UPDATE admin_operators SET identity_id = ? WHERE operator_id = ?",
                FACET_IDENTITY, FACET_OP_UUID);
        seedOperator(SUSPENDED_OP_UUID, SUSPENDED_SUBJECT, "acme-corp", "facet-suspended-618@example.com");
        jdbcTemplate.update("UPDATE admin_operators SET status = 'SUSPENDED' WHERE operator_id = ?",
                SUSPENDED_OP_UUID);
    }

    private org.springframework.test.web.servlet.ResultActions facet(String accountId, String identityId)
            throws Exception {
        var request = get("/internal/operators/facet").param("accountId", accountId);
        if (identityId != null) {
            request.param("identityId", identityId);
        }
        return mockMvc.perform(request);
    }

    // ── TASK-MONO-772 S4 — GET /internal/operators/console-eligibility on the real admin_operators rows ──
    // Same context on purpose. The predicate is the token exchange's (oidc_subject = accountId ∧ ACTIVE) —
    // the same SUSPENDED row the facet read above answers TRUE for answers FALSE here (the two questions differ).

    @Test
    @DisplayName("MONO-772 S4: ACTIVE 운영자의 oidc_subject → eligible=true")
    void consoleEligibility_activeOperator() throws Exception {
        seedFacetOperators();
        consoleEligibility(FACET_SUBJECT).andExpect(status().isOk())
                .andExpect(jsonPath("$.eligible").value(true));
    }

    @Test
    @DisplayName("🔴 MONO-772 S4 (AC-3): SUSPENDED 운영자 → eligible=false — 같은 행에 facet 은 true (질문이 다르면 술어도 다르다)")
    void consoleEligibility_suspendedOperator_false_whileFacetTrue() throws Exception {
        seedFacetOperators();
        consoleEligibility(SUSPENDED_SUBJECT).andExpect(status().isOk())
                .andExpect(jsonPath("$.eligible").value(false));
        facet(SUSPENDED_SUBJECT, null).andExpect(jsonPath("$.operatorFaceted").value(true));
    }

    @Test
    @DisplayName("🔴 MONO-772 S4 (AC-2): 운영자 행 없는 계정 → eligible=false · 신원 축만 맞는 계정도 false")
    void consoleEligibility_noOperator_false_identityAxisNotAsked() throws Exception {
        seedFacetOperators();
        consoleEligibility(UNKNOWN_SUBJECT).andExpect(status().isOk())
                .andExpect(jsonPath("$.eligible").value(false));
        // FACET_IDENTITY is the identity_id of an operator — passing it as an accountId matches nothing.
        consoleEligibility(FACET_IDENTITY).andExpect(status().isOk())
                .andExpect(jsonPath("$.eligible").value(false));
    }

    @Test
    @DisplayName("MONO-772 S4: accountId 공백 → 400 VALIDATION_ERROR")
    void consoleEligibility_blank_400() throws Exception {
        consoleEligibility(" ").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    private org.springframework.test.web.servlet.ResultActions consoleEligibility(String accountId) throws Exception {
        return mockMvc.perform(get("/internal/operators/console-eligibility").param("accountId", accountId));
    }

    // ── TASK-MONO-771 S4 — mfaRequired on the real V0047 table + V0013 role flag ─────────────

    @org.junit.jupiter.api.AfterEach
    void clearMfaFixtures() {
        jdbcTemplate.update("DELETE FROM tenant_entry_policy");
        jdbcTemplate.update("""
                DELETE b FROM admin_operator_roles b
                  JOIN admin_operators o ON o.id = b.operator_id
                 WHERE o.operator_id = ?
                """, SUPER_UUID);
    }

    @Test
    @DisplayName("MONO-771: V0047 empty + no require_2fa role → mfaRequired=false, present (net-zero)")
    void mfaRequired_netZero() throws Exception {
        check(OP_SUBJECT, "acme-corp").andExpect(status().isOk())
                .andExpect(jsonPath("$.assigned").value(true))
                .andExpect(jsonPath("$.mfaRequired").value(false));
    }

    @Test
    @DisplayName("MONO-771 AC-2: policy row ON for globex → mfaRequired=true there; acme-corp (no row) control → false")
    void mfaRequired_policyOnTenant_vsControl() throws Exception {
        jdbcTemplate.update("INSERT INTO tenant_entry_policy (tenant_id, require_mfa, updated_at, updated_by, version)"
                + " VALUES ('globex', TRUE, NOW(6), NULL, 0)");
        check(OP_SUBJECT, "globex").andExpect(status().isOk())
                .andExpect(jsonPath("$.assigned").value(true))
                .andExpect(jsonPath("$.mfaRequired").value(true));
        check(OP_SUBJECT, "acme-corp").andExpect(status().isOk())
                .andExpect(jsonPath("$.mfaRequired").value(false));
    }

    @Test
    @DisplayName("MONO-771: a policy row with require_mfa=FALSE is «off»")
    void mfaRequired_policyRowOff() throws Exception {
        jdbcTemplate.update("INSERT INTO tenant_entry_policy (tenant_id, require_mfa, updated_at, updated_by, version)"
                + " VALUES ('globex', FALSE, NOW(6), NULL, 0)");
        check(OP_SUBJECT, "globex").andExpect(jsonPath("$.mfaRequired").value(false));
    }

    @Test
    @DisplayName("MONO-771 AC-1b (F2): platform '*' operator bound to SUPER_ADMIN (V0013 require_2fa) → mfaRequired=true for any tenant")
    void mfaRequired_platformSuperAdmin() throws Exception {
        jdbcTemplate.update("""
                INSERT IGNORE INTO admin_operator_roles (operator_id, role_id, tenant_id, granted_at, granted_by)
                SELECT o.id, r.id, o.tenant_id, NOW(6), NULL
                  FROM admin_operators o JOIN admin_roles r ON r.name = 'SUPER_ADMIN'
                 WHERE o.operator_id = ?
                """, SUPER_UUID);
        check(SUPER_SUBJECT, "initech").andExpect(status().isOk())
                .andExpect(jsonPath("$.assigned").value(true))
                .andExpect(jsonPath("$.mfaRequired").value(true));
    }

    @Test
    @DisplayName("MONO-771: V0047 CHECK (tenant_id <> '*') — the platform sentinel cannot hold a policy")
    void v0047_rejectsPlatformSentinel() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbcTemplate.update(
                        "INSERT INTO tenant_entry_policy (tenant_id, require_mfa, updated_at, version)"
                                + " VALUES ('*', TRUE, NOW(6), 0)"))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private org.springframework.test.web.servlet.ResultActions check(String subject, String tenantId)
            throws Exception {
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
                    """,
                    uuid, tenantId, email, "Test Op", oidcSubject);
        }
    }

    private void seedAssignment(String operatorUuid, String assignedTenant, String orgScopeJson) {
        jdbcTemplate.update("""
                INSERT IGNORE INTO operator_tenant_assignment
                  (operator_id, tenant_id, granted_at, granted_by, permission_set_id, org_scope)
                SELECT o.id, ?, NOW(6), NULL, NULL, CAST(? AS JSON)
                  FROM admin_operators o WHERE o.operator_id = ?
                """, assignedTenant, orgScopeJson, operatorUuid);
    }
}
