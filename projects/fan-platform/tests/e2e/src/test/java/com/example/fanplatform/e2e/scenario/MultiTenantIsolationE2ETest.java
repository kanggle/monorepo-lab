package com.example.fanplatform.e2e.scenario;

import static com.example.fanplatform.e2e.testsupport.E2ETestFixtures.authedGet;
import static com.example.fanplatform.e2e.testsupport.E2ETestFixtures.pathCommunityFeed;
import static com.example.fanplatform.e2e.testsupport.E2ETestFixtures.randomAccountId;
import static com.example.fanplatform.e2e.testsupport.E2ETestFixtures.sendString;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.fanplatform.e2e.testsupport.FanPlatformE2ETestBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Scenario 2 — multi-tenant isolation enforced at the gateway
 * (TASK-FAN-INT-001 § In Scope #3).
 *
 * <p>Three sub-cases:
 *
 * <ul>
 *   <li>{@code tenant_id=wms} token -&gt; gateway returns 403
 *       {@code TENANT_FORBIDDEN}. Per the gateway tenant gate, the request
 *       MUST NOT reach community-service.</li>
 *   <li>Unauthenticated request -&gt; 401 {@code UNAUTHORIZED}.</li>
 *   <li>{@code tenant_id=fan-platform} fan token -&gt; gateway forwards to
 *       community-service which returns 200 (empty feed is fine — the
 *       success status alone validates the gate is open for the right
 *       tenant).</li>
 * </ul>
 *
 * <p>The "wms-tenant request never reaches community-service" assertion
 * relies on the canonical {@code TENANT_FORBIDDEN} error code in the
 * gateway's response envelope (per
 * {@code projects/fan-platform/apps/gateway-service/src/main/java/...
 * /security/TenantClaimValidator.java}). community-service emits
 * {@code TENANT_FORBIDDEN} as well when its own validator rejects a
 * cross-tenant token, so the response envelope alone is not a strict
 * "never forwarded" proof, but combined with the gateway integration test
 * coverage in {@code GatewayBootstrapIntegrationTest} this scenario
 * verifies the deployed gateway image enforces the gate.
 */
class MultiTenantIsolationE2ETest extends FanPlatformE2ETestBase {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("cross-tenant token (tenant_id=wms) -> 403 TENANT_FORBIDDEN")
    void crossTenantTokenIsBlocked() throws Exception {
        String token = jwt.signCrossTenantToken("wms-user-" + randomAccountId());

        HttpResponse<String> resp = sendString(http, authedGet(
                gatewayBaseUri().resolve(pathCommunityFeed()), token)
                .GET().build());

        assertThat(resp.statusCode())
                .as("gateway tenant gate blocks tenant_id=wms")
                .isEqualTo(403);
        JsonNode envelope = objectMapper.readTree(resp.body());
        assertThat(envelope.get("code").asText())
                .as("error code is TENANT_FORBIDDEN per platform/error-handling.md")
                .isEqualTo("TENANT_FORBIDDEN");
        assertThat(envelope.get("timestamp")).isNotNull();
    }

    @Test
    @DisplayName("unauthenticated request -> 401 UNAUTHORIZED")
    void unauthenticatedRequestIsRejected() throws Exception {
        HttpResponse<String> resp = http.send(
                HttpRequest.newBuilder(gatewayBaseUri().resolve(pathCommunityFeed()))
                        .timeout(Duration.ofSeconds(15))
                        .header("Accept", "application/json")
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(resp.statusCode()).isEqualTo(401);
        JsonNode envelope = objectMapper.readTree(resp.body());
        assertThat(envelope.get("code").asText()).isEqualTo("UNAUTHORIZED");
    }

    @Test
    @DisplayName("fan-platform tenant token -> gateway forwards to community-service (200)")
    void fanPlatformTenantTokenIsAllowed() throws Exception {
        String token = jwt.signFanToken("fan-allowed-" + randomAccountId());

        HttpResponse<String> resp = sendString(http, authedGet(
                gatewayBaseUri().resolve(pathCommunityFeed() + "?page=0&size=20"), token)
                .GET().build());

        // 200 with empty content array — empty feed is a valid response for a
        // brand-new fan with no follows. The status alone validates the
        // gateway forwards a fan-platform tenant token end-to-end.
        assertThat(resp.statusCode())
                .as("fan-platform tenant token must traverse the gateway end-to-end")
                .isEqualTo(200);
        JsonNode envelope = objectMapper.readTree(resp.body());
        assertThat(envelope.get("data")).isNotNull();
        assertThat(envelope.get("data").get("content").isArray()).isTrue();
    }
}
