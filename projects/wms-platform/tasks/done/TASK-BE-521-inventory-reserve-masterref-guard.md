# TASK-BE-521 — inventory-service: ReserveStockService missing master-ref guard (SKU_INACTIVE / LOT_INACTIVE / LOT_EXPIRED)

- **Type**: TASK-BE (bug fix — production correctness, sibling-parity straggler)
- **Status**: done
- **Service**: inventory-service (wms-platform)
- **Domain/traits**: wms / [transactional, event-driven, multi-tenant]
- **Analysis model**: Opus 4.8 · **Impl model**: Opus (domain-correctness fix)

## Goal

Fix a confirmed stock-integrity defect: `ReserveStockService.doReserve` does **not** validate master-ref
status before creating a reservation, so a reservation can be created against an inactive SKU or an
inactive/expired lot — violating the reservation guard the spec mandates and the invariant its 3 sibling
stock-mutation services already enforce.

**Verified (2026-07-19 wms test audit + orchestrator re-verification):**
- `ReserveStockService` constructor injects NO `MasterRefValidator` (grep for `masterRef|MasterRef|validate|Inactive|Expired` in the file = 0 hits); its sibling `AdjustStockService` (line 57) — and `TransferStockService`, `ReceiveStockService` — all inject and call `masterRefValidator.validate(...)`.
- The reserve ENTRY path `PickingRequestedConsumer` also has 0 master-ref refs — so no validation happens anywhere on the reserve path.
- Spec `state-machines/reservation-status.md` Guard Conditions table (the `(none) → RESERVED` rows) EXPLICITLY requires: "All `sku_id`s ACTIVE per MasterReadModel → else `SKU_INACTIVE` (rolled back, emits `inventory.reserve.failed{reason=SKU_INACTIVE}`)" and "All `lot_id`s non-EXPIRED and ACTIVE → else `LOT_INACTIVE` / `LOT_EXPIRED`".

This is a `project_enforcement_straggler_sibling_parity` straggler: N-1 stock-mutation services wired the guard, Reserve is the 1 unwired = a defect.

## Scope

- **In scope**: `inventory-service/.../application/service/ReserveStockService.java` (inject `MasterRefValidator`, call `validate(...)` inside the reserve transaction before mutating stock, on failure roll back and emit `inventory.reserve.failed{reason=SKU_INACTIVE|LOT_INACTIVE|LOT_EXPIRED}` exactly as the sibling services / spec prescribe) + its wiring (constructor, any `@Configuration`/bean) + a RED→GREEN test.
- **Out of scope**: the other Tier-1 fix (TASK-BE-522, release idempotency — separate file); the untested-but-not-buggy coverage gaps (Tier 2); refactor.

## Acceptance Criteria

- **AC-1**: `ReserveStockService` validates SKU-active + lot-active/non-expired via the SAME `MasterRefValidator` mechanism the sibling services use, inside the reserve transaction, BEFORE any `Inventory.reserve(...)` mutation.
- **AC-2**: on an inactive SKU / inactive lot / expired lot the reservation is rolled back (no stock mutated, no reservation row) and `inventory.reserve.failed{reason=SKU_INACTIVE|LOT_INACTIVE|LOT_EXPIRED}` is emitted — matching the spec's failure treatment (same shape as `INSUFFICIENT_STOCK`).
- **AC-3 (RED→GREEN)**: a new test drives a reserve against an inactive-SKU (and an expired-lot) fixture and asserts the reserve is rejected + the failure event emitted. Confirm it is RED before the fix (guard absent) and GREEN after (mutation-on-removal discipline). Prefer the layer the sibling services test at (application-service unit with the master-ref fake, PLUS an IT if the sibling pattern has one).
- **AC-4**: existing reserve happy-path + insufficient-stock + dedupe tests stay GREEN; `:inventory-service:test` (+ `:integrationTest` on the wms Testcontainers lane, authoritative) GREEN.
- **AC-5**: emitted `inventory.reserve.failed` reasons match the `reserve-events` / `reservation-status.md` enum exactly (no new code).

## Edge Cases / Failure Scenarios

- LOT-tracked vs non-lot SKUs: only lot-tracked lines get the lot guard (mirror how the sibling services branch).
- The validation must be INSIDE the transaction so a failure rolls back partially-applied line reservations atomically (per the spec "rolled back").
- Do NOT double-validate if the entry consumer is later given validation — keep the guard in the service (where the siblings keep it) so every reserve caller inherits it.

## Related

- Spec: `state-machines/reservation-status.md` (guard table), `sagas/reservation-saga.md`.
- Sibling implementations: `AdjustStockService` / `TransferStockService` / `ReceiveStockService` (the guard pattern to mirror) + their tests (e.g. `ReceiveStockServiceTest.expiredLotRejectsReceive`).
- Memory: `project_enforcement_straggler_sibling_parity` (N-1 wired + 1 unwired = defect), `feedback_deletion_leaves_survivors_grep_the_consumers` (verified the consumer path too), `project_testcontainers_docker_desktop_blocker` (CI wms lane authoritative).
