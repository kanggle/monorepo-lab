# Repository Structure

Defines per-directory ownership and placement rules. **Canonical directory layout** is in [`../CLAUDE.md`](../CLAUDE.md) § Repository Layout (single source of truth); this file adds ownership notes and shape distinction on top.

---

# Repository Shapes

This repository may be used either as a single-project repository or as a multi-project monorepo. The current shape is the **monorepo** (see `projects/` at repo root and `PROJECT.md` inside each).

## Single-project shape

Used when one project owns the entire repository.

```
/
├── apps/                    # Deployable services (owned by service teams)
├── libs/                    # Shared Java libraries
├── packages/                # Shared TypeScript packages (when frontend present)
├── specs/                   # Specifications — source of truth
├── tasks/                   # Implementation task lifecycle
├── knowledge/               # Design references (non-authoritative)
├── docs/                    # Human-oriented documentation
├── infra/                   # Infrastructure configuration
├── scripts/                 # Build / deploy / utility scripts
├── .claude/                 # AI agent guidance
├── platform/                # Platform specifications (this layer)
├── rules/                   # Rule library (common + domain + traits)
├── PROJECT.md               # Project classification (domain + traits)
└── CLAUDE.md                # AI operating rules
```

## Multi-project monorepo shape

Used when the repository hosts multiple projects sharing a library layer at the root.

```
/
├── .claude/                 # Shared agent config (always)
├── platform/                # Shared platform specs (always)
├── rules/                   # Shared rule library (always)
├── libs/                    # Shared Java libraries (always)
├── packages/                # Shared TypeScript packages (when ≥ 2 frontend apps share)
├── tasks/                   # Monorepo-level task lifecycle + tasks/templates/
├── docs/                    # Shared human docs (adr/, guides/, project-overview.md)
├── infra/                   # Shared infrastructure (observability stack, etc.)
├── scripts/                 # Shared build / deploy / utility scripts
├── build.gradle             # Root Gradle build
├── settings.gradle          # Root Gradle settings (includes each project)
├── CLAUDE.md                # AI operating rules
├── TEMPLATE.md              # Template extraction guide
├── README.md                # Portfolio hub
└── projects/                # Project instances
    ├── <project-a>/
    │   ├── PROJECT.md       # Project classification (domain + traits)
    │   ├── apps/            # Deployable services for this project
    │   ├── libs/            # OPTIONAL: project-scoped shared modules (see below)
    │   ├── specs/           # Project-internal specs
    │   ├── tasks/           # Project-internal task lifecycle
    │   ├── knowledge/       # Project design references
    │   ├── docs/            # Project human docs (non-guides)
    │   └── infra/           # Project-specific infrastructure
    └── <project-b>/
        └── ...
```

In the monorepo shape, the **library layer** (`platform/`, `rules/`, `.claude/`, `libs/`, `packages/` if present, `tasks/templates/`, `docs/guides/`, `CLAUDE.md`, `TEMPLATE.md`) is shared across all projects and must remain project-agnostic.

## Project-scoped shared modules (`projects/<p>/libs/`)

There are **two** library layers in the monorepo shape, and they are not interchangeable:

| | repo-root `libs/` | `projects/<p>/libs/` |
|---|---|---|
| Shared by | every project | the services of **one** project |
| Content | technical mechanisms only — project-agnostic, Hard-Stop-enforced (HARDSTOP-03) | that project's own domain vocabulary, including its **product decisions** (supported-value sets, domain policies) |
| Owner | Platform team | that project's team |
| Gradle include | `libs:<module>` | `projects:<p>:libs:<module>` |
| Travels on template extraction | no (stays in the monorepo library layer) | yes — it sits under `projects/<p>/` |

Use `projects/<p>/libs/` when two or more services **of the same project** declare the same domain concept and neither service owns it (`shared-library-policy.md` § Ownership Rule cannot pick a home, because the concept belongs to the project, not to one bounded context). Promoting such a concept to repo-root `libs/` instead would plant one project's product decision in the project-agnostic layer — the shape § Forbidden rules out.

Constraints, both directions:

- The module must not depend on any service module; consumers declare it with `implementation`, never `api` (`shared-library-policy.md` § Dependency Rule).
- It is not automatically promotable to repo-root `libs/` when a second project wants it — that promotion is a separate ADR decision, and it re-imposes the project-agnostic rule the module was placed here to avoid.
- Introducing one is an architecture decision and requires an ADR in the owning project's `docs/adr/` (`architecture-decision-rule.md`).
- **Check guard reachability before choosing the path.** Most repo guards and CI gradle-task lists key on `projects/*/apps/*`; a `libs/` module they do not enumerate is a module whose tests never run and whose drift nothing reports — and a guard that does not run reports green.

---

# Ownership

| Directory | Owner | Notes |
|---|---|---|
| `apps/` (single) or `projects/<p>/apps/` (mono) | Service team | One sub-dir per deployable service |
| `libs/` (repo root) | Platform team | Shared Java libs; technical reuse only — domain logic forbidden (`shared-library-policy.md`) |
| `projects/<p>/libs/` (mono, optional) | That project's team | Project-scoped shared modules — domain vocabulary shared by ≥ 2 of that project's services. Not project-agnostic, and not promotable to repo-root `libs/` without an ADR. See § Project-scoped shared modules |
| `packages/` | Frontend team | Shared TypeScript packages; only when ≥ 2 apps share |
| `specs/` (single) or `projects/<p>/specs/` (mono) | Architecture team | Source of truth: `contracts/`, `services/`, `features/`, `use-cases/` |
| `tasks/` | All teams | Lifecycle: `backlog/` → `ready/` → `in-progress/` → `review/` → `done/`. Templates under `tasks/templates/` (shared) |
| `.claude/skills/` | Platform team | AI implementation guidance — `SKILL.md` per skill folder, indexed in `INDEX.md` |
| `.claude/agents/` | Platform team | Agent definitions — `common/` (always) + `domain/<d>/` (when domain-specific) |
| `.claude/commands/` | Platform team | Slash command definitions |
| `.claude/config/` | Platform team | Dispatch catalogs (`domains.md`, `traits.md`, `activation-rules.md`) |
| `knowledge/` (single) or `projects/<p>/knowledge/` (mono) | All teams | Non-authoritative design references |
| `docs/` | All teams | `adr/` (decisions), `guides/` (human-only, shared in monorepo), `project-overview.md` (monorepo only) |
| `infra/` | Platform / DevOps | Observability stack, shared infrastructure configs |
| `platform/` | Platform team | Platform specifications (this layer) |
| `rules/` | Platform team | Rule library — `common.md`, `domains/<d>.md`, `traits/<t>.md`, `taxonomy.md` |

---

# Rules

- Do not create top-level directories without updating this document and `CLAUDE.md § Repository Layout`.
- In monorepo shape, service directories MUST live under `projects/<p>/apps/`. Root-level `apps/` is reserved for single-project shape.
- Each service under `<specs-scope>/services/` must have an `architecture.md` declaring its `Service Type`.
- Shared libraries must pass `shared-library-policy.md` before being added to `libs/` or `packages/`.
- A project-scoped shared module belongs under `projects/<p>/libs/<module>/` and is included as `projects:<p>:libs:<module>` — never under `projects/<p>/apps/` (it is not deployable) and never under repo-root `libs/` (it is not project-agnostic).
- Project-specific content (service names, paths, domain entities) MUST NOT appear in the shared library layer — Hard-Stop-enforced (HARDSTOP-03).

---

# Cross-references

- [`../CLAUDE.md`](../CLAUDE.md) § Repository Layout — canonical layout
- [`architecture.md`](architecture.md) § Repository Structure — shape summary
- [`shared-library-policy.md`](shared-library-policy.md) — what may live in shared libraries
- [`../TEMPLATE.md`](../TEMPLATE.md) — template extraction strategy (monorepo → standalone fork)

---

# Change Rule

Changes to the directory layout (adding/removing top-level shared directories, changing the monorepo `projects/<name>/` shape) must be updated here AND in `CLAUDE.md § Repository Layout` in the same PR.
