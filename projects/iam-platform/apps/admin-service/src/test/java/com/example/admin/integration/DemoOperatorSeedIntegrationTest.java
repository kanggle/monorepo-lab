package com.example.admin.integration;

import com.example.admin.support.OperatorJwtTestFixture;
import com.example.testsupport.integration.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-BE-576 (and retroactively TASK-BE-571) — the portfolio demo operator's seed
 * rows, asserted against the DB the real migration produced.
 *
 * <p><b>Why this test exists.</b> {@code R__seed_demo_operator.sql} carries the whole
 * console side of the demo identity, and until now <b>nothing asserted any of it</b>.
 * Every row in it is silently deletable: drop the {@code oidc_subject} literal and the
 * console fail-closes to a 401 that the UI renders with the same text a 5s timeout
 * produces; drop a tenant assignment and the console renders <b>200 with empty
 * lists</b>. Neither shows up as a failure anywhere. A seed nobody asserts is a seed
 * that drifts.
 *
 * <p><b>What the assignment set means.</b> The two tenants are not redundant — they
 * carry different things, and that is exactly what TASK-BE-576 had to discover:
 * <ul>
 *   <li>{@code demo-corp} — <b>authorization</b>. Its five ACTIVE domain subscriptions
 *       derive {@code ECOMMERCE/WMS/SCM/ERP/FINANCE_OPERATOR} at assume time.</li>
 *   <li>{@code ecommerce} — <b>visibility</b>. Every storefront row is written under
 *       {@code tenant_id='ecommerce'} (the gateway pins the consumer token's tenant,
 *       and the catalog itself takes that value from the column default), and each
 *       service filters reads by the request's tenant. Without this row the operator
 *       sees an empty console <em>and cannot write</em> — advancing a shipment the
 *       buyer's own token had just returned failed with 404 SHIPPING_NOT_FOUND.</li>
 * </ul>
 *
 * <p>The set is asserted <b>exactly</b>, in both directions. A missing entry is the
 * regression this test was written for; an unexpected extra one means someone widened
 * a demo operator's reach into a tenant nobody reviewed, which on this seed is the
 * more dangerous direction.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@Tag("integration")
class DemoOperatorSeedIntegrationTest extends AbstractIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)
            .withStartupTimeout(Duration.ofMinutes(3));

    /**
     * MUST equal the OIDC {@code sub} of the console login — the account UUID on the
     * matching {@code iam}-tenant credential row (auth-service migration-dev R__01).
     * Operator resolution has been account_id-ONLY since TASK-MONO-299, so a drifted
     * value here does not degrade: it fail-closes to 401.
     */
    private static final String DEMO_OIDC_SUBJECT = "0199de70-0000-7000-8000-00000000ad03";

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
    JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("seed: demo-operator exists, ACTIVE, home tenant demo-corp, and carries the console login's oidc_subject verbatim")
    void demoOperatorSeeded() {
        List<String> rows = jdbcTemplate.queryForList("""
                SELECT CONCAT_WS('|', tenant_id, email, status, oidc_subject)
                  FROM admin_operators WHERE operator_id = 'demo-operator'
                """, String.class);

        assertThat(rows)
                .as("demo-operator row (R__seed_demo_operator.sql). Absent = the whole "
                        + "portfolio demo console is unreachable.")
                .containsExactly("demo-corp|demo@demo.com|ACTIVE|" + DEMO_OIDC_SUBJECT);
    }

    @Test
    @DisplayName("seed: demo-operator may assume exactly {demo-corp, ecommerce} — demo-corp carries the roles, ecommerce carries the data")
    void demoOperatorTenantAssignmentsAreExactlyTheTwo() {
        List<String> tenants = jdbcTemplate.queryForList("""
                SELECT a.tenant_id
                  FROM operator_tenant_assignment a
                  JOIN admin_operators o ON o.id = a.operator_id
                 WHERE o.operator_id = 'demo-operator'
                 ORDER BY a.tenant_id
                """, String.class);

        assertThat(tenants)
                .as("Dropping `ecommerce` returns the console to TASK-BE-576's symptom: the "
                        + "gateway still ACCEPTS the demo-corp token (entitlement-trust), so every "
                        + "E-Commerce list renders 200 with zero rows and the operator cannot write. "
                        + "Nothing else in this repo fails when that row goes missing.")
                .containsExactly("demo-corp", "ecommerce");
    }

    @Test
    @DisplayName("seed: the demo operator's SUPER_ADMIN grant is bound to its home tenant, not to the data tenant")
    void superAdminGrantStaysOnHomeTenant() {
        List<String> grants = jdbcTemplate.queryForList("""
                SELECT CONCAT_WS('|', r.name, g.tenant_id)
                  FROM admin_operator_roles g
                  JOIN admin_operators o ON o.id = g.operator_id
                  JOIN admin_roles r ON r.id = g.role_id
                 WHERE o.operator_id = 'demo-operator'
                 ORDER BY r.name, g.tenant_id
                """, String.class);

        // The second assignment (TASK-BE-576) deliberately grants NO extra role: assuming
        // `ecommerce` derives ECOMMERCE_OPERATOR from that tenant's own subscriptions
        // (ADR-MONO-035 — domain-ops pages are gated by entitlement, not by this RBAC row).
        // If a future change starts minting per-tenant role grants here, this assertion is
        // where that shows up rather than in a widened production surface.
        assertThat(grants).containsExactly("SUPER_ADMIN|demo-corp");
    }

    // ---------------------------------------------------------------------------------
    // TASK-BE-597 — the restricted demo operator (R__seed_demo_viewer_operator.sql) and
    // the one deliberate 403 of the SUPER_ADMIN demo operator.
    // ---------------------------------------------------------------------------------

    /** == the viewer's `iam`-tenant credential account_id (auth-service migration-dev). */
    private static final String VIEWER_OIDC_SUBJECT = "0199de70-0000-7000-8000-00000000ad05";

    @Autowired
    MockMvc mockMvc;

    private static String bearer(String operatorId) {
        return "Bearer " + jwt.operatorToken(operatorId);
    }

    @Test
    @DisplayName("seed: demo-viewer exists, ACTIVE, home tenant demo-viewer (unregistered), with its own login's oidc_subject")
    void viewerOperatorSeeded() {
        List<String> rows = jdbcTemplate.queryForList("""
                SELECT CONCAT_WS('|', tenant_id, email, status, oidc_subject)
                  FROM admin_operators WHERE operator_id = 'demo-viewer'
                """, String.class);

        // Home tenant must NOT be demo-corp: the home tenant is assumable
        // (TenantScopeResolver home ∪ assignments), and demo-corp would derive all five
        // domain OPERATOR roles — write access, the opposite of a restricted account.
        assertThat(rows).containsExactly("demo-viewer|viewer@demo.com|ACTIVE|" + VIEWER_OIDC_SUBJECT);
    }

    @Test
    @DisplayName("seed: demo-viewer holds NO role and NO tenant assignment — minimal by construction")
    void viewerHasNoRoleAndNoAssignment() {
        Integer roles = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM admin_operator_roles g
                  JOIN admin_operators o ON o.id = g.operator_id
                 WHERE o.operator_id = 'demo-viewer'
                """, Integer.class);
        Integer assignments = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM operator_tenant_assignment a
                  JOIN admin_operators o ON o.id = a.operator_id
                 WHERE o.operator_id = 'demo-viewer'
                """, Integer.class);

        assertThat(roles).as("any role row widens the account that exists to show 403").isZero();
        assertThat(assignments).as("any assignment makes a tenant assumable → domain roles").isZero();
    }

    @Test
    @DisplayName("demo-viewer is an authenticated operator: GET /api/admin/me → 200 with an empty role list")
    void viewerAuthenticates() throws Exception {
        mockMvc.perform(get("/api/admin/me").header("Authorization", bearer("demo-viewer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles").isEmpty());
    }

    @Test
    @DisplayName("demo-viewer gets 403 PERMISSION_DENIED on gated screens' reads (operators, audit, roles, tenants)")
    void viewerIsDeniedOnGatedReads() throws Exception {
        for (String path : List.of("/api/admin/operators", "/api/admin/audit",
                "/api/admin/roles", "/api/admin/tenants")) {
            mockMvc.perform(get(path).header("Authorization", bearer("demo-viewer")))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        }
    }

    @Test
    @DisplayName("demo-operator (SUPER_ADMIN) gets 403 on GET /api/admin/partnerships — ADR-MONO-045 D2-C/D3-A, kept by owner decision")
    void superAdminDemoOperatorIsDeniedPartnerships() throws Exception {
        // rbac.md:72,:120 — the platform is not a party to a partnership between two
        // customer tenants. Owner decision 2026-09-24 (TASK-BE-597 option ①): keep it as
        // the demonstrable boundary. If this turns 200, a SUPER_ADMIN grant was added.
        mockMvc.perform(get("/api/admin/partnerships").header("Authorization", bearer("demo-operator")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    // ---------------------------------------------------------------------------------
    // TASK-BE-628 — the assigned-only demo operator (R__seed_demo_assigned_only_operator.sql):
    // HOME ecommerce, ASSIGNED demo-corp, no way to log in. It exists so the console's
    // group member picker has a real «배정만 됨» row inside a tenant the demo operator
    // administers (demo-corp).
    // ---------------------------------------------------------------------------------

    @Test
    @DisplayName("seed: demo-assigned-only exists, ACTIVE, HOME ecommerce, and has NO login path (password_hash and oidc_subject both NULL)")
    void assignedOnlyOperatorSeededWithoutLogin() {
        List<String> rows = jdbcTemplate.queryForList("""
                SELECT CONCAT_WS('|', tenant_id, email, status,
                                 password_hash IS NULL, oidc_subject IS NULL)
                  FROM admin_operators WHERE operator_id = 'demo-assigned-only'
                """, String.class);

        // A non-NULL login column turns a display-only fixture into an account on a demo
        // whose admin credentials are public.
        assertThat(rows).containsExactly("ecommerce|assigned-only@demo.com|ACTIVE|1|1");
    }

    @Test
    @DisplayName("seed: demo-assigned-only holds NO role and is assigned to exactly {demo-corp}")
    void assignedOnlyOperatorHasNoRoleAndOnlyTheDemoCorpAssignment() {
        Integer roles = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM admin_operator_roles g
                  JOIN admin_operators o ON o.id = g.operator_id
                 WHERE o.operator_id = 'demo-assigned-only'
                """, Integer.class);
        List<String> tenants = jdbcTemplate.queryForList("""
                SELECT a.tenant_id FROM operator_tenant_assignment a
                  JOIN admin_operators o ON o.id = a.operator_id
                 WHERE o.operator_id = 'demo-assigned-only'
                 ORDER BY a.tenant_id
                """, String.class);

        assertThat(roles).as("a role row would make this fixture an operator with permissions").isZero();
        assertThat(tenants).as("HOME stays ecommerce; demo-corp is the ONLY assignment").containsExactly("demo-corp");
    }

    @Test
    @DisplayName("demo-operator listing demo-corp sees demo-assigned-only with homeTenantId=ecommerce (the row the picker greys out)")
    void demoCorpOperatorListShowsAssignedOnlyWithItsHomeTenant() throws Exception {
        mockMvc.perform(get("/api/admin/operators?tenantId=demo-corp&size=100")
                        .header("Authorization", bearer("demo-operator")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.operatorId == 'demo-assigned-only')].homeTenantId")
                        .value("ecommerce"))
                // control: the demo operator itself is HOME in demo-corp
                .andExpect(jsonPath("$.content[?(@.operatorId == 'demo-operator')].homeTenantId")
                        .value("demo-corp"));
    }

    // ---------------------------------------------------------------------------------
    // TASK-MONO-781 — the CS 2nd-line demo operator (R__seed_demo_cs_operator.sql):
    // HOME ecommerce, SUPPORT_LOCK bound to ecommerce, confined to ecommerce. It exists so
    // the console's email-search-only «계정 운영» mode (TASK-PC-FE-326) can be shown.
    // ---------------------------------------------------------------------------------

    /** == the CS operator's `iam`-tenant credential account_id (auth-service migration-dev). */
    private static final String CS_OIDC_SUBJECT = "0199de70-0000-7000-8000-00000000ad08";

    @Autowired
    com.example.admin.application.OperatorAssignmentCheckUseCase assignmentCheck;

    @Test
    @DisplayName("seed: demo-cs exists, ACTIVE, HOME ecommerce, confined to ecommerce, with its own login's oidc_subject")
    void csOperatorSeeded() {
        List<String> rows = jdbcTemplate.queryForList("""
                SELECT CONCAT_WS('|', tenant_id, email, status, oidc_subject, confined_tenant_id)
                  FROM admin_operators WHERE operator_id = 'demo-cs'
                """, String.class);

        assertThat(rows).containsExactly("ecommerce|cs@demo.com|ACTIVE|" + CS_OIDC_SUBJECT + "|ecommerce");
    }

    @Test
    @DisplayName("seed: demo-cs holds exactly SUPPORT_LOCK bound to ecommerce (a SITE grant, not '*') and no assignment row")
    void csOperatorHoldsExactlySiteScopedSupportLock() {
        List<String> grants = jdbcTemplate.queryForList("""
                SELECT CONCAT_WS('|', r.name, g.tenant_id)
                  FROM admin_operator_roles g
                  JOIN admin_operators o ON o.id = g.operator_id
                  JOIN admin_roles r ON r.id = g.role_id
                 WHERE o.operator_id = 'demo-cs'
                 ORDER BY r.name, g.tenant_id
                """, String.class);
        Integer assignments = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM operator_tenant_assignment a
                  JOIN admin_operators o ON o.id = a.operator_id
                 WHERE o.operator_id = 'demo-cs'
                """, Integer.class);

        // '*' would make every lock a whole-account lock across sites (TASK-BE-621); any
        // other role widens CS 2nd-line past account control.
        assertThat(grants).containsExactly("SUPPORT_LOCK|ecommerce");
        assertThat(assignments).as("the home tenant is already in scope; an assignment only widens it").isZero();
    }

    @Test
    @DisplayName("demo-cs authenticates: GET /api/admin/me → 200 with roles = [SUPPORT_LOCK]")
    void csOperatorAuthenticatesWithSupportLock() throws Exception {
        mockMvc.perform(get("/api/admin/me").header("Authorization", bearer("demo-cs")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles.length()").value(1))
                .andExpect(jsonPath("$.roles[0]").value("SUPPORT_LOCK"));
    }

    @Test
    @DisplayName("demo-cs is denied the unfiltered account list (no account.read) — the reason the console opens search-only")
    void csOperatorIsDeniedTheUnfilteredAccountList() throws Exception {
        mockMvc.perform(get("/api/admin/accounts").header("Authorization", bearer("demo-cs")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    /**
     * 🔴 The DISCLOSED trade-off (owner decision A, 2026-10-09 UTC), admin side: the assume
     * gate admits demo-cs into `ecommerce` with NO second factor and no partnership cap. Once
     * admitted, auth-service derives the assumed token's roles from `ecommerce`'s ACTIVE
     * subscriptions — that half is pinned by auth-service DemoCsOperatorDerivedRolesTest
     * (ECOMMERCE_OPERATOR + the WMS operator tier). If this ever answers assigned=false for
     * `ecommerce`, the CS identity can no longer reach «계정 운영» at all; if it answers
     * true for demo-corp, it would receive all five domain OPERATOR roles.
     */
    @Test
    @DisplayName("assume gate: demo-cs may assume exactly ecommerce (no 2FA, no cap), and is refused demo-corp / fan-platform")
    void csOperatorMayAssumeOnlyEcommerce() {
        var ecommerce = assignmentCheck.check(CS_OIDC_SUBJECT, "ecommerce");
        assertThat(ecommerce.assigned()).isTrue();
        assertThat(ecommerce.mfaRequired())
                .as("SUPPORT_LOCK require_2fa=FALSE and no tenant_entry_policy row is seeded")
                .isFalse();
        assertThat(ecommerce.delegatedScope())
                .as("a normal (home) admission — no partnership cap narrows the derived roles")
                .isNull();

        assertThat(assignmentCheck.check(CS_OIDC_SUBJECT, "demo-corp").assigned()).isFalse();
        assertThat(assignmentCheck.check(CS_OIDC_SUBJECT, "fan-platform").assigned()).isFalse();
    }
}
