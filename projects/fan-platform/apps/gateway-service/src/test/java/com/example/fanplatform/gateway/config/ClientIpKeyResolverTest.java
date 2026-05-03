package com.example.fanplatform.gateway.config;

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
 * Verifies the {@code rate:fan-platform:<routeId>:<ip>} key format mandated by
 * TASK-FAN-BE-001 § Failure Scenarios (project-prefixed keys avoid cross-project
 * collisions in shared Redis).
 */
class ClientIpKeyResolverTest {

    private final KeyResolver resolver = new RateLimitConfig().clientIpKeyResolver();

    @Test
    void producesProjectPrefixedKeysScopedByRoute() {
        String ip = "203.0.113.42";

        MockServerWebExchange communityExchange = exchangeFor(ip, routeWithId("community-service"));
        MockServerWebExchange artistExchange = exchangeFor(ip, routeWithId("artist-service"));

        String communityKey = resolver.resolve(communityExchange).block();
        String artistKey = resolver.resolve(artistExchange).block();

        assertThat(communityKey).isEqualTo("rate:fan-platform:community-service:203.0.113.42");
        assertThat(artistKey).isEqualTo("rate:fan-platform:artist-service:203.0.113.42");
        assertThat(communityKey).isNotEqualTo(artistKey);
    }

    @Test
    void prefersXForwardedForOverRemoteAddress() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/community/posts")
                .header("X-Forwarded-For", "198.51.100.7, 10.0.0.1")
                .remoteAddress(new InetSocketAddress("10.0.0.1", 12345))
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes()
                .put(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR, routeWithId("community-service"));

        String key = resolver.resolve(exchange).block();

        assertThat(key).isEqualTo("rate:fan-platform:community-service:198.51.100.7");
    }

    @Test
    void fallsBackToUnknownRouteWhenAttributeMissing() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/community/posts")
                .remoteAddress(new InetSocketAddress("192.0.2.5", 9999))
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        // Deliberately DO NOT set GATEWAY_ROUTE_ATTR — resolver must not NPE.

        String key = resolver.resolve(exchange).block();

        assertThat(key).isEqualTo("rate:fan-platform:unknown:192.0.2.5");
    }

    @Test
    void fallsBackToUnknownIpWhenAllSourcesAreMissing() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/community/posts").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes()
                .put(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR, routeWithId("community-service"));

        String key = resolver.resolve(exchange).block();

        assertThat(key).isEqualTo("rate:fan-platform:community-service:unknown");
    }

    private static MockServerWebExchange exchangeFor(String ip, Route route) {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/community/posts")
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
