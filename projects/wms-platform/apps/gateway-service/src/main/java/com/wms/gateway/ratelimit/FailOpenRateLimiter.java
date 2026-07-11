package com.wms.gateway.ratelimit;

import io.lettuce.core.RedisException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;
import reactor.core.publisher.Mono;

/**
 * Decorator around Spring Cloud Gateway's {@link RedisRateLimiter} that implements the
 * fail-open policy mandated by {@code platform/api-gateway-policy.md} and
 * {@code specs/services/gateway-service/architecture.md}.
 *
 * <p>SCG's stock {@code RedisRateLimiter} fails <strong>closed</strong> when Redis is
 * unreachable — any reactive error from the Lua-backed token-bucket surface would bubble
 * up through the filter and translate to a 5xx. For a gateway, rate limiting is a soft
 * protection: losing the counter store must not take the whole edge offline. This
 * decorator intercepts <em>Redis-specific</em> reactive errors from the delegate,
 * increments the {@code gateway_ratelimit_redis_unavailable_total} counter, and returns
 * an allowed {@link Response} with {@code X-RateLimit-Remaining: -1} (sentinel meaning
 * "not enforced").
 *
 * <p><strong>Narrowing (TASK-BE-502):</strong> only Redis-class failures fail open.
 * Programming errors (NPE, ClassCast, malformed Lua arguments, etc.) propagate up so they
 * surface as 5xx and reach observability, instead of being silently swallowed under the
 * "Redis is down" banner. Catching every {@code Throwable} — as this class did until
 * TASK-BE-502 — is doubly harmful: a single bug anywhere in the limiter chain turns wms
 * into a gateway with <em>no rate limiting at all</em>, while the log line blames Redis
 * and sends everyone looking in the wrong place. Non-Redis errors increment the separate
 * {@code gateway_ratelimit_unexpected_error_total} counter for ops visibility before
 * propagating.
 *
 * <p>Configuration binding (the
 * {@code spring.cloud.gateway.filter.request-rate-limiter}
 * {@link org.springframework.cloud.gateway.event.FilterArgsEvent FilterArgsEvent}
 * machinery) is delegated to the wrapped {@link RedisRateLimiter}, which remains the bean
 * that listens for the event. This decorator only wraps {@code isAllowed}.
 */
public class FailOpenRateLimiter implements RateLimiter<RedisRateLimiter.Config> {

    private static final Logger log = LoggerFactory.getLogger(FailOpenRateLimiter.class);
    public static final String METRIC_REDIS_UNAVAILABLE = "gateway_ratelimit_redis_unavailable_total";
    public static final String METRIC_UNEXPECTED_ERROR = "gateway_ratelimit_unexpected_error_total";

    private final RedisRateLimiter delegate;
    private final Counter redisUnavailableCounter;
    private final Counter unexpectedErrorCounter;

    public FailOpenRateLimiter(RedisRateLimiter delegate, MeterRegistry meterRegistry) {
        this.delegate = delegate;
        this.redisUnavailableCounter = Counter.builder(METRIC_REDIS_UNAVAILABLE)
                .description("Gateway rate-limit Redis backend unavailable; failed open.")
                .register(meterRegistry);
        this.unexpectedErrorCounter = Counter.builder(METRIC_UNEXPECTED_ERROR)
                .description("Gateway rate-limit non-Redis error; propagated (not failed open).")
                .register(meterRegistry);
    }

    @Override
    public Mono<Response> isAllowed(String routeId, String id) {
        return delegate.isAllowed(routeId, id)
                .onErrorResume(FailOpenRateLimiter::isRedisFailure, err -> {
                    log.warn("Rate-limit Redis backend unavailable; failing open for routeId='{}' id='{}': {}",
                            routeId, id, err.toString());
                    redisUnavailableCounter.increment();
                    return Mono.just(new Response(true, Map.of(RedisRateLimiter.REMAINING_HEADER, "-1")));
                })
                .doOnError(err -> {
                    // Non-Redis errors bubble up; record them for ops visibility so an
                    // unexpected programming bug in the limiter chain doesn't go silent.
                    log.error("Rate-limit non-Redis error for routeId='{}' id='{}'; propagating: {}",
                            routeId, id, err.toString());
                    unexpectedErrorCounter.increment();
                });
    }

    /**
     * Recognises the Redis-class failure modes that warrant failing open. Anything
     * outside this set is a programming error and must propagate.
     */
    static boolean isRedisFailure(Throwable t) {
        if (t == null) {
            return false;
        }
        for (Throwable cur = t; cur != null; cur = cur.getCause()) {
            if (cur instanceof RedisConnectionFailureException
                    || cur instanceof QueryTimeoutException
                    || cur instanceof RedisSystemException
                    || cur instanceof RedisException) {
                return true;
            }
            if (cur == cur.getCause()) {
                break;
            }
        }
        return false;
    }

    @Override
    public Map<String, RedisRateLimiter.Config> getConfig() {
        return delegate.getConfig();
    }

    @Override
    public Class<RedisRateLimiter.Config> getConfigClass() {
        return delegate.getConfigClass();
    }

    @Override
    public RedisRateLimiter.Config newConfig() {
        return delegate.newConfig();
    }
}
