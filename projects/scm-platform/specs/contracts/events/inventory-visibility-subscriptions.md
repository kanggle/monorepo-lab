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

Breaking change policy: if wms introduces `wms.inventory.received.v2`, this consumer
continues on v1 during the grace period. Separate follow-up task migrates to v2.

---

## Published Events (this service → downstream)

### scm.inventory.alert.v1

Published by `KafkaAlertPublisherAdapter`. Best-effort (no outbox).

**Partition key**: `nodeId`

**Envelope:**
```json
{
  "eventId": "uuid",
  "eventType": "inventory.alert.snapshot_stale",
  "eventVersion": 1,
  "occurredAt": "2026-05-01T10:05:00Z",
  "producer": "inventory-visibility-service",
  "aggregateType": "inventory_node",
  "aggregateId": "node-uuid",
  "payload": {
    "nodeId": "node-uuid",
    "tenantId": "scm",
    "alertType": "SNAPSHOT_STALE",
    "stalenessStatus": "STALE",
    "detectedAt": "2026-05-01T10:05:00Z"
  }
}
```

**Alert types:**
- `SNAPSHOT_STALE` — node last_event_at exceeded threshold
- `NODE_UNREACHABLE` — node has never reported any event

**Delivery**: at-most-once (no outbox). Alert re-published on next batch if missed.
