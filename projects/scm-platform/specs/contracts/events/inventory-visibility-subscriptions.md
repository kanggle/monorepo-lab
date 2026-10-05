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
| `wms.inventory.confirmed.v1` | `inventory.confirmed` | `WmsInventoryConfirmedConsumer` |
| `scm.procurement.inbound-expected.third-party.v1` | `scm.procurement.inbound-expected.third-party` | `ScmThirdPartyInboundExpectedConsumer` |

### `wms.inventory.confirmed.v1` — on-hand decrement (TASK-MONO-762 AC-0 ⓐ)

Added by TASK-MONO-762 to fix the 21차 데모 window discrepancy where this service's snapshot
quantity (95) diverged from wms's `available_qty` (85) for the same warehouse/SKU, because
the outbound-confirmation leg of the wms inventory lifecycle was never subscribed to.

**Decision (AC-0 ⓐ, owner, 2026-10-05 UTC)**: the snapshot quantity means **on-hand**
(available + reserved). Only `wms.inventory.confirmed.v1` moves it — each line's `quantity`
is subtracted from the snapshot for that warehouse/SKU. `wms.inventory.reserved.v1` and
`wms.inventory.released.v1` are **deliberately not subscribed to** (they move stock between
available and reserved, which on-hand is indifferent to — subscribing to them as well would
double-count the same movement against on-hand).

- Node resolution: the payload's top-level `warehouseId` (same external-id convention as
  `wms.inventory.received.v1`, which is how the node for a warehouse is normally
  auto-registered) — **not** auto-registered here; see below.
- `payload.lines[]` — each line's `skuId` + `quantity` is one decrement
  (`InventoryVisibilityApplicationService#applyInventoryConfirmed`).
- **No auto-registration, no auto-create.** Unlike `received`/`adjusted`/`transferred`, this
  consumer never calls `resolveOrCreateNode` and never creates a snapshot row. If the
  warehouse node does not exist yet, or a snapshot row for `(node, sku)` does not exist
  (Edge Case: `confirmed` arrives before `received` — e.g. partition skew), the use case
  throws and the Kafka consumer's retry+DLT disposition applies (3 attempts, then
  `wms.inventory.confirmed.v1.DLT`) — **never** a negative or zero-floored row.
- **No clamping.** If a line's decrement would make the snapshot go negative, the use case
  throws the same way (retry → DLT) rather than floor at zero — AC-0 chose surfacing the
  discrepancy over silently understating on-hand.
- Idempotency: the standard `event_dedupe` eventId check (T8), same as the other three wms
  consumers — a re-delivered `confirmed` event is skipped without a second decrement.
- Multiple lines in one event each decrement independently within the same transaction; a
  failure on any one line rolls back the whole event (mirrors
  `applyInventoryTransferred`'s atomic source+destination update).

### Intra-scm subscription — 3PL inbound-expected honour sink (ADR-MONO-055 §D4 / TASK-SCM-BE-049)

`scm.procurement.inbound-expected.third-party.v1` is an **intra-scm** (not
cross-project) subscription: `procurement-service` publishes it when a
`THIRD_PARTY_LOGISTICS`-addressed replenishment PO is confirmed, and this service
is its **only** consumer — the scm-internal honour sink (ADR-MONO-055 §D4). The
authoritative payload schema is owned by
[`scm-procurement-events.md`](./scm-procurement-events.md) §
`scm.procurement.inbound-expected.third-party`; the fields this consumer reads:

- `payload.poId` / `payload.poNumber` — the source PO reference (idempotency key).
- `payload.tenantId` — always `scm` in v1. 🔵 **Not routed on**: the consumer does
  not read it; the tenant it records and compares against is this service's
  **projection tenant** (`inventory-visibility.projection-tenant-id`, default `scm` —
  TASK-MONO-760; `../../services/inventory-visibility-service/architecture.md`
  § Multi-tenancy).
- `payload.destinationNodeId` — the inventory-visibility node the PO is addressed
  to. The consumer resolves the `THIRD_PARTY_LOGISTICS` node by this id via
  `InventoryNodeRepository#findById` and **fails closed** (no orphan expectation)
  if the node is absent, not `THIRD_PARTY_LOGISTICS`, or belongs to a tenant
  other than the projection tenant (→ non-retryable DLT with a clear error).
- `payload.expectedArrivalDate` (nullable), `payload.currency`.
- `payload.lines[]` — `skuCode` + `expectedQty` (one `inbound_expectations` row
  per line).

The recorded expectation (`inbound_expectations`) is **reconciled** by a later
3PL observation (`POST /nodes/{nodeId}/observed-stock`, TASK-SCM-BE-047): when the
observed stock for `(node, sku)` meets or exceeds the expected quantity, the
OPEN expectation is marked SATISFIED. An unmet expectation stays OPEN as a
visible operational signal (never silently purged). **wms is not involved** —
no wms consumer subscribes to this topic (ADR-MONO-054 §D3).

### Idempotency Key

`eventId` from the wms envelope (UUID v7). Stored in `event_dedupe` table after processing.
Duplicate eventId → event is skipped without mutation (T8).

For the intra-scm 3PL sink event, idempotency is **structural** rather than via
`event_dedupe`: the `inbound_expectations` table is UNIQUE on
`(tenant_id, source_po_number, sku, node_id)`, so a re-confirmed / replayed PO
does not double-record — a duplicate insert is caught and treated as a no-op
(idempotent on the PO reference, ADR-MONO-055 §D4 / TASK-SCM-BE-049).

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
| `eventType` | string | `"inventory.alert.<type>"` (e.g. `"inventory.alert.snapshot_stale"` / `"inventory.alert.node_unreachable"`). |
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
