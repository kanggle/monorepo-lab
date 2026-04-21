# Spec Entry Point

This document defines the starting point for reading specifications before implementation.

---

# Purpose

Use this file to reduce ambiguity when selecting which platform specs to read first.

AI agents and developers must begin platform-spec reading from this file.

---

# Step 0: Project Classification (Read Before Core)

Before reading anything else under `platform/`, resolve the project's classification:

1. Read `PROJECT.md` at repository root. Extract `domain` and `traits` from its frontmatter.
2. Consult `.claude/config/activation-rules.md` for the short dispatch summary — which rule categories and skill bundles each declared trait/domain activates. Confirm membership against `.claude/config/domains.md` and `.claude/config/traits.md`.
3. Verify both values exist in `rules/taxonomy.md` (authoritative narrative definitions). Unknown values → Hard Stop.
4. Read detailed rule layers in this order (absent files mean "no additional constraints"):
   1. `rules/common.md` and every file it indexes
   2. `rules/domains/<domain>.md` (if present)
   3. `rules/traits/<trait>.md` for each declared trait (if present)
5. Continue with the Core section below.

The Core / Service-Type-Specific / Auxiliary layers described next are **still authoritative** — Step 0 augments them by narrowing the active rule set to what applies to this project.

> Routing layer (`.claude/config/`) gives the **dispatch summary**; this file (`entrypoint.md`) plus `rules/` give the **detailed rules**. Do not skip either.

---

# Platform Specs: Core, Service-Type-Specific, and Auxiliary

Platform specs are divided into three layers:

- **Core**: Always read before any implementation task.
- **Service-Type-Specific**: Read exactly one file matching the target service's declared `Service Type`.
- **Auxiliary**: Read only when the task requires it. Check the task's `Related Specs` section.

## Core (Always Read)

1. `architecture.md`
2. `service-boundaries.md`
3. `dependency-rules.md`
4. `shared-library-policy.md`
5. `security-rules.md`

## Service-Type-Specific (Read Exactly One)

After Core, read the file under `platform/service-types/` that matches the target service's `Service Type` declared in `specs/services/<service>/architecture.md`. Catalog and selection rules live in `platform/service-types/INDEX.md`.

| Service Type | File |
|---|---|
| `rest-api` | `service-types/rest-api.md` |
| `event-consumer` | `service-types/event-consumer.md` |
| `batch-job` | `service-types/batch-job.md` |
| `grpc-service` | `service-types/grpc-service.md` |
| `graphql-service` | `service-types/graphql-service.md` |
| `ml-pipeline` | `service-types/ml-pipeline.md` |
| `frontend-app` | `service-types/frontend-app.md` |

Reading more than one service-type spec for a single task is forbidden — pick the one declared by the target service.

## Auxiliary (Read When Relevant)

| Tag | Specs to Read |
|---|---|
| `api` | `api-gateway-policy.md`, `versioning-policy.md`, `error-handling.md` |
| `event` | `event-driven-policy.md` |
| `deploy` | `deployment-policy.md`, `observability.md` |
| `code` | `naming-conventions.md`, `coding-rules.md`, `testing-strategy.md` |
| `test` | `testing-strategy.md` |
| `adr` | `architecture-decision-rule.md`, `ownership-rule.md` |
| `onboarding` | `glossary.md`, `repository-structure.md` |
| `storage` | `object-storage-policy.md` (auto-activated when project declares `content-heavy` trait) |

Tags are declared in the task file under `Task Tags` (optional section, not required by CLAUDE.md).
If no tags are declared or the section does not exist, read only `error-handling.md` and `testing-strategy.md` as defaults.

---

# Task Navigation Rule

After reading Core platform specs:

1. Read the target task in `tasks/ready/`
2. Identify the target service's `Service Type` from its `architecture.md` and read the matching `platform/service-types/<type>.md` (exactly one)
3. Read auxiliary specs matching the task's tags
4. Read related API or event contracts in `specs/contracts/`
5. Read the target service specs in `specs/services/<service>/`
6. Read related feature specs in `specs/features/` (if the directory exists and contains files)
7. Read related use-cases in `specs/use-cases/` (if the directory exists and contains files)

If a directory is empty or does not exist, skip it and continue to the next step.

---

# Usage Rules

- Do not start implementation from existing code.
- Do not use `knowledge/` as a substitute for `specs/`.
- Use `.claude/skills/` only after official specs have been read.
- Skip auxiliary specs that are clearly irrelevant to the task.

---

# Conflict Rule

If any document conflicts with a higher-priority spec:

- follow the higher-priority spec
- stop implementation if the conflict blocks the task
- report the conflicting documents explicitly