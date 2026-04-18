# Error Handling

Defines the platform-wide error response format and error code conventions.

---

# Registry Structure

This document is the **platform-wide error registry**. It contains two kinds of error codes:

1. **Platform-Common** — errors that every project inherits regardless of domain/traits.
   Sections: `Authentication`, `Authorization`, `Validation`, `Rate Limiting`, `Transactional Trait`, `General`.
   These must be carried over verbatim when bootstrapping a new project.

2. **Domain-Specific** — errors that belong to the active primary domain declared in `PROJECT.md`.
   Sections below the `Platform-Common` block are tagged with their owning domain (e.g. `[domain: wms]`).
   When a new project is bootstrapped with a different domain, replace the existing domain sections with the matching domain's error codes.

For the active domain's context and business semantics of each code group, see
[`rules/domains/<domain>.md`](../rules/domains/) → `Standard Error Codes` section. The current project declares `wms`, so the authoritative file is [`rules/domains/wms.md`](../rules/domains/wms.md).

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

> The sections below belong to the active primary domain (`wms`). When bootstrapping a new project with a different `domain` in `PROJECT.md`, replace these sections with the matching domain's error codes.

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
