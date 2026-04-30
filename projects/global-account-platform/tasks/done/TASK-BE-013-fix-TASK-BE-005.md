# Task ID

TASK-BE-013

# Title

Fix issues found in TASK-BE-005: outbox table name mismatch, missing reuse-detected event, AccountServiceClient timeout not applied, geoCountry missing from event payloads, integration tests skipped

# Status

ready

# Owner

backend

# Task Tags

- code
- event

# depends_on

- TASK-BE-005

---

# Goal

Fix issues found in review of TASK-BE-005 (auth-service bootstrap). All items below are spec violations or contract mismatches discovered during code review.

---

# Scope

## In Scope

1. **[Critical] Outbox migration table name mismatch**: `V0002__create_outbox_events.sql` creates a table named `outbox` but `specs/services/auth-service/data-model.md` and the outbox library expect `outbox_events`. Rename the table in the migration (add new migration; do not modify `V0002`).

2. **[Critical] Missing `auth.token.reuse.detected` event publish**: `RefreshTokenUseCase` revokes all tokens on reuse detection but never calls `authEventPublisher.publishTokenReuseDetected(...)`. Add a `publishTokenReuseDetected` method to `AuthEventPublisher` and call it from `RefreshTokenUseCase` before throwing, including the required payload fields per `specs/contracts/events/auth-events.md` (`reusedJti`, `originalRotationAt`, `reuseAttemptAt`, `ipMasked`, `deviceFingerprint`, `sessionsRevoked`, `revokedCount`).

3. **[Warning] AccountServiceClient: timeout config values injected but not applied to RestClient**: `connectTimeoutMs` and `readTimeoutMs` are read from config but the `RestClient` is built without configuring the underlying HTTP client timeouts. Wire the timeout values into the `ClientHttpRequestFactory` (e.g., `JdkClientHttpRequestFactory` or `HttpComponentsClientHttpRequestFactory`) as specified in `specs/services/auth-service/dependencies.md`.

4. **[Warning] AccountServiceClient: no retry logic and no circuit breaker**: The dependencies spec mandates 2 retries with exponential backoff + jitter and a 50%/10s circuit breaker. The current implementation has neither. Implement retry (Spring Retry or Resilience4j) and circuit breaker for the `AccountServiceClient`. 4xx responses must NOT be retried per spec.

5. **[Warning] Event payloads missing `geoCountry` field**: `auth-events.md` requires `geoCountry` in `auth.login.attempted`, `auth.login.failed`, and `auth.login.succeeded` payloads. The current `AuthEventPublisher` omits this field entirely. Add `geoCountry` to `SessionContext` (with a default of `"unknown"` when not determinable) and include it in the affected event payloads.

6. **[Warning] Integration tests all skipped**: `AuthIntegrationTest` and `OutboxRelayIntegrationTest` were all skipped in the last run (Docker not available in CI environment). These tests cover the most critical acceptance criteria. Document the Docker prerequisite in the service README and ensure the tests are marked properly so they do not show as passing when skipped.

## Out of Scope

- Token reuse detection logic changes (TASK-BE-009)
- Any new feature work

---

# Acceptance Criteria

- [ ] A new Flyway migration corrects the outbox table name to `outbox_events` (or confirms the outbox library schema uses `outbox` and updates data-model.md to match — one or the other must be consistent)
- [ ] `RefreshTokenUseCase` publishes `auth.token.reuse.detected` event via outbox when reuse is detected; payload matches `auth-events.md` schema
- [ ] `AccountServiceClient` applies connect and read timeouts from config to the underlying HTTP client
- [ ] `AccountServiceClient` applies retry (2 retries, exp backoff + jitter, no retry on 4xx) and circuit breaker (50% failure rate / 10s window) as per `specs/services/auth-service/dependencies.md`
- [ ] `geoCountry` field is present in `auth.login.attempted`, `auth.login.failed`, `auth.login.succeeded` event payloads
- [ ] Unit test added for `publishTokenReuseDetected` event payload structure
- [ ] Unit test added for `AccountServiceClient` circuit breaker behavior (mock upstream failure)
- [ ] `./gradlew :apps:auth-service:test` passes (non-Docker tests)

---

# Related Specs

- `specs/services/auth-service/architecture.md`
- `specs/services/auth-service/dependencies.md`
- `specs/services/auth-service/data-model.md`
- `specs/contracts/events/auth-events.md`

# Related Skills

- `.claude/skills/backend/jwt-auth/SKILL.md`
- `.claude/skills/messaging/outbox-pattern/SKILL.md`

---

# Related Contracts

- `specs/contracts/events/auth-events.md`
- `specs/contracts/http/internal/auth-to-account.md`

---

# Target Service

- `apps/auth-service`

---

# Architecture

`specs/services/auth-service/architecture.md` — Layered 4-layer. All fixes must respect existing layer boundaries.

---

# Edge Cases

- If the outbox library already uses a table named `outbox` (not `outbox_events`), update `data-model.md` instead of adding a migration; document the decision.
- For circuit breaker: if Resilience4j is not yet in the dependency graph, add it; do not add a second circuit breaker library if one already exists.
- `geoCountry` cannot be determined server-side without GeoIP; default to `"unknown"` for now.

---

# Failure Scenarios

- Flyway migration fails on startup if table rename conflicts with existing data (use `RENAME TABLE` migration, not DROP + CREATE)
- Circuit breaker misconfiguration could block all login attempts; include a health-check test

---

# Test Requirements

- Unit: `AuthEventPublisher.publishTokenReuseDetected` payload structure
- Unit: `AccountServiceClient` timeout / retry / circuit breaker behavior (WireMock)
- Existing integration tests must continue to pass when Docker is available

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added and passing
- [ ] Event payload matches auth-events.md contract
- [ ] Outbox table name consistent between migration, data-model.md, and outbox library
- [ ] Ready for review
