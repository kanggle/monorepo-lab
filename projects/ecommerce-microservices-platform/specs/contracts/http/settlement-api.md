# HTTP Contract: settlement-service

## Overview
Published HTTP API for settlement-service (ADR-MONO-030 Step 4 facet b — marketplace
seller settlement / commission). All endpoints are accessible through
gateway-service only. **Operator-plane only** (`/api/admin/**`) — there is no
consumer-facing settlement surface. All endpoints require an authenticated
operator (Bearer token, `account_type = OPERATOR`).

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

## Error codes

| Code | HTTP | Meaning |
|---|---|---|
| `COMMISSION_RATE_INVALID` | 422 | `rateBps` outside `[0, 10000]` on `PUT /commission-rates/{sellerId}` |
| `SETTLEMENT_NOT_FOUND` | 404 | seller / accrual / order not found in the caller's tenant **or** outside the caller's seller scope (M3 — 404-over-403, no cross-tenant/cross-seller existence disclosure) |
| `TENANT_FORBIDDEN` | 403 | the tenant entitlement gate rejects (no `ecommerce` entitlement) |

---

## Out of scope (forward-declared — later increments)

- **Settlement-period close / payout** endpoints (`POST /periods`, `POST
  /periods/{id}/close`, payout list) — v1 is accrual + read only.
- **Seller bank account / disbursement** surface.
- A **consumer-facing** seller earnings view (v1 is operator-plane only).
- Partial / proportional refund clawback (v1 reverses a refund as a full order
  reversal).
