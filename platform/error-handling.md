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
| INVALID_STATE | 400 | OAuth `state` parameter missing, malformed, or does not match the stored CSRF token (RFC 6749 §10.12). Promoted to Platform-Common in TASK-MONO-052 — emitted by both ecommerce `auth-service` and GAP `auth-service` with identical semantics |

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
| IDEMPOTENCY_KEY_REQUIRED | 400 | `Idempotency-Key` header absent on a mutating endpoint that requires it (T1). Emitted by handler-side `MissingRequestHeaderException` guard, not a domain exception |
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
| DATA_INTEGRITY_VIOLATION | 409 | Generic DB constraint violation not covered by a domain-specific code. Catch-all surfaced by Spring `DataIntegrityViolationException` when no `*Exception.java` mapping applies. Prefer a domain code when a known constraint is hit |
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

Owned by `inbound-service`. See `rules/domains/wms.md` and
`specs/services/inbound-service/`.

| Code | HTTP | Description |
|---|---|---|
| ASN_NOT_FOUND | 404 | Advance Shipment Notice (ASN) does not exist |
| ASN_NO_DUPLICATE | 409 | `asnNo` is already taken globally |
| ASN_ALREADY_CLOSED | 422 | Operation attempted on an already-closed or already-cancelled ASN (specifically: cancellation attempted from `IN_PUTAWAY` or beyond, or any mutation on `CLOSED`/`CANCELLED`) |
| INSPECTION_NOT_FOUND | 404 | Inspection does not exist for the given id or ASN |
| INSPECTION_QUANTITY_MISMATCH | 422 | `qtyPassed + qtyDamaged + qtyShort` exceeds the ASN line's `expectedQty` |
| INSPECTION_INCOMPLETE | 422 | ASN cannot transition forward — at least one `InspectionDiscrepancy` is unacknowledged |
| PUTAWAY_INSTRUCTION_NOT_FOUND | 404 | PutawayInstruction does not exist for the given id or ASN |
| PUTAWAY_LINE_NOT_FOUND | 404 | PutawayLine does not exist within the given instruction |
| PUTAWAY_QUANTITY_EXCEEDED | 422 | Sum of `qtyToPutaway` for an AsnLine exceeds the corresponding `qtyPassed` from inspection |
| PUTAWAY_LOCATION_FULL | 422 | Target location has insufficient remaining capacity (advisory in v1; reserved for v2 hard-guard) |
| WAREHOUSE_MISMATCH | 422 | Destination location belongs to a different warehouse than the ASN (cross-service: also emitted by `outbound-service` for `Shipment.warehouseId` vs `Order.warehouseId` consistency — same semantic) |
| PARTNER_INVALID_TYPE | 422 | Supplier resolved by `supplierPartnerId/Code` is not `ACTIVE` and of `partner_type ∈ {SUPPLIER, BOTH}` |
| LOT_REQUIRED | 422 | LOT-tracked SKU received without `lotId` or `lotNo` at inspection |
| SKU_INACTIVE | 422 | Referenced SKU exists but is not `ACTIVE` (cannot be received or putaway). Cross-service: also emitted by `outbound-service` during picking. |
| LOCATION_INACTIVE | 422 | Putaway target location is not `ACTIVE` (deactivated by ops or under maintenance) |
| WEBHOOK_SIGNATURE_INVALID | 401 | ERP webhook HMAC signature missing, malformed, or mismatched |
| WEBHOOK_TIMESTAMP_INVALID | 401 | ERP webhook `X-Erp-Timestamp` header missing, unparseable, or outside the ±5-minute window |
| WEBHOOK_REPLAY_DETECTED | 401 | Reserved for future webhook-source replay protection (not used in v1; v1 returns 200 `ignored_duplicate` for repeat `X-Erp-Event-Id`) |

## Inventory  `[domain: wms]`

Owned by `inventory-service`. See `rules/domains/wms.md` and
`specs/services/inventory-service/`.

| Code | HTTP | Description |
|---|---|---|
| INVENTORY_NOT_FOUND | 404 | No inventory row exists for the given location/SKU/lot tuple |
| INSUFFICIENT_STOCK | 422 | Requested quantity exceeds available (non-reserved) stock |
| ADJUSTMENT_NOT_FOUND | 404 | Inventory adjustment with given id does not exist |
| ADJUSTMENT_REASON_REQUIRED | 400 | Inventory adjustment submitted without a reason |
| TRANSFER_NOT_FOUND | 404 | Stock transfer with given id does not exist |
| TRANSFER_SAME_LOCATION | 400 | Source and destination location are the same |
| RESERVATION_NOT_FOUND | 404 | Reservation with given id does not exist |
| RESERVATION_QUANTITY_MISMATCH | 422 | Release/consume quantity exceeds the reservation's reserved quantity |

## Outbound  `[domain: wms]`

Owned by `outbound-service`. See `rules/domains/wms.md` and
`specs/services/outbound-service/`.

| Code | HTTP | Description |
|---|---|---|
| ORDER_NOT_FOUND | 404 | Outbound order does not exist |
| ORDER_ALREADY_SHIPPED | 422 | Operation attempted on an already-shipped order |
| ORDER_NO_DUPLICATE | 409 | `orderNo` is already taken (mirror of inbound `ASN_NO_DUPLICATE`). Note: current `OrderNoDuplicateException` returns `CONFLICT` (Transactional Trait generic) — code rename to `ORDER_NO_DUPLICATE` is a deferred housekeeping item. |
| PICKING_REQUEST_NOT_FOUND | 404 | Picking request (assignment from order to picker) does not exist |
| PICKING_QUANTITY_EXCEEDED | 422 | Picked quantity exceeds ordered quantity |
| PICKING_INCOMPLETE | 422 | Packing attempted before picking is complete |
| PACKING_UNIT_NOT_FOUND | 404 | Packing unit (carton/pallet) does not exist for the given id or order |
| PACKING_INCOMPLETE | 422 | Shipping attempted before packing is complete |
| SHIPMENT_NOT_FOUND | 404 | Shipment record does not exist for the given id or order |
| EXTERNAL_SERVICE_UNAVAILABLE | 503 | Third-party integration (TMS / supplier / external WMS adapter) is unreachable after retries / circuit-breaker exhausted. Distinct from `SERVICE_UNAVAILABLE` (internal monorepo service) — different operator playbook (3rd-party SLA, support ticket vs internal restart). |

## Admin  `[domain: wms]`

Owned by `admin-service`. See `rules/domains/wms.md` and
`specs/services/admin-service/`.

| Code | HTTP | Description |
|---|---|---|
| USER_NOT_FOUND | 404 | User with given id does not exist |
| ROLE_NOT_FOUND | 404 | Role with given id does not exist |
| ASSIGNMENT_NOT_FOUND | 404 | UserRoleAssignment with given id does not exist |
| SETTING_NOT_FOUND | 404 | Setting with given `(key, warehouseId)` tuple does not exist |
| USER_EMAIL_DUPLICATE | 409 | `email` is already taken (case-insensitive uniqueness) |
| ROLE_CODE_DUPLICATE | 409 | `roleCode` is already taken |
| USER_HAS_ACTIVE_ASSIGNMENTS | 422 | Cannot deactivate a User who still has `ACTIVE` `UserRoleAssignment` rows. Override requires `force=true` and `WMS_SUPERADMIN` role |
| ROLE_IN_USE | 422 | Cannot deactivate a Role still referenced by `ACTIVE` `UserRoleAssignment` rows. Override requires `force=true` and `WMS_SUPERADMIN` role |
| ROLE_BUILTIN_IMMUTABLE | 422 | Built-in roles (`WMS_VIEWER` / `WMS_OPERATOR` / `WMS_ADMIN` / `WMS_SUPERADMIN`) cannot be deleted; only `permissionsJson` may be updated |
| SETTING_VALIDATION_ERROR | 400 | Setting `valueJson` does not satisfy the persisted `schemaJson` |
| SETTING_IMMUTABLE_FIELD | 422 | Attempted to change a Setting field that is immutable after creation (`key`, `scope`, `warehouseId`). Reuses `IMMUTABLE_FIELD` semantics with admin-specific naming |

## Notification  `[domain: wms]`

Owned by `notification-service`. See `specs/services/notification-service/`. Surface is non-REST in v1 (event-consumer only) — codes are observed in delivery audit + outbox payloads, not over HTTP. They become HTTP-mapped when v2 introduces the admin retry surface.

| Code | HTTP | Description |
|---|---|---|
| DELIVERY_RETRY_EXHAUSTED | 422 | A `NotificationDelivery` reached `attemptCount == max_attempts` (5 in v1) with the last attempt failing. The delivery transitions to terminal `FAILED` and the outbox emits `notification.delivered` with `outcome=FAILED_RETRY_EXHAUSTED`. |
| DELIVERY_STATE_TRANSITION_INVALID | 422 | Application code attempted a forbidden transition on a terminal `NotificationDelivery` (T4). Programmer error — surfaces only on a code path bug. |
| IDEMPOTENCY_KEY_DUPLICATE | 409 | UNIQUE constraint on `delivery_idempotency_key` violated under concurrent routing. Caller may safely retry — the existing row is the canonical delivery for the (event, channel) tuple. |
| ROUTING_AMBIGUOUS | 422 | Multiple enabled rules matched the same `eventType`. The partial UNIQUE index normally prevents this; surfaces only on bad manual DB edit (or two-rule race during admin v2 CRUD). |
| ROUTING_RULE_NOT_FOUND | 404 | No enabled routing rule for the inbound `eventType`. Logged at WARN; the dedupe row records `outcome=NO_RULE`. Not necessarily an error in v1 — events not in the seeded rule table simply don't trigger alerts. |

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
| MEDIA_NOT_FOUND | 404 | Media object not found in object storage by key (product-service `MediaNotFoundException`) |
| MEDIA_VALIDATION_FAILED | 400 | Media upload payload fails format, size, or MIME-type validation (product-service `MediaValidationException`) |
| STORAGE_UNAVAILABLE | 503 | Object storage service is unreachable or returned an error (product-service `StorageUnavailableException`) |

## Search  `[domain: ecommerce]`

Owned by `search-service`. See `rules/domains/ecommerce.md`.

| Code | HTTP | Description |
|---|---|---|
| INVALID_SEARCH_REQUEST | 400 | Search request is invalid (missing or blank `q` parameter, invalid `size`) |
| SEARCH_UNAVAILABLE | 503 | Search infrastructure (Elasticsearch / OpenSearch) is temporarily unavailable (search-service `SearchException`) |

## Auth  `[domain: ecommerce]`

Owned by `auth-service` — ecommerce-local credential and OAuth flow (distinct from GAP IdP; the ecommerce standalone repo retains its own auth flow per `project_gap_idp_promotion.md` § standalone frozen policy).

| Code | HTTP | Description |
|---|---|---|
| EMAIL_ALREADY_EXISTS | 409 | Email already registered; concurrent registration race resolved to conflict (`EmailAlreadyExistsException`) |
| INVALID_REFRESH_TOKEN | 401 | Refresh token missing, expired, or unknown (`InvalidRefreshTokenException`). Same string as Platform-Common Authentication entry — ecommerce-local emission |
| REFRESH_TOKEN_REVOKED | 401 | Refresh token explicitly revoked (`RefreshTokenRevokedException`). Same string as Platform-Common Authentication entry — ecommerce-local emission |
| OAUTH_UPSTREAM_ERROR | 502 | OAuth provider returned an error response to the token-exchange call (`OAuthUpstreamException`) |

> `INVALID_CREDENTIALS` and `INVALID_STATE` are emitted by this service but are documented under Platform-Common Authentication (shared across ecommerce + GAP auth-service emissions).

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
| PG_CONFIRM_FAILED | 502 | Payment Gateway confirmation API returned a definitive error (4xx) — PG processed the request and rejected it. Payment row transitions to `FAILED`. |
| PG_GATEWAY_UNAVAILABLE | 503 | Payment Gateway unreachable after Resilience4j retry / circuit-breaker / bulkhead exhaustion (5xx, timeout, circuit open). Distinct from `PG_CONFIRM_FAILED` — PG actual state is unknown, so the payment row is NOT transitioned to `FAILED` and the caller may idempotently retry. ADR-MONO-005 § D4 Category B (TASK-BE-139). |

## User  `[domain: ecommerce]`

Owned by `user-service`.

| Code | HTTP | Description |
|---|---|---|
| USER_PROFILE_NOT_FOUND | 404 | User profile does not exist |
| ADDRESS_NOT_FOUND | 404 | Address with given ID does not exist |
| ADDRESS_LIMIT_EXCEEDED | 422 | Maximum number of addresses reached |
| DEFAULT_ADDRESS_CANNOT_BE_DELETED | 422 | Cannot delete the default address while other addresses exist |
| USER_ALREADY_WITHDRAWN | 422 | User has already been withdrawn (v2-planned — no current `*Exception.java` source; double-withdraw guard relies on generic codes) |

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
| INVALID_WISHLIST_REQUEST | 400 | Wishlist request is invalid (v2-planned — currently handled via generic `VALIDATION_ERROR`) |
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

## Procurement  `[domain: scm]`

Owned by `procurement-service`. PO state machine + supplier integration.

| Code | HTTP | Description |
|---|---|---|
| PO_NOT_FOUND | 404 | Purchase order does not exist |
| PO_STATE_TRANSITION_INVALID | 422 | Requested PO state transition is not allowed from current state (use `STATE_TRANSITION_INVALID` from Transactional Trait if generic) |
| PO_ALREADY_CONFIRMED | 422 | PO is already in CONFIRMED state; idempotent re-confirm is rejected (`PoAlreadyConfirmedException`) |
| PO_QUANTITY_EXCEEDED | 422 | ASN line quantity exceeds the remaining open PO line quantity (`PoQuantityExceededException`) |
| SUPPLIER_NOT_FOUND | 404 | Supplier reference does not exist |
| SUPPLIER_INACTIVE | 422 | Supplier is deactivated; cannot create new PO |
| SUPPLIER_UNAVAILABLE | 503 | Supplier integration endpoint unreachable after Resilience4j retry / circuit-breaker / bulkhead exhaustion (`SupplierUnavailableException`, S2 / ADR-MONO-005 § D4 Category B reference) |
| CATALOG_SKU_UNKNOWN | 422 | SKU referenced in a PO line does not exist in the product catalog (`CatalogSkuUnknownException`) |
| IDEMPOTENCY_KEY_MISMATCH | 422 | `Idempotency-Key` matches an existing request but the request body hash differs (`IdempotencyKeyMismatchException`). Cf. Platform-Common `DUPLICATE_REQUEST` (409) — scm procurement uses 422 for this specific shape |
| SETTLEMENT_PERIOD_LOCKED | 422 | Settlement period is locked; cannot modify PO line in this period (S3 immutability) — **v2-planned**, no `SettlementPeriodLockedException` class yet (deferred to v2 settlement-service) |
| ASN_OVERRECEIPT | 422 | ASN reports more units than the PO line ordered (per spec deviation policy) |
| RECONCILIATION_DISCREPANCY_OPEN | 422 | Discrepancy exists; manual operator review required (S8 — auto-close forbidden) — **v2-planned**, no exception class yet (deferred to v2 settlement-service) |

> `IDEMPOTENCY_KEY_REQUIRED` (400) is also emitted by procurement-service via handler guard — see Platform-Common Transactional Trait section.

## Inventory Visibility  `[domain: scm]`

Owned by `inventory-visibility-service`. Read-only cross-node visibility (eventual consistency).

| Code | HTTP | Description |
|---|---|---|
| NODE_NOT_FOUND | 404 | Inventory node does not exist |
| SNAPSHOT_NOT_FOUND | 404 | Snapshot for the requested key does not exist |
| NODE_UNREACHABLE | 503 | Inventory node is unreachable (network partition or node down) (`NodeUnreachableException`) |
| SNAPSHOT_STALE | 200 (warning body) | Snapshot data is older than the configured staleness threshold; response includes stale data with `meta.staleness` warning and `meta.warning: "Not for procurement decisions"` (S5). Not an HTTP error — documented for caller awareness (`SnapshotStaleException`). Replaces the prior `STALENESS_THRESHOLD_EXCEEDED` catalog name |

---

## Account  `[domain: saas]`

Owned by `account-service` (Identity Platform — multi-tenant account lifecycle).

| Code | HTTP | Description |
|---|---|---|
| ACCOUNT_NOT_FOUND | 404 | Account does not exist (or cross-tenant — see `multi-tenant.md` M3) |
| ACCOUNT_ALREADY_EXISTS | 409 | Account with the given email/identifier already exists in this tenant |
| ACCOUNT_LOCKED | 423 | Account is locked (failed login threshold exceeded). **TODO**: GAP `auth-service` handler currently returns 403; correct to 423 in a follow-up code-side fix |
| ACCOUNT_DELETED | 410 | Account is soft-deleted (anonymized) |
| ACCOUNT_DORMANT | 423 | Account is in dormant state; reactivation flow required. **TODO**: GAP `auth-service` handler currently returns 403; correct to 423 in a follow-up code-side fix |
| ACCOUNT_STATUS_UNKNOWN | 500 | Account status is in an unexpected value (defensive guard, audit candidate) |
| ACCOUNT_SERVICE_UNREACHABLE | 503 | Internal `account-service` call failed (per `integration-heavy.md` I3); transient |
| EMAIL_ALREADY_VERIFIED | 409 | Email already verified; second verify attempt rejected (`EmailAlreadyVerifiedException`) |
| RATE_LIMITED | 429 | Generic rate-limit for account operations (e.g. resend-verify-email) (`RateLimitedException`) |
| AUTH_SERVICE_UNAVAILABLE | 503 | Upstream `auth-service` unreachable during signup; fail-closed (`AuthServicePort.AuthServiceUnavailable`) |
| BULK_LIMIT_EXCEEDED | 400 | Bulk provisioning request exceeds the 1 000-item limit (`BulkLimitExceededException`) |

## Auth / Token  `[domain: saas]`

Owned by `auth-service` (Spring Authorization Server).

| Code | HTTP | Description |
|---|---|---|
| AUTH_SERVICE_UNAVAILABLE | 503 | Internal `auth-service` call failed (transient) |
| TOKEN_EXPIRED | 401 | Bearer token expired |
| TOKEN_EXPIRED_OR_INVALID | 401 | Bearer token malformed, signature invalid, or expired (combined fallback) |
| TOKEN_REUSE_DETECTED | 401 | Refresh token reuse detected (RT rotation invariant); published as audit event `auth.token.reuse.detected`. Prior catalog alias `TOKEN_REUSE` removed in TASK-MONO-052 — only this canonical form is emitted |
| TOKEN_TENANT_MISMATCH | 403 | Token `tenant_id` claim does not match the targeted resource tenant |
| OAUTH_INVALID_GRANT | 400 | OAuth2 grant is invalid (RFC 6749 §5.2) |
| OAUTH_INVALID_CLIENT | 401 | OAuth2 client authentication failed |
| OAUTH_INSUFFICIENT_SCOPE | 403 | Token scope does not cover the requested resource |
| LOGIN_RATE_LIMITED | 429 | Per-IP / per-account login attempt threshold exceeded |
| LOGIN_TENANT_AMBIGUOUS | 400 | Login identifier matches accounts across multiple tenants without disambiguator |
| CREDENTIALS_INVALID | 401 | GAP auth credentials invalid (email/password login) (`CredentialsInvalidException`). Semantic alias of ecommerce-local `INVALID_CREDENTIALS` — two strings retained intentionally pending future standardization |
| PASSWORD_RESET_TOKEN_INVALID | 400 | Password-reset token unknown, expired, or already consumed (`PasswordResetTokenInvalidException`) |
| CREDENTIAL_ALREADY_EXISTS | 409 | Credential row already exists for account (e.g. social re-link attempt) (`CredentialAlreadyExistsException`) |
| SESSION_REVOKED | 401 | Active session has been administratively revoked (`SessionRevokedException`) |
| SESSION_NOT_FOUND | 404 | Session ID not found (logout / get-session) (`SessionNotFoundException`) |
| SESSION_OWNERSHIP_MISMATCH | 403 | Caller's token does not own the targeted session (`SessionOwnershipMismatchException`) |
| UNSUPPORTED_PROVIDER | 400 | OAuth provider name is not in the configured allowlist (`UnsupportedProviderException`) |
| INVALID_REDIRECT_URI | 400 | OAuth `redirect_uri` not in the registered allowlist (`InvalidOAuthRedirectUriException`) |
| EMAIL_REQUIRED | 422 | OAuth provider did not return email; `email` scope required (`OAuthEmailRequiredException`) |
| PROVIDER_ERROR | 502 | OAuth provider returned an error during token exchange (infra-layer, `OAuthProviderException`) |
| PASSWORD_POLICY_VIOLATION | 400 | Password does not meet complexity policy (`PasswordPolicyViolationException`) — also emitted by admin-service for operator password changes |

## Tenant  `[domain: saas]`

Owned by `account-service` + `admin-service` (per multi-tenant trait M1).

| Code | HTTP | Description |
|---|---|---|
| TENANT_NOT_FOUND | 404 | Tenant does not exist (or actor lacks visibility) |
| TENANT_ALREADY_EXISTS | 409 | Tenant identifier already taken |
| TENANT_FORBIDDEN | 403 | Cross-tenant write or invalid `tenant_id` claim (per `multi-tenant.md` M2/M3) |
| TENANT_SCOPE_DENIED | 403 | Token scope does not include the requested tenant context |
| TENANT_SUSPENDED | 423 | Tenant is suspended (administrative action) |
| TENANT_ID_RESERVED | 400 | Tenant ID is reserved and cannot be used (admin-service `TenantIdReservedException`) |

## Admin  `[domain: saas]`

Owned by `admin-service` (operator portal — operator lifecycle, 2FA, audit-logged commands).

| Code | HTTP | Description |
|---|---|---|
| REASON_REQUIRED | 400 | `X-Operator-Reason` header missing on an audited admin action (`ReasonRequiredException`) |
| PERMISSION_DENIED | 403 | Operator or account lacks required permission/role (`PermissionDeniedException`). Cross-service: also emitted by GAP community + membership services with identical semantics |
| INVALID_BOOTSTRAP_TOKEN | 401 | Bootstrap token missing, expired, or already consumed (`InvalidBootstrapTokenException`) |
| INVALID_2FA_CODE | 401 | TOTP code is invalid or expired (`InvalidTwoFaCodeException`) |
| TOTP_NOT_ENROLLED | 404 | TOTP enrollment required before recovery-code regeneration (`TotpNotEnrolledException`) |
| INVALID_REFRESH_TOKEN | 401 | Admin refresh token invalid (operator portal login) (`InvalidRefreshTokenException`). Same string as Platform-Common Authentication — admin-service-local emission |
| REFRESH_TOKEN_REUSE_DETECTED | 401 | Admin refresh token reuse detected; chain invalidated (`RefreshTokenReuseDetectedException`) |
| TOKEN_REVOKED | 401 | Operator access token has been explicitly revoked (`TokenRevokedException`). Same string as Platform-Common — admin-service-local emission |
| INVALID_RECOVERY_CODE | 401 | 2FA recovery code is invalid (`InvalidRecoveryCodeException`) |
| ENROLLMENT_REQUIRED | 401 | Operator must complete 2FA enrollment before login (`EnrollmentRequiredException`) |
| TOKEN_INVALID | 401 | Operator JWT absent, malformed, or signature-invalid (`OperatorUnauthorizedException`) |
| DOWNSTREAM_ERROR | 503 | Downstream service unavailable on an admin integration call (`DownstreamFailureException`). HTTP 503 (admin-specific) — distinct from Platform-Common General `DOWNSTREAM_ERROR` (502) |
| CIRCUIT_OPEN | 503 | Resilience4j circuit breaker is OPEN (R4j `CallNotPermittedException`). Same string as Platform-Common General — admin-service emission |
| AUDIT_FAILURE | 500 | Audit write failed; command aborted (fail-closed) (`AuditFailureException`) |
| BATCH_SIZE_EXCEEDED | 422 | Admin batch operation exceeds size limit (`BatchSizeExceededException`) |
| IDEMPOTENCY_KEY_CONFLICT | 409 | `Idempotency-Key` already used by a different operation (`IdempotencyKeyConflictException`). Variant of Platform-Common `DUPLICATE_REQUEST` (409) — admin-specific naming |
| OPERATOR_EMAIL_CONFLICT | 409 | Operator email already exists (`OperatorEmailConflictException`) |
| OPERATOR_NOT_FOUND | 404 | Operator account not found (`OperatorNotFoundException`) |
| ROLE_NOT_FOUND | 400 | Role identifier not recognized (`RoleNotFoundException`). Note HTTP 400 (not 404) — invalid identifier value, not missing resource |
| SELF_SUSPEND_FORBIDDEN | 400 | Operator cannot suspend their own account (`SelfSuspendForbiddenException`) |
| CURRENT_PASSWORD_MISMATCH | 400 | Current password does not match stored credential (`CurrentPasswordMismatchException`) |

## Community  `[domain: saas]`

Owned by GAP `community-service` (multi-tenant community feature distinct from `fan-platform/community-service` — same string codes, different service ownership).

| Code | HTTP | Description |
|---|---|---|
| MEMBERSHIP_REQUIRED | 403 | Caller's membership tier is below the content's required tier (`MembershipRequiredException`) |
| ALREADY_FOLLOWING | 409 | Already following this artist (`AlreadyFollowingException`) |
| NOT_FOLLOWING | 404 | Not currently following; unfollow rejected (`NotFollowingException`) |
| POST_STATUS_TRANSITION_INVALID | 422 | Post status transition is not allowed (illegal state guard) |

> Cross-project: `fan-platform/community-service` emits the same 4 strings (see `Community  [domain: fan-platform]` below). The semantic is identical — two services in two different projects intentionally share code strings.

## Membership  `[domain: saas]`

Owned by `membership-service`.

| Code | HTTP | Description |
|---|---|---|
| SUBSCRIPTION_ALREADY_ACTIVE | 409 | Subscription already active; new activation rejected (`SubscriptionAlreadyActiveException`) |
| ACCOUNT_NOT_ELIGIBLE | 409 | Account status disqualifies it from subscription (`AccountNotEligibleException`) |
| ACCOUNT_STATUS_UNAVAILABLE | 503 | `account-service` unreachable during eligibility check (`AccountStatusUnavailableException`) |
| SUBSCRIPTION_NOT_FOUND | 404 | Subscription record not found (`SubscriptionNotFoundException`) |
| SUBSCRIPTION_NOT_ACTIVE | 409 | Operation requires an active subscription (`SubscriptionNotActiveException`) |
| PLAN_NOT_FOUND | 404 | Subscription plan not found (`PlanNotFoundException`) |

## Community  `[domain: fan-platform]`

Owned by `community-service` (post / comment / reaction / follow).

| Code | HTTP | Description |
|---|---|---|
| POST_NOT_FOUND | 404 | Post does not exist (or cross-tenant — see `multi-tenant.md` M3) |
| POST_INVALID_STATE | 422 | Requested transition not allowed from current post state (DRAFT/PUBLISHED/HIDDEN/DELETED) |
| POST_STATUS_TRANSITION_INVALID | 422 | Same semantic as `POST_INVALID_STATE` — current code emits this string (`InvalidStateTransitionException`); cross-shared with GAP community-service |
| MEMBERSHIP_TIER_INSUFFICIENT | 403 | Caller membership tier below required (PUBLIC < FOLLOWERS < MEMBERS_ONLY < SUBSCRIBERS_ONLY) |
| MEMBERSHIP_REQUIRED | 403 | Caller's membership tier insufficient for this content (`MembershipRequiredException`). Cross-project alias — see `Community  [domain: saas]` |
| COMMENT_NOT_FOUND | 404 | Comment does not exist or scope mismatch (`CommentNotFoundException`) |
| SELF_FOLLOW_FORBIDDEN | 422 | Account cannot follow itself (`SelfFollowForbiddenException`) |
| EDIT_WINDOW_EXPIRED | 422 | PUBLISHED post is past the edit window (`EditWindowExpiredException`) |
| ALREADY_FOLLOWING | 409 | Already following this artist (`AlreadyFollowingException`). Cross-project — see `Community  [domain: saas]` |
| NOT_FOLLOWING | 404 | Not currently following; unfollow rejected (`NotFollowingException`). Cross-project — see `Community  [domain: saas]` |
| REACTION_INVALID_TYPE | 400 | Reaction type not in the allowed enum (v2-planned — currently handled via generic `VALIDATION_ERROR`) |
| FEED_QUERY_INVALID | 400 | Feed cursor or filter combination is invalid (v2-planned — currently handled via generic `VALIDATION_ERROR`) |

## Artist  `[domain: fan-platform]`

Owned by `artist-service` (artist identity / fandom metadata).

| Code | HTTP | Description |
|---|---|---|
| ARTIST_NOT_FOUND | 404 | Artist does not exist OR cross-tenant OR DRAFT/ARCHIVED (non-admin → 404 not 403, per content-heavy display rule) |
| ARTIST_INVALID_STATE | 422 | Requested transition not allowed from current artist state (DRAFT/PUBLISHED/ARCHIVED) |
| ARTIST_NOT_PUBLISHED | 422 | Artist is not in PUBLISHED state; operation rejected (`ArtistNotPublishedException`) |
| ARTIST_ARCHIVED | 422 | Artist is ARCHIVED; operation rejected (`ArtistArchivedException`) |
| ARTIST_GROUP_NOT_FOUND | 404 | Artist group not found (`ArtistGroupNotFoundException`) |
| STAGE_NAME_CONFLICT | 409 | Artist stage name already taken (`StageNameConflictException`) |
| GROUP_NAME_CONFLICT | 409 | Artist group name already taken (`GroupNameConflictException`) |
| FANDOM_NOT_FOUND | 404 | Fandom record not found (`FandomNotFoundException`) |
| FANDOM_ALREADY_EXISTS | 422 | Fandom already exists for this artist (`FandomAlreadyExistsException`) |
| ALREADY_MEMBER | 422 | Account already a fandom member (`AlreadyMemberException`) |
| FOLLOW_LIMIT_EXCEEDED | 429 | Per-account follow count threshold exceeded (v2-planned — no current exception class) |
| FANDOM_METADATA_INVALID | 400 | Fandom metadata payload fails schema validation (v2-planned — currently handled via generic `VALIDATION_ERROR`) |

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
