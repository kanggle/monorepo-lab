# TASK-INT-009 — Fix issues found in TASK-INT-008 review

## Goal

Address code quality and documentation issues identified during the review of
TASK-INT-008 (`GatewayMasterE2ETest.java` burst parallelisation):

1. Add WARN-level logging to the silent `catch (Exception ignored)` block in
   `burstFrom250RequestsTripsRateLimiter()` (violation of
   `platform/coding-rules.md` § Logging — "Do not swallow exceptions silently").
2. Rename the test method from `burstFrom250RequestsTripsRateLimiter()` to
   `burstFrom800RequestsTripsRateLimiter()` to match the actual request count
   (800) used in the implementation.
3. Update the class-level JavaDoc in `GatewayMasterE2ETest` — line 39 still
   reads "120-request burst", a stale reference from a previous revision.
4. Replace the two inline `new java.util.Random()` instantiations in
   `burstIp` generation with `ThreadLocalRandom.current()` to avoid
   potential same-seed collisions on fast JVMs.

## Scope

**Single file: `GatewayMasterE2ETest.java`**

- `catch (Exception ignored)` → add `log.warn("burst request failed", e)` (SLF4J).
- Method rename: `burstFrom250RequestsTripsRateLimiter` → `burstFrom800RequestsTripsRateLimiter`.
- JavaDoc Javadoc fix: update line 39 "120-request burst" → "800-request burst".
- `new java.util.Random()` (×2) → `ThreadLocalRandom.current()`.

No logic changes to assertions, request count, or executor pattern.

## Acceptance Criteria

1. `catch (Exception e)` block logs at WARN with `log.warn("burst request error ...", e)`
   using a class-level `private static final Logger log = LoggerFactory.getLogger(...)`.
2. Method is renamed to `burstFrom800RequestsTripsRateLimiter()`.
3. Class JavaDoc no longer contains "120-request burst"; replaced with
   "800-request burst".
4. `burstIp` uses `ThreadLocalRandom.current().nextInt(...)` instead of
   `new java.util.Random().nextInt(...)`.
5. `./gradlew :projects:wms-platform:apps:gateway-service:test` passes.
6. No change to assertions (`ok >= 190`, `rateLimited >= 40`, `retryAfterValues` not empty).
7. No change to request count (800) or executor pattern.

## Related Specs

- `projects/wms-platform/specs/services/gateway-service/architecture.md`
- `platform/coding-rules.md` § Logging
- `platform/naming-conventions.md`

## Related Contracts

None.

## Edge Cases

- Logger field must be `private static final` to satisfy `platform/coding-rules.md`
  SLF4J convention; do not use instance-level logger.
- `ThreadLocalRandom.current().nextInt(150)` and `.nextInt(250)` — use the same
  upper-bound values as the original `Random` calls to preserve the IP range.

## Failure Scenarios

- **Logger import missing**: `import org.slf4j.Logger; import org.slf4j.LoggerFactory;`
  required — SLF4J is on the test classpath transitively via Spring Boot test starter.
- **Method rename breaks CI test filter**: if CI uses `--tests` glob with the old
  method name, update the CI workflow grep/filter pattern to match the new name.
  Check `.github/workflows/` for any hardcoded test method names referencing
  `burstFrom250`.
