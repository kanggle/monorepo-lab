# Event Contract: procurement-service

## Overview

Domain events published by `procurement-service` (scm-platform). All events
flow through the **transactional outbox** (`libs/java-messaging`
`BaseEventPublisher` + `OutboxPollingScheduler` — see
[`procurement-service/architecture.md`](../../services/procurement-service/architecture.md)
§ Outbox + audit_log invariants).

Consumers MUST NOT depend on fields not declared in this contract. New
consumers SHOULD subscribe with `groupId` per the table below and
implement T8 idempotency keyed on `eventId` (UUID v7).

---

## Topics

Canonical Kafka topic names. Topic naming convention =
`<context>.<aggregate>.<event>` with `.v1` suffix per platform versioning.

| Event type | Kafka topic | Aggregate | Status | Trigger |
|---|---|---|---|---|
| `scm.procurement.po.submitted` | `scm.procurement.po.submitted.v1` | `purchase_order` | live | DRAFT → SUBMITTED transition (after supplier dispatch succeeds) |
| `scm.procurement.po.acknowledged` | `scm.procurement.po.acknowledged.v1` | `purchase_order` | live | SUBMITTED → ACKNOWLEDGED via supplier-ack webhook |
| `scm.procurement.po.confirmed` | `scm.procurement.po.confirmed.v1` | `purchase_order` | live | ACKNOWLEDGED → CONFIRMED (operator action) |
| `scm.procurement.po.canceled` | `scm.procurement.po.canceled.v1` | `purchase_order` | live | DRAFT/SUBMITTED/ACKNOWLEDGED → CANCELED (BUYER or OPERATOR) |
| `scm.procurement.po.received` | `scm.procurement.po.received.v1` | `purchase_order` | live | (CONFIRMED \| PARTIALLY_RECEIVED) → RECEIVED via ASN application |
| `scm.procurement.po.closed` | `scm.procurement.po.closed.v1` | `purchase_order` | **v2-deferred** | SETTLED → CLOSED (driven by `settlement-service`, not yet bootstrapped) |
| `scm.procurement.asn.received` | `scm.procurement.asn.received.v1` | `asn` | live | Supplier-issued ASN webhook accepted |

> **v1 reachability note**: The `po.closed` topic constant + event type are
> declared in `ProcurementEventPublisher` and `ProcurementOutboxPollingScheduler`
> but no v1 use case publishes them. They become live when v2's
> `settlement-service` issues the SYSTEM-actor `SETTLED → CLOSED`
> transition. Consumers may subscribe today; expect zero traffic in v1.

---

## Common Envelope

Every event in this contract uses the standard `BaseEventPublisher.writeEvent`
envelope (libs/java-messaging — see
[`ADR-MONO-004`](../../../docs/adr/) for the v1 vs v2 envelope distinction):

```json
{
  "eventId":       "01HZWX...",
  "eventType":     "scm.procurement.po.submitted",
  "source":        "scm-platform-procurement-service",
  "occurredAt":    "2026-05-11T08:30:00.123Z",
  "schemaVersion": 1,
  "partitionKey":  "01HZWX...",
  "payload":       { /* per-event shape */ }
}
```

| Envelope field | Type | Notes |
|---|---|---|
| `eventId` | string (UUID v7) | Generated per envelope at publish time. Use as the dedupe key (T8). |
| `eventType` | string | Matches the **Event type** column in the Topics table (no `.v1` suffix on this field — that lives only on the topic name). |
| `source` | string | Always `"scm-platform-procurement-service"` for events in this contract. |
| `occurredAt` | string (ISO 8601 UTC instant) | Wall-clock at envelope construction (publisher side). Outbox dispatch may add seconds of latency. |
| `schemaVersion` | integer | `1` for v1 envelope. Bumps to `2` only when the envelope shape itself changes (not when payload fields evolve — additive payload changes are spec-only). |
| `partitionKey` | string | Aggregate id — used as the Kafka record key for ordering. PO events use `poId`; ASN events use `asnId`. |
| `payload` | object | Per-event shape declared below. |

The Kafka record key MUST equal `partitionKey` so consumers can rely on
per-aggregate ordering within a partition.

---

## Payload Schemas

### Common PO payload base

Every `scm.procurement.po.*` event's payload starts with these fields
(emitted by `ProcurementEventPublisher.base(po)`). Per-event sections
below show only the **additional** fields each event appends.

| Field | Type | Nullable | Description |
|---|---|---|---|
| `poId` | string (UUID v7) | no | Purchase order aggregate id (matches `partitionKey`). |
| `poNumber` | string | no | `"PO-" + uuidV7-rand_b-tail-8-uppercase` per data-model.md § po_number format. |
| `tenantId` | string | no | Always `"scm"` in v1. |
| `supplierId` | string (UUID) | no | Reference to `suppliers.id` (no FK enforced cross-service). |
| `buyerAccountId` | string (UUID) | no | GAP `sub` claim of the actor who drafted the PO. |
| `totalAmount` | string (BigDecimal plain) | no | Sum of line.quantity × line.unit_price as plain decimal string (e.g., `"125000.00"`); avoid float parsing. |
| `currency` | string (ISO 4217) | no | 3-char currency code. |

`asn.received` uses a different payload (no PO base) — see § asn.received.

---

### scm.procurement.po.submitted

Triggered when the application service successfully dispatches a PO to the
supplier and commits the `DRAFT → SUBMITTED` transition.

**Additional payload fields:**

| Field | Type | Nullable | Description |
|---|---|---|---|
| `submittedAt` | string (ISO 8601) | no | `purchase_orders.submitted_at` value (or publisher wall-clock if null at write time). |

**Example:**

```json
{
  "eventId": "01HZWX12345678901234ABCDEF",
  "eventType": "scm.procurement.po.submitted",
  "source": "scm-platform-procurement-service",
  "occurredAt": "2026-05-11T08:30:00.123Z",
  "schemaVersion": 1,
  "partitionKey": "01HZWX12345678901234567890",
  "payload": {
    "poId": "01HZWX12345678901234567890",
    "poNumber": "PO-A1B2C3D4",
    "tenantId": "scm",
    "supplierId": "9b1d4a8c-1f2c-7a90-b1d4-3e6f8a2c9d10",
    "buyerAccountId": "7c2e9f5a-2f1c-7d80-9c1e-5d2a8f3b1e20",
    "totalAmount": "125000.00",
    "currency": "KRW",
    "submittedAt": "2026-05-11T08:30:00Z"
  }
}
```

---

### scm.procurement.po.acknowledged

Triggered when a supplier-ack webhook flips the PO `SUBMITTED → ACKNOWLEDGED`.

**Additional payload fields:**

| Field | Type | Nullable | Description |
|---|---|---|---|
| `supplierAckRef` | string | no | Supplier-issued acknowledgement reference (carried from the webhook body). |
| `acknowledgedAt` | string (ISO 8601) | no | `purchase_orders.acknowledged_at` value (or publisher wall-clock). |

**Example:**

```json
{
  "eventId": "01HZWX22345678901234ABCDEF",
  "eventType": "scm.procurement.po.acknowledged",
  "source": "scm-platform-procurement-service",
  "occurredAt": "2026-05-11T09:15:42.987Z",
  "schemaVersion": 1,
  "partitionKey": "01HZWX12345678901234567890",
  "payload": {
    "poId": "01HZWX12345678901234567890",
    "poNumber": "PO-A1B2C3D4",
    "tenantId": "scm",
    "supplierId": "9b1d4a8c-1f2c-7a90-b1d4-3e6f8a2c9d10",
    "buyerAccountId": "7c2e9f5a-2f1c-7d80-9c1e-5d2a8f3b1e20",
    "totalAmount": "125000.00",
    "currency": "KRW",
    "supplierAckRef": "SUP-ACK-2026-0001",
    "acknowledgedAt": "2026-05-11T09:15:42Z"
  }
}
```

---

### scm.procurement.po.confirmed

Triggered when an operator confirms an ACKNOWLEDGED PO (`ACKNOWLEDGED → CONFIRMED`).

**Additional payload fields:**

| Field | Type | Nullable | Description |
|---|---|---|---|
| `confirmedAt` | string (ISO 8601) | no | `purchase_orders.confirmed_at` value (or publisher wall-clock). |
| `actorAccountId` | string (UUID) | no | The OPERATOR `sub` claim that issued the confirm command. |

**Example:**

```json
{
  "eventId": "01HZWX32345678901234ABCDEF",
  "eventType": "scm.procurement.po.confirmed",
  "source": "scm-platform-procurement-service",
  "occurredAt": "2026-05-11T10:05:00.000Z",
  "schemaVersion": 1,
  "partitionKey": "01HZWX12345678901234567890",
  "payload": {
    "poId": "01HZWX12345678901234567890",
    "poNumber": "PO-A1B2C3D4",
    "tenantId": "scm",
    "supplierId": "9b1d4a8c-1f2c-7a90-b1d4-3e6f8a2c9d10",
    "buyerAccountId": "7c2e9f5a-2f1c-7d80-9c1e-5d2a8f3b1e20",
    "totalAmount": "125000.00",
    "currency": "KRW",
    "confirmedAt": "2026-05-11T10:05:00Z",
    "actorAccountId": "8d3f0a6b-3f2c-7e90-ad2e-6e3b9c4c2f30"
  }
}
```

---

### scm.procurement.po.canceled

Triggered when a BUYER or OPERATOR cancels a PO (`DRAFT/SUBMITTED/ACKNOWLEDGED → CANCELED`).
Once `CONFIRMED`, cancellation requires a v2 corrective task and does NOT
fire this event.

**Additional payload fields:**

| Field | Type | Nullable | Description |
|---|---|---|---|
| `reason` | string \| null | yes | Operator/buyer-supplied reason; absent when not provided in request body. |
| `canceledAt` | string (ISO 8601) | no | `purchase_orders.canceled_at` value (or publisher wall-clock). |
| `actorAccountId` | string (UUID) | no | The BUYER or OPERATOR `sub` claim. |

**Example:**

```json
{
  "eventId": "01HZWX42345678901234ABCDEF",
  "eventType": "scm.procurement.po.canceled",
  "source": "scm-platform-procurement-service",
  "occurredAt": "2026-05-11T10:30:00.000Z",
  "schemaVersion": 1,
  "partitionKey": "01HZWX12345678901234567890",
  "payload": {
    "poId": "01HZWX12345678901234567890",
    "poNumber": "PO-A1B2C3D4",
    "tenantId": "scm",
    "supplierId": "9b1d4a8c-1f2c-7a90-b1d4-3e6f8a2c9d10",
    "buyerAccountId": "7c2e9f5a-2f1c-7d80-9c1e-5d2a8f3b1e20",
    "totalAmount": "125000.00",
    "currency": "KRW",
    "reason": "Buyer canceled — supplier delay > SLA",
    "canceledAt": "2026-05-11T10:30:00Z",
    "actorAccountId": "7c2e9f5a-2f1c-7d80-9c1e-5d2a8f3b1e20"
  }
}
```

---

### scm.procurement.po.received

Triggered when ASN application drives the PO to `RECEIVED` (either
`CONFIRMED → RECEIVED` direct or `PARTIALLY_RECEIVED → RECEIVED`).
Published in the same transaction as the originating
`scm.procurement.asn.received` event when the ASN completes the PO.

**Additional payload fields:**

| Field | Type | Nullable | Description |
|---|---|---|---|
| `receivedAt` | string (ISO 8601) | no | Publisher wall-clock at the ASN application (NOT a `purchase_orders` column — there is no `received_at` on the PO row). |

**Example:**

```json
{
  "eventId": "01HZWX52345678901234ABCDEF",
  "eventType": "scm.procurement.po.received",
  "source": "scm-platform-procurement-service",
  "occurredAt": "2026-05-15T14:20:00.000Z",
  "schemaVersion": 1,
  "partitionKey": "01HZWX12345678901234567890",
  "payload": {
    "poId": "01HZWX12345678901234567890",
    "poNumber": "PO-A1B2C3D4",
    "tenantId": "scm",
    "supplierId": "9b1d4a8c-1f2c-7a90-b1d4-3e6f8a2c9d10",
    "buyerAccountId": "7c2e9f5a-2f1c-7d80-9c1e-5d2a8f3b1e20",
    "totalAmount": "125000.00",
    "currency": "KRW",
    "receivedAt": "2026-05-15T14:20:00Z"
  }
}
```

---

### scm.procurement.po.closed (v2-deferred)

Triggered when `settlement-service` (v2) drives `SETTLED → CLOSED`. The
event type constant + topic mapping exist in v1 but no v1 use case
publishes them. Consumers may pre-subscribe; expect zero traffic until
v2 ships.

When implemented in v2, the payload is expected to follow the common PO
payload base **without** any closing-specific additional fields. v2 may
amend this contract to add a `closedAt` field — implementers should treat
the v2 schema as authoritative when it lands.

---

### scm.procurement.asn.received

Triggered when a supplier ASN webhook is accepted and the ASN is persisted.
Aggregate is `asn` (not `purchase_order`) — partitionKey is the ASN id, not
the PO id.

**Payload (no common PO base):**

| Field | Type | Nullable | Description |
|---|---|---|---|
| `asnId` | string (UUID v7) | no | ASN aggregate id (matches `partitionKey`). |
| `poId` | string (UUID v7) | no | The PO this ASN applies to. |
| `tenantId` | string | no | Always `"scm"` in v1. |
| `supplierAsnRef` | string | no | Supplier-issued ASN reference; UNIQUE per `(tenantId, supplierAsnRef)` for S2 idempotency. |
| `expectedArrivalAt` | string (ISO 8601) | no | When the supplier expects the goods to arrive. |
| `receivedAt` | string (ISO 8601) | no | When the ASN was applied to the PO (`advance_shipment_notices.received_at`). |

> **Asymmetry note**: this event's payload is built directly via
> `LinkedHashMap` in `ProcurementEventPublisher.publishAsnReceived` rather
> than through the `base(po)` helper. Lines from the ASN are **not**
> embedded in the event — consumers needing per-line breakdown must query
> the procurement REST API or wait for a future `scm.procurement.asn.lines.applied.v1`
> event (not in v1 scope).

**Example:**

```json
{
  "eventId": "01HZWX62345678901234ABCDEF",
  "eventType": "scm.procurement.asn.received",
  "source": "scm-platform-procurement-service",
  "occurredAt": "2026-05-15T14:18:00.000Z",
  "schemaVersion": 1,
  "partitionKey": "01HZWX99988877766655544433",
  "payload": {
    "asnId": "01HZWX99988877766655544433",
    "poId": "01HZWX12345678901234567890",
    "tenantId": "scm",
    "supplierAsnRef": "ASN-2026-0001",
    "expectedArrivalAt": "2026-05-15T10:00:00Z",
    "receivedAt": "2026-05-15T14:18:00Z"
  }
}
```

---

## Consumer Rules

- **Idempotency (T8)**: dedupe on `eventId` (UUID v7). The same `eventId`
  may be redelivered by Kafka or by an outbox retry on the publisher side.
- **Ordering**: Kafka partition order is guaranteed within a single
  `partitionKey` (= aggregate id). Cross-aggregate or cross-topic ordering
  is NOT guaranteed.
- **At-least-once**: `procurement-service` publishes via the transactional
  outbox (T2 + T3) — events are never silently lost on the publisher side.
  Consumers MUST tolerate duplicates.
- **DLT**: failing consumer messages should route to `<topic>.DLT` after
  retry exhaustion (consumer-side concern; publisher does not provision DLT
  topics).
- **Schema evolution**: additive payload field changes (new optional field)
  are spec-only and do not bump `schemaVersion`. Removing a field or
  changing a field's type is a breaking change → `schemaVersion = 2` and
  the publisher dual-publishes during the deprecation window.
- **Unknown fields**: consumers MUST ignore unknown payload fields
  (forward compatibility).
- **Source filtering**: when subscribing to multiple `scm.procurement.*`
  topics from a single consumer group, filter on `envelope.source ==
  "scm-platform-procurement-service"` to defend against mistaken
  cross-service producers in the same topic namespace.

---

## Anticipated v1 Consumers

No internal `scm-platform` v1 service currently subscribes to these
topics:

- `inventory-visibility-service` could consume `po.received` to refresh
  open-PO counts but doesn't in v1 (eventual-consistency S5 boundary
  excludes it).
- v2 `settlement-service` will consume `po.received` to open settlement
  candidates and publish `po.closed` when settled.
- v2 `notification-service` will consume `po.canceled` and `po.received`
  for operator alerts.

Cross-project subscribers may exist outside scm-platform but are not
catalogued here — see each consuming project's
`specs/contracts/events/<*-subscriptions>.md` for cross-project consumer
declarations.

---

## References

- [`procurement-service/architecture.md`](../../services/procurement-service/architecture.md)
  § Outbox + audit_log invariants (event-type → topic mapping table)
- [`procurement-service/data-model.md`](../../services/procurement-service/data-model.md)
  (PO + ASN schema referenced from payload fields)
- [`inventory-visibility-subscriptions.md`](./inventory-visibility-subscriptions.md)
  (cross-project subscription pattern reference)
- `libs/java-messaging` `BaseEventPublisher.writeEvent` — envelope shape
- `platform/event-driven-policy.md` — versioning + outbox conventions
- `rules/traits/transactional.md` § T2 (atomic state-change + outbox) /
  T3 (outbox table + polling) / T8 (consumer idempotency)
- `rules/domains/scm.md` § S1 (multi-leg state transitions are idempotent +
  Tx protected) / S2 (idempotency keys on outbound)
- TASK-SCM-BE-006 — procurement-service architecture.md retroactive (PR #331)
- TASK-SCM-BE-009 — this event contract authoring task
