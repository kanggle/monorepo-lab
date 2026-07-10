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

**7 domain projects + 1 horizontal console.** Service counts track [`settings.gradle`](settings.gradle), the only inventory the build reads — [`scripts/check-service-map-drift.sh`](scripts/check-service-map-drift.sh) fails CI when the detailed service maps in [`docs/project-overview.md`](docs/project-overview.md) drift from it.

| Project | Domain | Tech | Status | Standalone repo |
|---|---|---|---|---|
| [wms-platform](projects/wms-platform/) | Warehouse Management | Java 21 · Spring Boot 3.4 · Postgres · Kafka · Redis · Hexagonal | ✅ **v1 complete — 7 services**: master (5 aggregates + Lot expiry) · inventory (W4/W5 reservations · 4 outbound-saga consumers · low-stock alerts) · inbound (ASN / inspection / putaway) · outbound (order / picking / packing / shipping + saga orchestrator) · notification (6-topic Kafka → Slack) · admin (CQRS read-side + dashboard) · gateway (OIDC + tenant gate). Fulfils ecommerce orders per [ADR-MONO-022](docs/adr/ADR-MONO-022-ecommerce-wms-fulfillment-integration.md). | [kanggle/wms-platform](https://github.com/kanggle/wms-platform) |
| [ecommerce-microservices-platform](projects/ecommerce-microservices-platform/) | E-commerce | Java 21 · Spring Boot 3.4 · Next.js 15 · React 19 · Postgres · Kafka · Redis · Elasticsearch · MinIO | ✅ End-to-end full-stack: **12 backend microservices** + Next.js storefront. Saga orchestration, outbox, ES product search, MinIO uploads, seller settlement, Playwright E2E. **IAM IdP migration complete** — the in-house `auth-service` was decommissioned by TASK-BE-132. Operator UI absorbed into platform-console. | [kanggle/ecommerce-microservices-platform](https://github.com/kanggle/ecommerce-microservices-platform) |
| [iam-platform](projects/iam-platform/) | SaaS / Identity | Java 21 · Spring Boot 3.4 · Spring Authorization Server 1.x · Postgres · Kafka · Redis | ✅ **OIDC IdP for the monorepo** (backend-only — operator UI absorbed by the unified platform console; `admin-web` retired 2026-05-18 per [ADR-MONO-013](docs/adr/ADR-MONO-013-platform-console-foundation.md) Phase 3): 5 backend services (auth / account / admin / gateway / security). Standard `/oauth2/{authorize,token,jwks,userinfo,revoke,introspect}` + `/.well-known/openid-configuration`. Multi-tenant `tenant_id` row-level isolation, assume-tenant RFC 8693 token exchange, `client_credentials` workload identity. Bulk provisioning `POST /internal/tenants/{id}/accounts:bulk`. **OIDC AS 운영 깊이 증명 (2026-05-09)**: SAS 기반 public-client (PKCE) `refresh_token` rotation + `revoke` (custom converter + provider-side fallback) + 3 OAuth provider callback (Google/Kakao/Microsoft) 모두 main CI 에서 deterministic PASS — 13-cycle 미해결 9 deferred IT 회복 ([ADR-003](projects/iam-platform/docs/adr/ADR-003-public-client-refresh-token-revoke-converter.md), [ADR-004](projects/iam-platform/docs/adr/ADR-004-oauth-callback-ci-linux-503-isolation.md)). **Consumed by every project.** | _(monorepo-only)_ |
| [scm-platform](projects/scm-platform/) | Supply Chain | Java 21 · Spring Boot 3.4 · Postgres · Kafka | ✅ **v1.1 — 4 services**: procurement (PO lifecycle, supplier ack, ASN intake) · inventory-visibility (cross-node read-model over wms snapshots) · demand-planning (wms low-stock alert consumer + nightly sweep → replenishment suggestions) · gateway. | [kanggle/scm-platform](https://github.com/kanggle/scm-platform) |
| [fan-platform](projects/fan-platform/) | Fan Community (K-pop) | Java 21 · Spring Boot 3.4 · Next.js 15 · Postgres · Kafka · Redis | ✅ **v1.1 — 5 backend + web**: community (post / comment / reaction / follow feed) · artist (profile + fandom) · membership (subscription state machine + PG mock + outbox) · notification (membership events → per-fan inbox) · gateway · lean Next.js frontend. | [kanggle/fan-platform](https://github.com/kanggle/fan-platform) |
| [finance-platform](projects/finance-platform/) | Fintech | Java 21 · Spring Boot 3.4 · Postgres · Kafka | ✅ **v1.x — 2 services**: account (KYC · available/ledger balance hold·release·capture · idempotent fund movement · immutable audit log) · ledger (double-entry general ledger — trial balance, periods, reconciliation). **No gateway module** — Traefik routes straight to the services. Rendered by platform-console. | [kanggle/finance-platform](https://github.com/kanggle/finance-platform) |
| [erp-platform](projects/erp-platform/) | ERP (internal system) | Java 21 · Spring Boot 3.4 · Postgres · Kafka | ✅ **v1.x — 4 services**: masterdata (org master data + org_scope subtree data-scope) · approval (multi-stage approval lines, proxy approval, delegation) · read-model (employee org-view + approval-fact projections) · notification (in-app approval inbox). **No gateway module.** Rendered by platform-console. | [kanggle/erp-platform](https://github.com/kanggle/erp-platform) |
| [platform-console](projects/platform-console/) | SaaS (horizontal) | Next.js 15 · React 19 · Java 21 · Spring Boot 3.4 | ✅ **Phase 7 LIVE — 6/6 federated domains**: `console-web` (the single operator UI — tenant switcher, per-domain ops screens, approval inbox, notification bell) + `console-bff` (cross-domain aggregation — operator overview, domain health). Model B: the console is the *only* frontend for wms / scm / finance / erp. | _(monorepo-only)_ |

Each project is extracted to its own standalone repo via [`scripts/sync-portfolio.sh`](scripts/sync-portfolio.sh) for easier discovery. This monorepo retains the full development history and shared library development.

_erp is the portfolio's **final** domain (ADR-MONO-002 § D4 ordering `scm → finance → erp`), bootstrapped 2026-05-19 per [ADR-MONO-016](docs/adr/ADR-MONO-016-erp-platform-bootstrap.md) as the second downstream Template fork, after finance-platform on 2026-05-18 per [ADR-MONO-008](docs/adr/ADR-MONO-008-finance-platform-bootstrap.md). mes-platform remains intentionally dropped — no further bootstrap ADR is planned._

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

After this, projects expose themselves on hostnames like `http://wms.local/` and `http://iam.local/` instead of `localhost:<port>`. See [infra/traefik/README.md](infra/traefik/README.md) for details.

> **Note**: All projects now use hostname-based routing — the legacy `PORT_PREFIX` scheme was fully retired by [TASK-MONO-024](tasks/done/TASK-MONO-024-existing-projects-traefik-migration.md).

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

- Domains: `wms`, `ecommerce`, `saas`, `fan-platform`, `scm`, `fintech`, `erp`
- Traits: `transactional`, `integration-heavy`, `read-heavy`, `content-heavy`, `regulated`, `audit-heavy`, `multi-tenant`, `batch-heavy`, `internal-system`

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
| 1. Single project | ✅ Completed | Establish the first project; prove the rule/skill/agent system |
| 2. Second project | ✅ Completed | Add a second domain to validate library generality |
| 3. Third project — Rule of Three | ✅ Completed | Filter true generalizations from coincidences |
| 4. Catalyst | ✅ Completed | Five projects cohabiting; library churn stabilised |
| 5. Template extraction | ✅ **Launched 2026-05-13** | [`kanggle/project-template`](https://github.com/kanggle/project-template) public, `is_template: true` ([ADR-MONO-003b](docs/adr/ADR-MONO-003b-phase-5-launch-criteria.md)) |
| 6. New-domain bootstrap | ✅ **Complete 2026-05-19/20** | First two downstream Template forks confirmed ([ADR-MONO-008](docs/adr/ADR-MONO-008-finance-platform-bootstrap.md) / [ADR-MONO-016](docs/adr/ADR-MONO-016-erp-platform-bootstrap.md)) |
| 7. Console federation | ✅ **Live 2026-05-20** | `console-bff` + cross-domain dashboards ([ADR-MONO-017](docs/adr/ADR-MONO-017-platform-console-bff-architecture.md)) |
| 8. Federation hardening | ✅ **Complete 2026-05-28** | Cross-product E2E + observability federation + multi-tenant isolation regression ([ADR-MONO-018](docs/adr/ADR-MONO-018-platform-console-phase-8-federation-hardening.md)) |
| 9. Ongoing sync | 🔮 Future | Periodically sync library improvements from this monorepo to the template |

Full strategy and rationale: [TEMPLATE.md](TEMPLATE.md). Detailed phase history: [docs/project-overview.md § 9](docs/project-overview.md).

---

## 📚 Key Documents

| Document | Purpose |
|---|---|
| [CLAUDE.md](CLAUDE.md) | AI operating rules (read first before any work) |
| [docs/project-overview.md](docs/project-overview.md) | 8 projects + shared library + ADR catalog navigation snapshot |
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
