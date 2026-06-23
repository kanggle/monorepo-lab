# Event Contract: product-service

## Overview
Domain events published by product-service.
Consumers must not depend on fields not defined in this contract.

---

## Event Envelope (common to all events)
```json
{
  "event_id": "string (UUID)",
  "event_type": "string",
  "occurred_at": "string (ISO 8601)",
  "source": "product-service",
  "tenant_id": "string",
  "payload": {}
}
```

`tenant_id` (multi-tenant async propagation — ADR-MONO-030 §2.3 M5,
[multi-tenancy-and-marketplace.md](../../features/multi-tenancy-and-marketplace.md))
identifies the owning tenant of the product the event concerns. It is derived
from the request's tenant context (gateway `X-Tenant-Id`) at publish time;
background/reconciliation-origin events (no request context) resolve to the
default tenant (`ecommerce`) for net-zero with the pre-multi-tenant baseline.
Consumers performing tenant-scoped processing must read this field; it is always
present (never blank).

---

## ProductCreated

Published when a new product is successfully registered.

**Consumers:** search-service

**Payload**
```json
{
  "productId": "string (UUID)",
  "name": "string",
  "description": "string",
  "price": 10000,
  "status": "ON_SALE",
  "thumbnailUrl": "string",
  "categoryId": "string (UUID)",
  "sellerId": "string",
  "variants": [
    {
      "variantId": "string (UUID)",
      "optionName": "string",
      "stock": 100,
      "additionalPrice": 0
    }
  ]
}
```

`sellerId` (inner marketplace axis — ADR-MONO-030 Step 3 §3.2,
[multi-tenancy-and-marketplace.md](../../features/multi-tenancy-and-marketplace.md))
is the owning seller within the tenant (ownership key `(tenant_id, seller_id)`).
It is always present (never blank); a product registered without a seller —
standalone / pre-marketplace data — resolves to the per-tenant default seller
`default` (D8 net-zero). The outer `tenant_id` is on the envelope (above).

---

## ProductUpdated

Published when product information (name, description, price, status) is updated.

**Consumers:** search-service

**Payload**
```json
{
  "productId": "string (UUID)",
  "name": "string",
  "description": "string",
  "price": 10000,
  "status": "ON_SALE",
  "thumbnailUrl": "string",
  "categoryId": "string (UUID)"
}
```

---

## ProductDeleted

Published when a product is permanently deleted.

**Consumers:** search-service

**Payload**
```json
{
  "productId": "string (UUID)"
}
```

---

## StockChanged

Published when inventory stock is adjusted for any variant.

**Consumers:** search-service, order-service

**Payload**
```json
{
  "productId": "string (UUID)",
  "variantId": "string (UUID)",
  "previousStock": 100,
  "currentStock": 150,
  "delta": 50,
  "reason": "RESTOCK",
  "orderId": null
}
```

**Topic:** `product.product.stock-changed`

**reason values:** `RESTOCK`, `ORDER_RESERVED`, `ORDER_CANCELLED`, `ADMIN_ADJUSTMENT`

**orderId:** nullable. Present when `reason` is `ORDER_RESERVED` or `ORDER_CANCELLED`. Identifies the order that triggered the stock change.

**Producers of each reason:**
- `ADMIN_ADJUSTMENT` — operator manual stock adjust (`AdjustStockService`).
- `RESTOCK` — replenishment (operator restock or WMS inventory-received reconciliation).
- `ORDER_RESERVED` — the payment-driven reservation saga decremented stock for a paid order (one event per reserved variant line; all carry the same `orderId`). Emitted by `ReservationService` on successful all-or-nothing reserve. `order-service` consumes this to transition the order `PENDING|BACKORDERED → CONFIRMED` (idempotent; duplicate `orderId` confirms once).
- `ORDER_CANCELLED` — a previously `RESERVED` order was cancelled; the saga restored the decremented stock (one event per restored line, same `orderId`).

---

## OrderReservationFailed

Published when the payment-driven reservation saga could **not** reserve stock for a paid order because at least one line was short. Per the all-or-nothing rule **no stock is decremented** — the whole order is held for backorder.

**Topic:** `product.product.reservation-failed`

**Consumers:** order-service (transitions the order `PENDING → BACKORDERED`)

**Payload**
```json
{
  "orderId": "string (UUID)",
  "reason": "INSUFFICIENT_STOCK",
  "shortages": [
    {
      "variantId": "string (UUID)",
      "requested": 5,
      "available": 2
    }
  ]
}
```

`shortages` lists only the lines that were short at reservation time (informational; the order is backordered as a whole regardless of how many lines were short). A later replenishment of the short variants triggers a FIFO re-reservation; on success the saga emits `StockChanged(ORDER_RESERVED)` and the order confirms.

---

## ProductImagesUpdated

Published when images are added to, removed from, or reordered on a product.
This includes changes to `isPrimary` and `sortOrder`.

**Topic:** `product.product.images-updated`

**Consumers:** search-service

**Payload**
```json
{
  "productId": "string (UUID)",
  "thumbnailUrl": "string (nullable — resolved URL of primary image)",
  "images": [
    {
      "imageId": "string (UUID)",
      "objectKey": "string",
      "url": "string (resolved CDN/storage URL)",
      "sortOrder": 0,
      "isPrimary": true
    }
  ]
}
```

`thumbnailUrl` is a convenience field: it equals the `url` of the image with
`isPrimary=true`. If no images remain after deletion, it is `null`.

`images` contains the **full current list** (snapshot, not delta) sorted by
`sortOrder` ascending. Consumers must replace their stored image list entirely.

---

## Consumer Rules

- Consumers must handle duplicate events idempotently.
- Consumers must not fail the entire pipeline on a single malformed event — route to DLQ instead.
- Consumers must not call product-service HTTP API to compensate for missing event data.
- Fields not listed in this contract must not be relied upon.
