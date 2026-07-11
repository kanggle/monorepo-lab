package com.example.apigateway.ratelimit;

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
 *   <li>Redis-class failures (connection refusal, timeout, Lettuce/Spring wrappers,
 *       and Redis errors buried in a cause chain) fail open with the sentinel header
 *       and increment {@code gateway_ratelimit_redis_unavailable_total}.</li>
 *   <li>Everything else propagates (does NOT fail open) and increments
 *       {@code gateway_ratelimit_unexpected_error_total} so ops keeps visibility.</li>
 * </ol>
 *
 * <p>This suite is the <strong>union</strong> of what wms and scm each tested before the
 * extraction. Their sets had drifted apart — scm covered {@code IllegalStateException}
 * propagation and a bare connection failure; wms covered {@code NullPointerException}
 * propagation and the self-referencing-cause loop guard. Taking either copy alone would
 * have silently dropped the other's coverage, which is the same failure mode
 * (ADR-MONO-048 § 1.3) this library exists to end.
 *
 * <p>Note what is <em>absent</em>: wms once had a test named
 * {@code failsOpenOnAnyReactiveError} asserting that a plain {@code RuntimeException}
 * fails open — the defect written down as the contract, which is why its suite could
 * never object to the bug. {@link #propagatesUnknownErrorsAndIncrementsUnexpectedCounter}
 * is that same scenario with the correct expectation.
 */
class FailOpenRateLimiterTest {

    private static final String ROUTE = "some-service";

    @Test
    void failsOpenWhenDelegateEmitsRedisConnectionFailureAndIncrementsMetric() {
        RedisRateLimiter delegate = mock(RedisRateLimiter.class);
        when(delegate.isAllowed(ROUTE, "203.0.113.1:some-service"))
                .thenReturn(Mono.error(new RedisConnectionFailureException("redis down")));

        MeterRegistry registry = new SimpleMeterRegistry();
        FailOpenRateLimiter limiter = new FailOpenRateLimiter(delegate, registry);

        StepVerifier.create(limiter.isAllowed(ROUTE, "203.0.113.1:some-service"))
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
        when(delegate.isAllowed(ROUTE, "k"))
                .thenReturn(Mono.error(new RedisConnectionFailureException("connection refused")));

        MeterRegistry registry = new SimpleMeterRegistry();
        FailOpenRateLimiter limiter = new FailOpenRateLimiter(delegate, registry);

        RateLimiter.Response response = limiter.isAllowed(ROUTE, "k").block();

        assertThat(response).isNotNull();
        assertThat(response.isAllowed()).isTrue();
        assertThat(response.getHeaders()).containsEntry(RedisRateLimiter.REMAINING_HEADER, "-1");
        assertThat(registry.counter(FailOpenRateLimiter.METRIC_REDIS_UNAVAILABLE).count())
                .isEqualTo(1.0);
    }

    @Test
    void failsOpenOnQueryTimeout() {
        RedisRateLimiter delegate = mock(RedisRateLimiter.class);
        when(delegate.isAllowed(ROUTE, "k"))
                .thenReturn(Mono.error(new QueryTimeoutException("redis command timed out")));

        MeterRegistry registry = new SimpleMeterRegistry();
        FailOpenRateLimiter limiter = new FailOpenRateLimiter(delegate, registry);

        RateLimiter.Response response = limiter.isAllowed(ROUTE, "k").block();

        assertThat(response).isNotNull();
        assertThat(response.isAllowed()).isTrue();
        assertThat(registry.counter(FailOpenRateLimiter.METRIC_REDIS_UNAVAILABLE).count())
                .isEqualTo(1.0);
    }

    @Test
    void failsOpenOnLettuceRedisException() {
        RedisRateLimiter delegate = mock(RedisRateLimiter.class);
        when(delegate.isAllowed(ROUTE, "k"))
                .thenReturn(Mono.error(new RedisException("MOVED slot")));

        MeterRegistry registry = new SimpleMeterRegistry();
        FailOpenRateLimiter limiter = new FailOpenRateLimiter(delegate, registry);

        RateLimiter.Response response = limiter.isAllowed(ROUTE, "k").block();

        assertThat(response).isNotNull();
        assertThat(response.isAllowed()).isTrue();
        assertThat(registry.counter(FailOpenRateLimiter.METRIC_REDIS_UNAVAILABLE).count())
                .isEqualTo(1.0);
    }

    @Test
    void failsOpenOnRedisSystemExceptionWrappingLettuce() {
        // Spring frequently re-wraps Lettuce errors in RedisSystemException; the cause
        // chain check must still recognise the failure. A predicate that only inspected
        // the top frame would wrongly propagate a genuine Redis outage as a 5xx.
        RedisRateLimiter delegate = mock(RedisRateLimiter.class);
        RedisSystemException wrapped = new RedisSystemException(
                "wrapped", new RedisException("low-level redis failure"));
        when(delegate.isAllowed(ROUTE, "k")).thenReturn(Mono.error(wrapped));

        MeterRegistry registry = new SimpleMeterRegistry();
        FailOpenRateLimiter limiter = new FailOpenRateLimiter(delegate, registry);

        RateLimiter.Response response = limiter.isAllowed(ROUTE, "k").block();

        assertThat(response).isNotNull();
        assertThat(response.isAllowed()).isTrue();
        assertThat(registry.counter(FailOpenRateLimiter.METRIC_REDIS_UNAVAILABLE).count())
                .isEqualTo(1.0);
    }

    @Test
    void propagatesUnknownErrorsAndIncrementsUnexpectedCounter() {
        // The regression this class exists to prevent. A non-Redis error (programming
        // bug, malformed Lua reply parser, NPE) MUST NOT be masked as "redis down":
        // masking it silently disables rate limiting for the whole edge while the log
        // blames Redis. Propagate so SCG translates to 5xx and observability sees it.
        RedisRateLimiter delegate = mock(RedisRateLimiter.class);
        when(delegate.isAllowed(ROUTE, "10.0.0.1:some-service"))
                .thenReturn(Mono.error(new RuntimeException("not redis: programming bug")));

        MeterRegistry registry = new SimpleMeterRegistry();
        FailOpenRateLimiter limiter = new FailOpenRateLimiter(delegate, registry);

        assertThatThrownBy(() -> limiter.isAllowed(ROUTE, "10.0.0.1:some-service").block())
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
        when(delegate.isAllowed(ROUTE, "k"))
                .thenReturn(Mono.error(new IllegalStateException("limiter not configured")));

        MeterRegistry registry = new SimpleMeterRegistry();
        FailOpenRateLimiter limiter = new FailOpenRateLimiter(delegate, registry);

        StepVerifier.create(limiter.isAllowed(ROUTE, "k"))
                .expectErrorMatches(t -> t instanceof IllegalStateException
                        && "limiter not configured".equals(t.getMessage()))
                .verify();

        assertThat(registry.counter(FailOpenRateLimiter.METRIC_REDIS_UNAVAILABLE).count())
                .isEqualTo(0.0);
        assertThat(registry.counter(FailOpenRateLimiter.METRIC_UNEXPECTED_ERROR).count())
                .isEqualTo(1.0);
    }

    @Test
    void propagatesNullPointerException() {
        RedisRateLimiter delegate = mock(RedisRateLimiter.class);
        when(delegate.isAllowed(ROUTE, "k"))
                .thenReturn(Mono.error(new NullPointerException("keyResolver returned null")));

        MeterRegistry registry = new SimpleMeterRegistry();
        FailOpenRateLimiter limiter = new FailOpenRateLimiter(delegate, registry);

        StepVerifier.create(limiter.isAllowed(ROUTE, "k"))
                .expectErrorMatches(t -> t instanceof NullPointerException
                        && "keyResolver returned null".equals(t.getMessage()))
                .verify();

        assertThat(registry.counter(FailOpenRateLimiter.METRIC_REDIS_UNAVAILABLE).count())
                .isEqualTo(0.0);
        assertThat(registry.counter(FailOpenRateLimiter.METRIC_UNEXPECTED_ERROR).count())
                .isEqualTo(1.0);
    }

    @Test
    void isRedisFailureTerminatesOnSelfReferencingCause() {
        // A throwable whose cause is itself must not spin the cause-chain walk forever.
        SelfCausingException loop = new SelfCausingException();

        assertThat(FailOpenRateLimiter.isRedisFailure(loop)).isFalse();
        assertThat(FailOpenRateLimiter.isRedisFailure(null)).isFalse();
    }

    @Test
    void passesThroughAllowedResponseFromDelegateWithoutIncrementingMetric() {
        RedisRateLimiter delegate = mock(RedisRateLimiter.class);
        RateLimiter.Response allowed = new RateLimiter.Response(true,
                java.util.Map.of(RedisRateLimiter.REMAINING_HEADER, "42"));
        when(delegate.isAllowed(ROUTE, "198.51.100.5:some-service"))
                .thenReturn(Mono.just(allowed));

        MeterRegistry registry = new SimpleMeterRegistry();
        FailOpenRateLimiter limiter = new FailOpenRateLimiter(delegate, registry);

        RateLimiter.Response response = limiter.isAllowed(ROUTE, "198.51.100.5:some-service").block();

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
        when(delegate.isAllowed(ROUTE, "198.51.100.6:some-service"))
                .thenReturn(Mono.just(denied));

        MeterRegistry registry = new SimpleMeterRegistry();
        FailOpenRateLimiter limiter = new FailOpenRateLimiter(delegate, registry);

        RateLimiter.Response response = limiter.isAllowed(ROUTE, "198.51.100.6:some-service").block();

        assertThat(response).isNotNull();
        assertThat(response.isAllowed()).isFalse();
    }

    /** Cause chain that points back at itself — the loop guard's target. */
    private static final class SelfCausingException extends RuntimeException {
        @Override
        public synchronized Throwable getCause() {
            return this;
        }
    }
}
