# Service Overview

## Service
`shipping-service`

## Responsibility
Owns shipping lifecycle management, including shipping record creation upon order confirmation and status transition tracking.

## In Scope
- shipping record creation on OrderConfirmed event
- shipping status transitions (PREPARING → SHIPPED → IN_TRANSIT → DELIVERED)
- shipping status query by order
- domain event publishing (ShippingStatusChanged)
- idempotent event processing (duplicate OrderConfirmed detection)

## Out of Scope
- order processing (owned by order-service)
- payment processing (owned by payment-service)
- notification delivery (owned by notification-service)

## Owned Data
- shipping (shippingId, orderId, userId, status, trackingNumber, carrier, status history, timestamps)

## Published Interfaces
- shipping HTTP APIs defined in `specs/contracts/http/shipping-api.md`
- shipping domain events defined in `specs/contracts/events/shipping-events.md`

## Dependent Systems
- persistence (relational database)
- messaging infrastructure (event consumption and publication)
- order-service (consumes OrderConfirmed event)
