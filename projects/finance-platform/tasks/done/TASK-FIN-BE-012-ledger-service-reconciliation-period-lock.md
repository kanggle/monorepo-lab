# TASK-FIN-BE-012 — ledger-service reconciliation period-lock (6th increment: a discrepancy whose statement date is in a CLOSED period is immutable)

**Status:** done

**Type:** TASK-FIN-BE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Opus (cross-aggregate guard — period × reconciliation; the `LocalDate` → start-of-day-UTC instant boundary mapping; net-zero correctness on the operator-only F8 resolve path)

---

## Goal

Deliver the **reconciliation period-lock** increment forward-declared by
`specs/services/ledger-service/architecture.md` § Increment Scope + § Reconciliation
(TASK-FIN-BE-010's explicit deferred item). Once an accounting period is CLOSED, the
reconciliation outcomes dated in that period are **frozen with the books**: a
`ReconciliationDiscrepancy` whose **statement date** falls in a CLOSED period can no
longer be resolved — the operator `resolve` is rejected with
`RECONCILIATION_PERIOD_LOCKED` (**422**, the reconciliation analog of
`LEDGER_PERIOD_CLOSED`). A correction is recorded against the next (open) period, not
by mutating the closed month's record (F8 immutability extended to the period
boundary).

This is a **small, focused** increment: one guard in `ResolveDiscrepancyUseCase` + one
domain exception. It reuses the EXISTING `AccountingPeriodRepository.findCovering`
(the posting guard's own query) and the existing statement read — **no migration, no
new aggregate, no schema change**.

## Scope

**Spec PR (this task → ready):** authored already —
`specs/services/ledger-service/architecture.md` (§ Increment Scope moves period-lock
IN as the 6th increment + scopes it resolve-only; § Reconciliation new § Period lock —
guard site, the `LocalDate` → start-of-day-UTC mapping, net-zero, no-machinery; § Resolve
note; § Failure Modes row 25; § Layer Structure; § Testing; fintech F8 mapping; the
provenance note) + `specs/contracts/http/reconciliation-api.md` (resolve endpoint adds
422 `RECONCILIATION_PERIOD_LOCKED` + error-codes row; out-of-scope → ingest-time lock)
+ `platform/error-handling.md` (6th increment in the ledger list; flip
`RECONCILIATION_PERIOD_LOCKED` v2-planned → implemented, 422).

**Impl PR — IN (period-lock increment):**
- **Domain** — `domain/error/LedgerErrors.java`: add
  `ReconciliationPeriodLockedException extends LedgerDomainException` with code
  `RECONCILIATION_PERIOD_LOCKED`.
- **Application** — `ResolveDiscrepancyUseCase` (one `@Transactional`, the existing
  operator-only F8 resolve path): inject `AccountingPeriodRepository` +
  (already-injected) `ReconciliationRepository`. After loading the OPEN discrepancy
  and **before** `discrepancy.resolve(...)`:
  1. If `discrepancy.statementId()` is non-null, load the statement
     (`reconciliationRepository.findStatementById(statementId, tenantId)`).
  2. If the statement is present, map its `statementDate` (`LocalDate`) to the
     **start-of-day UTC instant** (`statementDate.atStartOfDay(ZoneOffset.UTC).toInstant()`).
  3. If `accountingPeriodRepository.findCovering(tenantId, thatInstant, PeriodStatus.CLOSED)`
     is present → throw `ReconciliationPeriodLockedException`.
  - **Net-zero**: `statementId` null OR statement absent OR `findCovering` empty → the
    guard does not fire and `resolve` proceeds **byte-identically to FIN-BE-010**.
- **Presentation** — `GlobalExceptionHandler`: map `RECONCILIATION_PERIOD_LOCKED` →
  **422** (add to the code→status map; the existing `LedgerDomainException` handler
  renders the envelope).
- **Tests**: `ResolveDiscrepancyUseCaseTest` (mock ports) — statement date covered by a
  CLOSED period → `RECONCILIATION_PERIOD_LOCKED`, **no** mutation/save; no covering
  period / OPEN-covering period / null statementId / statement-absent → resolves
  normally (net-zero, verify the boundary mapping with a date exactly on a period
  edge); **Integration** (Testcontainers, the authoritative gate) — see AC-3.

**Impl PR — OUT (still forward-declared):** a reconciliation **ingest-time** period
lock (reject ingesting a statement dated in a CLOSED period — this increment locks
*resolution*, not *ingest*); fuzzy / N:M / split matching; multi-currency statements;
period **reopen**.

## Acceptance Criteria

- **AC-1 (period-lock on resolve)** — resolving a discrepancy whose statement date
  falls in a CLOSED accounting period is rejected with **422
  `RECONCILIATION_PERIOD_LOCKED`**; the discrepancy stays **OPEN** (no mutation, no
  audit row). The guard keys on the discrepancy's owning statement's `statementDate`
  mapped to its start-of-day UTC instant, via the existing
  `AccountingPeriodRepository.findCovering(..., CLOSED)`.
- **AC-2 (net-zero)** — with no covering CLOSED period (the common case, and always
  when no period is defined), or when the discrepancy has no `statementId` / its
  statement is absent, `resolve` proceeds exactly as in FIN-BE-010 (OPEN→RESOLVED +
  audit). A unit test proves the net-zero branches + the OPEN-covering-period case
  (an OPEN period does NOT lock).
- **AC-3** — `:ledger-service:check` BUILD SUCCESSFUL; **CI "Integration
  (finance-platform, Testcontainers)" GREEN**: post a clearing-account entry → ingest
  a statement (statement date D) → an OPEN discrepancy → open + close a period whose
  window covers D's start-of-day-UTC instant → `resolve` → 422
  `RECONCILIATION_PERIOD_LOCKED`, discrepancy still OPEN; a discrepancy whose statement
  date is NOT in any closed period → `resolve` 200 (net-zero); cross-tenant JWT → 403.
  No migration; no deploy-wiring change.

## Related Specs

- `projects/finance-platform/specs/services/ledger-service/architecture.md` (this PR — governing; § Reconciliation § Period lock)
- `rules/domains/fintech.md` § F8 (no auto-close / reconciliation immutability — governing)

## Related Contracts

- `projects/finance-platform/specs/contracts/http/reconciliation-api.md` (this PR — resolve endpoint 422 `RECONCILIATION_PERIOD_LOCKED`)
- `platform/error-handling.md` (this PR — `RECONCILIATION_PERIOD_LOCKED` 422 implemented; 6th increment)

## Edge Cases

- **`LocalDate` → instant boundary** — `statementDate` is a `LocalDate` (no time/zone);
  the period window is `[from, to)` of UTC instants. The lock maps the date to its
  **start-of-day UTC instant**; a statement dated any day in January maps into the
  `[Jan 1 00:00Z, Feb 1 00:00Z)` period. A date exactly on the period's `from` boundary
  is covered (from-inclusive); on `to` is the next period (to-exclusive) — half-open
  parity with the posting guard.
- **No statement / null statementId** — a discrepancy with no resolvable statement →
  no lock (net-zero; the guard cannot determine a period, so it does not block).
- **OPEN period covers the date** — does NOT lock (only a CLOSED period freezes).
- **Already-resolved** — `RECONCILIATION_ALREADY_RESOLVED` (409) takes precedence
  naturally only if the discrepancy is already RESOLVED; the period-lock applies to an
  OPEN discrepancy being resolved into a closed window. (Order: the use case checks the
  period lock before/independently of the `resolve()` OPEN guard — both are pre-mutation.)
- **Resolve-only** — ingesting a statement dated in a closed period still records OPEN
  discrepancies (they are simply locked from resolution); an ingest-time lock is OUT.

## Failure Scenarios

- **F1 — a closed month's reconciliation is mutated** — would break the period
  immutability (F8 extended to the period boundary). Guarded by the resolve-path
  period check (AC-1); an Integration assertion proves the discrepancy stays OPEN.
- **F2 — the guard blocks the happy path (over-fires)** — would break FIN-BE-010's
  golden path. Guarded by the net-zero design (empty `findCovering` / no statement /
  OPEN period → proceed) + a unit test on each net-zero branch (AC-2).
- **F3 — wrong period attribution via a naive date conversion** — a zone-dependent
  mapping could mis-attribute a boundary date. Guarded by the fixed start-of-day **UTC**
  mapping (the ledger is UTC throughout) + a boundary unit test.
- **F4 — Docker-free `:check` passes but the cross-aggregate guard is broken** — the
  unit test mocks the period repo; the Testcontainers Integration job exercises the
  real period close + statement + discrepancy round-trip and is the authoritative gate
  (AC-3).
