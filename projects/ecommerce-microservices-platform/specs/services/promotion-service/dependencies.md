# Service Dependencies

## Service
`promotion-service`

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
- own database (promotions, coupons, outbox, processed_events)

## Allowed Service Interactions
- pure event-driven; no outbound HTTP calls to other services
- exposes its own HTTP API for promotion and coupon management (consumed by authenticated users and admins via gateway)

## Consumes From

| Source | Contract | Purpose |
|---|---|---|
| order-service | `specs/contracts/events/order-events.md` — topic `order.order.cancelled` | Restore a previously applied coupon when the associated order is cancelled |

## Publishes To

| Topic | Contract | Purpose |
|---|---|---|
| `promotion.coupon.used` | `specs/contracts/events/promotion-events.md` | Notify downstream services (order-service, future notification-service) that a coupon was applied |
| `promotion.coupon.expired` | `specs/contracts/events/promotion-events.md` | Notify downstream services (future notification-service) that a coupon has expired |

## Forbidden Dependencies
- direct database access to another service (per `service-boundaries.md`)
- importing another service's internal code
- depending on another service's internal entity model
- calling non-public endpoints

## Notes
All dependency changes that affect service boundaries must be reflected in related specs and contracts first.
