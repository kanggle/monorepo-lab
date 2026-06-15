# TASK-BE-388 — Re-point the IAM→ecommerce account-lifecycle event bridge (signup off the decommissioned `auth.user.signed-up`; add `account.deleted` reaction)

**Status:** review
**Type:** TASK-BE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Opus (event-driven cross-project integration + compliance-adjacent semantics)

> **IMPLEMENTED (review) — ADR-MONO-037 P1–P6 realized (M1→M4).** **M1** user-service `AccountCreatedConsumer` → `AccountCreatedHandler.createMinimal` (minimal profile; `email`/`name` nullable via Flyway **V5**; idempotent on `userId`); notification-service `AccountCreatedEventConsumer` → WELCOME without PII personalization; the two dead-topic `auth.user.signed-up` consumers + their events/handler/tests **deleted**. **M2** `AccountDeletedConsumer` (anonymized=false) → idempotent fail-soft `UserProfileService.withdrawProfile` (throw relaxed to no-op on missing/already-WITHDRAWN) + `UserWithdrawn`. **M3** (anonymized=true) → `UserProfileService.anonymizeProfile` (`UserProfile.anonymize()` clears email/name/nickname/phone/imageUrl, preserves `userId`). **M4** new subscription contract `specs/contracts/events/account-lifecycle-subscriptions.md` (account.created + account.deleted two-phase + **order-PII cascade documented-deferred boundary** P3); `auth-events.md` UserSignedUp marked RETIRED; user-service + notification-service overview/architecture/dependencies/observability + `user-api.md`/`notification-api.md`/`user-management.md`/use-case specs re-pointed. **Tests**: unit (`UserProfile`, `AccountCreatedHandler`, `AccountCreated`/`AccountDeleted` consumers, `AccountLifecycleEventDeserialization`, `UserProfileService` withdraw/anonymize, notification `AccountCreatedEventConsumer`) — Docker-free `:test` GREEN both services locally; Testcontainers ITs are CI-Linux-authoritative (local Docker blocked per `project_testcontainers_docker_desktop_blocker`). id mapping (P4) verified `accountId = profile.userId = sub` (no translation layer). Deferred follow-ups (P3-B order-PII cascade / P1-B sync IAM fetch / P5-B dedup store / `account.locked` projection) recorded in the ADR § 3.3 — out of v1.

> ✅ **ADR PREREQUISITE SATISFIED (was HARDSTOP-09).** The cross-project integration architecture decision is now recorded: **[ADR-MONO-037](../../../../docs/adr/ADR-MONO-037-ecommerce-account-lifecycle-projection.md) — ACCEPTED 2026-06-15 (TASK-MONO-269)** decides the "Open decisions" below. This task is now **implement-ready**. The ADR's P1–P6 supersede the open-decisions framing as follows: **D-a/GDPR depth (P2/P3)** → IAM-prescribed two-phase `account.deleted` consumer (grace `anonymized=false` → status withdrawal; post-grace `anonymized=true` → user-service **profile** PII anonymization), aligning ecommerce to the *existing* TASK-BE-258 / consumer-integration-guide obligation it currently violates; the **order-service PII cascade is an explicit documented deferred follow-up** (v1 = profile store only, F2 boundary recorded — NOT silent). **D-b/id mapping (P4)** → `account.{created,deleted}.accountId` = `profile.userId` = OIDC `sub`; carry the verify-don't-assume obligation into implementation. **D-c/idempotency+availability (P5)** → fail-soft consumers + natural idempotency via the monotonic ACTIVE→WITHDRAWN→anonymized transition (relax `withdrawProfile()`'s `throw` to a no-op on missing/already-terminal); no `processed_event` store in v1. **D-d/grace timing (P2)** → branch on the event's own `anonymized` flag, NOT a self-scheduled `gracePeriodEndsAt` timer (the producer re-emits at grace end). **Onboarding (P1)** → re-point to `account.created` creating a **minimal** profile; onboarding PII comes from the OIDC token / profile-update, NOT the (emailHash-only) lifecycle event — a behavior change from the old raw-`email`+`name` signup event. Proceed dependency-correct **M1→M2→M3→M4** per ADR-037 § 3.3.

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
