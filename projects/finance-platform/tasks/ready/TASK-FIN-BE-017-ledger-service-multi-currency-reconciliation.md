# TASK-FIN-BE-017 — ledger-service multi-currency reconciliation (11th increment: FX/base-leg difference on a matched foreign statement line → AMOUNT_MISMATCH discrepancy)

**Status:** ready

**Type:** TASK-FIN-BE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Opus (fintech domain mechanic — the pure matcher gains a base-leg FX check producing a new discrepancy class on a *matched* line; threads a carrying base into the matcher inputs; additive nullable migration; exhaustive net-zero for the existing reconciliation paths)

---

## Goal

Deliver the **multi-currency reconciliation** increment forward-declared by
`specs/services/ledger-service/architecture.md` § Increment Scope. After the 8th increment a
clearing account holds **multi-currency** lines (each a transaction `Money` + a carrying base
in KRW). A **foreign-currency external statement** already reconciles on the **transaction
(foreign) leg** — the FIN-BE-010 matcher matches by `(amount, currency, direction)`, which is
currency-aware (a USD external line pairs with a USD internal line by exact USD amount;
**net-zero** — no change needed). This increment adds the **base (FX) leg**: a bank often reports
the **base-currency (KRW) value** it actually credited, at **its** FX rate; when that differs
from the internal line's **carrying base** (booked at the ledger's rate) there is a realized
**FX difference** — the same settlement, valued differently. The matcher records it as an
**`AMOUNT_MISMATCH`** discrepancy for **operator review** (F8 — recorded, never auto-adjusted;
the operator books the FX correction via a manual entry / settlement). This is the **first
activation** of the long-declared `AMOUNT_MISMATCH` `DiscrepancyType`.

**Key design** (architecture.md § Reconciliation § Multi-currency reconciliation): **reuse
`AMOUNT_MISMATCH`** (already in the `DiscrepancyType` enum, the events `type` enum, and the V4
`ck_recon_discrepancy_type` CHECK allow-list) → **no CHECK migration, no new code/status/event**.
The only migration is **additive nullable columns**. The transaction-leg match is **still
recorded** — a matched line may also carry an FX-difference discrepancy (the settlement is
identified, the gap is flagged).

This is a **deliberate first slice** — **same-foreign-currency** matching + an **exact** base
comparison. **Cross-currency base-leg matching** (a base-currency [KRW] external statement
matched against foreign internal lines by their carrying base), a **configurable FX tolerance**,
and **fuzzy / N:M matching** are forward-declared (named below).

**Net-zero is the central constraint**: a KRW-only statement, or a foreign statement whose lines
omit `baseAmount`, reconciles byte-identically to FIN-BE-010 (the base-leg check never fires).
The existing UNMATCHED_* classification, F8 no-auto-close, the period lock, and the
transaction-leg matching are all unchanged.

## Scope

**Spec PR (this task → ready):** authored already —
`specs/services/ledger-service/architecture.md` (§ Increment Scope 11th IN + forward-decl; new
§ Reconciliation § Multi-currency reconciliation; Matching-engine note; Layer Structure
[InternalLine.baseMoney, ExternalStatementLine.baseAmount, DiscrepancyType, matcher FX-leg, V6];
Failure Mode 37; Testing; provenance) + `specs/contracts/http/reconciliation-api.md` (ingest
request line optional `baseAmount`; AMOUNT_MISMATCH discrepancy shape) +
`specs/contracts/events/finance-ledger-events.md` (AMOUNT_MISMATCH first-activation note) +
`platform/error-handling.md` (11th increment; no new code).

**Impl PR — IN (multi-currency reconciliation increment):**
- **Domain — `InternalLine`**: add `Money baseMoney` (the line's carrying base, KRW). The record
  becomes `(journalEntryId, ledgerAccountCode, direction, money, baseMoney)`.
- **Domain — `ExternalStatementLine`**: add an **optional** base value — fields
  `Long baseAmountMinor` (nullable) + `Currency baseCurrency` (nullable) + a `Money baseAmount()`
  accessor returning `null` when absent. Extend the `of(...)` factory + the
  `ExternalStatement.RawLine` record + `ExternalStatement.open(...)` to thread the optional base
  amount. The base value, when present, MUST be the base/reporting currency (KRW) — validate
  (`CurrencyMismatchException` if not), mirroring `JournalLine.of(money, baseAmount)`.
- **Persistence (Flyway `V6__add_reconciliation_fx.sql`)**: `ALTER TABLE
  reconciliation_statement_line ADD COLUMN base_amount_minor BIGINT NULL, ADD COLUMN
  base_currency VARCHAR(3) NULL;` (additive + nullable — **no backfill, no CHECK change**;
  existing rows stay NULL → net-zero). `ExternalStatementLine` JPA entity gains the two nullable
  columns.
- **Domain — `ReconciliationMatcher`**: when an external line matches an internal line on the
  transaction leg (as today — `markMatched()` + a `ReconciliationMatch`), **additionally** run
  the base-leg check: **iff** `ext.currency() != LedgerReportingCurrency.BASE` AND
  `ext.baseAmount() != null` AND `ext.baseAmount().minorUnits() != internal.baseMoney().minorUnits()`,
  add an OPEN `AMOUNT_MISMATCH` discrepancy via
  `ReconciliationDiscrepancy.open(null, tenantId, statementId, ledgerAccountCode,
  DiscrepancyType.AMOUNT_MISMATCH, ext.externalRef(), internal.journalEntryId(),
  internal.baseMoney().minorUnits() /*expected*/, ext.baseAmount().minorUnits() /*actual*/,
  LedgerReportingCurrency.BASE /*currency=KRW*/, at)`. The match is **still** recorded. A KRW
  line / a foreign line without an external base amount → **no** base-leg discrepancy. The
  UNMATCHED_EXTERNAL / UNMATCHED_INTERNAL paths are unchanged.
- **Application — `IngestStatementUseCase`**: thread the optional per-line `baseAmount` from the
  command/DTO into `ExternalStatement.RawLine`. No change to the period-lock guard, the outbox
  emission, or the F8 invariant.
- **Infrastructure — `ReconciliationRepositoryImpl.toInternalLine` / `findUnmatchedInternalLines`**:
  build `InternalLine` with `line.baseMoney()` (the `JournalLine` already carries it). The
  `ExternalStatementLine` adapter persists/reads the two nullable base columns.
- **Presentation — ingest request DTO**: the statement line DTO gains an optional `baseAmount`
  (`MoneyRequest`); the mapper builds the `RawLine` with it (or `null`). The statement /
  discrepancy response DTOs already render the discrepancy `type` + `expected`/`actual`/`currency`
  — an `AMOUNT_MISMATCH` renders the same shape (carrying both `externalRef` + `journalEntryId`).
- **Tests** (unit + slice + Integration): see Acceptance Criteria.

**Impl PR — OUT (still forward-declared):** **cross-currency base-leg matching** (a KRW external
statement matched against foreign internal lines by their carrying base — the 11th matches
same-foreign-currency lines + the base-leg FX check); a **configurable FX tolerance** (the 11th is
an exact base comparison); fuzzy / N:M / split matching; period **reopen**.

## Acceptance Criteria

- **AC-1 (FX-difference on a matched foreign line)** — `ReconciliationMatcherTest`: a foreign
  (USD) external line matching an internal line on `(amount, currency, direction)` whose external
  `baseAmount` **differs** from the internal `baseMoney` is **MATCHED** (a `ReconciliationMatch`
  exists, the line flips to MATCHED) **and** produces an OPEN `AMOUNT_MISMATCH` discrepancy with
  `expectedMinor` = internal carrying base, `actualMinor` = external base, `currency` = KRW,
  carrying both `externalRef` + `journalEntryId`.
- **AC-2 (net-zero — no base-leg noise)** — a matched foreign line whose external `baseAmount`
  **equals** the internal `baseMoney` → MATCHED, **no** discrepancy; a **KRW** line → MATCHED, no
  base-leg discrepancy (base == amount); a foreign line **without** an external `baseAmount` →
  MATCHED, no base-leg discrepancy. The existing UNMATCHED_EXTERNAL / UNMATCHED_INTERNAL paths and
  determinism are unchanged (the FIN-BE-010 `ReconciliationMatcherTest` cases stay green).
- **AC-3 (carrying base threaded)** — `InternalLine` carries `baseMoney`; the infrastructure
  `findUnmatchedInternalLines` populates it from `JournalLine.baseMoney()` (a JPA slice asserts a
  USD internal line's `baseMoney` is its carrying KRW, not its USD amount).
- **AC-4 (additive nullable migration, no CHECK change)** — `V6__add_reconciliation_fx.sql` adds
  only the two nullable columns; the `ck_recon_discrepancy_type` CHECK is **unchanged**
  (`AMOUNT_MISMATCH` already allowed). Existing statement-line rows stay NULL. No new error code,
  status, event type, or topic.
- **AC-5 (F8 + immutability preserved)** — the `AMOUNT_MISMATCH` discrepancy is recorded **OPEN**
  and never auto-resolved/adjusted; it is resolvable only via `ResolveDiscrepancyUseCase`
  (OPEN→RESOLVED). The matcher posts no balancing entry. The period-lock guard is unchanged.
- **AC-6** — `:ledger-service:check` BUILD SUCCESSFUL; **CI "Integration (finance-platform,
  Testcontainers)" GREEN**: V6 runs; post a multi-currency entry establishing a USD line on
  `CASH_CLEARING` (carrying 130 000 KRW @ 13.0) → ingest a USD external statement line matching the
  USD amount + direction with `baseAmount` 132 000 KRW → 201: the line is `MATCHED` (a match
  exists) **and** an OPEN `AMOUNT_MISMATCH` discrepancy (expected 130 000 / actual 132 000 / KRW)
  is recorded, and **`finance.ledger.reconciliation.discrepancy.detected.v1` with
  `type=AMOUNT_MISMATCH`** is consumed off Kafka; a USD line whose `baseAmount` == carrying →
  MATCHED, no discrepancy; a **KRW-only** statement → byte-identical (net-zero); the discrepancy
  `resolve`s → RESOLVED; cross-tenant → 403. The existing reconciliation ITs (UNMATCHED_*,
  period-lock) stay green. No deploy-wiring change. NB: `ledger_outbox` queries use `event_type`
  (no `topic` column) — a known finance-IT trap; consume via Kafka.

## Related Specs

- `projects/finance-platform/specs/services/ledger-service/architecture.md` (this PR — governing; § Reconciliation § Multi-currency reconciliation)
- `rules/domains/fintech.md` § F8 (reconciliation no auto-close — governing), § F5 (money = minor units, no float), § F2 (double-entry), § F6 (audit)
- `projects/finance-platform/specs/services/account-service/architecture.md` (`reconciliation_discrepancy` placeholder mirrored)

## Related Contracts

- `projects/finance-platform/specs/contracts/http/reconciliation-api.md` (this PR — ingest line optional `baseAmount`; AMOUNT_MISMATCH shape)
- `projects/finance-platform/specs/contracts/events/finance-ledger-events.md` (this PR — AMOUNT_MISMATCH first-activation note)
- `platform/error-handling.md` (this PR — 11th increment; no new code)

## Edge Cases

- **AMOUNT_MISMATCH reuse, not FX_DIFFERENCE** — the base-leg difference is recorded as the
  existing `AMOUNT_MISMATCH` type (the base amounts mismatch on a matched line). This avoids a
  CHECK-constraint migration (the type allow-list already includes it) and is semantically
  accurate. A dedicated `FX_DIFFERENCE` type is **not** introduced.
- **Matched + discrepancy on the same line** — a foreign line can be BOTH `MATCHED` (transaction
  leg) AND carry an `AMOUNT_MISMATCH` discrepancy (base leg). This is intended — the settlement is
  identified; the FX gap is flagged. The line appears in `matches` AND a discrepancy references
  its `externalRef` + `journalEntryId`.
- **Base-leg only for foreign lines with a declared base** — the check fires only when
  `currency != KRW`, `ext.baseAmount` is present, and it differs. KRW lines (base == amount) and
  base-less foreign lines never produce a base-leg discrepancy (net-zero).
- **Exact comparison** — any non-zero base difference → `AMOUNT_MISMATCH` (a configurable tolerance
  is forward-declared). The internal carrying base already embeds any prior revaluation (it is the
  current `baseMoney`), so the FX difference is measured against the live carrying value.
- **Internal `baseMoney`** — built from `JournalLine.baseMoney()` (the 8th-increment field), so a
  USD internal line's carrying base is its KRW value at the booked rate, not its USD amount.
- **Period lock / F8 unchanged** — the ingest period-lock guard and the no-auto-close invariant
  are not touched; an FX-difference discrepancy in a closed period is frozen like any other.

## Failure Scenarios

- **F1 — the matcher auto-adjusts the FX difference (posts a balancing entry)** — would violate
  F8. Guarded by the matcher only RECORDING an OPEN discrepancy; it never posts an entry or
  resolves (AC-1, AC-5) — same invariant as FIN-BE-010, exercised by the new case.
- **F2 — a base-leg discrepancy fires on a KRW line / a base-less line (false positive)** — would
  flood the operator queue. Guarded by the triple condition (`currency != KRW` AND `baseAmount`
  present AND differs) (AC-2), proven by the net-zero unit cases.
- **F3 — the comparison uses the internal USD amount instead of its carrying base** — would
  compare incomparable currencies. Guarded by `InternalLine.baseMoney` being the KRW carrying base
  from `JournalLine.baseMoney()` (AC-3), asserted by a JPA slice.
- **F4 — the V6 migration breaks existing rows / the CHECK rejects AMOUNT_MISMATCH** — would fail
  every ingest. Guarded by additive nullable columns + reusing the already-allowed type (no CHECK
  change) (AC-4), validated by the Testcontainers IT (MySQL enforces CHECK).
- **F5 — the change regresses the FIN-BE-010 reconciliation (UNMATCHED_*, period-lock)** — would
  break existing reconciliation. Guarded by leaving the transaction-leg matching + the UNMATCHED_*
  classification + the period lock unchanged (net-zero) + the existing reconciliation ITs (AC-6).
- **F6 — Docker-free `:check` passes but the V6 / the base-column round-trip / the emission is
  broken** — the unit tests don't run Flyway, the JPA base-column persistence, or the Kafka
  emission; the Testcontainers Integration job (V6 + the FX-difference ingest + the
  `discrepancy.detected.v1 type=AMOUNT_MISMATCH` round-trip) is the authoritative gate (AC-6). NB:
  `ledger_outbox` queries use `event_type` (no `topic` column) — a known finance-IT trap.
