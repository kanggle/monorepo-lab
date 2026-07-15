---
name: rate-limiting
description: Redis fixed-window rate limiting with Lua scripts; principal-keyed for authenticated traffic
category: backend
---

# Skill: Rate Limiting

Patterns for Redis-based rate limiting in backend services.

**Source of truth:** `platform/api-gateway-policy.md` § Rate Limiting (normative — **what the key
must be**) + `platform/security-rules.md` + `platform/error-handling.md`. The routes a service
exposes (and which of them are pre-auth) come from its `specs/services/<service>/architecture.md`
and its API contract under `specs/contracts/http/`.

---

## The key is the whole decision (TASK-MONO-410)

| Traffic | Key | Why |
|---|---|---|
| **Anonymous / pre-auth** — `login`, `signup` | **client IP** | There is no principal yet. IP is **correct** here — do not "fix" it |
| **Authenticated** — everything else, **including `refresh`** | **`acct:<sub>`** | An authenticated caller is individually identifiable. An IP bucket puts everyone behind one NAT into one limit while an abuser rotating IPs is never throttled |

**`refresh` is authenticated traffic.** It arrives with a refresh token that resolves to a
principal, so it keys on the **account**, not the IP. (`api-gateway-policy.md`'s fleet table
records exactly this: iam keys `ip` for login/signup and `acct:<sub>` for refresh.)

> **A claim your own config pins to a constant is not a bucket.** ecommerce keyed authenticated
> traffic on `tenant_id` — the *same value* for every shopper — producing one global bucket
> wearing the costume of per-tenant isolation (TASK-MONO-368). wms keyed every authenticated
> route by IP (TASK-MONO-370). Both were real incidents, not hypotheticals. **Verify the value
> varies before you key on it.**

> **Do not over-correct.** Removing IP keying from `login`/`signup` means you can no longer
> throttle a credential-stuffing flood. The rule is *"authenticated traffic keys on the
> principal"*, **not** *"IP is forbidden"*.

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

Atomic INCR + EXPIRE using a Lua script.

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

On a **reactive gateway**, do not re-implement this — `libs/java-gateway` ships
`FailOpenRateLimiter`.

---

## Servlet Filter

Path-specific rate limits configured via properties.

```java
@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private record PathLimit(int maxRequests, long windowSeconds) {}

    private final Map<String, PathLimit> pathLimits;

    // Configured from application.yml:
    // /api/auth/login   -> 20 req / 60s   (pre-auth      -> IP key)
    // /api/auth/signup  -> 10 req / 3600s (pre-auth      -> IP key)
    // /api/auth/refresh -> 30 req / 60s   (AUTHENTICATED -> acct:<sub> key)

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        PathLimit limit = pathLimits.get(request.getRequestURI());
        if (limit != null) {
            if (rateLimiter.isRateLimited(rateKey(request), limit.maxRequests(), limit.windowSeconds())) {
                response.setStatus(429);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write(RATE_LIMIT_RESPONSE);
                return;
            }
        }
        chain.doFilter(request, response);
    }

    /**
     * Authenticated traffic keys on the principal; only pre-auth routes key on IP.
     * `refresh` carries a principal — it is NOT pre-auth.
     */
    private String rateKey(HttpServletRequest request) {
        String path = request.getRequestURI();
        String sub = principalResolver.subject(request);   // from the presented token
        if (sub != null && !sub.isBlank()) {
            return "acct:" + sub + ":" + path;
        }
        return clientIpResolver.resolve(request) + ":" + path;   // pre-auth only
    }
}
```

---

## Configuration

```yaml
app:
  rate-limit:
    login:      # pre-auth  -> IP key
      max-requests: 20
      window-seconds: 60
    signup:     # pre-auth  -> IP key
      max-requests: 10
      window-seconds: 3600
    refresh:    # authenticated -> acct:<sub> key
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

A rate limiter that fails **closed** turns a Redis blip into a fleet-wide outage — and the
outage wears the costume of a security control, which is how it survives review.

---

## Common Pitfalls

| Pitfall | Fix |
|---|---|
| **IP-keying authenticated routes (incl. `refresh`)** | Key on `acct:<sub>` — one NAT must not share a bucket (MONO-368 / MONO-370) |
| **Keying on a claim your config pins to a constant** | That is one global bucket. Verify the claim varies before keying on it |
| **"IP is forbidden" over-correction** | Pre-auth routes (`login`/`signup`) *must* key on IP, or a credential-stuffing flood is unthrottled |
| INCR and EXPIRE as separate commands | Use the Lua script for atomicity — prevents keys without a TTL |
| Failing closed on a Redis error | Fail open — a limiter failure must not block the service |
| Hardcoded rate limits | Use `@Value` / config properties |
| Missing metrics on rate-limit hits | Record a `rate_limited` metric |
| Client IP not resolved through the proxy | Use a `ClientIpResolver` that reads `X-Forwarded-For` |
