# Task ID

TASK-BE-008

# Title

Platform error-envelope compliance — add `timestamp`, correct `STATE_TRANSITION_INVALID` status

# Status

ready

# Owner

backend

# Task Tags

- code
- api
- test

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Goal

Fix two platform-baseline compliance gaps found in the TASK-BE-001 review
that also affect TASK-BE-002, TASK-BE-003, TASK-BE-004, and TASK-INT-001:

1. **`timestamp` missing from error envelope.** `platform/error-handling.md`
   (priority 2) mandates error responses always carry `{code, message,
   timestamp}` (ISO 8601 UTC). `ApiErrorEnvelope.ApiError` on master-service
   and `ApiErrorEnvelope` on gateway-service both omit `timestamp`.
2. **`STATE_TRANSITION_INVALID` mapped to 409, spec says 422.**
   `platform/error-handling.md` maps "unprocessable business rule violation
   (state transition, reference integrity)" to 422. The contract at
   `specs/contracts/http/master-service-api.md` and every aggregate's
   `GlobalExceptionHandler` currently return 409. Per CLAUDE.md Source of
   Truth Priority, the platform doc wins (2 > 6 > 14) — contract + code
   must be corrected.

---

# Scope

## In Scope

- Update the HTTP contract: `specs/contracts/http/master-service-api.md` §Error Envelope table — `STATE_TRANSITION_INVALID` maps to 422
- Add `timestamp` (Instant / ISO 8601 string) to the error envelope classes:
  - `projects/wms-platform/apps/master-service/src/.../ApiErrorEnvelope.java` (ApiError inner record)
  - `projects/wms-platform/apps/gateway-service/src/.../ApiErrorEnvelope.java`
  - Any inline JSON fallbacks (e.g. `GatewayErrorHandler.serialize()`)
- Populate `timestamp` via `Instant.now()` in every factory / writer
- Update `GlobalExceptionHandler` in master-service to return `HttpStatus.UNPROCESSABLE_ENTITY` for `InvalidStateTransitionException`
- Update every test that asserts 409 for state-transition-invalid scenarios to 422 (searching for `STATE_TRANSITION_INVALID` assertions in WarehouseControllerTest, ZoneControllerTest, LocationControllerTest, and any integration tests)
- Update the AC text in TASK-BE-001/002/003/004 review sections if they reference 409 for state transitions (documentation-only; these tasks are in `done/` so edits stay in review notes if we touch them — actually task files in done/ should not be edited; leave note in this task's review that the change makes the earlier review notes obsolete for those two bullets)

## Out of Scope

- Any other error-code refactoring
- Rewriting tests that weren't touching these two concerns
- Renaming `STATE_TRANSITION_INVALID` to a different code (stays the same; only the HTTP status changes)

---

# Acceptance Criteria

- [ ] `specs/contracts/http/master-service-api.md` §Error Envelope table shows `STATE_TRANSITION_INVALID → 422`
- [ ] `ApiErrorEnvelope.ApiError` (master-service) carries `Instant timestamp` (or ISO 8601 string); every factory populates it with `Instant.now()`
- [ ] `ApiErrorEnvelope` (gateway-service) carries the same
- [ ] `GatewayErrorHandler.serialize()` fallback JSON includes `"timestamp": "<utc-now>"`
- [ ] `GlobalExceptionHandler.handleInvalidTransition` returns `HttpStatus.UNPROCESSABLE_ENTITY`
- [ ] Every affected test asserts 422 (not 409) for state-transition cases; non-state-transition 409s (e.g. version conflict, duplicate code) still assert 409
- [ ] `./gradlew check` passes
- [ ] CI green on the PR

---

# Related Specs

- `platform/error-handling.md` — the authoritative mapping
- `specs/contracts/http/master-service-api.md` — to be updated

# Related Contracts

- `specs/contracts/http/master-service-api.md`

---

# Target Service

- `master-service` (primary); `gateway-service` (secondary — envelope field only)

---

# Implementation Notes

- **Jackson serialization**: `Instant` serializes to ISO 8601 by default when `JavaTimeModule` is registered (it is in both services). Use `Instant` directly on the record; serialization "just works".
- **Idempotency cache**: `IdempotencyFilter` caches entire response bodies. Once `timestamp` is added, cached responses will have fresh timestamps… wait, no — the cached body is bytes, and the timestamp was captured at the original write. Replay returns the cached bytes verbatim with the ORIGINAL timestamp. This is intentional: replay must be byte-identical.
- **Test impact**: contract tests (via `HttpContractTest`) will fail if the JSON schema does not include `timestamp`. Update `src/test/resources/contracts/http/error-envelope.schema.json` (and any per-aggregate error variants) to require the field.

---

# Edge Cases

- `GatewayErrorHandler.serialize()` catches `JsonProcessingException` and falls back to hand-rolled JSON — the fallback must also include `timestamp`.
- Error paths with no `Instant` available (e.g. during JVM startup) — use `Instant.now()`, no fallback needed.

---

# Failure Scenarios

- Rolling deploy mid-transition: an old caller may expect 409 for state transitions. Document in PR body; coordinate with any downstream client if one existed (none do in v1).
- Contract test suite fails on the new schema — update schemas in the same PR.

---

# Test Requirements

- Extend existing `WarehouseControllerTest` / `ZoneControllerTest` / `LocationControllerTest` cases that assert state-transition → 409 to assert 422 instead
- New assertion in one representative controller test: error response body contains a non-null `timestamp` matching ISO 8601
- Update `error-envelope.schema.json` to require `timestamp`
- Run `HttpContractTest` and `EventContractTest` to confirm schema validation passes

---

# Definition of Done

- [ ] Contract + code + tests updated in one PR
- [ ] CI green
- [ ] Review note summarizes the before/after on the 5 affected task reviews
- [ ] Ready for review
