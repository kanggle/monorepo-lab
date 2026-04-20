# HTTP Contract — master-service API

All `master-service` REST endpoints. This contract is authoritative — implementation
must match it. Changes here precede code changes (per `CLAUDE.md` Contract Rule).

Base path: `/api/v1/master`
Service: `master-service`
Base URL (via gateway): `https://{gateway}/api/v1/master`

---

## Global Conventions

### Headers

Every request:

| Header | Required | Notes |
|---|---|---|
| `Authorization` | yes | `Bearer <jwt>`. Validated at gateway; claims forwarded |
| `X-Request-Id` | yes | Generated/echoed by gateway. Surfaced in logs + traces |
| `X-Actor-Id` | yes | User id from JWT claim, set by gateway |
| `Idempotency-Key` | yes for POST / PUT / PATCH / DELETE | UUID. TTL 24h. Scope `(Idempotency-Key, method, path)` |
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
| `MASTER_READ` | All GET endpoints |
| `MASTER_WRITE` | POST / PUT / PATCH (create, update) |
| `MASTER_ADMIN` | DELETE = deactivation, and reactivate endpoints |

Enforced at the application layer, not in controllers.

### Error Envelope

Per `platform/error-handling.md`. Every error response carries `code`,
`message`, and `timestamp` (ISO 8601 UTC). The nested `error` wrapper is
service-level envelope used by `master-service`:

```json
{
  "error": {
    "code": "WAREHOUSE_NOT_FOUND",
    "message": "Warehouse not found: WH99",
    "timestamp": "2026-04-20T12:34:56.789Z",
    "details": { "warehouseCode": "WH99" },
    "traceId": "abc123",
    "requestId": "req-xyz"
  }
}
```

Domain error → HTTP status mapping:

| Code family | Example | HTTP |
|---|---|---|
| `*_NOT_FOUND` | `WAREHOUSE_NOT_FOUND`, `SKU_NOT_FOUND` | 404 |
| `*_DUPLICATE`, `*_CODE_DUPLICATE` | `LOCATION_CODE_DUPLICATE` | 409 |
| `REFERENCE_INTEGRITY_VIOLATION` | Deactivation blocked | 409 |
| `VALIDATION_ERROR` | Bad input | 400 |
| `CONFLICT` | Optimistic lock collision | 409 |
| `STATE_TRANSITION_INVALID` | Reactivate an `EXPIRED` Lot | 422 |
| `DUPLICATE_REQUEST` | Idempotency replay mismatch | 409 |
| `UNAUTHORIZED` / `FORBIDDEN` | | 401 / 403 |

### Pagination

All list endpoints support:

Query parameters:

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

### Common Filters

Every list endpoint supports:

- `status` — `ACTIVE` / `INACTIVE` (default: `ACTIVE`)
- `q` — free-text search across `*_code` and `name` (case-insensitive, `contains`)

### Idempotency Semantics

- `Idempotency-Key` absent on a mutating endpoint → 400 `VALIDATION_ERROR`
- Same key, same method+path, **same body hash** → cached response replayed (2xx status preserved)
- Same key, same method+path, **different body** → 409 `DUPLICATE_REQUEST`
- TTL: 24 hours

### Optimistic Locking

Update / deactivate / reactivate endpoints require `version` in the request body.
Mismatched version → 409 `CONFLICT`.

---

## 1. Warehouse

### 1.1 POST `/api/v1/master/warehouses` — Create

Auth: `MASTER_WRITE`.

Request:

```json
{
  "warehouseCode": "WH01",
  "name": "Seoul Main Warehouse",
  "address": "Seoul, Korea",
  "timezone": "Asia/Seoul"
}
```

Validation:

- `warehouseCode`: required, `^WH\d{2,3}$`, unique
- `name`: required, 1–100
- `address`: optional, max 200
- `timezone`: required, valid IANA tz id

Response `201`:

```json
{
  "id": "uuid",
  "warehouseCode": "WH01",
  "name": "Seoul Main Warehouse",
  "address": "Seoul, Korea",
  "timezone": "Asia/Seoul",
  "status": "ACTIVE",
  "version": 0,
  "createdAt": "2026-04-18T10:00:00Z",
  "createdBy": "user-uuid",
  "updatedAt": "2026-04-18T10:00:00Z",
  "updatedBy": "user-uuid"
}
```

Errors: `VALIDATION_ERROR` (400), `WAREHOUSE_CODE_DUPLICATE` (409).

### 1.2 GET `/api/v1/master/warehouses/{id}` — Get by id

Auth: `MASTER_READ`.
Response `200`: same shape as create response.
Errors: `WAREHOUSE_NOT_FOUND` (404).
Headers: `ETag: "v{version}"`.

### 1.3 GET `/api/v1/master/warehouses` — List

Auth: `MASTER_READ`.
Query: pagination + common filters (`status`, `q`).
Response `200`: paginated list.

### 1.4 PATCH `/api/v1/master/warehouses/{id}` — Update mutable fields

Auth: `MASTER_WRITE`.

Request (all fields optional; absent = no change):

```json
{
  "name": "Seoul Main (renamed)",
  "address": "Updated address",
  "timezone": "Asia/Seoul",
  "version": 3
}
```

`warehouseCode` is immutable — not accepted.
Response `200`: updated resource.
Errors: `WAREHOUSE_NOT_FOUND` (404), `CONFLICT` (409), `VALIDATION_ERROR` (400).

### 1.5 POST `/api/v1/master/warehouses/{id}/deactivate`

Auth: `MASTER_ADMIN`.

Request:

```json
{ "version": 3, "reason": "Closing this warehouse" }
```

Effect: `status` → `INACTIVE`. Blocked if any `ACTIVE` Zone under this warehouse.
Response `200`: updated resource.
Errors: `WAREHOUSE_NOT_FOUND` (404), `CONFLICT` (409), `REFERENCE_INTEGRITY_VIOLATION` (409),
`STATE_TRANSITION_INVALID` (422) if already `INACTIVE`.

### 1.6 POST `/api/v1/master/warehouses/{id}/reactivate`

Auth: `MASTER_ADMIN`. Opposite of deactivate. Always allowed on `INACTIVE` records.

---

## 2. Zone

Routes: `/api/v1/master/warehouses/{warehouseId}/zones/...` (nested).

### 2.1 POST — Create

Request:

```json
{
  "zoneCode": "Z-A",
  "name": "Ambient A",
  "zoneType": "AMBIENT"
}
```

Validation:

- `zoneCode`: required, `^Z-[A-Z0-9]+$`, unique within warehouse
- `name`: required, 1–100
- `zoneType`: required, one of `AMBIENT | CHILLED | FROZEN | RETURNS | BULK | PICK`

Parent `Warehouse` must be `ACTIVE`.

Response `201`:

```json
{
  "id": "uuid",
  "warehouseId": "uuid",
  "zoneCode": "Z-A",
  "name": "Ambient A",
  "zoneType": "AMBIENT",
  "status": "ACTIVE",
  "version": 0,
  "createdAt": "...", "createdBy": "...", "updatedAt": "...", "updatedBy": "..."
}
```

Errors: `WAREHOUSE_NOT_FOUND` (404), `ZONE_CODE_DUPLICATE` (409),
`STATE_TRANSITION_INVALID` (422) if parent is `INACTIVE`.

### 2.2 GET `/{zoneId}` — Get

### 2.3 GET — List zones within warehouse

Additional filter: `zoneType`.

### 2.4 PATCH `/{zoneId}` — Update (`name`, `zoneType`)

`zoneCode` and `warehouseId` immutable.

### 2.5 POST `/{zoneId}/deactivate`

Blocked if any `ACTIVE` Location under this zone. Errors include
`REFERENCE_INTEGRITY_VIOLATION` (409).

### 2.6 POST `/{zoneId}/reactivate`

---

## 3. Location

Routes: `/api/v1/master/locations/...` (flat — `location_code` is globally unique).
Creation is nested: `/api/v1/master/warehouses/{warehouseId}/zones/{zoneId}/locations`.

### 3.1 POST (under a zone) — Create

Request:

```json
{
  "locationCode": "WH01-A-01-02-03",
  "aisle": "01",
  "rack": "02",
  "level": "03",
  "bin": null,
  "locationType": "STORAGE",
  "capacityUnits": 500
}
```

Validation:

- `locationCode`: required, `^WH\d{2,3}-[A-Z0-9]+(-[A-Z0-9]+){1,5}$`, globally unique (W3)
- `locationCode` prefix should match the parent warehouse code — enforced at domain layer
- `locationType`: required, one of `STORAGE | STAGING_INBOUND | STAGING_OUTBOUND | DAMAGED | QUARANTINE`
- `capacityUnits`: optional, positive integer

Parent Zone must be `ACTIVE`.

Response `201`: includes `warehouseId`, `zoneId`, `locationCode`, etc.

Errors: `ZONE_NOT_FOUND` (404), `LOCATION_CODE_DUPLICATE` (409),
`STATE_TRANSITION_INVALID` (422), `VALIDATION_ERROR` (400).

### 3.2 GET `/api/v1/master/locations/{id}` — Get

### 3.3 GET `/api/v1/master/locations` — List (flat)

Additional filters:
- `warehouseId`
- `zoneId`
- `locationType`
- `code` (exact match on `locationCode`)

### 3.4 PATCH `/api/v1/master/locations/{id}`

Mutable: `locationType`, `capacityUnits`, `aisle`, `rack`, `level`, `bin`.
Immutable: `locationCode`, `warehouseId`, `zoneId`.

### 3.5 POST `/api/v1/master/locations/{id}/deactivate`

v1 check is local-only (always passes at master-service level). Cross-service
inventory check deferred to v2. Still returns `REFERENCE_INTEGRITY_VIOLATION` if
future local references are added.

### 3.6 POST `/api/v1/master/locations/{id}/reactivate`

---

## 4. SKU

Routes: `/api/v1/master/skus/...`.

### 4.1 POST — Create

Request:

```json
{
  "skuCode": "SKU-APPLE-001",
  "name": "Gala Apple 1kg",
  "description": "Fresh Gala apples, 1kg pack",
  "barcode": "8801234567890",
  "baseUom": "EA",
  "trackingType": "LOT",
  "weightGrams": 1000,
  "volumeMl": null,
  "hazardClass": null,
  "shelfLifeDays": 30
}
```

Validation:

- `skuCode`: required, 1–40, case-insensitive unique, immutable
- `name`: required, 1–200
- `barcode`: optional, unique if present
- `baseUom`: required, one of `EA | BOX | PLT | KG | L`, immutable
- `trackingType`: required, `NONE | LOT`; `NONE → LOT` transition forbidden post-create
- `shelfLifeDays`: optional; if `trackingType=LOT`, should be set (warning, not error)

Response `201`: full resource.

Errors: `SKU_CODE_DUPLICATE` (409), `BARCODE_DUPLICATE` (409), `VALIDATION_ERROR` (400).

### 4.2 GET `/{id}` — Get
### 4.3 GET `/api/v1/master/skus` — List
Filters: `status`, `q`, `trackingType`, `baseUom`, `barcode` (exact).
### 4.4 GET `/api/v1/master/skus/by-code/{skuCode}` — Lookup by business code (common for sync)
### 4.5 GET `/api/v1/master/skus/by-barcode/{barcode}` — Lookup by barcode (scanner path)
### 4.6 PATCH `/{id}` — Update
Mutable: `name`, `description`, `barcode`, `weightGrams`, `volumeMl`, `hazardClass`, `shelfLifeDays`.
Immutable: `skuCode`, `baseUom`, `trackingType`.
### 4.7 POST `/{id}/deactivate`
Blocked if any `ACTIVE` Lot under this SKU.
### 4.8 POST `/{id}/reactivate`

---

## 5. Partner

Routes: `/api/v1/master/partners/...`.

### 5.1 POST — Create

Request:

```json
{
  "partnerCode": "SUP-001",
  "name": "ACME Supplier Co.",
  "partnerType": "SUPPLIER",
  "businessNumber": "123-45-67890",
  "contactName": "Jane Kim",
  "contactEmail": "jane@acme.example.com",
  "contactPhone": "+82-2-1234-5678",
  "address": "Seoul, Korea"
}
```

Validation:

- `partnerCode`: required, 1–20, globally unique, immutable
- `name`: required, 1–200
- `partnerType`: required, one of `SUPPLIER | CUSTOMER | BOTH`
- `contactEmail`: optional, RFC5321 format check
- `contactPhone`: optional, max 30

Response `201`: full resource.

Errors: `PARTNER_CODE_DUPLICATE` (409), `VALIDATION_ERROR` (400).

### 5.2 GET `/{id}` — Get
### 5.3 GET `/api/v1/master/partners` — List
Filters: `status`, `q`, `partnerType`.
### 5.4 PATCH `/{id}` — Update
Mutable: all except `partnerCode`.
### 5.5 POST `/{id}/deactivate` / 5.6 `/reactivate`

---

## 6. Lot

Routes: nested under SKU: `/api/v1/master/skus/{skuId}/lots/...`.
Flat lookups via `/api/v1/master/lots/{id}`.

### 6.1 POST (nested) — Create

Request:

```json
{
  "lotNo": "L-20260418-A",
  "manufacturedDate": "2026-04-15",
  "expiryDate": "2026-05-15",
  "supplierPartnerId": "uuid-of-partner"
}
```

Validation:

- Parent SKU must have `trackingType = LOT` and be `ACTIVE`
- `lotNo`: required, 1–40, unique per SKU, immutable
- `manufacturedDate`, `expiryDate`: optional
- If both present, `expiryDate >= manufacturedDate`
- `supplierPartnerId`: optional; if present, partner must exist (soft validation — `ACTIVE` not required)

Response `201`:

```json
{
  "id": "uuid",
  "skuId": "uuid",
  "lotNo": "L-20260418-A",
  "manufacturedDate": "2026-04-15",
  "expiryDate": "2026-05-15",
  "supplierPartnerId": "uuid",
  "status": "ACTIVE",
  "version": 0,
  "createdAt": "...", "createdBy": "...", "updatedAt": "...", "updatedBy": "..."
}
```

Errors: `SKU_NOT_FOUND` (404), `LOT_NO_DUPLICATE` (409),
`STATE_TRANSITION_INVALID` (422) if parent SKU not `ACTIVE` or not LOT-tracked,
`VALIDATION_ERROR` (400), `PARTNER_NOT_FOUND` (404) if `supplierPartnerId` unknown.

### 6.2 GET `/api/v1/master/lots/{id}` — Get
### 6.3 GET `/api/v1/master/skus/{skuId}/lots` — List lots of SKU
Filters: `status` (`ACTIVE | INACTIVE | EXPIRED`), `expiryBefore` (date).
### 6.4 GET `/api/v1/master/lots` — Flat list
Filters: `skuId`, `status`, `expiryBefore`, `expiryAfter`.
### 6.5 PATCH `/api/v1/master/lots/{id}` — Update
Mutable: `manufacturedDate`, `expiryDate`, `supplierPartnerId`.
Immutable: `skuId`, `lotNo`.
### 6.6 POST `/api/v1/master/lots/{id}/deactivate`
Allowed in any non-terminal transition. `EXPIRED → INACTIVE` permitted.
### 6.7 POST `/api/v1/master/lots/{id}/reactivate`
Blocked if current state is `EXPIRED`. Returns `STATE_TRANSITION_INVALID`.

Note: **Lot expiration is not exposed as a public endpoint.** A scheduled domain
job transitions `ACTIVE → EXPIRED` when `expiry_date < today`. Job design:
`specs/services/master-service/scheduled-jobs.md` (to be authored in a later task).

---

## Operational Endpoints

### GET `/actuator/health` — Liveness / readiness

Standard Spring Boot Actuator. No auth (internal cluster traffic only).

### GET `/actuator/info` — Build info

No auth.

### GET `/api/v1/master/_meta/enums` — Runtime enum values

Returns the currently-valid enum values (zone types, location types, UOM, etc.).
Useful for admin UI dropdowns. Auth: `MASTER_READ`.

Response:

```json
{
  "zoneTypes": ["AMBIENT", "CHILLED", "FROZEN", "RETURNS", "BULK", "PICK"],
  "locationTypes": ["STORAGE", "STAGING_INBOUND", "STAGING_OUTBOUND", "DAMAGED", "QUARANTINE"],
  "baseUoms": ["EA", "BOX", "PLT", "KG", "L"],
  "trackingTypes": ["NONE", "LOT"],
  "partnerTypes": ["SUPPLIER", "CUSTOMER", "BOTH"]
}
```

---

## Event Side-Effects

Every successful mutation publishes one or more events via the outbox. See
`specs/contracts/events/master-events.md` for schemas and topic mapping.

Controller responses are returned **after** the DB transaction commits (outbox row
written in the same tx). Actual Kafka publish may happen asynchronously within the
outbox publisher's SLA.

---

## Not In v1

Explicitly out of v1 scope for this contract:

- Bulk import (`POST /skus/bulk`, CSV upload)
- Hard delete endpoints
- Zone / Warehouse CSV export
- ERP / PIM pull sync endpoints
- Batch deactivate (single endpoint to deactivate many Locations at once)
- Multi-tenant support (requires `X-Tenant-Id` header plus model changes)

These items either appear in v2 or require a separate architecture decision.

---

## References

- `specs/services/master-service/architecture.md`
- `specs/services/master-service/domain-model.md`
- `platform/error-handling.md`
- `platform/api-gateway-policy.md`
- `platform/versioning-policy.md`
- `rules/traits/transactional.md` (T1 idempotency, T4 state machine)
- `rules/domains/wms.md` (error codes, W1–W6)
