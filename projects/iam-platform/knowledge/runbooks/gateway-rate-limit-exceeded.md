# Runbook — GatewayRateLimitExceeded

Assumptions: [`README.md`](README.md) § Environment.

## 1. What it means

`rate(gateway_ratelimit_rejected_total[5m]) > 10` for 5 minutes, severity `warning` — more than 10
HTTP 429 per second **for one `scope` label** (the expression has no `sum`, so each scope is judged
separately).

Metric: counter `gateway_ratelimit_rejected_total{scope}`, exported by
`gateway-service/.../filter/RateLimitFilter.java` (`recordRejectedCounter`), incremented each time
`writeRateLimitResponse` answers `429 RATE_LIMITED` with a `Retry-After` header. Its sibling
`gateway_ratelimit_total{scope,result}` counts allowed and rejected.

Scopes and limits (`gateway-service/.../application.yml`, `gateway.rate-limit`, literal values — no
environment override exists for the numbers):

| scope | limit | bucket key `rate:<scope>:<identifier>` |
|---|---|---|
| `login` | 20 / 60 s | `login:<tenant>:<client /24 subnet>` |
| `signup` | 5 / 60 s | `signup:<tenant>:<client IP>` |
| `refresh` | 10 / 60 s | `refresh:<tenant>:<account id from the JWT sub>` (client IP if no JWT) |
| `onboarding` | 5 / 60 s | `onboarding:<client IP>` |
| `global` | 100 / 1 s | `global:<client IP>` |

`<tenant>` is the JWT `tenant_id`, or `anonymous` without a token. The client IP is the first
`X-Forwarded-For` value when present, otherwise the socket address.

## 2. First checks

```bash
# Which scope is being rejected, and how fast
docker exec iam-prometheus wget -qO- --post-data='query=rate(gateway_ratelimit_rejected_total[5m])' http://localhost:9090/api/v1/query

# Which buckets exist right now (one per client), and one bucket's count and remaining TTL
docker exec iam-redis redis-cli -a redispass --no-auth-warning --scan --pattern 'rate:login:*'
docker exec iam-redis redis-cli -a redispass --no-auth-warning --scan --pattern 'rate:global:*'
```

Take a key from the scan output and read it (replace nothing else):
`docker exec iam-redis redis-cli -a redispass --no-auth-warning GET <key-from-scan>` and `TTL <key-from-scan>`.
The counter is `INCR`-ed per request and expires `window + 10 s` after its first request
(`TokenBucketRateLimiter.java:59-60`).

## 3. Likely causes and how to confirm each

| Cause | Confirm |
|---|---|
| One client / one NAT'd office hitting a limit | Only a few keys in the scan have a count above the limit; the `login` key's identifier is a /24, so a whole subnet shares 20 logins a minute |
| All traffic arrives through one proxy address | The `global:` scan shows a single key whose count is at 100+ per second — a load balancer in front of the gateway without a forwarded client address |
| Credential stuffing or signup abuse | `scope="login"` / `"signup"` dominates, many keys over the limit, and `auth-login-failure-spike.md` / `gateway-jwt-failure-spike.md` are moving too |
| A client retry loop (a broken app) | One `refresh:` key (one account) is far over 10 |
| Redis unavailable | Not a cause of 429: with `RATE_LIMIT_FAIL_OPEN` (default `true`, `application.yml:127`) the filter **allows** and logs `Rate limit check failed, failing open`. If it was set to `false`, every request is rejected while Redis is down — check `iam-redis` in `docker compose -f projects/iam-platform/docker-compose.yml ps` |

## 4. Recover / mitigate

- Legitimate client over the limit: wait — the bucket expires by itself (`window + 10 s`). To release
  one client immediately: `docker exec iam-redis redis-cli -a redispass --no-auth-warning DEL <key-from-scan>`.
- Limits too low for real traffic: change the literal numbers in `application.yml` and restart the
  gateway. That is a product decision — get it approved before editing.
- Attack: the filter already answers 429 without reaching upstream; blocking an address at the
  network edge is **not verified** (no such configuration in this repository).
- Redis failing closed: restore `iam-redis`, or restart the gateway with `RATE_LIMIT_FAIL_OPEN=true`.
- Confirm: the first query is under 10 for every scope for 5 minutes.

## 5. Escalate

- A sustained attack, or a limit the product owner must decide on: escalate to security / the
  gateway owner with the scope, the top keys and the rate.
