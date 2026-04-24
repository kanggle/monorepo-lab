# HTTP Contract: shipping-service

## Overview
Published HTTP API for shipping-service.
All endpoints are accessible through gateway-service only.
Admin endpoints require an authenticated admin user (Bearer token with admin role).
User endpoints require an authenticated user (Bearer token).

---

## Base Path
`/api/shippings`

---

## Endpoints

### GET /api/shippings/orders/{orderId}
Get shipping status for an order. Accessible by the order owner.

**Response 200**
```json
{
  "shippingId": "string (UUID)",
  "orderId": "string (UUID)",
  "status": "PREPARING",
  "trackingNumber": "string | null",
  "carrier": "string | null",
  "statusHistory": [
    {
      "status": "PREPARING",
      "changedAt": "string (ISO 8601)"
    }
  ],
  "createdAt": "string (ISO 8601)",
  "updatedAt": "string (ISO 8601)"
}
```

**Error responses**
| Status | Code | Reason |
|---|---|---|
| 401 | UNAUTHORIZED | Missing or invalid access token |
| 403 | ACCESS_DENIED | Not the order owner |
| 404 | SHIPPING_NOT_FOUND | Shipping record for given order does not exist |

---

### PUT /api/shippings/{shippingId}/status (Admin)
Update shipping status. Only forward transitions are allowed.

**Request Body**
```json
{
  "status": "SHIPPED",
  "trackingNumber": "string (optional, required when status is SHIPPED)",
  "carrier": "string (optional, required when status is SHIPPED)"
}
```

**Response 200**
```json
{
  "shippingId": "string (UUID)",
  "status": "SHIPPED",
  "updatedAt": "string (ISO 8601)"
}
```

**Error responses**
| Status | Code | Reason |
|---|---|---|
| 400 | INVALID_SHIPPING_REQUEST | Missing or invalid field |
| 401 | UNAUTHORIZED | Missing or invalid access token |
| 403 | ACCESS_DENIED | Not an admin user |
| 404 | SHIPPING_NOT_FOUND | Shipping record does not exist |
| 422 | INVALID_STATUS_TRANSITION | Status transition is not allowed |

---

### GET /api/shippings (Admin)
List all shipping records.

**Query Parameters**
- `page` (default: 0) — page number
- `size` (default: 20) — page size
- `status` (optional) — filter by shipping status

**Response 200**
```json
{
  "content": [
    {
      "shippingId": "string (UUID)",
      "orderId": "string (UUID)",
      "status": "PREPARING",
      "trackingNumber": "string | null",
      "carrier": "string | null",
      "createdAt": "string (ISO 8601)",
      "updatedAt": "string (ISO 8601)"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 50
}
```

**Error responses**
| Status | Code | Reason |
|---|---|---|
| 401 | UNAUTHORIZED | Missing or invalid access token |
| 403 | ACCESS_DENIED | Not an admin user |

---

## Shipping Status Values

| Status | Description |
|---|---|
| `PREPARING` | Order confirmed, preparing for shipment |
| `SHIPPED` | Package handed to carrier |
| `IN_TRANSIT` | Package in transit |
| `DELIVERED` | Package delivered to recipient |

## Allowed Status Transitions

| From | To |
|---|---|
| `PREPARING` | `SHIPPED` |
| `SHIPPED` | `IN_TRANSIT` |
| `IN_TRANSIT` | `DELIVERED` |

Backward or skip transitions are not allowed.

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
- Shipping records are created automatically when `OrderConfirmed` event is consumed.
- `userId` for access control is extracted from the authentication token.
- Admin endpoints require admin role via `X-User-Role` header forwarded by gateway.
- `trackingNumber` and `carrier` are required when transitioning to `SHIPPED` status.
- Internal stack traces must not appear in error responses.
