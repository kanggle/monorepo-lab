# Task ID

TASK-MONO-069

# Title

Publish ADR-MONO-003b (PROPOSED) — Phase 5 launch criteria, procedure, sync, rollback

# Status

review

# Owner

monorepo

# Task Tags

- spec
- adr
- policy

---

# Goal

Author `ADR-MONO-003b` in **PROPOSED** status to define the concrete launch criteria, extraction procedure, monorepo ↔ Template sync mechanism, and rollback path for **Phase 5** (Template Repository extraction).

This ADR is a **prerequisite** for Phase 5 launch — not the launch itself. Per [ADR-MONO-003a § D4 (Phase 5 trigger redefinition)](../../docs/adr/ADR-MONO-003a-d4-override-scope-canonicalization.md):

> | Phase 5 launch decision | User-explicit statement of intent + new `ADR-MONO-003b` authoring (status: PROPOSED → ACCEPTED). No automatic timer. No verify-template-readiness exit-0 auto-promotion. |

ADR-MONO-003b currently does not exist. The gate is half-defined: user-explicit intent + ADR-MONO-003b ACCEPTED. With no ADR file authored, the user cannot complete the gate even after explicit intent — the prerequisite is missing. This task lands the PROPOSED draft so that:

1. The criteria are visible and reviewable before the launch decision moment.
2. A future "should we launch now?" question has a concrete checklist to evaluate against.
3. When the user later signals intent, ACCEPTED transition is a one-line status flip + audit-trail row append, not a fresh authoring session under time pressure.

This is **not** the launch trigger. PROPOSED status means: the criteria are documented; the launch is not yet authorised. ACCEPTED transition is a separate decision moment.

---

# Scope

## In Scope

### A. New ADR file

`docs/adr/ADR-MONO-003b-phase-5-launch-criteria.md` — Status: **PROPOSED** 2026-05-13.

Required sections:

- **Context** — current state of Phase 5 (DEFERRED per ADR-MONO-003 § D1, auto-trigger sealed per ADR-MONO-003a § D4), why a launch-criteria ADR is needed before launch (the gate cited by ADR-MONO-003a § D4 must exist for the gate to be checkable).
- **Decision** — five sub-decisions:
  - **D1** — Launch readiness criteria (checklist a candidate launch moment must satisfy).
  - **D2** — Extraction procedure (which script / which seed source / what the resulting repo contains).
  - **D3** — Monorepo ↔ Template sync mechanism (one-way / cadence / tooling / who initiates).
  - **D4** — Rollback procedure (how to retire the Template repo if the bet fails).
  - **D5** — ACCEPTED transition mechanics (what user-explicit statement form satisfies the gate; what audit-trail row gets appended where).
- **Consequences** — what changes the day this ADR is PROPOSED, and the day it transitions to ACCEPTED.
- **Alternatives Considered** — at minimum: (i) skip ADR-003b and inline criteria into ADR-003a (rejected: scope creep), (ii) defer until user signals intent (rejected: forces ad-hoc authoring at launch time), (iii) treat extract-template.sh as self-documenting (rejected: criteria are decisions, not script behaviour).
- **Relationship to ADR-MONO-003 / 003a** — D1 of ADR-MONO-003 (DEFERRED) is the parent state; ADR-MONO-003a § D4 cites this ADR by name as the gate. After ACCEPTED, ADR-MONO-003 + 003a are SUPERSEDED-on-launch (Status updated to "SUPERSEDED by ADR-MONO-003b ACCEPTED transition") — this ADR documents the supersession path.
- **Status transition history** — empty section ready for the ACCEPTED row append when the launch happens.

### B. Cross-reference updates

- Memory `project_monorepo_template_strategy.md` — update the Phase 5 launch-trigger line to mention ADR-MONO-003b is now PROPOSED (criteria are authored; launch still gated on user-explicit + ACCEPTED transition).
- `ADR-MONO-003a § 3 Audit trail` — append row for this PR (covered by D4 OVERRIDE § D1.2 if classified as a meta-policy ADR; explicitly call out that this is a meta-policy ADR — no implementation gate, no service-code change, so the audit-trail row pattern matches PR #395 / #396 precedent).

### C. No other changes

- No update to `scripts/extract-template.sh` (D2 references its current behaviour; if a procedural gap is found during authoring, file a follow-up task — do not bundle).
- No update to `scripts/verify-template-readiness.sh` (already diagnostic-only per ADR-MONO-003a § D4).
- No update to TEMPLATE.md § Phase 5 (which already enumerates the steps); this ADR layers decisions over the existing TEMPLATE.md procedure.
- No update to ADR-MONO-003 body (immutable historical record).

## Out of Scope

- ACCEPTED transition of ADR-MONO-003b. This task only lands PROPOSED. ACCEPTED transition is a separate event (user-explicit intent + status flip commit + audit-trail row append).
- Actual Phase 5 launch (running `scripts/extract-template.sh` + creating GitHub repo + enabling Template flag). Gated on ACCEPTED.
- Resolving the Template repo name. ADR-003b records `project-template` as the working name (already in TEMPLATE.md § Phase 5); final name is a one-line update at ACCEPTED time if the owner changes their mind.
- Filing the post-launch sync automation (script / cron / workflow). ADR-003b D3 records the mechanism intent; the implementation task gets filed after ACCEPTED.
- Re-evaluating `scripts/extract-template.sh` correctness. Existing script is treated as authoritative for the procedure spec; correctness validation happens at launch dress rehearsal.

---

# Acceptance Criteria

- [ ] `docs/adr/ADR-MONO-003b-phase-5-launch-criteria.md` exists with `Status: PROPOSED`, dated 2026-05-13.
- [ ] ADR-003b contains: Context / Decision (D1–D5) / Consequences / Alternatives Considered (≥3) / Relationship to ADR-MONO-003+003a / Status transition history section (empty placeholder).
- [ ] **D1 (Launch readiness criteria)** contains ≥5 enumerated criteria covering at minimum: (i) `scripts/verify-template-readiness.sh` execution result (diagnostic, not gate), (ii) seed-source decision (which project / generic skeleton), (iii) target repo URL + visibility, (iv) library-layer churn quiescence assessment, (v) user-explicit intent recorded.
- [ ] **D2 (Extraction procedure)** references `scripts/extract-template.sh` and its current flags + post-extraction GitHub repo setup (template flag, branch protection, default branch name).
- [ ] **D3 (Sync mechanism)** records: (i) direction (monorepo → Template only; back-porting to old projects is manual), (ii) cadence (monthly or on-demand, not strict timer), (iii) tooling (planned: per-library script or manual rsync), (iv) initiator (project owner, not automation).
- [ ] **D4 (Rollback procedure)** records: how to retire the Template repo (delete or archive on GitHub + update this ADR Status → SUPERSEDED-rollback + new ADR-MONO-003c authoring path).
- [ ] **D5 (ACCEPTED transition mechanics)** specifies: (i) the form of user-explicit statement that satisfies the gate (e.g. "launch ADR-003b ACCEPTED" or equivalent), (ii) the commit pattern (single-line Status change + audit-trail row append in this ADR + audit-trail row append in ADR-003a § 3), (iii) the same-PR launch artifact list (script execution log, new GitHub repo URL).
- [ ] Memory `project_monorepo_template_strategy.md` updated to reference ADR-003b PROPOSED.
- [ ] ADR-MONO-003a § 3 audit trail row appended (or noted as forward-link from this PR closure).
- [ ] No service code touched. No `libs/` / `apps/` / `projects/` diff.
- [ ] CI green (path-filter `rules` + path-filter `docs(adr)` flags only — no service integration / E2E / boot-jars jobs triggered).

# Related Specs

- `docs/adr/ADR-MONO-003-phase-5-template-extraction-deferred.md` (D1 DEFERRED parent state)
- `docs/adr/ADR-MONO-003a-d4-override-scope-canonicalization.md` § D4 (cites ADR-MONO-003b by name as the trigger)
- `docs/adr/ADR-MONO-002-phase-4-template-extraction-trigger.md` (Discovery → Distribution strategy parent)
- `TEMPLATE.md` § Phase 5 (existing procedure enumeration; this ADR layers decisions over it)
- `scripts/extract-template.sh` (D2 references)
- `scripts/sync-portfolio.sh` (D3 reference for "portfolio sync" precedent, but Template-sync is a different operation)
- `scripts/verify-template-readiness.sh` (D1 references as diagnostic input)
- Memory `project_monorepo_template_strategy.md` (current Phase 5 strategy state)

# Related Contracts

None — meta-policy ADR. No HTTP / event contract change.

# Edge Cases

- **Seed-source decision deferred to ACCEPTED transition** — D1 enumerates "seed source" as a launch readiness criterion but ADR-003b PROPOSED does not pre-pick which project. TEMPLATE.md § 1.20 already notes the Template is a "flat single-project shell" (not any specific project's content). D1 wording: "seed source is decided at ACCEPTED transition time, recorded in the ACCEPTED row of this ADR's audit trail."
- **`extract-template.sh` does not yet seed a specific project** — the existing script copies the shared library layer + structural shell. ADR-003b PROPOSED treats this as authoritative ("the script already does what § D2 says"). If a dress rehearsal during ACCEPTED preparation reveals a gap, that becomes a follow-up task (not blocking).
- **Sync mechanism tooling does not yet exist** — D3 records intent ("monthly or on-demand, planned script `scripts/sync-template.sh`"). No commitment to authoring it before ACCEPTED; it's only needed once the Template repo exists.
- **`scripts/sync-portfolio.sh` vs `scripts/sync-template.sh` confusion** — `sync-portfolio.sh` extracts each project to its own standalone repo (already operational, used monthly per memory `project_portfolio_submission_strategy`). Template-sync is a different operation: monorepo's shared library layer → Template repo, one-way. ADR-003b D3 disambiguates.
- **Rollback after launch** — if Template repo exists but the strategy proves wrong (e.g. drift outpaces sync, no new projects materialise), rollback = delete GitHub repo + update ADR-003b Status to SUPERSEDED-by-rollback. Old projects forked from the Template are unaffected (they own their library snapshot at fork time).
- **ADR-003a § D4 cites ADR-003b "PROPOSED → ACCEPTED"** — this PROPOSED ADR satisfies the existence half of the gate. The ACCEPTED transition is the other half, triggered by user-explicit intent. The gate cannot be auto-satisfied by this PR alone.

# Failure Scenarios

- **Reviewer asks "why PROPOSED not ACCEPTED?"** — answer: ACCEPTED status implies the launch decision is made; this PR is to record criteria, not the decision. PROPOSED is the correct status for criteria documents authored before the decision moment, consistent with ADR-MONO-003 (also DEFERRED, not ACCEPTED at authoring time).
- **Reviewer asks "is gap #4 (Chrome DevTools MCP) related?"** — no. Gap #4 is harness-tooling DX (OpenAI Harness gap series, scope already in ADR-003a § D1.2). Phase 5 launch is a portfolio milestone, orthogonal to harness work.
- **Reviewer asks "does this commit itself fall under D4 OVERRIDE?"** — yes, as a meta-policy ADR landing under ADR-003a § D1.2-adjacent precedent. PR description cites ADR-MONO-003a § D1 (meta-policy ADR authoring, structurally identical to TASK-MONO-063 PR #395 precedent — a meta-policy ADR with no implementation gate). Audit-trail row in ADR-003a § 3 appended at PR merge.
- **Reviewer asks "what if Phase 5 is abandoned?"** — covered by ADR-MONO-003a § D4 row 2: "Phase 5 abandonment decision: user-explicit + new ADR (ADR-MONO-003c or supersede ADR-MONO-003 entirely)". ADR-003b is for launch, not abandonment. Abandonment path documented but not authored here.

---

# Implementation Plan

1. Author `docs/adr/ADR-MONO-003b-phase-5-launch-criteria.md` per the structure above (Status: PROPOSED).
2. Update memory `project_monorepo_template_strategy.md` to reference ADR-003b PROPOSED.
3. (At PR merge) Append audit-trail row to `docs/adr/ADR-MONO-003a-d4-override-scope-canonicalization.md` § 3 — same PR or a same-day chore.
4. Single bundled commit (spec + memory; ADR-003a audit-trail row deferred to merge chore or co-bundled — both acceptable per `feedback_pr_bundling.md`).
5. Lifecycle: ready → in-progress → review on PR creation.
6. Push branch; user decides PR-open timing per `feedback_pr_on_request.md`.
7. After merge: close chore (ready → done; audit-trail row backfilled if not co-bundled).

# Estimated Cost

- Files: ADR-003b new (~250 LOC) + memory L3 update (~3 LOC) + ADR-003a § 3 row (~1 LOC, possibly deferred to merge chore) + this task file. Total ≈ 300 LOC additions.
- CI: path-filter `rules` + `docs(adr)` flags only → `Build & Test` lightweight job. ~20s baseline.
- Time: ~1.5 hour authoring + commit/push.

분석=Opus 4.7 / 구현 권장=Opus 4.7 (meta-policy authoring — D1 criteria phrasing + D3 sync mechanism judgement + D4 rollback completeness all require interpretive judgement; structurally identical to TASK-MONO-063 which was authored on Opus).
