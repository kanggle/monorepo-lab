---
name: start-task
description: Start a task in its own isolated git worktree + branch (keeps the main checkout parked on main)
---

# start-task

Create a dedicated git worktree + feature branch for a task, so concurrent sessions never share the main checkout (CLAUDE.md § Cross-Project Changes "Concurrent-session isolation"). The positive complement to `protect-main-branch.ps1` (blocks main push) and `warn-shared-checkout-switch.ps1` (warns on dirty shared-checkout switch).

## Usage

​```
/start-task <task-id> [<short-slug>]
​```

- `<task-id>`: the task identifier, e.g. `BE-365`, `MONO-240`, `FIN-BE-018`.
- `<short-slug>` (optional): a few kebab-case words describing the work. If omitted, derive a short slug from the task's Title.

Examples:
​```
/start-task BE-365 seller-settlement
/start-task MONO-240
​```

## Procedure

1. **Normalize names.**
   - `id_lower` = the task id lowercased (`BE-365` → `be-365`).
   - `branch` = `task/<id_lower>-<short-slug>`. If no slug given, locate the task file in a `tasks/ready/` (project or root) and derive a 2–4 word kebab slug from its Title.
   - `wt_dir` = a **sibling directory of the repo root** named `mlab-<id_lower-without-domain-prefix>` (e.g. `mlab-be365`). Base it on the repo root's parent — do not hardcode an absolute path.
   - **Guard (CLAUDE.md Branch name constraint):** the branch name MUST NOT contain the substring `master`. If the slug would introduce it, rename around the noun or abbreviate (`ms-`/`mst-`).

2. **Sync the base.** `git fetch origin main`.

3. **Create the worktree on a new branch off `origin/main`** (explicit `-b`, never DWIM — memory `env_git_worktree_verify_windows`):
   ​```
   git worktree add -b <branch> <wt_dir> origin/main
   ​```
   Creates branch + directory in one step off latest `origin/main`. Does NOT touch the main checkout.

4. **Verify:**
   ​```
   git -C <wt_dir> rev-parse --abbrev-ref HEAD   # == <branch>
   git -C <wt_dir> log -1 --oneline              # == origin/main tip
   ​```

5. **Report** the worktree path + branch; instruct: do all this task's work inside `<wt_dir>`, commit + push from there, open the PR, and after merge run `git worktree remove <wt_dir>` + delete the local branch.

## Rules

- One worktree + one branch per task; the main `monorepo-lab` checkout stays parked on `main` and is not used for task work.
- Always branch off `origin/main` (after fetch), not local `main` (may be behind).
- Branch names never contain `master`.
- Worktree creation is non-destructive — never moves the main checkout HEAD, safe while other sessions are live.
- Proceed without asking confirmation questions.
