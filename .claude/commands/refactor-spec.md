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

## Operational Patterns

### Tier Classification (dry-run finding triage)

When `--dry-run` surfaces findings, classify each into one of three tiers before deciding closure path:

| Tier | Definition | Closure pattern |
|---|---|---|
| **Tier 1** | Mechanical, in-cycle author artifact (newly-authored specs from the same author/session — depth miscount, basename typo, hedge phrasing). Risk: low. | Single PR mechanical batch — `sed` per pattern + per-file `Edit` for outliers. Verify via dead-ref checker. |
| **Tier 2** | Judgment-required (stale references where production code was renamed; sample link semantics; "참조 구현" mismatch). Risk: low–medium. | Author task with 3-option weighing (rename / drop link / drop sentence) in Goal. Single PR closure after judgment. |
| **Tier 3** | Pre-existing artifact from a different author origin or era (pre-import depth bug, prior-author miscount, cross-project sync gap). Risk: low (mechanical) but blast radius wider. | Separate task per origin (don't bundle with current cycle's Tier 1). Sibling mechanical batch precedent. |

If `--dry-run` produces a mix, **report each tier separately** in Phase 3 plan and let the user choose closure scope (Tier 1 only / Tier 1+3 bundle / per-tier individual).

### Mechanical batch closure pattern

For Tier 1 + Tier 3 mechanical fixes:

1. **Author task** in `tasks/ready/` describing total fix count + pattern catalog + Tier 2 skips with explicit `## Out of Scope` rationale.
2. **Apply fixes** preferring `sed -i 's|](OLD)|](NEW)|g'` for repeated patterns (markdown link syntax `](...)` narrows over-match risk); `Edit` for one-off cases.
3. **Verify** with a dead-ref checker script (markdown link extractor + `[ -e ... ]` per target). Expected remaining count = Tier 2 skips (documented in task body).
4. **Move lifecycle** `ready/` → `review/`. Don't modify task body after move except the Status field (CLAUDE.md HARDSTOP-05 — review/ frozen).
5. **Commit + push** per `feedback_pr_on_request.md` policy (PR open is user-explicit). Separate impl PR from close-chore PR per `tasks/INDEX.md § PR Separation Rule`.

### Cycle pattern (dry-run → multi-PR closure)

A single `/refactor-spec all --dry-run` audit typically spawns a sequence of single-PR closures spanning multiple projects:

```
dry-run (audit) ──┬──→ Tier 1 closure (current author/project)
                  ├──→ Tier 3 #N closure (per pre-existing artifact origin)
                  └──→ Tier 2 closure (per judgment)
```

Each closure = spec PR (task author) + impl PR (fixes + lifecycle move) + close-chore PR (review → done + INDEX update). The triplet per task ID preserves the audit trail.

**Diminishing-scope pattern (empirical)**: scope often shrinks per task in a single cycle (e.g. 5 → 47 → 1 → 1 fixes across 4 tasks). Tier 3 #1 often holds the largest pre-existing artifact backlog; later tasks are residual judgment.

**Cycle termination signal**: dead-ref checker returns 0 across all spec scopes after the last task. Record this in the final close-chore's Outcome section as a cycle-summary table.

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
