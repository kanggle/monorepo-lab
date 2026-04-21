# Service Boundaries

Defines ownership rules and cross-service interaction constraints.

---

# Ownership Rule

See `ownership-rule.md` for the authoritative service, contract, and shared library ownership principles.

This document adds the concrete boundary constraints on top of those principles:

- No other service may read or write another service's database directly.
- Cross-service data access must go through published contracts only.

---

# Service Boundary Constraints

For each service's primary responsibility, see the Services table in `architecture.md`.

The constraints below define what each service **must not** own or do:

| Service | Must NOT |
|---|---|
| `gateway-service` | Contain business logic; persist domain data |
| `auth-service` | Own user profile data; other services must not replicate its auth logic |
| `user-service` | Own authentication credentials; must expose data only through published contracts |
| `order-service` | Own payment logic |
| `payment-service` | Own order aggregate state |
| `promotion-service` | Own order or payment logic |
| `notification-service` | Own user profile or order data; contain business logic beyond notification delivery |
| `review-service` | Own product or order data |
| `shipping-service` | Own order or payment data |
| `batch-worker` | Own primary domain state; call non-public endpoints |

---

# Cross-Service Interaction Rules

## Synchronous
- Services communicate via HTTP through published contracts only.
- A service must not call another service's internal endpoints.
- Circular synchronous dependencies are forbidden.

### HTTP Dependency Matrix

All client-facing HTTP requests enter through `gateway-service`.

| Caller | Callee | Contract | Purpose |
|---|---|---|---|
| gateway-service | auth-service | `auth-api.md` | Authentication (signup, login, refresh, logout) |
| gateway-service | user-service | `user-api.md`, `wishlist-api.md` | User profile, address, and wishlist management |
| gateway-service | product-service | `product-api.md` | Product catalog and admin management |
| gateway-service | search-service | `search-api.md` | Product search by keyword and filters |
| gateway-service | order-service | `order-api.md` | Order placement, listing, cancellation |
| gateway-service | payment-service | `payment-api.md` | Payment status queries |
| gateway-service | promotion-service | `promotion-api.md` | Promotion and coupon management |
| gateway-service | notification-service | `notification-api.md` | Notification history and preferences |
| gateway-service | review-service | `review-api.md` | Product reviews, ratings, user review history |
| gateway-service | shipping-service | `shipping-api.md` | Shipping status tracking |
| order-service | product-service | `product-api.md` | Stock validation at order placement |
| order-service | promotion-service | `promotion-api.md` | Coupon application at order placement |
| user-service | product-service | `product-api.md` | Product info for wishlist display |
| review-service | order-service | `order-api.md` | Purchase verification for review creation |

- No circular synchronous dependencies exist.
- All other inter-service communication uses events (see `event-driven-policy.md`).

## Asynchronous
- Services communicate via domain events on shared messaging infrastructure.
- Event contracts must be defined in `specs/contracts/events/` before use.

## Data
- Each service has its own database. Shared databases are forbidden.
- A service must not import or embed another service's entity or table.
- Cross-service data access must go through contracts or local projections.
