# TASK-FIN-BE-015 — ledger-service FX gain/loss revaluation (9th increment: revalue a foreign-currency position at a closing rate, booking the base-carrying delta to FX_GAIN / FX_LOSS)

**Status:** ready

**Type:** TASK-FIN-BE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Opus (fintech domain mechanic — a new pure revaluation policy with signed gain/loss polarity, a zero-amount base-adjustment line primitive, no-double-booking on re-revaluation, funnelling through the guarded write path; exhaustive net-zero for the existing paths)

---

## Goal

Deliver the **FX gain/loss revaluation** increment forward-declared by
`specs/services/ledger-service/architecture.md` § Increment Scope (the item the 8th
increment, multi-currency journals, deferred). The 8th increment books a multi-currency
entry at the rate supplied **at posting time** and records each line's base value; it does
**not** revalue. As the market (spot) rate moves, an open **foreign-currency position**'s
carrying value in the base currency drifts from its current worth. This increment lets an
operator **revalue** a position (`{ledgerAccountCode, currency}`) at a new **closing
(spot) rate**, truing its base carrying value up to spot and recognising the difference as
an **unrealized FX gain or loss** booked to the new GL accounts `FX_GAIN` (income) /
`FX_LOSS` (expense).

**Key design** (architecture.md § FX gain/loss revaluation): the revaluation is itself a
**balanced base-currency (KRW) adjusting entry** funnelled through the **existing guarded
write path** (`PostJournalEntryUseCase.post`). It has two lines:
- a **base-carrying adjustment** on the foreign account — `money.amount = 0` in the foreign
  currency (the foreign **quantity is unchanged**) with a non-zero `baseAmount` (the KRW
  carrying delta); a new `JournalLine.baseAdjustment` factory, the **only** caller that
  permits a zero transaction amount; and
- a contra **`FX_GAIN`/`FX_LOSS`** ordinary positive KRW line.

Both balance in the base currency (`Σ baseDebit == Σ baseCredit`), so the existing
`JournalEntry` factory accepts it and the existing `journal_line` columns carry it —
**there is NO `V6` migration**.

This is a **deliberate first slice** — unrealized revaluation of one `(account, currency)`
position per operator call, at a **caller-supplied** closing rate. **Realized FX gain/loss
on settlement, a bulk / all-positions revaluation + a period-close auto-hook, a live FX
rate feed, multi-currency reconciliation, and a configurable base currency are
forward-declared** (named below).

**Net-zero is the central constraint**: the auto-journal and manual paths are untouched (no
revaluation unless the operator calls the endpoint); `delta == 0` (already at spot) or no
position → a `200 {revalued:false}` no-op (no entry). The `JournalEntry` factory, the
`PostingPolicy`, the existing line factories, and the schema are unchanged.

## Scope

**Spec PR (this task → ready):** authored already —
`specs/services/ledger-service/architecture.md` (§ Increment Scope 9th IN + forward-decl;
new § FX gain/loss revaluation [policy, the base-adjustment line, automatic polarity, the
no-double-booking worked example, the use-case flow]; § Chart of Accounts FX_GAIN/FX_LOSS;
REST endpoints `POST /revaluations`; Layer Structure; fintech F5 + F2 mappings; Failure
Modes 29–32; Testing; provenance) + `specs/contracts/http/ledger-api.md` (§ 10
`POST /revaluations`; `REVALUATION_RATE_INVALID` error row; `sourceType=REVALUATION`;
out-of-scope → realized FX) + `specs/contracts/events/finance-ledger-events.md`
(`entry.posted.v1` `sourceType` enum + `REVALUATION`) + `platform/error-handling.md` (9th
increment; `REVALUATION_RATE_INVALID`).

**Impl PR — IN (FX revaluation increment):**
- **Domain — chart of accounts**: `domain/account/LedgerAccountCodes.java` — add
  `FX_GAIN` / `FX_LOSS` constants; extend `typeForCode` to classify `FX_GAIN → INCOME`,
  `FX_LOSS → EXPENSE` (wallets stay LIABILITY, the rest ASSET). `LedgerAccountType` already
  has INCOME/EXPENSE.
- **Domain — `JournalLine.baseAdjustment`**: a new factory
  `baseAdjustment(tenantId, code, Currency currency, EntryDirection direction, Money
  baseAmount, BigDecimal spotRate)` — the transaction `money` is **zero** in `currency`
  (`Money.zero(currency)`), `baseAmount` is the KRW carrying delta (validate
  `baseAmount.currency() == LedgerReportingCurrency.BASE` and `baseAmount` strictly
  positive; `currency != BASE`), `exchangeRate = spotRate` (provenance). It is the ONLY
  factory that allows a zero transaction amount (the positive-amount `of`/`debit`/`credit`
  factories are unchanged — they still reject `≤ 0`). `reversed()` must preserve a
  base-adjustment line (it already swaps direction + keeps money/baseMoney/rate).
- **Domain — `FxRevaluationPolicy`** (`domain/journal/`, pure): `revalue(ledgerAccountCode,
  Currency currency, long foreignBalanceMinor, long carryingBaseMinor, BigDecimal
  closingRate) → Optional<RevaluationResult>`. `closingRate ≤ 0` →
  `RevaluationRateInvalidException`. `revaluedBase = round(foreignBalanceMinor ×
  closingRate)` (`BigDecimal`, HALF_UP, to a `long`); `delta = revaluedBase −
  carryingBaseMinor`. `delta == 0` → `Optional.empty()`. Else return a `RevaluationResult`
  carrying `delta`, the `outcome` (`FX_GAIN` when `delta > 0`, `FX_LOSS` when `< 0`), and
  the **two unattached `JournalLine`s** (the base-adjustment on the foreign account + the
  contra `FX_GAIN`/`FX_LOSS` KRW line) per the polarity table (§ architecture.md). Pure,
  exhaustively unit-tested.
- **Domain — `SourceRef`**: add `TYPE_REVALUATION = "REVALUATION"` +
  `ofRevaluation(reference, sourceEventId)` (mirrors `ofManual`).
- **Domain — error**: `LedgerErrors.RevaluationRateInvalidException` (code
  `REVALUATION_RATE_INVALID`).
- **Application — `RevalueForeignBalanceUseCase`** (one `@Transactional`, mirrors
  `PostManualJournalEntryUseCase`): require `Idempotency-Key` (`reval:{key}`, ≤ 50 chars →
  `IdempotencyKeyRequiredException`); replay (key processed) → return the original entry via
  `findBySourceEventId("reval:{key}", tenant)` → `Result(revalued=false, reason=REPLAY,
  entry)`; load the position via `journalRepository.accountTotalsForCurrency(account,
  currency, tenant)` (no row / zero foreign balance → `revalued=false, reason=NO_POSITION`,
  key NOT marked); `currency == KRW`/unsupported → `CurrencyMismatchException`;
  `FxRevaluationPolicy.revalue(...)` → empty (`delta==0`) → `revalued=false, reason=AT_SPOT`
  (key NOT marked); else build `JournalEntry.post(newId, tenant, postedAt,
  SourceRef.ofRevaluation(reference, "reval:{key}"), [adjustment, contra])`,
  `markProcessed("reval:{key}")`, then `postJournalEntryUseCase.post(entry, reason,
  operatorSubject)` (the guarded write path — closed-period guard, audit, outbox; FX
  accounts lazily created with the right type). Return `Result(revalued=true,
  deltaBaseMinor, outcome, entry)`. A `RevalueForeignBalanceCommand` carries
  `(tenantId, operatorSubject, ledgerAccountCode, currency, closingRate, postedAt?,
  reference, memo, idempotencyKey)`.
- **Application — port**: `JournalRepository.accountTotalsForCurrency(code, currency,
  tenant) → Optional<AccountTotals>` (the one position's foreign balance + base carrying).
  Implement by filtering the existing `accountTotalsForCode` rows to the currency (no new
  JPQL needed) OR a focused query — impl decides; **no new column / no migration**.
- **Presentation — `RevaluationController`** (`POST /api/finance/ledger/revaluations`, no
  `@Transactional`, `@RequestHeader("Idempotency-Key")`): request DTO
  `{ ledgerAccountCode, currency, closingRate (string decimal), postedAt?, reference?,
  memo? }`; `201` with `{revalued:true, deltaBaseMinor, outcome, entry}` on a booked
  revaluation, `200` with `{revalued:false, reason, entry?}` on a no-op/replay. Reuse the
  entry response DTO (renders `exchangeRate` + `baseAmount` per line). Map
  `RevaluationRateInvalidException`/`CurrencyMismatchException`/etc. in
  `GlobalExceptionHandler` (most codes already mapped; add `REVALUATION_RATE_INVALID`).
- **Config — `ChartOfAccountsSeedConfig`**: also seed `FX_GAIN` (INCOME) + `FX_LOSS`
  (EXPENSE) idempotently.
- **Tests** (unit + slice + Integration): see Acceptance Criteria.

**Impl PR — OUT (still forward-declared):** **realized FX gain/loss on settlement** (this
increment is unrealized only); a **bulk / all-positions** revaluation + a **period-close
auto-hook** (one position per call here); a **live FX rate feed** (rates caller-supplied);
**multi-currency reconciliation**; a **configurable base currency**; a human-readable
(units-per-unit) rate input (this increment takes the minor-to-minor factor, consistent
with the stored `exchangeRate`).

## Acceptance Criteria

- **AC-1 (revaluation policy, signed polarity)** — `FxRevaluationPolicyTest` proves, for a
  position read **debit-positive** (`foreignBalance = Σdebit − Σcredit`,
  `carryingBase = ΣbaseDebit − ΣbaseCredit`): an **asset gain** (F>0, rate↑ → delta>0 → DR
  account / CR FX_GAIN); an **asset loss** (rate↓ → CR account / DR FX_LOSS); a
  **liability loss** (F<0, base value ↑ → delta<0 → loss) and a **liability gain** (F<0,
  rate↓ → delta>0 → gain) — the sign of `delta` alone selects gain/loss, **no account-type
  branching**; `delta == 0` → empty (no-op); `round(F × rate)` HALF_UP; `closingRate ≤ 0`
  → `RevaluationRateInvalidException`.
- **AC-2 (base-adjustment line)** — `JournalLine.baseAdjustment` builds a line with a zero
  foreign `money` amount and a non-zero KRW `baseAmount`; an entry of `[baseAdjustment,
  contra]` **balances in base** (`Σ baseDebit == Σ baseCredit`) and the existing
  `JournalEntry` factory accepts it unchanged. `reversed()` preserves it. The positive
  `of`/`debit`/`credit` factories still reject a zero amount. A unit test proves all of
  this. **No migration** (`amount_minor` already allows 0).
- **AC-3 (no double-booking on re-revaluation)** — an Integration test posts a USD position,
  revalues it twice at two different rates, and a third time at a lower rate: each
  revaluation books only the **incremental** delta from the **already-revalued** carrying;
  the USD foreign balance is unchanged throughout; the position's base carrying ==
  `foreignBalance × latestRate` after each; the trial balance stays base-balanced.
- **AC-4 (guarded write path + provenance + emission)** — a revaluation funnels through
  `PostJournalEntryUseCase.post(entry, reason, operatorSubject)`: the audit actor is the
  operator subject, the closed-period guard applies (a `postedAt` in a CLOSED period → 422
  `LEDGER_PERIOD_CLOSED`), and it emits the same `finance.ledger.entry.posted.v1` with
  `source.sourceType = "REVALUATION"`. `FX_GAIN`/`FX_LOSS` are seeded + classified
  (INCOME/EXPENSE).
- **AC-5 (idempotency + no-op net-zero)** — a replayed `Idempotency-Key` returns the
  original entry (200, no second post); a missing/blank/oversized key → 400
  `IDEMPOTENCY_KEY_REQUIRED`; `delta == 0` (at spot) or no position in that currency → 200
  `{revalued:false}` with **no entry** and the key **not** consumed; `currency == KRW` /
  unsupported → 422 `CURRENCY_MISMATCH`; `closingRate ≤ 0` → 422 `REVALUATION_RATE_INVALID`.
- **AC-6 (F5 — no float)** — money stays integer minor units (the base-adjustment and contra
  amounts are `long`); only `closingRate` is an exact `BigDecimal`; `revaluedBase` is
  computed then stored as a `long` (HALF_UP) and the booked `delta` is integer base minor
  units balanced exactly. grep-zero `float`/`double` in the new code.
- **AC-7** — `:ledger-service:check` BUILD SUCCESSFUL; **CI "Integration (finance-platform,
  Testcontainers)" GREEN**: establish a USD position via a multi-currency manual entry →
  `POST /revaluations {CASH_CLEARING, USD, 13.5}` → 201, the 2-line revaluation entry
  persists (DR CASH_CLEARING USD amount 0 / base delta, CR FX_GAIN delta), trial balance
  stays base-balanced, USD foreign balance unchanged, consume `entry.posted.v1`
  `sourceType=REVALUATION`; second revaluation @ a different rate books the incremental
  delta (no double-booking, AC-3); a lower-rate revaluation books FX_LOSS; replay → 200 same
  entryId; `closingRate:0` → 422; back-dated into a CLOSED window → 422
  `LEDGER_PERIOD_CLOSED`; no-position currency → 200 `revalued:false`; cross-tenant → 403.
  The all-KRW auto-journal round-trip is unchanged (net-zero). No deploy-wiring change. NB:
  `ledger_outbox` queries use `event_type` (no `topic` column) — a known finance-IT trap.

## Related Specs

- `projects/finance-platform/specs/services/ledger-service/architecture.md` (this PR — governing; § FX gain/loss revaluation)
- `rules/domains/fintech.md` § F5 (money = minor units, no float — governing), § F2 (double-entry balance), § F6 (audit), § F1 (idempotency)
- `projects/finance-platform/specs/services/account-service/architecture.md` (`Money`/`Currency` semantics mirrored)

## Related Contracts

- `projects/finance-platform/specs/contracts/http/ledger-api.md` (this PR — § 10 `POST /revaluations`)
- `projects/finance-platform/specs/contracts/events/finance-ledger-events.md` (this PR — `entry.posted.v1` `sourceType=REVALUATION`)
- `platform/error-handling.md` (this PR — 9th increment; `REVALUATION_RATE_INVALID`)

## Edge Cases

- **The base-adjustment line carries the foreign currency (amount 0)** — it MUST land in the
  position's own `(account, currency)` row (so its `baseAmount` is part of the position's
  carrying), which is why its `money` is `Money.zero(currency)` not a KRW zero. This is what
  makes re-revaluation read the already-revalued carrying (no double-booking).
- **Polarity from debit-positive arithmetic** — `foreignBalance`/`carryingBase` are read
  `Σdebit − Σcredit` (debit-positive), so a liability's natural credit balance is negative;
  `delta = revaluedBase − carryingBase` then yields a loss for a growing liability and a gain
  for an appreciating asset with the **same** sign rule — no account-type branching.
- **Minor-unit scale** — `closingRate` is the **base-minor-per-foreign-minor** factor
  (consistent with the 8th increment's stored `exchangeRate = baseAmount.minor /
  money.minor`). USD (scale 2) → KRW (scale 0) at ₩1,350/$1: `$1 = 100 USD-minor = 1350
  KRW-minor` → `closingRate = 13.5`. Documented in the contract with the worked example.
- **No position / at spot** — `accountTotalsForCurrency` empty or `foreignBalance == 0`, or
  `delta == 0`, returns `revalued:false` (no entry); the `Idempotency-Key` is **not** marked
  (a real position can be revalued later under the same key conceptually, but operators
  should use a fresh key — documented).
- **Rounding** — `revaluedBase = round(foreignBalance × closingRate)` HALF_UP; the booked
  `delta` is the integer difference, so the entry balances exactly (the contra equals the
  adjustment). No fractional KRW.
- **FX accounts** — seeded + classified; the guarded write path's lazy-create also assigns
  INCOME/EXPENSE via `typeForCode`, so even an un-seeded environment posts correctly.
- **Closed period** — a back-dated revaluation `postedAt` in a CLOSED period is rejected by
  the inherited guard (422 `LEDGER_PERIOD_CLOSED`) — a month-end revaluation is posted into
  the still-OPEN period being closed, or at its last instant.

## Failure Scenarios

- **F1 — a revaluation entry that does not balance in base persists** — would break the
  double-entry invariant. Guarded by building both lines for the same `|delta|` and the
  factory's base-currency balance check (AC-2) before any persist.
- **F2 — double-booking on re-revaluation** — booking the full `revaluedBase` each time (not
  the delta from the already-revalued carrying) would inflate FX gain/loss. Guarded by the
  base-adjustment landing in the position's own row + reading carrying from the live totals
  (AC-3).
- **F3 — wrong gain/loss polarity for a liability** — special-casing asset vs liability would
  be error-prone. Guarded by the debit-positive signed `delta` rule (AC-1) — proven for all
  four quadrants.
- **F4 — float/rounding creates or destroys funds** — would violate F5. Guarded by keeping
  money integer (long minor), confining the only decimal to `closingRate`, and balancing the
  contra against the rounded integer `delta` (AC-6).
- **F5 — the change regresses the auto-journal / manual KRW path** — would break every
  existing posting. Guarded by leaving the `JournalEntry` factory, `PostingPolicy`, the
  positive line factories, and the schema unchanged (net-zero) + the existing ITs (AC-7).
- **F6 — Docker-free `:check` passes but the position read / no-double-booking / emission is
  broken** — the unit tests don't run Flyway, the JPA `accountTotalsForCurrency` round-trip,
  or the Kafka emission; the Testcontainers Integration job (the revaluation round-trip + the
  re-revaluation no-double-booking assertion + `entry.posted.v1 sourceType=REVALUATION`) is
  the authoritative gate (AC-7). NB: `ledger_outbox` queries use `event_type` (no `topic`
  column) — a known finance-IT trap.
