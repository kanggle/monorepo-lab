package com.wms.gateway.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.data.redis.RedisConnectionFailureException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Exercises the fail-open decorator: when the underlying Redis-backed limiter emits a
 * reactive error (simulating a Redis outage), the decorator must return
 * {@code isAllowed = true} with a sentinel remaining header — never let the error
 * propagate. See {@code platform/api-gateway-policy.md} §Rate Limiting.
 */
class FailOpenRateLimiterTest {

    @Test
    void failsOpenWhenDelegateEmitsRedisConnectionFailure() {
        RedisRateLimiter delegate = mock(RedisRateLimiter.class);
        when(delegate.isAllowed("master-service", "203.0.113.1:master-service"))
                .thenReturn(Mono.error(new RedisConnectionFailureException("redis down")));

        FailOpenRateLimiter limiter = new FailOpenRateLimiter(delegate);

        StepVerifier.create(limiter.isAllowed("master-service", "203.0.113.1:master-service"))
                .assertNext(response -> {
                    assertThat(response.isAllowed()).isTrue();
                    assertThat(response.getHeaders())
                            .containsEntry(RedisRateLimiter.REMAINING_HEADER, "-1");
                })
                .verifyComplete();
    }

    @Test
    void failsOpenOnAnyReactiveError() {
        RedisRateLimiter delegate = mock(RedisRateLimiter.class);
        when(delegate.isAllowed("master-service", "10.0.0.1:master-service"))
                .thenReturn(Mono.error(new RuntimeException("unexpected Lua error")));

        FailOpenRateLimiter limiter = new FailOpenRateLimiter(delegate);

        RateLimiter.Response response = limiter.isAllowed("master-service", "10.0.0.1:master-service").block();

        assertThat(response).isNotNull();
        assertThat(response.isAllowed()).isTrue();
        assertThat(response.getHeaders()).containsEntry(RedisRateLimiter.REMAINING_HEADER, "-1");
    }

    @Test
    void passesThroughAllowedResponseFromDelegate() {
        RedisRateLimiter delegate = mock(RedisRateLimiter.class);
        RateLimiter.Response allowed = new RateLimiter.Response(true,
                java.util.Map.of(RedisRateLimiter.REMAINING_HEADER, "42"));
        when(delegate.isAllowed("master-service", "198.51.100.5:master-service"))
                .thenReturn(Mono.just(allowed));

        FailOpenRateLimiter limiter = new FailOpenRateLimiter(delegate);

        RateLimiter.Response response = limiter.isAllowed("master-service", "198.51.100.5:master-service").block();

        assertThat(response).isNotNull();
        assertThat(response.isAllowed()).isTrue();
        assertThat(response.getHeaders()).containsEntry(RedisRateLimiter.REMAINING_HEADER, "42");
    }

    @Test
    void passesThroughDeniedResponseFromDelegate() {
        RedisRateLimiter delegate = mock(RedisRateLimiter.class);
        RateLimiter.Response denied = new RateLimiter.Response(false,
                java.util.Map.of(RedisRateLimiter.REMAINING_HEADER, "0"));
        when(delegate.isAllowed("master-service", "198.51.100.6:master-service"))
                .thenReturn(Mono.just(denied));

        FailOpenRateLimiter limiter = new FailOpenRateLimiter(delegate);

        RateLimiter.Response response = limiter.isAllowed("master-service", "198.51.100.6:master-service").block();

        assertThat(response).isNotNull();
        assertThat(response.isAllowed()).isFalse();
    }
}
