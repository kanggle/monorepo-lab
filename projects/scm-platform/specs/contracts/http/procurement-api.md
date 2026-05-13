# API Contract — procurement-service

Base path: `/api/procurement` (rewritten by gateway from `/api/v1/procurement`).

All non-webhook endpoints:
- Require Bearer token with `tenant_id ∈ {scm, *}`.
- Mutating endpoints require `Idempotency-Key: <client-generated>` header
  (rules/traits/transactional.md T1, S2). Missing header → 400
  `IDEMPOTENCY_KEY_REQUIRED`. Same key + different payload → 422
  `IDEMPOTENCY_KEY_MISMATCH`.
- Success envelope: `{ data, meta: { timestamp, ... } }`.
- Error envelope: `{ code, message, details?, timestamp }`.

Webhook endpoints (`/api/procurement/webhooks/**`):
- Public (no JWT) — supplier callers have no GAP identity.
- Verified by `X-Supplier-Signature: <shared-secret>` header (v1 simple
  shared secret; HMAC + timestamp + replay protection deferred to v2 per
  rules/traits/integration-heavy.md I6).
- Tenant-scoped via the request body `tenantId` field; the database
  `(tenant_id, supplier_asn_ref)` UNIQUE constraint is the structural
  backstop for replays.

---

## POST /api/procurement/po

Draft a new Purchase Order. Initial status = `DRAFT`.

**Headers:**
- `Authorization: Bearer <token>` (required)
- `Idempotency-Key: <string>` (required)
- `Content-Type: application/json`

**Request body:**
```json
{
  "supplierId": "9b1d4a8c-1f2c-7a90-b1d4-3e6f8a2c9d10",
  "currency": "KRW",
  "lines": [
    {
      "lineNo": 1,
      "sku": "SKU-001",
      "supplierSku": "SUP-001-A",
      "quantity": "10.0000",
      "unitPrice": "12500.00"
    }
  ]
}
```

Validation:
- `supplierId` — required, ≤ 36 chars
- `currency` — required, exactly 3 chars (ISO 4217)
- `lines` — required, ≥ 1 element, every line:
  - `lineNo` — positive integer; unique per PO (DB UNIQUE)
  - `sku` — required, ≤ 100 chars
  - `supplierSku` — optional, ≤ 100 chars
  - `quantity` — required, > 0, decimal(18, 4)
  - `unitPrice` — required, ≥ 0, decimal(18, 2)

**Response 201 (Created):**
```json
{
  "data": {
    "id": "01HZWX...",
    "tenantId": "scm",
    "poNumber": "PO-A1B2C3D4",
    "supplierId": "9b1d4a8c-...",
    "buyerAccountId": "7c2e9f5a-...",
    "status": "DRAFT",
    "totalAmount": "125000.00",
    "currency": "KRW",
    "submittedAt": null,
    "acknowledgedAt": null,
    "confirmedAt": null,
    "canceledAt": null,
    "createdAt": "2026-05-11T08:30:00Z",
    "updatedAt": "2026-05-11T08:30:00Z",
    "lines": [
      {
        "id": "01HZWX...",
        "lineNo": 1,
        "sku": "SKU-001",
        "supplierSku": "SUP-001-A",
        "quantity": "10.0000",
        "unitPrice": "12500.00",
        "receivedQuantity": "0.0000"
      }
    ]
  },
  "meta": { "timestamp": "2026-05-11T08:30:00Z" }
}
```

**Errors:** `IDEMPOTENCY_KEY_REQUIRED` (400), `IDEMPOTENCY_KEY_MISMATCH` (422),
`SUPPLIER_NOT_FOUND` (404), `SUPPLIER_INACTIVE` (422), `VALIDATION_ERROR`
(400/422), `TENANT_FORBIDDEN` (403), `UNAUTHORIZED` (401).

---

## GET /api/procurement/po

Search POs (paginated, tenant-scoped).

**Query parameters:**
- `status` — optional `PoStatus` filter (one of `DRAFT`, `SUBMITTED`,
  `ACKNOWLEDGED`, `CONFIRMED`, `PARTIALLY_RECEIVED`, `RECEIVED`, `SETTLED`,
  `CLOSED`, `CANCELED`)
- `supplierId` — optional supplier id filter
- `page` — default 0
- `size` — default 20

**Response 200:**
```json
{
  "data": {
    "content": [ /* PurchaseOrderResponse list */ ],
    "page": 0,
    "size": 20,
    "totalElements": 42,
    "totalPages": 3
  },
  "meta": { "timestamp": "..." }
}
```

Sort order: `createdAt DESC` (fixed).

---

## GET /api/procurement/po/{poId}

Fetch one PO by id. Tenant-scoped — cross-tenant lookups return
`PO_NOT_FOUND` (deliberate, no enumeration leak).

**Response 200:** `{ "data": <PurchaseOrderResponse>, "meta": { "timestamp": "..." } }`

**Errors:** `PO_NOT_FOUND` (404), `UNAUTHORIZED` (401), `TENANT_FORBIDDEN` (403).

---

## POST /api/procurement/po/{poId}/submit

Transition `DRAFT → SUBMITTED` and dispatch the PO to the supplier through
`SupplierAdapterPort` (resilience4j-decorated, S2 idempotency carried).
Failure of the supplier call rolls back the transition (Failure Mode #5 /
Edge Case #7 in architecture.md) — the PO stays `DRAFT` for retry.

**Headers:**
- `Authorization: Bearer <token>` (BUYER or OPERATOR scope)
- `Idempotency-Key: <string>` (required)

**Request body:** none.

**Response 200:** `{ "data": <PurchaseOrderResponse(status=SUBMITTED)>, "meta": { ... } }`

**Errors:** `PO_NOT_FOUND` (404), `PO_STATUS_TRANSITION_INVALID` (422),
`SUPPLIER_UNAVAILABLE` (503), `IDEMPOTENCY_KEY_REQUIRED` (400),
`CONCURRENT_MODIFICATION` (409, optimistic lock), `UNAUTHORIZED` (401), `TENANT_FORBIDDEN` (403).

---

## POST /api/procurement/po/{poId}/confirm

Transition `ACKNOWLEDGED → CONFIRMED`. OPERATOR actor only.

**Headers:** same as submit.

**Request body:** none.

**Response 200:** PO with `status=CONFIRMED`, `confirmedAt` populated.

**Errors:** `PO_NOT_FOUND` (404), `PO_STATUS_TRANSITION_INVALID` (422),
`PERMISSION_DENIED` (403), other auth/idempotency errors per § Error Codes.

---

## POST /api/procurement/po/{poId}/cancel

Transition any of `DRAFT / SUBMITTED / ACKNOWLEDGED → CANCELED`. BUYER or
OPERATOR. CONFIRMED+ POs cannot be canceled in v1 (corrective tasks deferred).

**Request body (optional):**
```json
{ "reason": "Buyer canceled — supplier delay > SLA" }
```
- `reason` — optional, ≤ 200 chars.

**Response 200:** PO with `status=CANCELED`, `canceledAt` populated,
`cancellationReason` echoed.

**Errors:** `PO_NOT_FOUND` (404), `PO_STATUS_TRANSITION_INVALID` (422)
(e.g., already CONFIRMED+).

---

## POST /api/procurement/webhooks/supplier-ack

Inbound webhook — supplier acknowledges a previously-submitted PO. Triggers
`SUBMITTED → ACKNOWLEDGED` transition (SUPPLIER actor) inside a transaction
that also writes `po_status_history`, `audit_log`, and the
`scm.procurement.po.acknowledged.v1` outbox entry.

**Headers:**
- `X-Supplier-Signature: <shared-secret>` (required)

**Request body:**
```json
{
  "tenantId": "scm",
  "poId": "01HZWX...",
  "supplierAckRef": "SUP-ACK-2026-0001"
}
```

Validation:
- `tenantId` — required, ≤ 64 chars
- `poId` — required, ≤ 36 chars
- `supplierAckRef` — required, ≤ 100 chars

**Response 200:** `{ "data": <PurchaseOrderResponse>, "meta": { ... } }`

**Idempotency:** if the PO is already in
`ACKNOWLEDGED / CONFIRMED / PARTIALLY_RECEIVED / RECEIVED / SETTLED / CLOSED`,
the call returns the current PO without state change (idempotent no-op,
logged at INFO).

**Errors:** `WEBHOOK_SIGNATURE_INVALID` (401, raised as
`ResponseStatusException` and mapped by `GlobalExceptionHandler`),
`PO_NOT_FOUND` (404), `PO_STATUS_TRANSITION_INVALID` (422 — only when the
PO is in a status that disallows ack, e.g. CANCELED), `VALIDATION_ERROR`
(422).

---

## POST /api/procurement/webhooks/asn

Inbound webhook — supplier delivers an Advance Shipment Notice. Creates an
`advance_shipment_notices` row (UNIQUE on `(tenantId, supplierAsnRef)` for
S2 idempotency) and applies each line to the matching PO line. Status
transitions per ASN coverage (CONFIRMED → PARTIALLY_RECEIVED → RECEIVED).

**Headers:** `X-Supplier-Signature: <shared-secret>`.

**Request body:**
```json
{
  "tenantId": "scm",
  "poId": "01HZWX...",
  "supplierAsnRef": "ASN-2026-0001",
  "expectedArrivalAt": "2026-05-15T10:00:00Z",
  "lines": [
    { "poLineId": "01HZWX...", "quantityShipped": "5.0000" }
  ]
}
```

Validation:
- `tenantId` — required, ≤ 64 chars
- `poId` — required, ≤ 36 chars
- `supplierAsnRef` — required, ≤ 100 chars
- `expectedArrivalAt` — required, ISO 8601 instant
- `lines` — required, ≥ 1 element; each line:
  - `poLineId` — required, ≤ 36 chars
  - `quantityShipped` — required, > 0, decimal(18, 4)

**Response 200:**
```json
{
  "data": {
    "id": "01HZWX...",
    "poId": "01HZWX...",
    "tenantId": "scm",
    "supplierAsnRef": "ASN-2026-0001",
    "expectedArrivalAt": "2026-05-15T10:00:00Z",
    "receivedAt": "2026-05-11T08:45:00Z",
    "lines": [
      {
        "id": "01HZWX...",
        "poLineId": "01HZWX...",
        "quantityShipped": "5.0000",
        "quantityReceived": null
      }
    ]
  },
  "meta": { "timestamp": "..." }
}
```

**Idempotency:** duplicate webhook with the same `(tenantId, supplierAsnRef)`
returns the previously-stored ASN with the original receivedAt.

**Errors:** `WEBHOOK_SIGNATURE_INVALID` (401), `PO_NOT_FOUND` (404),
`PO_STATUS_TRANSITION_INVALID` (422 — e.g., applying ASN to a CANCELED PO),
`ASN_OVERRECEIPT` (422 — cumulative received > ordered on the line),
`VALIDATION_ERROR` (422).

---

## Local management endpoints

| Path | Auth | Description |
|---|---|---|
| `GET /actuator/health` | none | liveness/readiness probe |
| `GET /actuator/info` | none | build info |
| `GET /actuator/prometheus` | network-isolated | metrics (internal docker network only) |

---

## Error Codes

| Code | HTTP | Description |
|---|---|---|
| `IDEMPOTENCY_KEY_REQUIRED` | 400 | Mutating endpoint called without the `Idempotency-Key` header |
| `IDEMPOTENCY_KEY_MISMATCH` | 422 | Same `Idempotency-Key` reused with a different payload hash |
| `VALIDATION_ERROR` | 400/422 | Bean Validation, malformed body, or type mismatch |
| `UNAUTHORIZED` | 401 | Missing / invalid bearer token; webhook signature missing |
| `WEBHOOK_SIGNATURE_INVALID` | 401 | Webhook `X-Supplier-Signature` does not match |
| `TENANT_FORBIDDEN` | 403 | `tenant_id` claim not in `{scm, *}` |
| `PERMISSION_DENIED` | 403 | Authenticated but lacks required scope/role |
| `PO_NOT_FOUND` | 404 | PO does not exist (or belongs to another tenant) |
| `SUPPLIER_NOT_FOUND` | 404 | Supplier id unknown |
| `CONCURRENT_MODIFICATION` | 409 | Optimistic-lock conflict (concurrent modification of the same aggregate — consumer may retry) |
| `CONFLICT` | 409 | DB integrity violation (FK / unique constraint — consumer should NOT retry without state change) |
| `PO_STATUS_TRANSITION_INVALID` | 422 | Requested transition forbidden by `PoStatusMachine` (response includes `details: { from, to, actor }`) |
| `PO_ALREADY_CONFIRMED` | 422 | Mutation attempted on a PO past CONFIRMED that requires DRAFT semantics (e.g., line addition) |
| `PO_QUANTITY_EXCEEDED` | 422 | Supplier ack quantity exceeds ordered |
| `ASN_OVERRECEIPT` | 422 | Cumulative ASN received quantity exceeds ordered on a line |
| `SUPPLIER_INACTIVE` | 422 | Supplier status ≠ ACTIVE |
| `CATALOG_SKU_UNKNOWN` | 422 | SKU not found in supplier's catalog (S8 future use) |
| `ILLEGAL_STATE` | 422 | Aggregate invariant violation surfaced at controller boundary |
| `SUPPLIER_UNAVAILABLE` | 503 | Supplier circuit OPEN, retries exhausted, or bulkhead saturation |
| `INTERNAL_ERROR` | 500 | Unhandled exception |

---

## References

- [`procurement-service/architecture.md`](../../services/procurement-service/architecture.md)
- [`gateway-public-routes.md`](./gateway-public-routes.md)
- `platform/error-handling.md`
- `rules/domains/scm.md` § Standard Error Codes — Procurement
- `rules/traits/transactional.md` (T1 idempotency, T4 state machine)
- `rules/traits/integration-heavy.md` (I6 webhook security, I7/I8 vendor isolation)
- TASK-SCM-BE-002 — bootstrap PR #239 (shipped implementation)
- TASK-SCM-BE-006 — this contract authoring task (retroactive)
