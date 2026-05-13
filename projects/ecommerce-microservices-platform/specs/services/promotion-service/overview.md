# promotion-service — Overview

> 1-pager: responsibilities, public surface, key invariants.

## Service identity

| Field | Value |
|---|---|
| Service name | `promotion-service` |
| Project | `ecommerce-microservices-platform` |
| Service Type | `rest-api` |
| Architecture Style | **DDD-style** — see [architecture.md § Architecture Style](architecture.md) |
| Stack | Java 21, Spring Boot 3.4, PostgreSQL, Kafka, `libs/java-messaging` (transactional outbox) |
| Deployable unit | `apps/promotion-service/` |
| Bounded Context | `Promotion / Coupon` |
| Persistent stores | PostgreSQL (promotion + coupon) + Kafka outbox table |
| Event publication | `promotion.coupon.used` (CouponUsed), `promotion.coupon.expired` (CouponExpired) |

## Responsibilities

- Manage promotion CRUD (admin) and coupon issuance / lifecycle.
- Apply coupons at order placement — synchronous HTTP from `order-service`, returns discount amount.
- Enforce usage constraints (one-use, quantity cap, expiry).
- Restore coupons on `OrderCancelled` event consumption (set `USED → ISSUED`).
- Calculate discounts (fixed amount, percentage with max cap).
- Publish `CouponUsed` / `CouponExpired` via transactional outbox.

## Public surface

| Channel | Endpoint / Topic | Auth | Purpose |
|---|---|---|---|
| REST | `POST /api/promotions` | JWT + ROLE_ADMIN | create promotion |
| REST | `GET /api/promotions/{id}` | JWT | promotion detail |
| REST | `POST /api/coupons/issue` | JWT + ROLE_ADMIN | issue coupons to users |
| REST | `GET /api/coupons` | JWT (owner) | user's coupon list |
| REST | `POST /api/coupons/{code}/apply` | JWT (service-to-service from order-service) | apply coupon at order placement |
| Kafka consume | `order.order.cancelled` | — | coupon restoration |
| Kafka publish | `promotion.coupon.used`, `promotion.coupon.expired` | — | analytics / notification consumers |

자세한 spec 은 [`../../contracts/http/promotion-api.md`](../../contracts/http/promotion-api.md) + [`../../contracts/events/promotion-events.md`](../../contracts/events/promotion-events.md) 참조.

## Key invariants

1. **Coupon used 1 회만** — coupon aggregate 의 unique constraint + status guard. 위반 시 `CouponAlreadyUsed`.
2. **Coupon quantity must not go negative** — issuance quantity cap 도달 시 추가 발급 차단.
3. **Expired coupons cannot be applied** — `apply` 시 `expiry < now()` → `CouponExpired` 예외.
4. **Discount must not exceed order total** — application service 가 cap 강제; 초과 시 order total 만큼만 차감.
5. **No order/payment ownership** — promotion-service 는 coupon meta + 할인 계산만; 실제 주문 / 결제 transition 은 `order-service` / `payment-service` 가 owner.

## Owned Data

- promotion (`promotionId`, `name`, `description`, `discountType`, `discountValue`, `period`, `maxIssuanceCount`)
- coupon (`couponId`, `code`, `promotionId`, `userId`, `status`, issued / used / expired timestamps)

## Published Interfaces

- [`../../contracts/http/promotion-api.md`](../../contracts/http/promotion-api.md) (HTTP)
- [`../../contracts/events/promotion-events.md`](../../contracts/events/promotion-events.md) — `CouponUsed`, `CouponExpired`

## Dependent Systems

- PostgreSQL — promotion / coupon persistence
- Kafka — event consumption + publication
- `order-service` (events: `OrderCancelled`; sync HTTP for coupon apply from `order-service`)

## Out of scope (v1)

- Order processing — `order-service`.
- Payment processing — `payment-service`.
- Product catalog — `product-service`.
- Tier / membership-level discount — v2 (`membership-service` 도입 시).
