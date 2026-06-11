# Task ID

TASK-FAN-BE-013

# Title

fan-platform **notification-service** IMPLEMENTATION — bootstrap the new `event-consumer` service declared by TASK-FAN-BE-012's `specs/services/notification-service/architecture.md`, closing the membership lifecycle loop. membership-service already **emits** `fan.membership.activated.v1` + `fan.membership.canceled.v1` (outbox, FAN-BE-009) with **no consumer**; this task builds the consumer service that subscribes to them, records a per-fan notification idempotently, fans out via deterministic mock channels (logging email/push), and exposes a thin in-app notification inbox read API. Skeleton + membership event consumer + idempotency (`processed_events`) + retry/DLQ (`<topic>.dlq`) + schema-version branching + mock `NotificationChannelPort` + inbox API + multi-tenant 3-layer isolation + Postgres `fanplatform_notification` (Flyway V1, **no outbox** — terminal consumer) + build/infra/CI wiring.

# Status

ready

# Owner

backend

# Task Tags

- fan-platform
- notification-service
- event-consumer
- membership
- implementation

---

# Dependency Markers

- **builds on**: TASK-FAN-BE-012 (the SoT spec `specs/services/notification-service/architecture.md`); TASK-FAN-BE-008/009 (membership-service — the producer of the two consumed topics); `specs/contracts/events/fan-membership-events.md` (the consumed contract).
- **prerequisite**: membership-service emits `fan.membership.activated.v1` / `fan.membership.canceled.v1` through `MembershipOutboxPollingScheduler` (already live).
- **gap honoured**: `fan.membership.expired.v1` is forward-declared but NOT emitted upstream (read-time expiry, no sweeper) — this service subscribes ONLY to the two emitted topics and does NOT subscribe to `expired.v1` (a dead consumer would never receive a message).
- **followed by**: a future FE notification-bell task + a real channel adapter increment (re-implements `NotificationChannelPort`) + the `expired.v1` sweeper + its consumer.

# Goal

Implement `projects/fan-platform/apps/notification-service` exactly as declared in `specs/services/notification-service/architecture.md`, satisfying `platform/service-types/event-consumer.md` (subscribed topics, consumer-group naming, idempotency, retry/DLQ, schema-version branching, OTel propagation, observability) and the fan-platform Layered conventions, so the membership lifecycle loop is closed with no further architectural decisions.

# Scope

- **NEW `projects/fan-platform/apps/notification-service`** (Layered: presentation / application / domain / infrastructure):
  - `MembershipEventConsumer` — two `@KafkaListener` methods (`fan.membership.activated.v1`, `fan.membership.canceled.v1`), consumer group `notification-service-membership-events`, raw-`String` value parsed by the service `ObjectMapper`.
  - `HandleMembershipEventUseCase` — idempotent (`processed_events` by `eventId` + unique `sourceEventId` secondary guard): create a `Notification` + dispatch to every `NotificationChannelPort`.
  - schema-version branching (current `schemaVersion=1`; unknown → DLQ); retry → DLQ topic `<topic>.dlq` via `DefaultErrorHandler` + `DeadLetterPublishingRecoverer` (`.dlq` suffix, NOT `.DLT`); emit-not-throw discipline (a per-message failure routes to DLQ, never poisons the partition).
  - `Notification` aggregate (JPA entity; `WELCOME` / `CANCELLATION`, `UNREAD` / `READ`); event→notification templating; deterministic mock `LoggingEmailChannelAdapter` + `LoggingPushChannelAdapter` behind `NotificationChannelPort` (NO real channel).
  - inbox read API: `GET /api/fan/notifications` (paginated, tenant+account scoped, optional `status` filter) + `POST /api/fan/notifications/{id}/read` (idempotent UNREAD→READ); end-user OAuth2.
  - multi-tenant 3-layer isolation (gateway already; service `JwtDecoder` validators + `TenantClaimEnforcer` filter); every query tenant+account scoped; cross-tenant/cross-account → 404.
  - **terminal consumer**: NO outbox, NO produced topic — `OutboxAutoConfiguration` + `OutboxMetricsAutoConfiguration` excluded; idempotency uses a service-owned `processed_events` table (libs:java-messaging shape).
  - observability: per-topic `messages_processed_total` / `messages_failed_total` / `dlq` counters + per-channel `notification_channel_deliveries_total{channel,outcome}`; `/actuator/health` + `/actuator/prometheus`.
  - Postgres `fanplatform_notification` — Flyway `V1__init.sql` (`notifications` + `processed_events`; NO `outbox`).
- **Wiring**: `settings.gradle` include; `package.json` (n/a — backend only, no FE script); `Dockerfile` (host-prebuilt jar); `docker-compose.yml` + postgres init (`fanplatform_notification` DB, expose-only); gateway-service route (`/api/v1/notifications/**` → `/api/fan/notifications/**`); CI `build-and-test` fan step (`:check`) + `fan-integration-tests` job (`:integrationTest`). The `fan` path filter is pure-prefix (`projects/fan-platform/**`) so no path-filter edit is required.
- **Tests**: unit (`HandleMembershipEventUseCaseTest`, template, `ListNotificationsUseCaseTest`, `MarkNotificationReadUseCaseTest`, logging-channel, validators), slice (`@WebMvcTest` inbox controller), integration (`@Tag("integration")`, Postgres + Kafka Testcontainers, WireMock JWKS): consume happy path, idempotent re-delivery, DLQ routing (unsupported `schemaVersion`), inbox API, multi-tenant isolation, health.

**Out of scope**: a real push/email integration; the `fan.membership.expired.v1` sweeper + its consumer; the FE notification bell; any change to membership-service (it already emits both topics); ADR creation (architecture.md + PROJECT.md § Service Map v2 satisfy HARDSTOP-09 — no new ADR, per the membership precedent).

# Acceptance Criteria

- **AC-1** `notification-service` boots, consumes `fan.membership.activated.v1` → persists a `WELCOME` notification + invokes the mock channels; consumes `fan.membership.canceled.v1` → `CANCELLATION` notification. Consumer group = `notification-service-membership-events`; partition key = `membershipId`.
- **AC-2** Re-delivery of the same `eventId` (at-least-once) creates NO duplicate notification and NO second dispatch (`processed_events` guard + unique `sourceEventId`).
- **AC-3** An unsupported `schemaVersion` (or unparseable envelope) is routed to `<topic>.dlq` with the original value, NOT silently dropped, and the listener container keeps running (no partition stall).
- **AC-4** Inbox API: `GET /api/fan/notifications` returns the caller's own notifications (newest first, optional `status` filter, paginated envelope); `POST /api/fan/notifications/{id}/read` is an idempotent UNREAD→READ; a cross-account / cross-tenant id → 404 `NOTIFICATION_NOT_FOUND` (no existence leak); a `wms`/foreign `tenant_id` → 403 `TENANT_FORBIDDEN`.
- **AC-5** Service does NOT subscribe to `fan.membership.expired.v1` (not emitted upstream) and publishes NO events (no outbox, no produced topic; `OutboxAutoConfiguration` excluded).
- **AC-6** Flyway `V1__init.sql` creates `notifications` + `processed_events` (NO `outbox`) in `fanplatform_notification`; Hibernate `ddl-auto=validate` passes against it.
- **AC-7** `./gradlew :projects:fan-platform:apps:notification-service:check` is GREEN and Docker-free (unit + slice only); `:integrationTest` (Testcontainers) is GREEN and wired into the `fan-integration-tests` CI job; the service builds into `build-and-test`.
- **AC-8** No production code or build files are added to any OTHER service; membership-service is untouched.

# Related Specs

- `projects/fan-platform/specs/services/notification-service/architecture.md` (SoT — the full declaration)
- `projects/fan-platform/specs/services/membership-service/architecture.md` (the producer + sibling Layered conventions)
- `platform/service-types/event-consumer.md` (normative)
- `platform/event-driven-policy.md` (`.dlq` suffix, retry policy)

# Related Contracts

- `projects/fan-platform/specs/contracts/events/fan-membership-events.md` (the consumed contract — envelope `eventId/eventType/source/occurredAt/schemaVersion/partitionKey/payload`)

# Edge Cases

- `fan.membership.expired.v1` is NOT emitted — MUST NOT subscribe (dead consumer). Recorded as a future increment.
- Duplicate delivery (at-least-once) — idempotent via `processed_events(eventId)`; a re-delivered activated/canceled event creates NO duplicate notification.
- A handler failure (unsupported `schemaVersion`, unparseable envelope, channel error after retries) → DLQ with the full value, never silent drop or partition stall (emit-not-throw, feedback §18).
- Unknown `schemaVersion` → DLQ (non-retryable), not retried 3× pointlessly.
- Cross-tenant / cross-account inbox reads → 404 (no leak); foreign `tenant_id` → 403.
- Terminal-consumer wiring: `libs:java-messaging` `OutboxAutoConfiguration` MUST be excluded (no `outbox` table) while `processed_events` idempotency is retained (service-owned table, libs shape) — feedback §13.

# Failure Scenarios

- If `OutboxAutoConfiguration` were not excluded, the context would require an `outbox` table that does not exist → Flyway/Hibernate validate failure (feedback §13). AC-5/AC-6 prevent this.
- If the consumer threw on a channel error without DLQ routing, the partition would stall (feedback §18 emit-not-throw) — the `DefaultErrorHandler` + `.dlq` recoverer prevents it (AC-3).
- If idempotency were omitted, at-least-once redelivery would create duplicate notifications — AC-2 mandates `processed_events` + unique `sourceEventId`.
- If the new `integration` source set were left out of the fan CI job, the Testcontainers suite would run nowhere on CI (feedback §19/§20, the FAN-BE-011 lesson) — AC-7 wires it into `fan-integration-tests`.
- If the inbox leaked another account's/tenant's notification with a 403-with-existence instead of a 404, it would be an enumeration oracle — AC-4 mandates 404 no-leak.
