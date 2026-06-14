# ledger-service — Architecture

This document declares the internal architecture of `finance-platform/apps/ledger-service`.
All implementation tasks targeting this service must follow this declaration,
`platform/architecture-decision-rule.md`, and the rule files indexed by
`PROJECT.md`'s declared `domain` (`fintech`) and `traits`
(`transactional`, `regulated`, `audit-heavy`).

> **Provenance**: Authored by [TASK-FIN-BE-007](../../../tasks/ready/) **before**
> implementation (HARDSTOP-09 — architecture decision precedes code).
> `ledger-service` is the **v2 double-entry ledger** deferred by
> [ADR-MONO-008](../../../../../docs/adr/ADR-MONO-008-finance-platform-bootstrap.md)
> § D3 (declared in `PROJECT.md` Service Map v2). The **first increment**
> (TASK-FIN-BE-007 — event-driven auto-journal + read) is live; the **second
> increment** (TASK-FIN-BE-008 — period close: `AccountingPeriod` lifecycle +
> posting guard + close snapshot, emission deferred) is live; the **third increment**
> (TASK-FIN-BE-009 — GL/AP feed: the transactional outbox + `finance.ledger.{entry.posted,
> period.closed}.v1` emission, terminal→publishing consumer) is specified by
> § Event publication + § Increment Scope; the **fourth increment** (TASK-FIN-BE-010
> — reconciliation matching: external-statement matching + the F8 no-auto-close
> discrepancy queue + emission) is specified by § Reconciliation + § Increment Scope;
> the **fifth increment** (TASK-FIN-BE-011 — manual journal posting: an operator-
> initiated adjusting-entry REST endpoint funnelling through the existing guarded
> write path) is specified by § Manual Journal Posting + § Increment Scope; the
> **sixth increment** (TASK-FIN-BE-012 — reconciliation period-lock: a discrepancy
> whose statement date is in a CLOSED period is immutable, `resolve` rejected with
> `RECONCILIATION_PERIOD_LOCKED`) and the **seventh increment** (TASK-FIN-BE-013 —
> reconciliation *ingest-time* period-lock: ingesting a statement dated in a CLOSED
> period is rejected with the same code, before any persist/match/emit) are specified
> by § Reconciliation § Period lock + § Increment Scope. The **eighth increment**
> (TASK-FIN-BE-014 — multi-currency journals: one entry may carry lines in different
> currencies, balanced in a fixed base/reporting currency [KRW] via per-line
> `exchangeRate` + `baseAmount`) is specified by § Multi-currency journals +
> § Increment Scope. The **ninth increment** (TASK-FIN-BE-015 — FX gain/loss revaluation:
> an operator revalues a foreign-currency position at a new closing rate, truing its base
> carrying value to spot and booking the delta to `FX_GAIN` / `FX_LOSS` via a balanced
> base-currency adjusting entry) is specified by § FX gain/loss revaluation +
> § Increment Scope. The **tenth increment** (TASK-FIN-BE-016 — realized FX gain/loss on
> settlement: an operator settles a foreign-currency position at a settlement rate, removing
> the position at its carrying value and recognising the difference between the base proceeds
> and the carrying as a *realized* `FX_GAIN` / `FX_LOSS`) is specified by § FX settlement +
> § Increment Scope. The **eleventh increment** (TASK-FIN-BE-017 — multi-currency
> reconciliation: a matched foreign statement line whose bank-reported base [KRW] value
> differs from the internal carrying base records an `AMOUNT_MISMATCH` [FX-difference]
> discrepancy for operator review) is specified by § Reconciliation § Multi-currency
> reconciliation + § Increment Scope. The **twelfth increment** (TASK-FIN-BE-018 — partial /
> weighted-average settlement: an operator settles a *portion* of a foreign position via an
> optional `settleForeignAmount`, removing a proportional `round(C×|F_settle|/|F|)` share of the
> carrying base and leaving a residual OPEN position) is specified by § FX settlement § Partial
> settlement + § Increment Scope. A FIFO / lot-level cost basis, a bulk/period-close revaluation
> hook, a live FX rate feed, foreign-external → KRW-internal cross matching (the reverse of the
> 14th increment), and fuzzy / N:M matching remain forward-declared (§ Increment Scope).
> The account-service architecture
> (`../account-service/architecture.md`) is the canonical blueprint for the
> shared infrastructure (Hexagonal, MySQL/Flyway, JWT/JWKS, tenant gate,
> idempotency, audit) — this service mirrors it.

---

## Identity

| Field | Value |
|---|---|
| Service Name | `ledger-service` |
| Project | `finance-platform` |
| Service Type | `rest-api + event-consumer` (dual-type — see Service Type Composition) |
| Architecture Style | **Hexagonal (Ports & Adapters) + DDD** (ADR-008 § D3 — harder invariants: immutable journal, balance identity) |
| Domain | fintech |
| Traits | transactional, regulated, audit-heavy |
| Primary language / stack | Java 21, Spring Boot 3.4 (Servlet stack) |
| Bounded Context | Ledger (double-entry journal + chart of accounts); downstream of account-service's single-entry wallet |
| Deployable unit | `apps/ledger-service/` |
| Data store | MySQL `finance_ledger_db` (Flyway) — separate schema from account-service `finance_db` |
| Event consumption | Kafka — `finance.transaction.completed.v1` / `finance.transaction.reversed.v1` (account-service outbox) |
| Event publication | **(3rd increment, TASK-FIN-BE-009)** `finance.ledger.entry.posted.v1` + `finance.ledger.period.closed.v1`; **(4th increment, TASK-FIN-BE-010)** `finance.ledger.reconciliation.completed.v1` + `finance.ledger.reconciliation.discrepancy.detected.v1` — all via the per-service transactional outbox (`OutboxRow` path; the generic `TopicResolver` covers new `finance.ledger.*` types). A **publishing consumer** from the 3rd increment. |
| Outbound integration | The GL/AP/ERP feed is the **emitted topics** above (the forward interface for an external accounting system); no synchronous outbound call (no in-repo consumer of the feed yet) |

### Service Type Composition

`ledger-service` is a **dual-type** service per
[`platform/service-types/INDEX.md`](../../../../../platform/service-types/INDEX.md):

- **`event-consumer`** — its *primary* trigger is **inbound** account-service
  domain events: it reacts to `finance.transaction.completed.v1` (and
  `.reversed.v1`) to auto-post the corresponding double-entry journal entry.
  This is the "도메인 이벤트 → 자동 분개" loop ADR-008 § D3 records as the v2
  ledger story. Unlike account-service (which only *publishes* as a side effect
  and therefore stays single-type `rest-api`), ledger-service is driven by
  inbound events → it IS an `event-consumer`.
- **`rest-api`** — it also exposes a synchronous **read** surface (journal
  entries, ledger-account balances, trial balance) for the platform-console
  operator read consumer (ADR-MONO-013 Model B) and future GL/AP queries.

Same composition as `erp-platform/read-model-service` (`rest-api + event-consumer`):
an event-driven projection/derivation with a read API. The architecture.md is the
authoritative Service Type decision site (HARDSTOP-09/10; CLAUDE.md Source-of-Truth:
`platform/service-types/` > PROJECT.md > task). `PROJECT.md`'s project-level
`service_types: [rest-api, event-consumer]` already covers this.

---

## Increment Scope

This service is large (full double-entry GL). The **first increment** (TASK-FIN-BE-007)
delivers the core that proves the accounting depth, deferring the rest as named
follow-ups (each its own task) — mirroring the erp `read-model-service` /
`approval-service` first-increment discipline.

**First increment — IN:**
- Double-entry domain: `JournalEntry` (a balanced set of `JournalLine`s; the
  **balance identity** `Σ debit == Σ credit` is the core invariant →
  `LEDGER_ENTRY_UNBALANCED`), `LedgerAccount` (minimal chart of accounts),
  `Money` (minor-units, no float — reused semantics).
- **Auto-journal event consumer**: subscribe `finance.transaction.completed.v1`
  (+ `.reversed.v1`); map the transaction to a balanced journal entry per the
  fixed **Posting Policy** (§ Posting Policy); idempotent (dedupe on source
  event id); immutable once posted (reversal-only, F3-style).
- **Read REST**: journal entry detail, a ledger account's entries (paginated) +
  running balance, and a **trial balance** (Σ over all accounts == 0 — a live
  demonstration of the double-entry invariant).
- Tenant gate (dual-accept), RS256/JWKS, append-only `audit_log`, Flyway, the
  full unit/slice/Testcontainers test pyramid.
- **Terminal consumer** (no outbox / no emission) — `OutboxAutoConfiguration`
  excluded, like erp read-model-service.

**Second increment — IN (TASK-FIN-BE-008, period close):**
- **`AccountingPeriod`** aggregate (OPEN→CLOSED state machine; tenant-scoped,
  non-overlapping `[from, to)` windows), a **posting-path guard** (a journal
  entry whose `postedAt` is covered by a CLOSED period is rejected with
  `LEDGER_PERIOD_CLOSED` → DLT on the consumer path; **net-zero** when no closed
  period covers it — including when no period is defined), and a **close-time
  trial-balance snapshot** (`PeriodBalanceSnapshot` — per-account + grand totals,
  in balance, == the live trial balance at close). Operator REST: open / close /
  list / detail (§ Accounting Period, § REST endpoints).
- **Decision — emission deferred / terminal consumer preserved**:
  `finance.ledger.period.closed.v1` is NOT emitted by this increment. The events
  contract sequences outbox introduction to the **GL/AP-feed** increment ("the
  service gains an outbox … until then it emits nothing"); period close lands the
  lifecycle + guard + snapshot + reads **without** an outbox, so the service stays
  a terminal consumer through this increment (the `period.closed.v1` topic stays
  forward-declared in the events contract). This mirrors the erp first-increment
  discipline — slice the depth, do not pull the outbox in early.

**Third increment — IN (TASK-FIN-BE-009, GL/AP feed — the outbox):**
- ledger-service transitions **terminal consumer → publishing consumer**: it gains
  a **transactional outbox** and emits the two forward-declared events as the
  external accounting/ERP/AP forward interface — **`finance.ledger.entry.posted.v1`**
  (appended for every posted entry in the posting `@Transactional`) +
  **`finance.ledger.period.closed.v1`** (appended on close in the close
  `@Transactional`). Atomic — the outbox row commits with the domain write (the GL
  feed can never diverge from the books). See § Event publication.
- **Decision — per-service `OutboxRow` path, NOT the libs `OutboxWriter`**: the libs
  `OutboxAutoConfiguration` (`OutboxWriter`) entity-scans the libs
  `ProcessedEventJpaEntity` (mapped to `processed_events`), which would **collide**
  with ledger-service's OWN `processed_events` consumer-dedupe table (different
  schema — the collision that made the 1st increment exclude `OutboxAutoConfiguration`).
  So this increment uses the **`AbstractOutboxPublisher` + per-service
  `LedgerOutboxJpaEntity implements OutboxRow`** path (ADR-MONO-004; wms
  inbound/inventory/outbound precedent): ledger keeps `OutboxAutoConfiguration` +
  `OutboxMetricsAutoConfiguration` **excluded** and owns a `ledger_outbox` table +
  relay. The consumer-dedupe path is untouched.

**Fourth increment — IN (TASK-FIN-BE-010, reconciliation matching):**
- The ledger reconciles its **clearing-account** entries (`CASH_CLEARING` /
  `SETTLEMENT_SUSPENSE`) against an ingested **external statement** (bank / PG
  settlement lines): 1:1 match by (amount, currency, direction); anything unmatched
  on either side → a **`ReconciliationDiscrepancy`** in an **OPEN operator review
  queue**. **F8 — no auto-close**: the system only RECORDS discrepancies (never
  auto-resolves or adjusts the difference); an operator resolves each manually.
  Emits `finance.ledger.reconciliation.completed.v1` + `.discrepancy.detected.v1`
  via the **existing FIN-BE-009 outbox** (the generic `TopicResolver` covers the new
  event types — no relay change). See § Reconciliation.

**Fifth increment — IN (TASK-FIN-BE-011, manual journal posting):**
- An **operator-initiated** adjusting-entry write endpoint — the first journal
  **mutation REST** surface (until now postings were event-driven only). A
  `POST /api/finance/ledger/entries` accepts a balanced set of operator-supplied
  lines and **funnels through the existing `PostJournalEntryUseCase.post`** — the
  single guarded write path (architecture.md § boundary rules; Architecture Style
  Rationale point 3 foresaw exactly this). The entry carries `SourceRef` type
  **`MANUAL`** (provenance), is **immutable + reversal-only** (F3, same as
  auto-journal), self-validates its balance (`LEDGER_ENTRY_UNBALANCED` /
  `CURRENCY_MISMATCH` surface **synchronously** now, not just via a future endpoint),
  is **closed-period-guarded** (a back-dated manual entry into a CLOSED period →
  `LEDGER_PERIOD_CLOSED` 422, synchronous), and is **idempotent** on a client
  `Idempotency-Key` (reuses the `processed_events` dedupe; replay returns the original
  entry). It reuses the FIN-BE-009 outbox unchanged — a manual entry emits the same
  `finance.ledger.entry.posted.v1` with `source.sourceType = "MANUAL"` (no new event).
  See § Manual Journal Posting.
- **Decision — referenced accounts must already exist (no operator-minted GL
  accounts).** Unlike the auto-journal path (which lazily creates a
  `CUSTOMER_WALLET:{accountId}` on first posting), the manual path **rejects** a line
  referencing an unknown ledger account (`LEDGER_ACCOUNT_NOT_FOUND` 404) — an
  operator adjusts existing accounts, never mints a new chart node via a posting.

**Sixth increment — IN (TASK-FIN-BE-012, reconciliation period-lock):**
- The **reconciliation analog of the posting closed-period guard** — a discrepancy
  whose **statement date** falls in a CLOSED accounting period is **immutable**: the
  operator `resolve` path is rejected with `RECONCILIATION_PERIOD_LOCKED` (422,
  mirroring `LEDGER_PERIOD_CLOSED`). A closed month's reconciliation outcomes are
  frozen with the books; a correction is recorded against the next (open) period. The
  guard reuses the EXISTING `AccountingPeriodRepository.findCovering(tenant, t,
  CLOSED)` (no new period machinery) and the discrepancy's owning statement
  (`ExternalStatement.statementDate`, a `LocalDate`, mapped to its **start-of-day UTC
  instant** for the `covers` check — the ledger is UTC throughout). **Net-zero**: no
  covering CLOSED period — the common case, and always when no period is defined, or
  the statement is absent/unknown — `resolve` proceeds byte-identically to FIN-BE-010.
  **No migration, no new aggregate** — one guard in `ResolveDiscrepancyUseCase` + one
  exception. See § Reconciliation § Period lock.
- **Scope decision — resolve-guard only (6th increment).** This increment freezes
  *resolution* of a discrepancy in a closed period (the FIN-BE-010 deferred wording: "a
  discrepancy … is immutable; correction via the next period"). It does **not** block
  *ingesting* a new statement dated in a closed period; the ingest-time lock is the 7th
  increment (below).

**Seventh increment — IN (TASK-FIN-BE-013, reconciliation ingest-time period-lock):**
- The **ingest-side** counterpart of the 6th increment's resolve lock — ingesting an
  external statement whose **statement date** falls in a CLOSED accounting period is
  rejected up-front with `RECONCILIATION_PERIOD_LOCKED` (422, same code/status as the
  resolve lock). A closed month is closed to **new** reconciliation activity, not only
  to resolving its existing discrepancies. The guard is the first thing
  `IngestStatementUseCase` does after the clearing-account validation and **before**
  any persist / match / emit — so a locked ingest records **nothing** (no statement,
  no lines, no discrepancies, no outbox events; atomic). It reuses the SAME
  `AccountingPeriodRepository.findCovering(tenant, t, CLOSED)` + the SAME
  `ReconciliationPeriodLockedException` (no new exception, no new code) and the SAME
  `LocalDate` → start-of-day-UTC-instant mapping. **Net-zero**: no covering CLOSED
  period (the common case, and always when no period is defined) → ingest proceeds
  byte-identically to FIN-BE-010. **No migration, no new aggregate** — one guard in
  `IngestStatementUseCase`. Together the 6th + 7th increments close a CLOSED period to
  reconciliation on **both** sides (ingest and resolve). See § Reconciliation § Period lock.

**Eighth increment — IN (TASK-FIN-BE-014, multi-currency journals):**
- A single journal entry may now carry lines in **different currencies**, balanced in a
  fixed **reporting / base currency (KRW)**. Each `JournalLine` keeps its original
  transaction `Money` (currency + minor units) and gains an **`exchangeRate`** (exact
  decimal to the base currency) + a **`baseAmount`** (the line's value in the base
  currency, KRW minor units). **The balance identity moves to the base currency**:
  `Σ baseDebit == Σ baseCredit` (→ `LEDGER_ENTRY_UNBALANCED`) — so cross-currency lines
  in one entry are now **allowed** (the previous blanket per-entry `CURRENCY_MISMATCH`
  rejection is removed; the base amounts are summed, all in KRW, so no mixed-currency
  arithmetic occurs). The trial balance gains a **base-currency consolidated** total
  (which balances) alongside the existing per-currency original breakdown.
- **`baseAmount` is authoritative for the balance** (supplied per line; the rate is
  recorded as provenance = `baseAmount / amount`). This avoids any "rounding breaks the
  balance" hazard — the entry balances **exactly** in integer base minor units. A
  same-base-currency (KRW) line has `rate = 1` and `baseAmount = amount`.
- **Net-zero for existing/auto-journal entries**: every existing line is KRW; the V5
  migration backfills `base_currency = currency` (KRW), `base_amount_minor = amount_minor`,
  `exchange_rate = 1`, and the base-currency balance check on an all-KRW entry is
  identical to the prior same-currency check. The auto-journal `PostingPolicy`
  (KRW transactions) produces lines with `baseAmount = money`, `rate = 1` — byte-identical
  posting. Multi-currency is exercised via **manual posting** (an operator FX adjusting
  entry) — the FIN-BE-011 path gains an optional per-line base amount; a single-currency
  manual entry is unchanged.
- See § Multi-currency journals.

**Ninth increment — IN (TASK-FIN-BE-015, FX gain/loss revaluation):**
- An **operator revalues a foreign-currency position** (`{ledgerAccountCode, currency}`) at
  a new **closing (spot) rate**. The 8th increment books multi-currency entries at the rate
  supplied **at posting time**; the spot rate then moves, so the position's **base carrying
  value** (Σ of its lines' historical `baseAmount`) drifts from its current market value.
  Revaluation **trues that carrying up to spot** (`foreignBalance × closingRate`) and books
  the difference as an **unrealized FX gain/loss** to the new GL accounts `FX_GAIN` (income)
  / `FX_LOSS` (expense). `POST /api/finance/ledger/revaluations`.
- **Decision — a balanced base-currency (KRW) adjusting entry; no JournalLine schema change,
  no migration.** The revaluation entry has two lines: a **base-carrying adjustment** on the
  foreign account (`money.amount = 0` in the foreign currency — the foreign **quantity is
  unchanged** — with a non-zero `baseAmount` = the carrying delta in KRW; a new
  `JournalLine.baseAdjustment` factory, the **only** caller that permits a zero transaction
  amount) + a contra **`FX_GAIN`/`FX_LOSS`** normal KRW line. Both balance in the base
  currency (`Σ baseDebit == Σ baseCredit`), so the existing factory + the existing
  `journal_line` columns (`amount_minor` already allows 0) carry it — **no `V6` migration**.
  The foreign account's per-`(account, currency)` row's `baseAmount` sum trues up while its
  foreign `amount` sum is untouched; a later revaluation reads the **already-revalued**
  carrying → **no double-booking**.
- **Decision — gain/loss polarity is automatic for assets AND liabilities.**
  `delta = revaluedBase − carryingBase` in **debit-positive** signed arithmetic
  (`baseDebit − baseCredit`): `delta > 0` → DR the foreign account / CR `FX_GAIN`;
  `delta < 0` → CR the foreign account / DR `FX_LOSS`. Because the foreign balance is read
  debit-positive (a liability's credit balance is negative), an appreciating asset (gain)
  and a growing liability (loss) both fall out of the sign — no account-type special-casing.
- It funnels through the existing **`PostJournalEntryUseCase.post(entry, reason, actor)`**
  (the single guarded write path — balance, closed-period guard, audit actor = operator,
  `entry.posted` outbox), is **idempotent** on a client `Idempotency-Key`
  (`reval:{key}` in `processed_events`, replay returns the original), and emits the same
  `finance.ledger.entry.posted.v1` tagged `source.sourceType = "REVALUATION"` (no new
  event). `SourceRef` gains the `REVALUATION` type. One new code `REVALUATION_RATE_INVALID`
  (422, non-positive `closingRate`); `currency` = base (KRW) or unsupported →
  `CURRENCY_MISMATCH`. **Net-zero**: `delta == 0` (already at spot) or no position in that
  currency → a `200 {revalued:false}` no-op (no entry); the auto-journal path never
  revalues. See § FX gain/loss revaluation.

**Tenth increment — IN (TASK-FIN-BE-016, realized FX gain/loss on settlement):**
- An **operator settles a foreign-currency position** (`{ledgerAccountCode, currency}`) at a
  **settlement (spot) rate**, converting it to the base currency. The 9th increment books the
  **unrealized** movement of an OPEN position; settling it **realizes** the gain/loss — the
  difference between the **base proceeds** (`foreignBalance × settlementRate`) and the
  position's **carrying base value** is booked to `FX_GAIN` (income) / `FX_LOSS` (expense),
  and the position is **removed** (its `(account, currency)` foreign + base sums go to zero).
  `POST /api/finance/ledger/settlements`.
- **Decision — a balanced base-currency 3-line entry reusing the 8th + 9th increments; no new
  line primitive, no migration.** The settlement entry has three lines:
  - a **position-removal** line on the foreign account — the existing 8th-increment
    multi-currency line `JournalLine.of(money = |F| {currency}, baseAmount = |C| KRW)`, posted
    on the side that **zeroes** the position (foreign `Σdebit − Σcredit → 0`, base → 0);
  - a **base proceeds** line on an operator-supplied `proceedsAccountCode` — an ordinary KRW
    line for `proceedsBase = round(foreignBalance × settlementRate)`; and
  - the realized **`FX_GAIN`/`FX_LOSS`** contra (9th-increment accounts) — an ordinary KRW line
    for the realized difference.
  All KRW base amounts net (`Σ baseDebit == Σ baseCredit`), so the existing `JournalEntry`
  factory accepts it — **no `V6`+ migration**, no new `JournalLine` factory.
- **Decision — polarity automatic for asset AND liability positions (debit-positive signed
  arithmetic).** `proceedsBase` and the carrying `C` are read debit-positive
  (`Σbase debit − Σbase credit`); `realized = proceedsBase − C`. The removal line's direction
  is `sign(F)` (a debit-balance asset is removed by a CREDIT, a credit-balance liability by a
  DEBIT); the proceeds line's direction is also `sign(F)` (an asset settlement brings base IN
  → DR the proceeds account; a liability settlement pays base OUT → CR it); the FX line's
  direction is `sign(realized)` (`> 0` → CR `FX_GAIN`, `< 0` → DR `FX_LOSS`). All three fall out
  of the signs — no account-type branching. (A foreign **asset** sold above carrying and a
  foreign **liability** settled below carrying both yield a gain via the same rule.)
- It funnels through the existing **`PostJournalEntryUseCase.post(entry, reason, actor)`** (the
  guarded write path — closed-period guard, audit actor = operator, `entry.posted` outbox), is
  **idempotent** on a client `Idempotency-Key` (`settle:{key}`, replay returns the original),
  and emits the same `finance.ledger.entry.posted.v1` tagged `source.sourceType = "SETTLEMENT"`
  (no new event). `SourceRef` gains the `SETTLEMENT` type. One new code `SETTLEMENT_RATE_INVALID`
  (422, non-positive `settlementRate`); `currency` = base (KRW) or unsupported →
  `CURRENCY_MISMATCH`; an unknown `ledgerAccountCode` / `proceedsAccountCode` →
  `LEDGER_ACCOUNT_NOT_FOUND` (no lazy mint — an operator settles into an existing account).
  **Net-zero**: no position in that currency (`foreignBalance == 0`) → a `200 {settled:false}`
  no-op (no entry); the auto-journal + revaluation + manual paths never settle.
- **Decision — full-position settlement only (first slice).** The whole `(account, currency)`
  position is settled in one call (it removes exactly `F` foreign at carrying `C`). A **partial**
  settlement (a specified foreign amount with a proportional / weighted-average / FIFO carrying
  basis + a residual position) is a distinct, harder mechanic → forward-declared. See § FX
  settlement.

**Eleventh increment — IN (TASK-FIN-BE-017, multi-currency reconciliation):**
- After the 8th increment a clearing account holds **multi-currency** lines (each a transaction
  `Money` + a carrying base [KRW]). A **foreign-currency external statement** already reconciles
  on the **transaction (foreign) leg** (the FIN-BE-010 matcher is currency-aware: a USD external
  line matches a USD internal line by exact USD amount — **net-zero**, no change). This increment
  adds the **base (FX) leg**: a bank reports the **base (KRW) value** it actually credited at its
  rate; when that differs from the internal line's **carrying base** (booked at the ledger's rate)
  the matcher records an **`AMOUNT_MISMATCH`** discrepancy — the realized **FX difference** — for
  **operator review** (F8 — recorded, never auto-adjusted). This is the **first activation** of
  the long-declared `AMOUNT_MISMATCH` `DiscrepancyType`.
- **Decision — reuse `AMOUNT_MISMATCH`; no CHECK migration, no new code/status/event.**
  `AMOUNT_MISMATCH` is already in the `DiscrepancyType` enum, the events `type` enum, and the V4
  `ck_recon_discrepancy_type` allow-list — so the only migration is **additive nullable columns**
  (`V6__add_reconciliation_fx.sql`: `base_amount_minor BIGINT NULL` + `base_currency VARCHAR(3)
  NULL` on `reconciliation_statement_line`). `InternalLine` gains `baseMoney` (from
  `JournalLine.baseMoney()`); `ExternalStatementLine` gains the optional `baseAmount`; the ingest
  request line gains an optional `baseAmount`. The transaction-leg match is **still recorded** (a
  matched line may also carry an FX-difference discrepancy — the settlement is identified, the
  gap is flagged).
- **Decision — exact base comparison, same-foreign-currency only (first slice).** The base-leg
  check fires only when `currency != KRW`, the external `baseAmount` is present, and it differs
  from the internal `baseMoney` (any non-zero difference → `AMOUNT_MISMATCH`). A **configurable FX
  tolerance** and **cross-currency base-leg matching** (a KRW external statement matched against
  foreign internal lines by their carrying base) are forward-declared. **Net-zero**: a KRW-only
  statement, or a foreign statement without `baseAmount`s, reconciles byte-identically to
  FIN-BE-010. See § Reconciliation § Multi-currency reconciliation.

**Twelfth increment — IN (TASK-FIN-BE-018, partial / weighted-average settlement):**
- The 10th increment settles the **whole** `(account, currency)` position; this increment settles
  a **portion**. An operator supplies an optional `settleForeignAmount` (foreign minor) and the
  position is reduced by exactly that, leaving a **residual OPEN position**. Omitting it (or
  supplying the full balance) settles the whole position **byte-identically to the 10th**
  (net-zero — the 10th's tests are unchanged). `POST /api/finance/ledger/settlements` (same path).
- **Decision — weighted-average proportional carrying; no FIFO/lot tracking, no new line
  primitive, no migration.** The settled portion's carrying base is a **proportional share** of
  the position's carrying at its **average unit cost**: `C_settle = round(C × |F_settle| / |F|)`
  (HALF_UP, signed). The 10th's 3-line entry is reused unchanged with the **partial** quantities —
  position-removal `JournalLine.of(money = |F_settle|, baseAmount = |C_settle|)`,
  `proceedsBase = round(F_settle × settlementRate)`, realized `= proceedsBase − C_settle` → the
  same `FX_GAIN`/`FX_LOSS` contra (omitted when `realized == 0`). The **residual**
  `(F − F_settle, C − C_settle)` simply remains on the account — double-entry leaves it OPEN, no
  extra line. Rounding is **self-correcting**: a final settle of the residual
  (`F_settle = F_remaining`) removes exactly `C_remaining` (`round(C × F/F) = C`), so repeated
  partials net to zero carrying with no drift.
- Polarity stays automatic (`sign(F)` / `sign(realized)`, § Tenth increment) — `F_settle` carries
  the **same sign** as `F`. Funnels through the existing `PostJournalEntryUseCase.post` (same
  guarded write path), same `SETTLEMENT` `sourceType`, same `settle:{key}` idempotency, same
  `finance.ledger.entry.posted.v1` (no new event, no new write boundary). One new code
  `SETTLEMENT_AMOUNT_INVALID` (422): `settleForeignAmount` is zero, has the **opposite sign** to
  `F`, or its magnitude **exceeds** `|F|` (over-settle). All 10th-increment errors
  (`SETTLEMENT_RATE_INVALID`, `CURRENCY_MISMATCH`, `LEDGER_ACCOUNT_NOT_FOUND`) are unchanged.
  **Net-zero**: `settleForeignAmount` omitted → the 10th's full-settle path exactly. See § FX
  settlement § Partial settlement.

**Forward-declared — OUT (each a later task):**
- Fuzzy / N:M / split matching; period **reopen**. *(A **configurable FX tolerance** — the 11th was
  an exact base comparison — is now **done**: the 13th increment, TASK-FIN-BE-020, § FX reconciliation
  tolerance. **Cross-currency base-leg matching** — a base-currency [KRW] external statement matched
  against foreign internal lines by their carrying base — is now **done**: the 14th increment,
  TASK-FIN-BE-021, § Cross-currency base-leg matching. The **foreign-external → KRW-internal** reverse
  direction is now **done** too: the 19th increment, TASK-FIN-BE-027, § Reverse cross-currency matching
  — cross-currency matching is bidirectionally symmetric.)* Per-currency-pair / per-account tolerance
  granularity stays forward-declared (v1 is per-tenant).
- **FIFO / lot-level settlement cost basis** — the 12th increment (TASK-FIN-BE-018) settles a
  *portion* of a `(account, currency)` position under a **weighted-average** carrying basis (a
  proportional share `round(C × |F_settle|/|F|)`, residual OPEN position); a **FIFO / lot-level**
  cost basis (per-lot acquisition tracking) is forward-declared. A **bulk / all-positions** revaluation + a
  **period-close auto-hook** (the 9th/10th increments act on one `(account, currency)` per
  operator call). A **live FX rate feed** (rates are caller-supplied, not fetched), a
  **proceeds-amount input** (the 10th derives proceeds from a rate; supplying the *actual*
  base received is forward-declared), and a **configurable base currency** (fixed KRW in v1) are
  also forward-declared. (Multi-currency **reconciliation** is the 11th increment — § above.)
- **Manual-posting body-hash idempotency conflict** (`IDEMPOTENCY_KEY_CONFLICT` 409
  on a same-key/different-body replay — the 5th increment is replay-safe on the key
  alone; storing the request hash for conflict detection is forward-declared) +
  a maker/checker approval workflow for manual entries.

---

## Responsibilities

`ledger-service` owns the finance-platform **double-entry general ledger**. It MUST:

- Maintain a **chart of accounts** (`LedgerAccount`): platform GL accounts
  (`CASH_CLEARING` asset, `SETTLEMENT_SUSPENSE`) + per-customer wallet liability
  accounts (`CUSTOMER_WALLET:{accountId}`), each with a normal balance side
  (DEBIT for assets, CREDIT for liabilities).
- Post **balanced double-entry journal entries** — every entry is ≥2 lines whose
  debit total equals its credit total (`LEDGER_ENTRY_UNBALANCED` rejects an
  unbalanced entry); an entry is **immutable** once posted (correction = a new
  reversing entry referencing the original, F3).
- **Auto-journal** from account-service transaction events: consume
  `finance.transaction.completed.v1` / `.reversed.v1`, map per the Posting Policy
  to a balanced entry, **idempotently** (a re-delivered event posts at most one
  entry — dedupe on the signed source event id).
- Expose a **read API**: journal entry detail, per-account entries + running
  balance, and a trial balance (Σ all accounts == 0).
- Append every posting to an immutable append-only `audit_log`
  (actor / occurred_at / before / after / reason) (F6).
- Validate IAM RS256 JWT (OAuth2 Resource Server), fail-closed on
  `tenant_id != finance` (dual-accept, § Multi-tenancy). Every table carries
  `tenant_id`.
- Represent all money as integer minor-units (`long`) + ISO-4217 currency; never
  `float`/`double` (F5).

It MUST NOT:

- Mutate account-service's balance / hold / transaction state, or write back to
  `finance_db` — ledger-service is a **downstream derivation**; the wallet
  (single-entry available/ledger balance) is account-service's authority. The
  ledger NEVER feeds back into the wallet.
- Re-implement the wallet's available/held split — that is single-entry and
  belongs to account-service (F2); the ledger records the **confirmed** balance
  movements (completed transactions) as double-entry, not the hold reservation
  lifecycle.
- Post an **unbalanced** entry, or mutate a posted entry (immutability, F3).
- Couple to external GL/ERP/bank SDKs in `domain/` or `application/` (v2 feed
  sits behind an `infrastructure/` port).
- Mutate account-service state or write back to `finance_db` when publishing the
  GL/AP feed — the emitted events (3rd increment) are a one-way downstream feed, not
  a callback into the wallet. (The 1st/2nd increments emitted nothing; the 3rd adds
  the per-service outbox — `OutboxRow` path, libs `OutboxAutoConfiguration` still
  excluded — see § Event publication.)

---

## Architecture Style Rationale

**Hexagonal + DDD** (ADR-008 § D3 specifies the `+ DDD` for the ledger's harder
invariants):

1. **The balance identity must be framework-free and exhaustively unit-tested** —
   `JournalEntry`, `JournalLine`, `LedgerAccount`, `Money`, and the Posting Policy
   are pure Java; the `Σ debit == Σ credit` invariant and each transaction-type
   mapping are provable by fast unit tests with no Spring/JPA.
2. **Posting is the single guarded write path** — every journal entry is created
   through one `application/` command that constructs a `JournalEntry` (which
   self-validates balance) and persists it atomically with its audit row; there
   is no other way to write a line (structural immutability + balance guarantee).
3. **The event source is a swappable inbound adapter** — the Kafka consumer
   translates the account-service envelope into a domain command; the domain is
   unaware of Kafka. A future manual-posting REST endpoint or a replay tool reuses
   the same command path.
4. **Testability** — domain unit (no Spring) + application unit (mock ports) +
   `@WebMvcTest` slice + Testcontainers (MySQL + real Kafka + WireMock JWKS); H2
   forbidden (parity with production MySQL).

---

## Layer Structure

Hexagonal variant — `presentation/` is the inbound web adapter, `messaging/` the
inbound event adapter, `infrastructure/` aggregates outbound adapters + config.
Root package `com.example.finance.ledger`.

```
com.example.finance.ledger/
├── LedgerServiceApplication.java          ← @SpringBootApplication (excludes OutboxAutoConfiguration — terminal consumer)
├── domain/                                ← pure Java, no framework
│   ├── account/
│   │   ├── LedgerAccount.java             ← chart-of-accounts node (code, type, normalSide)
│   │   ├── LedgerAccountCodes.java        ← code constants + typeForCode; (9th incr) + FX_GAIN (INCOME) / FX_LOSS (EXPENSE)
│   │   ├── LedgerAccountType.java         ← ASSET / LIABILITY / INCOME / EXPENSE (+ EQUITY reserved); (9th incr) INCOME/EXPENSE in use for FX_GAIN/FX_LOSS
│   │   ├── NormalSide.java                ← DEBIT / CREDIT
│   │   └── repository/LedgerAccountRepository.java   ← outbound port
│   ├── journal/
│   │   ├── JournalEntry.java              ← aggregate root; balanced invariant; immutable; sourceRef; (8th incr) balance moves to base currency (Σ baseDebit == Σ baseCredit); cross-currency lines allowed
│   │   ├── JournalLine.java               ← (ledgerAccountCode, direction DEBIT/CREDIT, Money); (8th incr) + exchangeRate(BigDecimal) + baseAmount(Money in base/KRW); base-ccy line: rate=1, baseAmount=amount; (9th incr) + baseAdjustment(currency, dir, baseAmount, spotRate) factory — zero foreign amount, non-zero base carrying delta (FX revaluation only)
│   │   ├── EntryDirection.java            ← DEBIT / CREDIT
│   │   ├── PostingPolicy.java             ← transaction-type → balanced lines (pure; § Posting Policy)
│   │   ├── FxRevaluationPolicy.java        ← (9th incr) pure: (account, currency, foreignBalanceMinor, carryingBaseMinor, closingRate) → Optional<RevaluationResult> (delta + base-adjustment + FX_GAIN/FX_LOSS lines); empty when delta==0; non-positive rate → RevaluationRateInvalidException
│   │   ├── FxSettlementPolicy.java         ← (10th incr) pure: (account, currency, F, C, settlementRate, proceedsAccount) → Optional<SettlementResult> (realized + 3 lines: position-removal[8th-incr of(money,baseAmount)] + base proceeds + FX_GAIN/FX_LOSS); empty when F==0; non-positive rate → SettlementRateInvalidException
│   │   ├── SourceRef.java                 ← (sourceType, sourceTxnId, sourceEventId) provenance; (5th incr) ofManual → TYPE_MANUAL; (9th incr) ofRevaluation → TYPE_REVALUATION; (10th incr) ofSettlement → TYPE_SETTLEMENT
│   │   └── repository/JournalRepository.java   ← (5th incr) + findBySourceEventId (manual idempotent-replay return); (9th incr) + accountTotalsForCurrency(code, currency, tenant) (one FX position's foreign balance + base carrying)
│   ├── period/                           ← (2nd increment) accounting period
│   │   ├── AccountingPeriod.java          ← aggregate; OPEN→CLOSED state machine; [from,to) covers(); non-overlap
│   │   ├── PeriodStatus.java              ← OPEN / CLOSED
│   │   ├── PeriodBalanceSnapshot.java     ← close-time per-account + grand totals (pure, immutable)
│   │   ├── PeriodAccountTotal.java        ← one account's debit/credit Money in the snapshot
│   │   └── repository/AccountingPeriodRepository.java  ← outbound port (findOverlapping/findCovering/save/findById/findAll)
│   ├── reconciliation/                   ← (4th increment) external-statement matching (F8)
│   │   ├── ExternalStatement.java         ← aggregate (statementId, ledgerAccountCode, source, statementDate, lines); (11th incr) RawLine + optional baseAmount
│   │   ├── ExternalStatementLine.java     ← (externalRef, Money, direction, valueDate, matchStatus); (11th incr) + optional baseAmount(Money KRW) [bank-reported base value, nullable]
│   │   ├── InternalLine.java              ← (journalEntryId, code, direction, Money); (11th incr) + baseMoney(Money KRW) [carrying base, from JournalLine.baseMoney()]
│   │   ├── ReconciliationMatch.java       ← statementLine ↔ internal journalEntryId
│   │   ├── ReconciliationDiscrepancy.java ← OPEN→RESOLVED (operator-only); type; resolution record (mirrors account-service placeholder)
│   │   ├── DiscrepancyType.java           ← UNMATCHED_EXTERNAL / UNMATCHED_INTERNAL / AMOUNT_MISMATCH; (11th incr) AMOUNT_MISMATCH first activated = FX/base-leg difference on a matched foreign line
│   │   ├── ReconciliationMatcher.java     ← pure: txn-leg 1:1 by (amount,currency,direction); (11th incr) + base(FX)-leg check on a match → AMOUNT_MISMATCH when ext.baseAmount ≠ internal.baseMoney (currency≠KRW)
│   │   └── repository/ReconciliationRepository.java + ReconciliationAccounts.java (clearing-account allow-list)
│   ├── money/
│   │   ├── Money.java                     ← long minorUnits + Currency (NO float/double)
│   │   ├── Currency.java                  ← ISO-4217 + minor-unit scale (KRW/USD/EUR/JPY)
│   │   └── LedgerReportingCurrency.java   ← (8th incr) BASE = KRW (fixed reporting/base currency; configurable forward-declared)
│   ├── audit/
│   │   ├── AuditLog.java
│   │   └── AuditLogRepository.java
│   └── error/                             ← domain exceptions (fintech codes)
│       (LedgerEntryUnbalancedException, LedgerAccountNotFoundException,
│        JournalEntryNotFoundException, DuplicateSourceEventException [internal — drives dedupe],
│        CurrencyMismatchException, ...;
│        (2nd incr) LedgerPeriodClosedException, AccountingPeriodNotFoundException,
│        AccountingPeriodOverlapException, AccountingPeriodAlreadyClosedException,
│        AccountingPeriodInvalidWindowException;
│        (4th incr) ReconciliationStatementNotFoundException, ReconciliationDiscrepancyNotFoundException,
│        ReconciliationAlreadyResolvedException, ReconciliationAccountInvalidException;
│        (5th incr) IdempotencyKeyRequiredException [handler guard → 400] — manual posting reuses LedgerEntryUnbalanced/CurrencyMismatch/LedgerAccountNotFound/LedgerPeriodClosed, no new domain code;
│        (6th incr) ReconciliationPeriodLockedException [→ 422];
│        (9th incr) RevaluationRateInvalidException [→ 422] — FX revaluation reuses IdempotencyKeyRequired/CurrencyMismatch/LedgerAccountNotFound/LedgerPeriodClosed otherwise;
│        (10th incr) SettlementRateInvalidException [→ 422] — FX settlement reuses CurrencyMismatch/LedgerAccountNotFound/IdempotencyKeyRequired/LedgerPeriodClosed otherwise)
├── application/                           ← use cases + outbound ports
│   ├── PostJournalEntryUseCase.java       ← @Transactional: balance-validate → (2nd incr) closed-period guard → persist entry + lines + audit → (3rd incr) append entry.posted outbox row (one Tx); (5th incr) + post(entry, reason, actor) overload (operator audit actor; the no-actor overload delegates with the auto-journal default — net-zero)
│   ├── PostFromTransactionUseCase.java    ← maps an account-service transaction envelope → PostJournalEntry (via PostingPolicy); idempotent on sourceEventId
│   ├── PostManualJournalEntryUseCase.java ← (5th incr) @Transactional operator: require Idempotency-Key → replay-return via findBySourceEventId else markProcessed(manual:{key}) → validate each referenced account EXISTS (no lazy mint) → build JournalEntry.post(SourceRef.ofManual) → PostJournalEntryUseCase.post(entry, reason, actor)
│   ├── RevalueForeignBalanceUseCase.java  ← (9th incr) @Transactional operator: require Idempotency-Key → replay-return (reval:{key}) → load (account,currency) position totals → FxRevaluationPolicy.revalue → delta==0/no-position → 200 revalued:false; else build base-adjustment + FX_GAIN/FX_LOSS lines, SourceRef.ofRevaluation, markProcessed → PostJournalEntryUseCase.post(entry, reason, actor)
│   ├── RevalueForeignBalanceCommand.java   ← (9th incr) (tenantId, operatorSubject, ledgerAccountCode, currency, closingRate, postedAt?, reference, memo, idempotencyKey)
│   ├── SettleForeignPositionUseCase.java   ← (10th incr) @Transactional operator: require Idempotency-Key → replay-return (settle:{key}) → validate currency≠KRW + proceedsAccount EXISTS (no mint) → load position → F==0 → 200 settled:false; else FxSettlementPolicy.settle → build 3-line entry, SourceRef.ofSettlement, markProcessed → PostJournalEntryUseCase.post(entry, reason, actor)
│   ├── SettleForeignPositionCommand.java   ← (10th incr) (tenantId, operatorSubject, ledgerAccountCode, currency, settlementRate, proceedsAccountCode, postedAt?, reference, memo, idempotencyKey)
│   ├── QueryLedgerUseCase.java            ← read: entry detail / per-account entries + balance / trial balance
│   ├── OpenAccountingPeriodUseCase.java   ← (2nd incr) @Transactional: non-overlap check → persist OPEN period + audit
│   ├── CloseAccountingPeriodUseCase.java  ← (2nd incr) @Transactional: require OPEN → compute snapshot (postedAt < to) → CLOSED + entryCount + snapshot + audit → (3rd incr) append period.closed outbox row
│   ├── QueryAccountingPeriodUseCase.java  ← (2nd incr) read: list periods / period detail + snapshot
│   ├── IngestStatementUseCase.java        ← (4th incr) @Transactional: validate clearing acct → persist statement+lines → match → persist matches + OPEN discrepancies + audit → append recon outbox events (no auto-close); (7th incr) + period-lock guard (statementDate in CLOSED period → RECONCILIATION_PERIOD_LOCKED, before any persist/match/emit; injects AccountingPeriodRepository); (11th incr) thread optional per-line baseAmount → ExternalStatement.RawLine; findUnmatchedInternalLines builds InternalLine.baseMoney
│   ├── ResolveDiscrepancyUseCase.java     ← (4th incr) @Transactional operator: OPEN→RESOLVED + resolution + audit; (6th incr) + period-lock guard (statement's owning period CLOSED → RECONCILIATION_PERIOD_LOCKED; injects AccountingPeriodRepository + ReconciliationRepository.findStatementById)
│   ├── QueryReconciliationUseCase.java    ← (4th incr) read: statement detail+summary / discrepancy queue / detail
│   ├── ActorContext.java
│   ├── view/ (JournalEntryView, JournalLineView, LedgerAccountBalanceView, TrialBalanceView)
│   └── port/outbound/
│       ├── ProcessedEventStore.java       ← dedupe port (processed_events, source event id)
│       ├── LedgerEventPublisher.java      ← (3rd incr) append-side port: publishEntryPosted / publishPeriodClosed; (4th incr) + publishReconciliationCompleted / publishDiscrepancyDetected (all called in-Tx)
│       └── ClockPort.java
├── infrastructure/
│   ├── persistence/jpa/                   ← Spring Data + adapters (toDomain/fromDomain)
│   │   (LedgerAccountJpaEntity/Repository/Adapter, JournalEntryJpaEntity, JournalLineJpaEntity;
│   │    (8th incr) JournalLineJpaEntity + exchange_rate/base_amount_minor/base_currency cols; AccountTotalsRow + base sums; accountTotals* queries add base SUM;
│   │    (9th incr) accountTotalsForCurrency(code, currency, tenant) → one FX position's foreign balance + base carrying (filters the existing per-(account,currency) totals; no new column);
│   │    AuditLogJpaEntity, processed_events;
│   │    (2nd incr) AccountingPeriodJpaEntity/Repository/Adapter, PeriodBalanceSnapshotJpaEntity;
│   │    (4th incr) ReconciliationStatement/Line/Match/DiscrepancyJpaEntity + Repository/Adapter)
│   │   Flyway: V1 init, V2 period, V3 outbox, V4 reconciliation, (8th incr) V5__add_multi_currency (journal_line cols + backfill KRW rate=1), (11th incr) V6__add_reconciliation_fx (reconciliation_statement_line base_amount_minor/base_currency NULL — additive, no CHECK change)
│   ├── outbox/                            ← (3rd incr) per-service transactional outbox (OutboxRow path)
│   │   ├── LedgerOutboxJpaEntity.java     ← implements OutboxRow (@Table ledger_outbox, MySQL payload TEXT)
│   │   ├── LedgerOutboxJpaRepository.java ← findPending(Pageable) + countByPublishedAtIsNull
│   │   ├── LedgerOutboxPublisher.java     ← extends AbstractOutboxPublisher; @Scheduled relay; TopicResolver finance.ledger.X→.v1
│   │   └── OutboxLedgerEventPublisher.java ← LedgerEventPublisher impl: build canonical envelope → save ledger_outbox row
│   ├── security/  (SecurityConfig, AllowedIssuersValidator, TenantClaimValidator,
│   │               ActorContextJwtAuthenticationConverter, ServiceLevelOAuth2Config)
│   └── config/ (ClockConfig, JpaConfig, KafkaConsumerConfig [also the outbox-relay KafkaTemplate],
│                ChartOfAccountsSeedConfig [(9th incr) also seeds FX_GAIN/FX_LOSS], (3rd incr) OutboxConfig [TransactionTemplate + ledger.outbox.* props])
├── messaging/                             ← inbound event adapter
│   ├── TransactionEventConsumer.java      ← @KafkaListener finance.transaction.{completed,reversed}.v1
│   │                                          group finance-ledger-v1, @RetryableTopic + DLT, manual ACK, dedupe
│   ├── TransactionEnvelope.java           ← inbound payload DTO (tolerant of unknown fields)
│   └── EnvelopeToCommandMapper.java       ← envelope → PostFromTransaction command
└── presentation/                          ← inbound web adapter
    ├── controller/LedgerController.java    ← /api/finance/ledger/** (reads)
    ├── controller/JournalController.java    ← (5th incr) POST /api/finance/ledger/entries (manual posting; Idempotency-Key header; no @Transactional — funnels to PostManualJournalEntryUseCase)
    ├── controller/RevaluationController.java ← (9th incr) POST /api/finance/ledger/revaluations (FX revaluation; Idempotency-Key header; no @Transactional — funnels to RevalueForeignBalanceUseCase; 201 revalued / 200 revalued:false)
    ├── controller/SettlementController.java ← (10th incr) POST /api/finance/ledger/settlements (FX settlement; Idempotency-Key header; no @Transactional — funnels to SettleForeignPositionUseCase; 201 settled / 200 settled:false)
    ├── controller/PeriodController.java     ← (2nd incr) /api/finance/ledger/periods/** (open/close/list/detail)
    ├── controller/ReconciliationController.java ← (4th incr) /api/finance/ledger/reconciliation/** (ingest/resolve/read)
    ├── advice/GlobalExceptionHandler.java  ← domain → HTTP envelope (fintech codes; (2nd incr) period codes; (5th incr) LEDGER_ENTRY_UNBALANCED/CURRENCY_MISMATCH/LEDGER_ACCOUNT_NOT_FOUND/LEDGER_PERIOD_CLOSED now surface synchronously + IDEMPOTENCY_KEY_REQUIRED handler guard)
    ├── dto/                                ← response DTOs (money as minor-units integer + currency)
    ├── filter/TenantClaimEnforcer.java
    └── security/PublicPaths.java
```

### Allowed / Forbidden dependencies

Same allow-list as account-service (`spring-boot-starter-{web,data-jpa,data-redis?,security,oauth2-resource-server,validation,actuator}`,
`spring-kafka`, `flyway-{core,mysql}`, `mysql-connector-j`, micrometer+otel, jackson,
shared libs `java-common`/`java-web`/`java-messaging`/`java-observability`/`java-security`).
Redis is **not** required in the first increment (no client idempotency-key surface;
dedupe is event-id based via `processed_events`). **Forbidden**: `float`/`double` in
money; writing to `finance_db` (account-service's schema); an outbox/publish path
(terminal consumer); external GL/ERP SDKs in `domain/`/`application/`.

### Boundary rules

- `domain/` MUST NOT depend on Spring (JPA annotations on entities are the single
  allowed exception; `JournalEntry`/`PostingPolicy`/`Money` are pure).
- `application/PostJournalEntryUseCase` is the **only** `@Transactional` write
  boundary; the consumer and (future) controller funnel through it. Controllers
  MUST NOT carry `@Transactional`.
- A `JournalEntry` self-validates its balance in its factory/constructor — it is
  impossible to persist an unbalanced entry (the invariant lives in the domain,
  not the DB).
- The consumer is **idempotent**: `processed_events` (source event id) is checked
  and inserted in the same Tx as the entry; a re-delivered event is a no-op.
- `presentation/controller/` MUST NOT touch JPA repositories directly — reads go
  through `QueryLedgerUseCase`.
- ledger-service MUST NOT open a connection to `finance_db` — it owns
  `finance_ledger_db` only (separate schema; downstream derivation).

---

## Chart of Accounts (first increment — minimal)

A deliberately small fixed chart proving the double-entry mechanics. Seeded at
startup (`ChartOfAccountsSeedConfig`, idempotent) or by Flyway; per-customer wallet
accounts are created lazily on first posting for that account.

| Code | Type | Normal side | Meaning |
|---|---|---|---|
| `CASH_CLEARING` | ASSET | DEBIT | platform cash / external clearing — increases when customers deposit |
| `SETTLEMENT_SUSPENSE` | ASSET | DEBIT | funds captured/settled out (e.g. a payment leaving the platform) |
| `CUSTOMER_WALLET:{accountId}` | LIABILITY | CREDIT | the platform's obligation to a customer (their wallet balance) |
| `FX_GAIN` | INCOME | CREDIT | **(9th incr)** unrealized FX revaluation gain (a foreign position's base carrying value rose to spot) |
| `FX_LOSS` | EXPENSE | DEBIT | **(9th incr)** unrealized FX revaluation loss (a foreign position's base carrying value fell to spot) |

A ledger account's **running balance** = Σ(debit lines) − Σ(credit lines); its
*natural* balance is interpreted by `normalSide` (a liability with a credit balance
is positive). EQUITY is reserved for a later increment; **(9th incr)** INCOME (`FX_GAIN`)
and EXPENSE (`FX_LOSS`) are now in use for FX revaluation (both seeded in
`ChartOfAccountsSeedConfig`; `LedgerAccountCodes.typeForCode` classifies them).

## Posting Policy (transaction-type → balanced entry)

`PostingPolicy.toEntry(transaction)` is a **pure** function mapping a completed
account-service transaction to a balanced 2-line entry. Only **ledger-balance-affecting**
transaction types post; `HOLD` / `RELEASE` change the wallet's held/available split
(single-entry, account-service) but NOT the confirmed ledger balance, so they post
**no** journal entry (documented, not silently dropped).

| Transaction type | Debit | Credit | Rationale |
|---|---|---|---|
| `TOPUP` | `CASH_CLEARING` | `CUSTOMER_WALLET:{acct}` | customer deposits → platform asset ↑, customer liability ↑ |
| `WITHDRAW` | `CUSTOMER_WALLET:{acct}` | `CASH_CLEARING` | customer withdraws → liability ↓, asset ↓ |
| `CAPTURE` | `CUSTOMER_WALLET:{acct}` | `SETTLEMENT_SUSPENSE` | held funds settle OUT of the wallet → liability ↓, settlement asset ↑ |
| `TRANSFER` | `CUSTOMER_WALLET:{from}` | `CUSTOMER_WALLET:{to}` | internal A→B → A liability ↓, B liability ↑ (cash unchanged) |
| `REVERSAL` | (the original entry's credit lines) | (the original entry's debit lines) | a compensating entry referencing the original (F3) |
| `HOLD` / `RELEASE` | — | — | no confirmed-balance change → no entry (held/available is single-entry) |

Every produced entry satisfies `Σ debit == Σ credit` by construction; the
`JournalEntry` factory re-asserts it (defense in depth → `LEDGER_ENTRY_UNBALANCED`
if a future policy bug produced an unbalanced set). The `PostingPolicy` is
**single-currency** (account-service transactions are KRW): each line is KRW, so
**(8th increment)** `baseAmount = money` and `rate = 1` — the auto-journal path is
byte-identical (net-zero) under multi-currency. Cross-currency lines in **one entry**
are allowed only via **manual posting** (§ Multi-currency journals), balanced in the
base currency; `CURRENCY_MISMATCH` no longer rejects a multi-currency entry (it remains
a guard for mixed-currency `Money` arithmetic).

## Immutability + Reversal (F3)

A posted `JournalEntry` is immutable — no UPDATE/DELETE of an entry or its lines.
A correction is a **new** `REVERSAL` entry whose lines are the original's lines with
debit/credit swapped, carrying `SourceRef.reversalOf = {originalEntryId}`. Driven by
the `finance.transaction.reversed.v1` event (which references the original
transaction; the ledger looks up the original entry by source transaction id).
Both entries are retained; the trial balance stays at zero.

## Accounting Period (second increment — TASK-FIN-BE-008)

An **`AccountingPeriod`** locks the books for a time window. It is a pure-domain
aggregate (state machine), guarded write path, and a close-time snapshot — no
outbox, no emission (§ Increment Scope decision).

**Model.** `AccountingPeriod(periodId, tenantId, [from, to), status, closedAt?,
closedBy?, entryCount?)`. The window is **half-open**: `covers(t) ⇔ from ≤ t < to`
(so consecutive periods abut at the boundary with no gap and no overlap). `status`
∈ {OPEN, CLOSED}. State machine:

- `AccountingPeriod.open(periodId, tenantId, from, to)` — factory; `from ≥ to` →
  `AccountingPeriodInvalidWindowException` (422 `ACCOUNTING_PERIOD_INVALID_WINDOW`).
  Creates an OPEN period.
- `close(closedAt, closedBy, entryCount)` — OPEN→CLOSED; a second close →
  `AccountingPeriodAlreadyClosedException` (`ACCOUNTING_PERIOD_ALREADY_CLOSED`).
  **No reopen** (forward-declared).

**Non-overlap invariant.** For one tenant no two periods' windows may overlap.
`OpenAccountingPeriodUseCase` rejects a window overlapping any existing period →
`AccountingPeriodOverlapException` (`ACCOUNTING_PERIOD_OVERLAP`). This keeps "which
period owns this entry" unambiguous (an entry's owning period is the one whose
window covers its `postedAt`).

**Posting guard (the lock).** `PostJournalEntryUseCase.post` — the single guarded
write path — consults `AccountingPeriodRepository.findCovering(tenantId,
entry.postedAt, CLOSED)`. If a CLOSED period covers the entry's `postedAt` →
`LedgerPeriodClosedException` (422 `LEDGER_PERIOD_CLOSED`). On the **consumer**
path this propagates → `@RetryableTopic` exhausts → DLT (a late/replayed/backdated
event into a closed window is a real anomaly, surfaced not swallowed; the dedupe
row is NOT written for a rejected entry). **Net-zero**: `findCovering` empty — the
common case, and always when no period is defined — posting proceeds byte-identically
to the first increment (the guard never blocks the happy path; periods are optional).

> Closing a window that includes the present is **permitted** in this increment
> (the model is the lock mechanism; a "period must have ended (`to ≤ now`)" policy
> is forward-declared). This is also what makes the guard deterministically testable
> against the real clock: close a window covering now, then a posting into it is
> rejected. In production an operator closes a *past* month, and the guard protects
> that closed month from backdated/replayed postings.

**Close-time snapshot.** `CloseAccountingPeriodUseCase` (one `@Transactional`):
load the period, require OPEN, compute a **`PeriodBalanceSnapshot`** — per-account
debit/credit totals + grand totals over entries with `postedAt < to` (tenant-scoped,
reusing the existing per-account totals query the trial balance uses) — flip
OPEN→CLOSED carrying `entryCount`, persist the immutable snapshot rows + the audit
row. The snapshot grand totals are **in balance** (Σdebit == Σcredit) and equal the
live trial balance at close; it is the period's immutable ending record (insert-only,
no UPDATE/DELETE — F3/F6 parity).

## Reconciliation (fourth increment — TASK-FIN-BE-010, F8)

The ledger reconciles its **clearing accounts** (`CASH_CLEARING`, `SETTLEMENT_SUSPENSE`
— the accounts that face an external bank / PG) against an ingested **external
statement**, classifying mismatches into an operator review queue. The governing
rule is **fintech F8 — no auto-close**: a discrepancy is RECORDED and surfaced; the
system never auto-resolves it or adjusts the difference (fund-leakage / accounting-
inconsistency risk). account-service modelled the `reconciliation_discrepancy`
placeholder (columns + policy); this increment is the first real matching.

**Model** (`domain/reconciliation/`, pure):
- `ExternalStatement` — a batch of external settlement lines for ONE clearing
  account: `(statementId, tenantId, ledgerAccountCode, source [BANK/PG/…],
  statementDate, lines)`. `ExternalStatementLine` =
  `(lineId, externalRef, Money, direction DEBIT/CREDIT vs the account, valueDate,
  description?, matchStatus UNMATCHED/MATCHED)`.
- `ReconciliationMatch` — links a matched `ExternalStatementLine` to an internal
  `journalEntryId` on the reconciled account (`Money`).
- `ReconciliationDiscrepancy` — a recorded mismatch:
  `(discrepancyId, tenantId, ledgerAccountCode, type {UNMATCHED_EXTERNAL,
  UNMATCHED_INTERNAL, AMOUNT_MISMATCH}, externalRef?, journalEntryId?,
  expectedMinor, actualMinor, currency, status {OPEN, RESOLVED},
  resolution? {resolutionType {MATCHED_MANUALLY, WRITTEN_OFF, ACCEPTED}, note,
  resolvedBy, resolvedAt}, detectedAt)` — mirrors the account-service placeholder
  columns. State machine **OPEN → RESOLVED only via the operator use case** (never
  auto). `RESOLVED` carries the resolution record (audit).

**Matching engine** (`ReconciliationMatcher`, pure): given the external lines + the
internal clearing-account ledger lines in scope, produce matches + discrepancies.
First increment = **1:1 by (amount, currency, direction)** — the **transaction**
(foreign) leg; an external line with no internal counterpart → `UNMATCHED_EXTERNAL`;
an internal entry with no external counterpart → `UNMATCHED_INTERNAL`. Deterministic
(when an amount could match multiple internal entries, the first deterministic
candidate matches; the rest stay unmatched → discrepancy → operator review —
documented, not silently merged). Exhaustively unit-tested, no Spring/JPA.
**(11th increment, TASK-FIN-BE-017)** the matcher additionally reconciles the **base
(FX) leg** of a matched foreign line — see § Multi-currency reconciliation.

**Ingest** (`IngestStatementUseCase`, one `@Transactional`): validate the account is
a reconcilable clearing account (`RECONCILIATION_ACCOUNT_INVALID` otherwise),
persist the statement + lines, run the matcher against the internal ledger lines on
that account (reuse the per-account line query, scoped to the statement window),
persist matches + **OPEN** discrepancies + audit, and append the outbox events
(`reconciliation.completed` + one `reconciliation.discrepancy.detected` per
discrepancy) in the SAME Tx (transactional outbox, FIN-BE-009). **No auto-close.**

**Resolve** (`ResolveDiscrepancyUseCase`, operator, one `@Transactional`): require
`OPEN` (`RECONCILIATION_ALREADY_RESOLVED` otherwise), **(6th increment)** require the
discrepancy's owning period be not CLOSED (§ Period lock — `RECONCILIATION_PERIOD_LOCKED`
otherwise), set RESOLVED + resolutionType + note + resolvedBy + audit. There is **no**
auto-resolve path anywhere.

### Period lock (sixth + seventh increments — TASK-FIN-BE-012 / TASK-FIN-BE-013)

The **reconciliation analog of the posting closed-period guard** (§ Accounting Period
§ Posting guard). Once an accounting period is CLOSED, the reconciliation activity
dated in that period is **frozen with the books**: a statement whose **statement date**
falls in a CLOSED period can neither be **resolved** (6th increment) nor **ingested**
(7th increment) — both are rejected with `ReconciliationPeriodLockedException`
(**422 `RECONCILIATION_PERIOD_LOCKED`**, mirroring `LEDGER_PERIOD_CLOSED`). The
correction is recorded against the next (open) period, not by mutating or adding to the
closed month's record (F8 immutability extended to the period boundary).

Both guards use the SAME `LocalDate` → **start-of-day UTC instant** mapping
(`statementDate.atStartOfDay(ZoneOffset.UTC).toInstant()` — the ledger is UTC
throughout; a statement dated any day in January maps into the
`[Jan 1 00:00Z, Feb 1 00:00Z)` period), the SAME
`accountingPeriodRepository.findCovering(tenant, thatInstant, CLOSED)` query, and the
SAME exception. **No migration, no new aggregate, no schema change** — one guard per
use case.

- **Resolve guard (6th)** — `ResolveDiscrepancyUseCase`, after loading the OPEN
  discrepancy and **before** `discrepancy.resolve(...)`: load the owning statement
  (`reconciliationRepository.findStatementById(discrepancy.statementId(), tenant)`),
  map its `statementDate`, consult `findCovering(..., CLOSED)`. Present →
  `RECONCILIATION_PERIOD_LOCKED`. **Net-zero**: `findCovering` empty (common case / no
  period defined), or the discrepancy has no `statementId` / the statement absent →
  resolve proceeds byte-identically to FIN-BE-010.
- **Ingest guard (7th)** — `IngestStatementUseCase`, **immediately after** the
  reconcilable-clearing-account check (`RECONCILIATION_ACCOUNT_INVALID`) and **before**
  any persist / match / emit: map the incoming `command.statementDate()` (the
  statement date is the input — no lookup needed), consult `findCovering(..., CLOSED)`.
  Present → `RECONCILIATION_PERIOD_LOCKED` thrown **before** the statement, lines,
  matches, discrepancies, or outbox events are written (a locked ingest records
  **nothing** — atomic). **Net-zero**: `findCovering` empty → ingest proceeds
  byte-identically to FIN-BE-010.

Together the two guards close a CLOSED period to reconciliation on **both** sides.

### Multi-currency reconciliation (eleventh increment — TASK-FIN-BE-017)

After the 8th increment a clearing account can hold lines in **multiple currencies**
(KRW + USD …), each carrying a transaction `Money` + a base-currency (KRW)
`baseAmount` (the carrying value at the booked rate). A **foreign-currency** external
statement (e.g. a USD nostro statement) already reconciles on the **transaction
(foreign) leg** — the FIN-BE-010 matcher matches by `(amount, currency, direction)`,
which is currency-aware, so a USD external line pairs with a USD internal line by
exact USD amount (net-zero — no change needed for same-currency foreign matching).

The 11th increment adds the **base (FX) leg**: a bank reports not only the foreign
amount it settled but often the **base-currency (KRW) value** it actually credited,
at **its** FX rate. When that differs from the internal line's **carrying base**
(booked at the ledger's rate), there is a realized **FX difference** — the same
settlement, valued differently. The matcher surfaces it as an **`AMOUNT_MISMATCH`**
discrepancy (the long-declared type's **first activation** — the base amounts mismatch
on an otherwise-matched line) for **operator review** (F8 — recorded, never
auto-adjusted; the operator books the FX correction via a manual entry / settlement).

**Model additions.**
- `InternalLine` gains `baseMoney` (the carrying base, `Money` in KRW) — the
  infrastructure `findUnmatchedInternalLines` builds it from `JournalLine.baseMoney()`.
- `ExternalStatementLine` gains an **optional** `baseAmount` (KRW minor units +
  `baseCurrency`) — the bank's reported base value, `NULL` when the statement does not
  carry one. Flyway `V6__add_reconciliation_fx.sql` adds
  `base_amount_minor BIGINT NULL` + `base_currency VARCHAR(3) NULL` to
  `reconciliation_statement_line` (additive + nullable — **net-zero** for existing rows;
  **no CHECK change** — `AMOUNT_MISMATCH` is already in the `ck_recon_discrepancy_type`
  allow-list).

**Matcher logic.** When an external line matches an internal line on the transaction
leg (as today), the matcher additionally checks the base leg: **iff** the line's
`currency != KRW` AND the external `baseAmount` is present AND it differs from the
internal line's `baseMoney`, it records an **`AMOUNT_MISMATCH`** discrepancy
(`expectedMinor` = the internal carrying base, `actualMinor` = the external base,
`currency` = KRW, carrying BOTH the matched `externalRef` and `journalEntryId`). **The
transaction-leg match is still recorded** — the settlement IS identified; the
discrepancy flags only the value gap. A KRW line, or a foreign line without an external
base amount, produces **no** base-leg discrepancy (net-zero). **(13th increment —
TASK-FIN-BE-020)** the exact base comparison is now gated by a per-tenant configurable
`FxTolerance` (see § FX reconciliation tolerance below); under the `EXACT` default (no
configured row) the matcher is byte-identical to this 11th-increment behaviour.

**No new error code / no new status / no new event** — `AMOUNT_MISMATCH` is an existing
`DiscrepancyType` (already in the events `type` enum + the V4 CHECK allow-list); it is
emitted on the existing `finance.ledger.reconciliation.discrepancy.detected.v1`. The
ingest request line gains an optional `baseAmount` (§ reconciliation-api.md).

**Net-zero.** A KRW-only statement, or a foreign statement whose lines omit `baseAmount`,
reconciles byte-identically to FIN-BE-010 (the base-leg check never fires). The existing
UNMATCHED_* classification, the F8 no-auto-close invariant, the period lock, and the
transaction-leg matching are all unchanged.

**Deferred** (forward-declared): fuzzy / N:M / split matching; period **reopen**. *(The **configurable
FX tolerance** is now **done** — the 13th increment below. **Cross-currency base-leg matching** — a
base-currency [KRW] external statement matched against foreign internal lines by their carrying base
— is now **done**: the 14th increment, TASK-FIN-BE-021, § Cross-currency base-leg matching below. The
**foreign-external → KRW-internal** reverse direction is now **done** too: the 19th increment,
TASK-FIN-BE-027, § Reverse cross-currency matching below.)*

### FX reconciliation tolerance (thirteenth increment — TASK-FIN-BE-020)

The 11th increment compared the base (FX) leg **exactly**: any non-zero difference between
the bank-reported base (KRW) value and the internal carrying base on an otherwise-matched
foreign line raised an `AMOUNT_MISMATCH`. Banks routinely report the base value at **their**
FX rate, a few minor units off the ledger's carrying rate; under an exact compare every such
settlement becomes an operator-review discrepancy, drowning the genuine value gaps. The 13th
increment makes the base-leg threshold a **per-tenant configurable tolerance** so within-band
FX-rounding differences match cleanly while larger gaps still flag.

**`FxTolerance` value object** (`domain/reconciliation/`, pure — NO Spring/JPA, mirroring the
`FxRevaluationPolicy` / `FxSettlementPolicy` style). Fields: `toleranceBps` (int, basis points /
万분율 of the internal carrying-base magnitude) + `absoluteFloorMinor` (long, an absolute floor in
base/KRW minor units), both `≥ 0`. `static FxTolerance EXACT = (0, 0)`. The allowed band is the
**looser** (larger) of the bps-derived term `round_half_up(|expected| × toleranceBps / 10000)`
and the floor; `isWithinTolerance(expected, actual) ⇔ |expected − actual| ≤ band` (**inclusive**
`≤`). The bps term scales with amount; the floor backstops tiny amounts. Under `EXACT` the band is
`max(0, 0) = 0`, so within ⇔ `expected == actual` — **net-zero**, byte-identical to FIN-BE-017.

**Matcher threading.** `ReconciliationMatcher.match(...)` gains an `FxTolerance tolerance`
parameter; the base-leg condition becomes `!tolerance.isWithinTolerance(internal.baseMoney,
ext.baseAmount)`. Everything else is byte-unchanged — the matcher stays **pure** (the use case
resolves the tolerance and passes it in; the matcher never reads a repository). Tolerance applies
**only** to the base (KRW) leg; the transaction (foreign) leg stays an exact `(amount, currency,
direction)` match.

**F8 invariant preserved.** A within-tolerance match **still records the transaction-leg match**
(the settlement IS identified) — tolerance suppresses only the base-leg *discrepancy*, never the
match, and it never auto-posts an FX correction or mutates a journal entry. A KRW line / base-less
foreign line never fires regardless of tolerance.

**Persistence.** Additive Flyway `V7__add_reconciliation_fx_tolerance.sql` — a **new** table
`reconciliation_fx_tolerance` (`tenant_id` PK, `tolerance_bps INT NOT NULL DEFAULT 0` CHECK `≥ 0`,
`floor_minor BIGINT NOT NULL DEFAULT 0` CHECK `≥ 0`, `updated_by` / `updated_at` audit columns).
**No** change to any existing table, **no** CHECK change (`AMOUNT_MISMATCH` stays in the V4
allow-list). **No row → `EXACT`** (the use case treats absence as the exact compare; no backfill —
net-zero for existing tenants). A domain aggregate `ReconciliationFxToleranceConfig` + repository
port + JPA adapter mirror the existing simple period/config aggregates (Hexagonal layer rules).

**Application + REST.** `IngestStatementUseCase` resolves the tenant's `FxTolerance` (repo lookup;
absent → `EXACT`) and passes it to the matcher. A `GetFxTolerance` + `SetFxTolerance` (upsert)
use-case pair; `SetFxTolerance` audits `updated_by` = the `ActorContext` identity (`actor.subject()
?? actor.tenantId()`, same rule as the journal/period mutations) and validates `bps ≥ 0` /
`floor ≥ 0` (→ `VALIDATION_ERROR`, 400). `GET` + `PUT /api/finance/ledger/reconciliation/fx-tolerance`
are tenant-scoped (`actor.tenantId()`); GET returns the EXACT default `{0, 0}` when unset; PUT
upserts (last-write-wins) + audits (§ reconciliation-api.md § 6/§ 7).

**No new error code / status / event** — within-tolerance simply does not emit; exceeds emits the
existing `AMOUNT_MISMATCH` on the existing `finance.ledger.reconciliation.discrepancy.detected.v1`;
the only new code is the platform-standard `VALIDATION_ERROR` on the config PUT. The ingest request
shape is unchanged.

### Cross-currency base-leg matching (fourteenth increment — TASK-FIN-BE-021)

The 11th/13th increments match a **foreign external** line to a **foreign internal** line on the
transaction (foreign) leg and then check/tolerate the base (KRW) leg. But a bank frequently settles
a foreign position **in the base currency (KRW)** while the ledger booked the underlying as a
**foreign** line carrying a KRW `baseMoney`. Under the same-currency matcher that KRW external line
finds no same-currency candidate → `UNMATCHED_EXTERNAL`, and the foreign internal line →
`UNMATCHED_INTERNAL` — **two spurious discrepancies for one real settlement**. This increment closes
that gap with a **cross-currency fallback**.

**Matcher fallback rule.** `ReconciliationMatcher.match(...)`: when the existing same-currency
`findCandidate` (the unchanged exact `(amount, currency, direction)` pass) returns **no** candidate
**and** the external line is **base-currency** (`ext.currency() == LedgerReportingCurrency.BASE`,
KRW), a strict second lookup `findCrossCurrencyCandidate(...)` runs — the **FIRST** not-consumed
internal line with `direction == ext.direction()` **AND** `money().currency() != KRW` (a **foreign**
line) **AND** `tolerance.isWithinTolerance(internal.baseMoney().minorUnits(), ext.amountMinor())`
(the external KRW amount is the base amount; the internal carrying base is `baseMoney`). On a hit the
foreign internal is consumed, the external line is marked `MATCHED`, and a `ReconciliationMatch`
carrying the external **KRW** `money` + the internal `journalEntryId` is recorded, flagged
**`crossCurrency = true`**. For a cross-currency match the carrying-base comparison **is** the match
key — within tolerance → a clean match with **NO** `AMOUNT_MISMATCH`; beyond tolerance → not a
candidate → the line falls through to `UNMATCHED_EXTERNAL` exactly as before.

**Precedence + determinism + net-zero.** Same-currency `findCandidate` runs **first** and is
byte-unchanged; the cross-currency pass is a strict **fallback** that fires only for a KRW external
with no KRW candidate but a carrying-base-matching foreign internal. Both passes consume candidates
in **input order** (deterministic). A **foreign** external line never enters the cross-currency pass
(the direction is **base-external → foreign-internal** only). Under `EXACT` (the default) the band is
0 ⇒ the fallback requires **exact** carrying-base equality. Every existing same-currency /
same-foreign-currency reconciliation — and the FIN-BE-017 / FIN-BE-020 base-leg behaviour — is
unaffected (net-zero). The matcher stays **pure** (it reuses the `FxTolerance` already passed in by
`IngestStatementUseCase`; it never reads a repository).

**Audit flag + persistence.** `ReconciliationMatch` gains a `boolean crossCurrency` (regulated-ledger
audit transparency — "this KRW bank line matched a foreign ledger position by carrying base");
same-currency matches set it `false`. Additive Flyway `V8__add_reconciliation_match_cross_currency.sql`
adds `cross_currency BOOLEAN NOT NULL DEFAULT FALSE` to `reconciliation_match` (additive + defaulted —
**net-zero** for existing rows; **no** other table change, **no** CHECK change). The flag is exposed
on every `matches[]` entry (the additive `crossCurrency` field, § reconciliation-api.md § 1/§ 3) via
`ReconciliationMatchView` → `StatementResponse.MatchResponse`.

**No new error code / status / event / REST.** A cross-currency match emits no discrepancy; the
ingest request shape is unchanged. **F8** preserved — the matcher only records matches/discrepancies;
it never posts or mutates a journal entry.

**Deferred** (forward-declared): the **foreign-external → KRW-internal** reverse direction was
forward-declared here and is now **done** — the 19th increment, TASK-FIN-BE-027, § Reverse
cross-currency matching below. FIFO / lot-level cost basis; fuzzy / N:M / split matching; period
**reopen**; per-currency-pair / per-account tolerance granularity remain deferred.

### Reverse cross-currency matching (nineteenth increment — TASK-FIN-BE-027)

The exact **mirror** of the 14th increment, in the opposite direction. The 14th increment paired a
**base-currency (KRW)** external line with a **foreign** internal line by carrying base. But a bank
also frequently settles a position **in a foreign currency (USD)** — reporting the bank-side base
(KRW) value on the statement line — while the ledger booked that settlement as a **base-currency
(KRW)** internal line. Under the same-currency matcher that foreign external line finds no
same-currency candidate → `UNMATCHED_EXTERNAL`, and the KRW internal line → `UNMATCHED_INTERNAL` —
**two spurious discrepancies for one real settlement**, the symmetric gap to FIN-BE-021. This
increment closes it with a second, mutually-exclusive **cross-currency fallback**.

**Matcher fallback rule.** `ReconciliationMatcher.match(...)`: when the existing same-currency
`findCandidate` returns **no** candidate, the else-branch resolves the cross candidate across **both**
directions, same-currency-first and mutually exclusive: a **base-currency** external
(`ext.currency() == LedgerReportingCurrency.BASE`, KRW) keeps using the 14th-increment
`findCrossCurrencyCandidate` (byte-unchanged); a **foreign** external (`ext.currency() != BASE`) that
carries a declared `baseAmount` runs the new `findReverseCrossCurrencyCandidate(...)` — the **FIRST**
not-consumed internal line with `direction == ext.direction()` **AND** `money().currency() == KRW` (a
**base-currency** line) **AND** `tolerance.isWithinTolerance(internal.money().minorUnits(),
ext.baseAmount().minorUnits())` (the internal KRW amount vs the external's bank-reported base). On a
hit the KRW internal is consumed, the external line is marked `MATCHED`, and a `ReconciliationMatch`
carrying the external **foreign** `money` + the internal `journalEntryId` is recorded, flagged
**`crossCurrency = true`**. As in the 14th increment the base comparison **is** the match key — within
tolerance → a clean match with **NO** `AMOUNT_MISMATCH`; beyond tolerance, or no declared
`baseAmount`, → not a candidate → the line falls through to `UNMATCHED_EXTERNAL` exactly as before.

**Precedence + determinism + net-zero.** Same-currency `findCandidate` runs **first** and is
byte-unchanged; the two cross-currency passes are strict fallbacks and **mutually exclusive** by the
external currency (KRW external → forward pass; foreign external → reverse pass). Both consume
candidates in **input order** (deterministic). Under `EXACT` (the default) the band is 0 ⇒ the
reverse fallback requires **exact** KRW-amount equality. Every existing reconciliation — same-currency,
the FIN-BE-017 base-leg `AMOUNT_MISMATCH`, the FIN-BE-020 tolerance, and the FIN-BE-021 KRW-external
fallback — is **byte-identical** (net-zero). The matcher stays **pure** (it reuses the `FxTolerance`
already passed in; it never reads a repository).

**No migration / no new error code / status / event / REST.** The reverse match reuses the existing
`reconciliation_match.cross_currency` flag (V8) and the existing external `base_amount_minor` column
(V6) — **no** schema change. A reverse cross-currency match emits no discrepancy; the ingest request
shape is unchanged. **F8** preserved — the matcher only records matches/discrepancies; it never posts
or mutates a journal entry. With this increment cross-currency matching is **bidirectionally
symmetric**.

## Manual Journal Posting (fifth increment — TASK-FIN-BE-011)

The first journal **mutation REST** surface. Until now journal entries were posted
only by the auto-journal consumer; an operator now posts an **adjusting entry**
(a correction, accrual, or write-off the event stream cannot express) directly. This
is the realization of Architecture Style Rationale point 3 ("A future manual-posting
REST endpoint … reuses the same command path") — the manual path adds **no** new
write boundary: it builds a balanced `JournalEntry` and funnels it through the
existing **`PostJournalEntryUseCase.post`** (the single guarded write path), so the
balance identity, the closed-period guard, the audit row, and the `entry.posted`
outbox append are all inherited unchanged.

**Endpoint.** `POST /api/finance/ledger/entries` — request: an optional `postedAt`
(defaults to now; a back-dated effective instant for an adjusting entry), an optional
free-text `reference` + `memo` (operator narrative, recorded as the audit reason and
the entry's `SourceRef.sourceTransactionId`), and a `lines[]` array of
`{ ledgerAccountCode, direction: DEBIT|CREDIT, money: {amount, currency} }`. Requires
a client **`Idempotency-Key`** header. `.authenticated()` + the dual-accept tenant
gate — same posture as the period/reconciliation mutations (no new scope-authority
axis; the operator caller arrives via the platform-console client). Returns `201`
with the posted entry (the `ledger-api.md` § 1 entry shape, `source.sourceType =
MANUAL`); an idempotent replay returns `200` with the original entry.

**`PostManualJournalEntryUseCase`** (one `@Transactional`):
1. **Idempotency (F1).** The key namespaces into the existing `processed_events`
   dedupe (`manual:{idempotencyKey}`). A replay (key already processed) returns the
   original entry via `JournalRepository.findBySourceEventId("manual:{key}", tenant)`
   (200, no re-post). A first request `markProcessed`s the key in the SAME Tx as the
   entry (the unique constraint makes a concurrent double-submit race-safe — the
   loser finds the key present and returns the original). Missing header →
   `IdempotencyKeyRequiredException` (400 `IDEMPOTENCY_KEY_REQUIRED`, handler guard).
2. **Referenced accounts must already exist.** Each line's `ledgerAccountCode` is
   checked with `ledgerAccountRepository.existsByCode` → `LedgerAccountNotFoundException`
   (404 `LEDGER_ACCOUNT_NOT_FOUND`) if absent. **No lazy minting** via the operator
   path (unlike the auto-journal consumer, which lazily creates a wallet account on
   first posting) — an operator adjusts the existing chart, never creates a new GL
   node by posting to it.
3. **Build + post.** Construct the lines, build `JournalEntry.post(entryId, tenant,
   postedAt, SourceRef.ofManual(reference, "manual:{key}"), lines)` — the factory
   **self-validates** the balance (`Σ debit == Σ credit` → `LEDGER_ENTRY_UNBALANCED`),
   the ≥2-line and single-currency rules (`CURRENCY_MISMATCH`) — then call
   `PostJournalEntryUseCase.post(entry, reason, operatorSubject)`. That guarded path
   re-checks the closed-period guard (a back-dated entry into a CLOSED period →
   `LedgerPeriodClosedException`, **422 synchronous** here — not the consumer's DLT
   route), writes the audit row with the **operator** as actor (the new
   `post(entry, reason, actor)` overload; the auto-journal overload keeps the
   `finance-ledger-service` default — net-zero), and appends the `entry.posted`
   outbox row.

**Immutability (F3).** A manual entry is as immutable as an auto-journal entry — no
update/delete; a correction to a manual entry is itself another manual (or reversal)
entry. The trial balance stays at zero (the factory rejects any unbalanced operator
input before it can persist).

**Emission.** A manual entry emits the **same** `finance.ledger.entry.posted.v1`
(via the FIN-BE-009 outbox, no change) with `source.sourceType = "MANUAL"` — the GL/AP
feed sees operator adjustments tagged by provenance. No new topic.

**Deferred** (forward-declared): body-hash idempotency **conflict** detection
(`IDEMPOTENCY_KEY_CONFLICT` 409 on same-key/different-body — this increment is
replay-safe on the key alone); a maker/checker **approval** workflow for manual
entries; bulk / multi-entry posting.

## Multi-currency journals (eighth increment — TASK-FIN-BE-014)

The first increment was single-currency per entry (cross-currency lines →
`CURRENCY_MISMATCH`). The 8th increment lets one entry carry lines in **different
currencies**, balanced in a fixed **reporting / base currency**.

**Base / reporting currency.** A single ledger-wide base currency — **KRW** in v1
(a `LedgerReportingCurrency.BASE` constant; a configurable base is forward-declared).
Every line's value is also expressed in this base currency, and the **double-entry
identity holds in the base currency** (`Σ baseDebit == Σ baseCredit`).

**Line model.** `JournalLine` keeps its transaction `Money` (`amountMinor` + `currency`)
and gains:
- `exchangeRate` — an **exact decimal** rate to the base currency (`baseAmount / amount`),
  stored as `DECIMAL(20,8)` (NOT a float — F5 is preserved: money stays integer minor
  units; only the *rate* is a decimal, and it is recorded for provenance, never used to
  re-derive the balance).
- `baseAmount` — the line's value in the **base currency** (KRW minor units, a `long`).
  **This is authoritative for the balance check.** A base-currency (KRW) line has
  `rate = 1` and `baseAmount = amount`.

**Balance identity (now in base currency).** `JournalEntry`'s factory sums each line's
`baseAmount` (all in KRW — single-currency arithmetic, no mismatch) and requires
`Σ baseDebit == Σ baseCredit` exactly → `LEDGER_ENTRY_UNBALANCED` otherwise. The blanket
"all lines same currency" check is **removed** (cross-currency lines are the point);
`CURRENCY_MISMATCH` remains only for genuinely mixed-currency `Money` arithmetic, which
the base-sum path never triggers. Because `baseAmount` is **supplied per line** (not
re-derived from the rate at balance time), the entry balances in **integer base minor
units** — there is no "rounding breaks the balance" hazard.

**Sources.**
- **Auto-journal** (`PostingPolicy`, account-service KRW transactions): each line is
  KRW → `baseAmount = money`, `rate = 1`. **Byte-identical** to the first increment
  (net-zero) — the policy is single-currency.
- **Manual posting** (FIN-BE-011): the request line gains an **optional base amount** for
  a foreign-currency line (`{ ledgerAccountCode, direction, money:{amount,currency},
  baseAmount?:{amount,"KRW"} }`); a base-currency line omits it (`baseAmount = amount`,
  `rate = 1`). The use case builds the lines with their base amounts; the factory
  validates the base-currency balance. This is how an operator books an FX adjusting
  entry (e.g. DR a USD clearing account, CR a KRW wallet, balanced in KRW).
- **Reversal** (F3): swaps debit/credit while preserving each line's transaction `Money`,
  `exchangeRate`, and `baseAmount` — the reversal balances in base by construction.

**Trial balance + period snapshot.** The per-account totals query gains **base-currency
sums** alongside the existing per-`(account, currency)` original sums. The trial balance
response keeps its per-currency breakdown and adds a **base-currency consolidated**
section (`grandBaseDebitTotal == grandBaseCreditTotal`, in balance). The close-time
period snapshot likewise records base totals so a multi-currency period still closes in
balance (the snapshot's grand totals are the base-currency consolidated totals).

**Persistence.** Flyway `V5__add_multi_currency.sql` adds `exchange_rate DECIMAL(20,8)
NOT NULL DEFAULT 1`, `base_amount_minor BIGINT NOT NULL`, `base_currency VARCHAR(3) NOT
NULL DEFAULT 'KRW'` to `journal_line` and **backfills existing rows**
(`base_amount_minor = amount_minor`, `base_currency = currency`, `exchange_rate = 1` —
all existing lines are KRW, so the backfill is exact and the base-balance check is
unchanged for them).

**Deferred** (forward-declared): a **live FX rate feed** (rates are caller-supplied);
**multi-currency reconciliation** (cross-currency clearing-account matching); a
**configurable base currency** (fixed KRW in v1). The **FX gain/loss revaluation** that
the 8th increment deferred is delivered by the 9th increment (§ FX gain/loss revaluation).

## FX gain/loss revaluation (ninth increment — TASK-FIN-BE-015)

The 8th increment books a multi-currency entry at the rate supplied **at posting time** and
records each line's base value; it does **not** revalue. Over time the market (spot) rate
moves, so an open **foreign-currency position**'s carrying value in the base currency drifts
from its current worth. **Revaluation** trues a position's base carrying value up to the
**closing (spot) rate**, recognising the difference as an **unrealized FX gain or loss**.

**The position.** A *position* is the lines of one ledger account in one foreign currency —
identified by `(ledgerAccountCode, currency)` where `currency ≠ KRW`. From the existing
per-`(account, currency)` totals (the trial-balance query) it has, in **debit-positive**
signed minor units:
- `foreignBalance` = `Σ debit − Σ credit` of the position's transaction `money` (foreign minor units);
- `carryingBase` = `Σ baseDebit − Σ baseCredit` of the position's `baseAmount` (KRW minor units) — its current base carrying value.

**The computation** (`FxRevaluationPolicy`, pure). Given `(foreignBalance, carryingBase,
closingRate)` where `closingRate` is the **base-minor-per-foreign-minor** spot factor:
- `revaluedBase = round(foreignBalance × closingRate)` (HALF_UP, a `long` KRW minor — the
  `closingRate` is an exact `BigDecimal`, F5: the result is integer minor units, never a float);
- `delta = revaluedBase − carryingBase` (debit-positive signed KRW).
- `delta == 0` → **no adjustment** (the position is already at spot — `Optional.empty()`).
- `closingRate ≤ 0` → `RevaluationRateInvalidException` (422 `REVALUATION_RATE_INVALID`).

**The adjusting entry** (balanced in the base currency). When `delta ≠ 0`, build a 2-line entry:

| delta | foreign-account line (base-carrying adjustment) | contra line | meaning |
|---|---|---|---|
| `> 0` | **DR** `{account}` — `money = 0 {currency}`, `baseAmount = +delta KRW` | **CR** `FX_GAIN` `delta KRW` | base carrying rose → gain |
| `< 0` | **CR** `{account}` — `money = 0 {currency}`, `baseAmount = +|delta| KRW` | **DR** `FX_LOSS` `|delta| KRW` | base carrying fell → loss |

The foreign-account line is a **base-carrying adjustment** (`JournalLine.baseAdjustment`):
its transaction `money` is **zero** in the position's foreign `currency` (the foreign
**quantity is unchanged** — a revaluation does not buy or sell currency), while its
`baseAmount` carries the KRW carrying delta. It is the **only** line factory that permits a
zero transaction amount; `exchangeRate` is recorded as the applied `closingRate` for
provenance. The contra `FX_GAIN`/`FX_LOSS` line is an ordinary positive KRW line
(`baseAmount = money`, `rate = 1`). `Σ baseDebit == Σ baseCredit` holds (both are `|delta|`),
so the **existing `JournalEntry` factory accepts it with no change**, and because both base
amounts already exist as columns, **there is no `V6` migration**.

**Polarity is automatic** for assets and liabilities. `foreignBalance` is read
debit-positive: an asset (debit balance) has `foreignBalance > 0`, a liability (credit
balance) `< 0`. An asset whose base value rises → `delta > 0` → gain; a liability whose base
value rises → `revaluedBase` more negative → `delta < 0` → loss. The sign of `delta` alone
selects gain vs loss — no account-type branching.

**No double-booking on re-revaluation.** The base-carrying adjustment lands in the position's
**own** `(account, currency)` row (it carries that foreign `currency`, just with amount 0), so
its `baseAmount` is part of the position's `carryingBase`. A later revaluation at a newer rate
reads the **already-revalued** carrying and books only the **incremental** delta. Worked
example (USD position, $100 = 10 000 USD-minor debit, first booked @ rate 13.0 → carrying
130 000 KRW):

| step | closingRate | revaluedBase | carryingBase (before) | delta | entry |
|---|---|---|---|---|---|
| reval 1 | 13.5 | 135 000 | 130 000 | +5 000 | DR CASH_CLEARING(USD,base +5 000) / CR FX_GAIN 5 000 |
| reval 2 | 14.0 | 140 000 | 135 000 | +5 000 | DR CASH_CLEARING(USD,base +5 000) / CR FX_GAIN 5 000 |
| reval 3 | 13.0 | 130 000 | 140 000 | −10 000 | CR CASH_CLEARING(USD,base +10 000) / DR FX_LOSS 10 000 |

The USD foreign balance stays 10 000 USD-minor throughout (the adjustments add 0 USD); only
the position's KRW carrying tracks spot. The trial balance's base-consolidated total stays in
balance (every revaluation entry balances in base).

**`RevalueForeignBalanceUseCase`** (one `@Transactional`, operator):
1. **Idempotency (F1).** Require a client `Idempotency-Key` (`reval:{key}` in
   `processed_events`, ≤ 50 chars → `IdempotencyKeyRequiredException` 400 otherwise). A replay
   (key processed) returns the original entry via `findBySourceEventId("reval:{key}", tenant)`
   → `200 {revalued:false, reason:"REPLAY"}` (no re-post).
2. **Load the position.** `journalRepository.accountTotalsForCurrency(account, currency, tenant)`
   (a focused read filtering the existing per-`(account,currency)` totals). No row / zero
   foreign balance → `200 {revalued:false, reason:"NO_POSITION"}` (net-zero; nothing booked,
   key NOT marked — a real position can be revalued later). `currency == KRW` or unsupported →
   `CURRENCY_MISMATCH` (422).
3. **Compute.** `FxRevaluationPolicy.revalue(...)`. `delta == 0` →
   `200 {revalued:false, reason:"AT_SPOT"}` (no entry, key not marked). `closingRate ≤ 0` →
   `REVALUATION_RATE_INVALID` (422).
4. **Post.** Build `JournalEntry.post(newId, tenant, postedAt, SourceRef.ofRevaluation(reference,
   "reval:{key}"), [adjustmentLine, contraLine])`, `markProcessed("reval:{key}")` in the SAME Tx,
   then funnel through **`PostJournalEntryUseCase.post(entry, reason, operatorSubject)`** — the
   single guarded write path: the closed-period guard (`postedAt` in a CLOSED period → 422
   `LEDGER_PERIOD_CLOSED`, synchronous), the audit row (actor = operator subject), and the
   `entry.posted` outbox append (`sourceType = "REVALUATION"`) are all inherited. `201
   {revalued:true, deltaBaseMinor, outcome:"FX_GAIN"|"FX_LOSS", entry}`.

**`FX_GAIN` (INCOME) / `FX_LOSS` (EXPENSE)** are seeded in `ChartOfAccountsSeedConfig` and
classified by `LedgerAccountCodes.typeForCode` (so the lazy-create in the guarded write path
also assigns the right type). The endpoint is `.authenticated()` + the dual-accept tenant gate
(parity with manual posting — no new scope-authority axis; the operator arrives via the
platform-console client).

**Emission.** A revaluation entry emits the **same** `finance.ledger.entry.posted.v1` (the
FIN-BE-009 outbox, unchanged) with `source.sourceType = "REVALUATION"` — the GL/AP feed sees
the unrealized FX adjustment tagged by provenance. No new topic.

**Net-zero / immutability.** The auto-journal and manual paths are untouched (no revaluation
unless the operator calls the endpoint). A revaluation entry is as **immutable** as any other
(F3) — a correction is another revaluation (a later rate) or a reversal. `closingRate` is
**caller-supplied** (a live FX rate feed is forward-declared).

**Deferred** (forward-declared): a **bulk / all-positions** revaluation + a **period-close
auto-hook** (one `(account, currency)` per call here); a **live FX rate feed**; a
**configurable base currency**. The **realized** FX gain/loss on settlement that this
increment deferred is delivered by the 10th increment (§ FX settlement).

## FX settlement (tenth increment — TASK-FIN-BE-016)

The 9th increment books the **unrealized** movement of an OPEN foreign position (its carrying
value tracks spot, the foreign quantity stays). **Settling** the position **realizes** the
gain/loss: the foreign holding is converted to the base currency and **removed**, and the
difference between the **base proceeds** and the position's **carrying base value** is
recognised as a *realized* `FX_GAIN` / `FX_LOSS`. This is the realization counterpart of
revaluation; together they cover the full FX P&L lifecycle.

**The position** (same read as revaluation). For `(ledgerAccountCode, currency)`, from the
existing per-`(account, currency)` totals, in **debit-positive** signed minor units:
`foreignBalance F = Σdebit − Σcredit` (foreign minor) and `carryingBase C = ΣbaseDebit −
ΣbaseCredit` (KRW minor — the carrying value, which already includes any prior revaluation
adjustments).

**The computation** (`FxSettlementPolicy`, pure). Given `(F, C, settlementRate,
proceedsAccountCode)` where `settlementRate` is the **base-minor-per-foreign-minor** spot
factor:
- `proceedsBase = round(F × settlementRate)` (HALF_UP, signed `long` KRW minor — the only
  decimal is the rate; F5: the result is integer minor units);
- `realized = proceedsBase − C` (debit-positive signed KRW);
- `F == 0` → **no position** to settle (`Optional.empty()` → the use case returns
  `200 {settled:false, reason:"NO_POSITION"}`);
- `settlementRate ≤ 0` → `SettlementRateInvalidException` (422 `SETTLEMENT_RATE_INVALID`).

**The settlement entry** (3 lines, balanced in base). The whole position is settled (first
slice — partial is forward-declared):

| line | side | amount | reuses |
|---|---|---|---|
| **position-removal** on `{ledgerAccountCode}` | `sign(F)>0 → CREDIT`, else `DEBIT` | `money = |F| {currency}`, `baseAmount = |C| KRW` | 8th-incr multi-currency `JournalLine.of(money, baseAmount)` |
| **base proceeds** on `{proceedsAccountCode}` | `sign(F)>0 → DEBIT`, else `CREDIT` | `|proceedsBase| KRW` | ordinary KRW line |
| **realized FX** `FX_GAIN`/`FX_LOSS` | `realized>0 → CREDIT FX_GAIN`, `<0 → DEBIT FX_LOSS` | `|realized| KRW` | 9th-incr accounts |

The removal line **zeroes** the position (`F − |F| → 0` foreign and `C − |C| → 0` base for a
debit-balance asset; the mirror for a liability), and `Σ baseDebit == Σ baseCredit` holds
(`|proceedsBase|` on one side nets `|C| + |realized|` on the other). Reuses the existing
`JournalEntry` factory + columns — **no new line primitive, no migration**.

**Polarity is automatic** for assets and liabilities — every line's direction is a sign:
the removal + proceeds directions follow `sign(F)` (a debit-balance **asset** position is
removed by a CREDIT and brings base IN → DR proceeds; a credit-balance **liability** is removed
by a DEBIT and pays base OUT → CR proceeds), and the FX direction follows `sign(realized)`
(`FX_GAIN` credit / `FX_LOSS` debit). A foreign asset sold **above** carrying and a foreign
liability settled **below** carrying both realize a gain via the same signed rule.

**Relationship to revaluation (no double-count).** `realized = proceedsBase − C` is measured
against the **carrying** `C`, which already embeds any prior revaluation. So if a position was
revalued to rate `R₁` (`C = F × R₁`, the unrealized gain already in `FX_GAIN`) and then settled
at `R₂`, only the **incremental** `F × (R₂ − R₁)` is realized — the lifetime total
`= unrealized + realized = (C − cost) + (proceeds − C) = proceeds − cost` is correct; the split
is purely timing. Settling at the carrying rate realizes 0 (all P&L was already unrealized).

**Worked example** (USD asset, `F = 10 000` USD-minor debit, carried `C = 130 000` KRW @ 13.0;
settle the whole position at `settlementRate = 13.7`, proceeds to `CASH_KRW`):

| line | dir | money | baseAmount |
|---|---|---|---|
| `CASH_KRW` proceeds | DEBIT | 137 000 KRW | 137 000 KRW |
| `CASH_CLEARING` removal | CREDIT | 10 000 USD | 130 000 KRW |
| `FX_GAIN` realized | CREDIT | 7 000 KRW | 7 000 KRW |

`proceedsBase = round(10 000 × 13.7) = 137 000`; `realized = 137 000 − 130 000 = +7 000` (gain).
After posting, the USD position on `CASH_CLEARING` is **gone** (`foreign → 0`, `base → 0`); the
137 000 KRW sits in `CASH_KRW`; the trial balance stays base-balanced.

**`SettleForeignPositionUseCase`** (one `@Transactional`, operator — mirrors
`RevalueForeignBalanceUseCase`):
1. **Idempotency (F1).** Require `Idempotency-Key` (`settle:{key}`, ≤ 50 chars →
   `IdempotencyKeyRequiredException` 400). A replay returns the original entry via
   `findBySourceEventId("settle:{key}", tenant)` → `200 {settled:false, reason:"REPLAY"}`.
2. **Validate + load.** `currency == KRW`/unsupported → `CURRENCY_MISMATCH` (422); the
   `proceedsAccountCode` must already exist (`existsByCode` → `LEDGER_ACCOUNT_NOT_FOUND` 404,
   no lazy mint — an operator settles into an existing account). Load the position via
   `accountTotalsForCurrency`; no row / `F == 0` → `200 {settled:false, reason:"NO_POSITION"}`
   (net-zero, key NOT marked).
3. **Compute.** `FxSettlementPolicy.settle(...)`. `settlementRate ≤ 0` →
   `SETTLEMENT_RATE_INVALID` (422).
4. **Post.** Build `JournalEntry.post(newId, tenant, postedAt, SourceRef.ofSettlement(reference,
   "settle:{key}"), [removal, proceeds, fx])`, `markProcessed("settle:{key}")`, then funnel
   through **`PostJournalEntryUseCase.post(entry, reason, operatorSubject)`** (closed-period
   guard → 422 `LEDGER_PERIOD_CLOSED`; audit actor = operator; `entry.posted` outbox append,
   `sourceType = "SETTLEMENT"`). `201 {settled:true, realizedBaseMinor, outcome:"FX_GAIN"|"FX_LOSS",
   proceedsBaseMinor, entry}`.

**Emission.** A settlement entry emits the **same** `finance.ledger.entry.posted.v1` (FIN-BE-009
outbox, unchanged) with `source.sourceType = "SETTLEMENT"` — the GL/AP feed sees the realized FX
result tagged by provenance. No new topic. The endpoint is `.authenticated()` + the dual-accept
tenant gate (parity with revaluation / manual posting).

**Net-zero / immutability.** The auto-journal, manual, and revaluation paths are untouched (no
settlement unless the operator calls the endpoint). A settlement entry is **immutable** (F3) — a
correction is a reversal or a re-establishing manual entry.

**Deferred** (forward-declared): a **FIFO / lot-level** carrying basis (the 12th increment is
weighted-average only); a **proceeds-amount input** (supply the *actual* base received instead of
a rate); a **bulk / all-positions** settle; a **live FX rate feed**; a **configurable base
currency**.

### Partial settlement (twelfth increment — TASK-FIN-BE-018)

The 10th increment settles the **whole** `(account, currency)` position. The twelfth lets an
operator settle a **portion** by supplying an optional **`settleForeignAmount`** (foreign minor,
`F_settle`); omitting it settles the whole position **byte-identically to the 10th** (net-zero —
the `F_settle/F` ratio collapses to 1, the 10th's tests are unchanged). It adds **no new write
boundary, no new line primitive, no migration** — the 10th's balanced base-currency 3-line entry
is reused with the partial quantities and funnelled through the same
`PostJournalEntryUseCase.post`.

**Weighted-average proportional carrying.** The settled portion removes a proportional share of
the position's carrying at its average unit cost:

- `C_settle = round(C × |F_settle| / |F|)` (HALF_UP, signed)
- `proceedsBase = round(F_settle × settlementRate)` (HALF_UP, signed)
- `realized = proceedsBase − C_settle`
- position-removal line `money = |F_settle| {currency}`, `baseAmount = |C_settle| KRW`

Polarity stays automatic (`sign(F)` for removal/proceeds, `sign(realized)` for the FX line) —
`F_settle` carries the **same sign** as `F`. When `round(C × |F_settle|/|F|) == 0` (a very small
tranche) `C_settle = 0` and `realized = proceedsBase` (a valid pure-FX realization).

**Residual OPEN position.** The remainder `(F − F_settle, C − C_settle)` simply **stays on the
account** — double-entry leaves it OPEN, no extra line. The `201` response additively exposes it
as `residualForeignMinor` / `residualCarryingBaseMinor` (both `"0"` on a full settle).

**Self-correcting rounding (no drift).** A final settle of the residual (`F_settle = F_remaining`)
removes **exactly** `C_remaining` (`round(C × F/F) = C`), so repeated partials summing to `F` net
to zero carrying with no rounding drift (F2).

**Validation (in the use case, not the policy).** After loading the position, a supplied
`settleForeignAmount` that is **zero**, the **opposite sign** to `F`, or **`|F_settle| > |F|`**
(over-settle — would flip the position) → **`SETTLEMENT_AMOUNT_INVALID`** (422); nothing persists,
the idempotency key is not consumed (F1/F4). `FxSettlementPolicy.settle(...)` delegates and trusts
the validated bounds.

### FX cost-flow method config (fifteenth increment — TASK-FIN-BE-023)

ADR-001 D1 step 1: store the operator-selected FX cost-flow method per tenant so
FIN-BE-025 can branch on it. Only two methods are supported — `WEIGHTED_AVERAGE`
(default, the existing behaviour) and `FIFO`; `LIFO` is excluded by ADR-001 D1 (IFRS
prohibition).

**Shadow / net-zero.** This increment adds **config storage and read surface only**.
`SettleForeignPositionUseCase` / `FxSettlementPolicy` are **not modified** — settlement
continues to use the weighted-average proportional carrying regardless of the stored
method. An absent row is equivalent to `WEIGHTED_AVERAGE`. FIN-BE-025 will read this
config and route to the FIFO lot-consumption path.

**`CostFlowMethod` enum** (`domain/journal/`, pure Java). Values: `WEIGHTED_AVERAGE`,
`FIFO`. `static CostFlowMethod fromString(String)` performs exact-match uppercase; an
unknown/null/blank value throws `CostFlowMethodInvalidException` (`VALIDATION_ERROR`,
400) before any persist — mirrors `FxTolerance` validation placement.

**Persistence.** Additive Flyway `V9__add_fx_cost_flow_config.sql` — a **new** table
`fx_cost_flow_config` (`tenant_id VARCHAR(64) PK`, `method VARCHAR(20) NOT NULL DEFAULT
'WEIGHTED_AVERAGE'` with `CHECK (method IN ('WEIGHTED_AVERAGE','FIFO'))`, `updated_by` /
`updated_at` audit columns). **No** change to any existing table, **no** existing CHECK
change, **no** backfill — net-zero for all existing tenants. A domain aggregate
`FxCostFlowConfig` (JPA entity) + repository port `FxCostFlowConfigRepository` +
JPA adapter — mirror `ReconciliationFxToleranceConfig` / its repository exactly.

**Application + REST.** `GetFxCostFlowConfigUseCase` (repo lookup; absent →
`FxCostFlowConfigView.weightedAverageDefault()`) + `SetFxCostFlowConfigUseCase`
(validate method via `CostFlowMethod.fromString` **before** any persist →
`VALIDATION_ERROR` 400 on unknown; upsert last-write-wins; write audit row
`FX_COST_FLOW_METHOD_SET` in the **same `@Transactional`** — regulated/audit-heavy).
`GET` + `PUT /api/finance/ledger/settlements/cost-flow-config` are tenant-scoped via
`ActorContext` (same idiom as the reconciliation fx-tolerance endpoints).

**No new error code / status / event** — `CostFlowMethodInvalidException` reuses the
platform-standard `VALIDATION_ERROR` (400) exactly like `FxToleranceInvalidException`;
no new Kafka topic or outbox row.

### FX position lots (acquisition / backfill) (sixteenth increment — TASK-FIN-BE-024)

ADR-001 D2 (lot model) + D5 (additive migration / backfill), § 3.1 step 2: materialize each
foreign-currency **acquisition** as a `fx_position_lot` row so FIN-BE-025 can walk open lots
FIFO on settlement. This increment is the **foundation** — lots are created (acquisition hook)
and backfilled (existing positions) but **nobody consumes them yet**.

**Shadow / net-zero.** Lots are **write-only** in this increment. `SettleForeignPositionUseCase`
/ `FxSettlementPolicy` / `FxRevaluationPolicy` are **not modified** — settlement continues to use
the weighted-average proportional carrying regardless of the lots (and of the cost-flow config
from the 15th increment). Every existing settlement / revaluation / reconciliation result is
byte-identical. FIN-BE-025 will read the cost-flow config and walk these lots when `FIFO` is set;
FIN-BE-026 will redistribute open-lot carrying on revaluation.

**`FxPositionLot` aggregate** (`domain/journal/`, JPA entity — the allowed domain↔framework
exception, exactly like `JournalLine` / `FxCostFlowConfig`). Columns: `lot_id VARCHAR(36) PK`
(UUID), `tenant_id`, `ledger_account_code`, `currency`, `acquired_at DATETIME(6)`, `seq BIGINT`
(the source `journal_line.id`), `original_foreign_minor` / `original_base_minor` (the acquired
quantity + its KRW cost), `remaining_foreign_minor` / `carrying_base_minor` (the still-open
portion — equal to the originals in this increment; FIN-BE-025 decrements them), `source_journal_entry_id
VARCHAR(36) NULL` (the acquiring entry; NULL for a synthetic backfill lot), `created_at`. A static
`acquire(...)` factory builds a fully-open lot (`remaining == original`, `carrying == original_base`).
Repository port `FxPositionLotRepository` (`save` + `findOpenLots(tenant, code, currency)` =
`remaining_foreign_minor > 0` ordered FIFO by `(acquired_at, seq)` — defined now for FIN-BE-025,
only `save` is exercised here) + JPA adapter — mirror `FxCostFlowConfigRepository` / its adapter.

**Acquisition hook (shadow).** A separate component `RecordFxAcquisitionLots` is invoked from
`PostJournalEntryUseCase.post(...)` **immediately after** `journalRepository.save(entry)` and
**inside the same `@Transactional`** boundary (lot creation is atomic with the entry — a
guard-rejected posting threw earlier → no lots). For each line of the saved entry it creates **one**
lot iff the line is an **acquisition** — all three hold: (1) `currency != KRW` (foreign);
(2) `amountMinor > 0` (excludes the zero-amount `baseAdjustment` revaluation line); (3) the line's
`direction` is the account's **position-increasing** side (`direction == typeForCode(code).normalSide()`
— DEBIT on an ASSET/EXPENSE account, CREDIT on a LIABILITY/INCOME/EQUITY account). The lot's `seq`
is `line.id()` (the IDENTITY id, assigned by the post-`save` flush), so lots within one position are
FIFO-ordered by `(acquired_at, seq)`. A position-**reducing** foreign line (the opposite side) creates
**no** lot — its consumption is the FIFO settlement path (FIN-BE-025). This is the known **shadow
desync** for non-settlement reductions, accepted in this increment.

**Persistence + backfill.** Additive Flyway `V10__add_fx_position_lot.sql` — a **new** table
`fx_position_lot` (InnoDB/utf8mb4; `KEY idx_fx_lot_position (tenant_id, ledger_account_code, currency,
acquired_at, seq)`; CHECK constraints `original_foreign_minor > 0`, `original_base_minor >= 0`,
`remaining_foreign_minor >= 0`, `remaining_foreign_minor <= original_foreign_minor`, `carrying_base_minor
>= 0`). **No** change to any existing table / row / CHECK. The same migration **backfills** every open
pre-existing foreign position as **one synthetic lot**: group `journal_line` by `(tenant_id,
ledger_account_code, currency)` where `currency <> 'KRW'`, signed foreign sum (`DEBIT +amount`,
`CREDIT -amount`) `<> 0` (`HAVING`); the synthetic lot's `original_foreign = remaining_foreign =
ABS(Σ signed amount)`, `original_base = carrying_base = ABS(Σ signed base)` — **exactly** the position's
current pool carrying (D5: zero double-count), `acquired_at = MIN(posted_at)`, `seq = MIN(journal_line.id)`,
`lot_id = UUID()`, `source_journal_entry_id = NULL`. A fresh CI / test DB has no pre-V10 lines → the
backfill is a **no-op**; it exists for real deployments carrying open positions at migration time.

**No new error code / status / event / contract** — lots are domain-internal persisted state; the
`entry.posted` outbox payload is unchanged (lots are a separate table, not exposed on the event).

### FIFO settlement consumption (seventeenth increment — TASK-FIN-BE-025)

ADR-001 D3 (§ 2 D3 + § 3.1 step 3): when a tenant's cost-flow method is `FIFO`, a settlement
derives the settled carrying `C_settle` by **consuming the open lots oldest-first** (lot-exact)
instead of the weighted-average pool share. `WEIGHTED_AVERAGE` (and unset — absence ⇒
`WEIGHTED_AVERAGE`) stays **byte-identical** to the 12th increment (net-zero); only the carrying
basis differs, never the entry shape.

**`FxSettlementPolicy` (pure) — core extraction.** A shared private `settleCore(F, F_settle,
C_settle, rate, …)` builds `proceedsBase = round(F_settle × rate)`, `realized = proceedsBase −
C_settle`, and the same balanced 3-line (or 2-line when `realized == 0`) base-currency entry — the
carrying-removal line carries `money = |F_settle| {currency}`, `baseAmount = |C_settle| KRW`. The
existing weighted-average `settle(...)` overload computes `C_settle = round(C × |F_settle|/|F|)`
then calls the core (output unchanged). A **new** public `settleWithCarrying(…, long
carryingSettledMinor, …)` takes a **pre-computed** `C_settle` and calls the SAME core — the FIFO
path supplies it. The policy stays pure (no repository / no Spring); the lot walk lives in the use
case (it needs the repository).

**`SettleForeignPositionUseCase` — the FIFO branch.** After **all** the existing guards (idempotent
replay, base-currency reject, proceeds-account-exists, no-position / zero-foreign no-op, and the
sign / zero / over-settle `resolveSettleForeignMinor` bound → `SETTLEMENT_AMOUNT_INVALID`) — so FIFO
and weighted-average share the identical guard surface — it resolves the method via
`FxCostFlowConfigRepository.findByTenantId(tenant)` (absent ⇒ `WEIGHTED_AVERAGE`):

- **`WEIGHTED_AVERAGE` / unset** → the pre-existing `FxSettlementPolicy.settle(…)` path (byte-identical).
- **`FIFO`** → load `FxPositionLotRepository.findOpenLots(tenant, code, currency)` (`remaining > 0`,
  ordered `(acquired_at, seq)` ASC) and **walk** `needed = |F_settle|` oldest-first: per lot
  `consume = min(lot.remaining, needed)`, `slice = round(lot.carrying × consume / lot.remaining,
  HALF_UP)` (when a lot is fully consumed `consume == lot.remaining` ⇒ `slice = lot.carrying`
  exactly — no drift, the per-lot analogue of the weighted-average self-correction);
  `C_settle_fifo += slice`; `needed -= consume`; `lot.consume(consume, slice)`. The consumed lots'
  decremented `remaining/carrying` are **saved in the same `@Transactional`** (atomic with the
  entry), then `FxSettlementPolicy.settleWithCarrying(…, C_settle_fifo, …)` builds the entry. The
  residual `(F − F_settle, C − C_settle_fifo)` is the use case's existing subtraction — lot-exact
  automatically because `settleWithCarrying` returns `carryingSettledMinor == C_settle_fifo`.

**Safe fallback (invariant).** The walk is computed **first** (no persistence) and lots are saved
**only after** `needed` reaches 0. When the open lots are absent or short
(`Σ remaining < |F_settle|` — e.g. a non-settlement reduction's shadow desync, or a pre-lot
position) the walk returns a shortfall signal: the use case **discards** the in-memory lot mutations
(persists none) and **falls back to the weighted-average** carrying (no net-non-zero — the
settlement always books), logging `FX_FIFO_LOT_SHORTFALL`.

**D4 boundary.** FIFO carrying is exact only when `Σ (open-lot carrying) == position carrying C` —
true after acquisition + backfill with **no interleaved revaluation**. Revaluation mutates `C` but
not the lots, so a revalue-then-FIFO-settle would diverge; redistributing the revaluation delta
across open lots (keeping the invariant) is **FIN-BE-026** (delivered below — § Revaluation lot
carrying distribution). The 17th-increment IT therefore asserts lot-exactness only on
non-revaluation scenarios; the 18th-increment IT closes the revaluation-touched case.

**No new error code / status / event / contract** — the settlement entry + `entry.posted` outbox
payload shape are unchanged (only the internally-derived `C_settle` differs); lot consumption is
domain-internal persisted state.

### Revaluation lot carrying distribution (eighteenth increment — TASK-FIN-BE-026)

ADR-001 **D4(D4-a)** + § 3.1 step 4 — the task that **closes the D4 double-count hazard** the 17th
increment flagged. FX revaluation (9th increment) trues the **aggregate** position carrying to spot
(`revaluedBase = C + delta`) but leaves the open lots at their acquisition carrying. A subsequent
FIFO settlement (17th increment) would then consume the *stale* lot carrying — `Σ (open-lot
carrying) ≠ revaluedBase` → double-count / understatement. This increment re-marks every open lot's
carrying to spot **immediately after** the revaluation post so the invariant `Σ (open-lot carrying)
== revaluedBase` holds again, making FIFO settlement lot-exact independent of revaluation history.

**Where.** Inside `RevalueForeignBalanceUseCase.revalue(...)`, in the **same `@Transactional`**,
**after** `postJournalEntryUseCase.post(...)` succeeds (atomic with the revaluation entry). Applied
on the success path **only** — the no-op `REPLAY` / `NO_POSITION` / `AT_SPOT` returns (no `delta`)
never reach the distribution. `FxRevaluationPolicy`, the 2-line revaluation entry, the signed
`delta`, the aggregate carrying, the `reval:{key}` idempotency, and the closed-period guard are all
**byte-unchanged** — only `fx_position_lot.carrying_base_minor` values are updated.

**Mark-to-spot + last-lot residual.** Load `FxPositionLotRepository.findOpenLots(tenant, code,
currency)` (`remaining > 0`, `(acquired_at, seq)` ASC). The new aggregate carrying magnitude is
`|revaluedBase| = |C + delta|`. For **every lot but the last**, `newCarrying = round(lot.remaining ×
closingRate, HALF_UP)` (the lot's own foreign-at-spot value, magnitude). The **last lot absorbs the
rounding residual**: `last.newCarrying = |revaluedBase| − Σ(prior newCarrying)` — forcing `Σ
(open-lot carrying) == |revaluedBase|` **exactly** (a single lot therefore receives `|revaluedBase|`
exactly). `remaining_foreign_minor` is untouched (a revaluation removes no foreign quantity); a new
`FxPositionLot.markCarrying(newCarrying)` mutator sets the carrying (guarded `>= 0`, mirroring the
`ck_fx_lot_carrying_base_nonneg` CHECK) and the lots are saved in the same Tx. The arithmetic is an
extracted pure helper `markToSpot(openLots, closingRate, revaluedBase)` (unit-covered).

**Non-negativity.** Each non-last `newCarrying = round(remaining × closingRate)` is non-negative
(`closingRate > 0`, `remaining >= 0`); a loss revaluation (`delta < 0`) merely yields smaller
magnitudes, never negative. The last-lot residual is **clamped at 0** for an extreme shadow-desync
where the prior lots already overshoot `|revaluedBase|` — a documented edge a normal position never
hits (it keeps the CHECK satisfied).

**Always-apply / net-zero.** The distribution runs **regardless of the tenant's cost-flow config**
(lots are always created at acquisition, so they are kept universally consistent — no branch on the
config). It is **net-zero for non-FIFO tenants**: weighted-average settlement derives `C_settle`
from the **aggregate** carrying (`accountTotalsForCurrency`), **not** the lots — so re-marking
`lot.carrying` does not change weighted-average settlement results (`WEIGHTED_AVERAGE` / unset stay
byte-identical). Only the FIFO path reads the lots, where the re-mark is exactly the D4 fix.

**Edge cases.** No open lots → distribution **skips** (the aggregate revaluation is already posted;
weighted-average settlement unaffected). Shadow desync (`Σ remaining ≠ |F|`) → the last-lot
absorption still forces `Σ carrying == revaluedBase` (compatible with the 17th increment's safe
fallback), though an individual lot may then differ from its own `foreign × spot` (a known
constraint).

**No new error code / status / event / contract / migration** — code-only; only existing
`fx_position_lot.carrying_base_minor` values are updated. The revaluation entry + `entry.posted`
outbox payload shape are unchanged.

### FX position lots read endpoint (twentieth increment — TASK-FIN-BE-028)

ADR-001 § 3.1 deferred("lot 콘솔 drill-in") backend surface. Exposes the FIFO/lot state
built by the 16th–18th increments (acquisition, FIFO consumption, revaluation mark-to-spot)
as a **read-only** `GET` on the existing `SettlementController`. Pure read; net-zero;
**no migration**.

**Endpoint.** `GET /api/finance/ledger/settlements/{ledgerAccountCode}/{currency}/lots`
(20th increment; `SettlementController` handler `getPositionLots`). Returns the tenant's
**open lots** (`remaining_foreign_minor > 0`) for the given `(ledgerAccountCode, currency)`
position, ordered `(acquired_at, seq)` ASC (the FIFO walk order, deterministic tiebreak),
plus a summary. Tenant-scoped via `ActorContext` (the same `ActorContextResolver.currentOrThrow()`
pattern). An unknown `currency` string (outside `{KRW,USD,EUR,JPY}`) returns `400
VALIDATION_ERROR` (client input error, not domain mismatch — wrapped before delegation
to distinguish from the 422 `CURRENCY_MISMATCH` used by write paths). An empty position
returns `200` with an empty list and zero-summary — not `404` (net-zero, AC-3).

**Response shape (F5 wire form).** All four monetary fields
(`originalForeignMinor`, `remainingForeignMinor`, `originalBaseMinor`, `carryingBaseMinor`)
and the summary totals (`totalRemainingForeignMinor`, `totalCarryingBaseMinor`) are
serialised as **strings** (F5 — `long` → `String`, consistent with all other money wire
forms in this service). `acquiredAt` is an ISO-8601 instant. `lotCount` is an integer.

```json
{ "data": {
    "lots": [
      { "lotId": "...", "currency": "USD", "acquiredAt": "2026-01-01T00:00:00Z", "seq": 1,
        "originalForeignMinor": "1000", "remainingForeignMinor": "1000",
        "originalBaseMinor": "1300000", "carryingBaseMinor": "1300000",
        "sourceJournalEntryId": "..." },
      { ... }
    ],
    "totalRemainingForeignMinor": "1500",
    "totalCarryingBaseMinor": "2000000",
    "lotCount": 2
  }, "meta": { "timestamp": "..." } }
```

**Application layer.** `GetFxPositionLotsUseCase` (`@Transactional(readOnly=true)`) calls
`FxPositionLotRepository.findOpenLots(tenantId, ledgerAccountCode, currency)` (existing port;
no new method) and projects the result into `FxPositionLotsView` (+ per-lot `FxPositionLotView`).
The presentation layer maps to `FxPositionLotsResponse` / `FxPositionLotResponse` (string minor
units). No write path is touched.

**No new error code / event / migration.** Pure read; `VALIDATION_ERROR` (400) for unknown
currency reuses the existing error code (same as cost-flow-config PUT). The `CURRENCY_MISMATCH`
(422) path is NOT used here — an unknown path variable is a client input parse failure, not a
domain currency-mismatch. Existing ITs stay GREEN (net-zero).

## Idempotency / dedupe (F1)

The consumer dedupes on the **signed source event id** (the envelope's `eventId`):
`ProcessedEventStore.markProcessed(eventId)` is a unique-constrained insert in the
same `@Transactional` boundary as the entry + audit row. A re-delivered event (Kafka
at-least-once) finds the id present → no-op (at-most-once posting). There is no
client `Idempotency-Key` surface in the first increment (the input is events, not
REST mutations); the reads are side-effect-free.

## Multi-tenancy / Security / Audit

Mirrors account-service exactly (single source of truth = the blueprint):
- **Dual-accept tenant gate** — `TenantClaimValidator.isEntitled(jwt, "finance")`
  (legacy `tenant_id ∈ {finance,*}` ∪ signed `entitled_domains ∋ finance`),
  applied at JWT decode AND the `TenantClaimEnforcer` filter (same helper), 403
  `TENANT_FORBIDDEN` when both fail; production net-zero while `entitled_domains`
  is absent. Every table carries `tenant_id VARCHAR(64) NOT NULL`; the consumer
  derives `tenant_id` from the event envelope (always `finance` in v1).
- **JWT (RS256)** via `oauth2-resource-server` against `${OIDC_ISSUER_URL}/oauth2/jwks`;
  `AllowedIssuersValidator` + `TenantClaimValidator`. The `finance.read` scope (IAM
  `finance-platform-internal-services-client`) is the v1 read caller; the
  platform-console operator read consumer (ADR-MONO-013 Model B) reads via its own
  console client.
- **audit_log** append-only (no UPDATE/DELETE), written in the posting Tx: records
  each journal entry posting (`actor` = `"finance-ledger-service"` for auto-journal,
  `occurred_at`, `after` = entry summary, `reason` = source transaction id) (F6).
- **Public paths**: `/actuator/{health,info,prometheus}` only.

## REST endpoints (first increment)

All under `/api/finance/ledger/**`. Formal shapes → [`ledger-api.md`](../../contracts/http/ledger-api.md).

| Method | Path | Auth | Use case |
|---|---|---|---|
| `GET` | `/api/finance/ledger/entries/{entryId}` | JWT (`finance.read`) | journal entry detail (lines) |
| `GET` | `/api/finance/ledger/accounts/{ledgerAccountCode}/entries` | JWT | paginated lines for a ledger account |
| `GET` | `/api/finance/ledger/accounts/{ledgerAccountCode}/balance` | JWT | the account's running balance (Σdebit − Σcredit) |
| `GET` | `/api/finance/ledger/trial-balance` | JWT | Σ over all accounts (== 0 invariant) |
| `POST` | `/api/finance/ledger/entries` | JWT (authenticated) + `Idempotency-Key` | **(5th increment)** post a manual adjusting entry (balanced lines → guarded write path) |
| `POST` | `/api/finance/ledger/revaluations` | JWT (authenticated) + `Idempotency-Key` | **(9th increment)** revalue a foreign-currency position at a closing rate → FX gain/loss adjusting entry (or 200 no-op) |
| `POST` | `/api/finance/ledger/settlements` | JWT (authenticated) + `Idempotency-Key` | **(10th increment)** settle a foreign-currency position at a settlement rate → realized FX gain/loss + base proceeds, position removed (or 200 no-op) |
| `POST` | `/api/finance/ledger/periods` | JWT (authenticated) | **(2nd increment)** open an accounting period `{from, to}` |
| `POST` | `/api/finance/ledger/periods/{periodId}/close` | JWT (authenticated) | **(2nd increment)** close a period → capture the trial-balance snapshot |
| `GET` | `/api/finance/ledger/periods` | JWT | **(2nd increment)** list accounting periods |
| `GET` | `/api/finance/ledger/periods/{periodId}` | JWT | **(2nd increment)** period detail + its balance snapshot |
| `POST` | `/api/finance/ledger/reconciliation/statements` | JWT (authenticated) | **(4th increment)** ingest an external statement → match + record discrepancies |
| `POST` | `/api/finance/ledger/reconciliation/discrepancies/{id}/resolve` | JWT (authenticated) | **(4th increment)** operator resolve a discrepancy (OPEN→RESOLVED) |
| `GET` | `/api/finance/ledger/reconciliation/statements/{id}` | JWT | **(4th increment)** statement detail + match/discrepancy summary |
| `GET` | `/api/finance/ledger/reconciliation/discrepancies` | JWT | **(4th increment)** discrepancy review queue (`?status=OPEN`) |
| `GET` | `/api/finance/ledger/reconciliation/discrepancies/{id}` | JWT | **(4th increment)** discrepancy detail |
| `GET` | `/actuator/{health,info}` | none | probes |
| `GET` | `/actuator/prometheus` | network-isolated | metrics |

Auto-journal postings remain event-driven; the **(5th increment)** manual posting
endpoint (`POST /entries`) is the first journal **mutation** REST surface, funnelling
through the same guarded write path (§ Manual Journal Posting). The
**period mutations** (open/close) are the 2nd increment's only write endpoints —
`.authenticated()` + the dual-accept tenant gate (parity with the service's
current posture; no new scope-authority axis — the operator caller arrives via the
platform-console client). The `{ledgerAccountCode}` path segment carries the
`CUSTOMER_WALLET:{accountId}` form url-encoded; reads are tenant-scoped.

## Event consumption

| Topic | Trigger | Action |
|---|---|---|
| `finance.transaction.completed.v1` | account-service transaction COMPLETED | post the policy entry (TOPUP/WITHDRAW/CAPTURE/TRANSFER → balanced lines); HOLD/RELEASE → no entry |
| `finance.transaction.reversed.v1` | account-service operator reversal | post a REVERSAL entry referencing the original (debit/credit swapped) |

Consumer group `finance-ledger-v1`; `@RetryableTopic` (3 retries → DLT); manual ACK;
malformed / unmappable envelope → DLT (not poison-looped); dedupe via
`processed_events`. Payload shapes →
[`finance-ledger-events.md`](../../contracts/events/finance-ledger-events.md).

## Event publication (third increment — TASK-FIN-BE-009: the GL/AP feed)

From the 3rd increment ledger-service is a **publishing consumer** — it emits two
events as the forward interface for an external accounting/ERP/AP system, via a
**per-service transactional outbox** (NOT a synchronous publish).

| Topic | Appended when | Payload (in the canonical envelope) |
|---|---|---|
| `finance.ledger.entry.posted.v1` | every posted `JournalEntry` (auto-journal + reversal), in `PostJournalEntryUseCase.post`'s `@Transactional` | `{ entryId, postedAt, lines:[{ledgerAccountCode, direction, money}], source:{sourceType, sourceTransactionId, sourceEventId}, reversalOfEntryId? }` |
| `finance.ledger.period.closed.v1` | a period closes, in `CloseAccountingPeriodUseCase.close`'s `@Transactional` | `{ periodId, from, to, closedAt, entryCount }` |
| `finance.ledger.reconciliation.completed.v1` | **(4th increment)** a statement is ingested + matched, in `IngestStatementUseCase`'s `@Transactional` | `{ statementId, ledgerAccountCode, source, statementDate, matchedCount, discrepancyCount }` |
| `finance.ledger.reconciliation.discrepancy.detected.v1` | **(4th increment)** one per recorded discrepancy, in the same ingest `@Transactional` | `{ discrepancyId, ledgerAccountCode, type, expectedMinor, actualMinor, currency, externalRef?, journalEntryId? }` |

**Transactional outbox (atomic).** The append-side `LedgerEventPublisher` builds the
canonical envelope (the same shape ledger-service's own consumer parses —
`{eventId, eventType, occurredAt, tenantId, source, aggregateType, aggregateId,
payload}`, `source = "finance-platform-ledger-service"`) and persists a
`ledger_outbox` row **inside the same `@Transactional`** as the domain write. The
row commits with the entry+audit (or close+snapshot) or not at all — the GL feed
can never diverge from the books (F1/T3). A guard-rejected posting into a CLOSED
period rolls the whole Tx back → **no** `entry.posted` row.

**Relay (`OutboxRow` path).** `LedgerOutboxPublisher extends
AbstractOutboxPublisher<LedgerOutboxJpaEntity>` (`libs/java-messaging`, ADR-MONO-004)
polls `ledger_outbox` (`published_at IS NULL`, created-at order), publishes via the
EXISTING `KafkaTemplate` (already present for `@RetryableTopic` DLT), and marks the
row published after the Kafka ACK (at-least-once; downstream consumers dedupe on the
envelope `eventId`). `TopicResolver`: `finance.ledger.X → finance.ledger.X.v1`.
Exponential backoff + a `ledger.outbox.pending.count` gauge come from the lib.

**Why the `OutboxRow` path, not the libs `OutboxWriter`.** The libs
`OutboxAutoConfiguration` entity-scans the libs `ProcessedEventJpaEntity` (mapped to
`processed_events`), which collides with ledger-service's OWN `processed_events`
consumer-dedupe table. ledger keeps `OutboxAutoConfiguration` +
`OutboxMetricsAutoConfiguration` **excluded** (1st-increment stance unchanged) and
owns `LedgerOutboxJpaEntity implements OutboxRow` (`ledger_outbox` table, MySQL
`payload TEXT`). The consumer-dedupe path is untouched (§ Idempotency / dedupe).

## fintech Mandatory Rule mapping (rules/domains/fintech.md)

| Rule | Status | Mechanism |
|---|---|---|
| **F1** idempotent + Tx-protected | ✅ | `processed_events` dedupe (source event id) in the posting `@Transactional`; at-most-once entry per event; **(5th incr)** manual posting reuses the same dedupe keyed by the client `Idempotency-Key` (`manual:{key}`) — replay returns the original entry |
| **F2** double-entry ledger | ✅ (this is it) | `JournalEntry` balanced invariant `Σdebit == Σcredit`; ledger is downstream of the wallet, never writes back; **(5th incr)** operator manual entries pass the SAME factory balance gate before any persist |
| **F3** posted entry immutable; reversal-only | ✅ | no UPDATE/DELETE of entries/lines; `REVERSAL` entry references the original; **(5th incr)** manual adjusting entries are equally immutable (a correction is another entry) |
| **F5** money = minor-units, no float | ✅ | `Money(long, Currency)`; grep-zero float/double in `domain/money`; `CURRENCY_MISMATCH` guard. **(8th incr)** money stays integer minor units (both transaction and base amounts are `long`); the `exchangeRate` is an exact `BigDecimal` / `DECIMAL(20,8)` (decimal, **not** a float) recorded for provenance — the balance is checked on integer `baseAmount`s, never re-derived from the rate, so no rounding can create/destroy funds. **(9th incr)** FX revaluation's `revaluedBase = round(foreignBalance × closingRate)` is computed with the `BigDecimal` rate then stored as a `long` KRW minor (HALF_UP); the booked `delta` is integer base minor units, balanced exactly — no float touches the books |
| **F6** immutable audit | ✅ | append-only `audit_log`, same Tx (audit-heavy) |
| **F7** regulated PII encrypted/masked | N/A (first increment) | the ledger stores account ids + amounts, no new regulated PII (no KYC documents); reuses account-service-masked refs |
| **F8** reconciliation no auto-close | ✅ (4th increment) | `ReconciliationMatcher` records mismatches as OPEN `ReconciliationDiscrepancy` (operator review queue); resolution is operator-only via `ResolveDiscrepancyUseCase` — no code path auto-closes or adjusts a discrepancy (§ Reconciliation); **(6th/7th increment)** a CLOSED period is closed to reconciliation on both sides — neither resolving an existing discrepancy nor ingesting a new statement dated in the period is allowed (`RECONCILIATION_PERIOD_LOCKED`; § Reconciliation § Period lock) |
| **F2 (period close)** | ✅ (2nd increment) | `AccountingPeriod` OPEN→CLOSED + non-overlap invariant; `PostJournalEntryUseCase` guard rejects a posting into a CLOSED period (`LEDGER_PERIOD_CLOSED`, net-zero otherwise); close captures an immutable trial-balance snapshot (§ Accounting Period) |
| **F2 (FX revaluation)** | ✅ (9th increment) | unrealized revaluation books a **balanced base-currency** adjusting entry (DR/CR the foreign position's base carrying + contra `FX_GAIN`/`FX_LOSS`) through the SAME guarded write path — the books stay balanced in the base currency; the foreign quantity is untouched (no synthetic currency movement); re-revaluation reads the already-revalued carrying (no double-booking); `delta == 0` / no position → net-zero no-op (§ FX gain/loss revaluation) |
| **F2 (FX settlement)** | ✅ (10th increment) | realized settlement books a **balanced base-currency 3-line** entry (remove the position at carrying via the 8th-incr multi-currency line + base proceeds + realized `FX_GAIN`/`FX_LOSS`) through the SAME guarded write path — `Σ baseDebit == Σ baseCredit`; `realized = proceeds − carrying` measured against the already-revalued carrying (no double-count vs revaluation); polarity automatic for assets + liabilities (line directions from `sign(F)` + `sign(realized)`); `F == 0` → net-zero no-op (§ FX settlement) |

## Trait Rule mapping

| Trait Rule | Status | Mechanism |
|---|---|---|
| **transactional** T1/T2 idempotency + atomic state-change | ✅ | dedupe + entry + audit in one Tx |
| T4 invariant via dedicated module | ✅ | `JournalEntry`/`PostingPolicy` pure, no setter mutation |
| T7 optimistic locking | ✅ | `@Version` on `JournalEntry` (entries are insert-only, but the aggregate carries it for consistency) |
| **regulated** | ✅ | tenant gate fail-closed; no new regulated PII surface; operator-read via console |
| **audit-heavy** | ✅ | append-only `audit_log` of every posting |

## Dependencies

| Dir | Target | Protocol | Notes |
|---|---|---|---|
| In | Kafka `finance.transaction.{completed,reversed}.v1` | TCP | account-service outbox; partition key `accountId` (per-account ordering) |
| In | finance `gateway-service` (v1 deferred) / direct JWT | HTTP `/api/finance/ledger/**` | tenant-validated read JWT |
| Out | MySQL `finance_ledger_db` | JDBC | ledger_account, journal_entry, journal_line, audit_log, processed_events; **(3rd incr)** `ledger_outbox` |
| Out | **(3rd incr)** Kafka `finance.ledger.{entry.posted,period.closed}.v1` | TCP | GL/AP feed — per-service outbox relay (`OutboxRow` path); partition key `entryId` / `periodId` |
| Out | IAM `/oauth2/jwks` | HTTPS | RS256 verification (libs/java-security) |
| Out (obs) | OTLP collector | HTTPS | traces |

ledger-service is a **publishing consumer** (3rd increment) — it consumes
account-service events, exposes reads, and emits a one-way **GL/AP feed**
(`finance.ledger.*`) for an external accounting system; it never calls other
services synchronously or writes back to `finance_db` (the feed is downstream-only).

## Observability

- Logback MDC `traceId / requestId / tenantId / ledgerAccountCode`.
- Counters: `ledger_entries_posted_total{txnType}`,
  `ledger_unbalanced_rejected_total`, `ledger_events_deduped_total`,
  `ledger_consumer_dlt_total`.
- Tracing OTLP; `/actuator/prometheus` internal docker network only.

## Failure Modes

| # | Situation | Behavior |
|---|---|---|
| 1 | Re-delivered event (same source event id) | dedupe no-op (at-most-once posting); `ledger_events_deduped_total++` |
| 2 | Unbalanced policy result (future bug) | `LEDGER_ENTRY_UNBALANCED` — entry rejected, event → DLT after retries (never persist unbalanced) |
| 3 | Unmappable / malformed envelope | → DLT immediately (no poison loop); counter increments |
| 4 | Reversal event with no original entry found | → DLT (the original COMPLETED event should have arrived first; per-account ordering makes this a real anomaly, not silently dropped) |
| 5 | Cross-tenant read JWT (`tenant_id ∉ {finance,*}` and `entitled_domains ∌ finance`) | 403 `TENANT_FORBIDDEN` |
| 6 | Unknown ledger account / entry on read | 404 `LEDGER_ACCOUNT_NOT_FOUND` / `JOURNAL_ENTRY_NOT_FOUND` |
| 7 | Cross-currency lines in one entry | **(≤7th incr)** 422 `CURRENCY_MISMATCH`; **(8th incr)** allowed — balanced in the base currency (§ Multi-currency journals) |
| 8 | HOLD / RELEASE transaction completed | no entry posted (documented; not an error) |
| 9 | Entry `postedAt` covered by a CLOSED period (late/replayed/backdated) | 422 `LEDGER_PERIOD_CLOSED` — entry rejected, consumer event → DLT after retries (no dedupe row written); no covering closed period → posts normally (net-zero) |
| 10 | Open a window overlapping an existing period | 422 `ACCOUNTING_PERIOD_OVERLAP` |
| 11 | Open a window with `from ≥ to` | 422 `ACCOUNTING_PERIOD_INVALID_WINDOW` |
| 12 | Close an already-closed period | 409 `ACCOUNTING_PERIOD_ALREADY_CLOSED` |
| 13 | Period id unknown on close/detail | 404 `ACCOUNTING_PERIOD_NOT_FOUND` |
| 14 | **(3rd incr)** Kafka publish fails (broker down) | the `ledger_outbox` row stays `published_at IS NULL`; the relay retries with exponential backoff (at-least-once); the domain write already committed (transactional outbox) |
| 15 | **(3rd incr)** Posting rejected (e.g. closed period) before the outbox append | the whole posting `@Transactional` rolls back → no entry AND no `entry.posted` outbox row (atomic) |
| 16 | **(3rd incr)** Re-delivered GL-feed event downstream (at-least-once) | consumers dedupe on the envelope `eventId` (no in-repo consumer yet; documented for the external GL/AP system) |
| 17 | **(4th incr)** Unmatched external statement line / unmatched internal entry | recorded as an OPEN `ReconciliationDiscrepancy` (UNMATCHED_EXTERNAL / UNMATCHED_INTERNAL) — surfaced to the operator queue, NEVER auto-closed (F8) |
| 18 | **(4th incr)** Resolve an already-RESOLVED discrepancy | 409 `RECONCILIATION_ALREADY_RESOLVED` |
| 19 | **(4th incr)** Ingest a statement for a non-clearing account | 422 `RECONCILIATION_ACCOUNT_INVALID` (only `CASH_CLEARING` / `SETTLEMENT_SUSPENSE` reconcile) |
| 20 | **(4th incr)** Unknown statement / discrepancy id on read or resolve | 404 `RECONCILIATION_STATEMENT_NOT_FOUND` / `RECONCILIATION_DISCREPANCY_NOT_FOUND` |
| 21 | **(5th incr)** Manual posting with unbalanced lines / cross-currency lines | 422 `LEDGER_ENTRY_UNBALANCED` / `CURRENCY_MISMATCH` (the `JournalEntry` factory rejects synchronously — nothing persists) |
| 22 | **(5th incr)** Manual posting referencing an unknown ledger account | 404 `LEDGER_ACCOUNT_NOT_FOUND` (no lazy mint via the operator path) |
| 23 | **(5th incr)** Manual posting whose `postedAt` falls in a CLOSED period | 422 `LEDGER_PERIOD_CLOSED` (the same closed-period guard, now surfaced synchronously on REST — not the consumer DLT route) |
| 24 | **(5th incr)** Manual posting `Idempotency-Key` absent / replayed | absent → 400 `IDEMPOTENCY_KEY_REQUIRED`; replayed key → 200 returning the original entry (no second post — `processed_events` dedupe, F1) |
| 25 | **(6th incr)** Resolve a discrepancy whose statement date is in a CLOSED period | 422 `RECONCILIATION_PERIOD_LOCKED` (the books are frozen; correct via the next period). No covering CLOSED period / no statement → resolve proceeds (net-zero) |
| 26 | **(7th incr)** Ingest a statement whose statement date is in a CLOSED period | 422 `RECONCILIATION_PERIOD_LOCKED` thrown before any persist/match/emit — a locked ingest records nothing (atomic). No covering CLOSED period → ingest proceeds (net-zero) |
| 27 | **(8th incr)** Multi-currency entry whose base amounts do not balance | 422 `LEDGER_ENTRY_UNBALANCED` (`Σ baseDebit ≠ Σ baseCredit`) — the factory rejects before persist; the base amounts are authoritative (no rounding involved at balance time) |
| 28 | **(8th incr)** A line's currency / base currency unsupported | 422 `CURRENCY_MISMATCH` (`UnsupportedCurrencyException` — outside `{KRW,USD,EUR,JPY}`; the base currency is always KRW in v1) |
| 29 | **(9th incr)** FX revaluation `closingRate` not strictly positive | 422 `REVALUATION_RATE_INVALID` (`RevaluationRateInvalidException`) — a position cannot be valued at a zero/negative rate; nothing persists |
| 30 | **(9th incr)** FX revaluation `currency` is the base currency (KRW) or unsupported | 422 `CURRENCY_MISMATCH` (the base currency cannot be revalued against itself) |
| 31 | **(9th incr)** FX revaluation finds no position in that currency / the position is already at spot (`delta == 0`) | `200 {revalued:false, reason:"NO_POSITION"\|"AT_SPOT"}` — no entry booked, the `Idempotency-Key` is **not** consumed (net-zero; a later real position can be revalued) |
| 32 | **(9th incr)** FX revaluation `postedAt` in a CLOSED period / `Idempotency-Key` absent / replayed | 422 `LEDGER_PERIOD_CLOSED` (inherited guard) / 400 `IDEMPOTENCY_KEY_REQUIRED` / `200 {revalued:false, reason:"REPLAY"}` returning the original entry |
| 33 | **(10th incr)** FX settlement `settlementRate` not strictly positive | 422 `SETTLEMENT_RATE_INVALID` (`SettlementRateInvalidException`) — a position cannot be settled at a zero/negative rate; nothing persists |
| 34 | **(10th incr)** FX settlement `currency` is the base (KRW)/unsupported, or `proceedsAccountCode` unknown | 422 `CURRENCY_MISMATCH` / 404 `LEDGER_ACCOUNT_NOT_FOUND` (the proceeds account must already exist — no lazy mint) |
| 35 | **(10th incr)** FX settlement finds no position in that currency (`F == 0`) | `200 {settled:false, reason:"NO_POSITION"}` — no entry booked, the `Idempotency-Key` is **not** consumed (net-zero) |
| 36 | **(10th incr)** FX settlement `postedAt` in a CLOSED period / `Idempotency-Key` absent / replayed | 422 `LEDGER_PERIOD_CLOSED` (inherited guard) / 400 `IDEMPOTENCY_KEY_REQUIRED` / `200 {settled:false, reason:"REPLAY"}` returning the original entry |
| 37 | **(11th incr)** A foreign external line matches the transaction leg but its bank-reported base (KRW) value differs from the internal carrying base | the match is recorded **and** an OPEN `AMOUNT_MISMATCH` discrepancy records the FX difference (`expectedMinor`=internal carrying base, `actualMinor`=external base, `currency`=KRW) — operator review, never auto-adjusted (F8). A KRW line / a line without an external base amount → no base-leg discrepancy (net-zero) |

## Testing Strategy

- **Unit** (`:ledger-service:test`): domain — `JournalEntryTest` (balanced invariant,
  unbalanced rejection, immutability), `PostingPolicyTest` (each txn-type → exact
  debit/credit lines; HOLD/RELEASE → no entry; reversal swap), `MoneyTest`,
  `LedgerAccountTest` (normal side, running balance); application —
  `PostFromTransactionUseCaseTest` (mock ports, dedupe, mapping).
- **Slice**: `@WebMvcTest` `LedgerController` + SecurityConfig + GlobalExceptionHandler
  error envelope; JPA adapter slices.
- **Integration** (`:ledger-service:integrationTest`, `@Tag("integration")`,
  Testcontainers MySQL + **real Kafka** + WireMock JWKS — H2 forbidden): produce a
  `finance.transaction.completed.v1` (TOPUP) → consume → a balanced entry exists +
  trial balance == 0; re-deliver same event → still one entry (dedupe); a TRANSFER →
  two wallet lines; a `reversed.v1` → reversal entry, trial balance still 0;
  cross-tenant read → 403; HOLD completed → no entry. `integrationTest` excluded from
  `./gradlew check`.
- **Period close (2nd increment)**: unit — `AccountingPeriodTest` (open/close
  transitions, re-close rejection, invalid window, `covers` boundary [inclusive
  `from`, exclusive `to`]); application — `CloseAccountingPeriodUseCaseTest`
  (snapshot computation, OPEN-required), `OpenAccountingPeriodUseCaseTest` (overlap
  rejection), `PostJournalEntryUseCase` guard (closed-covering rejects;
  no-period / open-period proceeds — net-zero). Integration: post entries → open a
  window covering now → close → snapshot == live trial balance + status CLOSED +
  entryCount; a subsequent `transaction.completed.v1` into the closed window posts
  **no** entry (→ DLT, `LEDGER_PERIOD_CLOSED`); a non-overlapping window opens; an
  overlapping window → 422; re-close → 409; list/detail return the contract shapes.
- **GL/AP feed (3rd increment)**: unit — `LedgerEventPublisher` builds the exact
  envelope + payload for both events (entry-posted incl. `reversalOfEntryId`;
  period-closed); `LedgerOutboxPublisher` topic resolution
  (`finance.ledger.entry.posted → finance.ledger.entry.posted.v1`);
  `PostJournalEntryUseCase` / `CloseAccountingPeriodUseCase` invoke the publisher
  in-Tx (mock port, verify call + after-save ordering). Integration (real Kafka,
  the authoritative round-trip): produce `transaction.completed.v1` → entry posts →
  a `ledger_outbox` row appears → the relay publishes → **consume
  `finance.ledger.entry.posted.v1`** and assert the envelope + balanced-lines
  payload; close a period → **consume `finance.ledger.period.closed.v1`** and assert
  `{periodId, from, to, closedAt, entryCount}`; a guard-rejected posting into a
  CLOSED period emits **no** `entry.posted` row (atomic rollback). App boot proves
  no `processed_events` duplicate-mapping (OutboxRow path; `OutboxAutoConfiguration`
  excluded).
- **Reconciliation (4th increment)**: unit — `ReconciliationMatcherTest` (1:1 match;
  unmatched-external → UNMATCHED_EXTERNAL; unmatched-internal → UNMATCHED_INTERNAL;
  amount-mismatch; multi-line determinism); application — `IngestStatementUseCase`
  (persists matches + **OPEN** discrepancies, emits both event types, **NO
  auto-close**), `ResolveDiscrepancyUseCase` (OPEN→RESOLVED, re-resolve → 409,
  account-invalid → 422). Integration (Testcontainers, authoritative): post ledger
  entries (TOPUP/TRANSFER → CASH_CLEARING) → ingest an external statement (some lines
  match, some don't) → matches + **OPEN** discrepancies recorded (assert NOT
  auto-closed) → **consume `finance.ledger.reconciliation.completed.v1` +
  `.discrepancy.detected.v1`**; resolve a discrepancy → RESOLVED; re-resolve → 409;
  ingest on a non-clearing account → 422 `RECONCILIATION_ACCOUNT_INVALID`. (The IT
  base `@BeforeEach` period cleanup also covers reconciliation tables to keep the
  static-container classes isolated.)
- **Manual journal posting (5th increment)**: application —
  `PostManualJournalEntryUseCaseTest` (balanced operator lines persist + emit
  `entry.posted` with `sourceType=MANUAL`; unbalanced → `LEDGER_ENTRY_UNBALANCED`;
  unknown account → `LEDGER_ACCOUNT_NOT_FOUND`, no lazy mint; back-dated into a CLOSED
  period → `LEDGER_PERIOD_CLOSED`; replayed key returns the original entry — no second
  post; operator subject recorded as the audit actor). Slice — `@WebMvcTest
  JournalController` (201 happy, 400 missing `Idempotency-Key`, error envelopes).
  Integration (Testcontainers, authoritative): `POST /entries` with a balanced manual
  entry (DR `CASH_CLEARING` / CR `CUSTOMER_WALLET:{acct}` — accounts pre-existing) →
  201 → the entry + its lines persist, trial balance still == 0, and
  **`finance.ledger.entry.posted.v1` with `source.sourceType=MANUAL`** is consumed off
  Kafka; replay with the same key → 200 the same entryId (one entry only); an
  unbalanced body → 422 `LEDGER_ENTRY_UNBALANCED`; a back-dated entry into a closed
  window → 422 `LEDGER_PERIOD_CLOSED`; a cross-tenant JWT → 403.
- **Reconciliation period-lock (6th increment)**: application —
  `ResolveDiscrepancyUseCaseTest` (statement date covered by a CLOSED period →
  `RECONCILIATION_PERIOD_LOCKED`, no mutation; no covering period / OPEN period / no
  statement → resolves normally — net-zero; the `LocalDate` → start-of-day-UTC instant
  mapping for the boundary). Integration (Testcontainers, authoritative): post a
  clearing-account entry → ingest a statement (statement date D) producing an OPEN
  discrepancy → open + close a period whose window covers D's start-of-day-UTC instant
  → `resolve` the discrepancy → 422 `RECONCILIATION_PERIOD_LOCKED` (still OPEN); a
  second discrepancy whose statement date is NOT in any closed period → resolves 200
  (net-zero); a cross-tenant JWT → 403.
- **Reconciliation ingest-time period-lock (7th increment)**: application —
  `IngestStatementUseCaseTest` (statement date covered by a CLOSED period →
  `RECONCILIATION_PERIOD_LOCKED` thrown, and assert NO statement/match/discrepancy
  saved + NO outbox publish — the guard runs before any write; no covering period /
  no period defined → ingests normally — net-zero; the boundary mapping). Integration
  (Testcontainers, authoritative): open + close a period covering date D's
  start-of-day-UTC instant → `POST .../reconciliation/statements` with
  `statementDate = D` → 422 `RECONCILIATION_PERIOD_LOCKED`, and assert no statement row
  / no discrepancy / no event emitted; an ingest with a statement date NOT in any
  closed period → 201 (net-zero, matches + OPEN discrepancies as in FIN-BE-010); a
  cross-tenant JWT → 403.
- **Multi-currency journals (8th increment)**: unit — `JournalEntryTest` (a
  cross-currency entry whose base amounts balance is accepted [DR USD line baseAmount ==
  CR KRW line baseAmount]; base amounts NOT balancing → `LEDGER_ENTRY_UNBALANCED`; a
  single-currency KRW entry is unchanged — `baseAmount = amount`, `rate = 1`); reversal
  preserves txn money + rate + baseAmount and still balances. `MoneyTest`/conversion
  (BigDecimal rate, no float; baseAmount supplied not re-derived). Application —
  `PostManualJournalEntryUseCaseTest` (a foreign-currency manual entry with per-line
  base amounts posts + emits with the base amounts; an unbalanced-base manual entry →
  422). Integration (Testcontainers, authoritative): **V5 migration runs + backfills
  existing KRW lines** (assert an existing/auto-journal KRW entry still posts
  byte-identically, `base_amount == amount`, `rate = 1`, trial balance == 0); a
  **manual cross-currency entry** (DR USD clearing / CR KRW wallet, balanced in KRW) →
  201, persisted with per-line base amounts, and the **trial balance** shows the
  per-currency breakdown + a **base-currency consolidated** total in balance; a
  multi-currency period close captures a base-balanced snapshot; an
  unbalanced-base manual entry → 422 `LEDGER_ENTRY_UNBALANCED`. The auto-journal KRW
  round-trip (post → entry.posted.v1) is unchanged (net-zero).
- **FX gain/loss revaluation (9th increment)**: unit — `FxRevaluationPolicyTest`
  (asset gain [F>0, rate↑ → delta>0 → DR account/CR FX_GAIN]; asset loss [rate↓ → CR
  account/DR FX_LOSS]; **liability** loss [F<0, base value ↑ → delta<0 → loss] + liability
  gain [F<0, rate↓ → delta>0 → gain] — polarity automatic; `delta==0` → empty no-op;
  rounding HALF_UP on `foreignBalance × closingRate`; `closingRate ≤ 0` →
  `RevaluationRateInvalidException`); `JournalLineTest` (the `baseAdjustment` factory — zero
  foreign amount, non-zero base, balances against its KRW contra; `reversed()` preserves it;
  the positive-amount `of` factories still reject 0). Application —
  `RevalueForeignBalanceUseCaseTest` (mock ports: a gain/loss position posts the 2-line entry
  + emits `entry.posted` with `sourceType=REVALUATION`; no position / at-spot → `revalued:false`
  no-op, key NOT marked; replayed key → original entry; `postedAt` in a CLOSED period →
  `LEDGER_PERIOD_CLOSED`; missing key → `IDEMPOTENCY_KEY_REQUIRED`; KRW currency →
  `CURRENCY_MISMATCH`; operator subject = audit actor). Slice — `@WebMvcTest
  RevaluationController` (201 revalued / 200 no-op, 400 missing key, error envelopes).
  Integration (Testcontainers, authoritative — **no migration**, columns reused): post a
  **multi-currency manual entry** establishing a USD position on `CASH_CLEARING` (e.g. DR USD
  / CR KRW wallet @ rate 13.0) → `POST /revaluations {account:CASH_CLEARING, currency:USD,
  closingRate:13.5}` → **201**, the 2-line revaluation entry persists (DR CASH_CLEARING USD
  amount 0 / base +5000, CR FX_GAIN 5000), `FX_GAIN` seeded, the **trial balance** stays
  base-balanced and the USD position's foreign balance is **unchanged** while its base
  carrying == `foreignBalance × 13.5`; **consume `finance.ledger.entry.posted.v1` with
  `sourceType=REVALUATION`**; a **second** revaluation @ 14.0 books only the incremental delta
  (no double-booking); a revaluation @ a lower rate books `FX_LOSS`; a replay (same key) → 200
  same entryId; `closingRate:0` → 422 `REVALUATION_RATE_INVALID`; a back-dated revaluation into
  a CLOSED window → 422 `LEDGER_PERIOD_CLOSED`; revaluing a currency with no position → 200
  `revalued:false`; cross-tenant JWT → 403. The all-KRW auto-journal round-trip is unchanged
  (net-zero).
- **FX settlement (10th increment)**: unit — `FxSettlementPolicyTest` (an **asset** position
  settled above carrying → realized `FX_GAIN`; below → `FX_LOSS`; a **liability** position
  (`F < 0`) settled below carrying → gain + above → loss [polarity automatic — line directions
  from `sign(F)` + `sign(realized)`]; the 3-line entry balances in base [`Σ baseDebit ==
  Σ baseCredit`]; the removal line zeroes the position [`money=|F| {ccy}`, `baseAmount=|C| KRW`];
  `proceedsBase = round(F × rate)` HALF_UP; `F == 0` → empty; `settlementRate ≤ 0` →
  `SettlementRateInvalidException`; settling at the carrying rate realizes 0). Application —
  `SettleForeignPositionUseCaseTest` (mock ports: a gain/loss settlement posts the 3-line entry
  + emits `entry.posted` with `sourceType=SETTLEMENT`; no position (`F==0`) → `settled:false`
  no-op, key NOT marked; replay → original; unknown `proceedsAccountCode` →
  `LEDGER_ACCOUNT_NOT_FOUND`; `postedAt` in a CLOSED period → `LEDGER_PERIOD_CLOSED`; missing key
  → `IDEMPOTENCY_KEY_REQUIRED`; KRW currency → `CURRENCY_MISMATCH`; operator subject = audit
  actor). Slice — `@WebMvcTest SettlementController` (201 settled / 200 no-op, 400 missing key,
  error envelopes). Integration (Testcontainers, authoritative — **no migration**, reuses the
  8th-incr multi-currency line + 9th-incr FX accounts): establish a USD position via a
  multi-currency manual entry (DR USD `CASH_CLEARING` / CR KRW wallet @ 13.0 → carrying 130 000),
  then `POST /settlements {CASH_CLEARING, USD, 13.7, proceedsAccountCode: CASH_KRW}` → **201**,
  the 3-line entry persists (DR CASH_KRW 137 000 / CR CASH_CLEARING 10 000 USD@130 000 base / CR
  FX_GAIN 7 000), the **USD position on CASH_CLEARING is removed** (`accountTotalsForCurrency`
  → foreign 0 + base 0), the proceeds sit in CASH_KRW, the trial balance stays base-balanced, and
  **`finance.ledger.entry.posted.v1` with `sourceType=SETTLEMENT`** is consumed; a settlement
  **below** carrying books `FX_LOSS`; a **revalue-then-settle** sequence realizes only the
  incremental delta (no double-count); replay (same key) → 200 same entryId; `settlementRate:0`
  → 422 `SETTLEMENT_RATE_INVALID`; an unknown proceeds account → 404; a back-dated settlement into
  a CLOSED window → 422 `LEDGER_PERIOD_CLOSED`; a currency with no position → 200 `settled:false`;
  cross-tenant JWT → 403. The all-KRW auto-journal round-trip is unchanged (net-zero).
- **Multi-currency reconciliation (11th increment)**: unit — `ReconciliationMatcherTest`
  additions (a foreign external line matching an internal line on the transaction leg whose
  external `baseAmount` **equals** the internal `baseMoney` → MATCHED, **no** discrepancy; whose
  external `baseAmount` **differs** → MATCHED **plus** an `AMOUNT_MISMATCH` discrepancy with
  `expectedMinor`=internal carrying base, `actualMinor`=external base, `currency`=KRW, carrying
  both `externalRef` + `journalEntryId`; a KRW line / a foreign line **without** an external base
  amount → no base-leg discrepancy [net-zero]; the existing UNMATCHED_* paths unchanged). Slice —
  JPA adapter slice asserting `findUnmatchedInternalLines` populates `InternalLine.baseMoney`.
  Integration (Testcontainers, authoritative — **V6 runs**): post a **multi-currency** entry
  establishing a USD line on `CASH_CLEARING` (carrying e.g. 130 000 KRW @ 13.0) → ingest a USD
  external statement line matching the USD amount + direction but declaring `baseAmount` 132 000
  KRW → **201**: the line is `MATCHED` (a `ReconciliationMatch` exists) **and** an OPEN
  `AMOUNT_MISMATCH` discrepancy is recorded (expected 130 000 / actual 132 000 / KRW) and
  **`finance.ledger.reconciliation.discrepancy.detected.v1` with `type=AMOUNT_MISMATCH`** is
  consumed; a USD line whose `baseAmount` equals the carrying → MATCHED, no discrepancy; a
  **KRW-only** statement (FIN-BE-010 scenario) → byte-identical (net-zero, no V6 effect); the
  discrepancy can be `resolve`d (operator) → RESOLVED; cross-tenant → 403. The existing
  reconciliation ITs (UNMATCHED_*, period-lock) stay green.

## Required Artifacts mapping (rules/domains/fintech.md § Required Artifacts)

| # | Artifact | Disposition |
|---|---|---|
| 6 | Ledger / double-entry model | **This spec** (§ Chart of Accounts, § Posting Policy, § Immutability) — the artifact ADR-008 § D3 deferred |
| 7 | Error-code registration | This spec PR claims the pre-registered `LEDGER_*` codes in `platform/error-handling.md` (removes `v2-planned`) + adds `LEDGER_ACCOUNT_NOT_FOUND` / `JOURNAL_ENTRY_NOT_FOUND` |
| 8 | Bounded-context map | ledger context split from account-service per `PROJECT.md` Service Map; boundary = events in, no write-back |

## References

- `platform/architecture-decision-rule.md`, `platform/service-types/INDEX.md`,
  `platform/service-types/rest-api.md`, `platform/service-types/event-consumer.md`,
  `platform/error-handling.md`, `platform/testing-strategy.md`
- `rules/domains/fintech.md` (F1–F8 — governing; § Ledger v2), `rules/traits/{transactional,regulated,audit-heavy}.md`
- `projects/finance-platform/specs/services/account-service/architecture.md` (the blueprint mirrored here)
- `projects/finance-platform/specs/contracts/events/finance-account-events.md` (the consumed transaction events)
- [`ledger-api.md`](../../contracts/http/ledger-api.md) (this PR),
  [`finance-ledger-events.md`](../../contracts/events/finance-ledger-events.md) (this PR)
- precedent: `projects/erp-platform/specs/services/read-model-service/architecture.md`
  (`rest-api + event-consumer` dual-type, terminal consumer, event-driven derivation + read API)
- `docs/adr/ADR-MONO-008-finance-platform-bootstrap.md` § D3 (ledger = v2),
  `docs/adr/ADR-MONO-013` §3.3 (backend-only + console render)
