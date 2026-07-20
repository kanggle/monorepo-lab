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

### Actor model (roles-only)

The PO **actor** (`BUYER` / `OPERATOR`) is derived from the verified JWT
`roles` claim — **not** from an OAuth scope and **not** from a separate
account-type claim. Per the roles-only identity model (ADR-MONO-032 D3 /
ADR-MONO-035; the `account_type` claim/`X-Account-Type` header were removed and
the gateways made role-based by TASK-MONO-262), `procurement-service` maps the
token to an actor via `ActorContext.isOperator()`:

- `roles ∋ {OPERATOR, ADMIN, SUPER_ADMIN}` → **OPERATOR** actor.
- any other authenticated caller → **BUYER** actor (the default mapping; BUYER
  is not a registered scope or account type).

scm's only OAuth **scopes** remain `scm.read` / `scm.write` (see
[`iam-integration.md`](../../integration/iam-integration.md) § Scopes); they
gate read-vs-write, orthogonal to the BUYER/OPERATOR actor split. Tenant
admission (`tenant_id ∈ {scm, *}` ∪ signed `entitled_domains ∋ scm`, ADR-MONO-019)
is a third, separate axis. `SUPPLIER` / `SYSTEM` actors are not token-derived
(webhook callers / internal transitions).

Webhook endpoints (`/api/procurement/webhooks/**`):
- Public (no JWT) — supplier callers have no IAM identity.
- Authenticated by **HMAC-SHA256 + timestamp + replay protection** (v2, per
  rules/traits/integration-heavy.md I6), enforced by a servlet filter before
  the controllers:
  - `X-Supplier-Signature` (required) — lowercase-hex HMAC-SHA256 digest.
  - `X-Supplier-Timestamp` (required) — epoch **seconds** the signature was
    produced.
  - **Signing input:** `timestamp + "." + rawBody` (the raw request bytes,
    pre-deserialization), keyed with the shared secret. The body must be HMAC'd
    over the raw bytes — a re-serialized body changes the digest.
  - **Freshness:** the timestamp must be within **300 seconds** of server time
    (`|now − ts| > 300s` rejects both stale and future-skewed deliveries).
  - **Replay:** the signature is the replay nonce; it is recorded for the
    window (Redis `SETNX`, TTL = window + 60s) and a repeated signature within
    the window is rejected.
  - **401 reasons** (all rendered as `{ "code": "UNAUTHORIZED", "message": <reason> }`):
    `WEBHOOK_SIGNATURE_INVALID` (missing/invalid/mismatched signature or
    malformed hex), `WEBHOOK_TIMESTAMP_INVALID` (missing/non-numeric/stale/
    future-skewed timestamp), `WEBHOOK_REPLAY_DETECTED` (signature already seen
    within the window).
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
- `Authorization: Bearer <token>` (BUYER or OPERATOR actor — see § Actor model)
- `Idempotency-Key: <string>` (required)

**Request body:** none.

**Response 200:** `{ "data": <PurchaseOrderResponse(status=SUBMITTED)>, "meta": { ... } }`

**Errors:** `PO_NOT_FOUND` (404), `PO_STATUS_TRANSITION_INVALID` (422),
`SUPPLIER_UNAVAILABLE` (503), `IDEMPOTENCY_KEY_REQUIRED` (400),
`CONCURRENT_MODIFICATION` (409, optimistic lock), `UNAUTHORIZED` (401), `TENANT_FORBIDDEN` (403).

---

## POST /api/procurement/po/{poId}/confirm

Transition `ACKNOWLEDGED → CONFIRMED`. OPERATOR actor only (i.e. the token's
`roles` claim must contain `OPERATOR`/`ADMIN`/`SUPER_ADMIN` — `ActorContext.isOperator()`;
see § Actor model).

**Headers:** same as submit.

**Request body:** none.

**Response 200:** PO with `status=CONFIRMED`, `confirmedAt` populated.

**Errors:** `PO_NOT_FOUND` (404), `PO_STATUS_TRANSITION_INVALID` (422),
`PERMISSION_DENIED` (403), other auth/idempotency errors per § Error Codes.

---

## POST /api/procurement/po/{poId}/cancel

Transition any of `DRAFT / SUBMITTED / ACKNOWLEDGED → CANCELED`. BUYER or
OPERATOR actor (see § Actor model). CONFIRMED+ POs cannot be canceled in v1
(corrective tasks deferred).

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
- `X-Supplier-Signature: <lowercase-hex HMAC-SHA256>` (required)
- `X-Supplier-Timestamp: <epoch-seconds>` (required)

See the webhook authentication block above for the signing input
(`timestamp + "." + rawBody`), the 300s freshness window, and replay rejection.

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

**Errors:** `WEBHOOK_SIGNATURE_INVALID` / `WEBHOOK_TIMESTAMP_INVALID` /
`WEBHOOK_REPLAY_DETECTED` (401, written directly by `WebhookSignatureFilter`
as `{ "code": "UNAUTHORIZED", "message": <reason> }`),
`PO_NOT_FOUND` (404), `PO_STATUS_TRANSITION_INVALID` (422 — only when the
PO is in a status that disallows ack, e.g. CANCELED), `VALIDATION_ERROR`
(422).

---

## POST /api/procurement/webhooks/asn

Inbound webhook — supplier delivers an Advance Shipment Notice. Creates an
`advance_shipment_notices` row (UNIQUE on `(tenantId, supplierAsnRef)` for
S2 idempotency) and applies each line to the matching PO line. Status
transitions per ASN coverage (CONFIRMED → PARTIALLY_RECEIVED → RECEIVED).

**Headers:**
- `X-Supplier-Signature: <lowercase-hex HMAC-SHA256>` (required)
- `X-Supplier-Timestamp: <epoch-seconds>` (required)

See the webhook authentication block above for the signing input
(`timestamp + "." + rawBody`), the 300s freshness window, and replay rejection.

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

**Errors:** `WEBHOOK_SIGNATURE_INVALID` / `WEBHOOK_TIMESTAMP_INVALID` /
`WEBHOOK_REPLAY_DETECTED` (401, written directly by `WebhookSignatureFilter`),
`PO_NOT_FOUND` (404),
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
| `UNAUTHORIZED` | 401 | Missing / invalid bearer token; or webhook HMAC failure (the `message` carries the specific reason below) |
| `WEBHOOK_SIGNATURE_INVALID` | 401 | Webhook `X-Supplier-Signature` missing, malformed-hex, or HMAC mismatch (rendered as `{code:UNAUTHORIZED, message:WEBHOOK_SIGNATURE_INVALID}`) |
| `WEBHOOK_TIMESTAMP_INVALID` | 401 | Webhook `X-Supplier-Timestamp` missing, non-numeric, or outside the 300s freshness window |
| `WEBHOOK_REPLAY_DETECTED` | 401 | Webhook signature already seen within the freshness window (replay) |
| `TENANT_FORBIDDEN` | 403 | `tenant_id` claim not in `{scm, *}` |
| `PERMISSION_DENIED` | 403 | Authenticated but lacks required scope/role |
| `PO_NOT_FOUND` | 404 | PO does not exist (or belongs to another tenant) |
| `SUPPLIER_NOT_FOUND` | 404 | Supplier id unknown |
| `CONCURRENT_MODIFICATION` | 409 | Optimistic-lock conflict (concurrent modification of the same aggregate — consumer may retry) |
| `CONFLICT` | 409 | DB **unique**-constraint violation (SQLSTATE 23505 — consumer should NOT retry without state change). FK / NOT NULL / CHECK violations are server defects and surface as `INTERNAL_ERROR` (500), not 409 (TASK-MONO-450). |
| `PO_STATUS_TRANSITION_INVALID` | 422 | Requested transition forbidden by `PoStatusMachine` (response includes `details: { from, to, actor }`) |
| `PO_ALREADY_CONFIRMED` | 422 | Mutation attempted on a PO past CONFIRMED that requires DRAFT semantics (e.g., line addition) |
| `PO_QUANTITY_EXCEEDED` | 422 | Supplier ack quantity exceeds ordered |
| `ASN_OVERRECEIPT` | 422 | Cumulative ASN received quantity exceeds ordered on a line |
| `SUPPLIER_INACTIVE` | 422 | Supplier status ≠ ACTIVE |
| `CATALOG_SKU_UNKNOWN` | 422 | SKU not found in supplier's catalog (S8 future use) |
| `ILLEGAL_STATE` | 422 | Aggregate invariant violation surfaced at controller boundary |
| `SUPPLIER_UNAVAILABLE` | 503 | Supplier circuit OPEN, retries exhausted, or bulkhead saturation |
| `INTERNAL_ERROR` | 500 | Unhandled exception, or a non-unique DB integrity violation (FK / NOT NULL / CHECK — a server defect, kept loud) |

---

## POST /api/procurement/po/from-suggestion (internal — ADR-MONO-027 D5)

**Additive** entry point for `demand-planning-service` to create a Purchase Order
in `DRAFT` from an approved reorder suggestion. Reuses the **existing** `DRAFT`
state + `PoStatusMachine` + audit/outbox — **no new PO state, no auto-SUBMIT.**

- **Caller**: `demand-planning-service` only (intra-scm, internal trust — same
  gateway-internal posture other scm services use). Not a public/operator route;
  the operator never calls this directly (they call demand-planning's
  `POST /suggestions/{id}/approve`, which calls this).
- **Idempotent on `sourceSuggestionId`** (NOT the generic `Idempotency-Key`
  header): a repeated call for the same suggestion returns the **existing** PO,
  never a duplicate. This is the cross-service idempotency key (S2).

**Request body:**
```json
{
  "supplierId": "9b1d4a8c-1f2c-7a90-b1d4-3e6f8a2c9d10",
  "currency": "KRW",
  "origin": "DEMAND_PLANNING",
  "sourceSuggestionId": "0192...",
  "lines": [
    { "lineNo": 1, "sku": "SKU-APPLE-001", "quantity": "100.0000", "unitPriceRef": "LAST_KNOWN" }
  ]
}
```

- `origin`: `DEMAND_PLANNING` (vs the default operator-authored origin) — recorded
  on the PO for provenance/audit.
- `unitPriceRef`: a price **reference/placeholder** (e.g. `LAST_KNOWN`), not a
  computed price — demand-planning invents no pricing; the operator sets the actual
  price during DRAFT review. (v1 may persist a 0/placeholder unit price pending review.)

**Response `201`:** the created (or existing, on idempotent re-call) PO in `DRAFT`,
carrying `origin=DEMAND_PLANNING` + `sourceSuggestionId`.

**Errors:** standard procurement codes. The PO is created in `DRAFT` only;
transition to `SUBMITTED` (supplier dispatch) remains the operator's existing flow.
procurement does **not** FK-validate `supplierId` (FK-free cross-service
convention) — an unknown supplier is caught by the operator at DRAFT review.

> Scope guard: this endpoint MUST NOT introduce demand/forecast concepts into
> procurement, and MUST NOT auto-advance the PO past `DRAFT`. It is a thin,
> idempotent DRAFT factory keyed by `sourceSuggestionId`.

---

## References

- [`procurement-service/architecture.md`](../../services/procurement-service/architecture.md)
- [`demand-planning-service/architecture.md`](../../services/demand-planning-service/architecture.md) — the caller (ADR-MONO-027 D5)
- [`gateway-public-routes.md`](./gateway-public-routes.md)
- `platform/error-handling.md`
- `rules/domains/scm.md` § Standard Error Codes — Procurement
- `rules/traits/transactional.md` (T1 idempotency, T4 state machine)
- `rules/traits/integration-heavy.md` (I6 webhook security, I7/I8 vendor isolation)
- TASK-SCM-BE-002 — bootstrap PR #239 (shipped implementation)
- TASK-SCM-BE-006 — this contract authoring task (retroactive)
