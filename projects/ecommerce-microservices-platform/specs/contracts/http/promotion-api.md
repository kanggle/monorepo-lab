# HTTP Contract: promotion-service

## Overview
Published HTTP API for promotion-service.
All endpoints are accessible through gateway-service only.
Admin endpoints require an authenticated admin user (Bearer token with admin role).
User endpoints require an authenticated user (Bearer token).

---

## Base Path
`/api/promotions` (admin), `/api/coupons` (user)

---

## Endpoints

### POST /api/promotions (Admin)
Create a new promotion.

**Request Body**
```json
{
  "name": "string",
  "description": "string",
  "discountType": "FIXED | PERCENTAGE",
  "discountValue": 5000,
  "maxDiscountAmount": 10000,
  "maxIssuanceCount": 1000,
  "startDate": "string (ISO 8601)",
  "endDate": "string (ISO 8601)"
}
```

**Response 201**
```json
{
  "promotionId": "string (UUID)"
}
```

**Error responses**
| Status | Code | Reason |
|---|---|---|
| 400 | VALIDATION_ERROR | Missing or invalid field |
| 401 | UNAUTHORIZED | Missing or invalid access token |
| 403 | ACCESS_DENIED | Not an admin user |

---

### GET /api/promotions (Admin)
List all promotions.

**Query Parameters**
- `page` (default: 0) — page number
- `size` (default: 20) — page size
- `status` (optional) — filter by status (one of: `ACTIVE`, `SCHEDULED`, `ENDED`)

**Response 200**
```json
{
  "content": [
    {
      "promotionId": "string (UUID)",
      "name": "string",
      "discountType": "FIXED",
      "discountValue": 5000,
      "maxIssuanceCount": 1000,
      "issuedCount": 500,
      "startDate": "string (ISO 8601)",
      "endDate": "string (ISO 8601)",
      "status": "ACTIVE"
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
| 403 | ACCESS_DENIED | Not an admin user |

---

### GET /api/promotions/{promotionId} (Admin)
Get promotion detail.

**Response 200**
```json
{
  "promotionId": "string (UUID)",
  "name": "string",
  "description": "string",
  "discountType": "FIXED",
  "discountValue": 5000,
  "maxDiscountAmount": 10000,
  "maxIssuanceCount": 1000,
  "issuedCount": 500,
  "startDate": "string (ISO 8601)",
  "endDate": "string (ISO 8601)",
  "status": "ACTIVE",
  "createdAt": "string (ISO 8601)",
  "updatedAt": "string (ISO 8601)"
}
```

**Error responses**
| Status | Code | Reason |
|---|---|---|
| 401 | UNAUTHORIZED | Missing or invalid access token |
| 403 | ACCESS_DENIED | Not an admin user |
| 404 | PROMOTION_NOT_FOUND | Promotion with given ID does not exist |

---

### PUT /api/promotions/{promotionId} (Admin)
Update a promotion.

**Request Body**
```json
{
  "name": "string",
  "description": "string",
  "discountType": "FIXED | PERCENTAGE",
  "discountValue": 5000,
  "maxDiscountAmount": 10000,
  "maxIssuanceCount": 1000,
  "startDate": "string (ISO 8601)",
  "endDate": "string (ISO 8601)"
}
```

**Response 200**
```json
{
  "promotionId": "string (UUID)"
}
```

**Error responses**
| Status | Code | Reason |
|---|---|---|
| 400 | VALIDATION_ERROR | Missing or invalid field |
| 401 | UNAUTHORIZED | Missing or invalid access token |
| 403 | ACCESS_DENIED | Not an admin user |
| 404 | PROMOTION_NOT_FOUND | Promotion with given ID does not exist |
| 422 | PROMOTION_ALREADY_ENDED | Cannot update an ended promotion |

---

### DELETE /api/promotions/{promotionId} (Admin)
Delete a promotion.

**Response 204** No content.

**Error responses**
| Status | Code | Reason |
|---|---|---|
| 401 | UNAUTHORIZED | Missing or invalid access token |
| 403 | ACCESS_DENIED | Not an admin user |
| 404 | PROMOTION_NOT_FOUND | Promotion with given ID does not exist |
| 422 | PROMOTION_HAS_ISSUED_COUPONS | Cannot delete a promotion with issued coupons |

---

### POST /api/promotions/{promotionId}/coupons/issue (Admin)
Issue coupons for a promotion to specified users.

**Idempotency (required on this endpoint, TASK-BE-536)**

`Promotion.validateCanIssue` caps only the TOTAL issued count, not "this exact
batch was already issued" — a replay mints an entire second batch of coupons
until the cap is hit. A missing or blank `Idempotency-Key` is refused with
**400 `IDEMPOTENCY_KEY_REQUIRED`**. The key is scoped to `promotionId`.

| Replay shape | Behaviour |
|---|---|
| Same key, same `userIds` | **Replay.** Returns **201** with the ALREADY-issued count. No second batch is minted. |
| Same key, different `userIds` | **409 `IDEMPOTENCY_KEY_CONFLICT`** — the key is bound to the first request's user batch (order-sensitive). |
| Different key, same promotion | A **genuine second issuance batch** (even to the same users). Proceeds normally, composing with the issuance cap. |

Records are retained indefinitely (no TTL). Under two simultaneous duplicates the
loser of the `UNIQUE (promotion_id, idempotency_key)` insert also receives **409
`IDEMPOTENCY_KEY_CONFLICT`**; retrying that request hits the replay row.

**Request Headers**
- `Idempotency-Key: string` (**required**) — client-supplied, scoped to `promotionId`

**Request Body**
```json
{
  "userIds": ["string (UUID)"]
}
```

**Response 201**
```json
{
  "issuedCount": 3
}
```

**Error responses**
| Status | Code | Reason |
|---|---|---|
| 400 | IDEMPOTENCY_KEY_REQUIRED | `Idempotency-Key` header missing or blank |
| 401 | UNAUTHORIZED | Missing or invalid access token |
| 403 | ACCESS_DENIED | Not an admin user |
| 404 | PROMOTION_NOT_FOUND | Promotion does not exist |
| 409 | IDEMPOTENCY_KEY_CONFLICT | The same `Idempotency-Key` was replayed for this promotion with a different `userIds` batch, or lost a concurrent same-key insert race |
| 422 | PROMOTION_NOT_ACTIVE | Promotion is not currently active |
| 422 | COUPON_LIMIT_EXCEEDED | Issuance would exceed max issuance count |

---

### GET /api/coupons/me
List coupons for the authenticated user.

**Query Parameters**
- `page` (default: 0) — page number
- `size` (default: 20) — page size
- `status` (optional) — filter by coupon status (one of: `ISSUED`, `USED`, `EXPIRED`)

**Response 200**
```json
{
  "content": [
    {
      "couponId": "string (UUID)",
      "promotionId": "string (UUID)",
      "promotionName": "string",
      "discountType": "FIXED",
      "discountValue": 5000,
      "maxDiscountAmount": 10000,
      "status": "ISSUED",
      "issuedAt": "string (ISO 8601)",
      "expiresAt": "string (ISO 8601)"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 3
}
```

**Error responses**
| Status | Code | Reason |
|---|---|---|
| 401 | UNAUTHORIZED | Missing or invalid access token |

---

### POST /api/coupons/{couponId}/apply
Apply a coupon to calculate discount. Called by order-service during order placement.

**Request Body**
```json
{
  "orderId": "string (UUID)",
  "orderAmount": 30000
}
```

**Response 200**
```json
{
  "couponId": "string (UUID)",
  "discountAmount": 5000,
  "finalAmount": 25000
}
```

**Error responses**
| Status | Code | Reason |
|---|---|---|
| 401 | UNAUTHORIZED | Missing or invalid access token |
| 404 | COUPON_NOT_FOUND | Coupon does not exist |
| 422 | COUPON_ALREADY_USED | Coupon has already been used |
| 422 | COUPON_EXPIRED | Coupon has expired |
| 422 | COUPON_NOT_OWNED | Coupon does not belong to the authenticated user |

---

## Promotion Status Values

| Status | Description |
|---|---|
| `SCHEDULED` | Promotion start date is in the future |
| `ACTIVE` | Promotion is currently active |
| `ENDED` | Promotion end date has passed |

## Coupon Status Values

| Status | Description |
|---|---|
| `ISSUED` | Coupon issued, available for use |
| `USED` | Coupon has been used |
| `EXPIRED` | Coupon has expired |

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
- `userId` is extracted from the authentication token, not from the request body.
- Admin endpoints require admin role via `X-User-Role` header forwarded by gateway.
- Internal stack traces must not appear in error responses.
