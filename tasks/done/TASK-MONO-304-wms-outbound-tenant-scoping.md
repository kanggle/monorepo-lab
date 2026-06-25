---
id: TASK-MONO-304
title: "Tenant-scoped wms outbound visibility for customer-tenant operators (ADR-MONO-022 § D9)"
status: done
scope: cross-project
projects: [wms-platform, platform-console]
tags: [code, test, contract, adr, multi-tenant, security, authz]
analysis_model: "Opus 4.8"
impl_model: "Opus 4.8"
created: 2026-06-25
branch: feat/wms-outbound-tenant-scoping
---

# TASK-MONO-304 — Tenant-scoped wms outbound visibility

## Goal

Once the ecommerce tenant is subscribed to the wms domain (its operators' tokens carry
`wms` in `entitled_domains`), an ecommerce operator can open the console wms-outbound
surface but currently sees **every** tenant's outbound orders — wms filters only on role,
never on the order's `tenant_id`. Make the outbound read **and** mutation surface
**tenant-scoped** so a customer-tenant operator sees only **their own tenant's
`FULFILLMENT_ECOMMERCE` orders**, and is blocked (`403`) from any other tenant's order.
Native wms / platform operators keep full visibility. Records the decision as
ADR-MONO-022 **§ D9**.

## Scope

In scope (wms-platform `outbound-service`):
- Resolve the caller's tenant scope from the SIGNED `tenant_id` claim (never a client
  header): native wms (`=wms`) / platform (`=*`) / no-context → unrestricted; any other
  (customer) tenant → restricted to that tenant.
- List (`GET /orders`): force `tenantId=<caller>` + `source=FULFILLMENT_ECOMMERCE` for a
  restricted caller (override client `source`).
- Single-order read + every mutation: `403 TENANT_SCOPE_DENIED` when the order's
  `tenant_id` ≠ caller tenant (incl. B2B / `null`-tenant orders).
- Contracts: outbound-service-api.md (error code + tenant-scoping section),
  console-integration-contract.md § 2.4.5.1, ADR-MONO-022 § D9.

Out of scope:
- No console-web code change — the console already forwards the assumed tenant-scoped
  token (`getDomainFacingToken()`); the existing `403 → inline "not available"` state
  (outbound-state.ts) covers the new code.
- No new JWT claim, no `X-Tenant-Id` header, no schema change (`orders.tenant_id` already
  exists from TASK-MONO-296 / facet d).
- wms native single-tenant behaviour unchanged; event-wire `tenant_id` stays opaque.

## Acceptance Criteria

- AC-1 A native wms operator (`tenant_id=wms`) and a platform operator (`*`) see all
  outbound orders (list + single-order + mutations) exactly as before.
- AC-2 An ecommerce-scoped operator's `GET /orders` returns ONLY orders with
  `tenant_id=ecommerce` AND `source=FULFILLMENT_ECOMMERCE` (client-supplied `source`
  ignored), with a correct `total`.
- AC-3 An ecommerce-scoped operator reading/mutating a foreign-tenant or B2B/`null`-tenant
  order (get, saga, picking-requests, cancel, pick-confirm, packing create/seal/confirm,
  shipping confirm, TMS retry) receives `403 TENANT_SCOPE_DENIED`.
- AC-4 An ecommerce-scoped operator reading/mutating its OWN tenant's order succeeds
  (subject to the existing role/state gates).
- AC-5 Scope is derived solely from the signed `tenant_id` claim — no client header can
  widen or change it.
- AC-6 Internal flows with no security context (Kafka consumers) are unrestricted (no
  regression in the fulfillment intake / return-leg consumers).
- AC-7 Contracts + ADR-MONO-022 § D9 updated; build green (unit + repo-filter IT).

## Related Specs

- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4.5 / § 2.4.5.1
- `projects/wms-platform/specs/services/outbound-service/architecture.md` (app-layer authz)

## Related Contracts

- `projects/wms-platform/specs/contracts/http/outbound-service-api.md`
  (§ Authorization → Tenant scoping; error code `TENANT_SCOPE_DENIED`)
- `docs/adr/ADR-MONO-022-ecommerce-wms-fulfillment-integration.md` § D9

## Edge Cases

- `tenant_id` claim absent/blank → unrestricted (defensive; the resource-server tenant
  validator already requires `tenant_id`=wms OR `entitled_domains` ∋ wms, so a bare token
  never reaches the app layer).
- Platform wildcard `*` → unrestricted (filtering on `*` would match nothing).
- Foreign-tenant order existence is hidden behind `403` (not `404`) — explicit user policy.
- `orders.tenant_id` is populated only for `FULFILLMENT_ECOMMERCE` orders, so a restricted
  caller can never match a B2B/null-tenant row (isolation key invariant).

## Failure Scenarios

- A restricted caller crafts a `?source=MANUAL` (or any) list filter → overridden to
  `FULFILLMENT_ECOMMERCE`; no cross-tenant leak.
- A restricted caller pages/looks up an order id belonging to another tenant → `403`,
  no state mutation, no outbox write (guard fires before role/version checks and writes).
- Security-context-less internal consumer path → unrestricted (must not start denying
  fulfillment intake / return-leg processing).

## Implementation Notes

- New: `CallerScopeProvider` (out-port) + `SecurityContextCallerScopeProvider` (adapter,
  reads `SecurityContextHolder`) + `CallerScope` (VO: `scopeListQuery`, `requireOrderAccess`)
  + `TenantScopeDeniedException` (→ 403 `TENANT_SCOPE_DENIED` via `GlobalExceptionHandler`).
- `OrderQueryCommand` gains a `tenantId` filter field (server-set only) +
  `OrderJpaRepository.findFiltered/countFiltered` gain `:tenantId` guard.
- Guards inserted in `OrderQueryService` (list scope + findById) and every mutation service
  (cancel, confirm-picking, packing create/seal/confirm, confirm-shipping,
  retry-tms helper) right after the owning order is loaded.
- Tests: `CallerScopeTest`, `SecurityContextCallerScopeProviderTest`, query-scope +
  mutation `403` cases (`OrderQueryServiceTest`, `CancelOrderServiceTest`),
  `OrderJpaRepositoryFilterIT` tenant-filter case. `FakeCallerScopeProvider` for service
  unit tests (defaults unrestricted → existing tests unaffected).
- Prerequisite (separate branch `feat/ecommerce-wms-subscription`): the ecommerce→wms
  domain subscription (V0025) so an ecommerce operator's token is wms-entitled at all.
