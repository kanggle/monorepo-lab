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
      "direction": "DEBIT", "valueDate": "2026-01-16" },
    { "externalRef": "FXTXN-003", "money": { "amount": "10000", "currency": "USD" },
      "baseAmount": { "amount": "132000", "currency": "KRW" },
      "direction": "CREDIT", "valueDate": "2026-01-20", "description": "USD settlement @ bank rate" }
  ] }
```
**(11th increment — multi-currency)** a line MAY carry an optional `baseAmount` — the bank's
**base-currency (KRW)** value for a foreign-currency line, at the bank's FX rate. When the line
matches an internal line on the transaction (foreign) leg, the matcher compares this `baseAmount`
to the internal line's **carrying base**; a difference records an `AMOUNT_MISMATCH`
(FX-difference) discrepancy on the **matched** line (the settlement is identified, the FX gap is
flagged for operator review — F8, never auto-adjusted). A KRW line, or a foreign line that omits
`baseAmount`, is unchanged (net-zero — no base-leg check).
`201`:
```json
{ "data": {
    "statementId": "...", "ledgerAccountCode": "CASH_CLEARING", "source": "BANK",
    "statementDate": "2026-01-31",
    "matchedCount": 1, "discrepancyCount": 1,
    "matches": [ { "statementLineExternalRef": "BANKTXN-001", "journalEntryId": "...",
                   "money": { "amount": "150000", "currency": "KRW" }, "crossCurrency": false } ],
    "discrepancies": [
      { "discrepancyId": "...", "type": "UNMATCHED_EXTERNAL", "externalRef": "BANKTXN-002",
        "expectedMinor": "99000", "actualMinor": "0", "currency": "KRW", "status": "OPEN" }
    ]
  }, "meta": { "timestamp": "..." } }
```
Discrepancies are recorded **OPEN** and are **never auto-closed (F8)**. `422
RECONCILIATION_ACCOUNT_INVALID` when `ledgerAccountCode` is not a reconcilable
clearing account. **(7th increment)** `422 RECONCILIATION_PERIOD_LOCKED` when
`statementDate` falls in a CLOSED accounting period — a closed month is closed to new
reconciliation; the guard runs **before** any persist/match/emit (a locked ingest
records nothing). Emits `finance.ledger.reconciliation.completed.v1` + one
`finance.ledger.reconciliation.discrepancy.detected.v1` per discrepancy
([`../events/finance-ledger-events.md`](../events/finance-ledger-events.md)).
**(11th increment)** an FX-difference discrepancy has `"type": "AMOUNT_MISMATCH"` and carries
**both** `externalRef` and `journalEntryId` (the matched pair), `expectedMinor` = the internal
carrying base, `actualMinor` = the bank's base value, `currency": "KRW"`; the corresponding
line still appears in `matches` (the transaction leg reconciled).

**(13th increment — configurable FX tolerance, TASK-FIN-BE-020)** the base-leg compare is
gated by the tenant's configured **`FxTolerance`** (see § 6). A base difference **within** the
tolerance band matches cleanly — the line still appears in `matches` (the settlement is
identified) but **no** `AMOUNT_MISMATCH` discrepancy is recorded; a difference **above** the band
records the `AMOUNT_MISMATCH` exactly as the 11th increment. The default (no configured row) is
`EXACT` `(0, 0)` → byte-identical to the 11th increment (any non-zero base diff → discrepancy).
Tolerance applies **only** to the base (KRW) leg; the transaction (foreign) leg stays an exact
`(amount, currency, direction)` match. Tolerance **never** auto-posts an FX correction or mutates
a journal entry (F8) — it suppresses only the base-leg *discrepancy*.

**(14th increment — cross-currency base-leg matching, TASK-FIN-BE-021)** when a **base-currency
(KRW)** external line finds **no same-currency candidate**, the matcher runs a strict **fallback**:
it pairs the line with the FIRST not-consumed **foreign** internal line (same direction; currency
≠ KRW) whose **carrying base** (`baseMoney`) is **within** the tenant's `FxTolerance` of the
external KRW amount. A bank often settles a foreign position **in KRW** while the ledger booked the
underlying as a foreign line carrying a KRW base; without this fallback the KRW external →
`UNMATCHED_EXTERNAL` and the foreign internal → `UNMATCHED_INTERNAL` (two spurious discrepancies for
one settlement). On a hit the line appears in `matches` with `"crossCurrency": true` and the match
`money` is the external **KRW** value; for a cross-currency match the carrying-base comparison **is**
the match key — **no** `AMOUNT_MISMATCH` is recorded (within tolerance → clean match; beyond
tolerance → not a candidate → the line stays `UNMATCHED_EXTERNAL` as before). **Precedence**:
same-currency matching runs **first** and is byte-unchanged; the cross-currency pass is a strict
fallback (net-zero for every existing reconciliation). The direction is **base-external →
foreign-internal only** — a foreign external line never enters the cross-currency pass. Under
`EXACT` (the default) the fallback requires **exact** carrying-base equality. Every `matches` entry
carries the additive boolean **`crossCurrency`** (`true` only for a cross-currency match; `false`
for every same-currency match). No new error code / status / event.

## 2. POST `/api/finance/ledger/reconciliation/discrepancies/{id}/resolve`

Operator resolves an OPEN discrepancy. Request:
```json
{ "resolutionType": "WRITTEN_OFF", "note": "bank fee, below threshold" }
```
`resolutionType` ∈ `{ MATCHED_MANUALLY, WRITTEN_OFF, ACCEPTED }`. `200`: the
discrepancy with `status: "RESOLVED"` + `resolution:{ resolutionType, note,
resolvedBy, resolvedAt }`. `404 RECONCILIATION_DISCREPANCY_NOT_FOUND` when unknown;
`409 RECONCILIATION_ALREADY_RESOLVED` when already RESOLVED;
**(6th increment)** `422 RECONCILIATION_PERIOD_LOCKED` when the discrepancy's
statement date falls in a CLOSED accounting period — the closed month's reconciliation
is frozen with the books; correct via the next (open) period (mirrors
`LEDGER_PERIOD_CLOSED`; net-zero when no covering closed period). There is **no
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

## 6. GET `/api/finance/ledger/reconciliation/fx-tolerance`

**(13th increment — TASK-FIN-BE-020)** The tenant's base-leg FX reconciliation tolerance.
Tenant-scoped. `200`:
```json
{ "data": { "toleranceBps": 100, "floorMinor": "200",
            "updatedBy": "operator-1", "updatedAt": "2026-02-01T10:00:00Z" },
  "meta": { "timestamp": "..." } }
```
When the tenant has **no** configured row, returns the **EXACT default** with the audit fields
omitted:
```json
{ "data": { "toleranceBps": 0, "floorMinor": "0" }, "meta": { "timestamp": "..." } }
```
`toleranceBps` is basis points (万분율) of the internal carrying-base magnitude; `floorMinor` is
an absolute floor in base/KRW minor units (string, F5). The allowed band is the **looser**
(larger) of `round_half_up(|carryingBase| × toleranceBps / 10000)` and `floorMinor`; a base
difference is within tolerance iff `|expected − actual| ≤ band` (inclusive). `EXACT` `(0, 0)` ⇒
band 0 ⇒ within iff `expected == actual`.

## 7. PUT `/api/finance/ledger/reconciliation/fx-tolerance`

**(13th increment — TASK-FIN-BE-020)** Operator upsert (last-write-wins) of the tenant's FX
tolerance. Tenant-scoped + audited. Request:
```json
{ "toleranceBps": 100, "floorMinor": 200 }
```
`200`: the persisted config (the § 6 `data` shape, audit fields populated — `updatedBy` = the
JWT subject else the tenant; `updatedAt` = the server clock). `400 VALIDATION_ERROR` when
`toleranceBps < 0` or `floorMinor < 0` (a DB CHECK is the structural backstop). **No new error
code / status / event** beyond `VALIDATION_ERROR`; the config is consulted at ingest time and
only ever suppresses the base-leg discrepancy (F8 — never auto-posts a correction).

---

## Error codes (this contract → `platform/error-handling.md`)

| Code | HTTP | Meaning |
|---|---|---|
| `RECONCILIATION_ACCOUNT_INVALID` | 422 | ingest target is not a reconcilable clearing account |
| `RECONCILIATION_STATEMENT_NOT_FOUND` | 404 | statement id unknown / not in tenant |
| `RECONCILIATION_DISCREPANCY_NOT_FOUND` | 404 | discrepancy id unknown / not in tenant |
| `RECONCILIATION_ALREADY_RESOLVED` | 409 | resolve attempted on an already-RESOLVED discrepancy |
| `RECONCILIATION_PERIOD_LOCKED` | 422 | **(6th/7th incr)** resolve (6th) OR ingest (7th) of a statement whose statement date is in a CLOSED accounting period (frozen with the books; both sides; mirrors `LEDGER_PERIOD_CLOSED`) |
| `VALIDATION_ERROR` | 400 | **(13th incr)** `PUT /fx-tolerance` with a negative `toleranceBps` / `floorMinor` (platform-standard code; not a new reconciliation code) |
| `TENANT_FORBIDDEN` | 403 | dual-accept gate rejects (both branches fail) |

`RECONCILIATION_DISCREPANCY` (pre-registered, fintech F8) names the **recorded
discrepancy entity** (its `type` + `OPEN/RESOLVED` status), not an HTTP error
response.

## Out of scope (forward-declared — later increments)

- Fuzzy / N:M / split matching; period **reopen**.
- Per-currency-pair / per-account FX tolerance granularity — v1 is **per-tenant**.
- An in-repo consumer of the reconciliation feed (this increment ships the producer
  + topics only).

> **Now in scope (implemented — no longer forward-declared):** **Foreign-external →
> KRW-internal** reverse cross-currency matching (the 19th increment, TASK-FIN-BE-027 —
> `ReconciliationMatcher#findReverseCrossCurrencyCandidate`, the strict mirror of the
> 14th-increment base-external → foreign-internal pass, TASK-FIN-BE-021) and **FIFO /
> lot-level** cost basis (TASK-FIN-BE-022–029 — per-tenant + per-account FX cost-flow
> config, FX position-lot acquisition, FIFO settlement consumption, open-lots read). See
> `ledger-api.md` § "Now in scope" for the consolidated ledger-side statement (both
> reconciliation directions + FIFO).
