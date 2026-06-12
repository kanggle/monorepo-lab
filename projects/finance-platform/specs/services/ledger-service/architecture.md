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
> § Event publication + § Increment Scope. Reconciliation matching, manual journal
> posting, and multi-currency remain forward-declared (§ Increment Scope). The account-service architecture
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
| Event publication | **(3rd increment, TASK-FIN-BE-009)** `finance.ledger.entry.posted.v1` (every posted entry — the GL/AP feed) + `finance.ledger.period.closed.v1` (on period close), via a per-service transactional outbox (`OutboxRow` path). 1st/2nd increments published nothing (terminal consumer); the 3rd makes it a **publishing consumer**. |
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

**Forward-declared — OUT (each a later task):**
- **Reconciliation matching** (`reconciliation_discrepancy` real matching vs
  external statements — account-service models it as a placeholder, F8).
- **Manual journal posting API** (operator-initiated adjusting entries).
- **Multi-currency journals** (first increment is single-currency per entry;
  the chart + lines carry currency but cross-currency entries are rejected).

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
│   │   ├── LedgerAccountType.java         ← ASSET / LIABILITY (+ EQUITY/INCOME/EXPENSE reserved)
│   │   ├── NormalSide.java                ← DEBIT / CREDIT
│   │   └── repository/LedgerAccountRepository.java   ← outbound port
│   ├── journal/
│   │   ├── JournalEntry.java              ← aggregate root; balanced invariant; immutable; sourceRef
│   │   ├── JournalLine.java               ← (ledgerAccountCode, direction DEBIT/CREDIT, Money)
│   │   ├── EntryDirection.java            ← DEBIT / CREDIT
│   │   ├── PostingPolicy.java             ← transaction-type → balanced lines (pure; § Posting Policy)
│   │   ├── SourceRef.java                 ← (sourceType, sourceTxnId, sourceEventId) provenance
│   │   └── repository/JournalRepository.java
│   ├── period/                           ← (2nd increment) accounting period
│   │   ├── AccountingPeriod.java          ← aggregate; OPEN→CLOSED state machine; [from,to) covers(); non-overlap
│   │   ├── PeriodStatus.java              ← OPEN / CLOSED
│   │   ├── PeriodBalanceSnapshot.java     ← close-time per-account + grand totals (pure, immutable)
│   │   ├── PeriodAccountTotal.java        ← one account's debit/credit Money in the snapshot
│   │   └── repository/AccountingPeriodRepository.java  ← outbound port (findOverlapping/findCovering/save/findById/findAll)
│   ├── money/
│   │   ├── Money.java                     ← long minorUnits + Currency (NO float/double)
│   │   └── Currency.java                  ← ISO-4217 + minor-unit scale
│   ├── audit/
│   │   ├── AuditLog.java
│   │   └── AuditLogRepository.java
│   └── error/                             ← domain exceptions (fintech codes)
│       (LedgerEntryUnbalancedException, LedgerAccountNotFoundException,
│        JournalEntryNotFoundException, DuplicateSourceEventException [internal — drives dedupe],
│        CurrencyMismatchException, ...;
│        (2nd incr) LedgerPeriodClosedException, AccountingPeriodNotFoundException,
│        AccountingPeriodOverlapException, AccountingPeriodAlreadyClosedException,
│        AccountingPeriodInvalidWindowException)
├── application/                           ← use cases + outbound ports
│   ├── PostJournalEntryUseCase.java       ← @Transactional: balance-validate → (2nd incr) closed-period guard → persist entry + lines + audit → (3rd incr) append entry.posted outbox row (one Tx)
│   ├── PostFromTransactionUseCase.java    ← maps an account-service transaction envelope → PostJournalEntry (via PostingPolicy); idempotent on sourceEventId
│   ├── QueryLedgerUseCase.java            ← read: entry detail / per-account entries + balance / trial balance
│   ├── OpenAccountingPeriodUseCase.java   ← (2nd incr) @Transactional: non-overlap check → persist OPEN period + audit
│   ├── CloseAccountingPeriodUseCase.java  ← (2nd incr) @Transactional: require OPEN → compute snapshot (postedAt < to) → CLOSED + entryCount + snapshot + audit → (3rd incr) append period.closed outbox row
│   ├── QueryAccountingPeriodUseCase.java  ← (2nd incr) read: list periods / period detail + snapshot
│   ├── ActorContext.java
│   ├── view/ (JournalEntryView, JournalLineView, LedgerAccountBalanceView, TrialBalanceView)
│   └── port/outbound/
│       ├── ProcessedEventStore.java       ← dedupe port (processed_events, source event id)
│       ├── LedgerEventPublisher.java      ← (3rd incr) append-side port: publishEntryPosted / publishPeriodClosed (called in-Tx)
│       └── ClockPort.java
├── infrastructure/
│   ├── persistence/jpa/                   ← Spring Data + adapters (toDomain/fromDomain)
│   │   (LedgerAccountJpaEntity/Repository/Adapter, JournalEntryJpaEntity, JournalLineJpaEntity,
│   │    AuditLogJpaEntity, processed_events;
│   │    (2nd incr) AccountingPeriodJpaEntity/Repository/Adapter, PeriodBalanceSnapshotJpaEntity)
│   ├── outbox/                            ← (3rd incr) per-service transactional outbox (OutboxRow path)
│   │   ├── LedgerOutboxJpaEntity.java     ← implements OutboxRow (@Table ledger_outbox, MySQL payload TEXT)
│   │   ├── LedgerOutboxJpaRepository.java ← findPending(Pageable) + countByPublishedAtIsNull
│   │   ├── LedgerOutboxPublisher.java     ← extends AbstractOutboxPublisher; @Scheduled relay; TopicResolver finance.ledger.X→.v1
│   │   └── OutboxLedgerEventPublisher.java ← LedgerEventPublisher impl: build canonical envelope → save ledger_outbox row
│   ├── security/  (SecurityConfig, AllowedIssuersValidator, TenantClaimValidator,
│   │               ActorContextJwtAuthenticationConverter, ServiceLevelOAuth2Config)
│   └── config/ (ClockConfig, JpaConfig, KafkaConsumerConfig [also the outbox-relay KafkaTemplate],
│                ChartOfAccountsSeedConfig, (3rd incr) OutboxConfig [TransactionTemplate + ledger.outbox.* props])
├── messaging/                             ← inbound event adapter
│   ├── TransactionEventConsumer.java      ← @KafkaListener finance.transaction.{completed,reversed}.v1
│   │                                          group finance-ledger-v1, @RetryableTopic + DLT, manual ACK, dedupe
│   ├── TransactionEnvelope.java           ← inbound payload DTO (tolerant of unknown fields)
│   └── EnvelopeToCommandMapper.java       ← envelope → PostFromTransaction command
└── presentation/                          ← inbound web adapter
    ├── controller/LedgerController.java    ← /api/finance/ledger/** (reads)
    ├── controller/PeriodController.java     ← (2nd incr) /api/finance/ledger/periods/** (open/close/list/detail)
    ├── advice/GlobalExceptionHandler.java  ← domain → HTTP envelope (fintech codes; (2nd incr) period codes)
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

A ledger account's **running balance** = Σ(debit lines) − Σ(credit lines); its
*natural* balance is interpreted by `normalSide` (a liability with a credit balance
is positive). EQUITY/INCOME/EXPENSE types are reserved for later increments.

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
if a future policy bug produced an unbalanced set). Cross-currency lines in one
entry → `CURRENCY_MISMATCH` (first increment is single-currency per entry).

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
| `POST` | `/api/finance/ledger/periods` | JWT (authenticated) | **(2nd increment)** open an accounting period `{from, to}` |
| `POST` | `/api/finance/ledger/periods/{periodId}/close` | JWT (authenticated) | **(2nd increment)** close a period → capture the trial-balance snapshot |
| `GET` | `/api/finance/ledger/periods` | JWT | **(2nd increment)** list accounting periods |
| `GET` | `/api/finance/ledger/periods/{periodId}` | JWT | **(2nd increment)** period detail + its balance snapshot |
| `GET` | `/actuator/{health,info}` | none | probes |
| `GET` | `/actuator/prometheus` | network-isolated | metrics |

Auto-journal postings remain event-driven (no posting mutation endpoint). The
**period mutations** (open/close) are the increment's only write endpoints —
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
| **F1** idempotent + Tx-protected | ✅ | `processed_events` dedupe (source event id) in the posting `@Transactional`; at-most-once entry per event |
| **F2** double-entry ledger | ✅ (this is it) | `JournalEntry` balanced invariant `Σdebit == Σcredit`; ledger is downstream of the wallet, never writes back |
| **F3** posted entry immutable; reversal-only | ✅ | no UPDATE/DELETE of entries/lines; `REVERSAL` entry references the original |
| **F5** money = minor-units, no float | ✅ | `Money(long, Currency)`; grep-zero float/double in `domain/money`; `CURRENCY_MISMATCH` guard |
| **F6** immutable audit | ✅ | append-only `audit_log`, same Tx (audit-heavy) |
| **F7** regulated PII encrypted/masked | N/A (first increment) | the ledger stores account ids + amounts, no new regulated PII (no KYC documents); reuses account-service-masked refs |
| **F8** reconciliation no auto-close | forward-decl | reconciliation matching is a later increment (account-service models the placeholder) |
| **F2 (period close)** | ✅ (2nd increment) | `AccountingPeriod` OPEN→CLOSED + non-overlap invariant; `PostJournalEntryUseCase` guard rejects a posting into a CLOSED period (`LEDGER_PERIOD_CLOSED`, net-zero otherwise); close captures an immutable trial-balance snapshot (§ Accounting Period) |

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
| 7 | Cross-currency lines in one entry | 422 `CURRENCY_MISMATCH` |
| 8 | HOLD / RELEASE transaction completed | no entry posted (documented; not an error) |
| 9 | Entry `postedAt` covered by a CLOSED period (late/replayed/backdated) | 422 `LEDGER_PERIOD_CLOSED` — entry rejected, consumer event → DLT after retries (no dedupe row written); no covering closed period → posts normally (net-zero) |
| 10 | Open a window overlapping an existing period | 422 `ACCOUNTING_PERIOD_OVERLAP` |
| 11 | Open a window with `from ≥ to` | 422 `ACCOUNTING_PERIOD_INVALID_WINDOW` |
| 12 | Close an already-closed period | 409 `ACCOUNTING_PERIOD_ALREADY_CLOSED` |
| 13 | Period id unknown on close/detail | 404 `ACCOUNTING_PERIOD_NOT_FOUND` |
| 14 | **(3rd incr)** Kafka publish fails (broker down) | the `ledger_outbox` row stays `published_at IS NULL`; the relay retries with exponential backoff (at-least-once); the domain write already committed (transactional outbox) |
| 15 | **(3rd incr)** Posting rejected (e.g. closed period) before the outbox append | the whole posting `@Transactional` rolls back → no entry AND no `entry.posted` outbox row (atomic) |
| 16 | **(3rd incr)** Re-delivered GL-feed event downstream (at-least-once) | consumers dedupe on the envelope `eventId` (no in-repo consumer yet; documented for the external GL/AP system) |

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
