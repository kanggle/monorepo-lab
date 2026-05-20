# Task ID

TASK-MONO-123

# Title

ADR-MONO-013 § 6 audit-trail backfill — Phase 4 (FE-007 + FE-008) + Phase 5 (FE-009 + FIN-BE-005) complete additive notes (spec-only, additive)

# Status

ready

# Owner

monorepo

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

# Dependency Markers

- **retrospective backfill (audit-trail정합)**: this is a **self-consistency restoration** of the FE-006-established § 6 *"Additive note — Phase X COMPLETE"* pattern in [`docs/adr/ADR-MONO-013-platform-console-foundation.md`](../../docs/adr/ADR-MONO-013-platform-console-foundation.md). Phase 2 (FE-006 PR #581 → squash `16cfdc00`) added one § 6 additive note explicitly. The pattern was **not followed through at Phase 4 complete** (FE-008 PR #637 → squash `c34fc0ac`, 2026-05-19) **nor at Phase 5 complete** (FE-009 PR #644 → squash `29b01826`, 2026-05-20). This task backfills both omissions in one chore, mirroring the FE-006 stanza verbatim in shape (additive-only, HARDSTOP-04 discipline — D1–D8 decisions unchanged).
- **governed by**: **ADR-MONO-013** itself (this task only restores § 6 audit-trail completeness; it changes **no decision**). No new ADR. No competing convention.
- **prerequisite SATISFIED (already on main, objectively verified)**: all referenced PR squash commits are present in `git log origin/main`:
  - FE-007 #633 `81395376` (Phase 4 slice 1 — wms; 2026-05-19)
  - FE-008 #637 `c34fc0ac` (Phase 4 slice 2 — scm; 2026-05-19; **completes Phase 4**)
  - FIN-BE-005 #639/`95c543a1` + #640/`8b5d60aa` + #641/`297948bd` (Phase 5 finance-side spec-first reconciliation; 2026-05-20)
  - FE-009 #642/`c49edce1` + #643/`456a6bde` + #644/`29b01826` + #645/`59ab228e` (Phase 5 console-side §2.4.7 + features/finance-ops + close; 2026-05-20; **completes Phase 5**)
- **monorepo-level**: this task edits `docs/adr/` (monorepo-shared governance) — it lives in **root `tasks/`** per CLAUDE.md § "Task Rules" (cross-project / shared-paths). Not a `platform-console/tasks/` task.
- **PR shape**: root `tasks/INDEX.md` PR Separation Rule (strict, identical to finance): *"Never bundle task spec authoring with implementation in the same PR."* Lifecycle = **spec PR** (this file → `ready/`) + **impl PR** (ADR edits + `ready → review`) + **close chore PR** (`review → done`).

# Goal

Restore ADR-MONO-013 § 6 **Status Transition History** self-consistency by appending the two missing **additive notes** — one for **Phase 4 COMPLETE** (FE-008-merge-time wms+scm bound), one for **Phase 5 COMPLETE** (FE-009-merge-time finance bound). The pattern was established by TASK-PC-FE-006's Phase-2-COMPLETE additive note (HARDSTOP-04-respecting, additive-only, D1–D8 decisions unchanged); Phase 4 and Phase 5 should follow the same pattern for audit-trail completeness.

Why this exists as a separate task (and not a part of FE-007/FE-008/FE-009):

- FE-006 explicitly scoped a § 6 additive blockquote into its "Phase 2 verification capstone" deliverable. FE-007/FE-008/FE-009 did not include an equivalent § 6 line in their task scopes — none of them edited `docs/adr/ADR-MONO-013-...md`. The omission was discovered retrospectively (after FE-009 close) when objectively checking ADR file mtime (`git log -- docs/adr/ADR-MONO-013-...md` last touched at `16cfdc00` / FE-006, never after).
- ADR § 6 carries the header **"Append-only"** — backfilling missing entries restores the append-only audit-trail without rewriting any existing row (no force-push semantics; purely additive append). The FE-006 stanza is the structural template.
- No ADR decision is being changed. § 1–§ 5 + § 7–§ 8 untouched. § 6 § 6 table rows untouched (the two PROPOSED/ACCEPTED rows stay byte-identical). The existing FE-006 Phase-2-COMPLETE additive blockquote stays byte-identical. **Only two new additive blockquotes** are appended after the FE-006 stanza, in the same shape.

# Scope

## In Scope (spec-only, additive)

- **`docs/adr/ADR-MONO-013-platform-console-foundation.md` § 6** — append exactly **two** new additive blockquotes after the existing FE-006 Phase-2 blockquote, in the same shape (`> **Additive note — Phase X COMPLETE …**`). Each new blockquote:
  1. Names the milestone (Phase 4 / Phase 5).
  2. Lists the task IDs + merged PR # + squash commit hash that satisfied it (objectively verifiable against `git log origin/main`).
  3. Records the cross-project prerequisite where applicable (Phase 5 = FIN-BE-005; Phase 4 = SCM-BE-015 for slice 2).
  4. States the milestone consequence consistent with ADR § D6 (Phase 4 = "non-GAP federation begins / contract proven across two non-GAP domains"; Phase 5 = "third non-GAP domain federated / zero-retrofit assumption confirmed for the third time / next gates Phase 6 erp + Phase 7 console-bff").
  5. Reuses the HARDSTOP-04 / D1–D8-unchanged discipline line **verbatim** from the FE-006 stanza (this is a structural invariant — keep it byte-stable).
  6. Names this task ID (`TASK-MONO-123`) + this PR as the source of the additive note.
- **Phase 4 additive note** records the objective merge fingerprint:
  - Phase 4 slice 1 = `TASK-PC-FE-007` (#633 squash `81395376`, 2026-05-19) — first non-GAP federation, established the **§ 2.4.5 per-domain credential rule**.
  - Phase 4 slice 2 = `TASK-PC-FE-008` (#637 squash `c34fc0ac`, 2026-05-19) — second non-GAP federation, **§ 2.4.6** reuses the rule verbatim, flat-envelope + read-only discipline, S5 `meta.warning` surfacing obligation; closes Phase 4. Cross-project prereq = `TASK-SCM-BE-015` (#635 + #636, merged before FE-008 impl).
  - ADR § D6 consequence: Phase 4 COMPLETE; the contract is now PROVEN across two non-GAP domains, generalising the FE-006 GAP-only §3 parity matrix (§ 3.3 "zero retrofit unverified" assumption — first round of verification). Phase 5 (finance) is unblocked.
- **Phase 5 additive note** records:
  - Phase 5 = **`TASK-PC-FE-009`** (#642/#643/#644/#645 — 4-PR sequence; impl squash `29b01826`, 2026-05-20) — third non-GAP federation, **§ 2.4.7** reuses § 2.4.5/§ 2.4.6 verbatim, finance flat error envelope (distinct producer), F5 minor-units **string** money (fintech analog of scm S5 obligation), confidential/F7, honest regulated-state surfacing, **no 429** (honest difference — not cargo-culted from scm). Cross-project prereq = `TASK-FIN-BE-005` (#639 + #640 + #641 — finance gap-integration.md `## platform-console Operator Read Consumer (ADR-MONO-013)` section + PROJECT.md clarifying bullet; 2026-05-20).
  - ADR § D6 consequence: Phase 5 COMPLETE; § 3.3 "zero retrofit" assumption confirmed for the **third** time. Phase 6 (erp console section, future erp ADR per ADR-MONO-016 ACCEPTED 2026-05-19) inherits the proven non-GAP contract. Phase 7 (`console-bff` + cross-domain dashboards, 5-domain readiness gate) is at 4/5 domains live (GAP + wms + scm + finance; erp pending).
  - Phase 3 (admin-web retirement) gate remains satisfied by FE-006 (no change from this backfill).

## Out of Scope

- Any change to ADR-MONO-013 **§ 1–§ 5 / § 7–§ 8 / § D1–§ D8 decisions** (HARDSTOP-04 — a decision change would require a fresh ADR row or a supersede ADR; this task is documentation-only, additive-only).
- Any change to the existing **§ 6 table rows** (PROPOSED / ACCEPTED — those stay byte-identical; this task only **appends** new additive blockquotes after the existing FE-006 stanza, never rewrites the table).
- Any change to the existing **FE-006 Phase-2-COMPLETE additive blockquote** (its wording is the structural template — keep it byte-identical to preserve the FE-006 pattern's authority).
- Any change to `console-integration-contract.md`, any `features/*`, any apps/, any other project (`projects/<name>/`), any other ADR.
- A new ADR (governing remains ADR-MONO-013 itself).
- Phase 6 / Phase 7 / Phase 8 notes — those are future milestones (erp + console-bff + federation hardening); record them when they happen, NOT retrospectively here.
- Any modification of `platform-console/tasks/done/TASK-PC-FE-007|008|009` task files (those are byte-stable per CLAUDE.md "Do not modify a task file after it moves to review/ or done/" — `audit-trail backfill on the ADR side` does not touch the task files themselves; the task INDEX entries already record "🎯 Phase 4/5 COMPLETE", which is what surfaced this ADR-side omission).

# Acceptance Criteria

- [ ] `docs/adr/ADR-MONO-013-platform-console-foundation.md` § 6 has **exactly three additive blockquotes** after this PR merges (the existing FE-006 Phase-2 one + new Phase-4 + new Phase-5). Verified by `grep -c "Additive note" docs/adr/ADR-MONO-013-platform-console-foundation.md` = 3.
- [ ] § 6 table rows (PROPOSED 2026-05-16 + ACCEPTED 2026-05-16) are **byte-identical** to pre-PR state. Verified by `git diff origin/main..HEAD docs/adr/ADR-MONO-013-platform-console-foundation.md` showing only additions in the blockquote region after the FE-006 stanza.
- [ ] The existing FE-006 Phase-2-COMPLETE additive blockquote is **byte-identical** to pre-PR state (its wording is the structural authority — do not edit it). Verified by the same git diff.
- [ ] Each new additive blockquote contains: the milestone name (Phase 4 / Phase 5), the task IDs + PR #s + squash commit hashes (objectively verifiable against `git log origin/main`), the cross-project prerequisite reference where applicable (Phase 5 → FIN-BE-005), the ADR § D6 consequence, the HARDSTOP-04 / D1–D8-unchanged discipline line.
- [ ] **No ADR decision changed** (§ 1–§ 5 / § 7–§ 8 / D1–D8). Verified by the git diff showing only § 6 additions.
- [ ] Spec internal-link lint clean (any `[…](path)` reference in the new blockquotes resolves to an existing file at the relative path).
- [ ] Scope = `docs/adr/ADR-MONO-013-platform-console-foundation.md` only (+ task lifecycle: this file + root `tasks/INDEX.md`). No code, no other project, no other ADR.
- [ ] Spec-authoring (this task to `ready/`) and impl (ADR edits + `ready → review`) are in **separate PRs** per root `tasks/INDEX.md` strict PR Separation Rule.

# Related Specs

> Target = monorepo-level. Governing = the ADR itself. Follow root `tasks/INDEX.md` + CLAUDE.md "Monorepo-level work".

- `docs/adr/ADR-MONO-013-platform-console-foundation.md` (edited — § 6 only, append two additive blockquotes; FE-006 stanza byte-identical; table rows byte-identical; § D6 referenced).
- `docs/adr/ADR-MONO-008-finance-platform-bootstrap.md` (cross-ref only — finance domain governance, unchanged; FIN-BE-005 is governed by ADR-MONO-013 not ADR-MONO-008 — same reasoning the FIN-BE-005 task file records).
- `projects/platform-console/specs/contracts/console-integration-contract.md` (cross-ref only — § 2.4.5/§ 2.4.6/§ 2.4.7 are the bindings the additive notes point at; § 3 verified parity matrix is FE-006's deliverable).
- `projects/platform-console/tasks/done/TASK-PC-FE-007-console-wms-operations-section.md`, `TASK-PC-FE-008-console-scm-operations-section.md`, `TASK-PC-FE-009-console-finance-operations-section.md` (cross-ref only — task files stay byte-stable per CLAUDE.md "no modification after review/done").
- `projects/scm-platform/tasks/done/TASK-SCM-BE-015-platform-console-operator-read-consumer-reconciliation.md` (cross-ref — Phase 4 slice 2 cross-project prereq).
- `projects/finance-platform/tasks/done/TASK-FIN-BE-005-platform-console-operator-read-consumer-reconciliation.md` (cross-ref — Phase 5 cross-project prereq).
- `tasks/INDEX.md` (root — this file's location + ready list entry; PR Separation Rule the lifecycle follows).

# Related Skills

- `.claude/skills/` — architect / design-api (ADR § 6 audit-trail backfill judgement; HARDSTOP-04 boundary: additive-only vs decision-change; the FE-006 stanza is the structural template).

---

# Related Contracts

- **Changed (this task, spec-only additive)**: `docs/adr/ADR-MONO-013-platform-console-foundation.md` § 6 — two new additive blockquotes appended (Phase 4 + Phase 5).
- **Cross-referenced (unchanged, authoritative)**: ADR-MONO-013 § D6 (Phase definitions), § 3 (verified parity matrix), § D7/§ D8 (readiness criteria + transition mechanics — unaffected), ADR-MONO-008 (finance domain governance), ADR-MONO-016 (erp Phase 6 future ADR — already ACCEPTED), `console-integration-contract.md` § 2.4.5/§ 2.4.6/§ 2.4.7, the 7 closed task files.
- **Not touched**: any `apps/`, any `platform/`, any `rules/`, any `.claude/`, any other ADR, any project specs, any task file already in `review/` or `done/`.

---

# Target Service

- monorepo-level — `docs/adr/ADR-MONO-013-platform-console-foundation.md` only. No service.

---

# Architecture

- ADR-MONO-013 itself is the governing decision; § 6 is its **append-only Status Transition History**. The FE-006 stanza established a "Phase complete = one additive blockquote in § 6" pattern; FE-008 (Phase 4 complete) and FE-009 (Phase 5 complete) did not follow that pattern. This task restores it.
- HARDSTOP-04 discipline: a decision change in an ADR requires a fresh row (PROPOSED → ACCEPTED) or a SUPERSEDED-BY line, not a free-form edit. Backfilling Phase-complete additive blockquotes is **not a decision change** — they are documentary, additive-only audit trail. The FE-006 stanza is the structural authority that proves this discipline is allowed (HARDSTOP-04 was not violated by FE-006; this task follows the same pattern, so it doesn't either).

---

# Implementation Notes

- Pure ADR edit. Sibling precedent for ADR additive-note discipline: FE-006 (the structural template — Phase 2 Additive Note + the "additive only — no ADR-MONO-013 decision (D1–D8) is changed (ADR-013 ACCEPTED; HARDSTOP-04 discipline)" closing line).
- Mirror the FE-006 stanza's shape **byte-stably**: same opening (`> **Additive note — Phase X COMPLETE …**`), same closing discipline line. Vary only the milestone-specific content (task IDs, PR numbers, squash hashes, ADR § D6 consequence).
- **Do not edit § 6 table rows** (rows 1 and 2 are byte-stable historical records; backfill is **append** of new blockquotes after the existing FE-006 stanza, not row insertion or rewrite).
- **Do not edit the FE-006 stanza** (it is the structural template; its byte-stability is the proof the FE-006 pattern is authoritative for this kind of backfill).
- Each squash hash is objectively verifiable via `git log origin/main --oneline | grep <prefix>` before the impl commit lands; failing that, the backfill text is wrong and must be corrected (no green-wash: do not paste an unverified hash).
- Recommend implementation model: **Opus** (ADR backfill judgement; the HARDSTOP-04 boundary is interpretive — additive-only vs decision-change; the FE-006 stanza's authority must be preserved byte-stable; squash hashes must be objectively verified). Branch name must not contain the `master` substring (use e.g. `task/mono-123-impl`).
- Root PR Separation Rule (strict — same as finance): spec-authoring (this file → `ready/`) and impl (ADR edits + `ready → review`) MUST land in **separate PRs**.

---

# Edge Cases

- A future agent reads § 6 and finds 1 additive blockquote + INDEX claims "Phase 4 COMPLETE / Phase 5 COMPLETE" → sees the gap and writes a duplicate Phase-4-COMPLETE note → this task closes the gap so the duplicate is unnecessary. Recording this task ID in the new blockquotes (Phase 4 + Phase 5 each name `TASK-MONO-123` as the backfill source) makes the deliberate-not-original nature explicit.
- A reader assumes the backfill is a decision change → the blockquotes explicitly state "additive only — no ADR-MONO-013 decision (D1–D8) is changed (ADR-013 ACCEPTED; HARDSTOP-04 discipline)" (verbatim from FE-006's closing line). The git diff is purely additive (no row mutation, no decision text touched).
- A reviewer questions why both Phase 4 and Phase 5 backfills land in one task → one chore is more honest than two: the omission is the same omission (FE-007/FE-008/FE-009 each forgot the § 6 follow-through). Backfilling them together restores the FE-006 pattern in one chronological step rather than two artificial steps.
- A future Phase 6 (erp console section) blockquote drift → this task does NOT pre-author the Phase 6 stanza. Phase 6 will record its own additive note at its own merge time (mirroring this backfill's verified shape).
- Squash hashes drift (a PR is squashed differently or re-pushed) → verify each hash against `git log origin/main` BEFORE the impl commit. If a hash mismatches, fix the backfill text; do NOT paste a hash without objective grep. (Honest discipline — no green-wash.)

# Failure Scenarios

- The backfill edits § 6 table rows or the FE-006 stanza → HARDSTOP-04 boundary breached; the backfill must remain strictly append-of-new-blockquotes-only. AC pins this.
- The backfill claims a Phase-completion that isn't actually merged on `origin/main` → green-wash; each squash hash must be `git log origin/main`-verified before commit. AC + Implementation Notes pin this.
- The backfill inadvertently changes an ADR § D1–§ D8 decision wording → HARDSTOP-04 violation requiring a fresh ADR row, not a free-form edit. AC forbids any decision-text change.
- A new ADR is created for this backfill → unnecessary; ADR-MONO-013 itself governs § 6's append-only history. AC forbids a new ADR.
- The spec PR (this file → `ready/`) and the impl PR (ADR edits + `ready → review`) are bundled into one PR → root `tasks/INDEX.md` strict PR Separation Rule violation. AC + Implementation Notes pin separability.
- Phase 6 (erp) or Phase 7 (console-bff) pre-authored here → pre-author of unmerged work = unverifiable claim. AC + Out-of-Scope forbid it (record at the time of the actual milestone, not now).

---

# Verification

- `grep -c "Additive note" docs/adr/ADR-MONO-013-platform-console-foundation.md` → **3** (1 existing FE-006 + 2 new = Phase 4 + Phase 5).
- `git diff origin/main..HEAD docs/adr/ADR-MONO-013-platform-console-foundation.md` shows: only additions, only inside § 6 (after the FE-006 stanza, before `## 7. Provenance`); no `-` line touches any existing § 6 row / the FE-006 stanza / any § D1–§ D8 decision text; no `-` line touches § 1–§ 5 / § 7–§ 8 anywhere.
- Each squash hash in the new blockquotes appears in `git log origin/main --oneline` (objectively verified at impl-commit time): `81395376`, `c34fc0ac`, `c49edce1`, `456a6bde`, `29b01826`, `59ab228e`, `95c543a1`, `8b5d60aa`, `297948bd`.
- `validate-rules` (or the repo's rule-consistency scan) reports no new inconsistency; spec internal-link lint clean (all relative paths in the new blockquotes resolve).
- Scope = `docs/adr/ADR-MONO-013-platform-console-foundation.md` (+ root `tasks/INDEX.md` + this task file lifecycle). No `apps/`, no other ADR, no other project file. No Docker/build required (spec-only).

---

# Definition of Done

- [ ] § 6 has 3 additive blockquotes (FE-006 + Phase-4-backfill + Phase-5-backfill); FE-006 stanza byte-identical; table rows byte-identical.
- [ ] All 9 squash hashes referenced in the new blockquotes objectively verified against `git log origin/main` before the impl commit lands.
- [ ] No ADR decision text changed; spec-only additive diff; no other-project file touched.
- [ ] Spec PR + impl PR + close chore PR (3 separate PRs per root strict rule); each stage objectively merge-verified (`gh pr view --json state,mergedAt,mergeCommit` + `git log origin/main` tip match).
- [ ] `git mv ready→review` and `git mv review→done` each pass the CLAUDE.md re-stage check (`git show :<new-path>` reads the expected new Status before commit).
- [ ] root `tasks/INDEX.md` ready / review / done list moves track the lifecycle.
- [ ] ADR-MONO-013 § 6 self-consistency restored — the FE-006 pattern's follow-through is back on Phase-4-complete and Phase-5-complete milestones.
- [ ] Ready for review.
