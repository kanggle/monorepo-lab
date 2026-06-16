# TASK-FIN-BE-039 — fx_rate_quote_history append-only audit trail

**Status:** done
**Domain:** finance · **Service:** ledger-service · **Type:** backend persistence (audit trail)
**Parent:** ADR-002 (realtime FX rate feed, ACCEPTED 2026-06-15) — § 3.1 roadmap item 3 (deferred): *"append-only `fx_rate_quote_history`"*. Builds on FIN-BE-031 (cache + poller), FIN-BE-038 (real Frankfurter adapter). The ledger spec already forward-declares this table (see Related Specs) — this task closes the spec-ahead-of-code gap.

> **Task number:** FIN-BE-039 (finance FIN-BE counter; 038 is the current max).

## Goal

Persist an **append-only audit trail** of every FX rate quote the scheduled poller fetches, so the
provenance of an auto-applied rate (provider · as-of · fetched-at) is queryable **historically**, not
just as the latest-only cache. The current `fx_rate_quote` table is a last-write-wins upsert keyed on
the currency pair — it keeps only the newest quote, losing the trail the `audit-heavy` / `regulated`
trait requires ("a P&L line must answer *where did this rate come from*", ADR-002 § Context).

## Scope
`ledger-service` only. **Additive, net-zero**: a NEW table + a NEW append on the poller path; the
existing `fx_rate_quote` latest-upsert + every operator/settlement/revaluation/reconciliation path is
**byte-unchanged**. Out of scope (still-deferred, keep the FIN-BE-033 boundary): a history **read /
per-pair drill** endpoint, the console FX **dashboard**, per-tenant rate override, ShedLock
single-leader poller guard.

### Changes
- **Flyway `V13__add_fx_rate_quote_history.sql`** — a NEW append-only table mirroring `fx_rate_quote`'s
  columns (`base_currency VARCHAR(3)`, `foreign_currency VARCHAR(3)`, `rate DECIMAL(20,8)`,
  `as_of DATETIME(6)`, `source VARCHAR(64)`, `fetched_at DATETIME(6)`) **plus a surrogate PK**
  (`id BIGINT AUTO_INCREMENT PRIMARY KEY` — append-only ⇒ many rows per pair, so NOT the
  pair composite PK). Add an index on `(base_currency, foreign_currency, fetched_at)` for future
  time-ordered drill. MySQL 8 / InnoDB / utf8mb4 — parity with V1..V12. ADDITIVE, no backfill, no
  change to any existing table.
- **Domain** — `FxRateQuoteHistory` (value/entity, mirroring `FxRateQuote`) +
  `FxRateQuoteHistoryRepository` port (a single `append(...)`/`save(...)` method — no update/delete:
  append-only). Mirror the existing `FxRateQuote` / `FxRateQuoteRepository` shape.
- **Infrastructure** — `FxRateQuoteHistoryJpaEntity` + `FxRateQuoteHistoryJpaRepository` +
  `FxRateQuoteHistoryRepositoryImpl`, mirroring the `FxRateQuote*` JPA trio.
- **Poller** — in `RefreshFxRateQuotesUseCase.refresh()`, after the existing latest-upsert
  `fxRateQuoteRepository.save(...)`, also **append** the same quote to history
  (`fxRateQuoteHistoryRepository.append(...)`), within the SAME `@Transactional` and the SAME
  per-pair try/catch (one pair's failure must not abort the rest — AC preserved). The latest-upsert
  call and its arguments stay byte-identical; history is an ADD.

## Acceptance Criteria
- A `mode=stub`/`real` poll run that upserts N pairs also writes **N** new `fx_rate_quote_history`
  rows (one per fetched quote), each carrying the same `rate`/`as_of`/`source`/`fetched_at` as the
  latest row written that run.
- **Append-only**: two successive poll runs for the same pair (different `as_of`/`fetched_at`) leave
  the `fx_rate_quote` row count for that pair at 1 (latest) but the `fx_rate_quote_history` row count
  at 2 (both retained, time-ordered by `fetched_at`).
- **Net-zero**: with the default config (`mode=noop`, `enabled=false`) the poller bean is not created
  → zero history rows; every existing FX/settlement/revaluation/reconciliation test is unaffected.
- A per-pair failure still skips only that pair for BOTH latest and history (per-pair try/catch
  unchanged); other pairs' latest+history are written.
- Unit/IT GREEN (`:projects:finance-platform:apps:ledger-service:test`). A Testcontainers
  `@SpringBootTest` IT (or the existing FX-feed IT extended) asserts the append-only behavior across
  two runs. (Docker-gated ITs run on CI Linux; locally they may be excluded — note it.)

## Related Specs / Contracts
- `projects/finance-platform/specs/services/ledger-service/architecture.md` (§ "FX rate feed
  (ADR-002 remainder)" line ~447 forward-declares `fx_rate_quote_history` — move it from "remainder"
  to implemented; add the table to the persistence/file-tree section).
- `projects/finance-platform/specs/contracts/http/ledger-api.md` (line ~626 forward-declares the same
  — reconcile: the append-only trail now exists; the history **read** endpoint stays deferred).
- `projects/finance-platform/docs/adr/ADR-002-realtime-fx-rate-feed.md` (§ 3.1 item 3 — mark
  `fx_rate_quote_history` delivered; console / per-tenant / read-drill stay deferred).

## Edge Cases
- High poll cadence → fast history growth: acceptable for v1 (no retention/pruning policy this task;
  note it as a future op concern — do NOT add pruning here).
- `as_of` ties across runs (provider returns same ECB date on weekend/holiday): both runs still
  append distinct rows (distinct `fetched_at` + surrogate id) — that IS the audit trail (we polled
  twice; both fetches recorded).
- Same `@Transactional` ⇒ if the history append throws, the latest upsert for that pair rolls back
  too (consistent per-pair) and the per-pair catch logs + continues to the next pair.

## Failure Scenarios
- **Not implemented**: the latest-only cache silently overwrites prior quotes → no historical
  provenance → an auto-applied rate's origin cannot be reconstructed for a past period close
  (regulated/audit gap).
- **Wrong PK (reuse pair composite PK)**: would make history a latest-only upsert too (no trail) —
  the surrogate PK + AC "two runs → two rows" guard against this.
- **Non-net-zero**: appending outside the poller, or changing the latest-upsert call, could perturb
  the operator path — scope strictly to an additive append on the existing poller loop.
