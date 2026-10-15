package com.example.gateway.ratelimit;

import com.example.gateway.config.EdgeGatewayProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("TokenBucketRateLimiter 단위 테스트")
class TokenBucketRateLimiterUnitTest {

    @Mock
    private ReactiveStringRedisTemplate redisTemplate;

    private TokenBucketRateLimiter rateLimiter;
    private EdgeGatewayProperties properties;

    @BeforeEach
    void setUp() {
        properties = new EdgeGatewayProperties();
        properties.getRateLimit().setFailOpen(true);
        properties.getRateLimit().setLogin(new EdgeGatewayProperties.ScopeLimit(20, 60));
        properties.getRateLimit().setGlobal(new EdgeGatewayProperties.ScopeLimit(100, 1));
        rateLimiter = new TokenBucketRateLimiter(redisTemplate, properties);
    }

    @Test
    @DisplayName("요청 수가 제한 이하일 때 허용")
    void isAllowed_underLimit_returnsAllowed() {
        given(redisTemplate.execute(any(), anyList(), anyList()))
                .willReturn(Flux.just(5L));

        StepVerifier.create(rateLimiter.isAllowed("login", "192.168.1.0/24"))
                .assertNext(result -> {
                    assertThat(result.isAllowed()).isTrue();
                    assertThat(result.retryAfterSeconds()).isZero();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("요청 수가 제한 초과 시 거부")
    void isAllowed_overLimit_returnsRejected() {
        given(redisTemplate.execute(any(), anyList(), anyList()))
                .willReturn(Flux.just(21L));

        StepVerifier.create(rateLimiter.isAllowed("login", "192.168.1.0/24"))
                .assertNext(result -> {
                    assertThat(result.isAllowed()).isFalse();
                    assertThat(result.retryAfterSeconds()).isEqualTo(60);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Redis 장애 시 fail-open 동작")
    void isAllowed_redisError_failOpen_returnsAllowed() {
        given(redisTemplate.execute(any(), anyList(), anyList()))
                .willReturn(Flux.error(new RuntimeException("Redis connection failed")));

        StepVerifier.create(rateLimiter.isAllowed("global", "10.0.0.1"))
                .assertNext(result -> assertThat(result.isAllowed()).isTrue())
                .verifyComplete();
    }

    @Test
    @DisplayName("Redis 장애 시 fail-closed 동작")
    void isAllowed_redisError_failClosed_returnsRejected() {
        properties.getRateLimit().setFailOpen(false);

        given(redisTemplate.execute(any(), anyList(), anyList()))
                .willReturn(Flux.error(new RuntimeException("Redis connection failed")));

        StepVerifier.create(rateLimiter.isAllowed("global", "10.0.0.1"))
                .assertNext(result -> assertThat(result.isAllowed()).isFalse())
                .verifyComplete();
    }

    @Test
    @DisplayName("TASK-MONO-773 OD-8: 'onboarding' scope 기본값 = 5회/60초 — 5번째 허용 · 6번째 거부(Retry-After 60) · 키 rate:onboarding:{ip}")
    void onboardingScope_defaultFivePerSixtySeconds() {
        // the DEFAULT limits (no override) — redis-keys.md § scope 종류: onboarding · IP · 60s · 5
        TokenBucketRateLimiter defaults = new TokenBucketRateLimiter(redisTemplate, new EdgeGatewayProperties());
        given(redisTemplate.execute(any(), anyList(), anyList()))
                .willReturn(Flux.just(5L), Flux.just(6L));

        StepVerifier.create(defaults.isAllowed("onboarding", "203.0.113.7"))
                .assertNext(result -> assertThat(result.isAllowed()).isTrue())
                .verifyComplete();
        StepVerifier.create(defaults.isAllowed("onboarding", "203.0.113.7"))
                .assertNext(result -> {
                    assertThat(result.isAllowed()).as("the 6th request in the window").isFalse();
                    assertThat(result.retryAfterSeconds()).isEqualTo(60);
                })
                .verifyComplete();
        org.mockito.Mockito.verify(redisTemplate, org.mockito.Mockito.times(2))
                .execute(any(), org.mockito.ArgumentMatchers.eq(List.of("rate:onboarding:203.0.113.7")),
                        org.mockito.ArgumentMatchers.eq(List.of("70")));
    }

    @Test
    @DisplayName("알 수 없는 scope는 항상 허용")
    void isAllowed_unknownScope_returnsAllowed() {
        StepVerifier.create(rateLimiter.isAllowed("unknown", "10.0.0.1"))
                .assertNext(result -> assertThat(result.isAllowed()).isTrue())
                .verifyComplete();
    }
}
