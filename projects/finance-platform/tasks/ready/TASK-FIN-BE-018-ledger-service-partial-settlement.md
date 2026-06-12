# Task ID

TASK-FIN-BE-018

# Title

ledger-service: partial / weighted-average FX settlement (12th increment)

# Status

ready

# Owner

backend

# Task Tags

- code
- api

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Goal

Deliver the **partial / weighted-average settlement** increment forward-declared by
`specs/services/ledger-service/architecture.md` § Increment Scope (now the **twelfth increment**;
the architecture § Increment Scope 12th block + header + forward-declared list are already
updated on this branch).

The 10th increment (TASK-FIN-BE-016) settles the **whole** `(account, currency)` foreign position
at a caller-supplied settlement rate. This increment settles a **portion**: an operator supplies an
optional **`settleForeignAmount`** (foreign minor) and the position is reduced by exactly that,
removing a **weighted-average proportional** share of the carrying base
(`C_settle = round(C × |F_settle| / |F|)`, HALF_UP) and leaving a **residual OPEN position**
`(F − F_settle, C − C_settle)`. Omitting `settleForeignAmount` (or supplying the full balance)
settles the whole position **byte-identically to the 10th** (net-zero — the 10th's tests unchanged).

This adds **no new write boundary, no new line primitive, no migration**: the 10th's balanced
base-currency 3-line entry (`FxSettlementPolicy`) is reused with the **partial** quantities and
funnelled through the existing `PostJournalEntryUseCase.post`. The residual simply remains on the
account (double-entry leaves it OPEN — no extra line). Rounding is **self-correcting**: a final
settle of the residual (`F_settle = F_remaining`) removes exactly `C_remaining` (`round(C×F/F)=C`),
so repeated partials net to zero carrying with no drift.

---

# Scope

## In Scope (ledger-service only)

**Spec (finish on this branch — architecture § Increment Scope 12th is already done):**
- `specs/services/ledger-service/architecture.md` **§ FX settlement** (line ~1110–1208, the `##
  FX settlement (tenth increment — TASK-FIN-BE-016)` section, before `## Idempotency / dedupe`):
  add a `### Partial settlement (twelfth increment — TASK-FIN-BE-018)` subsection documenting the
  weighted-average proportional carrying, the residual OPEN position, the self-correcting rounding,
  the `SETTLEMENT_AMOUNT_INVALID` validation, and the full↔partial net-zero equivalence.
- `specs/contracts/http/ledger-api.md` — `POST /api/finance/ledger/settlements`: add the **optional**
  `settleForeignAmount` request field (foreign minor; omitted = full settlement, the 10th's
  behaviour) and the new `422 SETTLEMENT_AMOUNT_INVALID` error. Keep the response shape; optionally
  expose the residual (`residualForeignMinor` / `residualCarryingBaseMinor`) — additive only.

**Code:**
- `FxSettlementPolicy` — extend `settle(...)` to accept the settled foreign quantity `F_settle`
  (an overload or a new parameter; **keep the existing full-settle call working unchanged**). When
  `F_settle` is the full `F` (or the partial path is not taken), the output is byte-identical to
  today. Partial: `C_settle = round(C × |F_settle| / |F|)` (HALF_UP, signed),
  `proceedsBase = round(F_settle × settlementRate)`, `realized = proceedsBase − C_settle`,
  position-removal `JournalLine.of(money = |F_settle| {currency}, baseAmount = |C_settle| KRW)`.
  Polarity stays `sign(F)` / `sign(realized)` — `F_settle` carries the same sign as `F`. Pure
  domain (no Spring/JPA), still returns the 3-line (or 2-line when `realized == 0`) entry.
- `SettleForeignPositionCommand` — add `Long settleForeignMinor` (nullable = full settlement).
- `SettleForeignPositionUseCase` — after loading the position `(F, C)`, validate `settleForeignMinor`
  when present: **zero**, **opposite sign to `F`**, or **`|settleForeignMinor| > |F|`** (over-settle)
  → throw `SettlementAmountInvalidException` (→ 422). Null → full (`F`). Pass `F_settle` into
  `FxSettlementPolicy.settle`. Everything else (idempotency `settle:{key}`, currency≠base,
  proceeds-account-exists, `F==0` → `settled:false`, `PostJournalEntryUseCase.post`, `SETTLEMENT`
  sourceType) unchanged.
- New error `SettlementAmountInvalidException` in `LedgerErrors` → mapped to **422
  `SETTLEMENT_AMOUNT_INVALID`** in the presentation advice (mirror `SettlementRateInvalidException`).
- `SettlementRequest` DTO + `SettlementController` — accept optional `settleForeignAmount`; thread
  into the command. `SettlementResponse` — additively expose the residual if cheap (optional).

## Out of Scope

- **FIFO / lot-level cost basis** — this slice is weighted-average only (forward-declared).
- **proceeds-amount input** (deriving from a supplied actual base, not a rate), **bulk/all-positions
  hook**, **live FX rate feed**, **configurable base currency** — all still forward-declared.
- The 10th-increment full-settle behaviour, `FxRevaluationPolicy`, the auto-journal/manual paths,
  the reconciliation legs, `JournalEntry`/`PostingPolicy`/line factories, and the DB schema — **all
  unchanged** (no migration).
- `finance.ledger.entry.posted.v1` event shape (still `SETTLEMENT` sourceType, no new event).

---

# Acceptance Criteria

- [ ] AC-1 — A partial settle (`settleForeignAmount < |F|`) books a balanced 3-line entry removing
      `|F_settle|` foreign at `C_settle = round(C × |F_settle|/|F|)`, proceeds `round(F_settle×rate)`,
      realized `= proceeds − C_settle`; the **residual** `(F − F_settle, C − C_settle)` stays OPEN
      (queryable position is non-zero). A single `finance.ledger.entry.posted.v1` (SETTLEMENT) emitted.
- [ ] AC-2 — `settleForeignAmount` omitted → **byte-identical to the 10th's full settlement**
      (all FIN-BE-016 tests pass unchanged; net-zero).
- [ ] AC-3 — A final settle of the residual (`settleForeignAmount = |F_remaining|`) removes exactly
      `C_remaining` → position goes to zero (foreign 0 + base 0); repeated partials net to zero
      carrying with **no rounding drift**.
- [ ] AC-4 — Validation: `settleForeignAmount` zero, opposite sign to `F`, or `> |F|` (over-settle)
      → **422 `SETTLEMENT_AMOUNT_INVALID`**; no entry, no key consumed. Asset AND liability positions
      (both signs) behave correctly (polarity automatic).
- [ ] AC-5 — `SETTLEMENT_RATE_INVALID` / `CURRENCY_MISMATCH` / `LEDGER_ACCOUNT_NOT_FOUND` and the
      `settled:false` no-position no-op are unchanged. Idempotency `settle:{key}` replay returns the
      original entry.
- [ ] AC-6 — Specs updated: architecture § FX settlement § Partial settlement subsection +
      ledger-api.md settlement request/error. (§ Increment Scope 12th already done on this branch.)
- [ ] AC-7 — `:ledger-service:check` BUILD SUCCESSFUL (unit + slice + Testcontainers IT); CI GREEN.

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read `PROJECT.md`, load `rules/common.md` + `rules/domains/fintech.md` (double-entry / money invariants).

- `projects/finance-platform/specs/services/ledger-service/architecture.md` (§ Increment Scope 12th [done] + § FX settlement [add Partial subsection])
- `projects/finance-platform/tasks/done/TASK-FIN-BE-016-ledger-service-fx-settlement.md` (the full-settle base this extends)
- `projects/finance-platform/PROJECT.md`

# Related Contracts

- `specs/contracts/http/ledger-api.md` (`POST /api/finance/ledger/settlements` — add optional `settleForeignAmount` + 422 `SETTLEMENT_AMOUNT_INVALID`)
- `specs/contracts/events/finance-ledger-events.md` (`finance.ledger.entry.posted.v1` — reused, SETTLEMENT sourceType, unchanged)

---

# Target Service

- `ledger-service`

---

# Edge Cases

- `settleForeignAmount == |F|` → full settlement (residual exactly 0), equals the 10th's path.
- Weighted-average rounding: `C_settle = round(C × |F_settle|/|F|)`; residual carrying = `C − C_settle`. A subsequent full-residual settle removes exactly `C_remaining` (no drift).
- `realized == 0` on a partial → 2-line removal+proceeds entry (FX line omitted), still balances.
- Liability position (`F < 0`): `F_settle` must be negative (same sign); polarity follows `sign(F)`.
- Very small `|F_settle|` where `round(C × |F_settle|/|F|) == 0` → `C_settle = 0`, removal base 0; realized = proceeds (all FX) — valid (a tiny tranche realizes pure FX). Document.

# Failure Scenarios

- **F1 — over-settle**: `|settleForeignAmount| > |F|` would drive the residual negative (flip the position). → AC-4 reject 422, no entry.
- **F2 — rounding drift**: naive per-partial truncation leaves a non-zero carrying after the position's foreign goes to 0. → AC-3 self-correcting `round(C × F_settle/F)` + final-residual exactness; IT asserts zero residual after sequential partials summing to `F`.
- **F3 — net-zero regression**: the partial parameter changes the full-settle path. → AC-2 omitted-amount byte-identical; FIN-BE-016 tests unchanged.
- **F4 — sign confusion**: `settleForeignAmount` opposite sign to `F`. → AC-4 reject 422.

---

# Test Requirements

- unit: `FxSettlementPolicyTest` — partial weighted-average (gain/loss/zero), full-equivalence (omit/`==F`), asset+liability, tiny-tranche `C_settle==0`. `SettleForeignPositionUseCaseTest` — validation (zero/opposite-sign/over-settle → exception), null=full, residual computed.
- slice: `SettlementControllerSliceTest` — optional `settleForeignAmount`, 422 mapping.
- IT: `LedgerFxSettlementIntegrationTest` — partial settle leaves residual OPEN (re-query position non-zero); sequential partials summing to `F` end at zero residual (no drift); full-omit path unchanged.

---

# Definition of Done

- [ ] Implementation completed (FxSettlementPolicy partial + Command/UseCase validation + DTO/controller + SETTLEMENT_AMOUNT_INVALID)
- [ ] Tests added & passing (weighted-average, full-equivalence, residual, over-settle, drift-free)
- [ ] Specs updated (architecture § FX settlement § Partial subsection + ledger-api.md)
- [ ] net-zero regression 0 (FIN-BE-016 full-settle path unchanged)
- [ ] Ready for review
