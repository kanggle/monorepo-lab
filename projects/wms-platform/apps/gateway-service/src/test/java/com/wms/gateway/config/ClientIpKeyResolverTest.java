package com.wms.gateway.config;

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
 * Covers the compound {@code (clientIp, routeId)} key format mandated by
 * {@code platform/api-gateway-policy.md}.
 */
class ClientIpKeyResolverTest {

    private final KeyResolver resolver = new RateLimitConfig().clientIpKeyResolver();

    @Test
    void producesDistinctKeysForSameIpButDifferentRoutes() {
        String ip = "203.0.113.42";

        MockServerWebExchange masterExchange = exchangeFor(ip, routeWithId("master-service"));
        MockServerWebExchange inventoryExchange = exchangeFor(ip, routeWithId("inventory-service"));

        String masterKey = resolver.resolve(masterExchange).block();
        String inventoryKey = resolver.resolve(inventoryExchange).block();

        assertThat(masterKey).isEqualTo("203.0.113.42:master-service");
        assertThat(inventoryKey).isEqualTo("203.0.113.42:inventory-service");
        assertThat(masterKey).isNotEqualTo(inventoryKey);
    }

    @Test
    void prefersXForwardedForOverRemoteAddress() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/master/warehouses")
                .header("X-Forwarded-For", "198.51.100.7, 10.0.0.1")
                .remoteAddress(new InetSocketAddress("10.0.0.1", 12345))
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR, routeWithId("master-service"));

        String key = resolver.resolve(exchange).block();

        assertThat(key).isEqualTo("198.51.100.7:master-service");
    }

    @Test
    void fallsBackToUnknownRouteWhenAttributeMissing() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/master/warehouses")
                .remoteAddress(new InetSocketAddress("192.0.2.5", 9999))
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        // Deliberately DO NOT set GATEWAY_ROUTE_ATTR — resolver must not NPE.

        String key = resolver.resolve(exchange).block();

        assertThat(key).isEqualTo("192.0.2.5:unknown");
    }

    @Test
    void fallsBackToUnknownIpWhenAllSourcesAreMissing() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/master/warehouses").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR, routeWithId("master-service"));

        String key = resolver.resolve(exchange).block();

        assertThat(key).isEqualTo("unknown:master-service");
    }

    private static MockServerWebExchange exchangeFor(String ip, Route route) {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/master/warehouses")
                .remoteAddress(new InetSocketAddress(ip, 54321))
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR, route);
        return exchange;
    }

    private static Route routeWithId(String id) {
        // Real Route construction requires a URI + predicate. A Mockito mock is
        // lighter and the resolver only calls getId(), so no Route internals leak.
        Route route = mock(Route.class);
        when(route.getId()).thenReturn(id);
        // Defensive: provide a URI for any accidental debug toString().
        when(route.getUri()).thenReturn(URI.create("http://localhost"));
        return route;
    }
}
