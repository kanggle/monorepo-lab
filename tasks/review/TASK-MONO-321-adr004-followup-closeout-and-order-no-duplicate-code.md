# TASK-MONO-321 — Close the last two ADR-MONO-004 §6 follow-ups + reconcile `ORDER_NO_DUPLICATE` error code

**Status:** review

**Type:** monorepo-level (shared: `platform/`, `docs/adr/`; project touch: wms outbound-service)

---

## Goal

Three low-effort housekeeping items surfaced by the backlog-discovery sweep (2026-07-01),
bundled into one atomic PR:

1. **`ORDER_NO_DUPLICATE` error-code reconcile (wms outbound-service).**
   `OrderNoDuplicateException.errorCode()` returns the generic `"CONFLICT"`, but the
   outbound-service API spec (`state-machines/order-status.md`, `idempotency.md`,
   `erp-order-webhook.md`) and `platform/error-handling.md:218` all declare the code as
   `ORDER_NO_DUPLICATE`. The code violates its own published contract. HTTP status stays
   `409` (the `DOMAIN_STATUS` map already pins the class → `CONFLICT` status). Only the
   emitted `code` string changes. `platform/error-handling.md:218` already carries a
   "deferred housekeeping item" note pointing at this exact drift — removed here.

2. **ADR-MONO-004 §6 follow-up — `AdminEventDedupeRepository` fate (architectural call).**
   Resolved as **keep custom** (no code change). The lib `EventDedupePort` is a simple
   run-once wrapper; `AdminEventDedupeRepository` is a projection-specific contract
   (`markStale`/LWW-late, `countLifetime` for `/operations/projection-status`,
   `maxProcessedAtByEventType` for the lag probe). Folding those into the shared lib port
   would bloat the shared contract with service-specific semantics (shared-library-policy
   boundary). The §6 row is struck with this rationale.

3. **ADR-MONO-004 §6 follow-up — `BaseEventPublisher.java` javadoc cleanup.**
   **Obsolete:** `BaseEventPublisher.java` was removed by TASK-MONO-312 (lib outbox v1
   dead-code removal). No file remains to clean; the §6 row is struck as void.

This closes the last two open rows of ADR-MONO-004 §6 (the v1→v2 outbox sweep itself
completed 2026-06-27).

## Scope

### In Scope
- `OrderNoDuplicateException.errorCode()` `"CONFLICT"` → `"ORDER_NO_DUPLICATE"` + javadoc.
- New unit test asserting the granular code.
- `platform/error-handling.md:218` — remove the "deferred housekeeping item" note.
- `docs/adr/ADR-MONO-004-shared-messaging-scaffolding.md` §6 — strike rows 258 (keep-custom)
  and 259 (obsolete) with resolution rationale.

### Out of Scope
- Any behavioural change beyond the emitted `code` string (HTTP status, message, event
  wire, dedupe semantics all unchanged).
- ADR-MONO-004 §1–5 historical body (decision-time narrative — an ADR is a historical
  record; only the live §6 follow-up table is updated).
- Refactoring `AdminEventDedupeRepository` or the lib port (decision = keep as-is).

## Acceptance Criteria

- [ ] **AC-1** — `OrderNoDuplicateException.errorCode()` returns `"ORDER_NO_DUPLICATE"`;
  javadoc updated. HTTP status unchanged (`409`, via `DOMAIN_STATUS`).
- [ ] **AC-2** — Unit test asserts `errorCode()=="ORDER_NO_DUPLICATE"`; `outbound-service`
  `:test` GREEN (incl. `FulfillmentRequestedConsumerTest.duplicateOrderNoIsIdempotentNoOp`,
  which catches by type and is unaffected).
- [ ] **AC-3** — `platform/error-handling.md:218` no longer carries the housekeeping note;
  the row's `409` + description otherwise unchanged.
- [ ] **AC-4** — ADR-MONO-004 §6 rows for `AdminEventDedupeRepository` and
  `BaseEventPublisher.java` are struck with resolution rationale (keep-custom / obsolete).
  No §1–5 body change.
- [ ] **AC-5** — `grep -rn '"CONFLICT"'` in outbound-service shows only the genuinely-generic
  handlers (`OptimisticLockingFailureException`, `DataIntegrityViolationException`) — not
  `OrderNoDuplicateException`.

## Related Specs

- `platform/error-handling.md` § Outbound (authoritative code table; source of the drift note).
- `projects/wms-platform/specs/services/outbound-service/state-machines/order-status.md`,
  `idempotency.md` (already declare `ORDER_NO_DUPLICATE` — code now conforms).
- `docs/adr/ADR-MONO-004-shared-messaging-scaffolding.md` §6 (follow-up ledger).

## Related Contracts

- `outbound-service` API error codes (the `code` string is a client-visible contract;
  this aligns code to the already-published `ORDER_NO_DUPLICATE`).

## Edge Cases

- `FulfillmentRequestedConsumer` absorbs `OrderNoDuplicateException` as an idempotent no-op
  (catches the exception **type**, not the code string) — the rename does not affect it.
- The generic `DataIntegrityViolationException`/`OptimisticLockingFailureException` handlers
  still emit `"CONFLICT"` by design (they are not the domain duplicate path) — unchanged.

## Failure Scenarios

- **F1 — client parsing on the old code** — a client keying on `"CONFLICT"` for the
  duplicate-order path would need to switch to `ORDER_NO_DUPLICATE`. This is the intended
  correction (the spec always said `ORDER_NO_DUPLICATE`); `CONFLICT` was the bug.
- **F2 — ADR history rewrite** — over-editing ADR §1–5 would corrupt the decision record.
  Guarded by AC-4 (only §6 live table touched).

## Definition of Done

- [ ] AC-1…AC-5 satisfied
- [ ] `outbound-service` build + tests GREEN (local `:test`; CI wms Integration lane confirms)
- [ ] Shared changes (`platform/`, `docs/adr/`) pushed → draft PR verified
- [ ] Ready for review
