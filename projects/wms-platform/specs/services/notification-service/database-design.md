# notification-service — Database Design

Physical schema reflection for `notification_db`. Flyway migrations under
`apps/notification-service/src/main/resources/db/migration/` are the
canonical source-of-truth; this document consolidates them into a single
spec artifact for review-time reasoning. When a new migration lands
(`V3+`), this file must be updated in the same commit.

**Target engine**: PostgreSQL 14+ (production). JSONB columns, partial
indexes, and `TIMESTAMPTZ` semantics are PostgreSQL native; portability
to other engines is out of scope for v1.

**Authoritative reference**: [`domain-model.md`](domain-model.md) for the
domain meaning of each table.

---

## Schema Overview

```
                    ┌─────────────────────────────────────┐
                    │  notification_routing_rule (V1)     │
                    │  matcher_json + channel_targets_json│
                    │  partial-unique (event_type, enabled)│
                    └──────────────────┬──────────────────┘
                                       │ (read-only at routing time)
                                       ▼
   ┌──────────────────┐     ┌─────────────────────────────┐
   │ event consume    │ ──▶ │ notification_delivery (V1)  │
   │ (Kafka inbound)  │     │ PENDING → SUCCEEDED | FAILED│
   └──────────────────┘     │ + delivery_idempotency_key  │
              │             │ + version optimistic-lock   │
              │             └──────────────┬──────────────┘
              │                            │
              ▼                            ▼
   ┌──────────────────────────┐  ┌──────────────────────┐
   │ notification_event_dedupe│  │ notification_outbox  │
   │ (V1, T8)                 │  │ (V1, T3 audit-only)  │
   │ INSERT-or-skip           │  │ JSONB payload        │
   └──────────────────────────┘  └──────────────────────┘
```

Total: 4 tables across 1 schema-creating migration (V1=117 line) plus
1 seed-only migration (V2=77 line, 6 routing rule INSERTs — no DDL).

---

## 1. NotificationRoutingRule (V1, domain-model § Persistence Layout)

Per-event-type routing decision. Seeded by V2 (see § 5). Operators may
toggle `enabled` or adjust matchers via direct DB edit until the v2 admin
UI ships.

```sql
CREATE TABLE notification_routing_rule (
    id                    UUID            PRIMARY KEY,
    event_type            VARCHAR(120)    NOT NULL,
    matcher_json          JSONB           NOT NULL,
    channel_targets_json  JSONB           NOT NULL,
    severity              VARCHAR(16)     NOT NULL,
    enabled               BOOLEAN         NOT NULL DEFAULT true,
    created_at            TIMESTAMPTZ     NOT NULL,
    updated_at            TIMESTAMPTZ     NOT NULL,
    CONSTRAINT ck_routing_rule_severity
        CHECK (severity IN ('INFO', 'WARNING', 'CRITICAL'))
);

CREATE UNIQUE INDEX uq_routing_rule_event_enabled
    ON notification_routing_rule (event_type)
    WHERE enabled = true;
```

**Partial unique index** (`WHERE enabled = true`) enforces T6 — at most
one enabled rule per `event_type`. The operator pattern is "stage a new
rule with `enabled=false`, atomically swap by toggling the flag" — this
is why the constraint scopes to `enabled=true` only. Multiple matching
enabled rules raise `ROUTING_AMBIGUOUS` at the application layer.

**JSONB columns** (`matcher_json`, `channel_targets_json`) map to JPA
entities annotated with `@JdbcTypeCode(SqlTypes.JSON)` — see
`TASK-SCM-INT-001b` root cause #2 + `TASK-SCM-BE-005` regression-guard
learning. `JsonbColumnRegressionGuardTest` enforces this at build time;
removing the annotation results in a JPA persistence test failure.

`matcher_json` discriminator union (mirrored by the sealed
`RoutingMatcher` type):

| Type | Shape |
|---|---|
| `AlwaysMatch` | `{"type":"ALWAYS"}` |
| `PayloadPredicateMatch` | `{"type":"PAYLOAD_PREDICATE","jsonPath":"$.payload.delta","op":"ABS_GTE","value":100}` |
| `SeverityThresholdMatch` | `{"type":"SEVERITY_THRESHOLD","min":"WARNING"}` |

`channel_targets_json` is `List<ChannelTarget>` shape:

```json
[{"channelType":"SLACK","channelId":"wms-alerts","templateKey":"low_stock"}]
```

---

## 2. NotificationDelivery (V1, T4 state machine)

One logical delivery row per `(event × channel)` decision. State machine
`PENDING → SUCCEEDED | FAILED` (both terminal); domain-layer transitions
enforce the per-transition rules, while the SQL `ck_delivery_status`
check catches out-of-band INSERTs.

```sql
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
    updated_at                  TIMESTAMPTZ     NOT NULL,
    CONSTRAINT ck_delivery_status
        CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED')),
    CONSTRAINT ck_delivery_attempt_nonneg
        CHECK (attempt_count >= 0)
);
```

**`delivery_idempotency_key UNIQUE`** is the load-bearing constraint —
blocks double-row creation under concurrent routing (architecture.md §
Idempotency). UNIQUE violation surfaced as `IDEMPOTENCY_KEY_DUPLICATE`
error code; the calling code treats this as the dedupe signal.

**`payload_snapshot JSONB`** preserves the event payload at routing-time
so retries do not need to re-fetch from Kafka — the row is self-sufficient
for replay. This decouples retry attempts from Kafka offset commits.

**`version INT`** is the JPA `@Version` optimistic lock — concurrent
attempts to advance a delivery (e.g., retry scheduler vs manual operator
trigger) fail with `OptimisticLockingFailureException`.

### 2.1 Indexes

```sql
CREATE INDEX idx_delivery_status_retry
    ON notification_delivery (status, scheduled_retry_at)
    WHERE status = 'PENDING';

CREATE INDEX idx_delivery_event_id
    ON notification_delivery (event_id);
```

**Retry scheduler hot path** (`idx_delivery_status_retry`): partial index
on `status='PENDING'` keeps the scheduler's "find rows due for retry"
scan fast regardless of how many terminal (`SUCCEEDED` / `FAILED`) rows
accumulate. The scheduler's query is:

```sql
SELECT * FROM notification_delivery
 WHERE status = 'PENDING' AND scheduled_retry_at <= NOW()
 ORDER BY scheduled_retry_at LIMIT 100;
```

This is an index-only scan once partial.

`idx_delivery_event_id` supports the audit query "show all deliveries
generated for this event_id" used by ops during incident triage.

---

## 3. NotificationEventDedupe (V1, transactional T8)

Consumer-side dedupe by `event_id`. Primary-key conflict is the dedupe
signal — INSERT-or-skip pattern with no extra SELECT round-trip.

```sql
CREATE TABLE notification_event_dedupe (
    event_id      UUID            PRIMARY KEY,
    source_topic  VARCHAR(120)    NOT NULL,
    processed_at  TIMESTAMPTZ     NOT NULL,
    outcome       VARCHAR(16)     NOT NULL,
    CONSTRAINT ck_dedupe_outcome
        CHECK (outcome IN ('QUEUED', 'FILTERED', 'NO_RULE', 'ERROR'))
);

CREATE INDEX idx_dedupe_processed_at
    ON notification_event_dedupe (processed_at);
```

**`outcome` enum** records *why* the event was deduped:

| Outcome | Meaning |
|---|---|
| `QUEUED` | Event reached the routing layer; one or more `notification_delivery` rows produced |
| `FILTERED` | Routing matcher rejected (e.g., `delta < 100`) — no delivery row |
| `NO_RULE` | No `enabled=true` rule for the event_type — no delivery row, alert ops |
| `ERROR` | Routing layer threw — row recorded for replay diagnosis |

This outcome field doubles as a partition for replay tooling — ops can
re-emit just `NO_RULE` rows after backfilling a routing rule, without
touching previously-delivered ones.

`idx_dedupe_processed_at` powers the 30-day retention sweeper (cleanup
job out of v1 scope per `domain-model.md` § Persistence — see Open Items
in `architecture.md`).

---

## 4. NotificationOutbox (V1, transactional T3)

Transactional outbox for audit-only Kafka publication
(`notification.delivered.v1`). The domain writes a row in the same TX as
the delivery-row mutation; a separate publisher polls pending rows and
ships them to Kafka.

```sql
CREATE TABLE notification_outbox (
    id              UUID            PRIMARY KEY,
    aggregate_type  VARCHAR(64)     NOT NULL,
    aggregate_id    VARCHAR(64)     NOT NULL,
    event_type      VARCHAR(120)    NOT NULL,
    event_version   VARCHAR(10)     NOT NULL DEFAULT 'v1',
    payload         JSONB           NOT NULL,
    partition_key   VARCHAR(120)    NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL,
    published_at    TIMESTAMPTZ     NULL,
    attempt_count   INT             NOT NULL DEFAULT 0
);

CREATE INDEX idx_notification_outbox_pending
    ON notification_outbox (created_at)
    WHERE published_at IS NULL;

CREATE INDEX idx_notification_outbox_aggregate
    ON notification_outbox (aggregate_type, aggregate_id);
```

**Service-local outbox shape** (not the shared `libs/java-messaging` base
schema): v1 mandates JSONB payload + `partition_key` per event, mirroring
the inventory-service outbox shape (see
[`../inventory-service/database-design.md`](../inventory-service/database-design.md)
§ 3 InventoryOutbox). The wms-specific shape diverges from
`master-service`'s legacy libs shared schema (BIGSERIAL + TEXT + status
enum, see [`../master-service/database-design.md`](../master-service/database-design.md)
§ 2). master's migration to this modern shape is deferred per
ADR-MONO-003 D2 cadence (≥ 2026-06-10), tracked as TASK-MONO-049 § 6
follow-up #1.

**Pending-publisher index** (`WHERE published_at IS NULL`) keeps the
publisher's FIFO scan cheap — only unpublished rows are indexed, and the
set empties to near-zero in steady state.

---

## 5. V2 Seeded Routing Rules

V2__seed_routing_rules.sql is **data-only** (no DDL) — it inserts 6
routing rules covering the 6 subscribed topics. Operators may toggle
`enabled` or adjust matchers via direct DB edit until v2 admin UI ships.

UUID prefix `00000000-0000-7000-8000-00000000000{1-6}` follows the v7
UUID layout (`7` in the version nibble), reserving a deterministic
identity for each v1 seed rule so test fixtures can assert against
known IDs.

| UUID tail | event_type | matcher | channel | severity |
|---|---|---|---|---|
| `…0001` | `inventory.low-stock-detected` | `ALWAYS` | `wms-alerts` | WARNING |
| `…0002` | `inventory.adjusted` | `PAYLOAD_PREDICATE` `\|delta\| ≥ 100` | `wms-alerts` | INFO |
| `…0003` | `inbound.inspection.completed` | `PAYLOAD_PREDICATE` `discrepancyCount > 0` | `wms-alerts` | WARNING |
| `…0004` | `inbound.asn.cancelled` | `ALWAYS` | `wms-alerts` | INFO |
| `…0005` | `outbound.order.cancelled` | `PAYLOAD_PREDICATE` `priorStatus IN ['PICKED','PACKED','SHIPPED']` | `wms-alerts` | WARNING |
| `…0006` | `outbound.shipping.confirmed` | `ALWAYS` | `wms-shipping` | INFO |

Each rule's `templateKey` is `<event_slug>` (e.g., `low_stock`,
`inventory_adjusted`) — see V2 source for the exact JSON payload.
Architecture-level cross-reference:
[`architecture.md`](architecture.md) § Routing Rules.

---

## 6. Indexing Strategy Summary

| Table | Index | Type | Purpose |
|---|---|---|---|
| `notification_routing_rule` | `notification_routing_rule_pkey` | PK | row lookup |
| `notification_routing_rule` | `uq_routing_rule_event_enabled` | partial unique (`enabled = true`) | one enabled rule per event_type (T6) |
| `notification_delivery` | `notification_delivery_pkey` | PK | row lookup |
| `notification_delivery` | `notification_delivery_delivery_idempotency_key_key` | unique | dedupe block on row creation |
| `notification_delivery` | `idx_delivery_status_retry` | partial (`status = 'PENDING'`) | retry scheduler hot path |
| `notification_delivery` | `idx_delivery_event_id` | btree | per-event audit query |
| `notification_event_dedupe` | `notification_event_dedupe_pkey` | PK | dedupe by event_id |
| `notification_event_dedupe` | `idx_dedupe_processed_at` | btree | 30-day retention sweeper (v2 cleanup job) |
| `notification_outbox` | `notification_outbox_pkey` | PK | row lookup |
| `notification_outbox` | `idx_notification_outbox_pending` | partial (`published_at IS NULL`) | publisher FIFO scan |
| `notification_outbox` | `idx_notification_outbox_aggregate` | btree | aggregate-scoped lookup |

---

## 7. Migration History

| Version | File | Line | Scope |
|---|---|---|---|
| V1 | `V1__init.sql` | 117 | 4 tables (routing_rule + delivery + event_dedupe + outbox) + 6 indexes |
| V2 | `V2__seed_routing_rules.sql` | 77 | 6 routing rule INSERTs (data-only, no DDL) |

When `V3+` lands, this document must be updated in the same commit (per
the retrospective contract introduced by TASK-BE-157 and applied here).

---

## References

- [`domain-model.md`](domain-model.md) — domain meaning of each table (canonical reference)
- [`architecture.md`](architecture.md) — § Persistence, § Routing Rules, § Invariants
- [`idempotency.md`](idempotency.md) — T8 dedupe + delivery_idempotency_key strategy
- [`external-integrations.md`](external-integrations.md) — Slack marquee (BE-158)
- [`../../contracts/events/notification-events.md`](../../contracts/events/notification-events.md) — `notification.delivered.v1` audit schema
- [`../../contracts/events/notification-subscriptions.md`](../../contracts/events/notification-subscriptions.md) — 6 source topic catalog
- [`../inventory-service/database-design.md`](../inventory-service/database-design.md) — sibling reference (BE-157, primary template)
- `../../../apps/notification-service/src/main/resources/db/migration/V1__init.sql` — 117 line schema
- `../../../apps/notification-service/src/main/resources/db/migration/V2__seed_routing_rules.sql` — 77 line seed
- `../../../../../rules/traits/transactional.md` — T3 (outbox), T4 (state machine), T8 (event dedupe)
- `../../../../../rules/traits/integration-heavy.md` — I1-I5 (retry + DLQ)
- `../../../../../platform/architecture.md` — system-level architecture
