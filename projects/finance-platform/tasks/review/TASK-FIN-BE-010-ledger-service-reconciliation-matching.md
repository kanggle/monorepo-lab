# TASK-FIN-BE-010 — ledger-service reconciliation matching (4th increment: external-statement matching + discrepancy queue, F8 no-auto-close)

**Status:** review

**Type:** TASK-FIN-BE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Opus (complex domain — matching engine, discrepancy state machine, F8 no-auto-close policy, outbox emission)

---

## Goal

Deliver the **reconciliation matching** increment forward-declared by
`specs/services/ledger-service/architecture.md` § Increment Scope (and modelled as
a placeholder by account-service `reconciliation_discrepancy`, fintech F8). The
ledger reconciles its **clearing-account** entries (e.g. `CASH_CLEARING`) against an
ingested **external statement** (bank / PG settlement lines): 1:1 match by amount +
currency; anything unmatched on either side becomes a **`ReconciliationDiscrepancy`**
in an **operator review queue**.

**F8 — no auto-close**: a detected discrepancy is recorded `OPEN` and surfaced; the
system NEVER auto-resolves it or adjusts the difference. An operator manually
resolves each discrepancy (matched-manually / written-off / accepted) with a reason
+ actor (audit). The reconciliation **emits** the catalogued events
(`finance.ledger.reconciliation.completed.v1` + `.discrepancy.detected.v1`) via the
existing per-service outbox (TASK-FIN-BE-009) so an operator-alerting consumer can
react — the forward interface; no in-repo consumer yet.

## Scope

**Spec PR (this task → ready):** authored already —
`specs/services/ledger-service/architecture.md` (§ Increment Scope moves
reconciliation matching IN; new § Reconciliation — model, matching engine, F8
no-auto-close, period-lock deferred; § REST endpoints + § Failure Modes + § Layer
Structure + § fintech F8 mapping forward-decl → ✅) + a new
`specs/contracts/http/reconciliation-api.md` (ingest / resolve / read endpoints) +
`specs/contracts/events/finance-ledger-events.md` (the two reconciliation events
added to § Published — emitted) + `platform/error-handling.md` (claim
`RECONCILIATION_DISCREPANCY` as the recorded-entity marker + add
`RECONCILIATION_STATEMENT_NOT_FOUND` / `RECONCILIATION_DISCREPANCY_NOT_FOUND` /
`RECONCILIATION_ALREADY_RESOLVED` / `RECONCILIATION_ACCOUNT_INVALID`).

**Impl PR — IN (first reconciliation increment):**
- **Domain (pure)** — package `com.example.finance.ledger.domain.reconciliation`:
  - `ExternalStatement` (aggregate: `statementId`, `tenantId`, `ledgerAccountCode`
    [the clearing account reconciled], `source` [BANK/PG/...], `statementDate`,
    `lines`) + `ExternalStatementLine` (`lineId`, `externalRef`, `Money`,
    `direction` DEBIT/CREDIT vs the account, `valueDate`, `description?`,
    `matchStatus` UNMATCHED/MATCHED).
  - `ReconciliationMatch` (`statementLineId` ↔ internal `journalEntryId` +
    `ledgerAccountCode` + `Money`).
  - `ReconciliationDiscrepancy` (`discrepancyId`, `tenantId`, `ledgerAccountCode`,
    `type` {UNMATCHED_EXTERNAL, UNMATCHED_INTERNAL, AMOUNT_MISMATCH},
    `externalRef?`, `journalEntryId?`, `expectedMinor`, `actualMinor`, `currency`,
    `status` {OPEN, RESOLVED}, `resolution?` {resolutionType
    {MATCHED_MANUALLY, WRITTEN_OFF, ACCEPTED}, note, resolvedBy, resolvedAt},
    `detectedAt`) — mirrors account-service `reconciliation_discrepancy` columns.
    State machine OPEN→RESOLVED only via the operator use case (never auto).
  - `ReconciliationMatcher` (pure): given the external lines + the internal
    clearing-account ledger lines (within scope), produce matches + discrepancies.
    First increment = **1:1 by (amount, currency, direction)**; an external line
    with no internal counterpart → UNMATCHED_EXTERNAL; an internal entry with no
    external counterpart → UNMATCHED_INTERNAL. Deterministic, exhaustively unit-tested.
  - Outbound ports `ReconciliationRepository` (+ `ReconciliationAccounts` —
    which codes are reconcilable clearing accounts: CASH_CLEARING / SETTLEMENT_SUSPENSE).
- **Application**:
  - `IngestStatementUseCase` (`@Transactional`): validate the account is a
    reconcilable clearing account (`RECONCILIATION_ACCOUNT_INVALID`), persist the
    statement + lines, run `ReconciliationMatcher` against the internal ledger lines
    on that account (reuse the existing per-account line query, scoped to the
    statement's date window), persist matches + **OPEN** discrepancies + audit, and
    append the outbox events (`reconciliation.completed` + one
    `reconciliation.discrepancy.detected` per discrepancy) in the SAME Tx. **No
    auto-close** — discrepancies stay OPEN.
  - `ResolveDiscrepancyUseCase` (`@Transactional`, operator): require `OPEN`
    (`RECONCILIATION_ALREADY_RESOLVED` otherwise), set RESOLVED + resolutionType +
    note + resolvedBy + audit. Never auto-invoked.
  - `QueryReconciliationUseCase` (read): statement detail + summary; the
    discrepancy queue (filter by status); discrepancy detail.
  - Extend `LedgerEventPublisher` (FIN-BE-009 port) with `publishReconciliationCompleted`
    + `publishDiscrepancyDetected` (reuse the canonical envelope + `ledger_outbox`;
    the relay's generic `TopicResolver finance.ledger.X → .v1` already covers the
    new event types — no relay change).
- **Presentation**: `ReconciliationController` —
  `POST /api/finance/ledger/reconciliation/statements` (ingest),
  `POST /api/finance/ledger/reconciliation/discrepancies/{id}/resolve`,
  `GET /api/finance/ledger/reconciliation/statements/{id}`,
  `GET /api/finance/ledger/reconciliation/discrepancies` (?status=),
  `GET /api/finance/ledger/reconciliation/discrepancies/{id}` — DTOs (money minor
  string) + `GlobalExceptionHandler` codes (404/409/422). `.authenticated()` +
  tenant gate (parity).
- **Persistence (Flyway `V4__create_reconciliation.sql`)**: `reconciliation_statement`,
  `reconciliation_statement_line`, `reconciliation_match`, `reconciliation_discrepancy`
  (InnoDB/utf8mb4; money BIGINT minor + currency; discrepancy columns mirror the
  account-service placeholder) + JPA entities/adapters.
- **Tests**: `ReconciliationMatcherTest` (1:1 match; unmatched-external; unmatched-
  internal; amount-mismatch; multi-line); application unit (ingest persists matches
  + OPEN discrepancies + emits events + NO auto-close; resolve OPEN→RESOLVED, re-
  resolve rejected; account-invalid); `@WebMvcTest ReconciliationControllerSliceTest`
  (error envelopes); **Integration** (Testcontainers, the authoritative gate): post
  ledger entries (TOPUP/TRANSFER → CASH_CLEARING lines) → ingest an external
  statement (some lines match, some don't) → assert matches + **OPEN** discrepancies
  recorded (never auto-closed) + the queue read shape; **consume**
  `finance.ledger.reconciliation.completed.v1` + `.discrepancy.detected.v1`; resolve
  a discrepancy → RESOLVED; re-resolve → 409; ingest on a non-clearing account → 422.

**Impl PR — OUT (still forward-declared):** reconciliation **period lock**
(`RECONCILIATION_PERIOD_LOCKED` — a discrepancy whose statement date is in a CLOSED
accounting period is immutable; correction via next period); fuzzy / N:M matching;
multi-currency statements; manual journal posting; an external GL/AP / bank-feed
*consumer* (this increment ships the producer side only); a console reconciliation view.

## Acceptance Criteria

- **AC-1 (matching)** — ingesting an external statement matches its lines 1:1
  against the internal clearing-account ledger entries by (amount, currency,
  direction); each match is recorded. A `ReconciliationMatcher` unit test proves the
  1:1 + unmatched cases.
- **AC-2 (discrepancy queue, F8 no-auto-close)** — an unmatched external line →
  `UNMATCHED_EXTERNAL`; an unmatched internal entry → `UNMATCHED_INTERNAL`; each is
  recorded **OPEN** and NEVER auto-resolved or adjusted. An Integration assertion
  proves discrepancies remain OPEN after ingest (no auto-close).
- **AC-3 (manual resolve)** — `POST …/discrepancies/{id}/resolve` transitions
  OPEN→RESOLVED with resolutionType + note + resolvedBy (audit); a second resolve →
  `RECONCILIATION_ALREADY_RESOLVED`. There is no auto-resolve path.
- **AC-4 (emission)** — ingest appends, in the SAME Tx, one
  `finance.ledger.reconciliation.completed.v1` (statement summary) + one
  `finance.ledger.reconciliation.discrepancy.detected.v1` per discrepancy; the
  relay publishes them (Integration consumes both). Reuses the FIN-BE-009 outbox
  (`OutboxRow` path) — no new outbox infra, `OutboxAutoConfiguration` still excluded.
- **AC-5 (read + tenant + guard)** — the statement/discrepancy reads return the
  `reconciliation-api.md` shapes; cross-tenant JWT → 403; unknown statement/
  discrepancy → 404; ingest on a non-clearing account → 422
  `RECONCILIATION_ACCOUNT_INVALID`. No write-back to `finance_db`.
- **AC-6** — `:ledger-service:check` BUILD SUCCESSFUL; **CI "Integration
  (finance-platform, Testcontainers)" GREEN** (real Kafka: ingest→match→discrepancy
  queue→resolve + emitted events round-trip). `V4` migration runs in the existing
  finance Integration job; no deploy-wiring change.

## Related Specs

- `projects/finance-platform/specs/services/ledger-service/architecture.md` (this PR — governing; § Reconciliation)
- `projects/finance-platform/specs/services/account-service/architecture.md` (the `reconciliation_discrepancy` placeholder + F8 policy it models)
- `rules/domains/fintech.md` § F8 (no auto-close — governing)

## Related Contracts

- `projects/finance-platform/specs/contracts/http/reconciliation-api.md` (this PR — new)
- `projects/finance-platform/specs/contracts/events/finance-ledger-events.md` (this PR — the two reconciliation events emitted)
- `platform/error-handling.md` (this PR — reconciliation codes claimed/added)

## Edge Cases

- **No external source previously** — account-service's `reconciliation_discrepancy`
  was a forward-declared placeholder (no v1 source); this increment is the first
  real matching against an ingested statement.
- **1:1 only (first increment)** — N:M / split / fuzzy matching is deferred; an
  amount that could match multiple internal entries matches the first deterministic
  candidate (documented), the rest stay unmatched (→ discrepancy, operator review).
- **Direction** — an external statement line carries a direction relative to the
  clearing account; matching respects it (a deposit credit ↔ the account's debit).
- **Empty statement / all-matched** — ingest succeeds; zero discrepancies;
  `reconciliation.completed` still emitted (discrepancyCount 0).
- **F8 never auto-close** — the matcher only RECORDS discrepancies; resolution is
  operator-only. No code path closes/adjusts a discrepancy automatically.
- **Money** — minor-units `BIGINT` + currency; never float (F5). No new regulated PII.

## Failure Scenarios

- **F1 — auto-closing a discrepancy** — would risk fund leakage / accounting
  inconsistency (F8 violation). Guarded by the OPEN-only-on-detect design + the
  operator-only resolve use case (AC-2/AC-3); an Integration assertion proves
  discrepancies stay OPEN after ingest.
- **F2 — arbitrary difference adjustment** — the matcher never posts a balancing
  entry or mutates a journal entry; a difference is RECORDED, not adjusted (F8).
- **F3 — emission diverges from the recorded reconciliation** — the outbox events
  are appended in the ingest Tx (transactional outbox, FIN-BE-009) so the feed
  cannot diverge from the persisted matches/discrepancies.
- **F4 — reconciling a non-clearing account** — would mis-classify wallet movements
  as discrepancies. Guarded by `RECONCILIATION_ACCOUNT_INVALID` (AC-5).
- **F5 — Docker-free `:check` passes but matching/emission broken** — the unit
  tests don't exercise real Kafka emit or the JPA round-trip; the Testcontainers
  Integration job is the authoritative gate (AC-6).
