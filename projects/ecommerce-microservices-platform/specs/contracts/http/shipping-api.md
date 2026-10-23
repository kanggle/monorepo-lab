# HTTP Contract: shipping-service

> 🔴 **업무 인가 — TASK-MONO-795 (ADR-MONO-083 P8 · 2026-10-16 UTC)**: 이 계약의 **운영자** 엔드포인트는 서비스가 영역 업무 역할로 다시 판정한다 — `ECOMMERCE_SHIPPING_READ` / `ECOMMERCE_SHIPPING_WRITE`(상태 · 추적 갱신). 조회(`GET`) = READ ∨ WRITE · 변경 = WRITE. `ECOMMERCE_OPERATOR`(진입 역할) 단독 · `CUSTOMER` · 빈 `X-User-Role` → `403`. `'*'` 와일드카드에는 열지 않는다(지금과 같음). 아래 본문의 «`X-User-Role` ∋ `ECOMMERCE_OPERATOR`» · «gateway `roles ∋ ECOMMERCE_OPERATOR` … Service applies no additional ecommerce-local RBAC» 문장은 운영자 엔드포인트에 한해 이 노트로 **대체**된다(게이트웨이 판정 자체는 그대로). 정본: [`iam-integration.md` § 업무 역할](../../integration/iam-integration.md#업무-역할-adr-mono-083-p8--task-mono-795--정본). 소비자 · 공개 · 워크로드 엔드포인트는 무변경.

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
  "wmsRouted": "boolean (order routed through wms fulfillment; gates the console WMS-deduct toggle, ADR-MONO-022 D4 v2(c))",
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
  "carrier": "string (optional, required when status is SHIPPED)",
  "deductWmsInventory": "boolean (optional, default false) — when true AND the order is wmsRouted AND status=SHIPPED, also publish ecommerce.shipping.manual-confirm-requested.v1 so wms deducts physical inventory (ADR-MONO-022 D4 v2(c)). No-op otherwise."
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
| 409 | DATA_INTEGRITY_VIOLATION | Concurrent conflict: another request transitioned this shipment to the same status at the same instant. The transition is idempotent on `(shippingId, newStatus)` via the `ShippingStatusChanged` event id (= `shipping_outbox` PK), so the race loser rolls back and the winner's transition stands. A retry observes the shipment already in the target status (TASK-BE-547). |
| 422 | INVALID_STATUS_TRANSITION | Status transition is not allowed — incl. any `status: CANCELLED` (system-only target, TASK-BE-649) and any transition out of `CANCELLED` |

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
      "wmsRouted": "boolean",
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

---

### POST /api/shippings/{shipmentId}/refresh-tracking (Admin)
Admin-triggered carrier tracking refresh (TASK-BE-293). Fetches the shipment's carrier status
via the logistics aggregator and advances the shipment status accordingly. Best-effort — a carrier
outage or unknown status leaves the shipment unchanged (returns 200 with the current status).
Default `shipping.carrier.mode=mock` is a no-op.

**Authorization**: `X-User-Role: ECOMMERCE_OPERATOR` header required (forwarded by gateway).

**Response 200**
```json
{
  "shippingId": "string (UUID)",
  "status": "string",
  "updatedAt": "string (ISO 8601)"
}
```

**Error responses**
| Status | Code | Reason |
|---|---|---|
| 400 | INVALID_SHIPPING_REQUEST | Missing or blank `X-User-Role` header |
| 403 | ACCESS_DENIED | Not an admin user |
| 404 | SHIPPING_NOT_FOUND | Shipping record does not exist |

---

### POST /api/shippings/carrier-webhook (Public — HMAC authenticated)
Inbound carrier/aggregator delivery event webhook. **Gateway public-route** (TASK-BE-359).
No bearer token is required at the gateway; the request is authenticated downstream via
HMAC-SHA256 (`X-Carrier-Signature: sha256=<lowercase-hex>` over the raw request body).

**Authentication**: `X-Carrier-Signature: sha256=<hex>` (HMAC-SHA256, shared secret configured
in `shipping.carrier.webhook.secret`). Fail-closed: blank secret → 401 for every request.

**Request Body**
```json
{
  "deliveryId": "string (idempotency key, UUID)",
  "shippingId": "string (UUID)",
  "status": "string (carrier/aggregator status code)"
}
```

**Response 200** — webhook acknowledged (outcome: ADVANCED / IGNORED / DUPLICATE)

**Error responses**
| Status | Code | Reason |
|---|---|---|
| 400 | INVALID_SHIPPING_REQUEST | Malformed body / missing required fields |
| 401 | WEBHOOK_SIGNATURE_INVALID | Missing, invalid, or misconfigured HMAC signature (`WebhookSignatureException`) |

---

## Shipping Status Values

| Status | Description |
|---|---|
| `PREPARING` | Order confirmed, preparing for shipment |
| `SHIPPED` | Package handed to carrier |
| `IN_TRANSIT` | Package in transit |
| `DELIVERED` | Package delivered to recipient |
| `CANCELLED` | Order cancelled before dispatch; terminal (ADR-MONO-022 D4 v2(d), TASK-BE-649) |

Every read that returns `status` (`GET /api/shippings/orders/{orderId}`, `GET /api/shippings`,
`statusHistory[].status`, the `PUT …/status` and `POST …/refresh-tracking` responses) may
carry `CANCELLED`. The `GET /api/shippings?status=` filter accepts it.

## Allowed Status Transitions

| From | To | Who |
|---|---|---|
| `PREPARING` | `SHIPPED` | operator (`PUT …/status`), wms ship-confirm, carrier refresh / webhook |
| `SHIPPED` | `IN_TRANSIT` | operator, carrier refresh / webhook |
| `IN_TRANSIT` | `DELIVERED` | operator, carrier refresh / webhook |
| `PREPARING` | `CANCELLED` | **system only** — shipping-service consuming `OrderCancelled` (`order-events.md`) |

Backward or skip transitions are not allowed. `CANCELLED` is terminal.

`CANCELLED` is **not** an operator target. `PUT /api/shippings/{id}/status` with
`status: CANCELLED` returns `422 INVALID_STATUS_TRANSITION` from every current status — an
operator cancels the **order**, and the shipment follows. (Cancelling only the shipment
would skip the refund and the wms cancel request.)

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
- **Carrier integration provider = a logistics aggregator** (물류 중개 플랫폼, ADR-007 D2),
  not a single-carrier-direct nor contract-partner-direct carrier API. `POST /api/shippings/{id}/refresh-tracking`
  (admin, best-effort) pulls the aggregator's unified status; `POST /api/shippings/carrier-webhook`
  (HMAC-SHA256 `X-Carrier-Signature: sha256=<hex>`, idempotent) ingests the aggregator's push.
  The `carrier` field is the aggregator-internal carrier code (the aggregator may auto-assign);
  shipment identity is keyed by `trackingNumber`/`shippingId`, not by the returned carrier code.
  The **concrete aggregator = Delivery Tracker** (`tracker.delivery`): the outbound pull is a
  GraphQL `track(carrierId, trackingNumber)` call over OAuth2 `client_credentials` (TASK-BE-364);
  the `carrier` field carries the reverse-DNS `carrierId` (e.g. `kr.cjlogistics`). This outbound
  vendor wire contract is **not** part of this published HTTP surface — it is specified in
  [`../../services/shipping-service/external-integrations.md`](../../services/shipping-service/external-integrations.md) § 1.
- **`POST /api/shippings/carrier-webhook` — gateway public-route** (TASK-BE-359 / ADR-007 D5-2):
  this endpoint is the ONLY path in `/api/shippings/**` that is exempt from JWT authentication at
  the gateway. Callers (the logistics aggregator) do NOT present a bearer token; authentication is
  performed exclusively by the downstream shipping-service HMAC verifier
  (`X-Carrier-Signature: sha256=<hex>`, `CarrierWebhookVerifier`, TASK-BE-294).
  Fail-closed: a blank `shipping.carrier.webhook.secret` causes the downstream to reject every
  webhook with 401 regardless of gateway public-route exposure (ADR-007 D4 net-zero).
  All other `/api/shippings/**` paths/methods retain their existing JWT requirement.
