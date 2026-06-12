# HTTP Contract: Platform Console Product / Tenant Registry (admin-service)

> **TASK-BE-296**. IAM is the **producer** of this surface; the authoritative
> consumer-side item shape is
> [`projects/platform-console/specs/contracts/console-integration-contract.md` § 2.2](../../../../platform-console/specs/contracts/console-integration-contract.md).
> This contract is the IAM-side source-of-truth for the endpoint path, auth,
> tenant scoping, and the full response envelope. Read-only — no mutation, no
> audit row (consistent with the existing tenant read-path,
> `GetTenantUseCase` / `ListTenantsUseCase`).

base path: `/api/admin/console`

---

## Host service decision (architecture)

The registry surface is **operator-scoped and tenant-aware**: it returns the
products a given operator may see and, per product, the tenant ids that
operator may select. The only IAM service that owns the **operator
authentication boundary** is `admin-service`
([admin-service/architecture.md](../../services/admin-service/architecture.md)
"Admin IdP Boundary") via `OperatorAuthenticationFilter`, and the only IAM
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

> **Consumer env note (producer-side authoritative):** the IAM-side registry
> URL is `http://iam.local/api/admin/console/registry` (gateway hostname
> `iam.local` → `/api/admin/**` → admin-service). The console's
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

> **Operator-token acquisition (TASK-BE-298 / ADR-MONO-014, producer
> requirement UNCHANGED)**: platform-console does not hold an
> admin-service operator token directly — it authenticates operators via the
> IAM OIDC `platform-console-web` client and then **exchanges** that subject
> token for the operator token via
> [`POST /api/admin/auth/token-exchange`](admin-api.md) (RFC 8693). The
> exchanged token is the **same** operator token this endpoint requires:
> `token_type=admin`, `iss=admin-service`, verified by the **same**
> `OperatorAuthenticationFilter`. This contract's producer requirement is
> therefore **unchanged** — only the consumer's token-acquisition step is
> defined elsewhere. The console-side wiring + the
> `projects/platform-console/specs/contracts/console-integration-contract.md`
> §2.1/§2.2 self-contradiction fix are **out of scope here** (ADR-MONO-014
> § D5 step 2 = `TASK-PC-FE-002a`, a cross-project platform-console task);
> this file only cross-references it.
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
      "productKey": "iam",
      "displayName": "Identity & Access Management",
      "available": true,
      "tenants": ["fan-platform", "wms", "scm"],
      "baseRoute": "/iam"
    },
    {
      "productKey": "wms",
      "displayName": "Warehouse Management System",
      "available": true,
      "tenants": ["wms"],
      "baseRoute": "/wms"
    },
    {
      "productKey": "scm",
      "displayName": "Supply Chain Management",
      "available": true,
      "tenants": ["scm"],
      "baseRoute": "/scm"
    },
    {
      "productKey": "erp",
      "displayName": "Enterprise Resource Planning",
      "available": true,
      "tenants": ["erp"],
      "baseRoute": "/erp"
    },
    {
      "productKey": "finance",
      "displayName": "Finance",
      "available": true,
      "tenants": ["finance"],
      "baseRoute": "/finance",
      "operatorContext": { "defaultAccountId": "01928c4a-7e9f-7c00-9a40-d2b1f5e8a000" }
    }
  ]
}
```

> The `operatorContext` example above shows the **set case** — when the
> calling operator's `admin_operators.finance_default_account_id` is non-null
> (per TASK-BE-304). When the operator has not configured a default finance
> account, the `operatorContext` field is **omitted** from the finance item
> entirely (Jackson `@JsonInclude(NON_NULL)`); see § Per-operator profile
> attributes below.

### Item shape (normative — mirrors console-integration-contract § 2.2)

| Field | Type | Meaning |
|---|---|---|
| `productKey` | string | one of `iam` \| `wms` \| `scm` \| `erp` \| `finance` \| `ecommerce` |
| `displayName` | string | Catalog tile label |
| `available` | boolean | `false` → console renders "coming soon" |
| `tenants` | string[] | Tenant ids the operator may select for this product |
| `baseRoute` | string | Console-internal route prefix for the product's screens |
| `operatorContext` | `{ defaultAccountId?: string } \| undefined` | **TASK-BE-304** — optional extensible carrier for per-operator per-product profile attributes; **omitted entirely** when no attribute is set (never rendered as `null`). v1: only the `finance` product item populates this (with `defaultAccountId` from `admin_operators.finance_default_account_id`); the other 5 items always omit it. See § Per-operator profile attributes. |

### Per-operator profile attributes (`operatorContext`) — TASK-BE-304

The `operatorContext` nested object is the **extensible carrier** for
per-operator per-product profile attributes the IAM `admin_operators` row owns
on behalf of a federated product. It is scoped per-operator-row (never
per-tenant, never cross-tenant) and is read only on the operator's own row in
`ConsoleRegistryUseCase.resolveOperator(operator)` — the same row that
already authorizes the registry call (no scope-widening JPA query).

Design rationale (recorded inline so consumer + producer agree without an ADR):

- **Why nested, not top-level**: a top-level `defaultAccountId` on the finance
  item would be polymorphic — every future per-operator per-product attribute
  (e.g. wms `defaultWarehouseId`, scm `defaultNodeId`, erp
  `defaultDepartmentId`) would need its own top-level field. The nested
  `operatorContext` object scales without per-product item shape divergence:
  each product item carries the same field name with a per-product attribute
  shape.
- **Why omitted, not `null`**: the consumer-side contract
  ([`console-integration-contract.md § 2.2`](../../../../platform-console/specs/contracts/console-integration-contract.md))
  reads each item as a typed shape; an absent field is unambiguously "no
  attribute set", while a literal `null` would force every consumer to treat
  the field as `null \| {…}` explicitly. Jackson
  `@JsonInclude(JsonInclude.Include.NON_NULL)` enforces omission at the
  serializer.
- **Why IAM-side storage (not finance-side)**: this is operator profile
  data, not finance domain data. `admin_operators` is the existing operator
  profile authority (`tenant_id` is already there per ADR-002). A new column
  `finance_default_account_id VARCHAR(36) NULL` extends the same row with a
  single nullable scalar — minimal blast radius, no cross-service call.
- **Why no validation against finance-platform**: IAM carries the value as
  opaque (`VARCHAR(36)`, non-empty when set). A stale account id surfaces
  honestly on the eventual cross-domain BFF call as a finance `404
  ACCOUNT_NOT_FOUND` — the console then renders the affected card as
  `degraded` with the producer error, preserving the IAM↔finance
  non-coupling invariant.

Per-product emission rule (v1):

| product | emits `operatorContext` | source | when |
|---|---|---|---|
| `iam` | no | — | always omitted |
| `wms` | no | — | always omitted |
| `scm` | no | — | always omitted |
| `erp` | no | — | always omitted |
| `finance` | yes (`{ defaultAccountId }`) | `admin_operators.finance_default_account_id` | when the column is non-null + non-empty after trim; omitted otherwise |
| `ecommerce` | no | — | always omitted |

The schema reserves `operatorContext` for future per-operator per-product
attributes; the v1 producer surface populates only the `finance` product item.
The other 4 products may begin populating it in future tasks without breaking
the v1 consumer.

### Product catalog (static, registry-driven)

The 6 product keys form a fixed catalog (ADR-MONO-013 federated domains +
ADR-MONO-030 ecommerce marketplace). `available` is derived:

| productKey | displayName | available rule |
|---|---|---|
| `iam` | Identity & Access Management | always `true` (this platform is live) |
| `wms` | Warehouse Management System | `true` (bootstrapped — V0010/V0016 seeds) |
| `scm` | Supply Chain Management | `true` (bootstrapped — V0013/V0015 seeds) |
| `erp` | Enterprise Resource Planning | `true` (V1 live per ADR-MONO-013 § D6 Phase 6 COMPLETE 2026-05-20 — ADR-MONO-016 ACCEPTED + ERP-BE-001 masterdata-service + ERP-BE-002 platform-console consumer reconciliation; flipped from `false` by TASK-BE-305 2026-05-21 reality-alignment) |
| `finance` | Finance | `true` (V1 live per ADR-MONO-013 § D6 Phase 5 COMPLETE 2026-05-19/20 — ADR-MONO-008 ACCEPTED + FIN-BE-001 account-service + FIN-BE-005 platform-console consumer reconciliation; flipped from `false` by TASK-BE-305 2026-05-21 reality-alignment) |
| `ecommerce` | E-Commerce Marketplace | `true` (V1 live per ADR-MONO-030 ACCEPTED — multi-vendor marketplace SaaS; bound subscription-driven like wms/scm/erp/finance via `tenant_domain_subscription` `domain_key='ecommerce'` self-seed V0022, TASK-MONO-240 2026-06-13) |

**Render is data-driven (0-change); membership enum is an explicit extension.**
Flipping `available` / changing `displayName` / changing `tenants` of an
**existing** catalog member is a **registry change only** — zero `console-web`
code change (console-integration-contract § 2.2 / ADR-MONO-013 § 1.2 / D5): the
console renders the dynamic product list verbatim. **Adding a NEW `productKey`,
however, requires a one-line `console-web` `ProductKeySchema` Zod enum
extension** — the deliberate fixed-membership guard asserted by
`registry-contract.test.ts` "rejects unknown productKey". A registry item whose
`productKey` is absent from that enum makes `RegistryResponseSchema.parse` throw
→ the whole catalog renders `degraded` (not a crash). So the producer-side
catalog addition and the consumer-side enum extension must land in the **same
atomic PR** (TASK-MONO-240 — ADR-MONO-030 § 6 factual correction). All 6
federated domains (`iam` + `wms` + `scm` + `erp` + `finance` + `ecommerce`) are
now V1 live; the `available` flag is `true` across the catalog and the console
renders each tile as interactive (subject to per-operator `tenants` selection
per § Tenant selection rule).

### Tenant selection rule

`tenants` per available product is the intersection of:

1. the product's tenant binding — `iam` binds to **all** registered tenants
   (the platform itself federates them); each of `wms` / `scm` / `erp` /
   `finance` binds to the tenants that hold an **ACTIVE subscription** to its
   `domain_key` (TASK-BE-322 / ADR-MONO-019 D2/D4 — see below);
2. the **operator's tenant scope** — `'*'` (platform) ⇒ all; a single
   tenant slug ⇒ only that slug;
3. the tenant being **registered + ACTIVE** in `tenants` (account-service
   owned; read via `ListTenantsUseCase`). A SUSPENDED or unregistered tenant
   is excluded.

An unavailable product always has `tenants: []` regardless of operator scope.

#### Subscription-driven domain binding (TASK-BE-322 — ADR-MONO-019 D2/D4)

The domain product binding in (1) is derived from the **ACTIVE tenant↔domain
subscriptions** that IAM account-service owns (ADR-019 **D2** entitlement
authority), read by admin-service via the internal subscription surface
([`internal/account-tenant-domain-subscriptions.md`](internal/account-tenant-domain-subscriptions.md))
and projected here (ADR-019 **D4**). This **replaces** the prior fixed
`tenantSlug == domain` binding; the `iam` `bindsAllTenants` branch is unchanged
(it never consults subscriptions). The operator-scope intersection (2) and the
registered+ACTIVE intersection (3) are unchanged and still apply on top.

**Net-zero (ADR-019 step 1)**: a backward-compatible seed has each domain-slug
tenant self-subscribe (`(wms,wms)`, `(scm,scm)`, `(erp,erp)`, `(finance,finance)`,
and `(ecommerce,ecommerce)` per V0022 / TASK-MONO-240),
so `subscriptions(domain_key) ∩ activeTenants` reproduces the legacy slug
binding **byte-identically**. The response envelope, item shape, and values are
unchanged in step 1; real customer-tenant subscriptions surface in a later step
without an envelope change.

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
- `iam` product's `tenants` for a single-tenant operator is `["<own>"]`
  (length ≤ 1) — it is **not** the full tenant list.
- **`operatorContext` (TASK-BE-304) carries no cross-tenant data**: the
  `admin_operators.finance_default_account_id` column is read from the
  calling operator's own row via `resolveOperator(operator.operatorId())` —
  the same per-operator lookup that already authorizes the registry call. No
  scope-widening JPA query is introduced; another operator's
  `finance_default_account_id` is never reachable on any response. (Asserted
  in `ConsoleRegistryIntegrationTest`.)

---

## Change Rule

1. Endpoint path / auth / response envelope changes update **this file**
   before implementation.
2. The item shape (`productKey` / `displayName` / `available` / `tenants` /
   `baseRoute` / `operatorContext`) is governed by the consumer contract
   (`platform-console/specs/contracts/console-integration-contract.md § 2.2`)
   — a shape change requires updating that file first and an ADR if it alters
   a deployed integration (console-integration-contract § 5).
3. Product catalog membership / `available` rules are documented here and in
   [multi-tenancy.md](../../features/multi-tenancy.md) "Platform Console
   Registry" — keep both in sync in the same PR. **Adding a new `productKey`
   additionally requires the consumer-side `console-web` `ProductKeySchema` Zod
   enum extension** (+ its `registry-contract.test.ts` membership assertion) in
   the same atomic PR — render is data-driven but membership is a fixed-set
   guard (TASK-MONO-240 added `ecommerce`; ADR-MONO-030 § 6 factual correction).
4. Adding a new `operatorContext.*` attribute (TASK-BE-304 extensible carrier)
   is **additive** when (a) it is optional + omitted-by-default and (b) the
   attribute is per-operator + scoped via `resolveOperator(operator.operatorId())`
   (no scope widening). Cross-tenant or cross-operator carriers require a
   separate spec PR and an ADR.
