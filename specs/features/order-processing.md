# Feature: Order Processing

## Purpose

Manages the order lifecycle from placement through delivery or cancellation. Coordinates with payment-service for payment processing and with product-service for stock management via domain events.

## Related Services

| Service | Role |
|---|---|
| order-service | Primary owner — order creation, status lifecycle, cancellation, event publishing |
| payment-service | Consumes OrderPlaced to process payment; consumes OrderCancelled to process refund |
| product-service | Publishes StockChanged (ORDER_RESERVED) consumed by order-service for stock confirmation |
| web-store | Customer-facing order creation, order history, order detail UI |
| admin-dashboard | Admin order list, detail view, status management, cancellation |
| gateway-service | Request routing, user identity injection |

## User Flows

### Order Placement

1. Authenticated user sends POST /api/orders with items and shipping address
2. order-service validates request, creates order in PENDING status
3. order-service publishes OrderPlaced event
4. payment-service consumes OrderPlaced and processes payment
5. payment-service publishes PaymentCompleted event
6. order-service consumes PaymentCompleted and transitions order to CONFIRMED status

### Payment Failure

1. payment-service publishes PaymentFailed event
2. order-service consumes event and transitions PENDING order to CANCELLED status

### Order Status Lifecycle

```
PENDING → CONFIRMED → SHIPPED → DELIVERED
   ↓          ↓
CANCELLED  CANCELLED
```

- PENDING: order placed, awaiting confirmation
- CONFIRMED: order confirmed (payment completed or stock reserved)
- SHIPPED: order shipped
- DELIVERED: order delivered
- CANCELLED: order cancelled

### Order Cancellation

1. Order owner sends POST /api/orders/{orderId}/cancel
2. order-service validates order can be cancelled (only PENDING or CONFIRMED)
3. order-service transitions status to CANCELLED
4. order-service publishes OrderCancelled event
5. payment-service consumes OrderCancelled and processes refund
6. payment-service publishes PaymentRefunded event
7. order-service consumes PaymentRefunded and updates refund status

### Stock Confirmation

1. product-service publishes StockChanged with reason ORDER_RESERVED
2. order-service consumes event and transitions PENDING order to CONFIRMED

### User Withdrawal Impact

1. user-service publishes UserWithdrawn event
2. order-service consumes event and cancels all active orders for the withdrawn user

### Order Query

1. Authenticated user sends GET /api/orders with optional status filter and pagination
2. order-service returns paginated list of user's orders
3. GET /api/orders/{orderId} returns full order detail (items, shipping address, timestamps)

## Business Rules

- userId is extracted from authentication token (X-User-Id header), not from request body
- Orders can only be cancelled when status is PENDING or CONFIRMED
- Only the order owner can view or cancel their orders (403 for unauthorized access)
- Optimistic locking applied for concurrent order modifications
- All event consumers handle duplicates idempotently
- Failed events are routed to DLQ (Dead Letter Queue)

## Related Contracts

- HTTP: `specs/contracts/http/order-api.md`
- Events: `specs/contracts/events/order-events.md`

## Related Events

| Event | Publisher | Consumers |
|---|---|---|
| OrderPlaced | order-service | payment-service |
| OrderCancelled | order-service | payment-service |
| PaymentCompleted | payment-service | order-service |
| PaymentFailed | payment-service | order-service |
| PaymentRefunded | payment-service | order-service |
| StockChanged (ORDER_RESERVED) | product-service | order-service |
| UserWithdrawn | user-service | order-service |
