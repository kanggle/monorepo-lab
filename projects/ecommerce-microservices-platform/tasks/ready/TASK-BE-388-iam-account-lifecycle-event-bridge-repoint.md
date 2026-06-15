# TASK-BE-388 — Re-point the IAM→ecommerce account-lifecycle event bridge (signup off the decommissioned `auth.user.signed-up`; add `account.deleted` reaction)

**Status:** ready
**Type:** TASK-BE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Opus (event-driven cross-project integration + compliance-adjacent semantics)

> ⚠️ **ADR PREREQUISITE (HARDSTOP-09).** This task requires a cross-project integration architecture decision that is **not yet recorded** — do NOT implement until an ADR (PROPOSED → user-gated ACCEPT, per the ADR-019/020/032/034/035/036 staged-child pattern) decides the items in "Open decisions" below. This file is a tracked **backlog finding**, not an implement-ready task.

---

## Goal

Close the IAM↔ecommerce account-lifecycle event bridge gap surfaced 2026-06-15 while investigating the (now-removed, TASK-BE-387) HTTP withdrawal endpoint. Post-IAM (TASK-BE-132, auth-service decommissioned), the ecommerce account-lifecycle event wiring is **orphaned / transitional**:

- **Producer is dead.** The only producer of `auth.user.signed-up` is the **decommissioned** ecommerce `auth-service` (`apps/auth-service/.../UserSignupRepublishService` + `AuthEventKafkaBridge` — excluded from the Gradle build, TASK-BE-132).
- **Two live consumers still listen to the dead topic.** `user-service` `UserSignedUpConsumer` (`groupId=user-service`) and `notification-service` `UserSignedUpEventConsumer` (`groupId=notification-service`) both `@KafkaListener(topics="auth.user.signed-up")`.
- **IAM's account events are unconsumed.** IAM `account-service` publishes `account.created` / `account.status.changed` / `account.deleted` / `account.locked` / `account.unlocked` / `account.roles.changed` (`AccountOutboxPollingScheduler`), but **ecommerce consumes none of them**.

So ecommerce profile creation (and notification onboarding) depend on a topic no live service emits, and account deletion has no ecommerce reaction at all (the `UserProfileService.withdrawProfile()` + `UserWithdrawn` machinery is an orphaned entry point — see TASK-BE-387).

## Scope (pending ADR)

**Likely in scope (subject to the ADR):**

1. **Signup re-point** — `user-service` + `notification-service` consume IAM **`account.created`** (the live event) instead of the dead `auth.user.signed-up`. Map `account.created.accountId` → the ecommerce profile `userId` (verify the id is the same identifier the old `auth.user.signed-up.payload.userId` carried). Preserve tenant-context binding (envelope `tenant_id`, M5 net-zero) as `UserSignedUpConsumer` does today.
2. **Delete reaction** — `user-service` consumes IAM **`account.deleted`** (`{accountId, tenantId, reasonCode, deletedAt, gracePeriodEndsAt, anonymized}`) → resolve profile by `accountId` → `UserProfileService.withdrawProfile()` (status WITHDRAWN) → emit ecommerce `UserWithdrawn` (already consumed by order-service). Idempotent (re-delivery / already-WITHDRAWN → no-op; user-service has no dedup store today — rely on withdraw idempotency or add one). Fail-soft consumer.
3. **Contracts** — add ecommerce subscription contracts for `account.created` + `account.deleted` (consumed IAM events); retire the `auth.user.signed-up` producer assumption from `authentication.md` / event docs.

**Explicitly OUT of scope / deferred (record in the ADR):**

- **PII anonymization on `account.deleted`.** IAM's `account.deleted` carries `anonymized` (GDPR-delete via `GdprDeleteUseCase` + `PiiAnonymizer`). A v1 status-withdrawal reaction does NOT anonymize ecommerce-held PII (profile name/email, addresses, order PII). Leaving non-anonymized PII after an IAM GDPR-delete is a **compliance gap** — the ADR must decide v1 (status-only, documented limitation) vs full anonymization/cascade. This is the crux decision, not a wiring detail.
- The `gracePeriodEndsAt` semantics (react immediately vs at grace-period end).

## Open decisions (the ADR must resolve — HARDSTOP-09)

- **D-a** GDPR reaction depth: status-withdrawal only (v1, documented PII-retention limitation) vs PII anonymization vs anonymize + cascade to orders. (compliance-sensitive)
- **D-b** id mapping: is `account.created.accountId` / `account.deleted.accountId` the same value as the ecommerce profile `userId`? (verify; if not, define resolution)
- **D-c** consumer availability/idempotency: fail-soft + dedup approach (user-service has no `processed_event` store today).
- **D-d** grace-period timing (immediate vs `gracePeriodEndsAt`).

## Acceptance Criteria (post-ADR)

- **AC-1** — No ecommerce consumer listens to `auth.user.signed-up` (the decommissioned-service topic); signup onboarding rides IAM `account.created`.
- **AC-2** — IAM `account.deleted` is consumed by `user-service` → profile WITHDRAWN + `UserWithdrawn` emitted, idempotent + fail-soft.
- **AC-3** — Subscription contracts exist for the consumed IAM events; the GDPR-anonymization stance is documented per the ADR.
- **AC-4** — Tests: unit + Testcontainers IT (the IT is the authority for Kafka wiring; **NOTE — Testcontainers is locally blocked on the dev Windows host per project memory `project_testcontainers_docker_desktop_blocker`; IT is CI-Linux-verified**).

## Related Specs

- `specs/services/user-service/architecture.md`, `specs/services/notification-service/architecture.md` (the two consumers)
- `specs/features/authentication.md` (DEPRECATED — documents the old auth.user.signed-up flow)
- `specs/contracts/events/` (new subscription contracts for account.created / account.deleted)
- `specs/contracts/http/user-api.md` (TASK-BE-387 removed the HTTP withdrawal endpoint; this task wires the event-driven reaction it pointed to)

## Related Contracts

- IAM `account-service` `AccountEventFactory` (`account.created` / `account.deleted` payloads) + `AccountOutboxPollingScheduler` (topics).
- TASK-BE-132 (auth-service decommission — the root cause), TASK-BE-387 (removed the stale HTTP withdrawal endpoint).
- ADR-MONO-032/034/035/036 (identity family — this is the **death/lifecycle-projection** sibling to ADR-036's birth-provisioning; a new ADR likely needed).

## Edge Cases

- **Dual consumer impact** — both user-service AND notification-service consume the dead topic; re-point both (or the onboarding notification breaks too).
- **Orphaned entry point preserved** — `withdrawProfile()` + `UserWithdrawn` stay as the wiring target (TASK-BE-387 kept them).
- **Not born-unified** — this is the account-lifecycle PROJECTION into ecommerce (death + onboarding), orthogonal to ADR-036 born-unified PROVISIONING (identity mint at birth, iam-scoped). No overlap.

## Failure Scenarios

- **F1 — build on broken foundation** — wiring the delete consumer alone (without re-pointing signup off the dead topic) leaves an inconsistent half-bridge whose id-mapping foundation is unestablished. Guarded by treating signup re-point + delete as one bridge.
- **F2 — silent GDPR non-compliance** — shipping status-only reaction to a GDPR `account.deleted` without documenting the PII-retention limitation would misrepresent deletion handling. Guarded by D-a + AC-3.
- **F3 — implementing before the ADR** — bakes the GDPR/availability model in code (HARDSTOP-09). Guarded by the ADR PREREQUISITE banner.
