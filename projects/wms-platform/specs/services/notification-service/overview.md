# notification-service — Overview

> 1-pager: responsibilities, public surface, key invariants.

## Service identity

| Field | Value |
|---|---|
| Service name | `notification-service` |
| Project | `wms-platform` |
| Service Type | `event-consumer` (pure; no REST surface in v1) |
| Architecture Style | **Hexagonal (Ports & Adapters)** — see [architecture.md § Architecture Style](architecture.md) |
| Stack | Java 21, Spring Boot 3.4, PostgreSQL, Kafka (consumer + outbox for audit), Resilience4j (retry / CB), Slack Webhook client |
| Deployable unit | `apps/notification-service/` |
| Bounded Context | `Admin / Operations` (alert side) |
| Persistent stores | PostgreSQL (NotificationDelivery audit log + RoutingConfig — Flyway-seeded) + Kafka outbox (audit only) |
| Event publication | `notification.delivered.v1` (audit trail only, per [`notification-events.md`](../../contracts/events/notification-events.md)) |

## Responsibilities

- Subscribe to **6 alert-worthy WMS source topics** (per [`notification-subscriptions.md`](../../contracts/events/notification-subscriptions.md)).
- Resolve target channel(s) from per-event-type routing config (Flyway-seeded).
- Execute delivery to Slack (v1) with **retry, circuit-breaker, idempotent send** (per [`idempotency.md`](idempotency.md)).
- Persist NotificationDelivery audit record per `(event → channel → outcome)` attempt — 성공/실패 모두 기록.
- eventId-based dedupe guards against duplicate event delivery (T8).

## Public surface

`notification-service` 는 v1 에서 **REST surface 0** — pure event-consumer.

| Channel | Endpoint / Topic | Auth | Purpose |
|---|---|---|---|
| Kafka consume | `inbound.inspection.completed`, `inbound.asn.cancelled` | — | inbound 알림 |
| Kafka consume | `outbound.order.cancelled`, `outbound.shipping.confirmed` | — | outbound 알림 |
| Kafka consume | `inventory.alert`, `inventory.adjusted` | — | 재고 알림 |
| Kafka publish | `notification.delivered.v1` | — | audit trail (downstream analytics) |
| HTTP outbound | Slack Webhook (R4j wrap) | webhook URL | actual delivery |

자세한 spec 은 [`../../contracts/events/notification-subscriptions.md`](../../contracts/events/notification-subscriptions.md) (6 topic catalog) + [`../../contracts/events/notification-events.md`](../../contracts/events/notification-events.md) + [`idempotency.md`](idempotency.md) + [`runbooks/dlt-replay.md`](runbooks/dlt-replay.md) 참조.

## Key invariants

1. **Every consumed event idempotency-checked before delivery** — T8, dedupe by `eventId` (per [`idempotency.md § Inbound`](idempotency.md)).
2. **Delivery attempt persisted regardless of channel outcome** — audit completeness; provider 5xx 든 4xx 든 NotificationDelivery row 기록.
3. **Circuit breaker per channel vendor** — Slack outage 가 다른 vendor 발송 차단 금지 (per-vendor bulkhead).
4. **Routing config seeded via Flyway** — runtime mutation 부재 v1; 변경은 migration PR.
5. **Source service decides alert content** — `notification-service` 는 routing + delivery 만; "어떤 event 가 alert-worthy 인가" 결정 금지.

## Owned Data

- NotificationDelivery audit rows (event_id, channel, status, attempts, last_error, timestamps).
- RoutingConfig rows (event_type → channels mapping, Flyway-seeded).

## Published Interfaces

- [`../../contracts/events/notification-events.md`](../../contracts/events/notification-events.md) — `notification.delivered.v1` audit only
- [`../../contracts/events/notification-subscriptions.md`](../../contracts/events/notification-subscriptions.md) — 6 source topic catalog

## Dependent Systems

- Kafka — event consumption (6 sibling WMS service topics)
- PostgreSQL — audit + config persistence
- Slack Webhook API (external channel, v1)

## Out of scope (v1)

- Email / mobile push / SMS channels — v2.
- Per-user preference UI — v2 (`admin-service`).
- Bidirectional Slack interactions (slash commands / approval) — v2.
- Template management UI — v2.
- REST API for ops query — v2 (현재는 Postgres 직접 조회 또는 Grafana 통과).
