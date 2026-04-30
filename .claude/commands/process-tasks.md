---
name: process-tasks
description: Implement all ready tasks and then review all completed tasks in a single pipeline
---

# process-tasks

Run the full task lifecycle: implement all tasks in `tasks/ready/`, then review all tasks in `tasks/review/`.

## Usage

```
/process-tasks                                            # process all tasks across all services
/process-tasks <service>                                  # filter tasks for a specific service
/process-tasks --dry-run                                  # show execution plan only, do not execute
```

Examples:

```
/process-tasks
/process-tasks order-service
/process-tasks --dry-run
```

## Architecture

```
Main context (coordinate only)
  ├─ Phase 1: Implement  (/implement-task)
  │    ├─ Discovery → Analysis → Dependencies → Plan
  │    ├─ Execute via worktree-isolated subagents (parallel/sequential rounds)
  │    └─ Merge completed branches → tasks move to review/
  ├─ Phase 2: Review  (/review-task)
  │    ├─ Discovery (tasks/review/)
  │    ├─ Execute via worktree-isolated subagents (all parallel)
  │    └─ Merge → approved tasks move to done/, fix tasks created in ready/
  └─ Phase 3: Summary
```

Main context never reads specs, skills, or source code directly — subagents do all heavy lifting.

## Procedure

### Phase 1: Implement

Follow the full `/implement-task` batch mode procedure:

1. Read `CLAUDE.md`
2. List all task files in `tasks/ready/` (exclude `.gitkeep`)
3. If argument is a service name, filter to tasks matching that Target Service
4. If no tasks found, skip to Phase 2 (there may be existing tasks in `tasks/review/`)
5. Read every task file fully
6. Analyze: extract metadata, classify category (simple-refactor, simple-code, code-with-event, contract-change, cross-service)
7. Resolve dependencies: build dependency graph, output topological ordering in execution rounds
8. Present execution plan
9. If `--dry-run`, show plan and continue to Phase 2 dry-run (do not execute)
10. Execute via worktree-isolated subagents following `/implement-task` batch mode Phase 5 rules:
    - Parallel rounds for independent tasks, `subagent_type` per task type (`"backend-engineer"` / `"frontend-engineer"`)
    - Sequential rounds for dependent tasks
    - Merge worktree branches between rounds
    - Mark dependents of failed tasks as blocked
11. Collect implementation results

### Phase 2: Review

After Phase 1 completes, follow the full `/review-task` batch mode procedure:

1. List all task files in `tasks/review/` (includes tasks from Phase 1 + any pre-existing review tasks)
2. If argument is a service name, filter to tasks matching that Target Service
3. If no tasks found, skip to Phase 3
4. If `--dry-run`, show review targets and skip to Phase 3
5. Launch all review agents in parallel with `isolation: "worktree"` and `subagent_type: "code-reviewer"` following `/review-task` batch mode rules:
    - Each agent reviews one task against specs, architecture, code quality, and testing checklists
    - No issues → move to `tasks/done/`
    - Issues found → create fix task in `tasks/ready/`, move original to `tasks/done/`
6. Merge all worktree branches
7. Collect review results

### Phase 3: Summary

Output a combined summary:

```
## Pipeline Summary

### Implementation Phase
| Task ID | Title | Service | Category | Result | Tests |
|---|---|---|---|---|---|

Implemented: N / Total: M
Failed: [list with reasons]
Blocked: [list with dependency reason]

### Review Phase
| Task ID | Title | Service | Result | Issues | Fix Task |
|---|---|---|---|---|---|

Approved: N / Total: M
Fix needed: [list with fix task IDs]

### Overall
- Tasks completed (done): [count]
- Fix tasks created (ready): [count]
- Failed/Blocked: [count]
```

## Rules

- Follow CLAUDE.md Hard Stop Rules at every step
- Main context does NOT read specs, skills, or source code — subagents do
- Phase 2 must wait for Phase 1 to fully complete (all merges done) before starting
- If Phase 1 has zero tasks, still run Phase 2 for any pre-existing review tasks
- If both phases have zero tasks, report "No tasks to process" and stop
- Proceed without asking confirmation questions (unless `--dry-run`)
- Always use `isolation: "worktree"` for all subagents
- Failed implementation tasks are not reviewed — they stay in their current state
- Blocked tasks are not reviewed — they remain in `tasks/ready/`
