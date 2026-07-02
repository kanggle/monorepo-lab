# TASK-PC-FE-159 — adopt shared `StatusBadge` on the remaining status columns

**Status:** backlog
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

- [ ] **ecommerce orders** — `OrdersScreen` + `OrderDetail` (+ `EcommerceOverview` order-status cells). Enum
      `ORDER_STATUS_VALUES` (order-types.ts): PENDING→warning, CONFIRMED/SHIPPED→progress, DELIVERED→success,
      CANCELLED/STUCK_RECOVERY_FAILED→danger.
- [ ] **ecommerce shipping** — `ShippingsTable`. `SHIPPING_STATUS_VALUES` (shipping-types.ts): PREPARING→warning,
      SHIPPED/IN_TRANSIT→progress, DELIVERED→success.
- [ ] **ecommerce product** — `ProductsScreen` + `ProductDetail`. `PRODUCT_STATUS_VALUES` (types.ts): ON_SALE→success,
      SOLD_OUT→warning, HIDDEN→neutral.
- [ ] **ecommerce promotion** — `PromotionsScreen` + `PromotionDetail`. `PROMOTION_STATUS_VALUES` (types.ts):
      ACTIVE→success, SCHEDULED→progress, ENDED→neutral.
- [ ] **finance** — `TransactionsTable` (+ account status where shown). `KNOWN_TXN_STATUSES`: ACTIVE/CAPTURED→progress,
      RELEASED→neutral, SETTLED→success. `KNOWN_ACCOUNT_STATUSES`: ACTIVE→success, RESTRICTED→warning, FROZEN→danger,
      CLOSED→neutral (finance-ops/api/types.ts).
- [ ] **ledger** — `PeriodsTable`/`PeriodDetail` + `DiscrepancyQueue`/`DiscrepancyDetail` (+ `StatementDetail`).
      `KNOWN_PERIOD_STATUSES`: OPEN→progress, CLOSED→success. `KNOWN_DISCREPANCY_STATUSES`: OPEN→warning,
      RESOLVED→success (ledger-ops/api/types.ts).
- [ ] **erp approvals** — `ApprovalDetail`: replace the inline `stageBadgeTone` ternary with `<StatusBadge>`, and badge
      the overall `ApprovalStatus` (approval-types.ts): DRAFT/WITHDRAWN→neutral, SUBMITTED/IN_REVIEW→progress,
      APPROVED→success, REJECTED→danger. Stage: APPROVED→success, current→progress, pending→neutral.
- [ ] **erp master lists** — `BusinessPartnerList` / `CostCenterList` / `DepartmentList` / `EmployeeList` /
      `JobGradeList` (+ Employee/Delegation cards). `KNOWN_MASTER_STATUSES` (erp-ops/api/types/common.ts):
      ACTIVE→success, RETIRED→neutral.
- [ ] **scm** — `ScmPoTable` / `ScmStalenessTable` (scm-ops; `status`/`staleness` are TOLERANT free strings — map the
      recognizable values best-effort, else `neutral`) and `ReplenishmentScreen` (scm-replenishment):
      `KNOWN_SUGGESTION_STATUSES`: SUGGESTED→warning, APPROVED→progress, MATERIALIZED→success, DISMISSED→neutral.

> Tone mappings above are the proposed default (semantics-preserving); confirm against each surface's current
> colour if one already exists. Where a status column is a non-lifecycle attribute (e.g. a type/category), leave it
> as plain text — badge only true *status* fields.

## Acceptance Criteria

- [ ] Every checklist surface renders its status via `<StatusBadge>`; no feature hardcodes the pill className or a
      colour map (grep for `inline-block rounded px-2 py-0.5` returns only `StatusBadge.tsx`).
- [ ] Each domain adds ONE `xStatusTone` helper in its `api/*-types.ts` (type-only `StatusTone` import); unknown/
      future status → `neutral`, never a crash.
- [ ] Raw status text preserved as the label (assertions + filters intact).
- [ ] Per changed domain: `pnpm lint` clean, `tsc --noEmit` no new errors, affected vitest green. (Can land per
      domain — no need for one big PR.)

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
