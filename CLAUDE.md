# CLAUDE.md

This document defines the minimum operating rules for AI agents and developers in this repository.

---

# Repository Layout (Read First)

This is a **monorepo** containing the shared library layer at the repo root and one or more projects under `projects/`.

```
<repo-root>/
├── CLAUDE.md                 ← this file (shared)
├── TEMPLATE.md               ← template extraction guide
├── README.md                 ← portfolio hub
├── platform/                 ← platform regulations (shared)
├── rules/                    ← rule library: domains/, traits/ (shared)
├── .claude/                  ← AI agent config: skills/, agents/, commands/, config/ (shared)
├── libs/                     ← shared Java libraries
├── tasks/                    ← monorepo-level task lifecycle: INDEX.md + ready/, in-progress/, review/, done/, templates/ (shared templates)
├── docs/guides/              ← human-oriented guides (shared)
├── build.gradle, settings.gradle, gradle/ ...
└── projects/
    └── <project-name>/       ← one directory per project
        ├── PROJECT.md        ← project classification (domain, traits)
        ├── README.md
        ├── apps/             ← service implementations
        ├── specs/            ← project-specific: contracts/, services/, features/, use-cases/
        ├── tasks/            ← project task lifecycle: INDEX.md + ready/, in-progress/, ...
        ├── knowledge/
        ├── docs/
        ├── infra/
        └── build.gradle      ← project-level Gradle (placeholder)
```

**Shared vs project content** (strict boundary):

- **Shared (at repo root)**: `platform/`, `rules/`, `.claude/`, `libs/`, `tasks/templates/`, `tasks/INDEX.md` + `tasks/{ready,in-progress,review,done}/` (monorepo-level lifecycle), `docs/guides/`, `CLAUDE.md`, `TEMPLATE.md` — must remain project-agnostic. No project-specific service names, API paths, domain entities.
- **Project-specific (under `projects/<project-name>/`)**: `PROJECT.md`, `apps/`, `specs/`, `tasks/` (project lifecycle, distinct from root `tasks/`), `knowledge/`, `docs/` (except `guides/`), `infra/`.

Violating this boundary blocks Template extraction and creates drift across projects. See `TEMPLATE.md` for the Discovery → Distribution strategy.

---

# Identify the Target Project (Read First)

Before reading any spec or starting implementation:

1. **Identify the target project** from the current working context:
   - Look at the file the user just opened/referenced, the directory they are working in, or their explicit instruction.
   - Walk up from that location to find the nearest ancestor containing a `PROJECT.md`.
   - The typical result: `projects/<project-name>/PROJECT.md`.
2. **If ambiguous** (multiple projects touched in one request, or no project mentioned), **ask the user** which project is the target. Do not guess.
3. **If no `PROJECT.md` is found in any ancestor**, STOP and report — the request is outside any defined project.

All path references in this document refer to either:

- **Repo-root-relative paths** (start with shared dirs like `platform/`, `rules/`, `.claude/`, `libs/`, `tasks/templates/`, `docs/guides/`) — these are unambiguous.
- **Project-relative paths** (`PROJECT.md`, `apps/`, `specs/`, `tasks/ready/`, `knowledge/`, `docs/`) — these are **inside the target project directory** (`projects/<project-name>/...`).

Throughout the rest of this document, project-relative paths are written without the `projects/<project-name>/` prefix for brevity. Always prefix them with the resolved project path when interpreting.

---

# Project Classification (After Target Project Is Identified)

Before reading any other spec, resolve which rule layers apply to the target project:

1. Read the target project's `PROJECT.md` to obtain `domain` and `traits`.
2. If `PROJECT.md` is missing or its frontmatter cannot be parsed, STOP and report — do not proceed with any implementation.
3. Consult `.claude/config/activation-rules.md` for the short dispatch table — it tells you which rule categories and skill bundles each declared trait/domain activates. Also confirm membership against `.claude/config/domains.md` and `.claude/config/traits.md`.
4. Verify that the declared `domain` and each `trait` appear in `rules/taxonomy.md` (authoritative narrative definitions). Undeclared or unknown tags are a Hard Stop.
5. Load detailed rule files in the order defined by `rules/README.md`:
   - `rules/common.md` (always)
   - `rules/domains/<domain>.md` (if present)
   - `rules/traits/<trait>.md` for each declared trait (if present)
6. Missing domain/trait files mean "no additional constraints beyond common" — do not auto-generate stubs. See the on-demand policy in `rules/README.md`.

Agents and skills are split into `common/` (always loaded) and `domain/<domain>/` (loaded only when the declared domain matches). See `.claude/agents/domain/README.md` and `.claude/skills/domain/README.md`.

---

# Core Principles

- Specifications are the source of truth.
- Work must be executed through tasks.
- Only tasks in the target project's `tasks/ready/` may be implemented.
- Follow the standard workflow: plan → implement → test → review.
- If specifications are missing, unclear, or conflicting, stop and report the issue.

---

# Source of Truth Priority

When documents conflict, use the following order (higher number = lower priority):

1. `<project>/PROJECT.md` (project classification — domain, traits)
2. `rules/common.md` and the canonical files it indexes (shared)
3. `rules/domains/<declared-domain>.md` (shared, if present)
4. `rules/traits/<declared-trait>.md` for each trait (shared, if present)
5. `platform/` remaining files, including `entrypoint.md` and auxiliary specs (shared)
   - Within `platform/service-types/`, only the file matching the target service's declared `Service Type` is read; other service-type files are skipped.
6. `<project>/specs/contracts/`
7. `<project>/specs/services/`
8. `<project>/specs/features/`
9. `<project>/specs/use-cases/`
10. `<project>/tasks/ready/`
11. `.claude/skills/` (shared)
12. `<project>/knowledge/`
13. `<project>/docs/` (excluding `docs/guides/` at repo root)

> `docs/guides/` at repo root is for human reference only. AI agents must NOT read or use it as a source of truth.

14. existing code

If any lower-priority source conflicts with a higher-priority source, follow the higher-priority source.
If a source is empty or does not exist, skip it and follow the next priority source.

Conflict resolution between layers 2–4: common wins unless a domain/trait file contains an explicit `## Overrides` block referencing the specific common rule being relaxed. If a conflict exists without an explicit override, STOP per Hard Stop Rules.

---

# Task Rules

- Do not implement work without a task.
- **Project-internal work** (changes inside a single `projects/<name>/`): the task must live in that project's `tasks/ready/` and follow `projects/<name>/tasks/INDEX.md`.
- **Monorepo-level work** (changes to shared library `libs/`, `platform/`, `rules/`, `.claude/`, `tasks/templates/`, `docs/guides/`, root `build.gradle`/`settings.gradle`/`.github/workflows/`/`scripts/`/`package.json`/`CLAUDE.md`/`TEMPLATE.md`, or cross-project structural changes): the task must live in repo-root `tasks/ready/` and follow `tasks/INDEX.md`. See the "When to Use Root vs Project Tasks" decision table in that file.
- Do not implement tasks outside the appropriate `tasks/ready/`.
- If a task conflicts with specs, specs win.
- If implementation requires spec or contract changes, update them first.
- Tasks missing required sections must not be implemented.
- Review and lifecycle rules are defined in the relevant `tasks/INDEX.md` (root or project).

Required task sections:

- Goal
- Scope
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Required Workflow

For any implementation task:

1. Read `CLAUDE.md` (this file)
2. Decide whether the task is **project-internal** or **monorepo-level** (see Task Rules above and the decision table in `tasks/INDEX.md`).

**For project-internal work:**

3. Identify the target project (see "Identify the Target Project" above)
4. Read the target project's `PROJECT.md` and load the rule layers determined by its `domain` and `traits` (see "Project Classification" above)
5. Read the target task in `<project>/tasks/ready/`
6. Follow `platform/entrypoint.md` for spec reading order
7. Determine the target service's `Service Type` from `<project>/specs/services/<service>/architecture.md` and read the matching `platform/service-types/<type>.md` (exactly one file)
8. Read `.claude/skills/INDEX.md` and matched skill files for implementation guidance
9. Use `<project>/knowledge/` for design judgment only
10. Read existing code in the target service to understand current patterns, conventions, and structure
11. Implement and test
12. Prepare for review

**For monorepo-level work:**

3. Read the target task in repo-root `tasks/ready/` and confirm its scope is monorepo-level per `tasks/INDEX.md` § "When to Use Root vs Project Tasks"
4. Read the relevant shared file(s) the task targets (`libs/`, `platform/`, `rules/`, `.claude/`, root `build.gradle`/`settings.gradle`/`.github/workflows/`, `scripts/`, etc.)
5. If the change has implications for any `projects/<name>/`, enumerate them in the task and verify each affected project's tests after implementation
6. Read `.claude/skills/INDEX.md` and matched skill files only if a skill applies (most monorepo-level work is build/CI/docs without a skill match)
7. Implement and verify (typically: `./gradlew check` for build changes, dry-run for script changes, doc lint for documentation changes)
8. Prepare for review

---

# Hard Stop Rules

Stop immediately if any of the following is true:

- No `PROJECT.md` can be located for the current working context
- `PROJECT.md` is missing, unparsable, or declares a `domain`/`trait` not present in `rules/taxonomy.md`
- A shared library file (under `platform/`, `rules/`, `.claude/`, `libs/`, `tasks/templates/`, `docs/guides/`) contains project-specific content (service names, API paths, domain entities) — the Library vs Project boundary is broken
- A domain/trait rule file exists but explicitly conflicts with a common rule without an `## Overrides` block
- The task is not in the target project's `tasks/ready/`
- Required specifications do not exist
- Specifications conflict
- Acceptance criteria are unclear
- Required contracts are missing
- The task requires architecture decisions not documented in specs
- The target service's `Service Type` is undeclared or not in the catalog at `platform/service-types/INDEX.md`

If stopped, report the blocking issue explicitly.

Do not attempt workaround implementation.

---

# Architecture Rule

Service architecture rules follow `platform/architecture-decision-rule.md`.

Each service must follow the architecture declared in its `<project>/specs/services/<service>/architecture.md`.

---

# Shared Library Rule

Shared libraries must follow `platform/shared-library-policy.md`.

Do not move service-owned domain logic into `libs/`.

Do not introduce project-specific content (service names, API paths, domain entities) into any shared library file — this is enforced at the Hard Stop level.

---

# Contract Rule

API and event changes must update contracts in the target project's `specs/contracts/` before implementation.

---

# Testing Rule

Every implementation must include tests appropriate to the task scope and follow:

- `platform/testing-strategy.md` (shared)
- The target project's and target service's specific testing requirements (declared in the service's `specs/services/<service>/architecture.md` or linked test-requirement docs)

---

# Cross-Project Changes

If a change affects multiple projects (e.g., a library rule refactor that requires every project to update), the same PR must include:

1. The library change (under shared paths).
2. The adaptation in every affected project (under `projects/<project-name>/`).

Atomic cross-project commits are the primary advantage of the monorepo layout. Breaking this pattern (separate PRs, staggered merges) creates transiently broken states — avoid.

Conventional Commit scopes help reviewers understand the surface:

- `feat(lib):`, `refactor(lib):`, `fix(lib):` — shared library changes
- `feat(rules):`, `feat(rules-<domain>):` — rule library changes
- `feat(<project>):` — project-specific changes (e.g., `feat(wms):`)
- Breaking changes use `!` suffix or `BREAKING CHANGE:` footer

See `docs/guides/` for the full commit-message convention and cross-project workflow.
