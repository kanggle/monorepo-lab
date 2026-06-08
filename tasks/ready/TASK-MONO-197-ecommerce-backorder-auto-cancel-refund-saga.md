# Task ID

TASK-MONO-197

# Title

ADR-MONO-022 §D4 v2(a) — ecommerce-side auto-cancel + refund saga on warehouse backorder: order-service consumes `wms.outbound.order.cancelled.v1` → Order CONFIRMED→CANCELLED → existing `order.cancelled` refund fan-out (the v2 realization of MONO-196's alert-only v1 return leg).

# Status

ready

# Owner

(unassigned) — ecommerce-internal code (order-service consumer + system-cancel service) under a monorepo ADR amendment (cross-project return-leg semantics). 분석=Opus 4.8 / 구현 권장=Opus (cross-project saga realization + idempotent system-initiated cancel + reuse of the existing refund/promotion fan-out = complex domain work per CLAUDE.md model-routing).

# Task Tags

- feat
- test

---

# Dependency Markers

- **선행**: TASK-MONO-196 (DONE `b3be3d4b` — wms now actually emits `wms.outbound.order.cancelled.v1` with `orderNo` + `reason=INSUFFICIENT_STOCK` on auto-backorder) + TASK-MONO-195 (DONE `f663c1fe` — ecommerce-owned fulfillment e2e harness this task extends).
- **맥락**: ADR-MONO-022 §D4 ("Auto-refund/auto-cancel saga = v2 (named, not built)") — this task realizes v2(a). §D4 v2(b) (product↔wms inventory reconciliation) stays named/未built. ADR-MONO-010/011 (e2e tag taxonomy + nightly).

# Goal

Close the customer-facing half of the backorder leg. After MONO-196, when wms cannot reserve physical stock it auto-backorders and emits `wms.outbound.order.cancelled.v1` — but on the ecommerce side **only `shipping-service` consumes it, alert-only** (Order stays CONFIRMED, payment stays captured, customer is never refunded; manual ops intervention required). This task makes the ecommerce order **auto-cancel + auto-refund**: `order-service` consumes the same wms event, transitions the Order to CANCELLED (system-initiated), and emits the existing `order.cancelled` event — which the **already-wired** `payment-service` (refund) + `promotion-service` (coupon restore) fan-out consumes. No new payment/refund machinery is built; the v2 work is the missing **trigger** into the existing cancel→refund saga.

# Background (current-state — verified 2026-06-08)

- ✅ **wms emits** `wms.outbound.order.cancelled.v1` (orderNo, previousStatus, reason=INSUFFICIENT_STOCK, cancelledAt) on auto-backorder (MONO-196).
- ✅ **ecommerce cancel→refund saga already complete**: `OrderCancellationService` (user path) → `order.cancel()` → publish `OrderCancelled` → `payment-service` `OrderCancelledEventConsumer` (`order.order.cancelled`) → `PaymentRefundService.refundPayment` → `payment.payment.refunded` → order-service `PaymentRefundedEventConsumer` → `order.markRefunded()`. `promotion-service` also consumes `order.order.cancelled` (coupon restore).
- ✅ **Order domain supports it**: `Order.cancel(clock)` is valid from CONFIRMED (`OrderStatus.isCancellable()` = PENDING|CONFIRMED); `markRefunded` requires CANCELLED. At backorder time the order is CONFIRMED (payment done, fulfillment requested) → reuse as-is.
- ❌ **GAP — no trigger into the saga**: nothing in `order-service` consumes `wms.outbound.order.cancelled.v1`. The order never cancels; the refund saga never fires.
- ⚠️ **`OrderCancellationService.cancelOrder` is user-scoped** (requires `requestingUserId == order.userId`) — cannot be reused for a system-initiated backorder cancel. A system path is needed.
- ⚠️ **ShippingStatus has no terminal CANCELLED** (PREPARING/SHIPPED/IN_TRANSIT/DELIVERED) and at backorder time **no ecommerce Shipping row exists yet** (Shipping is created on the wms shipping-confirmed leg) → `shipping-service` stays alert-only (MONO-196), unchanged. No enum/migration.

# Scope

## In Scope
1. **order-service consumer** `WmsOutboundCancelledConsumer` — `@KafkaListener(topics="wms.outbound.order.cancelled.v1", groupId="order-service-wms")`, `@Profile("!standalone")`. Parses the **wms camelCase envelope** (own DTO, mirroring shipping-service's). Dedupe on `eventId` (`EventDeduplicationChecker`, T8). Null-payload + blank-`orderNo` guards → DLT/skip. Delegates to (2).
2. **system-initiated cancel service** `OrderBackorderCancellationService.cancelForBackorder(orderId, reason)` — no userId ownership check; status-aware + idempotent:
   - order not found → log warn + skip (no fabricated order),
   - already CANCELLED → no-op (idempotent re-delivery),
   - SHIPPED/DELIVERED/STUCK_RECOVERY_FAILED → **ALERT log + skip** (cannot cancel a shipped order — contract anomaly, never auto-mutate),
   - PENDING/CONFIRMED → `order.cancel(clock)` + save + `metrics.recordOrderCancelled("backorder")` + `recordStatusTransition` + publish `OrderCancelled` (which fires the existing refund + promotion fan-out).
3. **Correlation**: locate the Order by `orderId == payload.orderNo` (ADR-022 §D5). No wms↔ecommerce id map.
4. **shipping-service**: javadoc-only update on the existing alert-only `WmsOutboundCancelledConsumer` noting that order cancellation/refund is now owned by order-service (v2); behavior unchanged.
5. **Contracts (specs-first)**: `wms-shipment-subscriptions.md` (ecommerce) — add the order-service consumer row + flip the "auto-refund/cancel saga = v2" note to "v2 realized (TASK-MONO-197)"; `ecommerce-fulfillment-subscriptions.md` + `outbound-events.md` (wms) return-leg note updated from "ops alert" to "ecommerce auto-cancels + refunds".
6. **ADR-MONO-022 §D4**: ledger row + flip the v2(a) note from "named, not built" to realized in TASK-MONO-197 (v2(b) inventory reconciliation stays named).
7. **Tests**: order-service unit test for `OrderBackorderCancellationService` (each status branch + idempotency) + consumer test (dedupe, null payload, delegation); extend the ecommerce e2e backorder branch to assert **Order → CANCELLED** + `order.cancelled` emitted on the broker (the refund saga's trigger).

## Out of Scope
- Product↔wms inventory reconciliation (ADR-022 §D4 v2(b) — still named, independent SoT in v1).
- New refund/payment machinery — strictly reuse the existing `order.cancelled` → payment refund fan-out.
- A terminal Shipping CANCELLED state / Shipping enum + migration (no Shipping row exists at backorder time; shipping-service stays alert-only).
- Customer notification copy changes (the existing OrderCancelled notification path is reused as-is; differentiating "backorder refund" wording is a future enhancement).

# Acceptance Criteria

- AC-1: A `wms.outbound.order.cancelled.v1`(reason=INSUFFICIENT_STOCK, orderNo=X) consumed by order-service transitions Order X **CONFIRMED → CANCELLED** and publishes `order.cancelled` (carrying orderId=X). (order-service unit + e2e assert.)
- AC-2: The emitted `order.cancelled` drives the **existing** refund saga — `payment-service` refunds and order-service `markRefunded()` runs (covered by the existing cancel-path tests; the new path reuses the same event, so AC-2 is satisfied by emission + the unchanged downstream).
- AC-3: **Idempotent / status-safe** — re-delivery of the same wms event (same eventId) is a no-op (dedupe); a second distinct cancel for an already-CANCELLED order is a no-op; a backorder event for a SHIPPED/DELIVERED order does **not** mutate state and logs an ALERT.
- AC-4: Unknown/blank `orderNo` or null payload → skip + warn (no NPE, no fabricated order).
- AC-5: `shipping-service` behavior unchanged (still alert-only; no Shipping mutation) — verified by its existing MONO-196 test still passing.
- AC-6: Contracts + ADR-MONO-022 §D4 reconciled (no spec says "alert-only v1 / saga = v2 not built" for the order cancel/refund path anymore); ADR ledger row appended.
- AC-7: e2e `@Tag("full")` backorder branch asserts Order → CANCELLED (replacing the MONO-196 "stays CONFIRMED" assertion) + `order.cancelled` observed on the broker.

# Related Specs

- `docs/adr/ADR-MONO-022-ecommerce-wms-fulfillment-integration.md` (§D4 v2(a), §D5 correlation, §D8 degradation).
- `projects/ecommerce-microservices-platform/specs/contracts/events/wms-shipment-subscriptions.md` (ecommerce consumer contract — primary edit).
- `projects/ecommerce-microservices-platform/specs/contracts/events/order-events.md` (`order.cancelled` producer — unchanged, referenced).

# Related Contracts

- Consumed: `wms.outbound.order.cancelled.v1` (wms producer; authoritative schema in `projects/wms-platform/specs/contracts/events/outbound-events.md`).
- Produced (reused, unchanged): `order.order.cancelled` (`OrderCancelled`) — already consumed by payment-service + promotion-service.
- Reused downstream (unchanged): `payment.payment.refunded` → order-service `markRefunded`.

# Edge Cases

- Re-delivery (same wms `eventId`) → dedupe no-op (T8).
- Order already CANCELLED (user cancelled first, or duplicate distinct event) → no-op.
- Order SHIPPED/DELIVERED at event time (backorder-after-ship is impossible but defend) → ALERT + skip, never mutate.
- Order PENDING (payment not yet completed but a backorder somehow arrived) → still cancellable (`isCancellable()` includes PENDING); refund saga is a no-op if no payment captured (payment-service refund is idempotent on no-payment).
- Order not found (event for an order this instance never created — standalone/degraded) → warn + skip.
- Null payload / blank orderNo → warn + skip.
- Standalone profile (no wms, no Kafka) → consumer disabled (`@Profile("!standalone")`), no degradation (ADR-022 §D8).

# Failure Scenarios

- order-service down when the wms event arrives → Kafka redelivers; dedupe makes re-processing safe once back up.
- Refund leg (payment-service) down → the `order.cancelled` event sits on the topic; payment-service consumes on recovery (existing saga's existing resilience — unchanged by this task).
- Wms emits a malformed cancel envelope → parse failure → retry then DLT (existing consumer-error handling), no order mutation.
- A backorder event races the happy-path `wms.outbound.shipping.confirmed` for the same order (should never co-occur from a correct wms saga) → whichever lands first wins; if SHIPPED already applied, the cancel hits the SHIPPED-guard ALERT path (AC-3) and does not corrupt state.

# Impact on `projects/<name>/`

- `projects/ecommerce-microservices-platform/apps/order-service/` — new consumer + new service + DTO + tests (the only production code).
- `projects/ecommerce-microservices-platform/apps/shipping-service/` — javadoc-only on the existing consumer.
- `projects/ecommerce-microservices-platform/tests/e2e/` — backorder branch assertion upgraded.
- `projects/ecommerce-microservices-platform/specs/contracts/events/` — contract reconcile.
- `projects/wms-platform/specs/contracts/events/` — return-leg note.
- `docs/adr/ADR-MONO-022-*` — §D4 v2(a) realized + ledger row.

# Notes

Why a monorepo `TASK-MONO` (not an ecommerce-project task) though the code is ecommerce-internal: this realizes a **monorepo ADR (ADR-MONO-022 §D4)** decision and reconciles cross-project return-leg contracts, continuing the TASK-MONO-193/195/196 cross-project series. v2(a) was **pre-named** in the ADR ("Auto-refund/auto-cancel saga = v2") → this is a realization, not a new architecture decision (no self-ACCEPT gate); a ledger row records it.
