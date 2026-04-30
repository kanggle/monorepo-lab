package com.example.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.net.HttpURLConnection;
import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * WMS tenant provisioning → JWT → gateway E2E (multi-tenant flow).
 *
 * <p>Covers:
 * <ol>
 *   <li>WMS tenant provisioning: POST /internal/tenants/wms/accounts creates user</li>
 *   <li>WMS user login via gateway (port 18080): POST /api/auth/login → JWT</li>
 *   <li>JWT tenant claim verification: assert tenant_id=wms, tenant_type=B2B_ENTERPRISE</li>
 *   <li>Gateway X-Tenant-Id propagation: protected endpoint returns X-Tenant-Id header</li>
 *   <li>Cross-tenant rejection: WMS JWT on fan-platform endpoint → 403 TENANT_SCOPE_DENIED</li>
 * </ol>
 *
 * <p>Prerequisites (TASK-BE-228~231 must be implemented):
 * <ul>
 *   <li>account-service: /internal/tenants/{tenantId}/accounts provisioning endpoint</li>
 *   <li>auth-service: JWT includes tenant_id and tenant_type claims</li>
 *   <li>gateway-service: reads JWT tenant_id, injects X-Tenant-Id header downstream</li>
 * </ul>
 *
 * <p>If Docker is not available this test is skipped (same as all other E2E tests).
 * If the multi-tenant features are absent, the test fails to surface implementation gaps.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TenantProvisioningE2ETest extends E2EBase {

    // Gateway port declared in docker-compose.e2e.yml
    static final int GATEWAY_PORT = 18080;
    static final String GATEWAY_BASE_URL = "http://" + ComposeFixture.HOST + ":" + GATEWAY_PORT;

    // WMS tenant test credentials
    private static final String WMS_TENANT_ID = "wms";
    private static final String WMS_USER_EMAIL = "wms-e2e-" + UUID.randomUUID().toString().substring(0, 8) + "@wms-test.example.com";
    private static final String WMS_USER_PASSWORD = "WmsPass123!";

    // Shared state across ordered test methods
    private static String wmsAccountId;
    private static String wmsAccessToken;

    @BeforeAll
    static void waitForGateway() {
        // Wait for gateway to be healthy before running any test in this class.
        // ComposeFixture waits for auth/account/security/admin but not gateway
        // (gateway was added after the original ComposeFixture was written).
        await()
            .atMost(Duration.ofMinutes(3))
            .pollInterval(Duration.ofSeconds(5))
            .alias("gateway health check")
            .until(() -> isHealthy(GATEWAY_BASE_URL + "/actuator/health"));
    }

    // -------------------------------------------------------------------------
    // Step 1 — WMS tenant provisioning
    // -------------------------------------------------------------------------

    @Test
    @Order(1)
    @DisplayName("WMS tenant provisioning: POST /internal/tenants/wms/accounts → 201 + accountId")
    void step1_wms_tenant_provisioning() throws Exception {
        // The internal provisioning endpoint must not be publicly accessible —
        // call account-service directly (bypassing the gateway) with the internal token.
        Response provision = RestAssured.given()
                .baseUri(ComposeFixture.ACCOUNT_BASE_URL)
                .contentType(ContentType.JSON)
                .header("X-Internal-Token", "e2e-internal-token")
                .body(Map.of(
                        "email", WMS_USER_EMAIL,
                        "password", WMS_USER_PASSWORD,
                        "displayName", "WMS E2E User"
                ))
                .post("/internal/tenants/" + WMS_TENANT_ID + "/accounts");

        assertThat(provision.statusCode())
                .as("Provisioning should return 201 Created. " +
                    "If 404: TASK-BE-228 (account-service tenant provisioning API) is not implemented.")
                .isEqualTo(201);

        JsonNode body = MAPPER.readTree(provision.asString());
        wmsAccountId = body.path("accountId").asText();
        assertThat(wmsAccountId)
                .as("Response must contain accountId")
                .isNotBlank();

        // Verify account exists in DB with correct tenant association
        String tenantId = readAccountTenantId(wmsAccountId);
        assertThat(tenantId)
                .as("Account must be associated with tenant 'wms' in account_db")
                .isEqualTo(WMS_TENANT_ID);
    }

    // -------------------------------------------------------------------------
    // Step 2 — WMS user login via gateway → JWT
    // -------------------------------------------------------------------------

    @Test
    @Order(2)
    @DisplayName("WMS login via gateway (18080): POST /api/auth/login → JWT with tenant claims")
    void step2_wms_login_via_gateway() throws Exception {
        assertThat(wmsAccountId)
                .as("step1 must run first (provisioning must succeed)")
                .isNotBlank();

        // Login through the gateway — path /api/auth/login is a public route
        Response login = RestAssured.given()
                .baseUri(GATEWAY_BASE_URL)
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "email", WMS_USER_EMAIL,
                        "password", WMS_USER_PASSWORD
                ))
                .post("/api/auth/login");

        assertThat(login.statusCode())
                .as("WMS user login should return 200 OK")
                .isEqualTo(200);

        JsonNode tokens = MAPPER.readTree(login.asString());
        wmsAccessToken = tokens.path("accessToken").asText();
        assertThat(wmsAccessToken)
                .as("Response must contain accessToken")
                .isNotBlank();
    }

    // -------------------------------------------------------------------------
    // Step 3 — JWT tenant claim verification
    // -------------------------------------------------------------------------

    @Test
    @Order(3)
    @DisplayName("JWT must contain tenant_id=wms and tenant_type=B2B_ENTERPRISE claims")
    void step3_jwt_tenant_claims() throws Exception {
        assertThat(wmsAccessToken)
                .as("step2 must run first (login must succeed)")
                .isNotBlank();

        // Parse JWT payload (base64url-decode the middle segment)
        String[] parts = wmsAccessToken.split("\\.");
        assertThat(parts.length)
                .as("JWT must have 3 parts")
                .isGreaterThanOrEqualTo(3);

        String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]));
        JsonNode payload = MAPPER.readTree(payloadJson);

        String tenantId = payload.path("tenant_id").asText(null);
        assertThat(tenantId)
                .as("JWT payload must contain tenant_id claim. " +
                    "If missing: TASK-BE-229 (auth-service JWT tenant claims) is not implemented.")
                .isEqualTo(WMS_TENANT_ID);

        String tenantType = payload.path("tenant_type").asText(null);
        assertThat(tenantType)
                .as("JWT payload must contain tenant_type claim with value B2B_ENTERPRISE. " +
                    "If missing: TASK-BE-229 (auth-service JWT tenant claims) is not implemented.")
                .isEqualTo("B2B_ENTERPRISE");
    }

    // -------------------------------------------------------------------------
    // Step 4 — Gateway X-Tenant-Id propagation
    // -------------------------------------------------------------------------

    @Test
    @Order(4)
    @DisplayName("Gateway injects X-Tenant-Id from JWT into downstream request; upstream sees it")
    void step4_gateway_tenant_header_propagation() throws Exception {
        assertThat(wmsAccessToken)
                .as("step2 must run first (login must succeed)")
                .isNotBlank();

        // Call a protected account endpoint through the gateway.
        // The gateway should read tenant_id from the JWT and inject X-Tenant-Id.
        // The account-service /api/accounts/me endpoint should return the header
        // (echo back X-Tenant-Id in the response header, or the endpoint exists and returns 200).
        Response me = RestAssured.given()
                .baseUri(GATEWAY_BASE_URL)
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + wmsAccessToken)
                .get("/api/accounts/me");

        assertThat(me.statusCode())
                .as("Protected endpoint via gateway should return 200. " +
                    "If 401: gateway JWT validation is failing. " +
                    "If 403: tenant scope enforcement is blocking incorrectly.")
                .isEqualTo(200);

        // The gateway must propagate X-Tenant-Id downstream and it should be
        // visible in the response (account-service echoes it or the header is forwarded back).
        // Implementation note: if account-service does not echo X-Tenant-Id back,
        // this assertion checks the response header forwarded by the gateway.
        String tenantHeader = me.header("X-Tenant-Id");
        assertThat(tenantHeader)
                .as("Response must include X-Tenant-Id header propagated by gateway. " +
                    "If absent: TASK-BE-230 (gateway X-Tenant-Id injection) is not implemented.")
                .isEqualTo(WMS_TENANT_ID);
    }

    // -------------------------------------------------------------------------
    // Step 5 — Cross-tenant rejection
    // -------------------------------------------------------------------------

    @Test
    @Order(5)
    @DisplayName("WMS JWT on fan-platform endpoint → 403 TENANT_SCOPE_DENIED")
    void step5_cross_tenant_rejection() throws Exception {
        assertThat(wmsAccessToken)
                .as("step2 must run first (login must succeed)")
                .isNotBlank();

        // The community-service endpoint (/api/community/**) is a fan-platform tenant scope.
        // A WMS JWT should be rejected with 403 TENANT_SCOPE_DENIED.
        // Note: community-service is not in the E2E compose; gateway will respond before forwarding.
        Response crossTenant = RestAssured.given()
                .baseUri(GATEWAY_BASE_URL)
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + wmsAccessToken)
                .get("/api/community/feed");

        assertThat(crossTenant.statusCode())
                .as("WMS tenant JWT should be rejected on fan-platform endpoint with 403. " +
                    "If 401: gateway is treating this as an auth failure, not a tenant scope failure. " +
                    "If 404/502: gateway is forwarding instead of rejecting. " +
                    "If 200: TASK-BE-231 (gateway tenant scope enforcement) is not implemented.")
                .isEqualTo(403);

        JsonNode errorBody = MAPPER.readTree(crossTenant.asString());
        assertThat(errorBody.path("code").asText())
                .as("Error code must be TENANT_SCOPE_DENIED. " +
                    "If missing: TASK-BE-231 (gateway tenant scope enforcement) is not implemented.")
                .isEqualTo("TENANT_SCOPE_DENIED");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static boolean isHealthy(String urlStr) {
        try {
            HttpURLConnection conn = (HttpURLConnection) URI.create(urlStr).toURL().openConnection();
            conn.setConnectTimeout(2_000);
            conn.setReadTimeout(2_000);
            conn.setRequestMethod("GET");
            int code = conn.getResponseCode();
            conn.disconnect();
            return code == 200;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Reads the tenant_id column for the given accountId from account_db.
     * Returns null if the column does not exist (multi-tenant schema not yet applied).
     */
    private static String readAccountTenantId(String accountId) throws Exception {
        try (Connection c = DriverManager.getConnection(
                "jdbc:mysql://127.0.0.1:13306/account_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
                "account_user", "account_pass")) {
            // The tenant_id column is added by TASK-BE-228 migration.
            // If it doesn't exist, the query fails and we propagate the error
            // so the test clearly shows the missing migration.
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT tenant_id FROM accounts WHERE id = ?")) {
                ps.setString(1, accountId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getString("tenant_id") : null;
                }
            }
        }
    }
}
