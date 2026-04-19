# monorepo-lab

[![CI](https://github.com/kanggle/monorepo-lab/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/kanggle/monorepo-lab/actions/workflows/ci.yml?query=branch%3Amain)
[![Java 21](https://img.shields.io/badge/java-21-007396)](https://adoptium.net/)
[![Spring Boot 3.4](https://img.shields.io/badge/spring--boot-3.4-6DB33F)](https://spring.io/projects/spring-boot)
[![Built with Claude Code](https://img.shields.io/badge/built%20with-Claude%20Code-D97757)](https://claude.com/claude-code)

> **AI-assisted multi-domain backend/fullstack portfolio**
> Built with Claude Code · rule-driven · spec-driven · task-driven

A monorepo for developing multiple domain projects side-by-side, accumulating a reusable library of rules, skills, and platform regulations along the way. Following a **"Discovery → Distribution"** strategy: the library matures here across several projects, then is extracted into a standalone template repository for future projects.

---

## 🎯 Projects

| Project | Domain | Tech | Status | Links |
|---|---|---|---|---|
| [wms-platform](projects/wms-platform/) | Warehouse Management | Java 21 · Spring Boot 3 · Kafka · Redis · Hexagonal | 🚧 Active — master-service Warehouse slice + gateway-service bootstrapped, CI green | [README](projects/wms-platform/README.md) · [PROJECT.md](projects/wms-platform/PROJECT.md) |

_Future projects (planned): additional domains to stress-test the shared library and validate the Template extraction._

---

## 🏗️ Repository Layout

```
monorepo-lab/
├── .claude/                  AI agent configuration (skills, agents, commands, config)
├── platform/                 Platform regulations (architecture, error handling, testing, ...)
├── rules/                    Rule library (common, domains/, traits/)
├── libs/                     Shared Java libraries (domain-neutral)
├── tasks/templates/          Backend/frontend/integration task templates
├── docs/guides/              Human-oriented workflow guides
├── CLAUDE.md                 AI operating rules
├── TEMPLATE.md               Template extraction strategy
├── build.gradle, settings.gradle, gradle/
└── projects/
    └── <project-name>/       One directory per project
        ├── PROJECT.md        Domain + traits declaration
        ├── apps/             Service implementations
        ├── specs/            Project-specific specs and contracts
        ├── tasks/            Task lifecycle for this project
        ├── knowledge/, docs/, infra/
        └── build.gradle
```

**Shared vs project content** is a strict boundary:

- **Shared (at repo root)**: `platform/`, `rules/`, `.claude/`, `libs/`, `tasks/templates/`, `docs/guides/` must remain project-agnostic — no project-specific service names, API paths, or domain entities.
- **Project-specific (under `projects/<name>/`)**: free to be domain-specific.

Violating this boundary blocks future Template extraction. See [TEMPLATE.md](TEMPLATE.md) for the full strategy and [CLAUDE.md](CLAUDE.md) for the enforcement rules.

---

## 🤖 AI Collaboration Approach

This repository is optimized for **rule-driven AI-assisted development** with Claude Code:

- **[PROJECT.md](projects/wms-platform/PROJECT.md)** per project declares `domain` and `traits` — the AI loads the matching rule layers automatically.
- **[CLAUDE.md](CLAUDE.md)** (root) defines the minimum operating rules: Hard Stop conditions, source-of-truth priority, task workflow.
- **[rules/](rules/)** contains accumulated domain/trait rule libraries. Each project activates a subset via its `PROJECT.md` classification.
- **[.claude/skills/](.claude/skills/)** holds implementation patterns (80+ skills) the AI pulls into context based on the task at hand.
- **[.claude/agents/](.claude/agents/)** defines specialized sub-agents (architect, backend-engineer, code-reviewer, ...) invoked for distinct phases of work.
- **[tasks/](projects/wms-platform/tasks/)** enforces a Plan → Implement → Test → Review lifecycle. Only `tasks/ready/` items may be implemented.

The AI treats specs as the source of truth. If specs are missing or conflicting, work stops and the issue is reported — no workaround implementations.

---

## 🛠️ Getting Started

### Prerequisites

- Java 21 (Temurin recommended)
- Docker (for Postgres/Kafka/Redis via Testcontainers and local `docker-compose`)
- Gradle 8.14+ (wrapper included — no global install needed)

### Clone and verify the build tree

```bash
git clone <this-repo-url>
cd monorepo-lab
./gradlew projects          # show multi-project tree
./gradlew build             # build everything
```

### Work on a specific project

```bash
cd projects/wms-platform
# or from root:
./gradlew :projects:wms-platform:apps:master-service:build
```

Each project's `README.md` (e.g., [projects/wms-platform/README.md](projects/wms-platform/README.md)) contains project-specific quickstart, architecture diagrams, and service lists.

---

## 📦 Rule System

Rules are assembled per project based on `PROJECT.md`:

```
Common (always loaded)
  ↓
Domain (the project's declared domain — e.g., wms)
  ↓
Traits (each declared trait — e.g., transactional, integration-heavy)
```

See [rules/README.md](rules/README.md) for the full taxonomy and on-demand policy.

**Currently defined in the library:**

- Domains: `wms`
- Traits: `transactional`, `integration-heavy`

Additional domains/traits get rule files when a new project declares them.

---

## 🧪 Testing

Each project's testing strategy follows the platform-wide baseline in [platform/testing-strategy.md](platform/testing-strategy.md). Integration tests use **Testcontainers** (no in-memory substitutes for persistence).

---

## 📐 Engineering Philosophy

- **Specs are the source of truth.** Implementation that diverges from specs is a bug.
- **Contracts precede code.** HTTP and event contracts are written and reviewed before the implementing service starts.
- **Atomic cross-project commits.** Library refactors + project adaptations go in the same PR — the monorepo's biggest operational advantage.
- **Production-grade patterns throughout.** Hexagonal architecture, transactional outbox, idempotency keys, circuit breakers, DLQ, idempotent consumers — applied consistently per declared traits.
- **Portfolio-focused but production-oriented.** Each project is a realistic backend system, not a tutorial simplification.

---

## 🗺️ Phases

| Phase | State | Goal |
|---|---|---|
| 1. Single Project | ✅ Completed | Establish WMS project; prove rule/skill/agent system |
| 2. Second Project | 🔜 Next | Add a second domain (e.g., ecommerce or fintech) to validate library generality |
| 3. Third Project | 🔜 Planned | Apply Rule of Three — filter true generalizations |
| 4. Template Extraction | 🔜 Planned | Extract the library into a standalone Template Repository once stable |
| 5. Ongoing Sync | 🔜 Future | Periodically sync library improvements from this monorepo to the template |

Full strategy and rationale: [TEMPLATE.md](TEMPLATE.md).

---

## 📚 Key Documents

| Document | Purpose |
|---|---|
| [CLAUDE.md](CLAUDE.md) | AI operating rules (read first before any work) |
| [TEMPLATE.md](TEMPLATE.md) | Discovery → Distribution strategy |
| [platform/entrypoint.md](platform/entrypoint.md) | Spec reading order |
| [platform/architecture.md](platform/architecture.md) | System architecture baseline |
| [rules/README.md](rules/README.md) | Rule library architecture |
| [platform/service-types/INDEX.md](platform/service-types/INDEX.md) | Service type catalog |
| [.github/workflows/README.md](.github/workflows/README.md) | CI pipeline (build-and-test + boot-jars jobs) |
| [docs/guides/](docs/guides/) | Human workflow guides (conventional commits, monorepo workflow — authored progressively) |

---

## 📄 License

_License declaration pending — this repository is currently private / portfolio-use-only._
