# Task ID

TASK-PC-FE-041

# Title

`console-web` — console UI polish (Vercel iteration): **(A)** add an outer border line to every data table (`<table>` gets `border border-border`); **(B)** replace the visible top-bar **로그아웃** button with a **kebab (⋮) account menu**; **(C)** clicking the kebab opens a dropdown with **아이디** (the signed-in operator identity, read-only), **계정 설정** (a new read-only `/account` page), and **로그아웃** (the existing RP-initiated OIDC logout, preserved verbatim).

# Status

done

# Owner

frontend-engineer (console-web presentation layer — table styling + top-bar account control + a read-only account page; no API / contract / domain change)

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- code

---

# Dependency Markers

- **follows**: PC-FE-038 (Vercel retheme — design tokens `border-border`/`bg-popover`/`accent`), PC-FE-039 (left sidebar + top bar with brand · tenant switcher · theme toggle · logout), PC-FE-040 (tenant-switch re-apply).
- **no dependency on**: any backend / contract / ADR change. The account identity is read **verification-free** from the existing GAP OIDC `id_token` / access-token cookies (`shared/lib/jwt.ts` `readJwtClaim` — UI display only, NOT authorization; the BFF + federated domains verify RS256). The `/account` page reads the same claims + the active-tenant cookie. No new network call, no new route handler.
- **preserves**: the logout behaviour (`POST /api/auth/logout` → navigate to `logoutUrl` = OIDC `/connect/logout`; TASK-PC-FE-033) and its `data-testid="logout-button"` — only its placement (now a menu item inside the kebab) changes.

# Goal

The console matches the Vercel data-table aesthetic (bounded, bordered tables) and the Vercel account-control pattern (a compact kebab/avatar menu in the top bar rather than a bare logout button), and surfaces the signed-in operator's identity + a settings destination — without changing any data, route, credential, or contract behaviour.

# Scope

## In Scope

- **(A) Table borders** — every `<table>` in `console-web` (17 across wms-ops / scm-ops / finance-ops / erp-ops / operators / audit / accounts) uses `... border-collapse text-sm`; add `border border-border` → `... border-collapse border border-border text-sm`. Under `border-collapse` this renders a single clean outer frame; the existing `border-b border-border` header/row dividers collapse with it (no doubled line, no vertical gridlines — the Vercel look). Purely additive class, no markup/structure change.
- **(B) Account menu** — new client component `shared/ui/AccountMenu.tsx`: a kebab (⋮) icon button (`data-testid="account-menu-trigger"`, `aria-haspopup="menu"` + `aria-expanded`) toggling a dropdown panel (`role="menu"`). Closes on outside-click + Escape + item-activation. Replaces `<LogoutButton />` in `(console)/layout.tsx`'s top bar.
- **(B) Menu items** — (1) **아이디** header row (`data-testid="account-menu-id"`, read-only, non-interactive) showing the signed-in identity; (2) **계정 설정** link (`data-testid="account-menu-settings"`, → `/account`); (3) **로그아웃** menu item (`data-testid="logout-button"` preserved) running the existing logout flow.
- **logout flow** — extract `LogoutButton`'s fetch+navigate into `features/auth/lib/logout.ts` `performLogout()` (no behaviour change), called by the menu's 로그아웃 item. Remove the now-unused `LogoutButton.tsx` + its `features/auth/index.ts` export (sole consumer was the layout); export `performLogout` instead.
- **(C) `/account` page** — new `(console)/account/page.tsx`, server component, **read-only**: surfaces the operator's identity from the token claims (아이디 = `email`/`preferred_username`/`sub`, 홈 테넌트 = `tenant_id`, 권한 도메인 = `entitled_domains`) + the current active tenant (cookie), with a link to `/operators` for self-service profile editing (existing 내 프로필 / `me/profile` + `me/password`) and a note that credentials/profile are managed by GAP (the IdP). No mutation.
- **identity decode** — `shared/lib/jwt.ts`: add `decodeJwtPayload(token: string | null): Record<string, unknown> | null` (null-safe), refactor `readJwtClaim` to use it (behaviour preserved). Layout computes the display label from the `id_token` / access-token cookies and passes it to `AccountMenu`.
- **tests** — `tests/unit/AccountMenu.test.tsx` (kebab toggles the menu; shows the id; settings link `href="/account"`; logout item triggers `performLogout`; outside-click / Escape close); `tests/unit/jwt.test.ts` add `decodeJwtPayload` cases; a light `/account` render assertion if practical. `pnpm test` + `tsc --noEmit` + `next lint` + `next build` green.

## Out of Scope

- Hiding non-entitled domains from the sidebar (catalog-gated nav) — unchanged (PC-FE-040 Out of Scope).
- A mutable account-settings surface (password / default-account editing) on `/account` — that self-service UI already lives in `/operators` (MyProfileForm + change-password); `/account` links to it rather than duplicating it. No new mutation route.
- Any `/api/**` / GAP / assume-tenant / contract change. Avatar image / initials avatar (kebab icon only).
- Mobile nav drawer (the sidebar stays `hidden md:block`; the top-bar account control is visible on all sizes) — deferred (PC-FE-039 Out of Scope).

# Acceptance Criteria

- [x] **AC-1** Every `console-web` `<table>` renders with an outer `border border-border` frame (17 tables; verified by `next build` + visual). No data/markup/testid change; existing table data-testids intact.
- [x] **AC-2** The top bar no longer shows a bare 로그아웃 button; it shows a kebab (⋮) trigger (`account-menu-trigger`). Clicking it opens a `role="menu"` dropdown with 아이디 / 계정 설정 / 로그아웃; clicking outside or pressing Escape closes it (verified by `AccountMenu.test.tsx`).
- [x] **AC-3** The 로그아웃 menu item keeps `data-testid="logout-button"` and runs the unchanged `performLogout()` (POST `/api/auth/logout` → navigate to `logoutUrl`); 계정 설정 links to `/account`; 아이디 shows the decoded identity (verified by `AccountMenu.test.tsx`).
- [x] **AC-4** `/account` renders read-only operator identity from the token claims + active tenant, with a `/operators` self-service link; resilient when claims/tenant are absent (no crash, graceful fallback label). No mutation affordance.
- [x] **AC-5** `decodeJwtPayload` is null-safe and `readJwtClaim` behaviour is preserved (`jwt.test.ts`). `pnpm test` + `tsc --noEmit` exit 0 + `next lint` clean + `next build` success.

# Related Specs

- `console-integration-contract.md` § 2.1 (the `id_token` is NEVER a credential — used here only for display, mirroring `shared/lib/jwt.ts` verification-free read posture established in PC-FE-036) + § 2.4.3 (self-service `me/profile` / `me/password` already power `/operators` 내 프로필, which `/account` links to). `architecture.md` § Server vs Client Components (the layout/`/account` are server; `AccountMenu` is the client island for the dropdown interaction).
- ADR-MONO-015 / ADR-MONO-017 (console shell / nav) — additive UI affordance, no decision change. `/account` is an in-console nav destination reached from the account menu (the PC-FE-004 "in-console nav destination, NOT a catalog product" precedent).

# Edge Cases

- **No id_token / malformed token**: `decodeJwtPayload` returns null → the menu shows a generic "운영자" label (never a crash); `/account` shows what claims are present and omits absent fields.
- **Platform operator (`tenant_id='*'`)**: 홈 테넌트 renders the `'*'` platform-scope sentinel honestly (or "플랫폼 범위"); the active-tenant line reflects the cookie (may be unselected).
- **Keyboard**: the kebab is a real `<button>`; the menu items are focusable (`<a>` / `<button>`); Escape returns focus to the trigger.
- **Logout from the menu**: identical to before (cookie-clear is the source of truth even if GAP revoke fails — logout.test.ts route behaviour unchanged).

# Failure Scenarios

- If `performLogout` regressed (e.g. lost the `logoutUrl` navigation), the IdP session would survive → next login silently re-auths (the PC-FE-033 defect). Avoided: the fetch+navigate logic is moved verbatim into `performLogout()` and still asserted (logout-button testid + menu test).
- If the account label leaked a raw token or a sensitive claim, it would violate § 2.1. Avoided: only `email`/`preferred_username`/`sub` (display identifiers) are read; never the token string, never an authorization decision.

# Test Requirements

- `tests/unit/AccountMenu.test.tsx`: open/close (click, outside-click, Escape); 아이디 shown; 계정 설정 `href="/account"`; 로그아웃 item triggers `performLogout` (mocked).
- `tests/unit/jwt.test.ts`: `decodeJwtPayload` (valid → object; null/garbage/2-part → null) + `readJwtClaim` regression intact.
- `pnpm test` + `tsc --noEmit` + `next lint` + `next build` green.
- Local rebuild + container restart for live confirmation at `http://localhost:3000`.

# Definition of Done

- [x] (A) 17 tables bordered + (B) kebab account menu replacing the logout button + (C) read-only `/account` page + `performLogout` extraction + `decodeJwtPayload`.
- [x] `pnpm test` + `tsc --noEmit` + `next lint` + `next build` green.
- [x] Local federation-e2e `console-web` rebuilt + restarted (live at :3000).
- [x] No API/route/contract change; diff confined to console-web presentation + the new account page + tests.
- [x] Task md + `INDEX.md` updated.
- [x] Reviewed + merged (impl PR #1061 squash `671bfdb6`, 3-dim verified; all CI GREEN, no transient).

---

분석=Opus 4.8 / 구현=Opus(직접). 사용자 요청 "테이블 테두리에 선추가 / 상단 로그아웃 버튼 대신 점3개 아이콘 + 클릭 시 아이디·계정 설정·로그아웃". UI 폴리시 3건 묶음 (presentation only, no API/contract change) — `feedback_pr_bundling` 케이스별 판단.
