# TASK-ERP-BE-024 — Correct stale "notification fan-out v2-deferred" note in approval-api.md

**Status:** ready
**Type:** docs (spec accuracy)

## Goal

`approval-api.md` § "v2 deferred" lists **Notification fan-out** as "events are published in this increment (forward interface), but `notification-service` consumes them in v2." This is stale: TASK-ERP-BE-011 shipped four live consumers in notification-service (`ApprovalApprovedConsumer`, `ApprovalRejectedConsumer`, `ApprovalSubmittedConsumer`, `ApprovalWithdrawnConsumer`). Remove the item from "v2 deferred" and record it as now-in-scope.

## Scope

- `projects/erp-platform/specs/contracts/http/approval-api.md` — remove the "Notification fan-out" bullet from the "v2 deferred" list; add a "Now in scope" note naming the four consumers + TASK-ERP-BE-011. Leave genuinely-deferred items (IN_REVIEW, multi-stage routes, delegation, inbox filtering) intact.

## Acceptance Criteria

- [ ] **AC-1** — "Notification fan-out" no longer appears under "v2 deferred".
- [ ] **AC-2** — A note records notification fan-out as live, naming the four consumers + TASK-ERP-BE-011 + the events contract link.
- [ ] **AC-3** — The other four v2-deferred items remain unchanged. Docs-only.

## Related Specs / Contracts

- `projects/erp-platform/specs/contracts/http/approval-api.md` (corrected)
- `projects/erp-platform/specs/contracts/events/erp-approval-events.md`
- Impl: `apps/notification-service/.../messaging/Approval{Approved,Rejected,Submitted,Withdrawn}Consumer.java` (TASK-ERP-BE-011)

## Edge Cases / Failure Scenarios

- N/A — documentation accuracy correction; no runtime behavior. (Delegation events have their own consumers but the delegation *feature* remains v2 — that bullet is left untouched.)
