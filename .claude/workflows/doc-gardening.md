# Workflow: Doc Gardening (Scheduled Routines)

Recurring **doc-gardening** routines that detect documentation drift on a fixed weekly cadence. The routines are registered via the harness's `/schedule` skill — their config does NOT live as a file the routines read; this document is the **canonical reconstruction reference** so future sessions (or a fresh harness) can re-register the routines if they're deleted via UI.

OpenAI Harness Engineering gap #2 closure (TASK-MONO-062). Complements gap A: gap A makes per-edit violations active next-turn context (synchronous, via `.claude/hooks/`); gap #2 makes background drift detection active on a weekly cadence (asynchronous, via scheduled routines).

Reference: [`platform/lint-remediation-message-standard.md`](../../platform/lint-remediation-message-standard.md), memory `reference_openai_harness_engineering.md` § "강제 메커니즘 핵심 3가지" item #3, [`.claude/hooks/README.md`](../hooks/README.md) § Scheduled routines.

---

# Routines

| Name | Skill invoked | Schedule | Output channel |
|---|---|---|---|
| `monorepo-lab-validate-rules-weekly` | `validate-rules` (user-level plugin skill) | Mon 09:00 KST (`0 0 * * 1` Asia/Seoul) | Draft PR titled `chore(rules): weekly validate-rules audit (<YYYY-MM-DD>)`, or no-op if clean |
| `monorepo-lab-audit-memory-weekly` | `audit-memory` (user-level plugin skill) | Mon 09:30 KST (`30 0 * * 1` Asia/Seoul) | Memory file `memory/audit_findings_<YYYY-MM-DD>.md` (type=project), or no-op if clean |

Schedule offset rationale: Monday morning gives the user fresh context at the start of the work week. The 30-min gap between the two routines avoids them competing for the same routine-slot if the harness serialises by user account.

---

# Routine prompts (verbatim)

These are the strings registered via `/schedule` as the routine's instruction body. The harness invokes the named skill, captures findings, and applies the output channel rules below.

## `monorepo-lab-validate-rules-weekly`

```
You are the weekly validate-rules doc-gardening agent for monorepo-lab.

Run the validate-rules skill against the current main HEAD. If the skill reports
no findings, exit with a one-line "no findings" message — do NOT open a PR.

If findings exist, open a DRAFT PR (never ready, never auto-merge) titled:

  chore(rules): weekly validate-rules audit (<YYYY-MM-DD>)

with body listing each finding in the 4-block format defined in
platform/lint-remediation-message-standard.md, using rule IDs
RULE-CONSISTENCY-05 onwards (the hook namespace 01..04 is reserved for
PreToolUse warnings; scheduled findings start at 05).

Do NOT auto-merge. Do NOT modify any files outside the PR. The PR exists so a
human reviewer can decide which findings to act on.

The PR body header MUST cite:
  - Routine name: monorepo-lab-validate-rules-weekly
  - Trigger time: <ISO timestamp>
  - Reference: platform/lint-remediation-message-standard.md +
    .claude/workflows/doc-gardening.md

If validate-rules cannot run (e.g. harness misconfiguration), exit with an
explicit error message in the run history — do NOT silently no-op.
```

## `monorepo-lab-audit-memory-weekly`

```
You are the weekly audit-memory doc-gardening agent for the user.

Run the audit-memory skill against the user's MEMORY.md index. If the skill
reports no findings, exit with a one-line "no findings" message — do NOT write a
memory file.

If findings exist, write a single memory entry at:

  memory/audit_findings_<YYYY-MM-DD>.md

with frontmatter:

  ---
  name: Weekly audit-memory findings (<YYYY-MM-DD>)
  description: <one-line summary count, e.g. "3 stale + 1 dangling reference">
  type: project
  ---

Body lists each finding in the 4-block format with rule IDs MEMORY-AUDIT-NN:
  - MEMORY-AUDIT-01 = stale memory (file:line citations that no longer resolve
    or content that contradicts current repo state)
  - MEMORY-AUDIT-02 = contradiction across memories (two entries assert
    conflicting facts about the same topic)
  - MEMORY-AUDIT-03 = dangling repo reference (cites a path / function / flag
    that no longer exists)
  - MEMORY-AUDIT-04 = CLAUDE.md duplicate (memory body restates a rule that's
    already canonical in CLAUDE.md or platform/)

Each finding's [REMEDIATION] block MUST list at least two options (e.g.
"update the memory entry" / "delete the memory entry" / "promote to canonical
docs").

The NEXT manual session picks up the finding, decides which memories to update
or delete, and removes this audit_findings_<date>.md entry when done.

Do NOT auto-modify other memory entries. The audit is advisory only.

The memory body header MUST cite:
  - Routine name: monorepo-lab-audit-memory-weekly
  - Trigger time: <ISO timestamp>
  - Reference: .claude/workflows/doc-gardening.md
```

---

# Output channel rules

## Draft PR (`validate-rules`)

- PR is always **draft** on first iteration — auto-merge is explicitly out of scope (Phase 2 candidate if manual review proves consistently rubber-stamped, per TASK-MONO-062 § "Out of scope").
- Title format: `chore(rules): weekly validate-rules audit (<YYYY-MM-DD>)` — the date suffix is unique so subsequent weeks open distinct PRs even if findings overlap.
- If a PR from a prior week is still open with overlapping findings, the new PR opens regardless — the user closes superseded PRs manually. First-iteration acceptable; if it becomes noisy, add "skip if open PR already covers" check in the routine prompt.
- PR body MUST render each finding as a 4-block stanza per `platform/lint-remediation-message-standard.md`.

## Memory file (`audit-memory`)

- Memory file path: `memory/audit_findings_<YYYY-MM-DD>.md` under the user's auto-memory directory (`C:\Users\<u>\.claude\projects\<…>\memory\`).
- Type field: `project` (advisory state, expected to be cleared after manual triage).
- Same-day re-run overwrites — acceptable since the latter run's findings supersede.
- Manual triage workflow: read the file, action each finding, delete the file when all actioned. The next weekly run reports fresh state.

---

# Registration

Routine registration happens via the harness `/schedule` skill — not via repo files. To register or re-register:

1. From any Claude Code session, invoke `/schedule create` (or the harness UI equivalent).
2. Supply the routine name + cron expression + prompt body verbatim from the sections above.
3. Verify registration via `/schedule list` — both routines should appear.
4. (Optional) Trigger an immediate test run via `/schedule run <name>` to verify the routine fires end-to-end before waiting for the scheduled time.

Re-registration after deletion uses the same procedure. The prompt bodies are authoritative — copy-paste exactly to avoid drift.

---

# Failure modes

| Failure | Detection | Recovery |
|---|---|---|
| Routine timeout (skill body slow) | Harness run-history UI shows timeout | Manual re-run via `/schedule run <name>`; tune prompt for narrower scope if recurring |
| Skill crash (`validate-rules` or `audit-memory` body bug) | Harness run-history UI shows error | File a fix task in the plugin-repo (skills are user-level / plugin-supplied per ADR-MONO-006 § 2.3); no monorepo-lab change needed |
| Routine deleted via UI by accident | `/schedule list` no longer shows the routine | Re-register per the procedure above using this document's prompt bodies |
| Schedule timezone drift | Routine fires at unexpected time | Confirm the harness account's default timezone matches `Asia/Seoul`; re-create with explicit timezone if necessary |
| `validate-rules` reports findings that the hook already handles | Routine PR carries findings duplicated by `.claude/hooks/hardstop-detect.ps1` synchronous output | Acceptable in v1 — hook catches per-edit, routine catches structural multi-file drift; minor overlap |

---

# Provenance

- TASK-MONO-062 (filed in PR #392, merged 2026-05-12)
- ADR-MONO-006 (Lint Remediation Message as Agent Context) — `RULE-CONSISTENCY-NN` / `MEMORY-AUDIT-NN` namespace reservation
- Memory `reference_openai_harness_engineering.md` § "강제 메커니즘 핵심 3가지" #3 (mechanism source) + § "우선순위 액션 후보" #2 (priority ranking)

분석=Opus 4.7 / 구현 권장=Sonnet 4.6 (config doc; routine prompt authoring is the main judgment surface).
