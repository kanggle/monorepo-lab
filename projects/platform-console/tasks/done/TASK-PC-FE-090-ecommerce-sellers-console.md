# TASK-PC-FE-090 — console ecommerce **sellers** operator surface (ADR-031 §2.4.10 — 7th area)

**Status:** done
**Area:** platform-console / console-web · **Feature:** `features/ecommerce-ops` (sellers slice)
**Parent:** ADR-MONO-030 Step 4 facet f (marketplace seller operator surface) · ADR-MONO-031 §2.4.10 console-absorption pattern (7th operator area — net-new, no admin-dashboard parity).
**Precondition:** TASK-BE-375 (seller admin read surface: `GET /api/admin/sellers` list + `GET /{id}` detail).

## Goal

Add a **seller management** operator surface to platform-console `features/ecommerce-ops`, the 7th ecommerce
operator area. Mirrors the established slice pattern (products/promotions). MVP = **list + detail + register**
(no deactivate/suspend — ADR-030 v1; the backend exposes only those three).

## Authoritative producer surface (3 endpoints)

All under **`ECOMMERCE_ADMIN_BASE_URL` (`http://ecommerce.local/api/admin`) + `/sellers`** (i.e. `/api/admin/sellers/**`,
the **admin** path — same as products/orders/users, NOT the PUBLIC base that promotions/notifications use). All admin-guarded.

| # | Method | Path | Purpose |
|---|--------|------|---------|
| 1 | `GET` | `/api/admin/sellers?page=&size=` | paginated list (rows: `sellerId, displayName, status, createdAt`) |
| 2 | `GET` | `/api/admin/sellers/{sellerId}` | seller detail (full); missing/cross-tenant → 404 |
| 3 | `POST` | `/api/admin/sellers` | register; body `{sellerId, displayName}` → 201 `{sellerId}` |

`status` is `ACTIVE` only (v1). **No update, no delete** (producer defines none).

## Scope — mirror the promotions slice

Reference (read first): the `features/ecommerce-ops` **promotions** slice (list + create + edit form) and **users**
slice (uses `ECOMMERCE_ADMIN_BASE_URL`). Sellers = list + detail + register (like promotions minus edit/delete).

Create under `projects/platform-console/apps/console-web/src/features/ecommerce-ops/`:
- `api/seller-types.ts` — Zod: `SellerSummary` (list row), `SellerDetail` (full), `SELLER_STATUS_VALUES` (`ACTIVE`), register body schema (`sellerId` ≤64 non-blank, `displayName` non-blank).
- `api/sellers-api.ts` — `listSellers(params)`, `getSeller(id)`, `registerSeller(body)`. `getDomainFacingToken()`
  (**never** `getOperatorToken()`), **`ECOMMERCE_ADMIN_BASE_URL + /sellers`**, flat error envelope `{code,message,timestamp}`,
  **no** `X-Tenant-Id`, **no** `Idempotency-Key`. Mirror `users-api.ts`/`promotions-api.ts` resilience/inline-error handling.
- `api/sellers-state.ts` — server-side section state loader (eligibility waterfall; mirror `users-state.ts`).
- `hooks/use-ecommerce-sellers.ts` — list query + getSeller query (for detail) + register mutation (invalidate list on success).
- `components/SellersScreen.tsx` — list table (sellerId, displayName, status badge, createdAt), pagination, "셀러 등록" link, row → detail.
- `components/SellerRegisterForm.tsx` — `sellerId` + `displayName` inputs, confirm-gate on submit (mirror PromotionForm create mode).
- (optional) `components/SellerDetail.tsx` — read-only detail view (sellerId, displayName, status, createdAt/updatedAt).

Route handlers (Next.js, **direct to ecommerce gateway, no console-bff write leg** — ADR-017 D2.A) under
`src/app/api/ecommerce/sellers/`:
- `route.ts` — `GET` (list) + `POST` (register).
- `[id]/route.ts` — `GET` (detail).

Pages under `src/app/(console)/ecommerce/sellers/`:
- `page.tsx` — list (eligibility waterfall: registryDegraded → notEligible → forbidden → degraded → happy; mirror users/page.tsx).
- `new/page.tsx` — register.
- `[id]/page.tsx` — detail (loads via GET, 404 → notFound). (Optional if you inline detail; prefer a page for parity.)

Sidebar: add a `셀러` leaf to the ecommerce `NavParent` children in `src/shared/ui/ConsoleSidebarNav.tsx`
(after the `알림` leaf): `{ href: '/ecommerce/sellers', label: '셀러', testid: 'nav-ecommerce-sellers' }`.

Contract: add **§2.4.10.5 (sellers)** to `projects/platform-console/specs/contracts/console-integration-contract.md`,
after §2.4.10.4 (notifications). Follow the §2.4.10.x structure: opening (the 7th operator area, ADR-030 facet f seller
axis, unblocked by TASK-BE-375; uses the **admin** base unlike promotions/notifications), inherits §2.4.10 cross-cutting
verbatim, the 3-endpoint producer table, status=ACTIVE-only + no-update/delete note, "Producer immutability", "Not a §3 parity row".

## Out of scope
- No update/delete/deactivate (backend defines none — ADR-030 v1).
- No backend change (BE-375 precondition, separate task/PR).
- `getOperatorToken()`, `X-Tenant-Id` header, `Idempotency-Key`, console-bff write leg — forbidden (§2.4.10).

## Acceptance Criteria
- `tsc --noEmit` → 0 errors.
- `pnpm --filter console-web lint` → clean (no-unused-vars etc. — **mandatory**; CI fails the two frontend jobs otherwise).
- vitest → all green incl. new tests (mirror users/promotions slice coverage: credential pin = getDomainFacingToken
  not getOperatorToken; **base URL = ECOMMERCE_ADMIN_BASE_URL**; resilience 401/403/503; register validation; detail 404).
- Sidebar `셀러` leaf present; pages render the eligibility waterfall; detail page 404s missing.
- Contract §2.4.10.5 added.
- products/orders/users/promotions/image/shippings/notifications slices, console-bff, backend: **0-change**.

## Related Specs / Contracts
- `docs/adr/ADR-MONO-030-ecommerce-multivendor-marketplace-saas.md` (Step 3 §3.1 / Step 4 facet f)
- `console-integration-contract.md` §2.4.10 (cross-cutting) + new §2.4.10.5
- Producer: `product-service` `AdminSellerController` (`/api/admin/sellers`) — list / detail / register.

## Edge Cases
- Register duplicate `sellerId` within tenant → producer 409/400 → inline.
- Cross-tenant/missing `{sellerId}` on detail → 404 → notFound.
- The per-tenant `default` seller appears in the list (real ACTIVE row) — fine.
- 401 → whole-session IAM re-login; 403 → "not available to your role"; 503/timeout → only this section degrades.

## Failure Scenarios
- Using `ECOMMERCE_PUBLIC_BASE_URL` instead of `ECOMMERCE_ADMIN_BASE_URL` → 404 (sellers live under `/api/admin/sellers`, the admin subtree — unlike promotions/notifications).
- Skipping `pnpm lint` before push → CI "Frontend lint & build" + "Frontend unit tests" RED.
