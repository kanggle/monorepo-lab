# community-service — Architecture

This document declares the internal architecture of `fan-platform/apps/community-service`.
All implementation tasks targeting this service must follow this declaration,
`platform/architecture-decision-rule.md`, and the relevant trait rules
(`rules/traits/transactional.md`, `multi-tenant.md`, `content-heavy.md`,
`read-heavy.md`, `integration-heavy.md`).

---

## Identity

| Field | Value |
|---|---|
| Service name | `community-service` |
| Project | `fan-platform` |
| Service Type | `rest-api` (single — see Service Type Composition below) |
| Architecture Style | **Layered + 명시적 상태 기계** |
| Domain | fan-platform |
| Primary language / stack | Java 21, Spring Boot 3.4, Spring Web (Servlet), Spring Data JPA, Spring Kafka, Spring Data Redis, Spring Security OAuth2 Resource Server |
| Bounded Context | `community` |
| Deployable unit | `apps/community-service/` |
| Data store | Postgres 16 (database `fanplatform_community`) |
| Cache | Redis 7 (feed cache only — fail-open) |
| Event publication | Kafka via outbox (`community.post.*` lifecycle events — `community.post.published`, `community.post.status_changed.v1`) |
| Event consumption | none (single-type rest-api) |

### Service Type Composition

`community-service` is a single-type `rest-api` service per
`platform/service-types/INDEX.md`. Synchronous HTTP CRUD over posts
backed by an internal post state machine (DRAFT/PUBLISHED/HIDDEN/DELETED)
and a Kafka outbox publisher. No inbound event-consumer surface — the
outbox is publication-only.

---

## Architecture Style Rationale

community-service has clearly delineated layers (controller → use case → domain →
infrastructure) but the domain is small and not aggregate-heavy. Hexagonal
ports/adapters add value when there are many cross-cutting infrastructure
boundaries; community-service has only Postgres + Redis + Kafka + IAM IdP.
**Layered** keeps the file count low and matches the IAM `community-service`
reference implementation directly (TASK-FAN-BE-002 § Implementation Notes).

The **명시적 상태 기계** addition is the only architectural deviation from a
plain CRUD layered service. Post status transitions
(`DRAFT → PUBLISHED → HIDDEN → DELETED`) flow through `PostStatusMachine`,
which holds the actor-typed transition matrix as the only source of truth.
Direct setter mutation of `Post.status` is impossible — every transition is
audited via `post_status_history` (append-only) and emits a
`community.post.status_changed.v1` event via the outbox.

---

## Package Layout

```
com.example.fanplatform.community/
├── CommunityServiceApplication.java
├── presentation/
│   ├── controller/
│   │   ├── PostController.java         ← /api/community/posts (CRUD + status)
│   │   ├── FeedController.java         ← /api/community/feed
│   │   ├── CommentController.java
│   │   ├── ReactionController.java     ← PUT/DELETE (idempotent upsert)
│   │   └── FollowController.java
│   ├── dto/                             ← request/response envelopes
│   ├── advice/
│   │   └── GlobalExceptionHandler.java  ← envelope mapping
│   └── filter/
│       └── TenantClaimEnforcer.java     ← service-level fail-closed (defense-in-depth)
├── application/
│   ├── ActorContext.java                ← caller value object
│   ├── PublishPost / UpdatePost / ChangePostStatus / DeletePost UseCase
│   ├── GetPostUseCase / GetFeedUseCase
│   ├── AddComment / DeleteComment UseCase
│   ├── AddReaction / RemoveReaction UseCase
│   ├── FollowArtist / UnfollowArtist UseCase
│   ├── PostAccessGuard.java             ← visibility + membership check
│   ├── PostMediaRefSerializer.java
│   └── event/
│       └── CommunityEventPublisher.java  ← outbox write port (v2; impl in infrastructure/outbox)
├── domain/
│   ├── post/                            ← Post aggregate, PostType, PostVisibility
│   │   ├── Post.java                    ← @Entity (JPA)
│   │   ├── PostRepository.java          ← port
│   │   └── status/                      ← PostStatus, PostStatusMachine, history
│   ├── comment/Comment.java + repository port
│   ├── reaction/Reaction.java + ReactionType
│   ├── follow/Follow.java + repository port
│   ├── tenant/TenantContext.java
│   └── membership/MembershipChecker.java  ← port
└── infrastructure/
    ├── config/JpaConfig.java + ClockConfig.java + OutboxConfig.java
    ├── jpa/                              ← Spring Data adapters per repository (+ CommunityOutboxJpaEntity/Repository)
    ├── outbox/OutboxCommunityEventPublisher.java (v2 write adapter) + CommunityOutboxPublisher.java (v2 relay, extends AbstractOutboxPublisher)
    ├── cache/FeedCacheRepository.java    ← Redis (fail-open)
    ├── membership/HttpMembershipChecker.java (prod default, FAN-BE-010) + AlwaysAllowMembershipChecker.java (inert fallback) + auto-config
    └── security/                         ← service-level OAuth2 + tenant validators
```

### Allowed dependencies

- `spring-boot-starter-{web,data-jpa,data-redis,validation,actuator,security,oauth2-resource-server}`
- `spring-kafka`
- `flyway-core`, `flyway-database-postgresql`, `org.postgresql:postgresql`
- `io.micrometer:micrometer-registry-prometheus`, `micrometer-tracing-bridge-otel`, `opentelemetry-exporter-otlp`
- shared libs: `libs:java-common`, `libs:java-web`, `libs:java-messaging`, `libs:java-observability`, `libs:java-security`

### Forbidden dependencies

- H2 / any in-memory DB (`platform/testing-strategy.md` — Postgres only).
- `spring-cloud-starter-gateway` (community-service is a downstream service, not an edge gateway).
- Direct Kafka usage outside the outbox path. Producers MUST go through `CommunityEventPublisher` → outbox → `CommunityOutboxPublisher`.
- Cross-service repository imports (community-service does not reach into artist-service / membership-service tables; membership access goes over an HTTP client — `HttpMembershipChecker`, FAN-BE-010 — never a DB-level reach-in).

### Boundary rules

- `presentation/` MUST NOT call `infrastructure/` directly. All infrastructure access flows through `application/` use cases that depend on domain ports.
- `domain/` MUST NOT depend on Spring or Jakarta annotations beyond `jakarta.persistence` (JPA) — chosen as a pragmatic exception so the entity types double as JPA-mapped objects (matches IAM reference). No Spring framework imports inside `domain/`.
- `application/event/CommunityEventPublisher` is the ONLY producer path. Any new event MUST extend the publisher; never call `OutboxWriter` directly from a use case or controller.
- `infrastructure/security/` re-validates `tenant_id` even though the gateway already does — this is fail-closed defense-in-depth (see § Tenant Isolation below).

---

## Tenant Isolation (multi-tenant.md M2)

Three independent layers enforce the same invariant:

1. **Gateway** — `fan-platform/apps/gateway-service` rejects tokens whose `tenant_id` is not `fan-platform` or `*`. See `projects/fan-platform/specs/services/gateway-service/architecture.md` § JWT Validation.
2. **Service-level JwtDecoder** (`infrastructure/security/ServiceLevelOAuth2Config`) — same validators (`AllowedIssuersValidator` + `TenantClaimValidator`) run against every JWT during decoding.
3. **TenantClaimEnforcer filter** — final guard after Spring Security has populated the SecurityContext. If `tenant_id` is wrong (or absent), the filter writes 403 `TENANT_FORBIDDEN` directly.

Every JPA query is tenant-scoped (`...AndTenantId(...)` derived methods, or
`WHERE p.tenantId = :tenantId` JPQL). Cross-tenant reads return
`Optional.empty()` → 404 (NOT 403) so the service does not leak the existence
of other tenants' posts.

---

## State Machine (transactional.md T4)

`PostStatus` ∈ { DRAFT, PUBLISHED, HIDDEN, DELETED }.
`ActorType` ∈ { AUTHOR, OPERATOR, SYSTEM }.

| Actor | Allowed transitions |
|---|---|
| AUTHOR | DRAFT→PUBLISHED, PUBLISHED→HIDDEN, PUBLISHED→DELETED |
| OPERATOR | DRAFT→DELETED, PUBLISHED→HIDDEN, PUBLISHED→DELETED, HIDDEN→PUBLISHED, HIDDEN→DELETED |
| SYSTEM | (none in v1) |

- `DELETED` is terminal — every transition out of DELETED is forbidden.
- Self-transitions (e.g. `PUBLISHED → PUBLISHED`) are forbidden.
- `PUBLISHED → DRAFT` is forbidden for every actor (TASK-FAN-BE-002 AC).
- Forbidden transitions throw `InvalidStateTransitionException` (HTTP 422 `POST_STATUS_TRANSITION_INVALID`).

Every transition appends a row to `post_status_history` (append-only) AND emits `community.post.status_changed.v1`.

---

## Visibility Tiers (content-heavy.md)

| Tier | Author | Same tenant non-author | Notes |
|---|---|---|---|
| PUBLIC | ✓ | ✓ | open to any authenticated actor in the tenant |
| MEMBERS_ONLY | ✓ | only if `MembershipChecker.hasAccess(...)` returns true | Production default = `HttpMembershipChecker` (calls membership-service over workload-identity, **fail-closed** on error; FAN-BE-010). `AlwaysAllowMembershipChecker` (always true + WARN) is retained only as the `@ConditionalOnMissingBean` fallback for stacks without membership-service (e.g. the live-trio e2e via `community.membership-service.enabled=false`, FAN-INT-002). |
| PREMIUM | ✓ | gated by `MembershipChecker.hasAccess(...)`, same as MEMBERS_ONLY | Production calls membership-service (fail-closed). The always-pass + WARN behaviour applies only under the inert fallback stub (e2e escape-hatch), not in production. |

`PostAccessGuard` centralizes this logic. The feed (`GetFeedUseCase`)
applies the same tiering at the row level — locked items are returned with
`title`/`bodyPreview` redacted and `locked=true` so the UI knows to display a
"Subscribe" CTA without leaking content.

---

## Outbox + Kafka (event-driven-policy.md, integration-heavy.md I8)

- **Outbox v2 (TASK-FAN-BE-021).** The write path is the port
  `application/event/CommunityEventPublisher` implemented by
  `infrastructure/outbox/OutboxCommunityEventPublisher`, which persists one
  `community_outbox` row (`extends OutboxRowEntity`, UUIDv7 `event_id` PK) inside
  the same transaction as the post/comment/reaction/status write. This replaces the
  v1 `BaseEventPublisher` + lib `OutboxWriter` → `outbox` (BIGSERIAL, `status`)
  write path.
- `CommunityOutboxPublisher` (extends `libs:java-messaging`'s
  `AbstractOutboxPublisher`, ADR-MONO-004 § 5) drains `community_outbox`
  (`WHERE published_at IS NULL ORDER BY occurred_at ASC`) and publishes to Kafka
  with `acks=all`, `enable.idempotence=true`, plus `eventId`/`eventType` record
  headers and exponential backoff. On success `published_at` is set; on a per-event
  send failure the preserved `community_outbox_publish_failures_total` counter
  increments (plus the v2 `community.outbox.publish.{success,failure}.total` /
  `.lag.seconds` metrics and the `community.outbox.pending.count` gauge) and the
  row is retried. The relay is an unconditional `@Component` (matching the v1
  scheduler — `@EnableScheduling` already on the main class); it polls every
  `community.outbox.poll-ms` (default 1000ms) in batches of
  `community.outbox.batch-size` (default 100).
- Topic mapping (`.v1` suffix per `platform/event-driven-policy.md`, ported
  verbatim from the v1 scheduler):
  - `community.post.published` → `community.post.published.v1`
  - `community.post.status_changed` → `community.post.status_changed.v1`
  - `community.comment.added` → `community.comment.added.v1`
  - `community.reaction.added` → `community.reaction.added.v1`
- The Kafka record key = `aggregateId` (postId; partition_key left null → relay
  fallback, preserving the v1 key).
- Envelope shape (canonical 7-field, byte-identical to the v1
  `BaseEventPublisher.writeEvent`): `{ eventId, eventType, source, occurredAt, schemaVersion, partitionKey, payload }`.
- **Legacy v1 tables (TASK-MONO-406).** The lib `OutboxAutoConfiguration` /
  `OutboxJpaConfig` / `ProcessedEventJpaEntity` were deleted, so **no library entity
  maps the v1 `outbox` / `processed_events` tables any more**. Both tables remain in
  the schema (applied Flyway migrations are immutable) but are now unmapped, and
  `ddl-auto=validate` only validates *mapped* entities. The v1 `outbox` table is
  neither written nor polled; the live table is `community_outbox`
  (`CommunityOutboxJpaEntity`).

---

## Read Path (read-heavy.md)

- Single-post `GET /api/community/posts/{id}` — pure DB read, no cache.
- Feed `GET /api/community/feed` — fan-out-on-read with Redis read-through cache (5-minute TTL). On Redis unavailability, fall through to Postgres directly (fail-open) and emit `community_feed_cache_unavailable_total`. **Invalidation**: TTL-only in v1 — newly published posts and follow/unfollow changes are visible after at most 5 minutes. v2 may add explicit DEL on `community.post.published` / a future `community.follow.changed` topic if sub-minute freshness becomes a requirement.
- Bulk aggregate counts (`countsByPostIds`) batch all comment/reaction tallies into 2 grouped queries per page.

---

## Failure Modes

| Situation | Response |
|---|---|
| Missing/invalid JWT | 401 UNAUTHORIZED |
| `tenant_id` is `wms`/other (not `*`) | 403 TENANT_FORBIDDEN |
| Post not found OR cross-tenant | 404 POST_NOT_FOUND |
| Forbidden status transition | 422 POST_STATUS_TRANSITION_INVALID |
| Author edits PUBLISHED past 5min | 422 EDIT_WINDOW_EXPIRED |
| Self-follow | 422 SELF_FOLLOW_FORBIDDEN |
| Member-tier visibility blocked | 403 MEMBERSHIP_REQUIRED |
| Already following | 409 ALREADY_FOLLOWING |
| Optimistic-lock conflict on Post | 409 CONFLICT |
| Postgres down | 5xx (gateway 503 envelope) |
| Kafka down | outbox PENDING accumulates; metric increments |
| Redis down | feed query bypasses cache (fail-open) |

---

## Testing Strategy

- **Unit** — `PostStatusMachineTest`, `PostAccessGuardTest`, `TenantClaimValidatorTest`, `AllowedIssuersValidatorTest`, `CommunityEventPublisherTest`, every use case (`Publish/ChangeStatus/AddReaction/FollowArtist`), `TenantClaimEnforcerTest`.
- **Slice** — `@WebMvcTest` for each controller (`Post/Feed/Comment/Reaction/Follow`) covering envelope shape, validation, auth.
- **Integration** (`@Tag("integration")`, Postgres + Kafka + Redis Testcontainers, WireMock JWKS):
  - `CommunityServiceIntegrationTest` — happy path E2E.
  - `MultiTenantIsolationTest` — cross-tenant queries return 404; cross-tenant writes 403.
  - `OutboxRelayIntegrationTest` — DB row → outbox row → Kafka topic → `published_at` set.
  - `FeedQueryIntegrationTest` — follow + pagination + Redis cache.
  - `MembershipGateIntegrationTest` — MEMBERS_ONLY/PREMIUM gating.
  - `CommunityHealthCheckIntegrationTest` — `/actuator/health` returns 200 unauthenticated.

The default `test` Gradle task excludes `@Tag("integration")`; `integrationTest` opts in.

---

## References

- `platform/architecture-decision-rule.md`
- `platform/event-driven-policy.md`
- `platform/testing-strategy.md`
- `projects/iam-platform/apps/community-service` (frozen reference)
- `projects/fan-platform/specs/integration/iam-integration.md`
- `projects/fan-platform/specs/services/gateway-service/architecture.md`
- `rules/traits/transactional.md` § T4 (state machine)
- `rules/traits/multi-tenant.md` § M2 (tenant_id everywhere)
- `rules/traits/integration-heavy.md` § I3 / I8 (fail-open, outbox)
