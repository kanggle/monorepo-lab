# API Contract — account-service

Base path: `/api/finance` (rewritten by the gateway from `/api/v1/finance`
once `gateway-service` is introduced; v1 = direct JWT to the service).

Authoritative architecture: [`account-service/architecture.md`](../../services/account-service/architecture.md).
Domain rules: [`rules/domains/fintech.md`](../../../../../rules/domains/fintech.md) (F1–F8).

All endpoints:
- Require `Authorization: Bearer <token>` with `tenant_id ∈ {finance, *}`
  (RS256, IAM JWKS). Cross-tenant → 403 `TENANT_FORBIDDEN`.
- **Business-role authorization (TASK-MONO-794 · ADR-MONO-083 P7 — canonical:
  [`iam-integration.md` § 업무 역할](../../integration/iam-integration.md#업무-역할-adr-mono-083-p7--task-mono-794--정본))**:
  `GET` = `FINANCE_ACCOUNT_READ ∨ FINANCE_ACCOUNT_WRITE ∨ finance.read ∨ finance.write ∨ tenant_id='*'`;
  every mutation = `FINANCE_ACCOUNT_WRITE ∨ finance.write`; KYC upgrade and top-up additionally
  require the **`FINANCE_ACCOUNT_WRITE` role** (a scope-only workload token → 403). The generic
  `OPERATOR`/`ADMIN`/`SUPER_ADMIN` roles, `FINANCE_OPERATOR` (now the derived entry role) and the
  subscription-synthesised `ROLE_FINANCE_VIEWER` open nothing. Insufficient → 403 `PERMISSION_DENIED`.
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

## GET /api/finance/accounts/metrics

Overview performance metrics — the «실적» strip of the console's Finance 개요
(ADR-MONO-084 P7, TASK-MONO-812). Shape, windows and error semantics are the shared
[`platform/contracts/performance-metrics-contract.md`](../../../../../platform/contracts/performance-metrics-contract.md);
this section owns only **which keys exist and what each one counts** (ADR-MONO-084 D5).

**Request**: `GET /api/finance/accounts/metrics?period=week|month` (case-insensitive; required).
No other parameter.

- **Not an account list.** The response is the **tenant-wide total** of the caller's
  tenant — never one row per account, and no account id, owner ref, counterparty or hold id
  appears anywhere in it (§ GET /api/finance/accounts — deliberately absent still holds).
- **Path.** It sits under `/accounts` because that is the prefix the finance gateway routes
  to this service (`gateway-service` `Path=/api/finance/accounts/**`); the literal
  `/accounts/metrics` takes precedence over `/accounts/{id}`.
- **Authorization** — exactly the account read gate above (`FINANCE_ACCOUNT_READ ∨
  FINANCE_ACCOUNT_WRITE ∨ finance.read ∨ finance.write ∨ tenant_id='*'`). A ledger-only operator →
  403 `PERMISSION_DENIED`.
- **Tenancy** — the token's `tenant_id`, as every other read. A platform wildcard token
  (`tenant_id='*'`) passes the read gate above but is then refused here with **403
  `PERMISSION_DENIED`**. Tenant `*` owns no transactions, so it would read all `"0"`, and a
  metrics «0» reads as «nothing happened» (TASK-MONO-817 ·
  `platform/contracts/performance-metrics-contract.md` § Wildcard tenant). Only this endpoint
  refuses; the other account reads keep answering the wildcard as before.
- **Source.** account-service `transactions` — one row per business transaction. The
  ledger's journal is **not** used: it is double-entry, and summing its lines would count each
  transaction twice (debit + credit).

**200** — the shared shape, **bare** (no `{data, meta}` envelope):

```json
{
  "period": "week", "timezone": "Asia/Seoul",
  "current":  { "from": "2026-10-19T00:00:00+09:00", "to": "2026-10-23T14:05:00+09:00" },
  "previous": { "from": "2026-10-12T00:00:00+09:00", "to": "2026-10-16T14:05:00+09:00" },
  "metrics": [
    { "key": "topupCount",         "unit": "count", "currency": null,  "current": "2",      "previous": "1" },
    { "key": "topupAmount:KRW",    "unit": "money", "currency": "KRW", "current": "500000", "previous": "1000000" },
    { "key": "topupAmount:USD",    "unit": "money", "currency": "USD", "current": "2550",   "previous": "0" },
    { "key": "transferCount",      "unit": "count", "currency": null,  "current": "1",      "previous": "0" },
    { "key": "transferAmount:KRW", "unit": "money", "currency": "KRW", "current": "50000",  "previous": "0" },
    { "key": "transferAmount:USD", "unit": "money", "currency": "USD", "current": "0",      "previous": "0" },
    { "key": "captureCount",       "unit": "count", "currency": null,  "current": "1",      "previous": "0" },
    { "key": "captureAmount:KRW",  "unit": "money", "currency": "KRW", "current": "12000",  "previous": "0" },
    { "key": "captureAmount:USD",  "unit": "money", "currency": "USD", "current": "0",      "previous": "0" }
  ],
  "series": [ { "date": "2026-10-19", "values": { "topupCount": "2", "topupAmount:KRW": "500000", "…": "…" } } ]
}
```

**Keys (normative — one line each).** Common to all: a row counts only when its **current
status ∈ {`SETTLED`, `COMPLETED`}**, and it is attributed to its **`settledAt`** (KST window
and KST day per the shared contract). Amounts are integer **minor units** of the row's own
currency, exactly as stored (F5 — no conversion, no rounding).

| Key | Counts | Excludes |
|---|---|---|
| `topupCount` | `TOPUP` transactions settled in the window (all currencies). | every other type; `FAILED` and any non-settled status |
| `topupAmount:<CUR>` | Sum of those `TOPUP` amounts in `<CUR>`. | other currencies (never added, never converted — OD-3) |
| `transferCount` | `TRANSFER` transactions settled in the window — **one per transfer**: a transfer is one row (source account + counterparty), never counted as a debit plus a credit. | as above |
| `transferAmount:<CUR>` | Sum of those `TRANSFER` amounts in `<CUR>`, each transfer once. | as above |
| `captureCount` | `CAPTURE` transactions settled in the window — a hold counts **when, and only when, it is captured**, at the capture's `settledAt` (a hold placed last week and captured this week is this week's). | `HOLD` rows (a reservation — written `COMPLETED` the moment the hold is placed, so excluded **by type**), `RELEASE` rows and expiries (the reservation going back — it never moved) |
| `captureAmount:<CUR>` | Sum of the **captured** amounts in `<CUR>` — a partial capture contributes only the captured part (the auto-released remainder is not a movement). | as above |

- **Display order**: per type (`topup` → `transfer` → `capture`), its count, then its amounts
  in currency-code order. **Currency set**: every currency a counted row carries in either
  window, the same set for every type (a type with no activity in that currency is `"0"`);
  when no counted row exists in either window the set is `KRW`, so the money cells keep their
  place.
- **Not reported**: `HOLD` / `RELEASE` (above), and `WITHDRAW` / `REVERSAL` — v1 has **no
  write path** for either (no HTTP mapping, no application call site — § Error code table's
  closing note). When one lands, its key and — for `REVERSAL` — whether it subtracts from
  the original's type is that change's contract decision; this endpoint does not guess it.
- **Can a past day change?** No, in v1: a counted row is written once, already `COMPLETED`
  with its `settledAt`, and is immutable (F3). The one v1-unreachable exception is a
  `COMPLETED → REVERSED` flip, which would drop the original from its day.

**Errors**: 400 `VALIDATION_ERROR` (`period` missing or unknown — not this service's 422
for a bare `IllegalArgumentException`), 401 (no / invalid token), 403 `PERMISSION_DENIED` /
`TENANT_FORBIDDEN`.

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
