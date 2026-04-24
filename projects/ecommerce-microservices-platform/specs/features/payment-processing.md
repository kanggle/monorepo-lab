# Feature: Payment Processing

## Purpose

Handles payment lifecycle triggered by order events. Creates PENDING payments when orders are placed, confirms payments via Toss Payments PG, and issues refunds when orders are cancelled.

## Related Services

| Service | Role |
|---|---|
| payment-service | Primary owner — payment creation, PG confirmation, refund, status management, event publishing |
| order-service | Publishes OrderPlaced and OrderCancelled events that trigger payment operations |
| web-store | Displays Toss Payments widget for user-facing payment, shows payment status |
| gateway-service | Request routing, user identity injection (X-User-Id header) |

## User Flows

### Payment Processing (PG Integration)

1. order-service publishes OrderPlaced event
2. payment-service consumes event and creates payment record in PENDING status
3. web-store displays Toss Payments widget to user
4. User authorizes payment through Toss Payments widget
5. Toss Payments redirects to success URL with paymentKey, orderId, amount
6. web-store calls POST /api/payments/confirm with paymentKey, orderId, amount
7. payment-service calls Toss Payments Confirm API to verify the payment
8. On success: transitions to COMPLETED, publishes PaymentCompleted event
9. On failure: transitions to FAILED, returns error to client

### Refund Processing (Event-Driven)

1. order-service publishes OrderCancelled event
2. payment-service consumes event and locates corresponding payment
3. payment-service calls Toss Payments Cancel API for refund
4. On success: transitions payment to REFUNDED, publishes PaymentRefunded event
5. On failure: logs error, retries via DLQ mechanism

### Payment Query

1. Authenticated user sends GET /api/payments/orders/{orderId}
2. payment-service validates ownership (X-User-Id must match payment userId)
3. Returns payment details (paymentId, orderId, amount, status, paymentMethod, receiptUrl, timestamps)

## Business Rules

- Payment statuses: PENDING -> COMPLETED / FAILED; COMPLETED -> REFUNDED
- Payment is created automatically on OrderPlaced event -- no direct payment creation API
- Payment confirmation requires valid paymentKey from Toss Payments
- Amount in confirm request must match the PENDING payment amount (tampering prevention)
- Only the payment owner (matching userId) can confirm or query payment information
- Refund is triggered automatically on OrderCancelled event
- All event consumers handle duplicates idempotently
- Failed events are routed to DLQ
- Toss Payments secret key must not be exposed to frontend

## Related Contracts

- HTTP: `specs/contracts/http/payment-api.md`
- Events: `specs/contracts/events/payment-events.md`

## Related Events

| Event | Publisher | Consumers |
|---|---|---|
| OrderPlaced | order-service | payment-service |
| OrderCancelled | order-service | payment-service |
| PaymentCompleted | payment-service | order-service |
| PaymentFailed | payment-service | order-service |
| PaymentRefunded | payment-service | order-service |
