# Task ID

TASK-MONO-206

# Title

**ADR-MONO-023 PROPOSED → ACCEPTED transition** (doc-only governance flip). Finalises the D1-D6 CHOSEN-PROPOSED direction byte-unchanged from the merged PROPOSED (#1237, squash `c4a30422`), authorizing the § 3.3 3-step execution roadmap as a dependency-correct base. Sibling staged-child ACCEPTED pattern: ADR-019→MONO-153 / ADR-020→MONO-157 / ADR-021→MONO-165. Bundles the TASK-MONO-205 close chore (ready→done, #1237 3-dim verified). Doc-only; implementation is separate iam-platform tasks.

# Status

ready

# Owner

architecture

# Task Tags

- docs
- adr
- security

---

# Dependency Markers

- **prerequisite (merged)**: TASK-MONO-205 / ADR-MONO-023 PROPOSED (#1237, squash `c4a30422`) — ACCEPTED finalises the merged PROPOSED byte-unchanged; dependency-correct base = the ACCEPTED main this task produces.
- **gated by**: user-explicit intent *"권장 순서대로 진행"* (2026-06-10) selecting the offered A→B recommended order after the PROPOSED merge.
- **unpauses**: ADR-MONO-023 § 3.3 execution roadmap (3 iam-platform steps).

# Goal

Flip ADR-MONO-023 PROPOSED → ACCEPTED (finalise, do not re-decide) so the § 3.3 execution roadmap is UNPAUSED on a dependency-correct ACCEPTED main, and close TASK-MONO-205 (ready→done) now that #1237 is 3-dimension-verified merged.

# Scope

- `docs/adr/ADR-MONO-023-...md` — Status PROPOSED → ACCEPTED; History ACCEPTED clause append; § 6 Status Transition History ACCEPTED row append (PROPOSED row `#<this>` → `#1237` resolved); § 1.3 minimal past-tense (execution UNPAUSED note). **D1-D6 bodies + § 1/2/3/4/5/7 byte-unchanged** (ACCEPTED *finalises*, does not re-decide).
- `docs/adr/ADR-MONO-003a-...md` § 3 audit table — append row #30 (Meta-policy: ADR-023 ACCEPTED transition; same one-off category as row #28 — does NOT add to § D1).
- `tasks/done/TASK-MONO-205-...md` — close chore: moved from `ready/` (git mv) + Status ready→done + completion note (#1237 3-dim verified).
- Doc-only. NO schema/code change.

# Acceptance Criteria

- **AC-1** ADR-MONO-023 Status = ACCEPTED; History carries the ACCEPTED clause; § 6 has the ACCEPTED row.
- **AC-2** D1-D6 decision tables + § 1/2/3/4/5/7 byte-identical to the merged PROPOSED `c4a30422` (ACCEPTED finalises, does not re-decide — HARDSTOP-04).
- **AC-3** ADR-003a § 3 audit row #30 appended (append-only, oldest-first; rows #1-#29 byte-unchanged).
- **AC-4** TASK-MONO-205 is in `tasks/done/` with Status done + completion note (re-stage verified: `git show :tasks/done/TASK-MONO-205-...` reads `done`).
- **AC-5** Doc-only diff (no `apps/` code, no migrations).

# Related Specs

- `docs/adr/ADR-MONO-023-entitlement-iam-plane-separation.md` (the ADR being finalised)

# Related Contracts

- none (doc-only governance flip)

# Edge Cases

- ACCEPTED must NOT alter any D1-D6 decision — it finalises the merged PROPOSED byte-unchanged (sibling ADR-019/020/021 ACCEPTED discipline).
- The `git mv` ready→done stages the ready-state blob; after editing Status to `done`, re-`git add` and verify `git show :tasks/done/TASK-MONO-205-...` reads `done` (CLAUDE.md re-stage rule).

# Failure Scenarios

- If ACCEPTED re-opens or edits a decision → violates the finalise-byte-unchanged rule; revert to the merged PROPOSED body.
- If TASK-MONO-205 lands under `done/` still reading `Status: review`/`ready` → the re-stage check failed; re-add and re-verify.
