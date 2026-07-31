# Task ID

TASK-MONO-499

# Title

Resolve /validate-rules findings from the 2026-07-31 run (1 Critical + 3 Warnings; 1 Warning verified as phantom)

# Status

review

# Owner

monorepo

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

A 2026-07-31 `/validate-rules` full scan (CLAUDE.md + 37 `platform/` files + 74 `SKILL.md` + 15 agent files + 11 commands) found 1 Critical, 4 Warning, and 5 Info findings. This task resolves the Critical and 3 of the 4 Warnings; the 4th Warning was re-verified during this task and found to be a phantom (the two documents it named are not actually in conflict — see Edge Cases). The 5 Info items (an ADR citation typo, uncommitted working-tree state, a minor doc-duplication note, a naming-style note, and an agent frontmatter completeness note) are out of scope here — the citation typo was already fixed by `TASK-MONO-498`, and the rest are lower-priority style/completeness notes, not correctness gaps.

1. **Critical — `code-reviewer` vs `review-checklist` vs `/review-task`**: `.claude/agents/common/code-reviewer.md`'s `Does NOT` section disclaimed "verify test-coverage completeness against acceptance criteria (→ `qa-engineer`)", but the checklist it is mandated to run (`.claude/skills/review-checklist/SKILL.md` § Spec Compliance / § Testing) requires exactly that (AC-met check, Edge-Case/Failure-Scenario coverage check), and `/review-task` never dispatches `qa-engineer` at all. Resolved by narrowing the Does-NOT clause: `code-reviewer` still runs the checklist's AC/Edge-Case verification as-is (that's mechanical checking, not test authorship); what it does NOT do is author new tests or design broader test-strategy coverage beyond what the task's own AC/Edge-Cases/Failure-Scenarios enumerate — that stays `qa-engineer`'s territory.
2. **Warning — `.claude/skills/testing/testcontainers/SKILL.md`**: its `@DynamicPropertySource` example registered `jwt.secret` as a shared symmetric key, contradicting `backend/jwt-auth/SKILL.md`'s RS256/JWKS-only mandate (which explicitly lists a shared symmetric secret as the retired anti-pattern, TASK-BE-132). Replaced the example with a stubbed-JWKS-endpoint override, matching how this repo's services actually verify JWTs in integration tests.
3. **Warning — 27 `SKILL.md` files with no `specs/...` reference** (RULE-CONSISTENCY-01): added a one-line "No single spec" rationale (the repo's own established convention, already used by `backend/testing-backend/SKILL.md` and `cross-cutting/observability-query/SKILL.md`) to each, citing the most relevant canonical `platform/*.md` file per skill's topic. List: `backend/{dto-mapping,oauth-provider,redis-session,transaction-handling,validation}`, `cross-cutting/{caching,security-hardening}`, `database/{migration-strategy,schema-change-workflow,transaction-boundary}`, `frontend/{api-client,bundling-perf,component-library,form-handling,loading-error-handling,server-actions,state-management,testing-frontend}`, `infra/{ci-cd,cost-optimization,kubernetes-deploy,monitoring-stack,secrets-management,terraform-module}`, `testing/{fixture-management,test-strategy}` (26 files) + `testing/testcontainers` (item 2 above, already covered).
4. **Warning — `.claude/commands/implement-task.md` § Single Task Mode**: never referenced worktree isolation, unlike Batch Mode which mandates `isolation: "worktree"`. Added a step requiring `/start-task` (or the equivalent manual `git worktree add`) before any file is touched, applying identically to both modes — this was always the intent per `CLAUDE.md` § Concurrent-Session Worktree Isolation (a repo-wide rule, not a batch-only one), just undocumented in this command's single-task path.

---

# Scope

## In Scope

- `.claude/agents/common/code-reviewer.md` — narrow the Does-NOT clause (item 1 above).
- `.claude/skills/testing/testcontainers/SKILL.md` — fix the JWT example + add spec reference (item 2/3).
- 26 other `SKILL.md` files listed in item 3 above — add a one-line spec reference each.
- `.claude/commands/implement-task.md` — add the worktree-isolation step to Single Task Mode (item 4).

## Out of Scope

- The `rules/common.md` vs `platform/entrypoint.md` Warning from the same audit run — re-verified in this task (see Edge Cases) and found to be a phantom, not a real conflict. No file changed for it.
- The 5 Info items from the same audit run (ADR-053 citation typo — already fixed by `TASK-MONO-498`; uncommitted working-tree state — resolved by `TASK-MONO-498` landing; `review-task.md` § Close Chore inlining worktree setup instead of pointing to `/start-task`; command-naming singular/plural inconsistency; `backend-engineer.md` `skills:` frontmatter completeness) — lower-priority style notes, not correctness gaps, left for a future pass if picked up.
- Any application/service code, any `specs/` file — this task touches only `.claude/agents/`, `.claude/skills/`, and `.claude/commands/`.

---

# Acceptance Criteria

- [ ] `code-reviewer.md`'s Does-NOT clause no longer contradicts what `review-checklist/SKILL.md` mandates it check.
- [ ] `testing/testcontainers/SKILL.md` no longer models a shared symmetric JWT secret in its example.
- [ ] All 27 previously-non-compliant `SKILL.md` files (26 + testcontainers) now contain a `specs/` reference (verified via `Grep -l specs/` returning all 27).
- [ ] `implement-task.md` § Single Task Mode references worktree isolation before any implementation step.
- [ ] `git diff --stat` against `origin/main` touches only the files listed in Scope § In Scope, plus this task's own lifecycle files.
- [ ] CI green (doc-lint / dead-ref / rule-consistency guards; no test suite applies to this docs+agent-config-only change).

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read `PROJECT.md`, then load `rules/common.md` plus any `rules/domains/<domain>.md` and `rules/traits/<trait>.md` matching the declared classification. Unknown tags are a Hard Stop per `CLAUDE.md`.

- `.claude/agents/common/code-reviewer.md` (amended)
- `.claude/skills/review-checklist/SKILL.md` (read, not amended — this is the checklist code-reviewer must keep satisfying)
- `.claude/skills/backend/jwt-auth/SKILL.md` (read, not amended — the RS256/JWKS mandate the testcontainers fix now aligns with)
- `.claude/commands/implement-task.md` (amended)
- `.claude/skills/INDEX.md` (read, not amended — the authoring convention the 27 skill fixes follow)

---

# Related Contracts

None — this task changes only agent/skill/command governance docs, not any HTTP/event contract.

---

# Target Service

- Monorepo-level (`.claude/agents/`, `.claude/skills/`, `.claude/commands/`) — no single service.

---

# Architecture

N/A — documentation/agent-config-only task.

---

# Implementation Notes

**Tooling hazard found and worked around**: `.claude/hooks/rule-consistency-check.ps1` intercepts every Edit/Write to a `SKILL.md`/agent/command file and blocks it if the resulting content doesn't satisfy its checks. For `SKILL.md` files specifically, it reconstructs the "resulting file" for an `Edit` call by doing a naive `.Replace(old_string, new_string)` against a fresh on-disk read (per the hook's own source comment) — this reconstruction can spuriously fail to find `old_string` and silently leave `$content` as the unchanged original, so the hook blocks even when the actual intended new content clearly contains the required `specs/` substring. **Workaround: use the Write tool (full-file content) instead of Edit for any `SKILL.md` change that needs to satisfy this hook** — Write supplies the hook with the true resulting content directly, sidestepping the reconstruction path entirely. All 27 skill-file fixes in this task used Write for this reason.

---

# Edge Cases

- **`rules/common.md` vs `platform/entrypoint.md` (the audit's 4th Warning) — verified as a phantom, not a real conflict.** The audit agent read `rules/common.md`'s line "이 파일에 등록된 규칙은 각 프로젝트의 `PROJECT.md`의 domain/traits와 무관하게 기본 baseline으로 동작한다" as a claim that all 14 indexed files must always be *read* on every task, and flagged `entrypoint.md`'s tag-gated Auxiliary layer as contradicting that. But `rules/common.md` line 53 explicitly states "이 매핑은 `platform/entrypoint.md`의 Auxiliary 섹션과 `README.md`의 resolution order가 공동으로 관리한다" — i.e. `rules/common.md`'s own text already defers the *when-to-read* question to `entrypoint.md`. The two documents answer different questions (universal *applicability* vs. task-tag-gated *read-trigger timing*) and were never in conflict; `entrypoint.md`'s design is intentional, not drifted. No fix needed. (This is itself a small instance of the repo's own recurring lesson — recorded across many done tasks — that a sweep's findings are hypotheses, not verdicts, and must be re-measured before acting on them.)

---

# Failure Scenarios

- Fixing the `rules/common.md`/`entrypoint.md` "Warning" without first re-reading both documents' full text would have produced a real regression (e.g. forcing all 14 files into every task's read path regardless of tag, contradicting `entrypoint.md`'s own already-cross-referenced design) — verify-first is why this task's Scope excludes it.
- Editing a `SKILL.md` via the `Edit` tool without knowing about the hook's reconstruction bug wastes round-trips on spurious blocks that look like a real content problem but aren't — use `Write` for these files.

---

# Test Requirements

- None (docs/agent-config-only). Run `.claude/hooks/` rule-consistency and doc-lint / dead-ref CI guards.

---

# Definition of Done

- [ ] Implementation completed
- [ ] Rule-consistency / doc-lint / dead-ref checks passing
- [ ] Contracts unchanged (verified — none applicable)
- [ ] Ready for review
