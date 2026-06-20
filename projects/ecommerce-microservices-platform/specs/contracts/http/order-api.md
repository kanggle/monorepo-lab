# HTTP Contract: order-service

## Overview
Published HTTP API for order-service.
All endpoints are accessible through gateway-service only.
All endpoints require an authenticated user (Bearer token).

---

## Base Path
`/api/orders`

---

## Endpoints

### POST /api/orders
Place a new order.

**Request Body**
```json
{
  "items": [
    {
      "productId": "string (UUID)",
      "variantId": "string (UUID)",
      "productName": "string",
      "optionName": "string (optional)",
      "quantity": 2,
      "unitPrice": 15000,
      "sellerId": "string (optional — owning seller of this line)"
    }
  ],
  "shippingAddress": {
    "recipientName": "string",
    "phone": "string",
    "zipCode": "string",
    "address1": "string",
    "address2": "string | null"
  }
}
```

`items[].sellerId` (inner marketplace axis — ADR-MONO-030 Step 3 §3.2) is the
owning seller of the line, supplied by the client as a denormalized snapshot
(like `productName` / `unitPrice`; order-service does not call product-service)
and captured immutably on the line. A single order may span multiple sellers.
Absent → the default seller `default` (D8 net-zero).

**Response 201**
```json
{
  "orderId": "string (UUID)"
}
```

**Error responses**
| Status | Code | Reason |
|---|---|---|
| 400 | INVALID_ORDER_REQUEST | Missing or invalid field |
| 401 | UNAUTHORIZED | Missing or invalid access token |

---

### GET /api/orders
List orders for the authenticated user.

**Query Parameters**
- `page` (default: 0) — page number
- `size` (default: 20) — page size
- `status` (optional) — filter by order status (one of: `PENDING`, `CONFIRMED`, `SHIPPED`, `DELIVERED`, `CANCELLED`)

**Response 200**
```json
{
  "content": [
    {
      "orderId": "string (UUID)",
      "status": "PENDING",
      "totalPrice": 30000,
      "itemCount": 2,
      "firstItemName": "string | null",
      "createdAt": "string (ISO 8601)"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 5
}
```

`firstItemName` is the product name of the first line item in the order
(used by clients for list previews). It is `null` when the order has no items.

**Error responses**
| Status | Code | Reason |
|---|---|---|
| 401 | UNAUTHORIZED | Missing or invalid access token |

---

### GET /api/orders/{orderId}
Get order detail for the authenticated user.

**Response 200**
```json
{
  "orderId": "string (UUID)",
  "status": "PENDING",
  "totalPrice": 30000,
  "items": [
    {
      "productId": "string (UUID)",
      "variantId": "string (UUID)",
      "productName": "string",
      "optionName": "string",
      "quantity": 2,
      "unitPrice": 15000,
      "sellerId": "string (owning seller of this line)"
    }
  ],
  "shippingAddress": {
    "recipientName": "string",
    "phone": "string",
    "zipCode": "string",
    "address1": "string",
    "address2": "string | null"
  },
  "createdAt": "string (ISO 8601)",
  "updatedAt": "string (ISO 8601)"
}
```

**Error responses**
| Status | Code | Reason |
|---|---|---|
| 401 | UNAUTHORIZED | Missing or invalid access token |
| 403 | ACCESS_DENIED | Not the order owner |
| 404 | ORDER_NOT_FOUND | Order with given ID does not exist |

---

### GET /api/orders/verify-purchase
Verify if the authenticated user has purchased a specific product (in DELIVERED status).
Used by review-service for purchase verification before review creation.

**Query Parameters**
- `productId` (required) — product ID to verify (UUID)

**Response 200**
```json
{
  "purchased": true
}
```

**Error responses**
| Status | Code | Reason |
|---|---|---|
| 400 | VALIDATION_ERROR | Missing or invalid productId |
| 401 | UNAUTHORIZED | Missing or invalid access token |

---

### POST /api/orders/{orderId}/cancel
Cancel an order. Only the order owner may cancel.

**Request Body**
```json
{}
```

**Response 200**
```json
{
  "orderId": "string (UUID)",
  "status": "CANCELLED"
}
```

**Error responses**
| Status | Code | Reason |
|---|---|---|
| 401 | UNAUTHORIZED | Missing or invalid access token |
| 403 | ACCESS_DENIED | Not the order owner |
| 404 | ORDER_NOT_FOUND | Order with given ID does not exist |
| 422 | ORDER_CANNOT_BE_CANCELLED | Order status does not allow cancellation |

---

## Order Status Values

| Status | Description |
|---|---|
| `PENDING` | Order placed, awaiting confirmation |
| `CONFIRMED` | Order confirmed by system or admin |
| `SHIPPED` | Order shipped |
| `DELIVERED` | Order delivered |
| `CANCELLED` | Order cancelled |

---

## Error Response Format
```json
{
  "code": "string",
  "message": "string",
  "timestamp": "string (ISO 8601)"
}
```

---

## Admin Endpoints

All admin endpoints require the `ADMIN` role. Non-admin users receive `403 ACCESS_DENIED`.

### GET /api/admin/orders
List all orders (admin view).

**Query Parameters**
- `page` (default: 0) — page number
- `size` (default: 20) — page size
- `status` (optional) — filter by order status (one of: `PENDING`, `CONFIRMED`, `SHIPPED`, `DELIVERED`, `CANCELLED`)

**Response 200**
```json
{
  "content": [
    {
      "orderId": "string (UUID)",
      "userId": "string (UUID)",
      "status": "PENDING",
      "totalPrice": 30000,
      "itemCount": 2,
      "firstItemName": "string | null",
      "createdAt": "string (ISO 8601)"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 5
}
```

**Error responses**
| Status | Code | Reason |
|---|---|---|
| 401 | UNAUTHORIZED | Missing or invalid access token |
| 403 | ACCESS_DENIED | Requires ADMIN role |

---

### GET /api/admin/orders/{orderId}
Get order detail (admin view). Includes order owner userId.

**Response 200**
```json
{
  "orderId": "string (UUID)",
  "userId": "string (UUID)",
  "status": "PENDING",
  "totalPrice": 30000,
  "items": [
    {
      "productId": "string (UUID)",
      "variantId": "string (UUID)",
      "productName": "string",
      "optionName": "string",
      "quantity": 2,
      "unitPrice": 15000,
      "sellerId": "string (owning seller of this line)"
    }
  ],
  "shippingAddress": {
    "recipient": "string",
    "phone": "string",
    "zipCode": "string",
    "address1": "string",
    "address2": "string | null"
  },
  "createdAt": "string (ISO 8601)",
  "updatedAt": "string (ISO 8601)"
}
```

**Error responses**
| Status | Code | Reason |
|---|---|---|
| 401 | UNAUTHORIZED | Missing or invalid access token |
| 403 | ACCESS_DENIED | Requires ADMIN role |
| 404 | ORDER_NOT_FOUND | Order with given ID does not exist |

---

### POST /api/admin/orders/{orderId}/status
Change order status (admin only).

**Allowed transitions:**
- `PENDING` → `CONFIRMED`
- `CONFIRMED` → `SHIPPED`
- `SHIPPED` → `DELIVERED`
- `PENDING` or `CONFIRMED` → `CANCELLED`

**Request Body**
```json
{
  "status": "CONFIRMED"
}
```

**Response 200**
```json
{
  "orderId": "string (UUID)",
  "status": "CONFIRMED"
}
```

**Error responses**
| Status | Code | Reason |
|---|---|---|
| 400 | INVALID_ORDER_REQUEST | Invalid status value or transition |
| 401 | UNAUTHORIZED | Missing or invalid access token |
| 403 | ACCESS_DENIED | Requires ADMIN role |
| 404 | ORDER_NOT_FOUND | Order with given ID does not exist |

---

## Internal Endpoints (gateway-excluded, system-to-system)

These endpoints are **not** routed by the gateway (only `/api/orders/**` and `/api/admin/orders/**`
are) and are reachable only on the internal service network. They authenticate with a
`client_credentials` Bearer JWT (NOT a user/admin token) and are **fail-closed** (missing/invalid
token → 401).

### POST /api/internal/orders/confirm-paid-stale
System stale paid-order **forward-confirm**. Recovers orders that paid but whose confirmation
event was lost — `status='PENDING' AND payment_id IS NOT NULL` past `olderThanMinutes` — by
transitioning each `PENDING → CONFIRMED` (the same transition the normal saga path performs) and
emitting the standard `OrderConfirmed` event so downstream fulfillment fires. Called by
batch-worker's `stalePaidOrderConfirmationJob` (TASK-BE-410 decision; impl TASK-BE-412/413).

Full contract (auth, request/response, server-side predicate, idempotency, BE-138 disjointness):
[`internal/order-confirm-paid-stale.md`](internal/order-confirm-paid-stale.md).

**System PENDING → CONFIRMED recovery path & BE-138 boundary.** order-service has two
PENDING-sweep recovery owners, **disjoint on `payment_id`**:

| Owner | Predicate | Action | Event |
|---|---|---|---|
| BE-138 `OrderStuckDetector` (in order-service) | `PENDING AND payment_id IS NULL` | escalate to terminal `STUCK_RECOVERY_FAILED` (never confirms) | `OrderSagaRecoveryExhausted` |
| This endpoint (batch-triggered, BE-410) | `PENDING AND payment_id IS NOT NULL` | forward-confirm `PENDING → CONFIRMED` | `OrderConfirmed` |

No order is ever a candidate for both. The user-facing `POST /api/orders/{id}/cancel` and admin
`POST /api/admin/orders/{id}/status` are unrelated (ownership/ADMIN-gated) and are NOT reused for
this recovery.

---

## Notes
- `userId` is extracted from the authentication token, not from the request body.
- An order may only be cancelled when its status is `PENDING` or `CONFIRMED`.
- Internal stack traces must not appear in error responses.
- The `/api/internal/orders/**` tree is gateway-excluded and authenticated by `client_credentials`
  Bearer (system-to-system), distinct from the user/admin Bearer auth of all `/api/orders/**` and
  `/api/admin/orders/**` endpoints.
