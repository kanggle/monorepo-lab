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
  "details": <object?>, "timestamp": "<ISO-8601>" }`. Codes per
  [`platform/error-handling.md`](../../../../../platform/error-handling.md)
  fintech section.
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

**200**: `{ "data": [ { "transactionId", "type", "status", "money",
"counterpartyAccountId?", "reversalOfTransactionId?", "createdAt",
"settledAt?" } ], "meta": { "page", "size", "totalElements", "timestamp" } }`

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
| `PERMISSION_DENIED` | 403 | operator-only op by non-operator |
| `TENANT_FORBIDDEN` | 403 | tenant_id ∉ {finance,*} |
| `IDEMPOTENCY_STORE_UNAVAILABLE` | 503 | Redis + DB idempotency store both down |
| `CONCURRENT_MODIFICATION` | 409 | optimistic-lock conflict |

All registered in `platform/error-handling.md` (this PR). Reversal: there is
**no** "edit transaction" endpoint — corrections are operator-initiated
reversal transactions (v2 admin-service surface; v1 = domain capability +
audit, no public mutate-settled path).
