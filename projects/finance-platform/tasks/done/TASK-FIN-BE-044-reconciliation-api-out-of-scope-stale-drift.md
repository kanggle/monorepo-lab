# Task ID

TASK-FIN-BE-044

# Title

reconciliation-api.md "Out of scope" stale drift — reverse cross-currency (FIN-BE-027) + FIFO/lot-level (FIN-BE-022–029) already shipped

# Status

done

# Owner

backend

# Task Tags

- contract
- code

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

`specs/contracts/http/reconciliation-api.md` § "Out of scope (forward-declared — later
increments)" still lists two increments that have since **shipped**, so the contract
under-claims implemented behaviour. The sibling contract `ledger-api.md` § "Now in scope"
already records both as implemented — only reconciliation-api.md drifted. Same class as
TASK-FIN-BE-043 (ledger-api FX-feed out-of-scope removal) and TASK-MONO-301 (stale ADR
acceptance log): a doc asserting work is undone when code proves otherwise.

**Stale "Out of scope" bullets (verified shipped)**:

| OOS bullet | Reality (code evidence) |
|---|---|
| "Foreign-external → KRW-internal cross matching (the reverse direction) … the reverse is a separate forward-declarable increment" | **Shipped** — TASK-FIN-BE-027 (19th increment), `ReconciliationMatcher#findReverseCrossCurrencyCandidate` (`ledger-service/.../domain/reconciliation/ReconciliationMatcher.java`), the strict mirror of the 14th-incr base-external → foreign-internal pass (FIN-BE-021). Both `tasks/done/`. |
| "FIFO / lot-level cost basis — a separate, larger increment" | **Shipped** — TASK-FIN-BE-022–029 (FX cost-flow FIFO lot tracking, position-lot acquisition, FIFO settlement consumption, revaluation lot distribution, per-account override, open-lots read). All `tasks/done/`; `RecordFxAcquisitionLots` / `GetFxPositionLotsUseCase` / FxCostFlow config use cases live. |

**Genuinely-still-OOS bullets (KEPT, verified unimplemented)**:

- Fuzzy / N:M / split matching — matcher is 1:1 only (`ReconciliationMatcher` "First increment = 1:1").
- period **reopen** — FIN-BE-008 "There is no reopen path" / `ACCOUNTING_PERIOD_ALREADY_CLOSED`; no reopen endpoint.
- Per-currency-pair / per-account FX **tolerance** granularity — only FIN-BE-020 per-tenant tolerance exists (`ReconciliationFxToleranceConfig`); no per-account/per-pair tolerance.
- In-repo **event-feed** consumer — FIN-BE-005 is a spec-only operator read-API consumer, not a consumer of the reconciliation event topics.

# Scope

## In Scope

- `projects/finance-platform/specs/contracts/http/reconciliation-api.md` § "Out of scope":
  - Remove the two shipped bullets (reverse cross-currency; FIFO/lot-level).
  - Add a "Now in scope (implemented — no longer forward-declared)" note naming
    FIN-BE-027 + FIN-BE-022–029, cross-referencing `ledger-api.md` § "Now in scope"
    (sibling style — matches the FIN-BE-043 ledger-api block).
  - Keep the four genuinely-OOS bullets unchanged.

## Out of Scope

- Any `apps/` code change — the implementations already exist and are authoritative; this is a docs-only contract reality-alignment.
- Re-litigating which increments are OOS beyond the four verified-unimplemented bullets.
- `ledger-api.md` (already correct).

# Acceptance Criteria

- [ ] reconciliation-api.md "Out of scope" no longer lists "Foreign-external → KRW-internal cross matching (the reverse direction)" as forward-declared.
- [ ] reconciliation-api.md "Out of scope" no longer lists "FIFO / lot-level cost basis" as a future increment.
- [ ] A "Now in scope" note names TASK-FIN-BE-027 and TASK-FIN-BE-022–029 and cross-references ledger-api.md.
- [ ] The four genuinely-OOS bullets (fuzzy/N:M/split; period reopen; per-pair/per-account tolerance; in-repo event-feed consumer) remain present and unchanged in intent.
- [ ] No `apps/` code or other spec file changed by this task.

# Related Specs

- `projects/finance-platform/specs/contracts/http/reconciliation-api.md` (edited)
- `projects/finance-platform/specs/contracts/http/ledger-api.md` § "Now in scope" (authoritative sibling — unchanged)

# Related Contracts

- `reconciliation-api.md` (the edited contract). No event/HTTP wire shape changes — narrative-only.

# Edge Cases

- The "Now in scope" note must not imply the four still-OOS items are done — phrase it as additive to, not a replacement of, the remaining OOS list.
- FIFO citation spans a task range (022–029); cite the range, not a single id, to stay accurate if a reader checks `tasks/done/`.

# Failure Scenarios

- **F1 — over-removal**: deleting a genuinely-OOS bullet (e.g. period reopen) would falsely claim unbuilt behaviour. Guarded by the verified KEPT list above.
- **F2 — silent re-introduction**: leaving the future-tense prose would re-surface as a drift finding next `/refactor-spec` cycle (the FIN-BE-043 / BE-316 lesson). Guarded by adding the explicit "Now in scope" note.
