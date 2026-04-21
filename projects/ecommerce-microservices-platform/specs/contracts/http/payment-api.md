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

## Payment Status Values

| Status | Description |
|---|---|
| `PENDING` | Payment initiated, waiting for user to complete PG authorization |
| `COMPLETED` | Payment confirmed via Toss Payments |
| `FAILED` | Payment confirmation failed |
| `REFUNDED` | Payment has been refunded via Toss Payments |

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
