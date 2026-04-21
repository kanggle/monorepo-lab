# Service Architecture

## Service
`promotion-service`

## Service Type
`rest-api`

## Architecture Style
`DDD-style Architecture`

## Why This Architecture
Promotion and coupon management involves meaningful domain concepts: promotion aggregates, coupon lifecycle, discount rules, and usage constraints.

Domain invariants (e.g. coupon cannot be used twice, coupon quantity cannot go negative, expired coupons cannot be applied) require aggregate-level enforcement.

DDD-style keeps these rules in the domain layer and prevents them from leaking into infrastructure or presentation.

## Internal Structure Rule
This service uses a domain-driven internal structure.

Recommended internal areas:
- interface (presentation)
- application
- domain
- infrastructure

Key domain concepts:
- Aggregates: Promotion, Coupon
- Entities: CouponIssuance
- Value Objects: DiscountType (FIXED, PERCENTAGE), PromotionPeriod, CouponStatus (ISSUED, USED, EXPIRED)
- Domain Events: CouponUsed, CouponExpired
- Domain Services: DiscountCalculator
- Repositories: PromotionRepository, CouponRepository

## Allowed Dependencies
- interface -> application
- application -> domain
- infrastructure -> domain
- infrastructure -> application ports

## Forbidden Dependencies
- domain must not depend on framework or persistence details
- application must not contain domain rules that belong in aggregates
- controllers must not bypass application services
- repositories must not contain business decisions

## Boundary Rules
- interface layer handles HTTP mapping and request validation entry
- application layer coordinates use-cases and transaction boundaries
- domain layer owns promotion and coupon rules and invariants
- infrastructure layer handles persistence, event publishing, and external adapters

## Domain Scope
- Promotion (name, description, discount type, discount value, period, max issuance count)
- Coupon (code, promotion reference, user reference, status, issued/used/expired timestamps)
- Discount calculation (fixed amount, percentage with max cap)

## Domain Constraints
- promotion-service must NOT own order or payment logic
- A coupon can only be used once per issuance
- Coupon quantity must not go negative
- Expired coupons cannot be applied
- Discount amount must not exceed order total

## Integration Rules
- HTTP behavior must follow published contracts
- Domain events must follow published event contracts
- order-service communicates coupon application via synchronous HTTP call to promotion-service
- Shared libraries may be used only under shared-library policy

## Events
- Publishes: `CouponUsed`, `CouponExpired`
- Consumes: `OrderCancelled` (from order-service, restores USED coupons to ISSUED)

## Testing Expectations
Required emphasis:
- aggregate and domain rule tests
- application service tests
- repository integration tests
- event publishing tests
- concurrency tests (coupon quantity limit)

## Change Rule
Any architectural change to this service must be documented here first before implementation.
