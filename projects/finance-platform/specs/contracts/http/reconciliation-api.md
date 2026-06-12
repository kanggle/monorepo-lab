# reconciliation-api — HTTP contract (ledger-service, 4th increment)

Reconciliation HTTP surface of `finance-platform/apps/ledger-service` (TASK-FIN-BE-010).
The ledger reconciles its **clearing accounts** (`CASH_CLEARING`, `SETTLEMENT_SUSPENSE`)
against an ingested **external statement** (bank / PG settlement lines): 1:1 match by
(amount, currency, direction); anything unmatched → a `ReconciliationDiscrepancy` in
an **operator review queue** — **never auto-closed (fintech F8)**. Architecture:
[`../../services/ledger-service/architecture.md`](../../services/ledger-service/architecture.md)
§ Reconciliation.

All paths under `/api/finance/ledger/reconciliation/**`. All require a valid IAM
RS256 JWT accepted by the dual-accept tenant gate (`finance` / `*` /
`entitled_domains ∋ finance`). Mutations (ingest / resolve) are `.authenticated()` +
the tenant gate (no separate scope-authority axis; the operator/integration caller
arrives via the platform-console / internal-services client). Responses are
tenant-scoped. **Money** is `{amount:"<minor-units-string>", currency}` (F5 — never a
float). Success envelope `{ data, meta:{timestamp} }`; error envelope
`{ code, message, details?, timestamp }`.

---

## 1. POST `/api/finance/ledger/reconciliation/statements`

Ingest an external statement for one clearing account and run matching. Request:
```json
{ "ledgerAccountCode": "CASH_CLEARING", "source": "BANK",
  "statementDate": "2026-01-31",
  "lines": [
    { "externalRef": "BANKTXN-001", "money": { "amount": "150000", "currency": "KRW" },
      "direction": "DEBIT", "valueDate": "2026-01-15", "description": "deposit" },
    { "externalRef": "BANKTXN-002", "money": { "amount": "99000", "currency": "KRW" },
      "direction": "DEBIT", "valueDate": "2026-01-16" }
  ] }
```
`201`:
```json
{ "data": {
    "statementId": "...", "ledgerAccountCode": "CASH_CLEARING", "source": "BANK",
    "statementDate": "2026-01-31",
    "matchedCount": 1, "discrepancyCount": 1,
    "matches": [ { "statementLineExternalRef": "BANKTXN-001", "journalEntryId": "...",
                   "money": { "amount": "150000", "currency": "KRW" } } ],
    "discrepancies": [
      { "discrepancyId": "...", "type": "UNMATCHED_EXTERNAL", "externalRef": "BANKTXN-002",
        "expectedMinor": "99000", "actualMinor": "0", "currency": "KRW", "status": "OPEN" }
    ]
  }, "meta": { "timestamp": "..." } }
```
Discrepancies are recorded **OPEN** and are **never auto-closed (F8)**. `422
RECONCILIATION_ACCOUNT_INVALID` when `ledgerAccountCode` is not a reconcilable
clearing account. Emits `finance.ledger.reconciliation.completed.v1` + one
`finance.ledger.reconciliation.discrepancy.detected.v1` per discrepancy
([`../events/finance-ledger-events.md`](../events/finance-ledger-events.md)).

## 2. POST `/api/finance/ledger/reconciliation/discrepancies/{id}/resolve`

Operator resolves an OPEN discrepancy. Request:
```json
{ "resolutionType": "WRITTEN_OFF", "note": "bank fee, below threshold" }
```
`resolutionType` ∈ `{ MATCHED_MANUALLY, WRITTEN_OFF, ACCEPTED }`. `200`: the
discrepancy with `status: "RESOLVED"` + `resolution:{ resolutionType, note,
resolvedBy, resolvedAt }`. `404 RECONCILIATION_DISCREPANCY_NOT_FOUND` when unknown;
`409 RECONCILIATION_ALREADY_RESOLVED` when already RESOLVED. There is **no
auto-resolve** path.

## 3. GET `/api/finance/ledger/reconciliation/statements/{id}`

Statement detail + its matches + discrepancies. `200`: the § 1 `data` shape.
`404 RECONCILIATION_STATEMENT_NOT_FOUND` when unknown / not in tenant.

## 4. GET `/api/finance/ledger/reconciliation/discrepancies`

The discrepancy review queue. `?status=OPEN|RESOLVED` (default all), paginated
(`?page=`/`?size=`). `200`: `data` = array of discrepancy objects; `meta` pagination.

## 5. GET `/api/finance/ledger/reconciliation/discrepancies/{id}`

Discrepancy detail (incl. `resolution` when RESOLVED).
`404 RECONCILIATION_DISCREPANCY_NOT_FOUND` when unknown.

---

## Error codes (this contract → `platform/error-handling.md`)

| Code | HTTP | Meaning |
|---|---|---|
| `RECONCILIATION_ACCOUNT_INVALID` | 422 | ingest target is not a reconcilable clearing account |
| `RECONCILIATION_STATEMENT_NOT_FOUND` | 404 | statement id unknown / not in tenant |
| `RECONCILIATION_DISCREPANCY_NOT_FOUND` | 404 | discrepancy id unknown / not in tenant |
| `RECONCILIATION_ALREADY_RESOLVED` | 409 | resolve attempted on an already-RESOLVED discrepancy |
| `TENANT_FORBIDDEN` | 403 | dual-accept gate rejects (both branches fail) |

`RECONCILIATION_DISCREPANCY` (pre-registered, fintech F8) names the **recorded
discrepancy entity** (its `type` + `OPEN/RESOLVED` status), not an HTTP error
response. `RECONCILIATION_PERIOD_LOCKED` (422) stays pre-registered for the deferred
period-lock increment.

## Out of scope (forward-declared — later increments)

- Reconciliation **period lock** (`RECONCILIATION_PERIOD_LOCKED` — a discrepancy
  whose statement date is in a CLOSED accounting period is immutable).
- Fuzzy / N:M / split matching; multi-currency statements.
- An in-repo consumer of the reconciliation feed (this increment ships the producer
  + topics only).
