# Service Dependencies

## Service
`payment-service`

## Allowed Direct Dependencies
- `libs/java-web` (shared web exception primitives, REST infrastructure)
- `libs/java-common` (UUID v7 primitives)
- `libs/java-messaging` (transactional outbox — `OutboxWriter`, `OutboxPollingScheduler`)
- `libs/java-observability` (metrics, tracing)
- `io.micrometer:micrometer-core`
- `org.springframework.boot:spring-boot-starter-web`
- `org.springframework.boot:spring-boot-starter-data-jpa`
- `org.springframework.boot:spring-boot-starter-validation`
- `org.springframework.kafka:spring-kafka` (event consumer + outbox relay producer)
- `org.flywaydb:flyway-core` + `flyway-database-postgresql`
- `org.postgresql:postgresql`
- `org.springdoc:springdoc-openapi-starter-webmvc-ui`
- own database (`payment_db` — payments, outbox, processed_events)
- Toss Payments PG API (external — outbound HTTP via `TossPaymentsAdapter`)

## Allowed Service Interactions
- outbound HTTP to Toss Payments API (external PG provider) for payment confirmation and refund
- no inbound direct service calls — all access via gateway-service using published HTTP contract

## Consumes From

| Source | Contract | Purpose |
|---|---|---|
| order-service | `specs/contracts/events/order-events.md` — topic `order.order.placed` | Create a pending payment record when a new order is placed |
| order-service | `specs/contracts/events/order-events.md` — topic `order.order.cancelled` | Initiate refund via Toss Payments when an order is cancelled |

## Publishes To

| Topic | Contract | Purpose |
|---|---|---|
| `payment.payment.completed` | `specs/contracts/events/payment-events.md` | Notify order-service and notification-service that payment succeeded |
| `payment.payment.refunded` | `specs/contracts/events/payment-events.md` | Notify order-service that a refund has been processed |

## Forbidden Dependencies
- direct database access to another service (per `service-boundaries.md`)
- importing another service's internal code
- depending on another service's internal entity model
- calling non-public endpoints
- storing raw card data (PCI-DSS scope must remain with Toss Payments)

## Notes
All dependency changes that affect service boundaries must be reflected in related specs and contracts first.

**Delivery semantic (ADR-006 Scenario A — COMPLETED, TASK-BE-136)**:
payment-service publishes both `payment.payment.completed` and
`payment.payment.refunded` through the **transactional outbox**
(`libs/java-messaging`). The envelope row commits atomically with the
payment state mutation; `PaymentEventOutboxRelay` (extends
`OutboxPollingScheduler`) polls `outbox` at the configured interval and
retries Kafka publish until broker ack. Producer-side silent loss on
`KafkaException` is no longer possible. See
[ADR-006](../../../docs/adr/ADR-006-at-least-once-delivery-policy.md) and
`specs/services/payment-service/architecture.md` § Event Publication.
