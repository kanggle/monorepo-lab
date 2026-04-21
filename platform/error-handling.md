# Error Handling

Defines the platform-wide error response format and error code conventions.

---

# Registry Structure

This document is the **platform-wide error registry**. It contains two kinds of error codes:

1. **Platform-Common** — errors that every project inherits regardless of domain/traits.
   Sections: `Authentication`, `Authorization`, `Validation`, `Rate Limiting`, `Transactional Trait`, `Content-Heavy Trait`, `General`.
   These must be carried over verbatim when bootstrapping a new project.

2. **Domain-Specific** — errors that belong to a primary domain declared by a project in `PROJECT.md`.
   Sections below the `Platform-Common` block are tagged with their owning domain (e.g. `[domain: wms]`, `[domain: ecommerce]`).
   In the monorepo, multiple domain sections coexist (one per project). When a standalone project is extracted via `scripts/sync-portfolio.sh`, only the matching domain's sections are carried into the extracted repo.

For each domain's context and business semantics of its code group, see the
matching `rules/domains/<domain>.md` (e.g. [`wms`](../rules/domains/wms.md),
[`ecommerce`](../rules/domains/ecommerce.md)) → `Standard Error Codes` section.

**Change protocol**:
- New platform-common error codes → add to this file only.
- New domain-specific error codes → add to this file **and** cross-reference from the matching `rules/domains/<domain>.md`.
- Do not duplicate the error table inside domain rule files. This document is the single authoritative registry.

---

# Error Response Format

All services must return errors in the following JSON format:

```json
{
  "code": "string",
  "message": "string",
  "timestamp": "string (ISO 8601)"
}
```

- `code`: machine-readable error code in UPPER_SNAKE_CASE
- `message`: human-readable description (must not contain sensitive data)
- `timestamp`: UTC time of the error in ISO 8601 format

Services that return additional context (trace/request ids, structured `details`) are permitted to extend this envelope, but the three fields above must always be present.

---

# HTTP Status Code Mapping

| Situation | HTTP Status |
|---|---|
| Validation failure (missing/invalid field) | 400 Bad Request |
| Authentication failure (invalid credentials, missing token) | 401 Unauthorized |
| Authorization failure (insufficient permission) | 403 Forbidden |
| Resource not found | 404 Not Found |
| Conflict (duplicate resource, optimistic lock collision, idempotency replay mismatch) | 409 Conflict |
| Unprocessable business rule violation (state transition, reference integrity) | 422 Unprocessable Entity |
| Rate limit exceeded | 429 Too Many Requests |
| Internal server error | 500 Internal Server Error |
| Upstream returned an error response (e.g. external ERP/TMS) | 502 Bad Gateway |
| Upstream dependency unavailable | 503 Service Unavailable |

---

# Platform-Common Error Codes

## Authentication

| Code | HTTP | Description |
|---|---|---|
| UNAUTHORIZED | 401 | Access token missing or invalid |
| INVALID_CREDENTIALS | 401 | Login credentials rejected |
| INVALID_REFRESH_TOKEN | 401 | Refresh token not found or expired |
| REFRESH_TOKEN_REVOKED | 401 | Refresh token has been explicitly revoked |
| TOKEN_REVOKED | 401 | Access token has been revoked (e.g. logout blacklist) |

## Authorization

| Code | HTTP | Description |
|---|---|---|
| FORBIDDEN | 403 | Authenticated caller lacks the required role/permission |
| ACCESS_DENIED | 403 | Insufficient permissions to access resource (alias kept for backwards compatibility; prefer FORBIDDEN in new code) |

## Validation

| Code | HTTP | Description |
|---|---|---|
| VALIDATION_ERROR | 400 | Request field is missing, malformed, or fails validation |
| BAD_REQUEST | 400 | Request violates structural constraints not captured by a specific validation code |

## Rate Limiting

| Code | HTTP | Description |
|---|---|---|
| RATE_LIMIT_EXCEEDED | 429 | Too many requests. Retry after the interval indicated by the `Retry-After` header |

## Transactional Trait

Codes activated by the `transactional` trait declared in `PROJECT.md`. Expected to be emitted by any service whose mutating endpoints follow `rules/traits/transactional.md` (T1 idempotency, T4 state-machine, T5 optimistic-lock).

| Code | HTTP | Description |
|---|---|---|
| CONFLICT | 409 | Optimistic lock collision on concurrent update (`version` mismatch) |
| STATE_TRANSITION_INVALID | 422 | Requested state transition is not allowed from the current aggregate state |
| DUPLICATE_REQUEST | 409 | Same `Idempotency-Key` replayed with a different request body/hash on the same endpoint |
| REFERENCE_INTEGRITY_VIOLATION | 409 | Operation blocked because active child/related records still reference this aggregate |

## Content-Heavy Trait

Codes activated by the `content-heavy` trait declared in `PROJECT.md`. Expected to be emitted by any service that stores or serves binary media following [`object-storage-policy.md`](object-storage-policy.md).

| Code | HTTP | Description |
|---|---|---|
| STORAGE_UNAVAILABLE | 503 | Object storage backend is unreachable or failed to issue a presigned URL |
| MEDIA_NOT_FOUND | 404 | Announced upload object was not found in the bucket at registration time |
| MEDIA_VALIDATION_FAILED | 400 | Uploaded object size or content-type does not match the allow-list |

## General

| Code | HTTP | Description |
|---|---|---|
| NOT_FOUND | 404 | Requested resource does not exist (use only where a domain-specific `*_NOT_FOUND` does not apply) |
| INTERNAL_ERROR | 500 | Unexpected server-side error |
| DOWNSTREAM_ERROR | 502 | A downstream internal service returned 5xx/timed out after retries exhausted |
| CIRCUIT_OPEN | 503 | Downstream circuit breaker is OPEN; the call was rejected without reaching the dependency. Distinct from DOWNSTREAM_ERROR so dashboards can separate "we tried and it failed" from "we shed load" |
| SERVICE_UNAVAILABLE | 503 | A required upstream service is unavailable |

---

# Domain-Specific Error Codes

> Sections below are grouped by owning domain. In the monorepo multiple domains coexist. During standalone extraction via `scripts/sync-portfolio.sh`, only the matching domain's sections are carried into the extracted repo.

## Master Data  `[domain: wms]`

Owned by `master-service`. See `specs/services/master-service/`.

| Code | HTTP | Description |
|---|---|---|
| WAREHOUSE_NOT_FOUND | 404 | Warehouse with given id/code does not exist |
| WAREHOUSE_CODE_DUPLICATE | 409 | `warehouseCode` is already taken |
| ZONE_NOT_FOUND | 404 | Zone with given id does not exist in the scoped warehouse |
| ZONE_CODE_DUPLICATE | 409 | `zoneCode` is already taken within the warehouse |
| LOCATION_NOT_FOUND | 404 | Location with given id/code does not exist |
| LOCATION_CODE_DUPLICATE | 409 | `locationCode` is already taken globally (W3: location codes are system-wide unique) |
| SKU_NOT_FOUND | 404 | SKU with given id/code does not exist |
| SKU_CODE_DUPLICATE | 409 | `skuCode` is already taken (case-insensitive) |
| BARCODE_DUPLICATE | 409 | `barcode` is already assigned to another SKU |
| PARTNER_NOT_FOUND | 404 | Partner with given id does not exist |
| PARTNER_CODE_DUPLICATE | 409 | `partnerCode` is already taken |
| LOT_NOT_FOUND | 404 | Lot with given id does not exist |
| LOT_NO_DUPLICATE | 409 | `lotNo` is already taken within the parent SKU |
| LOT_EXPIRED | 422 | Requested operation is not allowed on a Lot whose `status = EXPIRED` |
| IMMUTABLE_FIELD | 422 | Attempted to change a field that is immutable after creation (e.g. `warehouseCode`, `skuCode`, `baseUom`, `trackingType`) |

## Inbound  `[domain: wms]`

Owned by `inbound-service` (future). See `rules/domains/wms.md`.

| Code | HTTP | Description |
|---|---|---|
| ASN_NOT_FOUND | 404 | Advance Shipment Notice (ASN) does not exist |
| ASN_ALREADY_CLOSED | 422 | Operation attempted on an already-closed ASN |
| INSPECTION_QUANTITY_MISMATCH | 422 | Inspected quantity does not match the ASN quantity |
| PUTAWAY_LOCATION_FULL | 422 | Target location has insufficient remaining capacity |

## Inventory  `[domain: wms]`

Owned by `inventory-service` (future). See `rules/domains/wms.md`.

| Code | HTTP | Description |
|---|---|---|
| INVENTORY_NOT_FOUND | 404 | No inventory row exists for the given location/SKU/lot tuple |
| INSUFFICIENT_STOCK | 422 | Requested quantity exceeds available (non-reserved) stock |
| ADJUSTMENT_REASON_REQUIRED | 400 | Inventory adjustment submitted without a reason |
| TRANSFER_SAME_LOCATION | 400 | Source and destination location are the same |

## Outbound  `[domain: wms]`

Owned by `outbound-service` (future). See `rules/domains/wms.md`.

| Code | HTTP | Description |
|---|---|---|
| ORDER_NOT_FOUND | 404 | Outbound order does not exist |
| ORDER_ALREADY_SHIPPED | 422 | Operation attempted on an already-shipped order |
| PICKING_QUANTITY_EXCEEDED | 422 | Picked quantity exceeds ordered quantity |
| PACKING_INCOMPLETE | 422 | Shipping attempted before packing is complete |

## Product  `[domain: ecommerce]`

Owned by `product-service`. See `rules/domains/ecommerce.md`.

| Code | HTTP | Description |
|---|---|---|
| PRODUCT_NOT_FOUND | 404 | Product with given ID does not exist |
| INVALID_CATEGORY | 400 | Category with given ID does not exist |
| VARIANT_NOT_FOUND | 404 | Variant with given ID does not exist |
| INSUFFICIENT_STOCK | 400 | Stock adjustment would result in negative stock |
| IMAGE_NOT_FOUND | 404 | Image with given ID does not exist for this product |
| IMAGE_LIMIT_EXCEEDED | 422 | Product already has the maximum number of images |

## Search  `[domain: ecommerce]`

Owned by `search-service`. See `rules/domains/ecommerce.md`.

| Code | HTTP | Description |
|---|---|---|
| INVALID_SEARCH_REQUEST | 400 | Search request is invalid (missing or blank `q` parameter, invalid `size`) |

## Order  `[domain: ecommerce]`

Owned by `order-service`. See `rules/domains/ecommerce.md` rules E1, E2.

| Code | HTTP | Description |
|---|---|---|
| ORDER_NOT_FOUND | 404 | Order with given ID does not exist |
| INVALID_ORDER_REQUEST | 400 | Order request is invalid (missing fields, invalid quantity) |
| ORDER_CANNOT_BE_CANCELLED | 422 | Order cannot be cancelled in its current status |

## Payment  `[domain: ecommerce]`

Owned by `payment-service`. See `rules/domains/ecommerce.md` rule E2.

| Code | HTTP | Description |
|---|---|---|
| PAYMENT_NOT_FOUND | 404 | Payment for given order does not exist |
| INVALID_PAYMENT_REQUEST | 400 | Payment request is invalid (missing required identity header) |
| AMOUNT_MISMATCH | 400 | Confirm amount does not match PENDING payment amount |
| PAYMENT_ALREADY_COMPLETED | 409 | Payment is not in PENDING status |
| PG_CONFIRM_FAILED | 502 | Payment Gateway confirmation API returned an error |

## User  `[domain: ecommerce]`

Owned by `user-service`.

| Code | HTTP | Description |
|---|---|---|
| USER_PROFILE_NOT_FOUND | 404 | User profile does not exist |
| ADDRESS_NOT_FOUND | 404 | Address with given ID does not exist |
| ADDRESS_LIMIT_EXCEEDED | 422 | Maximum number of addresses reached |
| DEFAULT_ADDRESS_CANNOT_BE_DELETED | 422 | Cannot delete the default address while other addresses exist |
| USER_ALREADY_WITHDRAWN | 422 | User has already been withdrawn |

## Promotion  `[domain: ecommerce]`

Owned by `promotion-service`. See `rules/domains/ecommerce.md` rule E7.

| Code | HTTP | Description |
|---|---|---|
| INVALID_PROMOTION_REQUEST | 400 | Promotion request is invalid (missing or invalid fields, bad status filter, invalid date format) |
| PROMOTION_NOT_FOUND | 404 | Promotion with given ID does not exist |
| PROMOTION_ALREADY_ENDED | 422 | Cannot update an ended promotion |
| PROMOTION_HAS_ISSUED_COUPONS | 422 | Cannot delete a promotion with issued coupons |
| PROMOTION_NOT_ACTIVE | 422 | Promotion is not currently active |
| COUPON_NOT_FOUND | 404 | Coupon with given ID does not exist |
| COUPON_ALREADY_USED | 422 | Coupon has already been used |
| COUPON_EXPIRED | 422 | Coupon has expired |
| COUPON_NOT_OWNED | 422 | Coupon does not belong to the user |
| COUPON_LIMIT_EXCEEDED | 422 | Issuance would exceed max issuance count |
| COUPON_RESTORE_NOT_ALLOWED | 422 | Coupon cannot be restored (e.g. coupon is not in a used state) |

## Notification  `[domain: ecommerce]`

Owned by `notification-service`.

| Code | HTTP | Description |
|---|---|---|
| NOTIFICATION_NOT_FOUND | 404 | Notification with given ID does not exist |
| INVALID_PREFERENCE_REQUEST | 400 | Notification preference request is invalid |
| INVALID_TEMPLATE_REQUEST | 400 | Notification template request is invalid |
| TEMPLATE_NOT_FOUND | 404 | Template with given ID does not exist |
| TEMPLATE_ALREADY_EXISTS | 409 | Template for this type and channel already exists |

## Review  `[domain: ecommerce]`

Owned by `review-service`. See `rules/domains/ecommerce.md` rule E6.

| Code | HTTP | Description |
|---|---|---|
| INVALID_REVIEW_REQUEST | 400 | Review request is invalid (missing or invalid fields) |
| REVIEW_NOT_FOUND | 404 | Review with given ID does not exist |
| REVIEW_ALREADY_EXISTS | 409 | User already reviewed this product |
| PRODUCT_NOT_PURCHASED | 422 | User has not purchased this product |

## Wishlist  `[domain: ecommerce]`

Owned by `user-service` (wishlist feature) or a dedicated service.

| Code | HTTP | Description |
|---|---|---|
| INVALID_WISHLIST_REQUEST | 400 | Wishlist request is invalid (missing or invalid fields) |
| WISHLIST_ITEM_NOT_FOUND | 404 | Wishlist item with given ID does not exist |
| ALREADY_IN_WISHLIST | 409 | Product is already in the wishlist |

## Shipping  `[domain: ecommerce]`

Owned by `shipping-service`.

| Code | HTTP | Description |
|---|---|---|
| INVALID_SHIPPING_REQUEST | 400 | Shipping request is invalid (missing or invalid fields) |
| SHIPPING_NOT_FOUND | 404 | Shipping record does not exist |
| INVALID_STATUS_TRANSITION | 422 | Shipping status transition is not allowed |

---

# Rules

- Services must never expose stack traces, internal class names, or SQL in error responses.
- Error codes must be registered in this document before use.
- `GlobalExceptionHandler` (or equivalent) must handle all unhandled exceptions and return the standard format.
- Validation errors must use `VALIDATION_ERROR` and include the first failing field message.
- Business rule violations must use a domain-specific code (e.g. `LOCATION_CODE_DUPLICATE`) rather than the generic `VALIDATION_ERROR`.
- Codes introduced by a trait (transactional / integration-heavy / ...) belong in the matching Platform-Common subsection, not under the domain. Only truly domain-specific semantics go under `[domain: wms]`.

---

# Change Rule

New error codes must be added to this document before being used in implementation.
