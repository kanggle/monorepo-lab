---
name: rate-limiting
description: Redis fixed-window rate limiting with Lua scripts
category: backend
---

# Skill: Rate Limiting

Patterns for Redis-based rate limiting in backend services.

Prerequisite: read `specs/platform/security-rules.md` and `specs/platform/error-handling.md` before using this skill.

---

## Rate Limiter Interface

```java
// domain/service/RateLimiter.java
public interface RateLimiter {
    boolean isRateLimited(String clientKey, int maxRequests, long windowSeconds);
}
```

Returns `true` if the request should be **denied**.

---

## Redis Fixed-Window Implementation

Atomic INCR + EXPIRE using Lua script.

```java
@Slf4j
@Component
@Profile("!standalone")
public class RedisRateLimiter implements RateLimiter {

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

    @Override
    public boolean isRateLimited(String clientKey, int maxRequests, long windowSeconds) {
        try {
            Long count = redisTemplate.execute(RATE_LIMIT_SCRIPT,
                List.of(keyPrefix + clientKey), String.valueOf(windowSeconds));
            return count != null && count > maxRequests;
        } catch (DataAccessException e) {
            log.error("Rate limit check failed, failing open: clientKey={}", clientKey, e);
            return false; // fail-open
        }
    }
}
```

---

## Servlet Filter

Path-specific rate limits configured via properties.

```java
@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private record PathLimit(int maxRequests, long windowSeconds) {}

    private final Map<String, PathLimit> pathLimits;

    // Configured from application.yml:
    // /api/auth/login   → 20 req / 60s
    // /api/auth/signup  → 10 req / 3600s
    // /api/auth/refresh → 30 req / 60s

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        PathLimit limit = pathLimits.get(request.getRequestURI());
        if (limit != null) {
            String clientKey = clientIpResolver.resolve(request) + ":" + request.getRequestURI();
            if (rateLimiter.isRateLimited(clientKey, limit.maxRequests(), limit.windowSeconds())) {
                response.setStatus(429);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write(RATE_LIMIT_RESPONSE);
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
```

---

## Configuration

```yaml
app:
  rate-limit:
    login:
      max-requests: 20
      window-seconds: 60
    signup:
      max-requests: 10
      window-seconds: 3600
    refresh:
      max-requests: 30
      window-seconds: 60
```

---

## Fail-Open Strategy

On Redis failure, **allow** the request rather than blocking all traffic.

```java
catch (DataAccessException e) {
    log.error("Rate limit check failed, failing open", e);
    return false; // not rate limited
}
```

---

## Common Pitfalls

| Pitfall | Fix |
|---|---|
| INCR and EXPIRE as separate commands | Use Lua script for atomicity — prevents keys without TTL |
| Failing closed on Redis error | Use fail-open — rate limiter failure should not block service |
| Hardcoded rate limits | Use `@Value` properties for configurability |
| Missing metrics on rate limit hits | Record `rate_limited` metric for observability |
| Client IP not resolved through proxy | Use `ClientIpResolver` that checks `X-Forwarded-For` |
