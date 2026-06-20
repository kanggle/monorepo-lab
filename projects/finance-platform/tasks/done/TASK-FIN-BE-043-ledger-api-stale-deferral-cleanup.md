# TASK-FIN-BE-043 — Correct stale "Out of scope" deferrals in ledger-api.md

**Status:** done
**Type:** docs (spec accuracy — contract documentation)
**Parent:** ADR-002 (realtime FX rate feed) roadmap completion

## Goal

`specs/contracts/http/ledger-api.md` § "Out of scope (forward-declared — later increments)" still
lists five FX-rate-feed items as deferred that have **all shipped**. The stale block even contradicts
the adjacent "Now in scope" block (the per-pair history drill is listed in *both*). Move every shipped
item into "Now in scope" so the contract doc tells the truth about what exists.

## Scope

Edit one file: `projects/finance-platform/specs/contracts/http/ledger-api.md`.

Remove from "Out of scope" (all verified shipped):

| Stale deferral | Actual status |
|---|---|
| real public FX API provider schema | **TASK-FIN-BE-038 done** — `RealFxRateProviderAdapter` (`mode=real`, Frankfurter) |
| ShedLock single-leader poller guard (listed twice) | **TASK-FIN-BE-041 done** — `V14__create_shedlock_table.sql`, `SchedulerConfig`, `FxRateFeedPoller`, ShedLock wiring test |
| history read / per-pair drill endpoint "stays deferred" | **TASK-FIN-BE-040 done** — `/{foreignCurrency}/history` §14.1 (also already in "Now in scope") |
| console FX history drill tab (PC-FE) | **TASK-PC-FE-104 done** — `ledger-fx-rate-history-drill` |
| per-tenant rate override | **TASK-FIN-BE-042 done** — `FxRateOverride`, `SetFxRateOverrideUseCase`, `V15__add_fx_rate_override.sql` |

Preserve genuine deferrals: manual-posting idempotency conflict / maker-checker approval / bulk
posting; period reopen; proceeds-amount input / bulk-all-positions / period-close auto-hook /
configurable base currency; reconciliation fuzzy / N:M / split + per-pair/per-account FX-tolerance.

## Acceptance Criteria

- [ ] **AC-1** — The five shipped items no longer appear under "Out of scope"; the "Also out of scope" paragraph (all three items shipped) is removed.
- [ ] **AC-2** — Each shipped item is reflected in "Now in scope (implemented)" with its task reference.
- [ ] **AC-3** — Genuine deferrals (reconciliation granularity, period reopen, manual-posting approval, proceeds-amount/bulk/base-currency) remain intact.
- [ ] **AC-4** — Docs-only change; no code touched. CI path-filter skips code jobs.

## Related Specs / Contracts

- `projects/finance-platform/specs/contracts/http/ledger-api.md` (the file corrected)
- ADR-002 § 3.1 roadmap (FX rate feed increments)

## Edge Cases / Failure Scenarios

- N/A (documentation accuracy correction; no runtime behavior).
