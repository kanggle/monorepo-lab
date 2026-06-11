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
| `INTERNAL_ERROR` | 500 | Unexpected server error |
