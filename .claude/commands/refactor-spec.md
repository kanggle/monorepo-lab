---
name: refactor-spec
description: Refactor specs — improve structure, consistency, and clarity without changing meaning
---

# refactor-spec

Refactor specification files safely without changing their defined requirements or contracts.

## Usage

```
/refactor-spec <scope>                                     # scan scope + refactor
/refactor-spec <scope> <target> <refactoring-type>         # refactor specific target
/refactor-spec <scope> --dry-run                           # analyze only (show plan)
/refactor-spec <scope> --focus=<category>                  # filter by category
```

Scope values:

| Scope | Target |
|---|---|
| `platform` | `platform/` |
| `contracts` | `specs/contracts/` |
| `<service>` | `specs/services/<service>/` |
| `features` | `specs/features/` |
| `use-cases` | `specs/use-cases/` |
| `all` | All spec directories |

Examples:

```
/refactor-spec platform
/refactor-spec contracts --focus=consistency
/refactor-spec <service-name> architecture.md structure
/refactor-spec all --dry-run
/refactor-spec features <feature-file>.md missing-section
/refactor-spec use-cases --focus=formatting
```

## Mode Selection

| Arguments | Mode | Description |
|---|---|---|
| `<scope>` only | **Scan** | Scan all spec files in scope, auto-discover issues, and fix |
| `<scope> <target> <type>` | **Target** | Execute specified refactoring on a specific file directly |

---

## Refactoring Categories

| Category | Description |
|---|---|
| **structure** / `restructure sections` | Section ordering inconsistent with other specs of the same type |
| **consistency** / `unify format` | Formatting inconsistencies (table style, heading levels, list markers) |
| **missing-section** / `add missing section` | Required or standard sections missing compared to sibling specs |
| **dead-reference** / `fix broken ref` | Cross-references pointing to non-existent files or sections |
| **duplication** / `deduplicate` | Same information defined in multiple specs (violates single source of truth) |
| **naming** / `rename` | File names, section titles, or term usage not matching `naming-conventions.md` or `glossary.md` |
| **clarity** / `rewrite` | Ambiguous or unclear language that could cause misinterpretation |
| **orphan** / `remove orphan` | Spec files not referenced by any other spec, task, or contract |

---

## Constraints

This is spec refactoring, NOT spec authoring:

- **No requirement changes.** Business rules, acceptance criteria, and behavioral definitions must remain identical.
- **No contract changes.** API endpoints, request/response schemas, event payloads, and status codes must not change.
- **No scope changes.** Service boundaries, ownership, and feature scope must not change.
- **No new decisions.** Do not introduce architecture decisions, new rules, or new constraints.

If a discovered issue requires changing requirements or contracts, report it as a finding but do NOT fix it.

---

## Mode: Target

Direct refactoring of a specific spec file.

### Procedure

1. Read `CLAUDE.md`
2. Read `platform/entrypoint.md`
3. Read `platform/naming-conventions.md` (if exists)
4. Read the target spec file
5. Read sibling specs of the same type to establish the expected format and structure
6. Identify the specific refactoring to apply
7. Verify the change does NOT alter any requirement, contract, or decision
8. Apply the refactoring
9. Verify all cross-references in the modified file still point to existing targets

---

## Mode: Scan

Analyze all spec files in scope, find issues, and fix them.

### Architecture

```
Main context (analyze + coordinate)
  ├─ Phase 1: Discovery & Analysis
  ├─ Phase 2: Categorize & Prioritize
  ├─ Phase 3: Execution Plan
  ├─ Phase 4: Execute fixes (sequential, in main context)
  └─ Phase 5: Verify & Summary
```

Spec refactoring executes in the main context (no worktree needed — no tests to run, no build to break).

### Phase 1: Discovery & Analysis

1. Read `CLAUDE.md`
2. Read `platform/entrypoint.md`
3. Read `platform/naming-conventions.md` (if exists)
4. Read `platform/glossary.md` (if exists)
5. Collect all spec files in the target scope
6. For each file:
   - Check section structure against sibling specs of the same type
   - Check formatting consistency (heading levels, table style, list markers)
   - Check all cross-references (`specs/`, `tasks/`, `.claude/`) point to existing files
   - Check terminology matches `glossary.md`
   - Check for duplicated definitions across files

### Phase 2: Categorize & Prioritize

For each issue, record:
- File path and line range
- Category (from table above)
- Description (what is wrong and what the fix is)
- Risk: low (formatting, naming) / medium (structure, clarity) / high (dedup, reference fix)

Prioritize by:
1. **dead-reference** first (broken links block navigation)
2. **duplication** second (conflicting sources of truth cause implementation errors)
3. **missing-section** third (incomplete specs cause gaps)
4. **structure** and **consistency** fourth (readability)
5. **naming** and **clarity** last (polish)

Group issues in the same file into a single refactoring unit.

### Phase 3: Execution Plan

Present the plan:

```
## Spec Refactoring Plan — {scope}

| # | Category | File | Description | Risk |
|---|---|---|---|---|

Total: N items
```

If `--dry-run` is specified, **stop here**.

### Phase 4: Execute Fixes

For each refactoring unit (one file at a time):

1. Read the target file
2. Apply the fix
3. Verify the change does not alter requirements, contracts, or decisions
4. Verify all cross-references in the modified file are valid
5. Proceed to next unit

### Phase 5: Verify & Summary

1. Re-scan all modified files to confirm no new issues introduced
2. Verify all cross-references across modified files still resolve
3. Output summary:

```
## Spec Refactoring Summary — {scope}

| # | Category | File | Description | Result |
|---|---|---|---|---|

Completed: N / Total: M
Skipped: [list with reasons]
Findings (require manual action): [list]
```

---

## Rules

- Follow CLAUDE.md Hard Stop Rules at every step
- No requirement changes — this is structural refactoring only
- No contract changes — API and event specs are structurally refactored but semantically unchanged
- No new rules or decisions — only improve how existing rules are expressed
- One file at a time — do not mix changes across files in a single edit
- If a fix would change the meaning of a spec, report it as a finding and skip
- Source of Truth Priority from CLAUDE.md applies — if duplication is found, keep the higher-priority source and update the lower-priority one
- Proceed without asking confirmation questions (unless `--dry-run`)
