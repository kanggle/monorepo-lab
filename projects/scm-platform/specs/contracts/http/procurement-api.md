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
[`iam-integration.md`](../../integration/iam-integration.md) § Scopes); they are
**not** checked by this service — read-vs-write is the business-role axis below (TASK-MONO-792). Tenant
admission (`tenant_id ∈ {scm, *}` ∪ signed `entitled_domains ∋ scm`, ADR-MONO-019)
is a third, separate axis. `SUPPLIER` / `SYSTEM` actors are not token-derived
(webhook callers / internal transitions).

### Business roles (ADR-MONO-083 P5 · TASK-MONO-792)

A fourth axis, checked **first** by the service's filter chain (URL × method), before the actor model.
Canonical table: [`iam-integration.md` § 업무 역할](../../integration/iam-integration.md#업무-역할-adr-mono-083-p5--task-mono-792--정본).
For this service:

| Request | Required (any of) |
|---|---|
| `GET /api/procurement/**` | `SCM_PROCUREMENT_READ` · `SCM_PROCUREMENT_WRITE` |
| `POST /api/procurement/po/from-suggestion` | `SCM_PROCUREMENT_WRITE` · `SCM_DEMAND_PLANNING_WRITE` (DRAFT only — owner decision Q2) |
| every other `/api/procurement/**` mutation (`POST /po` · `submit` · `confirm` · `cancel` · `POST /suppliers`) | `SCM_PROCUREMENT_WRITE` |
| `/api/procurement/webhooks/**` | none — HMAC (unchanged) |

Missing → `403 PERMISSION_DENIED`. The actor model above still applies on top (confirm = OPERATOR actor; supplier
registration = `requireOperator()`), so the role gate never widens what an actor may do. `scm.read` / `scm.write`
scopes open nothing.

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
    "supplierCode": "SUP-0043",
    "supplierName": "Acme Components",
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

### `PurchaseOrderResponse` — supplier reference fields (`supplierCode`, `supplierName`)

Every endpoint whose body is a `PurchaseOrderResponse` — `POST /po`,
`POST /po/from-suggestion`, `GET /po` (each `content[]` element),
`GET /po/{poId}`, `POST /po/{poId}/submit|confirm|cancel` and the
`POST /webhooks/supplier-ack` response — carries two read-only fields next to
`supplierId` (TASK-MONO-677, owner decision ⓐ «id·code 둘 다 조인»):

| Field | Type | Nullable | Meaning |
|---|---|---|---|
| `supplierCode` | string | **yes** | `code` of the resolved supplier master row (§ `GET /api/procurement/suppliers/{supplierId}`) |
| `supplierName` | string | **yes** | `name` of the same row |

Resolution rule (normative):

1. **`supplierId` is unchanged** — it is still what the write path stored, and
   it is not a foreign key. By write path it holds: `POST /po` → a supplier
   master **id** (the draft use case looks the supplier up by id and rejects an
   unknown one with `SUPPLIER_NOT_FOUND`); `POST /po/from-suggestion` → whatever
   demand-planning sent, which per `scm-procurement-events.md` § inbound-expected
   (ADR-MONO-050 D9) is the supplier **code** and is not validated here.
2. Look the supplier up **inside the PO's own tenant**, first by
   `id = supplierId`, and only if that finds no row, by `code = supplierId`.
   An id match wins over a code match.
3. If neither matches — **including a row that exists only in another
   tenant** — both fields are `null`. Never an empty string, never a copy of
   `supplierId`.
4. Supplier status is not a filter. An `INACTIVE` / `CONTRACT_EXPIRED` supplier
   still resolves: v1 has no supplier delete path, the row keeps its name, and
   a PO references the supplier it was placed with.
5. The fields are computed when the response is built; nothing is stored on the
   PO. `GET /po` resolves a whole page with at most two batched lookups (by ids,
   then by codes for the refs the first lookup did not match), never one lookup
   per row. An idempotent replay (same `Idempotency-Key`) returns the response
   stored at first execution, including these two fields as they were then.
6. Neither field is a query filter — `GET /po?supplierId=` keeps matching the
   stored `supplierId` exactly.

---

## GET /api/procurement/po

Search POs (paginated, tenant-scoped).

**Query parameters:**
- `status` — optional `PoStatus` filter (one of `DRAFT`, `SUBMITTED`,
  `ACKNOWLEDGED`, `CONFIRMED`, `PARTIALLY_RECEIVED`, `RECEIVED`, `SETTLED`,
  `CLOSED`, `CANCELED`)
- `excludeStatus` — optional single `PoStatus` (same values as `status`); drops POs whose
  **current** status is that value. ANDed with every other filter. An unknown value →
  **400 `VALIDATION_ERROR`** (as for `status`)
- `supplierId` — optional supplier id filter
- `submittedFrom` — optional ISO-8601 instant (`2026-10-19T00:00:00+09:00`,
  `2026-10-18T15:00:00Z`); keeps POs with `submittedAt >= submittedFrom` (**inclusive**)
- `submittedTo` — optional ISO-8601 instant; keeps POs with `submittedAt < submittedTo`
  (**exclusive**)
- `page` — default 0
- `size` — default 20

**Submit-date filter (TASK-MONO-810).** `submittedFrom` / `submittedTo` filter on
`submittedAt` — **never `createdAt`** — over the half-open range `[submittedFrom,
submittedTo)`, the same attribution and the same half-open rule as `poCount` in
§ GET /po/metrics. Either bound may be given alone. When either bound is given, a PO with
no `submittedAt` (a `DRAFT`, never submitted) is **excluded**. The bounds combine with
`status`, `excludeStatus` and `supplierId` by AND. A value that is not an ISO-8601 instant (no offset, a bare date,
garbage) → **400 `VALIDATION_ERROR`**. `submittedFrom >= submittedTo` is not an error —
the range is empty and so is the page. A `+` offset must be percent-encoded in the query
string (`%2B09:00`); a raw `+` decodes to a space and the value no longer parses.

**Metrics card link (TASK-MONO-810).** The console links a `poCount` / `poAmount` card to
`GET /po?submittedFrom=<current.from>&submittedTo=<current.to>&excludeStatus=CANCELED`.
With exactly those three parameters the page's `totalElements` **equals that window's
`poCount`**: the same `submittedAt` attribution, the same half-open range, and the same
exclusion (current status `CANCELED`; `DRAFT` has no `submittedAt`). Without
`excludeStatus=CANCELED` the list also shows POs canceled after submit and the numbers
differ. The one residual gap is display precision: `current.to` is truncated to whole
seconds on the wire, so a PO submitted in the sub-second before the metrics call is counted
but not listed.

**Response 200:**
```json
{
  "data": {
    "content": [ /* PurchaseOrderResponse list — each element carries supplierCode / supplierName (see § POST /po, supplier reference fields) */ ],
    "page": 0,
    "size": 20,
    "totalElements": 42,
    "totalPages": 3
  },
  "meta": { "timestamp": "..." }
}
```

Sort order: `createdAt DESC` (fixed).

**Errors:** `VALIDATION_ERROR` (400 — unknown `status` / `excludeStatus`, malformed
`submittedFrom` / `submittedTo`), `UNAUTHORIZED` (401), `PERMISSION_DENIED` (403), `TENANT_FORBIDDEN` (403).

---

## GET /api/procurement/po/metrics

Overview performance metrics — the «실적» strip of the console SCM overview
(TASK-MONO-810, ADR-MONO-084 P5). Shape, windows and the integer-string rule are the
shared [`performance-metrics-contract.md`](../../../../../platform/contracts/performance-metrics-contract.md);
this section owns only what each key counts (ADR-MONO-084 D5).

The literal `/po/metrics` path is matched ahead of `/po/{poId}` — `metrics` is never
read as a PO id.

**Authorization / tenancy:** the same as every other `GET /api/procurement/**` —
`SCM_PROCUREMENT_READ` ∨ `SCM_PROCUREMENT_WRITE` (§ Business roles), tenant from the
token's `tenant_id`. Every number covers only that tenant's POs.

**Query parameters:**
- `period` — **required**, `week` | `month` (case-insensitive).

**Response 200:** the shared `PerformanceMetrics` body, **bare** (no `{ data, meta }`
envelope — the shared contract's shape), every value an integer string:

```json
{
  "period": "month",
  "timezone": "Asia/Seoul",
  "current":  { "from": "2026-10-01T00:00:00+09:00", "to": "2026-10-24T14:05:00+09:00" },
  "previous": { "from": "2026-09-01T00:00:00+09:00", "to": "2026-09-24T14:05:00+09:00" },
  "metrics": [
    { "key": "poCount",      "unit": "count", "currency": null,  "current": "12",      "previous": "9" },
    { "key": "poAmount:KRW", "unit": "money", "currency": "KRW", "current": "1200000", "previous": "900000" },
    { "key": "poAmount:USD", "unit": "money", "currency": "USD", "current": "12345",   "previous": "0" },
    { "key": "receivedCount","unit": "count", "currency": null,  "current": "4",       "previous": "6" }
  ],
  "series": [
    { "date": "2026-10-01", "values": { "poCount": "1", "poAmount:KRW": "100000", "poAmount:USD": "0", "receivedCount": "0" } }
  ]
}
```

**Metric keys (normative — one line each):**

| Key | Unit | Counts |
|---|---|---|
| `poCount` | count | POs whose `submittedAt` ∈ the window **and** whose **current** status is not `CANCELED`. A `DRAFT` has no `submittedAt` and never counts. 🔴 A PO canceled after it was submitted leaves its submit window when it is canceled — **a past window's value can decrease**. |
| `poAmount:<CUR>` | money | Σ `totalAmount` of exactly the `poCount` set, **one key per currency** (`currency` = the PO's own 3-letter code), never converted or added across currencies (ADR-MONO-084 D2 · OD-3). The per-currency sum is taken in decimal and converted **once** to integer minor units with that currency's ISO-4217 default fraction digits (KRW 0, USD 2, …; a code that is not ISO-4217, or an ISO code with no minor unit such as `XXX` → 2), rounding **HALF_UP**. A PO drafted from a suggestion and submitted at unit price 0 counts in `poCount` and adds 0 here. Same decrease-on-cancel caveat as `poCount`. |
| `receivedCount` | count | Distinct POs with a status-history transition **to `RECEIVED`** whose `occurredAt` ∈ the window — attributed to the **receipt time**, not the submit time (owner decision 2026-10-24), so a past window's value does not change. Its current status does not matter. 🔴 The supplier ASN path does not yet check the PO's status before applying a full receipt (`TASK-SCM-BE-063`), so a PO received after it was canceled is counted here. |

- **Order:** `poCount`, then the `poAmount:<CUR>` keys sorted by currency code, then
  `receivedCount`.
- **Which currencies appear:** the union of the currencies that occur in the `poCount`
  set of the **current or the previous** window, so `current` and `previous` of a key
  are always comparable. When neither window has any, a single `poAmount:KRW` with
  `"0"` / `"0"` is returned (the strip keeps its money cell).
- **Series:** every key on every KST date of `current`, zero-filled. A day's
  `poAmount:<CUR>` is that day's decimal sum converted with the same rule — rounded per
  day — so the days add up to the window's total exactly whenever every amount is a whole
  number of minor units (always for a currency with 2 or more fraction digits, since
  amounts are stored with 2 decimals); only a fraction below the minor unit (e.g. KRW
  `0.50`) can make the day sum differ from the total, by rounding alone.
- **Drill-down:** `GET /po?submittedFrom=<current.from>&submittedTo=<current.to>&excludeStatus=CANCELED`
  lists exactly the POs behind `poCount` / `poAmount`; its `totalElements` equals `poCount`
  for that window (§ GET /po, «Metrics card link»).

**Errors:**

| Status | Code | When |
|---|---|---|
| 400 | `VALIDATION_ERROR` | `period` missing or not `week` / `month` |
| 401 | `UNAUTHORIZED` | no / invalid token |
| 403 | `PERMISSION_DENIED` | neither `SCM_PROCUREMENT_READ` nor `SCM_PROCUREMENT_WRITE` |
| 403 | `TENANT_FORBIDDEN` | the token's tenant is not admitted |

---

## GET /api/procurement/po/{poId}

Fetch one PO by id. Tenant-scoped — cross-tenant lookups return
`PO_NOT_FOUND` (deliberate, no enumeration leak).

**Response 200:** `{ "data": <PurchaseOrderResponse>, "meta": { "timestamp": "..." } }` —
including `supplierCode` / `supplierName` resolved per § POST /po, supplier reference fields.

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

Transition `ACKNOWLEDGED → CONFIRMED`. `SCM_PROCUREMENT_WRITE` (§ Business roles) **and** OPERATOR
actor (i.e. the token's `roles` claim must contain `OPERATOR`/`ADMIN`/`SUPER_ADMIN`/`SCM_OPERATOR` —
`ActorContext.isOperator()`; see § Actor model). The role gate does not widen the state machine.

**Headers:** same as submit.

**Request body:** none.

**Response 200:** PO with `status=CONFIRMED`, `confirmedAt` populated.

**Errors:** `PO_NOT_FOUND` (404), `PO_STATUS_TRANSITION_INVALID` (422),
`PERMISSION_DENIED` (403), other auth/idempotency errors per § Error Codes.

---

## POST /api/procurement/po/{poId}/cancel

Transition any of `DRAFT / SUBMITTED / ACKNOWLEDGED / CONFIRMED → CANCELED`. BUYER or
OPERATOR actor (see § Actor model). `CONFIRMED → CANCELED` (a confirmed, not yet received
PO) is allowed since ADR-MONO-050 D6.3 / SCM-BE-036 (`PoStatusMachine`); once any goods
are received (`PARTIALLY_RECEIVED`, `RECEIVED`) or the PO is `SETTLED` / `CLOSED` /
already `CANCELED`, it cannot be canceled in v1 (returns are out of scope).

**Request body (optional):**
```json
{ "reason": "Buyer canceled — supplier delay > SLA" }
```
- `reason` — optional, ≤ 200 chars.

**Response 200:** PO with `status=CANCELED`, `canceledAt` populated,
`cancellationReason` echoed.

**Errors:** `PO_NOT_FOUND` (404), `PO_STATUS_TRANSITION_INVALID` (422)
(e.g., already `PARTIALLY_RECEIVED`+ or `CANCELED`).

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

## POST /api/procurement/suppliers

Register a supplier in the v1 internal master. Initial status = `ACTIVE`.

**Decision record:** `ADR-SCM-001` (ACCEPTED 2026-08-08, option A) — the v1 supplier
master is an **operated** object, so it gets an operator-facing write surface rather
than being filled by migrations. Before this endpoint existed, `POST /api/procurement/po`
answered `SUPPLIER_NOT_FOUND` and there was **no** way to create the row through any
API — the service's own e2e suite and the demo seed both wrote it with direct SQL.

🔴 **Credentials are not part of this endpoint and there is no other v1 path that
accepts them.** `supplier_credentials` / `SupplierCredentialsEncryptor` exist but have
zero call sites (measured 2026-08-08), and `SupplierAdapterPort` never reads a
credential — so **a supplier with no credentials is the normal v1 state**. Credential
handling is deferred whole to the v2 `supplier-service` (ADR-SCM-001 ACCEPT rider).
Do not add credential fields here; that decision belongs to v2.

**Authorization:** `SCM_PROCUREMENT_WRITE` (filter chain, § Business roles) **∧** OPERATOR actor —
`roles ∋ {OPERATOR, ADMIN, SUPER_ADMIN, SCM_OPERATOR}` (`ActorContext.isOperator()`). A BUYER-actor token → 403
`PERMISSION_DENIED`. (TASK-MONO-792: `SCM_OPERATOR` is now derived by auth-service whenever the token carries an
SCM business role, so an assumed operator holding `SCM_PROCUREMENT_WRITE` is also an OPERATOR actor.)

🔴 **Role only — `scm.write` is deliberately NOT required here, and that is a
measurement, not a preference.** The assumed operator token carries the
`platform-console-web` client's *registered* scopes (`openid profile email
tenant.read erp.write` — auth-service V0015 + V0023); `scm.write` is registered
on the separate `scm` client (V0013) and is **not** on the console one. Requiring
it would have made supplier registration unreachable for the only operator
identity that exists — the same shape as wms master-data writes, where
`MASTER_WRITE` is granted to nobody (TASK-MONO-514). The role side does resolve:
a `demo-corp` tenant holds an ACTIVE `scm` subscription →
`OperatorRoleDerivation` mints `SCM_OPERATOR`.

When a machine client legitimately needs to write suppliers, add
`hasScope("scm.write") ∨ isOperator()` — the erp masterdata precedent
(`RoleScopeAuthorizationAdapter`, WRITE arm). That requires giving scm's
`ActorContext` a scope axis, which it does not have today; do it when a caller
exists, not before.

**Headers:**
- `Authorization: Bearer <token>` (required)
- `Idempotency-Key: <string>` (required)
- `Content-Type: application/json`

**Request body:**
```json
{
  "code": "SUP-ACME-001",
  "name": "ACME Components Co.",
  "contractStartedAt": "2026-01-01T00:00:00Z",
  "contractExpiresAt": "2027-01-01T00:00:00Z"
}
```

Validation:
- `code` — required, ≤ 64 chars, `[A-Z0-9][A-Z0-9_-]*` (the **natural key**; unique
  per tenant — DB UNIQUE `(tenant_id, code)`)
- `name` — required, ≤ 200 chars
- `contractStartedAt` / `contractExpiresAt` — optional ISO-8601 instants;
  if both present, `contractExpiresAt` **must** be after `contractStartedAt`
  → else 422 `VALIDATION_ERROR`

**Response 201 (Created):**
```json
{
  "data": {
    "id": "9b1d4a8c-1f2c-7a90-b1d4-3e6f8a2c9d10",
    "tenantId": "scm",
    "code": "SUP-ACME-001",
    "name": "ACME Components Co.",
    "status": "ACTIVE",
    "contractStartedAt": "2026-01-01T00:00:00Z",
    "contractExpiresAt": "2027-01-01T00:00:00Z",
    "createdAt": "2026-08-08T08:30:00Z",
    "updatedAt": "2026-08-08T08:30:00Z"
  },
  "meta": { "timestamp": "2026-08-08T08:30:00Z" }
}
```

**Idempotency — two different mechanisms, both required:**

| Repeat | Result |
|---|---|
| Same `Idempotency-Key` + same payload | replayed **201**, no new row (S2, as every mutating endpoint here) |
| **Different** `Idempotency-Key`, same `code` | **200 OK** with the existing record — *not* 201, and **no second row** |

🔴 The second line is the one that matters for seeds and reruns, and it is why the
response code differs: a caller that lost its idempotency key (a re-run seed, a fresh
CI job) must converge, not duplicate. **Judge it by row count, not by the status
line** — a naive "created" log on a 2xx made `seed-scm.sh` mis-report a replay as a
creation once already.

**Errors:** `IDEMPOTENCY_KEY_REQUIRED` (400), `IDEMPOTENCY_KEY_MISMATCH` (422),
`VALIDATION_ERROR` (400/422), `PERMISSION_DENIED` (403), `TENANT_FORBIDDEN` (403),
`UNAUTHORIZED` (401), `CONFLICT` (409).

`CONFLICT` covers exactly one case: two *concurrent* registrations of the same
`code`, where both pass the existence check before either commits and the loser
trips `ux_suppliers_tenant_code`. It is a race outcome, not the answer to a
sequential re-registration — that one converges with 200 above. Retrying a 409
converges.

---

## GET /api/procurement/suppliers

List suppliers (paginated, tenant-scoped).

**Query:** `code` (exact, optional) · `status` (optional) · `page` (default 0) ·
`size` (default 20).

**Response 200:**
```json
{
  "data": {
    "content": [ /* supplier list — element shape identical to the 201 body's data */ ],
    "page": 0,
    "size": 20,
    "totalElements": 3,
    "totalPages": 1
  },
  "meta": { "timestamp": "..." }
}
```

Sort order: `createdAt DESC` (fixed).

> 🔵 This paginated envelope is `GET /api/procurement/po`'s, unchanged. An
> earlier draft of this section described a flat `data: [...]` with the page
> counters on `meta` — a shape this service's `ApiEnvelope` + `PageResponse`
> pair does not produce, so it would have made the two list endpoints in one
> file answer in two different formats. Corrected before implementation.

---

## GET /api/procurement/suppliers/{supplierId}

**Response 200:** `{ "data": <supplier>, "meta": { "timestamp" } }`
**Errors:** `SUPPLIER_NOT_FOUND` (404 — also when the row belongs to another tenant).

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
  "supplierId": "SUP-0043",
  "currency": "KRW",
  "origin": "DEMAND_PLANNING",
  "sourceSuggestionId": "0192...",
  "lines": [
    { "lineNo": 1, "sku": "SKU-APPLE-001", "quantity": "100.0000", "unitPriceRef": "LAST_KNOWN" }
  ]
}
```

- `supplierId`: the supplier **code** demand-planning copied from `sku_supplier_map`
  (ADR-MONO-050 §7 D9), not a supplier master id — see § supplier reference fields rule 1.
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
