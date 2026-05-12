# Task ID

TASK-MONO-064

# Title

Publish ADR-MONO-007 — worktree-isolated ephemeral observability stack (OpenAI Harness gap #3 Phase 0)

# Status

ready

# Owner

monorepo

# Task Tags

- spec
- adr
- harness

---

# Required Sections

- Goal
- Scope (In / Out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Goal

Close the **Phase 0 (policy)** slice of OpenAI Harness gap #3 by publishing **ADR-MONO-007** — pinning the observability stack choice (Vector + VictoriaLogs + VictoriaMetrics), the per-worktree ephemeral topology, the opt-in / e2e-scoped lifecycle, and the skill-mediated DX surface — **before any compose file, script, or skill body is authored**.

This is the third and final closure task in the OpenAI Harness gap series after gap A (lint remediation injection — ADR-MONO-006 ACCEPTED, TASK-MONO-059/060/061 DELIVERED) and gap #2 (doc-gardening recurrence — TASK-MONO-062 DELIVERED). Memory `reference_openai_harness_engineering.md` § "우선순위 액션 후보" item #3 → this task delivers the policy half; implementation follows in TASK-MONO-065/066/067.

The decision shape is pinned-first because three elements are irreversible once any compose file lands: stack family (Vector+Victoria vs. Grafana stack vs. ELK vs. cloud-hosted), topology (per-worktree vs. shared), and lifecycle (opt-in vs. always-on). Choosing implicitly during Phase 1 implementation would either retroactively ratify whichever shape happened to ship first, or force a 6-month migration. ADR-first matches the gap A precedent (TASK-MONO-059 ADR-MONO-006 before any hook authoring).

---

# Scope

## In Scope

### A. ADR-MONO-007 publication (this task's primary deliverable)

- New file `docs/adr/ADR-MONO-007-worktree-ephemeral-observability-stack.md` — full ADR per `platform/architecture-decision-rule.md` shape, ACCEPTED in the same PR (meta-policy ADR, no implementation gating beyond its own follow-up phases).
- Sections required: Context (§ 1) / Decision (§ 2 — D1 stack / D2 topology / D3 lifecycle / D4 DX target / D5 phasing) / Alternatives Considered (§ 3) / Consequences (§ 4) / Verification (§ 5 — delegated to follow-up phases) / Outstanding follow-ups (§ 6 — enumerates MONO-065/066/067 + ADR-MONO-007a trace deferral + gap #4 separate-ADR pointer) / Provenance (§ 7).
- Status line: `ACCEPTED 2026-05-12`. History line records PROPOSED → ACCEPTED on the same PR (matches ADR-MONO-006 precedent for meta-policy ADRs).

### B. Cross-reference updates

- `docs/adr/INDEX.md` — add ADR-MONO-007 row (Status ACCEPTED / Date 2026-05-12).
- `tasks/INDEX.md` (root, monorepo-level) — move this task from ready/ section after impl PR merges (closure chore, separate PR per the lifecycle convention used in MONO-059/-060/-061/-062).
- Memory `reference_openai_harness_engineering.md` § "monorepo-lab 갭 매핑" gap #3 row + § "우선순위 액션 후보" item #3 — annotate "**Phase 0 DELIVERED** 2026-05-12 (ADR-MONO-007 ACCEPTED, TASK-MONO-064)" without claiming full closure (implementation phases remain open).

### C. Follow-up filing — deliberate omission

Phase 1/2/3 follow-up tasks (TASK-MONO-065/066/067) are **NOT filed in this task's PR**. The ADR's § 6 enumerates them as outstanding, but each is filed individually when its prerequisite merges (precedent: TASK-MONO-060 was filed only after MONO-059 merged, not bundled). This keeps the spec PR focused on the policy decision and avoids the "filed-but-blocked" pattern that bloats the `backlog/` directory.

## Out of Scope

- **Any compose file, script, skill body, or e2e wiring.** All implementation belongs to Phase 1+ (MONO-065 onwards). This task is policy-only.
- **Trace layer decision.** ADR-MONO-007 § 2.1 D1 defers traces to a future ADR-MONO-007a, conditioned on Phase 2 (MONO-066) closure proving the LogQL+PromQL DX is sufficient on its own.
- **Chrome DevTools MCP / visual regression (gap #4).** Distinct OpenAI Harness gap; will get its own ADR (ADR-MONO-008+) when frontend visual verification becomes a recurring blocker.
- **Production observability stack.** admin-service already has its own Operations endpoint + per-topic Kafka lag (TASK-BE-046 DELIVERED). This ADR is about the per-agent ephemeral spine, not the production-facing monitoring story.
- **Modifying any service code** under `apps/` / `libs/` to emit additional telemetry. Existing Logback + Micrometer + OTel header emissions are the assumed baseline; Phase 1 demonstrates ingestion against that baseline without service-side changes.
- **CI activation.** ADR § 2.3 D3 explicitly excludes CI — gradle test reports + artifact upload remain the CI verification surface. The stack is for interactive sessions.

---

# Acceptance Criteria

- [ ] `docs/adr/ADR-MONO-007-worktree-ephemeral-observability-stack.md` exists with Status `ACCEPTED` 2026-05-12 and the full section structure required by `platform/architecture-decision-rule.md`.
- [ ] ADR § 2 (Decision) contains 5 numbered sub-decisions (D1 stack / D2 topology / D3 lifecycle / D4 DX / D5 phasing). Each sub-decision states "MUST" / "SHOULD" / "MAY" precisely.
- [ ] ADR § 3 (Alternatives) compares ≥3 options per major decision axis (stack family / topology / lifecycle / DX) using the table shape from ADR-MONO-006.
- [ ] ADR § 6 (Outstanding follow-ups) enumerates the four named successor items: TASK-MONO-065 (Phase 1) / TASK-MONO-066 (Phase 2) / TASK-MONO-067 (Phase 3) / ADR-MONO-007a (trace deferral) — plus the gap #4 separate-ADR pointer.
- [ ] `docs/adr/INDEX.md` shows ADR-MONO-007 row, sorted at the end (chronological, after ADR-MONO-006).
- [ ] Memory `reference_openai_harness_engineering.md` § "monorepo-lab 갭 매핑" gap #3 row updated to reflect Phase 0 DELIVERED state, *without* prematurely marking the gap closed.
- [ ] D4 OVERRIDE applicability quoted from ADR-MONO-003a § D1.3 (Harness gap series scope) — this task's last_churn impact is minimal.
- [ ] No file under `libs/`, `apps/`, `projects/<name>/`, or `infra/` modified by this task. Spec-only.
- [ ] Conventional commit prefix: `spec(adr)+task(mono-064):` or equivalent.

---

# Related Specs

- Memory `reference_openai_harness_engineering.md` § "monorepo-lab 갭 매핑" gap #3, § "다이어그램 3개 = 에이전트의 3감각" (Vector + Victoria stack details), § "우선순위 액션 후보" item #3
- `docs/adr/ADR-MONO-003a-d4-override-scope-canonicalization.md` § D1.3 (Harness gap series D4 OVERRIDE scope)
- `docs/adr/ADR-MONO-006-lint-remediation-as-agent-context.md` (gap A precedent — meta-policy ADR shape, same-PR ACCEPTED pattern)
- `platform/architecture-decision-rule.md` (ADR section structure)
- `platform/lint-remediation-message-standard.md` (4-block format that ADR § 2.4 D4 will require Phase 2 skill output to follow)

# Related Skills

- `.claude/skills/cross-cutting/*` (Phase 2 will add an observability-query skill here — not in scope for this task)

---

# Related Contracts

None — meta-policy ADR has no HTTP / event contract surface. The skill contract surface lands in Phase 2 (`OBSERVE-QUERY-NN` rule-id namespace defined in ADR § 2.4 D4).

---

# Target Service

N/A — monorepo-level spec authoring. Targets:

- `docs/adr/ADR-MONO-007-worktree-ephemeral-observability-stack.md` (new)
- `docs/adr/INDEX.md` (extension)
- `tasks/INDEX.md` root (lifecycle move)
- Memory `reference_openai_harness_engineering.md` (cross-reference)

---

# Architecture

This task authors a meta-policy ADR + lifecycle housekeeping. There is no runtime architecture change. The ADR itself describes the stack architecture for follow-up phases:

- **Stack:** Vector → VictoriaLogs (LogQL) + VictoriaMetrics (PromQL). Traces deferred.
- **Topology:** docker-compose per worktree, dynamic ports, tmpfs storage, isolated network namespace per worktree.
- **Lifecycle:** opt-in via `-Pobservability=on` Gradle attribute or manual `scripts/observability/{up,down}.sh`. 5-minute idle teardown.
- **DX:** `.claude/skills/cross-cutting/observability-query/` exposing `/observe logs|metrics|trace` slash commands. Output failures in 4-block format with `OBSERVE-QUERY-NN` rule IDs.

Phase 1+ tasks bind each item to concrete files.

---

# Implementation Notes

## ADR drafting checklist

1. Section 1 (Context) — name the gap from memory, name the OpenAI mechanism, justify policy-before-implementation. ADR-MONO-006 § 1 is the template.
2. Section 2 (Decision) — five sub-decisions, each with concrete numbers / paths where applicable (e.g., 200 MB footprint cap, 30 s start time, `127.0.0.1:0` port binding, `OBSERVE-QUERY-NN` rule-id namespace).
3. Section 3 (Alternatives) — at least one rejected alternative per major axis. Cite footprint numbers / operational complexity reasons. ADR-MONO-006 § 3 is the table template.
4. Section 4 (Consequences) — separate positive / negative / mitigation owner. Trace deferral mentioned as a known acceptable risk.
5. Section 5 (Verification) — explicitly delegate verification to follow-up phases (this is a policy ADR with no executable behaviour of its own; ADR-MONO-006 used this exact pattern).
6. Section 6 (Outstanding follow-ups) — enumerate the 5 successors named in this task's § Goal.
7. Section 7 (Provenance) — quote the memory section, cite the D4 OVERRIDE authority, record model annotation.

## D4 OVERRIDE applicability

ADR-MONO-003a § D1.3 lists "OpenAI Harness gap series" as in-scope for D4 OVERRIDE (`last_churn` clock relaxation on shared paths within the series). This task's only shared-path touches are:

- `docs/adr/ADR-MONO-007-...` (new file, additive, no relaxation of any existing rule)
- `docs/adr/INDEX.md` (additive row)
- `tasks/INDEX.md` (additive row + lifecycle move)

All three are cumulative authoring on the Harness gap series surface — the same shape as MONO-059's CLAUDE.md § Hard Stop Rules expansion and MONO-062's `platform/lint-remediation-message-standard.md` § Emission contracts extension. Last-churn impact is minimal; the OVERRIDE applies as it did for those PRs.

## Commit shape

Single commit for this task — spec PR pattern. Conventional commit prefix:

```
spec(adr)+task(mono-064): publish ADR-MONO-007 — worktree-isolated ephemeral observability stack (OpenAI Harness gap #3 Phase 0)
```

The closure chore (ready → done) lands in a separate small commit / PR after the spec PR merges, matching MONO-059/060/061/062 lifecycle precedent.

---

# Edge Cases

- **ADR Status remains PROPOSED indefinitely.** Avoided by publishing as ACCEPTED in the same PR (meta-policy ADRs use this pattern — ADR-MONO-006 precedent). If reviewers in PR comments object to a decision, the standard ADR amendment flow (new PR amending § 2 + History entry) applies; ADR is not held hostage to "ACCEPTED later".
- **Follow-up task numbering collision.** TASK-MONO-065 onwards are reserved by this ADR's § 6 but not yet filed. If an unrelated task picks 065 before MONO-065 is filed, the unrelated task takes the number — ADR § 6 then updates to whichever ID is filed first. Numeric sequence is not load-bearing; the title + content reference is.
- **Trace deferral revisited too early.** ADR § 2.1 D1 conditions ADR-MONO-007a on Phase 2 (MONO-066) closure. If Phase 1 alone surfaces a saga-spanning bug that LogQL cannot resolve, the Phase 1 task may itself open ADR-MONO-007a; the ADR-MONO-007 § 6 wording supports either path.

---

# Failure Scenarios

- **PR review surfaces stack-family objection** (e.g., "we already have Prometheus — why VictoriaMetrics?"). Resolve in PR: VictoriaMetrics is Prometheus-compatible on the query side; the stack family choice is about footprint + operational homogeneity with VictoriaLogs. If consensus shifts, amend ADR § 2.1 D1 and re-merge.
- **Topology objection on shared dev-machine concern** (e.g., "200 MB × 3 worktrees = 600 MB"). Resolve in PR by tightening D2 idle-teardown threshold or making D3 stricter; the per-worktree premise stays because alternatives violate the agent-loop-isolation requirement.
- **D4 OVERRIDE challenged.** ADR-MONO-003a § D1.3 is the canonical authority; quote that section in the PR description. If challenged, the resolution path is a new PR amending ADR-MONO-003a, not a sidestep here.

---

# Test Requirements

N/A — spec authoring with no executable code. Verification:

1. ADR file renders correctly in GitHub preview (no broken markdown links, table alignment intact).
2. `docs/adr/INDEX.md` entry lints — same shape as ADR-MONO-006 row.
3. `tasks/INDEX.md` move from ready → review (this PR) → done (closure PR) follows the precedent of MONO-062 / MONO-063 closure.
4. CI green (path-filter matches `docs/adr/**` + `tasks/**` + memory non-impact = backend / frontend / integration / e2e jobs skipped per TASK-MONO-045+058 path-filter rules; only `changes` job + minimal verification runs).

---

# Definition of Done

- [ ] All Acceptance Criteria pass.
- [ ] ADR-MONO-007 file renders cleanly on GitHub.
- [ ] `docs/adr/INDEX.md` + `tasks/INDEX.md` (root) updated.
- [ ] Memory `reference_openai_harness_engineering.md` annotation reflects Phase 0 DELIVERED but does not claim gap #3 fully closed.
- [ ] PR description quotes the gap #3 Phase 0 closure annotation.
- [ ] CI green (subset of jobs per path-filter).
- [ ] Closure chore (`tasks/review` → `tasks/done`) follow-up PR opens after this spec PR merges.

---

# Provenance

Memory `reference_openai_harness_engineering.md` (2026-05-07 receipt) § "monorepo-lab 갭 매핑" — gap #3 (워크트리당 ephemeral 관측 스택) flagged as the third-priority gap after gap A (closed by ADR-MONO-006 + TASK-MONO-059/060/061) and gap #2 (closed by TASK-MONO-062 in-repo artifacts; harness-side routine registration is operator-side).

D4 OVERRIDE applies per ADR-MONO-003a § D1.3 — Harness gap series scope, user-acknowledged 2026-05-12.

분석=Opus 4.7 / 구현 권장=Opus 4.7 (ADR drafting + stack-choice judgment surface). Follow-up phases (MONO-065/066/067) may downgrade to Sonnet 4.6 for routine compose / script authoring per ADR § 2.5 D5 phasing.
