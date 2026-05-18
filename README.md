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

| Project | Domain | Tech | Status | Standalone repo |
|---|---|---|---|---|
| [wms-platform](projects/wms-platform/) | Warehouse Management | Java 21 · Spring Boot 3.4 · Postgres · Kafka · Redis · Hexagonal | ✅ **v1 complete — 5 services**: master (5 aggregates + Lot expiry) · inventory (W4/W5 reservations · 4 outbound-saga consumers · low-stock alerts) · inbound (ASN / inspection / putaway) · outbound (order / picking / packing / shipping) · gateway (OIDC + tenant gate via TASK-MONO-019). admin / notification: bootstrap pending. | [kanggle/wms-platform](https://github.com/kanggle/wms-platform) |
| [ecommerce-microservices-platform](projects/ecommerce-microservices-platform/) | E-commerce | Java 21 · Spring Boot 3.4 · Next.js 15 · React 19 · Postgres · Kafka · Redis · Elasticsearch · MinIO | ✅ End-to-end full-stack: 12 backend microservices + Next.js storefront + admin dashboard. Saga orchestration, outbox, JWT, ES product search, MinIO uploads, Playwright E2E. GAP IdP migration pending (TASK-MONO-020 future). | [kanggle/ecommerce-microservices-platform](https://github.com/kanggle/ecommerce-microservices-platform) |
| [global-account-platform](projects/global-account-platform/) | SaaS / Identity | Java 21 · Spring Boot 3.4 · Spring Authorization Server 1.x · Postgres · Kafka · Redis | ✅ **OIDC IdP for the monorepo** (backend-only — operator UI absorbed by the unified platform console; `admin-web` retired 2026-05-18 per [ADR-MONO-013](docs/adr/ADR-MONO-013-platform-console-foundation.md) Phase 3): 5 backend services (auth / account / admin / gateway / security). Standard `/oauth2/{authorize,token,jwks,userinfo,revoke,introspect}` + `/.well-known/openid-configuration`. Multi-tenant `tenant_id` row-level isolation. Bulk provisioning `POST /internal/tenants/{id}/accounts:bulk`. **OIDC AS 운영 깊이 증명 (2026-05-09)**: SAS 기반 public-client (PKCE) `refresh_token` rotation + `revoke` (custom converter + provider-side fallback) + 3 OAuth provider callback (Google/Kakao/Microsoft) 모두 main CI 에서 deterministic PASS — 13-cycle 미해결 9 deferred IT 회복 ([ADR-003](projects/global-account-platform/docs/adr/ADR-003-public-client-refresh-token-revoke-converter.md), [ADR-004](projects/global-account-platform/docs/adr/ADR-004-oauth-callback-ci-linux-503-isolation.md)). Currently consumed by wms-platform; ecommerce + fan-platform pending. | _(monorepo-only)_ |
| [fan-platform](projects/fan-platform/) | Fan Community (K-pop) | Java 21 · Spring Boot 3.4 · Next.js 15 · Postgres · Kafka · Redis | 🚧 **Bootstrapping** — gateway-service first ([TASK-FAN-BE-001](projects/fan-platform/tasks/ready/)). v1 = gateway + community + artist + lean Next.js frontend. GAP OIDC consumer + Traefik hostname routing from day 1. | _(planned)_ |

Each project is extracted to its own standalone repo via [`scripts/sync-portfolio.sh`](scripts/sync-portfolio.sh) for easier discovery. This monorepo retains the full development history and shared library development.

_6th project — finance-platform (fintech domain — account-service v1; `transactional + regulated + audit-heavy`) — was bootstrapped 2026-05-18 as the first downstream Template fork per [ADR-MONO-008](docs/adr/ADR-MONO-008-finance-platform-bootstrap.md) (ACCEPTED, Option C: standalone Template fork + monorepo direct-include). scm-platform is operational; erp-platform / mes-platform remain planned — taxonomy entries already in [`rules/taxonomy.md`](rules/taxonomy.md), bootstrap pending to validate the Template extraction triggers._

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

> **Full process walk-through**: [docs/guides/development-process.md](docs/guides/development-process.md)
> — rule layers · spec-first · task lifecycle · `/process-tasks` pipeline · review discipline · concrete artifacts.

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
- Node.js 20+ and pnpm (for monorepo root scripts and frontend projects)

### Clone and verify the build tree

```bash
git clone <this-repo-url>
cd monorepo-lab
./gradlew projects          # show multi-project tree
./gradlew build             # build everything
```

### One-time local dev environment setup

The monorepo uses **hostname-based routing** via a shared Traefik reverse proxy ([ADR-MONO-001](docs/adr/ADR-MONO-001-port-prefix-scaling.md)). One-time setup:

```bash
# 1. Register *.local hostnames in your hosts file
bash scripts/dev-setup.sh                        # Linux / macOS (uses sudo)
# or:
.\scripts\dev-setup.ps1                          # Windows (Run as Administrator)

# 2. Start the shared Traefik proxy
pnpm traefik:up
```

After this, projects expose themselves on hostnames like `http://wms.local/` and `http://gap.local/` instead of `localhost:<port>`. See [infra/traefik/README.md](infra/traefik/README.md) for details.

> **Note**: Existing projects (ecommerce / wms / GAP) currently still use the legacy `PORT_PREFIX` scheme until [TASK-MONO-024](tasks/ready/TASK-MONO-024-existing-projects-traefik-migration.md) migrates them. Newly bootstrapped projects adopt the hostname pattern from day one.

### Work on a specific project

```bash
cd projects/wms-platform
# or from root:
./gradlew :projects:wms-platform:apps:master-service:build
```

Each project's `README.md` (e.g., [projects/wms-platform/README.md](projects/wms-platform/README.md)) contains project-specific quickstart, architecture diagrams, and service lists.

### Direct DB / queue tool access (DBeaver, Redis Insight, Kafka UI)

Backing services intentionally don't publish host ports (production parity). See [docs/guides/dev-tooling.md](docs/guides/dev-tooling.md) for three approaches: `docker exec`, per-developer overlay, or Traefik TCP routing.

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

- Domains: `wms`, `ecommerce`, `saas`, `fan-platform`, `scm`, `fintech`
- Traits: `transactional`, `integration-heavy`, `read-heavy`, `content-heavy`, `regulated`, `audit-heavy`, `multi-tenant`, `batch-heavy`

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
| [docs/project-overview.md](docs/project-overview.md) | 5 projects + shared library + ADR catalog navigation snapshot |
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
