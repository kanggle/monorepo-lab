# HTTP Contract — inventory-service API

All `inventory-service` REST endpoints. This contract is authoritative — implementation
must match it. Changes here precede code changes (per `CLAUDE.md` Contract Rule).

Base path: `/api/v1/inventory`
Service: `inventory-service`
Base URL (via gateway): `https://{gateway}/api/v1/inventory`

---

## Global Conventions

### Headers

Every request:

| Header | Required | Notes |
|---|---|---|
| `Authorization` | yes | `Bearer <jwt>`. Validated at gateway; claims forwarded |
| `X-Request-Id` | yes | Generated/echoed by gateway. Surfaced in logs + traces |
| `X-Actor-Id` | yes | User id from JWT claim, set by gateway |
| `Idempotency-Key` | yes for POST / PATCH | UUID. TTL 24h. Scope `(Idempotency-Key, method, path)` |
| `Content-Type` | yes on body | `application/json` |
| `Accept` | no | Defaults to `application/json` |

Responses:

| Header | Notes |
|---|---|
| `X-Request-Id` | Echoed |
| `ETag` | Single-resource GET responses |

### Authorization

| Role | Permits |
|---|---|
| `INVENTORY_READ` | All GET endpoints |
| `INVENTORY_WRITE` | POST adjustments, transfers, mark-damaged |
| `INVENTORY_ADMIN` | Write-off damaged, manual reservation release |
| `INVENTORY_RESERVE` | Service-account scope — `outbound-service` creates / confirms / releases reservations |

Enforced at the application layer, not in controllers.

`INVENTORY_RESERVE` is a machine-to-machine scope. Human users do not hold it.
`outbound-service` presents a service-account JWT with this scope for synchronous
reservation calls. See `platform/security-rules.md` for service-account flow.

### Error Envelope

Per `platform/error-handling.md`. Every error response:

```json
{
  "error": {
    "code": "INSUFFICIENT_STOCK",
    "message": "Available quantity 30 is less than requested 50 for inventory row uuid-xxx",
    "timestamp": "2026-04-20T10:00:00.000Z",
    "details": { "inventoryId": "uuid-xxx", "available": 30, "requested": 50 },
    "traceId": "abc123",
    "requestId": "req-xyz"
  }
}
```

Domain error → HTTP status mapping:

| Code | HTTP | Notes |
|---|---|---|
| `INVENTORY_NOT_FOUND` | 404 | Inventory row not found |
| `RESERVATION_NOT_FOUND` | 404 | |
| `ADJUSTMENT_NOT_FOUND` | 404 | |
| `TRANSFER_NOT_FOUND` | 404 | |
| `INSUFFICIENT_STOCK` | 422 | Available / damaged qty insufficient |
| `RESERVATION_QUANTITY_MISMATCH` | 422 | Shipped qty ≠ reserved qty |
| `ADJUSTMENT_REASON_REQUIRED` | 400 | `reasonNote` missing or blank |
| `TRANSFER_SAME_LOCATION` | 422 | Source and target location are identical |
| `LOCATION_INACTIVE` | 422 | Location snapshot is `INACTIVE` |
| `SKU_INACTIVE` | 422 | SKU snapshot is `INACTIVE` |
| `LOT_INACTIVE` | 422 | Lot snapshot is `INACTIVE` |
| `LOT_EXPIRED` | 422 | Lot snapshot is `EXPIRED` |
| `VALIDATION_ERROR` | 400 | Bad input (type, format, required field) |
| `STATE_TRANSITION_INVALID` | 422 | Reservation already `CONFIRMED` / `RELEASED` |
| `CONFLICT` | 409 | Optimistic lock version mismatch |
| `DUPLICATE_REQUEST` | 409 | Same `Idempotency-Key`, different body |
| `UNAUTHORIZED` / `FORBIDDEN` | 401 / 403 | |

### Pagination

All list endpoints support:

| Param | Type | Default | Max | Notes |
|---|---|---|---|---|
| `page` | int | 0 | | 0-indexed |
| `size` | int | 20 | 100 | |
| `sort` | string | `updatedAt,desc` | | `field,{asc\|desc}` |

Response envelope:

```json
{
  "content": [ /* items */ ],
  "page": { "number": 0, "size": 20, "totalElements": 137, "totalPages": 7 },
  "sort": "updatedAt,desc"
}
```

### Idempotency Semantics

- `Idempotency-Key` absent on a mutating endpoint → 400 `VALIDATION_ERROR`
- Same key, same method+path, **same body hash** → cached response replayed
- Same key, same method+path, **different body** → 409 `DUPLICATE_REQUEST`
- TTL: 24 hours
- Full strategy: `specs/services/inventory-service/idempotency.md`

### Optimistic Locking

Mutation endpoints rely on version-checked UPDATE internally. On conflict:
→ 409 `CONFLICT`. The caller should fetch fresh state (`GET`) and retry.
Manual `version` field is not exposed in request bodies (except reservation confirm/release
where the caller holds the prior response version).

---

## 1. Inventory (Stock Level Queries)

### 1.1 GET `/api/v1/inventory` — List inventory rows

Auth: `INVENTORY_READ`.

Query parameters:

| Param | Type | Notes |
|---|---|---|
| `warehouseId` | UUID | Filter by warehouse |
| `locationId` | UUID | Filter by location |
| `skuId` | UUID | Filter by SKU |
| `lotId` | UUID | Filter by lot |
| `hasStock` | boolean | If `true`, only rows where any bucket > 0 |
| `minAvailable` | int | Only rows where `availableQty >= minAvailable` |
| pagination | | standard |

Response `200`:

```json
{
  "content": [
    {
      "id": "uuid",
      "warehouseId": "uuid",
      "locationId": "uuid",
      "locationCode": "WH01-A-01-01-01",
      "skuId": "uuid",
      "skuCode": "SKU-APPLE-001",
      "lotId": "uuid-or-null",
      "lotNo": "L-20260418-A",
      "availableQty": 80,
      "reservedQty": 20,
      "damagedQty": 0,
      "onHandQty": 100,
      "lastMovementAt": "2026-04-20T10:00:00Z",
      "version": 5,
      "createdAt": "2026-04-18T10:00:00Z",
      "updatedAt": "2026-04-20T10:00:00Z"
    }
  ],
  "page": { "number": 0, "size": 20, "totalElements": 1, "totalPages": 1 },
  "sort": "updatedAt,desc"
}
```

`onHandQty = availableQty + reservedQty + damagedQty` — computed by response mapper, not stored.
`locationCode`, `skuCode`, `lotNo` are denormalized from `MasterReadModel` for display only.
`lotNo` is `null` when `lotId` is `null` (non-LOT-tracked SKU).

### 1.2 GET `/api/v1/inventory/{id}` — Get by id

Auth: `INVENTORY_READ`.
Response `200`: same shape as list item.
Errors: `INVENTORY_NOT_FOUND` (404).
Headers: `ETag: "v{version}"`.

### 1.3 GET `/api/v1/inventory/by-key` — Get by business key

Auth: `INVENTORY_READ`.

Query parameters:

| Param | Type | Required | Notes |
|---|---|---|---|
| `locationId` | UUID | yes | |
| `skuId` | UUID | yes | |
| `lotId` | UUID | no | Required iff SKU is LOT-tracked; null for NONE-tracked SKUs |

Response `200`: same shape as single item.
Response `404` (`INVENTORY_NOT_FOUND`): no row exists for this combination — means zero stock.

This is the hot path for `outbound-service` order validation. A `404` means available = 0;
callers should treat it as an empty inventory row, not an error.

---

## 2. Stock Adjustments

### 2.1 POST `/api/v1/inventory/adjustments` — Create adjustment

Auth: `INVENTORY_WRITE` for `AVAILABLE` or `DAMAGED` bucket; `INVENTORY_ADMIN` for `RESERVED` bucket.
Requires `Idempotency-Key`.

Request:

```json
{
  "inventoryId": "uuid",
  "bucket": "AVAILABLE",
  "delta": -5,
  "reasonCode": "ADJUSTMENT_LOSS",
  "reasonNote": "실사 결과 5개 분실 확인. 담당자: 김철수"
}
```

Validation:

- `inventoryId`: required, must exist
- `bucket`: required, one of `AVAILABLE | RESERVED | DAMAGED`
- `delta`: required, ≠ 0 (signed integer)
- `reasonCode`: required, one of `ADJUSTMENT_CYCLE_COUNT | ADJUSTMENT_DAMAGE | ADJUSTMENT_LOSS | ADJUSTMENT_FOUND | ADJUSTMENT_RECLASSIFY`
- `reasonNote`: required, length ≥ 3 after trim

Business rules:

- Resulting `bucket >= 0` — `INSUFFICIENT_STOCK` (422) if negative delta would underflow
- Location / SKU / Lot must be `ACTIVE`; otherwise respective `*_INACTIVE` / `LOT_EXPIRED` (422)
- Writes one `StockAdjustment` row + one `InventoryMovement` row + one `InventoryOutbox` row
  in one `@Transactional` boundary

Reclassify adjustments (moving between buckets on the same row):

Submit **two separate POST /adjustments requests** with `reasonCode = ADJUSTMENT_RECLASSIFY`,
each pointing at the same `inventoryId` — one positive, one negative — and both `reasonNote`
referencing each other's intended `adjustmentId`. The use-case routes reclassify pairs into
a single `@Transactional` if sent in the same idempotency scope; in v1 they are two independent
calls processed sequentially by the caller.

Response `201`:

```json
{
  "adjustmentId": "uuid",
  "inventoryId": "uuid",
  "bucket": "AVAILABLE",
  "delta": -5,
  "reasonCode": "ADJUSTMENT_LOSS",
  "reasonNote": "실사 결과 5개 분실 확인. 담당자: 김철수",
  "inventory": {
    "id": "uuid",
    "availableQty": 75,
    "reservedQty": 20,
    "damagedQty": 0,
    "onHandQty": 95,
    "version": 6
  },
  "actorId": "user-uuid",
  "createdAt": "2026-04-20T10:00:00Z"
}
```

Errors: `INVENTORY_NOT_FOUND` (404), `ADJUSTMENT_REASON_REQUIRED` (400), `INSUFFICIENT_STOCK` (422),
`LOCATION_INACTIVE` / `SKU_INACTIVE` / `LOT_INACTIVE` / `LOT_EXPIRED` (422),
`CONFLICT` (409), `DUPLICATE_REQUEST` (409).

### 2.2 POST `/api/v1/inventory/{inventoryId}/mark-damaged` — Move available → damaged

Auth: `INVENTORY_WRITE`.
Requires `Idempotency-Key`.

Request:

```json
{
  "quantity": 3,
  "reasonNote": "외부 충격으로 인한 포장 파손 — 판매 불가"
}
```

Validation:

- `quantity`: required, > 0
- `reasonNote`: required, length ≥ 3

Business rule: internally calls `Inventory.markDamaged(qty)` — `available -= qty`, `damaged += qty`.
Writes two `InventoryMovement` rows (`AVAILABLE -N` + `DAMAGED +N`) + one `StockAdjustment`
(`reasonCode = ADJUSTMENT_DAMAGE`) + one `InventoryOutbox` in one TX.

Response `200`:

```json
{
  "adjustmentId": "uuid",
  "inventoryId": "uuid",
  "quantity": 3,
  "reasonNote": "외부 충격으로 인한 포장 파손 — 판매 불가",
  "inventory": {
    "id": "uuid",
    "availableQty": 77,
    "reservedQty": 20,
    "damagedQty": 3,
    "onHandQty": 100,
    "version": 7
  },
  "actorId": "user-uuid",
  "createdAt": "2026-04-20T10:00:00Z"
}
```

Errors: `INVENTORY_NOT_FOUND` (404), `INSUFFICIENT_STOCK` (422 — available < quantity),
`CONFLICT` (409), `DUPLICATE_REQUEST` (409).

### 2.3 POST `/api/v1/inventory/{inventoryId}/write-off-damaged` — Write off damaged stock

Auth: `INVENTORY_ADMIN`.
Requires `Idempotency-Key`.

Request:

```json
{
  "quantity": 3,
  "reasonNote": "완전 폐기 처리. 재활용 불가 판정 — 2026-04-20 창고장 승인"
}
```

Validation:

- `quantity`: required, > 0
- `reasonNote`: required, length ≥ 3

Business rule: calls `Inventory.writeOffDamaged(qty, reason)` — `damaged -= qty`.
`damaged >= quantity` required (`INSUFFICIENT_STOCK` if not).

Response `200`: same shape as mark-damaged response.

Errors: `INVENTORY_NOT_FOUND` (404), `INSUFFICIENT_STOCK` (422), `CONFLICT` (409), `DUPLICATE_REQUEST` (409).

### 2.4 GET `/api/v1/inventory/adjustments/{id}` — Get single adjustment

Auth: `INVENTORY_READ`.
Response `200`:

```json
{
  "id": "uuid",
  "inventoryId": "uuid",
  "bucket": "AVAILABLE",
  "delta": -5,
  "reasonCode": "ADJUSTMENT_LOSS",
  "reasonNote": "...",
  "actorId": "user-uuid",
  "createdAt": "2026-04-20T10:00:00Z"
}
```

Errors: `ADJUSTMENT_NOT_FOUND` (404).

### 2.5 GET `/api/v1/inventory/{inventoryId}/adjustments` — List adjustments for a row

Auth: `INVENTORY_READ`.
Query: pagination + `reasonCode`, `createdAfter` (ISO-8601), `createdBefore` (ISO-8601).
Response `200`: paginated list of adjustment items.

### 2.6 GET `/api/v1/inventory/adjustments` — Cross-row adjustment list

Auth: `INVENTORY_READ`.
Query: pagination + `inventoryId` (optional UUID), `reasonCode`, `createdAfter`, `createdBefore`.

Note: if `inventoryId` is absent, `createdAfter` is required to prevent unbounded scans.

---

## 3. Stock Transfers

### 3.1 POST `/api/v1/inventory/transfers` — Create transfer

Auth: `INVENTORY_WRITE`.
Requires `Idempotency-Key`.

Request:

```json
{
  "sourceLocationId": "uuid",
  "targetLocationId": "uuid",
  "skuId": "uuid",
  "lotId": "uuid-or-null",
  "quantity": 10,
  "reasonCode": "TRANSFER_INTERNAL",
  "reasonNote": "피킹 존 보충"
}
```

Validation:

- `sourceLocationId`, `targetLocationId`: required, must exist in `MasterReadModel`, both `ACTIVE`
- `sourceLocationId != targetLocationId` — `TRANSFER_SAME_LOCATION` (422) otherwise
- Both locations must share `warehouseId` — cross-warehouse → `VALIDATION_ERROR` (400) in v1
- `skuId`: required
- `lotId`: required iff SKU is LOT-tracked; null for non-LOT SKUs
- `quantity`: required, > 0
- `reasonCode`: required, one of `TRANSFER_INTERNAL | REPLENISHMENT | CONSOLIDATION`
- `reasonNote`: optional, max 500 chars

Business rules:

- Source `Inventory.available_qty >= quantity` at UPDATE time — `INSUFFICIENT_STOCK` (422) otherwise
- Target inventory row upserted if missing (`available_qty = quantity`, `version = 0`)
- Both rows locked in deterministic order (by `location_id ASC`) to avoid deadlocks
- Two `InventoryMovement` rows (`TRANSFER_OUT` + `TRANSFER_IN`), one `StockTransfer`, one `InventoryOutbox` — all in one TX

Response `201`:

```json
{
  "transferId": "uuid",
  "warehouseId": "uuid",
  "sourceLocationId": "uuid",
  "targetLocationId": "uuid",
  "skuId": "uuid",
  "lotId": "uuid-or-null",
  "quantity": 10,
  "reasonCode": "TRANSFER_INTERNAL",
  "reasonNote": "피킹 존 보충",
  "sourceInventory": {
    "id": "uuid",
    "availableQty": 70,
    "reservedQty": 20,
    "damagedQty": 0,
    "version": 7
  },
  "targetInventory": {
    "id": "uuid",
    "availableQty": 10,
    "reservedQty": 0,
    "damagedQty": 0,
    "version": 1
  },
  "actorId": "user-uuid",
  "createdAt": "2026-04-20T10:00:00Z"
}
```

Errors: `TRANSFER_SAME_LOCATION` (422), `VALIDATION_ERROR` (400), `INSUFFICIENT_STOCK` (422),
`LOCATION_INACTIVE` / `SKU_INACTIVE` / `LOT_INACTIVE` / `LOT_EXPIRED` (422),
`CONFLICT` (409), `DUPLICATE_REQUEST` (409).

### 3.2 GET `/api/v1/inventory/transfers/{id}` — Get single

Auth: `INVENTORY_READ`.
Response `200`: same shape as the transfer in the create response.
Errors: `TRANSFER_NOT_FOUND` (404).

### 3.3 GET `/api/v1/inventory/transfers` — List transfers

Auth: `INVENTORY_READ`.
Query: pagination + `warehouseId`, `sourceLocationId`, `targetLocationId`, `skuId`,
`reasonCode`, `createdAfter`, `createdBefore`.

---

## 4. Reservations

Used by `outbound-service` (service-account scope `INVENTORY_RESERVE`) for the
W4 two-phase allocation. Human operators with `INVENTORY_ADMIN` may release manually.

### 4.1 POST `/api/v1/inventory/reservations` — Create reservation

Auth: `INVENTORY_RESERVE`.
Requires `Idempotency-Key`.

Request:

```json
{
  "pickingRequestId": "uuid",
  "warehouseId": "uuid",
  "lines": [
    { "inventoryId": "uuid", "quantity": 5 },
    { "inventoryId": "uuid", "quantity": 3 }
  ],
  "ttlSeconds": 86400
}
```

Validation:

- `pickingRequestId`: required, UUID; must be globally unique across all Reservations regardless
  of status — `DUPLICATE_REQUEST` (409) if already exists with a different body; cached response
  replayed if same body (`Idempotency-Key` semantics at the domain level)
- `warehouseId`: required
- `lines`: required, ≥ 1 element; each `inventoryId` must be distinct within the request;
  each `quantity > 0`
- All `inventoryId`s must belong to the declared `warehouseId`
- `ttlSeconds`: optional; default 86400 (24h); max 172800 (48h)

Business rules:

- For each line: `Inventory.reserve(qty, reservationId)` — `INSUFFICIENT_STOCK` (422) if
  any line fails; **full rollback** (no partial reservation)
- 2N `InventoryMovement` rows written (AVAILABLE -N + RESERVED +N for each line)
- One `Reservation` + N `ReservationLine` rows
- One `InventoryOutbox` row (`inventory.reserved`)

Response `201`:

```json
{
  "id": "uuid",
  "pickingRequestId": "uuid",
  "warehouseId": "uuid",
  "status": "RESERVED",
  "expiresAt": "2026-04-21T10:00:00Z",
  "lines": [
    {
      "id": "uuid",
      "inventoryId": "uuid",
      "locationId": "uuid",
      "skuId": "uuid",
      "lotId": "uuid-or-null",
      "quantity": 5
    }
  ],
  "version": 0,
  "createdAt": "2026-04-20T10:00:00Z"
}
```

Errors: `INSUFFICIENT_STOCK` (422), `INVENTORY_NOT_FOUND` (404), `VALIDATION_ERROR` (400),
`CONFLICT` (409), `DUPLICATE_REQUEST` (409),
`LOCATION_INACTIVE` / `SKU_INACTIVE` / `LOT_INACTIVE` / `LOT_EXPIRED` (422).

### 4.2 POST `/api/v1/inventory/reservations/{id}/confirm` — Confirm reservation

Auth: `INVENTORY_RESERVE`.
Requires `Idempotency-Key`.

Request:

```json
{
  "lines": [
    { "reservationLineId": "uuid", "shippedQuantity": 5 },
    { "reservationLineId": "uuid", "shippedQuantity": 3 }
  ],
  "version": 0
}
```

Validation:

- `Reservation.status == RESERVED` — `STATE_TRANSITION_INVALID` (422) if `CONFIRMED` or `RELEASED`
- Each `shippedQuantity` must equal `ReservationLine.quantity` exactly —
  `RESERVATION_QUANTITY_MISMATCH` (422) otherwise. v1 does not support partial shipments.
- `version`: required for optimistic lock on the Reservation aggregate

Business rules:

- For each line: `Inventory.confirm(qty, reservationId)` — `RESERVED -= qty`
- N `InventoryMovement` rows written (RESERVED -N for each line)
- Reservation status → `CONFIRMED`, `confirmedAt` = now
- One `InventoryOutbox` row (`inventory.confirmed`)

Response `200`:

```json
{
  "id": "uuid",
  "pickingRequestId": "uuid",
  "status": "CONFIRMED",
  "confirmedAt": "2026-04-20T14:00:00Z",
  "version": 1
}
```

Errors: `RESERVATION_NOT_FOUND` (404), `STATE_TRANSITION_INVALID` (422),
`RESERVATION_QUANTITY_MISMATCH` (422), `CONFLICT` (409), `DUPLICATE_REQUEST` (409).

### 4.3 POST `/api/v1/inventory/reservations/{id}/release` — Release reservation

Auth: `INVENTORY_ADMIN` or `INVENTORY_RESERVE`.
Requires `Idempotency-Key`.

Request:

```json
{
  "reason": "CANCELLED",
  "version": 0
}
```

`reason`: one of `CANCELLED | MANUAL`. (`EXPIRED` is set by the TTL job, not this endpoint.)

Validation:

- `Reservation.status == RESERVED` — `STATE_TRANSITION_INVALID` (422) if already terminal

Business rules:

- For each line: `Inventory.release(qty, reservationId, reason)` — `RESERVED -= qty`, `AVAILABLE += qty`
- 2N `InventoryMovement` rows written (RESERVED -N + AVAILABLE +N for each line)
- Reservation status → `RELEASED`, `releasedReason` set, `releasedAt` = now
- One `InventoryOutbox` row (`inventory.released`)

Response `200`:

```json
{
  "id": "uuid",
  "pickingRequestId": "uuid",
  "status": "RELEASED",
  "releasedReason": "CANCELLED",
  "releasedAt": "2026-04-20T12:00:00Z",
  "version": 1
}
```

Errors: `RESERVATION_NOT_FOUND` (404), `STATE_TRANSITION_INVALID` (422),
`CONFLICT` (409), `DUPLICATE_REQUEST` (409).

### 4.4 GET `/api/v1/inventory/reservations/{id}` — Get single

Auth: `INVENTORY_READ`.
Response `200`: full reservation with `lines` array.
Errors: `RESERVATION_NOT_FOUND` (404).
Headers: `ETag: "v{version}"`.

### 4.5 GET `/api/v1/inventory/reservations` — List reservations

Auth: `INVENTORY_READ`.
Query: pagination + `status` (`RESERVED | CONFIRMED | RELEASED`), `warehouseId`,
`pickingRequestId` (exact UUID match), `expiresAfter` (ISO-8601), `expiresBefore`.

---

## 5. Movement History (Read-only)

Movement rows are append-only and are never modified or deleted. These endpoints
are the W2 audit surface.

### 5.1 GET `/api/v1/inventory/{inventoryId}/movements` — List movements for one row

Auth: `INVENTORY_READ`.
Query: pagination + `movementType`, `bucket`, `reasonCode`, `occurredAfter` (ISO-8601),
`occurredBefore` (ISO-8601).

Response `200`:

```json
{
  "content": [
    {
      "id": "uuid",
      "inventoryId": "uuid",
      "movementType": "RESERVE",
      "bucket": "AVAILABLE",
      "delta": -5,
      "qtyBefore": 80,
      "qtyAfter": 75,
      "reasonCode": "PICKING",
      "reasonNote": null,
      "reservationId": "uuid-or-null",
      "transferId": "uuid-or-null",
      "adjustmentId": "uuid-or-null",
      "sourceEventId": "uuid-or-null",
      "actorId": "user-or-system:putaway-consumer",
      "occurredAt": "2026-04-20T10:00:00Z"
    }
  ],
  "page": { "number": 0, "size": 20, "totalElements": 42, "totalPages": 3 },
  "sort": "occurredAt,desc"
}
```

### 5.2 GET `/api/v1/inventory/movements` — Cross-row movement query

Auth: `INVENTORY_READ`.
Query: pagination + `inventoryId` (optional), `locationId` (optional), `skuId` (optional),
`movementType`, `reasonCode`, `occurredAfter`, `occurredBefore`.

**Constraint**: if `inventoryId` is absent, `occurredAfter` is required — prevents unbounded
table scans. Returns `VALIDATION_ERROR` (400) otherwise.

This endpoint serves admin-service dashboards and audit exports.

---

## Operational Endpoints

### GET `/actuator/health` — Liveness / readiness

Standard Spring Boot Actuator. No auth (internal cluster traffic only).

### GET `/actuator/info` — Build info

No auth.

---

## Event Side-Effects

Every successful mutation publishes one event via the transactional outbox.
The outbox row is written in the same `@Transactional` boundary as the state change.
Kafka publish is asynchronous (outbox publisher SLA). See
`specs/contracts/events/inventory-events.md` for full schemas.

| Endpoint | Event Published |
|---|---|
| POST `/adjustments` | `inventory.adjusted` |
| POST `/{id}/mark-damaged` | `inventory.adjusted` (with `movementType=DAMAGE_MARK`) |
| POST `/{id}/write-off-damaged` | `inventory.adjusted` (with `movementType=DAMAGE_WRITE_OFF`) |
| POST `/transfers` | `inventory.transferred` |
| POST `/reservations` | `inventory.reserved` |
| POST `/reservations/{id}/confirm` | `inventory.confirmed` |
| POST `/reservations/{id}/release` | `inventory.released` |

`inventory.received` is published by the `PutawayCompletedConsumer` (event-driven path,
not REST). `inventory.low-stock-detected` is published by the domain service after any
mutation that reduces `availableQty` below the configured threshold.

---

## Not In v1

- Bulk adjustment (`POST /adjustments/bulk`, CSV upload)
- Inventory count export (XLSX / CSV)
- Cross-warehouse atomic transfer (modeled as outbound + inbound in v2)
- Lot allocation strategy endpoint (FEFO selection — v2)
- Serial-number tracking
- Cycle-count scheduling endpoint (v2)
- Hard delete of any row
- Reservation TTL extension

---

## References

- `specs/services/inventory-service/architecture.md`
- `specs/services/inventory-service/domain-model.md`
- `specs/services/inventory-service/idempotency.md`
- `specs/contracts/events/inventory-events.md`
- `platform/error-handling.md`
- `platform/api-gateway-policy.md`
- `platform/security-rules.md`
- `rules/traits/transactional.md` (T1 idempotency, T4 state machine, T5 optimistic lock)
- `rules/domains/wms.md` (W1, W2, W4, W5, W6 and error codes)
