# TASK-FIN-BE-016 — ledger-service realized FX gain/loss on settlement (10th increment: settle a foreign-currency position at a settlement rate, removing it at carrying and booking the realized FX_GAIN / FX_LOSS)

**Status:** done

**Type:** TASK-FIN-BE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Opus (fintech domain mechanic — a new pure settlement policy with signed asset/liability polarity across a 3-line entry, the realized-vs-carrying computation [no double-count against revaluation], position removal, funnelling through the guarded write path; net-zero for the existing paths)

---

## Goal

Deliver the **realized FX gain/loss on settlement** increment forward-declared by
`specs/services/ledger-service/architecture.md` § Increment Scope (the item the 9th increment,
FX revaluation, deferred). The 9th increment books the **unrealized** movement of an OPEN
foreign position (its base carrying value tracks spot, the foreign quantity stays).
**Settling** the position **realizes** the gain/loss: the foreign holding is converted to the
base currency and **removed**, and the difference between the **base proceeds** and the
position's **carrying base value** is recognised as a *realized* `FX_GAIN` / `FX_LOSS`. Together
revaluation + settlement cover the full FX P&L lifecycle.

**Key design** (architecture.md § FX settlement): the settlement is a **balanced base-currency
3-line entry** funnelled through the **existing `PostJournalEntryUseCase.post`** (the single
guarded write path). It **reuses** the 8th-increment multi-currency line + the 9th-increment FX
accounts — **no new line primitive, no migration**:
- a **position-removal** line on the foreign account — `JournalLine.of(money = |F| {currency},
  baseAmount = |C| KRW)` (the 8th-incr multi-currency form), posted on the side that **zeroes**
  the `(account, currency)` position (foreign → 0, base → 0);
- a **base proceeds** line on an operator-supplied `proceedsAccountCode` — an ordinary KRW line
  for `proceedsBase = round(foreignBalance × settlementRate)`; and
- the realized **`FX_GAIN`/`FX_LOSS`** contra (9th-incr accounts) — an ordinary KRW line.

All KRW base amounts net (`Σ baseDebit == Σ baseCredit`), so the existing `JournalEntry` factory
accepts it.

**Polarity is automatic** for asset AND liability positions via **debit-positive signed
arithmetic**: `realized = proceedsBase − carryingBase`; the removal + proceeds line directions
follow `sign(F)` (a debit-balance asset is removed by a CREDIT and brings base IN → DR proceeds;
a credit-balance liability by a DEBIT and pays base OUT → CR proceeds); the FX line direction
follows `sign(realized)` (`> 0` → CR `FX_GAIN`, `< 0` → DR `FX_LOSS`). No account-type branching.

**No double-count vs revaluation**: `realized` is measured against the **carrying** `C` (which
already embeds any prior revaluation), so a revalue-then-settle realizes only the incremental
movement; the lifetime total `unrealized + realized = proceeds − cost` is correct.

This is a **deliberate first slice** — the **whole** `(account, currency)` position is settled at
a **caller-supplied** rate. **Partial / weighted-average settlement, a proceeds-amount input, a
live FX rate feed, a bulk/period-close hook, multi-currency reconciliation, and a configurable
base currency are forward-declared** (named below).

**Net-zero**: no position in that currency (`foreignBalance == 0`) → a `200 {settled:false}`
no-op (no entry); the auto-journal + revaluation + manual paths never settle. The `JournalEntry`
factory, `PostingPolicy`, the line factories, and the schema are unchanged.

## Scope

**Spec PR (this task → ready):** authored already —
`specs/services/ledger-service/architecture.md` (§ Increment Scope 10th IN + forward-decl; new
§ FX settlement [policy, the 3-line entry table, automatic polarity, the no-double-count note,
the worked example, the use-case flow]; REST endpoints `POST /settlements`; Layer Structure;
fintech F2/F5 mappings; Failure Modes 33–36; Testing; provenance) +
`specs/contracts/http/ledger-api.md` (§ 11 `POST /settlements`; `SETTLEMENT_RATE_INVALID` error
row; `sourceType=SETTLEMENT`; out-of-scope → partial settlement) +
`specs/contracts/events/finance-ledger-events.md` (`entry.posted.v1` `sourceType` enum +
`SETTLEMENT`) + `platform/error-handling.md` (10th increment; `SETTLEMENT_RATE_INVALID`).

**Impl PR — IN (FX settlement increment):**
- **Domain — `FxSettlementPolicy`** (`domain/journal/`, pure, NO Spring/JPA): `settle(String
  tenantId, String ledgerAccountCode, Currency currency, long foreignBalanceMinor, long
  carryingBaseMinor, BigDecimal settlementRate, String proceedsAccountCode) →
  Optional<SettlementResult>`. `settlementRate ≤ 0` → throw `SettlementRateInvalidException`.
  `F == 0` → `Optional.empty()`. `proceedsBase = new BigDecimal(F).multiply(rate).setScale(0,
  HALF_UP).longValueExact()`; `realized = proceedsBase − carryingBaseMinor`. Build the **three
  unattached `JournalLine`s** per the architecture.md table:
  - removal: `JournalLine.of(tenant, ledgerAccountCode, F > 0 ? CREDIT : DEBIT, Money.of(|F|,
    currency), Money.of(|C|, BASE))` (the 8th-incr multi-currency form);
  - proceeds: `JournalLine.of(tenant, proceedsAccountCode, F > 0 ? DEBIT : CREDIT,
    Money.of(|proceedsBase|, BASE))` (KRW line);
  - FX: `realized > 0` → `credit(FX_GAIN, |realized|)`; `realized < 0` → `debit(FX_LOSS,
    |realized|)`; `realized == 0` → **no FX line** (the entry is just removal + proceeds, which
    already balance — but it still needs ≥ 2 lines, which it has). `SettlementResult` carries
    `realized` (signed), `proceedsBase`, `outcome` (`FX_GAIN`/`FX_LOSS`, or none when realized
    == 0), and `List<JournalLine> lines`. **Polarity must be automatic** — only `sign(F)` and
    `sign(realized)`, no asset/liability branching. (Edge: `realized == 0` → a 2-line entry
    [removal + proceeds]; both base amounts equal `|C| == |proceedsBase|`.)
- **Domain — `SourceRef`**: add `TYPE_SETTLEMENT = "SETTLEMENT"` + `ofSettlement(reference,
  sourceEventId)` (mirror `ofRevaluation`).
- **Domain — error**: `LedgerErrors.SettlementRateInvalidException` (code
  `SETTLEMENT_RATE_INVALID`).
- **Application — `SettleForeignPositionCommand`** + **`SettleForeignPositionUseCase`** (one
  `@Transactional`, **mirror `RevalueForeignBalanceUseCase`**): require `Idempotency-Key`
  (`settle:{key}`, ≤ 50 chars → `IdempotencyKeyRequiredException`); replay → original via
  `findBySourceEventId("settle:{key}", tenant)` → `Result(settled=false, reason=REPLAY, entry)`;
  `currency == BASE`/unsupported → `CurrencyMismatchException`; `proceedsAccountCode` must exist
  (`ledgerAccountRepository.existsByCode` → `LedgerAccountNotFoundException` 404, no lazy mint);
  load `accountTotalsForCurrency(ledgerAccountCode, currency, tenant)` (empty / `F == 0` →
  `Result(settled=false, reason=NO_POSITION)`, key NOT marked); `FxSettlementPolicy.settle(...)`;
  else build `JournalEntry.post(UUID, tenant, postedAt(default clock.now()),
  SourceRef.ofSettlement(reference, "settle:{key}"), result.lines())`,
  `markProcessed("settle:{key}", ...)`, then `postJournalEntryUseCase.post(entry, reason,
  operatorSubject)`. Return `Result(settled=true, realizedBaseMinor, proceedsBaseMinor, outcome,
  entry)`. `reason` falls back memo→reference→"FX settlement".
- **Presentation — `SettlementController`** (`POST /api/finance/ledger/settlements`, NO
  `@Transactional`, `@RequestHeader("Idempotency-Key")` — **mirror `RevaluationController`**):
  request DTO `{ ledgerAccountCode, currency, settlementRate (String), proceedsAccountCode,
  postedAt? (Instant), reference?, memo? }`; pull `operatorSubject`/`tenantId` the same way the
  RevaluationController does. Response: `201 {settled:true, realizedBaseMinor, proceedsBaseMinor,
  outcome, entry}` on a booked settlement; `200 {settled:false, reason, entry?}` on no-op/replay.
  Reuse the entry response DTO (renders `exchangeRate`+`baseAmount` per line). Parse
  `settlementRate` as `new BigDecimal(string)`.
- **Presentation — `GlobalExceptionHandler`**: map `SettlementRateInvalidException` → 422
  `SETTLEMENT_RATE_INVALID` (the others — `CurrencyMismatch`, `LedgerAccountNotFound`,
  `IdempotencyKeyRequired`, `LedgerPeriodClosed` — are already mapped).
- **NO new GL accounts** (`FX_GAIN`/`FX_LOSS` already seeded by the 9th increment), **NO
  migration**, **NO new line factory** (reuses the 8th-incr `of(money, baseAmount)`).
- **Tests** (unit + slice + Integration): see Acceptance Criteria.

**Impl PR — OUT (still forward-declared):** **partial / weighted-average** settlement (a portion
of the position, proportional / FIFO carrying basis, residual position); a **proceeds-amount
input** (supply the actual base received instead of a rate); a **live FX rate feed**; a
**bulk/period-close auto-hook**; **multi-currency reconciliation**; a **configurable base
currency**.

## Acceptance Criteria

- **AC-1 (settlement policy, signed polarity)** — `FxSettlementPolicyTest` proves (reading the
  position debit-positive): an **asset** (`F > 0`) settled above carrying → realized `FX_GAIN`;
  below → `FX_LOSS`; a **liability** (`F < 0`) settled below carrying → gain + above → loss
  [polarity automatic — line directions from `sign(F)` + `sign(realized)`, no account-type
  branching]; the 3-line entry **balances in base**; the removal line zeroes the position
  (`money=|F| {ccy}`, `baseAmount=|C| KRW`); `proceedsBase = round(F × rate)` HALF_UP; `F == 0` →
  empty; `settlementRate ≤ 0` → `SettlementRateInvalidException`; settling at the carrying rate
  realizes 0 (a 2-line removal+proceeds entry that still balances).
- **AC-2 (reuse, no new primitive / no migration)** — the removal line uses the existing
  8th-increment `JournalLine.of(money, baseAmount)`; the FX line uses the 9th-increment
  `FX_GAIN`/`FX_LOSS`; **no `V6`+ migration**, **no new `JournalLine` factory**. A diff check
  confirms `JournalEntry`, `PostingPolicy`, `JournalLine`, and `db/migration` are unchanged.
- **AC-3 (position removed + no double-count)** — after a settlement the foreign position on the
  account is **gone** (`accountTotalsForCurrency` → foreign 0 + base 0) and the proceeds sit in
  `proceedsAccountCode`; an Integration **revalue-then-settle** sequence realizes only the
  incremental delta (the realized = proceeds − the already-revalued carrying).
- **AC-4 (guarded write path + provenance + emission)** — a settlement funnels through
  `PostJournalEntryUseCase.post(entry, reason, operatorSubject)`: audit actor = operator, the
  closed-period guard applies (`postedAt` in a CLOSED period → 422 `LEDGER_PERIOD_CLOSED`), and
  it emits the same `finance.ledger.entry.posted.v1` with `source.sourceType = "SETTLEMENT"`.
- **AC-5 (idempotency + no-op + validation net-zero)** — a replayed `Idempotency-Key` returns the
  original entry (200, no second post); missing/blank/oversized → 400 `IDEMPOTENCY_KEY_REQUIRED`;
  no position (`F == 0`) → 200 `{settled:false}` with no entry + key NOT consumed; `currency ==
  KRW`/unsupported → 422 `CURRENCY_MISMATCH`; unknown `ledgerAccountCode`/`proceedsAccountCode` →
  404 `LEDGER_ACCOUNT_NOT_FOUND`; `settlementRate ≤ 0` → 422 `SETTLEMENT_RATE_INVALID`.
- **AC-6 (F5 — no float)** — money stays integer minor units; only `settlementRate` is a
  `BigDecimal`; `proceedsBase` is computed then stored as a `long` (HALF_UP) and the entry
  balances exactly in integer base minor units. grep-zero `float`/`double` in the new code.
- **AC-7** — `:ledger-service:check` BUILD SUCCESSFUL; **CI "Integration (finance-platform,
  Testcontainers)" GREEN**: establish a USD position via a multi-currency manual entry (DR USD
  `CASH_CLEARING` / CR KRW wallet @ 13.0 → carrying 130 000) → `POST /settlements {CASH_CLEARING,
  USD, 13.7, proceedsAccountCode: CASH_KRW}` → 201, the 3-line entry persists (DR CASH_KRW
  137 000 / CR CASH_CLEARING 10 000 USD@130 000 base / CR FX_GAIN 7 000), the USD position is
  removed (foreign 0 + base 0), proceeds in CASH_KRW, trial balance base-balanced, consume
  `entry.posted.v1 sourceType=SETTLEMENT`; a below-carrying settlement → FX_LOSS; a
  revalue-then-settle realizes the incremental delta (AC-3); replay → 200 same entryId;
  `settlementRate:0` → 422; unknown proceeds account → 404; back-dated into a CLOSED window → 422
  `LEDGER_PERIOD_CLOSED`; no-position currency → 200 `settled:false`; cross-tenant → 403. The
  all-KRW auto-journal round-trip is unchanged (net-zero). No deploy-wiring change. NB:
  `ledger_outbox` queries use `event_type` (no `topic` column) — a known finance-IT trap.

## Related Specs

- `projects/finance-platform/specs/services/ledger-service/architecture.md` (this PR — governing; § FX settlement)
- `rules/domains/fintech.md` § F2 (double-entry balance — governing), § F5 (money = minor units, no float), § F6 (audit), § F1 (idempotency)
- `projects/finance-platform/specs/services/account-service/architecture.md` (`Money`/`Currency` semantics mirrored)

## Related Contracts

- `projects/finance-platform/specs/contracts/http/ledger-api.md` (this PR — § 11 `POST /settlements`)
- `projects/finance-platform/specs/contracts/events/finance-ledger-events.md` (this PR — `entry.posted.v1` `sourceType=SETTLEMENT`)
- `platform/error-handling.md` (this PR — 10th increment; `SETTLEMENT_RATE_INVALID`)

## Edge Cases

- **Position removal direction** — the removal line zeroes the `(account, currency)` position:
  for a debit-balance asset (`F > 0`) it is a CREDIT of `|F|` foreign / `|C|` base; for a
  credit-balance liability (`F < 0`) a DEBIT. The proceeds + FX directions follow the same / the
  realized sign — all automatic.
- **`realized == 0`** — settling at exactly the carrying rate books a 2-line entry (removal +
  proceeds), no FX line; the two base amounts are equal (`|C| == |proceedsBase|`) so it balances.
  Still ≥ 2 lines.
- **No double-count vs revaluation** — `realized = proceedsBase − carryingBase`, and
  `carryingBase` already includes prior revaluation adjustments, so the realized P&L is the
  movement from the **carried** rate to the settlement rate (not from cost). Lifetime
  `unrealized + realized = proceeds − cost`.
- **Minor-unit scale / rate** — `settlementRate` is the base-minor-per-foreign-minor factor
  (consistent with the 9th-incr `closingRate`). USD (scale 2) → KRW (scale 0) at ₩1,370/$1:
  `$1 = 100 USD-minor = 1370 KRW-minor` → `13.7`.
- **Proceeds account** — operator-supplied, must already exist (no lazy mint, like manual
  posting). It receives (asset) or pays (liability) base-currency proceeds; it is not
  currency-scoped (a KRW line is posted to it).
- **No position / not the base currency** — `F == 0` → 200 `settled:false` (key not consumed);
  `currency == KRW` → `CURRENCY_MISMATCH` (the base currency has no FX position to settle).
- **Closed period** — a back-dated settlement `postedAt` in a CLOSED period → 422
  `LEDGER_PERIOD_CLOSED` (inherited guard).

## Failure Scenarios

- **F1 — a settlement entry that does not balance in base persists** — would break the
  double-entry invariant. Guarded by constructing the 3 lines so `Σ baseDebit == Σ baseCredit`
  (proceeds = carrying + realized) and the factory's base-balance check (AC-1) before any persist.
- **F2 — wrong polarity for a liability / a loss booked as a gain** — special-casing would be
  error-prone. Guarded by the debit-positive `sign(F)` + `sign(realized)` rule (AC-1), proven for
  asset gain/loss + liability gain/loss.
- **F3 — double-count of FX P&L across revaluation + settlement** — booking the full
  proceeds-vs-cost on settlement would double-count the already-unrealized portion. Guarded by
  measuring `realized` against the **carrying** value (AC-3), proven by a revalue-then-settle IT.
- **F4 — the position is not removed (stale foreign balance lingers)** — would leave a phantom
  position revaluable again. Guarded by the removal line zeroing both the foreign and base sums
  of the `(account, currency)` row (AC-3), asserted via `accountTotalsForCurrency`.
- **F5 — float/rounding creates or destroys funds** — would violate F5. Guarded by keeping money
  integer (long minor), confining the decimal to `settlementRate`, and balancing the entry on the
  rounded integer `proceedsBase`/`realized` (AC-6).
- **F6 — the change regresses the auto-journal / revaluation / manual paths** — would break
  existing postings. Guarded by leaving `JournalEntry`/`PostingPolicy`/`JournalLine`/schema
  unchanged (net-zero) + the existing ITs (AC-7).
- **F7 — Docker-free `:check` passes but the position read / removal / emission is broken** —
  the unit tests don't run Flyway, the JPA `accountTotalsForCurrency` round-trip, or the Kafka
  emission; the Testcontainers Integration job (the settlement round-trip + position-removal
  assertion + `entry.posted.v1 sourceType=SETTLEMENT`) is the authoritative gate (AC-7). NB:
  `ledger_outbox` queries use `event_type` (no `topic` column) — a known finance-IT trap.
