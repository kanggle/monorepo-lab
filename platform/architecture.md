# Platform Architecture

This document defines the overall system structure that every project in this repository inherits by default. It describes structural principles, not specific service instances. Project-specific service maps are declared in each project's `PROJECT.md` and `specs/services/`.

All implementation tasks must read this document before proceeding.

---

# System Style

Microservices architecture deployed on Kubernetes.

Services communicate via:

- **HTTP** — synchronous request/response, internal and external
- **Events** — asynchronous via a shared messaging infrastructure (Kafka by default)

All external HTTP traffic enters through a single API gateway. No backend service is directly exposed to external clients.

---

# Services (Declaration Rule)

This platform does not prescribe a fixed service catalog. Each project declares its own service map and each service declares its own architecture.

Declaration locations:

- `PROJECT.md` (project root) — high-level service map for the project (name, service type, responsibility)
- `specs/services/<service>/architecture.md` — per-service internal architecture (see `architecture-decision-rule.md`)

Allowed internal architecture styles for a service:

- **Layered** — simple CRUD, thin domain, small surface area
- **Hexagonal (Ports & Adapters)** — external integration surface, clear domain isolation
- **Clean** — complex domain with multiple output formats (REST + gRPC + events)
- **DDD** — rich aggregate behavior, strong invariants, multiple bounded contexts per service

The style is declared explicitly in `specs/services/<service>/architecture.md`. Do not assume all services in a project share the same internal structure.

See `service-types/INDEX.md` for the service-type catalog (`rest-api`, `event-consumer`, `batch-job`, `grpc-service`, `graphql-service`, `ml-pipeline`, `frontend-app`, `identity-platform`).

---

# Frontend Applications

Frontend applications are declared in the project's `PROJECT.md` service map just like backend services, with `Service Type = frontend-app`.

Each frontend application declares its own internal architecture in `specs/services/<app>/architecture.md`, typically one of:

- **Feature-Sliced Design (FSD)**
- **Layered by Feature**
- Other patterns declared and justified in the service spec

Default technology baseline for frontend applications: Next.js (App Router) + TypeScript strict. Overrides require a recorded decision in the service spec.

---

# Communication Rules

## Synchronous (HTTP)

- External clients communicate through the gateway only.
- Internal service-to-service HTTP calls must be declared in `specs/contracts/http/<service>-api.md`.
- Direct unmediated HTTP calls outside declared contracts are forbidden.
- Circular synchronous dependencies are forbidden.

## Asynchronous (Events)

- Services publish and consume events through the shared messaging infrastructure.
- Event contracts must be declared in `specs/contracts/events/` before use.
- See `event-driven-policy.md` for event design rules (envelope, versioning, outbox, idempotent consumer, DLQ).

For the full dependency direction diagram and forbidden dependency rules, see `dependency-rules.md`.

---

# Shared Libraries

Shared libraries provide **technical reuse only** — never domain logic. See `shared-library-policy.md` for the decision rule ("would an unrelated project also need this?").

Default scaffolding (concrete libraries exist if the project chose to include them):

## Java (`libs/`)

| Library | Purpose |
|---|---|
| `java-common` | Common utilities (ids, time, errors, functional helpers) |
| `java-web` | Web configuration helpers (filters, exception handlers, DTO conventions) |
| `java-messaging` | Messaging abstractions (outbox, idempotent consumer, DLQ support) |
| `java-observability` | Tracing, metrics, logging helpers (OTel, Micrometer, MDC) |
| `java-security` | Security helpers (JWT validation, role extraction) |
| `java-test-support` | Test utilities (fixtures, Testcontainers wiring) |

## TypeScript (`packages/`, when frontend shared packages are present)

Root-level `packages/` may host TypeScript libraries shared across multiple frontend apps. Typical candidates (only added when ≥ 2 frontend apps need them, per `shared-library-policy.md`):

- `api-client` — shared HTTP client
- `types` — cross-app TypeScript types
- `ui` — shared component primitives (a11y, design tokens)
- `utils` — common utility functions
- `eslint-config` / `tsconfig` — shared lint/compile config

This catalog is illustrative — actual `packages/*` content is project-history-dependent. Frontend apps that don't need shared packages keep their dependencies internal to `<app>/`.

Shared library placement rules: `shared-library-policy.md`

---

# Repository Structure

This repository may be used either as a single-project repository or as a multi-project monorepo. **Canonical layout** is defined in [`../CLAUDE.md`](../CLAUDE.md) § Repository Layout (single source of truth); per-directory ownership notes live in [`repository-structure.md`](repository-structure.md).

Key invariant for both shapes — the **shared library layer** (`platform/`, `rules/`, `.claude/`, `libs/`, `tasks/templates/`, `docs/guides/`, `CLAUDE.md`, `TEMPLATE.md`) is project-agnostic and must not contain project-specific content (service names, API paths, domain entities). Hard-Stop-enforced (HARDSTOP-03). In monorepo shape, project-specific content lives under `projects/<name>/` (`PROJECT.md`, `apps/`, `specs/`, `tasks/`, `knowledge/`, `docs/` non-guides, `infra/`).

---

# Deployment

- Each service is containerized with Docker.
- Deployed to Kubernetes.
- Infrastructure managed with Terraform.
- CI/CD via GitHub Actions (in monorepo mode, path-filtered workflows per project).

Deployment rules: `deployment-policy.md`

---

# Key Constraints

- Services must not share databases. Each service owns its data store.
- Services must not import each other's internal code.
- Shared logic belongs in `libs/` or `packages/` only if it passes the shared-library policy check.
- All API and event changes require contract updates before implementation.
- Each service's architecture is fixed by its own `architecture.md` and must not be changed without updating that document first.
- Project-specific content (service names, paths, domain entities) does not belong in the platform layer or the rules library — it belongs in `PROJECT.md` and `specs/`.

---

# How to Read This Document for Implementation

1. Read this document for structural baseline.
2. Read `architecture-decision-rule.md` for the rule about declaring service-level architecture.
3. Read the target project's `PROJECT.md` to see the declared service map.
4. Read the target service's `specs/services/<service>/architecture.md` for its internal architecture.
5. Read the matching `service-types/<type>.md` (exactly one).
6. Follow `entrypoint.md` for the full spec-reading order.

---

# Change Rule

Changes to the platform-wide architecture baseline — adding or removing a service tier, altering the canonical HTTP/Events interaction pattern, redefining the boundary between gateway and downstream services — must be documented in this file before service-level adoption. Project-level architecture deviations are governed by each project's `PROJECT.md` `## Overrides` section per [`architecture-decision-rule.md`](architecture-decision-rule.md).
