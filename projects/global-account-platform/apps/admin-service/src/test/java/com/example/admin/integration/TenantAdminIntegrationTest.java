package com.example.admin.integration;

import com.example.testsupport.integration.AbstractIntegrationTest;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.example.admin.support.OperatorJwtTestFixture;
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
import java.util.Base64;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-BE-250: End-to-end integration test for the tenant lifecycle API.
 *
 * <p>Boots the admin-service against real MySQL + Kafka (via Testcontainers) and
 * a WireMock stub replacing account-service. Verifies audit row creation, outbox
 * event emission, permission gating, and error propagation.
 *
 * <p>Skipped automatically when Docker is unavailable (DockerAvailableCondition).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class TenantAdminIntegrationTest extends AbstractIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    static WireMockServer wireMock;
    static OperatorJwtTestFixture jwt;
    static String signingKeyPem;

    private static final String SUPER_ADMIN_UUID  = "00000000-0000-7000-8000-000000000002";
    private static final String REGULAR_OP_UUID   = "00000000-0000-7000-8000-000000000003";

    @BeforeAll
    static void setupShared() throws IOException {
        jwt = new OperatorJwtTestFixture();

        java.security.PrivateKey pk = extractPrivateKey(jwt);
        signingKeyPem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(pk.getEncoded())
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
    }

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetAndSeed() {
        wireMock.resetAll();
        seedSuperAdmin();
        seedRegularOp();
    }

    private void seedSuperAdmin() {
        Integer existing = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM admin_operators WHERE operator_id = ?",
                Integer.class, SUPER_ADMIN_UUID);
        if (existing == null || existing == 0) {
            jdbcTemplate.update("""
                    INSERT INTO admin_operators
                      (operator_id, tenant_id, email, password_hash, display_name, status,
                       created_at, updated_at, version)
                    VALUES (?, '*', ?, 'x', ?, 'ACTIVE', NOW(6), NOW(6), 0)
                    """, SUPER_ADMIN_UUID, "superadmin-tenant@example.com", "Super Admin Tenant");
        }
        jdbcTemplate.update("""
                INSERT IGNORE INTO admin_operator_roles (operator_id, role_id, tenant_id, granted_at, granted_by)
                SELECT o.id, r.id, '*', NOW(6), NULL
                  FROM admin_operators o CROSS JOIN admin_roles r
                 WHERE o.operator_id = ? AND r.name = 'SUPER_ADMIN'
                """, SUPER_ADMIN_UUID);
    }

    private void seedRegularOp() {
        Integer existing = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM admin_operators WHERE operator_id = ?",
                Integer.class, REGULAR_OP_UUID);
        if (existing == null || existing == 0) {
            jdbcTemplate.update("""
                    INSERT INTO admin_operators
                      (operator_id, tenant_id, email, password_hash, display_name, status,
                       created_at, updated_at, version)
                    VALUES (?, 'tenant-a', ?, 'x', ?, 'ACTIVE', NOW(6), NOW(6), 0)
                    """, REGULAR_OP_UUID, "regular-tenant@example.com", "Regular Op Tenant");
        }
    }

    private String superAdminToken() {
        return "Bearer " + jwt.operatorToken(SUPER_ADMIN_UUID);
    }

    private String regularOpToken() {
        return "Bearer " + jwt.operatorToken(REGULAR_OP_UUID);
    }

    // ---- Helpers for WireMock stubs ----------------------------------------

    private static final String WMS_TENANT_RESPONSE = """
            {
              "tenantId": "wms-test",
              "displayName": "WMS Test",
              "tenantType": "B2B_ENTERPRISE",
              "status": "ACTIVE",
              "createdAt": "2026-05-02T09:00:00Z",
              "updatedAt": "2026-05-02T09:00:00Z"
            }
            """;

    private static final String WMS_SUSPENDED_RESPONSE = """
            {
              "tenantId": "wms-test",
              "displayName": "WMS Test",
              "tenantType": "B2B_ENTERPRISE",
              "status": "SUSPENDED",
              "createdAt": "2026-05-02T09:00:00Z",
              "updatedAt": "2026-05-02T09:01:00Z"
            }
            """;

    // ---- Tests -------------------------------------------------------------

    @Test
    @DisplayName("SUPER_ADMIN POST → 201 + admin_actions TENANT_CREATE + outbox tenant.created")
    void superAdmin_post_creates_tenant_and_emits_outbox() throws Exception {
        wireMock.stubFor(WireMock.post(urlPathEqualTo("/internal/tenants"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody(WMS_TENANT_RESPONSE)));

        mockMvc.perform(post("/api/admin/tenants")
                        .header("Authorization", superAdminToken())
                        .header("X-Operator-Reason", "regression test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantId":"wms-test","displayName":"WMS Test","tenantType":"B2B_ENTERPRISE"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tenantId").value("wms-test"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        // Verify admin_actions row
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM admin_actions WHERE action_code = 'TENANT_CREATE' " +
                "AND target_id = 'wms-test' AND outcome = 'SUCCESS'",
                Integer.class);
        assertThat(count).isGreaterThan(0);

        // Verify outbox event
        Integer outboxCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox WHERE event_type = 'tenant.created'",
                Integer.class);
        assertThat(outboxCount).isGreaterThan(0);
    }

    @Test
    @DisplayName("Regular operator POST → 403 PERMISSION_DENIED (lacks tenant.manage; aspect rejects before platform-scope gate)")
    void regularOperator_post_returns_403() throws Exception {
        mockMvc.perform(post("/api/admin/tenants")
                        .header("Authorization", regularOpToken())
                        .header("X-Operator-Reason", "regression test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantId":"wms-test","displayName":"WMS Test","tenantType":"B2B_ENTERPRISE"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    @Test
    @DisplayName("SUPER_ADMIN POST with invalid slug → 400 VALIDATION_ERROR")
    void superAdmin_post_invalid_slug_returns_400() throws Exception {
        mockMvc.perform(post("/api/admin/tenants")
                        .header("Authorization", superAdminToken())
                        .header("X-Operator-Reason", "regression test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantId":"WMS","displayName":"WMS","tenantType":"B2B_ENTERPRISE"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("SUPER_ADMIN POST reserved 'admin' → 400 TENANT_ID_RESERVED")
    void superAdmin_post_reserved_returns_400() throws Exception {
        mockMvc.perform(post("/api/admin/tenants")
                        .header("Authorization", superAdminToken())
                        .header("X-Operator-Reason", "regression test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantId":"admin","displayName":"Admin Tenant","tenantType":"B2B_ENTERPRISE"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TENANT_ID_RESERVED"));
    }

    @Test
    @DisplayName("SUPER_ADMIN POST duplicate tenantId → 409 TENANT_ALREADY_EXISTS")
    void superAdmin_post_duplicate_returns_409() throws Exception {
        wireMock.stubFor(WireMock.post(urlPathEqualTo("/internal/tenants"))
                .willReturn(aResponse()
                        .withStatus(409)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"code\":\"TENANT_ALREADY_EXISTS\",\"message\":\"exists\"}")));

        mockMvc.perform(post("/api/admin/tenants")
                        .header("Authorization", superAdminToken())
                        .header("X-Operator-Reason", "regression test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantId":"wms-test","displayName":"WMS","tenantType":"B2B_ENTERPRISE"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TENANT_ALREADY_EXISTS"));
    }

    @Test
    @DisplayName("account-service 5xx repeatedly → 503 INTEGRATION_UNAVAILABLE (CB)")
    void accountService_5xx_returns_503() throws Exception {
        wireMock.stubFor(WireMock.post(urlPathEqualTo("/internal/tenants"))
                .willReturn(aResponse().withStatus(503)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"code\":\"DOWNSTREAM_ERROR\"}")));

        // Fire enough requests to open the CB; expect 503 on each
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/admin/tenants")
                            .header("Authorization", superAdminToken())
                            .header("X-Operator-Reason", "cb regression test")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"tenantId\":\"cb-test-" + i + "\","
                                    + "\"displayName\":\"CB Test\","
                                    + "\"tenantType\":\"B2B_ENTERPRISE\"}"))
                    .andExpect(status().isServiceUnavailable());
        }
    }

    @Test
    @DisplayName("SUPER_ADMIN PATCH status=SUSPENDED → 200 + TENANT_SUSPEND audit + outbox")
    void superAdmin_patch_suspend_emits_audit_and_outbox() throws Exception {
        // GET stub for current state
        wireMock.stubFor(WireMock.get(urlPathEqualTo("/internal/tenants/wms-test"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(WMS_TENANT_RESPONSE)));
        // PATCH stub
        wireMock.stubFor(WireMock.patch(urlPathEqualTo("/internal/tenants/wms-test"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(WMS_SUSPENDED_RESPONSE)));

        mockMvc.perform(patch("/api/admin/tenants/wms-test")
                        .header("Authorization", superAdminToken())
                        .header("X-Operator-Reason", "regression test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"SUSPENDED"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM admin_actions WHERE action_code = 'TENANT_SUSPEND' " +
                "AND target_id = 'wms-test' AND outcome = 'SUCCESS'",
                Integer.class);
        assertThat(count).isGreaterThan(0);

        Integer outboxCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox WHERE event_type = 'tenant.suspended'",
                Integer.class);
        assertThat(outboxCount).isGreaterThan(0);
    }

    @Test
    @DisplayName("SUPER_ADMIN PATCH status=ACTIVE on already-ACTIVE → 200, no audit, no event")
    void superAdmin_patch_same_status_noop() throws Exception {
        wireMock.stubFor(WireMock.get(urlPathEqualTo("/internal/tenants/wms-test"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(WMS_TENANT_RESPONSE)));

        int countBefore = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM admin_actions WHERE action_code LIKE 'TENANT_%'",
                Integer.class);

        mockMvc.perform(patch("/api/admin/tenants/wms-test")
                        .header("Authorization", superAdminToken())
                        .header("X-Operator-Reason", "regression test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"ACTIVE"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        int countAfter = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM admin_actions WHERE action_code LIKE 'TENANT_%'",
                Integer.class);
        assertThat(countAfter).isEqualTo(countBefore); // no new audit rows
    }

    @Test
    @DisplayName("SUPER_ADMIN PATCH displayName → 200 + TENANT_UPDATE audit + outbox tenant.updated")
    void superAdmin_patch_displayName_emits_update_audit() throws Exception {
        wireMock.stubFor(WireMock.get(urlPathEqualTo("/internal/tenants/wms-test"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(WMS_TENANT_RESPONSE)));

        String updatedResponse = WMS_TENANT_RESPONSE.replace("WMS Test", "WMS Platform");
        wireMock.stubFor(WireMock.patch(urlPathEqualTo("/internal/tenants/wms-test"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(updatedResponse)));

        mockMvc.perform(patch("/api/admin/tenants/wms-test")
                        .header("Authorization", superAdminToken())
                        .header("X-Operator-Reason", "regression test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"WMS Platform"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("WMS Platform"));

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM admin_actions WHERE action_code = 'TENANT_UPDATE' " +
                "AND target_id = 'wms-test' AND outcome = 'SUCCESS'",
                Integer.class);
        assertThat(count).isGreaterThan(0);

        Integer outboxCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox WHERE event_type = 'tenant.updated'",
                Integer.class);
        assertThat(outboxCount).isGreaterThan(0);
    }

    @Test
    @DisplayName("SUPER_ADMIN GET /api/admin/tenants → 200 paginated")
    void superAdmin_list_tenants_returns_200() throws Exception {
        wireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo("/internal/tenants"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "items": [{"tenantId":"fan-platform","displayName":"Fan Platform",
                                    "tenantType":"B2C_CONSUMER","status":"ACTIVE",
                                    "createdAt":"2026-04-01T00:00:00Z","updatedAt":"2026-04-01T00:00:00Z"}],
                                  "page":0,"size":20,"totalElements":1,"totalPages":1
                                }
                                """)));

        mockMvc.perform(get("/api/admin/tenants")
                        .header("Authorization", superAdminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].tenantId").value("fan-platform"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("SUPER_ADMIN GET /api/admin/tenants/wms-test → 200")
    void superAdmin_get_tenant_returns_200() throws Exception {
        wireMock.stubFor(WireMock.get(urlPathEqualTo("/internal/tenants/wms-test"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(WMS_TENANT_RESPONSE)));

        mockMvc.perform(get("/api/admin/tenants/wms-test")
                        .header("Authorization", superAdminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value("wms-test"));
    }

    @Test
    @DisplayName("Regular operator GET own tenant → 200")
    void regularOperator_get_own_tenant_returns_200() throws Exception {
        wireMock.stubFor(WireMock.get(urlPathEqualTo("/internal/tenants/tenant-a"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"tenantId":"tenant-a","displayName":"Tenant A",
                                 "tenantType":"B2C_CONSUMER","status":"ACTIVE",
                                 "createdAt":"2026-04-01T00:00:00Z","updatedAt":"2026-04-01T00:00:00Z"}
                                """)));

        mockMvc.perform(get("/api/admin/tenants/tenant-a")
                        .header("Authorization", regularOpToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value("tenant-a"));
    }

    @Test
    @DisplayName("Regular operator GET other tenant → 403 TENANT_SCOPE_DENIED")
    void regularOperator_get_other_tenant_returns_403() throws Exception {
        mockMvc.perform(get("/api/admin/tenants/tenant-b")
                        .header("Authorization", regularOpToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_SCOPE_DENIED"));
    }
}
