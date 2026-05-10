# Service Dependencies

## Service
`order-service`

## Allowed Direct Dependencies
- `libs/java-web` (shared web exception primitives, REST infrastructure)
- `libs/java-common` (shared domain primitives)
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
- own database (`order_db` — orders, order_items, outbox, processed_events)

## Allowed Service Interactions
- exposes its own HTTP API for order placement, cancellation, and purchase verification (consumed by review-service via `GET /api/orders/verify-purchase`)
- all inbound interactions arrive through gateway-service; no direct service-to-service HTTP calls initiated

## Consumes From

| Source | Contract | Purpose |
|---|---|---|
| payment-service | `specs/contracts/events/payment-events.md` — topic `payment.payment.completed` | Mark order payment confirmed; transition order state after successful PG confirmation |
| payment-service | `specs/contracts/events/payment-events.md` — topic `payment.payment.refunded` | Mark order as refunded after cancellation saga completes |
| product-service | `specs/contracts/events/product-events.md` — topic `product.product.stock-changed` | Confirm order when stock reservation (reason=`ORDER_RESERVED`) is acknowledged |
| user-service | `specs/contracts/events/user-events.md` — topic `user.user.withdrawn` | Cancel all pending orders for a withdrawn user |

## Publishes To

| Topic | Contract | Purpose |
|---|---|---|
| `order.order.placed` | `specs/contracts/events/order-events.md` | Notify payment-service and promotion-service that a new order was created |
| `order.order.cancelled` | `specs/contracts/events/order-events.md` | Notify payment-service (refund) and promotion-service (coupon restore) of cancellation |
| `order.order.confirmed` | `specs/contracts/events/order-events.md` | Notify shipping-service to create a shipment record after order is confirmed |

## Forbidden Dependencies
- direct database access to another service (per `service-boundaries.md`)
- importing another service's internal code
- depending on another service's internal entity model
- calling non-public endpoints

## Notes
All dependency changes that affect service boundaries must be reflected in related specs and contracts first.
