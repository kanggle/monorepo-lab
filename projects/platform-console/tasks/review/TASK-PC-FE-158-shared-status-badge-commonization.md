# TASK-PC-FE-158 — extract a shared **`StatusBadge`** and commonize status pills

**Status:** review
**Area:** platform-console / console-web · **Paths:** `shared/ui/StatusBadge.tsx` (new) + `features/ecommerce-ops` (users/sellers) + `features/wms-outbound-ops`
**Parent:** follows TASK-PC-FE-057 (wms outbound) + the ecommerce user/seller surfaces (PC-FE-084/090/154). Housekeeping/consistency task surfaced while badging the wms outbound status.
**Analysis model:** Opus 4.8 · **Impl model recommendation:** Sonnet (mechanical UI extraction + migration; no domain/resilience logic).

## Goal

The status "pill" was **not commonized**: there was no shared badge component, and each feature copy-pasted the
`inline-block rounded px-2 py-0.5 text-xs font-medium …` markup plus its own ad-hoc `status → colour` map
(ecommerce `sellerStatusTone`, ecommerce users' `STATUS_BADGE` duplicated in **both** `UsersScreen` and
`UserDetail`, wms `outboundStatusTone`, erp's inline `stageBadgeTone`). Palettes and dark-mode handling diverged
(only erp had `dark:` variants). Extract a single shared `StatusBadge` + semantic palette so any domain/menu renders
status chips consistently, and migrate the wms-outbound + ecommerce user/seller surfaces onto it.

## Design — semantic tone, domain-local status maps

A single global `status → colour` map is impossible (a domain's status enum differs — `ACTIVE` vs `SHIPPED`). So:

- **`shared/ui/StatusBadge.tsx`** owns the markup + the 5-tone semantic palette (`success | progress | warning |
  danger | neutral`) **with dark-mode variants baked in**. `<StatusBadge tone={…}>{rawStatus}</StatusBadge>`; also
  exports `statusToneClass(tone)` for the rare non-`<span>` caller. `tone` defaults to `neutral` (safe for unknown).
- **Each domain keeps a small `status → StatusTone` map** (its status vocabulary stays domain-local) and renders
  through the shared component. No colours are hardcoded at a call site anymore.
- The **raw status string stays the badge label** (verbatim), so screen readers, status-text assertions, and the
  status filters stay in lock-step with the producer enum.

## Scope

- **New:** `shared/ui/StatusBadge.tsx` (component + `StatusTone` + `statusToneClass`); `tests/unit/status-badge.test.tsx`.
- **wms-outbound:** `outboundStatusTone()` returns `StatusTone` (was `{label,className}`); `OutboundOrdersTable` +
  `OutboundOrderDrill` render via `<StatusBadge>`. (Rolls in the just-added wms-status-badge change.)
- **ecommerce sellers:** `sellerStatusTone()` returns `StatusTone`; `SellersScreen` / `SellerDetail` /
  `EcommerceOverview` render via `<StatusBadge>` (Overview keeps its `shrink-0` via `className`).
- **ecommerce users:** new `userStatusTone()` in `user-types.ts` replaces the **duplicated** `STATUS_BADGE` maps in
  `UsersScreen` **and** `UserDetail`; both render via `<StatusBadge>`.
- **Tone mapping** (semantics preserved from v1 colours): users ACTIVE→success, SUSPENDED→warning, WITHDRAWN→neutral;
  sellers ACTIVE→success, PENDING_PROVISIONING→warning, SUSPENDED→neutral, CLOSED→danger; outbound PICKING→warning,
  PICKED/PACKING/PACKED→progress, SHIPPED→success, CANCELLED→danger, BACKORDERED→warning.
- **Out of scope (follow-up):** erp `ApprovalDetail` stage badge; the many status columns still rendered as plain
  text (ecommerce orders/products/promotions/shippings, finance, ledger, scm, replenishment) — they can adopt
  `<StatusBadge>` incrementally now that it exists. Producer/contract unchanged; no backend touch.

## Acceptance Criteria

- [x] `shared/ui/StatusBadge.tsx` exists; owns the pill markup + 5 semantic tones with dark-mode variants; `neutral`
      is the safe default for unknown/absent status.
- [x] wms-outbound (table + drill) and ecommerce users/sellers (list + detail + overview) render status via the
      shared `<StatusBadge>`; no feature hardcodes the pill className or a colour map anymore.
- [x] Raw status text preserved as the label (a11y + assertions + filter parity); unknown/future status → neutral,
      never a crash (TOLERANCE invariant).
- [x] `pnpm lint` clean (changed files), `tsc --noEmit` clean (no new errors), full affected vitest green (338/338
      across 32 files, incl. new `status-badge` + updated `ecommerce-seller-status-tone`).

## Related Specs

- `specs/services/console-web/architecture.md` § Allowed Dependencies / Boundary Rules (feature-local status→tone
  maps; shared presentational component in `shared/ui`).
- `console-integration-contract.md` TOLERANCE invariant (unknown/future producer enum never crashes the console).

## Related Contracts

- None changed. Status enums are consumed as-is from the existing producer contracts
  (`outbound-service-api.md`, ecommerce `product-service` seller/user admin APIs). No API/event change.

## Edge Cases

- Unknown / future / absent status → `neutral` tone, raw string (or `—` when absent) as label. Verified by
  `status-badge` + `ecommerce-seller-status-tone` tests.
- `EcommerceOverview` badge is a flex child needing `shrink-0` → passed via `StatusBadge className`.
- Seller `SUSPENDED` maps to `neutral` (was gray) while user `SUSPENDED` maps to `warning` (was yellow) — domain
  maps legitimately differ; the shared component only owns the palette, not the mapping.

## Failure Scenarios

- **Colour regression:** a domain maps a status to the wrong tone → caught by the per-domain tone unit test
  (`ecommerce-seller-status-tone`) + visual review; the shared palette test locks tone→colour.
- **Text/assertion drift:** label no longer equals the raw status → the existing `*-row-status` / `*-detail-status`
  `toHaveTextContent` assertions fail. Mitigated by keeping the raw status as the badge child.
- **Layer leak:** an `api/*-types.ts` importing the JSX component (not just the `StatusTone` type) would pull React
  into the api layer → use `import type` only (done); tsc/lint guard it.
