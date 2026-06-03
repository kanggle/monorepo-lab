# Task ID

TASK-PC-FE-040

# Title

`console-web` — on a successful tenant switch, **re-apply the currently-viewed page to the new tenant's entitlements in place** by refreshing the route's server components (`router.refresh()`), instead of leaving the current view stale. (Also fixes a PC-FE-038 retheme leftover: the tenant switcher label/single-tenant text still used `text-primary-foreground` from the old dark top bar.)

# Status

review

# Owner

frontend-engineer (tenant-switch hook + switcher styling; no API/contract/domain change)

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- code

---

# Dependency Markers

- **follows**: PC-FE-039 (sidebar) / PC-FE-038 (retheme) / ADR-MONO-020 (assume-tenant exchange — the switch already re-scopes the signed token server-side; this surfaces it in the current view).
- **root cause**: `useTenantSwitch` only invalidated react-query `['catalog']`/`['session']` on success. The console's tenant-scoped domain views are rendered by **server components** keyed on the active-tenant cookie + assumed token; `invalidateQueries` re-runs only client queries, so the current page stayed on the previous tenant's render until manual navigation.
- **no dependency on**: any backend/contract/ADR change. The `/api/tenant` route + assume-tenant exchange already exist (ADR-MONO-020); this is the client-side view-refresh wiring.

# Goal

Selecting a tenant immediately re-evaluates the page the operator is on against the new tenant's permissions: an entitled domain shows live data; a non-entitled domain shows that section's forbidden/not-eligible state — without a manual refresh or navigation.

# Scope

## In Scope

- **`features/tenant/hooks/use-tenant-switch.ts`**: add `useRouter().refresh()` to the mutation `onSuccess` (after the existing query invalidations). Re-runs the current route's server components with the re-scoped token.
- **`features/tenant/components/TenantSwitcher.tsx`**: fix the retheme leftover — label + single-tenant span `text-primary-foreground/80` → `text-muted-foreground` (the old dark `bg-primary` top bar is gone; on the new `bg-background` bar that near-white text was low-contrast).
- **`tests/unit/TenantSwitcher.test.tsx`**: mock `next/navigation` `useRouter` (no App Router context in jsdom) + assert `refresh()` IS called on a successful switch and is NOT called on a rejected (403) switch.

## Out of Scope

- Hiding non-entitled domains from the sidebar nav per tenant (catalog-driven nav) — the current view re-applies (shows forbidden), but the static sidebar still lists all domains. A catalog-gated nav is a separate feature.
- Any `/api/tenant` / assume-tenant / backend change.
- Redirecting away from a now-forbidden page — the desired behaviour is in-place re-evaluation (the section renders its forbidden state), not a redirect.

# Acceptance Criteria

- [x] **AC-1** A successful tenant switch calls `router.refresh()` → the current route's server components re-render with the new tenant's assumed token, re-applying the entitlement gate in place (verified by `TenantSwitcher.test.tsx` refresh-on-success).
- [x] **AC-2** A rejected switch (403 cross-tenant) does NOT refresh (fail-closed) and still surfaces the "전환 실패" alert (verified — `refreshMock` not called on error).
- [x] **AC-3** Tenant switcher label/single-tenant text → `text-muted-foreground`/`text-foreground` (readable on `bg-background` bar, both modes). `pnpm test` 781/781 + `tsc --noEmit` exit0 + `next lint` clean + `next build` success.

# Related Specs

- `console-integration-contract.md` § 2.4 (per-domain entitlement gate) + ADR-MONO-020 (assume-tenant active-tenant scoping). `architecture.md` § Server vs Client Components (server components are the tenant-scoped render; `router.refresh()` is the App Router primitive to re-run them).

# Edge Cases

- **On a non-entitled domain after switch**: the section's server component re-fetches with the new token → 403 → renders its existing forbidden/not-eligible state (no crash, no redirect).
- **Overview/health pages**: `router.refresh()` re-runs their SSR composition (per-card entitlement) → cards flip per the new tenant (mirrors the federation tenant-switch-rescope behaviour).

# Failure Scenarios

- If `router.refresh()` fired on a failed switch, the view would re-render with a token that did not change (or a half-changed state) — avoided: refresh is in `onSuccess` only (verified by AC-2 test).

# Test Requirements

- `tests/unit/TenantSwitcher.test.tsx`: refresh-on-success + no-refresh-on-error (with `next/navigation` mock).
- `pnpm test` + `tsc --noEmit` + `next lint` + `next build` green.
- Local rebuild + container restart for live confirmation at `http://localhost:3000`.

# Definition of Done

- [x] `router.refresh()` on switch success + switcher retheme fix + tests.
- [x] `pnpm test` + `tsc --noEmit` + `next lint` + `next build` green.
- [x] Local federation-e2e `console-web` rebuilt + restarted (live at :3000).
- [x] No API/route/contract change; diff confined to the tenant feature + test.
- [x] Task md + `INDEX.md` updated.
- [ ] Reviewed + merged (3-dim verified) — pending close chore.

---

분석=Opus 4.8 / 구현=Opus(직접). 사용자 요청 "테넌트 선택 시 현재 보고있는 메뉴에서 테넌트의 권한에 따라 다시 적용". **메타: 멀티테넌트 콘솔의 권한-게이트 렌더는 서버 컴포넌트(active-tenant 쿠키+assumed token)라 react-query invalidate 만으로는 현재 화면이 stale — `router.refresh()` 가 App Router 에서 현재 라우트 서버 컴포넌트를 재실행해 in-place 재평가. assume-tenant 가 토큰을 re-scope 하므로 refresh 한 번으로 entitled→데이터 / non-entitled→forbidden 자동 전환.**
