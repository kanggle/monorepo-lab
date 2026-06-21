# HTTP Contract: payment-service

## Overview
Published HTTP API for payment-service.
All endpoints are accessible through gateway-service only.
All endpoints require an authenticated user (Bearer token).

---

## Base Path
`/api/payments`

---

## Endpoints

### POST /api/payments
Create a `PENDING` payment for a given order.
The payment is created on behalf of the authenticated caller; `userId` is taken from the `X-User-Id` header only.
If a payment record already exists for the same `orderId` owned by the same caller, the request is treated as idempotent (201 with no side effects).

**Request Headers**
- `X-User-Id: string` (required)

**Request Body**
```json
{
  "orderId": "string (UUID)",
  "amount": 30000
}
```

**Response 201**
No body.

**Error responses**
| Status | Code | Reason |
|---|---|---|
| 400 | INVALID_PAYMENT_REQUEST | Missing or invalid field, missing `X-User-Id` header |
| 400 | VALIDATION_ERROR | Malformed request body |
| 401 | UNAUTHORIZED | Missing or invalid access token |
| 403 | ACCESS_DENIED | A payment for this `orderId` already exists and belongs to a different user |

> Notes
> - Any `userId` field present in the request body is rejected (unknown property) — the server uses the `X-User-Id` header exclusively.
> - Ownership of the underlying order is verified against the existing payment record's `userId` when one exists. The first payment for an `orderId` cannot be cross-checked at this layer; downstream order-service correlation is the source of truth.

---

### POST /api/payments/confirm
Confirm a payment after user authorization through Toss Payments.
Only the payment owner (userId matching X-User-Id) may confirm.

**Request Headers**
- `X-User-Id: string` (required)

**Request Body**
```json
{
  "paymentKey": "string (from Toss Payments)",
  "orderId": "string (UUID)",
  "amount": 30000
}
```

**Response 200**
```json
{
  "paymentId": "string (UUID)",
  "orderId": "string (UUID)",
  "status": "COMPLETED",
  "paymentMethod": "CARD",
  "receiptUrl": "string (URL, nullable)",
  "paidAt": "string (ISO 8601)"
}
```

**Error responses**
| Status | Code | Reason |
|---|---|---|
| 400 | INVALID_PAYMENT_REQUEST | Missing or invalid field |
| 400 | AMOUNT_MISMATCH | Confirm amount does not match PENDING payment amount |
| 401 | UNAUTHORIZED | Missing or invalid access token |
| 403 | ACCESS_DENIED | Not the payment owner |
| 404 | PAYMENT_NOT_FOUND | Payment for given order does not exist |
| 409 | PAYMENT_ALREADY_COMPLETED | Payment is not in PENDING status |
| 502 | PG_CONFIRM_FAILED | Toss Payments confirmation API returned an error |

---

### GET /api/payments/orders/{orderId}
Get payment information for a given order.
Only the payment owner (userId matching X-User-Id) may access.

**Request Headers**
- `X-User-Id: string` (required)

**Response 200**
```json
{
  "paymentId": "string (UUID)",
  "orderId": "string (UUID)",
  "userId": "string (UUID)",
  "amount": 30000,
  "status": "COMPLETED",
  "paymentMethod": "CARD",
  "paymentKey": "string (nullable)",
  "receiptUrl": "string (URL, nullable)",
  "createdAt": "string (ISO 8601)",
  "paidAt": "string (ISO 8601)",
  "refundedAt": null
}
```

**Error responses**
| Status | Code | Reason |
|---|---|---|
| 400 | INVALID_PAYMENT_REQUEST | Missing or invalid field |
| 401 | UNAUTHORIZED | Missing or invalid access token |
| 403 | ACCESS_DENIED | Not the payment owner |
| 404 | PAYMENT_NOT_FOUND | Payment for given order does not exist |

---

### POST /api/payments/{paymentId}/refund
Refund a payment, in full or in part. A partial refund returns the requested `amount`
(must be `> 0` and `≤` the remaining refundable = captured `amount − totalRefunded`);
repeated calls accumulate until the payment is fully refunded. Each call publishes a
`PaymentRefunded` event (see `events/payment-events.md`). Only the payment owner
(`userId` matching `X-User-Id`) may refund.

**Request Headers**
- `X-User-Id: string` (required)

**Request Body**
```json
{
  "amount": 10000
}
```

**Response 200**
```json
{
  "paymentId": "string (UUID)",
  "orderId": "string (UUID)",
  "userId": "string (UUID)",
  "amount": 30000,
  "refundedAmount": 10000,
  "status": "PARTIALLY_REFUNDED",
  "refundedAt": "string (ISO 8601)"
}
```
- `amount` — the captured payment total. `refundedAmount` — cumulative refunded.
- `status` is `PARTIALLY_REFUNDED` while `refundedAmount < amount`, `REFUNDED` once equal.

**Error responses**
| Status | Code | Reason |
|---|---|---|
| 400 | INVALID_PAYMENT_REQUEST | `amount` ≤ 0 or > remaining refundable, or payment not in a refundable state |
| 401 | UNAUTHORIZED | Missing or invalid access token |
| 403 | ACCESS_DENIED | Not the payment owner |
| 404 | PAYMENT_NOT_FOUND | Payment does not exist |

---

## Payment Status Values

| Status | Description |
|---|---|
| `PENDING` | Payment initiated, waiting for user to complete PG authorization |
| `COMPLETED` | Payment confirmed via Toss Payments |
| `FAILED` | Payment confirmation failed |
| `PARTIALLY_REFUNDED` | Part of the captured amount has been refunded; more remains refundable |
| `REFUNDED` | Payment has been fully refunded via Toss Payments |

---

## Error Response Format
```json
{
  "code": "string",
  "message": "string",
  "timestamp": "string (ISO 8601)"
}
```

## Notes
- `userId` is extracted from the `X-User-Id` header, not from the request body.
- Internal stack traces must not appear in error responses.
- `paymentKey` is the Toss Payments transaction identifier used for confirmation and refund.
