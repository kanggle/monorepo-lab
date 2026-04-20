package com.wms.gateway.ratelimit;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import reactor.core.publisher.Mono;

/**
 * Decorator around Spring Cloud Gateway's {@link RedisRateLimiter} that implements the
 * fail-open policy mandated by {@code platform/api-gateway-policy.md} and
 * {@code specs/services/gateway-service/architecture.md}.
 * <p>
 * SCG's stock {@code RedisRateLimiter} fails <strong>closed</strong> when Redis is
 * unreachable — any reactive error from the Lua-backed token-bucket surface would bubble
 * up through the filter and translate to a 5xx. For a gateway, rate limiting is a soft
 * protection: losing the counter store must not take the whole edge offline. This
 * decorator intercepts reactive errors from the delegate and returns an allowed
 * {@link Response} with {@code X-RateLimit-Remaining: -1} (sentinel meaning "not
 * enforced").
 * <p>
 * Configuration binding (the {@code spring.cloud.gateway.filter.request-rate-limiter}
 * {@link org.springframework.cloud.gateway.event.FilterArgsEvent FilterArgsEvent}
 * machinery) is delegated to the wrapped {@link RedisRateLimiter}, which remains the
 * bean that listens for the event. This decorator only wraps {@code isAllowed}.
 */
public class FailOpenRateLimiter implements RateLimiter<RedisRateLimiter.Config> {

    private static final Logger log = LoggerFactory.getLogger(FailOpenRateLimiter.class);

    private final RedisRateLimiter delegate;

    public FailOpenRateLimiter(RedisRateLimiter delegate) {
        this.delegate = delegate;
    }

    @Override
    public Mono<Response> isAllowed(String routeId, String id) {
        return delegate.isAllowed(routeId, id)
                .onErrorResume(err -> {
                    log.warn("Rate-limit backing store failed; failing open for routeId='{}' id='{}': {}",
                            routeId, id, err.toString());
                    return Mono.just(new Response(true, Map.of(RedisRateLimiter.REMAINING_HEADER, "-1")));
                });
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
