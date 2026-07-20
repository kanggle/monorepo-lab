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
| 403 | ACCESS_DENIED | A payment for this `orderId` already exists and belongs to a different user **in the caller's own tenant** |
| 404 | PAYMENT_NOT_FOUND | No payment for this `orderId` in the caller's own tenant, **and** either none exists at all or one exists under a **different tenant** — the two cases are indistinguishable to the caller (TASK-BE-543 AC-1; cross-tenant existence is masked, not confirmed with 403/409) |

> Notes
> - Any `userId` field present in the request body is rejected (unknown property) — the server uses the `X-User-Id` header exclusively.
> - Ownership of the underlying order is verified against the existing payment record's `userId` when one exists **for the caller's own tenant**. The first payment for an `orderId` cannot be cross-checked against order-service at this layer; downstream order-service correlation is the source of truth for `orderId` → `userId` ownership.
> - `orderId` is globally unique (order-service assigns it once via `UUID.randomUUID()`, no tenant-partitioned scheme), so `payments.order_id` is a global `UNIQUE` constraint. A request referencing an `orderId` that already has a payment under a **different** tenant is rejected with 404 before any write is attempted — this both prevents hitting that global constraint and avoids confirming cross-tenant existence (M3).

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
repeated calls with **distinct** `Idempotency-Key`s accumulate until the payment is
fully refunded. Each *performed* refund publishes a `PaymentRefunded` event (see
`events/payment-events.md`). Only the payment owner (`userId` matching `X-User-Id`)
may refund.

**Idempotency (required on this endpoint)**

This is a funds-out path, so the client key is **mandatory** (ADR-002 Decision-3):
a missing or blank `Idempotency-Key` is refused with **400 `IDEMPOTENCY_KEY_REQUIRED`**
rather than falling back to a non-idempotent write. The key is scoped to
`{paymentId}` — the same key value may be reused against a different payment.

| Replay shape | Behaviour |
|---|---|
| Same key, same `amount` | **Replay.** Returns **200** with the payment's current state. `refundedAmount` is NOT increased a second time, the PG is NOT re-called, and NO second `PaymentRefunded` is published. |
| Same key, different `amount` | **409 `IDEMPOTENCY_KEY_CONFLICT`** — the key is bound to the first request's amount; it is never silently replayed for a different one. |
| Different key, same payment | A **genuine second partial refund**. Proceeds normally and accumulates (subject to the remaining-refundable check). |

Records are retained indefinitely (no TTL), so a replay window never expires into a
second payout. Under two simultaneous duplicates the loser of the
`UNIQUE (payment_id, idempotency_key)` insert also receives **409
`IDEMPOTENCY_KEY_CONFLICT`**; retrying that request hits the replay row and returns 200.

**Request Headers**
- `X-User-Id: string` (required)
- `Idempotency-Key: string` (**required**) — client-supplied, scoped to `{paymentId}`

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
| 400 | IDEMPOTENCY_KEY_REQUIRED | `Idempotency-Key` header missing or blank |
| 401 | UNAUTHORIZED | Missing or invalid access token |
| 403 | ACCESS_DENIED | Not the payment owner |
| 404 | PAYMENT_NOT_FOUND | Payment does not exist |
| 409 | IDEMPOTENCY_KEY_CONFLICT | The same `Idempotency-Key` was replayed for this payment with a different `amount`, or lost a concurrent same-key insert race |

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
