# Task ID

TASK-FAN-BE-013

# Title

fan-platform **notification-service** IMPLEMENTATION — bootstrap the new `event-consumer` service declared by TASK-FAN-BE-012's `specs/services/notification-service/architecture.md`, closing the membership lifecycle loop. membership-service already **emits** `fan.membership.activated.v1` + `fan.membership.canceled.v1` (outbox, FAN-BE-009) with **no consumer**; this task builds the consumer service that subscribes to them, records a per-fan notification idempotently, fans out via deterministic mock channels (logging email/push), and exposes a thin in-app notification inbox read API. Skeleton + membership event consumer + idempotency (`processed_events`) + retry/DLQ (`<topic>.dlq`) + schema-version branching + mock `NotificationChannelPort` + inbox API + multi-tenant 3-layer isolation + Postgres `fanplatform_notification` (Flyway V1, **no outbox** — terminal consumer) + build/infra/CI wiring.

# Status

done

> **완료 (2026-06-11)**: impl PR #1282 (squash `16be2fc2`). 3차원 ✓ (state=MERGED / origin/main tip=`16be2fc2` 일치 / pre-merge 0 failing — 전 job pass 포함 **Build & Test + Integration(fan-platform) Testcontainers**). 신규 5번째 fan 서비스 `notification-service` 부트스트랩 (73파일), membership 라이프사이클 이벤트 루프 닫음. **Layered**, Postgres `fanplatform_notification`, **terminal consumer**(no outbox — `OutboxAutoConfiguration`+`OutboxMetricsAutoConfiguration` exclude + 자체 `processed_events` table=feedback §13; erp notification 선례). `MembershipEventConsumer` 2 `@KafkaListener`(activated/canceled, group `notification-service-membership-events`, key `membershipId`) → `MembershipEventParser`(schemaVersion 분기, unsupported/malformed=non-retryable) → `HandleMembershipEventUseCase`(멱등=processed_events guard + unique `source_event_id`; create WELCOME/CANCELLATION Notification + 전 채널 dispatch). DLQ=`DefaultErrorHandler`+`DeadLetterPublishingRecoverer`→`<topic>.dlq`(`.dlq` suffix, security-service 패턴; erp `.DLT` 아님), emit-not-throw §18. mock `NotificationChannelPort`(LoggingEmail/Push, `mockmail_`/`mockpush_` ref + 채널 counter). inbox API(`GET /api/fan/notifications` paginated+status filter + `POST /{id}/read` 멱등; cross-account/tenant→404 no-leak, wms→403); 단일 end-user 보안 체인(`/internal` 없음) + 3계층 테넌트 격리. `expired.v1`=forward-declared 미구독(미발행). 배선: settings.gradle·docker-compose+`fanplatform_notification` DB(expose-only)·postgres init·gateway route(`/api/v1/notifications/**`→`/api/fan/notifications/**`)·CI fan `:check`+`fan-integration-tests` job(fan path-filter=pure-prefix 수정불요). membership-service 무변경, 신규 ADR 0(HARDSTOP-09=architecture.md+PROJECT.md Service Map v2). **테스트**: unit(use case·template·parser·list/mark-read·channels·validators) + slice(`@WebMvcTest` inbox) + IT(Postgres+Kafka Testcontainers, WireMock JWKS — consume happy·멱등 재전달·DLQ 라우팅·inbox·multi-tenant·health) — `:check` 로컬 GREEN + **Integration(fan-platform) CI 첫 실행 GREEN**(§19/§20 latent 0). ⚠️로컬 Testcontainers=Rancher npipe `MalformedChunkCodingException`(라이브 federation-e2e 데모스택 유지 위해 엔진 미재시작)→CI Linux 위임. ⚠️footprint job flake=Docker Hub registry timeout(`ci.yml` 편집→`workflows=true` 트리거, 변경 무관)→rerun GREEN. follow-up=멤버십 히스토리 페이지·FE notification-bell·실채널 어댑터 increment·`expired.v1` sweeper. 분석=Opus 4.8 / 구현=Opus 4.8.

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
