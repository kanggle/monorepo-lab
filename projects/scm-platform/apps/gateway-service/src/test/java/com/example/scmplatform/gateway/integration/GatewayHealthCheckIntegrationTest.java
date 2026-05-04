package com.example.scmplatform.gateway.integration;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("integration")
class GatewayHealthCheckIntegrationTest extends GatewayIntegrationBase {

    @Test
    void healthEndpointIsPublicAndReturns200() {
        webTestClient.get().uri("/actuator/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP");
    }

    @Test
    void unauthenticatedRequestToProtectedRouteReturns401() {
        webTestClient.get().uri("/api/v1/procurement/po")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo("UNAUTHORIZED");
    }
}
