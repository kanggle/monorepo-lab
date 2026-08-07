# API Contract — account-service

Base path: `/api/finance` (rewritten by the gateway from `/api/v1/finance`
once `gateway-service` is introduced; v1 = direct JWT to the service).

Authoritative architecture: [`account-service/architecture.md`](../../services/account-service/architecture.md).
Domain rules: [`rules/domains/fintech.md`](../../../../../rules/domains/fintech.md) (F1–F8).

All endpoints:
- Require `Authorization: Bearer <token>` with `tenant_id ∈ {finance, *}`
  (RS256, IAM JWKS). Cross-tenant → 403 `TENANT_FORBIDDEN`.
- **Mutating** endpoints require `Idempotency-Key: <client-generated>`
  (fintech F1, transactional T1). Missing → 400 `IDEMPOTENCY_KEY_REQUIRED`.
  Same key + identical payload → first stored response replayed (no fund
  re-movement). Same key + different payload → 409 `IDEMPOTENCY_KEY_CONFLICT`.
- **Money** is always an object `{ "amount": "<integer-minor-units>",
  "currency": "<ISO-4217>" }` — `amount` is a **string-encoded integer in
  minor units** (KRW scale 0 → `"1000"` = ₩1,000; USD scale 2 → `"1000"` =
  $10.00). Never a float/decimal JSON number (F5). Responses use the same
  shape.
- Success envelope: `{ "data": <payload>, "meta": { "timestamp": "<ISO-8601>" } }`.
- Error envelope: `{ "code": "<ERROR_CODE>", "message": "<human>",
  "timestamp": "<ISO-8601>" }` — exactly the three-field base envelope of
  [`platform/error-handling.md`](../../../../../platform/error-handling.md)
  § Error Response Format, serialised from the shared
  `libs/java-web.ErrorResponse` (ADR-MONO-058 § D2, TASK-FIN-BE-066). Codes per
  the same document's fintech section.
  A previously documented optional `details` object was **removed** from this
  line by TASK-FIN-BE-066: no handler arm in this service ever populated it, so
  no response has ever carried the key and its removal is not observable to a
  client. The platform envelope still *permits* a service to extend the base
  with structured `details`; if a future error code needs one, that code's row
  below must document its keys and the envelope regains the field then.
- No webhook / public-callback surface in v1 (finance has no external caller;
  contrast scm). Only `/actuator/{health,info}` are unauthenticated.

---

## POST /api/finance/accounts

Open a new account. Initial status `PENDING_KYC`.

**Headers**: `Authorization` (req), `Idempotency-Key` (req), `Content-Type: application/json`

**Request**:
```json
{ "ownerRef": "cust-9b1d4a8c", "currency": "KRW", "kycLevel": "NONE" }
```
- `ownerRef` — required, ≤ 64 chars (opaque external owner id; stored, masked in logs F7)
- `currency` — required, ISO-4217 (3 chars), must be a supported currency
- `kycLevel` — optional, enum `NONE|BASIC|FULL` (default `NONE`)

**201**: `{ "data": { "accountId", "status": "PENDING_KYC", "currency",
"kycLevel", "createdAt" }, "meta": {...} }`

**Errors**: 400 `VALIDATION_ERROR`, 400 `IDEMPOTENCY_KEY_REQUIRED`,
409 `IDEMPOTENCY_KEY_CONFLICT`, 422 `CURRENCY_MISMATCH` (unsupported currency),
403 `TENANT_FORBIDDEN`.

## GET /api/finance/accounts — deliberately absent (v1)

There is **no account list / search endpoint**, and TASK-FIN-BE-068 decided not to
add one. Recorded here rather than left silent, with what it costs:

| | measured 2026-08-07 |
|---|---|
| console screens affected | **1** (`/finance/accounts` — the operator types an accountId; the screen already documents this as "honest finance constraint: v1") |
| accounts an operator would need ids for, in the demo stack | **2** (both printed by `infra/demo/seed/seed-finance.sh`) |
| repository methods that could back a list today | **0** — `AccountRepository` exposes no `findAll` / `Page` method at all |

The blocker is not wiring. `owner_ref` is **encrypted at rest** (F7 —
`AccountRepositoryImpl` encrypts on write and decrypts on read, so the column holds
ciphertext only), so a list that supports the one filter an operator would actually
want — "find this customer's account" — needs a blind index or searchable-encryption
scheme. That is an architecture decision (ADR territory), not an omission, and it is
independent of funding. A list with no owner search would be a tenant-wide dump of
opaque ids, which is not what the screen needs.

## GET /api/finance/accounts/{id}

**200**: `{ "data": { "accountId", "status", "currency", "kycLevel",
"balances": [ { "currency", "ledger": "<minor>", "available": "<minor>",
"held": "<minor>" } ], "createdAt", "updatedAt" }, "meta": {...} }`

**Errors**: 404 `ACCOUNT_NOT_FOUND`, 403 `TENANT_FORBIDDEN`.

## POST /api/finance/accounts/{id}/kyc/upgrade

Operator raises KYC level; may transition `PENDING_KYC → ACTIVE`.

**Request**: `{ "toLevel": "BASIC|FULL", "reason": "<≤256>" }`

**200**: `{ "data": { "accountId", "kycLevel", "status" }, ... }`

**Errors**: 404 `ACCOUNT_NOT_FOUND`, 409 `ACCOUNT_STATUS_TRANSITION_INVALID`,
403 `PERMISSION_DENIED` (non-operator), 400 `IDEMPOTENCY_KEY_REQUIRED`.

## GET /api/finance/accounts/{id}/balances

**200**: `{ "data": [ { "currency", "ledger", "available", "held" } ], ... }`
(minor-units strings). **Errors**: 404 `ACCOUNT_NOT_FOUND`.

## POST /api/finance/accounts/{id}/topups

**Operator-only** internal funding credit — the v1 path by which money enters an
account. Credits `ledger` (and therefore `available`) by `money.amount`, emits a
`TOPUP` transaction, and is auto-journalled by ledger-service as
DR `CASH_CLEARING` / CR `CUSTOMER_WALLET:{id}`.

**Provenance (v1).** The funding source is **internal**, not an external bank —
architecture.md § Balance Model (`topup`/`withdraw` … "v1 = internal/stub funding
source") and § Responsibilities MUST-NOT ("v1 has no real external adapter"). This
endpoint therefore does **not** represent a customer deposit a bank has confirmed;
it represents an operator crediting funds the platform received out-of-band. v2
replaces the *source* (a real bank/PG adapter behind an infrastructure port) while
keeping this balance/ledger effect — the credit semantics documented here are not
the deferred part.

**Why operator-only** — a holder-initiated call would let any authenticated
account-holder mint their own balance. `PERMISSION_DENIED` is enforced in the
application layer (the same gate as `/kyc/upgrade`), not only by the
`SecurityConfig` write authorities, so a `finance.write`-scoped non-operator token
is rejected too.

**Headers**: `Authorization` (req), `Idempotency-Key` (req), `Content-Type: application/json`

**Request**:
```json
{ "money": { "amount": "150000", "currency": "KRW" }, "reason": "operator funding" }
```
- `money` — required (F5 shape); `currency` must equal the account currency
- `reason` — optional, ≤ 256

**200**: `{ "data": { "transactionId", "accountId", "type": "TOPUP", "money",
"status": "COMPLETED" }, ... }`

**Errors**: 404 `ACCOUNT_NOT_FOUND`, 403 `PERMISSION_DENIED` (non-operator),
409 `ACCOUNT_NOT_ACTIVE` / `ACCOUNT_FROZEN`, 422 `CURRENCY_MISMATCH`,
422 `AMOUNT_INVALID`, 403 `KYC_REQUIRED` / `KYC_LEVEL_INSUFFICIENT`,
422 `TRANSACTION_LIMIT_EXCEEDED`, 422 `SANCTION_HIT`,
400 `IDEMPOTENCY_KEY_REQUIRED`, 409 `IDEMPOTENCY_KEY_CONFLICT`.

> **Idempotency here is stored-response replay, not a balance check.** A repeat of
> the same key + payload returns the *first* stored 2xx and credits nothing more.
> There is no second-order "already topped up" state a client can inspect — so a
> caller (or a seed) MUST NOT infer "this call moved money" from the status code;
> read the balance back, or count `transactions`.

## POST /api/finance/accounts/{id}/holds

Place a hold (reserve funds; `available ≥ amount`).

**Request**:
```json
{ "money": { "amount": "150000", "currency": "KRW" },
  "expiresInSeconds": 3600, "reason": "checkout-auth" }
```
- `money` — required (F5 shape); `currency` must equal the account currency
- `expiresInSeconds` — optional, 1..604800 (default 3600); expiry → auto-release
- `reason` — optional, ≤ 256

**201**: `{ "data": { "holdId", "accountId", "money", "status": "ACTIVE",
"expiresAt", "transactionId" }, ... }`

**Errors**: 422 `INSUFFICIENT_AVAILABLE_BALANCE`, 422 `CURRENCY_MISMATCH`,
422 `AMOUNT_INVALID`, 409 `ACCOUNT_NOT_ACTIVE` / `ACCOUNT_FROZEN`,
403 `KYC_REQUIRED` / `KYC_LEVEL_INSUFFICIENT`, 422 `TRANSACTION_LIMIT_EXCEEDED`,
422 `SANCTION_HIT`, 400 `IDEMPOTENCY_KEY_REQUIRED`, 409 `IDEMPOTENCY_KEY_CONFLICT`.

## POST /api/finance/accounts/{id}/holds/{holdId}/capture

Capture (full or partial; remainder auto-released).

**Request**: `{ "money": { "amount": "120000", "currency": "KRW" } }`
(`amount ≤ hold amount`; same currency)

**200**: `{ "data": { "holdId", "captured": {money}, "released": {money},
"status": "CAPTURED", "transactionId" }, ... }`

**Errors**: 404 `HOLD_NOT_FOUND`, 409 `HOLD_ALREADY_SETTLED`,
422 `AMOUNT_INVALID` (> hold), 422 `CURRENCY_MISMATCH`,
400 `IDEMPOTENCY_KEY_REQUIRED`, 409 `IDEMPOTENCY_KEY_CONFLICT`.

## POST /api/finance/accounts/{id}/holds/{holdId}/release

**200**: `{ "data": { "holdId", "released": {money}, "status": "RELEASED",
"transactionId" }, ... }`

**Errors**: 404 `HOLD_NOT_FOUND`, 409 `HOLD_ALREADY_SETTLED`,
400 `IDEMPOTENCY_KEY_REQUIRED`.

## POST /api/finance/accounts/{id}/transfers

Atomic transfer to another finance account (hold-source + capture +
credit-target in one Tx).

**Request**:
```json
{ "toAccountId": "acct-...", "money": { "amount": "50000", "currency": "KRW" },
  "reason": "p2p" }
```

**200**: `{ "data": { "transactionId", "fromAccountId", "toAccountId",
"money", "status": "COMPLETED" }, ... }`

**Errors**: 404 `ACCOUNT_NOT_FOUND` (either side), 422
`INSUFFICIENT_AVAILABLE_BALANCE`, 422 `CURRENCY_MISMATCH`,
409 `ACCOUNT_NOT_ACTIVE`/`ACCOUNT_FROZEN`, 403 `KYC_*`,
422 `TRANSACTION_LIMIT_EXCEEDED` / `SANCTION_HIT`,
400 `IDEMPOTENCY_KEY_REQUIRED`, 409 `IDEMPOTENCY_KEY_CONFLICT`,
409 `CONCURRENT_MODIFICATION`.

## GET /api/finance/accounts/{id}/transactions

Paginated (`?page=&size=&type=&status=`).

**200**: `{ "data": { "content": [ { "transactionId", "type", "status", "money",
"counterpartyAccountId?", "reversalOfTransactionId?", "createdAt",
"settledAt?" } ], "page", "size", "totalElements", "totalPages" },
"meta": { "page", "size", "totalElements", "timestamp" } }` — `data` is a
`com.example.common.page.PageResult` page object (not a bare array); `page` /
`size` / `totalElements` are carried both inside `data` and (redundantly) in
`meta` for parity with the other v1 list endpoints' `meta`-only convention.

**Errors**: 404 `ACCOUNT_NOT_FOUND`.

---

## Error code → HTTP status (fintech)

| Code | HTTP | Trigger |
|---|---|---|
| `VALIDATION_ERROR` | 400 | bean-validation failure |
| `IDEMPOTENCY_KEY_REQUIRED` | 400 | mutating call without header |
| `IDEMPOTENCY_KEY_CONFLICT` | 409 | same key, different payload |
| `ACCOUNT_NOT_FOUND` | 404 | unknown account |
| `ACCOUNT_NOT_ACTIVE` | 409 | fund op on non-ACTIVE |
| `ACCOUNT_FROZEN` | 409 | fund op on FROZEN |
| `ACCOUNT_STATUS_TRANSITION_INVALID` | 409 | illegal account transition |
| `INSUFFICIENT_AVAILABLE_BALANCE` | 422 | available < amount |
| `HOLD_NOT_FOUND` | 404 | unknown hold |
| `HOLD_ALREADY_SETTLED` | 409 | re-capture/release settled hold |
| `TRANSACTION_NOT_FOUND` | 404 | unknown txn |
| `TRANSACTION_STATUS_TRANSITION_INVALID` | 409 | illegal txn transition |
| `TRANSACTION_ALREADY_SETTLED` | 409 | mutate settled txn (reversal-only) |
| `CURRENCY_MISMATCH` | 422 | mixed-currency op / unsupported currency |
| `AMOUNT_INVALID` | 422 | ≤0 / scale / minor-unit violation |
| `KYC_REQUIRED` | 403 | KYC incomplete |
| `KYC_LEVEL_INSUFFICIENT` | 403 | level below required |
| `AML_SCREENING_REQUIRED` | 422 | screening unresolved |
| `SANCTION_HIT` | 422 | sanction/watchlist match (txn FAILED + operator queue) |
| `TRANSACTION_LIMIT_EXCEEDED` | 422 | KYC/policy limit exceeded |
| `PERMISSION_DENIED` | 403 | operator-only op by non-operator; also insufficient OAuth2 scope — a write without `finance.write`, or a read without `finance.read`/`finance.write` (and no operator role), rejected by `SecurityConfig` (TASK-FIN-BE-046) |
| `TENANT_FORBIDDEN` | 403 | tenant_id ∉ {finance,*} |
| `IDEMPOTENCY_STORE_UNAVAILABLE` | 503 | Redis + DB idempotency store both down |
| `CONCURRENT_MODIFICATION` | 409 | optimistic-lock conflict |
| `ILLEGAL_STATE` | 422 | aggregate invariant violated at the controller boundary — the unclassified `IllegalStateException` fallback (Platform-Common). Prefer a domain code above where the failure is a known one |

All registered in `platform/error-handling.md` (this PR). Reversal: there is
**no** "edit transaction" endpoint — corrections are operator-initiated
reversal transactions (v2 admin-service surface; v1 = domain capability +
audit, no public mutate-settled path).
