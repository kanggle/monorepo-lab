# Task ID

TASK-BE-014

# Title

Fix issues found in TASK-BE-007 (gateway-service bootstrap)

# Status

ready

# Owner

backend

# Task Tags

- code
- api

---

# Goal

Fix issues found in review of TASK-BE-007 (gateway-service bootstrap). The following defects must be resolved:

1. WebClient for JWKS fetch has no connection/read timeout configured — violates `specs/contracts/http/internal/gateway-to-auth.md` (connect 3s, read 5s).
2. `libs/java-observability` is not declared in `build.gradle` — violates `specs/services/gateway-service/architecture.md` Allowed Dependencies and `specs/services/gateway-service/dependencies.md`.
3. `LoggingFilter` is absent — declared as a required file in `specs/services/gateway-service/architecture.md` `filter/` package.
4. `UpstreamHealthIndicator` is absent — declared as a required file in `specs/services/gateway-service/architecture.md` `route/` package.
5. `refresh` rate-limit scope uses client IP as identifier instead of `account_id` — violates `specs/services/gateway-service/redis-keys.md` scope table (`refresh` identifier = `account_id`).
6. All 10 integration tests are skipped via `@EnabledIf("isDockerAvailable")` — acceptance criteria for rate-limit 429, JWKS flow, and spoofing prevention cannot be verified in any environment without Docker. Integration tests must be runnable in CI.

---

# Scope

## In Scope

- `apps/gateway-service/src/main/java/com/example/gateway/config/WebClientConfig.java`: add connect 3s + read 5s timeout to `jwksWebClient` bean
- `apps/gateway-service/build.gradle`: add `implementation project(':libs:java-observability')`
- `apps/gateway-service/src/main/java/com/example/gateway/filter/LoggingFilter.java`: implement `GlobalFilter` that logs method, path, status, latency; uses `java-observability` MDC/metrics
- `apps/gateway-service/src/main/java/com/example/gateway/route/UpstreamHealthIndicator.java`: implement `HealthIndicator` that checks upstream route availability
- `apps/gateway-service/src/main/java/com/example/gateway/filter/RateLimitFilter.java`: change `refresh` scope identifier to extract `account_id` from JWT subject claim (token is available in Authorization header at rate-limit filter time; if absent, fall back to IP with warning)
- `apps/gateway-service/src/test/java/com/example/gateway/integration/GatewayIntegrationTest.java`: remove or refactor `@EnabledIf("isDockerAvailable")` condition so integration tests run in CI (use Testcontainers with `@Testcontainers` — Docker-unavailable failure is a hard CI failure, not a skip)

## Out of Scope

- Any new route or filter beyond what is listed above
- Resilience4j circuit breaker (remains out of scope per TASK-BE-007)

---

# Acceptance Criteria

- [ ] `WebClientConfig` configures connect timeout 3s and read timeout 5s on `jwksWebClient`
- [ ] `build.gradle` includes `implementation project(':libs:java-observability')`
- [ ] `LoggingFilter` exists in `filter/` package, implements `GlobalFilter`, logs method/path/status/latency
- [ ] `UpstreamHealthIndicator` exists in `route/` package, implements `HealthIndicator`
- [ ] `refresh` scope rate-limit identifier is `account_id` extracted from JWT sub claim (falls back to IP if token absent)
- [ ] All integration tests in `GatewayIntegrationTest` execute (not skipped) when Docker is available
- [ ] `./gradlew :apps:gateway-service:test` passes with 0 failures

---

# Related Specs

- `specs/services/gateway-service/architecture.md`
- `specs/services/gateway-service/dependencies.md`
- `specs/services/gateway-service/redis-keys.md`
- `specs/contracts/http/internal/gateway-to-auth.md`

# Related Skills

- `.claude/skills/backend/gateway-security/SKILL.md`
- `.claude/skills/backend/rate-limiting/SKILL.md`

---

# Related Contracts

- `specs/contracts/http/internal/gateway-to-auth.md`
- `specs/contracts/http/gateway-api.md`

---

# Target Service

- `apps/gateway-service`

---

# Architecture

Follow `specs/services/gateway-service/architecture.md` — Thin Layered (filter pipeline).

---

# Edge Cases

- If `Authorization` header is absent on the `refresh` scope path (e.g. malformed request), fall back to IP identifier and log a warning metric
- `LoggingFilter` must not log Authorization header value (PII/secret leak risk per `platform/security-rules.md`)

---

# Failure Scenarios

- WebClient timeout: if auth-service JWKS endpoint does not respond within 5s, `WebClient` should throw `ReadTimeoutException` — handled by existing `GatewayErrorConfig` → 503
- `UpstreamHealthIndicator` failure: treat as WARNING (do not mark service DOWN on a single check failure)

---

# Test Requirements

- Unit: `LoggingFilter` — verify latency is recorded, sensitive headers not logged
- Unit: `RateLimitFilter` — verify `refresh` scope uses account_id from JWT sub claim
- Integration: all existing `GatewayIntegrationTest` scenarios must execute when Docker is available

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added and passing
- [ ] `./gradlew :apps:gateway-service:test` passes
- [ ] Ready for review
