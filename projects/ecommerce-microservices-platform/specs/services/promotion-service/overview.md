# Service Overview

## Service
`promotion-service`

## Responsibility
Owns promotion and coupon lifecycle management, including coupon issuance, usage, expiration, and discount calculation.

## In Scope
- promotion CRUD (admin)
- coupon issuance to users
- coupon application at order placement (synchronous HTTP call from order-service)
- coupon usage tracking and quantity enforcement
- coupon expiration processing
- coupon restoration on order cancellation (OrderCancelled event)
- discount calculation (fixed amount, percentage with max cap)
- domain event publishing (CouponUsed, CouponExpired)

## Out of Scope
- order processing (owned by order-service)
- payment processing (owned by payment-service)
- product catalog management (owned by product-service)

## Owned Data
- promotion (promotionId, name, description, discountType, discountValue, period, maxIssuanceCount)
- coupon (couponId, code, promotionId, userId, status, issued/used/expired timestamps)

## Published Interfaces
- promotion HTTP APIs defined in `specs/contracts/http/promotion-api.md`
- promotion domain events defined in `specs/contracts/events/promotion-events.md`

## Dependent Systems
- persistence (relational database)
- messaging infrastructure (event consumption and publication)
- order-service (consumes OrderCancelled event to restore coupons)
