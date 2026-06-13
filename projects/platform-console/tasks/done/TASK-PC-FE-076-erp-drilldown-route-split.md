# Task ID

TASK-PC-FE-076

# Title

console-web ERP: split the single-page ERP operations surface into a 4-way sidebar drill-in (마스터 / 통합 조회 / 결재함 / 위임), mirroring the WMS parent model — each section becomes its own route loading only its own data slice

# Status

done

# Owner

frontend (Opus 4.8 analysis / Sonnet 4.6 impl) — route split + sidebar drill parent + per-route state loaders; pure UI/IA + data-fan-out refactor, NO contract/spec change (FE-059 precedent).

# Task Tags

- code
- test

---

# Dependency Markers

- **builds on**: TASK-PC-FE-010 (the ERP operations surface), TASK-PC-FE-049/051/054/055 (the org-view / approval / delegation sub-surfaces currently stacked in the one ERP page), and TASK-PC-FE-059 (the Vercel-style sidebar drill-in `NavParent`/`NavLeaf` model — WMS is the precedent: `/wms`=운영 + `/wms/outbound`=출고; the parent route doubles as the first child).
- **spec**: none. The console IA / sidebar interaction is a pure UI concern (FE-059: "no `console-integration-contract` change"). `console-integration-contract.md` § 2.4.8 governs the ERP *data surface* (10 GETs, list+detail, `?asOf=`, resilience) — none of which changes; only how the console *lays out* those reads across routes changes. § 2.4.8 already calls the surface "list-driven (browsable index for each master, drillable into detail)" — the split is consistent with, not in conflict with, the spec.

# Goal

Today `/erp` renders **nine independent data blocks** on one page (5 masters + read-model org-view + approval requests/inbox + delegation management + delegation facts) behind nine server-side fan-out calls, with an in-page anchor nav (`마스터 / 통합 조회 / 결재함 / 위임(관리) / 위임 현황`). The anchor nav is itself evidence the page is over-dense.

Split it into a one-level sidebar drill-in identical in model to WMS, so the operator picks one section and only that section's data loads:

- `ERP` (parent, `nav-erp`) →
  - `마스터` (`nav-erp-masters` → `/erp`) — the 5 masters (부서/직원/직급/비용센터/거래처), `<AsOfPicker>`, write affordances unchanged.
  - `통합 조회` (`nav-erp-orgview` → `/erp/orgview`) — the read-model `EmployeeOrgViewCard`, `<AsOfPicker>`.
  - `결재함` (`nav-erp-approval` → `/erp/approval`) — the `ApprovalScreen` (requests + inbox).
  - `위임` (`nav-erp-delegation` → `/erp/delegation`) — `DelegationScreen` (관리, write) + `DelegationFactCard` (현황, read-model), the two former 위임 sub-sections combined.

`/erp` stays the parent's first child (마스터), exactly as `/wms` is WMS's 운영 child. The in-page anchor nav is removed (the sidebar replaces it).

# Scope

## In Scope

### Sidebar (`src/shared/ui/ConsoleSidebarNav.tsx`)

- Convert the `ERP` entry from a `NavLeaf` (`{ href:'/erp', label:'ERP', testid:'nav-erp' }`) to a `NavParent` (`{ key:'erp', label:'ERP', testid:'nav-erp', children:[…] }`) with the 4 children above. Reuse the existing FE-059 drill machinery verbatim (route-driven auto-open, longest-prefix active child, pinned-parent back toggle) — no new sidebar logic, only a new parent in `GROUPS`.
- `nav-erp` now denotes the **parent toggle** (parity with how FE-059 repurposed `nav-wms`); `nav-erp-masters` is the new child testid for the `/erp` destination previously reached via `nav-erp`.

### Routes (`src/app/(console)/erp/`)

- Extract the shared **eligibility preflight** (the `getCatalog()` → `erp` product resolve + 401-redirect + `registryDegraded`) and the **four notice states** (`registryDegraded` / `notEligible` / `forbidden` / `degraded`) out of the current `page.tsx` into reusable helpers so all 4 routes share one implementation and render identical notices:
  - `features/erp-ops/api/erp-eligibility.ts` — `resolveErpEligibility(): Promise<{ eligible: boolean; registryDegraded: boolean }>` (server-only; the `getCatalog()` logic, 401 → `redirect('/login')`).
  - `features/erp-ops/components/ErpSectionNotice.tsx` — `<ErpSectionNotice kind="registryDegraded"|"notEligible"|"forbidden"|"degraded" />` rendering the existing `<section aria-labelledby="erp-heading">` + `role="status"` notice with the existing `data-testid`s (`erp-degraded`, `erp-not-eligible`, `erp-forbidden`) and copy verbatim.
- `/erp` `page.tsx` — rewritten to render ONLY the masters slice (`ErpMastersScreen`), loading via `getErpMastersState`.
- `/erp/orgview/page.tsx` (new) — `ErpOrgViewScreen` via `getErpOrgViewState`.
- `/erp/approval/page.tsx` (new) — `ErpApprovalScreen` via `getErpApprovalState`.
- `/erp/delegation/page.tsx` (new) — `ErpDelegationScreen` via `getErpDelegationState`.
- Each route keeps `export const dynamic = 'force-dynamic'`, the same eligibility preflight, and the same per-section degrade discipline (401 → whole-session re-login; 403 → inline forbidden; 503/timeout → only this route degrades).

### State loaders (`src/features/erp-ops/api/erp-state.ts`)

- Split the monolithic `getErpSectionState` into four focused loaders, each fetching ONLY its route's slice (so a 4-way split means each route makes 1–5 calls, not 9):
  - `getErpMastersState(eligible, asOf)` → `{ departments, employees, jobGrades, costCenters, businessPartners, notEligible, forbidden, degraded }` — the 5 masters are the **sole section-degrade authority** for this route (unchanged 401/403/503 mapping).
  - `getErpOrgViewState(eligible, asOf)` → `{ employeeOrgViews, … }` — single read-model leg; this leg IS the route's degrade authority here (no longer "best-effort behind masters"). 401/403/503 mapped per § 2.4.8.
  - `getErpApprovalState(eligible)` → `{ approvalRequests, approvalInbox, … }`.
  - `getErpDelegationState(eligible)` → `{ delegationFacts, … }` (the `DelegationScreen` write surface is client-driven and needs no server seed).
- Remove the old aggregate `getErpSectionState` + `ErpSectionState` once no route imports them (update the `index.ts` barrel + any test). Keep the `erp-keys` re-exports intact.

### Screens (`src/features/erp-ops/components/`)

- Replace `ErpOpsScreen` with four focused screens (new files; keep components small and named per section): `ErpMastersScreen` (heading "ERP 마스터" + `<AsOfPicker>` + 5 lists, `mastersWritable` prop preserved), `ErpOrgViewScreen` ("ERP 통합 조회" + `<AsOfPicker>` + `EmployeeOrgViewCard`), `ErpApprovalScreen` ("ERP 결재함" + `ApprovalScreen`), `ErpDelegationScreen` ("ERP 위임" + `DelegationScreen` + `DelegationFactCard`). The in-page anchor `<nav aria-label="ERP 섹션 이동">` is dropped.
- Update the `features/erp-ops/index.ts` barrel to export the 4 new screens + 4 new loaders; drop `ErpOpsScreen` + `getErpSectionState` exports.

### Tests

- Sidebar unit test (extend the FE-059 `sidebar-drilldown` suite or add an `erp-drilldown` case): ERP renders as a toggle (not a link); clicking drills in to 마스터/통합 조회/결재함/위임; `/erp/orgview` deep-link auto-opens ERP with `통합 조회` `aria-current=page` and `마스터`(/erp) NOT active (longest-prefix); pinned ERP drills back out.
- Any existing unit test asserting `nav-erp` is a link, or asserting the single `/erp` page renders approval/delegation/org-view blocks, is updated to the new routes (e.g. an org-view/approval/delegation block now lives on its own route).
- e2e (Playwright, `apps/console-web/tests/e2e` or the federation-hardening pack if ERP nav is covered there): if an existing spec navigates to `/erp` and asserts approval/delegation/org-view visible on the same page, retarget it to the new routes; add nav-drill steps. If no ERP e2e exists, no new e2e is required (unit coverage suffices — parity with FE-059).
- `npx tsc --noEmit` clean; `pnpm exec vitest run` green; scope = `console-web` only.

## Out of Scope

- Any change to the ERP **backend** / `console-bff` / producer endpoints, or to the `?asOf=` semantics.
- A summary/dashboard `/erp` landing (the "요약 + drill-in" alternative) — explicitly deferred; this task is the pure 4-split. A future task may layer a count-card landing once an aggregate endpoint exists.
- Converting SCM / Finance into parents (they stay flat leaves — their block counts are below the density threshold).
- Multi-level (>1) drill, mobile drawer, or any `console-integration-contract` / contract change.
- Changing master write affordances, approval/delegation mutation flows, or `AsOfPicker` URL-binding behavior.

# Acceptance Criteria

- [ ] Sidebar: `ERP` is a toggle (`nav-erp`, not a link). Clicking it drills in to `마스터`(`nav-erp-masters`→`/erp`), `통합 조회`(`nav-erp-orgview`→`/erp/orgview`), `결재함`(`nav-erp-approval`→`/erp/approval`), `위임`(`nav-erp-delegation`→`/erp/delegation`); ERP pins at top.
- [ ] Clicking pinned `ERP` returns to the full top-level list.
- [ ] Deep-linking `/erp/orgview` (or `/approval`, `/delegation`) auto-opens the ERP drill with the correct child `aria-current=page`; `마스터`(/erp) does NOT light up on the child routes (longest-prefix active).
- [ ] `/erp` renders ONLY the 5 masters (+ AsOfPicker + write affordances); it makes the masters fetch only, NOT the approval/delegation/org-view fetches.
- [ ] `/erp/orgview` renders only the org-view card; `/erp/approval` only the approval screen; `/erp/delegation` only delegation 관리+현황 — each fetching only its slice.
- [ ] All four routes share one eligibility preflight + identical notice rendering for registryDegraded / notEligible (`erp-not-eligible`) / forbidden (`erp-forbidden`) / degraded (`erp-degraded`); 401 on any route → `/login`.
- [ ] `npx tsc --noEmit` clean; `pnpm exec vitest run` green (new/updated sidebar + any retargeted page tests, no regression); scope = console-web only.

# Related Specs

- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4.8 (ERP operations surface — the data surface is UNCHANGED; the split only re-lays-out these reads across routes), § 2.5 (resilience — per-route degrade discipline preserved).
- `projects/platform-console/specs/services/console-web/architecture.md` (Layered-by-Feature; app/ imports only the `features/erp-ops` barrel — the new screens/loaders are exported from it).

# Related Contracts

- None changed. ERP producer endpoints, credentials (IAM OIDC access token), and `?asOf=` semantics are untouched. Sidebar + route layout are UI concerns.

# Target Service

- `platform-console` / `apps/console-web` — `src/app/(console)/erp/**` (1 rewritten + 3 new route files), `src/features/erp-ops/{api,components,index.ts}` (split loaders + screens + shared eligibility/notice), `src/shared/ui/ConsoleSidebarNav.tsx` (ERP parent), unit tests. Client + server components; no backend/data-contract change.

# Architecture

- Reuses the FE-059 single-level drill union (`NavParent`/`NavLeaf`) — ERP is simply a second parent alongside WMS/IAM. Per-route data isolation: each route's loader fetches only its slice, so the worst-case page fan-out drops from 9 parallel calls to ≤5 (masters) / 1 (others), improving perceived load + failure isolation (an approval-service outage can no longer surface a degraded notice next to healthy masters — it degrades only `/erp/approval`). Shared eligibility/notice helpers keep the four routes DRY and behaviorally identical on the auth/degrade paths.

# Edge Cases

- Deep-link `/erp/approval` while approval-service is down → only `/erp/approval` degrades; `/erp` (masters) loads normally. (Under the old single page a down approval-service was swallowed to `null`; now it is the approval route's own degrade authority — assert the inline degraded notice on that route, masters route unaffected.)
- Collapse ERP drill (click pinned ERP) while on a `/erp/*` route → top-level list shown; next navigation into any `/erp/*` route re-opens ERP.
- `?asOf=` is meaningful on `/erp` (masters) and `/erp/orgview` only; `/erp/approval` + `/erp/delegation` legs take no `asOf` (unchanged) — do not thread it where the producer ignores it.
- A retired/non-eligible operator hitting any of the 4 routes directly → the shared `notEligible` / `forbidden` notice, never a crash or fabricated cross-tenant call.

# Failure Scenarios

- ERP rendered as a `<Link href="/erp">` (parent not converted) → drill never triggers → AC asserts `nav-erp` is a toggle button.
- A child route fetching the whole 9-call slice (loader not actually split) → defeats the purpose → AC asserts each route fetches only its slice.
- `마스터`(/erp) lighting up on `/erp/orgview` (bare-prefix active) → AC asserts longest-prefix.
- Duplicated/divergent notice copy across the 4 routes (eligibility not extracted) → AC asserts one shared notice helper + identical testids.
- The old `getErpSectionState`/`ErpOpsScreen` left exported but unused → barrel drift → Scope requires removing them from `index.ts`.

# Definition of Done

- [ ] 4-way drill-in works per AC; route auto-open + longest-prefix active correct; each route fetches only its slice.
- [ ] Shared eligibility preflight + notice helper; 401/403/503 discipline preserved per route.
- [ ] `getErpSectionState` + `ErpOpsScreen` removed; barrel exports the 4 screens + 4 loaders.
- [ ] tsc + vitest green, no regression; scope = console-web only.
- [ ] Acceptance Criteria satisfied.
- [ ] Ready for review.
