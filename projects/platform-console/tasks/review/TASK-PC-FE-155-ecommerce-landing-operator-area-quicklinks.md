# TASK-PC-FE-155 — ecommerce landing **operator-area quick-links** (drop the stale "준비중" note)

**Status:** review
**Area:** platform-console / console-web · **Route:** `app/(console)/ecommerce/page.tsx` (section landing)
**Parent:** ADR-MONO-031 §2.4.10 console-absorption (7 operator areas). Follows TASK-PC-FE-081…090 + TASK-PC-FE-154.

## Goal

Fix a **navigation/doc drift** on the ecommerce section landing (`/ecommerce`). All 7 operator areas
(products / orders / users / promotions / shippings / notifications / sellers) are **shipped and live**, wired
into `ConsoleSidebarNav` (`ConsoleSidebarNav.tsx` ecommerce NavParent). Yet the landing page still renders a
**products-only** "운영" block plus a **factually false** note — `ecommerce-ops-coming-soon` — claiming
"추가 운영 표면 준비중 / 주문 · 셀러 관리 운영 화면은 후속 작업에서 제공됩니다". That copy was correct at Phase 1
(products-only) and was never retired as PC-FE-083…090/154 landed each area.

User-visible symptom: on `/ecommerce`, the operator sees only "상품 운영 →" and a "준비중" note, even though the
left sidebar already links all 7 — an internal contradiction that reads as "orders/sellers not built".

## Scope

Under `projects/platform-console/apps/console-web/src/`, **`app/(console)/ecommerce/page.tsx` only** (+ its unit test):

- Replace the single-link "운영" block with a **quick-launch grid** of all 7 operator areas, labels + hrefs
  mirroring `ConsoleSidebarNav` (상품 `/ecommerce/products` · 주문 `/ecommerce/orders` · 배송 `/ecommerce/shippings`
  · 프로모션 `/ecommerce/promotions` · 사용자 `/ecommerce/users` · 셀러 `/ecommerce/sellers` · 알림
  `/ecommerce/notifications/templates`). Keep the existing `ecommerce-products-link` testid on the 상품 tile
  (back-compat) and give each other tile a `nav`-parallel testid.
- Remove the `ecommerce-ops-coming-soon` note entirely (the grid is self-describing; no false "준비중" copy remains).
- Update the page's stale doc-comment (the "rich operations surface … are deferred follow-ups" / "상세 운영 표면
  준비중" paragraph) to describe the current landing = domain-health card + operator-area quick-links.
- STRICTLY READ-ONLY page semantics unchanged. Eligibility / degrade / 401-redirect / health-card branches
  untouched — only the eligible-path "운영" section body changes.

Non-goals: no backend, no proxy route, no contract change (landing content is not contract-governed — no API
surface added). Sidebar nav already correct → not touched.

## Acceptance Criteria

- **AC-1** On the eligible path, `/ecommerce` renders 7 operator-area links whose hrefs exactly match the
  `ConsoleSidebarNav` ecommerce children (products/orders/shippings/promotions/users/sellers/notifications·templates).
- **AC-2** No `ecommerce-ops-coming-soon` node and no "준비중 / 후속 작업에서 제공" string remains anywhere in the
  rendered eligible output.
- **AC-3** Degrade-safe branches unchanged: registry-degraded → `ecommerce-degraded`; not-eligible →
  `ecommerce-not-eligible`; registry 401 → `redirect('/login')`; health bff unavailable → section still renders +
  `ecommerce-health-unavailable`. (Existing tests for these keep passing, minus the coming-soon assertions.)
- **AC-4** `ecommerce-page.test.tsx` updated: the eligible/degrade-safe tests assert the 7 links are present and the
  coming-soon note is **absent**; the stale header comment in the test file is refreshed.
- **AC-5** `pnpm lint` + `tsc --noEmit` + `vitest` (ecommerce-page) all green.

## Related Specs

- `projects/platform-console/specs/services/console-web/architecture.md` (§ ecommerce 운영 섹션 — line describing
  `page.tsx` = domain-health 랜딩 + 7 child 영역 via `ConsoleSidebarNav`). Landing quick-links are consistent with
  this description (nav via sidebar remains the primary path; the landing grid is a redundant convenience entry) —
  no spec change required.
- `projects/platform-console/specs/contracts/console-integration-contract.md` §2.4.10 (operator areas). No contract
  change (no producer surface touched).

## Related Contracts

None changed. Consumes nothing new.

## Edge Cases

- Not-eligible / degraded / 401 branches short-circuit **before** the quick-link grid → they never render the grid
  (no false affordance to an operator without ecommerce scope).
- A future 8th area would be added to both `ConsoleSidebarNav` and this grid; the grid is a static list mirroring
  the nav (acceptable minor duplication — flagged in the doc-comment).

## Failure Scenarios

- Health bff unavailable → the quick-link grid + health-unavailable note both render (grid is static, not
  health-gated); only the health card collapses (§2.5 degrade-safe, unchanged).
