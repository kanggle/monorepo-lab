# TASK-FIN-BE-008 — ledger-service period close (2nd increment: accounting-period lifecycle + posting guard + close snapshot)

**Status:** done

**Type:** TASK-FIN-BE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Opus (complex domain — accounting-period state machine, non-overlap invariant, posting-path guard, close-time trial-balance snapshot)

---

## Goal

Deliver the **period-close** increment forward-declared by
`specs/services/ledger-service/architecture.md` § Increment Scope (the first
named follow-up after TASK-FIN-BE-007's auto-journal + read first increment).
A closed accounting period **locks the books** for that window: once an
`AccountingPeriod` is `CLOSED`, no journal entry may be posted whose `postedAt`
falls inside the period's `[from, to)` window (`LEDGER_PERIOD_CLOSED`, 422 → DLT
on the consumer path). Closing a period captures an immutable **trial-balance
snapshot** (per-account debit/credit totals + grand totals) as the period's
ending record. Operators drive close + read the period lifecycle over REST.

This deepens the finance domain's accounting depth (the regulatory "books are
closed for the month" guarantee + the period-end snapshot record) while keeping
the service a **terminal consumer** — `finance.ledger.period.closed.v1`
**emission stays deferred** to the GL/AP-feed increment that introduces the
outbox (architecture.md § Increment Scope decision; the events contract already
sequences outbox introduction to that increment, not this one).

## Scope

**Spec PR (this task → ready):** authored already —
`specs/services/ledger-service/architecture.md` (§ Increment Scope moves
period-close IN with the explicit **no-outbox / emission-deferred** decision; new
§ Accounting Period — lifecycle, non-overlap invariant, posting guard, close
snapshot; § REST endpoints + § Failure Modes + § fintech rule mapping updated) +
`specs/contracts/http/ledger-api.md` (period endpoints + claimed
`LEDGER_PERIOD_CLOSED` + new period error codes) +
`specs/contracts/events/finance-ledger-events.md` (note `period.closed.v1`
emission still deferred — the outbox arrives with the GL/AP feed) +
`platform/error-handling.md` (claim the pre-registered `LEDGER_PERIOD_CLOSED`
[remove `v2-planned`] + add `ACCOUNTING_PERIOD_NOT_FOUND` /
`ACCOUNTING_PERIOD_OVERLAP` / `ACCOUNTING_PERIOD_ALREADY_CLOSED` /
`ACCOUNTING_PERIOD_INVALID_WINDOW`).

**Impl PR — IN (the period-close increment):**
- **Domain (pure)**: `AccountingPeriod` aggregate (`periodId`, `tenantId`,
  `[from, to)` half-open `Instant` window, `status` OPEN|CLOSED, `closedAt?`,
  `closedBy?`, `entryCount?`) with a state machine: `open(...)` factory
  (validates `from < to` → `AccountingPeriodInvalidWindowException`) →
  `close(closedAt, closedBy, entryCount)` (OPEN→CLOSED; re-close →
  `AccountingPeriodAlreadyClosedException`; no reopen). A pure
  `covers(Instant)` predicate (`from ≤ t < to`). `PeriodBalanceSnapshot` /
  `PeriodAccountTotal` value objects (per-account debit/credit Money +
  grand totals) — pure, captured at close. Outbound port
  `AccountingPeriodRepository` (`findOverlapping(tenant, from, to)`,
  `findCovering(tenant, postedAt, status=CLOSED)`, `save`, `findById`, `findAll`).
- **Application**:
  - `OpenAccountingPeriodUseCase` (`@Transactional`: reject a window overlapping
    any existing period for the tenant → `AccountingPeriodOverlapException`;
    persist OPEN period + audit).
  - `CloseAccountingPeriodUseCase` (`@Transactional`: load the period, require
    OPEN, compute the trial-balance snapshot over entries with `postedAt < to`
    [tenant-scoped, reusing the existing per-account totals query], flip
    OPEN→CLOSED with `entryCount`, persist snapshot rows + audit).
  - `QueryAccountingPeriodUseCase` (read: list periods, period detail incl. its
    snapshot).
  - **Posting guard wired into the existing write path** —
    `PostJournalEntryUseCase.post` consults `AccountingPeriodRepository`: if a
    **CLOSED** period covers `entry.postedAt` → throw
    `LedgerPeriodClosedException` (`LEDGER_PERIOD_CLOSED`). **Net-zero**: no
    covering closed period (the common case, and always when no period is
    defined) → posting proceeds byte-identically to the first increment.
- **Presentation (write + read)**: `PeriodController`
  (`POST /api/finance/ledger/periods` open; `POST /api/finance/ledger/periods/{periodId}/close`;
  `GET /api/finance/ledger/periods`; `GET /api/finance/ledger/periods/{periodId}`),
  request/response DTOs (window as ISO-8601 instants; snapshot money as
  minor-units string), `GlobalExceptionHandler` mappings for the new codes.
  Mutations are `.authenticated()` + dual-accept tenant gate (parity with the
  service's current posture — no new scope-authority axis; operator caller via
  console).
- **Persistence (Flyway `V2__create_accounting_period.sql`)**: `accounting_period`
  + `period_balance_snapshot` tables (+ tenant_id, the non-overlap supporting
  index) + JPA entities/adapters; the `findCovering` / `findOverlapping` queries.
- **Tests**: domain unit (`AccountingPeriodTest` — open/close transitions,
  re-close rejection, invalid window, `covers` boundary [inclusive `from`,
  exclusive `to`]); application unit (`CloseAccountingPeriodUseCaseTest` snapshot
  computation + OPEN-required; `OpenAccountingPeriodUseCaseTest` overlap
  rejection; `PostJournalEntryUseCase` guard — closed-covering rejects,
  no-period/open-period proceed [net-zero]); `@WebMvcTest` `PeriodController`
  slice + error envelopes; **Testcontainers Integration** (MySQL + real Kafka +
  WireMock JWKS): post entries → `POST /periods` (window covering now) →
  `POST /periods/{id}/close` → snapshot matches the live trial balance + status
  CLOSED + entryCount; then publish a `transaction.completed.v1` whose entry
  would post into the closed window → **no new entry** (consumer routes it to
  DLT — `LEDGER_PERIOD_CLOSED`); open a second non-overlapping window → succeeds;
  overlapping window → 422; close an already-closed period → 409/422;
  list/detail return the contract shapes; cross-tenant → 403.

**Impl PR — OUT (still forward-declared, later tasks):** `period.closed.v1`
emission (lands with the GL/AP-feed outbox increment); period **reopen**;
"period must have ended (`to ≤ now`)" close policy refinement; GL/AP feed
`finance.ledger.entry.posted.v1`; reconciliation matching; manual journal
posting; multi-currency snapshot consolidation.

## Acceptance Criteria

- **AC-1 (lifecycle)** — `POST /periods` creates an OPEN period for a valid
  `[from, to)` window; `POST /periods/{id}/close` transitions it OPEN→CLOSED,
  recording `closedAt`, `closedBy` (actor), and `entryCount`. A second close →
  `ACCOUNTING_PERIOD_ALREADY_CLOSED`. There is no reopen path.
- **AC-2 (non-overlap invariant)** — opening a window that overlaps any existing
  period for the tenant → `ACCOUNTING_PERIOD_OVERLAP`; a non-overlapping window
  succeeds. `from ≥ to` → `ACCOUNTING_PERIOD_INVALID_WINDOW`. A domain unit test
  proves `covers` boundary semantics (`from` inclusive, `to` exclusive).
- **AC-3 (posting guard)** — once a period is CLOSED, a journal entry whose
  `postedAt` is covered by it is rejected with `LEDGER_PERIOD_CLOSED`; on the
  **consumer** path this routes the event to the DLT (no entry persisted, books
  unchanged). **Net-zero**: with no covering closed period — including when no
  period is defined at all — posting proceeds exactly as the first increment
  (an Integration assertion proves the pre-period happy path is unchanged).
- **AC-4 (close snapshot)** — closing a period captures a `PeriodBalanceSnapshot`
  (per-account debit/credit totals + grand totals) computed over entries with
  `postedAt < to`; the snapshot's grand totals are **in balance** (Σdebit ==
  Σcredit) and equal the live trial balance at close time. `GET /periods/{id}`
  returns the snapshot; the snapshot is immutable (insert-only).
- **AC-5 (read + tenant + boundary)** — `GET /periods` (list) and
  `GET /periods/{id}` (detail) return the `ledger-api.md` shapes; cross-tenant
  JWT → 403 `TENANT_FORBIDDEN`; unknown period id → 404
  `ACCOUNTING_PERIOD_NOT_FOUND`. The service stays a **terminal consumer** — no
  outbox / no `period.closed.v1` emission this increment (grep-zero
  `KafkaTemplate` / publish in the close path); no write-back to `finance_db`.
- **AC-6** — `:ledger-service:check` BUILD SUCCESSFUL; **CI "Integration
  (finance-platform, Testcontainers)" GREEN** (the authoritative behavioural
  gate — real Kafka: close a period, then a late event into the closed window
  posts no entry). No deploy-wiring change is required (ledger-service is
  already wired by FIN-BE-007); the new `V2` migration runs in the existing
  finance Integration job.

## Related Specs

- `projects/finance-platform/specs/services/ledger-service/architecture.md` (this PR — governing; § Accounting Period + § Increment Scope decision)
- `projects/finance-platform/specs/services/account-service/architecture.md` (the blueprint mirrored — tenant gate / audit / Flyway parity)

## Related Contracts

- `projects/finance-platform/specs/contracts/http/ledger-api.md` (this PR — period endpoints + codes)
- `projects/finance-platform/specs/contracts/events/finance-ledger-events.md` (this PR — `period.closed.v1` emission still deferred note)
- `platform/error-handling.md` (this PR — claim `LEDGER_PERIOD_CLOSED` + add period codes)
- `docs/adr/ADR-MONO-008-finance-platform-bootstrap.md` § D3 (the v2 ledger story being deepened)

## Edge Cases

- **`covers` boundary** — a period `[from, to)` covers `postedAt == from` but NOT
  `postedAt == to` (half-open). The next period starts exactly at the previous
  `to` with no gap and no overlap.
- **Closing a window that includes the present** — permitted (the increment
  models the lock mechanism; a "period must have ended" policy is forward-
  declared). This is what makes the guard deterministically testable with the
  real clock: close a window covering now → the next posting into it is rejected.
- **No period defined** — `findCovering` returns empty → posting proceeds
  (net-zero; periods are optional, absence = unrestricted).
- **Late / replayed event into a closed window** — the consumer's posting raises
  `LEDGER_PERIOD_CLOSED` → `@RetryableTopic` exhausts → DLT (a real anomaly,
  surfaced not swallowed; the dedupe row is NOT written for a rejected entry, so
  a corrected replay after reopen-via-new-period remains possible).
- **Empty period (no entries in window)** — close still succeeds; the snapshot
  is empty (grand totals zero, in balance); `entryCount = 0`.
- **`@JdbcTypeCode(VARCHAR)` per `@Enumerated`** (status) — finance/erp precedent.
- **Snapshot money** — minor-units `BIGINT` + currency `VARCHAR(3)`, never float
  (F5); single-currency per the first increment (snapshot groups by currency).

## Failure Scenarios

- **F1 — a posting lands in a closed period** — would corrupt a closed-and-
  reported period. Guarded by the `PostJournalEntryUseCase` closed-covering check
  (AC-3); the Integration test proves a late event into a closed window posts no
  entry (→ DLT).
- **F2 — overlapping periods** — would make "which period owns this entry"
  ambiguous and double-count snapshots. Guarded by the non-overlap invariant on
  open (AC-2).
- **F3 — guard turns the happy path fail-closed** — a net-zero regression (every
  posting blocked when no period is closed) would break auto-journal. Guarded by
  the explicit net-zero requirement + an Integration assertion that the pre-period
  happy path posts normally (AC-3).
- **F4 — snapshot drift from the live trial balance** — a wrong close computation
  would record a false period-end. Guarded by AC-4 (snapshot grand totals == live
  trial balance at close, in balance).
- **F5 — accidental outbox / emission** — would violate the terminal-consumer
  decision and the spec's outbox sequencing. Guarded by AC-5 grep-zero
  `KafkaTemplate` / publish in the close path.
- **F6 — Docker-free `:check` passing but the guard/consumer path broken** — the
  unit/slice tests don't exercise real Kafka consume→guard→DLT; the
  Testcontainers Integration job is the authoritative gate (AC-6), per the
  finance/erp `§14` pattern.
