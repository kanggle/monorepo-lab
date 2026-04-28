# Event Contract — inbound-service Domain Events

Authoritative schemas for events **published** by `inbound-service`, and the
expected shapes of events **consumed** from `master-service` for snapshot
refresh.

Consumers (`inventory-service`, `admin-service`, `notification-service`) rely
on the published schemas. Changes here precede code changes (per `CLAUDE.md`
Contract Rule).

---

## Delivery Semantics

- **Publisher side**: transactional outbox (trait `transactional` T3). State
  change and outbox row written in the same DB transaction; a separate
  publisher process forwards outbox rows to Kafka. Guarantees **at-least-once**
  delivery. Partition key: `asnId` for all ASN-scoped events; ensures ordered
  delivery per ASN, which is the contention unit.
- **Consumer side**: must implement **idempotent handling** keyed by `eventId`
  (trait `transactional` T8). See `specs/services/inbound-service/idempotency.md`.
- **No cross-topic ordering**. A consumer correlating
  `inbound.inspection.completed` with `inbound.putaway.completed` must use
  `asnId`, not arrival order.

---

## Global Envelope

Every event shares this outer envelope. The `payload` field carries
event-specific data.

```json
{
  "eventId": "0191d8f0-1f0e-7c40-9d13-4a2c9e3f1234",
  "eventType": "inbound.putaway.completed",
  "eventVersion": 1,
  "occurredAt": "2026-04-20T10:00:00.123Z",
  "producer": "inbound-service",
  "aggregateType": "asn",
  "aggregateId": "uuid-of-asn",
  "traceId": "abc-123",
  "actorId": "user-uuid-or-system:erp-webhook",
  "payload": { /* event-specific */ }
}
```

| Field | Type | Notes |
|---|---|---|
| `eventId` | UUIDv7 string | Unique per event. Consumers dedupe on this (T8) |
| `eventType` | string | `inbound.<aggregate>.<action>` |
| `eventVersion` | int | Schema version for that `eventType`. v1 is the baseline |
| `occurredAt` | ISO-8601 UTC, ms precision | DB transaction commit time |
| `producer` | string | Always `inbound-service` |
| `aggregateType` | string | `asn` \| `inspection` \| `putaway_instruction` |
| `aggregateId` | UUID string | Aggregate root id (Asn / Inspection / PutawayInstruction) |
| `traceId` | string | OTel trace id; propagated from REST request, webhook, or consumed Kafka event |
| `actorId` | string or null | JWT subject for REST-driven; `system:erp-webhook` for webhook-origin; `system:<job>` for scheduled paths |
| `payload` | object | Defined per `eventType` below |

Serialization: JSON. Future Avro/Protobuf migration possible but not v1.

---

## Topic Layout

| Topic | Event type(s) | Partition key |
|---|---|---|
| `wms.inbound.asn.received.v1` | `inbound.asn.received` | `asnId` |
| `wms.inbound.asn.cancelled.v1` | `inbound.asn.cancelled` | `asnId` |
| `wms.inbound.inspection.completed.v1` | `inbound.inspection.completed` | `asnId` |
| `wms.inbound.putaway.instructed.v1` | `inbound.putaway.instructed` | `asnId` |
| `wms.inbound.putaway.completed.v1` | `inbound.putaway.completed` | `asnId` |
| `wms.inbound.asn.closed.v1` | `inbound.asn.closed` | `asnId` |

- `v1` in topic name: contract version. Breaking schema changes require a
  parallel `v2` topic with coexistence period (per
  `cross-cutting/api-versioning.md`).
- Retention: minimum 7 days. 30-day preferred for DLQ replay windows.
- Partitions: start with 3 per topic (inbound throughput is lower than
  inventory's mutation surface).
- Dead-letter topic: `<topic>.DLT` — for consumers, not the producer.

---

## Published Events (inbound-service → consumers)

### 1. `inbound.asn.received`

Triggered when a new ASN is created — either via the ERP webhook background
processor or a manual `POST /asns`.

Topic: `wms.inbound.asn.received.v1`
`aggregateType`: `asn`
`aggregateId`: `Asn.id`

```json
"payload": {
  "asnId": "uuid",
  "asnNo": "ASN-20260420-0001",
  "source": "WEBHOOK_ERP",
  "supplierPartnerId": "uuid",
  "supplierPartnerCode": "SUP-001",
  "warehouseId": "uuid",
  "expectedArriveDate": "2026-04-22",
  "lines": [
    {
      "asnLineId": "uuid",
      "lineNo": 1,
      "skuId": "uuid",
      "skuCode": "SKU-APPLE-001",
      "lotId": "uuid-or-null",
      "expectedQty": 100
    }
  ]
}
```

`source`: `MANUAL` | `WEBHOOK_ERP`. Helps admin-service distinguish operator
entries from ERP feed.

`supplierPartnerCode`, `skuCode`: denormalized from `MasterReadModel` for
display in admin dashboards (read-only consumers don't have to join).

Consumer expectations:

- `admin-service`: projects into `InboundDashboard` and `AsnAuditLog`
- `notification-service`: optional alert on high-value or expedited ASNs

### 2. `inbound.asn.cancelled`

Triggered when an ASN is cancelled (allowed only from `CREATED` or
`INSPECTING`).

Topic: `wms.inbound.asn.cancelled.v1`
`aggregateType`: `asn`
`aggregateId`: `Asn.id`

```json
"payload": {
  "asnId": "uuid",
  "asnNo": "ASN-20260420-0001",
  "previousStatus": "CREATED",
  "reason": "공급사 출하 취소 통보 — 차주 재발송",
  "cancelledAt": "2026-04-20T11:30:00Z"
}
```

`previousStatus`: `CREATED` | `INSPECTING`. Helps admin-service audit which
phase the cancellation interrupted.

Consumer expectations:

- `admin-service`: updates `InboundDashboard`, removes ASN from active queue
- `notification-service`: optional ops alert (especially for `INSPECTING`
  cancellations — physical work was wasted)

### 3. `inbound.inspection.completed`

Triggered on `Asn.completeInspection()` — Inspection is finalised and ASN
transitions to `INSPECTED`.

Topic: `wms.inbound.inspection.completed.v1`
`aggregateType`: `inspection`
`aggregateId`: `Inspection.id`

```json
"payload": {
  "inspectionId": "uuid",
  "asnId": "uuid",
  "asnNo": "ASN-20260420-0001",
  "warehouseId": "uuid",
  "inspectorId": "user-uuid",
  "completedAt": "2026-04-20T12:15:00Z",
  "lines": [
    {
      "inspectionLineId": "uuid",
      "asnLineId": "uuid",
      "skuId": "uuid",
      "lotId": "uuid-or-null",
      "lotNo": "L-20260420-A",
      "expectedQty": 100,
      "qtyPassed": 95,
      "qtyDamaged": 3,
      "qtyShort": 2
    }
  ],
  "discrepancyCount": 1,
  "discrepancySummary": [
    {
      "discrepancyId": "uuid",
      "asnLineId": "uuid",
      "discrepancyType": "QUANTITY_MISMATCH",
      "variance": -5,
      "acknowledged": true
    }
  ]
}
```

`discrepancyCount` is the number of lines that did not fully match expected
quantity (any of: short, damaged, overcount). All discrepancies must be
`acknowledged = true` for this event to fire (`INSPECTION_INCOMPLETE` blocks
otherwise).

Consumer expectations:

- `admin-service`: projects per-ASN inspection result; updates supplier KPI
  (discrepancy rate per supplier)
- `notification-service`: alert on `discrepancyCount > 0` (configurable)

### 4. `inbound.putaway.instructed`

Triggered when a `PutawayInstruction` is created — operational signal that
goods are being placed.

Topic: `wms.inbound.putaway.instructed.v1`
`aggregateType`: `putaway_instruction`
`aggregateId`: `PutawayInstruction.id`

```json
"payload": {
  "putawayInstructionId": "uuid",
  "asnId": "uuid",
  "warehouseId": "uuid",
  "plannedBy": "user-uuid",
  "lines": [
    {
      "putawayLineId": "uuid",
      "asnLineId": "uuid",
      "skuId": "uuid",
      "lotId": "uuid-or-null",
      "destinationLocationId": "uuid",
      "destinationLocationCode": "WH01-A-01-01-01",
      "qtyToPutaway": 95
    }
  ]
}
```

Operational-only. `inventory-service` does **NOT** consume this — stock
crediting waits for `inbound.putaway.completed`.

Consumer expectations:

- `admin-service`: updates `OperatorTaskBoard` showing pending putaway lines

### 5. `inbound.putaway.completed`  ⚠️ Cross-service contract

Triggered when the LAST `PutawayLine` of an instruction transitions to
`CONFIRMED` or `SKIPPED`. **One event per instruction** carrying every
confirmed line.

Topic: `wms.inbound.putaway.completed.v1`
`aggregateType`: `putaway_instruction`
`aggregateId`: `PutawayInstruction.id`

```json
"payload": {
  "putawayInstructionId": "uuid",
  "asnId": "uuid",
  "warehouseId": "uuid",
  "completedAt": "2026-04-20T13:45:00Z",
  "lines": [
    {
      "putawayConfirmationId": "uuid",
      "skuId": "uuid",
      "lotId": "uuid-or-null",
      "locationId": "uuid",
      "qtyReceived": 95
    }
  ]
}
```

> **⚠️ Authoritative cross-service contract.** This event is the **only** way
> `inventory-service` learns that goods have arrived. Topic name and payload
> shape are jointly owned with `inventory-service`; any change requires a
> coordinated migration.

Per-line semantics:

- `locationId`: the **actual** location goods were placed at (may differ from
  planned in the original `PutawayInstruction` if ops corrected mid-putaway).
- `qtyReceived`: equals `PutawayConfirmation.qtyConfirmed` (v1 no partial
  confirms).
- Lines that ended in `SKIPPED` are NOT included — only confirmed lines.

Consumer expectations:

- `inventory-service` (`PutawayCompletedConsumer`): for each line, calls
  `ReceiveStockUseCase` → `Inventory.receive(qty)` at `(locationId, skuId,
  lotId)`, creating the row if absent. Publishes `inventory.received` with
  `sourceEventId` set to this event's `eventId`.
- `admin-service`: projects per-ASN "received-quantity" KPI; computes
  `cycleTime = completedAt - asnReceivedAt`.

### 6. `inbound.asn.closed`

Triggered when ASN transitions `PUTAWAY_DONE → CLOSED`.

Topic: `wms.inbound.asn.closed.v1`
`aggregateType`: `asn`
`aggregateId`: `Asn.id`

```json
"payload": {
  "asnId": "uuid",
  "asnNo": "ASN-20260420-0001",
  "warehouseId": "uuid",
  "closedAt": "2026-04-20T14:00:00Z",
  "closedBy": "user-uuid",
  "summary": {
    "expectedTotal": 100,
    "passedTotal": 95,
    "damagedTotal": 3,
    "shortTotal": 2,
    "putawayConfirmedTotal": 95,
    "discrepancyCount": 1
  }
}
```

`summary` is the running ledger snapshot at close time; admin-service uses it
as the canonical "what arrived from this ASN" for downstream supplier
reconciliation.

Consumer expectations:

- `admin-service`: closes the open `InboundDashboard` row; computes cycle-time
  metric

---

## Consumed Events (cross-service, authoritative schema in publishing service)

`inbound-service` consumes the following events only. The authoritative schema
lives in the publishing service's contract; shapes are reproduced here as the
contract `inbound-service` expects.

### C1. `master.warehouse.*` / `master.zone.*` / `master.location.*` / `master.sku.*` / `master.lot.*` / `master.partner.*`

Topics: `wms.master.warehouse.v1`, `wms.master.zone.v1`,
`wms.master.location.v1`, `wms.master.sku.v1`, `wms.master.lot.v1`,
`wms.master.partner.v1`
Authoritative schema: `specs/contracts/events/master-events.md`

Effect: upsert the corresponding snapshot in `MasterReadModel`. Ignore events
whose `master_version <= cachedVersion` (out-of-order handling).

Consumed actions per master entity:

- `*.created` / `*.updated` → upsert snapshot, set `status = ACTIVE`
- `*.deactivated` → set snapshot `status = INACTIVE`
- `*.reactivated` → set snapshot `status = ACTIVE`
- `master.lot.expired` → set `LotSnapshot.status = EXPIRED`

`inbound-service` only **reads** master snapshots from these tables. It does
not publish anything in response to consuming a master event.

`inbound-service` does NOT consume any `inventory.*` or `outbound.*` events in
v1.

---

## Schema Versioning

- `eventVersion` is monotonic per `eventType`.
- Additive changes (new optional field) stay on the same version.
- Breaking changes (renamed/removed field, type change) bump `eventVersion`
  AND publish on a new topic (e.g., `wms.inbound.putaway.completed.v2`) with a
  coexistence period.
- Cross-service contract events (`inbound.putaway.completed`) require
  inventory-service to be ready for the new topic before producer cut-over.
- Deprecation deadline, producer cut-over, and topic retirement are governed
  by `cross-cutting/api-versioning.md`.

---

## Consumer Contract

Every downstream consumer MUST:

1. Dedupe on `eventId` (T8) — use the consumer's local dedupe table.
2. Tolerate at-least-once delivery.
3. Treat `payload.lines` as a current-state snapshot — never patch from partial
   field diffs.
4. On unparseable event: move to `<topic>.DLT` and alert
   (`messaging/consumer-retry-dlq/SKILL.md`).
5. Not assume strict ordering across different `aggregateId` values.
6. For `inbound.putaway.completed`: **all** lines in the payload are confirmed
   receipts. Skipped lines are absent (not `qtyReceived = 0`).

---

## Producer Guarantees (inbound-service)

- Exactly one outbox row per committed state change.
- `eventId` generated at outbox write time and stable across retries.
- `occurredAt` = DB transaction commit time.
- `traceId` propagates from HTTP request, webhook ingest, or consumed Kafka
  event (OTel context propagation).
- Publisher retries on Kafka failure with exponential backoff; outbox row
  marked `published_at` only after broker ACK.
- Publisher metrics: `inbound.outbox.pending.count`, `inbound.outbox.lag.seconds`,
  `inbound.outbox.publish.failure.total`.

---

## Not In v1

- No command-shaped events (e.g., `inbound.asn.cancellation-requested`) — v1
  only emits fact events.
- No compaction-keyed topics — append-only with time retention.
- No Avro / Protobuf encoding — v1 is JSON.
- No `inbound.discrepancy.acknowledged` event — discrepancies are surfaced via
  the `inbound.inspection.completed` envelope; downstream cares about the
  final ack state, not each ack action.
- No event for `Asn.startInspection()` — that's an internal lifecycle marker;
  no downstream consumer needs it.

---

## References

- `specs/services/inbound-service/architecture.md`
- `specs/services/inbound-service/domain-model.md`
- `specs/services/inbound-service/state-machines/asn-status.md`
- `specs/contracts/http/inbound-service-api.md` — REST endpoints (Open Item)
- `specs/contracts/webhooks/erp-asn-webhook.md` — webhook contract (Open Item)
- `specs/contracts/events/master-events.md` (consumed)
- `specs/contracts/events/inventory-events.md` § C1 — counterpart consumer
- `platform/event-driven-policy.md`
- `messaging/outbox-pattern/SKILL.md`
- `messaging/idempotent-consumer/SKILL.md`
- `rules/traits/transactional.md` (T3, T8)
- `rules/domains/wms.md` (W1, W2, W6)
