# Task ID

TASK-PC-FE-037

# Title

Fix 도메인 상태 개요(`/dashboards/health`) showing "테넌트를 먼저 선택하세요" even AFTER a tenant is selected — the SSR `getDomainHealthState`/`fetchDomainHealth` never forwarded the request cookies to the in-process proxy fetch (the TASK-PC-FE-030 server-side cookie-forward fix was applied to the operator-overview sibling but MISSED on domain-health), so the proxy's `cookies()` read empty → `400 NO_ACTIVE_TENANT` on every load regardless of the active-tenant cookie. Mirror the PC-FE-030 fix + wrap the React Query `queryFn`. console-web only; no producer/BFF/contract change.

# Status

done

# Owner

frontend-engineer (console-web — domain-health SSR fetch + react-query hook; no BE/producer/contract change)

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- code
- test

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Dependency Markers

- **mirrors the fix in**: [TASK-PC-FE-030](../done/) (forward Cookie on the server-side overview fetch, #790) — the operator-overview sibling (`features/operator-overview/api/operator-overview-api.ts`) lazy-imports `next/headers` `cookies()` and passes `(await cookies()).toString()` as the `Cookie` header to the in-process proxy fetch. The domain-health sibling (`features/domain-health/api/domain-health-api.ts`, TASK-PC-FE-013) was the structurally identical module but never received that fix.
- **surfaced by**: [TASK-PC-FE-036](../done/) live verification — after FE-036 defaulted the active tenant on login, 운영자 통합 개요 worked but 도메인 상태 개요 still gated even after an explicit tenant selection. That isolated the bug to domain-health (the overview was already cookie-forwarding via FE-030; domain-health was not).
- **no dependency on**: any producer / console-bff / contract change. The proxy route + composition route (`§ 2.4.9.2`) are byte-unchanged; the fix only makes the SSR fetch carry the session cookies the proxy already expects to read.

---

# Goal

도메인 상태 개요(`/dashboards/health`) shows the "테넌트를 먼저 선택하세요" gate on **every** load — even after the operator has selected a tenant (the active-tenant cookie IS set, the switcher shows it, and 운영자 통합 개요 works). Only domain-health is affected.

Root cause: the SSR route entry calls `getDomainHealthState()` → `fetchDomainHealth()`, which does `fetch(url, { credentials: 'include' })` **without forwarding the page's request cookies**. In a Next.js server component, Node `fetch` has no cookie jar and `credentials: 'include'` is a browser-only directive, so the internal fetch to the in-process proxy carries **no cookies**. The proxy reads `cookies()` → empty → fast-fails `400 NO_ACTIVE_TENANT` → the page renders the gate, regardless of the real active-tenant cookie.

The operator-overview sibling already fixed exactly this in TASK-PC-FE-030 (it forwards `(await cookies()).toString()` as the `Cookie` header). domain-health — the structurally identical module created in TASK-PC-FE-013 — never got the fix. This task applies the same forward to domain-health and wraps the React Query `queryFn` (the new optional `cookieHeader` param must not receive React Query's context object on the client path).

# Scope

## In Scope

console-web only — single bundled PR (no spec/contract/ADR change):

1. **`src/features/domain-health/api/domain-health-api.ts`**
   - `fetchDomainHealth(cookieHeader?: string)` — add the optional param; when present, set the `Cookie` request header (mirror `fetchOperatorOverview`).
   - `getDomainHealthState()` — lazy-import `next/headers` `cookies()` and pass `(await cookies()).toString()` to `fetchDomainHealth(...)` (mirror `getOperatorOverviewState`).
2. **`src/features/domain-health/hooks/use-domain-health.ts`** — wrap `queryFn: () => fetchDomainHealth()` (was `queryFn: fetchDomainHealth`) so React Query's context object is not passed as `cookieHeader` (type + runtime: the bare reference would send `Cookie: [object Object]` on the client retry).
3. **Tests** — `tests/unit/domain-health-api.test.ts`: mock `next/headers` `cookies()` (now lazy-imported by `getDomainHealthState`) + a regression test asserting the SSR path forwards the `Cookie` header to the fetch.
4. **Task md + `INDEX.md`** entry.

## Out of Scope

- **Any producer / console-bff / composition-contract change.** `§ 2.4.9.2` request/response/auth byte-unchanged; the proxy route is unchanged — the fix only supplies the cookies it already reads.
- **The proxy route handler** (`/api/console/dashboards/domain-health`) — unchanged; it correctly reads `cookies()`, the problem was the caller not sending them.
- **operator-overview** — already correct (FE-030); untouched.
- **The gate copy / NO_ACTIVE_TENANT proxy behavior** — correct for a genuinely tenant-less session; only the SSR cookie forwarding changes.

# Acceptance Criteria

- [ ] **AC-1** After selecting a tenant (active-tenant cookie set), navigating to `/dashboards/health` renders the `<DomainHealthScreen>` (5 cards) — NOT the "테넌트를 먼저 선택하세요" gate.
- [ ] **AC-2** `getDomainHealthState()` forwards the page's request cookies as the `Cookie` header on the in-process proxy fetch (regression test asserts `headers.Cookie` equals the request cookie string).
- [ ] **AC-3** `fetchDomainHealth()` called with no arg (client/React Query path) sends NO `Cookie` header (browser supplies cookies via `credentials: 'include'`); the `queryFn` wrapper prevents React Query's context object from leaking in as `cookieHeader`.
- [ ] **AC-4** A genuinely tenant-less session (no active-tenant cookie) still gates `NO_ACTIVE_TENANT` (the proxy fast-fail is unchanged; the fix only forwards cookies that exist).
- [ ] **AC-5** `console-web` vitest + `tsc --noEmit` + lint all green (MONO-166 PR CI gate). Diff scope = `domain-health-api.ts` + `use-domain-health.ts` + `domain-health-api.test.ts` + task lifecycle. No producer/BFF/contract/ADR.

# Related Specs

- [`specs/services/console-web/architecture.md`](../../specs/services/console-web/architecture.md) — Server vs Client Components; the SSR route entry composes server-side via the in-process proxy (cookies must be forwarded explicitly on the Node fetch).
- Sibling reference: `features/operator-overview/api/operator-overview-api.ts` (the already-correct cookie-forward pattern, TASK-PC-FE-030).

# Related Contracts

- [`console-integration-contract.md`](../../specs/contracts/console-integration-contract.md) § 2.4.9.2 (Domain Health composition route) — **byte-unchanged**. The proxy still requires `X-Tenant-Id` and fast-fails `NO_ACTIVE_TENANT` when genuinely absent; the fix only makes the SSR fetch carry the session cookies the proxy reads to derive `X-Tenant-Id`.

# Edge Cases

- **Genuinely tenant-less session** (`'*'` operator before selection): no active-tenant cookie → the forwarded Cookie header has no `console_active_tenant` → proxy still fast-fails `NO_ACTIVE_TENANT` → gate (correct).
- **Client-side retry** (`<RetryButton>` → `useDomainHealth`): the browser attaches HttpOnly cookies natively via `credentials: 'include'`; the `queryFn` wrapper calls `fetchDomainHealth()` with no `cookieHeader` so no bogus header is sent.
- **Empty cookie string**: `(await cookies()).toString()` may be empty for an unauthenticated request, but the `(console)` layout guard redirects to `/login` before the page renders, so the health page only runs with a session.

# Failure Scenarios

- **React Query context leaking as cookieHeader** — `queryFn: fetchDomainHealth` (bare) would pass React Query's context object as `cookieHeader` → `Cookie: [object Object]` on the client retry (+ a tsc error). Mitigation: the `() => fetchDomainHealth()` wrapper (AC-3) + tsc gate.
- **Same bug recurring on a future composition route** — any new SSR composition fetcher must forward cookies like overview/health. Mitigation: this task + FE-030 establish the pattern; the regression test guards domain-health.
- **Green-wash** — the pre-fix `getDomainHealthState` tests mocked `fetch` globally so they never exercised cookie forwarding (the bug slipped past them). Mitigation: AC-2 adds an explicit `headers.Cookie` assertion.

---

분석=Opus 4.8 / 구현 권장=Sonnet 4.6 (mechanical mirror of the proven TASK-PC-FE-030 cookie-forward pattern onto the domain-health sibling + the queryFn wrap; small, well-understood, regression-tested). 직접 수행 + 로컬 vitest 780/780 + tsc + lint green 선검증.
