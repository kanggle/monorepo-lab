# TASK-FIN-BE-013 — ledger-service reconciliation ingest-time period-lock (7th increment: ingesting a statement dated in a CLOSED period is rejected)

**Status:** ready

**Type:** TASK-FIN-BE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Sonnet (one guard mirroring the just-shipped FIN-BE-012 pattern; the only subtlety is guard placement before any persist/match/emit + net-zero)

---

## Goal

Deliver the **ingest-time** counterpart of the FIN-BE-012 resolve period-lock
(forward-declared by `specs/services/ledger-service/architecture.md` § Increment
Scope + § Reconciliation § Period lock). Ingesting an external statement whose
**statement date** falls in a CLOSED accounting period is rejected up-front with
`RECONCILIATION_PERIOD_LOCKED` (**422**, the same code/status the 6th increment uses
for resolve). A closed month is closed to **new** reconciliation activity, not only to
resolving its existing discrepancies — together the 6th + 7th increments close a CLOSED
period to reconciliation on **both** sides.

This is a **tiny** increment: one guard in `IngestStatementUseCase`. It reuses the
EXISTING `ReconciliationPeriodLockedException` (FIN-BE-012; already mapped to 422 in
`GlobalExceptionHandler`), the EXISTING `AccountingPeriodRepository.findCovering`, and
the EXISTING `LocalDate` → start-of-day-UTC-instant mapping. **No new exception, no new
error code, no migration, no schema change.** Simpler than FIN-BE-012 — the statement
date is the use-case input, so there is no statement lookup.

## Scope

**Spec PR (this task → ready):** authored already —
`specs/services/ledger-service/architecture.md` (§ Increment Scope moves the
ingest-time lock IN as the 7th increment; § Reconciliation § Period lock retitled to
6th + 7th, ingest-guard paragraph added; § Resolve note; § Failure Modes row 26;
§ Layer Structure; § Testing; fintech F8 mapping; provenance) +
`specs/contracts/http/reconciliation-api.md` (ingest endpoint § 1 adds 422
`RECONCILIATION_PERIOD_LOCKED`; error-codes row now both sides; out-of-scope drops the
ingest-time lock) + `platform/error-handling.md` (7th increment in the ledger list;
`RECONCILIATION_PERIOD_LOCKED` row now resolve OR ingest).

**Impl PR — IN (ingest-time period-lock increment):**
- **Application** — `IngestStatementUseCase` (the existing one `@Transactional`): inject
  `AccountingPeriodRepository`. **Immediately after** the
  `ReconciliationAccounts.isReconcilable(code)` check (`RECONCILIATION_ACCOUNT_INVALID`)
  and **before** `ExternalStatement.open(...)` / `saveStatement` / matching / emit:
  ```java
  Instant at = command.statementDate().atStartOfDay(ZoneOffset.UTC).toInstant();
  if (accountingPeriodRepository.findCovering(tenantId, at, PeriodStatus.CLOSED).isPresent()) {
      throw new ReconciliationPeriodLockedException(
          "ingesting a statement dated in a CLOSED accounting period (statementDate="
              + command.statementDate() + ", account=" + code + ")");
  }
  ```
  Net-zero: `findCovering` empty (the common case, and always when no period is defined)
  → ingest proceeds byte-identically to FIN-BE-010. The guard runs before any write, so a
  locked ingest persists **nothing** (no statement, lines, matches, discrepancies, audit,
  or outbox events — the whole `@Transactional` is effectively a no-op via the early throw).
- **No new exception / mapping** — `ReconciliationPeriodLockedException` already exists
  (FIN-BE-012) and `GlobalExceptionHandler` already maps `RECONCILIATION_PERIOD_LOCKED`
  → 422.
- **Tests**: `IngestStatementUseCaseTest` (mock ports) — add: a statement date covered
  by a CLOSED period → `ReconciliationPeriodLockedException`, and assert
  `saveStatement` / `saveMatches` / `saveDiscrepancies` / `publishReconciliationCompleted`
  / `publishDiscrepancyDetected` / audit are NEVER called (the guard runs before any
  write); no covering period / no period defined → ingests normally (net-zero — verify the
  existing happy-path assertions still hold + a boundary date exactly on a period edge).
  **Integration** (Testcontainers, the authoritative gate) — see AC-3.

**Impl PR — OUT (still forward-declared):** fuzzy / N:M / split matching;
multi-currency statements; period **reopen** (which would re-open both the resolve and
the ingest paths).

## Acceptance Criteria

- **AC-1 (ingest period-lock)** — `POST .../reconciliation/statements` with a
  `statementDate` in a CLOSED accounting period is rejected with **422
  `RECONCILIATION_PERIOD_LOCKED`**; **nothing** is persisted (no statement row, no
  discrepancy) and **no** outbox event is emitted (the guard runs before any write).
  The guard keys on `command.statementDate()` mapped to its start-of-day UTC instant via
  the existing `AccountingPeriodRepository.findCovering(..., CLOSED)`.
- **AC-2 (net-zero)** — with no covering CLOSED period (the common case, and always when
  no period is defined), ingest proceeds exactly as in FIN-BE-010 (persist + match +
  OPEN discrepancies + emit). A unit test proves the net-zero branch + that the guard
  runs before any write on the locked branch (no repo/publisher interaction).
- **AC-3** — `:ledger-service:check` BUILD SUCCESSFUL; **CI "Integration
  (finance-platform, Testcontainers)" GREEN**: open + close a period covering date D's
  start-of-day-UTC instant → ingest a statement with `statementDate = D` → 422
  `RECONCILIATION_PERIOD_LOCKED`, and assert no statement / no discrepancy / no event;
  an ingest with a statement date NOT in any closed period → 201 (net-zero — matches +
  OPEN discrepancies as in FIN-BE-010); cross-tenant JWT → 403. No migration; no
  deploy-wiring change.

## Related Specs

- `projects/finance-platform/specs/services/ledger-service/architecture.md` (this PR — governing; § Reconciliation § Period lock)
- `projects/finance-platform/tasks/done/TASK-FIN-BE-012-ledger-service-reconciliation-period-lock.md` (the resolve-side sibling — same pattern)
- `rules/domains/fintech.md` § F8 (reconciliation immutability — governing)

## Related Contracts

- `projects/finance-platform/specs/contracts/http/reconciliation-api.md` (this PR — ingest endpoint 422 `RECONCILIATION_PERIOD_LOCKED`)
- `platform/error-handling.md` (this PR — 7th increment; `RECONCILIATION_PERIOD_LOCKED` both sides)

## Edge Cases

- **`LocalDate` → instant boundary** — `statementDate` is a `LocalDate`; mapped to its
  **start-of-day UTC instant** for the half-open `[from, to)` `findCovering` check (same
  mapping as FIN-BE-012; a date on the period's `from` day is covered, on `to` is the
  next period).
- **No period defined / OPEN period covers the date** — does NOT lock (net-zero; only a
  CLOSED period blocks). An OPEN period covering the date ingests normally.
- **Guard placement** — the guard MUST run before `saveStatement` (and matching/emit) so
  a locked ingest writes nothing; placing it after the account check keeps
  `RECONCILIATION_ACCOUNT_INVALID` (422) precedence for a non-clearing account (both 422
  but distinct codes; account validity is checked first).
- **Resolve already locked (6th)** — a discrepancy whose statement date is in a closed
  period was already un-resolvable (FIN-BE-012); this increment additionally prevents a
  **new** statement from being ingested into that closed period.

## Failure Scenarios

- **F1 — a statement is ingested into a closed period** — would add new reconciliation
  records (and emit events) into a frozen month. Guarded by the up-front ingest check
  (AC-1); an Integration assertion proves no statement/discrepancy/event on the locked
  branch.
- **F2 — the guard runs after a partial write** — would leave an orphan statement / emit
  an event for a rejected ingest. Guarded by placing the check before any
  persist/match/emit (AC-1) — the early throw rolls back the (empty) `@Transactional`.
- **F3 — the guard over-fires (breaks the happy path)** — would break FIN-BE-010's
  golden path. Guarded by the net-zero design (empty `findCovering` → proceed) + a unit
  test on the net-zero branch (AC-2).
- **F4 — Docker-free `:check` passes but the guard/atomicity is broken** — the unit test
  mocks the period repo; the Testcontainers Integration job exercises the real period
  close + ingest round-trip (and asserts no event emitted on the locked branch) and is
  the authoritative gate (AC-3).
