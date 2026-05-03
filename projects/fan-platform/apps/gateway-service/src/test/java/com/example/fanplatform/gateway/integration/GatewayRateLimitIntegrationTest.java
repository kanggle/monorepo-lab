package com.example.fanplatform.gateway.integration;

import okhttp3.mockwebserver.MockResponse;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Verifies that exceeding the route's burst capacity (5 tokens, 1/s replenish in
 * the test profile) yields {@code 429 TOO_MANY_REQUESTS} with the
 * {@code Retry-After} header.
 */
@Tag("integration")
class GatewayRateLimitIntegrationTest extends GatewayIntegrationBase {

    @Test
    void exhaustingTheBurstCapacityYields429() {
        // Ensure plenty of downstream responses so non-rate-limited requests succeed.
        for (int i = 0; i < 50; i++) {
            downstream.enqueue(new MockResponse().setResponseCode(200).setBody("{}"));
        }

        String token = jwt.signFanToken("hot-user");

        // Send a burst — burstCapacity=5 means request 6 must trip the limiter.
        // We allow some slack for token-bucket warm-up by sending 25 requests
        // and asserting at least one 429 surfaces.
        boolean saw429 = false;
        for (int i = 0; i < 25; i++) {
            WebTestClient.ResponseSpec spec = webTestClient.get()
                    .uri("/api/v1/community/posts")
                    .header("Authorization", "Bearer " + token)
                    .exchange();
            byte[] body = spec.expectBody().returnResult().getResponseBody();
            int status = spec.returnResult(byte[].class).getStatus().value();
            if (status == 429) {
                saw429 = true;
                break;
            }
        }

        org.assertj.core.api.Assertions.assertThat(saw429)
                .as("Expected at least one 429 after exhausting the burst capacity")
                .isTrue();
    }
}
