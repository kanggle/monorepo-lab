package com.example.gateway.ratelimit;

import com.example.gateway.config.EdgeGatewayProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Redis-based token bucket rate limiter using atomic Lua script.
 * Returns the current request count after increment.
 */
@Slf4j
@Component
public class TokenBucketRateLimiter {

    private static final DefaultRedisScript<Long> RATE_LIMIT_SCRIPT;

    static {
        RATE_LIMIT_SCRIPT = new DefaultRedisScript<>();
        RATE_LIMIT_SCRIPT.setScriptText(
                "local current = redis.call('INCR', KEYS[1])\n" +
                "if current == 1 then\n" +
                "    redis.call('EXPIRE', KEYS[1], ARGV[1])\n" +
                "end\n" +
                "return current"
        );
        RATE_LIMIT_SCRIPT.setResultType(Long.class);
    }

    private final ReactiveStringRedisTemplate redisTemplate;
    private final EdgeGatewayProperties properties;

    public TokenBucketRateLimiter(ReactiveStringRedisTemplate redisTemplate,
                                  EdgeGatewayProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    /**
     * Checks if the request should be rate limited.
     *
     * @param scope      the rate limit scope (login, signup, refresh, global)
     * @param identifier the client identifier (IP, account_id, etc.)
     * @return Mono of RateLimitResult indicating whether the request is allowed
     */
    public Mono<RateLimitResult> isAllowed(String scope, String identifier) {
        EdgeGatewayProperties.ScopeLimit limit = getScopeLimit(scope);
        if (limit == null) {
            return Mono.just(RateLimitResult.allowed());
        }

        long windowSeconds = limit.getWindowSeconds();
        int maxRequests = limit.getMaxRequests();
        // Add 10s safety margin to TTL
        long ttlSeconds = windowSeconds + 10;
        String key = String.format("rate:%s:%s", scope, identifier);

        return redisTemplate.execute(RATE_LIMIT_SCRIPT,
                        List.of(key), List.of(String.valueOf(ttlSeconds)))
                .next()
                .map(count -> {
                    if (count > maxRequests) {
                        return RateLimitResult.rejected(windowSeconds);
                    }
                    return RateLimitResult.allowed();
                })
                .onErrorResume(e -> {
                    if (properties.getRateLimit().isFailOpen()) {
                        log.error("Rate limit check failed, failing open: scope={}, id={}", scope, identifier, e);
                        return Mono.just(RateLimitResult.allowed());
                    }
                    log.error("Rate limit check failed, failing closed: scope={}, id={}", scope, identifier, e);
                    return Mono.just(RateLimitResult.rejected(windowSeconds));
                });
    }

    private EdgeGatewayProperties.ScopeLimit getScopeLimit(String scope) {
        EdgeGatewayProperties.RateLimitProperties rl = properties.getRateLimit();
        return switch (scope) {
            case "login" -> rl.getLogin();
            case "signup" -> rl.getSignup();
            case "refresh" -> rl.getRefresh();
            case "global" -> rl.getGlobal();
            default -> null;
        };
    }

    public record RateLimitResult(boolean isAllowed, long retryAfterSeconds) {
        public static RateLimitResult allowed() {
            return new RateLimitResult(true, 0);
        }

        public static RateLimitResult rejected(long retryAfterSeconds) {
            return new RateLimitResult(false, retryAfterSeconds);
        }
    }
}
