# Task ID

TASK-PC-FE-036

# Title

Fix the 운영자 통합 개요(`/dashboards/overview`) + 도메인 상태 개요(`/dashboards/health`) first-load bug where the tenant-scoped screens show the "테넌트를 먼저 선택하세요" gate even though the tenant switcher displays a tenant as selected — (A) default the active tenant to the operator's home `tenant_id` on login so the overviews work on first load; (B) make the switcher render an honest UNSELECTED placeholder (not a silent `tenants[0]` default) for platform/no-home operators. console-web only; no producer/BFF/contract change.

# Status

done

# Owner

frontend-engineer (console-web — auth callback + tenant switcher; no BE/producer/contract change)

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

- **fixes a defect surfaced by**: live use of the running console (TASK-PC-FE-034 promoted the 5-domain overview to the console home `/dashboards/overview`; the first-load gate is hit immediately on landing). Reported symptom: "처음에 테넌트 선택되어있는데 선택하라고 뜨는 문제 (선택하고 나면 됨)".
- **interacts with (unchanged)**: [ADR-MONO-020](../../../../docs/adr/ADR-MONO-020-operator-multitenant-assignment.md) D4 active-tenant switcher → assume-tenant flow. The fix DEFAULTS the active tenant to the operator's **home** tenant (the login token's `tenant_id`), which is served by the base access token — **no assume-tenant exchange** (getDomainFacingToken falls back to the base token, already scoped to home). Switching to a NON-home assigned tenant still drives the assume-tenant exchange via `/api/tenant` (D4 unchanged). The platform sentinel `'*'` has no single home → no default → explicit selection (consistent with D4 / TASK-MONO-169's `'*'`-is-assignment-inexpressible finding).
- **interacts with (unchanged)**: [ADR-MONO-019](../../../../docs/adr/ADR-MONO-019-customer-tenant-model.md) D3-A single-value `admin_operators.tenant_id` — the home tenant the active selection now defaults to IS the D3-A scope; this is the MONO-154 single-tenant runtime (base token tenant_id=home + entitled_domains), now wired for first-load instead of only after an explicit switch.
- **no dependency on**: any producer / console-bff / contract change. The BFF proxy + composition routes (`§ 2.4.9.1/.2`) are byte-unchanged; the fix only changes WHEN the active-tenant cookie is first set + how the switcher renders the unset state.

---

# Goal

On first load after login, the tenant-scoped overviews (운영자 통합 개요 `/dashboards/overview`, 도메인 상태 개요 `/dashboards/health`) show the "테넌트를 먼저 선택하세요" gate (the BFF proxy fast-fails `NO_ACTIVE_TENANT` because the `console_active_tenant` cookie is unset) — **while the top-bar tenant switcher simultaneously shows a tenant as selected**. The two contradict each other; only after the operator (re-)selects a tenant in the switcher does the overview work.

Root cause (two parts):

1. **The OAuth callback never sets `console_active_tenant`.** It stores access/refresh/operator/id_token cookies but no active tenant, so `getActiveTenant()` is `null` for *every* operator on first load → the proxy gate fires.
2. **The switcher masks the unset state by defaulting to `tenants[0]`** (`useState(activeTenant ?? tenants[0] ?? '')`). The `<select>` visually shows the first tenant as selected, but no `onChange` fired, so the cookie was never set — the UI says "acme-corp selected" while the server says "no tenant".

Fix:

- **(A)** Default the active tenant to the operator's **home** tenant on login: the GAP OIDC access token already carries `tenant_id=<home>` (+ `entitled_domains`), so the callback sets `console_active_tenant=<home>` when `tenant_id` is a real customer (not the `'*'` platform sentinel). The base token is already scoped to home → the overviews compose immediately, no assume-tenant needed.
- **(B)** For an operator with no single home (`'*'` platform operator), the callback leaves the active tenant unset and the switcher renders an **unselected placeholder** ("테넌트 선택…") instead of silently defaulting to `tenants[0]` — the switcher is now honest (no false "selected" state), and the gate + switcher agree until the operator explicitly picks a customer (which drives the assume-tenant exchange).

# Scope

## In Scope

console-web only — a single bundled PR (no spec/contract/ADR change; precedent `feedback_pr_bundling`):

1. **`src/shared/lib/jwt.ts`** (new) — `readJwtClaim(token, claim)` (verification-free payload read; SAFE only because the token just came from the trusted GAP exchange — NOT an authz check) + `homeTenantFromAccessToken(accessToken)` (returns `tenant_id` when a real customer; `null` for absent / empty / `'*'`).
2. **`src/app/api/auth/callback/route.ts`** — after the operator-token exchange succeeds, set `TENANT_COOKIE` to `homeTenantFromAccessToken(access_token)` when non-null (maxAge = `expires_in`). Also: the operator-exchange failure path now additionally clears `TENANT_COOKIE` + `ASSUMED_TOKEN_COOKIE` (no partial authed state — consistency with the existing 4-cookie clear).
3. **`src/features/tenant/components/TenantSwitcher.tsx`** — `useState(activeTenant ?? '')` (was `?? tenants[0] ?? ''`) + render a disabled `테넌트 선택…` placeholder option when `selected === ''`. No silent default to `tenants[0]`.
4. **Tests** — `tests/unit/jwt.test.ts` (home-tenant extraction: real / `'*'` / absent / empty / malformed) + `TenantSwitcher.test.tsx` (activeTenant=null → placeholder, value `''`, not `tenants[0]`; activeTenant set → that tenant selected, no placeholder).
5. **Task md + `INDEX.md`** entry.

## Out of Scope

- **Auto-MINTING an assumed token on login.** The default is the *home* tenant, served by the base token (no assume-tenant). Switching to a non-home assigned tenant keeps the D4 assume-tenant flow unchanged.
- **Defaulting a tenant for the `'*'` platform operator.** No single home → explicit selection (the placeholder + gate stand).
- **Any producer / console-bff / composition-contract change.** `§ 2.4.9.1/.2` request/response/auth byte-unchanged; the proxy gate logic is unchanged (it still fast-fails on a genuinely-absent active tenant).
- **The single-tenant static-label branch** (`tenants.length === 1`) — unchanged; (A) sets that operator's home cookie on login so its overview now works and the label is accurate.
- **Changing the gate copy / the proxy NO_ACTIVE_TENANT behavior** — the gate is correct for a genuinely tenant-less session (the `'*'` operator before selection); only the first-load default + switcher honesty change.

# Acceptance Criteria

- [ ] **AC-1** After login as a real-customer operator (token `tenant_id=<home>`, e.g. acme-corp), `console_active_tenant` is set to `<home>` by the callback; `/dashboards/overview` and `/dashboards/health` render their content on first load (no `NO_ACTIVE_TENANT` gate), with no manual switcher interaction.
- [ ] **AC-2** After login as the `'*'` platform operator, the callback leaves `console_active_tenant` unset; the switcher shows an UNSELECTED placeholder ("테넌트 선택…", `value=''`) — NOT `tenants[0]` — and the overview gate stands until the operator explicitly selects a customer.
- [ ] **AC-3** `homeTenantFromAccessToken` returns the `tenant_id` for a real customer, `null` for `'*'` / absent / empty, and never throws on a malformed token.
- [ ] **AC-4** `TenantSwitcher` with `activeTenant=null` has the `<select>` value `''` (placeholder selected, disabled) and does not pre-select `tenants[0]`; with `activeTenant=X` the value is `X` and no placeholder option renders.
- [ ] **AC-5** The operator-exchange failure path clears `TENANT_COOKIE` + `ASSUMED_TOKEN_COOKIE` along with the access/refresh/operator/id_token cookies (no partial authed state).
- [ ] **AC-6** No assume-tenant exchange is triggered by the login default (the base access token serves the home tenant); switching to a non-home assigned tenant still drives `/api/tenant` → assume-tenant (D4 unchanged).
- [ ] **AC-7** `console-web` vitest + `tsc --noEmit` + lint all green (MONO-166 PR CI gate). Diff scope = `console-web` `src/shared/lib/jwt.ts` + `api/auth/callback/route.ts` + `features/tenant/components/TenantSwitcher.tsx` + 2 tests + task lifecycle. No producer/BFF/contract/ADR.

# Related Specs

- [`specs/services/console-web/architecture.md`](../../specs/services/console-web/architecture.md) — Authentication (HttpOnly cookie session) + multi-tenant active-tenant; the fix sets the active-tenant cookie at login and keeps the switcher honest.
- [ADR-MONO-020](../../../../docs/adr/ADR-MONO-020-operator-multitenant-assignment.md) D4 (active-tenant switcher → assume-tenant; the home-tenant default needs no assume) — unchanged.
- [ADR-MONO-019](../../../../docs/adr/ADR-MONO-019-customer-tenant-model.md) D3-A (single-value home tenant — the default's source) — unchanged.

# Related Contracts

- [`console-integration-contract.md`](../../specs/contracts/console-integration-contract.md) § 2.4.9.1 (operator overview) / § 2.4.9.2 (domain health) — **byte-unchanged**. The proxy still requires `X-Tenant-Id` and fast-fails `NO_ACTIVE_TENANT` when genuinely absent; the fix only changes when the active tenant is first established (login default) + the switcher's unset-state rendering.

# Edge Cases

- **Platform `'*'` operator**: no home tenant → no login default → switcher placeholder → must explicitly select a customer (which drives the assume-tenant exchange, since the `'*'` login token is not scoped to a specific customer). Gate is correct here.
- **Multi-assignment operator** (home + others): defaults to the home tenant on login (works immediately via base token); switching to a non-home assigned tenant drives assume-tenant (D4). The default is the home, not a union.
- **`tenant_id` claim absent / malformed**: `homeTenantFromAccessToken` returns `null` → no default → placeholder + gate (degrades to the pre-fix behavior for that operator, never throws).
- **Stale `console_active_tenant` from a prior session on a failed login**: the operator-exchange failure path now clears it (+ assumed token) so a failed login leaves no tenant pointing at an unauthenticated session.
- **Post-switch server re-render**: the switcher is a client component; after `/api/tenant` the `selected` state reflects the pick immediately; the `activeTenant` prop updates on the next server render (existing behavior; unchanged).

# Failure Scenarios

- **Unverified claim misused for authz** — `readJwtClaim` is verification-free; if it were ever used as an authorization signal it would be a trust-boundary hole. Mitigation: it is used ONLY to pick a UI default (the active-tenant cookie); the BFF + domains verify the RS256 signature and the domain entitlement gates are the real authority. Documented in the util's JSDoc + this task.
- **Defaulting a tenant the operator may not act for** — the home `tenant_id` comes from the operator's OWN login token (`credentials.tenant_id`); it is by definition their scope. The proxy/domain gates re-check on every call. No widening.
- **`'*'` operator silently scoped to some tenant** — explicitly excluded (`homeTenantFromAccessToken` returns null for `'*'`); AC-2 asserts the unset + placeholder behavior.
- **Green-wash** — asserting "switcher shows a value" without asserting it is NOT the first tenant would miss the bug. Mitigation: AC-4 asserts `value === ''` (placeholder) AND `!== tenants[0]`.

---

분석=Opus 4.8 / 구현 권장=Opus 4.8 (auth-callback touch + active-tenant/assume-tenant model interaction needs precision — the "home tenant defaults via base token, non-home still assumes" reasoning is load-bearing; the surface is small but the correctness argument spans ADR-019/020). 직접 수행 + 로컬 vitest/tsc/lint green 선검증.
