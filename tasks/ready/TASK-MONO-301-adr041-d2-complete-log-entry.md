# TASK-MONO-301 — Add the D2 COMPLETE / D3 DESCOPED entries to the ADR-MONO-041 acceptance log

**Status:** ready
**Type:** TASK-MONO (root — shared `docs/adr/`) · docs accuracy
**Analysis model:** Opus 4.8 / **Recommended impl model:** Haiku (1-table-row doc fix)

## Goal

`docs/adr/ADR-MONO-041-container-image-build-standard.md`'s acceptance log was frozen at
the pilot stage — its last entry reads **"D2 PILOT (iam) … Remaining 36 services re-based in
a follow-up."** But D2 finished the next day: TASK-MONO-294 (done 2026-06-18) re-based all 41
service Dockerfiles onto `monorepo/java-service-base:v1`. The stale log is not just inaccurate
— it actively misled a backlog-discovery scan into surfacing the (already-done) "re-base 36
services" sweep as an actionable candidate. Add the closing log entries so the ADR reflects
reality and the phantom cannot recur.

## Scope

Edit one file: `docs/adr/ADR-MONO-041-container-image-build-standard.md` — append two rows to
the § 6 acceptance log after the "D2 PILOT (iam)" row:
- **2026-06-18 | D2 COMPLETE** — all 41 services `FROM monorepo/java-service-base:v1`
  (TASK-MONO-294; PRs #1796/#1800–#1804/#1806; CI base-step in all 3 image-building workflows).
- **2026-06-18 | D3 DESCOPED** — context narrowing (AC-4) deferred as optional (low reward vs
  multi-surface risk; iam-only narrowing is the clean subset if reopened).

Docs-only; no code, no Dockerfile, no CI change.

## Acceptance Criteria

- [ ] **AC-1** — The log carries a D2 COMPLETE entry naming TASK-MONO-294 + the D2 PRs; the "Remaining 36 … follow-up" wording is no longer the terminal D2 state.
- [ ] **AC-2** — A D3 DESCOPED entry records the deferral rationale (matches TASK-MONO-294 closure).
- [ ] **AC-3** — Docs-only; CI path-filter skips code jobs.

## Related Specs / Contracts

- `docs/adr/ADR-MONO-041-container-image-build-standard.md` (the ADR corrected)
- `tasks/done/TASK-MONO-294-container-image-build-standard-sweep.md` (authoritative completion record)

## Edge Cases / Failure Scenarios

- N/A — documentation accuracy; no runtime behavior. The actual sweep (A1) was already complete; this only closes the stale log that caused it to be re-discovered.
