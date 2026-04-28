# TASK-INT-008 — E2E rate-limit scenario: parallelise burst requests

## Goal

`E2E (gateway-master live-pair, Testcontainers)` times out at 30 minutes on every CI run.
Root cause: Scenario 3 (`burstFrom250RequestsTripsRateLimiter`) fires 250 HTTP requests
**sequentially**. In CI, 5 running Docker containers cause gateway latency of ~3–5 s per
request → `250 × 4s ≈ 17 min` for Scenario 3 alone. Adding ~10 min container startup,
the job consistently exceeds `timeout-minutes: 30`.

TASK-INT-007 fixed the previous timeout (20 → 30 min + artifact reuse) but explicitly
deferred parallelisation. This task completes that work.

## Scope

**Single file: `GatewayMasterE2ETest.java`**

Replace the sequential `for` loop in `RateLimit.burstFrom250RequestsTripsRateLimiter()`
with a concurrent submission using `Executors.newVirtualThreadPerTaskExecutor()` (Java 21).
All 250 requests fire nearly simultaneously; the Redis token-bucket rate limiter sees them
at once, drains the bucket (burstCapacity=200), and returns ~50 × 429. Wall-clock time
for the scenario drops from ~17 min to ~2–5 s.

**Not in scope:**
- CI `timeout-minutes` — the existing 30 min budget is sufficient after the fix.
- Other E2E scenarios.
- `E2EBase.java` infrastructure.

## Acceptance Criteria

1. `burstFrom250RequestsTripsRateLimiter()` submits all 250 requests to a
   `newVirtualThreadPerTaskExecutor()` and calls `Future.get()` on each before assertions.
2. `retryAfterValues` list is thread-safe (`Collections.synchronizedList` or
   `CopyOnWriteArrayList`).
3. Existing numeric assertions unchanged: `ok >= 190`, `rateLimited >= 40`,
   `retryAfterValues` not empty.
4. Local `./gradlew :projects:wms-platform:apps:gateway-service:test` passes (unit + slice
   tests unaffected; e2eTest is skipped without Docker).
5. CI `E2E (gateway-master live-pair, Testcontainers)` passes (or at minimum completes
   within 30 min — previously the job never completed before timeout).

## Related Specs

- `projects/wms-platform/specs/services/gateway-service/` (gateway routing + rate limit)

## Related Contracts

None.

## Edge Cases

- **Token replenishment between concurrent requests**: with all 250 in-flight at once,
  replenishment during the burst window is negligible (~2 s × 100 tokens/s = 200 tokens
  max). Starting bucket is 200. Net result: ~200 pass, ~50 fail — within assertion bounds.
- **HttpClient thread-safety**: `java.net.http.HttpClient` is thread-safe; concurrent
  `send()` calls from virtual threads are fine on a single shared instance.
- **ExecutorService lifecycle**: use `try-with-resources` (`AutoCloseable` since Java 19)
  so the executor shuts down cleanly even if a future throws.

## Failure Scenarios

- **`rateLimited` remains 0 after parallelisation**: rate limiter config broken or Redis
  not reachable from gateway container. Check `REDIS_HOST` env on gateway container in
  `E2EBase`.
- **`ok < 190`**: rate limiter too aggressive; check `replenishRate`/`burstCapacity` in
  gateway-service `application.yml`.
- **Future throws `ExecutionException`**: individual request timed out or connection
  refused. Add logging in the catch block to surface the root cause.
