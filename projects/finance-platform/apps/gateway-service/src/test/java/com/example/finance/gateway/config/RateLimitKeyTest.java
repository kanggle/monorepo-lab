package com.example.finance.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

/**
 * The rate-limit key's <em>shape</em>, which is a decision rather than a detail.
 *
 * <p>Anonymous traffic can only be bucketed by client IP; authenticated traffic is keyed on the
 * JWT {@code sub}, so the bucket follows the account rather than the network path
 * ({@code platform/api-gateway-policy.md} § Rate Limiting &gt; Key shape). Both keys carry a
 * project prefix, which an unprefixed key does not — that collides across domains the moment two
 * projects share a Redis.
 *
 * <p><b>Correction (TASK-MONO-370).</b> This class previously said wms's IP-only keying carried
 * "no documented rationale". It did: {@code api-gateway-policy.md} L92 declared
 * {@code (clientIp, routeId)} as the platform <em>default</em> at the time, and wms was the one
 * gateway that conformed to it. TASK-MONO-368 raised that rule and TASK-MONO-370 aligned wms, so
 * the whole fleet now keys authenticated traffic on the principal. The claim was false when it
 * was written and is moot now; it is corrected rather than merely deleted, because the mistake
 * behind it — asserting a fleet convention from a head-count instead of reading the policy — is
 * the part worth remembering.
 */
@DisplayName("RateLimitConfig — 키 형태 (프로젝트 접두사 + 계정 키잉)")
class RateLimitKeyTest {

    private final RateLimitConfig config = new RateLimitConfig();

    // --- authenticated: the bucket follows the account ------------------------

    @Test
    @DisplayName("인증 요청 → rate:finance-platform:<route>:acct:<sub>")
    void authenticatedRequestKeysOnTheAccount() {
        String key = config.accountKeyResolver()
                .resolve(get())
                .contextWrite(authContext("operator-a"))
                .block();

        assertThat(key).isEqualTo("rate:finance-platform:unknown:acct:operator-a");
    }

    @Test
    @DisplayName("같은 IP 의 서로 다른 두 계정 → 키 분리 (NAT 뒤에서 버킷을 공유하지 않는다)")
    void sameIpDifferentAccountsProduceIndependentKeys() {
        KeyResolver resolver = config.accountKeyResolver();

        String a = resolver.resolve(get()).contextWrite(authContext("operator-a")).block();
        String b = resolver.resolve(get()).contextWrite(authContext("operator-b")).block();

        assertThat(a).isNotEqualTo(b);
        assertThat(a).endsWith(":acct:operator-a");
        assertThat(b).endsWith(":acct:operator-b");
    }

    /**
     * TASK-MONO-370. The previous chain built {@code "acct:" + getSubject()}, so a token with no
     * {@code sub} produced the literal key {@code acct:null} — one synthetic bucket shared by
     * every such caller. Falling back to the IP key is coarse, but it is a real bucket.
     */
    @Test
    @DisplayName("sub 가 없는 토큰 → IP 키로 폴백 ('acct:null' 합성 버킷 금지)")
    void aTokenWithoutASubjectFallsBackToTheIpKey() {
        Jwt noSubject = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("tenant_id", "finance")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();

        String key = config.accountKeyResolver()
                .resolve(get())
                .contextWrite(authContext(noSubject))
                .block();

        assertThat(key).doesNotContain("acct:").contains("203.0.113.7");
    }

    @Test
    @DisplayName("보안 컨텍스트 부재 → IP 키로 폴백 (pre-auth 경로)")
    void anonymousRequestFallsBackToTheIpKey() {
        String key = config.accountKeyResolver().resolve(get()).block();

        assertThat(key).isEqualTo("rate:finance-platform:unknown:203.0.113.7");
    }

    // --- the IP key itself ----------------------------------------------------

    @Test
    @DisplayName("키에 프로젝트 접두사가 있다 — 공유 Redis 에서 도메인 간 충돌을 막는다")
    void keyIsProjectPrefixed() {
        String key = config.clientIpKeyResolver().resolve(get()).block();

        assertThat(key)
                .as("an unprefixed key collides across domains in a shared Redis")
                .startsWith("rate:finance-platform:")
                .contains("203.0.113.7");
    }

    @Test
    @DisplayName("X-Forwarded-For 의 첫 값을 클라이언트 IP 로 쓴다")
    void usesTheFirstForwardedForHop() {
        String key = config.clientIpKeyResolver()
                .resolve(MockServerWebExchange.from(
                        MockServerHttpRequest.get("/api/finance/accounts")
                                .header("X-Forwarded-For", "203.0.113.7, 10.0.0.1")
                                .build()))
                .block();

        assertThat(key).contains("203.0.113.7").doesNotContain("10.0.0.1");
    }

    @Test
    @DisplayName("라우트 속성이 없어도 NPE 없이 'unknown' 으로 강등된다")
    void degradesGracefullyWithoutARouteAttribute() {
        String key = config.clientIpKeyResolver()
                .resolve(MockServerWebExchange.from(
                        MockServerHttpRequest.get("/api/finance/accounts").build()))
                .block();

        assertThat(key).startsWith("rate:finance-platform:unknown:");
    }

    // --- helpers --------------------------------------------------------------

    private static MockServerWebExchange get() {
        return MockServerWebExchange.from(MockServerHttpRequest.get("/api/finance/accounts")
                .header("X-Forwarded-For", "203.0.113.7")
                .build());
    }

    private static Context authContext(String subject) {
        return authContext(Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(subject)
                .claim("tenant_id", "finance")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build());
    }

    private static Context authContext(Jwt jwt) {
        return ReactiveSecurityContextHolder.withSecurityContext(
                Mono.just(new SecurityContextImpl(new JwtAuthenticationToken(jwt))));
    }
}
