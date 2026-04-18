# Service Boundaries

Defines ownership rules and cross-service interaction constraints that every project inherits. This document states **principles and constraints**; concrete ownership tables and dependency matrices live in each project's `PROJECT.md` and `specs/services/`.

---

# Ownership Principle

See `ownership-rule.md` for the authoritative service, contract, and shared library ownership principles.

This document adds the concrete boundary constraints on top of those principles:

- No service may read or write another service's database directly.
- Cross-service data access must go through published contracts only (`specs/contracts/http/` or `specs/contracts/events/`).
- Each service owns its own persistent state. Shared persistence is forbidden.

---

# Service Boundary Constraints (Generic)

The following rules apply to every service regardless of domain. Each rule describes what a service **must not** do or own. Concrete per-service boundaries for a given project are declared in that project's `specs/services/<service>/architecture.md` and `PROJECT.md`.

## Every service must NOT

- Own state that another service's responsibility contract implies
- Call another service's non-public (undeclared) endpoint
- Replicate authentication or authorization logic that a dedicated auth service already provides
- Mix unrelated bounded contexts (split instead — see `architecture.md` on service-size)
- Hold direct database credentials for another service's store
- Bypass the API gateway for externally-originated traffic

## Service Type Boundaries

Additional constraints tied to `Service Type` declared in `specs/services/<service>/architecture.md`:

| Service Type | Must NOT |
|---|---|
| `gateway` (API gateway) | Contain business logic; persist domain data; own aggregate state |
| `rest-api` | Expose non-public internal endpoints to external traffic; bypass contract definitions |
| `event-consumer` | Publish events that belong to another service's aggregate; skip idempotency |
| `batch-job` | Own primary domain state; call non-public endpoints; mutate without an audit trail |
| `grpc-service` | Expose the same endpoint via both gRPC and REST without documenting the bridge |
| `graphql-service` | Own primary domain state; persist directly into source services' stores |
| `frontend-app` | Hold service-to-service secrets client-side; accept identity headers from clients |
| `ml-pipeline` | Persist predictions in a source service's store; invoke training from hot request paths |

The catalog of service types lives in `service-types/INDEX.md`.

---

# Cross-Service Interaction Rules

## Synchronous (HTTP)

- Services communicate via HTTP through **published contracts only**.
- The contract for each call path is a file in `specs/contracts/http/` owned by the callee.
- A service must not call another service's internal (non-contract) endpoints.
- **Circular synchronous dependencies are forbidden.** Break the cycle with an event-based path.
- Each project declares its concrete HTTP dependency matrix in:
  - `PROJECT.md` (service map overview)
  - `specs/contracts/http/<service>-api.md` (the callable surface of each callee)
  - optional `specs/services/<service>/external-integrations.md` (outbound calls listed per service)

## Asynchronous (Events)

- Services communicate via domain events on the shared messaging infrastructure.
- Event contracts must be defined in `specs/contracts/events/<aggregate>.md` before use.
- Event schemas follow the envelope + payload pattern declared in `event-driven-policy.md`.
- Consumers must be idempotent and support at-least-once delivery.
- Each project declares its concrete event topology in `specs/contracts/events/`.

## External Systems

- Calls to systems outside the project (ERP, payment providers, notification vendors, identity providers, scanners, etc.) are governed by `rules/traits/integration-heavy.md` when that trait is active.
- All external calls must sit behind a dedicated adapter (Hexagonal outbound port pattern) with timeout, circuit breaker, retry, and bulkhead as per the trait rules.

---

# Data Boundaries

- Each service has its own database. Shared databases are forbidden.
- A service must not import or embed another service's entity, table definition, or migration.
- Cross-service data access must go through:
  - A published HTTP or event contract, OR
  - A local projection built from consumed events (read-model cache)
- Master/reference data (catalog-style data that multiple services need to read) is owned by exactly one service and distributed via events for local caching.

---

# How Each Project Uses This Document

1. **Read this file** for the generic constraints and interaction patterns.
2. **Declare the concrete service map** in `PROJECT.md`.
3. **Write per-service ownership and inbound/outbound surface** in:
   - `specs/services/<service>/architecture.md` (declares service type, architecture style, dependencies)
   - `specs/contracts/http/<service>-api.md` (declares callable HTTP surface)
   - `specs/contracts/events/<aggregate>.md` (declares published events)
4. **Validate** the project-level service map against the generic rules above:
   - Are there any forbidden circular dependencies?
   - Does any service own state outside its responsibility?
   - Are all cross-service calls through published contracts?
   - Is every service type boundary rule respected?

---

# Conflict Resolution

If a project-level spec (in `PROJECT.md` or `specs/services/`) conflicts with a rule in this document, the generic rule in this document wins unless the project explicitly declares an override in its `PROJECT.md` under the `## Overrides` section with documented reason, scope, and expiry. See `rules/README.md` for the override protocol.
