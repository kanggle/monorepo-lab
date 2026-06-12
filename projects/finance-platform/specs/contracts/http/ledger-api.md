# ledger-api — HTTP contract (ledger-service)

HTTP surface of `finance-platform/apps/ledger-service`. Authored by
TASK-FIN-BE-007 **before** implementation (contract-first,
`platform/service-types/rest-api.md`); extended by TASK-FIN-BE-008 (period close),
TASK-FIN-BE-011 (manual posting), TASK-FIN-BE-015 (FX revaluation), and
TASK-FIN-BE-016 (FX settlement). Journal
**postings** are **event-driven** by default
(§ `finance-ledger-events.md`); the **2nd increment** adds **period** mutation
endpoints (open/close — § Accounting periods), the **5th increment** adds the
first journal **mutation** endpoint — operator **manual posting**
(`POST /entries` — § Manual journal posting), the **9th increment** adds operator
**FX revaluation** (`POST /revaluations` — § FX revaluation), and the **10th increment** adds
operator **FX settlement** (`POST /settlements` — § FX settlement). Architecture: [`../../services/ledger-service/architecture.md`](../../services/ledger-service/architecture.md).

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
      { "ledgerAccountCode": "CASH_CLEARING", "direction": "DEBIT",  "money": { "amount": "150000", "currency": "KRW" },
        "exchangeRate": "1", "baseAmount": { "amount": "150000", "currency": "KRW" } },
      { "ledgerAccountCode": "CUSTOMER_WALLET:acc-1", "direction": "CREDIT", "money": { "amount": "150000", "currency": "KRW" },
        "exchangeRate": "1", "baseAmount": { "amount": "150000", "currency": "KRW" } }
    ],
    "balanced": true
  }, "meta": { "timestamp": "..." } }
```
**(8th increment)** Each line carries its transaction `money` plus an `exchangeRate`
and a `baseAmount` (the line's value in the fixed base currency, **KRW**). **`baseAmount`
is authoritative for the balance**; `exchangeRate` is the exact-decimal provenance factor
**in minor units** (`exchangeRate = baseAmount.amount / money.amount`). A single-currency
KRW entry has `exchangeRate: "1"` and `baseAmount == money` (existing entries —
backfilled). The double-entry identity holds in the base currency
(`Σ baseDebit == Σ baseCredit`); `balanced` reflects that. A multi-currency example (an
operator FX adjusting entry, DR USD clearing / CR KRW wallet, where `$100.00` USD =
`10000` minor units converts to `135000` KRW — `1 USD = 1350 KRW`, i.e. minor-to-minor
`13.5`):
```json
{ "lines": [
    { "ledgerAccountCode": "CASH_CLEARING", "direction": "DEBIT",
      "money": { "amount": "10000", "currency": "USD" },
      "exchangeRate": "13.5", "baseAmount": { "amount": "135000", "currency": "KRW" } },
    { "ledgerAccountCode": "CUSTOMER_WALLET:acc-1", "direction": "CREDIT",
      "money": { "amount": "135000", "currency": "KRW" },
      "exchangeRate": "1", "baseAmount": { "amount": "135000", "currency": "KRW" } } ] }
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
      { "ledgerAccountCode": "CASH_CLEARING", "debitTotal": {…}, "creditTotal": {…},
        "baseDebitTotal": {…}, "baseCreditTotal": {…} },
      { "ledgerAccountCode": "CUSTOMER_WALLET:acc-1", "debitTotal": {…}, "creditTotal": {…},
        "baseDebitTotal": {…}, "baseCreditTotal": {…} }
    ],
    "grandDebitTotal":  { "amount": "150000", "currency": "KRW" },
    "grandCreditTotal": { "amount": "150000", "currency": "KRW" },
    "grandBaseDebitTotal":  { "amount": "150000", "currency": "KRW" },
    "grandBaseCreditTotal": { "amount": "150000", "currency": "KRW" },
    "inBalance": true
  }, "meta": { "timestamp": "..." } }
```
`inBalance` is always `true` in a correct ledger (the posting path rejects unbalanced
entries — `LEDGER_ENTRY_UNBALANCED` — so the books can never go out of balance). The
per-account `debitTotal`/`creditTotal` are grouped **by currency** (the original
transaction amounts). **(8th increment)** each account also carries its **base-currency**
(KRW) totals (`baseDebitTotal`/`baseCreditTotal`), and the grand totals add the
**base-currency consolidated** `grandBaseDebitTotal == grandBaseCreditTotal` — the
invariant that holds across currencies (`inBalance` reflects the base-currency
consolidation). In the all-KRW happy path the original and base totals coincide.

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

> **Posting guard** — once a period is CLOSED, the posting path rejects any journal
> entry whose `postedAt` falls in the closed window with `422 LEDGER_PERIOD_CLOSED`.
> On the **event-driven** path the consumer routes such an event to the DLT; on the
> **(5th increment)** manual posting path it surfaces **synchronously** as a 422. With
> no covering closed period — including when no period is defined — postings proceed
> unchanged (net-zero).

---

## Manual journal posting (5th increment — TASK-FIN-BE-011)

The first journal **mutation** endpoint — an operator posts an adjusting entry
directly (the auto-journal consumer remains the source for transaction-driven
entries). Requires a valid JWT accepted by the dual-accept tenant gate
(`.authenticated()` + tenant gate — no separate scope-authority axis; the operator
caller arrives via the platform-console client) **and** a client `Idempotency-Key`
header. The entry funnels through the same guarded write path as the auto-journal
consumer (balance self-validation, closed-period guard, audit, `entry.posted` outbox)
— see architecture.md § Manual Journal Posting.

### 9. POST `/api/finance/ledger/entries`

Headers: `Idempotency-Key: <client-key>` (required). Request:
```json
{ "postedAt": "2026-06-12T00:00:00Z",
  "reference": "ADJ-2026-06-CORR-014",
  "memo": "correct mis-posted settlement clearing",
  "lines": [
    { "ledgerAccountCode": "CASH_CLEARING",          "direction": "DEBIT",  "money": { "amount": "50000", "currency": "KRW" } },
    { "ledgerAccountCode": "CUSTOMER_WALLET:acc-1",  "direction": "CREDIT", "money": { "amount": "50000", "currency": "KRW" } }
  ] }
```
`postedAt` is optional (defaults to the server clock; a back-dated effective instant
for an adjusting entry). `reference` / `memo` are optional operator narrative
(recorded as the audit reason + the entry's `source.sourceTransactionId`). `lines`
MUST be ≥2 and balanced.

**(8th increment — multi-currency)** a line MAY carry a foreign `money.currency` together
with an explicit `baseAmount` (the line's value in the base currency, **KRW**); a
base-currency (KRW) line omits `baseAmount` (it defaults to `money`, `exchangeRate = 1`).
The balance requirement is **on the base amounts**: `Σ baseDebit == Σ baseCredit` (an
all-KRW entry is unchanged — base == original). Example of a multi-currency line:
`{ "ledgerAccountCode": "CASH_CLEARING", "direction": "DEBIT", "money": {"amount":"10000","currency":"USD"}, "baseAmount": {"amount":"135000","currency":"KRW"} }`.

`201` — the posted entry, in the § 1 entry shape, with
`source.sourceType = "MANUAL"`:
```json
{ "data": {
    "entryId": "...", "postedAt": "2026-06-12T00:00:00Z",
    "source": { "sourceType": "MANUAL", "sourceTransactionId": "ADJ-2026-06-CORR-014", "sourceEventId": "manual:<client-key>" },
    "reversalOfEntryId": null,
    "lines": [ … ], "balanced": true
  }, "meta": { "timestamp": "..." } }
```

- **Idempotent replay** — re-issuing the SAME `Idempotency-Key` returns `200` with
  the **original** entry (no second post; `processed_events` dedupe, F1).
- `400 IDEMPOTENCY_KEY_REQUIRED` when the `Idempotency-Key` header is absent.
- `422 LEDGER_ENTRY_UNBALANCED` when the base amounts do not balance (`Σ baseDebit ≠ Σ
  baseCredit`, or fewer than 2 lines). **(8th increment)** a multi-currency entry whose
  per-line base amounts do not net to zero is rejected here.
- `422 CURRENCY_MISMATCH` when lines carry more than one currency.
- `404 LEDGER_ACCOUNT_NOT_FOUND` when a line references a ledger account that does
  not exist (the manual path does **not** lazily create accounts — an operator
  adjusts the existing chart).
- `422 LEDGER_PERIOD_CLOSED` when `postedAt` falls in a CLOSED accounting period
  (the closed-period guard, surfaced synchronously here).
- `403 TENANT_FORBIDDEN` when the dual-accept gate rejects.

---

## FX revaluation (9th increment — TASK-FIN-BE-015)

An operator revalues a **foreign-currency position** at a new closing (spot) rate. The
8th increment books multi-currency entries at the rate supplied **at posting time**; over
time the spot rate moves, so a foreign position's **base carrying value** (Σ of its lines'
historical `baseAmount`) drifts from its current market value. Revaluation **trues that
carrying value up to spot**, booking the difference as an **unrealized FX gain or loss**.

The revaluation is itself a **balanced base-currency (KRW) adjusting entry**:
- a **base-carrying adjustment** line on the foreign account — `money.amount = "0"` in the
  foreign `currency` (the foreign **quantity is unchanged**) with a non-zero `baseAmount`
  (the carrying delta in KRW); and
- a contra **`FX_GAIN`** (income) or **`FX_LOSS`** (expense) line for the same KRW amount.

So the foreign account's base carrying value moves to `foreignBalance × closingRate` while
its foreign-currency balance is untouched. The entry funnels through the **same guarded
write path** as every posting (balance self-validation in the base currency, closed-period
guard, audit, `entry.posted` outbox) — see architecture.md § FX gain/loss revaluation.

### 10. POST `/api/finance/ledger/revaluations`

Headers: `Idempotency-Key: <client-key>` (required, ≤ 50 chars). Request:
```json
{ "ledgerAccountCode": "CASH_CLEARING",
  "currency": "USD",
  "closingRate": "13.5",
  "postedAt": "2026-06-30T23:59:59Z",
  "reference": "FX-REVAL-2026-06-USD",
  "memo": "month-end USD revaluation" }
```
- `ledgerAccountCode` + `currency` identify the **foreign position** to revalue (the
  account's lines in that currency). `currency` MUST NOT be the base currency (KRW).
- `closingRate` is the **base-currency minor units per one foreign-currency minor unit**
  (a string decimal — never a float, F5). For USD (minor scale 2) → KRW (minor scale 0) at
  a spot of ₩1,350 per $1: `$1 = 100 USD-minor = 1350 KRW-minor`, so `closingRate = "13.5"`.
  MUST be strictly positive.
- `postedAt` optional (defaults to the server clock; a month-end effective instant). The
  closed-period guard applies — a revaluation `postedAt` in a CLOSED period is rejected.
- `reference` / `memo` optional operator narrative (audit reason + `source.sourceTransactionId`).

`201` — the posted revaluation entry, in the § 1 entry shape, with
`source.sourceType = "REVALUATION"` and `revalued: true`:
```json
{ "data": {
    "revalued": true,
    "deltaBaseMinor": "5000",
    "outcome": "FX_GAIN",
    "entry": {
      "entryId": "...", "postedAt": "2026-06-30T23:59:59Z",
      "source": { "sourceType": "REVALUATION", "sourceTransactionId": "FX-REVAL-2026-06-USD", "sourceEventId": "reval:<client-key>" },
      "reversalOfEntryId": null,
      "lines": [
        { "ledgerAccountCode": "CASH_CLEARING", "direction": "DEBIT",  "money": {"amount":"0","currency":"USD"}, "exchangeRate": "13.5", "baseAmount": {"amount":"5000","currency":"KRW"} },
        { "ledgerAccountCode": "FX_GAIN",       "direction": "CREDIT", "money": {"amount":"5000","currency":"KRW"}, "exchangeRate": "1", "baseAmount": {"amount":"5000","currency":"KRW"} }
      ],
      "balanced": true
    }
  }, "meta": { "timestamp": "..." } }
```

`200` — **no adjustment booked** (`revalued: false`): the position is already at spot
(`delta == 0`), or there is **no position** in that `currency` on the account, OR an
**idempotent replay** (the same `Idempotency-Key` already booked a revaluation — returns
the original entry). Body: `{ "data": { "revalued": false, "reason": "AT_SPOT" | "NO_POSITION" | "REPLAY", "entry": <original-entry-or-null> }, "meta": {…} }`.

- `400 IDEMPOTENCY_KEY_REQUIRED` when the `Idempotency-Key` header is absent / blank / > 50 chars.
- `422 REVALUATION_RATE_INVALID` when `closingRate` is not strictly positive.
- `422 CURRENCY_MISMATCH` when `currency` is unsupported or is the base currency (KRW —
  the base currency cannot be revalued against itself).
- `404 LEDGER_ACCOUNT_NOT_FOUND` when `ledgerAccountCode` does not exist.
- `422 LEDGER_PERIOD_CLOSED` when `postedAt` falls in a CLOSED accounting period.
- `403 TENANT_FORBIDDEN` when the dual-accept gate rejects.

The `FX_GAIN` (income) and `FX_LOSS` (expense) GL accounts are seeded in the chart of
accounts (§ architecture.md § Chart of Accounts). A subsequent revaluation at a newer rate
trues the carrying up again from its **already-revalued** carrying — no double-booking
(the prior delta is part of the foreign position's base carrying).

---

## FX settlement (10th increment — TASK-FIN-BE-016)

An operator **settles** a foreign-currency position at a settlement (spot) rate — converting
the holding to the base currency and **removing** the position — and the difference between
the base proceeds and the position's carrying value is recognised as a **realized** FX gain or
loss. Where revaluation (§ FX revaluation) marks an OPEN position to market, settlement closes
it. The entry funnels through the same guarded write path — see architecture.md § FX settlement.

### 11. POST `/api/finance/ledger/settlements`

Headers: `Idempotency-Key: <client-key>` (required, ≤ 50 chars). Request:
```json
{ "ledgerAccountCode": "CASH_CLEARING",
  "currency": "USD",
  "settlementRate": "13.7",
  "proceedsAccountCode": "CASH_KRW",
  "postedAt": "2026-06-30T23:59:59Z",
  "reference": "FX-SETTLE-2026-06-USD",
  "memo": "liquidate USD holdings" }
```
- `ledgerAccountCode` + `currency` identify the **foreign position** to settle (the account's
  lines in that currency); the **whole** position is settled (partial is forward-declared).
  `currency` MUST NOT be the base currency (KRW).
- `settlementRate` is the **base-currency minor units per one foreign-currency minor unit**
  (string decimal, never a float — F5); the base proceeds = `round(foreignBalance ×
  settlementRate)`. MUST be strictly positive.
- `proceedsAccountCode` is the **base-currency account** that receives (asset) or pays
  (liability) the proceeds; it MUST already exist (no lazy mint).
- `postedAt` optional (defaults to the server clock); the closed-period guard applies.
- `reference` / `memo` optional operator narrative (audit reason + `source.sourceTransactionId`).

`201` — the posted settlement entry, in the § 1 entry shape, with
`source.sourceType = "SETTLEMENT"` and `settled: true`:
```json
{ "data": {
    "settled": true,
    "realizedBaseMinor": "7000",
    "proceedsBaseMinor": "137000",
    "outcome": "FX_GAIN",
    "entry": {
      "entryId": "...", "postedAt": "2026-06-30T23:59:59Z",
      "source": { "sourceType": "SETTLEMENT", "sourceTransactionId": "FX-SETTLE-2026-06-USD", "sourceEventId": "settle:<client-key>" },
      "reversalOfEntryId": null,
      "lines": [
        { "ledgerAccountCode": "CASH_KRW",       "direction": "DEBIT",  "money": {"amount":"137000","currency":"KRW"}, "exchangeRate": "1",    "baseAmount": {"amount":"137000","currency":"KRW"} },
        { "ledgerAccountCode": "CASH_CLEARING",  "direction": "CREDIT", "money": {"amount":"10000","currency":"USD"},  "exchangeRate": "13",   "baseAmount": {"amount":"130000","currency":"KRW"} },
        { "ledgerAccountCode": "FX_GAIN",        "direction": "CREDIT", "money": {"amount":"7000","currency":"KRW"},   "exchangeRate": "1",    "baseAmount": {"amount":"7000","currency":"KRW"} }
      ],
      "balanced": true
    }
  }, "meta": { "timestamp": "..." } }
```
The `CASH_CLEARING` USD position is **removed** (its foreign + base sums go to zero); the
proceeds sit in `CASH_KRW`; `realizedBaseMinor = proceedsBase − carryingBase`.

`200` — **no settlement booked** (`settled: false`): there is **no position** in that
`currency` on the account (`foreignBalance == 0`), OR an **idempotent replay** (the same
`Idempotency-Key` already settled — returns the original entry). Body:
`{ "data": { "settled": false, "reason": "NO_POSITION" | "REPLAY", "entry": <original-entry-or-null> }, "meta": {…} }`.

- `400 IDEMPOTENCY_KEY_REQUIRED` when the `Idempotency-Key` header is absent / blank / > 50 chars.
- `422 SETTLEMENT_RATE_INVALID` when `settlementRate` is not strictly positive.
- `422 CURRENCY_MISMATCH` when `currency` is unsupported or is the base currency (KRW).
- `404 LEDGER_ACCOUNT_NOT_FOUND` when `ledgerAccountCode` or `proceedsAccountCode` does not exist.
- `422 LEDGER_PERIOD_CLOSED` when `postedAt` falls in a CLOSED accounting period.
- `403 TENANT_FORBIDDEN` when the dual-accept gate rejects.

Polarity is automatic for **asset** (debit-balance) and **liability** (credit-balance) foreign
positions — the line directions derive from `sign(foreignBalance)` (removal + proceeds) and
`sign(realized)` (FX gain/loss). `realized` is measured against the position's **carrying** value
(which already embeds any prior revaluation), so a revalue-then-settle realizes only the
incremental movement — no double-count.

---

## Error codes (this contract → `platform/error-handling.md`)

| Code | HTTP | Meaning |
|---|---|---|
| `JOURNAL_ENTRY_NOT_FOUND` | 404 | entry id unknown / not in tenant |
| `LEDGER_ACCOUNT_NOT_FOUND` | 404 | ledger account code unknown — read; **(5th incr)** also a manual-posting guard (a referenced account must already exist — no lazy mint) |
| `LEDGER_ENTRY_UNBALANCED` | 422 | debit ≠ credit (or <2 lines) — the posting-path guard; **(5th incr)** surfaced synchronously on `POST /entries` (manual posting) |
| `CURRENCY_MISMATCH` | 422 | cross-currency lines in one entry (reused fintech code); **(5th incr)** also synchronous on manual posting |
| `TENANT_FORBIDDEN` | 403 | dual-accept gate rejects (both branches fail) |
| `IDEMPOTENCY_KEY_REQUIRED` | 400 | **(5th incr)** `Idempotency-Key` header absent on `POST /entries` (Platform-Common transactional code; handler guard) |
| `LEDGER_PERIOD_CLOSED` | 422 | **(2nd incr)** journal posting into a CLOSED accounting period (consumer-path → DLT; **(5th incr)** synchronous 422 on the manual `POST /entries` path; net-zero when no closed period covers `postedAt`) |
| `ACCOUNTING_PERIOD_NOT_FOUND` | 404 | **(2nd incr)** period id unknown / not in tenant |
| `ACCOUNTING_PERIOD_OVERLAP` | 422 | **(2nd incr)** opened window overlaps an existing period for the tenant |
| `ACCOUNTING_PERIOD_ALREADY_CLOSED` | 409 | **(2nd incr)** close attempted on an already-CLOSED period |
| `ACCOUNTING_PERIOD_INVALID_WINDOW` | 422 | **(2nd incr)** `from ≥ to` |
| `REVALUATION_RATE_INVALID` | 422 | **(9th incr)** FX revaluation `closingRate` not strictly positive on `POST /revaluations` (`RevaluationRateInvalidException`) |
| `SETTLEMENT_RATE_INVALID` | 422 | **(10th incr)** FX settlement `settlementRate` not strictly positive on `POST /settlements` (`SettlementRateInvalidException`) |

## Out of scope (forward-declared — later increments)

- Manual-posting body-hash idempotency **conflict** (`IDEMPOTENCY_KEY_CONFLICT` 409 on
  same-key/different-body — the 5th increment is replay-safe on the key alone); a
  maker/checker **approval** workflow for manual entries; bulk multi-entry posting.
- Period **reopen**; a "period must have ended (`to ≤ now`)" close policy.
- GL/AP export endpoints (`finance.ledger.entry.posted.v1` — the increment that
  introduces the outbox, and with it the deferred `finance.ledger.period.closed.v1`).
- **Partial / weighted-average settlement** (the 10th increment settles a **whole**
  `(account, currency)` position; settling a *portion* with a proportional / FIFO carrying basis
  + a residual position is forward-declared) + a **proceeds-amount input** (the 10th derives
  proceeds from a `settlementRate`; supplying the *actual* base received is forward-declared)
  + a **bulk / all-positions** revaluation and a **period-close auto-hook** (the 9th/10th
  increments act on one `(account, currency)` per call, operator-triggered) + a **live FX rate
  feed** (rates are caller-supplied) + a **configurable base currency** (fixed KRW in v1)
  + **multi-currency reconciliation** (cross-currency clearing-account matching).
