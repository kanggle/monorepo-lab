# Error Handling

Defines the platform-wide error response format and error code conventions.

---

# Registry Structure

This document is the **platform-wide error registry**. It contains two kinds of error codes:

1. **Platform-Common** — errors that every project inherits regardless of domain/traits.
   Sections: `Authentication`, `Authorization`, `Registration`, `Validation`, `Rate Limiting`, `OAuth`, `General`.
   These must be carried over verbatim when bootstrapping a new project.

2. **Domain-Specific** — errors that belong to the active primary domain declared in `PROJECT.md`.
   Sections below the `Platform-Common` block are tagged with their owning domain (e.g. `[domain: saas]`).
   When a new project is bootstrapped with a different domain, replace the existing domain sections with the matching domain's error codes.

For the active domain's context and business semantics of each code group, see
[`rules/domains/<domain>.md`](../rules/domains/) → `Standard Error Codes` section. The current project declares `saas`, so the authoritative file is [`rules/domains/saas.md`](../rules/domains/saas.md).

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

---

# HTTP Status Code Mapping

| Situation | HTTP Status |
|---|---|
| Validation failure (missing/invalid field) | 400 Bad Request |
| Authentication failure (invalid credentials, missing token) | 401 Unauthorized |
| Authorization failure (insufficient permission) | 403 Forbidden |
| Resource not found | 404 Not Found |
| Conflict (duplicate resource) | 409 Conflict |
| Unprocessable business rule violation | 422 Unprocessable Entity |
| Rate limit exceeded | 429 Too Many Requests |
| Internal server error | 500 Internal Server Error |
| Upstream returned an error response (e.g. OAuth provider, PG) | 502 Bad Gateway |
| Upstream dependency unavailable | 503 Service Unavailable |

---

# Standard Error Codes

## Authentication

| Code | HTTP | Description |
|---|---|---|
| INVALID_CREDENTIALS | 401 | Email or password is incorrect |
| INVALID_REFRESH_TOKEN | 401 | Refresh token not found or expired |
| REFRESH_TOKEN_REVOKED | 401 | Refresh token has been explicitly revoked |
| UNAUTHORIZED | 401 | Access token missing or invalid |
| TOKEN_INVALID | 401 | JWT is missing, expired, has an invalid signature, or is structurally malformed (gateway-generated). Also used when `tenant_id` claim is absent and the grace-period fallback is disabled. |

## Authorization

| Code | HTTP | Description |
|---|---|---|
| ACCESS_DENIED | 403 | Insufficient permissions to access resource |
| PERMISSION_DENIED | 403 | Caller is not the owner or lacks the required role for this operation |
| TENANT_SCOPE_DENIED | 403 | Path `{tenantId}` does not match the JWT `tenant_id` claim; cross-tenant access is forbidden (gateway internal provisioning routes). |

## Registration

| Code | HTTP | Description |
|---|---|---|
| EMAIL_ALREADY_EXISTS | 409 | Email is already registered |

## Validation

| Code | HTTP | Description |
|---|---|---|
| VALIDATION_ERROR | 400 | Request field is missing or fails validation |

## Rate Limiting

| Code | HTTP | Description |
|---|---|---|
| RATE_LIMIT_EXCEEDED | 429 | Too many login attempts (auth-service / gateway login rate limit). Try again later. Distinct from `RATE_LIMITED` (account-service signup and email-verification resend rate limits) — see saas Email Verification section. Code unification is deferred to a separate task; do not conflate the two contexts. |

## OAuth

| Code | HTTP | Description |
|---|---|---|
| INVALID_STATE | 400 | OAuth state is invalid or expired |
| OAUTH_UPSTREAM_ERROR | 502 | OAuth provider returned an error |

## General

| Code | HTTP | Description |
|---|---|---|
| NOT_FOUND | 404 | Requested resource does not exist |
| INTERNAL_ERROR | 500 | Unexpected server-side error |
| DOWNSTREAM_ERROR | 503 | A downstream internal service returned 5xx/timed out after retries exhausted (fail-fast for audit-heavy paths) |
| CIRCUIT_OPEN | 503 | Downstream circuit breaker is OPEN; the call was rejected without reaching the dependency. Distinct from DOWNSTREAM_ERROR so dashboards can separate "we tried and it failed" from "we shed load". Operators should wait for the CB half-open probe before retrying. |
| SERVICE_UNAVAILABLE | 503 | A required upstream service is unavailable |

---

# Domain-Specific Error Codes

> The sections below belong to the active primary domain. When bootstrapping a new project with a different `domain` in `PROJECT.md`, replace these sections with the matching domain's error codes.

## Product  `[domain: ecommerce]`

| Code | HTTP | Description |
|---|---|---|
| PRODUCT_NOT_FOUND | 404 | Product with given ID does not exist |
| INVALID_CATEGORY | 400 | Category with given ID does not exist |
| VARIANT_NOT_FOUND | 404 | Variant with given ID does not exist |
| INSUFFICIENT_STOCK | 400 | Stock adjustment would result in negative stock |
| CONFLICT | 409 | Optimistic locking conflict on concurrent update |

## Search  `[domain: ecommerce]`

| Code | HTTP | Description |
|---|---|---|
| INVALID_SEARCH_REQUEST | 400 | Search request is invalid (missing or blank q parameter, invalid size) |

## Order  `[domain: ecommerce]`

| Code | HTTP | Description |
|---|---|---|
| ORDER_NOT_FOUND | 404 | Order with given ID does not exist |
| INVALID_ORDER_REQUEST | 400 | Order request is invalid (missing fields, invalid quantity) |
| ORDER_CANNOT_BE_CANCELLED | 422 | Order cannot be cancelled in its current status |

## Payment  `[domain: ecommerce]`

| Code | HTTP | Description |
|---|---|---|
| PAYMENT_NOT_FOUND | 404 | Payment for given order does not exist |
| INVALID_PAYMENT_REQUEST | 400 | Payment request is invalid (missing X-User-Id header) |
| AMOUNT_MISMATCH | 400 | Confirm amount does not match PENDING payment amount |
| PAYMENT_ALREADY_COMPLETED | 409 | Payment is not in PENDING status |
| PG_CONFIRM_FAILED | 502 | Toss Payments confirmation API returned an error |
| ACCESS_DENIED | 403 | Not the payment owner (reuses Authorization/ACCESS_DENIED) |

## User  `[domain: ecommerce]`

| Code | HTTP | Description |
|---|---|---|
| USER_PROFILE_NOT_FOUND | 404 | User profile does not exist |
| ADDRESS_NOT_FOUND | 404 | Address with given ID does not exist |
| ADDRESS_LIMIT_EXCEEDED | 422 | Maximum number of addresses reached (10) |
| DEFAULT_ADDRESS_CANNOT_BE_DELETED | 422 | Cannot delete the default address while other addresses exist |
| USER_ALREADY_WITHDRAWN | 422 | User has already been withdrawn |

## Promotion  `[domain: ecommerce]`

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

| Code | HTTP | Description |
|---|---|---|
| NOTIFICATION_NOT_FOUND | 404 | Notification with given ID does not exist |
| INVALID_PREFERENCE_REQUEST | 400 | Notification preference request is invalid |
| INVALID_TEMPLATE_REQUEST | 400 | Notification template request is invalid |
| TEMPLATE_NOT_FOUND | 404 | Template with given ID does not exist |
| TEMPLATE_ALREADY_EXISTS | 409 | Template for this type and channel already exists |

## Review  `[domain: ecommerce]`

| Code | HTTP | Description |
|---|---|---|
| INVALID_REVIEW_REQUEST | 400 | Review request is invalid (missing or invalid fields) |
| REVIEW_NOT_FOUND | 404 | Review with given ID does not exist |
| REVIEW_ALREADY_EXISTS | 409 | User already reviewed this product |
| PRODUCT_NOT_PURCHASED | 422 | User has not purchased this product |

## Wishlist  `[domain: ecommerce]`

| Code | HTTP | Description |
|---|---|---|
| INVALID_WISHLIST_REQUEST | 400 | Wishlist request is invalid (missing or invalid fields) |
| WISHLIST_ITEM_NOT_FOUND | 404 | Wishlist item with given ID does not exist |
| ALREADY_IN_WISHLIST | 409 | Product is already in the wishlist |

## Shipping  `[domain: ecommerce]`

| Code | HTTP | Description |
|---|---|---|
| INVALID_SHIPPING_REQUEST | 400 | Shipping request is invalid (missing or invalid fields) |
| SHIPPING_NOT_FOUND | 404 | Shipping record does not exist |
| INVALID_STATUS_TRANSITION | 422 | Shipping status transition is not allowed |

## Community  `[domain: saas]`

| Code | HTTP | Description |
|---|---|---|
| ARTIST_NOT_FOUND | 404 | Artist account does not exist (`POST /api/community/subscriptions/artists/{artistAccountId}`) |
| POST_STATUS_TRANSITION_INVALID | 422 | Post status transition is not allowed (e.g., DELETED → *) (`PATCH /api/community/posts/{postId}/status`) |

## Signup  `[domain: saas]`

| Code | HTTP | Description |
|---|---|---|
| AUTH_SERVICE_UNAVAILABLE | 503 | Authentication service is temporarily unavailable (account-service signup flow — auth-service 5xx/timeout/circuit-open triggers fail-closed rollback) |

## Tenant Provisioning  `[domain: saas]`

| Code | HTTP | Description |
|---|---|---|
| `TENANT_NOT_FOUND` | 404 | The `{tenantId}` path parameter does not correspond to a registered tenant in the `tenants` table (account-service internal provisioning API). |
| `TENANT_SUSPENDED` | 409 | The target tenant exists but its status is `SUSPENDED`; new account creation is rejected (account-service internal provisioning API). |
| `ROLES_REPLACE_INVALID` | 400 | Role list contains entries that violate role-name constraints (e.g. exceeds 50 chars). |

## Email Verification  `[domain: saas]`

| Code | HTTP | Description |
|---|---|---|
| TOKEN_EXPIRED_OR_INVALID | 400 | Email verification token is expired, does not exist in the Redis store (`email-verify:{token}`), or has already been consumed (account-service `POST /api/accounts/signup/verify-email`). Distinct from auth-service `INVALID_REFRESH_TOKEN` and admin-service `INVALID_BOOTSTRAP_TOKEN` which apply to JWT-based tokens; this code is reserved for the opaque email-verification UUID flow. |
| EMAIL_ALREADY_VERIFIED | 409 | The target account's email is already verified (`accounts.email_verified_at IS NOT NULL`). Surfaced by both `POST /api/accounts/signup/verify-email` (re-verification of an already-verified account) and `POST /api/accounts/signup/resend-verification-email` (resend request when no verification is needed). |
| RATE_LIMITED | 429 | Per-account or per-flow rate limit exceeded for signup-related flows in account-service. Currently surfaced by `POST /api/accounts/signup` (signup attempts rate limit) and `POST /api/accounts/signup/resend-verification-email` (5-minute single-shot resend window via `email-verify:rate:{accountId}` Redis marker). Distinct from `RATE_LIMIT_EXCEEDED` (auth-service / gateway login rate limit) — code unification is deferred to a separate task. Do not conflate the two contexts. |

## Admin Operations  `[domain: saas]`

| Code | HTTP | Description |
|---|---|---|
| BATCH_SIZE_EXCEEDED | 422 | `accountIds` exceeds the per-request batch cap (100) on admin bulk commands |
| IDEMPOTENCY_KEY_CONFLICT | 409 | Same `Idempotency-Key` replayed with a different payload on an admin command |
| AUDIT_FAILURE | 500 | Audit row persistence failed; the admin command is aborted to preserve audit integrity |
| ACCOUNT_NOT_FOUND | 404 | Target account does not exist (admin path only; distinct from public/account-api usage context) |
| STATE_TRANSITION_INVALID | 409 | Requested state transition is not allowed from the current account state (e.g., already DELETED, not LOCKED). Aligned with `specs/contracts/http/internal/admin-to-account.md` lock/unlock/delete/gdpr-delete endpoints which all surface this as a state-transition conflict. The admin-service operator-row same-state PATCH path (`PATCH /api/admin/operators/{id}/status`) is a separate context that may surface 400 — see admin contracts. |
| INVALID_BOOTSTRAP_TOKEN | 401 | Bootstrap token (2FA enroll/verify sub-tree) is missing, malformed, expired, wrong `token_type`, or has been replayed (`jti` already consumed) |
| INVALID_2FA_CODE | 401 | Submitted TOTP code does not verify against the operator's enrolled secret (±1 window, 30s step) |
| INVALID_CREDENTIALS | 401 | Operator lookup miss or Argon2id password verification failure on `POST /api/admin/auth/login` (returned without distinguishing the two so miss vs wrong-password cannot be inferred from the response) |
| ENROLLMENT_REQUIRED | 401 | Operator's role set requires 2FA but no `admin_operator_totp` row exists. Response body carries a single-use bootstrap token authorising the `/2fa/enroll` sub-tree |
| INVALID_RECOVERY_CODE | 401 | Submitted recovery code does not match any stored Argon2id hash after normalization (upper-case trim) and optimistic-lock retry |
| TOTP_NOT_ENROLLED | 404 | Operator has not yet enrolled TOTP; regeneration requires an existing enrollment (`POST /api/admin/auth/2fa/enroll` 선행 필요) |
| BAD_REQUEST | 400 | Login request body violates the `totpCode` / `recoveryCode` mutual exclusion (both present, or both absent when 2FA is required) |
| INVALID_REFRESH_TOKEN | 401 | Operator refresh JWT failed signature/exp/issuer/`token_type=admin_refresh` validation, the jti is not registered in `admin_operator_refresh_tokens`, or the operator id does not match the registered row (TASK-BE-040) |
| REFRESH_TOKEN_REUSE_DETECTED | 401 | An already-revoked refresh jti was presented again — the operator's entire refresh-token chain is bulk-revoked with reason `REUSE_DETECTED` (TASK-BE-040) |
| TOKEN_REVOKED | 401 | The operator access JWT's jti is on the Redis logout blacklist (`admin:jti:blacklist:{jti}`), or the blacklist lookup itself failed — fail-closed per audit-heavy A10 (TASK-BE-040) |
| CURRENT_PASSWORD_MISMATCH | 400 | 현재 비밀번호가 일치하지 않습니다 (`PATCH /api/admin/operators/me/password` — 운영자 본인 비밀번호 변경 시 현재 비밀번호 Argon2id `verify` 실패) |
| PASSWORD_POLICY_VIOLATION | 400 | 새 비밀번호가 정책을 위반합니다 (`PATCH /api/admin/operators/me/password` — 길이 8–128자, 대·소·숫·특 4종 중 3종 이상 미충족) |

---

# Rules

- Services must never expose stack traces, internal class names, or SQL in error responses.
- Error codes must be registered in this document before use.
- `GlobalExceptionHandler` (or equivalent) must handle all unhandled exceptions and return the standard format.
- Validation errors must use `VALIDATION_ERROR` and include the first failing field message.
- Business rule violations must use a domain-specific code (e.g. `EMAIL_ALREADY_EXISTS`), not `VALIDATION_ERROR`.

---

# Change Rule

New error codes must be added to this document before being used in implementation.
