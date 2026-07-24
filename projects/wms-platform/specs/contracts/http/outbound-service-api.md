# HTTP Contract — outbound-service API

All `outbound-service` REST endpoints. This contract is authoritative —
implementation must match it. Changes here precede code changes (per
`CLAUDE.md` Contract Rule).

Base path: `/api/v1/outbound`
Service: `outbound-service`
Base URL (via gateway): `https://{gateway}/api/v1/outbound`

The webhook endpoint (`POST /webhooks/erp/order`) is **NOT** part of this
contract. It is documented in
[`specs/contracts/webhooks/erp-order-webhook.md`](../webhooks/erp-order-webhook.md)
and lives outside the `/api/v1/` namespace because it has different auth
(HMAC) and rate-limit tiers.

---

## Global Conventions

### Headers

Every request:

| Header | Required | Notes |
|---|---|---|
| `Authorization` | yes | `Bearer <oauth2-access-token>` issued by IAM (OIDC, ADR-001). RS256 JWT validated against IAM JWKS by both gateway and downstream service; `tenant_id=wms` enforced. See [`specs/integration/iam-integration.md`](../../integration/iam-integration.md). |
| `X-Request-Id` | yes | Generated/echoed by gateway. Surfaced in logs + traces |
| `X-Actor-Id` | yes | User id from JWT claim, set by gateway |
| `Idempotency-Key` | yes for POST / PATCH / DELETE | UUID. TTL 24h. Scope `(Idempotency-Key, method, path)` |
| `Content-Type` | yes on body | `application/json` |
| `Accept` | no | Defaults to `application/json` |

Responses:

| Header | Notes |
|---|---|
| `X-Request-Id` | Echoed |
| `ETag` | Single-resource GET responses |

### Authorization

| Role | Permits |
|---|---|
| `OUTBOUND_READ` | All GET endpoints |
| `OUTBOUND_WRITE` | Manual order creation, picking/packing confirmations, shipping confirmation |
| `OUTBOUND_ADMIN` | Order cancellation (post-pick), force-saga operations |

Enforced at the application layer, not in controllers. Roles are propagated
through the command record and checked in the use-case service.

#### Tenant scoping (TASK-MONO-304 / ADR-MONO-022 § D9)

Beyond the role gate, every order read and mutation is **tenant-scoped** from
the SIGNED `tenant_id` claim of the access token (never a client header):

- **Native wms callers** (`tenant_id = wms`), platform callers (`tenant_id = *`),
  and internal flows (no security context) are **unrestricted** — they see and
  may act on every outbound order. Behaviour is unchanged.
- **Customer-tenant callers** — e.g. an ecommerce operator admitted to wms via
  the `entitled_domains` dual-accept (ADR-MONO-019 § D5), whose `tenant_id` is
  their own tenant (`ecommerce`, …) — are **restricted** to their own tenant's
  `FULFILLMENT_ECOMMERCE` orders:
  - `GET /orders` is forced to `tenantId = <caller tenant>` **and**
    `source = FULFILLMENT_ECOMMERCE` (any client-supplied `source` is overridden).
  - Any single-order read or mutation (`GET /orders/{id}`, `…/saga`,
    `…/picking-requests`, cancel, pick-confirm, packing create/seal/confirm,
    shipping confirm) on an order whose `tenant_id` does not match the
    caller — including B2B / `null`-tenant orders — returns **403
    `TENANT_SCOPE_DENIED`**.

The isolation key is `orders.tenant_id`, populated only for
`FULFILLMENT_ECOMMERCE` orders (ADR-MONO-022 facet d) and `null` otherwise.

### Error Envelope

Per `platform/error-handling.md`. Every error response:

```json
{
  "error": {
    "code": "ORDER_NOT_FOUND",
    "message": "Outbound order uuid does not exist",
    "timestamp": "2026-04-29T10:00:00.000Z",
    "details": { "orderId": "uuid" },
    "traceId": "abc123",
    "requestId": "req-xyz"
  }
}
```

Domain error → HTTP status mapping:

| Code | HTTP | Notes |
|---|---|---|
| `ORDER_NOT_FOUND` | 404 | |
| `PICKING_REQUEST_NOT_FOUND` | 404 | |
| `PACKING_UNIT_NOT_FOUND` | 404 | |
| `SHIPMENT_NOT_FOUND` | 404 | |
| `ORDER_ALREADY_SHIPPED` | 422 | Cancellation or mutation attempted on `SHIPPED` order |
| `PICKING_QUANTITY_EXCEEDED` | 422 | Pick quantity per line exceeds `order_line.qty_ordered` |
| `RESERVATION_NOT_FOUND` | 422 | Picking source location has no reservation for the SKU/Lot (echoed from inventory) |
| `PICKING_INCOMPLETE` | 422 | Packing attempted before all lines are pick-confirmed |
| `PACKING_INCOMPLETE` | 422 | Shipping attempted before packing is complete (sum of `PackingUnitLine.qty` < `order_line.qty_ordered`) |
| `PARTNER_INVALID_TYPE` | 422 | Customer partner is not `ACTIVE` or `partner_type ∉ {CUSTOMER, BOTH}` |
| `SKU_INACTIVE` | 422 | SKU snapshot is `INACTIVE` in MasterReadModel |
| `LOT_REQUIRED` | 422 | LOT-tracked SKU order line missing `lot_id` (or `lot_no` at pick confirmation) |
| `WAREHOUSE_MISMATCH` | 422 | Order lines belong to different warehouses (v1: single-warehouse only) |
| `STATE_TRANSITION_INVALID` | 422 | Requested state transition not allowed from current order or saga state |
| `CONFLICT` | 409 | Optimistic lock version mismatch |
| `DUPLICATE_REQUEST` | 409 | Same `Idempotency-Key`, different body hash |
| `VALIDATION_ERROR` | 400 | Bad input (type, format, required field) |
| `FORBIDDEN` | 403 | Caller lacks required role (e.g., `OUTBOUND_ADMIN` required for post-pick cancel) |
| `TENANT_SCOPE_DENIED` | 403 | Tenant-scoped caller accessed an order outside its tenant scope (see § Authorization → Tenant scoping) |
| `INTERNAL_ERROR` | 500 | Unexpected server-side error |

### Pagination

All list endpoints support:

| Param | Type | Default | Max | Notes |
|---|---|---|---|---|
| `page` | int | 0 | | 0-indexed |
| `size` | int | 20 | 100 | |
| `sort` | string | `updatedAt,desc` | | `field,{asc\|desc}` |

Response envelope:

```json
{
  "content": [ /* items */ ],
  "page": { "number": 0, "size": 20, "totalElements": 42, "totalPages": 3 },
  "sort": "updatedAt,desc"
}
```

### Idempotency Semantics

- `Idempotency-Key` absent on a mutating endpoint → 400 `VALIDATION_ERROR`
- Same key, same method+path, **same body hash** → cached response replayed
- Same key, same method+path, **different body** → 409 `DUPLICATE_REQUEST`
- TTL: 24 hours
- Full strategy: [`specs/services/outbound-service/idempotency.md`](../../services/outbound-service/idempotency.md)

### Optimistic Locking

Mutation endpoints rely on version-checked UPDATE internally. On conflict:
→ 409 `CONFLICT`. The caller should fetch fresh state (`GET`) and retry. The
`version` field is included in mutation request bodies where the caller is
expected to assert "I've seen this state" (cancel, confirm shipping).

---

## 1. Order Lifecycle

### 1.1 POST `/api/v1/outbound/orders` — Create Order (manual entry)

Auth: `OUTBOUND_WRITE`.
Requires `Idempotency-Key`.

Equivalent of the ERP webhook for operator-driven entry. The created order
has `source = MANUAL`. Also atomically creates `OutboundSaga` (state =
`REQUESTED`) and writes the first outbox row (`outbound.picking.requested`)
in the same `@Transactional` boundary.

Request:

```json
{
  "orderNo": "ORD-20260429-9001",
  "customerPartnerId": "uuid",
  "warehouseId": "uuid",
  "requiredShipDate": "2026-05-02",
  "notes": "긴급 출고 — 고객 요청",
  "lines": [
    {
      "lineNo": 1,
      "skuId": "uuid",
      "lotId": "uuid-or-null",
      "qtyOrdered": 50
    }
  ]
}
```

Validation:

- `orderNo`: required, 1..40 chars. Pattern `ORD-\d{8}-\d+` recommended but
  not enforced. Globally unique.
- `customerPartnerId`: required UUID; must resolve to `ACTIVE` Partner with
  `partner_type ∈ {CUSTOMER, BOTH}` in `MasterReadModel`. Else `422 PARTNER_INVALID_TYPE`.
- `warehouseId`: required UUID; must resolve to `ACTIVE` Warehouse.
- `requiredShipDate`: optional `YYYY-MM-DD`; if present, ≥ today.
- `notes`: optional, ≤ 1000 chars.
- `lines`: required, ≥ 1 element.
- `lines[].lineNo`: required, ≥ 1, unique within request.
- `lines[].skuId`: required UUID; must resolve to `ACTIVE` SKU in `MasterReadModel`.
- `lines[].lotId`: optional UUID. Required iff SKU is LOT-tracked AND lot is
  explicitly specified. Null = any available lot (operator/FEFO selects at pick
  time). Non-LOT-tracked SKU with non-null `lotId` → `VALIDATION_ERROR`.
- `lines[].qtyOrdered`: required, > 0, ≤ 1,000,000.
- All lines must belong to the same `warehouseId`. Else `WAREHOUSE_MISMATCH`.

Response `201`:

```json
{
  "orderId": "uuid",
  "orderNo": "ORD-20260429-9001",
  "source": "MANUAL",
  "customerPartnerId": "uuid",
  "warehouseId": "uuid",
  "requiredShipDate": "2026-05-02",
  "notes": "긴급 출고 — 고객 요청",
  "status": "PICKING",
  "lines": [
    {
      "orderLineId": "uuid",
      "lineNo": 1,
      "skuId": "uuid",
      "lotId": null,
      "qtyOrdered": 50
    }
  ],
  "sagaId": "uuid",
  "sagaState": "REQUESTED",
  "version": 0,
  "createdAt": "2026-04-29T10:00:00Z",
  "createdBy": "user-uuid",
  "updatedAt": "2026-04-29T10:00:00Z",
  "updatedBy": "user-uuid"
}
```

Note: `status = PICKING` because `startPicking()` is called in the same TX
as order creation (the picking saga starts immediately).

Side-effects:
- Outbox: `outbound.order.received` (see `specs/contracts/events/outbound-events.md` §1)
- Outbox: `outbound.picking.requested` (§3) — triggers saga step 1

Errors: `VALIDATION_ERROR` (400), `PARTNER_INVALID_TYPE` (422),
`WAREHOUSE_MISMATCH` (422), `SKU_INACTIVE` (422), `LOT_REQUIRED` (422),
`DUPLICATE_REQUEST` (409).

### 1.2 GET `/api/v1/outbound/orders/{id}` — Get Order by id

Auth: `OUTBOUND_READ`.
Response `200`: same shape as create response, with current `status` and `sagaState`.
Errors: `ORDER_NOT_FOUND` (404).
Headers: `ETag: "v{version}"`.

### 1.3 GET `/api/v1/outbound/orders` — List Orders

Auth: `OUTBOUND_READ`.

Query parameters:

| Param | Type | Notes |
|---|---|---|
| `status` | string | One of `PICKING \| PICKED \| PACKING \| PACKED \| SHIPPED \| CANCELLED \| BACKORDERED` |
| `warehouseId` | UUID | |
| `customerPartnerId` | UUID | |
| `source` | string | `MANUAL \| WEBHOOK_ERP` |
| `orderNo` | string | Exact match |
| `requiredShipAfter` | ISO date | |
| `requiredShipBefore` | ISO date | |
| `createdAfter` | ISO-8601 | |
| `createdBefore` | ISO-8601 | |
| pagination | | Standard |

Response `200`: paginated list of order summaries (no `lines` array — fetch
detail via §1.2):

```json
{
  "content": [
    {
      "orderId": "uuid",
      "orderNo": "ORD-20260429-9001",
      "source": "MANUAL",
      "customerPartnerId": "uuid",
      "warehouseId": "uuid",
      "status": "PICKING",
      "sagaState": "REQUESTED",
      "lineCount": 1,
      "totalQtyOrdered": 50,
      "requiredShipDate": "2026-05-02",
      "createdAt": "2026-04-29T10:00:00Z",
      "updatedAt": "2026-04-29T10:00:00Z"
    }
  ],
  "page": { "number": 0, "size": 20, "totalElements": 1, "totalPages": 1 },
  "sort": "updatedAt,desc"
}
```

### 1.4 POST `/api/v1/outbound/orders/{id}:cancel` — Cancel Order

Auth: `OUTBOUND_WRITE` for `PICKING` orders (pre-pick). `OUTBOUND_ADMIN`
required for `PICKED`, `PACKING`, and `PACKED` orders (post-pick).

Requires `Idempotency-Key`.

Allowed only when `Order.status ∈ {PICKING, PICKED, PACKING, PACKED}`. Status
`SHIPPED` → 422 `ORDER_ALREADY_SHIPPED`. Already `CANCELLED` is a no-op if
the `Idempotency-Key` matches; otherwise `STATE_TRANSITION_INVALID`.

For any order that has an active `OutboundSaga` (non-terminal state), the
cancellation also emits `outbound.picking.cancelled` (if saga is in
`RESERVED`, `PICKING_CONFIRMED`, or `PACKING_CONFIRMED` state) to trigger
inventory reservation release.

Request:

```json
{
  "reason": "고객 주문 취소 요청",
  "version": 1
}
```

Validation:

- `reason`: required, 3..500 chars.
- `version`: required, optimistic lock check.

Response `200`:

```json
{
  "orderId": "uuid",
  "orderNo": "ORD-20260429-9001",
  "status": "CANCELLED",
  "previousStatus": "PICKING",
  "cancelledReason": "고객 주문 취소 요청",
  "cancelledAt": "2026-04-29T11:30:00Z",
  "cancelledBy": "user-uuid",
  "sagaState": "CANCELLATION_REQUESTED",
  "version": 2
}
```

`sagaState` reflects the saga state at the time of response. It will
eventually transition to `CANCELLED` once `inventory.released` is consumed.
For pre-`RESERVED` cancellations (saga still `REQUESTED`), `sagaState` may
transition to `CANCELLED` more quickly.

Side-effects:
- `outbound.order.cancelled` outbox event (§2)
- `outbound.picking.cancelled` outbox event (§4) — if saga has an active reservation

Errors: `ORDER_NOT_FOUND` (404), `ORDER_ALREADY_SHIPPED` (422),
`STATE_TRANSITION_INVALID` (422), `FORBIDDEN` (403 — post-pick cancel
without `OUTBOUND_ADMIN`), `CONFLICT` (409), `DUPLICATE_REQUEST` (409).

---

## 2. Picking

### 2.1 POST `/api/v1/outbound/orders/{id}/picking-requests` — Create Picking Request

Auth: `OUTBOUND_WRITE`.
Requires `Idempotency-Key`.

In the normal flow this is called implicitly by `POST /orders` (saga starts
automatically). This endpoint exists for **re-entry / manual saga recovery**
where an operator needs to explicitly trigger the picking step. Allowed only
when `Order.status == PICKING` AND no `PickingRequest` yet exists for the
order.

If a `PickingRequest` already exists for this order, returns `409 CONFLICT`
(the saga already started this step). If the saga has advanced past
`REQUESTED`, returns `422 STATE_TRANSITION_INVALID`.

Request:

```json
{
  "lines": [
    {
      "orderLineId": "uuid",
      "locationId": "uuid",
      "qtyToPick": 50
    }
  ]
}
```

Validation:

- `lines`: required, must match all `OrderLine` ids for this order.
- `lines[].orderLineId`: required UUID; must belong to this order.
- `lines[].locationId`: required UUID; must resolve to `ACTIVE` Location in the
  same `warehouseId` as the order.
- `lines[].qtyToPick`: required, > 0, ≤ `order_line.qty_ordered`. Else
  `PICKING_QUANTITY_EXCEEDED`.

Response `201`:

```json
{
  "pickingRequestId": "uuid",
  "orderId": "uuid",
  "sagaId": "uuid",
  "warehouseId": "uuid",
  "status": "SUBMITTED",
  "lines": [
    {
      "pickingRequestLineId": "uuid",
      "orderLineId": "uuid",
      "skuId": "uuid",
      "lotId": "uuid-or-null",
      "locationId": "uuid",
      "qtyToPick": 50
    }
  ],
  "version": 0,
  "createdAt": "2026-04-29T10:00:00Z",
  "createdBy": "user-uuid"
}
```

Side-effect: outbox `outbound.picking.requested` (§3) — triggers
`inventory-service` to reserve stock.

Errors: `ORDER_NOT_FOUND` (404), `STATE_TRANSITION_INVALID` (422),
`PICKING_QUANTITY_EXCEEDED` (422), `CONFLICT` (409), `DUPLICATE_REQUEST` (409),
`VALIDATION_ERROR` (400).

### 2.2 GET `/api/v1/outbound/picking-requests/{id}` — Get Picking Request

Auth: `OUTBOUND_READ`.
Response `200`: same shape as §2.1 create response with current `status`.
Errors: `PICKING_REQUEST_NOT_FOUND` (404).
Headers: `ETag: "v{version}"`.

### 2.3 POST `/api/v1/outbound/picking-requests/{id}/confirmations` — Confirm Picks

Auth: `OUTBOUND_WRITE`.
Requires `Idempotency-Key`.

Operator confirms physical pick execution. Allowed only when the saga is in
state `RESERVED` (inventory has confirmed the reservation). In v1 all lines
are confirmed in one call (per-line confirmation is v2).

For LOT-tracked SKUs, `lotId` on each confirmation line is required. The
confirmed `lotId` may differ from the `PickingRequestLine.lotId` if the
operator substituted (allowed and logged).

Request:

```json
{
  "notes": "정상 피킹 완료",
  "lines": [
    {
      "orderLineId": "uuid",
      "skuId": "uuid",
      "lotId": "uuid-or-null",
      "actualLocationId": "uuid",
      "qtyConfirmed": 50
    }
  ]
}
```

Validation:

- `notes`: optional, ≤ 500 chars.
- `lines`: required; must include all `orderLineId`s for this order. Length
  must equal the order's `OrderLine` count.
- `lines[].orderLineId`: required UUID; must belong to the parent order.
- `lines[].skuId`: required UUID; must match the `OrderLine.sku_id`.
- `lines[].lotId`: required for LOT-tracked SKUs (`LOT_REQUIRED`); null for
  non-LOT-tracked SKUs.
- `lines[].actualLocationId`: required UUID; must resolve to `ACTIVE` Location
  in the same warehouse.
- `lines[].qtyConfirmed`: required, > 0, must equal `order_line.qty_ordered`
  (v1 no short-pick).

Response `201`:

```json
{
  "pickingConfirmationId": "uuid",
  "pickingRequestId": "uuid",
  "orderId": "uuid",
  "confirmedBy": "user-uuid",
  "confirmedAt": "2026-04-29T12:00:00Z",
  "notes": "정상 피킹 완료",
  "lines": [
    {
      "pickingConfirmationLineId": "uuid",
      "orderLineId": "uuid",
      "skuId": "uuid",
      "lotId": "uuid-or-null",
      "actualLocationId": "uuid",
      "qtyConfirmed": 50
    }
  ],
  "orderStatus": "PICKED",
  "sagaState": "PICKING_CONFIRMED"
}
```

Side-effect: outbox `outbound.picking.completed` (§5)

Errors: `PICKING_REQUEST_NOT_FOUND` (404), `STATE_TRANSITION_INVALID` (422),
`LOT_REQUIRED` (422), `VALIDATION_ERROR` (400), `CONFLICT` (409),
`DUPLICATE_REQUEST` (409).

### 2.4 GET `/api/v1/outbound/orders/{id}/picking-requests` — List Picking Requests for Order

Auth: `OUTBOUND_READ`.

Returns every `PickingRequest` belonging to the order (v1: at most one — the
saga creates a single picking request per order; returned as a list for
forward-compatibility with v2 wave / partial picking).

This read closes the **picking-request discovery gap**: a consumer holding only
the `orderId` (e.g. an operations console driving the pick → pack → ship
lifecycle) can locate the picking request and its planned lines — including the
planned `locationId` and `qtyToPick` each confirmation line requires (§2.3) —
without already knowing the `pickingRequestId`. Without it, §2.3 confirmation is
only reachable by a caller that captured the `pickingRequestId` out-of-band at
saga-creation time.

Response `200` (not paginated — bounded by the order; each element matches the
§2.1 / §2.2 picking-request shape):

```json
{
  "content": [
    {
      "pickingRequestId": "uuid",
      "orderId": "uuid",
      "sagaId": "uuid",
      "warehouseId": "uuid",
      "status": "SUBMITTED",
      "lines": [
        {
          "pickingRequestLineId": "uuid",
          "orderLineId": "uuid",
          "skuId": "uuid",
          "lotId": "uuid-or-null",
          "locationId": "uuid",
          "qtyToPick": 50
        }
      ],
      "version": 0,
      "createdAt": "2026-04-29T10:00:00Z",
      "updatedAt": "2026-04-29T10:00:00Z"
    }
  ]
}
```

The `content` array is **empty** (`200` with `{ "content": [] }`, **not** `404`)
when the order exists but the saga has not yet created its picking request (e.g.
the order is still `PICKING` / saga `REQUESTED` and the picking-request row has
not been written). A `404 ORDER_NOT_FOUND` is returned only when the order `id`
itself does not exist.

Errors: `ORDER_NOT_FOUND` (404).

---

## 3. Packing

### 3.1 POST `/api/v1/outbound/orders/{id}/packing-units` — Create Packing Unit

Auth: `OUTBOUND_WRITE`.
Requires `Idempotency-Key`.

Creates a new `PackingUnit` (box/pallet/envelope) for the order. Allowed only
when `Order.status ∈ {PICKED, PACKING}`. The first call to this endpoint
transitions the order to `PACKING` if it was in `PICKED`. Multiple packing
units per order are permitted.

Request:

```json
{
  "cartonNo": "BOX-001",
  "packingType": "BOX",
  "weightGrams": 2500,
  "lengthMm": 400,
  "widthMm": 300,
  "heightMm": 200,
  "notes": null,
  "lines": [
    {
      "orderLineId": "uuid",
      "skuId": "uuid",
      "lotId": "uuid-or-null",
      "qty": 50
    }
  ]
}
```

Validation:

- `cartonNo`: required, 1..40 chars, unique within the order.
- `packingType`: required; one of `BOX`, `PALLET`, `ENVELOPE`.
- `weightGrams`: optional, ≥ 0.
- `lengthMm`, `widthMm`, `heightMm`: optional, ≥ 0. If any dimension provided,
  all three should be provided (advisory warning in v1).
- `notes`: optional, ≤ 500 chars.
- `lines`: required, ≥ 1 element.
- `lines[].orderLineId`: required UUID; must belong to this order.
- `lines[].skuId`: required UUID; must match the `OrderLine.sku_id`.
- `lines[].lotId`: optional; for LOT-tracked SKUs must be provided.
- `lines[].qty`: required, > 0.

Note: Sum of `lines[].qty` across all PackingUnits for a given `orderLineId`
need not equal `qty_ordered` when the unit is created — that constraint is
only enforced at shipping time (`PACKING_INCOMPLETE`).

Response `201`:

```json
{
  "packingUnitId": "uuid",
  "orderId": "uuid",
  "cartonNo": "BOX-001",
  "packingType": "BOX",
  "weightGrams": 2500,
  "lengthMm": 400,
  "widthMm": 300,
  "heightMm": 200,
  "notes": null,
  "status": "OPEN",
  "lines": [
    {
      "packingUnitLineId": "uuid",
      "orderLineId": "uuid",
      "skuId": "uuid",
      "lotId": null,
      "qty": 50
    }
  ],
  "orderStatus": "PACKING",
  "version": 0,
  "createdAt": "2026-04-29T13:00:00Z",
  "createdBy": "user-uuid"
}
```

Errors: `ORDER_NOT_FOUND` (404), `STATE_TRANSITION_INVALID` (422),
`VALIDATION_ERROR` (400), `DUPLICATE_REQUEST` (409).

### 3.2 PATCH `/api/v1/outbound/packing-units/{id}` — Seal Packing Unit

Auth: `OUTBOUND_WRITE`.
Requires `Idempotency-Key`.

The **sole operation** is **Seal**: transition the unit from `OPEN` to
`SEALED`. Once `SEALED` the unit is immutable — a further PATCH returns
`422 STATE_TRANSITION_INVALID`.

Packing units are fully populated at create time (§3.1 `lines[]`); there is
**no** incremental "add lines" operation. An earlier version of this contract
advertised a `seal` flag plus an `addLines[]` array, but add-lines was never
implemented. To stop a client written against that old shape from silently
sealing a unit it meant to keep open (the unbound fields were dropped and the
seal happened unconditionally — TASK-BE-550), the request body is now strict:

- `seal: false` → `400 VALIDATION_ERROR` — this endpoint always seals; "don't
  seal" cannot be requested.
- a non-empty `addLines` → `400 VALIDATION_ERROR` — add-lines is not supported.
- a legacy `seal: true` is tolerated (equivalent to omitting it).

Request:

```json
{
  "version": 0
}
```

Validation:

- `version`: required, optimistic lock (`@Min(0)`).
- `seal`: optional; if present it MUST be `true` — `seal: false` → 400.
- `addLines`: MUST be absent or empty — a non-empty array → 400.

Response `200`:

```json
{
  "packingUnitId": "uuid",
  "orderId": "uuid",
  "cartonNo": "BOX-001",
  "packingType": "BOX",
  "status": "SEALED",
  "lines": [
    {
      "packingUnitLineId": "uuid",
      "orderLineId": "uuid",
      "skuId": "uuid",
      "lotId": null,
      "qty": 60
    }
  ],
  "version": 1,
  "updatedAt": "2026-04-29T13:15:00Z",
  "updatedBy": "user-uuid"
}
```

Side-effect (when this seal makes all packing units for the order `SEALED`
AND sum of PackingUnitLines equals all order line quantities): outbox
`outbound.packing.completed` (§6); order transitions to `PACKED`.

Errors: `PACKING_UNIT_NOT_FOUND` (404), `STATE_TRANSITION_INVALID` (422),
`VALIDATION_ERROR` (400), `CONFLICT` (409), `DUPLICATE_REQUEST` (409).

---

## 4. Shipping

### 4.1 POST `/api/v1/outbound/orders/{id}/shipments` — Confirm Shipping

Auth: `OUTBOUND_WRITE`.
Requires `Idempotency-Key`.

Finalises the shipment. Allowed only when `Order.status == PACKED` (all
packing units sealed, all quantities packed). This call atomically:
1. Creates the `Shipment` record.
2. Calls `Order.confirmShipping()` → status transitions to `SHIPPED`.
3. Writes outbox `outbound.shipping.confirmed` event (saga step 4).

Carrier dispatch is driven downstream by the scm `logistics-service` off the
`outbound.shipping.confirmed` event (ADR-MONO-053 §D8); outbound-service performs
no TMS notification.

Request:

```json
{
  "carrierCode": "CJ-LOGISTICS",
  "version": 3
}
```

Validation:

- `carrierCode`: optional, ≤ 40 chars. Captured at ship-confirm time.
- `version`: required, optimistic lock check on the Order.

Response `201`:

```json
{
  "shipmentId": "uuid",
  "shipmentNo": "SHP-20260429-0001",
  "orderId": "uuid",
  "orderNo": "ORD-20260429-9001",
  "carrierCode": "CJ-LOGISTICS",
  "trackingNo": null,
  "shippedAt": "2026-04-29T15:00:00Z",
  "orderStatus": "SHIPPED",
  "sagaState": "SHIPPED",
  "version": 0,
  "createdAt": "2026-04-29T15:00:00Z",
  "createdBy": "user-uuid"
}
```

`trackingNo` is null at creation; if the downstream logistics-service dispatch
records a tracking number it may be reflected on a later `GET /shipments/{id}`.

Side-effect: outbox `outbound.shipping.confirmed` (§7) — cross-service
contract with `inventory-service` (and the scm `logistics-service` dispatch).

Errors: `ORDER_NOT_FOUND` (404), `STATE_TRANSITION_INVALID` (422),
`PACKING_INCOMPLETE` (422), `CONFLICT` (409), `DUPLICATE_REQUEST` (409),
`VALIDATION_ERROR` (400).

### 4.2 GET `/api/v1/outbound/shipments/{id}` — Get Shipment

Auth: `OUTBOUND_READ`.
Response `200`: same shape as §4.1 create response.
Errors: `SHIPMENT_NOT_FOUND` (404).
Headers: `ETag: "v{version}"`.

> **Carrier-dispatch recovery relocated (ADR-MONO-053 §D8, TASK-BE-560).** The
> former `POST /shipments/{id}:retry-tms-notify` endpoint was removed with the
> outbound TMS side-channel. The operator recovery action now drives the scm
> logistics-service dispatch `:retry` (PC-FE-258).

---

## 5. Saga

### 5.1 GET `/api/v1/outbound/orders/{id}/saga` — Get Saga State

Auth: `OUTBOUND_READ`.

Returns the `OutboundSaga` record for the order. Useful for ops visibility
into saga lifecycle and failure investigation.

Response `200`:

```json
{
  "sagaId": "uuid",
  "orderId": "uuid",
  "state": "PICKING_CONFIRMED",
  "failureReason": null,
  "startedAt": "2026-04-29T10:00:00Z",
  "lastTransitionAt": "2026-04-29T12:00:00Z",
  "version": 3
}
```

`state` values: `REQUESTED`, `RESERVE_FAILED`, `RESERVED`, `PICKING_CONFIRMED`,
`PACKING_CONFIRMED`, `CANCELLATION_REQUESTED`, `CANCELLED`, `SHIPPED`,
`COMPLETED`, `STUCK_RECOVERY_FAILED`.

`failureReason`: populated for `RESERVE_FAILED` (insufficient-stock details
from inventory) and `STUCK_RECOVERY_FAILED` (sweeper-exhaustion details).

Errors: `ORDER_NOT_FOUND` (404).

---

## Operational Endpoints

### GET `/actuator/health` — Liveness / readiness

Standard Spring Boot Actuator. No auth (internal cluster traffic only).

### GET `/actuator/info` — Build info

No auth.

---

## Event Side-Effects Summary

Every successful mutation publishes at most one event via the transactional
outbox. The outbox row is written in the same `@Transactional` boundary as
the state change. Kafka publish is asynchronous (outbox publisher SLA). See
[`specs/contracts/events/outbound-events.md`](../events/outbound-events.md).

| Endpoint | Event Published |
|---|---|
| `POST /orders` | `outbound.order.received` + `outbound.picking.requested` |
| `POST /orders/{id}:cancel` | `outbound.order.cancelled` (+ `outbound.picking.cancelled` if saga has active reservation) |
| `POST /orders/{id}/picking-requests` | `outbound.picking.requested` |
| `POST /picking-requests/{id}/confirmations` | `outbound.picking.completed` |
| `POST /orders/{id}/packing-units` | none (unit creation is internal lifecycle) |
| `PATCH /packing-units/{id}` | `outbound.packing.completed` (only when all units sealed and all quantities packed) |
| `POST /orders/{id}/shipments` | `outbound.shipping.confirmed` |

The ERP webhook ingest path's `outbound.order.received` + `outbound.picking.requested`
events are fired by the **background processor**, not by the webhook controller.

---

## Not In v1

- `PATCH /orders/{id}` — modify order lines after creation (order lines are
  immutable once picking starts)
- Multi-warehouse split orders
- Partial packing confirmation (`qty < qty_ordered` per line)
- Per-line picking confirmation (all lines confirmed in one call)
- Wave/batch picking endpoints (Wave aggregate is v2)
- Returns / RMA outbound (distinct lifecycle, v2)
- Webhook inbox retry endpoint for ERP order events (admin v2)
- Hard delete of any row
- Carrier assignment endpoint separate from shipping confirmation
- FEFO auto-allocation endpoint (v2 — calls inventory allocation API)
- Bulk order creation (CSV upload, batch API)

---

## References

- `specs/services/outbound-service/architecture.md`
- `specs/services/outbound-service/domain-model.md`
- `specs/services/outbound-service/state-machines/order-status.md`
- `specs/services/outbound-service/state-machines/saga-status.md`
- `specs/services/outbound-service/sagas/outbound-saga.md`
- `specs/services/outbound-service/idempotency.md`
- `specs/contracts/events/outbound-events.md`
- `specs/contracts/webhooks/erp-order-webhook.md`
- `platform/error-handling.md`
- `platform/api-gateway-policy.md`
- `platform/security-rules.md`
- `rules/traits/transactional.md` — T1 (idempotency), T3 (outbox), T4 (state machine), T5 (optimistic lock)
- `rules/traits/integration-heavy.md` — I6 (ERP webhook reception)
- `rules/domains/wms.md` — Outbound bounded context, W1, W4, W5, W6
