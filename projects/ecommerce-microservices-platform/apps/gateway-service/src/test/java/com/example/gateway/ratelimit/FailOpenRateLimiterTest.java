package com.example.gateway.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.lettuce.core.RedisException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Exercises the fail-open degrade decorator (TASK-BE-405 § Degrade / Edge Cases —
 * "Redis down: fail-open"). The contract is intentionally narrowed:
 *
 * <ol>
 *   <li>Redis-class failures (connection refusal, timeout, Lettuce/Spring wrappers) fail
 *       open with sentinel header {@code X-RateLimit-Remaining: -1} and increment
 *       {@code gateway_ratelimit_redis_unavailable_total}.</li>
 *   <li>Other reactive errors propagate (do NOT fail open) and increment
 *       {@code gateway_ratelimit_unexpected_error_total} so ops still has visibility.</li>
 * </ol>
 */
@DisplayName("FailOpenRateLimiter 단위 테스트")
class FailOpenRateLimiterTest {

    @Test
    @DisplayName("Redis 연결 실패 → fail-open + sentinel 헤더 + 메트릭 증가")
    void failsOpenOnRedisConnectionFailureAndIncrementsMetric() {
        RedisRateLimiter delegate = mock(RedisRateLimiter.class);
        when(delegate.isAllowed("product-service", "rate:ecommerce-gw:product-service:t:acme"))
                .thenReturn(Mono.error(new RedisConnectionFailureException("redis down")));

        MeterRegistry registry = new SimpleMeterRegistry();
        FailOpenRateLimiter limiter = new FailOpenRateLimiter(delegate, registry);

        StepVerifier.create(limiter.isAllowed("product-service",
                        "rate:ecommerce-gw:product-service:t:acme"))
                .assertNext(response -> {
                    assertThat(response.isAllowed()).isTrue();
                    assertThat(response.getHeaders())
                            .containsEntry(RedisRateLimiter.REMAINING_HEADER, "-1");
                })
                .verifyComplete();

        assertThat(registry.counter(FailOpenRateLimiter.METRIC_REDIS_UNAVAILABLE).count())
                .isEqualTo(1.0);
        assertThat(registry.counter(FailOpenRateLimiter.METRIC_UNEXPECTED_ERROR).count())
                .isEqualTo(0.0);
    }

    @Test
    @DisplayName("Redis 쿼리 타임아웃 → fail-open")
    void failsOpenOnQueryTimeout() {
        RedisRateLimiter delegate = mock(RedisRateLimiter.class);
        when(delegate.isAllowed("order-service", "k"))
                .thenReturn(Mono.error(new QueryTimeoutException("redis command timed out")));

        MeterRegistry registry = new SimpleMeterRegistry();
        FailOpenRateLimiter limiter = new FailOpenRateLimiter(delegate, registry);

        RateLimiter.Response response = limiter.isAllowed("order-service", "k").block();

        assertThat(response).isNotNull();
        assertThat(response.isAllowed()).isTrue();
        assertThat(registry.counter(FailOpenRateLimiter.METRIC_REDIS_UNAVAILABLE).count())
                .isEqualTo(1.0);
    }

    @Test
    @DisplayName("Lettuce RedisException → fail-open")
    void failsOpenOnLettuceRedisException() {
        RedisRateLimiter delegate = mock(RedisRateLimiter.class);
        when(delegate.isAllowed("order-service", "k"))
                .thenReturn(Mono.error(new RedisException("MOVED slot")));

        MeterRegistry registry = new SimpleMeterRegistry();
        FailOpenRateLimiter limiter = new FailOpenRateLimiter(delegate, registry);

        RateLimiter.Response response = limiter.isAllowed("order-service", "k").block();

        assertThat(response).isNotNull();
        assertThat(response.isAllowed()).isTrue();
        assertThat(registry.counter(FailOpenRateLimiter.METRIC_REDIS_UNAVAILABLE).count())
                .isEqualTo(1.0);
    }

    @Test
    @DisplayName("Lettuce 에러를 감싼 RedisSystemException → cause chain 인식 후 fail-open")
    void failsOpenOnRedisSystemExceptionWrappingLettuce() {
        RedisRateLimiter delegate = mock(RedisRateLimiter.class);
        RedisSystemException wrapped = new RedisSystemException(
                "wrapped", new RedisException("low-level redis failure"));
        when(delegate.isAllowed("order-service", "k")).thenReturn(Mono.error(wrapped));

        MeterRegistry registry = new SimpleMeterRegistry();
        FailOpenRateLimiter limiter = new FailOpenRateLimiter(delegate, registry);

        RateLimiter.Response response = limiter.isAllowed("order-service", "k").block();

        assertThat(response).isNotNull();
        assertThat(response.isAllowed()).isTrue();
        assertThat(registry.counter(FailOpenRateLimiter.METRIC_REDIS_UNAVAILABLE).count())
                .isEqualTo(1.0);
    }

    @Test
    @DisplayName("Redis 가 아닌 에러 → 전파(fail-open 금지) + unexpected 메트릭")
    void propagatesUnknownErrorsAndIncrementsUnexpectedCounter() {
        RedisRateLimiter delegate = mock(RedisRateLimiter.class);
        when(delegate.isAllowed("order-service", "k"))
                .thenReturn(Mono.error(new RuntimeException("not redis: programming bug")));

        MeterRegistry registry = new SimpleMeterRegistry();
        FailOpenRateLimiter limiter = new FailOpenRateLimiter(delegate, registry);

        assertThatThrownBy(() -> limiter.isAllowed("order-service", "k").block())
                .isInstanceOf(RuntimeException.class)
                .hasMessage("not redis: programming bug");

        assertThat(registry.counter(FailOpenRateLimiter.METRIC_REDIS_UNAVAILABLE).count())
                .isEqualTo(0.0);
        assertThat(registry.counter(FailOpenRateLimiter.METRIC_UNEXPECTED_ERROR).count())
                .isEqualTo(1.0);
    }

    @Test
    @DisplayName("delegate 의 allowed 응답 → 그대로 통과(메트릭 무증가)")
    void passesThroughAllowedResponseFromDelegate() {
        RedisRateLimiter delegate = mock(RedisRateLimiter.class);
        RateLimiter.Response allowed = new RateLimiter.Response(true,
                java.util.Map.of(RedisRateLimiter.REMAINING_HEADER, "42"));
        when(delegate.isAllowed("order-service", "k")).thenReturn(Mono.just(allowed));

        MeterRegistry registry = new SimpleMeterRegistry();
        FailOpenRateLimiter limiter = new FailOpenRateLimiter(delegate, registry);

        RateLimiter.Response response = limiter.isAllowed("order-service", "k").block();

        assertThat(response).isNotNull();
        assertThat(response.isAllowed()).isTrue();
        assertThat(response.getHeaders()).containsEntry(RedisRateLimiter.REMAINING_HEADER, "42");
        assertThat(registry.counter(FailOpenRateLimiter.METRIC_REDIS_UNAVAILABLE).count())
                .isEqualTo(0.0);
        assertThat(registry.counter(FailOpenRateLimiter.METRIC_UNEXPECTED_ERROR).count())
                .isEqualTo(0.0);
    }

    @Test
    @DisplayName("delegate 의 denied 응답(429 경계) → 그대로 통과")
    void passesThroughDeniedResponseFromDelegate() {
        RedisRateLimiter delegate = mock(RedisRateLimiter.class);
        RateLimiter.Response denied = new RateLimiter.Response(false,
                java.util.Map.of(RedisRateLimiter.REMAINING_HEADER, "0"));
        when(delegate.isAllowed("order-service", "k")).thenReturn(Mono.just(denied));

        MeterRegistry registry = new SimpleMeterRegistry();
        FailOpenRateLimiter limiter = new FailOpenRateLimiter(delegate, registry);

        RateLimiter.Response response = limiter.isAllowed("order-service", "k").block();

        assertThat(response).isNotNull();
        assertThat(response.isAllowed()).isFalse();
    }
}
