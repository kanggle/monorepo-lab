# HTTP Contract: Platform Console Product / Tenant Registry (admin-service)

> **TASK-BE-296**. GAP is the **producer** of this surface; the authoritative
> consumer-side item shape is
> [`projects/platform-console/specs/contracts/console-integration-contract.md` § 2.2](../../../../platform-console/specs/contracts/console-integration-contract.md).
> This contract is the GAP-side source-of-truth for the endpoint path, auth,
> tenant scoping, and the full response envelope. Read-only — no mutation, no
> audit row (consistent with the existing tenant read-path,
> `GetTenantUseCase` / `ListTenantsUseCase`).

base path: `/api/admin/console`

---

## Host service decision (architecture)

The registry surface is **operator-scoped and tenant-aware**: it returns the
products a given operator may see and, per product, the tenant ids that
operator may select. The only GAP service that owns the **operator
authentication boundary** is `admin-service`
([admin-service/architecture.md](../../services/admin-service/architecture.md)
"Admin IdP Boundary") via `OperatorAuthenticationFilter`, and the only GAP
service that already models **cross-tenant operator scope** is `admin-service`
(ADR-002 platform-scope sentinel `tenant_id='*'`,
[admin-service/architecture.md](../../services/admin-service/architecture.md)
"Tenant Scope Enforcement"). The gateway already treats `/api/admin/**` as a
public-path subtree and **delegates operator JWT verification to
admin-service** as a platform invariant
([gateway-api.md §Admin Routes](gateway-api.md)). Therefore the registry is
hosted by **admin-service** under `/api/admin/console/registry`:

- Reuses the existing operator JWT auth boundary — no new auth infrastructure.
- Reuses `AdminOperatorJpaRepository` + `AdminOperator.isPlatformScope()` for
  the operator's tenant scope (ADR-002).
- Reuses `ListTenantsUseCase` (read-through proxy to account-service which
  owns `tenants`) to enumerate registered tenants.

The registry is **not** placed under the tenant-scoped
`/internal/tenants/{tenantId}/**` gateway route: that route enforces
`path tenantId == JWT tenant_id` equality (single-tenant), which is the wrong
semantics for an operator catalog that spans the operator's accessible
tenants.

> **Consumer env note (producer-side authoritative):** the GAP-side registry
> URL is `http://gap.local/api/admin/console/registry` (gateway hostname
> `gap.local` → `/api/admin/**` → admin-service). The console's
> `CONSOLE_REGISTRY_URL` (in `projects/platform-console/`, out of scope for
> TASK-BE-296) must point at this path. Any prior placeholder
> (`/internal/console/registry`) is superseded by this contract — the producer
> owns the path (console-integration-contract § 2.2 fixes only the item shape).

---

## Gateway routing

- Public path subtree (gateway skips its account-JWT check; admin-service
  `OperatorAuthenticationFilter` is the single operator-identity verification
  point — same model as `GET /api/admin/audit`):
  `GET:/api/admin/console/registry`.
- Route id `admin-service` (`Path=/api/admin/**`) already forwards this path;
  no new route is required, only a `public-paths` entry
  ([gateway-api.md](gateway-api.md) Route Map + §Admin Routes).

---

## Authentication

- `Authorization: Bearer <operator-token>` required
  (`token_type = "admin"`, `iss = "admin-service"`).
- Verified by admin-service `OperatorAuthenticationFilter` before the
  controller is reached (gateway does not double-verify — platform invariant).
- `X-Operator-Reason` is **NOT** required: this is a read-only catalog lookup
  for the operator's own console shell, not an operational command against
  another subject (same exemption rationale as the
  `admin-api.md` Authentication-Exceptions read rationale; analogous to
  `GET /api/admin/me`, which also requires no `X-Operator-Reason`).

## Authorization

- Any **valid operator JWT** may call this endpoint (no specific
  RBAC permission key — analogous to `GET /api/admin/me`). The response is
  **scoped by the operator's tenant**, so a low-privilege operator simply
  sees fewer selectable tenants; it never sees another tenant's ids.
- Operator tenant scope is resolved from `admin_operators.tenant_id`:
  - **Platform-scope operator** (`tenant_id = '*'`, i.e. SUPER_ADMIN):
    sees every product as `available` per the product catalog, and every
    **registered, ACTIVE** tenant in `tenants` is selectable for each
    available product.
  - **Single-tenant operator** (`tenant_id = "<slug>"`): every available
    product lists exactly that one tenant slug in `tenants` (length 1),
    provided that tenant is registered + ACTIVE; otherwise `tenants` is `[]`.
- An operator whose `admin_operators` row is missing → `401 TOKEN_INVALID`
  (`OperatorUnauthorizedException`, existing behavior).

---

## GET /api/admin/console/registry

Returns the data-driven product/tenant catalog the console renders.

**Auth required**: Yes (operator token, `token_type=admin`)
**Required permission**: none (valid operator JWT only)
**X-Operator-Reason**: not required (read-only)
**Idempotent**: yes (pure read; safe to retry)

**Request**: no body, no query parameters.

**Response 200 OK**:

```json
{
  "products": [
    {
      "productKey": "gap",
      "displayName": "Global Account Platform",
      "available": true,
      "tenants": ["fan-platform", "wms", "scm"],
      "baseRoute": "/gap"
    },
    {
      "productKey": "wms",
      "displayName": "Warehouse Management Platform",
      "available": true,
      "tenants": ["wms"],
      "baseRoute": "/wms"
    },
    {
      "productKey": "scm",
      "displayName": "Supply Chain Management Platform",
      "available": true,
      "tenants": ["scm"],
      "baseRoute": "/scm"
    },
    {
      "productKey": "erp",
      "displayName": "Enterprise Resource Planning",
      "available": false,
      "tenants": [],
      "baseRoute": "/erp"
    },
    {
      "productKey": "finance",
      "displayName": "Finance Platform",
      "available": false,
      "tenants": [],
      "baseRoute": "/finance"
    }
  ]
}
```

### Item shape (normative — mirrors console-integration-contract § 2.2)

| Field | Type | Meaning |
|---|---|---|
| `productKey` | string | one of `gap` \| `wms` \| `scm` \| `erp` \| `finance` |
| `displayName` | string | Catalog tile label |
| `available` | boolean | `false` → console renders "coming soon" |
| `tenants` | string[] | Tenant ids the operator may select for this product |
| `baseRoute` | string | Console-internal route prefix for the product's screens |

### Product catalog (static, registry-driven)

The 5 product keys form a fixed catalog (ADR-MONO-013 federated domains).
`available` is derived:

| productKey | displayName | available rule |
|---|---|---|
| `gap` | Global Account Platform | always `true` (this platform is live) |
| `wms` | Warehouse Management Platform | `true` (bootstrapped — V0010/V0016 seeds) |
| `scm` | Supply Chain Management Platform | `true` (bootstrapped — V0013/V0015 seeds) |
| `erp` | Enterprise Resource Planning | `false` (not bootstrapped — ADR-MONO-008 / future erp ADR) |
| `finance` | Finance Platform | `false` (not bootstrapped) |

Adding a product or flipping `available` is a **registry change only** — zero
`console-web` code change (console-integration-contract § 2.2 / ADR-MONO-013
§ 1.2 / D5). erp/finance are representable today as `available:false`
placeholders (task Edge Case + Out-of-Scope).

### Tenant selection rule

`tenants` per available product is the intersection of:

1. the product's tenant binding — `gap` binds to **all** registered tenants
   (the platform itself federates them); `wms` binds to the `wms` tenant
   slug; `scm` binds to the `scm` tenant slug; `erp`/`finance` bind to none
   (unavailable);
2. the **operator's tenant scope** — `'*'` (platform) ⇒ all; a single
   tenant slug ⇒ only that slug;
3. the tenant being **registered + ACTIVE** in `tenants` (account-service
   owned; read via `ListTenantsUseCase`). A SUSPENDED or unregistered tenant
   is excluded.

An unavailable product always has `tenants: []` regardless of operator scope.

---

## Errors

| Status | Code | Condition |
|---|---|---|
| 401 | `TOKEN_INVALID` | operator JWT missing / expired / tampered / `token_type` mismatch, or operator row not found |
| 401 | `TOKEN_REVOKED` | operator JWT jti is blacklisted (post-logout) |
| 503 | `DOWNSTREAM_ERROR` | account-service tenant list call failed (5xx / timeout) |
| 503 | `CIRCUIT_OPEN` | account-service circuit breaker OPEN |

Error envelope: the standard
[admin-api.md Common Error Format](admin-api.md#common-error-format)
`{ code, message, timestamp }`.

> **Resilience degradation**: if the account-service tenant list is
> unavailable, the endpoint returns `503` rather than a partial catalog with
> empty `tenants` — the console's `integration-heavy` degradation
> (console-integration-contract § 2.5) renders only the registry section as
> degraded, never the whole shell.

---

## Multi-tenant isolation (regression-tested)

- A single-tenant operator's response never contains another tenant's slug in
  any product's `tenants` array — covered by a cross-tenant isolation
  regression test ([rules/traits/multi-tenant.md](../../../../../rules/traits/multi-tenant.md)
  M6; task Failure Scenario "Registry leaks cross-tenant products").
- `gap` product's `tenants` for a single-tenant operator is `["<own>"]`
  (length ≤ 1) — it is **not** the full tenant list.

---

## Change Rule

1. Endpoint path / auth / response envelope changes update **this file**
   before implementation.
2. The item shape (`productKey` / `displayName` / `available` / `tenants` /
   `baseRoute`) is governed by the consumer contract
   (`platform-console/specs/contracts/console-integration-contract.md § 2.2`)
   — a shape change requires updating that file first and an ADR if it alters
   a deployed integration (console-integration-contract § 5).
3. Product catalog membership / `available` rules are documented here and in
   [multi-tenancy.md](../../features/multi-tenancy.md) "Platform Console
   Registry" — keep both in sync in the same PR.
