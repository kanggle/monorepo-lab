# Task ID

TASK-MONO-196

# Title

ADR-MONO-022 §D4 — BACKORDER / insufficient-stock return path: wms auto-backorder cancel-event emission + contract reconciliation + e2e branch (the negative-leg counterpart of TASK-MONO-195's happy path).

# Status

ready

# Owner

(unassigned) — cross-project (wms outbound-service + inventory-service producer side + ecommerce shipping-service consumer test + cross-project e2e). 분석=Opus 4.8 / 구현 권장=Opus (saga negative-branch + cross-project event emission + contract reconciliation = complex domain work per CLAUDE.md model-routing).

# Task Tags

- feat
- test

---

# Dependency Markers

- **선행**: TASK-BE-340 + TASK-BE-341 (ADR-MONO-022 happy path, merged) + TASK-MONO-195 (happy-path e2e harness, merged `f663c1fe` — this task extends it with the backorder branch).
- **맥락**: ADR-MONO-022 §D4 (BACKORDER decision + dual-inventory v1), §D5 (orderNo correlation), §D8 (graceful degradation). ADR-MONO-010/011 (e2e tag taxonomy + nightly).

# Goal

Make the **insufficient-stock / backorder** leg of the storefront→warehouse loop actually fire end to end: when wms cannot reserve inventory for an ecommerce-origin order, the wms outbound order transitions to BACKORDERED **and emits `wms.outbound.order.cancelled.v1` (carrying `orderNo`)**, which the existing ecommerce `WmsOutboundCancelledConsumer` consumes (v1 = ops alert + Shipping stays PREPARING-flagged per ADR-MONO-022 §D4). Today this path is dead: the BACKORDERED transition is never triggered in production and emits nothing.

# Background (current-state gaps — verified 2026-06-08)

- ✅ **ecommerce consumer exists**: `shipping-service` `WmsOutboundCancelledConsumer` (`wms.outbound.order.cancelled.v1`, group `shipping-service-wms`) dedupes + logs ops alert, leaves Shipping PREPARING — correct for v1. **No test.**
- ✅ **wms emit primitive exists**: `OrderCancelledEvent` (eventType `outbound.order.cancelled`, carries `orderNo`) + `CancelOrderService` emit it on **explicit REST cancel**.
- ✅ **wms domain transition exists**: `OutboundSagaCoordinator.onReserveFailed()` → saga `RESERVE_FAILED` + order `BACKORDERED` (unit-tested) — **but never called in production**.
- ❌ **GAP 1 — no trigger**: no `InventoryAdjustedConsumer` in outbound-service to route `inventory.adjusted`(reason=`INSUFFICIENT_STOCK`) from inventory-service to `onReserveFailed()`. `InventoryReservedConsumer` handles only the positive branch. Order stays `PICKING`, saga stuck `REQUESTED`.
- ❌ **GAP 2 — no emission on auto-backorder**: `onReserveFailed()` transitions state but emits no event → ecommerce can never learn of the backorder (it cannot poll wms saga state).
- ❌ **GAP 3 — contract contradiction**: `wms-platform/specs/contracts/events/outbound-events.md` (§ "Not In v1") says *"no separate backordered event — implicit from saga state"*, while `ecommerce-fulfillment-subscriptions.md` says *"on reserve-failure the order goes BACKORDERED and `wms.outbound.order.cancelled.v1` carries orderNo"*. These conflict; the cross-project consumer makes the ecommerce-side reading authoritative.
- ❌ **inventory-service producer side unconfirmed**: impl must verify (and wire if absent) that inventory-service emits `inventory.adjusted`(INSUFFICIENT_STOCK), or whatever reservation-shortfall signal outbound-service will consume.

# Scope

## In Scope
1. **Contract reconciliation (specs-first)**: resolve GAP 3 — `outbound-events.md` declares that an auto-backorder (saga RESERVE_FAILED) **emits `wms.outbound.order.cancelled.v1`** with `orderNo` + a `reason` distinguishing INSUFFICIENT_STOCK/BACKORDERED from a manual cancel; fix the §C1 consumed-events table (the "InventoryReservedConsumer (negative branch)" handler that doesn't exist) to name the real consumer; align `ecommerce-fulfillment-subscriptions.md` + `wms-shipment-subscriptions.md`.
2. **wms trigger (GAP 1)**: add the outbound-service consumer that routes the inventory reservation-shortfall signal (`inventory.adjusted` reason=`INSUFFICIENT_STOCK`, or the reconciled topic) → `OutboundSagaCoordinator.onReserveFailed()`. Idempotent (T8 dedupe), non-retryable on bad data → DLT.
3. **wms emission (GAP 2)**: `onReserveFailed()` (or its caller) emits `outbound.order.cancelled` via the outbox with `orderNo` + backorder reason.
4. **inventory-service producer**: confirm/realize the `inventory.adjusted`(INSUFFICIENT_STOCK) emission on a reservation shortfall (scope: only what's needed to make the outbound consumer fire).
5. **Tests**: wms IT for the reserve-fail → BACKORDERED → cancel-event-emitted path (Testcontainers, authoritative gate per the §14 testing lesson); ecommerce unit/IT for `WmsOutboundCancelledConsumer` (currently untested).
6. **e2e backorder branch**: extend `FulfillmentLoopE2ETest` (or a sibling in `projects/ecommerce-microservices-platform/tests/e2e/`) — synthesise the wms `outbound.order.cancelled.v1` for a PREPARING order → assert Shipping stays PREPARING (flagged, not SHIPPED) + Order NOT shipped. `@Tag("full")` (nightly).

## Out of Scope
- Auto-refund / auto-cancel saga on the ecommerce side (ADR-MONO-022 §D4 explicitly v2 — v1 is alert-only, Shipping stays PREPARING).
- Order→CANCELLED/BACKORDERED status mutation on the ecommerce side (v1 keeps Order as-is; alert only).
- Product↔wms inventory reconciliation (D4 v2, independent SoT in v1).

# Acceptance Criteria

- AC-1: A wms outbound order that fails inventory reservation transitions to BACKORDERED **and** emits `wms.outbound.order.cancelled.v1` carrying `orderNo` + backorder reason (wms IT asserts state + outbox row).
- AC-2: ecommerce `WmsOutboundCancelledConsumer` consumes it, dedupes, surfaces the ops alert, leaves Shipping PREPARING (covered by a new test).
- AC-3: The two contract files no longer contradict; `outbound-events.md` §C1 names a consumer that actually exists; `orderNo` correlation documented on the cancel path.
- AC-4: e2e backorder branch asserts the negative leg (`@Tag("full")`, nightly; graceful skip without Docker).

# Related Specs

- `docs/adr/ADR-MONO-022-ecommerce-wms-fulfillment-integration.md` §D4, §D5, §D8.
- ADR-MONO-010 / ADR-MONO-011 (e2e tag taxonomy + nightly cadence).

# Related Contracts

- `projects/wms-platform/specs/contracts/events/outbound-events.md` (`outbound.order.cancelled` emit spec + §C1 consumed-events table + "Not In v1" backordered note — **the contradiction to reconcile**).
- `projects/wms-platform/specs/contracts/events/ecommerce-fulfillment-subscriptions.md` (wms consumer view — claims cancel-on-reserve-fail).
- `projects/ecommerce-microservices-platform/specs/contracts/events/wms-shipment-subscriptions.md` (ecommerce consumer view — alert-only v1).
- `projects/wms-platform/specs/contracts/events/inventory-events.md` (the `inventory.adjusted` producer contract — confirm INSUFFICIENT_STOCK shape).

# Edge Cases

- Manual REST cancel vs auto-backorder must be distinguishable by `reason` so the ecommerce alert can phrase correctly (both ride `outbound.order.cancelled`).
- Idempotency: duplicate `inventory.adjusted` must not double-cancel; duplicate cancel event must not double-alert (T8 dedupe both sides).
- Reserve-fail allowed only from pre-SHIPPED saga states (post-SHIPPED cancel forbidden in v1 per `outbound-events.md`).
- inventory-service may not currently emit INSUFFICIENT_STOCK on shortfall — confirm before wiring the consumer (the trigger may need realizing on the producer side).

# Failure Scenarios

- Wiring the consumer without the inventory-service producer actually emitting the shortfall signal → the path stays dead (silent). The wms IT must drive the real reservation-shortfall, not just unit-call `onReserveFailed()`.
- Emitting on auto-backorder but leaving the contract saying "no event" → spec drift re-opens. Reconcile contracts in the same PR (specs-first).
- Putting the e2e in the fast PR lane → CI blowout. Must be `@Tag("full")` nightly (AC-4).
