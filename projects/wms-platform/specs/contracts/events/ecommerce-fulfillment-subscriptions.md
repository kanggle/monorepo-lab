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
**additive `orderNo`** (D5) so ecommerce correlates. On reserve-failure — inventory-service emits
`inventory.reserve.failed` (TASK-MONO-196), `InventoryReserveFailedConsumer` advances the saga to
`RESERVE_FAILED`, the order goes `BACKORDERED`, and the coordinator emits
`wms.outbound.order.cancelled.v1` carrying `orderNo` + `reason=INSUFFICIENT_STOCK`. Both are
authoritative in `outbound-events.md`; consumed by ecommerce per `wms-shipment-subscriptions.md`.

## Standalone-publish degradation (ADR-022 D8)

Without ecommerce present, this topic never arrives; wms runs on ERP-webhook/manual order intake
exactly as today. No hard dependency.
