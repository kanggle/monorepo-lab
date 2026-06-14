# TASK-PC-FE-088 — console ecommerce **shippings** operator surface (ADR-031 Phase 4b)

**Status:** done
**Area:** platform-console / console-web · **Feature:** `features/ecommerce-ops` (shippings slice)
**Parent:** ADR-MONO-031 Phase 4 (absorb ecommerce admin-dashboard shipping surface into the unified console)
**Precondition:** TASK-BE-369 (shipping-service row-level `tenant_id`) — the §2.4.10 tenant-isolation gate.

## Goal

Absorb the ecommerce admin-dashboard **shipping management** operator surface into platform-console
`features/ecommerce-ops`, mirroring the established **orders** slice (status state-machine) + **promotions**
slice (mutation template). After this, an operator manages shipments entirely from the console; the
standalone admin-dashboard shipping page becomes redundant (deleted in Phase 6).

## Authoritative producer surface (3 endpoints — from admin-dashboard parity)

All under the ecommerce gateway, **`ECOMMERCE_PUBLIC_BASE_URL` (`http://ecommerce.local/api`) + `/shippings`**
(i.e. `/api/shippings/**`, the **non-admin** path — same model as promotions, NOT `ECOMMERCE_ADMIN_BASE_URL`):

| # | Method | Path | Purpose |
|---|--------|------|---------|
| 1 | `GET` | `/api/shippings?page=&size=&status=` | paginated list, optional `status` filter (operator, `X-User-Role=ADMIN`) |
| 2 | `PUT` | `/api/shippings/{shippingId}/status` | status transition; body `{status, trackingNumber?, carrier?}` — `trackingNumber`+`carrier` **required** when `status=SHIPPED` |
| 3 | `POST` | `/api/shippings/{shippingId}/refresh-tracking` | operator-triggered carrier sync (empty body), returns the updated shipment |

## Shipping state machine (mirror in UI — from `ShippingStatus.java`)

**Strictly linear, single successor each:** `PREPARING → SHIPPED → IN_TRANSIT → DELIVERED`.
`DELIVERED` is terminal. The UI must expose only the one allowed forward transition per current status
(no skips, no backward). The `PREPARING → SHIPPED` transition opens a form requiring **carrier + tracking
number** (producer rejects SHIPPED without them — surface inline). All other transitions are confirm-gated only.

## Scope — mirror the orders + promotions slices

Reference (read first, copy the structure exactly): the existing `features/ecommerce-ops` **orders**
slice (OrdersScreen/OrderDetail/OrderStatusDialog + orders-api/order-types/orders-state/use-ecommerce-orders
+ route handlers + pages) and **promotions** slice (PromotionsScreen + CouponIssueDialog mutation dialog +
promotions-api PUT/POST + route handlers). Shipping is **list-centric** like the admin-dashboard original
(list with per-row actions); a detail page is **optional** — prefer parity with admin-dashboard (list-only
with inline action dialogs) unless a detail view with status-history is cheap to add.

Create under `projects/platform-console/apps/console-web/src/features/ecommerce-ops/`:
- `api/shippings-api.ts` — `listShippings(params)`, `updateShippingStatus(id, body)`, `refreshTracking(id)`.
  Uses `getDomainFacingToken()` (**never** `getOperatorToken()`), `ECOMMERCE_PUBLIC_BASE_URL + /shippings`,
  flat error envelope `{code,message,timestamp}`, **no** `X-Tenant-Id` header (JWT claim), **no** `Idempotency-Key`.
  Mirror `promotions-api.ts` resilience/inline-error handling. (Add `ECOMMERCE_PUBLIC_BASE_URL` is already in `env.ts`.)
- `api/shipping-types.ts` — Zod schemas: `Shipping` (shippingId, orderId, userId, status, trackingNumber?,
  carrier?, statusHistory[], createdAt, updatedAt), `ShippingSummary` (list row), `SHIPPING_STATUS_VALUES`
  (`PREPARING|SHIPPED|IN_TRANSIT|DELIVERED`), an `allowedNextStatus(current)` helper encoding the linear machine.
- `api/shippings-state.ts` — server-side section state loader (mirror `orders-state.ts`).
- `hooks/use-ecommerce-shippings.ts` — list query + `useUpdateShippingStatus` + `useRefreshTracking` mutations
  (invalidate the list key on success).
- `components/ShippingsScreen.tsx` — list table (orderId, status badge, carrier, trackingNumber, createdAt,
  actions), status filter dropdown, pagination. Per-row: a status-transition action (confirm-gate; SHIPPED →
  the ship-form dialog) + a refresh-tracking button. A shared pending guard prevents double-submit across both
  mutations (mirror admin-dashboard `isAnyPending`).
- `components/ShipFormDialog.tsx` — carrier + trackingNumber inputs, shown when transitioning to SHIPPED
  (mirror promotions `CouponIssueDialog` / orders `OrderStatusDialog` dialog shape).
- (optional) `components/ShippingDetail.tsx` if a detail page is added.

Route handlers (Next.js, **direct to ecommerce gateway, no console-bff write leg** — ADR-017 D2.A) under
`src/app/api/ecommerce/shippings/`:
- `route.ts` — `GET` (list, query passthrough).
- `[id]/status/route.ts` — `PUT`.
- `[id]/refresh-tracking/route.ts` — `POST`.

Pages under `src/app/(console)/ecommerce/shippings/`:
- `page.tsx` — list (eligibility waterfall: registryDegraded → notEligible → forbidden → degraded → happy;
  mirror `orders/page.tsx`). (+ `[id]/page.tsx` only if a detail view is added.)

Sidebar: add a `배송` leaf to the ecommerce `NavParent` children in
`src/shared/ui/ConsoleSidebarNav.tsx` (after the `프로모션` leaf):
`{ href: '/ecommerce/shippings', label: '배송', testid: 'nav-ecommerce-shippings' }`.

Contract: add **§2.4.10.3 (shippings)** to `projects/platform-console/specs/contracts/console-integration-contract.md`,
immediately after §2.4.10.2. Follow the §2.4.10.2 (promotions) structure: opening (unblocked by TASK-BE-369
shipping `tenant_id`, ADR-030 Step 4), "inherits §2.4.10 cross-cutting rules verbatim" list, the 3-endpoint
authoritative-producer table, state-gated mutation note (linear machine; SHIPPED requires carrier+tracking;
refresh-tracking best-effort), out-of-scope/deferred, "Producer immutability", "Not a §3 parity row".

## Out of scope
- No create/delete of shipments from the console (shipments are created by the OrderConfirmed flow).
- No backend change (BE-369 is the precondition, separate task/PR).
- `getOperatorToken()`, `X-Tenant-Id` header, `Idempotency-Key`, console-bff write leg — all forbidden (§2.4.10).

## Acceptance Criteria
- `pnpm --filter console-web tsc --noEmit` (or repo's tsc script) → 0 errors.
- `pnpm --filter console-web lint` → clean (no-unused-vars etc. — **mandatory**, CI fails the two frontend jobs otherwise).
- `pnpm --filter console-web test` (vitest) → all green, including new tests for the shippings api/hooks/screen
  (mirror the orders/promotions slice test coverage: credential pin = getDomainFacingToken not getOperatorToken;
  resilience 401/403/503; linear state-machine guard; SHIPPED requires carrier+tracking).
- Sidebar `배송` leaf present; pages render the eligibility waterfall.
- Contract §2.4.10.3 added.
- products/orders/users/promotions/image slices, console-bff, backend: **0-change**.

## Related Specs / Contracts
- `docs/adr/ADR-MONO-031-ecommerce-admin-console-consolidation.md` (Phase 4)
- `console-integration-contract.md` §2.4.10 (cross-cutting) + new §2.4.10.3
- Producer: `shipping-service` `ShippingController` (`/api/shippings`) — list / status / refresh-tracking.

## Edge Cases
- Cross-tenant shipment → producer 404 (BE-369 M3) → inline "not found".
- SHIPPED without carrier/tracking → producer 400 (InvalidShipping) → inline field error.
- Illegal transition (e.g. PREPARING→DELIVERED) → producer 409/422 → inline; UI shouldn't offer it anyway.
- refresh-tracking when carrier mode=mock or carrier outage → 200 unchanged status (best-effort, no error).
- 401 → whole-session IAM re-login; 403 → "not available to your role"; 503/timeout → only this section degrades.

## Failure Scenarios
- Using `ECOMMERCE_ADMIN_BASE_URL` (`/api/admin`) instead of `ECOMMERCE_PUBLIC_BASE_URL` → 404 (no
  `/api/admin/shippings` route exists). Shipping lives at `/api/shippings` (promotions model).
- Offering a non-linear transition → producer rejects; keep the UI gated to `allowedNextStatus`.
- Skipping `pnpm lint` before push → CI "Frontend lint & build" + "Frontend unit tests" RED.
