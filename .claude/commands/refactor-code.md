---
name: refactor-code
description: Refactor code — specific target or entire service scan
---

# refactor-code

Refactor code safely without changing behavior. Scope depends on arguments.

## Usage

```
/refactor-code <service>                                        # scan entire service + refactor
/refactor-code <service> <target> <refactoring-type>            # refactor specific target
/refactor-code <service> --dry-run                              # analyze only (show plan)
/refactor-code <service> --focus=<category>                     # filter by category
```

Examples:

```
/refactor-code <service-name>
/refactor-code <service-name> <AggregateClass> method extraction
/refactor-code <service-name> presentation layer cleanup
/refactor-code <service-name> --dry-run
/refactor-code <service-name> --focus=duplication
```

## Mode Selection

| Arguments | Mode | Description |
|---|---|---|
| `<service>` only | **Service scan** | Scan entire service code, auto-discover refactoring opportunities, and execute |
| `<service> <target> <type>` | **Target** | Execute specified refactoring on the specified target directly |

---

## Refactoring Categories

| Category | Description |
|---|---|
| **duplication** / `reduce duplication` | Duplicated logic across classes or methods |
| **long-method** / `method extraction` | Methods exceeding ~30 lines or doing multiple things |
| **layer-violation** / `layer cleanup` | Wrong dependency direction or logic in wrong layer |
| **naming** / `rename` | Names not matching `platform/naming-conventions.md` |
| **dead-code** / `dead code removal` | Unused classes, methods, or imports |
| **complexity** / `simplify conditional` | Deeply nested conditionals, god classes, excessive parameters |
| **pattern-mismatch** / `pattern replacement` | Code not following patterns declared in service architecture |
| `class extraction` | Move a responsibility into its own class |
| `inline` | Remove unnecessary indirection |
| `restructure package` | Reorganize package structure |

---

## Mode: Target

Direct refactoring of a specific target. No analysis phase.

### Procedure

1. Read `CLAUDE.md` — including its "Project Classification (Read First)" section, which requires loading `PROJECT.md` and the applicable `rules/common.md`, `rules/domains/<domain>.md`, and `rules/traits/<trait>.md` files per `platform/entrypoint.md` Step 0 before touching any code.
2. Read `platform/refactoring-policy.md`
3. Read `specs/services/<service>/architecture.md`
4. Read `.claude/skills/INDEX.md` → read matched architecture skill and `backend/refactoring/SKILL.md`
5. Read the target code and all related code
6. Check existing tests — if no tests exist, write tests before refactoring
7. Run existing tests → verify all pass (baseline)
8. Perform refactoring
9. Re-run existing tests → verify all pass
10. Adjust test code structure if needed (do not change what the tests verify)

---

## Mode: Service Scan

Analyze the entire service, find refactoring opportunities, and execute via subagents.

### Architecture

```
Main context (lightweight — analyze + coordinate only)
  ├─ Phase 1: Discovery & Analysis
  ├─ Phase 2: Categorize & Prioritize
  ├─ Phase 3: Execution Plan
  ├─ Phase 4: Delegate to subagents (worktree-isolated, sequential)
  │    ├─ Agent[R-1](worktree-1) refactors item 1
  │    ├─ Agent[R-2](worktree-2) refactors item 2
  │    └─ ...
  └─ Phase 5: Verify & Summary
```

Main context reads service code for analysis but does NOT perform refactoring directly — subagents do all changes.

### Phase 1: Discovery & Analysis (main context)

1. Read `CLAUDE.md` — including its "Project Classification (Read First)" section, which requires loading `PROJECT.md` and the applicable `rules/common.md`, `rules/domains/<domain>.md`, and `rules/traits/<trait>.md` files per `platform/entrypoint.md` Step 0 before analysis. Active traits can dictate refactoring constraints (e.g., `transactional` may forbid changes to state-transition paths without saga review).
2. Read `platform/refactoring-policy.md`
3. Read `specs/services/<service>/architecture.md` to understand the declared architecture
4. Read `platform/coding-rules.md` (if exists)
5. Read `platform/naming-conventions.md` (if exists)
6. Read `.claude/skills/INDEX.md` → read matched architecture skill and `backend/refactoring/SKILL.md`
7. Scan all source files in the target service (backend: `apps/<service>/src/`, frontend: `apps/<service>/src/`)
8. Determine test command based on service type:
   - Backend (Gradle): `./gradlew :apps:<service>:test`
   - Frontend (Node): `npm --prefix apps/<service> test` or `pnpm --filter <service> test`
   - Use the test command found in the service's `build.gradle.kts` or `package.json`
9. For each file, identify refactoring opportunities by category

### Phase 2: Categorize & Prioritize (main context)

For each refactoring opportunity, record:
- File path and line range
- Category (from table above)
- Description (what to refactor and why)
- Risk level: low (rename, extract) / medium (restructure) / high (cross-class change)
- Dependencies: other refactoring items that must complete first

Prioritize by:
1. **layer-violation** and **pattern-mismatch** first (architecture compliance)
2. **dead-code** second (safe removal, reduces noise)
3. **duplication** and **long-method** third (structural improvement)
4. **naming** and **complexity** last (polish)

Group items that touch the same file or class into a single refactoring unit.

### Phase 3: Execution Plan (main context)

Present the plan:

```
## Refactoring Plan — {service}

| # | Category | Target | Description | Risk | Depends On |
|---|---|---|---|---|---|

Total: N items, M execution units
```

If `--dry-run` is specified, **stop here**.

### Phase 4: Execute via Subagents (worktree-isolated, sequential)

**Key principle**: Refactoring items execute sequentially to avoid merge conflicts. Each runs in its own worktree.

For each refactoring unit:

1. Launch one Agent with `isolation: "worktree"` and `subagent_type: "refactoring-engineer"` using the Agent Prompt Template below
2. Wait for completion
3. If successful: merge worktree branch into main
   ```
   git merge <worktree-branch> --no-ff -m "refactor(<service>): <description>"
   ```
4. If failed: discard worktree, skip dependent items
5. Proceed to next unit

#### Agent Prompt Template

```
You are performing a safe refactoring in this project. Follow these steps exactly:

## Refactoring Target
- Service: {service}
- Category: {category}
- Target files: {fileList}
- Description: {description}

## Steps
1. Read `CLAUDE.md` — including its "Project Classification (Read First)" section, which requires loading `PROJECT.md` and the applicable `rules/common.md`, `rules/domains/<domain>.md`, and `rules/traits/<trait>.md` files per `platform/entrypoint.md` Step 0 before touching any code.
2. Read `platform/refactoring-policy.md`
3. Read `specs/services/{service}/architecture.md`
4. Read `platform/coding-rules.md` (if exists)
5. Read `platform/naming-conventions.md` (if exists)
6. Read `.claude/skills/INDEX.md` and matched architecture skill and `backend/refactoring/SKILL.md`
7. Read the target files and all related code
8. Run existing tests: {testCommand} — verify all pass (baseline)
9. Perform the refactoring:
   - {description}
   - Do NOT change externally observable behavior
   - Do NOT change API or event contracts
10. Re-run tests: {testCommand} — verify all pass
11. If tests fail, revert changes and return failure

## Rules
- No behavior change — externally observable behavior must remain identical
- Tests must pass both before and after
- Refactor only in the direction consistent with the declared architecture
- One refactoring unit at a time — do not touch unrelated code
- Do not ask confirmation questions — proceed autonomously
- If a Hard Stop condition from CLAUDE.md is triggered, stop and return the reason

## Return
When done, return a summary:
- Refactoring #: {index}
- Category: {category}
- Result: success / failed / skipped
- Files modified (list)
- Tests: passed / failed (count)
- Notes: any issues encountered
```

### Phase 5: Verify & Summary (main context)

1. Run full test suite: `{testCommand}`
2. Collect all subagent results and output:

```
## Refactoring Summary — {service}

| # | Category | Target | Result | Files Changed | Tests |
|---|---|---|---|---|---|

Completed: N / Total: M
Failed: [list with reasons]
Skipped: [list with dependency reason]
```

---

## Rules

- Follow CLAUDE.md Hard Stop Rules at every step
- Follow `platform/refactoring-policy.md` for all constraints
- No behavior change — this is refactoring, not feature work
- Tests must pass before and after every refactoring
- One refactoring at a time — do not mix multiple changes
- Refactor only in the direction consistent with the declared architecture
- Do not modify API or event contracts — use contract-change workflow instead
- In service scan mode: execute sequentially, use `isolation: "worktree"` for safety
- If a refactoring fails tests, discard it entirely — do not partially apply
- Proceed without asking confirmation questions (unless `--dry-run`)
