# TASK-BE-380 — ecommerce gateway operator-surface routing + admission completeness

**Status:** ready
**Domain:** ecommerce · **Service:** gateway-service · **Type:** backend gateway routing/admission fix (live-surfaced integration gap)
**Parent:** ADR-MONO-030 (marketplace) / ADR-MONO-031 (platform-console operator absorption) — closes the gateway side of the operator surfaces shipped by BE-375 + PC-FE-086/088/089/090.

> **Task number:** BE-380 (global TASK-BE counter; BE-379 is held by a concurrent ecommerce session = `ecommerce-swagger-jwt`).

## Goal

Make the **operator (Admin)** endpoints of the ecommerce platform reachable through the gateway, end-to-end. Two
gaps were surfaced when the platform-console operator surfaces were first driven **live** against the real gateway
(CI only ever exercised them with mocked HTTP, so neither was caught):

1. **Seller route gap** — `AdminSellerController` (`/api/admin/sellers`, BE-375) has **no gateway route**. The
   product-service predicate is `/api/products/**,/api/admin/products/**` only. BE-375's task doc assumed a generic
   `/api/admin/**` OPERATOR branch routed it — but Spring Cloud Gateway routing is **per-service predicate**, so
   `/api/admin/sellers` matched no route → **404** before reaching product-service.

2. **operator-on-public admission gap** — `promotion-api.md`, `shipping-api.md`, `notification-api.md` deliberately
   host their **Admin** endpoints on the **public path tree** (`GET /api/promotions (Admin)`,
   `GET /api/shippings (Admin)`, `GET /api/notifications/templates (Admin)`), each self-gated service-side by
   `X-User-Role == ADMIN` — NOT under `/api/admin/**`. But `AccountTypeEnforcementFilter` admits non-`/api/admin/`
   paths **only** for `roles ∋ CUSTOMER` (ADR-MONO-035 4b-2a roles-only). So the operator's `roles=[ADMIN]` token is
   **403'd at the gateway** before reaching the service that would accept it. The gateway admission rule and these
   producer contracts are in direct conflict.

## Scope

`gateway-service` only — the producer services (promotion/shipping/notification) already accept the operator
(`X-User-Role == ADMIN`) on these endpoints, and `JwtHeaderEnrichmentFilter` already injects `X-User-Role`; the only
blocker is admission. No producer-service change, no console change, no Flyway.

### Changes
- **Route** — `application.yml` product-service predicate: add `/api/admin/sellers/**` (alongside
  `/api/admin/products/**`). (product-api.md § sellers — `/api/admin/sellers` is the contracted prefix.)
- **Admission** — `AccountTypeEnforcementFilter`: on the non-`/api/admin/` branch, admit `CUSTOMER` **or**
  (`ADMIN` **and** the path is one of the contracted operator-on-public read trees `/api/promotions`,
  `/api/shippings`, `/api/notifications`). Scoped (not blanket ADMIN-everywhere) to keep the blast radius to exactly
  the contracted trees; the service still enforces the per-endpoint operator/consumer split via `X-User-Role`.
- **Contract** — `specs/integration/iam-integration.md` § "Role 강제" + Error-Responses table: document the
  operator-on-public exception and refresh the legacy `account_type` wording to the roles-only model (ADR-MONO-035).

## Acceptance Criteria
- `GET /api/admin/sellers` through the gateway routes to product-service (an ADMIN token reaches the controller; no 404).
- An ADMIN-role token is admitted on `/api/promotions`, `/api/shippings`, `/api/notifications` (and sub-paths); a
  CUSTOMER token still passes there; an ADMIN-only token is **still 403** on other public trees
  (`/api/products`, `/api/orders`, `/api/search`) — scope guard.
- `AccountTypeEnforcementFilterTest` covers the three cases above (added).
- `iam-integration.md` admission rule reflects the exception.

## Related Specs / Contracts
- `specs/integration/iam-integration.md` (gateway admission — updated here)
- `specs/contracts/http/product-api.md` (§ sellers — `/api/admin/sellers`)
- `specs/contracts/http/promotion-api.md`, `shipping-api.md`, `notification-api.md` (Admin endpoints on public path)

## Edge Cases
- Consumer endpoints on the operator-on-public trees (`/api/coupons/me`, `/api/notifications/me`,
  `/api/shippings/orders/{id}`) keep working for CUSTOMER (unchanged).
- A token with both `CUSTOMER` + `ADMIN` (unified identity) passes everywhere (already covered by
  `dualCapability_*` test).
- The carrier webhook (`/api/shippings/carrier-webhook`, public/no-JWT) is unaffected (no security context → pass-through).

## Failure Scenarios
- A CUSTOMER token hitting a promotion/shipping/notification **Admin** endpoint now passes the gateway but is 403'd
  **service-side** (`X-User-Role != ADMIN`) — net behaviour for consumers on operator endpoints is unchanged.
- Over-broad admission (ADMIN on all public paths) is explicitly avoided via the scoped path check + scope-guard test.
