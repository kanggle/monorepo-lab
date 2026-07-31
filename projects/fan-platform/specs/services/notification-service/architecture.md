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
| Event consumption | Kafka, two independent subscriptions — **membership lifecycle**: `fan.membership.activated.v1`, `fan.membership.canceled.v1`, `fan.membership.expired.v1` (group `notification-service-membership-events`); **community interaction**: `community.comment.added.v1`, `community.reaction.added.v1` (group `notification-service-community-events`, TASK-FAN-BE-026) |
| Event publication | **none** — terminal consumer (no outbox, no produced topics) |

### Service Type Composition

`notification-service` is **primarily `event-consumer`** per
`platform/service-types/INDEX.md`: its core role is asynchronously reacting to
membership lifecycle events and community interaction events and fanning out
notifications through channel adapters. It additionally exposes a **small secondary `rest-api`** surface — the
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
│   │   ├── MembershipEventConsumer.java         ← @KafkaListener(activated.v1, canceled.v1, expired.v1) → use case
│   │   ├── MembershipEventParser.java + MembershipEvent.java
│   │   ├── CommunityEventConsumer.java          ← @KafkaListener(comment.added.v1, reaction.added.v1) → use case
│   │   ├── CommunityEventParser.java + CommunityEvent.java
│   │   ├── EventEnvelope.java                   ← package-private helper: shared envelope preamble +
│   │   │                                           schemaVersion gate (TASK-FAN-BE-035, not a Spring bean)
│   │   ├── JsonFields.java                      ← package-private helper: shared requireText/requireInt/
│   │   │                                           optionalText/optionalTextArray/requireInstant accessors
│   │   │                                           (TASK-FAN-BE-035, not a Spring bean)
│   │   └── MalformedEventException.java + UnsupportedSchemaVersionException.java  ← shared by BOTH parsers
│   ├── HandleMembershipEventUseCase.java        ← idempotent: create Notification + dispatch channels
│   ├── HandleCommunityEventUseCase.java         ← idempotent: 0..N Notifications (reply/mention/badge) + dispatch
│   ├── ListNotificationsUseCase.java            ← inbox read (tenant+account scoped, paginated)
│   └── MarkNotificationReadUseCase.java         ← UNREAD → READ (idempotent)
├── domain/
│   ├── notification/
│   │   ├── Notification.java                    ← @Entity (JPA) — notification aggregate
│   │   ├── NotificationRepository.java          ← port
│   │   ├── NotificationType.java                ← WELCOME / CANCELLATION / EXPIRY_REMINDER / REPLY / MENTION / REACTION_BADGE
│   │   └── NotificationStatus.java              ← UNREAD / READ
│   └── channel/
│       └── NotificationChannelPort.java         ← port: deliver(notification) → DeliveryResult
└── infrastructure/
    ├── config/JpaConfig.java + ClockConfig.java + KafkaConsumerConfig.java
    │                                             ← KafkaConsumerConfig owns the single DefaultErrorHandler
    │                                               (retry backoff + DeadLetterPublishingRecoverer → <topic>.dlq)
    │                                               shared by BOTH listener groups — topic-agnostic, no per-family class
    ├── jpa/                                      ← Spring Data adapter for NotificationRepository
    ├── messaging/
    │   ├── idempotency/                          ← libs:java-messaging processed_events guard
    │   └── ConsumerMetrics.java                  ← per-topic processed/failed counters (both consumers)
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
- shared libs: `libs:java-common`, `libs:java-web`, `libs:java-web-servlet` (ADR-MONO-058 § D2 — `CommonGlobalExceptionHandler`), `libs:java-messaging`, `libs:java-observability`, `libs:java-security`, `libs:java-security-servlet` (ADR-MONO-049 § D5-6 — `TenantClaimEnforcer`; ADR-MONO-058 § D5 — `PublicPathSet`; ADR-MONO-058 § D1 — the actor/JWT-claim cluster `ActorClaims`/`ActorContextJwtAuthenticationConverter`/`ActorContextResolver`/`@CurrentActor`)

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
- `application/consumer/` holds the ONLY inbound Kafka surfaces — exactly two
  consumers, one per event family: `MembershipEventConsumer` →
  `HandleMembershipEventUseCase`, and `CommunityEventConsumer` →
  `HandleCommunityEventUseCase` (TASK-FAN-BE-026). Neither embeds business logic;
  each parses its envelope and delegates. **No third inbound surface may be added
  without updating this section and § Subscribed Topics.**
- The two families stay **separate use cases**, not one merged handler: their
  payload shapes and recipient-resolution rules differ (one membership event → one
  recipient; one comment event → 0..N recipients across two notification types).
  Only the idempotency / persist / dispatch mechanics are common.
- **No outbound synchronous call to a sibling fan-platform service.** Community
  alerts are routed purely from the enriched event payload
  (`postAuthorAccountId` / `mentionedAccountIds` — community-events.md
  § Recipient-routing fields). The only outbound HTTP in this service is the
  optional real EMAIL / FCM channel adapter to an *external* provider
  (§ Channel Mock Boundary); nothing calls community/artist/membership-service.
- `infrastructure/security/` re-validates `tenant_id` for the inbox routes even
  though the gateway already does — fail-closed defense-in-depth (§ Tenant Isolation).

---

## Domain Model

`Notification` is the single aggregate — one delivered/queued notification held
for one fan account, derived from one consumed event (a membership lifecycle
event, or a community interaction event since TASK-FAN-BE-026).

| Field | Type | Notes |
|---|---|---|
| `id` | string (UUID v7) | aggregate id |
| `tenantId` | string | row-level isolation; always `fan-platform` in this project |
| `accountId` | string | the recipient fan = IAM `sub` claim (membership: the event payload `accountId`; community: the routed recipient — post author or a mentioned account) |
| `type` | `NotificationType` | `WELCOME` \| `CANCELLATION` \| `EXPIRY_REMINDER` \| `REPLY` \| `MENTION` \| `REACTION_BADGE` |
| `title` | string | rendered from the type template |
| `body` | string | rendered from the event payload (tier, plan window, commenter, reaction type, …) |
| `status` | `NotificationStatus` | `UNREAD` \| `READ` |
| `sourceEventId` | string (UUID) | the consumed envelope `eventId` — the idempotency key (see the composite unique below) |
| `sourceEventType` | string | `fan.membership.{activated,canceled,expired}` \| `community.{comment,reaction}.added` |
| `membershipId` | string (UUID)? | the originating membership aggregate id. **Nullable since V3** — non-null only for membership-sourced rows |
| `postId` | string (UUID)? | the correlating post. Non-null only for community-sourced rows (V3) |
| `createdAt` | timestamptz | consume time |
| `readAt` | timestamptz? | set on UNREAD → READ; null while UNREAD |
| `version` | long | optimistic lock |

`membershipId` and `postId` are **mutually alternative origin correlations** —
exactly one is non-null per row, decided by the originating use case. The schema
deliberately does **not** enforce that exclusivity with a CHECK (the invariant is
owned by the two consumers; a DB constraint is not required by any current
acceptance criterion). The inbox DTO uses `@JsonInclude(NON_NULL)`, so a
community-sourced item simply omits `membershipId`.

### Event → Notification mapping

| Consumed topic | `NotificationType` | Title (template) | Body (from payload) |
|---|---|---|---|
| `fan.membership.activated.v1` | `WELCOME` | "Welcome to {tier} membership" | window `[validFrom … validTo]`, `planMonths` |
| `fan.membership.canceled.v1` | `CANCELLATION` | "Your {tier} membership was canceled" | `canceledAt`, optional `reason` |
| `fan.membership.expired.v1` | `EXPIRY_REMINDER` | "Your {tier} membership has expired" | `validTo` (window end) |
| `community.comment.added.v1` → `postAuthorAccountId` | `REPLY` | "New reply on your post" | `authorAccountId` (commenter) + `postId` |
| `community.comment.added.v1` → each `mentionedAccountIds` entry | `MENTION` | "You were mentioned in a comment" | `authorAccountId` (commenter) + `postId` |
| `community.reaction.added.v1` → `postAuthorAccountId` | `REACTION_BADGE` | "Someone reacted to your post" | `reactorAccountId` + `reactionType` + `postId` |

**One event → 0..N notifications (community only).** `community.comment.added`
is the only consumed event whose notification type is **recipient-role dependent**:
the same envelope yields a REPLY for the post author *and* a MENTION per mentioned
account. `NotificationType.fromEventType` therefore rejects that event type
(it maps only 1:1 event types, incl. `community.reaction.added` → `REACTION_BADGE`);
`HandleCommunityEventUseCase` selects the constant per recipient.

**Zero-notification outcomes are successes** (logged, event still marked
processed, no DLQ): self-notify suppression (actor == post author, or a
self-mention), an empty `mentionedAccountIds`, and a **pre-enrichment in-flight
event with no `postAuthorAccountId`** (a rollout artifact, explicitly not a
malformed event — community-events.md § Recipient-routing fields).

**Body rendering names accounts by `accountId`**, not display name: the events
carry no display name and cross-service reads / sync calls are forbidden, so a
friendlier name would require a further contract change, not a lookup.

All three membership lifecycle topics are now consumed (`expired.v1` since
TASK-FAN-BE-014 — the producer's expiry sweeper emits it; see
`fan-membership-events.md`). The expiry payload carries only `validTo` (plus the
common id/tenant/account/tier fields); `planMonths` / `validFrom` / `canceledAt` /
`reason` are absent, so the parser's `EVENT_EXPIRED` case reads only `validTo`.
Adding `EXPIRY_REMINDER` to the stored `type` requires a **V2 migration** to extend
the `ck_notification_type` CHECK allow-list (§16 — the Testcontainers IT is the
authoritative gate).

### Migrations

| Version | Change |
|---|---|
| `V1__init.sql` | `notifications` + `processed_events`; `type` allow-list `WELCOME, CANCELLATION`; `UNIQUE (source_event_id)` |
| `V2__expiry_reminder_type.sql` | `type` allow-list += `EXPIRY_REMINDER` (TASK-FAN-BE-014) |
| `V3__community_notification_types.sql` | TASK-FAN-BE-026: `membership_id` **DROP NOT NULL**; add nullable `post_id VARCHAR(36)` + index `(tenant_id, post_id)`; `type` allow-list += `REPLY, MENTION, REACTION_BADGE`; **`uq_notification_source_event` widened from `(source_event_id)` to `(source_event_id, account_id, type)`** |

The unique-key widening is load-bearing, not cosmetic: one
`community.comment.added` event legitimately writes a REPLY row *and* one MENTION
row per mentioned account, all sharing the same `source_event_id`, which the
single-column UNIQUE would have rejected. The composite preserves the
secondary-guard semantics exactly — a duplicate delivery regenerates the identical
`(event, recipient, type)` tuples and still collides — and degenerates to the old
behaviour for membership events (one event → one recipient → one type).

---

## Subscribed Topics (event-consumer.md § Subscription Ownership)

| Topic | Producer | Consumer group | Partition key | Handler | Status |
|---|---|---|---|---|---|
| `fan.membership.activated.v1` | membership-service | `notification-service-membership-events` | `membershipId` | `MembershipEventConsumer#onActivated` | consumed |
| `fan.membership.canceled.v1` | membership-service | `notification-service-membership-events` | `membershipId` | `MembershipEventConsumer#onCanceled` | consumed |
| `fan.membership.expired.v1` | membership-service (expiry sweeper) | `notification-service-membership-events` | `membershipId` | `MembershipEventConsumer#onExpired` | consumed (TASK-FAN-BE-014) |
| `community.comment.added.v1` | community-service | `notification-service-community-events` | `postId` | `CommunityEventConsumer#onCommentAdded` | consumed (TASK-FAN-BE-026) |
| `community.reaction.added.v1` | community-service | `notification-service-community-events` | `postId` | `CommunityEventConsumer#onReactionAdded` | consumed (TASK-FAN-BE-026) |

- **Consumer groups** (`<service>-<purpose>` convention): one per event family —
  `notification-service-membership-events` and
  `notification-service-community-events`. Separate groups so community lag /
  replay / rebalance is independent of the membership subscription.
- **Ordering**: per-`membershipId` ordering is preserved by the membership
  producer's partition key; cross-membership ordering is NOT relied upon. WELCOME
  and CANCELLATION for the same membership therefore arrive in causal order.
  Community events are keyed by `postId`, so interactions on the same post are
  ordered; ordering across posts is not relied upon (each notification is
  independent).
- **Not subscribed**: `community.post.published.v1` and
  `community.post.status_changed.v1` remain produced-but-unconsumed. Follower
  fan-out on `post.published` needs a follow graph + a `community.follow.added`
  event that does not exist yet (TASK-FAN-BE-026 § Out of scope);
  `post.status_changed` is a search-service concern, not notification.

---

## Consume Semantics

### Idempotency (event-consumer.md § Idempotency; idempotent-consumer.md)

- Strategy: **idempotency table keyed by `eventId`** via `libs:java-messaging`'s
  `processed_events` (24h+ retention). Before handling, the use case checks/inserts
  `processed_events(eventId)`; a duplicate delivery (at-least-once) short-circuits
  with NO second `Notification` row and NO second channel dispatch. **Both**
  consumers use the same store and the same guard — the eventId space is global,
  so a community event and a membership event can never collide on it.
- Secondary natural guard: `Notification (sourceEventId, accountId, type)` is
  unique — a race that slipped past the processed-events check still cannot create
  a duplicate (DB unique constraint → caught + treated as already-processed). It is
  a **composite** rather than `sourceEventId` alone so one community comment event
  can legally fan out to several recipients (§ Migrations, V3).
- An event that resolves to **zero** notifications (self-notify suppression, empty
  mention list, missing recipient on a pre-enrichment event) is still marked
  processed — a redelivery must not re-run the suppression logic and must not DLQ.

### Retry and DLQ (event-consumer.md § Retry and DLQ; consumer-retry-dlq.md)

- Transient handler failures (e.g., a channel mock throwing a simulated transient
  error, a DB blip): in-process exponential backoff with jitter, **max 3 retries**.
- Persistent failures (retries exhausted) and **un-parseable / unsupported-schema**
  envelopes: routed to the DLQ topic **`<topic>.dlq`** (e.g.
  `fan.membership.activated.v1.dlq`, `community.comment.added.v1.dlq`) with the
  **full original envelope + failure reason** header. The event is then marked
  consumed (offset committed) so the partition is never poisoned.
  The mechanism is a **single topic-agnostic** `DefaultErrorHandler` bean in
  `infrastructure/config/KafkaConsumerConfig` (backoff + a
  `DeadLetterPublishingRecoverer` whose destination resolver appends `.dlq` to the
  source topic), auto-wired into the listener container factory. It therefore
  covers **both** consumer groups with no per-family publisher class; the
  `MalformedEventException` / `UnsupportedSchemaVersionException` non-retryable
  registration is likewise shared by both parsers.
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

The default adapters `LoggingEmailChannelAdapter` + `LoggingPushChannelAdapter` are
**deterministic logged mocks** — each logs a structured delivery line + returns a
synthetic `ref` (e.g. `mockmail_<uuid>`, `mockpush_<uuid>`) and increments a
per-channel counter. The mocks are the default (dev + the Testcontainers IT) and
perform no real I/O.

### Real EMAIL channel — `http` mode (TASK-FAN-BE-016)

The EMAIL channel has a **real** alternative: `HttpEmailChannelAdapter`, a
provider-agnostic **HTTP** transactional-email integration (the service's first real
external channel). Channel selection is by property:

- `fanplatform.notification.email.mode = mock` (default) → `LoggingEmailChannelAdapter`.
- `fanplatform.notification.email.mode = http` → `HttpEmailChannelAdapter`, which
  POSTs `${provider-base-url}/emails` with an API-key auth header and a JSON
  `{from, to, subject, body}`, via a `ResilienceClientFactory` RestClient (connect /
  read timeouts). A 2xx `{ "id": … }` is a delivered ref.

The two are mutually-exclusive `@ConditionalOnProperty` beans → exactly **one** EMAIL
`NotificationChannelPort` bean under either mode; PUSH stays a mock (FCM/APNs is a
separate future increment). No real SDK is added — the integration is plain RestClient
over `libs:java-common` + `spring-boot-starter-web`, so the *no real push/email SDK*
forbidden-dependency line still holds.

- **Best-effort, never-throw.** The fan-out runs inside the use-case `@Transactional`;
  a *real* delivery failure (non-2xx / transport / timeout / unparseable body) is
  **caught**, recorded on `…{outcome=failed}`, logged `warn`, and returned as
  `DeliveryResult(false, …)`. It MUST NOT throw — otherwise a transient email outage
  would roll the transaction back and discard the durable in-app notification. (This
  refines the earlier "a throwing channel rolls the unit back" note for real delivery;
  v1 has no automatic redelivery of a failed real send — the inbox row is the record.)
- **Recipient limitation.** The consumed event carries no recipient email (only
  `accountId` = IAM `sub`), and cross-service table reads are forbidden, so the real
  adapter sends to the deterministic synthetic `${accountId}@${recipient-domain}`. A
  production version would enrich the address via a preferences/profile lookup
  (out of scope).
- **No CI side effects.** The IT runs the default `mock` mode; the adapter unit test
  points RestClient at a MockWebServer — no real provider is contacted in CI.

### Real PUSH channel — `fcm` mode (TASK-FAN-BE-017)

The PUSH channel has the symmetric real alternative: `HttpFcmPushChannelAdapter`, a
**Firebase Cloud Messaging (FCM) HTTP v1** integration. Selection is by property:

- `fanplatform.notification.push.mode = mock` (default) → `LoggingPushChannelAdapter`.
- `fanplatform.notification.push.mode = fcm` → `HttpFcmPushChannelAdapter`, which POSTs
  `${fcm-base-url}/v1/projects/${project-id}/messages:send` with `Authorization: Bearer
  ${api-key}` and the FCM v1 JSON `{"message":{"topic":"<prefix><accountId>",
  "notification":{"title":…,"body":…}}}`, via a `ResilienceClientFactory` RestClient.
  A 2xx `{"name":"projects/…/messages/<id>"}` is the delivered ref.

Same invariants as the EMAIL adapter — exactly **one** PUSH `NotificationChannelPort`
bean per mode (EMAIL selection independent); **best-effort/never-throw** (failures →
`…{outcome=failed}`, no transaction rollback); **no real SDK** (plain RestClient).

- **Topic targeting (not device tokens).** The event carries no device registration
  token (only `accountId`) and cross-service reads are forbidden, so the adapter targets
  the FCM **topic** `${topic-prefix}<accountId>` (clients subscribe to their per-account
  topic) — no device-token registry is needed. Device-token targeting would need a
  device registry / preferences lookup (out of scope). `accountId` is sanitized to the
  FCM topic charset `[a-zA-Z0-9-_.~%]+`.
- **Auth.** `Authorization: Bearer ${api-key}`. Real FCM v1 mints a short-lived OAuth2
  access token from a Google service account; that minting is out of scope (a real
  deployment supplies a current token).
- **No CI side effects.** The IT runs the default `mock` mode; the unit test points
  RestClient at a MockWebServer.

A real **APNs** adapter remains a further increment, wired the same way
(`@ConditionalOnProperty` / profile), with the mock retained. The domain and use-case
layers are unchanged by any of these swaps.

Delivery is **best-effort and decoupled from the inbox write**: the `Notification`
row is the durable record (always created on a fresh event); a mock channel that
throws a *simulated* transient error retries/DLQs the event, while the real EMAIL
adapter is best-effort (never throws) so the inbox row, once written under the
idempotency guard, stays authoritative.

---

## Inbox Read API (the secondary rest-api surface)

End-user OAuth2 (the fan's access token). All routes are tenant + account scoped.

| Method | Path | Semantics |
|---|---|---|
| `GET` | `/api/fan/notifications?unread={true\|false}&page=&size=&sort=` | the caller's own notifications, newest first; `unread` optional filter; paginated envelope |
| `POST` | `/api/fan/notifications/{id}/read` | `UNREAD → READ` (sets `readAt`); re-marking a READ notification is an idempotent 200 no-op |

- The recipient is always the authenticated `accountId` (`sub`); a fan can read
  ONLY their own notifications. A notification belonging to another account or
  tenant → 404 `NOTIFICATION_NOT_FOUND` (no existence leak).
- **Read filter** — `unread` is the normative cross-domain param
  ([`platform/contracts/notification-inbox-contract.md`](../../../../../platform/contracts/notification-inbox-contract.md) § 2.1): `unread=true`
  → unread only, `unread=false` → read only, absent → all. The pre-existing
  `status={UNREAD|READ}` param is retained as a **back-compat alias**, applied
  only when `unread` is absent (ADR-MONO-043 P2 / TASK-FAN-BE-023).
- **Item shape** — list/detail items conform to the § 1 envelope: each carries
  `sourceDomain="fan"` (attribution for the console-bff aggregator) and a
  nullable `deepLink` (currently `null` — fan derives no in-app link yet, omitted
  under NON_NULL). The fan-native `status`/`membershipId` fields are preserved as
  non-normative domain extensions (contract § 1.2).
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
| Community event where the actor IS the post author (self-comment / self-reaction / self-mention) | no notification; logged; event marked processed (AC-3) |
| Community event with **no** `postAuthorAccountId` (emitted before the TASK-FAN-BE-026 producer enrichment) | no addressable recipient → skip + log + mark processed. **NOT** a DLQ case — it is an expected rollout artifact, not a malformed event |
| Community event whose recipient account is deleted/inactive | the notification row is still written (no cross-service read exists to detect it); it is simply never fetched by that account's inbox |
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
  `HandleCommunityEventUseCaseTest` (comment→REPLY, mention fan-out one row per
  mentioned account + dedupe of a repeated account, reaction→REACTION_BADGE,
  **self-notify suppression for both REPLY and REACTION_BADGE plus self-mention**,
  idempotent re-delivery, **missing `postAuthorAccountId` → skip + mark processed,
  never throw**),
  `CommunityEventParserTest` (both envelopes, malformed JSON, missing required
  field, unsupported `schemaVersion`, absent `mentionedAccountIds` → empty list,
  absent `postAuthorAccountId` → null not an exception, unrelated community
  eventType rejected),
  `NotificationTypeTest` (1:1 mappings incl. `community.reaction.added` →
  REACTION_BADGE; `community.comment.added` rejected as recipient-role dependent),
  `NotificationTypeTemplateTest` (title/body rendering per payload, incl. the
  REPLY / MENTION / REACTION_BADGE renderers),
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
  - `CommunityEventConsumeIntegrationTest` — publish `community.comment.added.v1`
    → REPLY row for the post author (`post_id` set, `membership_id` NULL); a
    mention-carrying event → REPLY + one MENTION row per mentioned account, all
    sharing one `source_event_id` (proves the V3 composite unique); publish
    `community.reaction.added.v1` → REACTION_BADGE row; a self-interaction → no
    row while a following third-party event still lands (proves suppression does
    not stall the partition); duplicate community `eventId` → one row. This IT is
    the **authoritative gate for the V3 migration** — the new `type` values must
    pass `ck_notification_type`, `membership_id` must accept NULL, and `post_id`
    must exist; a Docker-free `:check` slice cannot catch any of those (§16).
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
- `projects/fan-platform/specs/contracts/events/community-events.md` (the second consumed contract — § Recipient-routing fields)
- `rules/traits/integration-heavy.md` § I3 / I8 (fail-closed, retry/DLQ)
- `rules/traits/multi-tenant.md` § M2 (tenant_id everywhere)
- ADR-MONO-005 (workload identity) — not used by the consume path; the inbox is end-user OAuth2
