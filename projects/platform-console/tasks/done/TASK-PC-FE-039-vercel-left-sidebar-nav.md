# Task ID

TASK-PC-FE-039

# Title

`console-web` — move the console section navigation from the top bar into a **Vercel-style left sidebar**, keeping only the brand + **tenant switcher** (+ theme toggle / logout account controls) in the top bar. The sidebar is a grouped vertical rail with current-route active highlighting.

# Status

done

# Owner

frontend-engineer (shell layout restructure; no API/contract/domain change)

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- code

---

# Dependency Markers

- **follows**: TASK-PC-FE-038 (Vercel retheme — tokens/font/dark mode). This is the layout-structure follow-up (top-nav → left sidebar) on top of that aesthetic.
- **no dependency on**: any backend/contract/ADR change. Pure presentation/layout (console-web `(console)` shell).

# Goal

The console reads like a Vercel dashboard: a minimal full-width top bar (brand + tenant context switcher + account controls) over a left section-nav sidebar with an active highlight — with **zero change to routes, data-testids, or API**.

# Scope

## In Scope

- **New** `shared/ui/ConsoleSidebarNav.tsx` (client) — the section links as a grouped vertical rail (`개요`/`도메인 상태`/`카탈로그` · `관리`: `감사·보안`/`운영자 관리` · `도메인 운영`: `WMS`/`SCM`/`Finance`/`ERP`) with `usePathname()` active highlighting (`aria-current="page"` + `bg-accent`). **All `data-testid` (`nav-dashboards` … `nav-erp`) + `href` preserved verbatim** from the prior top-bar nav.
- **`(console)/layout.tsx`**: restructure to a full-width sticky top bar (brand left; tenant switcher + theme toggle + logout right — nav removed) over a `flex` row of `<aside class="hidden w-56 shrink-0 border-r md:block">` (sidebar) + `<main>` (content, inner `max-w-6xl`).

## Out of Scope

- **Mobile nav drawer** — the sidebar is `hidden md:block` (desktop ops console; the top-bar controls remain visible on all sizes). A hamburger/drawer for < md is a deferred follow-up.
- No route/API/contract/auth/tenant logic change; no feature-screen changes.
- Theme toggle + logout stay in the top-right (account/display controls, not section menus); only the **section nav** moves to the sidebar. (If "only tenant in the top bar" is meant strictly, moving toggle+logout to a sidebar footer is a trivial follow-up.)

# Acceptance Criteria

- [x] **AC-1** Section nav renders as a left sidebar (`ConsoleSidebarNav`, grouped 개요/관리/도메인 운영, active-highlighted via `usePathname` + `aria-current`); top bar holds brand + tenant switcher + theme toggle + logout only.
- [x] **AC-2** All nav `data-testid`s + hrefs preserved verbatim (moved to the data-driven list in `ConsoleSidebarNav`); nav guard test updated to read the new source + assert layout wires it in.
- [x] **AC-3** `pnpm test` (781/781) + `tsc --noEmit` (exit 0) + `next lint` (clean) + `next build` (success) all green. No route/API change. Local federation-e2e `console-web` container rebuilt + restarted (live at :3000).

# Related Specs

- `console-web` `architecture.md` § Server vs Client Components (sidebar nav is a client boundary for `usePathname`; layout stays a server component composing it).

# Edge Cases

- **Active highlight for `/console` (catalog)**: exact match only (so domain sub-routes don't light it); domain/section routes use `pathname === href || startsWith(href + '/')`.
- **< md viewport**: sidebar hidden; top-bar controls (tenant/toggle/logout) remain. e2e/Playwright run at desktop viewport (≥ md) → sidebar present → selectors resolve.

# Failure Scenarios

- If an e2e clicked a nav link expecting it in the header, the testid is preserved + still clickable in the sidebar (desktop viewport) → no break. (Federation golden-paths use `page.goto`, not nav clicks.)

# Test Requirements

- `pnpm test` green (nav unit tests assert routing logic, not nav DOM location).
- `tsc --noEmit` + `next lint` + `next build` green (MONO-166 gate parity).
- Local rebuild + container restart so the user can visually confirm at `http://localhost:3000`.

# Definition of Done

- [x] `ConsoleSidebarNav` + `(console)/layout.tsx` restructure.
- [x] `pnpm test` + `tsc --noEmit` + `next lint` + `next build` green.
- [x] Local federation-e2e `console-web` container rebuilt + restarted (live at :3000).
- [x] No route/data-testid/API change; diff confined to console-web shell + task lifecycle (+ nav guard test update).
- [x] Task md + `INDEX.md` updated.
- [x] Reviewed + merged (impl PR #1057 squash `5ec37d67`, 3-dim verified; gateway-master E2E transient Docker-Hub timeout rerun→GREEN pre-merge).

---

분석=Opus 4.8 / 구현=Opus(직접). 사용자 요청 "상단 메뉴를 Vercel처럼 왼쪽 사이드바로, 테넌트 선택만 상단에". **메타: PC-FE-038 의 시맨틱 토큰 위에서 셸 구조만 재배치 — nav 를 client `usePathname` 사이드바로 추출, data-testid/href 보존으로 e2e/unit 무영향. 토글/로그아웃은 계정 컨트롤이라 상단 우측 유지(모바일 사용성).**
