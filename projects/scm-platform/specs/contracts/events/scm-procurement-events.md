# Event Contract — procurement-service

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
| `scm.procurement.po.canceled` | `scm.procurement.po.canceled.v1` | `purchase_order` | live | DRAFT/SUBMITTED/ACKNOWLEDGED/CONFIRMED → CANCELED (BUYER or OPERATOR); CONFIRMED only while not-yet-received (ADR-MONO-050 D6.3) |
| `scm.procurement.po.received` | `scm.procurement.po.received.v1` | `purchase_order` | live | (CONFIRMED \| PARTIALLY_RECEIVED) → RECEIVED via ASN application |
| `scm.procurement.po.closed` | `scm.procurement.po.closed.v1` | `purchase_order` | **v2-deferred** | SETTLED → CLOSED (driven by `settlement-service`, not yet bootstrapped) |
| `scm.procurement.asn.received` | `scm.procurement.asn.received.v1` | `asn` | live | Supplier-issued ASN webhook accepted |
| `scm.procurement.inbound-expected` | `scm.procurement.inbound-expected.v1` | `purchase_order` | live | ACKNOWLEDGED → CONFIRMED, **only** for a `WMS_WAREHOUSE`-addressed replenishment PO (ADR-MONO-050 D1/D2/D4) |
| `scm.procurement.inbound-expected.cancelled` | `scm.procurement.inbound-expected.cancelled.v1` | `purchase_order` | live | A non-terminal (not-yet-received) `WMS_WAREHOUSE`-addressed PO is cancelled/withdrawn (ADR-MONO-050 D6.3) |

> **v1 reachability note**: The `po.closed` topic constant + event type are
> declared in `ProcurementEventPublisher` and `ProcurementOutboxPollingScheduler`
> but no v1 use case publishes them. They become live when v2's
> `settlement-service` issues the SYSTEM-actor `SETTLED → CLOSED`
> transition. Consumers may subscribe today; expect zero traffic in v1.

---

## Common Envelope

Every event in this contract uses the standard `BaseEventPublisher.writeEvent`
envelope (libs/java-messaging — see
[`ADR-MONO-004`](../../../../../docs/adr/ADR-MONO-004-shared-messaging-scaffolding.md) for the v1 vs v2 envelope distinction):

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
| `supplierId` | string | no | Supplier reference (no FK enforced cross-service). For DEMAND_PLANNING-originated POs this is a supplier **CODE** (ADR-050 D9 Option A — sourced from `sku_supplier_map.supplier_id`, resolved by wms `findPartnerByCode`); operator-authored POs carry whatever supplier reference the operator supplied. Stored in `purchase_orders.supplier_id` (VARCHAR(36)). |
| `buyerAccountId` | string (UUID) | no | IAM `sub` claim of the actor who drafted the PO. |
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
The OPERATOR/BUYER actor behind `actorAccountId` is the roles-derived `ActorType`
(token `roles ∋ {OPERATOR, ADMIN, SUPER_ADMIN}` → `ActorContext.isOperator()`,
ADR-MONO-032/035 roles-only model) — no separate actor-type field is emitted; the
value is the actor's `sub`.

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

Triggered when a BUYER or OPERATOR cancels a PO
(`DRAFT/SUBMITTED/ACKNOWLEDGED/CONFIRMED → CANCELED`). A `CONFIRMED` PO may be
cancelled **only while not-yet-received** (ADR-MONO-050 D6.3); once any goods
have arrived (`PARTIALLY_RECEIVED` / `RECEIVED`) cancellation is forbidden and
belongs to the return/reverse-logistics domain (out of v1 scope). A
warehouse-addressed CONFIRMED cancellation additionally fires
`scm.procurement.inbound-expected.cancelled.v1` so wms drops the now-stranded
inbound expectation (see that event).

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

### scm.procurement.inbound-expected (ADR-MONO-050)

Triggered when an operator confirms an ACKNOWLEDGED PO (`ACKNOWLEDGED → CONFIRMED`)
**and** that PO is a warehouse-addressed replenishment PO
(`destinationNodeType = WMS_WAREHOUSE` with a non-null `destinationWarehouseId`).
It is published in the **same transaction** as the `CONFIRMED` state change
(alongside `scm.procurement.po.confirmed`) via the transactional outbox, so wms
never loses the arrival expectation on the publisher side (ADR-MONO-050 D1/D2).

**Producer-side filter (D4)**: a PO whose `destinationNodeType != WMS_WAREHOUSE`
(e.g. a `THIRD_PARTY_LOGISTICS` destination) — or an operator-authored PO with no
destination warehouse at all — does **not** emit this event. v1 turns confirmed
POs into wms inbound expectations only for own warehouses. This is enforced
producer-side; wms additionally rejects a non-`WMS_WAREHOUSE` payload defensively.

**Payload does NOT re-use the common PO base** — it is built as an ordered map
(like `asn.received`), carrying only the fields wms `inbound-service` needs.

> **Cross-service identifiers are CODES (ADR-MONO-050 §D9 — Option A).** Phase 1
> built the scm→wms leg in two parallel lanes that diverged on identifier form:
> scm emitted a warehouse **UUID** (the alert's `locationId`) and an scm supplier
> **UUID**, but wms resolves **both by CODE** (`findWarehouseByCode` /
> `findPartnerByCode`) → the loop fail-closed to the DLT. D9 (user-directed Option A)
> fixes this at the source: `supplierId` and `destinationWarehouseId` in this payload
> are **business codes**, not UUIDs. scm sources the warehouse code from the additive
> `warehouseCode` field on the wms low-stock alert, and treats
> `sku_supplier_map.supplier_id` as the supplier code (v1 contract). The internal
> warehouse UUID (`locationId`) is retained scm-side only as the reorder-suggestion
> dedup-key dimension `(tenantId, skuCode, warehouseId)` — it is never emitted here.

> **Envelope reconciliation (ADR-MONO-050 §D1 illustration vs the live envelope).**
> ADR-MONO-050 §D1 sketched `eventId` + `occurredAt` inside the payload. Per this
> file's **Common Envelope** convention (and TASK-SCM-BE-034 Edge Case #1 — "add in
> the same shape, introduce no new format"), those two fields live in the standard
> envelope, **not** duplicated in the payload. The idempotency key consumers dedupe
> on (T8 / ADR-050 D6.1) is the **envelope** `eventId` (a UUID v7), exactly as for
> every other event in this contract.

**Payload fields:**

| Field | Type | Nullable | Description |
|---|---|---|---|
| `poId` | string (UUID v7) | no | Purchase order aggregate id (matches `partitionKey`). |
| `poNumber` | string | no | Business dedup key on the wms side, combined with the line (ADR-050 D6.2 `(poNumber, line)`). |
| `supplierId` | string (**CODE**) | no | ★ **Supplier business CODE** (ADR-050 **D9 Option A** — cross-service identifiers are codes, not UUIDs), e.g. `"SUP-0043"`. wms resolves it via `findPartnerByCode`. Sourced from `sku_supplier_map.supplier_id` (the v1 supplier-code stand-in). |
| `destinationWarehouseId` | string (**CODE**) | no | ★ **Warehouse business CODE, addressed not assumed** (ADR-050 D3 + **D9 Option A**), e.g. `"WH-SEOUL-01"`. wms resolves it via `findWarehouseByCode`. Carried additively on the wms low-stock alert (`warehouseCode`) that seeded the reorder suggestion — the alert names the warehouse whose stock dropped. Single- and multi-warehouse deployments are the same code path. |
| `destinationNodeType` | string enum | no | v1 emits only `WMS_WAREHOUSE`. The field exists for forward-compat (ADR-050 D4: a future `THIRD_PARTY_LOGISTICS` value routes elsewhere and is never emitted here in v1). |
| `expectedArrivalDate` | string (ISO 8601 date, `YYYY-MM-DD`) | no | `confirmedAt` (UTC date) + `sku_supplier_map.lead_time_days`. Computed at confirm time so it is relative to supplier acknowledgement, not draft. |
| `currency` | string (ISO 4217) | no | PO currency (from `sku_supplier_map.currency` at materialization). |
| `lines` | array | no | One entry per PO line — see below. |

**`lines[]` element:**

| Field | Type | Nullable | Description |
|---|---|---|---|
| `skuCode` | string | no | `purchase_order_lines.sku`. |
| `expectedQty` | string (BigDecimal plain) | no | Ordered quantity as a plain decimal string (e.g. `"100"`); avoid float parsing — same convention as `totalAmount`. |
| `uom` | string | no | Unit of measure. **v1 constant `"EA"`** — `purchase_order_lines` carries no per-line uom yet; a real uom column is v2. |

**Example:**

```json
{
  "eventId": "0192d4e0-1a2b-7c3d-8e4f-5a6b7c8d9e0f",
  "eventType": "scm.procurement.inbound-expected",
  "source": "scm-platform-procurement-service",
  "occurredAt": "2026-07-19T04:12:00.000Z",
  "schemaVersion": 1,
  "partitionKey": "01HZWX12345678901234567890",
  "payload": {
    "poId": "01HZWX12345678901234567890",
    "poNumber": "PO-A1B2C3D4",
    "supplierId": "SUP-0043",
    "destinationWarehouseId": "WH-SEOUL-01",
    "destinationNodeType": "WMS_WAREHOUSE",
    "expectedArrivalDate": "2026-07-24",
    "currency": "KRW",
    "lines": [
      { "skuCode": "SKU-APPLE-001", "expectedQty": "100", "uom": "EA" }
    ]
  }
}
```

---

### scm.procurement.inbound-expected.cancelled (ADR-MONO-050 D6.3)

Companion event so wms can mark a not-yet-received inbound expectation
`CANCELLED` instead of stranding a phantom expectation. Emitted when a
warehouse-addressed (`WMS_WAREHOUSE`) PO is cancelled while **non-terminal**.

> **Reachability (ADR-MONO-050 D6.3, TASK-SCM-BE-036).** The PO state machine
> allows `CANCELED` from `DRAFT / SUBMITTED / ACKNOWLEDGED / CONFIRMED` — a
> `CONFIRMED` PO may be cancelled **only while not-yet-received** (`RECEIVED` /
> `PARTIALLY_RECEIVED` remain terminal-for-cancel). Since `inbound-expected.v1`
> fires on `CONFIRMED`, the case that actually strands a wms expectation (cancel
> **after** confirm) is now reachable, and this event carries the cancellation to
> wms (mark the open expectation `CANCELLED`). It is emitted for **every**
> warehouse-addressed PO cancellation regardless of prior state — for a
> pre-`CONFIRMED` cancel no expectation was ever created, so it is a harmless
> no-op on the wms side (no matching open expectation → ignored); wms is the
> authority on whether an expectation exists to cancel. The producer therefore
> emits the cancellation fact unconditionally rather than tracking wms state.

**Payload fields:**

| Field | Type | Nullable | Description |
|---|---|---|---|
| `poId` | string (UUID v7) | no | Purchase order aggregate id (matches `partitionKey`). |
| `poNumber` | string | no | Business dedup key (with line) wms uses to locate the open expectation. |
| `lines` | array | no | The cancelled lines — see below. |

**`lines[]` element:**

| Field | Type | Nullable | Description |
|---|---|---|---|
| `skuCode` | string | no | `purchase_order_lines.sku` — identifies the expectation line to cancel. |

**Example:**

```json
{
  "eventId": "0192d4e0-9f8e-7d6c-8b5a-4c3d2e1f0a9b",
  "eventType": "scm.procurement.inbound-expected.cancelled",
  "source": "scm-platform-procurement-service",
  "occurredAt": "2026-07-19T05:00:00.000Z",
  "schemaVersion": 1,
  "partitionKey": "01HZWX12345678901234567890",
  "payload": {
    "poId": "01HZWX12345678901234567890",
    "poNumber": "PO-A1B2C3D4",
    "lines": [
      { "skuCode": "SKU-APPLE-001" }
    ]
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

**Sanctioned cross-project consumer (ADR-MONO-050 D7)**: wms `inbound-service`
consumes `scm.procurement.inbound-expected.v1` (+ `.cancelled.v1`) to create /
cancel an `InboundExpectation` (ASN) — the first `scm → wms` runtime coupling.
Its consumer-driven subscription doc (`projects/wms-platform/specs/contracts/events/scm-inbound-expected-subscriptions.md`)
reproduces only the subset it reads and defers to **this** file as the
authoritative payload owner.

Other cross-project subscribers may exist outside scm-platform but are not
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
