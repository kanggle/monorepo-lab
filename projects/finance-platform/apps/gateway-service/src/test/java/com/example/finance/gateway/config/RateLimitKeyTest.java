package com.example.finance.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

/**
 * The rate-limit key's <em>shape</em>, which is a decision rather than a detail.
 *
 * <p>finance keys under a project prefix. wms does not — its keys are a bare
 * {@code {ip}:{routeId}}, which collides across domains the moment two projects share a Redis,
 * and it carries no documented rationale for that. TASK-MONO-355 left that open as a question for
 * a human rather than picking a default inside a refactor; a new gateway still has to choose, and
 * it chooses the shape that can be defended (TASK-MONO-357).
 */
@DisplayName("RateLimitConfig — 키 형태 (프로젝트 접두사)")
class RateLimitKeyTest {

    private final RateLimitConfig config = new RateLimitConfig();

    @Test
    @DisplayName("키에 프로젝트 접두사가 있다 — 공유 Redis 에서 도메인 간 충돌을 막는다")
    void keyIsProjectPrefixed() {
        KeyResolver resolver = config.clientIpKeyResolver();

        String key = resolver.resolve(MockServerWebExchange.from(
                        MockServerHttpRequest.get("/api/finance/accounts")
                                .header("X-Forwarded-For", "203.0.113.7")
                                .build()))
                .block();

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
}
