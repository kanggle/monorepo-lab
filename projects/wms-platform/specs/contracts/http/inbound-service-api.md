# HTTP Contract — inbound-service API

All `inbound-service` REST endpoints. This contract is authoritative —
implementation must match it. Changes here precede code changes (per
`CLAUDE.md` Contract Rule).

Base path: `/api/v1/inbound`
Service: `inbound-service`
Base URL (via gateway): `https://{gateway}/api/v1/inbound`

The webhook endpoint (`POST /webhooks/erp/asn`) is **NOT** part of this
contract. It is documented in
[`specs/contracts/webhooks/erp-asn-webhook.md`](../webhooks/erp-asn-webhook.md)
and lives outside the `/api/v1/` namespace because it has different auth
(HMAC) and rate-limit tiers.

---

## Global Conventions

### Headers

Every request:

| Header | Required | Notes |
|---|---|---|
| `Authorization` | yes | `Bearer <jwt>`. Validated at gateway; claims forwarded |
| `X-Request-Id` | yes | Generated/echoed by gateway. Surfaced in logs + traces |
| `X-Actor-Id` | yes | User id from JWT claim, set by gateway |
| `Idempotency-Key` | yes for POST / PATCH / DELETE | UUID. TTL 24h. Scope `(Idempotency-Key, method, path)` |
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
| `INBOUND_READ` | All GET endpoints |
| `INBOUND_WRITE` | Manual ASN creation, inspection recording, putaway instruct/confirm/skip, ASN close |
| `INBOUND_ADMIN` | ASN cancellation, force-close, discrepancy override, webhook inbox retry |

Enforced at the application layer, not in controllers (per
`platform/architecture.md` and the BE-028 pattern: roles propagated through
the command record and checked in the use-case service).

### Error Envelope

Per `platform/error-handling.md`. Every error response:

```json
{
  "error": {
    "code": "ASN_ALREADY_CLOSED",
    "message": "ASN ASN-20260420-0001 is already in CLOSED status",
    "timestamp": "2026-04-20T10:00:00.000Z",
    "details": { "asnId": "uuid", "currentStatus": "CLOSED" },
    "traceId": "abc123",
    "requestId": "req-xyz"
  }
}
```

Domain error → HTTP status mapping:

| Code | HTTP | Notes |
|---|---|---|
| `ASN_NOT_FOUND` | 404 | |
| `INSPECTION_NOT_FOUND` | 404 | |
| `PUTAWAY_INSTRUCTION_NOT_FOUND` | 404 | |
| `PUTAWAY_LINE_NOT_FOUND` | 404 | |
| `ASN_ALREADY_CLOSED` | 422 | ASN cancelled/closed; or cancel attempted from `IN_PUTAWAY+` |
| `INSPECTION_QUANTITY_MISMATCH` | 422 | passed+damaged+short > expected |
| `INSPECTION_INCOMPLETE` | 422 | Unacknowledged discrepancies block forward transition |
| `PUTAWAY_QUANTITY_EXCEEDED` | 422 | qtyToPutaway > qtyPassed |
| `LOCATION_INACTIVE` | 422 | Destination location snapshot is INACTIVE |
| `WAREHOUSE_MISMATCH` | 422 | Destination location belongs to a different warehouse |
| `WAREHOUSE_NOT_FOUND` | 422 | Warehouse code unknown to MasterReadModel |
| `SKU_INACTIVE` | 422 | SKU snapshot is INACTIVE |
| `LOT_REQUIRED` | 422 | LOT-tracked SKU received without lot info |
| `PARTNER_INVALID_TYPE` | 422 | Supplier not ACTIVE or wrong partner_type |
| `ASN_NO_DUPLICATE` | 409 | `asnNo` already exists |
| `STATE_TRANSITION_INVALID` | 422 | Not allowed from current ASN/instruction status |
| `CONFLICT` | 409 | Optimistic lock version mismatch |
| `DUPLICATE_REQUEST` | 409 | Same `Idempotency-Key`, different body |
| `VALIDATION_ERROR` | 400 | Bad input (type, format, required field) |
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
- Full strategy: [`specs/services/inbound-service/idempotency.md`](../../services/inbound-service/idempotency.md)

### Optimistic Locking

Mutation endpoints rely on version-checked UPDATE internally. On conflict:
→ 409 `CONFLICT`. The caller should fetch fresh state (`GET`) and retry. The
`version` field is included in mutation request bodies where the caller is
expected to assert "I've seen this state" (cancel, close).

---

## 1. ASN Lifecycle

### 1.1 POST `/api/v1/inbound/asns` — Create ASN (manual entry)

Auth: `INBOUND_WRITE`.
Requires `Idempotency-Key`.

Equivalent of the ERP webhook for operator-driven entry. The created ASN has
`source = MANUAL`.

Request:

```json
{
  "asnNo": "ASN-20260420-9001",
  "supplierPartnerId": "uuid",
  "warehouseId": "uuid",
  "expectedArriveDate": "2026-04-22",
  "notes": "Walk-in supplier delivery — no ERP entry",
  "lines": [
    {
      "lineNo": 1,
      "skuId": "uuid",
      "lotId": "uuid-or-null",
      "expectedQty": 100
    }
  ]
}
```

Validation:

- `asnNo`: required, 1..40 chars, globally unique. Pattern recommended
  `ASN-\d{8}-\d+` — not enforced.
- `supplierPartnerId`: required UUID; must resolve to ACTIVE Partner with
  `partner_type ∈ {SUPPLIER, BOTH}`.
- `warehouseId`: required UUID; must resolve to ACTIVE Warehouse.
- `expectedArriveDate`: optional `YYYY-MM-DD`; if present, ≥ today.
- `notes`: optional, ≤ 1000 chars.
- `lines`: required, ≥ 1 element.
- `lines[].lineNo`: required, ≥ 1, unique within request.
- `lines[].skuId`: required UUID; must resolve to ACTIVE SKU.
- `lines[].lotId`: optional. If SKU is LOT-tracked AND lot is known, supply
  `lotId` resolved against `MasterReadModel`. If SKU is LOT-tracked but lot
  unknown, leave `null` (lot revealed at inspection). If SKU is NOT
  LOT-tracked and `lotId` is provided → `VALIDATION_ERROR`.
- `lines[].expectedQty`: required, > 0, ≤ 1,000,000.

Response `201`:

```json
{
  "asnId": "uuid",
  "asnNo": "ASN-20260420-9001",
  "source": "MANUAL",
  "supplierPartnerId": "uuid",
  "warehouseId": "uuid",
  "expectedArriveDate": "2026-04-22",
  "notes": "Walk-in supplier delivery — no ERP entry",
  "status": "CREATED",
  "lines": [
    {
      "asnLineId": "uuid",
      "lineNo": 1,
      "skuId": "uuid",
      "lotId": null,
      "expectedQty": 100
    }
  ],
  "version": 0,
  "createdAt": "2026-04-20T10:00:00Z",
  "createdBy": "user-uuid",
  "updatedAt": "2026-04-20T10:00:00Z",
  "updatedBy": "user-uuid"
}
```

Side-effect: outbox row → `inbound.asn.received` (see
`specs/contracts/events/inbound-events.md` §1).

Errors: `VALIDATION_ERROR` (400), `ASN_NO_DUPLICATE` (409),
`PARTNER_INVALID_TYPE` (422), `WAREHOUSE_NOT_FOUND` (422), `SKU_INACTIVE`
(422), `LOT_REQUIRED` (422), `DUPLICATE_REQUEST` (409).

### 1.2 GET `/api/v1/inbound/asns/{id}` — Get ASN by id

Auth: `INBOUND_READ`.
Response `200`: same shape as create response.
Errors: `ASN_NOT_FOUND` (404).
Headers: `ETag: "v{version}"`.

### 1.3 GET `/api/v1/inbound/asns` — List ASNs

Auth: `INBOUND_READ`.

Query parameters:

| Param | Type | Notes |
|---|---|---|
| `status` | string | One of `CREATED \| INSPECTING \| INSPECTED \| IN_PUTAWAY \| PUTAWAY_DONE \| CLOSED \| CANCELLED` |
| `warehouseId` | UUID | |
| `supplierPartnerId` | UUID | |
| `source` | string | `MANUAL \| WEBHOOK_ERP` |
| `asnNo` | string | exact match |
| `expectedArriveAfter` | ISO date | |
| `expectedArriveBefore` | ISO date | |
| `createdAfter` | ISO-8601 | |
| `createdBefore` | ISO-8601 | |
| pagination | | standard |

Response `200`: paginated list of ASN summaries (no `lines` array — fetch
detail via §1.2):

```json
{
  "content": [
    {
      "asnId": "uuid",
      "asnNo": "ASN-20260420-9001",
      "source": "WEBHOOK_ERP",
      "supplierPartnerId": "uuid",
      "warehouseId": "uuid",
      "status": "INSPECTED",
      "lineCount": 3,
      "expectedTotalQty": 300,
      "expectedArriveDate": "2026-04-22",
      "createdAt": "2026-04-20T08:00:00Z",
      "updatedAt": "2026-04-20T11:30:00Z"
    }
  ],
  "page": { "number": 0, "size": 20, "totalElements": 1, "totalPages": 1 },
  "sort": "updatedAt,desc"
}
```

### 1.4 POST `/api/v1/inbound/asns/{id}:cancel` — Cancel ASN

Auth: `INBOUND_ADMIN`.
Requires `Idempotency-Key`.

Allowed only when `Asn.status ∈ {CREATED, INSPECTING}`. Past that,
422 `ASN_ALREADY_CLOSED`.

Request:

```json
{
  "reason": "공급사 출하 취소 통보 — 차주 재발송",
  "version": 0
}
```

Validation:

- `reason`: required, 3..500 chars.
- `version`: required, optimistic lock check.

Response `200`:

```json
{
  "asnId": "uuid",
  "asnNo": "ASN-20260420-9001",
  "status": "CANCELLED",
  "previousStatus": "CREATED",
  "cancelledReason": "공급사 출하 취소 통보 — 차주 재발송",
  "cancelledAt": "2026-04-20T11:30:00Z",
  "cancelledBy": "user-uuid",
  "version": 1
}
```

Side-effect: outbox → `inbound.asn.cancelled` (see events §2).

Errors: `ASN_NOT_FOUND` (404), `ASN_ALREADY_CLOSED` (422), `CONFLICT` (409),
`DUPLICATE_REQUEST` (409).

### 1.5 POST `/api/v1/inbound/asns/{id}:close` — Close ASN

Auth: `INBOUND_WRITE`.
Requires `Idempotency-Key`.

Allowed only when `Asn.status == PUTAWAY_DONE`.

Request:

```json
{
  "version": 4
}
```

Response `200`:

```json
{
  "asnId": "uuid",
  "asnNo": "ASN-20260420-9001",
  "status": "CLOSED",
  "closedAt": "2026-04-20T14:00:00Z",
  "closedBy": "user-uuid",
  "summary": {
    "expectedTotal": 100,
    "passedTotal": 95,
    "damagedTotal": 3,
    "shortTotal": 2,
    "putawayConfirmedTotal": 95,
    "discrepancyCount": 1
  },
  "version": 5
}
```

Side-effect: outbox → `inbound.asn.closed` (see events §6).

Errors: `ASN_NOT_FOUND` (404), `STATE_TRANSITION_INVALID` (422), `CONFLICT`
(409), `DUPLICATE_REQUEST` (409).

---

## 2. Inspection

### 2.1 POST `/api/v1/inbound/asns/{asnId}/inspection:start` — Start inspection

Auth: `INBOUND_WRITE`.
Requires `Idempotency-Key`.

Allowed only when `Asn.status == CREATED`.

Request: empty body or `{ "version": 0 }` (optimistic lock optional — body
mismatch on retry triggers `DUPLICATE_REQUEST`).

Response `200`:

```json
{
  "asnId": "uuid",
  "status": "INSPECTING",
  "version": 1,
  "startedAt": "2026-04-20T11:00:00Z",
  "startedBy": "user-uuid"
}
```

No outbox event in v1 — internal lifecycle marker only.

Errors: `ASN_NOT_FOUND` (404), `STATE_TRANSITION_INVALID` (422), `CONFLICT`
(409), `DUPLICATE_REQUEST` (409).

### 2.2 POST `/api/v1/inbound/asns/{asnId}/inspection` — Record inspection

Auth: `INBOUND_WRITE`.
Requires `Idempotency-Key`.

Submits inspection results for every `AsnLine` in one call. Allowed only when
`Asn.status == INSPECTING`.

Request:

```json
{
  "notes": "Lot L-20260420-A 수령 — 외관 양호",
  "lines": [
    {
      "asnLineId": "uuid",
      "qtyPassed": 95,
      "qtyDamaged": 3,
      "qtyShort": 2,
      "lotId": "uuid-or-null",
      "lotNo": "L-20260420-A"
    }
  ],
  "version": 1
}
```

Validation:

- `notes`: optional, ≤ 1000 chars.
- `lines`: required, length must equal the ASN's AsnLine count.
- Each `asnLineId` must belong to this ASN; each line must appear exactly once.
- `qtyPassed`, `qtyDamaged`, `qtyShort`: required, ≥ 0.
- `qtyPassed + qtyDamaged + qtyShort ≤ asnLine.expectedQty` —
  `INSPECTION_QUANTITY_MISMATCH` (422) otherwise.
- For LOT-tracked SKU lines: at least one of `lotId` / `lotNo` must be
  present. Else `LOT_REQUIRED` (422).
  - `lotId` present: must resolve to ACTIVE Lot in `MasterReadModel`. Else
    `LOT_INACTIVE` / `LOT_EXPIRED`.
  - `lotId` null + `lotNo` present: lot reconciled later; carried to putaway
    as text.
- For non-LOT-tracked SKUs: both `lotId` and `lotNo` must be null. Else
  `VALIDATION_ERROR`.

Response `201`:

```json
{
  "inspectionId": "uuid",
  "asnId": "uuid",
  "asnStatus": "INSPECTED",
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
  "discrepancies": [
    {
      "discrepancyId": "uuid",
      "asnLineId": "uuid",
      "discrepancyType": "QUANTITY_MISMATCH",
      "expectedQty": 100,
      "actualTotalQty": 100,
      "variance": 0,
      "acknowledged": false
    }
  ],
  "version": 2
}
```

`discrepancies`: 0..N. A line with `qtyShort > 0` OR `qtyDamaged > 0` results
in a `QUANTITY_MISMATCH` discrepancy with `acknowledged = false`.

Side-effect: only fires `inbound.inspection.completed` outbox event if all
discrepancies are auto-acknowledged (variance = 0 case). In practice, any
discrepancy must be ack'd before close — see §2.3. The TX writes the
Inspection + InspectionLines + InspectionDiscrepancies + ASN status
transition + outbox in one boundary.

Errors: `ASN_NOT_FOUND` (404), `STATE_TRANSITION_INVALID` (422),
`INSPECTION_QUANTITY_MISMATCH` (422), `LOT_REQUIRED` (422), `LOT_INACTIVE`
(422), `LOT_EXPIRED` (422), `SKU_INACTIVE` (422), `CONFLICT` (409),
`DUPLICATE_REQUEST` (409), `VALIDATION_ERROR` (400).

### 2.3 POST `/api/v1/inbound/inspections/{id}/discrepancies/{discrepancyId}:acknowledge` — Acknowledge discrepancy

Auth: `INBOUND_ADMIN`.
Requires `Idempotency-Key`.

Marks `InspectionDiscrepancy.acknowledged = true`. Required before ASN can
move forward (specifically, no effect on transitions but
`INSPECTION_INCOMPLETE` blocks until ack'd).

Request:

```json
{
  "notes": "공급사 confirm 완료 — 차회 입고 시 보충 합의"
}
```

Validation:

- `notes`: required, 3..500 chars.

Response `200`:

```json
{
  "discrepancyId": "uuid",
  "inspectionId": "uuid",
  "asnLineId": "uuid",
  "discrepancyType": "QUANTITY_MISMATCH",
  "acknowledged": true,
  "acknowledgedBy": "user-uuid",
  "acknowledgedAt": "2026-04-20T12:30:00Z",
  "notes": "공급사 confirm 완료 — 차회 입고 시 보충 합의"
}
```

No outbox event. Discrepancy ack state is reflected in the next
`inbound.inspection.completed` event (which fires on ASN forward transition).

Errors: `INSPECTION_NOT_FOUND` (404), `VALIDATION_ERROR` (400),
`DUPLICATE_REQUEST` (409). Idempotent re-ack is a no-op (returns the same
body).

### 2.4 GET `/api/v1/inbound/inspections/{id}` — Get inspection

Auth: `INBOUND_READ`.
Response `200`: same shape as the §2.2 create response (plus
`acknowledged*` fields populated on each discrepancy).
Errors: `INSPECTION_NOT_FOUND` (404).
Headers: `ETag: "v{version}"`.

### 2.5 GET `/api/v1/inbound/asns/{asnId}/inspection` — Get inspection by ASN

Auth: `INBOUND_READ`.
Returns the (single, in v1) inspection for an ASN, or 404 if not yet started.
Response `200`: same shape as §2.4.
Errors: `INSPECTION_NOT_FOUND` (404).

---

## 3. Putaway

### 3.1 POST `/api/v1/inbound/asns/{asnId}/putaway:instruct` — Issue putaway instruction

Auth: `INBOUND_WRITE`.
Requires `Idempotency-Key`.

Allowed only when `Asn.status == INSPECTED`.

Request:

```json
{
  "lines": [
    {
      "asnLineId": "uuid",
      "destinationLocationId": "uuid",
      "qtyToPutaway": 95
    }
  ],
  "version": 2
}
```

Validation:

- `lines`: required, ≥ 1 element.
- Sum of `qtyToPutaway` per `asnLineId` ≤ `inspectionLine.qtyPassed` for that
  line. Else `PUTAWAY_QUANTITY_EXCEEDED` (422).
- Each `destinationLocationId`: must resolve to ACTIVE Location.
- `destinationLocationId.warehouseId == asn.warehouseId` else
  `WAREHOUSE_MISMATCH` (422).
- `destinationLocationId` ACTIVE → else `LOCATION_INACTIVE` (422).
- Multiple lines may target the same destination (consolidating receipts).
- Multiple lines may target different destinations for the same
  `asnLineId` (split across locations).
- `qtyToPutaway` > 0.

Response `201`:

```json
{
  "putawayInstructionId": "uuid",
  "asnId": "uuid",
  "asnStatus": "IN_PUTAWAY",
  "warehouseId": "uuid",
  "plannedBy": "user-uuid",
  "status": "PENDING",
  "lines": [
    {
      "putawayLineId": "uuid",
      "asnLineId": "uuid",
      "skuId": "uuid",
      "lotId": "uuid-or-null",
      "lotNo": "L-20260420-A",
      "destinationLocationId": "uuid",
      "destinationLocationCode": "WH01-A-01-01-01",
      "qtyToPutaway": 95,
      "status": "PENDING"
    }
  ],
  "version": 0,
  "createdAt": "2026-04-20T12:30:00Z"
}
```

Side-effect: outbox → `inbound.putaway.instructed` (events §4).

Errors: `ASN_NOT_FOUND` (404), `STATE_TRANSITION_INVALID` (422),
`PUTAWAY_QUANTITY_EXCEEDED` (422), `LOCATION_INACTIVE` (422),
`WAREHOUSE_MISMATCH` (422), `CONFLICT` (409), `DUPLICATE_REQUEST` (409).

### 3.2 POST `/api/v1/inbound/putaway/{instructionId}/lines/{lineId}:confirm` — Confirm putaway line

Auth: `INBOUND_WRITE`.
Requires `Idempotency-Key`.

Confirms one PutawayLine. The last successful confirmation transitions the
PutawayInstruction to `COMPLETED`/`PARTIALLY_COMPLETED` and the ASN to
`PUTAWAY_DONE`.

Request:

```json
{
  "actualLocationId": "uuid",
  "qtyConfirmed": 95
}
```

Validation:

- `actualLocationId`: required UUID. Defaults to the planned
  `destinationLocationId` if equal. May differ — operator override is allowed
  per §3.4.
- `qtyConfirmed`: required, > 0, must equal `putawayLine.qtyToPutaway` (v1 no
  partial). Mismatch → `VALIDATION_ERROR` (400) with details indicating
  expected.
- `actualLocationId` must resolve to ACTIVE Location and same warehouse as
  the instruction.
- `putawayLine.status` must be `PENDING` else `STATE_TRANSITION_INVALID`.

Response `200`:

```json
{
  "confirmationId": "uuid",
  "putawayLineId": "uuid",
  "putawayInstructionId": "uuid",
  "actualLocationId": "uuid",
  "actualLocationCode": "WH01-A-01-01-01",
  "qtyConfirmed": 95,
  "confirmedBy": "user-uuid",
  "confirmedAt": "2026-04-20T13:45:00Z",
  "instruction": {
    "status": "COMPLETED",
    "confirmedLineCount": 1,
    "totalLineCount": 1
  },
  "asn": {
    "status": "PUTAWAY_DONE"
  }
}
```

Side-effect: if this is the last pending line, the TX also fires the
`inbound.putaway.completed` outbox event (events §5 — cross-service contract
to inventory-service). Otherwise no outbox row.

Errors: `PUTAWAY_INSTRUCTION_NOT_FOUND` (404), `PUTAWAY_LINE_NOT_FOUND` (404),
`STATE_TRANSITION_INVALID` (422), `LOCATION_INACTIVE` (422),
`WAREHOUSE_MISMATCH` (422), `VALIDATION_ERROR` (400 — qty mismatch),
`CONFLICT` (409), `DUPLICATE_REQUEST` (409).

### 3.3 POST `/api/v1/inbound/putaway/{instructionId}/lines/{lineId}:skip` — Skip putaway line

Auth: `INBOUND_WRITE`.
Requires `Idempotency-Key`.

Marks a line as `SKIPPED` (operator decision — typically destination is
unavailable). The instruction transitions to `PARTIALLY_COMPLETED` if any
SKIPPED lines exist when the last line is resolved.

Request:

```json
{
  "reason": "Location WH01-A-01-01-01 lift broken — handled in TASK-OPS-1234"
}
```

Validation:

- `reason`: required, 3..500 chars.
- `putawayLine.status` must be `PENDING` else `STATE_TRANSITION_INVALID`.

Response `200`:

```json
{
  "putawayLineId": "uuid",
  "status": "SKIPPED",
  "skippedReason": "Location WH01-A-01-01-01 lift broken — handled in TASK-OPS-1234",
  "skippedBy": "user-uuid",
  "skippedAt": "2026-04-20T13:45:00Z",
  "instruction": {
    "status": "PARTIALLY_COMPLETED",
    "confirmedLineCount": 0,
    "skippedLineCount": 1,
    "totalLineCount": 1
  },
  "asn": {
    "status": "PUTAWAY_DONE"
  }
}
```

Side-effect: if this is the last pending line AND at least one CONFIRMED
exists, fires `inbound.putaway.completed` (only confirmed lines included). If
all lines are SKIPPED, the event still fires with an empty `lines` array —
inventory-service receives a no-op (zero qtyReceived rows are filtered out).

Errors: same as §3.2 except quantity ones.

### 3.4 GET `/api/v1/inbound/putaway/{instructionId}` — Get putaway instruction

Auth: `INBOUND_READ`.
Response `200`: same shape as §3.1 create response with current
line statuses.
Errors: `PUTAWAY_INSTRUCTION_NOT_FOUND` (404).

### 3.5 GET `/api/v1/inbound/asns/{asnId}/putaway` — Get putaway by ASN

Auth: `INBOUND_READ`.
Returns the (single, in v1) PutawayInstruction for an ASN.
Response `200`: same as §3.4.
Errors: `PUTAWAY_INSTRUCTION_NOT_FOUND` (404).

### 3.6 GET `/api/v1/inbound/putaway/lines/{lineId}/confirmation` — Get the confirmation row

Auth: `INBOUND_READ`.
Returns the `PutawayConfirmation` row written for a confirmed line. 404 if
the line is `PENDING` or `SKIPPED`.
Errors: `PUTAWAY_LINE_NOT_FOUND` (404).

---

## 4. Webhook Inbox (Admin)

### 4.1 GET `/api/v1/inbound/webhooks/inbox` — List webhook inbox rows

Auth: `INBOUND_ADMIN`.

Query parameters:

| Param | Type | Notes |
|---|---|---|
| `status` | string | `PENDING \| APPLIED \| FAILED` |
| `source` | string | ERP env (`erp-prod`, `erp-stg`) |
| `receivedAfter` | ISO-8601 | |
| `receivedBefore` | ISO-8601 | |
| pagination | | standard |

Response `200`:

```json
{
  "content": [
    {
      "eventId": "X-Erp-Event-Id-value",
      "source": "erp-prod",
      "status": "FAILED",
      "failureReason": "PARTNER_INVALID_TYPE",
      "receivedAt": "2026-04-20T11:00:00Z",
      "processedAt": "2026-04-20T11:00:01Z",
      "asnId": null,
      "asnNo": "ASN-20260420-0001"
    }
  ],
  "page": { "number": 0, "size": 20, "totalElements": 1, "totalPages": 1 },
  "sort": "receivedAt,desc"
}
```

`raw_payload` is **NOT** included in list — fetch via §4.2 for inspection.

### 4.2 GET `/api/v1/inbound/webhooks/inbox/{eventId}` — Get webhook inbox row

Auth: `INBOUND_ADMIN`.
Response `200`: full row including `rawPayload` (JSON object, parsed).
Errors: 404 if not found.

### 4.3 POST `/api/v1/inbound/webhooks/inbox/{eventId}:retry` — Retry failed inbox row (v2)

> **Not in v1.** Listed here as a future extension. v1 ops re-trigger
> webhooks by asking ERP to re-emit the event with the same `event_id` after
> the dedupe TTL expires (7 days), or via direct DB intervention.

---

## Operational Endpoints

### GET `/actuator/health` — Liveness / readiness

Standard Spring Boot Actuator. No auth (internal cluster traffic only).

### GET `/actuator/info` — Build info

No auth.

---

## Event Side-Effects Summary

Every successful mutation publishes at most one event via the transactional
outbox. The outbox row is written in the same `@Transactional` boundary as
the state change. Kafka publish is asynchronous (outbox publisher SLA). See
[`specs/contracts/events/inbound-events.md`](../events/inbound-events.md).

| Endpoint | Event Published |
|---|---|
| `POST /asns` | `inbound.asn.received` |
| `POST /asns/{id}:cancel` | `inbound.asn.cancelled` |
| `POST /asns/{id}/inspection:start` | none (lifecycle marker only) |
| `POST /asns/{id}/inspection` | `inbound.inspection.completed` |
| `POST /inspections/{id}/discrepancies/{did}:acknowledge` | none (state surfaced via subsequent inspection-completed event) |
| `POST /asns/{id}/putaway:instruct` | `inbound.putaway.instructed` |
| `POST /putaway/{iid}/lines/{lid}:confirm` | `inbound.putaway.completed` (only when last line) |
| `POST /putaway/{iid}/lines/{lid}:skip` | `inbound.putaway.completed` (only when last line, unless all skipped) |
| `POST /asns/{id}:close` | `inbound.asn.closed` |

The webhook ingest path's `inbound.asn.received` event is fired by the
**background processor**, not by the webhook controller. The controller only
writes to `erp_webhook_inbox` (synchronous ack).

---

## Not In v1

- ASN `PATCH` (modify lines, expected qty after creation)
- Reverse putaway / un-confirm
- Partial putaway confirmation (`qtyConfirmed < qtyToPutaway`)
- Multi-warehouse split ASN
- ASN bulk endpoints (CSV upload, batch create)
- Inspection re-do (modify after `INSPECTED`)
- Auto-putaway planner endpoint (`POST /asns/{id}/putaway:plan-auto`)
- Webhook inbox manual retry endpoint (admin v2)
- Damaged-bucket destination validation (advisory only in v1)
- ERP outbound ack (we don't notify ERP of receipt)
- Hard delete of any row

---

## References

- `specs/services/inbound-service/architecture.md`
- `specs/services/inbound-service/domain-model.md`
- `specs/services/inbound-service/state-machines/asn-status.md`
- `specs/services/inbound-service/idempotency.md` (Open Item)
- `specs/contracts/events/inbound-events.md`
- `specs/contracts/webhooks/erp-asn-webhook.md`
- `platform/error-handling.md`
- `platform/api-gateway-policy.md`
- `platform/security-rules.md`
- `rules/traits/transactional.md` — T1 (idempotency), T4 (state machine), T5 (optimistic lock)
- `rules/domains/wms.md` — W1, W2, W6 and Standard Error Codes
