# notification-service — Architecture

This document declares the internal architecture of `notification-service`.
All implementation tasks targeting this service must follow this declaration
and `platform/architecture-decision-rule.md`.

---

## Identity

| Field | Value |
|---|---|
| Service name | `notification-service` |
| Service Type | `event-consumer` (single — alert routing only in v1) |
| Architecture Style | **Hexagonal** (matches sibling services; channel-adapter substitution drives the choice) |
| Primary language / stack | Java 21, Spring Boot |
| Bounded Context | **Admin / Operations** (per `rules/domains/wms.md`) — alert side of operations |
| Deployable unit | `apps/notification-service/` |
| Data store | PostgreSQL (owned) — delivery audit + routing config |
| Event publication | Kafka via outbox — `notification.delivered.v1` only (audit trail) |
| Event consumption | Kafka with eventId-based dedupe across all alert-worthy WMS topics |

### Service Type — single

`notification-service` is a pure event consumer in v1. No REST surface, no
admin UI. Routing rules are configured via Flyway-seeded rows + environment
overrides — **operator UX for managing channels / preferences is v2**, owned
by `admin-service`.

Read `platform/service-types/event-consumer.md`.

---

## Responsibility

`notification-service` owns:

- **Alert routing** — subscribe to alert-worthy WMS events, resolve target
  channel(s), enqueue delivery
- **Delivery execution** — call out to external channel vendors (Slack v1;
  email / push v2) with retry, circuit-breaker, and idempotent send
- **Delivery audit** — persistent record of every (event → channel → outcome)
  attempt for ops investigation and SLA review
- **Routing config** — per-event-type → channel mapping (seeded via Flyway in
  v1; admin UI in v2)

It does **not** own:

- Authentication / user identity (`gateway-service` / `admin-service`)
- Notification *content* business rules (each source service decides what
  warrants an alert by emitting an event; `notification-service` only
  formats and routes)
- Cross-tenant routing (single-tenant in v1 per `PROJECT.md`)
- Read-model / dashboard projections (`admin-service`)
- In-app / WebSocket push (out of scope per `PROJECT.md`)

---

## Out of Scope (v1)

- Email channel (SMTP / SES) — v2
- Mobile push (FCM / APNs) — v2
- SMS — explicit non-goal (cost / regulatory)
- Per-user preference UI (mute, schedule, severity threshold) — v2 via
  `admin-service`
- Bidirectional Slack interactions (slash commands, button callbacks) — v2
- Template management UI — v2

v1 ships a minimal but production-shaped slice: ≥1 alert-worthy event class
flows through to a real external channel (Slack webhook), with retry / DLT /
idempotent delivery and audit. Quality bar matches sibling services.

---

## Architecture Style: Hexagonal

### Rationale

`notification-service` looks small in v1 but its surface area is **defined
by external integrations** — Slack now, email / push later. Hexagonal
isolates each channel behind a port, so:

1. Adding a new channel in v2 = new adapter implementation, zero domain
   churn.
2. Vendor swap (Slack → Mattermost, SES → SendGrid) is local to the
   adapter.
3. Integration-heavy resilience patterns (retry, circuit breaker, fallback)
   are wired at the adapter boundary, keeping the application service
   pure.

This matches the project-wide Hexagonal default declared in master /
inventory / inbound / outbound. No `## Overrides` needed.

### Package Structure

```
com.wms.notification/
├── domain/
│   ├── alert/                  # Alert (aggregate root), AlertSeverity, RoutingDecision
│   ├── delivery/               # NotificationDelivery, DeliveryStatus, DeliveryAttempt
│   ├── routing/                # RoutingRule, ChannelTarget, EventTypeMatcher
│   └── error/                  # NotificationDomainException + subtypes
├── application/
│   ├── port/
│   │   ├── inbound/            # ProcessInboundEventUseCase, RetryFailedDeliveryUseCase
│   │   └── outbound/           # ChannelPort (sealed: SlackChannelPort), DeliveryRepository,
│   │                           #   RoutingRuleRepository, AlertDedupePort, OutboxPort
│   └── service/                # AlertRoutingService, DeliveryExecutor
├── adapter/
│   ├── inbound/
│   │   └── messaging/          # @KafkaListener consumers (one per source topic group)
│   └── outbound/
│       ├── slack/              # SlackChannelAdapter (Resilience4j-wrapped)
│       ├── persistence/jpa/    # NotificationDeliveryJpaEntity, RoutingRuleJpaEntity
│       └── messaging/          # OutboxAdapter (notification.delivered.v1)
└── config/
```

### Layer Rules

1. **Domain layer is framework-free.** `Alert`, `NotificationDelivery`, and
   the routing types are plain Java with no Spring / Jackson / JPA.
2. **Application services own transaction boundary.** One inbound event =
   one DB transaction (dedupe row + delivery row + outbox row, all atomic).
3. **Channel ports are async at the application boundary.** The
   transactional commit completes before the adapter hits the external
   vendor — vendor failure / timeout cannot poison the ingest path.
4. **Adapters may be sync internally** (Resilience4j wrappers around
   blocking HTTP). What matters is the application-service-to-adapter
   boundary; the adapter's own retry budget is internal.

---

## Dependencies (Inbound)

| Caller | Contract | Purpose |
|---|---|---|
| `inventory-service` | `wms.inventory.alert.v1` | Low-stock detection (primary v1 trigger) |
| `inventory-service` | `wms.inventory.adjusted.v1` | Significant adjustments (configurable threshold) |
| `inbound-service` | `wms.inbound.inspection.completed.v1` | Discrepancy alerts (configurable threshold) |
| `inbound-service` | `wms.inbound.asn.cancelled.v1` | ASN cancellation alerts |
| `outbound-service` | `wms.outbound.order.cancelled.v1` | Post-pick cancellation alerts |
| `outbound-service` | `wms.outbound.shipping.confirmed.v1` | High-value shipment confirmations (configurable) |

Authoritative schemas live in each producer service's `specs/contracts/events/<service>-events.md`.
This service's `specs/contracts/events/notification-subscriptions.md` cross-links
those and declares **which events trigger which routing rule** in v1.

`notification-service` is **not** invoked synchronously by any other service.

---

## Dependencies (Outbound)

| Dependency | Why | v1 Resilience |
|---|---|---|
| PostgreSQL | Owned DB — delivery audit + routing rules + outbox + dedupe | Standard pool + Hikari |
| Kafka | Subscribed topics (consume) + `wms.notification.delivered.v1` (publish via outbox) | DefaultErrorHandler → DLT, 3 retries with exponential backoff |
| **Slack Incoming Webhooks** | v1 sole external channel | Resilience4j circuit breaker + retry-with-jitter + bounded timeout (3s connect / 5s read) |

External vendor footprint is intentionally tiny in v1.

---

## Event Consumption (subscribed topics)

| Subscribed Event | Source Topic | v1 Routing Default |
|---|---|---|
| `inventory.low-stock-detected` | `wms.inventory.alert.v1` | Slack `#wms-alerts` (severity=WARNING) |
| `inventory.adjusted` | `wms.inventory.adjusted.v1` | Slack `#wms-alerts` only when `\|delta\| ≥ 100` (config-driven) |
| `inbound.inspection.completed` | `wms.inbound.inspection.completed.v1` | Slack `#wms-alerts` when `discrepancyCount > 0` |
| `inbound.asn.cancelled` | `wms.inbound.asn.cancelled.v1` | Slack `#wms-alerts` (severity=INFO) |
| `outbound.order.cancelled` | `wms.outbound.order.cancelled.v1` | Slack `#wms-alerts` when status was `PICKED` or later |
| `outbound.shipping.confirmed` | `wms.outbound.shipping.confirmed.v1` | Slack `#wms-shipping` always (severity=INFO) |

Detailed payload mapping → channel template lives in
`specs/contracts/events/notification-subscriptions.md` (Open Items).

### Consumer Rules

- **Consumer group**: `wms-notification-v1` (one group; per-topic listener
  containers within the same group).
- **EventId dedupe**: shared `notification_event_dedupe(event_id PK,
  source_topic, processed_at, outcome)`. 30-day retention.
- **Filter step**: routing rule may classify an event as **NO_ACTION**
  (e.g., `\|delta\| < 100`) — recorded in dedupe with `outcome=FILTERED` so
  replay is idempotent and observability shows filter rate.
- **DLT**: `<source-topic>.DLT` per Spring Kafka convention. Operator drains
  via the runbook in `runbooks/dlt-replay.md` (Open Items).

---

## Event Publication (outbox)

Single published topic in v1:

| Event | Topic | Trigger |
|---|---|---|
| `notification.delivered` | `wms.notification.delivered.v1` | Every successful or terminally failed delivery (after retry exhaustion) |

This is an **audit trail event**, consumed downstream by `admin-service`'s
read-model for dashboards (delivery rate, latency, channel health).
Publication uses the standard outbox pattern (trait `transactional` T3).

Schema: defined in `specs/contracts/events/notification-events.md` (Open Items).

---

## Routing & Delivery Pipeline

```
Kafka topic ─→ @KafkaListener
                    │
                    │ 1. dedupe.isProcessed(eventId) → exit if seen
                    ▼
                AlertRoutingService.process(envelope)
                    │
                    │ 2. resolve RoutingRule by eventType
                    │ 3. evaluate matcher (severity threshold, payload predicate)
                    │    - if no match: dedupe.record(FILTERED), commit, ack
                    │ 4. build NotificationDelivery (PENDING) per channel target
                    │ 5. dedupe.record(QUEUED), persist deliveries, append outbox row, COMMIT
                    ▼
                DeliveryExecutor (post-commit)
                    │
                    │ 6. for each PENDING delivery:
                    │    - call ChannelPort (Slack adapter)
                    │    - on success → status=SUCCEEDED, attempt_count++
                    │    - on transient fail → status=PENDING, scheduled_retry_at
                    │    - on permanent fail (4xx) → status=FAILED, append outbox audit
                    ▼
                Retry scheduler (Spring @Scheduled, batch-heavy-light)
                    │
                    └─ 7. picks PENDING deliveries with scheduled_retry_at ≤ now
                          re-invokes DeliveryExecutor with attempt_count cap
```

### Why post-commit delivery?

If we called Slack inside the consumer transaction, a vendor 500 would roll
back the dedupe insert and the next consumer poll would replay forever. By
committing the dedupe + delivery row first and dispatching after, we
preserve at-least-once semantics from Kafka while making the vendor call
idempotent at the application layer (per-delivery row).

### Retry budget

- Max attempts per delivery: 5
- Backoff: 1s → 5s → 30s → 2m → 10m (capped, with ±20% jitter)
- After exhaustion: `status=FAILED` + `notification.delivered` event with
  `outcome=FAILED_PERMANENTLY` so admin dashboards can flag and ops can
  manually re-drive via `POST /internal/notifications/{id}/retry` (v2 admin
  surface; v1 = direct DB row update by operator).

---

## Saga / Long-running Flow (ADR-MONO-005)

Per [ADR-MONO-005](../../../../../docs/adr/ADR-MONO-005-saga-timeout-escalation-dead-letter-policy.md) — **Category C reference implementation** for the monorepo.

| Flow | Category | Backoff / Poll | Cap | Metrics | DLT terminal | Status |
|---|---|---|---|---|---|---|
| notification delivery (channel send + retry) | **C** (single-step retry+DLT, persistent `notification_delivery` row) | `wms.notification.delivery.backoff-seconds=1,5,30,120,600` ±20 % jitter · `wms.notification.delivery.retry-poll-interval-ms=5000` | 5 attempts → terminal `FAILED` + error code `DELIVERY_RETRY_EXHAUSTED` (422) | `notification.delivery.attempts{channel,status}`, `notification.delivery.duration.seconds` | `notification.delivered.v1 outcome=FAILED_RETRY_EXHAUSTED` (outbox audit event acts as DLT analog; vendor 4xx → `FAILED_PERMANENT` short-circuit) | **Compliant** (reference impl) |

Source: `DeliveryDispatchPerRow` runs each delivery under `@Transactional(REQUIRES_NEW)` via a separate bean so Spring AOP self-invocation is honoured (per `feedback_refactor_code_baseline_it.md`). Slack channel adapter is Resilience4j-wrapped (Category B sub-step inside Category C).

---

## Idempotency

### Inbound (Kafka)

- `notification_event_dedupe(event_id PK, source_topic, processed_at,
  outcome)` — 30 days retention. `outcome ∈ {QUEUED, FILTERED}` records
  why the event was (or wasn't) actioned, so replay produces the same
  classification.

### Outbound (channel)

- Each `NotificationDelivery` row has a deterministic `delivery_idempotency_key
  = sha256(eventId + channelId + recipient)`. Slack incoming webhooks don't
  enforce idempotency, but the key persists across retries so we never
  fire two messages for the same logical delivery — the row's
  `attempt_count` is incremented in place.

---

## Concurrency Control

- `NotificationDelivery` carries `version` (JPA optimistic lock). The retry
  scheduler picks rows with a `SELECT … FOR UPDATE SKIP LOCKED` so two
  worker instances cannot double-fire the same delivery.
- Routing rules are read-mostly; an in-memory cache with a 60s TTL is
  acceptable in v1 (operator config edit propagates after one minute).

---

## Key Domain Invariants

| Invariant | Source | Error code |
|---|---|---|
| `attempt_count ≤ max_attempts` (5 in v1) | derived | `DELIVERY_RETRY_EXHAUSTED` |
| Status transitions: `PENDING → SUCCEEDED \| FAILED` (terminal) | T4 | `DELIVERY_STATE_TRANSITION_INVALID` |
| `scheduled_retry_at` only set when `status=PENDING` | derived | (programmer error — assertion) |
| `delivery_idempotency_key` unique per delivery row | derived | UNIQUE violation surfaced as `IDEMPOTENCY_KEY_DUPLICATE` |
| Routing rule must match exactly one or zero rules per event type | T6 | `ROUTING_AMBIGUOUS` |

---

## Persistence

- Database: PostgreSQL (one logical DB per service)
- Migrations: Flyway, `apps/notification-service/src/main/resources/db/migration/`
- Tables (full layout in `domain-model.md` — Open Items):
  - `notification_routing_rule(id PK, event_type, matcher_json, channel_target_json,
    severity, enabled, created_at, updated_at)` — seeded with v1 defaults
  - `notification_delivery(id PK, event_id, source_topic, channel_id,
    delivery_idempotency_key UQ, status, attempt_count, scheduled_retry_at,
    last_error, version, created_at, updated_at)`
  - `notification_event_dedupe(event_id PK, source_topic, processed_at, outcome)`
  - `notification_outbox` — standard outbox table (libs/java-messaging schema)

---

## Observability

Standard event-consumer + outbox-relay metrics, plus channel-specific:

- `notification.consumer.lag.seconds{topic}` — event time → application
  service entry (consumer baseline)
- `notification.routing.classified.count{event_type, outcome}` —
  outcome ∈ `{QUEUED, FILTERED, AMBIGUOUS, NO_RULE}`. `AMBIGUOUS`/`NO_RULE`
  should be near-zero
- `notification.delivery.attempts{channel, status}` — for SLA dashboards
- `notification.delivery.duration.seconds{channel, status}` — vendor
  latency
- `notification.channel.circuit.state{channel}` — Resilience4j circuit
  breaker open/half-open/closed gauge

Logs include `eventId`, `deliveryId`, `channelId`, `attempt`, `traceId` so
ops can pivot from Slack message → delivery row → source event.

---

## Security

### JWT / Inbound Auth

`notification-service` has **no REST surface in v1**. The only inbound
trust boundary is Kafka, which is already authenticated at the broker level
(per `gap-integration.md`). No JWT validation required in v1.

When v2 adds an admin surface (`/internal/notifications/{id}/retry`,
`/api/v1/notifications/preferences`), it will sit behind `gateway-service`
with the standard OAuth2 RS pattern declared in
`specs/services/gateway-service/gap-integration.md`.

### Outbound Vendor Auth

Slack incoming webhook URL contains the auth token in the path. Storage:

- v1: env var `SLACK_WEBHOOK_URL_<CHANNEL>` per channel — operator
  responsibility to inject. **NEVER logged**, **NEVER persisted to the
  DB**. Routing rule references the channel by alias only (e.g.,
  `wms-alerts`).
- v2: encrypted column or external secret manager (Vault / AWS Secrets
  Manager).

---

## Trait Application

This service is the project's most direct exercise of the
`integration-heavy` trait:

| Trait Rule | Application |
|---|---|
| **I1** — Vendor adapters wrapped in resilience layer | `SlackChannelAdapter` uses Resilience4j `@CircuitBreaker` + `@Retry` annotations (or programmatic) |
| **I2** — Idempotent side-effects | Per-delivery `delivery_idempotency_key` ensures no double-send across retries |
| **I3** — DLT path for unparseable / poison events | Spring Kafka `DefaultErrorHandler` → `<topic>.DLT` |
| **I4** — Bounded vendor timeouts | 3s connect, 5s read on Slack HTTP client |
| **I5** — Vendor outage observability | Circuit breaker state metric + alert dashboard (consumed by `admin-service`) |

`transactional` rules T1, T3, T4, T8 also apply — outbox + dedupe +
state-machine guards on `NotificationDelivery.status`.

---

## Testing Requirements

### Unit
- Routing-rule matcher: payload predicate + severity threshold + ambiguity
- `NotificationDelivery.transition(...)` — every legal state move + every
  illegal one
- Retry budget arithmetic (`attempt_count`, `scheduled_retry_at` calc)

### Application Service (port fakes)
- Per source topic: happy path → QUEUED + outbox row in same TX
- Filter path: predicate fails → FILTERED dedupe row + zero deliveries +
  zero outbox rows
- Dedupe-hit path: same eventId twice → second call exits cleanly
- Channel adapter failure path: PENDING delivery + `last_error` populated +
  scheduled retry

### Persistence Adapter (Testcontainers Postgres)
- All repos
- `SELECT … FOR UPDATE SKIP LOCKED` semantics with two parallel workers
- Outbox row + delivery row + dedupe row visibility under one TX

### Channel Adapter (WireMock)
- Slack 200 → SUCCEEDED
- Slack 5xx → exception → caller schedules retry
- Slack 4xx (e.g., 410 channel-not-found) → permanent failure (no retry)
- Circuit breaker: N consecutive failures → open → adapter fast-fails
- Bounded timeout: WireMock `setFixedDelay(10s)` → adapter aborts at 5s

### Consumer (Testcontainers Kafka)
- One IT per subscribed topic — publish envelope, observe dedupe row +
  delivery row + (mocked) Slack call
- Cross-context replay: same event consumed by a fresh Spring context →
  no duplicate Slack call (dedupe defends)
- Out-of-order events (rare): does not corrupt routing decision

### Contract Tests
- `notification-subscriptions.md` event payload shapes verified against
  each source service's contract
- `notification-events.md` published `notification.delivered.v1` schema

### Failure-mode (BE-002d / SCM-BE-005 pattern)
- Slack vendor down for 30s → all attempts go PENDING → vendor recovers
  → backlog drains within retry budget
- Postgres FK violation on routing rule (e.g., manual DB edit) →
  application service raises `ROUTING_RULE_NOT_FOUND` → dedupe row records
  outcome=ERROR + manual replay path documented

---

## Extensibility Notes

- **Channel adapters**: each new channel = new `ChannelPort` impl + new
  routing target type. Domain unchanged.
- **Per-user preference**: introduces `notification_recipient` aggregate
  + `RoutingRule` matcher extension. Application service shape unchanged.
- **Multi-tenant**: Out of v1. Adding requires `tenant_id` on every
  aggregate (matches the `transactional` trait's tenant scoping rules).
- **Time-windowed delivery** (e.g., suppress non-critical alerts after
  hours): adds a windowing predicate to the routing rule matcher. v2.

---

## Open Items (Before First Implementation Task)

These must be completed before any `TASK-BE-*` targeting `notification-service`
moves from `tasks/ready/` to `tasks/in-progress/`:

1. `specs/services/notification-service/domain-model.md` — Alert,
   NotificationDelivery, RoutingRule + table layouts (consolidated)
2. `specs/contracts/events/notification-subscriptions.md` — per-event
   routing rule defaults + payload-to-template mapping
3. `specs/contracts/events/notification-events.md` — published
   `notification.delivered.v1` schema
4. `specs/services/notification-service/idempotency.md` — dedupe + delivery
   key + retry budget tabulated
5. `specs/services/notification-service/runbooks/dlt-replay.md` — DLT drain
   + manual delivery retry procedure
6. Register error codes in `platform/error-handling.md`:
   `DELIVERY_RETRY_EXHAUSTED`, `DELIVERY_STATE_TRANSITION_INVALID`,
   `IDEMPOTENCY_KEY_DUPLICATE`, `ROUTING_AMBIGUOUS`, `ROUTING_RULE_NOT_FOUND`

> **Pragmatic v1 ordering**: items 1 + 2 are required for the bootstrap
> task (`TASK-BE-043`). Items 3 / 4 / 5 / 6 may be authored as part of the
> bootstrap task PR if scope permits, otherwise filed as immediate
> follow-up tasks.

---

## References

- `CLAUDE.md`, `PROJECT.md`
- `rules/domains/wms.md` — Admin / Operations bounded context
- `rules/traits/integration-heavy.md` — I1, I2, I3, I4, I5
- `rules/traits/transactional.md` — T1, T3, T4, T8
- `platform/architecture-decision-rule.md`
- `platform/service-types/event-consumer.md`
- `specs/services/admin-service/architecture.md` — sibling read-model
  consumer (different scope, similar consumer-only shape)
- `specs/contracts/events/inventory-events.md`,
  `specs/contracts/events/inbound-events.md`,
  `specs/contracts/events/outbound-events.md` — subscribed event sources
