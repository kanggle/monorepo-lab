# Task ID

TASK-MONO-495

# Title

Track ADR-MONO-058 (fleet-wide shared technical scaffolding consolidation) pending owner ACCEPT

# Status

ready

<!-- Filed in ready/ per this project's convention for blocked/pending root items (no separate backlog/ directory at root — see TASK-MONO-328/367/399 for precedent of a "ready, but explicitly not startable yet" entry). Do NOT implement any of ADR-MONO-058's D1-D8 until the ADR's Status flips PROPOSED -> ACCEPTED via the owner's exact-form instruction. -->

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

This task exists only to hold a place in the root task queue for `ADR-MONO-058` (filed `PROPOSED` alongside this task, `docs/adr/ADR-MONO-058-fleet-wide-shared-technical-scaffolding-consolidation.md`) — a fleet-wide consolidation of technical scaffolding duplicated across all 8 projects, found by an 8-project parallel commonality/naming audit run 2026-07-29. Per `platform/shared-library-policy.md § Review Rule`/`§ Change Rule`, none of the ADR's D1–D8 decisions may be implemented until a human accepts the ADR with the exact-form instruction (`"ADR-MONO-058 ACCEPTED"`) — an agent may not self-accept. **This task is not implementable as written and must not be picked up from `ready/` until that happens.**

---

# Scope

## In Scope

- Once `ADR-MONO-058` is ACCEPTED: split the ADR's § 6 suggested sequence into individual per-decision, per-project tasks (a root task per shared-library promotion — D1/D4/D5/D6 — plus per-service adoption tasks for D2/D3/D7/D8 in each affected project's own `tasks/ready/`), per this repo's shared-vs-project task-filing boundary.
- Until ACCEPT: nothing. This task's only job is to be a visible, queryable placeholder so the ADR doesn't silently fall out of anyone's view the way a `PROPOSED` record sitting only in `docs/adr/` can.

## Out of Scope

- Any actual code change from D1–D8 — all of that is genuinely out of scope until ACCEPT, not just deferred.
- Deciding the ADR's open questions (D2's `details`-field/status-code conflict, D1's per-project role-set threading) — those are implementation-time decisions for whoever picks up the post-ACCEPT tasks, not this placeholder.

---

# Acceptance Criteria

- [ ] Before this task is worked, `docs/adr/ADR-MONO-058-...md`'s `Status` field reads `ACCEPTED` (verify by reading the file directly, not by inference from this task's own prose — an ADR status can go stale, per this repo's own documented history of `ADR-MONO-049`'s bracket going stale three times).
- [ ] Once ACCEPTED: this task closes by being superseded — split into the per-decision/per-project tasks described in Scope, referencing this task and the ADR as their origin, then this task moves `ready → done` with a note pointing at the split tasks (it produces no code of its own).

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read `PROJECT.md`, then load `rules/common.md` plus any `rules/domains/<domain>.md` and `rules/traits/<trait>.md` matching the declared classification. Unknown tags are a Hard Stop per `CLAUDE.md`.

- `docs/adr/ADR-MONO-058-fleet-wide-shared-technical-scaffolding-consolidation.md` (the decision record this task tracks)
- `platform/shared-library-policy.md` (the gate this ADR satisfies)
- `platform/architecture-decision-rule.md` (the ACCEPT-gate procedure)

---

# Related Contracts

None — this task itself makes no code or contract change.

---

# Target Service

- Monorepo-level — no single service; the ADR spans `libs/java-security`, `libs/java-security-servlet`, and 8 projects' `apps/`.

---

# Architecture

N/A — tracking task only.

---

# Implementation Notes

Filed alongside `ADR-MONO-058` in the same PR, per this repo's convention of a PROPOSED ADR needing a visible queue entry (precedent: `ADR-MONO-057` was proposed via its own PR, `#2988`, before later acceptance).

---

# Edge Cases

- If a future session finds this task sitting in `ready/` and is tempted to "just start" because the ADR feels obviously correct — it is explicitly not authorized without the owner's exact-form ACCEPT. Re-read `platform/architecture-decision-rule.md § The ACCEPTED Gate` before assuming otherwise.

---

# Failure Scenarios

- Implementing any of D1–D8 before ACCEPT would violate `shared-library-policy.md § Change Rule` and this repo's ADR-ACCEPT-gate convention — treat any such attempt as a Hard Stop, not a judgment call.
- Letting this task sit stale after the ADR is actually accepted (i.e. nobody splits it into real work) reproduces the exact "declaration outlives the truth" failure class this repo has already documented three times for `ADR-MONO-049`'s own status bracket — if you find the ADR is ACCEPTED but this task hasn't been split yet, split it before doing anything else in this area.

---

# Test Requirements

- None — tracking task only.

---

# Definition of Done

- [ ] ADR-MONO-058 status confirmed (ACCEPTED or still PROPOSED) before any action
- [ ] If ACCEPTED: split into per-decision/per-project tasks, this task moved to done referencing them
- [ ] If still PROPOSED: no action, task remains in ready/ as a placeholder
