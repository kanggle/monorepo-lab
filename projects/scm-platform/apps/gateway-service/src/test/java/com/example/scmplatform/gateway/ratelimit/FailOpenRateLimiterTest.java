package com.example.scmplatform.gateway.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.lettuce.core.RedisException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Exercises the fail-open decorator. The contract is intentionally narrowed:
 *
 * <ol>
 *   <li>Redis-class failures (connection refusal, timeout, Lettuce/Spring wrappers)
 *       fail open with sentinel header and increment
 *       {@code gateway_ratelimit_redis_unavailable_total}.</li>
 *   <li>Other reactive errors propagate (do NOT fail open) and increment
 *       {@code gateway_ratelimit_unexpected_error_total} so ops still has visibility.</li>
 * </ol>
 *
 * See {@code platform/api-gateway-policy.md} § Rate Limiting and TASK-SCM-BE-001
 * § Failure Scenarios.
 */
class FailOpenRateLimiterTest {

    @Test
    void failsOpenWhenDelegateEmitsRedisConnectionFailureAndIncrementsMetric() {
        RedisRateLimiter delegate = mock(RedisRateLimiter.class);
        when(delegate.isAllowed("procurement-service", "rate:scm-platform:procurement-service:203.0.113.1"))
                .thenReturn(Mono.error(new RedisConnectionFailureException("redis down")));

        MeterRegistry registry = new SimpleMeterRegistry();
        FailOpenRateLimiter limiter = new FailOpenRateLimiter(delegate, registry);

        StepVerifier.create(limiter.isAllowed("procurement-service",
                        "rate:scm-platform:procurement-service:203.0.113.1"))
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
    void failsOpenOnRedisConnectionFailure() {
        RedisRateLimiter delegate = mock(RedisRateLimiter.class);
        when(delegate.isAllowed("procurement-service", "k"))
                .thenReturn(Mono.error(new RedisConnectionFailureException("connection refused")));

        MeterRegistry registry = new SimpleMeterRegistry();
        FailOpenRateLimiter limiter = new FailOpenRateLimiter(delegate, registry);

        RateLimiter.Response response = limiter.isAllowed("procurement-service", "k").block();

        assertThat(response).isNotNull();
        assertThat(response.isAllowed()).isTrue();
        assertThat(response.getHeaders()).containsEntry(RedisRateLimiter.REMAINING_HEADER, "-1");
        assertThat(registry.counter(FailOpenRateLimiter.METRIC_REDIS_UNAVAILABLE).count())
                .isEqualTo(1.0);
    }

    @Test
    void failsOpenOnQueryTimeout() {
        RedisRateLimiter delegate = mock(RedisRateLimiter.class);
        when(delegate.isAllowed("procurement-service", "k"))
                .thenReturn(Mono.error(new QueryTimeoutException("redis command timed out")));

        MeterRegistry registry = new SimpleMeterRegistry();
        FailOpenRateLimiter limiter = new FailOpenRateLimiter(delegate, registry);

        RateLimiter.Response response = limiter.isAllowed("procurement-service", "k").block();

        assertThat(response).isNotNull();
        assertThat(response.isAllowed()).isTrue();
        assertThat(registry.counter(FailOpenRateLimiter.METRIC_REDIS_UNAVAILABLE).count())
                .isEqualTo(1.0);
    }

    @Test
    void failsOpenOnLettuceRedisException() {
        RedisRateLimiter delegate = mock(RedisRateLimiter.class);
        when(delegate.isAllowed("procurement-service", "k"))
                .thenReturn(Mono.error(new RedisException("MOVED slot")));

        MeterRegistry registry = new SimpleMeterRegistry();
        FailOpenRateLimiter limiter = new FailOpenRateLimiter(delegate, registry);

        RateLimiter.Response response = limiter.isAllowed("procurement-service", "k").block();

        assertThat(response).isNotNull();
        assertThat(response.isAllowed()).isTrue();
        assertThat(registry.counter(FailOpenRateLimiter.METRIC_REDIS_UNAVAILABLE).count())
                .isEqualTo(1.0);
    }

    @Test
    void failsOpenOnRedisSystemExceptionWrappingLettuce() {
        // Spring frequently re-wraps Lettuce errors in RedisSystemException; the cause
        // chain check must still recognise the failure.
        RedisRateLimiter delegate = mock(RedisRateLimiter.class);
        RedisSystemException wrapped = new RedisSystemException(
                "wrapped", new RedisException("low-level redis failure"));
        when(delegate.isAllowed("procurement-service", "k")).thenReturn(Mono.error(wrapped));

        MeterRegistry registry = new SimpleMeterRegistry();
        FailOpenRateLimiter limiter = new FailOpenRateLimiter(delegate, registry);

        RateLimiter.Response response = limiter.isAllowed("procurement-service", "k").block();

        assertThat(response).isNotNull();
        assertThat(response.isAllowed()).isTrue();
        assertThat(registry.counter(FailOpenRateLimiter.METRIC_REDIS_UNAVAILABLE).count())
                .isEqualTo(1.0);
    }

    @Test
    void propagatesUnknownErrorsAndIncrementsUnexpectedCounter() {
        // A non-Redis error (e.g. programming bug, malformed Lua reply parser, NPE)
        // MUST NOT be masked as "redis down". Propagate so SCG translates to 5xx,
        // observability picks it up, and increment the unexpected counter.
        RedisRateLimiter delegate = mock(RedisRateLimiter.class);
        when(delegate.isAllowed("procurement-service", "k"))
                .thenReturn(Mono.error(new RuntimeException("not redis: programming bug")));

        MeterRegistry registry = new SimpleMeterRegistry();
        FailOpenRateLimiter limiter = new FailOpenRateLimiter(delegate, registry);

        assertThatThrownBy(() -> limiter.isAllowed("procurement-service", "k").block())
                .isInstanceOf(RuntimeException.class)
                .hasMessage("not redis: programming bug");

        assertThat(registry.counter(FailOpenRateLimiter.METRIC_REDIS_UNAVAILABLE).count())
                .isEqualTo(0.0);
        assertThat(registry.counter(FailOpenRateLimiter.METRIC_UNEXPECTED_ERROR).count())
                .isEqualTo(1.0);
    }

    @Test
    void propagatesIllegalStateException() {
        RedisRateLimiter delegate = mock(RedisRateLimiter.class);
        when(delegate.isAllowed("procurement-service", "k"))
                .thenReturn(Mono.error(new IllegalStateException("limiter not configured")));

        MeterRegistry registry = new SimpleMeterRegistry();
        FailOpenRateLimiter limiter = new FailOpenRateLimiter(delegate, registry);

        StepVerifier.create(limiter.isAllowed("procurement-service", "k"))
                .expectErrorMatches(t -> t instanceof IllegalStateException
                        && "limiter not configured".equals(t.getMessage()))
                .verify();

        assertThat(registry.counter(FailOpenRateLimiter.METRIC_REDIS_UNAVAILABLE).count())
                .isEqualTo(0.0);
        assertThat(registry.counter(FailOpenRateLimiter.METRIC_UNEXPECTED_ERROR).count())
                .isEqualTo(1.0);
    }

    @Test
    void passesThroughAllowedResponseFromDelegateWithoutIncrementingMetric() {
        RedisRateLimiter delegate = mock(RedisRateLimiter.class);
        RateLimiter.Response allowed = new RateLimiter.Response(true,
                java.util.Map.of(RedisRateLimiter.REMAINING_HEADER, "42"));
        when(delegate.isAllowed("procurement-service", "k"))
                .thenReturn(Mono.just(allowed));

        MeterRegistry registry = new SimpleMeterRegistry();
        FailOpenRateLimiter limiter = new FailOpenRateLimiter(delegate, registry);

        RateLimiter.Response response = limiter.isAllowed("procurement-service", "k").block();

        assertThat(response).isNotNull();
        assertThat(response.isAllowed()).isTrue();
        assertThat(response.getHeaders()).containsEntry(RedisRateLimiter.REMAINING_HEADER, "42");
        assertThat(registry.counter(FailOpenRateLimiter.METRIC_REDIS_UNAVAILABLE).count())
                .isEqualTo(0.0);
        assertThat(registry.counter(FailOpenRateLimiter.METRIC_UNEXPECTED_ERROR).count())
                .isEqualTo(0.0);
    }

    @Test
    void passesThroughDeniedResponseFromDelegate() {
        RedisRateLimiter delegate = mock(RedisRateLimiter.class);
        RateLimiter.Response denied = new RateLimiter.Response(false,
                java.util.Map.of(RedisRateLimiter.REMAINING_HEADER, "0"));
        when(delegate.isAllowed("procurement-service", "k"))
                .thenReturn(Mono.just(denied));

        MeterRegistry registry = new SimpleMeterRegistry();
        FailOpenRateLimiter limiter = new FailOpenRateLimiter(delegate, registry);

        RateLimiter.Response response = limiter.isAllowed("procurement-service", "k").block();

        assertThat(response).isNotNull();
        assertThat(response.isAllowed()).isFalse();
    }
}
