package com.example.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

/**
 * Unit tests for the {@code tenantRouteKeyResolver} bean (TASK-BE-405 — M7 realization).
 *
 * <p>Verifies the {@code (tenant_id, route_id)} key tuple (AC-1): an authenticated request
 * keys on its JWT {@code tenant_id} claim ({@code rate:ecommerce-gw:<route>:t:<tenant>}),
 * an anonymous request falls back to the default tenant {@code 'ecommerce'} qualified by
 * client IP (AC-3 / D8 net-zero, and pre-auth IP bounding is preserved). The key is never
 * null (default-tenant guard, § Failure Scenarios).
 */
@DisplayName("tenantRouteKeyResolver 단위 테스트")
class TenantRouteRateLimitConfigTest {

    private final KeyResolver resolver = new TenantRouteRateLimitConfig().tenantRouteKeyResolver();

    @Test
    @DisplayName("인증 요청 → rate:ecommerce-gw:<route>:t:<tenant> (claim tenant)")
    void authenticatedRequest_keysByTenantAndRoute() {
        MockServerWebExchange exchange = exchangeFor("10.0.0.5", routeWithId("product-service"));

        String key = resolver.resolve(exchange)
                .contextWrite(authContext(jwtWithTenant("acme")))
                .block();

        assertThat(key).isEqualTo("rate:ecommerce-gw:product-service:t:acme");
    }

    @Test
    @DisplayName("같은 IP라도 tenant 가 다르면 키가 분리된다 (cross-tenant 격리)")
    void differentTenantsSameIp_produceIndependentKeys() {
        MockServerWebExchange exchangeA = exchangeFor("10.0.0.9", routeWithId("order-service"));
        MockServerWebExchange exchangeB = exchangeFor("10.0.0.9", routeWithId("order-service"));

        String keyA = resolver.resolve(exchangeA).contextWrite(authContext(jwtWithTenant("tenant-a"))).block();
        String keyB = resolver.resolve(exchangeB).contextWrite(authContext(jwtWithTenant("tenant-b"))).block();

        assertThat(keyA).isEqualTo("rate:ecommerce-gw:order-service:t:tenant-a");
        assertThat(keyB).isEqualTo("rate:ecommerce-gw:order-service:t:tenant-b");
        assertThat(keyA).isNotEqualTo(keyB);
    }

    @Test
    @DisplayName("같은 tenant, 다른 route → 키 분리 (route_id tuple)")
    void sameTenantDifferentRoutes_produceIndependentKeys() {
        String keyProducts = resolver.resolve(exchangeFor("10.0.0.1", routeWithId("product-service")))
                .contextWrite(authContext(jwtWithTenant("acme"))).block();
        String keyOrders = resolver.resolve(exchangeFor("10.0.0.1", routeWithId("order-service")))
                .contextWrite(authContext(jwtWithTenant("acme"))).block();

        assertThat(keyProducts).isEqualTo("rate:ecommerce-gw:product-service:t:acme");
        assertThat(keyOrders).isEqualTo("rate:ecommerce-gw:order-service:t:acme");
        assertThat(keyProducts).isNotEqualTo(keyOrders);
    }

    @Test
    @DisplayName("tenant_id claim 이 비어있는 인증 토큰 → 기본 테넌트 'ecommerce' (null 금지)")
    void blankTenantClaim_fallsBackToDefaultTenant() {
        MockServerWebExchange exchange = exchangeFor("10.0.0.5", routeWithId("product-service"));

        String key = resolver.resolve(exchange)
                .contextWrite(authContext(jwtWithTenant("")))
                .block();

        assertThat(key).isEqualTo("rate:ecommerce-gw:product-service:t:ecommerce");
    }

    @Test
    @DisplayName("익명/pre-auth 요청 → 기본 테넌트 + client IP 로 키 (IP 바운딩 보존)")
    void anonymousRequest_fallsBackToDefaultTenantQualifiedByIp() {
        MockServerWebExchange exchange = exchangeFor("203.0.113.7", routeWithId("search-service"));

        String key = resolver.resolve(exchange).block();

        assertThat(key).isEqualTo("rate:ecommerce-gw:search-service:t:ecommerce:ip:203.0.113.7");
    }

    @Test
    @DisplayName("익명 요청은 X-Forwarded-For 의 첫 IP 를 우선한다")
    void anonymousRequest_prefersXForwardedFor() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/search/q")
                .header("X-Forwarded-For", "198.51.100.7, 10.0.0.1")
                .remoteAddress(new InetSocketAddress("10.0.0.1", 12345))
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes()
                .put(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR, routeWithId("search-service"));

        String key = resolver.resolve(exchange).block();

        assertThat(key).isEqualTo("rate:ecommerce-gw:search-service:t:ecommerce:ip:198.51.100.7");
    }

    @Test
    @DisplayName("GATEWAY_ROUTE_ATTR 부재 → routeId 'unknown' (NPE 금지)")
    void missingRouteAttr_fallsBackToUnknownRoute() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/search/q")
                .remoteAddress(new InetSocketAddress("192.0.2.5", 9999))
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        // Deliberately DO NOT set GATEWAY_ROUTE_ATTR.

        String key = resolver.resolve(exchange).block();

        assertThat(key).isEqualTo("rate:ecommerce-gw:unknown:t:ecommerce:ip:192.0.2.5");
    }

    @Test
    @DisplayName("JWT 가 아닌 인증 토큰 → 익명 경로(기본 테넌트 + IP)로 강등")
    void nonJwtAuthentication_treatedAsAnonymous() {
        MockServerWebExchange exchange = exchangeFor("203.0.113.50", routeWithId("order-service"));
        Authentication nonJwt = new TestingAuthenticationToken("principal", "creds");

        String key = resolver.resolve(exchange)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(
                        Mono.just(new SecurityContextImpl(nonJwt))))
                .block();

        assertThat(key).isEqualTo("rate:ecommerce-gw:order-service:t:ecommerce:ip:203.0.113.50");
    }

    // --- helpers -----------------------------------------------------------

    private static Context authContext(Jwt jwt) {
        JwtAuthenticationToken token = new JwtAuthenticationToken(jwt);
        return ReactiveSecurityContextHolder.withSecurityContext(
                Mono.just(new SecurityContextImpl(token)));
    }

    private static Jwt jwtWithTenant(String tenantId) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("user-123")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300));
        builder.claim("tenant_id", tenantId);
        return builder.build();
    }

    private static MockServerWebExchange exchangeFor(String ip, Route route) {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/products")
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
