# notification-service — Domain Model

This document declares aggregates, value objects, and persistence layout for
`notification-service`. Cross-references in `architecture.md` § Persistence
+ § Domain Invariants.

---

## Aggregates (v1)

### 1. `RoutingRule` (read-mostly aggregate)

Represents a per-event-type rule that decides whether and where an inbound
event becomes a notification.

| Field | Type | Notes |
|---|---|---|
| `id` | UUID | PK |
| `eventType` | string | Authoritative source: producer service's `eventType` (e.g., `inventory.low-stock-detected`) |
| `matcher` | `RoutingMatcher` (VO) | predicate over event payload (see below) |
| `channelTargets` | `List<ChannelTarget>` (VO) | one or more channels to fan out to in v1 (commonly one) |
| `severity` | `AlertSeverity` enum | `INFO \| WARNING \| CRITICAL` — surfaced to channel template |
| `enabled` | boolean | operator kill-switch without deleting the rule |
| `createdAt` / `updatedAt` | Instant | |

#### Invariants

- One enabled rule per `eventType` in v1 (T6 — no ambiguity). Storage-level
  enforcement: partial UNIQUE index on `(event_type) WHERE enabled = true`.
- `channelTargets` non-empty when `enabled = true`.
- `matcher` valid against the source service's contract — not enforced at
  domain level (test contract responsibility).

#### Lifecycle

- v1: seeded by Flyway migration. Operator changes via direct DB edit
  (with operator runbook).
- v2: `admin-service` exposes CRUD. `RoutingRule` becomes a write-shaped
  aggregate.

### 2. `NotificationDelivery` (write-shaped aggregate)

Represents one logical delivery (one channel × one event). Spans the
moment the routing decision queues a delivery to terminal SUCCESS or
FAIL.

| Field | Type | Notes |
|---|---|---|
| `id` | UUID | PK |
| `eventId` | UUID | the source-event id; combined with `channelId` to derive `deliveryIdempotencyKey` |
| `sourceTopic` | string | for observability + DLT replay |
| `channelId` | string | logical channel alias (e.g., `wms-alerts`) |
| `recipient` | string | channel-resolved recipient (Slack: webhook URL alias; v2 email: address) |
| `deliveryIdempotencyKey` | string | UNIQUE — `sha256(eventId + channelId + recipient)` |
| `payloadSnapshot` | JSONB | canonical envelope used to render the message; immutable after creation |
| `status` | `DeliveryStatus` enum | `PENDING \| SUCCEEDED \| FAILED` |
| `attemptCount` | int | initialised 0; capped at 5 (v1) |
| `scheduledRetryAt` | Instant nullable | only set when `status = PENDING` and `attemptCount > 0` |
| `lastError` | string nullable | last vendor error; trimmed to ≤ 500 chars; never includes secrets |
| `version` | int | JPA optimistic lock |
| `createdAt` / `updatedAt` | Instant | |

#### State Machine

```
PENDING ─[deliveryAdapter.send() OK]─→ SUCCEEDED
PENDING ─[transient fail, attempt < max]─→ PENDING (scheduledRetryAt = backoff)
PENDING ─[permanent fail OR attempt == max]─→ FAILED
```

`SUCCEEDED` and `FAILED` are terminal. Disallowed transitions raise
`DELIVERY_STATE_TRANSITION_INVALID` (T4).

#### Invariants

- `deliveryIdempotencyKey` UNIQUE — prevents double-row creation under
  concurrent routing.
- `attemptCount ≤ 5` (v1). Extension knob: per-channel max via routing rule.
- `scheduledRetryAt` set only while `status = PENDING`. Cleared on
  transition to terminal status.
- `payloadSnapshot` immutable after creation — guarantees retry attempt
  reproduces the same Slack message.

### 3. `NotificationEventDedupe` (idempotency table aggregate)

Records every observed event-id (across all subscribed topics) so the
service is fully replay-safe.

| Field | Type | Notes |
|---|---|---|
| `eventId` | UUID | PK |
| `sourceTopic` | string | for cross-topic ambiguity diagnostics |
| `processedAt` | Instant | DB write time |
| `outcome` | `DedupeOutcome` enum | `QUEUED \| FILTERED \| ERROR` |

#### Invariants

- Once written, never updated (insert-only). 30-day retention via scheduled
  cleanup job (out of v1 scope — table grows; see Open Items in
  `architecture.md`).

---

## Value Objects

### `RoutingMatcher`

Sealed type. v1 implementations:

- `AlwaysMatch` — passes every event of the matching `eventType`
- `PayloadPredicateMatch(jsonPath, op, value)` — e.g.,
  `$.payload.delta` `>=` `100`
- `SeverityThresholdMatch(min)` — passes when payload-derived severity
  ≥ `min`

Stored as `matcher_json` (discriminator union). Domain layer parses on load.

### `ChannelTarget`

| Field | Type | Notes |
|---|---|---|
| `channelType` | `ChannelType` enum | `SLACK` only in v1 |
| `channelId` | string | logical alias resolved at adapter to a vendor-specific endpoint (Slack webhook URL alias, future: email address group, push topic) |
| `templateKey` | string | reference to a hard-coded template in v1; v2 = lookup in `notification_template` table |

### `AlertSeverity`

`INFO | WARNING | CRITICAL` — orthogonal to delivery outcome; passed
through to channel template for visual emphasis.

---

## Persistence Layout

```sql
-- Flyway V1__init.sql sketch (full SQL in BE-043 implementation PR)

CREATE TABLE notification_routing_rule (
    id              UUID            PRIMARY KEY,
    event_type      VARCHAR(120)    NOT NULL,
    matcher_json    JSONB           NOT NULL,
    channel_targets_json JSONB      NOT NULL,
    severity        VARCHAR(16)     NOT NULL,
    enabled         BOOLEAN         NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ     NOT NULL,
    updated_at      TIMESTAMPTZ     NOT NULL
);
CREATE UNIQUE INDEX uq_routing_rule_event_enabled
    ON notification_routing_rule (event_type) WHERE enabled = true;

CREATE TABLE notification_delivery (
    id                          UUID            PRIMARY KEY,
    event_id                    UUID            NOT NULL,
    source_topic                VARCHAR(120)    NOT NULL,
    channel_id                  VARCHAR(120)    NOT NULL,
    recipient                   VARCHAR(255)    NOT NULL,
    delivery_idempotency_key    VARCHAR(64)     NOT NULL UNIQUE,
    payload_snapshot            JSONB           NOT NULL,
    status                      VARCHAR(16)     NOT NULL,
    attempt_count               INT             NOT NULL DEFAULT 0,
    scheduled_retry_at          TIMESTAMPTZ     NULL,
    last_error                  VARCHAR(500)    NULL,
    version                     INT             NOT NULL DEFAULT 0,
    created_at                  TIMESTAMPTZ     NOT NULL,
    updated_at                  TIMESTAMPTZ     NOT NULL
);
CREATE INDEX idx_delivery_status_retry
    ON notification_delivery (status, scheduled_retry_at)
    WHERE status = 'PENDING';
CREATE INDEX idx_delivery_event_id
    ON notification_delivery (event_id);

CREATE TABLE notification_event_dedupe (
    event_id        UUID            PRIMARY KEY,
    source_topic    VARCHAR(120)    NOT NULL,
    processed_at    TIMESTAMPTZ     NOT NULL,
    outcome         VARCHAR(16)     NOT NULL
);
CREATE INDEX idx_dedupe_processed_at
    ON notification_event_dedupe (processed_at);

-- standard outbox table per libs/java-messaging schema
CREATE TABLE notification_outbox (
    id              UUID            PRIMARY KEY,
    aggregate_type  VARCHAR(64)     NOT NULL,
    aggregate_id    VARCHAR(64)     NOT NULL,
    event_type      VARCHAR(120)    NOT NULL,
    payload         JSONB           NOT NULL,
    occurred_at     TIMESTAMPTZ     NOT NULL,
    published_at    TIMESTAMPTZ     NULL,
    attempt_count   INT             NOT NULL DEFAULT 0
);
CREATE INDEX idx_outbox_unpublished
    ON notification_outbox (occurred_at)
    WHERE published_at IS NULL;
```

### Index Notes

- `idx_delivery_status_retry` is a partial index — keeps the retry-poll
  query (`WHERE status = 'PENDING' AND scheduled_retry_at <= now()`) fast
  even as terminal rows accumulate.
- `idx_dedupe_processed_at` enables the eventual cleanup job (v2 scope).
- All `JSONB` columns get `@JdbcTypeCode(SqlTypes.JSON)` on the JPA entity
  (per `INT-001b` root cause #2 / `BE-005` regression-guard learning).

---

## Seeded Routing Rules (v1)

Flyway `V1__init.sql` (or `V2__seed_routing_rules.sql`) seeds the following
defaults. Operators may toggle `enabled` or adjust matchers via direct DB
edit until v2 admin UI ships.

| eventType | matcher | channel | severity |
|---|---|---|---|
| `inventory.low-stock-detected` | `AlwaysMatch` | `wms-alerts` (SLACK) | WARNING |
| `inventory.adjusted` | `PayloadPredicateMatch($.payload.delta abs >= 100)` | `wms-alerts` | INFO |
| `inbound.inspection.completed` | `PayloadPredicateMatch($.payload.discrepancyCount > 0)` | `wms-alerts` | WARNING |
| `inbound.asn.cancelled` | `AlwaysMatch` | `wms-alerts` | INFO |
| `outbound.order.cancelled` | `PayloadPredicateMatch($.payload.priorStatus in ['PICKED','PACKED','SHIPPED'])` | `wms-alerts` | WARNING |
| `outbound.shipping.confirmed` | `AlwaysMatch` | `wms-shipping` | INFO |

Rules not in this table = events not routed in v1. Adding a new event
trigger = new seed row + (if needed) a new `ChannelType` enum value.

---

## Channel Templates (v1)

Hard-coded in code (no DB table in v1). Each `eventType` has a Slack
template formatter that takes the canonical envelope and emits a Slack
`blocks` payload:

```
[severity-emoji] [eventType] @ [warehouseId or aggregateId]
> humanized message
> ▸ [link to admin dashboard if available]
```

v2 introduces `notification_template(template_key PK, channel_type,
markdown_template, version)` for operator-editable templates.

---

## Domain Errors

Error codes registered in `platform/error-handling.md`:

| Code | Trigger |
|---|---|
| `DELIVERY_RETRY_EXHAUSTED` | `attemptCount == max_attempts` and last attempt failed |
| `DELIVERY_STATE_TRANSITION_INVALID` | Application code attempted to transition a terminal delivery |
| `IDEMPOTENCY_KEY_DUPLICATE` | UNIQUE constraint on `delivery_idempotency_key` violated under concurrent routing |
| `ROUTING_AMBIGUOUS` | Multiple enabled rules matched the same `eventType` (storage UNIQUE prevents this; surfaces only on bad manual DB edit) |
| `ROUTING_RULE_NOT_FOUND` | No enabled rule for the `eventType` (logged at WARN, dedupe outcome=NO_RULE; not necessarily an error in v1) |

---

## Out of Scope (v1)

- `NotificationRecipient` aggregate (per-user delivery preferences)
- `NotificationTemplate` table (operator-editable templates)
- Multi-channel fanout with per-channel retry budgets (single channel target
  per rule supports the same shape; explicit fanout is v2)
- Tenant-scoped routing
- Severity-driven delivery (e.g., `CRITICAL` → SMS escalation) — requires
  v2 channels

---

## References

- `architecture.md` — service-level decisions
- `specs/contracts/events/notification-subscriptions.md` — subscribed event
  payload mapping
- `specs/contracts/events/notification-events.md` — published event schema
- `rules/traits/integration-heavy.md` — I1, I2, I3, I4, I5
- `rules/traits/transactional.md` — T1, T3, T4, T8
- `projects/scm-platform/apps/inventory-visibility-service/...` — sibling
  Spring Boot 3.x JSONB pattern (`@JdbcTypeCode(SqlTypes.JSON)`)
