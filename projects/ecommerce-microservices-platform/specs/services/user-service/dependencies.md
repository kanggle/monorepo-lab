# Service Dependencies

## Service
`user-service`

## Allowed Direct Dependencies
- `libs/java-common` (shared domain primitives)
- `libs/java-web` (shared web exception primitives, REST infrastructure)
- `libs/java-observability` (metrics, tracing)
- `org.springframework.boot:spring-boot-starter-web`
- `org.springframework.boot:spring-boot-starter-data-jpa`
- `org.springframework.boot:spring-boot-starter-validation`
- `org.springframework.boot:spring-boot-starter-actuator`
- `org.springframework.kafka:spring-kafka` (event consumer and direct Kafka producer)
- `org.flywaydb:flyway-core` + `flyway-database-postgresql`
- `org.postgresql:postgresql`
- `org.springdoc:springdoc-openapi-starter-webmvc-ui`
- own database (`user_db` — user_profiles, user_addresses, wishlist_items)

## Allowed Service Interactions
- outbound HTTP to product-service (`GET /api/products/{productId}`) to enrich wishlist items with product name, price, and status (via `RestProductInfoProvider` using `RestTemplate`)
- no inbound direct service calls from other services — all access via gateway-service using published HTTP contract

## Consumes From

| Source | Contract | Purpose |
|---|---|---|
| GAP (global-account-platform) | topic `auth.user.signed-up` (auth-events.md DEPRECATED — see `specs/integration/gap-integration.md`) | Create a local user profile record when a new account is registered in GAP |
| product-service | `specs/contracts/http/product-api.md` — `GET /api/products/{productId}` | Fetch product details (name, price, status) for wishlist item enrichment |

## Publishes To

| Topic | Contract | Purpose |
|---|---|---|
| `user.user-profile.updated` | `specs/contracts/events/user-events.md` | Notify downstream services (admin-dashboard, future notification-service) of profile changes |
| `user.user-withdrawn` | `specs/contracts/events/user-events.md` | Notify downstream services (order-service, auth-service) when a user withdraws their account |

## Forbidden Dependencies
- direct database access to another service (per `service-boundaries.md`)
- importing another service's internal code
- depending on another service's internal entity model
- calling non-public endpoints

## Notes
All dependency changes that affect service boundaries must be reflected in related specs and contracts first.
