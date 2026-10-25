# HTTP Contract: order-service

> 🔴 **업무 인가 — TASK-MONO-795 (ADR-MONO-083 P8 · 2026-10-16 UTC)**: 이 계약의 **운영자** 엔드포인트는 서비스가 영역 업무 역할로 다시 판정한다 — `ECOMMERCE_ORDER_READ` / `ECOMMERCE_ORDER_WRITE`. 조회(`GET`) = READ ∨ WRITE · 변경 = WRITE. `ECOMMERCE_OPERATOR`(진입 역할) 단독 · `CUSTOMER` · 빈 `X-User-Role` → `403`. ~~`'*'` 와일드카드 조회(`X-Tenant-Id = '*'`)는 주문 조회 4 경로에서 **그대로** 열린다(TASK-BE-506).~~ → **TASK-BE-659 (2026-10-25 UTC, 소유자 결정): 운영자 주문 읽기 5 경로(`/summary` · `/metrics` · `/insights` · 목록 · 상세) 모두 `'*'` 에 `403 ACCESS_DENIED`** — BE-506 은 역할 게이트만 열었고 데이터 계층에는 `'*'` 분기가 없어서, `'*'` 는 «전 테넌트» 가 아니라 테넌트 `'*'`(행 0)를 읽었다(조용한 0, `performance-metrics-contract.md` § Wildcard tenant). 아래 본문의 «`X-User-Role` ∋ `ECOMMERCE_OPERATOR`» · «gateway `roles ∋ ECOMMERCE_OPERATOR` … Service applies no additional ecommerce-local RBAC» 문장은 운영자 엔드포인트에 한해 이 노트로 **대체**된다(게이트웨이 판정 자체는 그대로). 정본: [`iam-integration.md` § 업무 역할](../../integration/iam-integration.md#업무-역할-adr-mono-083-p8--task-mono-795--정본). 소비자 · 공개 · 워크로드 엔드포인트는 무변경.

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

**Request Headers**
| Header | Required | Description |
|---|---|---|
| `X-User-Id` | yes | Authenticated user id (gateway-trusted). |
| `Idempotency-Key` | no (recommended) | Client-generated key making placement idempotent (TASK-BE-430). A repeat `POST` with the **same key + same user** returns the **original** order (201, same `orderId`) **without** creating a duplicate order or re-publishing `OrderPlaced`. Scoped per `(tenant, user, key)`; max 64 chars. Absent → legacy non-idempotent placement (every call creates a new order). A concurrent second request with an in-flight key returns `409 DUPLICATE_ORDER_REQUEST` (retry resolves to the original order). The web-store generates one key per checkout (stable across payment retries, reset after a completed payment). |

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
    "recipient": "string",
    "phone": "string",
    "zipCode": "string",
    "address1": "string",
    "address2": "string | null"
  },
  "couponId": "string (optional, ≤ 36 chars — coupon to apply)"
}
```

**`couponId` — the discount is decided by the server (TASK-INT-026).** The client sends
only which coupon to use, never a discount amount. When `couponId` is present,
order-service:

1. assigns the new `orderId` and computes the line subtotal (`Σ unitPrice × quantity`);
2. calls promotion-service `POST /api/coupons/{couponId}/apply` **synchronously** with
   that `orderId` and the subtotal (see `promotion-api.md`);
3. sets `totalPrice = subtotal − discountAmount` and publishes `OrderPlaced` with it —
   so the payment PENDING amount already carries the discount;
4. if the placement transaction does **not** commit after step 2 was attempted (any
   later failure, including a rejected or timed-out apply), asks promotion-service to
   release the coupon for that `orderId` (`POST /api/internal/coupons/{couponId}/release`).
   Release only reverts a coupon used **by that `orderId`**, so it is safe after a
   rejection too.

A coupon whose discount would leave less than 1 KRW to pay is refused with
`422 COUPON_NOT_APPLICABLE` (the PG cannot charge 0). An idempotent replay (same
`Idempotency-Key`) returns the original order's `totalPrice` / `discountAmount` and does
not call promotion-service again.

> 🔴 **The field is `recipient`, not `recipientName` — and the neighbouring address API
> disagrees on purpose.** `/api/users/me/addresses` (user-api.md) uses `recipientName`;
> the checkout screen maps that to `recipient` when it builds this payload (web-store
> `use-shipping-address-state.ts`). Both names are correct in their own API, and that
> seam is where this document drifted: it said `recipientName` here until 2026-08-15
> (`TASK-BE-588`), so a request built from this very example was rejected with
> `400 VALIDATION_ERROR "recipient is required"`. Authoritative shape:
> `PlaceOrderRequest.ShippingAddressRequest`.

`items[].sellerId` (inner marketplace axis — ADR-MONO-030 Step 3 §3.2) is the
owning seller of the line, supplied by the client as a denormalized snapshot
(like `productName` / `unitPrice`; order-service does not call product-service)
and captured immutably on the line. A single order may span multiple sellers.
Absent → the default seller `default` (D8 net-zero).

**Response 201**
```json
{
  "orderId": "string (UUID)",
  "totalPrice": 25000,
  "discountAmount": 5000
}
```

`totalPrice` is **the amount to charge** — net of `discountAmount` (0 without a coupon).
The web-store requests exactly this amount from the PG; payment-service confirms against
the same value (from `OrderPlaced.totalPrice`), so any other amount is rejected with
`AMOUNT_MISMATCH`.

**Error responses**
| Status | Code | Reason |
|---|---|---|
| 400 | INVALID_ORDER_REQUEST | Missing or invalid field |
| 401 | UNAUTHORIZED | Missing or invalid access token |
| 409 | DUPLICATE_ORDER_REQUEST | Concurrent placement with an in-flight `Idempotency-Key` (retry → original order) |
| 422 | COUPON_NOT_FOUND | `couponId` does not exist in the caller's tenant |
| 422 | COUPON_ALREADY_USED | The coupon was already used by another order |
| 422 | COUPON_EXPIRED | The coupon has expired |
| 422 | COUPON_NOT_OWNED | The coupon belongs to another user |
| 422 | COUPON_NOT_APPLICABLE | The discount would leave less than 1 KRW to pay, or promotion-service refused the request for another reason |
| 503 | COUPON_SERVICE_UNAVAILABLE | promotion-service did not answer (timeout, 5xx, circuit open). No order is created; retry later |

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
| `CONFIRMED` | Order confirmed by the system (payment / stock-reservation saga) — never by an admin (TASK-BE-648) |
| `BACKORDERED` | Paid, but stock could not be reserved; held until a restock re-reserves it (system-driven `→ CONFIRMED`) or it is cancelled |
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

### GET /api/admin/orders/summary
Calendar-period-to-date order counts (KST calendar bounds), for the operator overview.

**Response 200**
```json
{
  "today": 3,
  "week": 12,
  "month": 45,
  "total": 320
}
```
`today`/`week`/`month` are cumulative counts since the KST calendar period start through now;
`total` is the tenant-scoped all-time count. All values are non-negative and satisfy
`today <= week <= month <= total` for a single tenant.

**Error responses**
| Status | Code | Reason |
|---|---|---|
| 401 | UNAUTHORIZED | Missing or invalid access token |
| 403 | ACCESS_DENIED | Requires `ECOMMERCE_ORDER_READ` / `ECOMMERCE_ORDER_WRITE` — **or the tenant is the wildcard `*`, whatever the roles** (TASK-BE-659 — before it, `*` passed the gate and read nothing: zeros / an empty page / 404) |

---

### GET /api/admin/orders/metrics
Performance metrics for the operator overview's «실적» strip (ADR-MONO-084 P2, TASK-MONO-807). The response shape, the windows (KST, same-length previous window) and the error behaviour are **[`platform/contracts/performance-metrics-contract.md`](../../../../../platform/contracts/performance-metrics-contract.md)** — this section only names the keys and what each one counts.

**Query Parameters**
- `period` (required) — `week` | `month`. Missing or unknown → `400 INVALID_ORDER_REQUEST`.

**Metrics** (in this order)

| Key | Unit | What it counts |
|---|---|---|
| `revenue` | money, `KRW` | Unrestricted view: the sum of `total_price` — what the customer paid, net of any coupon. Seller-scoped view (`X-Seller-Scope` bound): the sum of that seller's lines `unit_price × quantity`, **before** the order-level coupon (a coupon is not allocated to sellers). |
| `units` | count | The sum of `quantity` over the lines in view (all lines, or the seller's). |
| `orders` | count | Orders in view (with a seller scope: orders containing at least one of that seller's lines). |

**Which orders count — the same for every key:**
- **Included states:** `CONFIRMED`, `SHIPPED`, `DELIVERED` — paid *and* stock-reserved (`CONFIRMED` is set only by the payment / stock-reservation saga, TASK-BE-648).
- **Excluded:** `PENDING` (unpaid), `BACKORDERED` (paid, stock not held), `CANCELLED`, `STUCK_RECOVERY_FAILED`, and any order with `refunded_at` set (a refund is only ever made on a cancelled order, so this is a second guard, not a second population).
- **Attributed to:** `paid_at`, bucketed by its **KST** date. Not `created_at`: an order placed on Sunday and paid on Monday is Monday's sale.
- 🔴 **A past day can still change.** A `BACKORDERED` order that is later confirmed joins the day it was **paid** (ADR-MONO-084 OD-1). And because payment and stock confirmation arrive on separate events, a just-confirmed order whose `paid_at` has not landed yet joins its paid day when it does.
- Tenant-scoped; a bound seller scope narrows as above, inside the tenant filter.
- 🔴 **Wildcard tenant (`X-Tenant-Id = '*'`) → `403`, whatever the roles** (TASK-MONO-817 · shared contract § Wildcard tenant). Tenant `*` owns no order, so the figures would all be `"0"`. Since TASK-BE-659 the same holds for `/summary`, `/insights`, the list and the detail (see the note at the top) — none of them ever read across tenants; `*` answered nothing.

**Error responses**
| Status | Code | Reason |
|---|---|---|
| 400 | INVALID_ORDER_REQUEST | `period` missing or not `week` / `month` |
| 401 | UNAUTHORIZED | Missing or invalid access token |
| 403 | ACCESS_DENIED | Requires `ECOMMERCE_ORDER_READ` / `ECOMMERCE_ORDER_WRITE` (same as the other admin order reads) — or the tenant is the wildcard `*` |

---

### GET /api/admin/orders/insights
Four tenant-scoped top-5 rankings derived from `order_items`, for the operator overview.

**Query Parameters**
- `period` (optional, TASK-MONO-807) — `week` | `month`: only orders **paid** in that period's current window (the same window as `/metrics`). Omitted: every realised sale up to now. Unknown value → `400 INVALID_ORDER_REQUEST`.

**Which orders count:** exactly the `/metrics` rule above — `CONFIRMED` / `SHIPPED` / `DELIVERED`, not refunded, by `paid_at`, seller scope applied. 🔴 Before TASK-MONO-807 the rankings counted every status except `CANCELLED` (so unpaid `PENDING` orders), over all time, and ignored the seller scope; the numbers drop where those orders were — a basis correction, not a regression.

**Revenue here is line amounts** — `unit_price × quantity` per product / seller, before the order-level coupon (a coupon is not allocated to lines). So the sum of a ranking is not `/metrics` `revenue`, which is what customers paid.

**Response 200**
```json
{
  "topProductsByOrderCount": [
    { "id": "string", "label": "string", "value": 0 }
  ],
  "topProductsByRevenue": [
    { "id": "string", "label": "string", "value": 0 }
  ],
  "topSellersByOrderCount": [
    { "id": "string", "label": "string", "value": 0 }
  ],
  "topSellersByRevenue": [
    { "id": "string", "label": "string", "value": 0 }
  ]
}
```

**Error responses**
| Status | Code | Reason |
|---|---|---|
| 400 | INVALID_ORDER_REQUEST | `period` not `week` / `month` |
| 401 | UNAUTHORIZED | Missing or invalid access token |
| 403 | ACCESS_DENIED | Requires `ECOMMERCE_ORDER_READ` / `ECOMMERCE_ORDER_WRITE` — **or the tenant is the wildcard `*`, whatever the roles** (TASK-BE-659 — before it, `*` passed the gate and read nothing: zeros / an empty page / 404) |

---

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
| 403 | ACCESS_DENIED | Requires `ECOMMERCE_ORDER_READ` / `ECOMMERCE_ORDER_WRITE` — **or the tenant is the wildcard `*`, whatever the roles** (TASK-BE-659 — before it, `*` passed the gate and read nothing: zeros / an empty page / 404) |

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
| 403 | ACCESS_DENIED | Requires `ECOMMERCE_ORDER_READ` / `ECOMMERCE_ORDER_WRITE` — **or the tenant is the wildcard `*`, whatever the roles** (TASK-BE-659 — before it, `*` passed the gate and read nothing: zeros / an empty page / 404) |
| 404 | ORDER_NOT_FOUND | Order with given ID does not exist |

---

### POST /api/admin/orders/{orderId}/status
Change order status (admin only).

**Allowed transitions (operator-initiated):**
- `PENDING`, `CONFIRMED` or `BACKORDERED` → `CANCELLED` — **the only status an operator can set.**

**`CONFIRMED` is not operator-settable, from any current status** (TASK-BE-648). An order is
confirmed solely by the payment / stock-reservation saga (`OrderConfirmationService` — the
`StockChanged(ORDER_RESERVED)` consumer, the restock re-reservation of a `BACKORDERED` order, and
the system stale-paid forward-confirm `POST /api/internal/orders/confirm-paid-stale`), which is
the path that reserves stock and emits `OrderConfirmed` (`events/order-events.md` § OrderConfirmed).
Submitting `status: CONFIRMED` returns `400 INVALID_ORDER_REQUEST` and leaves the order unchanged
(no save, no event) — for a `PENDING`, a `BACKORDERED` (TASK-BE-645) and an already-`CONFIRMED`
order alike. A missing order is still `404`.

> **History — conflict resolved (TASK-BE-648, 2026-10-22).** This section used to allow
> `PENDING → CONFIRMED` and a `CONFIRMED → CONFIRMED` idempotent no-op (`200`), while
> `events/order-events.md:84-89` says `OrderConfirmed` is published whenever an order is
> confirmed, co-committed with the transition. The operator path did neither: it saved
> `CONFIRMED` with no stock reservation and no `OrderConfirmed`. A later payment then found the
> order already `CONFIRMED` (`Order.confirm` → `false`), so `OrderConfirmed` was **never**
> emitted — no shipment — and if payment never came the order also left the stuck-order sweep,
> which targets `PENDING` only. TASK-BE-645 first closed `BACKORDERED → CONFIRMED`; TASK-BE-648
> closes the rest. The event contract wins: every confirmation goes through the saga.

**`CANCELLED` is an operator cancel with the full compensation fan-out** (TASK-BE-646). It
runs the same application path as the internal operator cancel
(`POST /api/internal/orders/{orderId}/cancel`): in one transaction the order moves to
`CANCELLED` and `OrderCancelled` with `cancelReason = "OPERATOR"` is written to the outbox
(`events/order-events.md` § OrderCancelled). Consumers then refund a captured payment / void a
pending one (payment-service), release the stock reservation — restoring stock if it was
reserved, and detaching a `BACKORDERED` hold so a later restock does not bind to the cancelled
order (product-service) — and restore a used coupon (promotion-service). The order is looked up
in the caller's tenant (cross-tenant id → `404`). A repeated request finds the order already
`CANCELLED` and returns `422 ORDER_CANNOT_BE_CANCELLED` **without** a second `OrderCancelled`;
of two concurrent cancels (operator + customer) only one commits (optimistic lock), so the
compensation is published once.

`SHIPPED` and `DELIVERED` are **not** operator-settable on this endpoint. The Order
reaches those states solely via the shipping-driven return-leg — the
`ShippingStatusChanged` event consumed by order-service flips the Order
`CONFIRMED → SHIPPED` / `SHIPPED → DELIVERED` (ADR-MONO-022 §D7). Submitting
`status: SHIPPED` or `status: DELIVERED` here returns `400 INVALID_ORDER_REQUEST`,
preventing order/shipping status divergence.

**Request Body**
```json
{
  "status": "CANCELLED"
}
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
| 400 | INVALID_ORDER_REQUEST | Invalid status value or transition — incl. `status: CONFIRMED` from any current status (TASK-BE-648), and `SHIPPED` / `DELIVERED` / `PENDING` |
| 401 | UNAUTHORIZED | Missing or invalid access token |
| 403 | ACCESS_DENIED | Requires ADMIN role |
| 404 | ORDER_NOT_FOUND | Order with given ID does not exist |
| 422 | ORDER_CANNOT_BE_CANCELLED | `status: CANCELLED` for an order whose status does not allow cancellation (incl. one already `CANCELLED` — no second `OrderCancelled`) |

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
- An order may only be cancelled when its status is `PENDING`, `CONFIRMED` or `BACKORDERED`
  (`OrderStatus.isCancellable`).
- Internal stack traces must not appear in error responses.
- The `/api/internal/orders/**` tree is gateway-excluded and authenticated by `client_credentials`
  Bearer (system-to-system), distinct from the user/admin Bearer auth of all `/api/orders/**` and
  `/api/admin/orders/**` endpoints.
