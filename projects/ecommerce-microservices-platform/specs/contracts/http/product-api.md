# HTTP Contract: product-service

## Overview
Published HTTP API for product-service.
All endpoints are accessible through gateway-service only.
Public endpoints (`/api/products/**`) do not require authentication.
Admin endpoints (`/api/admin/products/**`) require an authenticated admin user (Bearer token, admin role).

---

## Base Path
`/api/products`

---

## Endpoints

### GET /api/products
List products with filtering and pagination.

**Query Parameters**
- `name` (optional) — filter by product name (partial match)
- `categoryId` (optional) — filter by category
- `status` (optional) — filter by status: `ON_SALE`, `SOLD_OUT`, `HIDDEN`
- `page` (default: 0) — page number
- `size` (default: 20) — page size

**Response 200**
```json
{
  "content": [
    {
      "id": "string (UUID)",
      "name": "string",
      "status": "ON_SALE",
      "price": 10000,
      "thumbnailUrl": "string",
      "categoryId": "string (UUID)",
      "sellerId": "string (read-only — owning seller within the tenant)"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 100
}
```

`sellerId` (inner marketplace axis — ADR-MONO-030 Step 3 §3.2) is the owning
seller within the tenant. On the CONSUMER plane it is **read-only display** (the
shared catalog is never seller-narrowed — F5). When an OPERATOR request carries a
seller-scope claim (gateway header `X-Seller-Scope`), the list is narrowed to that
seller; an absent / `*` scope returns the full tenant catalog (net-zero, F1).

---

### GET /api/products/{productId}
Get product detail including variants.

**Response 200**
```json
{
  "productId": "string (UUID)",
  "name": "string",
  "description": "string",
  "status": "ON_SALE",
  "price": 10000,
  "categoryId": "string (UUID)",
  "thumbnailUrl": "string (nullable — resolved URL of primary image)",
  "sellerId": "string (read-only — owning seller within the tenant)",
  "images": [
    {
      "imageId": "string (UUID)",
      "url": "string (resolved CDN/storage URL)",
      "sortOrder": 0,
      "isPrimary": true
    }
  ],
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

`thumbnailUrl` is derived: it equals the resolved URL of the image with `isPrimary=true`.
If no images exist, the value from the manual `thumbnailUrl` field (set via PATCH) is used.
`images` is sorted by `sortOrder` ascending; empty array `[]` if no images.

**Error responses**
| Status | Code | Reason |
|---|---|---|
| 404 | PRODUCT_NOT_FOUND | Product with given ID does not exist |

---

### POST /api/admin/products
Register a new product. Requires admin role.

**Request Body**
```json
{
  "name": "string",
  "description": "string",
  "price": 10000,
  "categoryId": "string (UUID)",
  "sellerId": "string (optional — owning seller; OPERATOR surface)",
  "variants": [
    {
      "optionName": "string",
      "stock": 100,
      "additionalPrice": 0
    }
  ]
}
```

`sellerId` (optional, OPERATOR surface — ADR-MONO-030 Step 3 §3.2) sets the owning
seller. When omitted, ownership resolves from the request's seller-scope claim
(`X-Seller-Scope`), else the per-tenant default seller `default` (D8). The CONSUMER
plane has no seller authority — it cannot register products.

**Response 201**
```json
{ "id": "string (UUID)" }
```

**Error responses**
| Status | Code | Reason |
|---|---|---|
| 400 | VALIDATION_ERROR | Missing or invalid field |
| 400 | INVALID_CATEGORY | Category with given ID does not exist |
| 403 | ACCESS_DENIED | Admin role required |

---

### POST /api/admin/sellers
Register a marketplace seller within the current tenant (ADR-MONO-030 Step 3 §3.1).
Requires admin role. Minimal v1 lifecycle (register + ACTIVE); the owning
`tenant_id` is derived from the token (gateway `X-Tenant-Id`), not the body.
Onboarding flow / settlement are out of scope (Step 4).

**Request Body**
```json
{
  "sellerId": "string",
  "displayName": "string"
}
```

**Response 201**
```json
{ "sellerId": "string" }
```

**Error responses**
| Status | Code | Reason |
|---|---|---|
| 400 | VALIDATION_ERROR | Missing or invalid field |
| 403 | ACCESS_DENIED | Admin role required |

---

### PATCH /api/admin/products/{productId}
Update product information. Requires admin role.

**Request Body** (partial update)
```json
{
  "name": "string",
  "description": "string",
  "price": 10000,
  "status": "ON_SALE"
}
```

**Response 200**
```json
{ "productId": "string (UUID)" }
```

**Error responses**
| Status | Code | Reason |
|---|---|---|
| 400 | VALIDATION_ERROR | Missing or invalid field |
| 403 | ACCESS_DENIED | Admin role required |
| 404 | PRODUCT_NOT_FOUND | Product with given ID does not exist |
| 409 | CONFLICT | Optimistic locking conflict |

---

### PATCH /api/admin/products/{productId}/stock
Adjust inventory stock. Requires admin role.

**Request Body**
```json
{
  "variantId": "string (UUID)",
  "quantity": 50,
  "reason": "RESTOCK | ORDER_RESERVED | ORDER_CANCELLED | ADMIN_ADJUSTMENT"
}
```

**Response 200**
```json
{ "variantId": "string (UUID)", "currentStock": 150 }
```

**Error responses**
| Status | Code | Reason |
|---|---|---|
| 400 | VALIDATION_ERROR | Missing or invalid field |
| 400 | INSUFFICIENT_STOCK | Stock adjustment would result in negative stock |
| 403 | ACCESS_DENIED | Admin role required |
| 404 | PRODUCT_NOT_FOUND | Product with given ID does not exist |
| 404 | VARIANT_NOT_FOUND | Variant with given ID does not exist |

---

### POST /api/admin/products/{productId}/images/upload-url
Request a presigned PUT URL for direct image upload to object storage.
Requires admin role.
See `platform/object-storage-policy.md` for the full upload flow.

**Request Body**
```json
{
  "contentType": "image/jpeg",
  "contentLength": 2048000
}
```

**Validation rules**
- `contentType` must be one of: `image/jpeg`, `image/png`, `image/webp`
- `contentLength` must be > 0 and ≤ 5,242,880 (5 MB)
- Product must exist and not be soft-deleted

**Response 200**
```json
{
  "uploadUrl": "string (presigned PUT URL)",
  "objectKey": "products/{productId}/{sortOrder}-{uuid}.jpg",
  "expiresAt": "string (ISO 8601)"
}
```

**Error responses**
| Status | Code | Reason |
|---|---|---|
| 400 | VALIDATION_ERROR | Invalid content type or content length |
| 400 | MEDIA_VALIDATION_FAILED | Content type not in allow-list or size exceeds limit |
| 403 | ACCESS_DENIED | Admin role required |
| 404 | PRODUCT_NOT_FOUND | Product with given ID does not exist |
| 503 | STORAGE_UNAVAILABLE | Object storage backend unreachable |

---

### POST /api/admin/products/{productId}/images
Register a previously uploaded image. Requires admin role.
The service verifies the object exists in the bucket via HEAD before persisting.

**Request Body**
```json
{
  "objectKey": "products/{productId}/0-{uuid}.jpg",
  "sortOrder": 0,
  "isPrimary": true
}
```

**Validation rules**
- `objectKey` must be a valid key matching `products/{productId}/*`
- `sortOrder` must be ≥ 0
- `isPrimary`: if true, any existing primary image is demoted
- Maximum 10 images per product
- Object must exist in the bucket (verified via HEAD)

**Response 201**
```json
{
  "imageId": "string (UUID)",
  "url": "string (resolved CDN/storage URL)",
  "objectKey": "string",
  "sortOrder": 0,
  "isPrimary": true,
  "uploadedAt": "string (ISO 8601)"
}
```

**Side effects**
- Publishes `ProductImagesUpdated` event
- If `isPrimary` is true, updates product `thumbnailUrl` to the resolved URL of this image

**Error responses**
| Status | Code | Reason |
|---|---|---|
| 400 | VALIDATION_ERROR | Missing or invalid field |
| 400 | MEDIA_VALIDATION_FAILED | Object size/type mismatch with allow-list |
| 403 | ACCESS_DENIED | Admin role required |
| 404 | PRODUCT_NOT_FOUND | Product with given ID does not exist |
| 404 | MEDIA_NOT_FOUND | Object key does not exist in the bucket |
| 422 | IMAGE_LIMIT_EXCEEDED | Product already has 10 images |

---

### PATCH /api/admin/products/{productId}/images/{imageId}
Update image metadata (sort order, primary flag). Requires admin role.

**Request Body** (partial update)
```json
{
  "sortOrder": 1,
  "isPrimary": false
}
```

**Response 200**
```json
{
  "imageId": "string (UUID)",
  "url": "string",
  "objectKey": "string",
  "sortOrder": 1,
  "isPrimary": false,
  "uploadedAt": "string (ISO 8601)"
}
```

**Side effects**
- Publishes `ProductImagesUpdated` event
- If `isPrimary` changes, updates product `thumbnailUrl` accordingly

**Error responses**
| Status | Code | Reason |
|---|---|---|
| 400 | VALIDATION_ERROR | Invalid field value |
| 403 | ACCESS_DENIED | Admin role required |
| 404 | PRODUCT_NOT_FOUND | Product with given ID does not exist |
| 404 | IMAGE_NOT_FOUND | Image with given ID does not exist for this product |

---

### DELETE /api/admin/products/{productId}/images/{imageId}
Delete an image. Requires admin role.
Removes both the metadata row and the object from the bucket.

**Response 204** (no body)

**Side effects**
- Publishes `ProductImagesUpdated` event
- If the deleted image was primary, the image with the lowest `sortOrder` is promoted
- Deletes the object from the bucket (best-effort; orphans cleaned by lifecycle)

**Error responses**
| Status | Code | Reason |
|---|---|---|
| 403 | ACCESS_DENIED | Admin role required |
| 404 | PRODUCT_NOT_FOUND | Product with given ID does not exist |
| 404 | IMAGE_NOT_FOUND | Image with given ID does not exist for this product |

---

### GET /api/products/{productId}/images
List all images for a product, sorted by `sortOrder` ascending.
Public endpoint — no authentication required.

**Response 200**
```json
{
  "images": [
    {
      "imageId": "string (UUID)",
      "url": "string (resolved CDN/storage URL)",
      "sortOrder": 0,
      "isPrimary": true
    }
  ]
}
```

**Error responses**
| Status | Code | Reason |
|---|---|---|
| 404 | PRODUCT_NOT_FOUND | Product with given ID does not exist |

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
- Internal stack traces must not appear in error responses.
- Stock adjustment publishes a `StockChanged` event regardless of the direction (increase or decrease).
