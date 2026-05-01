# Task ID

TASK-BE-260

# Title

Fix issue found in TASK-BE-248: AccountLockedConsumer test regression + AuthEventPublisher tenantId guard gap (LOGIN_TENANT_AMBIGUOUS DLQ leak)

# Status

ready

# Owner

backend

# Task Tags

- code
- event
- test

---

# Required Sections

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Goal

Fix issue found in TASK-BE-248. Two blocker-level findings from review of `tasks/review/TASK-BE-248-security-service-tenant-events.md`:

1. **Test regression**: `AccountLockedConsumerUnitTest` has 4 failing tests because Phase 2b commit `0325ede1` made `AccountLockedConsumer` reject `account.locked` payloads without `tenantId`, but the test JSON fixtures were not updated. `:security-service:test` is RED — violates the DoD "Tests passing (CI green)".
2. **Production bug**: `LoginUseCase.LOGIN_TENANT_AMBIGUOUS` path publishes `auth.login.failed` with `tenantId=null`. `AuthEventPublisher.publishLoginAttempted/Failed` lack the `requireTenantId` guard that the other publish methods enforce, so the null silently flows into the outbox payload. Once consumed by `security-service`, every LOGIN_TENANT_AMBIGUOUS event is rejected with `MissingTenantIdException` and routed to `auth.login.failed.dlq` — the security audit signal for the most ambiguity-sensitive failure mode is lost in production.

This task closes both gaps and adds a small defensive cleanup (Issue 3 — guard on `publishLoginAttempted/Failed`) that prevents the same root cause from recurring.

---

# Scope

## In Scope

- **Fix 1 — `AccountLockedConsumerUnitTest` (security-service)**: add `"tenantId": "fan-platform"` (or equivalent valid value) to the 4 test JSON payloads listed below. Add one *new* test asserting the strict behavior — `account.locked` payload missing `tenantId` throws `MissingTenantIdException` (regression coverage for Phase 2b's strict mode):
  - `Envelope-wrapped payload is parsed and saved with reasonCode/actorType mapping` (line 67)
  - `Flat payload (account-service current form) is also accepted` (line 94)
  - `Duplicate event_id triggers DataIntegrityViolation which is swallowed (idempotent)` (line 139)
  - `Explicit source field overrides actorType-derived default` (line 172)
- **Fix 2 — `LoginUseCase.LOGIN_TENANT_AMBIGUOUS` path (auth-service)**: pick one of two strategies:
  - (Preferred) Reuse `tenantIdForRateLimit` (already non-null with `fan-platform` fallback) for the ambiguous path. Audit-trail gets attributed to the request's apparent rate-limit tenant — consistent with the surrounding rate-limit and login-attempted publishes on lines 69, 70, 76.
  - (Alternative) Introduce a sentinel `__ambiguous__` tenant constant on `TenantContext`. Document it in `specs/contracts/events/auth-events.md` `auth.login.failed` payload notes as the value emitted when an email matches multiple tenants and the caller did not specify one.

  Whichever is chosen, document the decision in this task's "Implementation Notes" before merging and update `auth-events.md` if the sentinel approach is taken.
- **Fix 3 — Defensive guard parity (auth-service)**: add `requireTenantId(tenantId)` at the top of `AuthEventPublisher.publishLoginAttempted(...)` and `publishLoginFailed(...)` so future call-sites that omit the tenant context fail at publish time (and so Fix 2 cannot regress silently).
  - Mark the legacy 3-arg `publishLoginAttempted(...)` and 5-arg `publishLoginFailed(...)` deprecated overloads `@Deprecated(forRemoval = true)` and remove their bodies in a follow-up cleanup task once no callers remain — the current bodies pass `null`, which would now throw post-Fix 3.
- **Cleanup (Issue 4, optional)**: refresh stale "Phase 1 / Phase 2 will replace" comments in `security-service` to reflect that Phase 2a already enforces strict tenant_id at `AbstractAuthEventConsumer`:
  - `AuthEventMapper.java` lines 39-46, 102-107
  - These fallbacks are dead code in production once `AbstractAuthEventConsumer.processEvent` rejects missing tenant_id upstream.

- **Tests**:
  - `:security-service:test` GREEN (192 + new tests)
  - `:auth-service:test` GREEN with new coverage:
    - `LoginUseCase` test for the LOGIN_TENANT_AMBIGUOUS path verifying the published event carries a non-null tenantId
    - `AuthEventPublisherTest` cases asserting `publishLoginAttempted` / `publishLoginFailed` throw `IllegalArgumentException` for null/blank tenantId (mirror of the existing SecurityEventPublisher/AccountEventPublisher coverage)

## Out of Scope

- `SecurityQueryService` X-Tenant-Id propagation (still tracked in TASK-BE-248 implementation notes as a Phase 2 deferral; query routes are not in this fix task — separate task should be opened if desired).
- `auth.token.reuse.detected` tenant_id addition — already deferred to TASK-BE-259, do not preempt.
- Removal of legacy deprecated overloads on `AuthEventPublisher` — opt-in cleanup task after this fix lands.

---

# Acceptance Criteria

- [ ] `./gradlew :projects:global-account-platform:apps:security-service:test` GREEN (no failures)
- [ ] `./gradlew :projects:global-account-platform:apps:auth-service:test` GREEN
- [ ] New unit test in `AccountLockedConsumerUnitTest`: `account.locked` payload missing `tenantId` → throws `MissingTenantIdException` (asserts contract compliance with Phase 2b)
- [ ] `LoginUseCase` LOGIN_TENANT_AMBIGUOUS path: published `auth.login.failed` event carries a non-null, non-blank tenantId
- [ ] `AuthEventPublisher.publishLoginAttempted(null tenantId, ...)` → throws `IllegalArgumentException` with message "tenantId required"
- [ ] `AuthEventPublisher.publishLoginFailed(null tenantId, ...)` → throws `IllegalArgumentException` with message "tenantId required"
- [ ] If sentinel approach chosen for Fix 2: `specs/contracts/events/auth-events.md` `auth.login.failed` payload note documents the sentinel value

---

# Related Specs

> Step 0: read `PROJECT.md`, rules layers per classification.

- `specs/features/multi-tenancy.md` § "Cross-Tenant Security Rules" (event isolation requirement)
- `specs/contracts/events/auth-events.md` (`auth.login.failed` payload — `tenantId` required)
- `specs/services/security-service/architecture.md` § "consumer/" (DLQ routing for malformed events)
- `tasks/review/TASK-BE-248-security-service-tenant-events.md` (original task — Goal #1 + AC #5)

# Related Skills

- `.claude/skills/backend/event-driven-policy.md`
- `.claude/skills/review-checklist/SKILL.md`

---

# Related Contracts

- `specs/contracts/events/auth-events.md` — payload note for `auth.login.failed` if sentinel approach is taken (Fix 2 alternative)

---

# Target Service

- `security-service` (test fixture fix)
- `auth-service` (publisher guard + LOGIN_TENANT_AMBIGUOUS callsite fix)

---

# Architecture

No structural changes. Edits are confined to:

- `apps/security-service/src/test/java/.../consumer/AccountLockedConsumerUnitTest.java`
- `apps/auth-service/src/main/java/.../application/LoginUseCase.java`
- `apps/auth-service/src/main/java/.../application/event/AuthEventPublisher.java`
- `apps/auth-service/src/test/java/.../application/event/AuthEventPublisherTest.java`
- `apps/auth-service/src/test/java/.../application/LoginUseCaseTest.java`
- (optional) `apps/security-service/src/main/java/.../consumer/AuthEventMapper.java` — comment cleanup

---

# Implementation Notes

- **Decision pending**: Fix 2 strategy (reuse `tenantIdForRateLimit` vs introduce `__ambiguous__` sentinel). Recommendation: reuse the rate-limit tenant. The audit row is honest about what tenant the request hit (rate-limit attribution); a sentinel introduces a new vocabulary point that other consumers must learn. The rate-limit fallback already exists and makes detection counters consistent across the rate-limit and ambiguous paths.
- **Test fixture pattern for Fix 1**: pick `"tenantId": "fan-platform"` for consistency with the existing migration backfill default. Avoid randomized values — keeps test diffs minimal and matches the convention used by `AccountLockHistoryJpaRepositoryTest` fixtures.
- **`requireTenantId` placement (Fix 3)**: keep the guard private and reuse the existing static helper at `AuthEventPublisher.requireTenantId(String)` (line 25). Call it as the first statement in both `publishLoginAttempted` overloads' real-work method and `publishLoginFailed` real-work method.
- **Why this is a fix task and not a TASK-BE-248 amendment**: per `tasks/INDEX.md` § "Review Rules", tasks in `review/` cannot be re-implemented; review-found defects must be filed as new fix tasks referencing the original ID. TASK-BE-248 will move to `done/` post-fix-task-creation per the same rule.

---

# Edge Cases

- **`account.locked` event from a legacy non-multi-tenant publisher**: post-Phase 2b, account-service always emits tenantId. There is no legacy queue replay scenario in dev; production migration plan (TASK-BE-248 Implementation Notes) calls for backfill before deploy. The new test for "missing tenantId throws" is regression coverage, not a real production path.
- **LOGIN_TENANT_AMBIGUOUS combined with rate-limit**: the rate-limit branch (line 69) and the ambiguous branch (line 105) are mutually exclusive in execution but both need a tenantId. Fix 2's reuse-`tenantIdForRateLimit` strategy works for both because `tenantIdForRateLimit` is computed before the branch.

---

# Failure Scenarios

- **If LOGIN_TENANT_AMBIGUOUS happens with `command.tenantId() == null`**: `tenantIdForRateLimit` defaults to `fan-platform`. The published event will carry `tenantId=fan-platform`, which the security-service accepts. Detection counters for the ambiguous path will be attributed to `fan-platform`. This is acceptable — the request was inherently ambiguous; the fan-platform attribution is documented at the contract layer.
- **If callers regress and pass null again**: Fix 3's `requireTenantId` guard now throws at the publisher boundary, surfacing the bug at unit-test time rather than at runtime against a broken Kafka pipeline.
- **If reviewer chooses the sentinel approach instead of reuse**: `__ambiguous__` value must be added to the `auth.login.failed` `tenantId` field doc in `auth-events.md`. The security-service tenant validator must accept the sentinel (currently `MissingTenantIdException` only fires on null/blank — a non-null sentinel passes through fine, but downstream detection rules will aggregate counters under `__ambiguous__`).

---

# Test Requirements

- **Unit tests (security-service)**:
  - Update 4 fixtures in `AccountLockedConsumerUnitTest` to include tenantId
  - Add 1 new test: missing tenantId on `account.locked` → `MissingTenantIdException` thrown
- **Unit tests (auth-service)**:
  - `AuthEventPublisherTest`: 4 new cases — null/blank tenantId throws on `publishLoginAttempted` / `publishLoginFailed` (mirror of existing SecurityEventPublisher tests)
  - `LoginUseCaseTest`: assert LOGIN_TENANT_AMBIGUOUS path emits non-null tenantId on the captured `publishLoginFailed` argument
- **Integration tests**: not required for this fix — Phase 2a's `DlqRoutingIntegrationTest.missingTenantIdRoutedToDlqAndMetricIncremented` already covers the consumer-side strict mode. The fix is to source events that satisfy the contract, not to extend the DLQ behavior.

---

# Definition of Done

- [ ] AccountLockedConsumerUnitTest fixtures updated (4 existing + 1 new test)
- [ ] AuthEventPublisher.publishLoginAttempted/Failed null guard added
- [ ] LoginUseCase LOGIN_TENANT_AMBIGUOUS callsite fixed
- [ ] auth-events.md updated if sentinel chosen (otherwise no spec update needed)
- [ ] `:security-service:test` and `:auth-service:test` GREEN
- [ ] AuthEventMapper stale "Phase 1" comments refreshed (optional cleanup)
- [ ] Ready for review
