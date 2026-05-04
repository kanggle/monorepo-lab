package com.example.scmplatform.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.InetSocketAddress;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

/**
 * Unit tests for the {@code clientIpKeyResolver} bean defined in {@link RateLimitConfig}.
 * Verifies the {@code rate:scm-platform:<routeId>:<ip>} key format mandated by
 * TASK-SCM-BE-001 § Failure Scenarios (project-prefixed keys avoid cross-project
 * collisions in shared Redis).
 */
class ClientIpKeyResolverTest {

    private final KeyResolver resolver = new RateLimitConfig().clientIpKeyResolver();

    @Test
    void producesProjectPrefixedKeysScopedByRoute() {
        String ip = "203.0.113.42";

        MockServerWebExchange procurementExchange = exchangeFor(ip, routeWithId("procurement-service"));
        MockServerWebExchange inventoryExchange = exchangeFor(ip, routeWithId("inventory-visibility-service"));

        String procurementKey = resolver.resolve(procurementExchange).block();
        String inventoryKey = resolver.resolve(inventoryExchange).block();

        assertThat(procurementKey).isEqualTo("rate:scm-platform:procurement-service:203.0.113.42");
        assertThat(inventoryKey).isEqualTo("rate:scm-platform:inventory-visibility-service:203.0.113.42");
        assertThat(procurementKey).isNotEqualTo(inventoryKey);
    }

    @Test
    void prefersXForwardedForOverRemoteAddress() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/procurement/po")
                .header("X-Forwarded-For", "198.51.100.7, 10.0.0.1")
                .remoteAddress(new InetSocketAddress("10.0.0.1", 12345))
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes()
                .put(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR, routeWithId("procurement-service"));

        String key = resolver.resolve(exchange).block();

        assertThat(key).isEqualTo("rate:scm-platform:procurement-service:198.51.100.7");
    }

    @Test
    void fallsBackToUnknownRouteWhenAttributeMissing() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/procurement/po")
                .remoteAddress(new InetSocketAddress("192.0.2.5", 9999))
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        // Deliberately DO NOT set GATEWAY_ROUTE_ATTR — resolver must not NPE.

        String key = resolver.resolve(exchange).block();

        assertThat(key).isEqualTo("rate:scm-platform:unknown:192.0.2.5");
    }

    @Test
    void fallsBackToUnknownIpWhenAllSourcesAreMissing() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/procurement/po").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes()
                .put(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR, routeWithId("procurement-service"));

        String key = resolver.resolve(exchange).block();

        assertThat(key).isEqualTo("rate:scm-platform:procurement-service:unknown");
    }

    private static MockServerWebExchange exchangeFor(String ip, Route route) {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/procurement/po")
                .remoteAddress(new InetSocketAddress(ip, 54321))
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR, route);
        return exchange;
    }

    private static Route routeWithId(String id) {
        Route route = mock(Route.class);
        when(route.getId()).thenReturn(id);
        when(route.getUri()).thenReturn(URI.create("http://localhost"));
        return route;
    }
}
