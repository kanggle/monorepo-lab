# HTTP Contract: settlement-service

## Overview
Published HTTP API for settlement-service (ADR-MONO-030 Step 4 facet b — marketplace
seller settlement / commission). All endpoints are accessible through
gateway-service only. **Operator-plane only** (`/api/admin/**`) — there is no
consumer-facing settlement surface. All endpoints require an authenticated
operator (Bearer token, `roles ∋ ECOMMERCE_OPERATOR` — the platform-console operator's
ADR-MONO-035 4a assume-tenant-derived domain role; the gateway gates
`/api/admin/**` on `roles ∋ ECOMMERCE_OPERATOR`).

> **No write path for accruals.** Commission is booked **only** from the
> order/payment event streams (see `settlement-subscriptions.md`). The HTTP
> surface is read + commission-rate admin. Setting a rate is **prospective** — it
> never rewrites already-booked accruals (ledger immutability, F3).

---

## Base Path
`/api/admin/settlements`

---

## Tenancy & Seller Scope (ADR-MONO-030)

- **Tenant** — every endpoint is implicitly scoped to the caller's tenant
  (gateway `X-Tenant-Id`, derived from the operator's `tenant_id` claim). A
  resource in another tenant resolves to **404** (M3). Never a request field.
- **Seller scope (ABAC `org_scope`, ADR-025, net-zero / fail-OPEN)** — the
  operator's seller-scope claim (gateway `X-Seller-Scope`) filters reads:
  absent / `'*'` → **unrestricted** (tenant-operator sees all sellers);
  restricted → only the operator's `seller_id` rows. Always applied **inside** the
  tenant filter (isolate-then-attribute). Cross-seller access resolves to **404**.

---

## Endpoints

### GET /api/admin/settlements/accruals
List commission accrual lines for the caller's tenant (seller-scoped), most recent
first. Optional `sellerId` / `orderId` filters (a `sellerId` outside the caller's
seller scope → 404).

**Query params**: `sellerId` (string, optional), `orderId` (string UUID, optional),
`page` (int, default 0), `size` (int, default 20).

**Response 200**
```json
{
  "items": [
    {
      "accrualId": "string (UUID)",
      "orderId": "string (UUID)",
      "paymentId": "string (UUID)",
      "sellerId": "string",
      "type": "ACCRUAL | REVERSAL",
      "grossMinor": 30000,
      "rateBps": 1000,
      "commissionMinor": 3000,
      "sellerNetMinor": 27000,
      "occurredAt": "string (ISO 8601)"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1
}
```
- All money fields are **minor units** (`long`, KRW). `rateBps` is basis points
  (`1000 = 10%`). A `REVERSAL` row carries the **negative** of the original
  (`grossMinor`/`commissionMinor`/`sellerNetMinor < 0`).

---

### GET /api/admin/settlements/sellers/{sellerId}/balance
The accrued settlement balance for one seller in the caller's tenant. `sellerId`
outside the caller's seller scope (or another tenant) → **404**.

**Response 200**
```json
{
  "sellerId": "string",
  "accruedNetMinor": 27000,
  "platformCommissionMinor": 3000,
  "grossMinor": 30000,
  "accrualCount": 1,
  "asOf": "string (ISO 8601)"
}
```
- `accruedNetMinor = Σ sellerNetMinor` (ACCRUAL − REVERSAL); the seller's
  currently-settleable net. `platformCommissionMinor = Σ commissionMinor`. A fully
  refunded order nets these to the values before that order (reversal cancels it).

---

### GET /api/admin/settlements/commission-rates/{sellerId}
The effective commission rate for a seller. Returns the per-seller override if set,
else the platform default (with `source` indicating which).

**Response 200**
```json
{ "sellerId": "string", "rateBps": 1000, "source": "SELLER_OVERRIDE | PLATFORM_DEFAULT" }
```

---

### PUT /api/admin/settlements/commission-rates/{sellerId}
Set the per-seller commission rate (operator-plane). **Prospective** — applies to
accruals booked *after* this call; already-booked accruals are immutable (F3).

**Request Body**
```json
{ "rateBps": 1200 }
```
- `rateBps` is an integer in `[0, 10000]` (0% … 100%). Out of range → **422
  `COMMISSION_RATE_INVALID`**.

**Response 200** — the stored rate, same shape as the GET (`source = SELLER_OVERRIDE`).

---

## Period close + payout (period-close increment)

Operator-plane (`roles ∋ ECOMMERCE_OPERATOR`), tenant-scoped like every other endpoint. A
period / payout in another tenant resolves to **404** (M3). The two read-list
shapes below are also **seller-scoped** for payouts (the `X-Seller-Scope` filter, as
on the accrual reads). Mirrors the finance-platform ledger period endpoints.

### POST /api/admin/settlements/periods
Open a settlement period over a half-open `[from, to)` window (operator supplies the
window — grain-agnostic).

**Request Body**
```json
{ "from": "2026-06-01T00:00:00Z", "to": "2026-07-01T00:00:00Z" }
```
- `from` / `to` are ISO-8601 instants; the window is half-open `[from, to)`. Empty /
  inverted window (`from ≥ to`) → **422 `PERIOD_WINDOW_INVALID`**.
- A replay of the **exact same window** while an OPEN period already covers it →
  **409 `PERIOD_ALREADY_OPEN`**. Enforced by a partial unique index
  `(tenant_id, period_from, period_to) WHERE status = 'OPEN'`, so it holds for two
  simultaneous in-flight duplicates too. Only *exact duplicates* are refused:
  genuinely **overlapping** (non-identical) windows remain permitted by design, and
  re-opening the same window after the earlier one is CLOSED is permitted so a
  correction re-run is not blocked.
- **Residual double-payout risk of the overlap allowance (retained by design).** Because
  `close` folds every accrual in `[from, to)` into `seller_payout` rows without mutating
  the accrual, two genuinely-overlapping (non-identical) OPEN windows — once both closed —
  each fold the accruals in their intersection, paying those accruals twice. Only *exact*
  duplicates are guarded (the partial unique index); non-identical overlaps are **not**
  defended in code. The window is an operator-supplied natural key, so this boundary is the
  operator's to respect. This is a deliberate, documented acceptance — not an oversight.

**Response 201**
```json
{
  "periodId": "string (UUID)",
  "from": "2026-06-01T00:00:00Z",
  "to": "2026-07-01T00:00:00Z",
  "status": "OPEN",
  "closedAt": null,
  "sellerCount": null
}
```

---

### POST /api/admin/settlements/periods/{periodId}/close
Close the period (OPEN→CLOSED). Aggregates the EXISTING `commission_accrual` rows
whose `occurredAt ∈ [from, to)` into one `seller_payout` per seller (skipping sellers
whose `payableNetMinor ≤ 0`), creates those payouts **PENDING**, and emits
`settlement.period.closed.v1`. Accrual rows are never mutated (F3). A second close →
**409 `PERIOD_ALREADY_CLOSED`**. `periodId` not in the caller's tenant → **404**.

**Response 200**
```json
{
  "periodId": "string (UUID)",
  "from": "2026-06-01T00:00:00Z",
  "to": "2026-07-01T00:00:00Z",
  "status": "CLOSED",
  "closedAt": "2026-07-01T09:00:00Z",
  "sellerCount": 2,
  "payouts": [
    {
      "payoutId": "string (UUID)",
      "sellerId": "string",
      "payableNetMinor": 27000,
      "commissionMinor": 3000,
      "accrualCount": 1,
      "status": "PENDING",
      "payoutReference": null,
      "paidAt": null
    }
  ]
}
```

---

### GET /api/admin/settlements/periods
List settlement periods for the caller's tenant, most recent first.

**Query params**: `page` (int, default 0), `size` (int, default 20).

**Response 200**
```json
{
  "items": [
    { "periodId": "string (UUID)", "from": "...", "to": "...", "status": "OPEN | CLOSED", "closedAt": "... | null", "sellerCount": 2 }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1
}
```

---

### GET /api/admin/settlements/periods/{periodId}/payouts
List the `seller_payout` rows of a closed period (tenant-scoped + **seller-scoped**:
the `X-Seller-Scope` filter applies exactly as on the accrual reads — absent / `'*'`
→ all sellers, restricted → only the operator's `sellerId` rows, always inside the
tenant filter). `periodId` not in the caller's tenant → **404**.

**Response 200**
```json
{
  "items": [
    {
      "payoutId": "string (UUID)",
      "sellerId": "string",
      "payableNetMinor": 27000,
      "commissionMinor": 3000,
      "accrualCount": 1,
      "status": "PENDING | PAID | FAILED",
      "payoutReference": "string | null",
      "paidAt": "string (ISO 8601) | null"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1
}
```
- `payoutReference` is `null` while `PENDING`; set when `PAID`. In this increment the
  reference is **synthetic** (a clearly-marked simulated payout — no real
  disbursement occurred).

---

### POST /api/admin/settlements/periods/{periodId}/payouts/execute
Execute the simulated payouts for a closed period: run the `SellerPayoutPort`
(simulated adapter) over the period's **PENDING** payouts, flipping each
PENDING→PAID|FAILED. Idempotent on `(periodId, sellerId)` — already-PAID payouts are
left untouched (a re-run only touches still-PENDING rows). The period must be CLOSED
(executing an OPEN period → **409 `PERIOD_NOT_CLOSED`**). `periodId` not in the
caller's tenant → **404**.

**Response 200** — same shape as `GET …/payouts`, reflecting the post-execution
statuses (PAID rows now carry a `payoutReference` + `paidAt`).

> **Simulated — not a real disbursement.** This endpoint moves payout *status* via a
> simulated adapter that records a synthetic reference; **no money moves**. A real
> banking/PG adapter (`settlement.payout.mode=bank`) is a forward-declared seam.

---

## Error codes

| Code | HTTP | Meaning |
|---|---|---|
| `COMMISSION_RATE_INVALID` | 422 | `rateBps` outside `[0, 10000]` on `PUT /commission-rates/{sellerId}` |
| `PERIOD_WINDOW_INVALID` | 422 | empty / inverted window (`from ≥ to`) on `POST /periods` |
| `PERIOD_ALREADY_OPEN` | 409 | `POST /periods` for a window an OPEN period already covers exactly (same `tenant_id`, `from`, `to`) |
| `PERIOD_ALREADY_CLOSED` | 409 | `POST /periods/{id}/close` on an already-CLOSED period |
| `PERIOD_NOT_CLOSED` | 409 | `POST /periods/{id}/payouts/execute` on a still-OPEN period |
| `SETTLEMENT_NOT_FOUND` | 404 | seller / accrual / order / period / payout not found in the caller's tenant **or** outside the caller's seller scope (M3 — 404-over-403, no cross-tenant/cross-seller existence disclosure) |
| `TENANT_FORBIDDEN` | 403 | the tenant entitlement gate rejects (no `ecommerce` entitlement) |

---

## Out of scope (forward-declared — later increments)

- **REAL seller bank account / disbursement** surface — this increment's payout
  endpoints drive only a **simulated** adapter (synthetic reference, no money
  movement). A real banking/PG adapter (`settlement.payout.mode=bank`) is an
  unimplemented seam.
- A **consumer-facing** seller earnings / payout view (this increment is
  operator-plane only).
- Partial / proportional refund clawback (v1 reverses a refund as a full order
  reversal).
- Period **reopen** (the period state machine is OPEN→CLOSED).
