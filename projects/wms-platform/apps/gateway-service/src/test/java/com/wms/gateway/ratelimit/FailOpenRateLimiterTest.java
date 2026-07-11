package com.wms.gateway.ratelimit;

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
 * Exercises the fail-open decorator. The contract is intentionally narrowed (TASK-BE-502):
 *
 * <ol>
 *   <li>Redis-class failures (connection refusal, timeout, Lettuce/Spring wrappers)
 *       fail open with the sentinel header and increment
 *       {@code gateway_ratelimit_redis_unavailable_total}.</li>
 *   <li>Other reactive errors propagate (do NOT fail open) and increment
 *       {@code gateway_ratelimit_unexpected_error_total} so ops still has visibility.</li>
 * </ol>
 *
 * <p>Until TASK-BE-502 this suite contained a test named {@code failsOpenOnAnyReactiveError}
 * which asserted the <em>opposite</em> of (2): it pinned "a plain {@code RuntimeException}
 * fails open" as the intended contract. That is how the defect survived — the bug was
 * written down as the specification, so nothing in the suite could ever object to it. The
 * replacement below (`propagatesUnknownErrorsAndIncrementsUnexpectedCounter`) is the same
 * scenario with the correct expectation.
 *
 * <p>See {@code platform/api-gateway-policy.md} § Rate Limiting.
 */
class FailOpenRateLimiterTest {

    @Test
    void failsOpenWhenDelegateEmitsRedisConnectionFailureAndIncrementsMetric() {
        RedisRateLimiter delegate = mock(RedisRateLimiter.class);
        when(delegate.isAllowed("master-service", "203.0.113.1:master-service"))
                .thenReturn(Mono.error(new RedisConnectionFailureException("redis down")));

        MeterRegistry registry = new SimpleMeterRegistry();
        FailOpenRateLimiter limiter = new FailOpenRateLimiter(delegate, registry);

        StepVerifier.create(limiter.isAllowed("master-service", "203.0.113.1:master-service"))
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
    void failsOpenOnQueryTimeout() {
        RedisRateLimiter delegate = mock(RedisRateLimiter.class);
        when(delegate.isAllowed("master-service", "k"))
                .thenReturn(Mono.error(new QueryTimeoutException("redis command timed out")));

        MeterRegistry registry = new SimpleMeterRegistry();
        FailOpenRateLimiter limiter = new FailOpenRateLimiter(delegate, registry);

        RateLimiter.Response response = limiter.isAllowed("master-service", "k").block();

        assertThat(response).isNotNull();
        assertThat(response.isAllowed()).isTrue();
        assertThat(registry.counter(FailOpenRateLimiter.METRIC_REDIS_UNAVAILABLE).count())
                .isEqualTo(1.0);
    }

    @Test
    void failsOpenOnLettuceRedisException() {
        RedisRateLimiter delegate = mock(RedisRateLimiter.class);
        when(delegate.isAllowed("master-service", "k"))
                .thenReturn(Mono.error(new RedisException("MOVED slot")));

        MeterRegistry registry = new SimpleMeterRegistry();
        FailOpenRateLimiter limiter = new FailOpenRateLimiter(delegate, registry);

        RateLimiter.Response response = limiter.isAllowed("master-service", "k").block();

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
        when(delegate.isAllowed("master-service", "k")).thenReturn(Mono.error(wrapped));

        MeterRegistry registry = new SimpleMeterRegistry();
        FailOpenRateLimiter limiter = new FailOpenRateLimiter(delegate, registry);

        RateLimiter.Response response = limiter.isAllowed("master-service", "k").block();

        assertThat(response).isNotNull();
        assertThat(response.isAllowed()).isTrue();
        assertThat(registry.counter(FailOpenRateLimiter.METRIC_REDIS_UNAVAILABLE).count())
                .isEqualTo(1.0);
    }

    @Test
    void propagatesUnknownErrorsAndIncrementsUnexpectedCounter() {
        // The regression that motivated TASK-BE-502. A non-Redis error (programming bug,
        // malformed Lua reply parser, NPE) MUST NOT be masked as "redis down": masking it
        // silently disables rate limiting for the whole edge while the log blames Redis.
        // Propagate so SCG translates to 5xx and observability picks it up.
        RedisRateLimiter delegate = mock(RedisRateLimiter.class);
        when(delegate.isAllowed("master-service", "10.0.0.1:master-service"))
                .thenReturn(Mono.error(new RuntimeException("unexpected Lua error")));

        MeterRegistry registry = new SimpleMeterRegistry();
        FailOpenRateLimiter limiter = new FailOpenRateLimiter(delegate, registry);

        assertThatThrownBy(() -> limiter.isAllowed("master-service", "10.0.0.1:master-service").block())
                .isInstanceOf(RuntimeException.class)
                .hasMessage("unexpected Lua error");

        assertThat(registry.counter(FailOpenRateLimiter.METRIC_REDIS_UNAVAILABLE).count())
                .isEqualTo(0.0);
        assertThat(registry.counter(FailOpenRateLimiter.METRIC_UNEXPECTED_ERROR).count())
                .isEqualTo(1.0);
    }

    @Test
    void propagatesNullPointerException() {
        RedisRateLimiter delegate = mock(RedisRateLimiter.class);
        when(delegate.isAllowed("master-service", "k"))
                .thenReturn(Mono.error(new NullPointerException("keyResolver returned null")));

        MeterRegistry registry = new SimpleMeterRegistry();
        FailOpenRateLimiter limiter = new FailOpenRateLimiter(delegate, registry);

        StepVerifier.create(limiter.isAllowed("master-service", "k"))
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
        when(delegate.isAllowed("master-service", "198.51.100.5:master-service"))
                .thenReturn(Mono.just(allowed));

        MeterRegistry registry = new SimpleMeterRegistry();
        FailOpenRateLimiter limiter = new FailOpenRateLimiter(delegate, registry);

        RateLimiter.Response response =
                limiter.isAllowed("master-service", "198.51.100.5:master-service").block();

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
        when(delegate.isAllowed("master-service", "198.51.100.6:master-service"))
                .thenReturn(Mono.just(denied));

        MeterRegistry registry = new SimpleMeterRegistry();
        FailOpenRateLimiter limiter = new FailOpenRateLimiter(delegate, registry);

        RateLimiter.Response response =
                limiter.isAllowed("master-service", "198.51.100.6:master-service").block();

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
