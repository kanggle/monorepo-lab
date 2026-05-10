# Service Dependencies

## Service
`review-service`

## Allowed Direct Dependencies
- `libs/java-web` (shared web exception primitives, REST infrastructure)
- `libs/java-observability` (metrics, tracing)
- `libs/java-messaging` (outbox pattern — `OutboxWriter`, `OutboxPublisher`, `OutboxPollingScheduler`)
- `org.springframework.boot:spring-boot-starter-web`
- `org.springframework.boot:spring-boot-starter-data-jpa`
- `org.springframework.boot:spring-boot-starter-validation`
- `org.springframework.kafka:spring-kafka` (event producer via outbox)
- `org.flywaydb:flyway-core` + `flyway-database-postgresql`
- `org.postgresql:postgresql`
- `org.springdoc:springdoc-openapi-starter-webmvc-ui`
- own database (reviews, outbox)

## Allowed Service Interactions
- outbound HTTP to order-service (`GET /api/orders/verify-purchase`) to verify a user has purchased a product before allowing review creation (via `OrderServiceClient` using `RestClient`)
- no inbound event subscriptions at current scope

## Consumes From

| Source | Contract | Purpose |
|---|---|---|
| order-service | `specs/contracts/http/order-api.md` — `GET /api/orders/verify-purchase` | Verify that a user has a completed purchase for a product before allowing a review to be created |

## Publishes To

| Topic | Contract | Purpose |
|---|---|---|
| `review.review.created` | `specs/contracts/events/review-events.md` | Notify search-service and product-service of a new review |
| `review.review.updated` | `specs/contracts/events/review-events.md` | Notify search-service and product-service of a review update |
| `review.review.deleted` | `specs/contracts/events/review-events.md` | Notify search-service and product-service of a review deletion |

## Forbidden Dependencies
- direct database access to another service (per `service-boundaries.md`)
- importing another service's internal code
- depending on another service's internal entity model
- calling non-public endpoints
- reading order-service internal tables directly to verify purchase (HTTP contract only)

## Notes
All dependency changes that affect service boundaries must be reflected in related specs and contracts first.
