# Projects

Each directory here is an independent project within the `monorepo-lab` dev workspace. A project owns its own services, specs, tasks, and knowledge — while drawing on the shared library at the repo root (`platform/`, `rules/`, `.claude/`, `libs/`, `tasks/templates/`, `docs/guides/`).

---

## Active Projects

| Project | Domain | Traits | Status | Description |
|---|---|---|---|---|
| [wms-platform](wms-platform/) | `wms` | `transactional`, `integration-heavy` | 🚧 Active | Warehouse Management System. 7 services (gateway, master, inbound, inventory, outbound, notification, admin). Master-service v1 specs complete; Warehouse CRUD implementation next. |

---

## Adding a New Project

See [TEMPLATE.md at the repo root](../TEMPLATE.md) (section "Starting a New Project in the Monorepo") for the step-by-step procedure:

1. Create directory structure under `projects/<new-project>/`.
2. Write `PROJECT.md` (declare domain/traits/service_types).
3. Write `tasks/INDEX.md` (task lifecycle).
4. Update root `settings.gradle` with new Gradle include paths.
5. Create project-level `build.gradle` placeholder.
6. Write the first `tasks/ready/TASK-BE-001-*.md`.
7. Verify with `./gradlew projects`.

**Before starting a new project**, verify that the library layer (`rules/`, `platform/`) covers the new domain/traits. If a new domain or trait is declared that has no rule file yet, write it in the same PR per the On-Demand Policy (see [rules/README.md](../rules/README.md)).

---

## Project Directory Structure

Each project follows the same internal layout:

```
<project-name>/
├── PROJECT.md              ← domain + traits declaration
├── README.md               ← project-specific intro
├── apps/                   ← service implementations (Spring Boot modules)
├── specs/
│   ├── contracts/
│   │   ├── http/           ← HTTP API contracts
│   │   └── events/         ← event schema contracts
│   ├── services/           ← per-service architecture + specs
│   ├── features/
│   └── use-cases/
├── tasks/
│   ├── INDEX.md            ← task lifecycle definition
│   ├── ready/              ← tasks ready to implement
│   ├── in-progress/
│   ├── review/
│   ├── done/
│   └── archive/
├── knowledge/              ← design references, ADRs
├── docs/                   ← project-specific docs (not guides)
├── infra/                  ← project-specific Prometheus/Grafana/Loki configs
├── scripts/                ← project-specific scripts (topic creation, migrations, e2e)
├── docker-compose.yml      ← local dev stack
├── .env.example            ← local env var template
└── build.gradle            ← project-level Gradle config
```

Only `apps/`, `specs/`, `tasks/`, `knowledge/` are mandatory. The others are optional per project needs.

---

## Relationship to the Shared Library

Each project **consumes** the shared library but does not modify it casually. Modifications to `platform/`, `rules/`, or `.claude/` should:

- Be justified by **actual reuse across multiple projects** (not a single project's convenience).
- Keep the shared content **project-agnostic** (no specific service names, API paths, or domain entities).
- Propagate to all consuming projects **in the same PR** — atomic cross-project commits.

See [CLAUDE.md](../CLAUDE.md) § "Cross-Project Changes" for the workflow.
