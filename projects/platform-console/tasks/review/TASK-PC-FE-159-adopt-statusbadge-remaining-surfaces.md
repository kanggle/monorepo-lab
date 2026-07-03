# TASK-PC-FE-159 — adopt shared `StatusBadge` on the remaining status columns

**Status:** review
**Area:** platform-console / console-web · **Paths:** `features/{ecommerce-ops,finance-ops,ledger-ops,erp-ops,scm-ops,scm-replenishment}`
**Parent / depends on:** TASK-PC-FE-158 (shared `StatusBadge` extraction — `shared/ui/StatusBadge.tsx`). This is the
incremental roll-out follow-up that PC-FE-158 explicitly scoped out.
**Analysis model:** Opus 4.8 · **Impl model recommendation:** Sonnet (mechanical per-domain migration; no domain
logic). Each domain is independent → parallel-dispatchable per domain.

## Goal

PC-FE-158 introduced the shared `<StatusBadge>` + semantic 5-tone palette and migrated wms-outbound + ecommerce
users/sellers. Every OTHER surface still renders its status as **plain text** (or, for erp approvals, an inline
one-off tone). Adopt `<StatusBadge>` across them so all status chips are visually consistent (colour + dark mode)
and no feature re-implements the pill markup or a colour map. Pure presentation; no producer/contract change.

## Pattern (identical to PC-FE-158)

Per domain: add a small `xStatusTone(status): StatusTone` to the domain's `api/*-types.ts` (`import type { StatusTone }
from '@/shared/ui/StatusBadge'` — **type-only**, never the JSX component into an api file), map the domain's status
enum → semantic tone, then render `<StatusBadge tone={xStatusTone(s)}>{s}</StatusBadge>` at each call site (raw
status stays the label — keeps `*-status` `toHaveTextContent` assertions + status filters intact). Unknown/future →
`neutral` (TOLERANCE). Reuse the existing tone if a domain already has a partial helper.

## Scope — per-domain checklist (each independently shippable)

- [x] **ecommerce orders** — `OrdersScreen` + `OrderDetail` + `EcommerceOverview` recent-orders. `orderStatusTone`
      (order-types.ts): PENDING→warning, CONFIRMED/SHIPPED→progress, DELIVERED→success, CANCELLED/
      STUCK_RECOVERY_FAILED→danger. (The overview *distribution* panel shows per-status **counts**, not a status
      pill — left as-is.)
- [x] **ecommerce shipping** — `ShippingsTable` via `shippingStatusTone` (shipping-types.ts): PREPARING→warning,
      SHIPPED/IN_TRANSIT→progress, DELIVERED→success. (Label stays the Korean `statusLabel(...)`.)
- [x] **ecommerce product** — `ProductsScreen` + `ProductDetail` via `productStatusTone` (types.ts): ON_SALE→success,
      SOLD_OUT→warning, HIDDEN→neutral.
- [x] **ecommerce promotion** — `PromotionsScreen` + `PromotionDetail` via `promotionStatusTone` (types.ts):
      ACTIVE→success, SCHEDULED→progress, ENDED→neutral.
- [x] **finance** — `AccountDetail` (`accountStatusTone`) + `TransactionsTable` (`txnStatusTone`). Account:
      PENDING_KYC/RESTRICTED→warning, ACTIVE→success, FROZEN→danger, CLOSED→neutral. Txn: PENDING→warning,
      COMPLETED/SETTLED→success, FAILED/REVERSED→danger, CAPTURED/ACTIVE→progress, RELEASED→neutral. Replaces the
      per-file `STATUS_CLASS`+`statusVariant`; `labelForUnknown` "(unknown)" kept as the label.
- [x] **ledger** — `PeriodsTable`+`PeriodDetail` (`periodStatusTone`: OPEN→progress, CLOSED→success) +
      `DiscrepancyQueue`+`DiscrepancyDetail`+`StatementDetail` (`discrepancyStatusTone`: OPEN→warning,
      RESOLVED→success). The plain-text detail cards now badge too (list/detail consistent).
- [x] **erp approvals** — `approvalStatusTone` (approval-types.ts): DRAFT/WITHDRAWN→neutral, SUBMITTED→warning,
      IN_REVIEW→progress, APPROVED→success, REJECTED→danger. The approval badge + the `ApprovalDetail` stage badge
      use `statusToneClass(tone)` (keeping their own `<span>` for `data-status`/`data-terminal`/`approval-stage-*`).
      Stage: APPROVED→success, current→progress, pending→neutral. (SUBMITTED kept warning — preserves the current
      amber; WITHDRAWN moved red→neutral, a recalled request is not a failure.)
- [x] **erp master lists** — all 5 lists (`BusinessPartner`/`CostCenter`/`Department`/`Employee`/`JobGrade`) via
      `masterStatusTone` (ACTIVE→success, RETIRED→neutral) + Employee `employmentStatus` via `employmentStatusTone`
      (EMPLOYED→success, ON_LEAVE→warning, SEPARATED→neutral). RETIRED/SEPARATED stay honestly surfaced (neutral,
      row still dimmed via the existing `data-retired`/`data-separated`).
- [x] **scm** — `ScmPoTable` (`poStatusTone`) + `ScmStalenessTable` (`stalenessTone`: FRESH→success, STALE→warning,
      UNREACHABLE→danger) in scm-ops-helpers.ts; `ReplenishmentScreen` (`suggestionStatusTone`: SUGGESTED→warning,
      APPROVED→progress, MATERIALIZED→success, DISMISSED→neutral) in scm-replenishment/api/types.ts. All tolerant
      free-string → neutral fallback.

> **Delivered 2026-07-03** (branch `feat/pc-fe-159-adopt-statusbadge`, 3 commits: ecommerce · finance+ledger ·
> erp+scm). Also added an optional `data-testid` passthrough to `StatusBadge` so the span-badge surfaces
> (finance/ledger/erp-master/scm-staleness) keep their existing `*-status` testid on the badge itself.
> Full suite green: **2185/2185** vitest, tsc clean, lint clean.
>
> **Residual (small, optional follow-up)** — a few *secondary* erp badges were left as-is to keep this focused and
> avoid churning their `data-status` contracts: `DelegationFactCard` / `DelegationGrantList` (delegation ACTIVE/
> REVOKED/ACTIVE_EXPIRED) and `EmployeeOrgViewCard` (free-string read-model status, always muted). They can adopt
> `statusToneClass` the same way the approval badge did whenever touched next.

> Tone mappings above are the proposed default (semantics-preserving); confirmed against each surface's current
> colour. Where a status column is a non-lifecycle attribute (e.g. a type/category), it is left as plain text —
> only true *status* fields are badged.

## Acceptance Criteria

- [x] Every checklist surface renders its status via `<StatusBadge>` (or `statusToneClass` for the erp badges that
      keep their own span for data-* attributes); no feature hardcodes the pill className or a colour map (grep for
      `inline-block rounded px-2 py-0.5` returns only `StatusBadge.tsx`). ✓ verified.
- [x] Each domain adds ONE `xStatusTone` helper (type-only `StatusTone` import); unknown/future/absent status →
      `neutral`, never a crash.
- [x] Raw status text preserved as the label (`labelForUnknown`/`statusLabel`/`data-status` assertions + filters
      intact).
- [x] `tsc --noEmit` clean, `next lint` clean (ecommerce/finance/ledger/erp/scm/shared-ui), full vitest green
      (2185/2185, incl. 4 new tone-map suites). Landed as 3 per-domain-group commits.

## Related Specs

- `specs/services/console-web/architecture.md` § Allowed Dependencies / Boundary Rules (feature-local tone maps;
  shared presentational component in `shared/ui`).
- `console-integration-contract.md` TOLERANCE invariant + the per-domain "surfaced HONESTLY" notes already on each
  enum (e.g. ledger OPEN/CLOSED, erp RETIRED) — badge colour must not hide a status, only style it.

## Related Contracts

- None changed. Status enums consumed as-is from existing producer contracts.

## Edge Cases

- TOLERANT free-string statuses (scm PO/staleness) → map known values, `neutral` fallback for anything else.
- A "status" column that is actually a **type/category** (not lifecycle) → do NOT badge (leave plain).
- erp `ApprovalDetail` stage badge already carries dark-mode variants inline → the shared `StatusBadge` supersedes
  them (dark mode now central); drop the inline ternary.

## Failure Scenarios

- Wrong tone (e.g. CANCELLED not danger) → add/extend the per-domain tone unit test (mirror
  `ecommerce-seller-status-tone.test.ts`) asserting the status→tone map.
- Text/assertion drift → keep the raw status as the badge child (same guard as PC-FE-158).
- Layer leak (JSX component imported into an api `*-types.ts`) → `import type` only; tsc/lint guard.
