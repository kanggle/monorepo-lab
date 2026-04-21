# Service Overview

## Service
`payment-service`

## Responsibility
Owns the payment lifecycle. Processes payments when orders are placed and issues refunds when orders are cancelled.

## In Scope
- payment processing triggered by OrderPlaced event (PENDING record creation)
- payment confirmation via external PG (Toss Payments)
- refund processing triggered by OrderCancelled event (PG refund API call)
- payment status query by orderId

## Out of Scope
- order management (owned by order-service)
- product catalog and inventory management (owned by product-service)
- authentication and token issuance (owned by auth-service)

## Owned Data
- payment aggregate (paymentId, orderId, userId, amount, status, paymentMethod, paymentKey, receiptUrl, createdAt, paidAt, refundedAt)

## Published Interfaces
- payment HTTP APIs defined in `specs/contracts/http/payment-api.md`
- payment domain events defined in `specs/contracts/events/payment-events.md`

## Dependent Systems
- order-service (via published event contracts: OrderPlaced, OrderCancelled)
- Toss Payments API (external PG for payment confirmation and refund)
- persistence (relational database)
- messaging infrastructure (for event consuming and publishing)
