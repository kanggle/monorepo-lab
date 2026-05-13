# Shared Library Policy

This document defines what may and may not be placed in shared libraries.

---

# Purpose

Shared libraries exist to provide reusable technical building blocks across services.

They must not become a container for service-specific business logic.

---

# Allowed in Shared Libraries

Shared libraries may contain:

- common technical utilities
- shared web configuration helpers
- common exception primitives
- shared security helpers
- messaging abstractions used by multiple services
- observability helpers
- test support utilities
- common DTO primitives only if they are truly cross-service and stable

## Messaging-specific guidance (per ADR-MONO-004)

For `libs/java-messaging` — the boundary between transport scaffolding (allowed)
and domain events (forbidden) is explicit:

| Allowed | Forbidden |
|---|---|
| `OutboxRow` interface, `OutboxRowEntity` reference JPA mapping | Service-specific outbox entity classes that live alongside domain types |
| `AbstractOutboxPublisher<R>` generic poll loop | Domain-typed publisher subclasses with `switch (event)` branching over service-specific event types |
| `EventEnvelope` (record with `payload : JsonNode`) | Typed payload classes (e.g. `WarehouseCreatedPayload`, `OrderPlacedPayload`) |
| `EventEnvelopeParser` (`@Component`, malformed JSON → `IllegalArgumentException`) | Per-service envelope subtypes that bake in `aggregateId : UUID` instead of `: String`, or carry domain-specific fields like `sourceTopic` / `tenantId` |
| `EventDedupePort` interface + `Outcome` enum | Per-service dedupe table entities, retention-cleanup schedulers, tenant-scoping logic |
| `MessagingMdc` helper for `traceId` / `eventId` / `consumerLabel` | Consumer-pipeline classes that bake in service-specific listener-group naming |
| `OutboxMetrics` interface + `MicrometerOutboxMetrics` reference impl | Per-service metric naming conventions (the lib accepts a prefix; the service supplies it) |
| `TopicResolver` strategy interface | Per-service topic-resolution lambdas — these stay inside the service's `OutboxPublisher` subclass |

Reference: [ADR-MONO-004](../docs/adr/ADR-MONO-004-shared-messaging-scaffolding.md).

---

# Forbidden in Shared Libraries

Shared libraries must not contain:

- service-specific domain logic
- business rules owned by a single service
- direct references to a specific service entity
- repositories tied to one service database
- service-private policies
- service-specific orchestration logic
- code that forces unrelated services to depend on a domain they do not own
- domain event payload classes (`*.event.<Verb>Event`) — these stay per-service per ADR-MONO-004

---

# Dependency Rule

- Shared libraries may be depended on by services.
- Shared libraries must not depend on service implementation modules.
- Dependencies must remain one-way.
- A shared library must remain reusable by multiple services.

---

# Decision Rule

Before adding code to `libs/`, confirm all of the following:

1. Is it used by more than one service?
2. Is it technical/common rather than domain-owned?
3. Can it remain stable without depending on one service's internal model?
4. Would moving it to `libs/` reduce duplication without increasing coupling?

If any answer is no, keep the code inside the owning service.

---

# Ownership Rule

If a rule or model belongs to one bounded context, it must stay in that service.

Domain ownership is more important than reuse convenience.

---

# Examples

## Good candidates

- common logging setup
- request tracing helpers
- retry utility
- Kafka envelope abstraction
- test fixture utilities
- standardized error response helpers

## Bad candidates

- order discount calculation
- payment approval policy
- user registration rule
- inventory allocation rule
- service-specific entity definitions

---

# Review Rule

Any new shared library or major shared-library expansion must be reviewed against this policy before implementation.

---

# Change Rule

Changes to the forbidden/allowed scope (what may or may not be promoted to `libs/`), the shared-library catalog entry list, or the promotion procedure must be documented in this file **before** any `libs/` touch. New shared-library introduction or breaking expansion requires an ADR (`docs/adr/` for monorepo-wide impact) per [`architecture-decision-rule.md`](architecture-decision-rule.md) — see also `# Review Rule` above for the review handoff.
