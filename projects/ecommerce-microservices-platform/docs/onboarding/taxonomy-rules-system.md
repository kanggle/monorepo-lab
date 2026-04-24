# Taxonomy-Based Rules System — Onboarding Guide

> **Audience**: Developers joining this repository, or anyone bootstrapping a new project from this template.
> **Status**: v0.1 (dogfood phase on ecommerce-microservices-platform).

This guide explains how the **taxonomy-based rules system** works, why it exists, and how to use it when starting a new project or working on an existing one.

For human readers, this is the fastest way to understand what `PROJECT.md` and `specs/rules/` are for. For authoritative specifications, follow the links into [CLAUDE.md](../../CLAUDE.md), [specs/platform/entrypoint.md](../../specs/platform/entrypoint.md), and [specs/rules/README.md](../../specs/rules/README.md).

---

## Why this system exists

The goal is to be able to start a **new project in any business domain** (ecommerce, fintech, logistics, edtech, …) by declaring just a few classification tags, and have the correct rule set automatically apply. Without this system, every new project either (a) copies all rules and manually strips what doesn't fit, or (b) starts from scratch and re-derives the same conventions — both waste effort and drift over time.

The system answers: **"Which rules apply to this project, and why?"**

---

## Three moving parts

### 1. `PROJECT.md` — the classification declaration

At the repository root. YAML frontmatter declares the project's classification:

```yaml
---
name: ecommerce-microservices-platform
domain: ecommerce
traits: [transactional, content-heavy, read-heavy, integration-heavy]
service_types: [rest-api, event-consumer, batch-job, frontend-app]
compliance: []
data_sensitivity: pii
scale_tier: startup
taxonomy_version: 0.1
---
```

Required fields: `name`, `domain`, `traits`, `taxonomy_version`.

- **`domain`** — exactly one primary domain from the catalog.
- **`traits`** — zero or more cross-cutting characteristics from the catalog.
- **`taxonomy_version`** — pins this declaration to a specific version of the catalog.

### 2. `specs/rules/taxonomy.md` — the catalog

The authoritative list of 38 domains and 11 traits. Every value used in `PROJECT.md` must exist here. Unknown values are a Hard Stop.

This file also defines:
- What each domain/trait means (definition, typical subsystems, when to pick)
- Common combinations with examples
- Incompatibilities that require justification

### 3. `specs/rules/` — the rule layers (three-tier structure)

```
specs/rules/
├── README.md          — system overview and resolution order
├── taxonomy.md        — the catalog above
├── common.md          — index pointing at specs/platform/*.md (14 files)
├── domains/
│   └── ecommerce.md   — primary domain rules (currently one)
└── traits/
    ├── transactional.md
    ├── content-heavy.md
    ├── read-heavy.md
    └── integration-heavy.md
```

**Layers** (always applied in this order):

1. **Common** (always) — the 14 canonical platform files (architecture, coding, security, testing, etc.) indexed by `common.md`. These are not rewritten per project; they are shared technology baselines.
2. **Domain** (one) — the file matching the project's declared `domain`. Contains bounded contexts, ubiquitous language, domain-specific mandatory rules, and error-code section references.
3. **Traits** (many) — one file per declared trait. Contains cross-cutting rules that are additive to the common layer (e.g., `transactional.md` adds idempotency/saga/outbox requirements on top of common architecture rules).

Service Type (`rest-api`, `event-consumer`, etc.) is a **separate orthogonal axis** from domain/traits and continues to live under [specs/platform/service-types/](../../specs/platform/service-types/). One service picks exactly one type.

---

## Resolution order (the complete picture)

When starting any implementation task, read rules in this order:

1. [CLAUDE.md](../../CLAUDE.md) — including its "Project Classification (Read First)" section
2. [PROJECT.md](../../PROJECT.md) — extract `domain` and `traits`
3. Follow [specs/platform/entrypoint.md](../../specs/platform/entrypoint.md) **Step 0**:
   - Load [specs/rules/common.md](../../specs/rules/common.md) and every file it indexes
   - Load [specs/rules/domains/<domain>.md](../../specs/rules/domains/) (if present)
   - Load [specs/rules/traits/<trait>.md](../../specs/rules/traits/) for each declared trait (if present)
4. Continue with the Core / Service-Type-Specific / Auxiliary layers described in `entrypoint.md`
5. Read the target task, service specs, contracts, etc. (standard workflow)

**Missing rule file ≠ error**. If a project declares a trait but no matching `traits/<trait>.md` exists, that just means "no additional constraints beyond common". Rule files are generated **on-demand** when a project actually needs them — we don't pre-create stubs for all 38 × 11 combinations.

---

## Conflict resolution

If two layers disagree, the higher layer wins:

1. **Common** is the baseline. Domain or Trait files may **specialize** it but may not silently contradict it.
2. To relax or override a common rule, the domain/trait file must include an explicit `## Overrides` block naming the specific rule being relaxed and the reason.
3. Implicit conflicts (contradictions without an Overrides block) are a **Hard Stop** per CLAUDE.md.

For trait-vs-trait conflicts (e.g., `real-time` + `batch-heavy`), check the Incompatibilities table in [specs/rules/taxonomy.md](../../specs/rules/taxonomy.md) first; coexistence usually requires a justification recorded in `PROJECT.md`'s `## Overrides` section.

---

## Worked example — ecommerce-microservices-platform

`ecommerce-microservices-platform` is classified as:

- **domain**: `ecommerce`
- **traits**: `transactional`, `content-heavy`, `read-heavy`, `integration-heavy`

What gets loaded:

| Layer | Files |
|---|---|
| Common | [architecture.md](../../specs/platform/architecture.md), [coding-rules.md](../../specs/platform/coding-rules.md), [security-rules.md](../../specs/platform/security-rules.md), [naming-conventions.md](../../specs/platform/naming-conventions.md), [error-handling.md](../../specs/platform/error-handling.md), [testing-strategy.md](../../specs/platform/testing-strategy.md), [observability.md](../../specs/platform/observability.md), [shared-library-policy.md](../../specs/platform/shared-library-policy.md), [ownership-rule.md](../../specs/platform/ownership-rule.md), [versioning-policy.md](../../specs/platform/versioning-policy.md), [repository-structure.md](../../specs/platform/repository-structure.md), [service-boundaries.md](../../specs/platform/service-boundaries.md), [dependency-rules.md](../../specs/platform/dependency-rules.md), [architecture-decision-rule.md](../../specs/platform/architecture-decision-rule.md) |
| Domain | [specs/rules/domains/ecommerce.md](../../specs/rules/domains/ecommerce.md) |
| Traits | [transactional.md](../../specs/rules/traits/transactional.md), [content-heavy.md](../../specs/rules/traits/content-heavy.md), [read-heavy.md](../../specs/rules/traits/read-heavy.md), [integration-heavy.md](../../specs/rules/traits/integration-heavy.md) |

**Explicitly excluded** (declared in `PROJECT.md` Out of Scope): `marketplace`, `regulated`, `audit-heavy`, `multi-tenant`, `real-time`, `batch-heavy`, `data-intensive`. If any of these become relevant later, update `PROJECT.md` first and add the missing rule files.

---

## Bootstrapping a new project

Suppose you want to start a **fintech** project with `transactional`, `regulated`, and `audit-heavy` traits.

1. **Copy** `ecommerce-microservices-platform/` to a new directory (exclude `node_modules`, `build`, `.git`).
2. **Edit `PROJECT.md`** at the new root:
   - Change `name`
   - Change `domain: fintech`
   - Change `traits: [transactional, regulated, audit-heavy]`
   - Update `compliance` (e.g., `[pci-dss]`), `data_sensitivity` (e.g., `financial`), etc.
   - Update the rationale sections (Domain Rationale, Trait Rationale, Out of Scope)
3. **Reconcile rule files** under `specs/rules/`:
   - `rules/traits/transactional.md` — already exists, keep.
   - `rules/traits/regulated.md`, `rules/traits/audit-heavy.md` — create from the template shape in [specs/rules/README.md](../../specs/rules/README.md).
   - `rules/domains/fintech.md` — create with bounded contexts such as `Account`, `Ledger`, `Transaction`, `Settlement`, `KYC`, `Risk`.
   - Leave `rules/traits/content-heavy.md`, `rules/traits/read-heavy.md`, `rules/traits/integration-heavy.md`, and `rules/domains/ecommerce.md` alone — they belong to the previous project but are inert unless referenced by the new `PROJECT.md`. (Alternatively, delete them for cleanliness.)
4. **Update `specs/platform/error-handling.md`**:
   - Keep the `Platform-Common` sections (Auth, Validation, OAuth, General, etc.) verbatim.
   - Remove the sections tagged `[domain: ecommerce]` and replace with fintech error codes tagged `[domain: fintech]`.
5. **Clear project-specific content** (not the rule system itself):
   - Empty `specs/services/`, `specs/contracts/`, `specs/features/`, `specs/use-cases/`
   - Empty `tasks/{ready,in-progress,review,done,backlog,archive}/`
   - Empty `apps/`, `libs/`
6. **Run** `/validate-rules` (once the skill understands the new layer) to sanity-check references.
7. **Start the first task** via the normal workflow.

---

## Common pitfalls

- **Declaring a tag that isn't in [taxonomy.md](../../specs/rules/taxonomy.md)** — Hard Stop. Add the tag to the catalog AND create the matching rule file in the same change.
- **Copying rule text into another file** — causes drift. [common.md](../../specs/rules/common.md) is an **index only**; never paste rule bodies into it.
- **Relaxing a common rule inside a trait file without an `## Overrides` block** — silent conflict, Hard Stop when detected. Always declare overrides explicitly.
- **Forgetting to bump `taxonomy_version` in `PROJECT.md`** — if a breaking change happens in [taxonomy.md](../../specs/rules/taxonomy.md), all existing `PROJECT.md` files must migrate. Track this with the `taxonomy_version` field.
- **Assuming Service Type is part of the taxonomy** — it isn't. Service Type is orthogonal and declared per service in [specs/services/<service>/architecture.md](../../specs/services/).

---

## Where to read next

- [CLAUDE.md](../../CLAUDE.md) — minimum operating rules, Hard Stop list
- [specs/platform/entrypoint.md](../../specs/platform/entrypoint.md) — Step 0 and the rest of the reading order
- [specs/rules/README.md](../../specs/rules/README.md) — resolution order and layer semantics
- [specs/rules/taxonomy.md](../../specs/rules/taxonomy.md) — the 38 domains and 11 traits with definitions
- [specs/rules/domains/ecommerce.md](../../specs/rules/domains/ecommerce.md) — a complete domain rule file example
- [specs/rules/traits/transactional.md](../../specs/rules/traits/transactional.md) — a complete trait rule file example

This guide is for understanding. The authoritative rules live in the files above.
