package com.example.erp.gateway.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Exercises erp's real {@code RequestRateLimiter} leg on the {@code masterdata-service} route
 * through the live filter chain (TASK-MONO-458). The base class wires {@code burstCapacity=5},
 * {@code replenishRate=1}, keyed by {@code #{@accountKeyResolver}} (the erp-specific
 * account-scoped resolver in {@code RateLimitConfig}) against a Redis Testcontainer — so a burst
 * from one authenticated account must eventually trip {@code 429 TOO_MANY_REQUESTS}.
 *
 * <p>This asserts the rate-limit filter is genuinely on the route, not merely bean-registered:
 * a unit test cannot observe the RedisRateLimiter decrementing a real token bucket.
 */
@Tag("integration")
@DisplayName("erp gateway 통합 — 버스트 소진 시 429")
class GatewayRateLimitIntegrationTest extends GatewayIntegrationBase {

    @Test
    void exhaustingTheBurstCapacityYields429() {
        // The base's downstream dispatcher answers every non-limited request with 200, so any
        // status other than 200 in the burst is the limiter's doing, not a starved response queue.
        String token = jwt.signErpOperatorToken("hot-account");

        // burstCapacity=5 → a sustained burst must trip the limiter. Slack (30 requests) absorbs
        // token-bucket warm-up; we assert at least one 429 surfaces.
        boolean saw429 = false;
        for (int i = 0; i < 30; i++) {
            WebTestClient.ResponseSpec spec = webTestClient.get()
                    .uri("/api/erp/masterdata/employees/1")
                    .header("Authorization", "Bearer " + token)
                    .exchange();
            int status = spec.returnResult(byte[].class).getStatus().value();
            if (status == 429) {
                saw429 = true;
                break;
            }
        }

        assertThat(saw429)
                .as("Expected at least one 429 after exhausting the burst capacity")
                .isTrue();
    }
}
