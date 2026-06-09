# Task ID

TASK-PC-FE-059

# Title

console-web sidebar: Vercel-style drill-in navigation — clicking a parent with submenus reveals its submenus, pins the parent at the top as a back toggle, and clicking the pinned parent returns to the top-level list (WMS → 운영 / 출고)

# Status

done

# Owner

frontend (Opus 4.8 analysis / Sonnet 4.6 impl) — `ConsoleSidebarNav` interaction model change, additive nav structure; no contract/spec change.

# Task Tags

- code
- test

---

# Dependency Markers

- **builds on**: TASK-PC-FE-039 (the Vercel-style left sidebar that replaced the top-bar nav with a data-driven `ConsoleSidebarNav` list) and TASK-PC-FE-057 (added the `/wms/outbound` "WMS 출고" entry as the second wms surface — `nav-wms-outbound`).
- **note**: the `/wms` (운영) and `/wms/outbound` (출고) destinations are unchanged; this task only re-shapes the sidebar from a single flat list into a one-level drill-in where `WMS` becomes a **parent** of `운영`(/wms) + `출고`(/wms/outbound). Other domains (SCM, Finance, ERP) stay flat leaves (no submenus → no drill).
- **spec**: none. Sidebar interaction is a pure UI concern (PC-FE-039 precedent — no `console-integration-contract` change).

# Goal

Make the console left sidebar behave like Vercel's drill-in nav:

1. A top-level item that **has submenus** renders as a toggle. Clicking it **drills in**: the sidebar is replaced by that parent pinned at the very top (acting as a back control) followed by its submenu items.
2. Clicking the **pinned parent** drills back **out** to the full top-level list (the position where all top-level menus live).
3. Top-level items **without** submenus keep navigating directly (plain links — unchanged).
4. The current route auto-selects the correct drill state: loading `/wms` or `/wms/outbound` directly opens the `WMS` group with the matching submenu marked active.

Concrete structure (the only parent in this task):

- `WMS` (parent, `nav-wms`) → `운영` (`nav-wms-ops` → `/wms`), `출고` (`nav-wms-outbound` → `/wms/outbound`).

# Scope

## In Scope

- **`src/shared/ui/ConsoleSidebarNav.tsx`** — restructure the `GROUPS` model to allow a `NavParent` ({ key, label, testid, children:[NavLeaf] }) alongside `NavLeaf`. Add client drill state (`useState`/`useEffect` synced from `usePathname`). Two render modes: top-level list (leaves = links, parents = drill-toggle buttons with a right chevron) and drilled view (pinned parent back-button with a left chevron + its children, the active child via longest-prefix match so `운영`/`/wms` does NOT also light up on `/wms/outbound`). Inline stroke-SVG chevrons (matching the `ThemeToggle` idiom — no new dependency).
- Preserve every existing `data-testid` + `href`: `nav-dashboards`, `nav-domain-health`, `nav-catalog`, `nav-audit`, `nav-operators`, `nav-wms` (now the WMS parent toggle), `nav-scm`, `nav-finance`, `nav-erp`, `nav-wms-outbound` (now the `출고` child). Add `nav-wms-ops` (the `운영` child → `/wms`, the destination previously reached via `nav-wms`).
- **Tests**:
  - `tests/unit/outbound-nav.test.tsx` — update the `nav-wms` href assertion to the new model: on `/wms/outbound` the WMS group is drilled in, `nav-wms-outbound` (href `/wms/outbound`) is present + `aria-current=page`, `nav-wms-ops` (href `/wms`) is the 운영 child, and `nav-wms` is the parent back-toggle. The `resolveConsoleRoute` catalog-routing assertions stay unchanged.
  - `tests/unit/sidebar-drilldown.test.tsx` (new) — top-level list shows WMS as a toggle (not a link); clicking it reveals `운영`+`출고` and pins WMS at top; clicking pinned WMS returns to the top-level list; the active child uses longest-prefix (`출고` active on `/wms/outbound`, `운영` not).
  - `tests/unit/domain-health-nav.test.tsx` — source-string guard stays green (all asserted literals preserved); extend only if needed to assert the new `운영`/`출고` children exist.

## Out of Scope

- Any change to `/wms`, `/wms/outbound` pages or their backend.
- A mobile drawer / multi-level (>1) drill — single level only.
- Converting SCM / Finance / ERP into parents (they remain flat leaves).
- Any contract / spec change.

# Acceptance Criteria

- [ ] Top-level list: `WMS` renders as a toggle button (`nav-wms`, not a link). Clicking it drills in — `운영` (`nav-wms-ops`→`/wms`) and `출고` (`nav-wms-outbound`→`/wms/outbound`) appear and `WMS` is pinned at the top.
- [ ] Clicking the pinned `WMS` returns to the full top-level list (개요 / 도메인 상태 / 카탈로그 / 관리 / 도메인 운영 leaves visible again).
- [ ] Leaves (개요, 도메인 상태, 카탈로그, 감사·보안, 운영자 관리, SCM, Finance, ERP) keep navigating directly as links — no behavior change.
- [ ] Loading `/wms/outbound` (or `/wms`) directly auto-opens the WMS drill with the correct child `aria-current=page`; `운영` does NOT light up on `/wms/outbound` (longest-prefix active).
- [ ] `pnpm exec vitest run` green (new `sidebar-drilldown` + updated `outbound-nav` + existing `domain-health-nav` guard, no regression); `npx tsc --noEmit` clean; scope = console-web only.

# Related Specs

- `projects/platform-console/specs/contracts/console-integration-contract.md` §2.4.5.1 (the `/wms/outbound` surface — TASK-PC-FE-057; unchanged here).

# Related Contracts

- None changed. Sidebar interaction is a UI concern.

# Target Service

- `platform-console` / `apps/console-web` — `src/shared/ui/ConsoleSidebarNav.tsx` + unit tests. Client component; no UI route or data change beyond the sidebar interaction model.

# Architecture

- Single-level drill: `NavParent`/`NavLeaf` discriminated union; drill state is local UI state (`useState`) initialised + re-synced from `usePathname` (route-driven auto-open), with manual collapse of the pinned parent. Active-child resolution is longest-matching-prefix to disambiguate nested routes (`/wms` vs `/wms/outbound`).

# Edge Cases

- Direct deep-link to `/wms/outbound` → WMS auto-drilled, `출고` active, `운영` inactive.
- Collapse (click pinned WMS) while still on a `/wms/*` route → list shown; next navigation into a `/wms/*` route re-opens it.
- Clicking a child link → navigates and stays within the drilled WMS group.
- Keyboard: parent toggle + back are real `<button>`s (focus-visible ring); children are `<Link>`s.

# Failure Scenarios

- Active check left as bare prefix → `운영`(/wms) lights up on `/wms/outbound` → AC asserts longest-prefix.
- Parent rendered as a `<Link href="/wms">` → drill never triggers → AC asserts `nav-wms` is a toggle button.
- A preserved testid/href literal dropped from the source → `domain-health-nav` guard fails → AC asserts the guard stays green.

# Definition of Done

- [ ] Drill-in / drill-out works per AC; route auto-open + longest-prefix active correct
- [ ] vitest + tsc green, no regression; scope = console-web only
- [ ] Acceptance Criteria satisfied
- [ ] Ready for review
