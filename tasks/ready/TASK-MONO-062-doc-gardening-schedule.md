# Task ID

TASK-MONO-062

# Title

Schedule `validate-rules` + `audit-memory` as a recurring doc-gardening agent (OpenAI Harness gap #2)

# Status

ready

# Owner

monorepo

# Task Tags

- spec
- schedule
- harness

---

# Goal

Convert the existing `validate-rules` and `audit-memory` skills from manual-invoke commands into **recurring doc-gardening agents** that fire on a fixed schedule (weekly) without human prompting. This closes the second-largest OpenAI Harness gap flagged in memory `reference_openai_harness_engineering.md` § "monorepo-lab 갭 매핑" — "doc-gardening 자동 백그라운드" (priority action candidate #2).

OpenAI's Harness Engineering report names background doc-gardening as one of three core enforcement primitives — agents that periodically check for documentation drift, auto-PR fixes for the trivial cases, and surface non-trivial findings for human triage. monorepo-lab has the skills (`validate-rules`, `audit-memory`) but invokes them only on manual prompt. The gap is the *recurrence*, not the skill body.

This is a complement to gap A's closure (TASK-MONO-059/060/061): gap A makes rule violations *active context* on the agent's next turn; gap #2 makes rule-drift detection *active context* on a weekly cadence. Together they cover both the synchronous (per-edit) and asynchronous (background) enforcement lanes.

---

# Scope

## In Scope

### A. Routine definition

Two scheduled routines via the harness's `/schedule` skill (Codex / Claude harness `routines`):

| Routine name | Invocation | Schedule | Output |
|---|---|---|---|
| `monorepo-lab-validate-rules-weekly` | Run the `validate-rules` skill against the current `main` HEAD | Every Monday 09:00 KST | If findings exist → open a draft PR titled `chore(rules): weekly validate-rules audit (<date>)` with the findings rendered into a body checklist. If clean → no-op (no PR). |
| `monorepo-lab-audit-memory-weekly` | Run the `audit-memory` skill against the user's `MEMORY.md` | Every Monday 09:30 KST (30 min after the rules audit so they don't compete for the same routine slot) | If findings exist → write a `memory/audit_findings_<date>.md` entry summarising stale / contradictory / dangling references, with a recommended action per finding. If clean → no-op. |

Schedule offset rationale: weekly Monday morning gives the user fresh context at the start of the work week without competing for routine-slot capacity with weekday work.

### B. Routine configuration

`/schedule` is a harness-side feature — the routine config is NOT a file in the repo. Document the configuration in `.claude/workflows/doc-gardening.md` (new) so future sessions can reconstruct the routines if they're deleted via UI:

- Routine names + schedules
- Prompt body for each routine (verbatim — the harness consumes this as the routine's instructions)
- Expected output shape (PR title format / memory file format)
- Failure handling (routine crash logs land in the harness's run history; manual re-run is the recovery path)

### C. Output format alignment

Both routines MUST emit findings in the 4-block remediation message standard defined in [`platform/lint-remediation-message-standard.md`](../../platform/lint-remediation-message-standard.md), reusing the rule-id namespaces:

- `validate-rules` → `RULE-CONSISTENCY-NN` (extends the hook namespace; new IDs `05+` for drift findings the hook can't synchronously detect — e.g. domain/trait taxonomy drift, INDEX vs filesystem drift).
- `audit-memory` → new namespace `MEMORY-AUDIT-NN` (01 = stale memory, 02 = contradiction across memories, 03 = dangling repo reference, 04 = CLAUDE.md duplicate).

The format alignment means the routine PR / memory finding output slots directly into the same shape as agent-emitted Hard Stop messages — reviewers can read both with the same mental model.

### D. Documentation

- `.claude/hooks/README.md § Inventory` — add a new section "## Scheduled routines (gap #2)" enumerating the two routines and pointing to `.claude/workflows/doc-gardening.md`.
- `platform/lint-remediation-message-standard.md § Emission contracts` — add a new row for "Scheduled routine finding" describing the PR-as-output / memory-file-as-output channel.
- Memory `reference_openai_harness_engineering.md` § "우선순위 액션 후보" item #2 — partial closure annotation (DELIVERED on impl PR merge).

## Out of Scope

- **Auto-merge of doc-gardening PRs**. The first iteration always opens a *draft* PR so the user manually reviews. Auto-merge for trivial drift (e.g. INDEX line-count fixes) is a separate Phase 2 task if the manual review proves consistently rubber-stamped.
- **Real-time drift detection** (i.e. hook-style synchronous PreToolUse). The `validate-rules` skill checks structural invariants that span multiple files — too expensive for per-edit Hook execution. Stays asynchronous / scheduled.
- **`refactor-code` / `simplify` / `review` skills as routines**. These are too expensive to run weekly across the full repo and produce too much output. Manual invocation remains the right pattern.
- **CI integration of routine output**. The routine produces PRs / memory files. CI consumes those normally on PR open. No special CI hookup needed.
- **Multiple cadences** (daily / hourly). Weekly is the right starting cadence — drift accumulates slowly, and a daily PR would create noise. Move to daily if findings consistently exceed the weekly window's capacity.

---

# Acceptance Criteria

- [ ] Two routines registered via `/schedule` skill: `monorepo-lab-validate-rules-weekly` (Mon 09:00 KST) + `monorepo-lab-audit-memory-weekly` (Mon 09:30 KST).
- [ ] `.claude/workflows/doc-gardening.md` documents the routine names, schedules, prompt bodies, and output shapes.
- [ ] Routine prompts instruct the agent to emit findings in 4-block format (`RULE-CONSISTENCY-NN` / `MEMORY-AUDIT-NN`).
- [ ] First scheduled invocation succeeds end-to-end — either a draft PR opens (if drift exists) or the routine reports "no findings" via the harness's run-history UI.
- [ ] `.claude/hooks/README.md` updated with a "Scheduled routines" section + cross-reference to the workflows file.
- [ ] `platform/lint-remediation-message-standard.md § Emission contracts` adds the scheduled-routine row.
- [ ] No production code under `libs/` or `projects/` modified.

---

# Related Specs

- `platform/lint-remediation-message-standard.md` (format alignment target)
- `.claude/hooks/README.md` (cross-reference target)
- `.claude/workflows/doc-gardening.md` (new — routine config doc)
- Memory `reference_openai_harness_engineering.md` § "우선순위 액션 후보" item #2 (provenance)
- Memory `reference_openai_harness_engineering.md` § "강제 메커니즘 핵심 3가지" item #3 ("백그라운드 doc-gardening / refactor 에이전트")

# Related Skills

- `validate-rules` (user-level / plugin-supplied — the routine invokes this)
- `audit-memory` (user-level / plugin-supplied — the routine invokes this)
- `schedule` (harness-supplied — the routine registration mechanism)

---

# Related Contracts

None — agent harness configuration only.

---

# Target Service

N/A — agent harness configuration. Targets:

- `.claude/workflows/doc-gardening.md` (new)
- `.claude/hooks/README.md` (cross-reference update)
- `platform/lint-remediation-message-standard.md` (emission contracts table extension)
- 2 harness-side routines (config lives in the harness UI, not in repo)

---

# Architecture

The routine is a thin wrapper:

1. Harness scheduler fires at the configured time.
2. The routine prompt opens a session in the repo's `main` working directory.
3. The agent invokes the named skill (`validate-rules` or `audit-memory`).
4. The skill returns findings (or empty).
5. The routine prompt instructs the agent: if findings, format as a draft PR / memory file; else exit silently.

No new code or runtime in the monorepo. The mechanism is entirely on the harness side.

---

# Implementation Notes

## Routine prompt body — `validate-rules` weekly

```
You are the weekly validate-rules doc-gardening agent for monorepo-lab.

Run the validate-rules skill against the current main HEAD. If the skill reports
no findings, exit with a one-line "no findings" message — do NOT open a PR.

If findings exist, open a draft PR titled:

  chore(rules): weekly validate-rules audit (<YYYY-MM-DD>)

with body listing each finding in the 4-block format defined in
platform/lint-remediation-message-standard.md, using rule IDs
RULE-CONSISTENCY-05 onwards (the hook namespace is reserved 01..04 for
PreToolUse warnings; scheduled findings start at 05).

Do NOT auto-merge. Do NOT modify any files outside the PR. The PR exists so a
human reviewer can decide which findings to act on.

Reference: platform/lint-remediation-message-standard.md +
.claude/workflows/doc-gardening.md
```

## Routine prompt body — `audit-memory` weekly

```
You are the weekly audit-memory doc-gardening agent for the user.

Run the audit-memory skill against the user's MEMORY.md index. If the skill
reports no findings, exit with a one-line "no findings" message.

If findings exist, write a single memory entry at:

  memory/audit_findings_<YYYY-MM-DD>.md

Type: project. Body lists each finding in the 4-block format with rule IDs
MEMORY-AUDIT-NN (01 = stale memory, 02 = contradiction across memories,
03 = dangling repo reference, 04 = CLAUDE.md duplicate; pick the appropriate
ID per finding).

The next manual session will pick up the finding, decide which memories to
update or delete, and remove this audit_findings entry when done.

Do NOT auto-modify other memory entries. The audit is advisory.

Reference: ~/.claude/projects/.../memory/MEMORY.md +
platform/lint-remediation-message-standard.md
```

## D4 churn-clock interaction

This task is scope-extended under the OpenAI Harness gap series (ADR-MONO-006 § Provenance — user-acknowledged D4 OVERRIDE for the series). Implementation touches `.claude/workflows/` (new) + `.claude/hooks/README.md` (existing) + `platform/lint-remediation-message-standard.md` (existing) — all shared paths. `last_churn` marker reset minimal (cleanup-class authoring consistent with PR #383 / #386 / #388 precedent).

---

# Edge Cases

- **Routine fires while the repo has uncommitted PR-side changes** — routines operate against main HEAD, not against in-flight branches. Findings reflect main state, not branch state. If a branch is fixing a finding that the routine just published, the routine PR can be closed manually as superseded.
- **Routine output duplicates prior week's findings** — if the user hasn't actioned the prior week's PR, the new routine opens a second PR with overlapping findings. The PR title includes the date so the user can see they're duplicates. First-iteration acceptable behaviour; if it becomes noisy, add "skip if open PR already covers" check in the routine prompt.
- **`validate-rules` skill body changes upstream (plugin repo)** — the routine prompt does not depend on skill internals; only on its public output shape. Skill body changes are absorbed transparently.
- **Routine timeout / harness failure** — routine logs land in the harness's run-history UI. No retry policy in v1 — user manually re-runs if a scheduled invocation fails. Add retry policy if observed unreliable.

---

# Failure Scenarios

- **PR title collision with manual chore PRs** — `chore(rules): weekly validate-rules audit (...)` is unlikely to collide with manually-titled chore PRs since the date suffix is unique. No-op.
- **Memory entry name collision** (`audit_findings_<date>.md`) — same-day re-run would overwrite. Acceptable — the latter run's findings supersede.
- **False-positive flooding** — the `validate-rules` skill's detection accuracy is the upper bound. If it reports false positives, the routine PR has noise; the user can close-as-invalid. Tune the skill body if persistent.

---

# Test Requirements

N/A — harness configuration only, no executable code in the repo.

**Verification** (post-impl, when the first scheduled invocation lands):

1. Routine fires at the configured time (visible in harness UI run history).
2. If repo is clean → no PR opened; harness UI shows "no findings" message.
3. If repo has known drift (e.g. an intentionally-broken cross-ref): routine opens a draft PR with the finding rendered in 4-block format.
4. PR title format matches `chore(rules): weekly validate-rules audit (<YYYY-MM-DD>)`.

---

# Definition of Done

- [ ] All Acceptance Criteria pass.
- [ ] First scheduled invocation verified end-to-end (either no-op or PR).
- [ ] PR description quotes the gap #2 closure annotation (memory `reference_openai_harness_engineering.md` § "우선순위 액션 후보" #2 → DELIVERED).

---

# Provenance

Memory `reference_openai_harness_engineering.md` (2026-05-07 receipt) § "monorepo-lab 갭 매핑" — gap #2 (doc-gardening 자동 백그라운드) flagged as the second-priority gap after gap A (lint remediation injection, now closed via TASK-MONO-059/060/061).

D4 OVERRIDE applies per ADR-MONO-003 § 3.4 risk 2 — scope extension to OpenAI Harness gap series, user-acknowledged 2026-05-12.

분석=Opus 4.7 / 구현 권장=Sonnet 4.6 (harness configuration + thin documentation; routine prompt authoring is the main judgment surface, otherwise mechanical).
