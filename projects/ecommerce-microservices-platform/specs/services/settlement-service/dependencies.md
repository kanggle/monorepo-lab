# Service Dependencies

## Service
`settlement-service`

## Allowed Direct Dependencies
- `libs/java-common` (shared domain primitives)
- `libs/java-web` (shared web exception primitives, REST infrastructure)
- `libs/java-observability` (metrics, tracing)
- `org.springframework.boot:spring-boot-starter-web`
- `org.springframework.boot:spring-boot-starter-data-jpa`
- `org.springframework.boot:spring-boot-starter-validation`
- `org.springframework.boot:spring-boot-starter-actuator`
- `org.springframework.kafka:spring-kafka` (event consumer **+ producer** since the period-close increment)
- `libs/java-messaging` (`OutboxAutoConfiguration` / `OutboxMetricsAutoConfiguration`) — **introduced in the period-close increment** (TASK-BE-415) to publish `settlement.period.closed.v1` via the transactional outbox (was excluded in v1)
- `org.flywaydb:flyway-core` + `flyway-database-postgresql`
- `org.postgresql:postgresql`
- `org.springdoc:springdoc-openapi-starter-webmvc-ui`
- own database (`settlement_db` — `order_snapshot`, `order_snapshot_line`, `commission_accrual`, `seller_commission_rate`, `processed_event`; **period-close increment adds** `settlement_period`, `seller_payout`, `outbox`)

## Allowed Service Interactions
- exposes operator-plane HTTP API (reads + rate admin) through gateway-service; no direct service-to-service HTTP calls initiated
- **must NOT call order-service or payment-service HTTP APIs** to backfill missing event data (consumer rule — all attribution comes from the cached `OrderPlaced` snapshot)

## Consumes From

| Source | Contract | Purpose |
|---|---|---|
| order-service | `specs/contracts/events/order-events.md` — topic `order.order.placed` | Cache per-order line snapshot `(orderId, sellerId, gross, tenant_id)` for future accrual |
| payment-service | `specs/contracts/events/payment-events.md` — topic `payment.payment.completed` | Trigger commission accrual (money captured — compute split per cached snapshot) |
| payment-service | `specs/contracts/events/payment-events.md` — topic `payment.payment.refunded` | Append reversal rows netting the order's accruals to zero |

## Publishes To

| Target | Contract | Purpose |
|---|---|---|
| (none yet — no consumer) | `specs/contracts/events/settlement-events.md` — topic `settlement.period.closed` | `settlement.period.closed.v1` emitted on period close via the transactional outbox (period-close increment). No service subscribes yet. |

`settlement.commission.accrued.v1` remains forward-declared / deferred (not defined or emitted).

## Forbidden Dependencies
- direct database access to another service (per `service-boundaries.md`)
- importing another service's internal code
- calling order-service or payment-service HTTP APIs for event compensation
- depending on another service's internal entity model

## Notes
All dependency changes that affect service boundaries must be reflected in related specs and contracts first.
`libs/java-messaging` (`OutboxAutoConfiguration` / `OutboxMetricsAutoConfiguration`) was excluded in v1 (terminal consumer) and is **introduced in the period-close increment** (TASK-BE-415) — settlement-service publishes exactly one event (`settlement.period.closed.v1`) via the transactional outbox.
