package com.wms.gateway.config;

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
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

/**
 * The rate-limit key's <em>shape</em>, which is a decision rather than a detail
 * (TASK-MONO-370).
 *
 * <p>Every wms route is JWT-authenticated, so the bucket follows the <b>account</b>, not the
 * network path — otherwise every operator behind one warehouse NAT shares a bucket while an
 * abuser rotating IPs is never throttled per account
 * ({@code platform/api-gateway-policy.md} § Rate Limiting &gt; Key shape). The IP key survives
 * as the fallback for requests that arrive without a security context, so such a path degrades
 * to the pre-370 behaviour rather than to a null key.
 */
class ClientIpKeyResolverTest {

    private final RateLimitConfig config = new RateLimitConfig();
    private final KeyResolver ipResolver = config.clientIpKeyResolver();
    private final KeyResolver accountResolver = config.accountKeyResolver();

    // --- authenticated: the bucket follows the account -----------------------

    /**
     * The regression this task exists for: two operators sharing one warehouse NAT must not
     * share a rate-limit bucket.
     */
    @Test
    @DisplayName("같은 IP 의 서로 다른 두 계정 → 키 분리 (NAT 뒤 운영자들이 버킷을 공유하지 않는다)")
    void sameIpDifferentAccounts_produceIndependentKeys() {
        String ip = "203.0.113.42";

        String keyA = accountResolver.resolve(exchangeFor(ip, routeWithId("outbound-service")))
                .contextWrite(authContext("operator-a")).block();
        String keyB = accountResolver.resolve(exchangeFor(ip, routeWithId("outbound-service")))
                .contextWrite(authContext("operator-b")).block();

        assertThat(keyA).isEqualTo("rate:wms-platform:outbound-service:acct:operator-a");
        assertThat(keyB).isEqualTo("rate:wms-platform:outbound-service:acct:operator-b");
        assertThat(keyA).isNotEqualTo(keyB);
    }

    @Test
    @DisplayName("같은 계정, 다른 route → 키 분리 (route 별 독립 버킷)")
    void sameAccountDifferentRoutes_produceIndependentKeys() {
        String master = accountResolver.resolve(exchangeFor("10.0.0.1", routeWithId("master-service")))
                .contextWrite(authContext("operator-a")).block();
        String inventory = accountResolver.resolve(exchangeFor("10.0.0.1", routeWithId("inventory-service")))
                .contextWrite(authContext("operator-a")).block();

        assertThat(master).isEqualTo("rate:wms-platform:master-service:acct:operator-a");
        assertThat(inventory).isEqualTo("rate:wms-platform:inventory-service:acct:operator-a");
        assertThat(master).isNotEqualTo(inventory);
    }

    // --- degrade: no identity → the previous behaviour, never a null key -----

    @Test
    @DisplayName("보안 컨텍스트 부재 → IP 키로 폴백 (pre-370 동작 보존, 널 키 금지)")
    void withoutASecurityContext_fallsBackToTheIpKey() {
        String key = accountResolver.resolve(exchangeFor("198.51.100.7", routeWithId("master-service")))
                .block();

        assertThat(key).isEqualTo("rate:wms-platform:master-service:198.51.100.7");
    }

    @Test
    @DisplayName("sub 가 비어 있는 토큰 → IP 키로 폴백 (합성 계정 버킷 금지)")
    void blankSubject_fallsBackToTheIpKey() {
        Jwt noSubject = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("tenant_id", "wms")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();

        String key = accountResolver.resolve(exchangeFor("192.0.2.5", routeWithId("admin-service")))
                .contextWrite(authContext(noSubject))
                .block();

        assertThat(key).isEqualTo("rate:wms-platform:admin-service:192.0.2.5");
    }

    // --- the IP key itself (pre-auth routes, and the fallback above) ---------

    @Test
    @DisplayName("키에 프로젝트 접두사가 있다 — 공유 Redis 에서 도메인 간 충돌을 막는다")
    void keyIsProjectPrefixed() {
        String key = ipResolver.resolve(exchangeFor("203.0.113.42", routeWithId("master-service")))
                .block();

        assertThat(key)
                .as("an unprefixed key collides across domains in a shared Redis")
                .isEqualTo("rate:wms-platform:master-service:203.0.113.42");
    }

    @Test
    void producesDistinctKeysForSameIpButDifferentRoutes() {
        String ip = "203.0.113.42";

        String masterKey = ipResolver.resolve(exchangeFor(ip, routeWithId("master-service"))).block();
        String inventoryKey = ipResolver.resolve(exchangeFor(ip, routeWithId("inventory-service"))).block();

        assertThat(masterKey).isEqualTo("rate:wms-platform:master-service:203.0.113.42");
        assertThat(inventoryKey).isEqualTo("rate:wms-platform:inventory-service:203.0.113.42");
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

        String key = ipResolver.resolve(exchange).block();

        assertThat(key).isEqualTo("rate:wms-platform:master-service:198.51.100.7");
    }

    @Test
    void fallsBackToUnknownRouteWhenAttributeMissing() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/master/warehouses")
                .remoteAddress(new InetSocketAddress("192.0.2.5", 9999))
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        // Deliberately DO NOT set GATEWAY_ROUTE_ATTR — resolver must not NPE.

        String key = ipResolver.resolve(exchange).block();

        assertThat(key).isEqualTo("rate:wms-platform:unknown:192.0.2.5");
    }

    @Test
    void fallsBackToUnknownIpWhenAllSourcesAreMissing() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/master/warehouses").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR, routeWithId("master-service"));

        String key = ipResolver.resolve(exchange).block();

        assertThat(key).isEqualTo("rate:wms-platform:master-service:unknown");
    }

    // --- helpers ------------------------------------------------------------

    private static Context authContext(String subject) {
        return authContext(Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(subject)
                .claim("tenant_id", "wms")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build());
    }

    private static Context authContext(Jwt jwt) {
        return ReactiveSecurityContextHolder.withSecurityContext(
                Mono.just(new SecurityContextImpl(new JwtAuthenticationToken(jwt))));
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
