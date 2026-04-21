# Service Overview

## Service
`order-service`

## Responsibility
Owns the order aggregate lifecycle from placement through completion or cancellation.

## In Scope
- order placement and validation
- order status lifecycle management (PENDING → CONFIRMED → SHIPPED → DELIVERED / CANCELLED)
- order query by user
- order cancellation
- order-related domain event publishing
- order status transitions triggered by payment events (PaymentCompleted, PaymentFailed, PaymentRefunded)
- order handling on user withdrawal (UserWithdrawn event)

## Out of Scope
- payment processing logic (owned by payment-service)
- product catalog and inventory management (owned by product-service)
- authentication and token issuance (owned by auth-service)
- stock reservation enforcement (coordination with product-service is an integration concern)

## Owned Data
- order aggregate (orderId, userId, items, status, totalPrice, shippingAddress, timestamps)
- order item records (productId, variantId, productName, optionName, quantity, unitPrice)

## Published Interfaces
- order HTTP APIs defined in `specs/contracts/http/order-api.md`
- order domain events defined in `specs/contracts/events/order-events.md`

## Dependent Systems
- persistence (relational database)
- messaging infrastructure (for event consuming and publishing)
- product-service (via published HTTP contract for product/variant validation — optional at bootstrap)
- payment-service (via published event contracts: PaymentCompleted, PaymentFailed, PaymentRefunded)
- user-service (via published event contract: UserWithdrawn)
