# notification-service — Architecture

This document declares the internal architecture of `fan-platform/apps/notification-service`.
All implementation tasks targeting this service must follow this declaration,
`platform/architecture-decision-rule.md`, `platform/service-types/event-consumer.md`,
and the relevant trait rules (`rules/traits/integration-heavy.md`,
`rules/traits/multi-tenant.md`, `rules/traits/content-heavy.md`).

> **Authoring status.** This spec is the source of truth produced by
> **TASK-FAN-BE-012** (SPEC/design increment). Implementation =
> **TASK-FAN-BE-013** (skeleton + membership event consumer + idempotency +
> retry/DLQ + mock channels + inbox read API + infra). No production code, build
> files, or ADR are created by FAN-BE-012 — fan-platform has no ADR directory;
> architectural decisions live in this `architecture.md` (same convention as
> community/artist/gateway/membership), and PROJECT.md § Service Map v2 is the
> forward-declaration authority, so no new ADR is required (HARDSTOP-09 satisfied
> by spec-before-impl).

---

## Identity

| Field | Value |
|---|---|
| Service name | `notification-service` |
| Project | `fan-platform` |
| Service Type | `event-consumer` (primary) + thin `rest-api` inbox surface — see Service Type Composition |
| Architecture Style | **Layered** |
| Domain | fan-platform |
| Primary language / stack | Java 21, Spring Boot 3.4, Spring Kafka, Spring Web (Servlet), Spring Data JPA, Spring Security OAuth2 Resource Server |
| Bounded Context | `notification` |
| Deployable unit | `apps/notification-service/` |
| Data store | Postgres 16 (database `fanplatform_notification`) |
| Cache | none (v1 — the inbox is a tenant+account indexed point query; Redis deliberately omitted) |
| Event consumption | Kafka — `fan.membership.activated.v1`, `fan.membership.canceled.v1` (consumer group `notification-service-membership-events`) |
| Event publication | **none** — terminal consumer (no outbox, no produced topics) |

### Service Type Composition

`notification-service` is **primarily `event-consumer`** per
`platform/service-types/INDEX.md`: its core role is asynchronously reacting to
membership lifecycle events and fanning out notifications through channel
adapters. It additionally exposes a **small secondary `rest-api`** surface — the
in-app **notification inbox** (`GET /api/fan/notifications`, mark-as-read) — which
is an explicitly allowed "small query endpoint as a secondary capability"
(`event-consumer.md` § Allowed Patterns). The service publishes **no** events
(terminal consumer): there is no outbox and no produced topic.

---

## Architecture Style Rationale

notification-service has a small domain centered on a single `Notification`
aggregate plus two infrastructure boundaries (Kafka inbound + a channel adapter).
Its layers are cleanly delineated (consumer/controller → use case → domain →
infrastructure). Hexagonal ports/adapters earn their cost when there are many
cross-cutting boundaries; this service has Postgres + Kafka inbound + a single
`NotificationChannelPort` + IAM IdP. **Layered** keeps the file count low and
matches the sibling `membership-service` / `community-service` / `artist-service`
convention directly (fan-platform uses Layered, NOT Hexagonal). The one named
collaborator boundary that earns a port is the **channel** (so the deterministic
mock can be swapped for a real FCM/APNs/email adapter later) — declared as
`NotificationChannelPort`.

---

## Package Layout

```
com.example.fanplatform.notification/
├── NotificationServiceApplication.java
├── presentation/
│   ├── controller/
│   │   └── NotificationInboxController.java     ← GET /api/fan/notifications, POST /{id}/read
│   ├── dto/                                     ← inbox response envelopes
│   ├── advice/
│   │   └── GlobalExceptionHandler.java          ← envelope mapping
│   └── filter/
│       └── TenantClaimEnforcer.java             ← service-level fail-closed (defense-in-depth)
├── application/
│   ├── ActorContext.java                        ← caller value object (accountId = sub)
│   ├── consumer/
│   │   └── MembershipEventConsumer.java         ← @KafkaListener(activated.v1, canceled.v1) → use case
│   ├── HandleMembershipEventUseCase.java        ← idempotent: create Notification + dispatch channels
│   ├── ListNotificationsUseCase.java            ← inbox read (tenant+account scoped, paginated)
│   └── MarkNotificationReadUseCase.java         ← UNREAD → READ (idempotent)
├── domain/
│   ├── notification/
│   │   ├── Notification.java                    ← @Entity (JPA) — notification aggregate
│   │   ├── NotificationRepository.java          ← port
│   │   ├── NotificationType.java                ← WELCOME / CANCELLATION (+ template mapping)
│   │   └── NotificationStatus.java              ← UNREAD / READ
│   ├── channel/
│   │   └── NotificationChannelPort.java         ← port: deliver(notification) → DeliveryResult
│   └── tenant/TenantContext.java
└── infrastructure/
    ├── config/JpaConfig.java + ClockConfig.java + KafkaConsumerConfig.java
    ├── jpa/                                      ← Spring Data adapter for NotificationRepository
    ├── messaging/
    │   ├── idempotency/                          ← libs:java-messaging processed_events guard
    │   └── dlq/MembershipEventDlqPublisher.java  ← routes poisoned/exhausted events to <topic>.dlq
    ├── channel/
    │   ├── LoggingEmailChannelAdapter.java       ← deterministic mock (NO real email)
    │   └── LoggingPushChannelAdapter.java        ← deterministic mock (NO real FCM/APNs)
    └── security/                                 ← service-level OAuth2 + tenant validators
```

### Allowed dependencies

- `spring-boot-starter-{web,data-jpa,validation,actuator,security,oauth2-resource-server}`
- `spring-kafka`
- `flyway-core`, `flyway-database-postgresql`, `org.postgresql:postgresql`
- `io.micrometer:micrometer-registry-prometheus`, `micrometer-tracing-bridge-otel`, `opentelemetry-exporter-otlp`
- shared libs: `libs:java-common`, `libs:java-web`, `libs:java-messaging`, `libs:java-observability`, `libs:java-security`

### Forbidden dependencies

- H2 / any in-memory DB (`platform/testing-strategy.md` — Postgres only).
- `spring-cloud-starter-gateway` (downstream service, not an edge gateway).
- An **outbox/producer** path — notification-service is a terminal consumer; it MUST NOT publish domain events. (The only Kafka *producer* use is the DLQ publisher writing to `<topic>.dlq`, which is infrastructure error-handling, not a domain event.)
- Any real push/email SDK (FCM/APNs/SES/SMTP) — channels are deterministic logged mocks in v1; a real adapter is a future increment that re-implements `NotificationChannelPort`.
- Cross-service repository imports (notification-service does not reach into membership/community/artist tables — it reacts to their events only).
- `spring-boot-starter-data-redis` — no cache case in v1.

### Boundary rules

- `presentation/` and `application/consumer/` MUST NOT call `infrastructure/`
  directly. All infrastructure access flows through `application/` use cases that
  depend on domain ports.
- `domain/` MUST NOT depend on Spring or Jakarta annotations beyond
  `jakarta.persistence` (JPA) — the `Notification` entity doubles as a JPA-mapped
  object (matches the membership/community convention). No Spring imports in `domain/`.
- `NotificationChannelPort` is the ONLY boundary to delivery side effects. Use
  cases depend on the port; the deterministic mock adapters live in
  `infrastructure/channel/`. A real channel adapter swaps in via
  `@ConditionalOnMissingBean` / profile without touching domain or use cases.
- `MembershipEventConsumer` is the ONLY inbound Kafka surface; it delegates to
  `HandleMembershipEventUseCase` and never embeds business logic.
- `infrastructure/security/` re-validates `tenant_id` for the inbox routes even
  though the gateway already does — fail-closed defense-in-depth (§ Tenant Isolation).

---

## Domain Model

`Notification` is the single aggregate — one delivered/queued notification held
for one fan account, derived from one membership lifecycle event.

| Field | Type | Notes |
|---|---|---|
| `id` | string (UUID v7) | aggregate id |
| `tenantId` | string | row-level isolation; always `fan-platform` in this project |
| `accountId` | string | the recipient fan = IAM `sub` claim (from the event payload `accountId`) |
| `type` | `NotificationType` | `WELCOME` \| `CANCELLATION` |
| `title` | string | rendered from the type template |
| `body` | string | rendered from the event payload (tier, plan window, etc.) |
| `status` | `NotificationStatus` | `UNREAD` \| `READ` |
| `sourceEventId` | string (UUID) | the consumed envelope `eventId` — also the idempotency key |
| `sourceEventType` | string | `fan.membership.activated` \| `fan.membership.canceled` |
| `membershipId` | string (UUID) | the originating membership aggregate id |
| `createdAt` | timestamptz | consume time |
| `readAt` | timestamptz? | set on UNREAD → READ; null while UNREAD |
| `version` | long | optimistic lock |

### Event → Notification mapping

| Consumed topic | `NotificationType` | Title (template) | Body (from payload) |
|---|---|---|---|
| `fan.membership.activated.v1` | `WELCOME` | "Welcome to {tier} membership" | window `[validFrom … validTo]`, `planMonths` |
| `fan.membership.canceled.v1` | `CANCELLATION` | "Your {tier} membership was canceled" | `canceledAt`, optional `reason` |

`fan.membership.expired.v1` is **forward-declared but NOT consumed** — the producer
does not emit it (read-time expiry, no sweeper; see `fan-membership-events.md`).
A future increment that adds the producer sweeper will also add an `EXPIRY_REMINDER`
type + subscription here. Until then notification-service MUST NOT subscribe to it
(a dead consumer).

---

## Subscribed Topics (event-consumer.md § Subscription Ownership)

| Topic | Producer | Consumer group | Partition key | Handler | Status |
|---|---|---|---|---|---|
| `fan.membership.activated.v1` | membership-service | `notification-service-membership-events` | `membershipId` | `MembershipEventConsumer#onActivated` | consumed |
| `fan.membership.canceled.v1` | membership-service | `notification-service-membership-events` | `membershipId` | `MembershipEventConsumer#onCanceled` | consumed |
| `fan.membership.expired.v1` | (membership-service, future sweeper) | — | `membershipId` | — | **forward-declared, NOT subscribed** (not emitted upstream) |

- **Consumer group**: `notification-service-membership-events` — one team-owned
  group for the membership-event subscription (`<service>-<purpose>` convention).
- **Ordering**: per-`membershipId` ordering is preserved by the producer's
  partition key; cross-membership ordering is NOT relied upon. WELCOME and
  CANCELLATION for the same membership therefore arrive in causal order.

---

## Consume Semantics

### Idempotency (event-consumer.md § Idempotency; idempotent-consumer.md)

- Strategy: **idempotency table keyed by `eventId`** via `libs:java-messaging`'s
  `processed_events` (24h+ retention). Before handling, the use case checks/inserts
  `processed_events(eventId)`; a duplicate delivery (at-least-once) short-circuits
  with NO second `Notification` row and NO second channel dispatch.
- Secondary natural guard: `Notification.sourceEventId` is unique — a race that
  slipped past the processed-events check still cannot create a duplicate
  (DB unique constraint → caught + treated as already-processed).

### Retry and DLQ (event-consumer.md § Retry and DLQ; consumer-retry-dlq.md)

- Transient handler failures (e.g., a channel mock throwing a simulated transient
  error, a DB blip): in-process exponential backoff with jitter, **max 3 retries**.
- Persistent failures (retries exhausted) and **un-parseable / unsupported-schema**
  envelopes: routed to the DLQ topic **`<topic>.dlq`** (e.g.
  `fan.membership.activated.v1.dlq`) with the **full original envelope + failure
  reason** header, via `MembershipEventDlqPublisher`. The event is then marked
  consumed (offset committed) so the partition is never poisoned.
- **emit-not-throw discipline** (feedback §18): a handler MUST NOT let a per-message
  exception escape to stall the partition — it either succeeds, retries, or routes
  to DLQ. The channel dispatch failure is isolated per notification.
- Operator alert when **`dlq_depth > 0`**.

### Schema versioning (event-consumer.md § Schema Versioning)

- Consumers tolerate unknown payload fields (forward compatibility).
- Branch on the envelope `schemaVersion`; the current contract is `schemaVersion=1`.
- An event with an **unsupported `schemaVersion` → DLQ** (never silently dropped).

### Trace propagation (event-consumer.md § Trace Propagation)

- OTel context is propagated from Kafka headers via `KafkaPropagator`
  (`libs:java-observability`); each consumed event creates a span linked to the
  producer span.

---

## Channel Mock Boundary

Delivery side effects are abstracted behind `NotificationChannelPort`:

```
DeliveryResult deliver(Notification notification) → { delivered, channel, ref }
```

The v1 adapters `LoggingEmailChannelAdapter` + `LoggingPushChannelAdapter` are
**deterministic logged mocks** — there is **no real external channel integration**
in this increment. Each logs a structured delivery line + returns a synthetic
`ref` (e.g. `mockmail_<uuid>`, `mockpush_<uuid>`) and increments a per-channel
counter. A real channel adapter (FCM/APNs/SES) is a future increment: it
re-implements `NotificationChannelPort`, wired via `@ConditionalOnMissingBean` /
profile, with the mock retained for dev + integration tests. The domain and
use-case layers are unchanged by that swap.

Delivery is **best-effort and decoupled from the inbox write**: the `Notification`
row is the durable record (always created on a fresh event); a channel failure
retries/DLQs the event but the inbox row, once written under the idempotency
guard, is authoritative.

---

## Inbox Read API (the secondary rest-api surface)

End-user OAuth2 (the fan's access token). All routes are tenant + account scoped.

| Method | Path | Semantics |
|---|---|---|
| `GET` | `/api/fan/notifications?status={UNREAD\|READ}&page=&size=&sort=` | the caller's own notifications, newest first; `status` optional filter; paginated envelope |
| `POST` | `/api/fan/notifications/{id}/read` | `UNREAD → READ` (sets `readAt`); re-marking a READ notification is an idempotent 200 no-op |

- The recipient is always the authenticated `accountId` (`sub`); a fan can read
  ONLY their own notifications. A notification belonging to another account or
  tenant → 404 `NOTIFICATION_NOT_FOUND` (no existence leak).
- Response envelope + pagination follow `platform/` (same `PageResponse` shape as
  membership/community list endpoints).

---

## Tenant Isolation (multi-tenant.md M2)

Three independent layers enforce the same invariant on the inbox surface
(`/api/fan/notifications/**`):

1. **Gateway** — `fan-platform/apps/gateway-service` rejects tokens whose
   `tenant_id` is not `fan-platform` or `*`.
2. **Service-level JwtDecoder** (`infrastructure/security`) — same validators
   (`AllowedIssuersValidator` + `TenantClaimValidator`) run during decoding.
3. **TenantClaimEnforcer filter** — final guard after the SecurityContext is
   populated; wrong/absent `tenant_id` → 403 `TENANT_FORBIDDEN`.

Every inbox query is tenant + account scoped (`...AndTenantIdAndAccountId(...)`).
Cross-tenant / cross-account reads return `Optional.empty()` → 404 (not 403) so the
service does not leak other accounts'/tenants' notifications.

On the **consume** side, `tenantId` comes from the event payload; every
`Notification` row is written with that `tenantId` and is only ever read back under
the same tenant+account scope.

---

## Observability (event-consumer.md § Observability)

- Per-topic metrics: `messages_processed_total`, `messages_failed_total`,
  `consumer_lag`, `dlq_depth` (tagged by topic + consumer group).
- Per-channel counter: `notification_channel_deliveries_total{channel,outcome}`.
- Alerts: consumer lag > 1 min; `dlq_depth > 0`; processing error rate > 1%.
- `/actuator/health` + `/actuator/prometheus` exposed; health is reachable
  unauthenticated.

---

## Failure Modes

| Situation | Response |
|---|---|
| Duplicate event delivery (at-least-once) | idempotent no-op — no second notification, no second dispatch (`processed_events` + unique `sourceEventId`) |
| Channel mock transient failure | bounded in-process retry (≤3, backoff+jitter); the inbox row is already durable |
| Channel failure after retries exhausted | event → `<topic>.dlq` with envelope + reason; offset committed; `dlq_depth` alert |
| Un-parseable envelope / unsupported `schemaVersion` | event → `<topic>.dlq` (never silent drop) |
| Missing/invalid end-user JWT (inbox) | 401 UNAUTHORIZED |
| `tenant_id` is `wms`/other (not `fan-platform`/`*`) | 403 TENANT_FORBIDDEN |
| Inbox read of another account's / tenant's notification | 404 NOTIFICATION_NOT_FOUND (no leak) |
| Mark-read of an already-READ notification | 200 idempotent no-op |
| Optimistic-lock conflict on Notification (concurrent mark-read) | 409 CONFLICT |
| Postgres down | consume side: handler retries → DLQ on exhaustion; inbox: 5xx (gateway 503 envelope) |
| Kafka down | no events consumed; `consumer_lag` grows; inbox read surface still serves existing rows |

---

## Testing Strategy

- **Unit** — `HandleMembershipEventUseCaseTest` (activated→WELCOME, canceled→CANCELLATION,
  idempotent re-delivery = no duplicate, channel-failure→retry/DLQ path),
  `NotificationTypeTemplateTest` (title/body rendering per payload),
  `ListNotificationsUseCaseTest` (tenant+account scoping, status filter, paging),
  `MarkNotificationReadUseCaseTest` (UNREAD→READ, idempotent re-mark),
  `LoggingChannelAdapterTest` (deterministic mock ref + counter),
  `TenantClaimValidatorTest`, `AllowedIssuersValidatorTest`, `TenantClaimEnforcerTest`.
- **Slice** — `@WebMvcTest` for `NotificationInboxController` (envelope shape,
  pagination, auth, cross-account 404, mark-read idempotency).
- **Integration** (`@Tag("integration")`, Postgres + Kafka Testcontainers,
  WireMock JWKS):
  - `MembershipEventConsumeIntegrationTest` — publish `fan.membership.activated.v1`
    → notification row created (WELCOME) + mock channel invoked; publish
    `canceled.v1` → CANCELLATION row.
  - `IdempotentConsumeIntegrationTest` — re-deliver the same `eventId` → single
    notification row (at-least-once tolerated).
  - `DlqRoutingIntegrationTest` — a forced persistent handler failure (or
    unsupported `schemaVersion`) → event lands in `<topic>.dlq` with the original
    envelope + failure reason; partition continues.
  - `InboxApiIntegrationTest` — list (tenant+account scoped, status filter, paging)
    + mark-read; cross-account/cross-tenant → 404.
  - `MultiTenantIsolationTest` — a `wms` token → 403; cross-tenant inbox read → 404.
  - `NotificationHealthCheckIntegrationTest` — `/actuator/health` 200 unauthenticated.

The default `test` Gradle task excludes `@Tag("integration")`; `integrationTest`
opts in. Per feedback §19, the new `integration` source set MUST be wired into the
fan CI job in the implementation task (FAN-BE-013), not left disabled.

---

## Deploy Dependencies (mention-only — implemented by TASK-FAN-BE-013)

This spec does NOT create build or infra files. FAN-BE-013 wires:

- **settings.gradle** — include `projects:fan-platform:apps:notification-service`.
- **package.json** — dev shortcut script (mirrors community/artist/membership entries).
- **Dockerfile** — host-prebuilt jar pattern (`./gradlew bootJar` before
  `docker compose build`; see project memory `env_gap_docker_host_prebuilt_jar_redeploy_trap`).
- **docker-compose** — `notification-service` + Postgres `fanplatform_notification`
  DB; backing services `expose:` only (no host ports). Traefik routes the inbox via
  `fan-platform.local`.
- **gateway-service route** — `/api/v1/notifications/**` → `notification-service:8080`
  (RewritePath strips to `/api/fan/notifications/**`). The consume path has no
  gateway surface (Kafka inbound only).
- **CI per-service path filter** — pure-positive `code-changed` composition
  (negation prohibited per MONO-074/075); add a `notification-service` entry to the
  positive filter; wire the `integration` source set into the fan CI job.
- **Flyway** — `V1__init.sql` for `fanplatform_notification` (table `notifications`
  + `processed_events` per `libs:java-messaging`). No `outbox` table (terminal
  consumer — no publication).

---

## References

- `platform/architecture-decision-rule.md`
- `platform/service-types/event-consumer.md` (normative)
- `platform/event-driven-policy.md`
- `platform/testing-strategy.md`
- `projects/fan-platform/PROJECT.md` § Service Map v2 (forward-declaration authority)
- `projects/fan-platform/specs/services/membership-service/architecture.md` (the producer + sibling conventions)
- `projects/fan-platform/specs/services/gateway-service/architecture.md`
- `projects/fan-platform/specs/integration/iam-integration.md`
- `projects/fan-platform/specs/contracts/events/fan-membership-events.md` (the consumed contract)
- `rules/traits/integration-heavy.md` § I3 / I8 (fail-closed, retry/DLQ)
- `rules/traits/multi-tenant.md` § M2 (tenant_id everywhere)
- ADR-MONO-005 (workload identity) — not used by the consume path; the inbox is end-user OAuth2
