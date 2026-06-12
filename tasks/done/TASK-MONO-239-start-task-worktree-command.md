# Task ID

TASK-MONO-239

# Title

Add a `/start-task` slash command (`.claude/commands/start-task.md`) that creates a dedicated git worktree + feature branch off `origin/main` for a task — the positive (one-command) complement to the negative guards (`protect-main-branch.ps1` block + `warn-shared-checkout-switch.ps1` ask) enforcing the TASK-MONO-235 "Concurrent-session worktree isolation" rule.

# Status

done

# Owner

backend

# Task Tags

- docs
- monorepo
- governance

---

# Dependency Markers

- **선행**: `TASK-MONO-235` (CLAUDE.md "Concurrent-session worktree isolation" rule, done) + `TASK-MONO-236` (`warn-shared-checkout-switch.ps1` advisory hook, done). Those are the **rule** and the **negative guard** (warn when you don't isolate). This task adds the **positive automation** — a single command that does the right thing (worktree + branch off origin/main), so isolating a task is the path of least resistance instead of a deliberate manual ritual.
- **motivation**: every prior guard is negative (block main push / warn on dirty shared-checkout switch). None *creates* the worktree — sessions still default to working in the main checkout because it is the always-open landing directory. A `/start-task` command removes that friction.
- **⚠️ classifier constraint**: `.claude/commands/` edit+commit is **hard-blocked by the auto-mode classifier even with explicit approval** (memory `env_classifier_claude_self_mod_block`; re-confirmed 2026-06-13). The command file in § Operator Patch is **applied and committed by the human operator**; this task file (outside `.claude/`) is the committable record with the command embedded verbatim.
- **model**: 분석=Opus 4.8 / **구현 권장=Sonnet** (single command markdown; mechanical).

---

# Goal

Make "every task starts in its own worktree" a one-command operation (`/start-task <id> [<slug>]`), so the ideal isolation workflow is followed by default rather than by manual discipline. Non-destructive: the command never moves the main checkout's HEAD, so it is safe while other sessions are live.

# Scope

## In scope (`.claude/` — human-applied per classifier constraint)

New command `.claude/commands/start-task.md`:

```markdown
---
name: start-task
description: Start a task in its own isolated git worktree + branch (keeps the main checkout parked on main)
---

# start-task

Create a dedicated git worktree + feature branch for a task, so concurrent sessions never share the main checkout (CLAUDE.md § Cross-Project Changes "Concurrent-session worktree isolation"). The positive complement to `protect-main-branch.ps1` (blocks main push) and `warn-shared-checkout-switch.ps1` (warns on dirty shared-checkout switch).

## Usage

\`\`\`
/start-task <task-id> [<short-slug>]
\`\`\`

- `<task-id>`: the task identifier, e.g. `BE-365`, `MONO-239`, `FIN-BE-017`.
- `<short-slug>` (optional): a few kebab-case words describing the work. If omitted, derive a short slug from the task's Title.

Examples:
\`\`\`
/start-task BE-365 seller-settlement
/start-task MONO-239
\`\`\`

## Procedure

1. **Normalize names.**
   - `id_lower` = the task id lowercased (e.g. `BE-365` → `be-365`).
   - `branch` = `task/<id_lower>-<short-slug>`. If no slug was given, locate the task file in a `tasks/ready/` (project or root) and derive a 2–4 word kebab slug from its Title.
   - `wt_dir` = a **sibling directory of the repo root** named `mlab-<id_lower-without-domain-prefix>` (e.g. `mlab-be365`, `mlab-mono239`). Base it on the repo root's parent directory — do not hardcode an absolute path.
   - **Guard (CLAUDE.md Branch name constraint):** the branch name MUST NOT contain the substring `master`. If the slug would introduce it, rename around the noun or abbreviate (`ms-`/`mst-`).

2. **Sync the base.** `git fetch origin main`.

3. **Create the worktree on a new branch off `origin/main`** (explicit `-b`, never rely on DWIM remote resolution — see memory `env_git_worktree_verify_windows`):
   \`\`\`
   git worktree add -b <branch> <wt_dir> origin/main
   \`\`\`
   - Creates the branch AND the directory in one step, based on the latest `origin/main`.
   - Does NOT touch the main checkout — it stays wherever it is (ideally parked on `main`).

4. **Verify:**
   \`\`\`
   git -C <wt_dir> rev-parse --abbrev-ref HEAD   # == <branch>
   git -C <wt_dir> log -1 --oneline              # == origin/main tip
   \`\`\`

5. **Report** the worktree path + branch, and instruct: do all of this task's work inside `<wt_dir>`, commit + push from there, open the PR, and after merge run `git worktree remove <wt_dir>` + delete the local branch.

## Rules

- One worktree + one branch per task; the main `monorepo-lab` checkout stays parked on `main` and is not used for task work (CLAUDE.md § Concurrent-session worktree isolation).
- Always branch off `origin/main` (after fetch), not local `main` (which may be behind).
- Branch names never contain `master` (sandbox force-push regex; CLAUDE.md Branch name constraint).
- Worktree creation is non-destructive — it never moves the main checkout's HEAD, so it is safe even while other sessions are live.
- Proceed without asking confirmation questions.
```

## Out of scope
- Editing `warn-shared-checkout-switch.ps1` to cross-reference `/start-task` in its message (nice-to-have follow-up; avoids more `.claude/` churn now).
- Any *forced* automation — git has no "worktree per task" enforcement primitive; this command makes the right path easy, but adoption remains behavioral (the warn hook is the backstop).

# Acceptance Criteria

- **AC-1**: `.claude/commands/start-task.md` exists with the § Scope content and is invocable as `/start-task <id> [<slug>]`.
- **AC-2**: Running it creates `mlab-<id>` worktree on branch `task/<id>-<slug>` off `origin/main`, without moving the main checkout's HEAD.
- **AC-3**: The command enforces the `master`-substring branch-name guard and branches off `origin/main` after fetch.
- **AC-4**: Applied + committed by the human operator (classifier blocks AI `.claude/commands/` edit+commit). Suggested commit: `feat(commands): TASK-MONO-239 — /start-task worktree-isolation command`.

# Related Specs / Code

- `CLAUDE.md § Cross-Project Changes` → "Concurrent-session worktree isolation" (TASK-MONO-235) + "Branch name constraint".
- `.claude/hooks/protect-main-branch.ps1`, `.claude/hooks/warn-shared-checkout-switch.ps1` (the negative guards this command positively complements).
- `.claude/commands/implement-task.md` (the dispatched-subagent path already auto-creates `isolation:"worktree"`; this command brings the same isolation to interactive sessions).
- Memory: `env_concurrent_git_branch_switch_hazard`, `env_git_worktree_verify_windows`, `env_classifier_claude_self_mod_block`.

# Related Contracts

- None (harness command + governance — no API or event contract touched).

# Edge Cases / Failure Scenarios

- **Classifier hard-block** — AI cannot edit/commit any `.claude/commands/` file; the § Scope command is human-applied. This task file (outside `.claude/`) is the committable record.
- **Non-destructive** — `git worktree add` never moves the main checkout HEAD, so the command is safe to run while be-364 / other sessions are live in the main checkout.
- **Not a hard enforcement** — git lacks a "worktree per task" primitive; the command lowers friction, the warn hook backstops the misses. Adoption stays partly behavioral (documented honestly, no silent over-promise).
- **HARDSTOP-03 N/A** — `.claude/commands/` is shared/project-agnostic; generic worktree workflow, no project-specific content.
- **Self-dogfooding** — task authored in a dedicated `mlab-mono239` worktree off `origin/main`.

# Notes

- Completes the worktree-isolation triad: rule (MONO-235) + negative guards (protect-main / MONO-236 warn) + **positive one-command start (this task)**. Docs/harness only; the operator applies the `.claude/commands/` file.
