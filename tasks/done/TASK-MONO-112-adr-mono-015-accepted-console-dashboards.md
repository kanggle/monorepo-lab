# Task ID

TASK-MONO-112

# Title

ADR-MONO-015 ACCEPTED transition + author TASK-PC-FE-005 (composed operator overview)

# Status

done

# Owner

architecture

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- adr

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

Execute the **ADR-MONO-015 PROPOSED → ACCEPTED** transition (user-explicit intent "ACCEPTED 승격 + FE-005 착수", 2026-05-16) per its § D6 readiness gate, and author the first downstream task of its § D5 sequence (`TASK-PC-FE-005`).

After this task:

- ADR-MONO-015 is ACCEPTED (Status + History + § 6 ACCEPTED row); § 1.3/§ D6 ACCEPTED past-tense; decision (Model B composed operator overview) unchanged.
- ADR-MONO-013 § History forward-pointer to ADR-MONO-015 is status-accurate (additive only — ADR-013 decisions unchanged).
- `projects/platform-console/tasks/ready/TASK-PC-FE-005-...` exists (composed operator-overview slice 4/5, spec-first), implementation deferred to its own PR.
- Memory reflects ADR-015 ACCEPTED + the D5 sequence unblocked.
- `FE-006` parity-verify remains unstarted until `TASK-PC-FE-005` is **merged** (ADR-MONO-015 § D5/§ D6).

# Scope

## In Scope

### Doc-only (ACCEPTED recording) — this task
- `docs/adr/ADR-MONO-015-platform-console-dashboards-model.md`: `PROPOSED → ACCEPTED` (Status, History, § 6 append-only ACCEPTED row with user-intent quote + this PR; § 1.3/§ D6 ACCEPTED past-tense). Decision B unchanged.
- `docs/adr/ADR-MONO-013-platform-console-foundation.md`: confirm the History "AMENDED-BY ADR-MONO-015" pointer is status-accurate (ADR-015 now ACCEPTED). Additive only — **no ADR-013 decision change**.
- `projects/platform-console/tasks/ready/TASK-PC-FE-005-...` — author the composed operator-overview slice task (spec-first scope per ADR-MONO-015 § D3/D4/D5 step 1). Authoring only; implementation is TASK-PC-FE-005's own PR. **`git add` the FE-005 task file explicitly** (FE-003 gap-prevention rule); do **not** touch the still-untracked FE-002/FE-002a task files (separate lifecycle chore).
- This task file + memory update.

## Out of Scope

- The composed-overview implementation itself (`TASK-PC-FE-005` — its own spec-first PR).
- `console-integration-contract.md` § 2.4.4 / § 3 binding + `console-web/architecture.md` `features/dashboards` module (executed inside `TASK-PC-FE-005`, spec-first, per ADR-015 § D4 — NOT here).
- `FE-006` parity-verify (slice 5/5 — its own task, gated on FE-005 merge).
- Re-deciding the model (B fixed by ADR-015 D1; A/C rejected there).
- The FE-002/FE-002a untracked task-file lifecycle gap (separate chore).

# Acceptance Criteria

- [ ] ADR-MONO-015 Status = ACCEPTED; History carries the ACCEPTED line; § 6 has the PROPOSED row (PR #578) + an appended ACCEPTED row (user intent quote + this PR).
- [ ] § 1.3/§ D6 no longer assert "PROPOSED" as the current state (reworded to ACCEPTED; staged rationale preserved as history).
- [ ] ADR-MONO-013 forward-pointer to ADR-MONO-015 resolves and is status-accurate; ADR-013 decisions byte-unchanged except the additive pointer.
- [ ] `TASK-PC-FE-005` exists in `projects/platform-console/tasks/ready/` with all required sections, spec-first scope matching ADR-015 § D3/D4/D5-step-1, dependency markers (depends on ADR-MONO-015 ACCEPTED; part of ADR-MONO-013 Phase 2 slice 4/5; blocks FE-006), committed in this PR's commit (FE-003 git-add rule applied).
- [ ] ADR number/links resolve; `validate-rules` clean (doc-only).
- [ ] Memory reflects ACCEPTED + that FE-006 stays gated until FE-005 merged.

# Related Specs

> Monorepo-level governance doc (shared `docs/adr/`). No project `PROJECT.md` classification applies to this task.

- `docs/adr/ADR-MONO-015-platform-console-dashboards-model.md` (§ D1/D2/D3/D4/D5/D6 — the executed gate)
- `docs/adr/ADR-MONO-013-platform-console-foundation.md` § 3 / § D7.4 (parity checklist — amended-by pointer)
- `docs/adr/ADR-MONO-014-platform-console-operator-auth-token-exchange.md` + `tasks/ready/TASK-MONO-110-...` (ACCEPTED-flip precedent — task shape parity)
- `platform/hardstop-rules.md` (HARDSTOP-06 #3 / HARDSTOP-09 #2 — the mandate this discharges)
- `projects/platform-console/specs/contracts/console-integration-contract.md` § 3 (the dashboards line FE-005 binds)

# Related Skills

- `.claude/skills/` — ADR ACCEPTED recording (ADR-MONO-008 D6.2 / TASK-MONO-070 / TASK-MONO-110 precedent).

---

# Related Contracts

- None changed by this task (doc-only + task authoring). ADR-015 § D4 contract reconciliation is mandated for execution **inside `TASK-PC-FE-005`** (spec-first), not here.

---

# Target Service

- None (monorepo-level governance doc + platform-console task-file authoring).

---

# Architecture

ADR-MONO house structure per `docs/adr/ADR-MONO-014`. ACCEPTED-flip shape per `TASK-MONO-110` precedent (doc-only PR; downstream impl is a separate task/PR).

---

# Implementation Notes

- ACCEPTED gate per ADR-015 § D6: user-explicit intent satisfied ("ACCEPTED 승격 + FE-005 착수", 2026-05-16). On ACCEPTED: append § 6 row + author `TASK-PC-FE-005` + begin § D5 sequence.
- Doc-only + task authoring: no production code; no churn-clock effect.
- ADR-013 is ACCEPTED — only the additive AMENDED-BY pointer may be touched; do not alter ADR-013 decisions (HARDSTOP-04 discipline).
- Apply the FE-003 task-file gap-prevention rule: `git add` the new FE-005 task md in this PR's commit; leave the still-untracked FE-002/FE-002a task files untouched.
- Branch name must not contain the `master` substring.

---

# Edge Cases

- Reader treats ADR-015 ACCEPTED as license to skip FE-005 spec-first → § D4 + FE-005 task body re-assert spec-first-before-code.
- Reader runs FE-006 right after ACCEPTED (before FE-005 merged) → ADR-015 § D5/§ D6 + FE-005 dependency markers re-enter the Hard Stop.
- ADR-013 pointer edit drifts into a decision change → constrained to the additive AMENDED-BY line only.

---

# Failure Scenarios

- ACCEPTED recorded but `TASK-PC-FE-005` never authored → ADR claims ACCEPTED with no execution path (mitigated: this task's AC binds the flip + FE-005 authoring in one PR).
- FE-005 task file left untracked (the FE-002/002a gap repeats) → mitigated: AC mandates the explicit `git add`.
- Scope creep: doing the overview impl here → explicitly Out of Scope; this PR is doc + task authoring only.

---

# Test Requirements

- ADR/spec internal-link lint clean; `validate-rules` no new inconsistency (doc-only).
- `TASK-PC-FE-005` passes the required-sections check.

---

# Definition of Done

- [ ] ADR-MONO-015 ACCEPTED (Status + History + § 6 row + § 1.3/§ D6 past-tense)
- [ ] ADR-MONO-013 forward-pointer status-accurate (additive only)
- [ ] `TASK-PC-FE-005` authored in `projects/platform-console/tasks/ready/` (spec-first scope, git-added)
- [ ] Acceptance Criteria satisfied
- [ ] Memory + INDEX updated
- [ ] Ready for review
