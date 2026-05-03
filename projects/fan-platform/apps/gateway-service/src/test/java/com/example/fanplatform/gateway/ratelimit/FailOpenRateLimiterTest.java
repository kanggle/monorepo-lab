package com.example.fanplatform.gateway.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.data.redis.RedisConnectionFailureException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Exercises the fail-open decorator: when the underlying Redis-backed limiter emits a
 * reactive error (simulating a Redis outage), the decorator must:
 * <ol>
 *   <li>return {@code isAllowed = true} with sentinel header,</li>
 *   <li>increment the {@code gateway_ratelimit_redis_unavailable_total} counter.</li>
 * </ol>
 * See {@code platform/api-gateway-policy.md} § Rate Limiting and TASK-FAN-BE-001
 * § Implementation Notes.
 */
class FailOpenRateLimiterTest {

    @Test
    void failsOpenWhenDelegateEmitsRedisConnectionFailureAndIncrementsMetric() {
        RedisRateLimiter delegate = mock(RedisRateLimiter.class);
        when(delegate.isAllowed("community-service", "rate:fan-platform:community-service:203.0.113.1"))
                .thenReturn(Mono.error(new RedisConnectionFailureException("redis down")));

        MeterRegistry registry = new SimpleMeterRegistry();
        FailOpenRateLimiter limiter = new FailOpenRateLimiter(delegate, registry);

        StepVerifier.create(limiter.isAllowed("community-service",
                        "rate:fan-platform:community-service:203.0.113.1"))
                .assertNext(response -> {
                    assertThat(response.isAllowed()).isTrue();
                    assertThat(response.getHeaders())
                            .containsEntry(RedisRateLimiter.REMAINING_HEADER, "-1");
                })
                .verifyComplete();

        assertThat(registry.counter(FailOpenRateLimiter.METRIC_REDIS_UNAVAILABLE).count())
                .isEqualTo(1.0);
    }

    @Test
    void failsOpenOnAnyReactiveError() {
        RedisRateLimiter delegate = mock(RedisRateLimiter.class);
        when(delegate.isAllowed("community-service", "k"))
                .thenReturn(Mono.error(new RuntimeException("unexpected Lua error")));

        MeterRegistry registry = new SimpleMeterRegistry();
        FailOpenRateLimiter limiter = new FailOpenRateLimiter(delegate, registry);

        RateLimiter.Response response = limiter.isAllowed("community-service", "k").block();

        assertThat(response).isNotNull();
        assertThat(response.isAllowed()).isTrue();
        assertThat(response.getHeaders()).containsEntry(RedisRateLimiter.REMAINING_HEADER, "-1");
        assertThat(registry.counter(FailOpenRateLimiter.METRIC_REDIS_UNAVAILABLE).count())
                .isEqualTo(1.0);
    }

    @Test
    void passesThroughAllowedResponseFromDelegateWithoutIncrementingMetric() {
        RedisRateLimiter delegate = mock(RedisRateLimiter.class);
        RateLimiter.Response allowed = new RateLimiter.Response(true,
                java.util.Map.of(RedisRateLimiter.REMAINING_HEADER, "42"));
        when(delegate.isAllowed("community-service", "k"))
                .thenReturn(Mono.just(allowed));

        MeterRegistry registry = new SimpleMeterRegistry();
        FailOpenRateLimiter limiter = new FailOpenRateLimiter(delegate, registry);

        RateLimiter.Response response = limiter.isAllowed("community-service", "k").block();

        assertThat(response).isNotNull();
        assertThat(response.isAllowed()).isTrue();
        assertThat(response.getHeaders()).containsEntry(RedisRateLimiter.REMAINING_HEADER, "42");
        assertThat(registry.counter(FailOpenRateLimiter.METRIC_REDIS_UNAVAILABLE).count())
                .isEqualTo(0.0);
    }

    @Test
    void passesThroughDeniedResponseFromDelegate() {
        RedisRateLimiter delegate = mock(RedisRateLimiter.class);
        RateLimiter.Response denied = new RateLimiter.Response(false,
                java.util.Map.of(RedisRateLimiter.REMAINING_HEADER, "0"));
        when(delegate.isAllowed("community-service", "k"))
                .thenReturn(Mono.just(denied));

        MeterRegistry registry = new SimpleMeterRegistry();
        FailOpenRateLimiter limiter = new FailOpenRateLimiter(delegate, registry);

        RateLimiter.Response response = limiter.isAllowed("community-service", "k").block();

        assertThat(response).isNotNull();
        assertThat(response.isAllowed()).isFalse();
    }
}
