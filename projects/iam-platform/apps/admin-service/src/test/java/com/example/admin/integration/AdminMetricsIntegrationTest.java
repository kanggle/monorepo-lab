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

import java.security.KeyPair;
import java.util.Base64;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-MONO-811 — {@code GET /api/admin/metrics} end to end in admin-service: real MySQL grants (Flyway
 * seed roles), the real {@code RequiresPermissionAspect} + {@code QueryTenantScopeGate}, and WireMock in
 * place of security-service and account-service. Proves that the tenant the gate resolved is the one sent
 * downstream, that a refusal sends nothing downstream and writes a DENIED {@code METRICS_QUERY} row, and
 * that one failing downstream fails the whole call.
 *
 * <p>Skipped when Docker is unavailable (AbstractIntegrationTest) — CI Linux is authoritative.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@Tag("integration")
@org.springframework.test.annotation.DirtiesContext(
        classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class AdminMetricsIntegrationTest extends AbstractIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    static WireMockServer wireMock;
    static OperatorJwtTestFixture jwt;
    static String adminSigningKeyPem;

    private static final String SECURITY_PATH = "/internal/security/metrics/daily";
    private static final String ACCOUNT_PATH = "/internal/account-metrics/daily";

    private static final String TENANT = "m811-tenant";
    private static final String TENANT_OP_UUID = "00000000-0000-7000-8000-000000811001";
    private static final String SUPER_ADMIN_UUID = "00000000-0000-7000-8000-000000811002";
    private static final String NO_ROLE_UUID = "00000000-0000-7000-8000-000000811003";

    @BeforeAll
    static void setupShared() {
        jwt = new OperatorJwtTestFixture();
        java.security.PrivateKey pk = extractPrivateKey(jwt);
        adminSigningKeyPem = "-----BEGIN PRIVATE KEY-----\n"
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
            var f = OperatorJwtTestFixture.class.getDeclaredField("keyPair");
            f.setAccessible(true);
            return ((KeyPair) f.get(fixture)).getPrivate();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("admin.jwt.active-signing-kid", () -> "test-key-001");
        registry.add("admin.jwt.signing-keys.test-key-001", () -> adminSigningKeyPem);
        registry.add("admin.jwt.issuer", () -> "admin-service");
        registry.add("admin.jwt.expected-token-type", () -> "admin");
        registry.add("admin.account-service.base-url", wireMock::baseUrl);
        registry.add("admin.auth-service.base-url", wireMock::baseUrl);
        registry.add("admin.security-service.base-url", wireMock::baseUrl);
        registry.add("iam.internal-client.token-uri", () -> wireMock.baseUrl() + "/oauth2/token");
    }

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seed() {
        wireMock.resetAll();
        wireMock.stubFor(WireMock.post(WireMock.urlEqualTo("/oauth2/token"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"access_token\":\"test-jwt\",\"expires_in\":300,\"token_type\":\"Bearer\"}")));
        stubDownstreams();

        // SUPPORT_LOCK carries audit.read (V0022) — the same grade GET /api/admin/audit needs.
        seedOperator(TENANT_OP_UUID, TENANT, "SUPPORT_LOCK");
        seedOperator(SUPER_ADMIN_UUID, "*", "SUPER_ADMIN");
        seedOperator(NO_ROLE_UUID, TENANT, null);
    }

    private void stubDownstreams() {
        wireMock.stubFor(WireMock.get(urlPathEqualTo(SECURITY_PATH))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"tenantId\":\"x\",\"days\":[]}")));
        wireMock.stubFor(WireMock.get(urlPathEqualTo(ACCOUNT_PATH))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"tenantId\":\"x\",\"days\":[]}")));
    }

    private org.springframework.test.web.servlet.ResultActions metrics(String operatorUuid, String tenantId)
            throws Exception {
        var req = get("/api/admin/metrics").param("period", "week")
                .header("Authorization", "Bearer " + jwt.operatorToken(operatorUuid));
        if (tenantId != null) {
            req = req.param("tenantId", tenantId);
        }
        return mockMvc.perform(req);
    }

    @Test
    @DisplayName("company operator, tenantId omitted → its one audit.read tenant is what both downstreams get (cur + prev)")
    void omittedTenantResolvesToTheGrantTenant() throws Exception {
        metrics(TENANT_OP_UUID, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metrics.length()").value(4))
                .andExpect(jsonPath("$.metrics[0].key").value("loginSuccess"))
                .andExpect(jsonPath("$.metrics[3].key").value("newAccount"));

        wireMock.verify(2, getRequestedFor(urlPathEqualTo(SECURITY_PATH)).withQueryParam("tenantId", equalTo(TENANT)));
        wireMock.verify(2, getRequestedFor(urlPathEqualTo(ACCOUNT_PATH)).withQueryParam("tenantId", equalTo(TENANT)));
    }

    @Test
    @DisplayName("🔴 company operator asking for another tenant → 403 TENANT_SCOPE_DENIED, nothing sent downstream, DENIED METRICS_QUERY row")
    void otherTenantDenied() throws Exception {
        Integer before = deniedRows(TENANT_OP_UUID);

        metrics(TENANT_OP_UUID, "someone-else")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_SCOPE_DENIED"));

        wireMock.verify(0, getRequestedFor(urlPathEqualTo(SECURITY_PATH)));
        wireMock.verify(0, getRequestedFor(urlPathEqualTo(ACCOUNT_PATH)));
        assertThat(deniedRows(TENANT_OP_UUID)).isEqualTo(before + 1);
    }

    @Test
    @DisplayName("🔴 company operator asking for '*' → 403 TENANT_SCOPE_DENIED")
    void starDeniedForCompanyOperator() throws Exception {
        metrics(TENANT_OP_UUID, "*")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_SCOPE_DENIED"));
        wireMock.verify(0, getRequestedFor(urlPathEqualTo(SECURITY_PATH)));
    }

    @Test
    @DisplayName("SUPER_ADMIN with tenantId=* → 200, '*' forwarded to both downstreams")
    void superAdminAllTenants() throws Exception {
        metrics(SUPER_ADMIN_UUID, "*").andExpect(status().isOk());

        wireMock.verify(2, getRequestedFor(urlPathEqualTo(SECURITY_PATH)).withQueryParam("tenantId", equalTo("*")));
        wireMock.verify(2, getRequestedFor(urlPathEqualTo(ACCOUNT_PATH)).withQueryParam("tenantId", equalTo("*")));
    }

    @Test
    @DisplayName("🔴 operator without audit.read → 403 PERMISSION_DENIED, nothing sent downstream")
    void withoutAuditRead() throws Exception {
        metrics(NO_ROLE_UUID, TENANT)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        wireMock.verify(0, getRequestedFor(urlPathEqualTo(SECURITY_PATH)));
    }

    @Test
    @DisplayName("🔴 account-service 5xx while security-service answers → 503, never a partial body")
    void oneDownstreamDownFailsTheCall() throws Exception {
        wireMock.stubFor(WireMock.get(urlPathEqualTo(ACCOUNT_PATH)).willReturn(aResponse().withStatus(500)));

        metrics(TENANT_OP_UUID, TENANT)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.metrics").doesNotExist());
    }

    private Integer deniedRows(String operatorUuid) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM admin_actions WHERE outcome = 'DENIED' AND actor_id = ? "
                        + "AND action_code = 'METRICS_QUERY'",
                Integer.class, operatorUuid);
    }

    private void seedOperator(String uuid, String tenantId, String roleName) {
        Integer existing = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM admin_operators WHERE operator_id = ?", Integer.class, uuid);
        if (existing == null || existing == 0) {
            jdbcTemplate.update("""
                    INSERT INTO admin_operators
                      (operator_id, tenant_id, email, password_hash, display_name,
                       status, created_at, updated_at, version)
                    VALUES (?, ?, ?, NULL, ?, 'ACTIVE', NOW(6), NOW(6), 0)
                    """, uuid, tenantId, uuid + "@example.com", "M811 " + roleName);
        }
        jdbcTemplate.update("""
                DELETE ar FROM admin_operator_roles ar
                  JOIN admin_operators o ON o.id = ar.operator_id
                 WHERE o.operator_id = ?
                """, uuid);
        if (roleName != null) {
            jdbcTemplate.update("""
                    INSERT IGNORE INTO admin_operator_roles (operator_id, role_id, tenant_id, granted_at, granted_by)
                    SELECT o.id, r.id, o.tenant_id, NOW(6), NULL
                      FROM admin_operators o CROSS JOIN admin_roles r
                     WHERE o.operator_id = ? AND r.name = ?
                    """, uuid, roleName);
        }
        if (!"*".equals(tenantId)) {
            MembershipFixture.addHomeMembership(jdbcTemplate, uuid);
        }
    }
}
