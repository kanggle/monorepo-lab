package com.example.admin.integration;

import com.example.admin.support.OperatorJwtTestFixture;
import com.example.testsupport.integration.AbstractIntegrationTest;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
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

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-BE-296: end-to-end integration test for
 * {@code GET /api/admin/console/registry}.
 *
 * <p>Boots admin-service against real MySQL + Kafka (Testcontainers) + Redis,
 * with a WireMock stub replacing account-service's internal tenant endpoints.
 * Verifies:
 * <ul>
 *   <li>response matches console-integration-contract § 2.2 item shape exactly
 *       (productKey / displayName / available / tenants / baseRoute);</li>
 *   <li>erp / finance representable as {@code available:false};</li>
 *   <li>operator-scoped + tenant-aware: SUPER_ADMIN sees all tenants;</li>
 *   <li><b>multi-tenant isolation regression</b>: a single-tenant operator
 *       never receives another tenant's slug ({@code rules/traits/multi-tenant.md}
 *       M6 / task Failure Scenario "Registry leaks cross-tenant products");</li>
 *   <li>missing operator JWT → 401.</li>
 * </ul>
 *
 * <p>Skipped automatically when Docker is unavailable
 * ({@code AbstractIntegrationTest} DockerAvailableCondition).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class ConsoleRegistryIntegrationTest extends AbstractIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    static WireMockServer wireMock;
    static OperatorJwtTestFixture jwt;
    static String signingKeyPem;

    private static final String SUPER_ADMIN_UUID      = "00000000-0000-7000-8000-000000000010";
    private static final String WMS_OP_UUID           = "00000000-0000-7000-8000-000000000011";
    // TASK-BE-304: dedicated operator for the operatorContext set-case.
    // Kept separate from SUPER_ADMIN_UUID so the other tests' assertions
    // (which read a NULL finance_default_account_id) remain stable.
    private static final String FINANCE_OP_UUID       = "00000000-0000-7000-8000-000000000012";
    private static final String FINANCE_ACCOUNT_UUID  =
            "01928c4a-7e9f-7c00-9a40-d2b1f5e8a000";
    // TASK-BE-305: single-tenant operators for finance + erp isolation regression guard.
    private static final String FINANCE_TENANT_OP_UUID = "00000000-0000-7000-8000-000000000013";
    private static final String ERP_TENANT_OP_UUID     = "00000000-0000-7000-8000-000000000014";

    @BeforeAll
    static void setupShared() throws IOException {
        jwt = new OperatorJwtTestFixture();

        java.security.PrivateKey pk = extractPrivateKey(jwt);
        signingKeyPem = "-----BEGIN PRIVATE KEY-----\n"
                + java.util.Base64.getMimeEncoder(64, "\n".getBytes())
                        .encodeToString(pk.getEncoded())
                + "\n-----END PRIVATE KEY-----\n";

        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();
        WireMock.configureFor("localhost", wireMock.port());
    }

    @AfterAll
    static void tearDownShared() {
        if (wireMock != null) wireMock.stop();
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
        registry.add("admin.auth-service.base-url", wireMock::baseUrl);
        registry.add("admin.account-service.base-url", wireMock::baseUrl);
        registry.add("admin.security-service.base-url", wireMock::baseUrl);
        // TASK-BE-318b: account (tenant) client fetches a GAP client_credentials Bearer token.
        registry.add("gap.internal-client.token-uri", () -> wireMock.baseUrl() + "/oauth2/token");
    }

    /**
     * TASK-BE-318b: stub the GAP client_credentials token endpoint so the tenant client can
     * obtain a Bearer token. resetAll() clears stubs, so (re)register after every reset.
     */
    private static void stubGapTokenEndpoint() {
        wireMock.stubFor(WireMock.post(WireMock.urlEqualTo("/oauth2/token"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"access_token\":\"test-jwt\",\"expires_in\":300,\"token_type\":\"Bearer\"}")));
    }

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;

    /** Three baseline portfolio tenants registered + ACTIVE (no finance/erp rows). */
    private static final String ALL_ACTIVE_TENANTS = """
            {
              "items": [
                {"tenantId":"fan-platform","displayName":"Fan Platform","tenantType":"B2C_CONSUMER",
                 "status":"ACTIVE","createdAt":"2026-04-01T00:00:00Z","updatedAt":"2026-04-01T00:00:00Z"},
                {"tenantId":"wms","displayName":"Warehouse Management Platform","tenantType":"B2B_ENTERPRISE",
                 "status":"ACTIVE","createdAt":"2026-04-01T00:00:00Z","updatedAt":"2026-04-01T00:00:00Z"},
                {"tenantId":"scm","displayName":"Supply Chain Management Platform","tenantType":"B2B_ENTERPRISE",
                 "status":"ACTIVE","createdAt":"2026-04-01T00:00:00Z","updatedAt":"2026-04-01T00:00:00Z"}
              ],
              "page":0,"size":100,"totalElements":3,"totalPages":1
            }
            """;

    /**
     * TASK-BE-305: all five V1 portfolio tenants registered + ACTIVE.
     * Used by the single-tenant finance/erp isolation IT cases to prove
     * per-operator scoping works once the slugs are ACTIVE-registered.
     */
    private static final String ALL_FIVE_ACTIVE_TENANTS = """
            {
              "items": [
                {"tenantId":"fan-platform","displayName":"Fan Platform","tenantType":"B2C_CONSUMER",
                 "status":"ACTIVE","createdAt":"2026-04-01T00:00:00Z","updatedAt":"2026-04-01T00:00:00Z"},
                {"tenantId":"wms","displayName":"Warehouse Management Platform","tenantType":"B2B_ENTERPRISE",
                 "status":"ACTIVE","createdAt":"2026-04-01T00:00:00Z","updatedAt":"2026-04-01T00:00:00Z"},
                {"tenantId":"scm","displayName":"Supply Chain Management Platform","tenantType":"B2B_ENTERPRISE",
                 "status":"ACTIVE","createdAt":"2026-04-01T00:00:00Z","updatedAt":"2026-04-01T00:00:00Z"},
                {"tenantId":"finance","displayName":"Finance Platform","tenantType":"B2B_ENTERPRISE",
                 "status":"ACTIVE","createdAt":"2026-04-01T00:00:00Z","updatedAt":"2026-04-01T00:00:00Z"},
                {"tenantId":"erp","displayName":"Enterprise Resource Planning","tenantType":"B2B_ENTERPRISE",
                 "status":"ACTIVE","createdAt":"2026-04-01T00:00:00Z","updatedAt":"2026-04-01T00:00:00Z"}
              ],
              "page":0,"size":100,"totalElements":5,"totalPages":1
            }
            """;

    /**
     * TASK-BE-322 (ADR-MONO-019 step 1): the backward-compatible subscription
     * seed — each domain-slug tenant self-subscribes. This makes the
     * subscription-driven catalog binding byte-identical to the pre-BE-322
     * slug binding (net-zero). `gap` is intentionally absent (it federates all
     * tenants via bindsAllTenants and never consults this surface).
     */
    private static final String BACKWARD_COMPAT_SUBSCRIPTIONS = """
            {
              "items": [
                {"tenantId":"wms","domainKey":"wms"},
                {"tenantId":"scm","domainKey":"scm"},
                {"tenantId":"erp","domainKey":"erp"},
                {"tenantId":"finance","domainKey":"finance"}
              ]
            }
            """;

    /**
     * TASK-BE-322: re-register the subscription endpoint stub. resetAll() clears
     * stubs, so call after every reset (mirrors stubGapTokenEndpoint()).
     */
    private static void stubSubscriptionsEndpoint() {
        wireMock.stubFor(WireMock.get(urlPathEqualTo("/internal/tenant-domain-subscriptions"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(BACKWARD_COMPAT_SUBSCRIPTIONS)));
    }

    @BeforeEach
    void resetAndSeed() {
        wireMock.resetAll();
        stubGapTokenEndpoint();
        stubSubscriptionsEndpoint();
        wireMock.stubFor(WireMock.get(urlPathEqualTo("/internal/tenants"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(ALL_ACTIVE_TENANTS)));
        seedOperator(SUPER_ADMIN_UUID, "*");
        seedOperator(WMS_OP_UUID, "wms");
    }

    private void seedOperator(String operatorId, String tenantId) {
        Integer existing = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM admin_operators WHERE operator_id = ?",
                Integer.class, operatorId);
        if (existing == null || existing == 0) {
            jdbcTemplate.update("""
                    INSERT INTO admin_operators
                      (operator_id, tenant_id, email, password_hash, display_name, status,
                       created_at, updated_at, version)
                    VALUES (?, ?, ?, 'x', ?, 'ACTIVE', NOW(6), NOW(6), 0)
                    """, operatorId, tenantId, operatorId + "@example.com",
                    "Op " + operatorId.substring(operatorId.length() - 2));
        }
    }

    private String token(String operatorId) {
        return "Bearer " + jwt.operatorToken(operatorId);
    }

    // -----------------------------------------------------------------------

    @Test
    @DisplayName("SUPER_ADMIN → 200, contract shape exact, gap binds all tenants, erp/finance available=true (TASK-BE-305)")
    void superAdmin_returnsFullCatalog() throws Exception {
        // ALL_ACTIVE_TENANTS stub has fan-platform/wms/scm only (no erp/finance rows).
        // erp/finance available=true; tenants=[] because their slugs are not registered
        // in the tenant list (tenant-selection rule: slug not in activeTenants → []).
        // AC-4: wms-operator isolation test still holds (erp/finance tenants:[]).
        mockMvc.perform(get("/api/admin/console/registry")
                        .header("Authorization", token(SUPER_ADMIN_UUID)))
                .andExpect(status().isOk())
                // exactly 5 products in catalog order
                .andExpect(jsonPath("$.products.length()").value(5))
                .andExpect(jsonPath("$.products[0].productKey").value("gap"))
                .andExpect(jsonPath("$.products[0].displayName").value("Global Account Platform"))
                .andExpect(jsonPath("$.products[0].available").value(true))
                .andExpect(jsonPath("$.products[0].baseRoute").value("/gap"))
                // gap federates all registered ACTIVE tenants for a platform-scope op
                .andExpect(jsonPath("$.products[0].tenants",
                        org.hamcrest.Matchers.containsInAnyOrder("fan-platform", "wms", "scm")))
                .andExpect(jsonPath("$.products[1].productKey").value("wms"))
                .andExpect(jsonPath("$.products[1].available").value(true))
                .andExpect(jsonPath("$.products[1].tenants[0]").value("wms"))
                .andExpect(jsonPath("$.products[2].productKey").value("scm"))
                .andExpect(jsonPath("$.products[2].tenants[0]").value("scm"))
                // erp / finance — V1 live (TASK-BE-305); slugs not yet in test tenant
                // list so tenants:[] — available:true is the key assertion change.
                .andExpect(jsonPath("$.products[3].productKey").value("erp"))
                .andExpect(jsonPath("$.products[3].available").value(true))
                .andExpect(jsonPath("$.products[3].tenants.length()").value(0))
                .andExpect(jsonPath("$.products[4].productKey").value("finance"))
                .andExpect(jsonPath("$.products[4].available").value(true))
                .andExpect(jsonPath("$.products[4].tenants.length()").value(0));
    }

    @Test
    @DisplayName("multi-tenant isolation regression: wms-scoped operator never sees fan-platform/scm slugs")
    void singleTenantOperator_isCrossTenantIsolated() throws Exception {
        mockMvc.perform(get("/api/admin/console/registry")
                        .header("Authorization", token(WMS_OP_UUID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products.length()").value(5))
                // gap shows ONLY the operator's own tenant — not the full list
                .andExpect(jsonPath("$.products[0].productKey").value("gap"))
                .andExpect(jsonPath("$.products[0].tenants.length()").value(1))
                .andExpect(jsonPath("$.products[0].tenants[0]").value("wms"))
                // wms product → own tenant
                .andExpect(jsonPath("$.products[1].tenants[0]").value("wms"))
                // scm product → not selectable by a wms operator
                .andExpect(jsonPath("$.products[2].productKey").value("scm"))
                .andExpect(jsonPath("$.products[2].tenants.length()").value(0))
                // hard isolation assertion: NO product anywhere leaks another tenant
                .andExpect(jsonPath(
                        "$.products[*].tenants[?(@ == 'fan-platform' || @ == 'scm')]")
                        .doesNotExist());
    }

    @Test
    @DisplayName("no operator JWT → 401 TOKEN_INVALID")
    void noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/admin/console/registry"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
    }

    @Test
    @DisplayName("account-service tenant list 5xx → 503 (no partial catalog)")
    void accountServiceDown_returns503() throws Exception {
        wireMock.resetAll();
        stubGapTokenEndpoint();
        stubSubscriptionsEndpoint();
        wireMock.stubFor(WireMock.get(urlPathEqualTo("/internal/tenants"))
                .willReturn(aResponse().withStatus(503)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"code\":\"DOWNSTREAM_ERROR\"}")));

        mockMvc.perform(get("/api/admin/console/registry")
                        .header("Authorization", token(SUPER_ADMIN_UUID)))
                .andExpect(status().isServiceUnavailable());
    }

    // ── TASK-BE-304: operatorContext emission ────────────────────────────────

    @Test
    @DisplayName("TASK-BE-304 (a) null case: finance_default_account_id IS NULL → body has no 'operatorContext' substring (AC-2)")
    void operatorContext_nullCase_omittedFromBody() throws Exception {
        // SUPER_ADMIN_UUID is seeded by resetAndSeed() without
        // finance_default_account_id (column defaults to NULL via V0028).
        String body = mockMvc.perform(get("/api/admin/console/registry")
                        .header("Authorization", token(SUPER_ADMIN_UUID)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body)
                .as("AC-2: null column → @JsonInclude.NON_NULL omits operatorContext entirely. body=%s", body)
                .doesNotContain("operatorContext");
    }

    @Test
    @DisplayName("TASK-BE-304 (b) set case: finance_default_account_id=<uuid> → finance item carries operatorContext.defaultAccountId, other 4 items don't (AC-3)")
    void operatorContext_setCase_emittedOnlyOnFinanceItem() throws Exception {
        // Seed a dedicated operator with finance_default_account_id set via
        // raw JDBC after Flyway runs (V0028 has provisioned the column).
        seedOperatorWithFinanceAccount(FINANCE_OP_UUID, "*", FINANCE_ACCOUNT_UUID);

        String body = mockMvc.perform(get("/api/admin/console/registry")
                        .header("Authorization", token(FINANCE_OP_UUID)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body)
                .as("AC-3 (a): finance item present. body=%s", body)
                .contains("\"productKey\":\"finance\"");
        assertThat(body)
                .as("AC-3 (a): operatorContext rendered with the exact UUID. body=%s", body)
                .contains("\"operatorContext\":{\"defaultAccountId\":\"" + FINANCE_ACCOUNT_UUID + "\"}");
        // Regression guard for non-finance leakage — substring count == 1.
        int occurrences = (body.length() - body.replace("operatorContext", "").length())
                / "operatorContext".length();
        assertThat(occurrences)
                .as("AC-3 (b) regression guard: substring 'operatorContext' MUST appear exactly once (only on finance). body=%s", body)
                .isEqualTo(1);
    }

    // ── TASK-BE-305: single-tenant finance + erp isolation regression guard ──────

    @Test
    @DisplayName("TASK-BE-305: single-tenant finance operator → finance available=true tenants=[finance], erp available=true tenants=[] (no cross-tenant leak)")
    void singleTenantFinanceOperator_seesFinanceInteractive_erpEmpty() throws Exception {
        // Override the WireMock tenant stub for this test: all 5 slugs ACTIVE so
        // the tenant-selection rule can populate finance.tenants and erp.tenants.
        wireMock.resetAll();
        stubGapTokenEndpoint();
        stubSubscriptionsEndpoint();
        wireMock.stubFor(WireMock.get(urlPathEqualTo("/internal/tenants"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(ALL_FIVE_ACTIVE_TENANTS)));
        seedOperator(FINANCE_TENANT_OP_UUID, "finance");

        mockMvc.perform(get("/api/admin/console/registry")
                        .header("Authorization", token(FINANCE_TENANT_OP_UUID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products.length()").value(5))
                // finance product: available=true, own tenant selectable
                .andExpect(jsonPath("$.products[4].productKey").value("finance"))
                .andExpect(jsonPath("$.products[4].available").value(true))
                .andExpect(jsonPath("$.products[4].tenants.length()").value(1))
                .andExpect(jsonPath("$.products[4].tenants[0]").value("finance"))
                // erp product: available=true, but own tenant (finance) doesn't match erp binding
                .andExpect(jsonPath("$.products[3].productKey").value("erp"))
                .andExpect(jsonPath("$.products[3].available").value(true))
                .andExpect(jsonPath("$.products[3].tenants.length()").value(0))
                // hard isolation: no product leaks another tenant's slug
                .andExpect(jsonPath(
                        "$.products[*].tenants[?(@ == 'wms' || @ == 'scm' || @ == 'erp')]")
                        .doesNotExist());
    }

    @Test
    @DisplayName("TASK-BE-305: single-tenant erp operator → erp available=true tenants=[erp], finance available=true tenants=[] (no cross-tenant leak)")
    void singleTenantErpOperator_seesErpInteractive_financeEmpty() throws Exception {
        // Override the WireMock tenant stub for this test: all 5 slugs ACTIVE.
        wireMock.resetAll();
        stubGapTokenEndpoint();
        stubSubscriptionsEndpoint();
        wireMock.stubFor(WireMock.get(urlPathEqualTo("/internal/tenants"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(ALL_FIVE_ACTIVE_TENANTS)));
        seedOperator(ERP_TENANT_OP_UUID, "erp");

        mockMvc.perform(get("/api/admin/console/registry")
                        .header("Authorization", token(ERP_TENANT_OP_UUID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products.length()").value(5))
                // erp product: available=true, own tenant selectable
                .andExpect(jsonPath("$.products[3].productKey").value("erp"))
                .andExpect(jsonPath("$.products[3].available").value(true))
                .andExpect(jsonPath("$.products[3].tenants.length()").value(1))
                .andExpect(jsonPath("$.products[3].tenants[0]").value("erp"))
                // finance product: available=true, but own tenant (erp) doesn't match finance binding
                .andExpect(jsonPath("$.products[4].productKey").value("finance"))
                .andExpect(jsonPath("$.products[4].available").value(true))
                .andExpect(jsonPath("$.products[4].tenants.length()").value(0))
                // hard isolation: no product leaks another tenant's slug
                .andExpect(jsonPath(
                        "$.products[*].tenants[?(@ == 'wms' || @ == 'scm' || @ == 'finance')]")
                        .doesNotExist());
    }

    /**
     * TASK-BE-304: helper to seed an operator with a non-null
     * {@code finance_default_account_id}. V0028 has already added the
     * column; we set it via raw JDBC because there is no production
     * mutation surface for this column in Phase 1 (operator-management
     * mutation is out of scope per the task spec § Out of Scope).
     */
    private void seedOperatorWithFinanceAccount(String operatorId,
                                                 String tenantId,
                                                 String financeAccountId) {
        Integer existing = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM admin_operators WHERE operator_id = ?",
                Integer.class, operatorId);
        if (existing == null || existing == 0) {
            jdbcTemplate.update("""
                    INSERT INTO admin_operators
                      (operator_id, tenant_id, email, password_hash, display_name, status,
                       finance_default_account_id, created_at, updated_at, version)
                    VALUES (?, ?, ?, 'x', ?, 'ACTIVE', ?, NOW(6), NOW(6), 0)
                    """, operatorId, tenantId, operatorId + "@example.com",
                    "Op " + operatorId.substring(operatorId.length() - 2),
                    financeAccountId);
        } else {
            jdbcTemplate.update(
                    "UPDATE admin_operators SET finance_default_account_id = ? WHERE operator_id = ?",
                    financeAccountId, operatorId);
        }
    }
}
