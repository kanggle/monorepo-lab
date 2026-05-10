# Service Dependencies

## Service
`shipping-service`

## Allowed Direct Dependencies
- `libs/java-common` (shared domain primitives)
- `libs/java-web` (shared web exception primitives, REST infrastructure)
- `libs/java-messaging` (outbox pattern — `OutboxWriter`, `OutboxPublisher`, `OutboxPollingScheduler`)
- `libs/java-observability` (metrics, tracing)
- `org.springframework.boot:spring-boot-starter-web`
- `org.springframework.boot:spring-boot-starter-data-jpa`
- `org.springframework.boot:spring-boot-starter-validation`
- `org.springframework.boot:spring-boot-starter-actuator`
- `org.springframework.kafka:spring-kafka` (event consumer and producer via outbox)
- `org.flywaydb:flyway-core` + `flyway-database-postgresql`
- `org.postgresql:postgresql`
- `org.springdoc:springdoc-openapi-starter-webmvc-ui`
- own database (shippings, shipping_status_history, outbox, processed_events)

## Allowed Service Interactions
- pure event-driven inbound; no outbound HTTP calls to other services
- exposes its own HTTP API for shipping status queries and admin status updates (consumed via gateway)

## Consumes From

| Source | Contract | Purpose |
|---|---|---|
| order-service | `specs/contracts/events/order-events.md` — topic `order.order.confirmed` | Create a new shipping record when an order transitions to CONFIRMED state |

## Publishes To

| Topic | Contract | Purpose |
|---|---|---|
| `shipping.shipping.status-changed` | `specs/contracts/events/shipping-events.md` | Notify order-service and notification-service of every shipping status transition |

## Forbidden Dependencies
- direct database access to another service (per `service-boundaries.md`)
- importing another service's internal code
- depending on another service's internal entity model
- calling non-public endpoints

## Notes
All dependency changes that affect service boundaries must be reflected in related specs and contracts first.
