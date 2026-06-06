# artist-service — Architecture

This document declares the internal architecture of `fan-platform/apps/artist-service`.
All implementation tasks targeting this service must follow this declaration,
`platform/architecture-decision-rule.md`, and the relevant trait rules
(`rules/traits/transactional.md`, `multi-tenant.md`, `content-heavy.md`,
`read-heavy.md`, `integration-heavy.md`).

---

## Identity

| Field | Value |
|---|---|
| Service name | `artist-service` |
| Project | `fan-platform` |
| Service Type | `rest-api` (single — see Service Type Composition below) |
| Architecture Style | **Hexagonal (ports/adapters)** |
| Domain | fan-platform |
| Primary language / stack | Java 21, Spring Boot 3.4, Spring Web (Servlet), Spring Data JPA, Spring Kafka, Spring Data Redis, Spring Security OAuth2 Resource Server |
| Bounded Context | `artist` (master-data for community-service) |
| Deployable unit | `apps/artist-service/` |
| Data store | Postgres 16 (database `fanplatform_artist`) |
| Cache | Redis 7 (artist directory search read-through, fail-open) |
| Event publication | Kafka via outbox (`artist.*` lifecycle events for downstream consumers, e.g. community-service) |
| Event consumption | none (single-type rest-api) |

### Service Type Composition

`artist-service` is a single-type `rest-api` service per
`platform/service-types/INDEX.md`. Synchronous HTTP read/write API
over the `artist` master-data aggregate plus a Kafka outbox publisher
for downstream consumers (community-service). No inbound event-consumer
surface — the outbox is publication-only.

---

## Architecture Style Rationale

artist-service is a master-data service: write traffic is low (admin-only) but
read traffic on the directory and profile endpoints is high. Future expansion
will likely add adapters for external sources (e.g. Spotify metadata import,
search indexing pipeline). Hexagonal cleanly isolates these concerns:

- The domain layer (`domain/{artist,group,fandom}`) is framework-free POJO.
  Status transitions live on the aggregates (`Artist.publish()`,
  `Artist.archive()`, `ArtistGroup.prepareMembership()`).
- Use cases are interfaces in `application/port/in/`. Adapters in
  `adapter/in/web/` translate HTTP into commands; adapters in
  `adapter/out/{persistence,cache,event,messaging}` translate ports into
  Postgres / Redis / outbox.
- Adding an external metadata adapter (e.g. Spotify import) only needs a new
  outbound port + adapter; the domain stays untouched.

This mirrors `wms-platform/apps/master-service` — a proven pattern for
master-data services in this monorepo.

---

## Package Layout

```
com.example.fanplatform.artist/
├── ArtistServiceApplication.java
├── adapter/
│   ├── in/
│   │   └── web/
│   │       ├── controller/
│   │       │   ├── ArtistController.java               ← /api/artists CRUD
│   │       │   ├── ArtistDirectoryController.java      ← /api/artists?q= search
│   │       │   ├── ArtistGroupController.java          ← /api/artist-groups
│   │       │   └── FandomController.java               ← /api/fandoms
│   │       ├── dto/{request,response}
│   │       ├── advice/GlobalExceptionHandler.java
│   │       ├── filter/TenantClaimEnforcer.java         ← service-level fail-closed
│   │       └── security/                               ← AllowedIssuersValidator,
│   │                                                      TenantClaimValidator,
│   │                                                      ActorContextJwtAuthenticationConverter,
│   │                                                      PublicPaths, ActorContextResolver
│   └── out/
│       ├── persistence/                                ← JPA entities + adapters per repo port
│       ├── cache/ArtistDirectoryCacheAdapter.java      ← Redis read-through, fail-open
│       ├── event/ArtistEventPublisherAdapter.java      ← outbox writer (libs:java-messaging)
│       └── messaging/ArtistOutboxPollingScheduler.java ← extends OutboxPollingScheduler
├── application/
│   ├── ActorContext.java                               ← caller value object
│   ├── exception/                                      ← application-layer exceptions
│   ├── port/
│   │   ├── in/                                         ← use case interfaces + commands/views
│   │   └── out/                                        ← repository, cache, event ports
│   └── service/                                        ← ArtistManagementService,
│                                                          ArtistDirectoryService,
│                                                          ArtistGroupService, FandomService
├── domain/
│   ├── artist/                                         ← Artist (aggregate root), ArtistId,
│   │                                                      ArtistProfile (VO), ArtistStatus, ArtistType
│   ├── group/                                          ← ArtistGroup, ArtistGroupId,
│   │                                                      GroupMembership, GroupRole, ArtistGroupStatus
│   ├── fandom/                                         ← Fandom, FandomId
│   └── tenant/TenantContext.java
└── config/
    ├── SecurityConfig.java                             ← OAuth2 RS + admin role enforcement
    ├── ServiceLevelOAuth2Config.java                   ← service-level JwtDecoder + validators
    ├── JpaConfig.java                                  ← @EnableJpaRepositories scan
    ├── ClockConfig.java
    └── RedisCacheConfig.java
```

### Allowed dependencies

- `spring-boot-starter-{web,data-jpa,data-redis,validation,actuator,security,oauth2-resource-server}`
- `spring-kafka`
- `flyway-core`, `flyway-database-postgresql`, `org.postgresql:postgresql`
- `io.micrometer:micrometer-registry-prometheus`, `micrometer-tracing-bridge-otel`, `opentelemetry-exporter-otlp`
- shared libs: `libs:java-common`, `libs:java-web`, `libs:java-messaging`, `libs:java-observability`, `libs:java-security`

### Forbidden dependencies

- H2 / any in-memory DB (`platform/testing-strategy.md` — Postgres only).
- `spring-cloud-starter-gateway` (artist-service is a downstream service, not an edge gateway).
- Direct Kafka usage outside the outbox path. Producers MUST go through `ArtistEventPublisherAdapter` → outbox → `ArtistOutboxPollingScheduler`.
- Cross-service repository imports (artist-service does not reach into community-service tables; cross-service references go through HTTP contracts or events).

### Boundary rules

- `domain/` MUST NOT depend on Spring or Jakarta annotations beyond pure Java
  + the JDK. Aggregates are framework-free POJOs.
- `application/` MUST NOT import Spring Web (HTTP), Jakarta Persistence (JPA),
  Spring Data Redis, or Kafka client classes. Allowed: Spring's `@Service`,
  `@Transactional`, dependency injection.
- `adapter/in/web/` MUST NOT call `adapter/out/...` directly. All infrastructure
  access flows through `application/` use cases that depend on
  `application/port/out/` ports.
- `adapter/out/` MUST implement `application/port/out/` ports — no other
  outward-facing contract. JPA entities live here (`ArtistJpaEntity` etc.); the
  domain knows nothing about JPA.
- `ArtistEventPublisherAdapter` is the ONLY producer path. New events extend
  the publisher; never call `OutboxWriter` directly from a use case.

---

## Tenant Isolation (multi-tenant.md M2)

Three independent layers enforce the same invariant:

1. **Gateway** — `fan-platform/apps/gateway-service` rejects tokens whose `tenant_id` is not `fan-platform` or `*`.
2. **Service-level JwtDecoder** (`config/ServiceLevelOAuth2Config`) — same validators (`AllowedIssuersValidator` + `TenantClaimValidator`) run against every JWT during decoding.
3. **TenantClaimEnforcer filter** — final guard after Spring Security has populated the SecurityContext. If `tenant_id` is wrong (or absent), the filter writes 403 `TENANT_FORBIDDEN` directly.

Every JPA query is tenant-scoped (`...AndTenantId(...)` derived methods).
Cross-tenant reads return `Optional.empty()` → 404 (NOT 403) so the service
does not leak the existence of other tenants' rows.

---

## State Machine (transactional.md T4)

`ArtistStatus` ∈ { DRAFT, PUBLISHED, ARCHIVED }.

| From → To | Allowed |
|---|---|
| DRAFT → PUBLISHED | YES |
| DRAFT → ARCHIVED | YES |
| PUBLISHED → ARCHIVED | YES |
| ARCHIVED → anything | NO |
| Self-transitions | NO |
| PUBLISHED → DRAFT | NO |

Forbidden transitions throw `Artist.IllegalStateTransitionException` →
HTTP 422 `STATE_TRANSITION_INVALID`. Every transition emits a domain event
(`artist.published`, `artist.archived`).

---

## Visibility & Authorization (asymmetric content)

| Resource state | Caller | Visibility |
|---|---|---|
| PUBLISHED | any authenticated tenant member | GET allowed |
| DRAFT | non-admin | 404 ARTIST_NOT_FOUND (do not leak) |
| DRAFT | admin / operator / super_admin | GET allowed |
| ARCHIVED | non-admin | 404 ARTIST_NOT_FOUND |
| ARCHIVED | admin | GET allowed |

POST / PATCH / DELETE on `/api/artists/**`, `/api/artist-groups/**`, PUT on
`/api/fandoms/**` require admin role (enforced both at SecurityConfig and the
application service for defense-in-depth). Reads require any authenticated
user in the same tenant.

---

## Outbox + Kafka (event-driven-policy.md, integration-heavy.md I8)

- Business writes (artist/group/fandom) and outbox INSERT share one transaction.
- `ArtistOutboxPollingScheduler` (extends `libs:java-messaging`'s
  `OutboxPollingScheduler`) polls PENDING rows and publishes to Kafka with
  `acks=all`, `enable.idempotence=true`. On success, `published_at` is set; on
  failure, `artist_outbox_publish_failures_total` increments and the row is
  retried on the next tick.
- Topic mapping (`.v1` suffix per `platform/event-driven-policy.md`):
  - `artist.registered`            → `artist.registered.v1`
  - `artist.published`             → `artist.published.v1`
  - `artist.updated`               → `artist.updated.v1`
  - `artist.archived`              → `artist.archived.v1`
  - `artist.group_created`         → `artist.group_created.v1`
  - `artist.group_member_changed`  → `artist.group_member_changed.v1`
- Envelope shape (from `BaseEventPublisher`): `{ eventId, eventType, source,
  occurredAt, schemaVersion, partitionKey, payload }`. `eventId` is **UUID v7**
  per `TASK-MONO-025` (머지 완료, `libs/java-messaging` BaseEventPublisher 이
  `UuidV7.randomString()` 발급).

---

## Read Path (read-heavy.md)

- Single-artist `GET /api/artists/{id}` — pure DB read, no cache (low traffic
  per id, high cardinality).
- Directory search `GET /api/artists?q=...` — read-through Redis cache. Key:
  `cache:fan-platform:artist:directory:<tenantId>:<sha256(qNorm|type|page|size)>`.
  TTL 5 minutes (configurable via `fanplatform.artist.cache.directory.ttl-seconds`).
  On Redis unavailability: fail-open (DB query) + emit
  `artist_directory_cache_unavailable_total`. **Invalidation**: explicit
  tenant-wide DEL on artist publish / update / archive (handled by
  `ArtistManagementService`).

---

## Failure Modes

| Situation | Response |
|---|---|
| Missing/invalid JWT | 401 UNAUTHORIZED |
| `tenant_id` is `wms`/other (not `*`) | 403 TENANT_FORBIDDEN |
| Non-admin POST/PATCH/DELETE | 403 FORBIDDEN |
| Artist not found OR cross-tenant OR DRAFT/ARCHIVED to non-admin | 404 ARTIST_NOT_FOUND |
| Stage name UNIQUE collision | 409 STAGE_NAME_CONFLICT |
| Group name UNIQUE collision | 409 GROUP_NAME_CONFLICT |
| Forbidden status transition | 422 STATE_TRANSITION_INVALID |
| (group, artist) active membership added twice | 422 ALREADY_MEMBER |
| Fandom create on DRAFT artist | 422 ARTIST_NOT_PUBLISHED |
| Optimistic-lock conflict | 409 CONFLICT |
| Postgres down | 5xx (gateway 503 envelope) |
| Kafka down | outbox PENDING accumulates; metric increments |
| Redis down | directory query bypasses cache (fail-open) |

---

## Testing Strategy

- **Unit** — `ArtistTest`, `ArtistGroupTest`, `FandomTest` (domain
  invariants); `ArtistManagementServiceTest`, `ArtistDirectoryServiceTest`,
  `ArtistGroupServiceTest`, `FandomServiceTest` (use cases);
  `AllowedIssuersValidatorTest`, `TenantClaimValidatorTest`,
  `TenantClaimEnforcerTest`.
- **Slice** — `@WebMvcTest` for each controller (`Artist/ArtistDirectory/
  ArtistGroup/Fandom`) covering envelope shape, validation, auth.
- **Integration** (`@Tag("integration")`, Postgres + Kafka + Redis Testcontainers,
  WireMock JWKS):
  - `ArtistServiceIntegrationTest` — happy-path E2E (register → publish → search → GET).
  - `AdminRoleEnforcementIntegrationTest` — fan token POST → 403; admin → 201.
  - `MultiTenantIsolationTest` — cross-tenant 403 via filter; DRAFT non-admin → 404.
  - `OutboxRelayIntegrationTest` — `artist.registered.v1` Kafka publish via Awaitility.
  - `ArtistDirectoryCacheIntegrationTest` — Redis cache populate + invalidate on publish.
  - `StageNameUniquenessIntegrationTest` — UNIQUE constraint per tenant.
  - `ArtistApiContractTest` — endpoint envelope shape per spec.
  - `ArtistHealthCheckIntegrationTest` — `/actuator/health` returns 200 unauthenticated.

The default `test` Gradle task excludes `@Tag("integration")`; `integrationTest`
opts in.

---

## References

- `platform/architecture-decision-rule.md`
- `platform/event-driven-policy.md`
- `platform/testing-strategy.md`
- `projects/wms-platform/apps/master-service` (Hexagonal master-data reference)
- `projects/fan-platform/apps/community-service` (sibling fan-platform service patterns)
- `projects/fan-platform/specs/integration/iam-integration.md`
- `projects/fan-platform/specs/services/gateway-service/architecture.md`
- `rules/traits/transactional.md` § T3 / T4 (outbox / state machine)
- `rules/domains/fan-platform.md` § F1 / F7 (asymmetric content, multi-tenant)
- `rules/traits/integration-heavy.md` § I3 / I8 (fail-open, outbox)
- `rules/traits/read-heavy.md` § R3 (cache layer)
