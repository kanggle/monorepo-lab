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
/implement-task <service-name>    # implement tasks for a specific service only
/implement-task --dry-run         # show execution plan only, do not implement
```

## Argument Parsing

1. If argument matches `TASK-*` pattern → **single task mode**
2. If argument is a service name (as declared in `PROJECT.md`) → **batch mode filtered by service**
3. If argument is `--dry-run` → **batch mode, plan only**
4. If no argument → **batch mode for all tasks**

---

## Single Task Mode

When a specific task ID is given:

1. Read `CLAUDE.md`
2. Find and read the task file matching the given ID in `tasks/ready/`
3. If the task is not in `tasks/ready/`, **stop immediately** — do not implement tasks from other directories
4. Verify all required sections exist (Goal, Scope, Acceptance Criteria, Related Specs, Related Contracts, Edge Cases, Failure Scenarios) — stop if any is missing
5. Read Related Specs in the order defined by `platform/entrypoint.md`
6. Read Related Contracts
7. Read `.claude/skills/INDEX.md` → read skill files matching Related Skills
8. Read existing code in the target service to understand current patterns and structure
9. Set the task's `Status` to `in-progress` **while the file is still in `tasks/ready/`**, then move it to `tasks/in-progress/`. Files under `in-progress/` and `review/` are frozen except for that single field, so the edit must precede the move.
10. Implement
11. Write and run tests as specified in Test Requirements
12. Verify all Acceptance Criteria are met
13. Set `Status` to `review` while the file is still in `tasks/in-progress/`, then move it to `tasks/review/` (same reason as step 9).
14. Open the **impl PR**. Landing happens through a PR — `main` is hook-protected and cannot be pushed directly. **The task is not closed here**: `review/ → done/` is a separate close-chore PR gated on merge verification (see `/review-task` § Close Chore).

---

## Batch Mode

When no task ID is given (or filtered by service):

### Architecture

```
Main context (lightweight — plan + coordinate only)
  ├─ Phase 1~4: Discovery, Analysis, Dependencies, Plan
  ├─ Phase 5: Delegate to subagents (worktree-isolated)
  │    ├─ Round 1: Agent[BE-A](worktree-1) + Agent[BE-B](worktree-2)  (parallel)
  │    │    └─ merge worktree branches → task/batch-<id>   (integration branch, NOT main)
  │    ├─ Round 2: Agent[BE-C](worktree-3)  (branches off task/batch-<id>)
  │    │    └─ merge worktree branch → task/batch-<id>
  │    └─ Round N: ...
  └─ Phase 6: Collect results + Summary → land task/batch-<id> to main via PR
```

Main context never reads specs, skills, or source code directly — subagents do all heavy lifting.

### Isolation Strategy

- **Parallel rounds**: Each agent runs with `isolation: "worktree"` — gets its own copy of the repo on a temporary branch. No file conflicts possible.
- **Sequential rounds**: Also use worktree for safety — failed tasks leave the integration branch untouched.
- **After each round**: Merge completed worktree branches into the coordinator's **integration branch** (`task/batch-<id>`), never into `main`, before starting the next round. This ensures the next round sees all prior changes. `main` is landed once at the end, via PR.

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
   - Launch multiple Agent tool calls in a single message, each with `isolation: "worktree"` and the `subagent_type` matching the task category: `"backend-engineer"` (BE tasks), `"frontend-engineer"` (FE tasks), `"refactoring-engineer"` (`simple-refactor` category). For `contract-change` tasks, dispatch `"api-designer"` (API contracts) or `"event-architect"` (event contracts) for the contract step first, then the implementing engineer — per the Contract-first rule (Phase 3, step 4).
   - **Pass `model=` explicitly on every Agent call — do not rely on session inheritance** (`CLAUDE.md` § Recommending Tasks and Dispatching Agents — the canonical rule and tier table; do not restate it here). Read that table and pick the tier from the task's complexity: complex domain work (state machines, transaction design, event-driven outbox, cross-cutting refactors, contract design) → `model="opus"`; CI / docs / single-line config / lifecycle chore → `model="sonnet"` (or `"haiku"`). The `api-designer` / `event-architect` agents already default to `opus` in their frontmatter (their role is Opus-tier), so a contract dispatch is Opus even if `model=` is omitted — but pass it anyway, so the choice is visible at the dispatch site.
   - Each agent gets its own copy of the repo on a temporary branch
   - Each agent receives a complete, self-contained prompt (see Agent Prompt Template below)
   - Wait for all agents in the round to complete

2. **Sequential round** (dependent tasks):
   - Launch one agent at a time with `isolation: "worktree"` and appropriate `subagent_type`, passing `model=` explicitly (same tier rule as the parallel round above)
   - Wait for completion before launching the next

3. **Between rounds** (integration step):

   **Never merge into `main` and never push `main`.** `protect-main-branch.ps1` blocks pushes to `main`,
   but it does **not** block a local `git merge` into it — so merging here succeeds quietly and the result
   can then never be pushed (local `main` runs ahead of `origin/main` and the work is stranded). It also
   moves HEAD in a checkout that concurrent sessions may be sharing.

   Integrate rounds on a **coordinator-owned integration branch** instead:
   ```
   # once, before Round 1 — off latest origin/main, in its own worktree
   git fetch origin main
   git worktree add -b task/batch-<id> ../wt-batch-<id> origin/main

   # after each round, from the integration worktree
   git -C ../wt-batch-<id> merge <agent-branch> --no-ff -m "Merge {taskId}: {title}"
   ```
   The next round's agent worktrees branch off `task/batch-<id>`, **not** `origin/main`, so they see the
   prior rounds' output.

   - Check each agent's result (success/failure)
   - For successful agents: merge their branch into the integration branch (above)
   - For failed agents: discard the worktree (the integration branch stays clean)
   - If a task failed, mark all tasks that depend on it as `blocked`
   - Do not launch blocked tasks
   - Verify the **integration branch** builds/compiles after each merge before proceeding
   - Landing to `main` happens **once, at the end, via PR** — never by merging locally

#### Agent Prompt Template

Each subagent receives this prompt (filled with task-specific values):

```
You are implementing a task in this project. Follow these steps exactly:

## Task
- Task ID: {taskId}
- Task file: tasks/ready/{taskFileName}

## Steps
1. Read `CLAUDE.md`
2. Read the task file at `tasks/ready/{taskFileName}`
3. Set the task's `Status` to `in-progress` while the file is still in `tasks/ready/`, then move it to `tasks/in-progress/` (files under `in-progress/`/`review/` are frozen except for that field — edit before the move)
4. Read `platform/entrypoint.md` and follow the spec reading order
5. Read all Related Specs listed in the task
6. Read all Related Contracts listed in the task
7. Read `.claude/skills/INDEX.md` and matched skill files
8. Read existing code in the target service
9. Implement the task following specs and architecture rules
10. Write tests as specified in Test Requirements
11. Run tests for the target service's stack — backend (Gradle): `./gradlew :apps:{service}:test`; frontend (Node): `pnpm --filter {service} test` (per the service's build.gradle.kts / package.json).
12. Verify all Acceptance Criteria are met
13. Set `Status` to `review` while the file is still in `tasks/in-progress/`, then move it to `tasks/review/` (edit before the move — same reason as step 3)
14. Commit and push your branch. **Do not merge into `main` and do not push `main`** — the coordinator integrates your branch.

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

**simple-code**:
```
Standard implementation. No special handling needed.
```

**simple-refactor** (mirrors `/refactor-code`'s baseline → refactor → retest order):
```
- Baseline FIRST: run the existing tests and verify they pass BEFORE changing anything. Never refactor without a green baseline (`refactoring-engineer` `## Does NOT`).
- Refactor with NO change to externally observable behavior; do not modify API/event contracts, do not mix in feature work, do not change test assertions.
- Re-run the SAME tests afterward — they must still pass unchanged.
```

**code-with-event**:
```
- Read event contracts before implementation
- Implement event publishing logic
- Test event publishing in integration tests
- Follow platform/event-driven-policy.md (outbox pattern, idempotency)
```

**contract-change** — this category dispatches **two agents in sequence** (contract agent first, then the implementing engineer — Phase 5 step 1). Give each its own instructions; do **not** send one block to both:

_Contract step — `api-designer` / `event-architect`:_
```
- Update ONLY the contract files (specs/contracts/http/ or specs/contracts/events/) to match the task's Related Contracts.
- Do NOT write implementation code — your `## Does NOT` forbids it. The implementing engineer, dispatched next, does that.
- Verify the contract is internally consistent and versioned per platform rules.
```

_Implement step — `backend-engineer` / `frontend-engineer` (dispatched after the contract step):_
```
- The contract files are already updated by the contract agent — implement against them.
- Do NOT re-edit the contracts. If a contract is wrong, stop and report — do not silently diverge.
- Verify the implementation matches the contract.
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
- **Never `git merge` into `main` and never push `main`.** Pushes to `main` are hook-blocked; a local merge
  into `main` is *not* blocked, so it succeeds quietly and strands the work (it can never be pushed) while
  moving HEAD in a checkout other sessions may share. In batch mode, integrate rounds on the coordinator's
  integration branch (`task/batch-<id>`) and land to `main` once, via PR
- Lifecycle moves (`ready/ → in-progress/ → review/`) and implementation commits both live in the **impl PR**,
  as separate commits. Do not bundle task-spec authoring or close chores into it (`tasks/INDEX.md`
  § PR Separation Rule). **Implementation never closes a task** — `review/ → done/` is a separate chore PR
  gated on merge verification
- If a task fails, log the failure and continue with the next independent task
- Do not launch tasks that depend on a failed task — mark them as blocked
- If merge conflict occurs, resolve it before proceeding to the next round
