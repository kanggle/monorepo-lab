# Task ID

TASK-MONO-110

# Title

ADR-MONO-014 ACCEPTED transition + author GAP TASK-BE-298 (operator token exchange)

# Status

ready

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

Execute the **ADR-MONO-014 PROPOSED → ACCEPTED** transition (user-explicit intent "ACCEPTED 승격 + BE-298 착수", 2026-05-16) per its § D6 readiness gate, and author the first downstream task of its § D5 sequence (GAP `TASK-BE-298`).

After this task:

- ADR-MONO-014 is ACCEPTED (Status + History + § 6 ACCEPTED row); D2/D3 read as finalised-in-impl-scope, no "proposed" hedging on the decision itself.
- ADR-MONO-013 § D5 forward-pointer to ADR-MONO-014 is confirmed status-accurate (additive only — ADR-013 decisions unchanged).
- GAP `projects/global-account-platform/tasks/ready/TASK-BE-298-...` exists (spec-first exchange endpoint), implementation deferred to its own PR.
- Memory (`project_platform_console_adr_013` + `MEMORY.md`) reflects ADR-014 ACCEPTED + the D5 sequence unblocked.
- Phase 2 / `TASK-PC-FE-002` remains PAUSED until `TASK-BE-298` is **merged** (ADR-014 § D5 / Consequences "Future-self").

# Scope

## In Scope

### Doc-only (ACCEPTED recording) — this task
- `docs/adr/ADR-MONO-014-platform-console-operator-auth-token-exchange.md`: `PROPOSED → ACCEPTED` (Status line, History line, § 6 append-only ACCEPTED row with user-intent quote + this PR). § 1.3 / § D6 PROPOSED-guard reworded to ACCEPTED past-tense (decision unchanged).
- `docs/adr/ADR-MONO-013-platform-console-foundation.md`: confirm the § D5 / History "AMENDED-BY ADR-MONO-014" pointer is status-accurate (ADR-014 now ACCEPTED). Additive wording only — **no ADR-013 decision change** (ADR-013 is ACCEPTED).
- `projects/global-account-platform/tasks/ready/TASK-BE-298-...` — author the GAP exchange-endpoint task (spec-first scope per ADR-014 § D5 step 1). Authoring only; implementation is TASK-BE-298's own PR.
- This task file + root `tasks/INDEX.md` ready/review entry + GAP `tasks/INDEX.md` ready entry for BE-298.
- Memory update.

## Out of Scope

- The GAP exchange endpoint implementation itself (`TASK-BE-298` — its own spec-first PR).
- `console-integration-contract.md` / `console-registry-api.md` / `admin-api.md` reconciliation edits (ADR-014 § D4 — executed inside `TASK-BE-298` / the console wiring task, spec-first, NOT here).
- platform-console exchange wiring + #569 contract self-contradiction fix (ADR-014 § D5 step 2 — `TASK-PC-FE-002a`).
- Phase 2 operator-parity slices (`TASK-PC-FE-002`+ — PAUSED until BE-298 merged).
- Re-deciding the model (B is fixed by ADR-014 D1; A/C/D rejected there).

# Acceptance Criteria

- [ ] ADR-MONO-014 Status = ACCEPTED; History carries the ACCEPTED line; § 6 has the PROPOSED row (PR #570) + an appended ACCEPTED row (user intent quote + this PR).
- [ ] § 1.3 / § D6 no longer assert "PROPOSED, not ACCEPTED" as the current state (reworded to ACCEPTED; the staged-pattern rationale preserved as history).
- [ ] ADR-MONO-013 forward-pointer to ADR-MONO-014 resolves and is status-accurate; ADR-013 decisions byte-unchanged except the additive pointer.
- [ ] `TASK-BE-298` exists in GAP `tasks/ready/` with all required sections, spec-first scope matching ADR-014 § D2/D3/D4/D5-step-1, and dependency markers (depends on ADR-MONO-014 ACCEPTED; blocks `TASK-PC-FE-002a`/Phase 2).
- [ ] Root + GAP `tasks/INDEX.md` updated; ADR number/links resolve; `validate-rules` clean (doc-only).
- [ ] Memory reflects ACCEPTED + that Phase 2 stays paused until BE-298 **merged**.

# Related Specs

> Monorepo-level governance doc (shared `docs/adr/`). No project `PROJECT.md` classification applies to this task.

- `docs/adr/ADR-MONO-014-platform-console-operator-auth-token-exchange.md` (§ D2/D3/D4/D5/D6 — the executed gate)
- `docs/adr/ADR-MONO-013-platform-console-foundation.md` § D5 (amended-by pointer)
- `platform/hardstop-rules.md` (HARDSTOP-06 #3 / HARDSTOP-09 #2 — the mandate this ADR discharges)
- `tasks/ready/TASK-MONO-108-...` (ADR-013 ACCEPTED-flip precedent — task shape parity)
- `projects/global-account-platform/specs/services/admin-service/security.md` (operator JWT boundary — BE-298 spec-first target)

# Related Skills

- `.claude/skills/` — ADR ACCEPTED recording (ADR-MONO-008 D6.2 / TASK-MONO-070 / TASK-MONO-108 precedent).

---

# Related Contracts

- None changed by this task (doc-only + task authoring). ADR-014 § D4 contract reconciliation is mandated for execution **inside `TASK-BE-298`** (spec-first), not here.

---

# Target Service

- None (monorepo-level governance doc + GAP task-file authoring).

---

# Architecture

ADR-MONO house structure per `docs/adr/ADR-MONO-008` / `ADR-MONO-013`. ACCEPTED-flip shape per `TASK-MONO-108` precedent (doc-only PR; downstream impl is a separate task/PR).

---

# Implementation Notes

- ACCEPTED gate per ADR-014 § D6: user-explicit intent satisfied ("ACCEPTED 승격 + BE-298 착수", 2026-05-16). On ACCEPTED: append § 6 row + author `TASK-BE-298` + begin § D5 sequence.
- Doc-only + task authoring: no production code; no churn-clock effect (the platform-console churn event already fired at #567 per ADR-MONO-003a § D2.1).
- ADR-013 is ACCEPTED — only the additive AMENDED-BY pointer may be touched; do not alter ADR-013 decisions (HARDSTOP-04 discipline).
- Branch name must not contain the `master` substring (sandbox push regex).
- `TASK-BE-298` is **authored** here but stays unimplemented until its own spec-first PR; Phase 2 stays paused until BE-298 is merged.

---

# Edge Cases

- Reader treats ADR-014 ACCEPTED as license to skip BE-298 spec-first → § D4/D5 + BE-298 task body re-assert spec-first-before-code.
- Reader resumes Phase 2 right after ACCEPTED (before BE-298 merged) → ADR-014 Consequences "Future-self" + BE-298 dependency markers re-enter the Hard Stop.
- ADR-013 pointer edit drifts into a decision change → constrained to the additive AMENDED-BY line only.

---

# Failure Scenarios

- ACCEPTED recorded but `TASK-BE-298` never authored → ADR claims ACCEPTED with no execution path (mitigated: this task's AC binds the flip and the BE-298 authoring together in one PR).
- ADR-014 flipped ACCEPTED while contract self-contradiction (#569) left untracked → mitigated: BE-298 scope explicitly owns the § D4 reconciliation (console-integration-contract §2.1/§2.2 + console-registry-api §Authentication + admin-api).
- Scope creep: doing the exchange-endpoint impl here → explicitly Out of Scope; this PR is doc + task authoring only.

---

# Test Requirements

- ADR/spec internal-link lint clean; `validate-rules` no new inconsistency (doc-only).
- `TASK-BE-298` passes the required-sections check (Goal/Scope/AC/Related Specs/Related Contracts/Edge Cases/Failure Scenarios).

---

# Definition of Done

- [ ] ADR-MONO-014 ACCEPTED (Status + History + § 6 row)
- [ ] ADR-MONO-013 forward-pointer status-accurate (additive only)
- [ ] `TASK-BE-298` authored in GAP `tasks/ready/` (spec-first scope)
- [ ] Acceptance Criteria satisfied
- [ ] Memory + INDEX updated
- [ ] Ready for review
