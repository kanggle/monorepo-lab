# Task ID

TASK-MONO-237

# Title

`/validate-rules` 2026-06-13 remediation — fix platform navigational orphans (abac-data-scope / access-conditions) + stale service-type skill path; hand the `.claude/` findings (code-reviewer↔review-task Critical + 3 command/agent warnings) to the operator as a patch (classifier-blocked).

# Status

ready

# Owner

backend

# Task Tags

- docs
- monorepo
- governance

---

# Dependency Markers

- **source**: `/validate-rules` full scan 2026-06-13 (4 parallel verification agents over skills/agents/commands/platform). Result: 1 Critical, 6 Warning, 3 Info; skills layer (74) fully clean, architecture.md (47) Service Types all valid, CLAUDE.md paths/anchors all resolve.
- **split**: `platform/` fixes are agent-committable and land in THIS task's PR. The `.claude/` findings (hooks/agents/commands) are **classifier-blocked for AI edit+commit** (memory `env_classifier_claude_self_mod_block`) → handed to the operator as a verbatim patch (see § Operator Patch below); this task records them but does not commit them.
- **model**: 분석=Opus 4.8 / **구현 권장=Sonnet** (docs/governance edits, single shared layer).

---

# Goal

Resolve the mechanically-fixable `/validate-rules` findings so the platform rule surface is internally consistent and the two ADR-MONO-025/026 authorization contracts are discoverable from the standard reading order, and give the operator a ready-to-apply patch for the `.claude/` findings the classifier blocks the agent from committing.

# Scope

## In scope — `platform/` (committed in this task's PR)

1. **W1 — `platform/service-types/INDEX.md` Selection Rule 3 stale skill path**: `.claude/skills/service-types/<type>-setup.md` → `.claude/skills/service-types/<type>-setup/SKILL.md` (skills are folders containing `SKILL.md`; all 8 existing follow the folder form).
2. **W2 — `platform/abac-data-scope.md` navigational orphan** (ADR-MONO-025 ACCEPTED): add to `platform/README.md` § What Lives Here + a cross-reference in the always-read Core spec `platform/security-rules.md` (new "Related Authorization Contracts" section).
3. **W3 — `platform/access-conditions.md` navigational orphan** (ADR-MONO-026 ACCEPTED): same treatment as W2.
   - **Design note**: discoverability is added via (a) `platform/README.md` index rows and (b) a pointer from `security-rules.md` (which IS in `entrypoint.md` Core "Always Read"), rather than a new `entrypoint.md` Auxiliary **tag** — a new tag would trigger the `entrypoint.md` line-122 rule requiring a same-PR update to `.claude/config/activation-rules.md`. The Core-pointer path is lighter and guarantees discovery (Core is always read).

## Out of scope — `.claude/` (operator-applied; classifier-blocked for AI)

Recorded here as the authoritative finding list; the operator applies the § Operator Patch.

- **Critical — `code-reviewer` agent ↔ `review-task` command capability mismatch**: `review-task.md` Phase 2 Agent Prompt Template (step 11 + Fix Task Rules) instructs the `subagent_type: "code-reviewer"` subagent to (a) create a fix task file in `tasks/ready/` and (b) move the task `review/ → done/`. But `.claude/agents/common/code-reviewer.md` declares `tools: Read, Glob, Grep, Bash` — no `Write`/`Edit`. The sanctioned file-authoring path is absent (only a Bash workaround exists, contradicting the agent's read-only design + the dedicated-tool convention). **Resolution (recommended)**: keep the reviewer read-only — move the lifecycle file operations (task move + fix-task creation) to `review-task.md` Phase 3 (main context / orchestrator). Alternative: add `Write, Edit` to the `code-reviewer` `tools` list.
- **W4 — `.claude/agents/common/database-designer.md` (~line 43)**: inline link `schema-change-workflow/SKILL.md` missing the `.claude/skills/database/` prefix (target file exists; link is broken). Fix → `.claude/skills/database/schema-change-workflow/SKILL.md`.
- **W5 — `.claude/commands/write-tests.md`**: procedure starts at `platform/testing-strategy.md` without the project-classification step (read `PROJECT.md` → load domain/trait rule layers) that the other code-touching commands carry. Add the classification step before test authoring.
- **W6 — `.claude/commands/validate-rules.md`**: minor — `skills/INDEX.md` path references should consistently be `.claude/skills/INDEX.md`.

## Out of scope (Info, no action)
- backend-engineer ↔ qa-engineer shared `backend/testing-backend` (implicit but adequate boundary); data-engineer ↔ backend-engineer batch (documented placeholder boundary); write-tests ↔ implement-task partial overlap.

# Acceptance Criteria

- **AC-1**: `platform/service-types/INDEX.md` Selection Rule 3 names `.claude/skills/service-types/<type>-setup/SKILL.md` (folder form).
- **AC-2**: `platform/abac-data-scope.md` and `platform/access-conditions.md` each appear in `platform/README.md` § What Lives Here and are cross-referenced from `platform/security-rules.md`.
- **AC-3**: Edits are additive — no existing rule weakened; markdown well-formed; HARDSTOP-03 N/A (shared, project-agnostic content only).
- **AC-4**: The `.claude/` findings (Critical + W4/W5/W6) are recorded with a verbatim operator patch; applied + committed by the operator (classifier blocks AI `.claude/` edit+commit). After the operator lands them, this task may close.

# Related Specs / Code

- `platform/service-types/INDEX.md`, `platform/README.md`, `platform/security-rules.md`, `platform/abac-data-scope.md`, `platform/access-conditions.md`.
- `.claude/agents/common/code-reviewer.md`, `.claude/commands/review-task.md`, `.claude/agents/common/database-designer.md`, `.claude/commands/write-tests.md`, `.claude/commands/validate-rules.md`.
- Memory: `env_classifier_claude_self_mod_block`.

# Related Contracts

- None (governance/docs — no API or event contract changed).

# Edge Cases / Failure Scenarios

- **Classifier hard-block** — AI cannot edit/commit `.claude/` hooks/agents/commands; the § Operator Patch is human-applied. `platform/` is unaffected and committed here.
- **No new entrypoint tag** — deliberately avoided to skip the activation-rules.md same-PR coupling (entrypoint.md line 122); Core-pointer path chosen instead.
- **Self-dogfooding** — authored in a dedicated `mlab-mono237` worktree off `origin/main`.

# Notes

- `/validate-rules` is read-only by definition; this task is the remediation follow-up. Platform fixes land here; `.claude/` fixes handed to the operator.
