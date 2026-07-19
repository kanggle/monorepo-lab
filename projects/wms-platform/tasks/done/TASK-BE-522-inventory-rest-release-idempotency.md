# TASK-BE-522 — inventory-service: REST manual release not idempotent on an already-RELEASED reservation

- **Type**: TASK-BE (bug fix — production correctness, sibling-parity straggler)
- **Status**: done
- **Service**: inventory-service (wms-platform)
- **Domain/traits**: wms / [transactional, event-driven, multi-tenant]
- **Analysis model**: Opus 4.8 · **Impl model**: Opus (domain-correctness fix)

## Goal

Fix a confirmed idempotency defect: the REST manual-release path (`INVENTORY_ADMIN` →
`ReservationController.release` → `ReleaseReservationService.release`) has **no terminal-state pre-check**,
so a second release on an already-`RELEASED` reservation runs the per-line `inv.release(...)` loop and then
throws `StateTransitionInvalid` at `reservation.release(...)` → HTTP 422, instead of the spec-mandated
idempotent **200 no-op**.

**Verified (2026-07-19 wms test audit + orchestrator re-verification):**
- `ReleaseReservationService.release` (lines ~88-104) iterates `reservation.lines()` calling
  `inv.release(...)` and only then calls `reservation.release(...)` at ~L103 — with NO `if (status != RESERVED) return existing` guard before the loop.
- The sibling release-trigger paths DO pre-check: `PickingCancelledConsumer` and `ShippingConfirmedConsumer` both check terminal status before entering the use-case (per the audit). Only the REST path is the straggler.
- Spec `sagas/reservation-saga.md`: "If already `RELEASED` or `CONFIRMED` → no-op" (L125), "a second release attempt on a `RELEASED` row is a no-op" (L141), "Domain-level terminal-once: `release()` on a terminal row [is a] no-op" (L233). (Contrast: confirm-on-RELEASED → throw `RESERVATION_ALREADY_RELEASED` → DLT, which is correct and unchanged.)

Straggler-parity defect (`project_enforcement_straggler_sibling_parity`): consumers guard, REST doesn't.

## Scope

- **In scope**: `inventory-service/.../application/service/ReleaseReservationService.java` — add a terminal-state pre-check at the TOP of `release(...)` (before the line loop) that, when the reservation is already `RELEASED` (idempotent replay — same reason, or any reason per the spec's "no-op"), returns the existing reservation as a 200 no-op WITHOUT re-running `inv.release(...)` or re-emitting the event. Mirror the pre-check the consumer siblings already do. + a RED→GREEN test.
- **Out of scope**: TASK-BE-521 (reserve guard — separate); the CONFIRM path (confirm-on-RELEASED → throw is correct, do NOT change); the domain `Reservation.release()` throw behavior (keep it — the guard lives at the service layer where the sibling consumers keep it, minimal blast radius); Tier 2 coverage; refactor.

## Acceptance Criteria

- **AC-1**: `ReleaseReservationService.release` pre-checks terminal status BEFORE the `inv.release(...)` line loop; an already-`RELEASED` reservation returns 200 with the existing reservation state, no stock re-mutation, no duplicate `inventory.released` event.
- **AC-2**: a first release on a `RESERVED` reservation is unchanged (releases + emits event once).
- **AC-3 (RED→GREEN)**: a new test calls release twice on the same reservation and asserts the 2nd call is a 200 no-op (reserved/available quantities unchanged after the 2nd call, exactly ONE `inventory.released` event emitted). RED before the fix (2nd call → 422 / double-mutation-attempt), GREEN after. Verify at the layer the release path is tested (application-service unit; add/extend the REST slice or IT if that is where the sibling idempotency is proven).
- **AC-4**: the CONFIRM-on-RELEASED path still throws `RESERVATION_ALREADY_RELEASED` (unchanged — do not let the release no-op leak into confirm). Existing release/confirm tests stay GREEN. `:inventory-service:test` (+ wms Testcontainers `:integrationTest`, authoritative) GREEN.

## Edge Cases / Failure Scenarios

- Idempotent replay semantics: the spec says release-on-RELEASED is a no-op regardless of reason; return the stored reservation. Do NOT compare reasons in a way that turns a genuine replay into a 409 unless the spec requires it (it says no-op).
- The pre-check must be BEFORE the line loop — a check after the loop would still have double-mutated (and only a transaction rollback saves it, returning 422 not 200).
- Concurrent release vs TTL-sweep: the optimistic-lock version check (already present) still guards the genuine race; the new pre-check only handles the already-terminal replay.

## Related

- Spec: `sagas/reservation-saga.md` §8 (L125/141/233), `state-machines/reservation-status.md`.
- Sibling pattern: `PickingCancelledConsumer` / `ShippingConfirmedConsumer` terminal pre-check.
- Memory: `project_enforcement_straggler_sibling_parity`, `env_test_fixture_impossible_input_proves_nothing` (assert the quantity-unchanged property, not just the status), `project_testcontainers_docker_desktop_blocker`.
