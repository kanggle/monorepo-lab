# ledger-api — HTTP contract (ledger-service, first increment)

Read-only HTTP surface of `finance-platform/apps/ledger-service`. Authored by
TASK-FIN-BE-007 **before** implementation (contract-first,
`platform/service-types/rest-api.md`). Postings are **event-driven** (§
`finance-ledger-events.md`); the first increment exposes **no mutation
endpoints**. Architecture: [`../../services/ledger-service/architecture.md`](../../services/ledger-service/architecture.md).

All paths under `/api/finance/ledger/**`. All require a valid IAM RS256 JWT with
`tenant_id` accepted by the dual-accept gate (`finance` / `*` / `entitled_domains ∋
finance`); `finance.read` scope. Responses are tenant-scoped.

## Common shapes

**Money** is always `{ "amount": "<minor-units-integer-string>", "currency": "<ISO-4217>" }`
(string amount, never float — F5). **Success envelope**:
`{ "data": <payload>, "meta": { "timestamp": "<ISO-8601>" } }`. **Error envelope**:
`{ "code": "<ERROR_CODE>", "message": "...", "details": {…}?, "timestamp": "..." }`.
List endpoints are paginated (`?page=`/`?size=`, `meta.page/size/totalElements/totalPages`).

A `{ledgerAccountCode}` path segment is URL-encoded (the `CUSTOMER_WALLET:{accountId}`
form contains a colon).

---

## 1. GET `/api/finance/ledger/entries/{entryId}`

A single journal entry with its lines.

`200`:
```json
{ "data": {
    "entryId": "...", "postedAt": "<ISO-8601>",
    "source": { "sourceType": "TRANSACTION", "sourceTransactionId": "...", "sourceEventId": "..." },
    "reversalOfEntryId": null,
    "lines": [
      { "ledgerAccountCode": "CASH_CLEARING", "direction": "DEBIT",  "money": { "amount": "150000", "currency": "KRW" } },
      { "ledgerAccountCode": "CUSTOMER_WALLET:acc-1", "direction": "CREDIT", "money": { "amount": "150000", "currency": "KRW" } }
    ],
    "balanced": true
  }, "meta": { "timestamp": "..." } }
```
`404 JOURNAL_ENTRY_NOT_FOUND` when absent / not in tenant.

## 2. GET `/api/finance/ledger/accounts/{ledgerAccountCode}/entries`

Paginated journal lines posted to one ledger account (most-recent first).

`200`: `data` = array of `{ entryId, postedAt, direction, money, counterpartyLines? }`;
`meta` carries pagination. `404 LEDGER_ACCOUNT_NOT_FOUND` when the account code has no
ledger account (no posting ever referenced it).

## 3. GET `/api/finance/ledger/accounts/{ledgerAccountCode}/balance`

The account's running balance.

`200`:
```json
{ "data": {
    "ledgerAccountCode": "CUSTOMER_WALLET:acc-1", "type": "LIABILITY", "normalSide": "CREDIT",
    "debitTotal":  { "amount": "0",      "currency": "KRW" },
    "creditTotal": { "amount": "150000", "currency": "KRW" },
    "balance":     { "amount": "150000", "currency": "KRW" },
    "balanceSide": "CREDIT"
  }, "meta": { "timestamp": "..." } }
```
`balance = |debitTotal − creditTotal|`; `balanceSide` is the side the net falls on
(a liability with net credit is "positive" in its normal side). `404
LEDGER_ACCOUNT_NOT_FOUND` when absent.

## 4. GET `/api/finance/ledger/trial-balance`

The double-entry invariant, live: every account's debit/credit totals + the grand
totals, which MUST be equal (Σ debit == Σ credit across the ledger).

`200`:
```json
{ "data": {
    "accounts": [
      { "ledgerAccountCode": "CASH_CLEARING", "debitTotal": {…}, "creditTotal": {…} },
      { "ledgerAccountCode": "CUSTOMER_WALLET:acc-1", "debitTotal": {…}, "creditTotal": {…} }
    ],
    "grandDebitTotal":  { "amount": "150000", "currency": "KRW" },
    "grandCreditTotal": { "amount": "150000", "currency": "KRW" },
    "inBalance": true
  }, "meta": { "timestamp": "..." } }
```
`inBalance` is always `true` in a correct ledger (the posting path rejects unbalanced
entries — `LEDGER_ENTRY_UNBALANCED` — so the books can never go out of balance). The
first increment is single-currency; the trial balance groups by currency (one group
in the v1 KRW happy path).

---

## Error codes (this contract → `platform/error-handling.md`)

| Code | HTTP | Meaning |
|---|---|---|
| `JOURNAL_ENTRY_NOT_FOUND` | 404 | entry id unknown / not in tenant |
| `LEDGER_ACCOUNT_NOT_FOUND` | 404 | ledger account code unknown |
| `LEDGER_ENTRY_UNBALANCED` | 422 | debit ≠ credit (a posting-path guard; surfaced if a future manual-posting endpoint is added — pre-registered) |
| `CURRENCY_MISMATCH` | 422 | cross-currency lines in one entry (reused fintech code) |
| `TENANT_FORBIDDEN` | 403 | dual-accept gate rejects (both branches fail) |

`LEDGER_PERIOD_CLOSED` (422) is pre-registered for the deferred period-close increment.

## Out of scope (first increment — forward-declared)

- Mutation endpoints (manual journal posting / adjusting entries).
- Period-close endpoints (`POST /periods/{id}/close`, `GET /periods`).
- GL/AP export endpoints.
- Multi-currency trial-balance consolidation.
