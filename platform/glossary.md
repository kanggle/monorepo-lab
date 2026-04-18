# Glossary

Defines **platform-wide** terms used across specs, tasks, and documentation. This glossary covers terms that apply regardless of domain.

**Domain-specific terms** (e.g., ASN / Picking / Lot for WMS, or Order / Cart / Coupon for ecommerce) are defined in the active domain's `rules/domains/<domain>.md` under "Ubiquitous Language" — not here.

---

# Authentication & Authorization

| Term | Definition |
|---|---|
| **Access Token** | Short-lived JWT issued by the project's auth service (as declared in `PROJECT.md`) on login. Used to authenticate API requests |
| **Refresh Token** | Long-lived token used to obtain a new access token without re-authentication |
| **JWT** | JSON Web Token. A signed token carrying identity claims |
| **Bearer Token** | HTTP authentication scheme: `Authorization: Bearer <token>` |
| **Revoked Token** | A token that has been explicitly invalidated (e.g., via logout, security incident) |
| **Authentication** | Verifying the identity of a caller ("who are you?") |
| **Authorization** | Verifying what an authenticated caller is allowed to do ("what can you do?") |
| **JWKS** | JSON Web Key Set — the public key material used to verify JWT signatures |
| **Identity Propagation** | Gateway sets `X-User-Id`, `X-User-Role` headers on forwarded requests from verified JWT claims |

---

# Architecture Terms

| Term | Definition |
|---|---|
| **Service** | An independently deployable unit with its own database and domain. Declared in `PROJECT.md` with a `Service Type` |
| **Service Type** | One of `rest-api`, `event-consumer`, `batch-job`, `grpc-service`, `graphql-service`, `ml-pipeline`, `frontend-app`. See `service-types/INDEX.md` |
| **Gateway** | The single entry point for all external traffic. Handles authentication, routing, rate limiting, header enrichment |
| **Contract** | A published interface definition (HTTP or event) that producers and consumers must follow. Lives in `specs/contracts/` |
| **Spec** | A specification document defining rules, policies, or behavior. Source of truth |
| **Task** | An execution unit for implementing a defined piece of work. Lifecycle: backlog → ready → in-progress → review → done |
| **ADR** | Architecture Decision Record. Documents significant architecture decisions and their rationale. Lives in `knowledge/adr/` |
| **Aggregate** | A cluster of domain objects treated as a single unit for consistency. One transaction affects one aggregate |
| **Bounded Context** | A boundary within which a domain model is consistent. Typically maps to a service or a small group of services |
| **Layered Architecture** | Internal service structure with layers: presentation → application → domain → infrastructure |
| **DDD (Domain-Driven Design)** | Architecture style centered on complex domain models, aggregates, and bounded contexts. Used when business rules are central |
| **Hexagonal Architecture** | Architecture style (also called Ports and Adapters) that isolates business logic from external systems via port interfaces and adapter implementations |
| **Clean Architecture** | Architecture style that arranges code in concentric dependency-direction layers: entities, use-cases, interface adapters, frameworks |

---

# Event & Messaging Terms

| Term | Definition |
|---|---|
| **Event** | A fact that has already happened, published for consumption by other services. Past-tense named |
| **Event Envelope** | The outer JSON shape with `eventId`, `eventType`, `occurredAt`, `source`, `payload`, etc. See `event-driven-policy.md` |
| **Producer** | The service that emits an event. Owns the event contract |
| **Consumer** | A service that subscribes to and processes an event |
| **Outbox Pattern** | Transactional pattern: state change and event row are written to the same DB in one transaction; a separate process forwards outbox rows to the broker |
| **Idempotent Consumer** | A consumer that produces the same business result even if the same event is delivered multiple times |
| **DLQ (Dead-Letter Queue)** | A Kafka topic holding messages that failed processing after retry. Named `<topic>.dlq` |
| **Partition Key** | The field (typically aggregate id) used by Kafka to decide which partition receives the message. Guarantees per-key ordering |
| **Schema Version** | Integer version of an event's payload schema. Bumped on breaking changes |

---

# Transactional Terms

| Term | Definition |
|---|---|
| **Idempotency Key** | Client-supplied unique identifier on a mutating HTTP request so that retries produce the same result |
| **Optimistic Locking** | Concurrency control using a `version` column; conflicts surface as `CONFLICT` errors |
| **Saga** | A long-running, multi-step process across services coordinated by events, with compensating actions for each step |
| **Compensating Action** | A rollback operation that semantically undoes a Saga step when a later step fails |

---

# Infrastructure & Tooling Terms

| Term | Definition |
|---|---|
| **Testcontainers** | Java library that spins up real Docker containers (PostgreSQL, Redis, Kafka) during integration tests |
| **Flyway** | Database migration tool. Applies versioned SQL scripts (`V{n}__description.sql`) in order |
| **Redis** | In-memory key-value store. Commonly used for session cache, rate limiting, idempotency storage |
| **Kafka** | Distributed event streaming platform. Default async messaging substrate |
| **Monorepo** | Single repository containing multiple services and/or libraries. Shared tooling at the root, per-project subdirectories |
| **Gradle Multi-Project** | Gradle build structure where a root project includes multiple subprojects via `settings.gradle` |
| **OTel (OpenTelemetry)** | Vendor-neutral observability framework for traces, metrics, and logs |
| **Micrometer** | JVM metrics facade that integrates with Prometheus, Grafana, and others |
| **JWKS** | JSON Web Key Set — endpoint exposing public keys for JWT signature validation |

---

# Task Lifecycle Terms

| Term | Definition |
|---|---|
| **backlog** | Task is defined but not yet ready for implementation |
| **ready** | Task is fully specified and ready to be implemented |
| **in-progress** | Implementation is actively underway |
| **review** | Implementation is complete and awaiting review |
| **done** | Review approved and task is complete |
| **archive** | Task is done and no further changes are expected |

---

# Project-Level Terms

| Term | Definition |
|---|---|
| **Project** | A bounded product or portfolio slice declared by a `PROJECT.md` file. Has one `domain` and zero-or-more `traits` |
| **Domain** | The top-level industry / product area of a project (`wms`, `ecommerce`, `fintech`, ...). See `rules/taxonomy.md` |
| **Trait** | A cross-cutting architectural characteristic of a project (`transactional`, `integration-heavy`, `real-time`, ...). A project declares zero or more |
| **Service Map** | The list of services that make up a project, declared in `PROJECT.md` |
| **Rules Library** | The `rules/` directory — a catalog of domain and trait rule files accumulated across projects and reused |

---

# Change Rule

Changes to glossary terms must be documented here before applying across specs. If a term is domain-specific, it belongs in `rules/domains/<domain>.md` — not this file.
