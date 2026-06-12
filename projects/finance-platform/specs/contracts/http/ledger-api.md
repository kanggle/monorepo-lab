# ledger-api — HTTP contract (ledger-service)

HTTP surface of `finance-platform/apps/ledger-service`. Authored by
TASK-FIN-BE-007 **before** implementation (contract-first,
`platform/service-types/rest-api.md`); extended by TASK-FIN-BE-008 (period close).
Journal **postings** are **event-driven** (§ `finance-ledger-events.md`) — there is
no posting mutation endpoint; the **2nd increment** adds **period** mutation
endpoints (open/close — § Accounting periods). Architecture: [`../../services/ledger-service/architecture.md`](../../services/ledger-service/architecture.md).

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

## Accounting periods (2nd increment — TASK-FIN-BE-008)

Period mutations require a valid JWT accepted by the dual-accept tenant gate
(`.authenticated()` + tenant gate — no separate scope-authority axis; the operator
caller arrives via the platform-console client). A period window is a **half-open**
`[from, to)` interval of ISO-8601 instants.

### 5. POST `/api/finance/ledger/periods`

Open an accounting period. Request:
```json
{ "from": "2026-01-01T00:00:00Z", "to": "2026-02-01T00:00:00Z" }
```
`201`:
```json
{ "data": {
    "periodId": "...", "status": "OPEN",
    "from": "2026-01-01T00:00:00Z", "to": "2026-02-01T00:00:00Z",
    "closedAt": null, "closedBy": null, "entryCount": null
  }, "meta": { "timestamp": "..." } }
```
`422 ACCOUNTING_PERIOD_INVALID_WINDOW` when `from ≥ to`;
`422 ACCOUNTING_PERIOD_OVERLAP` when the window overlaps an existing period for the tenant.

### 6. POST `/api/finance/ledger/periods/{periodId}/close`

Close an OPEN period: capture the trial-balance snapshot (entries with `postedAt <
to`) and transition OPEN→CLOSED. `200`:
```json
{ "data": {
    "periodId": "...", "status": "CLOSED",
    "from": "2026-01-01T00:00:00Z", "to": "2026-02-01T00:00:00Z",
    "closedAt": "<ISO-8601>", "closedBy": "<actor>", "entryCount": 12,
    "snapshot": {
      "accounts": [
        { "ledgerAccountCode": "CASH_CLEARING", "debitTotal": {…}, "creditTotal": {…} },
        { "ledgerAccountCode": "CUSTOMER_WALLET:acc-1", "debitTotal": {…}, "creditTotal": {…} }
      ],
      "grandDebitTotal": { "amount": "…", "currency": "KRW" },
      "grandCreditTotal": { "amount": "…", "currency": "KRW" },
      "inBalance": true
    }
  }, "meta": { "timestamp": "..." } }
```
`404 ACCOUNTING_PERIOD_NOT_FOUND` when the id is unknown;
`409 ACCOUNTING_PERIOD_ALREADY_CLOSED` when the period is already CLOSED. The
snapshot is immutable (insert-only); `inBalance` is always `true` (the books are
balanced by the posting guard).

### 7. GET `/api/finance/ledger/periods`

List accounting periods for the tenant (most-recent window first). `200`: `data`
= array of the period object (without `snapshot`); `meta` carries pagination.

### 8. GET `/api/finance/ledger/periods/{periodId}`

Period detail including its balance `snapshot` (present only for a CLOSED period;
`null` while OPEN). `404 ACCOUNTING_PERIOD_NOT_FOUND` when absent / not in tenant.

> **Posting guard** — once a period is CLOSED, the **event-driven** posting path
> rejects any journal entry whose `postedAt` falls in the closed window with `422
> LEDGER_PERIOD_CLOSED` (the consumer routes such an event to the DLT). With no
> covering closed period — including when no period is defined — postings proceed
> unchanged (net-zero). There is no REST posting endpoint, so `LEDGER_PERIOD_CLOSED`
> surfaces on the consumer path, not as a synchronous HTTP response in this increment.

---

## Error codes (this contract → `platform/error-handling.md`)

| Code | HTTP | Meaning |
|---|---|---|
| `JOURNAL_ENTRY_NOT_FOUND` | 404 | entry id unknown / not in tenant |
| `LEDGER_ACCOUNT_NOT_FOUND` | 404 | ledger account code unknown |
| `LEDGER_ENTRY_UNBALANCED` | 422 | debit ≠ credit (a posting-path guard; surfaced if a future manual-posting endpoint is added — pre-registered) |
| `CURRENCY_MISMATCH` | 422 | cross-currency lines in one entry (reused fintech code) |
| `TENANT_FORBIDDEN` | 403 | dual-accept gate rejects (both branches fail) |
| `LEDGER_PERIOD_CLOSED` | 422 | **(2nd incr)** journal posting into a CLOSED accounting period (consumer-path → DLT; net-zero when no closed period covers `postedAt`) |
| `ACCOUNTING_PERIOD_NOT_FOUND` | 404 | **(2nd incr)** period id unknown / not in tenant |
| `ACCOUNTING_PERIOD_OVERLAP` | 422 | **(2nd incr)** opened window overlaps an existing period for the tenant |
| `ACCOUNTING_PERIOD_ALREADY_CLOSED` | 409 | **(2nd incr)** close attempted on an already-CLOSED period |
| `ACCOUNTING_PERIOD_INVALID_WINDOW` | 422 | **(2nd incr)** `from ≥ to` |

## Out of scope (forward-declared — later increments)

- Manual journal posting / adjusting-entry mutation endpoints.
- Period **reopen**; a "period must have ended (`to ≤ now`)" close policy.
- GL/AP export endpoints (`finance.ledger.entry.posted.v1` — the increment that
  introduces the outbox, and with it the deferred `finance.ledger.period.closed.v1`).
- Multi-currency trial-balance / snapshot consolidation.
