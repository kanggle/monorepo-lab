# Event Contract — outbound-service subscriptions (cross-project: ← ecommerce)

Implements **ADR-MONO-022** (ecommerce ↔ wms order-fulfillment integration), D1 forward leg.

`outbound-service` subscribes to an **ecommerce-platform** event so a storefront purchase
creates a warehouse outbound order automatically — making ecommerce a **second external order
source** alongside the existing ERP webhook and manual REST entry.

> **Scope boundary (ADR-022 § 1.4).** This does NOT make wms a commerce system. wms's PROJECT.md
> "ecommerce out of scope" is about *domain responsibility*, not *order sources* — wms already
> accepts external orders (ERP webhook). This consumer adds **no** commerce logic: it resolves
> codes → uuids (as the webhook does) and calls the **existing** `ReceiveOrderUseCase`. The ACL
> (SKU/partner/warehouse vocabulary translation) lives on the ecommerce side.

Authoritative producer schema: `projects/ecommerce-microservices-platform/specs/contracts/events/fulfillment-events.md`.

---

## Consumer Group

`outbound-service` (the existing outbound consumer group — same as the inventory saga-reply consumers).

## Subscribed Topic

| Topic | ecommerce event | Handler (new) | Effect |
|---|---|---|---|
| `ecommerce.fulfillment.requested.v1` | `ecommerce.fulfillment.requested` | `FulfillmentRequestedConsumer` | Resolve codes→uuids via `MasterReadModelPort`; build `ReceiveOrderCommand` (`source = FULFILLMENT_ECOMMERCE`, additive `shipTo`); call `ReceiveOrderUseCase.receive(...)` → Order + saga created, `outbound.order.received` + `outbound.picking.requested` emitted in the same TX. |
| `ecommerce.shipping.manual-confirm-requested.v1` | `ecommerce.shipping.manual-confirm-requested` | `ManualShipConfirmConsumer` (new) | Resolve `orderNo`→Order via `findByOrderNo`; **iff** saga `PACKING_CONFIRMED` call the existing `ConfirmShippingUseCase` → `wms.outbound.shipping.confirmed.v1` → inventory deduction. ADR-MONO-022 D4 v2(c). See § Manual ship-confirm. |
| `ecommerce.fulfillment.cancel-requested.v1` | `ecommerce.fulfillment.cancel-requested` | `FulfillmentCancelRequestedConsumer` (new) | Resolve `orderNo`→Order via `findByOrderNo`; **iff** order `RECEIVED`/`PICKING` call the existing `CancelOrderUseCase` with the **WRITE role only** → existing `wms.outbound.order.cancelled.v1` (+ `wms.outbound.picking.cancelled.v1` if a reservation is held). Post-pick ⇒ `ALERT`, no cancel. ADR-MONO-022 D4 v2(d), X-A. See § Ecommerce-initiated cancel. |

## Envelope

ecommerce emits this cross-project event in the **wms envelope convention** (camelCase
`eventId`/`eventType`/`occurredAt`/`aggregateType`/`aggregateId`/`payload`) **by ACL design**, so
this consumer reuses the existing `EventEnvelopeParser` + `outbound_event_dedupe` (T8) unchanged.

## Payload consumed

```json
{
  "orderNo": "<ecommerce orderId — stored as wms Order.orderNo (correlation key, D5)>",
  "customerPartnerCode": "ECOMMERCE-STORE",
  "warehouseCode": "<default warehouse code>",
  "requiredShipDate": null,
  "shipTo": { "recipientName": "...", "address": "...", "phone": "..." },
  "lines": [ { "lineNo": 1, "skuCode": "...", "lotNo": null, "qtyOrdered": 2 } ]
}
```

## Tenant correlation passthrough (ADR-022 facet d, TASK-MONO-296)

The inbound `ecommerce.fulfillment.requested.v1` envelope carries an
**envelope-level `tenantId`** (the ecommerce tenant, M5). `FulfillmentRequestedConsumer`
**captures** it from the envelope and threads it through `ReceiveOrderCommand` →
the wms outbound `Order`, where it is persisted as an **additive, nullable
correlation column** (`outbound_order.tenant_id`) — exactly like `orderNo` (D5)
and `shipTo` (D2-a): wms stores it without interpreting it. On the return leg the
stored `tenantId` is **echoed** onto the `wms.outbound.shipping.confirmed.v1` and
`wms.outbound.order.cancelled.v1` envelopes so the ecommerce consumers re-bind the
originating tenant.

> **wms stays single-tenant (ADR-MONO-030 §1.1, AC-6).** `tenant_id` is an opaque
> correlation field, NOT an isolation key: **no** `NOT NULL` constraint, **no** row
> filtering by tenant, **no** tenant-gate change. `TenantClaimValidator` and the
> repositories are untouched in their isolation behavior. `null` when the inbound
> event omits a tenant (standalone ecommerce / pre-M5 producer — D8).

## Code → uuid resolution (mirrors the ERP webhook)

| Incoming code | Resolver (`MasterReadModelPort`) | Failure |
|---|---|---|
| `customerPartnerCode` (`ECOMMERCE-STORE`) | `findPartnerByCode` → must be ACTIVE + can-receive | unknown/inactive → DLT + alert |
| `warehouseCode` | `findWarehouseByCode` → must be ACTIVE | unknown/inactive → DLT + alert |
| `lines[].skuCode` | `findSkuByCode` → must be ACTIVE | unknown/inactive → DLT + alert |
| `lines[].lotNo` (nullable) | `findLotBySkuAndLotNo` when present | unknown → DLT + alert |

Seed requirement (demo + v1): `ECOMMERCE-STORE` partner (type CUSTOMER/BOTH) + the default
warehouse + the ecommerce-mapped SKUs must exist ACTIVE in wms master before fulfillment flows.

## Order intake (additive `shipTo`)

`ReceiveOrderCommand` gains an additive optional `shipTo` (recipientName/address/phone). When
present (ecommerce origin), it is persisted on the outbound Order (new nullable columns) and
echoed into `outbound.order.received` (ADR-022 D2-a). ERP-webhook / manual orders pass `shipTo = null`
(unchanged). `source` enum gains `FULFILLMENT_ECOMMERCE` (additive).

## Idempotency / Retry / DLT

- Dedupe on envelope `eventId` (`outbound_event_dedupe`, T8) — re-delivery is a no-op.
- Order-level idempotency: `existsByOrderNo(orderNo)` — a re-sent fulfillment for an existing
  `orderNo` is a safe no-op (same guard the webhook/manual path uses).
- Retry then `<topic>.DLT`. Unparseable / unresolved-code / inactive-master → non-retryable → DLT + alert.

## Return leg (this service → ecommerce)

On ship, `outbound-service` emits `wms.outbound.shipping.confirmed.v1` (existing) with the
**additive `orderNo`** (D5) and the **additive envelope `tenantId`** (facet d, echoed from the
stored correlation column) so ecommerce correlates and re-binds the originating tenant. On reserve-failure — inventory-service emits
`inventory.reserve.failed` (TASK-MONO-196), `InventoryReserveFailedConsumer` advances the saga to
`RESERVE_FAILED`, the order goes `BACKORDERED`, and the coordinator emits
`wms.outbound.order.cancelled.v1` carrying `orderNo` + `reason=INSUFFICIENT_STOCK`
+ the echoed envelope `tenantId` (facet d). Both are
authoritative in `outbound-events.md`; consumed by ecommerce per `wms-shipment-subscriptions.md`.
On the ecommerce side the cancel signal now (ADR-022 §D4 v2(a), TASK-MONO-197) **auto-cancels the
ecommerce order + refunds** (order-service consumer), in addition to the ops alert (shipping-service)
— wms emits the same event either way; the ecommerce-side reaction is wms-opaque.

## Manual ship-confirm (ADR-MONO-022 D4 v2(c), TASK-MONO-305)

A second subscribed topic — `ecommerce.shipping.manual-confirm-requested.v1` — lets an
ecommerce operator who hand-ships an order (manual `PUT /api/shippings/{id}/status` →
SHIPPED) **opt in** to also deducting wms physical inventory. This closes the
reverse-direction gap: without it, a WMS-routed order shipped by hand leaves its wms
reservation `RESERVED` forever (D4 v2(b) only reconciles the wms→ecommerce direction).

New `ManualShipConfirmConsumer` (`@Profile("!standalone")`, group `outbound-service`) —
same envelope convention + `EventEnvelopeParser` + `outbound_event_dedupe` (T8) as the
fulfillment consumer:

1. Resolve `payload.orderNo` → outbound `Order` via `OrderJpaRepository.findByOrderNo`.
   **Miss ⇒ no-op** (the order never routed through wms — legitimate; debug log, no DLT).
2. Load the saga by `order.id`:
   - `PACKING_CONFIRMED` ⇒ call the **existing** `ConfirmShippingUseCase.confirm`
     (carrierCode from the event; blank ⇒ wms default) → existing
     `wms.outbound.shipping.confirmed.v1` → existing inventory deduction. **No new
     inventory path; inventory data model untouched.**
   - already `SHIPPED`/`COMPLETED` ⇒ terminal no-op (already deducted; idempotent).
   - any earlier state ⇒ WARN no-op (not yet physically picked/packed — a forced
     deduction would violate the physical invariant; never a silent forced skip).
3. The ecommerce Order/Shipping are **already** SHIPPED (operator path); the return-leg
   `wms.outbound.shipping.confirmed.v1` → ecommerce `markShippedByOrderId` is an
   idempotent no-op (no loop).

Idempotency: envelope `eventId` dedupe (T8) + saga terminal-state guard. Unparseable
envelope / missing `orderNo` ⇒ non-retryable ⇒ DLT. Authoritative producer schema:
`projects/ecommerce-microservices-platform/specs/contracts/events/fulfillment-events.md` § Event 2.

## Ecommerce-initiated cancel (ADR-MONO-022 D4 v2(d), X-A — TASK-BE-650)

A third subscribed topic — `ecommerce.fulfillment.cancel-requested.v1` — carries an
ecommerce order cancel that happened after `CONFIRMED`, while the ecommerce Shipping row
was still `PREPARING` and `wmsRouted`. Until now cancellation ran only wms → ecommerce
(D4 v2(a)); without this wire a cancelled, refunded ecommerce order could still be
picked, packed and shipped.

New `FulfillmentCancelRequestedConsumer` (`@Profile("!standalone")`, group
`outbound-service`) — same envelope convention + `EventEnvelopeParser` +
`outbound_event_dedupe` (T8) as the two consumers above.

Payload consumed: `orderNo` (required), `tenantId` (opaque, facet d — never gated on),
`reason` (`ORDER_CANCELLED`; absent ⇒ `ORDER_CANCELLED`), `requestedAt` (informational).

1. Resolve `payload.orderNo` → outbound `Order` via `findByOrderNo`.
   **Miss ⇒ no-op** (never routed through wms; debug log, no DLT).
2. Decide by the **order** status (`OrderStatus`, not the saga status):

| wms outbound order status | Effect |
|---|---|
| `RECEIVED` · `PICKING` | Auto-cancel: call the **existing** `CancelOrderUseCase.cancel` with `callerRoles = {ROLE_OUTBOUND_WRITE}` (never `ROLE_OUTBOUND_ADMIN`), `expectedVersion = -1` (system trigger, no `If-Match`), `actorId = system:fulfillment-cancel-request`, `reason` = payload `reason`. Existing compensation (`wms.outbound.picking.cancelled.v1` → inventory releases the reservation) and existing `wms.outbound.order.cancelled.v1` emission, **unchanged**. |
| `PICKED` · `PACKING` · `PACKED` | **Not cancelled.** `ALERT` log (ERROR level) naming `orderNo` + status. A wms admin (`ROLE_OUTBOUND_ADMIN`) cancels through the existing admin path once goods are physically put back. Not a DLT. |
| `SHIPPED` | Logged no-op (return flow). Not a DLT. |
| `CANCELLED` · `BACKORDERED` | No-op (already terminal). |

**Why the WRITE role is fixed, and why D9 does not widen it.** `CancelOrderService` has two
independent guards. The **role** guard reads only the command's `callerRoles` — post-pick
cancel requires `ROLE_OUTBOUND_ADMIN`. The **tenant-visibility** guard (D9 `CallerScope`)
reads the security context, and an internal caller with no security context is
*unrestricted* — that widens **which tenants' orders** it may see, never **which states it
may cancel**. Supplying exactly `{ROLE_OUTBOUND_WRITE}` therefore makes the service itself
refuse a post-pick cancel (`AccessDeniedException`) even if the order moves to `PICKED`
between the consumer's status read and the service's own read. That refusal is retried;
on retry the consumer reads the new status and ends with the `ALERT`. The REST cancel path
is untouched.

Echo: the auto-cancel's `wms.outbound.order.cancelled.v1` returns to ecommerce, where
order-service no-ops an already-`CANCELLED` order and shipping-service suppresses the ALERT
for an already-`CANCELLED` Shipping row (`TASK-BE-649`). No loop.

Idempotency: envelope `eventId` dedupe (T8) + order terminal-state guard (already
`CANCELLED` ⇒ no-op). Unparseable envelope / missing `orderNo` ⇒ non-retryable ⇒ DLT.
Authoritative producer schema:
`projects/ecommerce-microservices-platform/specs/contracts/events/fulfillment-events.md` § Event 3.

> 🔴 **Known window — no cross-topic ordering.** If this event is consumed before the same
> order's `ecommerce.fulfillment.requested.v1`, step 1 misses and the order is created
> afterwards with nothing left to cancel it. Recorded, not closed, in v1.

## Standalone-publish degradation (ADR-022 D8)

Without ecommerce present, this topic never arrives; wms runs on ERP-webhook/manual order intake
exactly as today. No hard dependency.
