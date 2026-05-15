# Console ↔ Domain Integration Contract

> The contract every product must satisfy to be federated by `platform-console`.
> Authoritative skeleton: [ADR-MONO-013](../../../../docs/adr/ADR-MONO-013-platform-console-foundation.md) § D5. This document is the full form.
> Status: **v1 skeleton** — element shapes are normative; concrete per-domain endpoint schemas are added as each domain section is built (ADR-MONO-013 Phase 2/4/5/6).

---

## 1. Scope

`platform-console` is **Model B** (ADR-MONO-013 D1): the console is the single UI and renders each domain's operational screens by calling that domain's gateway/admin REST API. This contract defines the five integration elements a domain must provide. It does **not** define domain business APIs — those live in each domain's own `specs/contracts/`.

---

## 2. Contract Elements

### 2.1 Identity (OIDC)

- The console is a GAP OIDC **public client** (`platform-console-web`), Authorization Code + PKCE.
- One operator login covers all federated domains (SSO). Access token carries `tenant_id`.
- Tokens are held in **HttpOnly cookies only** (per `platform/service-types/frontend-app.md`); refresh via a server route.
- GAP-side registration is a GAP project-internal prerequisite — `TASK-BE-296`.

### 2.2 Product / Tenant Registry (catalog source)

- GAP exposes a registry surface the console reads to build the **data-driven** catalog.
- Minimum item shape (normative):

| Field | Type | Meaning |
|---|---|---|
| `productKey` | string | `gap` \| `wms` \| `scm` \| `erp` \| `finance` |
| `displayName` | string | Catalog tile label |
| `available` | boolean | `false` → rendered as "coming soon" (e.g. `erp`/`finance` pre-bootstrap) |
| `tenants` | string[] | Tenant ids the operator may select for this product |
| `baseRoute` | string | Console-internal route prefix for the product's screens |

- Adding a product (e.g. finance) or flipping `available` is a **registry change only** — zero `console-web` code change (ADR-MONO-013 § 1.2 / D5).

### 2.3 Routing

- Each domain is reachable at its Traefik hostname (`gap.local`, `wms.local`, `scm.local`, … ; console at `console.local`).
- The console reaches domains **server-side** (server components / server routes), never via browser-direct calls that bypass the typed API client.

### 2.4 Console-facing API surface (per domain)

- Each domain's gateway/admin service exposes the read/ops endpoints the console renders. These are declared per-domain in that domain's `specs/contracts/` and cross-referenced from the console's `specs/services/console-web/` when the domain section is built.
- All calls are **tenant-scoped**: the console propagates the selected tenant (`X-Tenant-Id` header or equivalent honored by the domain gateway); the domain MUST reject cross-tenant requests.
- Operator mutating actions (e.g. account lock/unlock) MUST be idempotent on the domain side; the console sends an idempotency key and renders the result (it owns no domain transaction — `platform-console` is not `transactional`).

### 2.5 Resilience

- Console/BFF fan-out applies circuit-breaker / retry / timeout per `platform/` baselines (`integration-heavy` trait).
- One domain unavailable MUST degrade only that domain's section — never blank the console shell.

---

## 3. GAP `admin-web` absorption (Phase 3 reference)

The console's GAP section must reach functional parity with the existing GAP `admin-web` operator surface before `admin-web` is retired (ADR-MONO-013 D4, parity-gated). Parity checklist (enumerated at ACCEPTED, ADR-MONO-013 § 6 D7.4):

- accounts: search, detail, lock/unlock, bulk-lock, revoke-session, GDPR-delete, export
- audit: query
- dashboards
- operators: create, edit-roles, change-status, change-password
- security: login-history, suspicious

Retirement itself is a GAP project-internal spec-first change (GAP `PROJECT.md` service map), not a `platform-console` task.

---

## 4. Out of Scope (this contract)

- Domain business logic / domain event contracts (each domain owns these).
- `console-bff` aggregation endpoint shapes (ADR-MONO-013 Phase 7 — added when the BFF lands).
- finance/erp domain contracts (governed by ADR-MONO-008 / future erp ADR).

---

## 5. Change Rule

Changes to the contract elements (§ 2) require updating this file **before** implementation, and — if they alter a deployed integration — an ADR per [`architecture-decision-rule.md`](../../../../platform/architecture-decision-rule.md). The skeleton → full transition (adding concrete per-domain endpoint schemas) is additive and tracked per domain task.
