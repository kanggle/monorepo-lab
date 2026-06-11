# Task ID

TASK-FAN-BE-012

# Title

fan-platform **notification-service** SPEC increment — author `specs/services/notification-service/architecture.md`, the source-of-truth declaration for the v2-tier `event-consumer` service that closes the membership lifecycle loop. membership-service already **emits** `fan.membership.activated.v1` + `fan.membership.canceled.v1` (outbox, FAN-BE-009) with **no consumer**; this spec declares the notification-service that subscribes to them, records a per-fan notification (idempotently), fans out via mock channels (email/push), and exposes a thin in-app **notification inbox** read API. SPEC-only: no production code, build files, or ADR (fan-platform has no ADR dir — architecture lives in `architecture.md`, and PROJECT.md § Service Map v2 is the forward-declaration authority, satisfying HARDSTOP-09). Implementation = TASK-FAN-BE-013.

# Status

ready

# Owner

backend

# Task Tags

- fan-platform
- notification-service
- event-consumer
- spec
- membership

---

# Dependency Markers

- **builds on**: TASK-FAN-BE-008/009 (membership-service spec + impl — the producer of `fan.membership.activated.v1` / `fan.membership.canceled.v1`); `specs/contracts/events/fan-membership-events.md` (the consumed contract, which names "notification-service v2" as the planned consumer).
- **forward-declared by**: PROJECT.md § Service Map v2 (`notification-service | event-consumer | 이벤트 fanout … membership 라이프사이클 이벤트 소비 forward`).
- **followed by**: TASK-FAN-BE-013 (implementation — skeleton + consumer + inbox API + mock channels + infra).
- **gap honoured**: `fan.membership.expired.v1` is forward-declared but NOT emitted (read-time expiry, no sweeper) — the spec subscribes ONLY to the two emitted topics and records the expired topic as a future increment.

# Goal

Produce a complete, implementable `architecture.md` for `notification-service` that satisfies `platform/service-types/event-consumer.md` (Subscribed Topics, consumer-group naming, idempotency, retry/DLQ, schema-version branching, trace propagation, observability) and the fan-platform conventions (Layered, multi-tenant 3-layer isolation, OAuth2 resource server, libs:java-messaging outbox/processed-events), so FAN-BE-013 can build it with no further architectural decisions.

# Scope

- NEW `projects/fan-platform/specs/services/notification-service/architecture.md` — the full declaration:
  - **Identity / Service Type Composition**: `event-consumer` (primary) + a thin `rest-api` inbox read surface; Layered; Postgres `fanplatform_notification`; consumes membership lifecycle events; **no event publication** (terminal consumer).
  - **Subscribed Topics**: `fan.membership.activated.v1`, `fan.membership.canceled.v1` (consumer group `notification-service-membership-events`); `fan.membership.expired.v1` documented as NOT subscribed (not emitted upstream).
  - **Package Layout** (Layered: presentation/application/domain/infrastructure) including the `MembershipEventConsumer`, the `Notification` aggregate, the `NotificationChannelPort` (mock email/push), the inbox controller, and the libs:java-messaging idempotency + outbox-poller-free consumer wiring.
  - **Consume semantics**: idempotency via `processed_events` (eventId); bounded in-process retry → DLQ topic `<topic>.dlq`; schema-version branching; OTel `KafkaPropagator`; emit-not-throw discipline (feedback §18) — a per-message handler failure routes to DLQ, never poisons the partition.
  - **Notification creation rules**: event→notification templating (activated → WELCOME; canceled → CANCELLATION), recipient = `accountId`, tenant-scoped, status UNREAD.
  - **Channel mock boundary**: `NotificationChannelPort` with a deterministic logged mock adapter (NO real FCM/APNs/email) — a real adapter is a future increment.
  - **Inbox read API**: `GET /api/fan/notifications` (paginated, tenant+account scoped) + `POST /api/fan/notifications/{id}/read`; end-user OAuth2; envelope per `platform/`.
  - **Tenant Isolation** (multi-tenant.md M2): gateway + service JwtDecoder + filter; every query tenant+account scoped; cross-tenant → not found.
  - **Observability**: per-topic `messages_processed_total` / `messages_failed_total` / `consumer_lag` / `dlq_depth`; alerts.
  - **Failure Modes / Testing Strategy / Deploy Dependencies (mention-only) / References**.

**Out of scope** (this task): any production/build/infra/Flyway file (those are FAN-BE-013); a real push/email integration; the `fan.membership.expired.v1` sweeper + its consumer; the frontend notification bell (a future FE task); ADR creation (not required — see HARDSTOP-09 note).

# Acceptance Criteria

- **AC-1** `specs/services/notification-service/architecture.md` exists and declares all `platform/service-types/event-consumer.md` § Mandatory items: Subscribed Topics, consumer-group name (`notification-service-membership-events`), idempotency strategy, retry/DLQ (`<topic>.dlq`), schema-version branching, OTel propagation, ordering (partition key `membershipId`), observability metrics.
- **AC-2** The spec subscribes ONLY to the two **emitted** topics and records `fan.membership.expired.v1` as forward-declared-not-consumed, consistent with `fan-membership-events.md`.
- **AC-3** The spec declares Layered architecture, the `fanplatform_notification` Postgres store, the libs:java-messaging `processed_events` idempotency table, and the multi-tenant 3-layer isolation — matching the sibling membership-service conventions.
- **AC-4** The spec declares the inbox read API (list + mark-read), tenant+account scoping, and the mock `NotificationChannelPort` boundary (no real channel).
- **AC-5** No production code, build files, Flyway, or ADR are added by this task (SPEC-only); PROJECT.md § Service Map v2 + this `architecture.md` satisfy HARDSTOP-09 (no new ADR needed, per the membership-service precedent).
- **AC-6** Markdown lints clean; all internal references resolve (event contract, membership architecture, gateway architecture, platform docs).

# Related Specs

- `projects/fan-platform/PROJECT.md` § Service Map v2 (forward-declaration authority)
- `projects/fan-platform/specs/services/membership-service/architecture.md` (sibling conventions + the producer)
- `platform/service-types/event-consumer.md` (normative requirements)
- `platform/event-driven-policy.md`

# Related Contracts

- `projects/fan-platform/specs/contracts/events/fan-membership-events.md` (the consumed contract — already declares notification-service v2 as the planned consumer)

# Edge Cases

- `fan.membership.expired.v1` is NOT emitted upstream — the spec MUST NOT subscribe to it (would be a dead consumer); it is recorded as a future increment, mirroring how the event contract records the gap.
- Duplicate delivery (at-least-once) — idempotent via `processed_events(eventId)`; a re-delivered activated/canceled event creates NO duplicate notification.
- A handler failure (e.g., channel mock error) MUST route to DLQ with the full envelope + reason, never silently drop or poison the partition (forbidden patterns).
- Unknown `schemaVersion` → DLQ, not silent drop.
- Cross-tenant / cross-account inbox reads → not found (no leak), never 403-with-existence.

# Failure Scenarios

- If the spec subscribed to `fan.membership.expired.v1`, the consumer would never receive a message (the producer does not emit it) — AC-2 prevents this dead subscription.
- If idempotency were omitted, at-least-once redelivery would create duplicate notifications — AC-1/AC-3 mandate `processed_events`.
- If the handler threw on a channel error instead of routing to DLQ, the partition would stall (feedback §18 emit-not-throw) — the spec's retry/DLQ section prevents it.
- If a new ADR were required, this task would HARDSTOP — the membership-service precedent (PROJECT.md Service Map v2 + architecture.md as the decision record) confirms none is needed.
