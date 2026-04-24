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
  "payload": {}
}
```

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

**reason values:** `RESTOCK`, `ORDER_RESERVED`, `ORDER_CANCELLED`, `ADMIN_ADJUSTMENT`

**orderId:** nullable. Present when `reason` is `ORDER_RESERVED` or `ORDER_CANCELLED`. Identifies the order that triggered the stock change.

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
