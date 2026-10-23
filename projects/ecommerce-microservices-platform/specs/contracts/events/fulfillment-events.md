# Event Contract: fulfillment (ecommerce → wms cross-project)

Implements **ADR-MONO-022** (ecommerce ↔ wms order-fulfillment integration), D1 forward leg.

`shipping-service` publishes a **fulfillment-intent** event when a Shipping record
enters `PREPARING` (i.e. an order is confirmed/paid and ready to be physically
shipped). The wms `outbound-service` consumes it and creates an outbound order.

This is the storefront→warehouse trigger: *buy in the web store → ship from the
connected warehouse*.

---

## Producer

`shipping-service` (transactional outbox; same `outbox` table + `OutboxPollingScheduler`
that publishes `ShippingStatusChanged`). The Anti-Corruption Layer (ACL) lives here
(ADR-MONO-022 D6): shipping-service translates ecommerce vocabulary into the
**wms-shaped** payload below before writing the outbox row.

## Topic

`ecommerce.fulfillment.requested.v1`

- Partition key: `orderNo` (the ecommerce order business id — preserves per-order ordering).
- Retention: ≥ 7 days. DLT: `<topic>.DLT` (consumer-side).

## Envelope — **wms-compatible (camelCase)**, by ACL design

> ⚠️ **Interop note.** Unlike ecommerce's own events (`event_id`/`event_type`/`payload`,
> snake-ish), this cross-project event is emitted in the **wms envelope convention**
> (camelCase `eventId`/`eventType`/`occurredAt`/`aggregateType`/`aggregateId`/`payload`)
> so the wms `outbound-service` consumes it with its **existing** `EventEnvelopeParser`
> + `outbound_event_dedupe` (T8) unchanged. Emitting the consumer's shape is the ACL's
> job (ADR-MONO-022 D6). The outbox row's `payload` column carries this whole envelope.

```json
{
  "eventId": "0192...-uuidv7",
  "eventType": "ecommerce.fulfillment.requested",
  "occurredAt": "2026-06-08T10:00:00.000Z",
  "aggregateType": "fulfillment",
  "aggregateId": "<ecommerce orderId>",
  "payload": { /* below */ }
}
```

## Payload

```json
{
  "orderNo": "<ecommerce orderId — round-trips as wms orderNo (ADR-022 D5)>",
  "customerPartnerCode": "ECOMMERCE-STORE",
  "warehouseCode": "<default warehouse code, config-driven (ADR-022 D3)>",
  "requiredShipDate": null,
  "shipTo": {
    "recipientName": "홍길동",
    "address": "서울특별시 ...",
    "phone": "010-0000-0000"
  },
  "lines": [
    {
      "lineNo": 1,
      "skuCode": "<wms SKU code, ACL-mapped from ecommerce product SKU>",
      "lotNo": null,
      "qtyOrdered": 2
    }
  ]
}
```

| Field | Type | Nullable | Notes |
|---|---|---|---|
| `orderNo` | string (1–40) | no | ecommerce order id. Becomes wms `orderNo`; the correlation key for the return leg (D5). MUST be globally unique on the wms side. |
| `customerPartnerCode` | string | no | Constant `ECOMMERCE-STORE` in v1 (the store = one wms customer partner, D2-a). Seeded ACTIVE in wms partner master. |
| `warehouseCode` | string | no | Default fulfillment warehouse code (config). Multi-warehouse routing = v2. |
| `requiredShipDate` | string (YYYY-MM-DD) \| null | yes | Optional. |
| `shipTo` | object \| null | yes | **B2C drop-ship recipient** (ADR-022 D2-a). Maps to wms outbound order's additive `shipTo`. Null ⇒ ship to the customer partner's default address (B2B fallback). |
| `shipTo.recipientName` | string | no (if shipTo present) | End consumer name. |
| `shipTo.address` | string | no (if shipTo present) | Full shipping address (single line, v1). |
| `shipTo.phone` | string \| null | yes | Recipient contact. |
| `lines[].lineNo` | int ≥ 1 | no | Unique within the request. |
| `lines[].skuCode` | string | no | **wms** SKU code — ACL-resolved from the ecommerce product SKU (D3 mapping). Unmapped SKU ⇒ ACL rejects to DLT + ops alert (never silently dropped). |
| `lines[].lotNo` | string \| null | yes | Usually null for ecommerce (any-lot). |
| `lines[].qtyOrdered` | int > 0 | no | EA. |

## Consumer (wms)

`outbound-service` — `FulfillmentRequestedConsumer` (consumer group `outbound-service`).
Resolves `customerPartnerCode`/`warehouseCode`/`skuCode` → uuids via `MasterReadModelPort`
(same code→uuid resolution the ERP webhook uses), then calls `ReceiveOrderUseCase.receive(...)`
with `source = FULFILLMENT_ECOMMERCE` and the additive `shipTo`. Authoritative consumer
contract: `projects/wms-platform/specs/contracts/events/ecommerce-fulfillment-subscriptions.md`.

## Delivery Semantics

- At-least-once (outbox). Consumer dedupes on envelope `eventId` (T8).
- Resolution failure (unknown partner/warehouse/SKU, inactive SKU) → **asynchronous** like
  the ERP webhook: event routed to DLT + ops alert. NOT a silent drop.
- Physical-stock-insufficient is **not** a producer concern — it surfaces on the wms side as
  `BACKORDERED` and flows back via the return leg (see `wms-shipment-subscriptions.md`).

## Standalone-publish degradation (ADR-022 D8)

If the ecommerce stack is published/run without wms, this event is simply unconsumed and the
Shipping record stays in `PREPARING` (today's manual-admin behavior). No hard dependency.

## Event 2 — `ecommerce.shipping.manual-confirm-requested.v1` (ADR-MONO-022 D4 v2(c))

Published when an operator manually marks a Shipping `SHIPPED` via
`PUT /api/shippings/{id}/status` **and** elects (`deductWmsInventory=true`) to also
deduct wms physical inventory, **and** the order is `wmsRouted` (the forward-leg
fulfillment event was actually published for it). Lets the operator reconcile wms
stock for a hand-shipped, warehouse-routed order without going through the wms
console. Same transactional outbox + `OutboxPollingScheduler` as Event 1.

### Topic
`ecommerce.shipping.manual-confirm-requested.v1`
- Partition key: `orderNo`. Retention ≥ 7 days. DLT: `<topic>.DLT` (consumer-side).

### Envelope — wms-compatible (camelCase), by ACL design (same as Event 1)
```json
{
  "eventId": "uuidv7",
  "eventType": "ecommerce.shipping.manual-confirm-requested",
  "occurredAt": "2026-06-25T10:00:00.000Z",
  "aggregateType": "shipping",
  "aggregateId": "<ecommerce orderId>",
  "tenantId": "<ecommerce tenant (facet d) | null>",
  "payload": { }
}
```

### Payload
```json
{
  "orderNo": "<ecommerce orderId == wms orderNo (D5 correlation key)>",
  "carrierCode": "<operator-entered carrier | null>",
  "trackingNo": "<operator-entered tracking number | null>"
}
```

| Field | Type | Nullable | Notes |
|---|---|---|---|
| `orderNo` | string | no | ecommerce orderId; resolves the wms outbound order via `findByOrderNo`. |
| `carrierCode` | string | yes | Operator-entered carrier. Passed to the wms ship-confirm; blank ⇒ wms default carrier resolution (does **not** block the deduction). |
| `trackingNo` | string | yes | Operator-entered tracking; informational on the wms side. |

### Consumer (wms)
`outbound-service` — `ManualShipConfirmConsumer` (group `outbound-service`,
`@Profile("!standalone")`). `findByOrderNo` → load saga → **iff `PACKING_CONFIRMED`**
call `ConfirmShippingUseCase` (→ `wms.outbound.shipping.confirmed.v1` → inventory
deduction). Authoritative consumer contract:
`projects/wms-platform/specs/contracts/events/ecommerce-fulfillment-subscriptions.md`.

### Delivery semantics / idempotency
- At-least-once (outbox). Consumer dedupes on envelope `eventId` (T8) + saga
  terminal-state guard (already-`SHIPPED` ⇒ no-op).
- `findByOrderNo` miss (non-WMS-routed order) ⇒ safe no-op (debug log), NOT a DLT.
- Saga not yet `PACKING_CONFIRMED` ⇒ WARN no-op (physically un-picked; never a forced skip).
- Unparseable envelope / missing `orderNo` ⇒ non-retryable ⇒ DLT.

## Event 3 — `ecommerce.fulfillment.cancel-requested.v1` (ADR-MONO-022 D4 v2(d))

Published when an ecommerce order is cancelled after `CONFIRMED` (customer, or
operator) and its Shipping row is still `PREPARING` **and** `wmsRouted` (the
forward-leg Event 1 was actually published for it). It asks wms to cancel the
outbound order it already holds. ADR-MONO-022 D4 v2(d), choices **X-A / Y-A**.

- **Producer:** `shipping-service`, on consuming `order.order.cancelled`. In the
  **same transaction** that moves the Shipping row `PREPARING → CANCELLED`, it writes
  this event to the outbox (same transactional outbox + `OutboxPollingScheduler` as
  Events 1 and 2). Producer implementation: `TASK-BE-649`.
- **Not emitted** when the Shipping row is `SHIPPED` or later (return flow, out of
  scope), when it is not `wmsRouted` (wms holds no outbound order), when the row is
  missing, or on a redelivered `order.order.cancelled`.
- **Best-effort (Y-A).** The ecommerce order cancel and the refund complete
  immediately, as before. This event does **not** wait for, or roll back on, the wms
  outcome. A wms refusal ends as an `ALERT` on the wms side; there is no ack event.

### Topic
`ecommerce.fulfillment.cancel-requested.v1`
- Partition key: `orderNo`. Retention ≥ 7 days. DLT: `<topic>.DLT` (consumer-side).
- 🔴 **No cross-topic ordering.** Event 1 and Event 3 are different topics, so Kafka does
  not order Event 3 after its own Event 1. The producer writes Event 1 first (the Shipping
  row is `wmsRouted` only after it), but if Event 3 is consumed before Event 1, wms finds no
  order (`findByOrderNo` miss ⇒ no-op) and then creates the order from Event 1 with nothing
  left to cancel it. This window is recorded, not closed, in v1 (TASK-BE-650 open question).

### Envelope — wms-compatible (camelCase), by ACL design (same shape as Event 2)
```json
{
  "eventId": "uuidv7",
  "eventType": "ecommerce.fulfillment.cancel-requested",
  "occurredAt": "2026-10-23T10:00:00.000Z",
  "aggregateType": "fulfillment",
  "aggregateId": "<ecommerce orderId>",
  "tenantId": "<ecommerce tenant (facet d) | null>",
  "payload": { }
}
```

### Payload
```json
{
  "orderNo": "<ecommerce orderId == wms orderNo (D5 correlation key)>",
  "tenantId": "<ecommerce tenant | null>",
  "reason": "ORDER_CANCELLED",
  "requestedAt": "2026-10-23T10:00:00.000Z"
}
```

| Field | Type | Nullable | Notes |
|---|---|---|---|
| `orderNo` | string | no | ecommerce orderId; resolves the wms outbound order via `findByOrderNo`. |
| `tenantId` | string | yes | ecommerce tenant. **Opaque correlation** on the wms side (facet d) — wms never gates on it. Same value as the envelope `tenantId`. |
| `reason` | string | no | Constant `ORDER_CANCELLED` in v1. wms records it as the outbound cancel reason, so it is echoed back on `wms.outbound.order.cancelled.v1`. |
| `requestedAt` | string (ISO-8601 instant) | no | When ecommerce accepted the order cancel. Informational on the wms side. |

### Consumer (wms)
`outbound-service` — `FulfillmentCancelRequestedConsumer` (group `outbound-service`,
`@Profile("!standalone")`). `findByOrderNo` → decide by the wms **order** status (X-A):

| wms outbound order status | Effect |
|---|---|
| `RECEIVED` · `PICKING` | Auto-cancel via the **existing** `CancelOrderUseCase`, with the **WRITE role only** (`ROLE_OUTBOUND_WRITE`; never `ROLE_OUTBOUND_ADMIN`). Existing compensation (`wms.outbound.picking.cancelled.v1` → reservation release) and existing `wms.outbound.order.cancelled.v1` emission, unchanged. |
| `PICKED` · `PACKING` · `PACKED` | **Not cancelled.** `ALERT` log naming `orderNo` + status. A wms admin (`ROLE_OUTBOUND_ADMIN`) cancels through the existing admin path once goods are put back. |
| `SHIPPED` | Logged no-op (return flow). |
| `CANCELLED` · `BACKORDERED` · unknown `orderNo` | No-op (already terminal / never routed through wms). |

Authoritative consumer contract:
`projects/wms-platform/specs/contracts/events/ecommerce-fulfillment-subscriptions.md` § Ecommerce-initiated cancel.

### Echo back to ecommerce (no loop)
A wms auto-cancel emits `wms.outbound.order.cancelled.v1` (existing). order-service
skips an order that is already `CANCELLED` (`OrderBackorderCancellationService`).
shipping-service's alert-only `WmsOutboundCancelledConsumer` must skip a Shipping row that
is already `CANCELLED`, so a self-requested cancel raises no false ALERT (`TASK-BE-649`).

### Delivery semantics / idempotency
- At-least-once (outbox). Consumer dedupes on envelope `eventId` (T8) + order
  terminal-state guard (already `CANCELLED` ⇒ no-op).
- `findByOrderNo` miss ⇒ safe no-op (debug log), NOT a DLT.
- A post-pick refusal is NOT an error (ALERT no-op), never a DLT.
- Unparseable envelope / missing `orderNo` ⇒ non-retryable ⇒ DLT.

---

## Not in v1

- Multi-warehouse routing (single default warehouse).
- Amendment of an already-requested fulfillment. (Cancellation now has a wire — Event 3,
  ADR-MONO-022 D4 v2(d) — for pre-pick wms orders; post-pick and shipped orders stay on
  the wms admin cancel path and the return flow.)
- Real carrier tracking-number assignment at request time (assigned by wms at ship).
