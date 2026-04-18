# Event Contract — master-service Domain Events

Authoritative schemas for events published by `master-service`. Consumers
(`inventory-service`, `inbound-service`, `outbound-service`, `admin-service`,
`notification-service`) rely on these schemas. Changes here precede code changes.

---

## Delivery Semantics

- **Publisher side**: transactional outbox (trait `transactional` T3).
  State change and outbox row written in the same DB transaction; a separate
  publisher process forwards outbox rows to Kafka. Guarantees **at-least-once**
  delivery; ordering preserved per aggregate id (partition key).
- **Consumer side**: must implement **idempotent handling** keyed by `eventId`
  (trait `transactional` T8). No ordering guarantee across aggregate ids.
- **No cross-topic ordering.** A consumer that needs to correlate `sku.updated`
  with `lot.created` must resolve order via `occurredAt` timestamps or aggregate
  state, not via arrival order.

---

## Global Envelope

Every event shares this outer envelope. The `payload` field carries event-specific
data.

```json
{
  "eventId": "0191d8f0-1f0e-7c40-9d13-4a2c9e3f1234",
  "eventType": "master.warehouse.created",
  "eventVersion": 1,
  "occurredAt": "2026-04-18T10:00:00.123Z",
  "producer": "master-service",
  "aggregateType": "warehouse",
  "aggregateId": "uuid-of-warehouse",
  "traceId": "abc-123",
  "actorId": "user-uuid",
  "payload": { /* event-specific */ }
}
```

| Field | Type | Notes |
|---|---|---|
| `eventId` | UUIDv7 string | Unique per event. Consumers dedupe on this |
| `eventType` | string | `master.<aggregate>.<action>`, e.g., `master.sku.updated` |
| `eventVersion` | int | Schema version for that `eventType`. v1 is the baseline |
| `occurredAt` | ISO-8601 UTC, ms precision | Time the state change committed |
| `producer` | string | Always `master-service` |
| `aggregateType` | string | `warehouse | zone | location | sku | partner | lot` |
| `aggregateId` | UUID string | Aggregate instance id |
| `traceId` | string | OTel trace id; links to the HTTP request that caused the event |
| `actorId` | string or null | JWT subject; null for system-originated (scheduled expire) |
| `payload` | object | Defined per eventType below |

Serialization: JSON. Future binary (Avro/Protobuf) migration is possible but not v1.

---

## Topic Layout

Per aggregate type. Partition key: `aggregateId` (guarantees per-aggregate order).

| Topic | Carries |
|---|---|
| `wms.master.warehouse.v1` | `master.warehouse.*` events |
| `wms.master.zone.v1` | `master.zone.*` events |
| `wms.master.location.v1` | `master.location.*` events |
| `wms.master.sku.v1` | `master.sku.*` events |
| `wms.master.partner.v1` | `master.partner.*` events |
| `wms.master.lot.v1` | `master.lot.*` events |

- `v1` in the topic name: contract version. Breaking schema changes require a
  parallel `v2` topic with coexistence period (per `cross-cutting/api-versioning.md`).
- Retention: minimum 7 days (lets a broken consumer replay). Longer retention
  (e.g., 30 days) preferred for ops but not a contract guarantee.
- Partitions: start with 3 per topic (tune later based on throughput).

Dead-letter topic per source topic: `<topic>.dlq` — for consumers, not producer.

---

## Common Actions

Most aggregates emit this same set of actions. Per-aggregate payload differences
are listed in each section below.

| Action | When |
|---|---|
| `created` | New aggregate successfully committed |
| `updated` | Mutable fields changed and committed |
| `deactivated` | `ACTIVE` → `INACTIVE` transition committed |
| `reactivated` | `INACTIVE` → `ACTIVE` transition committed |

Lot has additional:

| Action | When |
|---|---|
| `expired` | Scheduled job transitioned `ACTIVE` → `EXPIRED` |

### `updated` — Snapshot or Diff?

`master-service` events carry a **full snapshot** (current state after mutation) in
`payload.after`. No `before/after` diff. Rationale: consumers are building read-model
caches and want the authoritative current state; diff reconstruction is error-prone.

If a consumer needs before/after, it holds its own prior snapshot from the previous
event for the same `aggregateId`.

---

## 1. Warehouse Events

Topic: `wms.master.warehouse.v1`.

### `master.warehouse.created`

```json
"payload": {
  "warehouse": {
    "id": "uuid",
    "warehouseCode": "WH01",
    "name": "Seoul Main",
    "address": "Seoul, Korea",
    "timezone": "Asia/Seoul",
    "status": "ACTIVE",
    "version": 0,
    "createdAt": "2026-04-18T10:00:00.123Z",
    "createdBy": "user-uuid",
    "updatedAt": "2026-04-18T10:00:00.123Z",
    "updatedBy": "user-uuid"
  }
}
```

### `master.warehouse.updated`

```json
"payload": {
  "warehouse": { /* full current snapshot */ },
  "changedFields": ["name", "address"]
}
```

`changedFields` lists names of mutable fields that differ from the pre-update state.
Convenience only — consumers should not rely on exact wording.

### `master.warehouse.deactivated`

```json
"payload": {
  "warehouse": { /* snapshot with status=INACTIVE */ },
  "reason": "Closing this warehouse"
}
```

### `master.warehouse.reactivated`

```json
"payload": {
  "warehouse": { /* snapshot with status=ACTIVE */ }
}
```

---

## 2. Zone Events

Topic: `wms.master.zone.v1`.

Payload `zone` shape per `domain-model.md` Zone entity plus `warehouseId`.

```json
"payload": {
  "zone": {
    "id": "uuid",
    "warehouseId": "uuid",
    "zoneCode": "Z-A",
    "name": "Ambient A",
    "zoneType": "AMBIENT",
    "status": "ACTIVE",
    "version": 0,
    "createdAt": "...", "createdBy": "...",
    "updatedAt": "...", "updatedBy": "..."
  }
}
```

Same `created | updated | deactivated | reactivated` set.

---

## 3. Location Events

Topic: `wms.master.location.v1`.

```json
"payload": {
  "location": {
    "id": "uuid",
    "warehouseId": "uuid",
    "zoneId": "uuid",
    "locationCode": "WH01-A-01-02-03",
    "aisle": "01", "rack": "02", "level": "03", "bin": null,
    "locationType": "STORAGE",
    "capacityUnits": 500,
    "status": "ACTIVE",
    "version": 0,
    "createdAt": "...", "createdBy": "...",
    "updatedAt": "...", "updatedBy": "..."
  }
}
```

Same four actions.

**Consumer expectation**: `inventory-service` keeps a Location cache keyed by
`locationCode` **and** `id`. A single `deactivated` event means inventory should
reject new put-away targeting this location. Existing inventory rows must **not**
be auto-moved — that is the master-service / ops team's decision.

---

## 4. SKU Events

Topic: `wms.master.sku.v1`.

```json
"payload": {
  "sku": {
    "id": "uuid",
    "skuCode": "SKU-APPLE-001",
    "name": "Gala Apple 1kg",
    "description": "...",
    "barcode": "8801234567890",
    "baseUom": "EA",
    "trackingType": "LOT",
    "weightGrams": 1000,
    "volumeMl": null,
    "hazardClass": null,
    "shelfLifeDays": 30,
    "status": "ACTIVE",
    "version": 0,
    "createdAt": "...", "createdBy": "...",
    "updatedAt": "...", "updatedBy": "..."
  }
}
```

Same four actions.

**Consumer expectation**: barcode scanners at inbound/outbound resolve SKU via
`barcode`. A barcode change emits `master.sku.updated` with `changedFields`
including `"barcode"`. Scanners must refresh cache on receipt.

---

## 5. Partner Events

Topic: `wms.master.partner.v1`.

```json
"payload": {
  "partner": {
    "id": "uuid",
    "partnerCode": "SUP-001",
    "name": "ACME Supplier Co.",
    "partnerType": "SUPPLIER",
    "businessNumber": "123-45-67890",
    "contactName": "Jane Kim",
    "contactEmail": "jane@acme.example.com",
    "contactPhone": "+82-2-1234-5678",
    "address": "Seoul, Korea",
    "status": "ACTIVE",
    "version": 0,
    "createdAt": "...", "createdBy": "...",
    "updatedAt": "...", "updatedBy": "..."
  }
}
```

Same four actions.

---

## 6. Lot Events

Topic: `wms.master.lot.v1`.

```json
"payload": {
  "lot": {
    "id": "uuid",
    "skuId": "uuid",
    "lotNo": "L-20260418-A",
    "manufacturedDate": "2026-04-15",
    "expiryDate": "2026-05-15",
    "supplierPartnerId": "uuid-or-null",
    "status": "ACTIVE",
    "version": 0,
    "createdAt": "...", "createdBy": "...",
    "updatedAt": "...", "updatedBy": "..."
  }
}
```

Actions: `created | updated | deactivated | reactivated | expired`.

### `master.lot.expired`

Emitted by a scheduled domain job. `actorId` is `null`.

```json
"payload": {
  "lot": { /* snapshot with status=EXPIRED */ },
  "triggeredBy": "scheduled-job:lot-expiry",
  "scheduledAt": "2026-05-16T00:00:00Z"
}
```

---

## Schema Versioning

- `eventVersion` is monotonic per `eventType`.
- Additive changes (new optional field) stay on the same version.
- Breaking changes (renamed/removed field, type change) bump `eventVersion` AND
  publish on a new topic (`wms.master.<aggregate>.v2`) with a coexistence period.
  Consumers migrate explicitly.
- Deprecation deadline, producer cut-over, and topic retirement are governed by
  `cross-cutting/api-versioning.md`.

---

## Consumer Contract

Every downstream consumer MUST:

1. Dedupe on `eventId` (trait `transactional` T8)
2. Tolerate at-least-once delivery
3. Treat `payload.<aggregate>` as the **current** authoritative snapshot — never
   attempt to patch local state from `changedFields` alone
4. Handle actions received in non-strict order (e.g., `updated` then `created` for
   the same `aggregateId` is theoretically possible across a rebalance; consumer
   should compare `version` and keep the higher one)
5. On unparseable event, move to `<topic>.dlq` and alert (per
   `messaging/consumer-retry-dlq/SKILL.md`)

---

## Producer Guarantees (master-service)

- Exactly one outbox row per committed state change
- `eventId` is generated at outbox write time and stable
- `occurredAt` = DB commit time
- `traceId` propagates the HTTP request that caused the change
- Publisher retries on Kafka failure with exponential backoff; outbox row deleted
  only after broker acknowledgment
- Publisher metrics: `master.outbox.pending.count`, `master.outbox.lag.seconds`,
  `master.outbox.publish.failure.total`

---

## Not In v1

- No command-shaped events (e.g., `master.sku.deactivation-requested`) — v1 only
  emits fact events
- No compaction-keyed topics (`cleanup.policy=compact`) — v1 is append-only with
  time retention
- No Avro / Protobuf encoding — v1 is JSON

---

## References

- `specs/services/master-service/architecture.md`
- `specs/services/master-service/domain-model.md`
- `specs/contracts/http/master-service-api.md`
- `platform/event-driven-policy.md`
- `cross-cutting/api-versioning/SKILL.md`
- `messaging/outbox-pattern/SKILL.md`
- `messaging/idempotent-consumer/SKILL.md`
- `rules/traits/transactional.md`
- `rules/domains/wms.md` (event naming conventions)
