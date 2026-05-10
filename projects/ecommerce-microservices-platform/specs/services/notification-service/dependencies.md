# Service Dependencies

## Service
`notification-service`

## Allowed Direct Dependencies
- `libs/java-web` (shared web exception primitives, REST infrastructure)
- `libs/java-observability` (metrics, tracing)
- `org.springframework.boot:spring-boot-starter-web`
- `org.springframework.boot:spring-boot-starter-data-jpa`
- `org.springframework.boot:spring-boot-starter-validation`
- `org.springframework.boot:spring-boot-starter-mail` (SMTP outbound for email delivery)
- `org.springframework.kafka:spring-kafka` (event consumer)
- `org.flywaydb:flyway-core` + `flyway-database-postgresql`
- `org.postgresql:postgresql`
- `org.springdoc:springdoc-openapi-starter-webmvc-ui`
- own database (`notification_db` — notifications, templates, preferences)
- SMTP relay (external mail server — outbound only, no response state stored)

## Allowed Service Interactions
- pure event consumer — no outbound HTTP calls to other services
- exposes its own HTTP API for managing notifications, templates, and preferences (consumed by authenticated users and admins via gateway)

## Consumes From

| Source | Contract | Purpose |
|---|---|---|
| order-service | `specs/contracts/events/order-events.md` — topic `order.order.placed` | Trigger ORDER_PLACED notification email to user |
| payment-service | `specs/contracts/events/payment-events.md` — topic `payment.payment.completed` | Trigger PAYMENT_COMPLETED notification email to user |
| shipping-service | `specs/contracts/events/shipping-events.md` — topic `shipping.shipping.status-changed` | Trigger SHIPPING_STATUS_CHANGED notification email to user |
| GAP (global-account-platform) | topic `auth.user.signed-up` (auth-events.md DEPRECATED — see `specs/integration/gap-integration.md`) | Trigger WELCOME notification email on new account registration |

## Publishes To
- None at current scope (notification-service is a pure event consumer; no domain events are published)

## Forbidden Dependencies
- direct database access to another service (per `service-boundaries.md`)
- importing another service's internal code
- depending on another service's internal entity model
- outbound HTTP calls to other internal services (notification delivery uses SMTP only)
- calling non-public endpoints

## Notes
All dependency changes that affect service boundaries must be reflected in related specs and contracts first.
