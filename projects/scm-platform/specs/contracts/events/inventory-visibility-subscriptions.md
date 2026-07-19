# Event Contract — inventory-visibility-service

## Subscriptions (cross-project)

inventory-visibility-service subscribes to wms-platform events.
The authoritative envelope schema is at:
`projects/wms-platform/specs/contracts/events/inventory-events.md`

### Consumer Group

`scm-inventory-visibility-v1` — matches `application.yml` and `KafkaConsumerConfig`.

### Subscribed Topics

| Topic | Event Type | Handler Class |
|---|---|---|
| `wms.inventory.received.v1` | `inventory.received` | `WmsInventoryReceivedConsumer` |
| `wms.inventory.adjusted.v1` | `inventory.adjusted` | `WmsInventoryAdjustedConsumer` |
| `wms.inventory.transferred.v1` | `inventory.transferred` | `WmsInventoryTransferredConsumer` |

### Idempotency Key

`eventId` from the wms envelope (UUID v7). Stored in `event_dedupe` table after processing.
Duplicate eventId → event is skipped without mutation (T8).

### Retry + DLT

- Retry: 3 attempts with exponential backoff (1s, 2s)
- DLT: `<topic>.DLT` (e.g., `wms.inventory.received.v1.DLT`)
- Invalid envelope (null eventId or null payload) → immediate DLT, no retry

### Schema Compatibility

wms v1 envelope fields used by this consumer:
- `eventId` (UUID, required for idempotency)
- `occurredAt` (Instant, required)
- `payload.warehouseId` / `payload.locationId` (String, identifies the node)
- `payload.skuId` (String, identifies the SKU)
- `payload.qtyReceived` / `payload.delta` / `payload.quantity` (Long, quantity)
- `payload.source.locationId` / `payload.target.locationId` (transfer events)
- `payload.warehouseCode` (String, **nullable** — ADR-MONO-050 D9 / TASK-SCM-BE-037): the
  business code of the warehouse the mutated inventory belongs to. Persisted on the node
  read-model (`inventory_nodes.warehouse_code`) and served by the internal snapshot
  endpoint, so `demand-planning-service`'s **batch** sweep can address a replenishment PO
  by warehouse **code** (cross-service identifiers are codes, not uuids). Best-effort: wms
  emits `null` while its warehouse master snapshot is unpopulated — the node is still
  created/updated, and a null code simply omits the wms inbound-expected addressing
  downstream (fail-closed, no uuid leak). A non-null incoming code updates the stored one;
  a null incoming code never overwrites a previously stored non-null code.

Breaking change policy: if wms introduces `wms.inventory.received.v2`, this consumer
continues on v1 during the grace period. Separate follow-up task migrates to v2.

---

## Published Events (this service → downstream)

### scm.inventory.alert.v1

Published by `KafkaAlertPublisherAdapter`. Best-effort (no outbox).

**Partition key**: `nodeId` (matches Kafka record key for per-node ordering).

**Envelope** (aligned with sibling `scm-procurement-events.md` § Common Envelope — `BaseEventPublisher.writeEvent` standard shape):

```json
{
  "eventId": "uuid",
  "eventType": "inventory.alert.snapshot_stale",
  "source": "scm-platform-inventory-visibility-service",
  "occurredAt": "2026-05-01T10:05:00Z",
  "schemaVersion": 1,
  "partitionKey": "node-uuid",
  "payload": {
    "nodeId": "node-uuid",
    "tenantId": "scm",
    "alertType": "SNAPSHOT_STALE",
    "stalenessStatus": "STALE",
    "detectedAt": "2026-05-01T10:05:00Z"
  }
}
```

| Envelope field | Type | Notes |
|---|---|---|
| `eventId` | string (UUID) | Generated per envelope at publish time. |
| `eventType` | string | `"inventory.alert.<type>"` (예: `"inventory.alert.snapshot_stale"` / `"inventory.alert.node_unreachable"`). |
| `source` | string | Always `"scm-platform-inventory-visibility-service"`. |
| `occurredAt` | string (ISO 8601 UTC instant) | Detection timestamp (= `payload.detectedAt`). |
| `schemaVersion` | integer | `1` for v1 envelope. |
| `partitionKey` | string | `nodeId` — Kafka record key for per-node ordering. |
| `payload` | object | Per-event shape (alertType, stalenessStatus, etc.). |

The Kafka record key MUST equal `partitionKey` so consumers can rely on per-node ordering within a partition.

**Alert types:**
- `SNAPSHOT_STALE` — node last_event_at exceeded threshold
- `NODE_UNREACHABLE` — node has never reported any event

**Delivery**: at-most-once (no outbox). Alert re-published on next batch if missed.
