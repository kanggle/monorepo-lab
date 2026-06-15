# TASK-BE-387 — Remove stale ecommerce HTTP withdrawal endpoint from the contract (withdrawal is IAM-owned post-BE-132)

**Status:** review
**Type:** TASK-BE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Sonnet (net-zero contract correction — supersedes the NOT-IMPLEMENTED placeholder left by TASK-BE-386)

---

## Goal

Close out the last contract↔code gap from the ecommerce spec sweep. TASK-BE-386 (#1628) marked `POST /api/users/me/withdrawal` as **NOT IMPLEMENTED** because the documented HTTP endpoint has no controller. On investigation the endpoint is not merely unbuilt — it is **architecturally wrong post-IAM**: account lifecycle (withdrawal / deletion) is owned by the **IAM identity authority** (`iam-platform` account-service runs the GDPR-delete / status flow and emits the `account.deleted` domain event). A direct ecommerce profile-mutation HTTP endpoint would bypass IAM. This task **removes** the stale endpoint from the contract and documents the correct IAM-owned model + the (forward-declared, not-yet-wired) ecommerce reaction. Net-zero, spec-only, no `apps/**` change.

## Scope

**In scope:**

- `specs/contracts/http/user-api.md` — replace the `POST /api/users/me/withdrawal` endpoint section (request/response/errors) with a note: there is no consumer-facing ecommerce withdrawal endpoint; withdrawal/deletion is IAM-owned (`account.deleted`); the ecommerce reaction (`UserProfileService.withdrawProfile()` → `UserWithdrawn`) is forward-declared and **not yet wired** (the entry point is orphaned — no controller, no consumer).

**Out of scope:**

- All `apps/**` code — unchanged. The `withdrawProfile()` domain logic + `UserWithdrawn` event are left as-is (forward-declared entry point).
- **Building the `account.deleted` consumer** in user-service (the proper cross-project withdrawal wiring) — a separate, optional feature-completion task (IAM↔ecommerce account-lifecycle integration + idempotency + Testcontainers IT). Recorded here as the follow-up, not done.
- IAM account-service (`account.deleted` producer) — owned by iam-platform; cross-referenced only.

## Acceptance Criteria

- **AC-1** — `user-api.md` no longer documents a `POST /api/users/me/withdrawal` HTTP endpoint (no request/response/error schema for it).
- **AC-2** — The contract states withdrawal is IAM-owned (`account.deleted`) and that the ecommerce profile-withdrawal reaction is forward-declared / not-yet-wired (accurate to code: `withdrawProfile()` exists but is orphaned).
- **AC-3 (net-zero)** — no `apps/**` diff; only `user-api.md` + this task's lifecycle file change.

## Related Specs

- `specs/contracts/http/user-api.md` (the endpoint removed)
- `specs/contracts/events/user-events.md` (`UserWithdrawn` — the ecommerce event the forward-declared reaction would emit)
- `specs/integration/iam-integration.md` (IAM as identity authority post-BE-132)

## Related Contracts

- IAM `account.deleted` domain event (`iam-platform` account-service `AccountEventFactory` / `AccountOutboxPollingScheduler`).
- TASK-BE-132 (auth-service decommission / IAM delegation), TASK-BE-386 (the NOT-IMPLEMENTED placeholder this supersedes).

## Edge Cases

- **Orphaned entry point preserved** — `UserProfileService.withdrawProfile()` + `UserWithdrawn` stay in code (no deletion); they become the wiring target for the future `account.deleted` consumer. The contract documents this honestly rather than implying the path is live.
- **Consumer-facing UX** — a web-store "delete my account" action, if added later, calls IAM (via the gateway), not an ecommerce endpoint; the removal does not foreclose that.

## Failure Scenarios

- **F1 — re-introducing a bypass endpoint** — leaving the documented HTTP endpoint could lead an implementer to build a direct ecommerce withdrawal that bypasses IAM. Guarded by AC-1/AC-2 (removed + IAM-ownership documented).
- **F2 — over-deletion** — removing the `withdrawProfile()` code or the `UserWithdrawn` event would break the forward-declared reaction's future wiring. Guarded by AC-3 (spec-only; code untouched).
