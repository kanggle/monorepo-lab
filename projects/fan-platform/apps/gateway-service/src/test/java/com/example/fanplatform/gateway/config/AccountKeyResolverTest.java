package com.example.fanplatform.gateway.config;

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
 * {@code accountKeyResolver} — the bean every fan route actually references
 * ({@code key-resolver: "#{@accountKeyResolver}"}).
 *
 * <p>Added by TASK-MONO-370. Until then the only key-shape test in this project covered
 * {@code clientIpKeyResolver}, which <em>no</em> route uses: the live path was untested and the
 * dead one was tested. That is how the {@code "acct:" + getSubject()} defect below survived —
 * a token with no {@code sub} concatenated to the literal key {@code acct:null}, merging every
 * such caller into one synthetic shared bucket.
 */
@DisplayName("accountKeyResolver — 인증 트래픽 키 형태")
class AccountKeyResolverTest {

    private final KeyResolver resolver = new RateLimitConfig().accountKeyResolver();

    @Test
    @DisplayName("인증 요청 → rate:fan-platform:<route>:acct:<sub>")
    void authenticatedRequestKeysOnTheAccount() {
        String key = resolver.resolve(exchangeFor("10.0.0.5", routeWithId("community-service")))
                .contextWrite(authContext("operator-a"))
                .block();

        assertThat(key).isEqualTo("rate:fan-platform:community-service:acct:operator-a");
    }

    @Test
    @DisplayName("같은 IP 의 서로 다른 두 계정 → 키 분리 (NAT 뒤에서 버킷을 공유하지 않는다)")
    void sameIpDifferentAccountsProduceIndependentKeys() {
        String ip = "203.0.113.42";

        String a = resolver.resolve(exchangeFor(ip, routeWithId("community-service")))
                .contextWrite(authContext("operator-a")).block();
        String b = resolver.resolve(exchangeFor(ip, routeWithId("community-service")))
                .contextWrite(authContext("operator-b")).block();

        assertThat(a).isEqualTo("rate:fan-platform:community-service:acct:operator-a");
        assertThat(b).isEqualTo("rate:fan-platform:community-service:acct:operator-b");
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    @DisplayName("sub 가 없는 토큰 → IP 키로 폴백 ('acct:null' 합성 버킷 금지)")
    void aTokenWithoutASubjectFallsBackToTheIpKey() {
        Jwt noSubject = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("tenant_id", "fan-platform")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();

        String key = resolver.resolve(exchangeFor("192.0.2.5", routeWithId("community-service")))
                .contextWrite(authContext(noSubject))
                .block();

        assertThat(key).isEqualTo("rate:fan-platform:community-service:192.0.2.5");
    }

    @Test
    @DisplayName("보안 컨텍스트 부재 → IP 키로 폴백 (pre-auth 경로)")
    void anonymousRequestFallsBackToTheIpKey() {
        String key = resolver.resolve(exchangeFor("198.51.100.7", routeWithId("community-service")))
                .block();

        assertThat(key).isEqualTo("rate:fan-platform:community-service:198.51.100.7");
    }

    // --- helpers --------------------------------------------------------------

    private static Context authContext(String subject) {
        return authContext(Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(subject)
                .claim("tenant_id", "fan-platform")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build());
    }

    private static Context authContext(Jwt jwt) {
        return ReactiveSecurityContextHolder.withSecurityContext(
                Mono.just(new SecurityContextImpl(new JwtAuthenticationToken(jwt))));
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
