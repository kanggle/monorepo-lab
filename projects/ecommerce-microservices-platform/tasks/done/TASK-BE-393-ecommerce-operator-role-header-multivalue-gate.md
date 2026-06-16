# TASK-BE-393 — ecommerce operator role-gate: accept multi-value X-User-Role (multi-domain operator)

**Status:** done
**Domain:** ecommerce · **Services:** product-service, promotion-service, shipping-service, user-service, search-service, settlement-service · **Type:** backend authorization fix (live-surfaced correctness bug)
**Parent / sibling:** TASK-BE-380 (gateway operator-surface routing/admission). BE-380 fixed the **gateway** so operator tokens reach these endpoints; this fixes the **service-side** `X-User-Role == ADMIN` self-gate, which BE-380 explicitly left to the producers.

> **Task number:** BE-393 (global TASK-BE counter; 390=ecommerce, 391=wms-platform, 392 held by a concurrent wms-platform session = `be-392-wms-tier-c-saga-drift`).

## Goal

Make every ecommerce operator-plane endpoint that self-gates on `X-User-Role == ADMIN` accept a **multi-value**
`X-User-Role` header so that an operator entitled to **more than one domain** is admitted. Today the gate is an
**exact-string** match and breaks the moment the header carries more than just `ADMIN`.

## Root Cause

The gateway `JwtHeaderEnrichmentFilter` (gateway-service) sets `X-User-Role` to the token `roles` claim **comma-joined**:

```
roles=["ADMIN"]                                   → X-User-Role: "ADMIN"
roles=["ADMIN","ERP_OPERATOR","SCM_OPERATOR",…]   → X-User-Role: "ADMIN,ERP_OPERATOR,SCM_OPERATOR,…"
```

A **single-domain** operator (assume-tenant into a tenant entitled to `ecommerce` only) gets `roles=["ADMIN"]` →
header is exactly `"ADMIN"`. A **multi-domain** operator (assume-tenant into a tenant entitled to several domains)
gets the operator role for **each** entitled domain (`OperatorRoleDerivation`: ecommerce→ADMIN, scm→SCM_OPERATOR,
erp→ERP_OPERATOR, finance→FINANCE_OPERATOR, wms→WMS_OPERATOR) → header is the comma-joined list.

The service-side checks use exact equality:

```java
if (!"ADMIN".equalsIgnoreCase(userRole)) { throw new AccessDeniedException(); }
```

`"ADMIN".equalsIgnoreCase("ADMIN,ERP_OPERATOR,…")` is **false** → `403 ACCESS_DENIED`. So any operator entitled to
ecommerce **plus at least one other domain** is locked out of these surfaces.

`product-service` list/detail of **products** is unaffected because it relies on the gateway
`AccountTypeEnforcementFilter` (`roles ∋ ADMIN`, a membership check) and adds no controller re-check — which is
exactly why the bug reads as "products work, everything else 403".

### Discovery
Surfaced live by the `omni-corp` all-domain demo tenant (one operator entitled to all 5 domains → `X-User-Role`
= `ADMIN,ERP_OPERATOR,FINANCE_OPERATOR,SCM_OPERATOR,WMS_OPERATOR`). `/ecommerce/products` rendered; `/ecommerce/sellers`
returned `403 ACCESS_DENIED` (gateway logged `GET /api/admin/sellers 403`, product-service `ecommerce_seller_forbidden`).

## Scope

Service-side authorization only. No gateway change (BE-380 done), no console change, no Flyway, no contract semantics
change. The 10 affected sites (all the same `"ADMIN".equalsIgnoreCase(userRole)` anti-pattern on the gateway-injected
`X-User-Role`):

- product-service: `AdminSellerController` (list/detail/register via `validateAdminRole`) — `…:89`
- promotion-service: `CouponCommandService:91`, `PromotionCommandService:82`, `PromotionQueryService:53`
- search-service: `SearchAdminController:49`
- settlement-service: `SettlementController:86`
- shipping-service: `RefreshTrackingService:45`, `ShippingCommandService:102`, `ShippingQueryService:54`
- user-service: `AdminUserController:45`

### Change
Replace the exact-equals check with a **token-membership** check: split `X-User-Role` on `,`, trim, and match any
token `equalsIgnoreCase("ADMIN")`. This mirrors the gateway `AccountTypeEnforcementFilter`'s `roles ∋ ADMIN` semantics
(the single source of truth for "is this caller an operator"). Implement once per service as a tiny private helper
(e.g. `hasAdminRole(String header)`) reused by that service's sites — do NOT add a shared `libs/` helper
(HARDSTOP-03: `libs/` must stay project-agnostic; this is ecommerce-specific X-User-Role plumbing).

### Anti-pattern guard
Use split-then-`equalsIgnoreCase`, NOT `header.contains("ADMIN")` — a substring check would false-admit a future
role like `"SUPERADMIN"`/`"NONADMIN"` and is semantically wrong. Trim whitespace around each comma-split token.

## Acceptance Criteria
- An operator token whose `X-User-Role` contains `ADMIN` among several comma-joined roles (e.g.
  `ADMIN,ERP_OPERATOR,SCM_OPERATOR`) is **admitted** at all 10 sites (no 403).
- A single-role `ADMIN` header still admitted (no regression).
- A header with **no** `ADMIN` token (e.g. `SCM_OPERATOR,ERP_OPERATOR`, or empty/missing) is still **403**.
- A header containing a superstring like `SUPERADMIN` (and no real `ADMIN` token) is **403** (no substring false-match).
- Unit tests per service covering: multi-role-with-ADMIN (admit), single ADMIN (admit), roles-without-ADMIN (deny),
  empty/null (deny), superstring-only (deny).
- Live re-check: `omni-operator` (omni-corp, 5-domain) renders `/ecommerce/sellers` (+ promotions/shippings/users)
  without 403 (`verify-omni-all-domains.mjs` sellers section clean).

## Related Specs / Contracts
- `specs/integration/iam-integration.md` — § "Role 강제" / X-User-Role. Clarify that `X-User-Role` is the
  **comma-joined `roles` claim** and operator gating is **membership of `ADMIN`**, not exact equality (doc the
  multi-value contract so the next implementer doesn't reintroduce exact-match).
- `specs/contracts/http/product-api.md` (§ sellers `/api/admin/sellers`), `promotion-api.md`, `shipping-api.md`,
  `search` admin endpoints, settlement operator endpoints, `user` admin endpoints — operator surfaces gated by
  `X-User-Role ∋ ADMIN`.

## Related Tasks
- TASK-BE-380 (gateway routing/admission for the same operator surfaces — sibling, done).
- ADR-MONO-035 4b-2a (roles-only identity; gateway `JwtHeaderEnrichmentFilter` / `AccountTypeEnforcementFilter`).
- ADR-MONO-032 / BE-376 (`OperatorRoleDerivation` — why multi-domain operators carry multiple roles).

## Edge Cases
- Unified identity token carrying both `CUSTOMER` and `ADMIN` (+ domain operator roles) → admitted (ADMIN present).
- Whitespace after commas (`"ADMIN, SCM_OPERATOR"`) → trimmed, admitted.
- Case variants (`admin`) → `equalsIgnoreCase` admits (matches existing behavior).
- Order independence — `ADMIN` may appear at any position in the list.

## Failure Scenarios
- **Not fixed**: every operator entitled to ecommerce **and** any other domain is locked out of seller / promotion /
  shipping / user / search / settlement operator surfaces (403) — i.e. any real multi-domain operator. Only the
  degenerate single-domain (ecommerce-only) operator works, which the multi-tenant/ADR-030 model does not guarantee.
- **Over-broad fix (substring `contains`)**: would admit unintended future roles — rejected; use token membership.
