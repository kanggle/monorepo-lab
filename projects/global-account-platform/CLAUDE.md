# CLAUDE.md

This document defines the minimum operating rules for AI agents and developers in this repository.

---

# Project Classification (Read First)

Before reading any other spec, resolve which rule layers apply to this project:

1. Read `PROJECT.md` at repository root to obtain `domain` and `traits`.
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
- Only tasks in `tasks/ready/` may be implemented.
- Follow the standard workflow: plan → implement → test → review.
- If specifications are missing, unclear, or conflicting, stop and report the issue.

---

# Source of Truth Priority

Use documents in the following order:

1. `PROJECT.md` (project classification — domain, traits)
2. `rules/common.md` and the canonical files it indexes
3. `rules/domains/<declared-domain>.md` (if present)
4. `rules/traits/<declared-trait>.md` for each trait (if present)
5. `platform/` (remaining files, including `entrypoint.md` and auxiliary specs)
   - Within `platform/service-types/`, only the file matching the target service's declared `Service Type` is read; other service-type files are skipped.
6. `specs/contracts/`
7. `specs/services/`
8. `specs/features/`
9. `specs/use-cases/`
10. `tasks/ready/`
11. `.claude/skills/`
12. `knowledge/`
13. `docs/` (excluding `docs/guides/`)

> `docs/guides/` is for human reference only. AI agents must NOT read or use it as a source of truth.
14. existing code

If any lower-priority source conflicts with a higher-priority source, follow the higher-priority source.
If a source is empty or does not exist, skip it and follow the next priority source.

Conflict resolution between layers 2–4: common wins unless a domain/trait file contains an explicit `## Overrides` block referencing the specific common rule being relaxed. If a conflict exists without an explicit override, STOP per Hard Stop Rules.

---

# Task Rules

- Do not implement work without a task.
- Do not implement tasks outside `tasks/ready/`.
- If a task conflicts with specs, specs win.
- If implementation requires spec or contract changes, update them first.
- Tasks missing required sections must not be implemented.
- Review and lifecycle rules are defined in `tasks/INDEX.md`.

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

1. Read `CLAUDE.md`
2. Read `PROJECT.md` and load the rule layers determined by its `domain` and `traits` (see "Project Classification" above)
3. Read the target task in `tasks/ready/`
4. Follow `platform/entrypoint.md` for spec reading order
5. Determine the target service's `Service Type` from `specs/services/<service>/architecture.md` and read the matching `platform/service-types/<type>.md` (exactly one file)
6. Read `.claude/skills/INDEX.md` and matched skill files for implementation guidance
7. Use `knowledge/` for design judgment only
8. Read existing code in the target service to understand current patterns, conventions, and structure
9. Implement and test
10. Prepare for review

---

# Hard Stop Rules

Stop immediately if any of the following is true:

- `PROJECT.md` is missing, unparsable, or declares a `domain`/`trait` not present in `rules/taxonomy.md`
- a domain/trait rule file exists but explicitly conflicts with a common rule without an `## Overrides` block
- the task is not in `tasks/ready/`
- required specifications do not exist
- specifications conflict
- acceptance criteria are unclear
- required contracts are missing
- the task requires architecture decisions not documented in specs
- the target service's `Service Type` is undeclared or not in the catalog at `platform/service-types/INDEX.md`

If stopped, report the blocking issue explicitly.

Do not attempt workaround implementation.

---

# Architecture Rule

Service architecture rules follow `platform/architecture-decision-rule.md`.

Each service must follow the architecture declared in `specs/services/<service>/architecture.md`.

---

# Shared Library Rule

Shared libraries must follow `platform/shared-library-policy.md`.

Do not move service-owned domain logic into `libs/`.

---

# Contract Rule

API and event changes must update contracts before implementation.

---

# Testing Rule

Every implementation must include tests appropriate to the task scope and follow:

- `platform/testing-strategy.md`
