# wms-platform

[![CI](https://github.com/kanggle/monorepo-lab/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/kanggle/monorepo-lab/actions/workflows/ci.yml?query=branch%3Amain)

> **Warehouse Management System backend** — production-oriented, spec-driven, AI-assisted

Spring Boot 3 microservices implementing the full inbound → inventory → outbound workflow for a warehouse. Built as a portfolio project, but engineered to production standards: event-driven separation, transactional outbox, idempotency keys, circuit breakers, observability, and strict contract-first development.

---

## 📍 Status

| Area | State |
|---|---|
| Project classification | ✅ Declared ([PROJECT.md](PROJECT.md)) |
| master-service v1 specs | ✅ Architecture / domain model / HTTP contract / event contract / idempotency strategy |
| master-service — Warehouse slice | ✅ Hexagonal skeleton · JPA persistence · application layer · HTTP adapter · JWT + method security · Idempotency-Key filter · outbox publisher · Flyway + seed · Dockerfile |
| master-service — Zone slice | ✅ Domain · persistence (compound unique · Flyway V3) · application (parent-warehouse-active guard) · nested HTTP route · outbox wired to `wms.master.zone.v1` · seed V100 |
| master-service — Location slice | ✅ Domain (dual parent · prefix-match invariant) · persistence (global unique · Flyway V4) · split HTTP routing (POST nested / others flat) · outbox wired to `wms.master.location.v1` · seed V101 · **Zone guard turned on (real query)** |
| master-service — SKU slice | ✅ Domain (UPPERCASE normalization) · persistence (global unique code · partial unique barcode · Flyway V5) · 8 endpoints incl. `by-code`/`by-barcode` lookup · outbox wired to `wms.master.sku.v1` · seed V102 · Lot guard stubbed (awaits TASK-BE-006) |
| gateway-service bootstrap | ✅ Spring Cloud Gateway route · JWT validation · rate limit (Redis) · identity header strip · X-Request-Id propagation |
| CI pipeline | ✅ GitHub Actions: `./gradlew check` + boot-jar artifacts on Linux/JDK 21 |
| Next | 🚧 TASK-BE-005 Partner aggregate (independent; SUPPLIER / CUSTOMER / BOTH partnerType) |

---

## 🏛️ Architecture

### Services (7)

| Service | Service Type | Responsibility |
|---|---|---|
| `gateway-service` | `rest-api` | External routing, JWT validation, rate limiting, header enrichment |
| `master-service` | `rest-api` | Master data: warehouses, zones, locations, SKUs, partners, lots |
| `inbound-service` | `rest-api` | ASN management, inspection, putaway |
| `inventory-service` | `rest-api` | Location-based inventory, transfers, adjustments, real-time query |
| `outbound-service` | `rest-api` | Outbound orders, picking, packing, shipping |
| `notification-service` | `event-consumer` | Kafka consumer for operational alerts (Slack, email) |
| `admin-service` | `rest-api` | Dashboards, KPIs, user/permission management |

Each service declares its own internal architecture in `specs/services/<service>/architecture.md`. Master-service uses **Hexagonal (Ports & Adapters)**; inventory/inbound/outbound will also use Hexagonal (integration-heavy nature). Gateway is Layered.

### Bounded Contexts (per `rules/domains/wms.md`)

- **Inbound** — ASN, inspection, putaway
- **Inventory** — location-based stock, transfers, adjustments
- **Outbound** — orders, picking, packing, shipping
- **Master Data** — warehouse, zone, location, SKU, partner, lot
- **Admin / Operations** — dashboards, KPIs, user management

### Traits applied

- **`transactional`** — all mutating paths use `Idempotency-Key`, state machines, optimistic locking, transactional outbox
- **`integration-heavy`** — future ERP / TMS / scanner integrations via dedicated ports, circuit breakers, bulkhead patterns

See [`../../rules/traits/transactional.md`](../../rules/traits/transactional.md) and [`../../rules/traits/integration-heavy.md`](../../rules/traits/integration-heavy.md) for the full rule set.

---

## 🛠️ Tech Stack

- **Language**: Java 21
- **Framework**: Spring Boot 3.4
- **Build**: Gradle 8.14 (multi-project monorepo)
- **Persistence**: PostgreSQL + Flyway (per-service DB; no shared DB)
- **Messaging**: Apache Kafka (transactional outbox pattern)
- **Cache**: Redis (session, idempotency key storage, rate limit counters)
- **Observability**: Micrometer → Prometheus → Grafana, OpenTelemetry → Jaeger, structured JSON logs → Loki
- **Test**: JUnit 5 + Testcontainers (no in-memory substitutes), WireMock for external integrations
- **Local dev**: Docker Compose

---

## 🚀 Getting Started

### Prerequisites

- Java 21 (Temurin recommended)
- Docker (for local stack and Testcontainers)
- From repo root:

### Boot the local stack

```bash
cd projects/wms-platform
cp .env.example .env    # fill in values
docker-compose up -d    # Postgres, Kafka, Redis, Prometheus, Grafana, Loki
```

### Run a service

```bash
# from repo root — master-service on :8081, gateway-service on :8080
./gradlew :projects:wms-platform:apps:master-service:bootRun
./gradlew :projects:wms-platform:apps:gateway-service:bootRun

# or boot the jars produced by CI
./gradlew :projects:wms-platform:apps:master-service:bootJar
java -jar projects/wms-platform/apps/master-service/build/libs/master-service.jar
```

### Run tests

```bash
./gradlew :projects:wms-platform:apps:master-service:test
./gradlew :projects:wms-platform:build    # everything
```

---

## 📁 Directory Structure

```
wms-platform/
├── PROJECT.md              ← domain=wms, traits=[transactional, integration-heavy]
├── README.md               ← this file
├── apps/                   ← 7 service modules (build.gradle per service, added on implementation)
│   ├── gateway-service/
│   ├── master-service/
│   ├── inbound-service/
│   ├── inventory-service/
│   ├── outbound-service/
│   ├── notification-service/
│   └── admin-service/
├── specs/
│   ├── contracts/
│   │   ├── http/           ← REST contracts (currently: master-service-api.md)
│   │   └── events/         ← event schemas (currently: master-events.md)
│   ├── services/
│   │   └── master-service/ ← architecture, domain-model, idempotency
│   ├── features/
│   └── use-cases/
├── tasks/
│   ├── INDEX.md            ← lifecycle rules
│   ├── ready/              ← TASK-BE-001, TASK-INT-001
│   ├── in-progress/, review/, done/, archive/
├── knowledge/
│   └── adr/                ← architecture decision records
├── docs/                   ← project-specific operational docs
├── infra/                  ← Prometheus, Grafana, Loki, Promtail, Alertmanager configs
├── scripts/                ← dev-up, build, migrate, topic creation, e2e scripts
├── docker/                 ← Docker build contexts (DB init, etc.)
├── docker-compose.yml
├── .env.example
└── build.gradle            ← project-level Gradle config (placeholder)
```

---

## 📐 Key Design Decisions

### v1 Entity Scope (Master Data)

6 aggregates: **Warehouse, Zone, Location, SKU, Partner, Lot**. Each aggregate carries common fields (`id`, `*_code`, `name`, `status`, `version`, timestamps, actor ids). Soft deactivation only (no hard deletes in v1). Details: [specs/services/master-service/domain-model.md](specs/services/master-service/domain-model.md).

### Hexagonal Architecture for Write-Heavy Services

Master / inventory / inbound / outbound use Hexagonal to isolate domain logic from infrastructure. Gateway is Layered (no rich domain). Rationale: external integration variety (ERP, TMS, scanners) matches the Ports & Adapters metaphor naturally. Details: [specs/services/master-service/architecture.md](specs/services/master-service/architecture.md).

### Transactional Outbox for Event Publication

Every state change writes an outbox row in the same DB transaction; a separate publisher forwards rows to Kafka. Guarantees exactly-one publish per committed change. At-least-once delivery; consumers must be idempotent keyed by `eventId`.

### Idempotency-Key on All Mutating Endpoints

Client-supplied UUID + method + path scope. Redis-backed storage with 24h TTL. Fail-closed on Redis outage (returns 503). Details: [specs/services/master-service/idempotency.md](specs/services/master-service/idempotency.md).

### Local-Only Referential Integrity (v1)

Master-service checks only its own child records on deactivation (e.g., Zone deactivation blocked while active Locations remain). Cross-service check (inventory references) deferred to v2 via a `deactivation.requested` saga. This is a known v1 simplification.

---

## 🔗 Related Documents

### Repo-shared rules (this project activates these)

- [`../../rules/common.md`](../../rules/common.md) — always-loaded rule index
- [`../../rules/domains/wms.md`](../../rules/domains/wms.md) — WMS domain rules (W1–W6)
- [`../../rules/traits/transactional.md`](../../rules/traits/transactional.md) — T1–T8
- [`../../rules/traits/integration-heavy.md`](../../rules/traits/integration-heavy.md) — I1–I10

### Project-specific specs

- [PROJECT.md](PROJECT.md) — domain/traits declaration, service map, out-of-scope list
- [specs/services/master-service/architecture.md](specs/services/master-service/architecture.md)
- [specs/services/master-service/domain-model.md](specs/services/master-service/domain-model.md)
- [specs/services/master-service/idempotency.md](specs/services/master-service/idempotency.md)
- [specs/contracts/http/master-service-api.md](specs/contracts/http/master-service-api.md)
- [specs/contracts/events/master-events.md](specs/contracts/events/master-events.md)

### Tasks

- [tasks/review/TASK-BE-001-master-service-bootstrap.md](tasks/review/TASK-BE-001-master-service-bootstrap.md) — Warehouse CRUD vertical slice (implementation + tests landed, in review)
- [tasks/review/TASK-INT-001-gateway-master-service-route.md](tasks/review/TASK-INT-001-gateway-master-service-route.md) — gateway route + JWT filter wiring (implementation + filter tests landed, in review)
- [tasks/review/TASK-BE-002-zone-aggregate.md](tasks/review/TASK-BE-002-zone-aggregate.md) — Zone CRUD vertical slice (implementation + tests landed, in review)
- [tasks/review/TASK-BE-003-location-aggregate.md](tasks/review/TASK-BE-003-location-aggregate.md) — Location CRUD + Zone active-children guard turned on (implementation + tests landed, in review)
- [tasks/review/TASK-BE-004-sku-aggregate.md](tasks/review/TASK-BE-004-sku-aggregate.md) — SKU CRUD with case-insensitive code + partial barcode unique + lookup endpoints (implementation + tests landed, in review)
- [tasks/review/TASK-BE-007-master-service-integration-tests.md](tasks/review/TASK-BE-007-master-service-integration-tests.md) — full `@SpringBootTest` suite + contract harness (CI-gated)
- [tasks/review/TASK-INT-002-gateway-master-e2e.md](tasks/review/TASK-INT-002-gateway-master-e2e.md) — live-pair gateway↔master e2e with new CI job (CI-gated)

---

## 🧭 How I Built This

This project is developed with **[Claude Code](https://claude.com/claude-code)** (Anthropic) using a rule-driven workflow:

- Specs, contracts, and task definitions are authored **before** any implementation code.
- A taxonomy-based rule system (`rules/common.md` + `rules/domains/wms.md` + `rules/traits/*.md`) is activated by the project's `PROJECT.md` classification. The AI loads only the relevant rules.
- 80+ reusable skills under `.claude/skills/` guide implementation patterns (Hexagonal structure, outbox pattern, idempotent consumer, testing strategy, etc.).
- Specialized sub-agents (`architect`, `backend-engineer`, `code-reviewer`, `api-designer`, ...) handle distinct phases of work under `.claude/agents/`.
- Every task follows Plan → Implement → Test → Review. Only `tasks/ready/` items may be implemented.

The full framework lives at the [repo root](../../) and is designed to scale to multiple projects. See the [root README](../../README.md) and [TEMPLATE.md](../../TEMPLATE.md) for the "Discovery → Distribution" strategy.

---

## 📄 License

Part of the [monorepo-lab](../../) portfolio. License pending.
