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
Apply a coupon to calculate discount. Called by **order-service** during order placement,
synchronously, on the internal network (TASK-INT-026). The web-store does not call it — the
discount it shows before placement is a preview; the amount charged comes from this call.

**Request Headers**
| Header | Required | Description |
|---|---|---|
| `X-User-Id` | yes | The user placing the order (order-service forwards the gateway-trusted id). |
| `X-Tenant-Id` | no | Tenant of the order; absent → default tenant. |

**Request Body**
```json
{
  "orderId": "string (UUID)",
  "orderAmount": 30000
}
```

`orderAmount` is the order's line subtotal **before** any discount.

**Idempotent for the same order.** If the coupon is already `USED` by **the same `orderId`**
and the same user, the call returns `200` with the discount for `orderAmount` again and
publishes no second `CouponUsed`. A coupon used by a different order still answers
`422 COUPON_ALREADY_USED`.

**A released placement cannot be applied afterwards** (TASK-INT-027). `apply` and the internal
`release` below are not ordered with respect to each other: a caller whose `apply` times out
rolls its placement back and sends the `release` while that `apply` is still being processed
here. If this call's `(couponId, orderId)` already has a release recorded, the placement is
known dead — the coupon stays `ISSUED`, no `CouponUsed` is published, and the call answers
`422 COUPON_PLACEMENT_RELEASED`. Without this, the late `apply` would bind the coupon to an
order that was never saved, and nothing could ever free it: the cancellation restore path
needs an order to fire, so the coupon would sit `USED` until it expired.

The fence is scoped to the **pair**, not the coupon: the same coupon applied by a *different*
`orderId` is unaffected, so a user whose placement failed can simply order again.

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
| 422 | COUPON_PLACEMENT_RELEASED | This `(couponId, orderId)` was already released — the placement did not commit and its order was never saved (TASK-INT-027) |

---

### POST /api/internal/coupons/{couponId}/release
**Internal — not routed by gateway-service** (the gateway forwards only `/api/promotions/**`
and `/api/coupons/**`). Called by order-service when an order placement that attempted
`apply` does not commit, so the coupon is not left `USED` by an order that does not exist
(TASK-INT-026).

It must stay off the public route: a user who could call it would release the coupon behind
an order they were already discounted for, and use it again.

**Request Headers**
| Header | Required | Description |
|---|---|---|
| `X-Tenant-Id` | no | Tenant of the order; absent → default tenant. |

**Request Body**
```json
{
  "orderId": "string (UUID)"
}
```

**Behaviour** — reverts the coupon to `ISSUED` **only if** it is `USED` by this `orderId`.
Any other state (used by another order, expired, not found) is left untouched.

**A release with nothing to give back is still recorded** (TASK-INT-027). When the coupon is
not `USED` by this `orderId`, this call records the `(couponId, orderId)` pair as released
before returning. That record is what a later `apply` for the same pair refuses on — see
`POST /api/coupons/{couponId}/apply` above. Without it the compensation only works when the
`apply` happens to commit first: the caller sends `release` precisely because its `apply`
timed out, which is exactly when that call may still be in flight here.

Recording is **idempotent** — a retried release writes one record, not two — and the record is
scoped to the pair, so it never blocks the same coupon on a different order.

**Response 204** — always, including the cases where nothing was reverted, so a retry is
harmless.

**Error responses**
| Status | Code | Reason |
|---|---|---|
| 400 | VALIDATION_ERROR | `orderId` missing or blank |

**Second caller — batch-worker** (TASK-INT-028). batch-worker releases coupons held by orders that never
existed (see `POST /api/internal/coupons/stale-used` below). Because the coupon lookup here is
**tenant-scoped**, batch-worker sends `X-Tenant-Id` set to **the coupon's own `tenantId`** from that list;
without it a coupon in any tenant other than the default would not be found and the call would be a
silent no-op.

---

### POST /api/internal/coupons/stale-used
**Internal — not routed by gateway-service.** Called by **batch-worker** (TASK-INT-028) to find coupons
that have been `USED` for a while, so it can ask order-service whether each coupon's order exists and
release the ones whose order was never saved. **Read-only.**

**Request Body**
```json
{
  "olderThanMinutes": 60,
  "limit": 200
}
```

| Field | Type | Default | Constraint |
|---|---|---|---|
| `olderThanMinutes` | int | 60 | **≥ 30.** Only coupons used longer ago than this are returned. The floor is enforced here, not trusted to the caller: an order placement that is still in flight must never have its coupon offered for release. |
| `limit` | int | 200 | 1..500 |

**Predicate** — tenant-agnostic (a system sweep with no request tenant, like the expiry sweep):

```sql
SELECT coupon_id, order_id, tenant_id, used_at FROM coupons
WHERE status = 'USED'
  AND order_id IS NOT NULL
  AND used_at < (now() - (:olderThanMinutes * interval '1 minute'))
ORDER BY used_at ASC
LIMIT :limit
```

- `order_id IS NULL` rows are excluded: with no order id there is nothing to ask order-service about, and
  "cannot judge" must never become "release".
- Coupons past `expires_at` **are** returned. Releasing one returns it to `ISSUED`, and the expiry sweep then
  moves it to `EXPIRED` — the same end state a never-used coupon reaches.

**Response 200**
```json
{
  "coupons": [
    { "couponId": "string (UUID)", "orderId": "string (UUID)", "tenantId": "ecommerce", "usedAt": "2026-09-16T01:02:03Z" }
  ]
}
```

**Error responses**
| Status | Code | Reason |
|---|---|---|
| 400 | VALIDATION_ERROR | `olderThanMinutes` below 30, or `limit` outside 1..500 |

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
