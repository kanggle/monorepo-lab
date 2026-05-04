package com.example.scmplatform.gateway.integration;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the gateway's {@code RewritePath} filters correctly strip the
 * {@code /api/v1/} external namespace before forwarding to downstream services.
 *
 * <p>2 test cases — one per scm v1 placeholder route:
 * <ol>
 *   <li>{@code /api/v1/procurement/po} → procurement-service at {@code /api/procurement/po}</li>
 *   <li>{@code /api/v1/inventory-visibility/snapshot} → inventory-visibility-service at
 *       {@code /api/inventory-visibility/snapshot}</li>
 * </ol>
 *
 * <p>Uses the shared {@link GatewayIntegrationBase} infrastructure (Redis + JWKS
 * MockWebServer) and re-uses the same {@code downstream} MockWebServer as the
 * target for both routes. The base class wires routes[0] for procurement-service;
 * this class adds routes[1] for inventory-visibility-service via
 * {@link DynamicPropertySource}.
 */
@Tag("integration")
class GatewayRouteRewriteTest extends GatewayIntegrationBase {

    /**
     * Wires the inventory-visibility-service route to the shared {@code downstream}
     * MockWebServer at index 1. The base class registers procurement at index 0.
     */
    @DynamicPropertySource
    static void wireInventoryRoute(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.gateway.routes[1].id", () -> "inventory-visibility-service");
        registry.add("spring.cloud.gateway.routes[1].uri",
                () -> "http://" + downstream.getHostName() + ":" + downstream.getPort());
        registry.add("spring.cloud.gateway.routes[1].predicates[0]",
                () -> "Path=/api/v1/inventory-visibility/**");
        registry.add("spring.cloud.gateway.routes[1].filters[0]",
                () -> "RewritePath=/api/v1/inventory-visibility/(?<segment>.*), /api/inventory-visibility/${segment}");
        registry.add("spring.cloud.gateway.routes[1].filters[1].name",
                () -> "RequestRateLimiter");
        registry.add("spring.cloud.gateway.routes[1].filters[1].args.redis-rate-limiter.replenishRate",
                () -> "1");
        registry.add("spring.cloud.gateway.routes[1].filters[1].args.redis-rate-limiter.burstCapacity",
                () -> "120");
        registry.add("spring.cloud.gateway.routes[1].filters[1].args.redis-rate-limiter.requestedTokens",
                () -> "1");
        registry.add("spring.cloud.gateway.routes[1].filters[1].args.key-resolver",
                () -> "#{@accountKeyResolver}");
    }

    @Test
    void procurementRouteRewritesV1PrefixToInternalPath() throws Exception {
        downstream.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"po\":[]}"));

        String token = jwt.signScmToken("buyer-rewrite-1");

        webTestClient.get().uri("/api/v1/procurement/po")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isOk();

        RecordedRequest received = downstream.takeRequest(5, TimeUnit.SECONDS);
        assertThat(received).as("downstream did not receive the request").isNotNull();
        assertThat(received.getPath())
                .as("/api/v1/procurement/po must be rewritten to /api/procurement/po")
                .isEqualTo("/api/procurement/po");
    }

    @Test
    void procurementRoutePreservesPathVariablesAndSegments() throws Exception {
        downstream.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"items\":[]}"));

        String poId = "0190f3e2-1234-7abc-8def-000000000001";
        String token = jwt.signScmToken("buyer-rewrite-2");

        webTestClient.get()
                .uri("/api/v1/procurement/po/{poId}/items", poId)
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isOk();

        RecordedRequest received = downstream.takeRequest(5, TimeUnit.SECONDS);
        assertThat(received).isNotNull();
        assertThat(received.getPath())
                .as("nested path-variable segment must be preserved after rewrite")
                .isEqualTo("/api/procurement/po/" + poId + "/items");
    }

    @Test
    void inventoryVisibilityRouteRewritesV1Prefix() throws Exception {
        downstream.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"snapshot\":{}}"));

        String token = jwt.signScmToken("buyer-rewrite-3");

        webTestClient.get()
                .uri("/api/v1/inventory-visibility/snapshot")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isOk();

        RecordedRequest received = downstream.takeRequest(5, TimeUnit.SECONDS);
        assertThat(received).isNotNull();
        assertThat(received.getPath())
                .as("/api/v1/inventory-visibility/snapshot must be rewritten to /api/inventory-visibility/snapshot")
                .isEqualTo("/api/inventory-visibility/snapshot");
    }
}
