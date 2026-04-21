---
name: implement-task
description: Implement the given task end-to-end following the standard workflow
---

# implement-task

Implement tasks in `tasks/ready/` end-to-end.

## Usage

```
/implement-task TASK-BE-113       # implement a single task
/implement-task                   # implement all tasks in tasks/ready/
/implement-task order-service     # implement tasks for a specific service only
/implement-task --dry-run         # show execution plan only, do not implement
```

## Argument Parsing

1. If argument matches `TASK-*` pattern → **single task mode**
2. If argument is a service name (e.g., `order-service`) → **batch mode filtered by service**
3. If argument is `--dry-run` → **batch mode, plan only**
4. If no argument → **batch mode for all tasks**

---

## Single Task Mode

When a specific task ID is given:

1. Read `CLAUDE.md`
2. Find and read the task file matching the given ID in `tasks/ready/`
3. If the task is not in `tasks/ready/`, **stop immediately** — do not implement tasks from other directories
4. Verify all required sections exist (Goal, Scope, Acceptance Criteria, Related Specs, Related Contracts, Edge Cases, Failure Scenarios) — stop if any is missing
5. Read Related Specs in the order defined by `specs/platform/entrypoint.md`
6. Read Related Contracts
7. Read `.claude/skills/INDEX.md` → read skill files matching Related Skills
8. Read existing code in the target service to understand current patterns and structure
9. Move task from `tasks/ready/` → `tasks/in-progress/`
10. Implement
11. Write and run tests as specified in Test Requirements
12. Verify all Acceptance Criteria are met
13. Move task from `tasks/in-progress/` → `tasks/review/`

---

## Batch Mode

When no task ID is given (or filtered by service):

### Architecture

```
Main context (lightweight — plan + coordinate only)
  ├─ Phase 1~4: Discovery, Analysis, Dependencies, Plan
  ├─ Phase 5: Delegate to subagents (worktree-isolated)
  │    ├─ Round 1: Agent[BE-A](worktree-1) + Agent[BE-B](worktree-2)  (parallel)
  │    │    └─ merge worktree branches → main
  │    ├─ Round 2: Agent[BE-C](worktree-3)  (depends on Round 1)
  │    │    └─ merge worktree branch → main
  │    └─ Round N: ...
  └─ Phase 6: Collect results + Summary
```

Main context never reads specs, skills, or source code directly — subagents do all heavy lifting.

### Isolation Strategy

- **Parallel rounds**: Each agent runs with `isolation: "worktree"` — gets its own copy of the repo on a temporary branch. No file conflicts possible.
- **Sequential rounds**: Also use worktree for safety — failed tasks leave main branch untouched.
- **After each round**: Merge completed worktree branches into main before starting the next round. This ensures the next round sees all prior changes.

### Phase 1: Discovery (main context)

1. Read `CLAUDE.md`
2. List all task files in `tasks/ready/` (exclude `.gitkeep`)
3. Read every task file fully
4. If argument is a service name, filter to tasks matching that Target Service

### Phase 2: Analysis (main context)

For each task, extract and record:
- Task ID, Title, Target Service
- Task Tags (code, event, refactor, etc.)
- Scope (In/Out)
- Related Specs and Related Contracts
- Edge Cases and Failure Scenarios complexity
- Test Requirements

Classify each task into one of:

| Category | Criteria |
|---|---|
| **simple-refactor** | Tag contains `refactor`, no contract changes, no event changes |
| **simple-code** | Single-layer change, no event, no contract change |
| **code-with-event** | Tag contains `event`, or Related Contracts includes event contracts |
| **contract-change** | Requires API or event contract updates before implementation |
| **cross-service** | Scope touches multiple services |

### Phase 3: Dependency Resolution (main context)

Build a dependency graph:

1. **Explicit dependencies**: Check if any task's Implementation Notes or Scope references code that another task modifies
2. **Package/file overlap**: Tasks modifying the same package or class must run sequentially
3. **Refactor-first rule**: Refactoring tasks (package moves, renames) must run before tasks that add code to the same area
4. **Contract-first rule**: Contract changes must complete before implementation tasks that depend on them
5. **Independent tasks**: Tasks with no shared files or dependencies can run in parallel

Output a topological ordering grouped into execution rounds:
```
Round 1 (parallel): [TASK-A, TASK-B]  — no shared files
Round 2 (sequential): [TASK-C]        — depends on A's output
Round 3 (parallel): [TASK-D, TASK-E]  — depend on C but not each other
```

### Phase 4: Execution Plan (main context)

Present the plan before executing:

```
## Execution Plan

| Order | Task ID | Title | Category | Parallel | Depends On |
|---|---|---|---|---|---|

Total: N tasks, M rounds
```

If `--dry-run` is specified, **stop here** and do not proceed to Phase 5.

### Phase 5: Execute via Subagents (worktree-isolated)

**Key principle**: Each task runs in its own subagent with `isolation: "worktree"`. Main context only coordinates.

#### Per-round execution:

1. **Parallel round** (independent tasks):
   - Launch multiple Agent tool calls in a single message, each with `isolation: "worktree"` and appropriate `subagent_type` (`"backend-engineer"` for BE tasks, `"frontend-engineer"` for FE tasks)
   - Each agent gets its own copy of the repo on a temporary branch
   - Each agent receives a complete, self-contained prompt (see Agent Prompt Template below)
   - Wait for all agents in the round to complete

2. **Sequential round** (dependent tasks):
   - Launch one agent at a time with `isolation: "worktree"` and appropriate `subagent_type`
   - Wait for completion before launching the next

3. **Between rounds** (merge step):
   - Check each agent's result (success/failure)
   - For successful agents: merge their worktree branch into main
     ```
     git merge <worktree-branch> --no-ff -m "Merge {taskId}: {title}"
     ```
   - For failed agents: discard the worktree (main stays clean)
   - If a task failed, mark all tasks that depend on it as `blocked`
   - Do not launch blocked tasks
   - Verify main branch builds/compiles after merge before proceeding

#### Agent Prompt Template

Each subagent receives this prompt (filled with task-specific values):

```
You are implementing a task in this project. Follow these steps exactly:

## Task
- Task ID: {taskId}
- Task file: tasks/in-progress/{taskFileName}

## Steps
1. Read `CLAUDE.md`
2. Read the task file at `tasks/ready/{taskFileName}`
3. Move task from `tasks/ready/` to `tasks/in-progress/`
4. Read `specs/platform/entrypoint.md` and follow the spec reading order
5. Read all Related Specs listed in the task
6. Read all Related Contracts listed in the task
7. Read `.claude/skills/INDEX.md` and matched skill files
8. Read existing code in the target service
9. Implement the task following specs and architecture rules
10. Write tests as specified in Test Requirements
11. Run tests: ./gradlew :apps:{service}:test
12. Verify all Acceptance Criteria are met
13. Move task from `tasks/in-progress/` to `tasks/review/`

## Category-specific instructions
{categoryInstructions}

## Rules
- Specs win over implementation when they conflict
- Update contract files first if API or event shape changes
- Follow the architecture in `specs/services/{service}/architecture.md`
- Do not ask confirmation questions — proceed autonomously
- If a Hard Stop condition from CLAUDE.md is triggered, stop and return the reason

## Return
When done, return a summary:
- Task ID
- Result: success / failed / blocked
- Files created or modified (list)
- Tests: passed / failed (count)
- Notes: any issues encountered
```

Category-specific instructions per type:

**simple-refactor / simple-code**:
```
Standard implementation. No special handling needed.
```

**code-with-event**:
```
- Read event contracts before implementation
- Implement event publishing logic
- Test event publishing in integration tests
- Follow specs/platform/event-driven-policy.md (outbox pattern, idempotency)
```

**contract-change**:
```
- Update contract files FIRST (specs/contracts/http/ or specs/contracts/events/)
- Then implement against the updated contracts
- Verify contract and implementation are consistent
```

**cross-service**:
```
- Process changes for {primaryService} only
- Verify cross-service contracts are consistent
- Do not modify other services
```

### Phase 6: Summary (main context)

Collect all subagent results and output:

```
## Processing Summary

| Task ID | Title | Category | Result | Files Changed | Tests |
|---|---|---|---|---|---|

Completed: N / Total: M
Failed: [list with reasons]
Blocked: [list with dependency reason]
Moved to review: [list]
```

---

## Rules

- Specs win over implementation when they conflict
- Update contract files first if API or event shape changes
- Follow the architecture style declared in `specs/services/<service>/architecture.md`
- Follow CLAUDE.md Hard Stop Rules at every step
- Proceed without asking confirmation questions (unless `--dry-run`)
- In batch mode, main context does NOT read specs, skills, or source code — subagents do
- In batch mode, always use `isolation: "worktree"` when launching task agents
- In batch mode, always merge worktree branches between rounds
- If a task fails, log the failure and continue with the next independent task
- Do not launch tasks that depend on a failed task — mark them as blocked
- If merge conflict occurs, resolve it before proceeding to the next round
