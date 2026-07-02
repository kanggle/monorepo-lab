# TASK-PC-FE-154 — console ecommerce **seller lifecycle actions** (provision / suspend / close)

**Status:** review
**Area:** platform-console / console-web · **Feature:** `features/ecommerce-ops` (sellers slice)
**Parent:** ADR-MONO-042 (seller onboarding + lifecycle: `PENDING_PROVISIONING → ACTIVE`, `→ SUSPENDED`, `→ CLOSED`) · ADR-MONO-031 §2.4.10 console-absorption pattern (7th operator area).
**Precondition:** TASK-PC-FE-090 (seller list + detail + register — DONE). Producer already ships the lifecycle endpoints (`AdminSellerController` provision/suspend/close, ADR-042 D3/D4).

## Goal

Extend the console seller surface (TASK-PC-FE-090's read+register MVP) with the **operator lifecycle actions**
the producer already exposes but the console never wired: **provision** (retry a `PENDING_PROVISIONING` seller),
**suspend** (`ACTIVE → SUSPENDED` + lock backing account), **close** (`→ CLOSED` terminal + deactivate account).
This resolves the user-visible gap "셀러 등록은 되는데 수정/삭제가 없음" — the seller domain has **no** update/delete
(CRUD); its lifecycle is modelled as **state transitions**, so exposing provision/suspend/close IS the "수정/삭제"
equivalent. Read-only MVP said "status=ACTIVE only / no suspend"; that was a deliberate v1 scope, now lifted.

## Authoritative producer surface (already live — do NOT redefine)

All under **`ECOMMERCE_ADMIN_BASE_URL` (`http://ecommerce.local/api/admin`) + `/sellers`** (admin subtree, ADMIN-guarded).
Existing 3 (PC-FE-090) + the 3 lifecycle endpoints this task consumes:

| # | Method | Path | Purpose | Response |
|---|--------|------|---------|----------|
| 4 | `POST` | `/api/admin/sellers/{sellerId}/provision` | re-provision a `PENDING_PROVISIONING` seller (idempotent; already-ACTIVE = no-op) | 204 / 404 |
| 5 | `POST` | `/api/admin/sellers/{sellerId}/suspend` | `ACTIVE → SUSPENDED` + lock account (idempotent, null-safe) | 204 / 404 |
| 6 | `POST` | `/api/admin/sellers/{sellerId}/close` | `→ CLOSED` terminal + deactivate account (idempotent, null-safe) | 204 / 404 |

Seller statuses: `PENDING_PROVISIONING`, `ACTIVE`, `SUSPENDED`, `CLOSED`. Flat error envelope
`{ code, message, timestamp }` (403 `ACCESS_DENIED`, 404 `SELLER_NOT_FOUND`). No body on any lifecycle POST.
No re-activation path (`SUSPENDED → ACTIVE` is NOT a producer endpoint — provision only targets PENDING).

## Scope — mirror the products delete/mutation slice

Reference (read first): `ProductDetail.tsx` (confirm-gated `useDeleteProduct` via `ConfirmDialog`) + the ecommerce
product `[id]` DELETE proxy route (returns `204`) + `callEcommerce(..., undefined, ...)` void/204 handling.

Under `projects/platform-console/apps/console-web/src/`:
- `features/ecommerce-ops/api/seller-types.ts` — expand `SELLER_STATUS_VALUES` to the full lifecycle set; add
  `sellerStatusTone(status)` → `{ label, className }` badge helper (green ACTIVE / amber PENDING / gray SUSPENDED / red CLOSED / neutral unknown).
- `features/ecommerce-ops/api/sellers-api.ts` — add `provisionSeller(id)`, `suspendSeller(id)`, `closeSeller(id)` →
  `Promise<void>` (POST, no body, `callEcommerce(..., undefined, SELLER_LABEL)`).
- `app/api/ecommerce/sellers/[id]/provision/route.ts`, `.../suspend/route.ts`, `.../close/route.ts` — POST proxy
  handlers: call the api fn → `new NextResponse(null, { status: 204 })`; `mapEcommerceError(err, requestId)` on failure.
- `features/ecommerce-ops/hooks/use-ecommerce-sellers.ts` — add `useProvisionSeller` / `useSuspendSeller` /
  `useCloseSeller` mutations (`apiClient.post<void>(url)`); on success invalidate seller detail + list.
- `features/ecommerce-ops/components/SellerDetail.tsx` — status-conditional confirm-gated action buttons
  (PENDING→[프로비저닝]; ACTIVE→[정지][폐점]; SUSPENDED→[폐점]; CLOSED→none) via `ConfirmDialog`; status badge tone.
- `features/ecommerce-ops/components/SellersScreen.tsx` — status badge tone (stop hard-coding green ACTIVE); update
  the "수정/삭제 없음" copy to describe the lifecycle actions.

## Spec-first (contract)

`projects/platform-console/specs/contracts/console-integration-contract.md` §2.4.10.5 — add rows 4/5/6 to the
consumed producer table; revise the "status=ACTIVE only (v1)" / "No update, no delete" paragraphs to document the
lifecycle-action model (transitions, not CRUD). Cross-reference ADR-MONO-042. §3 parity count unchanged (net-new domain scope).

## Acceptance Criteria

- **AC-1** Contract §2.4.10.5 lists the 3 lifecycle endpoints and describes the transition model; no stale "no suspend" claim remains.
- **AC-2** `SELLER_STATUS_VALUES` = `[PENDING_PROVISIONING, ACTIVE, SUSPENDED, CLOSED]`; `sellerStatusTone` maps each (+ unknown fallback) to a label+className. List & detail badges use it (no hard-coded green).
- **AC-3** Three POST proxy routes exist; each returns `204` on producer 204 and maps 403→403 / 404→404 / 503→503 via `mapEcommerceError`. No `X-Tenant-Id`, no `Idempotency-Key`, domain-facing token only.
- **AC-4** SellerDetail shows exactly the status-valid actions, each confirm-gated (`ConfirmDialog`, `pending` + inline `errorMessage`), and on success invalidates detail+list so the badge/actions reflect the new state without a full reload.
- **AC-5** `pnpm lint` + `tsc --noEmit` + `vitest` all green (unit: proxy routes, `sellerStatusTone`, SellerDetail action gating/visibility).

## Related Specs

- `projects/platform-console/specs/services/console-web/architecture.md` (§ Server vs Client Components; mutation/proxy model)
- `projects/platform-console/specs/contracts/console-integration-contract.md` §2.4.10 / §2.4.10.5 / §2.5

## Related Contracts

- Producer: ecommerce `product-service` `AdminSellerController` (`/api/admin/sellers/{id}/{provision|suspend|close}`) — ADR-MONO-042 D3/D4. Consumed read-only; never redefined (§5 Change Rule).

## Edge Cases

- CLOSED seller → no actions rendered (terminal). SUSPENDED → only 폐점 (no producer reactivation path).
- Idempotent no-op (e.g. provision on already-ACTIVE) returns 204 → treated as success; UI re-reads the (unchanged) state.
- Non-ASCII / spaced `sellerId` (e.g. `셀러 1`): the proxy `[id]` segment is decoded once then re-encoded by the api client (same StrictHttpFirewall double-encode trap fixed in PC-FE-133) — lifecycle routes must not double-encode.
- Unknown/future status string from producer → `passthrough` schema keeps it; `sellerStatusTone` neutral fallback; no crash.

## Failure Scenarios

- 403 `ACCESS_DENIED` (operator lacks ADMIN role) → inline "권한 없음" in the ConfirmDialog error slot; action not performed.
- 404 `SELLER_NOT_FOUND` (seller deleted/cross-tenant mid-session) → inline error; list invalidation drops the stale row on next read.
- 503 / timeout / network → `EcommerceUnavailableError` → inline "일시적으로 처리할 수 없습니다"; only this action fails, the shell + other sections stay intact (§2.5).
