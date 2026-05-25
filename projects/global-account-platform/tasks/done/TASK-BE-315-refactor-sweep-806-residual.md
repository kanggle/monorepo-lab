# Task ID

TASK-BE-315

# Title

Refactor sweep — PR #806 residual cleanup (PortAdapter/JpaAdapter/RepositoryAdapter rename + Slf4jEmailSender drop + AccountAdminUseCase extract + AdminAuthController safeRecord unify)

# Status

done

# Owner

backend

# Task Tags

- code

---

# Goal

Clean up remaining refactoring debt left after PR #806 across five services:
`account-service`, `admin-service`, `auth-service`, `community-service`, `membership-service`.

All changes are mechanical; no behavior change, no contract change, no schema change.

---

# Scope

## In Scope

### L1 — Class renames (11 files)

**account-service**
- `AccountQueryPortAdapter` → `AccountQueryPortImpl` (implements `AccountQueryPort`)
- `TenantJpaAdapter` → `TenantRepositoryImpl` (implements `TenantRepository`)

**admin-service**
- `AdminRefreshTokenJpaAdapter` → `AdminRefreshTokenPortImpl` (implements `AdminRefreshTokenPort`)
- `BulkLockIdempotencyJpaAdapter` → `BulkLockIdempotencyPortImpl` (implements `BulkLockIdempotencyPort`)
- `OperatorLookupJpaAdapter` → `OperatorLookupPortImpl` (implements `OperatorLookupPort`)

**community-service**
- `CommentRepositoryAdapter` → `CommentRepositoryImpl`
- `FeedSubscriptionRepositoryAdapter` → `FeedSubscriptionRepositoryImpl`
- `PostRepositoryAdapter` → `PostRepositoryImpl`
- `PostStatusHistoryRepositoryAdapter` → `PostStatusHistoryRepositoryImpl`
- `ReactionRepositoryAdapter` → `ReactionRepositoryImpl`

**membership-service**
- `ContentAccessPolicyRepositoryAdapter` → `ContentAccessPolicyRepositoryImpl`
- `MembershipPlanRepositoryAdapter` → `MembershipPlanRepositoryImpl`
- `SubscriptionRepositoryAdapter` → `SubscriptionRepositoryImpl`
- `SubscriptionStatusHistoryRepositoryAdapter` → `SubscriptionStatusHistoryRepositoryImpl`

### L2 — Dead code removal

**auth-service**
- Delete `Slf4jEmailSender.java` (superseded by `LoggingEmailSender` per TASK-BE-242)
- Delete `Slf4jEmailSenderUnitTest.java` (tests dead class)

### L6 — Long method / duplication

**admin-service**
- `AccountAdminUseCase`: extract shared lock/unlock flow into private `executeAccountAction(...)` helper to eliminate the duplicated audit-start → downstream-call → audit-complete pattern between `lock()` and `unlock()`
- `AdminAuthController`: route the 3 direct `auditor.record(...)` SUCCESS-path calls (in `regenerateRecoveryCodes`, `enroll`, `verify`) through the existing `safeRecord(...)` helper (PR #809 pattern)

## Out of Scope

- `AdminActionAuditor` itself (TASK-BE-314 god-class split)
- DB schema / Flyway migrations
- API/event contract changes
- Cross-service changes

---

# Acceptance Criteria

- [ ] All 14 renamed classes use the correct suffix per `platform/naming-conventions.md`
- [ ] No references to old class names remain in main source (test rename lockstep)
- [ ] `Slf4jEmailSender.java` and `Slf4jEmailSenderUnitTest.java` deleted
- [ ] `AccountAdminUseCase.lock()` and `unlock()` delegate to a shared `executeAccountAction(...)` helper
- [ ] `AdminAuthController` SUCCESS-path audit writes in `regenerateRecoveryCodes`, `enroll`, `verify` go through `safeRecord(...)`
- [ ] All 5 services: `./gradlew :<project>:<service>:check` BUILD SUCCESSFUL
- [ ] audit shape byte-equal (audit-heavy A1/A3 trait)

---

# Related Specs

- `platform/refactoring-policy.md`
- `platform/naming-conventions.md`
- `projects/global-account-platform/specs/services/<svc>/architecture.md` for each service

---

# Related Contracts

- No contract changes

---

# Target Services

- `account-service`, `admin-service`, `auth-service`, `community-service`, `membership-service`

---

# Edge Cases

- Admin-service `AdminRefreshTokenJpaAdapter` / `BulkLockIdempotencyJpaAdapter` / `OperatorLookupJpaAdapter` implement Port interfaces not Repository interfaces → suffix is `PortImpl` not `RepositoryImpl`
- `TenantJpaAdapter` implements `TenantRepository` (domain repository) → suffix is `RepositoryImpl`
- `AccountQueryPortAdapter` implements `AccountQueryPort` → suffix is `PortImpl`
- `Slf4jEmailSender` has `@ConditionalOnMissingBean` so it never activates alongside `LoggingEmailSender` — safe to delete
- TASK-BE-314 conflict: do NOT touch `AdminActionAuditor` itself; only update call sites

---

# Failure Scenarios

- Rename without updating all references → compile failure
- Deleting `Slf4jEmailSender` without deleting its test → compile failure
- `executeAccountAction` helper breaks fail-closed audit path → audit regression
- Routing SUCCESS `auditor.record()` through `safeRecord` changes exception propagation semantics → A10 violation

---

# Test Requirements

- All existing tests pass without test-logic changes
- `./gradlew :projects:global-account-platform:apps:account-service:check`
- `./gradlew :projects:global-account-platform:apps:admin-service:check`
- `./gradlew :projects:global-account-platform:apps:auth-service:check`
- `./gradlew :projects:global-account-platform:apps:community-service:check`
- `./gradlew :projects:global-account-platform:apps:membership-service:check`

---

# Definition of Done

- [ ] All renames applied
- [ ] Dead code deleted
- [ ] Helpers extracted / unified
- [ ] All 5 services check GREEN
- [ ] Committed and pushed
