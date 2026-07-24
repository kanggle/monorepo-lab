# API Contract — inventory-visibility-service

Base path: `/api/inventory-visibility` (rewritten by gateway from `/api/v1/inventory-visibility`)

All endpoints:
- Require Bearer token with `tenant_id=scm`
- Return `{ data: ..., meta: { timestamp, warning: "Not for procurement decisions (S5)" } }`
- Error format: `{ code, message, details?, timestamp }`

---

## GET /api/inventory-visibility/snapshot

Returns cross-node inventory snapshots.

**Query parameters:**
- `nodeId` (optional): filter to a specific node
- `page` (default 0): page index
- `size` (default 20, max 100): page size

**Response 200 — without nodeId (paginated cross-node list):**
```json
{
  "data": {
    "content": [
      {
        "id": "uuid",
        "nodeId": "uuid",
        "sku": "SKU-001",
        "quantity": 100.000,
        "lastEventAt": "2026-05-01T10:00:00Z",
        "version": 3,
        "staleness": "FRESH"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 150
  },
  "meta": {
    "timestamp": "2026-05-01T10:05:00Z",
    "warning": "Not for procurement decisions (S5)",
    "staleness": "ALL_FRESH"
  }
}
```

**Response 200 — with nodeId:**
```json
{
  "data": [ /* array of SnapshotResponse */ ],
  "meta": {
    "timestamp": "...",
    "warning": "Not for procurement decisions (S5)",
    "nodeId": "uuid",
    "count": 5,
    "staleness": "FRESH"
  }
}
```

---

## GET /api/inventory-visibility/sku/{sku}

SKU cross-node breakdown.

**Path parameter:** `sku` — SKU code

**Response headers:** `X-Cache: HIT | MISS | UNAVAILABLE`

**Response 200:**
```json
{
  "data": {
    "sku": "SKU-001",
    "nodes": [
      { "nodeId": "uuid-1", "quantity": 100.000, "staleness": "FRESH" },
      { "nodeId": "uuid-2", "quantity": 50.000, "staleness": "STALE" }
    ],
    "totalQuantity": 150.000
  },
  "meta": {
    "timestamp": "...",
    "warning": "Not for procurement decisions (S5)"
  }
}
```

---

## GET /api/inventory-visibility/staleness

Node-by-node staleness status.

**Response 200:**
```json
{
  "data": [
    {
      "nodeId": "uuid",
      "stalenessStatus": "FRESH",
      "lastEventAt": "2026-05-01T10:00:00Z",
      "lastCheckedAt": "2026-05-01T10:05:00Z"
    }
  ],
  "meta": { "timestamp": "...", "warning": "Not for procurement decisions (S5)" }
}
```

---

## GET /api/inventory-visibility/nodes

Node list with status.

**Response 200:**
```json
{
  "data": [
    {
      "id": "uuid",
      "nodeExternalId": "WH-001",
      "nodeType": "WMS_WAREHOUSE",
      "name": "Main Warehouse",
      "status": "ACTIVE"
    }
  ],
  "meta": { "timestamp": "...", "warning": "Not for procurement decisions (S5)" }
}
```

---

## POST /api/inventory-visibility/nodes

Explicitly register a `THIRD_PARTY_LOGISTICS` inventory node (ADR-MONO-054 §D2 /
TASK-SCM-BE-046). This is the one **mutating** endpoint on an otherwise
read-only API (S5) — a 3PL relationship is an onboarding fact, not an event
side-effect, so it has no auto-registration path the way a `WMS_WAREHOUSE`
node does. Registers only `THIRD_PARTY_LOGISTICS` nodes; the registered node is
**empty** (no stock) and **observed read-only, never operated** thereafter
(ADR-054 §D4 / ADR-050 §D4) — stock observation is TASK-SCM-BE-047.

**Request body:**
```json
{
  "nodeExternalId": "3PL-GOODGOOD-001",
  "name": "품고 물류센터"
}
```

**Response 201 — new node registered:**
```json
{
  "data": {
    "id": "uuid",
    "nodeExternalId": "3PL-GOODGOOD-001",
    "nodeType": "THIRD_PARTY_LOGISTICS",
    "name": "품고 물류센터",
    "status": "ACTIVE"
  },
  "meta": { "timestamp": "...", "warning": "Not for procurement decisions (S5)" }
}
```

**Response 200 — idempotent repeat registration (same `(tenantId, nodeExternalId)`,
already `THIRD_PARTY_LOGISTICS`):** same body shape as 201, no second row created.

**Response 422 — `nodeExternalId` or `name` blank:** standard `VALIDATION_ERROR` envelope.

**Response 409 — `nodeExternalId` already registered under a different `NodeType`**
(e.g. a wms auto-registered `WMS_WAREHOUSE`): `NODE_TYPE_CONFLICT`.

```json
{
  "code": "NODE_TYPE_CONFLICT",
  "message": "Inventory node externalId=WH-EXT-1 is already registered as WMS_WAREHOUSE; cannot re-register as THIRD_PARTY_LOGISTICS",
  "timestamp": "..."
}
```

---

## POST /api/inventory-visibility/nodes/{nodeId}/observed-stock

Record an **absolute** point-in-time observation of stock held at an already-registered
`THIRD_PARTY_LOGISTICS` node (ADR-MONO-054 §D4 / TASK-SCM-BE-047). This is a full
reading, not a delta — each line **replaces** the stored quantity for that
`(nodeId, sku)`, mirroring what the 3PL itself reports at `observedAt`. The node
must already exist as `THIRD_PARTY_LOGISTICS` (registered via `POST /nodes`,
TASK-SCM-BE-046); this endpoint **never** auto-registers a node and never mutates
a `WMS_WAREHOUSE` node.

**Path parameter:** `nodeId` — the inventory node's UUID.

**Request body:**
```json
{
  "observedAt": "2026-07-24T10:00:00Z",
  "lines": [
    { "skuCode": "SKU-001", "quantity": 25 },
    { "skuCode": "SKU-002", "quantity": 0 }
  ]
}
```

- `observedAt` (optional): the point in time the reading was taken. Defaults to the
  server's current time when omitted.
- `lines` (required, non-empty): each `skuCode` must be non-blank; each `quantity`
  must be present and non-negative — **zero is a valid observation** (the SKU
  dropped to 0 at the 3PL), not an omission.

**Response 200 — observation recorded:**
```json
{
  "data": {
    "nodeId": "uuid",
    "skuCount": 2,
    "observedAt": "2026-07-24T10:00:00Z"
  },
  "meta": { "timestamp": "...", "warning": "Not for procurement decisions (S5)" }
}
```

The response is a summary, not the full snapshot — re-read via
`GET /snapshot?nodeId=` or `GET /sku/{sku}` for current state.

**Ordering guard:** if a line's SKU already has a stored snapshot whose
`lastEventAt` is **newer** than this request's `observedAt`, that line is
**skipped** (a stale/replayed reading must not overwrite a newer one) — the
request as a whole still returns 200 and the node's staleness is still
refreshed (the push itself did happen), even if every individual line was
skipped as stale.

**Side effect:** this is the **only** action that creates/refreshes the node's
`NodeStaleness` row (registration does not) — the node joins the
FRESH/STALE/UNREACHABLE lifecycle from its first observation onward.

**Response 404 — `nodeId` does not exist:** `NODE_NOT_FOUND` (same shape as the
other endpoints' 404).

**Response 409 — `nodeId` resolves to a node that is not `THIRD_PARTY_LOGISTICS`,
or belongs to a different tenant than the caller's:** `NODE_TYPE_CONFLICT`
(same code as `POST /nodes`'s type conflict — reused, not a new code).

```json
{
  "code": "NODE_TYPE_CONFLICT",
  "message": "Inventory node nodeId=... has type=WMS_WAREHOUSE; observed-stock ingestion requires THIRD_PARTY_LOGISTICS",
  "timestamp": "..."
}
```

**Response 422 — `lines` empty/absent, a `skuCode` blank, or a `quantity`
missing/negative:** standard `VALIDATION_ERROR` envelope.

---

## Internal endpoints (non-gateway, network-trusted) — ADR-MONO-027 §D7.1

These are **NOT** routed by scm-gateway (the gateway routes only `/api/v1/**`) and **NOT** exposed on any public host route. They are reachable only on the intra-scm container network and carry **no JWT** (`permitAll`). The trust boundary is network isolation; production must keep IVS un-routed externally. Used only by the demand-planning `ReorderSweepScheduler` (unattended `@Scheduled`, no operator token).

### GET /internal/inventory-visibility/snapshot

Current inventory snapshot **across all tenants** (the replenishment batch is tenant-agnostic — see ADR-MONO-027 §D7.1). Returns a flat list; the caller filters each row against its own reorder policy.

**Response 200:**
```json
{
  "data": [
    { "sku": "SKU-001", "nodeId": "uuid", "availableQty": 42 }
  ],
  "meta": { "count": 1 }
}
```

`availableQty` is the snapshot quantity as an integer (whole units). No pagination at v1 demo scale.

---

## Error Codes

| Code | HTTP | Description |
|---|---|---|
| `NODE_NOT_FOUND` | 404 | Requested nodeId does not exist |
| `NODE_UNREACHABLE` | 503 | Node has never reported events |
| `SNAPSHOT_STALE` | 200 | Snapshot exceeds staleness threshold (informational) |
| `UNAUTHORIZED` | 401 | Missing or invalid bearer token |
| `TENANT_FORBIDDEN` | 403 | token.tenant_id ≠ scm |
| `PERMISSION_DENIED` | 403 | Insufficient scope |
| `VALIDATION_ERROR` | 400/422 | Invalid parameters |
| `NODE_TYPE_CONFLICT` | 409 | `POST /nodes` externalId already registered under a different `NodeType` (TASK-SCM-BE-046); also reused by `POST /nodes/{nodeId}/observed-stock` when `nodeId` is not `THIRD_PARTY_LOGISTICS` or belongs to a different tenant (TASK-SCM-BE-047) |
| `INTERNAL_ERROR` | 500 | Unexpected server error |
