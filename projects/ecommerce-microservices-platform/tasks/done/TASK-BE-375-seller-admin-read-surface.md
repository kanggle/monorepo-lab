# TASK-BE-375 — seller admin read surface (list + detail) — ADR-030 Step 4 facet f

**Status:** done
**Domain:** ecommerce · **Service:** product-service · **Type:** backend API gap-fill (operator read surface)
**Parent:** ADR-MONO-030 Step 3 §3.1 (seller inner axis) / Step 4 facet f (seller operator surface). Unblocks the
platform-console seller operator slice (separate console task, ADR-031 §2.4.10 absorption pattern — 7th operator area).

> **Task number:** BE-375 (global TASK-BE counter; iam-platform holds 370–374, ecommerce's previous was BE-373).

## Goal

Fill the seller **read** gap so the marketplace seller operator surface can be driven from platform-console.
`AdminSellerController` today exposes only `POST /api/admin/sellers` (register). A useful operator surface needs
**list** and **detail** reads — this task adds them, **tenant-scoped, admin-guarded, no new migration**.

**MVP scope (user-approved option A):** list + detail + (existing) register only. **No** deactivate / suspend /
status-transition / onboarding / settlement — those remain ADR-030 Step 4 out-of-scope (the `Seller` v1 lifecycle
is ACTIVE-only by design; do not add write/lifecycle endpoints).

**Authz = promotions-exact:** keep `validateAdminRole(X-User-Role==ADMIN)` on the new reads (the controller already
uses it for POST). `/api/admin/sellers/**` routes through the gateway `/api/admin/**` OPERATOR branch — **no gateway change.**

## Scope

The `Seller` aggregate is **already tenant-scoped** — `SellerRepositoryImpl` filters via `TenantContext`
(`findByTenantIdAndSellerId`), V14 added the seller axis. So this is purely additive read endpoints; **no Flyway
migration, no tenant-column work.** Reference: the notification BE-373 `GET /templates/{id}` gap-fill (same shape),
and the existing `AdminSellerController` + `SellerScopeIsolationIntegrationTest`.

### Endpoints (add to `AdminSellerController`, `/api/admin/sellers`)
- `GET /api/admin/sellers?page=&size=` — paginated list of sellers **in the current tenant**, admin-guarded.
  Response row: `sellerId, displayName, status, createdAt` (+ `updatedAt` if cheap). Newest-first or sellerId order.
- `GET /api/admin/sellers/{sellerId}` — single seller **detail** in the current tenant, admin-guarded. Cross-tenant
  or missing → **404** (a `SellerNotFoundException` or the project's standard not-found → 404 mapping).

### Layer work
- `SellerRepository` (domain port) — add `findAll(PageQuery)` (tenant-scoped via the impl's `TenantContext`), returning `PageResult<Seller>`.
- `SellerRepositoryImpl` — implement `findAll` via a new `SellerJpaRepository.findByTenantId(tenantId, pageable)` (paged).
- A `SellerQueryService` (or extend an existing query service) — `listSellers(PageQuery)` + `getSeller(sellerId)`
  (404 on absent). Mirror the project's existing admin query-service style (e.g. AdminUser/AdminProduct read services).
- Response DTOs: `SellerListResponse` (page envelope + summary rows) + `SellerResponse`/`SellerDetailResponse`. Mirror
  the project's existing list/detail response shape (`PageResult` → response).
- Controller: 2 new `@GetMapping` on `AdminSellerController`, each calling `validateAdminRole` first.

### M6 — cross-tenant isolation test
Extend `SellerScopeIsolationIntegrationTest` (or add to it): seed sellers in two tenants; assert (a) the list under
tenant A excludes tenant B's sellers; (b) `GET /{id}` for tenant B's seller under tenant A context → 404. Tag
`@Tag("integration")` per the project convention (won't run locally — host Docker blocker; CI Linux authority).

### Out of scope / unchanged
- No migration (seller already tenant-scoped, V14). No gateway change. No seller write/lifecycle endpoints (Step 4).
- `RegisterSellerService` / POST register — unchanged. `Seller` domain — unchanged (read-only additions).

## Acceptance Criteria
- `./gradlew :product-service:check` GREEN (Docker-free; `@Tag("integration")` excluded by default; `--rerun-tasks` if a Mockito ctor change leaves a stale `:test` cache).
- `GET /api/admin/sellers` returns the current tenant's sellers paged (admin); non-admin → 403 (existing `validateAdminRole`).
- `GET /api/admin/sellers/{id}` returns detail; cross-tenant/missing → 404.
- New repo `findAll` is tenant-scoped (no cross-tenant leak); M6 test compiles and asserts isolation.
- All existing seller tests still pass.

## Related Specs
- `docs/adr/ADR-MONO-030-ecommerce-multivendor-marketplace-saas.md` (Step 3 §3.1 seller axis; Step 4 facet f)
- `projects/ecommerce-microservices-platform/specs/services/product-service/architecture.md`

## Related Contracts
- No contract change here (backend-only). The console binding §2.4.10.5 lands in the Phase B console task. The new
  read endpoints are additive to the existing `/api/admin/sellers` surface.

## Edge Cases
- Empty tenant (no sellers beyond the per-tenant `default`) → list returns the default seller (and any registered). Page past end → empty page, 200.
- Cross-tenant `{sellerId}` → 404 (not 403), mirroring BE-373/BE-367 M3.
- The per-tenant `default` seller appears in the list (it is a real ACTIVE seller row) — acceptable.

## Failure Scenarios
- If `findAll` is not tenant-scoped → cross-tenant seller leak. Guard: scope via `TenantContext` in the impl (mirror `findById`), M6 assertion (a).
- If detail uses an un-scoped lookup → cross-tenant read. Guard: reuse the tenant-scoped `findById`.
- Adding any write/lifecycle endpoint → scope creep beyond ADR-030 v1; keep reads-only.
