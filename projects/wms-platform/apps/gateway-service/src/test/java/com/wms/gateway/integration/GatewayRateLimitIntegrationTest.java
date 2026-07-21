package com.wms.gateway.integration;

import static org.assertj.core.api.Assertions.assertThat;

import okhttp3.mockwebserver.MockResponse;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * The {@code RequestRateLimiter} route filter + wms's {@code accountKeyResolver} (keyed on the
 * JWT subject) + {@code FailOpenRateLimiter} + the Redis Testcontainer, end-to-end. The base
 * wires the master route with a low burst (1/s replenish, 5 burst); hammering a single account
 * subject past that burst must yield {@code 429 TOO_MANY_REQUESTS}.
 *
 * <p>Keying on the account (not client IP) is a wms-specific decision (TASK-MONO-370): every
 * request in this test shares one subject, so it shares one bucket regardless of source port.
 */
@Tag("integration")
class GatewayRateLimitIntegrationTest extends GatewayIntegrationBase {

    @Test
    void exhaustingThePerAccountBurstYields429() {
        // Plenty of downstream 200s so admitted requests succeed; the limiter is what we exercise.
        for (int i = 0; i < 60; i++) {
            downstream.enqueue(new MockResponse().setResponseCode(200).setBody("{}"));
        }

        String token = jwt.signToken("hot-operator", "MASTER_WRITE", 300);

        boolean saw429 = false;
        for (int i = 0; i < 30; i++) {
            WebTestClient.ResponseSpec spec = webTestClient.get()
                    .uri("/api/v1/master/warehouses")
                    .header("Authorization", "Bearer " + token)
                    .exchange();
            int status = spec.returnResult(byte[].class).getStatus().value();
            if (status == 429) {
                saw429 = true;
                break;
            }
        }

        assertThat(saw429)
                .as("expected at least one 429 after exhausting the per-account burst capacity")
                .isTrue();
    }
}
