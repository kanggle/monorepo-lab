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
 * Unit tests for the {@code tenantRouteKeyResolver} bean (TASK-BE-405 M7 realization; key
 * corrected by TASK-MONO-368).
 *
 * <p><b>What the pre-MONO-368 suite could not see.</b> It asserted that two different
 * {@code tenant_id} claims produce two different keys — and they do. But a shopper's
 * {@code tenant_id} is a constant ({@code ecommerce}, derived from the OAuth client), so those
 * two values never coexist in shopper traffic. The suite built an input production does not
 * produce, and therefore proved an isolation that did not exist: in reality every authenticated
 * shopper shared one bucket. The tenant cases below are kept — they are real for assume-tenant /
 * operator tokens, where {@code tenant_id} genuinely varies — and the case that matters for the
 * dominant traffic is added: <b>same tenant, different accounts</b>.
 */
@DisplayName("tenantRouteKeyResolver 단위 테스트")
class TenantRouteRateLimitConfigTest {

    private final KeyResolver resolver = new TenantRouteRateLimitConfig().tenantRouteKeyResolver();

    @Test
    @DisplayName("인증 요청 → rate:ecommerce-gw:<route>:t:<tenant>:acct:<sub>")
    void authenticatedRequest_keysByTenantAccountAndRoute() {
        MockServerWebExchange exchange = exchangeFor("10.0.0.5", routeWithId("product-service"));

        String key = resolver.resolve(exchange)
                .contextWrite(authContext(jwt("acme", "user-123")))
                .block();

        assertThat(key).isEqualTo("rate:ecommerce-gw:product-service:t:acme:acct:user-123");
    }

    /**
     * The regression this whole task exists for. Every ecommerce shopper carries
     * {@code tenant_id=ecommerce}; before MONO-368 they all collapsed into
     * {@code rate:ecommerce-gw:<route>:t:ecommerce} and one bursting account could 429 the
     * entire marketplace.
     */
    @Test
    @DisplayName("같은 tenant 의 서로 다른 두 계정 → 키 분리 (한 쇼퍼가 전체를 굶기지 못한다)")
    void sameTenantDifferentAccounts_produceIndependentKeys() {
        MockServerWebExchange exchangeA = exchangeFor("10.0.0.9", routeWithId("order-service"));
        MockServerWebExchange exchangeB = exchangeFor("10.0.0.9", routeWithId("order-service"));

        String keyA = resolver.resolve(exchangeA)
                .contextWrite(authContext(jwt("ecommerce", "shopper-a"))).block();
        String keyB = resolver.resolve(exchangeB)
                .contextWrite(authContext(jwt("ecommerce", "shopper-b"))).block();

        assertThat(keyA).isEqualTo("rate:ecommerce-gw:order-service:t:ecommerce:acct:shopper-a");
        assertThat(keyB).isEqualTo("rate:ecommerce-gw:order-service:t:ecommerce:acct:shopper-b");
        assertThat(keyA).isNotEqualTo(keyB);
    }

    @Test
    @DisplayName("같은 IP라도 tenant 가 다르면 키가 분리된다 (cross-tenant 격리, M7)")
    void differentTenantsSameIp_produceIndependentKeys() {
        MockServerWebExchange exchangeA = exchangeFor("10.0.0.9", routeWithId("order-service"));
        MockServerWebExchange exchangeB = exchangeFor("10.0.0.9", routeWithId("order-service"));

        String keyA = resolver.resolve(exchangeA)
                .contextWrite(authContext(jwt("tenant-a", "user-123"))).block();
        String keyB = resolver.resolve(exchangeB)
                .contextWrite(authContext(jwt("tenant-b", "user-123"))).block();

        assertThat(keyA).isEqualTo("rate:ecommerce-gw:order-service:t:tenant-a:acct:user-123");
        assertThat(keyB).isEqualTo("rate:ecommerce-gw:order-service:t:tenant-b:acct:user-123");
        assertThat(keyA).isNotEqualTo(keyB);
    }

    @Test
    @DisplayName("같은 tenant+계정, 다른 route → 키 분리 (route_id tuple)")
    void sameTenantDifferentRoutes_produceIndependentKeys() {
        String keyProducts = resolver.resolve(exchangeFor("10.0.0.1", routeWithId("product-service")))
                .contextWrite(authContext(jwt("acme", "user-123"))).block();
        String keyOrders = resolver.resolve(exchangeFor("10.0.0.1", routeWithId("order-service")))
                .contextWrite(authContext(jwt("acme", "user-123"))).block();

        assertThat(keyProducts).isEqualTo("rate:ecommerce-gw:product-service:t:acme:acct:user-123");
        assertThat(keyOrders).isEqualTo("rate:ecommerce-gw:order-service:t:acme:acct:user-123");
        assertThat(keyProducts).isNotEqualTo(keyOrders);
    }

    @Test
    @DisplayName("tenant_id claim 이 비어있는 인증 토큰 → 기본 테넌트 'ecommerce' (null 금지)")
    void blankTenantClaim_fallsBackToDefaultTenant() {
        MockServerWebExchange exchange = exchangeFor("10.0.0.5", routeWithId("product-service"));

        String key = resolver.resolve(exchange)
                .contextWrite(authContext(jwt("", "user-123")))
                .block();

        assertThat(key).isEqualTo("rate:ecommerce-gw:product-service:t:ecommerce:acct:user-123");
    }

    /**
     * A token with no usable {@code sub} degrades to the tenant-only bucket — coarse, but it
     * must never produce a null key or a synthetic account that merges distinct callers.
     */
    @Test
    @DisplayName("sub 가 없는 인증 토큰 → tenant-only 키로 강등 (null 키 금지)")
    void missingSubject_degradesToTenantOnlyKey() {
        MockServerWebExchange exchange = exchangeFor("10.0.0.5", routeWithId("product-service"));
        Jwt noSubject = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("tenant_id", "acme")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();

        String key = resolver.resolve(exchange).contextWrite(authContext(noSubject)).block();

        assertThat(key).isEqualTo("rate:ecommerce-gw:product-service:t:acme");
    }

    @Test
    @DisplayName("익명/pre-auth 요청 → 기본 테넌트 + client IP 로 키 (IP 바운딩 보존, MONO-368 무변경)")
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

    private static Jwt jwt(String tenantId, String subject) {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(subject)
                .claim("tenant_id", tenantId)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
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
