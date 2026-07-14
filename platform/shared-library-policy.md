# Shared Library Policy

This document defines what may and may not be placed in shared libraries.

---

# Purpose

Shared libraries exist to provide reusable technical building blocks across services.

They must not become a container for service-specific business logic.

---

# Catalog

| Module | Contents | Consumers |
|---|---|---|
| `libs/java-common` | framework-neutral technical utilities | most services |
| `libs/java-gateway` | **reactive** (WebFlux / Spring Cloud Gateway) edge plumbing — error envelope + writer, request-id / retry-after filters, the fail-open rate-limiter decorator, the shared reactive security chain, and the token/identity boundary: allowed-issuer validator, parameterized tenant gate, identity-header strip (add-only baseline) + JWT header enrichment, claim extractors, JWT decoder + validator-chain assembly | **gateway services only** (wms, scm, fan, ecommerce). Not iam — its gateway is an independent implementation (ADR-MONO-048 § D2) |
| `libs/java-messaging` | messaging / outbox transport scaffolding (ADR-MONO-004) | event producers + consumers |
| `libs/java-notification` | notification contract + client (ADR-MONO-043) | notification producers |
| `libs/java-observability` | tracing / metrics helpers | most services |
| `libs/java-security` | JWT signing/verification, password hashing | auth-side services |
| `libs/java-test-support` | test fixtures + helpers | test source sets |
| `libs/java-web` | **framework-agnostic** web primitives — safe on both servlet and reactive classpaths | servlet services + gateways |
| `libs/java-web-servlet` | servlet-only web helpers | servlet services |

> **Reactive / servlet separation is load-bearing, not stylistic.** `libs/java-web-servlet` was split out of `libs/java-web` by TASK-MONO-044a *after* a servlet leak triggered `BeanDefinitionOverrideException` in three reactive gateway classpaths. `libs/java-gateway` exists for the mirror-image reason (ADR-MONO-048 § D1): putting Spring Cloud Gateway into `java-web` would drag WebFlux onto every servlet service that consumes it. **Do not add reactive types to `java-web`, or servlet types to `java-gateway`.**

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
- reactive gateway/edge plumbing (filters, error envelope, rate-limiter decorators, token validators) — `libs/java-gateway` only
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
- **context-wide Spring annotations in an `@AutoConfiguration`** — see below

## No context-wide annotations in a shared `@AutoConfiguration`

A shared library's `@AutoConfiguration` — or any `@Configuration` it `@Import`s — **must not
declare `@EnableJpaRepositories` or `@EntityScan`** (the same reasoning applies to any
annotation whose effect is the whole consuming `ApplicationContext`: `@ComponentScan`,
`@EnableScheduling`, `@EnableCaching`, …).

An auto-configuration runs in **every** consumer. These annotations are not scoped to the
library; they reconfigure the application:

1. **They silently disable Boot's default.** An explicit `@EnableJpaRepositories` anywhere in
   the context makes Spring Boot's `JpaRepositoriesAutoConfiguration` back off **for the whole
   application**. Every consumer must then hand-declare its own narrowing
   `@EnableJpaRepositories` (and `@EntityScan`) — and a consumer that does not **silently
   loses its own repositories**. A library that ships this obligation has moved a bug into its
   javadoc.
2. **They collide on bean name.** A repository the library registers takes the default bean
   name derived from its simple name. If a service models the same concept — which, for a
   generic concept, it will — both register under that name and the context dies with
   `BeanDefinitionOverrideException`.
3. **They force schema on services that never asked.** An `@Entity` registered by an
   auto-configuration is scanned into every consumer, so under `ddl-auto: validate` every
   consumer must create that table or fail to boot — including services that never use it.

**Ship the contract, not the mapping.** The library provides the port/interface and the
generic machinery; the service owns the `@Entity`, the table, the migration and the repository
scan. If the library genuinely needs a JPA type, expose it as a `@MappedSuperclass` (resolved
via the entity class hierarchy, so it needs no entity scan) and let each service declare the
concrete `@Entity`.

**Why this rule exists.** These are not hypotheticals. Four services' `ApplicationContext`
failed to load because of one such auto-configuration, each was fixed locally with an
`exclude = …` rather than at the source, and the auto-configuration ended up being retained
*because* so many services excluded it. **No compiler and no unit test can see this defect
class** — a slice test never loads auto-configurations, so only a booting context (an
integration lane) observes it. Guarded by `scripts/check-shared-lib-jpa-scan.sh`.
(TASK-BE-333, TASK-BE-432, TASK-BE-461, TASK-BE-489 → TASK-MONO-406.)

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
