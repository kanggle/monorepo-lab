package com.example.scmplatform.gateway.integration;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Verifies the prometheus scrape endpoint isolation contract.
 *
 * <p>Design: {@code /actuator/prometheus} is NOT added to the gateway's PUBLIC_PATHS
 * in {@link com.example.apigateway.config.SecurityConfig}. The gateway
 * does not proxy this path to any downstream service. An unauthenticated external
 * caller therefore receives 401 UNAUTHORIZED — the gateway's own Spring Security
 * filter chain rejects it before reaching the actuator endpoint.
 *
 * <p>Prometheus scrapers reach per-service {@code /actuator/prometheus} directly on
 * the internal docker network ({@code scm-platform-net}) without going through the
 * gateway. Mirrors the same isolation contract used by fan-platform's gateway
 * (TASK-FAN-BE-004 option c — network isolation).
 */
@Tag("integration")
class GatewayPrometheusIsolationTest extends GatewayIntegrationBase {

    /**
     * An anonymous external request to the gateway's own {@code /actuator/prometheus}
     * must be rejected with 401 (Spring Security: path not in PUBLIC_PATHS).
     *
     * <p>This confirms that (a) the gateway does not allow unauthenticated scraping
     * through the public edge, and (b) the path is not forwarded to any downstream
     * service (option c: network isolation — Prometheus scrapes internally).
     */
    @Test
    void anonymousRequestToPrometheusEndpointIsRejectedByGateway() {
        webTestClient.get()
                .uri("/actuator/prometheus")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo("UNAUTHORIZED");
    }

    /**
     * Actuator health and info remain publicly accessible — the isolation applies
     * only to the prometheus metrics path. This guard test ensures the fix does not
     * accidentally break the health probe contract.
     */
    @Test
    void healthEndpointRemainsPubliclyAccessibleAfterPrometheusIsolation() {
        webTestClient.get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus().isOk();
    }
}
