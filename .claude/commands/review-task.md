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
8. Run tests: `./gradlew :apps:{service}:test` (if applicable)
9. If no issues: move task from `tasks/review/` → `tasks/done/`
10. If issues found: create a fix task in `tasks/ready/` (referencing the original task ID), then move the original task to `tasks/done/`

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
10. Run tests: ./gradlew :apps:{service}:test (if applicable)
11. Based on review results:
    - If **no issues**: move task from `tasks/review/` to `tasks/done/`
    - If **issues found**: create a fix task in `tasks/ready/` referencing the original task ID, then move the original task to `tasks/done/`

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
Approved (moved to done): [list]
Fix needed (fix tasks created): [list with fix task IDs]
Errors: [list with reasons]
```

---

## Rules

- Follow CLAUDE.md Hard Stop Rules at every step
- Do not modify implementation code during review — create fix tasks
- After review, original task always moves to `tasks/done/` regardless of result
- Fix tasks go to `tasks/ready/` for future implementation
- Proceed without asking confirmation questions (unless `--dry-run`)
- In batch mode, always use `isolation: "worktree"` when launching review agents
- In batch mode, always merge worktree branches after all agents complete
