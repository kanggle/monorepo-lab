# TASK-FIN-BE-014 — ledger-service multi-currency journals (8th increment: one entry may carry lines in different currencies, balanced in a fixed base/reporting currency)

**Status:** done

**Type:** TASK-FIN-BE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Opus (cross-cutting fintech change — the balance identity moves to a base currency, per-line FX fields, trial-balance + period-snapshot base consolidation, migration + backfill, exhaustive net-zero for existing KRW)

---

## Goal

Deliver the **multi-currency journals** increment forward-declared by
`specs/services/ledger-service/architecture.md` § Increment Scope. Until now a journal
entry was single-currency (cross-currency lines → `CURRENCY_MISMATCH`). The 8th
increment lets one entry carry lines in **different currencies**, balanced in a fixed
**reporting / base currency (KRW)**: each `JournalLine` keeps its transaction `Money` and
gains an `exchangeRate` (exact decimal to base) + a `baseAmount` (its value in KRW), and
the double-entry identity moves to the base currency (`Σ baseDebit == Σ baseCredit`).

This is a **deliberate first slice** — it books multi-currency entries at a
**caller-supplied** rate/base amount and consolidates the trial balance + period snapshot
in the base currency. **FX gain/loss + period-end revaluation, a live FX rate feed,
multi-currency reconciliation, and a configurable base currency are forward-declared**
(named below) — slicing the depth per the erp/ledger first-increment discipline.

**Net-zero is the central constraint**: every existing line is KRW; the V5 migration
backfills `base_amount = amount`, `base_currency = currency` (KRW), `exchange_rate = 1`,
and the base-currency balance check on an all-KRW entry is identical to the prior
same-currency check. The auto-journal `PostingPolicy` (KRW) is byte-identical.

## Scope

**Spec PR (this task → ready):** authored already —
`specs/services/ledger-service/architecture.md` (§ Increment Scope 8th IN + forward-decl;
new § Multi-currency journals; § Posting Policy note; Failure Modes 27–28; Layer
Structure; fintech F5 mapping; Testing; provenance) +
`specs/contracts/http/ledger-api.md` (§ 1 entry shape per-line `exchangeRate` +
`baseAmount`; § 4 trial-balance base-consolidated totals; § 9 manual posting per-line
optional `baseAmount`; out-of-scope → FX gain/loss) +
`specs/contracts/events/finance-ledger-events.md` (`entry.posted.v1` lines carry
`exchangeRate` + `baseAmount`) + `platform/error-handling.md` (8th increment; no new code).

**Impl PR — IN (multi-currency increment):**
- **Domain — base/reporting currency**: `domain/money/LedgerReportingCurrency.java` —
  `public static final Currency BASE = Currency.KRW;` (a single fixed reporting currency;
  configurable is forward-declared).
- **Domain — `JournalLine`**: add `exchangeRate` (a `BigDecimal`) + `baseAmount` (a
  `Money` in the base currency). New factory params; convenience factories keep the
  single-currency form (`debit(tenant, code, money)` → `baseAmount = money`,
  `exchangeRate = ONE`). A `JournalLine.of(tenant, code, direction, money, baseAmount)`
  form sets `exchangeRate = BigDecimal(baseAmount.minorUnits) / BigDecimal(money.minorUnits)`
  (exact, the minor-to-minor provenance factor; never used to re-derive the balance).
  Validate `baseAmount.currency() == LedgerReportingCurrency.BASE`. `reversed()` preserves
  `exchangeRate` + `baseAmount`.
- **Domain — `JournalEntry`**: the factory's balance check moves to the **base currency** —
  sum each line's `baseAmount` (all KRW) per side, require `Σ baseDebit == Σ baseCredit`
  → `LedgerEntryUnbalancedException`. **Remove** the "all lines same currency" rejection
  (cross-currency is now allowed). Keep `≥ 2 lines`. `debitTotal()`/`creditTotal()` stay
  per original currency for the reads (they are now only meaningful per-currency); add
  `baseDebitTotal()`/`baseCreditTotal()` (KRW). (Keep the existing single-currency
  `currency()` helper for the all-same-currency case; for a mixed entry it is the base —
  or expose `baseCurrency()`; the impl decides, but the reads must not throw on a mixed
  entry.)
- **Domain — `PostingPolicy`**: unchanged mapping; the KRW lines it builds get
  `baseAmount = money`, `rate = 1` via the convenience factory (net-zero).
- **Persistence (Flyway `V5__add_multi_currency.sql`)**: add to `journal_line`
  `exchange_rate DECIMAL(20,8) NOT NULL DEFAULT 1`, `base_amount_minor BIGINT NOT NULL`
  (no default — see backfill), `base_currency VARCHAR(3) NOT NULL DEFAULT 'KRW'`;
  **backfill** `UPDATE journal_line SET base_amount_minor = amount_minor,
  base_currency = currency, exchange_rate = 1` (all existing rows are KRW). Add a CHECK
  that `base_currency = 'KRW'` (v1) if convenient. `JournalLineJpaEntity` gains the three
  columns (`BigDecimal exchangeRate`, `long baseAmountMinor`, `Currency baseCurrency`).
- **Persistence (trial-balance + snapshot queries)**: `AccountTotalsRow` /
  `JournalRepository.AccountTotals` gain `baseDebitMinor` / `baseCreditMinor`; the
  `accountTotals` / `accountTotalsForCode` / `accountTotalsUpTo` JPQL add
  `SUM(base_amount_minor)` per side (the base-currency consolidation; the original
  `(account, currency)` grouping is retained for the per-currency breakdown). Decide the
  shape: either two parallel groupings (per-currency original + per-account base) or one
  row carrying both — keep the trial-balance view able to render both.
- **Application / reads**: `QueryLedgerUseCase` + the trial-balance view assemble the
  base-currency consolidated grand totals (`grandBaseDebitTotal == grandBaseCreditTotal`)
  + per-account base totals, alongside the existing per-currency breakdown.
- **Application — `CloseAccountingPeriodUseCase`**: the close-time snapshot grand totals
  are the **base-currency** consolidated totals (so a multi-currency period closes in
  balance); the per-account snapshot rows may keep original + base.
- **Presentation — manual posting (FIN-BE-011)**: `ManualJournalEntryRequest.LineRequest`
  gains an optional `baseAmount` (`MoneyRequest`); the mapper builds a multi-currency
  `JournalLine` (foreign `money` + supplied `baseAmount`) or the single-currency form
  (no `baseAmount` → defaults). `PostManualJournalEntryUseCase` is otherwise unchanged
  (the factory validates the base balance). The entry/trial-balance response DTOs render
  `exchangeRate` + `baseAmount` per line + base totals.
- **Tests** (unit + slice + Integration): see Acceptance Criteria.

**Impl PR — OUT (still forward-declared):** **FX gain/loss accounts + period-end
revaluation** (revaluing a foreign balance at a new rate, booking `FX_GAIN`/`FX_LOSS`);
a **live FX rate feed** (rates are caller-supplied); **multi-currency reconciliation**
(cross-currency clearing-account matching); a **configurable base currency** (fixed KRW);
fractional-rate edge handling beyond exact supplied base amounts.

## Acceptance Criteria

- **AC-1 (multi-currency entry, base-balanced)** — a journal entry MAY carry lines in
  different `money.currency`, each with an `exchangeRate` + a base-currency (KRW)
  `baseAmount`; the factory accepts it iff `Σ baseDebit == Σ baseCredit` (in integer KRW
  minor units) → else `LEDGER_ENTRY_UNBALANCED`. A `JournalEntryTest` proves an accepted
  cross-currency entry (DR USD line + CR KRW line with matching base amounts) and a
  rejected one (base amounts not netting to zero).
- **AC-2 (net-zero, existing/auto-journal KRW)** — a single-currency KRW entry is posted
  byte-identically: `baseAmount = amount`, `exchangeRate = 1`, `base_currency = KRW`. The
  auto-journal consumer path is unchanged (the existing reconciliation/period/auto-journal
  ITs still pass). The V5 migration backfills existing rows (an Integration assertion
  confirms an existing/auto-journal KRW line has `base_amount == amount`, `rate = 1`).
- **AC-3 (trial balance + snapshot consolidation)** — the trial balance returns the
  per-currency `debitTotal`/`creditTotal` breakdown **and** the base-currency
  `grandBaseDebitTotal == grandBaseCreditTotal` (in balance); a CLOSED period's snapshot
  grand totals are the base-currency consolidated totals (a multi-currency period closes
  in balance). In the all-KRW path the original and base totals coincide.
- **AC-4 (F5 — no float)** — money stays integer minor units (transaction and base both
  `long`); the `exchangeRate` is an exact `BigDecimal` / `DECIMAL(20,8)` recorded for
  provenance and **never** used to re-derive the balance (the supplied `baseAmount` is
  authoritative — no rounding can create/destroy funds). grep-zero `float`/`double` in
  money handling.
- **AC-5 (reversal)** — a `REVERSAL` of a multi-currency entry swaps debit/credit while
  preserving each line's `money` + `exchangeRate` + `baseAmount`, and still balances in
  base. A unit test proves it.
- **AC-6** — `:ledger-service:check` BUILD SUCCESSFUL; **CI "Integration
  (finance-platform, Testcontainers)" GREEN**: V5 runs + backfills; an existing KRW
  auto-journal posting round-trips unchanged (`entry.posted.v1`, trial balance == 0); a
  **manual cross-currency entry** (DR USD clearing / CR KRW wallet, balanced in KRW) →
  201 persisted with per-line base amounts, trial balance shows per-currency + base
  consolidated in balance; an unbalanced-base manual entry → 422 `LEDGER_ENTRY_UNBALANCED`;
  cross-tenant → 403. No deploy-wiring change.

## Related Specs

- `projects/finance-platform/specs/services/ledger-service/architecture.md` (this PR — governing; § Multi-currency journals)
- `rules/domains/fintech.md` § F5 (money = minor units, no float — governing), § F2 (double-entry balance)
- `projects/finance-platform/specs/services/account-service/architecture.md` (`Money`/`Currency` semantics mirrored)

## Related Contracts

- `projects/finance-platform/specs/contracts/http/ledger-api.md` (this PR — entry/trial-balance/manual posting shapes)
- `projects/finance-platform/specs/contracts/events/finance-ledger-events.md` (this PR — `entry.posted.v1` per-line base amounts)
- `platform/error-handling.md` (this PR — 8th increment; no new code)

## Edge Cases

- **Minor-unit scale across currencies** — USD/EUR scale 2 (cents), KRW/JPY scale 0. The
  `baseAmount` is a base-currency (KRW, scale 0) integer; the `exchangeRate` is the
  **minor-to-minor** factor (`baseAmount.minor / money.minor`) — e.g. `$100.00` =
  `10000` USD-minor converts to `135000` KRW-minor at rate `13.5`. The contract example
  uses these consistent numbers; the balance is on the KRW base minor integers, so
  cross-scale conversion never introduces float error.
- **`baseAmount` authoritative (no rounding-breaks-balance)** — the caller supplies the
  base amount per line; the factory checks `Σ baseDebit == Σ baseCredit` on those integers.
  The rate is derived for provenance. (A future increment that computes the base from a
  rate must handle rounding — out of scope here.)
- **Base-currency line** — `money.currency == KRW` → `baseAmount` defaults to `money`,
  `exchangeRate = 1` (the operator omits `baseAmount`).
- **Net-zero / backfill** — all existing rows are KRW; the V5 backfill is exact; the
  base-balance check on an all-KRW entry equals the prior same-currency check.
- **`CURRENCY_MISMATCH` semantics narrow** — it no longer rejects a multi-currency entry;
  it remains for `Money` arithmetic on mismatched currencies and an unsupported currency
  code (`UnsupportedCurrencyException`). The base-sum path only adds KRW to KRW.
- **Auto-journal unchanged** — `PostingPolicy` is single-currency (account-service KRW);
  multi-currency arises only via manual posting in this increment.

## Failure Scenarios

- **F1 — a multi-currency entry that does not balance in base persists** — would break
  the double-entry invariant. Guarded by the factory's base-currency balance check
  (AC-1) before any persist.
- **F2 — float/rounding creates or destroys funds** — would violate F5. Guarded by keeping
  money integer (long minor) on both transaction and base amounts and treating the
  `baseAmount` as authoritative (the rate is provenance-only, never re-derives the
  balance) (AC-4).
- **F3 — the change regresses the single-currency KRW path** — would break every existing
  posting. Guarded by the net-zero convenience factories (`baseAmount = money`, `rate = 1`)
  + the V5 backfill + the unchanged auto-journal ITs (AC-2).
- **F4 — a multi-currency period closes out of balance** — would corrupt the close
  snapshot. Guarded by computing the snapshot grand totals in the base currency (AC-3).
- **F5 — Docker-free `:check` passes but the migration / base consolidation is broken** —
  the unit tests don't run Flyway or the JPA base-sum round-trip; the Testcontainers
  Integration job (V5 + the multi-currency posting + trial-balance round-trip) is the
  authoritative gate (AC-6). NB: `ledger_outbox` queries use `event_type` (no `topic`
  column) — a known finance-IT trap.
