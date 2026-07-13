---
name: review-task
description: Review the implementation of a task in tasks/review/
---

# review-task

Review the implementation of tasks in `tasks/review/`.

## Usage

```
/review-task TASK-BE-024        # review a single task
/review-task                    # review all tasks in tasks/review/
/review-task <service-name>     # review tasks for a specific service only
/review-task --dry-run          # list tasks to review only, do not execute
```

## Argument Parsing

1. If argument matches `TASK-*` pattern → **single task mode**
2. If argument is a service name (as declared in `PROJECT.md`) → **batch mode filtered by service**
3. If argument is `--dry-run` → **batch mode, list only**
4. If no argument → **batch mode for all tasks**

---

## Single Task Mode

When a specific task ID is given:

1. Find and read the task file matching the given ID in `tasks/review/`
2. If the task is not in `tasks/review/`, **stop immediately**
3. Read `platform/entrypoint.md` and follow the spec reading order
4. Read all Related Specs and Related Contracts listed in the task
5. Read `.claude/skills/INDEX.md` and matched skill files
6. Read the implementation code in the target service
7. Run the review checklist defined in `.claude/skills/review-checklist/SKILL.md`
8. Run tests: backend `./gradlew :apps:{service}:test`, or frontend `pnpm --filter {service} test` — whichever applies to the target service
9. Report the verdict (`approved` / `fix_needed`). **Do not move the task file** — review never performs a lifecycle transition.
10. If issues found: create a fix task in `tasks/ready/` referencing the original task ID. A fix task file is a **task spec** → it lands in a **spec PR**, never bundled with implementation or close-chore commits (`tasks/INDEX.md` § PR Separation Rule).
11. The `review/ → done/` transition happens **only in a separate close-chore PR**, gated on objective merge verification of the task's impl PR — see § Close Chore below.

---

## Close Chore (separate PR — NOT part of review)

`review/ → done/` is a **lifecycle transition, not a review outcome.** A passing review does not
close a task; a **merged, GREEN impl PR** does.

It may run only after the task's impl PR is **objectively merge-verified** — all three dimensions
(CLAUDE.md § Task Rules). A "merged it" statement is not proof:

1. `gh pr view <n> --json state,mergedAt,mergeCommit,statusCheckRollup` → `state == MERGED`
2. `git log origin/main` tip matches that squash commit
3. the impl PR's **pre-merge** `gh pr checks <n>` snapshot had **0 failing required checks**

If any dimension fails: **STOP.** CI-RED-at-merge creates a `main` regression and requires a separate
fix task that restores `main` GREEN *before* the close chore.

> **Never gate on the exit code of `gh pr checks <n>`.** A still-pending check makes it exit non-zero,
> so an exit-code gate misreads "pending" as "failed". Parse its text / `--json` output and check each
> check's state explicitly. There is no external `jq` in this environment — use `gh --jq`.

The hook does **not** protect you here: `.claude/hooks/hardstop-detect.ps1` explicitly permits lifecycle
Status-field moves, so an unearned close passes straight through. The gate above is the only gate.

**Procedure** — run it in a worktree branched off `origin/main`, **never in the shared main checkout**
(a concurrent session's `git commit` there will sweep up your unstaged changes). All four steps land in
**one commit**:

1. `git mv tasks/review/TASK-XXX.md tasks/done/TASK-XXX.md`
2. Edit the moved file's `Status`: `review` → `done`. Change **nothing else** — `review/` and `done/`
   files are frozen except for this one field.
3. `git add tasks/done/TASK-XXX.md` **again** — `git mv` staged the *review*-state blob. Verify with
   `git show :tasks/done/TASK-XXX.md` that the staged blob actually reads `done`. Skipping this lands a
   `Status: review` file under `done/`.
4. Update `tasks/INDEX.md`: remove the entry from `## review`, append a one-line outcome under `## done`
   prefixed `(impl PR #N 머지, <squash commit>)`.

Skipping step 2, 3, or 4 produces silent drift.

---

## Batch Mode

When no task ID is given (or filtered by service):

### Architecture

```
Main context (lightweight — coordinate only)
  ├─ Phase 1: Discovery
  ├─ Phase 2: Delegate to subagents (parallel, worktree-isolated)
  │    ├─ Agent[BE-A](worktree-1) reviews TASK-A
  │    ├─ Agent[BE-B](worktree-2) reviews TASK-B
  │    └─ Agent[BE-C](worktree-3) reviews TASK-C
  └─ Phase 3: Collect results + Summary
```

Main context never reads specs, skills, or source code directly — subagents do all heavy lifting.

### Phase 1: Discovery (main context)

1. Read `CLAUDE.md`
2. List all task files in `tasks/review/` (exclude `.gitkeep`)
3. Read every task file fully
4. If argument is a service name, filter to tasks matching that Target Service
5. If `--dry-run`, list tasks and stop
6. If no tasks found, report and stop

### Phase 2: Execute via Subagents (worktree-isolated)

All review tasks are independent — launch all agents in parallel.

Launch one subagent per task, all in a single message, each with `isolation: "worktree"` and `subagent_type: "code-reviewer"`.

Each subagent receives the Agent Prompt Template below.

#### Agent Prompt Template

```
You are reviewing a completed task in this project. Follow these steps exactly:

## Task
- Task ID: {taskId}
- Task file: tasks/review/{taskFileName}

## Steps
1. Read `CLAUDE.md`
2. Read the task file at `tasks/review/{taskFileName}`
3. If the task is not in `tasks/review/`, **stop immediately** and return result: not_found
4. Read `platform/entrypoint.md` and follow the spec reading order (Core specs)
5. Read all Related Specs listed in the task
6. Read all Related Contracts listed in the task
7. Read `.claude/skills/INDEX.md` and matched skill files
8. Read the implementation code in the target service
9. Run the Review Checklist defined in `.claude/skills/review-checklist/SKILL.md`
10. Run tests: backend ./gradlew :apps:{service}:test, or frontend pnpm --filter {service} test — whichever applies to the target service
11. Report the verdict. **Do not move the task file** — it stays in `tasks/review/` whatever the outcome.
    Closing a task is a lifecycle transition gated on a merged, GREEN impl PR, not on your review passing.
    If **issues found**: create a fix task in `tasks/ready/` referencing the original task ID.

## Fix Task Rules
- Fix task file name: `TASK-{type}-{nextId}-fix-{originalTaskId}.md`
- Fix task Goal must reference the original task ID (e.g., "Fix issue found in {taskId}")
- Fix task must include all required sections per CLAUDE.md
- Fix task goes in `tasks/ready/`

## Rules
- Do not modify implementation code directly — create fix tasks instead
- If specs are missing or conflicting, note it in the review but do not block the review
- Proceed without asking confirmation questions
- If a Hard Stop condition from CLAUDE.md is triggered, stop and return the reason

## Return
When done, return a summary:
- Task ID
- Result: approved / fix_needed / not_found / error
- Review Checklist: pass/fail per section
- Issues: list of issues found (if any)
- Fix Task: created fix task ID and file name (if any)
- Notes: any additional observations
```

### Phase 3: Summary (main context)

Collect all subagent results and output:

```
## Review Summary

| Task ID | Title | Service | Result | Issues | Fix Task |
|---|---|---|---|---|---|

Reviewed: N / Total: M
Approved (still in review/ — close only after the impl PR is merge-verified): [list]
Fix needed (fix tasks created — land them in one spec PR): [list with fix task IDs]
Errors: [list with reasons]
```

Approved tasks are **not** closed here. List which of them are ready for a close chore (impl PR merged +
3-dim verified) and which are still waiting on their PR.

---

## Rules

- Follow CLAUDE.md Hard Stop Rules at every step
- Do not modify implementation code during review — create fix tasks
- **Review never moves the task file.** The task stays in `tasks/review/` whatever the verdict.
  `review/ → done/` is a separate close-chore PR, gated on 3-dimension merge verification (§ Close Chore).
  A passing review does not close a task; a merged, GREEN impl PR does.
- Reviewed tasks therefore accumulate in `review/`. That is intended, not a leak — CLAUDE.md
  § Recommending Tasks already requires scanning the `review/` queue and clearing it before picking new work.
- Files under `review/` and `done/` are frozen except for the single `Status` field during a lifecycle move.
  Move first (`git mv`), then edit only that field.
- Fix tasks go to `tasks/ready/` for future implementation, and land in a **spec PR** — never bundled with
  implementation or close-chore commits (`tasks/INDEX.md` § PR Separation Rule)
- Proceed without asking confirmation questions (unless `--dry-run`)
- In batch mode, always use `isolation: "worktree"` when launching review agents
- **Never `git merge` into `main` and never push `main`.** Direct main pushes are hook-blocked
  (`.claude/hooks/protect-main-branch.ps1`); a local merge into `main` is *not* blocked, so it succeeds
  quietly and then can never be pushed — the work is stranded, and the merge moved HEAD in a checkout other
  sessions may be sharing. Every landing goes through a PR.
- In batch mode, review agents produce **verdicts** plus (optionally) fix-task files. Collect the fix-task
  files from the agents' worktrees and land them together in **one spec PR**.