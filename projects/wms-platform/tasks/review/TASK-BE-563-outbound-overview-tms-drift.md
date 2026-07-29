# Task ID

TASK-BE-563

# Title

Reconcile outbound-service's own overview.md with the TMS side-channel retirement

# Status

review

# Owner

backend

# Task Tags

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

If any section is missing or incomplete, this task must not be implemented.

---

# Goal

`TASK-BE-562` reconciled 15 sibling-spec / project-doc references to the retired outbound-service TMS push, but explicitly scoped out `outbound-service`'s own specs on the assumption they were "already correct" (updated by `TASK-BE-560`). That assumption was wrong for one file: `projects/wms-platform/specs/services/outbound-service/overview.md` still describes TMS as a live integration in 6 places (Stack row, a Responsibilities bullet, the Public surface table's `HTTP outbound` row, Key invariant #6, the Dependent Systems list, and the Out-of-scope bullet), even though `architecture.md` and `external-integrations.md` in the same service were correctly updated. This task closes that single-file gap.

---

# Scope

## In Scope

- `projects/wms-platform/specs/services/outbound-service/overview.md`:
  - `## Service identity` Stack row — drop "TMS adapter" from the stack list.
  - `## Responsibilities` — remove the "Hand off shipment-ready notification to external TMS" bullet (or mark it retired, matching how `architecture.md`/`external-integrations.md` phrase the retirement).
  - `## Public surface` table — remove the `HTTP outbound | TMS adapter (R4j wrap) | ... ` row.
  - `## Key invariants` #6 — remove or repoint the TMS-handover invariant (the surviving invariant set should match `architecture.md`'s current post-retirement list).
  - `## Dependent Systems` — remove "TMS (external HTTP, R4j wrap)".
  - `## Out of scope (v1)` — the "Carrier rating / TMS quote — external TMS 책임" bullet: keep or reword only if it still makes sense post-retirement (there is no more TMS relationship to carve this out from); otherwise remove it.

## Out of Scope

- `architecture.md`, `external-integrations.md`, `database-design.md` in the same service — already correct, do not re-edit.
- Any other service's specs — `TASK-BE-562` already covers those.
- Any code, build, or compose file — this task is exactly one markdown file.

---

# Acceptance Criteria

- [ ] `grep -ni "tms" projects/wms-platform/specs/services/outbound-service/overview.md` returns zero matches (or, if any bullet is kept, it is explicitly past-tense/retired — not describing a live integration).
- [ ] No heading added or removed; only the 6 identified lines/rows are edited or removed.
- [ ] `overview.md`'s remaining content stays consistent with `architecture.md`'s current (already-correct) invariant list — no new contradiction introduced.
- [ ] wms doc-lint / dead-ref / anchor-check CI guards GREEN.

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read `PROJECT.md`, then load `rules/common.md` plus any `rules/domains/<domain>.md` and `rules/traits/<trait>.md` matching the declared classification. Unknown tags are a Hard Stop per `CLAUDE.md`.

- `projects/wms-platform/specs/services/outbound-service/architecture.md` (the authoritative post-retirement statement `overview.md` must agree with)
- `projects/wms-platform/specs/services/outbound-service/external-integrations.md`

# Related Skills

- `.claude/skills/backend/refactoring/SKILL.md`

---

# Related Contracts

None — this is a one-file docs correction with no contract surface.

---

# Target Service

- `outbound-service` (docs only)

---

# Architecture

Follow:

- `projects/wms-platform/specs/services/outbound-service/architecture.md`

---

# Implementation Notes

Found as a byproduct of `TASK-BE-562`'s implementation — that task's own scope explicitly excluded re-editing `outbound-service/*`, so this file was flagged rather than fixed there.

---

# Edge Cases

- The `Out of scope (v1)` TMS-carve-out bullet may simply be deleted rather than reworded, since there is no longer a TMS relationship to be "out of scope" relative to.

---

# Failure Scenarios

- Editing `architecture.md` or `external-integrations.md` "for consistency" would violate this task's Out of Scope and re-touch already-correct files — if a real contradiction is found there, stop and report rather than expanding scope.

---

# Test Requirements

- None (docs-only). Run doc-lint / dead-ref CI guards.

---

# Definition of Done

- [ ] Implementation completed
- [ ] Contracts unchanged (verified — none applicable)
- [ ] Ready for review
