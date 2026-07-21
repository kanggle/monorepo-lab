package com.example.finance.gateway.integration;

import okhttp3.mockwebserver.MockResponse;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises finance's {@code RequestRateLimiter} through the real chain: the account-service
 * route is wired (in {@link GatewayIntegrationBase}) with {@code burstCapacity=5},
 * {@code replenishRate=1/s} and {@code key-resolver=#{@accountKeyResolver}}, so a burst from one
 * authenticated principal must eventually trip {@code 429 TOO_MANY_REQUESTS} once the bucket is
 * drained. The limiter is real Redis-backed (Testcontainer), not a mock.
 *
 * <p>AC-3 mutation (CI-authoritative): dropping the {@code RequestRateLimiter} filter from the
 * route (or the {@code accountKeyResolver} bean) makes this test go RED — every request would 200.
 */
@Tag("integration")
class GatewayRateLimitIntegrationTest extends GatewayIntegrationBase {

    @Test
    void exhaustingBurstCapacityYields429() {
        // Plenty of downstream 200s so only the limiter, not a starved stub, can produce non-200.
        for (int i = 0; i < 60; i++) {
            downstream.enqueue(new MockResponse().setResponseCode(200).setBody("{}"));
        }

        String token = jwt.signFinanceToken("hot-operator");

        boolean saw429 = false;
        for (int i = 0; i < 30; i++) {
            WebTestClient.ResponseSpec spec = webTestClient.get()
                    .uri("/api/finance/accounts/1")
                    .header("Authorization", "Bearer " + token)
                    .exchange();
            int status = spec.returnResult(byte[].class).getStatus().value();
            if (status == 429) {
                saw429 = true;
                break;
            }
        }

        assertThat(saw429)
                .as("Expected a 429 after draining the burst capacity of 5")
                .isTrue();
    }
}
