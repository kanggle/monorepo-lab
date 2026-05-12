# Task ID

TASK-MONO-068

# Title

validate-rules + audit-memory cleanup — observability-query INDEX backfill + 3 memory rename + 2 MEMORY description sync

# Status

ready

# Owner

monorepo

# Task Tags

- chore
- cleanup
- audit

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

Close the 5 findings surfaced by the post-gap-#3 portfolio audit (2026-05-13):

- 1 Critical (`validate-rules`): `.claude/skills/INDEX.md` "Available Skills" table is missing the new `cross-cutting/observability-query/SKILL.md` row — TASK-MONO-066 (Phase 2) added the skill file but did not extend the INDEX.
- 3 Info (`audit-memory`): filename-vs-content mismatch on 3 user-level memory files (`_in_progress`, `_pending`, `_phase_0_partial` suffixes) whose content has since moved to `DONE` / `종결`. Filenames need rename + index link refresh.
- 2 Warning (`audit-memory`): 2 `MEMORY.md` description lines are out-of-date relative to the underlying memory file's body (`project_046_7_11_cycle_burn.md` description says "0/7 추가 회복" but body says "9/8 회복 완성"; `reference_openai_harness_engineering.md` description says "갭 3개...명확화" but the gaps are all DELIVERED).

The audit was triggered after the full OpenAI Harness gap series closure (gap A / #2 / #3 all DELIVERED 2026-05-12~13). With those large series done, the surfaced findings are the natural drift produced by 5 phase-PRs landing in a 2-day burst. This task cleans up the bookkeeping without changing any production / spec content.

---

# Scope

## In Scope

### A. `.claude/skills/INDEX.md` — add observability-query row (Critical)

Insert one row into the "Available Skills" table, alphabetised under the `cross-cutting/` section:

```
| Query the worktree-isolated ephemeral observability stack (LogQL + PromQL) | `cross-cutting/observability-query/SKILL.md` |
```

Position: directly after the existing `cross-cutting/observability-setup/SKILL.md` row, matching the `setup` ↔ `query` companion-skill convention referenced in the SKILL.md body.

### B. Memory file rename + MEMORY.md link refresh (3 files, user-level)

Memory directory: `C:/Users/kangdow/.claude/projects/c--Users-kangdow-dev-project-ai-project-monorepo-lab/memory/`

| Current filename | Renamed to | Rationale |
|---|---|---|
| `project_scm_be_series_in_progress.md` | `project_scm_be_series_complete.md` | Body: "INT-001 series 완전 종결 2026-05-07". The `_in_progress` suffix has been stale since the closure landed. |
| `project_b_common_rule_refactor_pending.md` | `project_b_common_rule_refactor_done.md` | Body: "DONE 5/5 (2026-05-11)". The `_pending` suffix has been stale since the closure. |
| `project_046_8_phase_0_partial.md` | `project_046_8_security_consumer_saga_done.md` | Body: "Phase 0 + 046-8a (DONE), SecurityServiceIT 20/20 PASS". The `_phase_0_partial` suffix predates the 046-8a closure. The new name reflects the closure topic (security-service consumer-pipeline saga).

Each rename requires:
1. `git mv` (memory dir is git-tracked at user level, but this monorepo's PR scope is the **monorepo** — memory rename happens outside the PR diff and is recorded in this task's Implementation Notes only).
2. Update the `MEMORY.md` index line's link target (`(filename.md)` portion) to the new filename.

### C. `MEMORY.md` description sync (2 entries, user-level)

| Memory file | Current description (stale) | Replacement |
|---|---|---|
| `project_046_7_11_cycle_burn.md` | "...PR #289 doc-only / #290 chore / #291 spec, **0/7 추가 회복**" | "...초기 046-7 (1/8 회복) + 046-7a (0/7 추가) 후 **BE-272/273/274 closure 시점에 9/8 회복 완성**. ADR-003/004 ACCEPTED. **메타 학습: local PASS / CI FAIL split 은 surface fix 미달성 신호, diagnostic harness 우선**." (body 와 동기) |
| `reference_openai_harness_engineering.md` | "...monorepo-lab 갭 3개(lint remediation 주입, doc-gardening 자동화, 워크트리 ephemeral 관측 스택) **명확화**." | "...monorepo-lab 갭 3개(lint remediation / doc-gardening / 워크트리 ephemeral 관측) **모두 DELIVERED 2026-05-12~13** (ADR-MONO-006 + ADR-MONO-007 + MONO-059/060/061/062/064/065/066/067). gap #4 Chrome DevTools MCP 만 미발화. Vector+VictoriaLogs/Metrics 실측 footprint Windows 27 / Linux 63 MiB 기록 포함." |

### D. `tasks/INDEX.md` (monorepo) — ready row added, lifecycle move on closure chore

Standard task lifecycle bookkeeping for this task file itself.

## Out of Scope

- **Promoting any memory rule to CLAUDE.md.** The audit found 0 promotion candidates — every memory entry is project-specific or personal preference. No common-rule extraction work.
- **Backfilling `specs/` references to existing SKILL.md files.** The `validate-rules` hook fires only on new edits, and the 50+ existing SKILL.md files without explicit `specs/` references are pre-existing state (cross-cutting skills often have no single source spec). Filing a separate task to backfill all of them would be high-cost / low-ROI; consider a `simplify` skill pass later if it becomes a recurring blocker.
- **Memory file body content updates beyond filename rename + MEMORY.md sync.** The body content of the 3 renamed files is already accurate. No content edits.
- **Re-running the audit after this PR.** This task closes the 5 specific findings; if new drift emerges from subsequent PRs, the weekly `validate-rules` + `audit-memory` doc-gardening routines (already scheduled per MONO-062, first run 2026-05-18) will catch it.

---

# Acceptance Criteria

- [ ] `.claude/skills/INDEX.md` "Available Skills" table has the `observability-query` row positioned after `observability-setup`.
- [ ] `path-set diff` between filesystem and INDEX of `.claude/skills/` is empty (re-run check: `find .claude/skills -name SKILL.md` ↔ INDEX backtick-paths).
- [ ] 3 memory files renamed via `git mv` in the user-level memory directory.
- [ ] `MEMORY.md` index links point to the new filenames; 2 stale description lines refreshed to match the file body.
- [ ] Task lifecycle: ready → review (this PR) → done (closure chore PR).
- [ ] No file under `libs/`, `apps/`, `projects/<name>/`, `infra/`, `scripts/`, `.github/workflows/` modified. Skill INDEX + tasks/ only.

---

# Related Specs

- `.claude/skills/INDEX.md` (the file being patched)
- `platform/lint-remediation-message-standard.md` (audit findings emit in this format)
- User-level `MEMORY.md` (the audit's primary subject — user's auto-memory directory)
- `tasks/done/TASK-MONO-066-observability-query-skill.md` (the skill whose INDEX entry was missed)

# Related Skills

- `validate-rules` (the skill that surfaced the Critical finding)
- `audit-memory` (the skill that surfaced the 3 Info + 2 Warning findings)

---

# Related Contracts

None — cleanup chore. No HTTP / event contract surface.

---

# Target Service

N/A — agent harness configuration + user-level memory.

---

# Architecture

Pure cleanup; no architectural decisions. Two surfaces touched:

1. **Monorepo** (PR scope): `.claude/skills/INDEX.md` row addition + this task file lifecycle.
2. **User-level memory** (out-of-PR): 3 file renames + 2 MEMORY.md description fixes. Recorded as Implementation Notes in this task only.

---

# Implementation Notes

## Why split the work between monorepo PR and user-level memory

The user-level `memory/` directory at `C:/Users/kangdow/.claude/projects/c--Users-kangdow-dev-project-ai-project-monorepo-lab/memory/` is the Claude Code auto-memory storage; it is **not** the monorepo's `tasks/` or `docs/` — it is per-user / per-machine state. Renames there do not produce a monorepo PR diff. This task records the planned memory-side changes here so future audit runs (the scheduled doc-gardening routines from TASK-MONO-062) have a paper trail of what was done, but the actual `git mv` happens in the user's home dir.

If the team adopts the OpenAI Harness pattern of "everything-in-repo" later (memory shared across team members via a central artifact rather than per-user state), this split disappears. For now, the user-level directory stays as the single source of truth for per-user memory.

## Why no `specs/` backfill for existing SKILL.md files

`validate-rules` Phase 1 inventoried 73 SKILL.md files. Spot-check showed ~50 of them have no literal `specs/` token in body (cross-cutting skills, infra skills, frontend skills — all genuinely operate without a single source spec). The `RULE-CONSISTENCY-01` hook fires only on new edits, not on existing-file drift, so the 50 files are not blocking new work. Backfilling all of them would either (a) add token-only "specs/" mentions purely to satisfy the regex (zero information value) or (b) require authoring synthetic spec references (high cost, dubious accuracy). Defer until a dedicated `simplify` or `refactor-skill` pass surfaces this as actionable.

## D4 churn-clock interaction

ADR-MONO-003a § D1.3 (Harness gap series) closed with TASK-MONO-067 (last entry in the gap series). This cleanup task is a **post-series** chore — strictly speaking it is no longer under the gap series scope. However, since the only shared-path touch is `.claude/skills/INDEX.md` (additive single row) and the rest is user-level memory + tasks/, the D4 churn-clock impact is minimal regardless. The task can claim the same shape as MONO-063 (canonicalization chore post-Harness-series start) or stand on its own as a standard cleanup chore.

## Commit shape

Single commit / single PR pattern. Conventional commit prefix:

```
chore(claude+tasks): validate-rules + audit-memory cleanup — observability-query INDEX backfill (post-gap-#3 audit)
```

The closure chore lands separately after this PR merges.

---

# Edge Cases

- **Memory `git mv` blocked by permission error.** User-level memory directory has different permissions than the monorepo working tree. If `git mv` fails, fall back to `mv` + `git rm` + `git add` (semantically equivalent — git tracks renames via content similarity at commit time).
- **`MEMORY.md` line-number drift between audit and fix.** The audit captured specific line content. If the user has manually edited `MEMORY.md` between the audit and this task's execution, re-grep for the description text rather than line number.
- **Audit finding becomes obsolete before fix lands.** Unlikely (between session continuation and PR open) but possible. If the `observability-query` INDEX row was added by another session before this task starts, the AC check (path-set diff empty) verifies completion and the task closes without an additive change.

---

# Failure Scenarios

- **INDEX row column count drift.** The "Available Skills" table format is 2 columns (Situation, Skill path). Adding a 3-column row would break rendering. Verify column count by `grep -c '^| ' .claude/skills/INDEX.md` before and after — same delta = +1, otherwise format drift.
- **MEMORY.md index link link-rot.** Renaming a file but forgetting to update the `[Title](file.md)` link is the audit's #3 dangling-reference class. The 3 renames in this task each get their link updated as part of the same edit; a re-run of `audit-memory --dry-run` after the PR confirms 0 dangling references.

---

# Test Requirements

N/A — cleanup chore. Verification:

1. `comm -23 <(find .claude/skills -name SKILL.md | sed 's|^\.claude/skills/||' | sort) <(grep -oE '[a-z][a-z0-9/_-]*SKILL\.md' .claude/skills/INDEX.md | sort -u)` → empty result.
2. `grep -c '^- \[' MEMORY.md` (entry count) unchanged before / after — 32 entries stay 32.
3. Re-running `audit-memory --dry-run` reports 0 stale / 0 contradiction / 0 dangling / 0 duplicate / 0 promote.

---

# Definition of Done

- [ ] AC items all pass.
- [ ] `audit-memory --dry-run` clean re-run confirms 0 findings.
- [ ] Closure chore PR opens after this PR merges.

---

# Provenance

Post-OpenAI-Harness-gap-#3 portfolio audit (2026-05-13). Audit run after TASK-MONO-067 (Phase 3 final) and its closure chore (TASK-MONO-067 lifecycle) landed on main. The audit surfaced 5 drift findings — none architectural, all bookkeeping.

User explicitly authorised the cleanup (2026-05-13 session).

Memory `reference_openai_harness_engineering.md` § "강제 메커니즘 핵심 3가지" item #3 ("백그라운드 doc-gardening / refactor 에이전트") — this task's findings would have been caught by the weekly `validate-rules` + `audit-memory` routines (MONO-062, first scheduled run 2026-05-18), but were caught manually 5 days earlier by the post-series audit. Both paths converge.

분석=Opus 4.7 / 구현=Sonnet 4.6 (pure mechanical cleanup, judgment-light).
