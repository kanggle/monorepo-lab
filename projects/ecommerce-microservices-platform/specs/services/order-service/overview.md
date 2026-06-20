# order-service — Overview

> 1-pager: responsibilities, public surface, key invariants.

## Service identity

| Field | Value |
|---|---|
| Service name | `order-service` |
| Project | `ecommerce-microservices-platform` |
| Service Type | `rest-api` |
| Architecture Style | **DDD-style** — see [architecture.md § Architecture Style](architecture.md) |
| Stack | Java 21, Spring Boot 3.4, PostgreSQL, Kafka, `libs/java-messaging` (transactional outbox) |
| Deployable unit | `apps/order-service/` |
| Bounded Context | `Order` |
| Persistent stores | PostgreSQL (order aggregate + items) + Kafka outbox table (`libs/java-messaging` standard schema) |
| Event publication | `order.order.placed` (OrderPlaced), `order.order.cancelled` (OrderCancelled) |

## Responsibilities

- Own the order aggregate lifecycle: `PENDING → CONFIRMED → SHIPPED → DELIVERED` / `CANCELLED`.
- Validate and place new orders; enforce aggregate-boundary consistency.
- React to upstream events: `PaymentCompleted` → `CONFIRMED`, `PaymentFailed` → `CANCELLED`, `PaymentRefunded` → status sync.
- Handle user withdrawal via `UserWithdrawn` event (cancel open orders or mark for cleanup).
- Publish `OrderPlaced` / `OrderCancelled` via transactional outbox (single TX with aggregate write).

## Public surface

| Channel | Endpoint / Topic | Auth | Purpose |
|---|---|---|---|
| REST | `POST /api/orders` | JWT | place new order |
| REST | `GET /api/orders/{id}` | JWT (owner / ROLE_ADMIN) | order detail |
| REST | `GET /api/orders` | JWT (owner) | user order history |
| REST | `POST /api/orders/{id}/cancel` | JWT (owner) | cancel by user |
| REST (internal) | `POST /api/internal/orders/confirm-paid-stale` | `client_credentials` Bearer (gateway-excluded, fail-closed) | system stale paid-order forward-confirm (`PENDING AND payment_id IS NOT NULL` → `CONFIRMED`); called by batch-worker (TASK-BE-410/412). Disjoint from BE-138 (`payment_id IS NULL`). |
| Kafka consume | `payment.payment.completed`, `payment.payment.failed`, `payment.payment.refunded` | — | status transitions |
| Kafka consume | `user.user.withdrawn` | — | user cleanup |
| Kafka publish | `order.order.placed`, `order.order.cancelled` | — | downstream consumers (payment / shipping / notification / promotion) |

자세한 spec 은 [`../../contracts/http/order-api.md`](../../contracts/http/order-api.md) + [`../../contracts/events/order-events.md`](../../contracts/events/order-events.md) 참조.

## Key invariants

1. **Status transitions follow the defined lifecycle** — no backward transition, no skip. 위반 시 `IllegalOrderStateTransition`.
2. **Aggregate boundary** — 다른 service 가 order 테이블 직접 read/write 금지; HTTP 또는 event 만 통과.
3. **Outbox + aggregate atomic commit** — `OrderPlaced` outbox row 와 aggregate 쓰기가 한 TX 안에서 atomic. dual-write 금지.
4. **Domain ↛ framework** — domain layer 는 Spring / JPA / Kafka SDK 직접 import 금지 (architecture.md § Forbidden Dependencies).
5. **Repositories carry no business decision** — 분기 로직 repository 금지.

## Owned Data

- order aggregate (`orderId`, `userId`, `items[]`, `status`, `totalPrice`, `shippingAddress`, timestamps) + order item records.

## Published Interfaces

- [`../../contracts/http/order-api.md`](../../contracts/http/order-api.md) (HTTP)
- [`../../contracts/http/internal/order-confirm-paid-stale.md`](../../contracts/http/internal/order-confirm-paid-stale.md) (internal system-command HTTP — `client_credentials`, gateway-excluded)
- [`../../contracts/events/order-events.md`](../../contracts/events/order-events.md) — `OrderPlaced`, `OrderCancelled`, `OrderConfirmed`

## Dependent Systems

- PostgreSQL — order aggregate persistence
- Kafka — event publication + consumption
- `payment-service` (events)
- `user-service` (events)
- `product-service` (optional sync HTTP for product snapshot)
- `promotion-service` (optional sync HTTP for coupon apply)

## Out of scope (v1)

- Payment processing — owned by `payment-service`.
- Product catalog / inventory — owned by `product-service`; order snapshots product info at placement.
- Authentication / token issuance — owned by `auth-service` (deprecated) → IAM.
- Stock reservation enforcement — `product-service` 가 stock 검증, order 는 결과 반영만.
