# Task ID

TASK-PC-FE-033

# Title

Console logout = **RP-initiated OIDC logout** (OIDC end_session). Today the console logout only revokes tokens (RFC 7009) + clears its own cookies; the GAP **IdP session** (SAS authenticated session at auth-service) survives, so the next "GAP 계정으로 로그인" silently re-authenticates WITHOUT the credential form. Fix: terminate the IdP session by redirecting the browser to the OIDC `end_session_endpoint` (`/connect/logout`) with `id_token_hint` + a registered `post_logout_redirect_uri`. Requires registering `post-logout-redirect-uris` on the GAP `platform-console-web` client (currently absent) + storing the `id_token` console-side.

# Status

done

# Owner

frontend

# Task Tags

- code
- security
- auth
- oidc

---

# Dependency Markers

- **observed**: live federation-e2e stack — logout button (header top-right) → `/login` → "GAP 계정으로 로그인" → immediate login, no credential prompt. Root cause: IdP SSO session not terminated.
- **cross-project (atomic PR)**: ① GAP auth-service Flyway (register `post-logout-redirect-uris` on `platform-console-web`) + ② platform-console console-web (capture `id_token`, RP-initiated logout flow). Land in one PR.
- **model**: 분석=Opus 4.8 / **구현 권장=Opus** (auth/OIDC-sensitive cross-project; fail-safe degradation required).

---

# Root Cause (verified, live stack)

- auth-service OIDC discovery exposes `end_session_endpoint = ${issuer}/connect/logout` (SAS OIDC logout). **Console never calls it.**
- `console-web/app/api/auth/logout/route.ts` = `/oauth2/revoke` (RFC 7009, token invalidation) + clear cookies (access/refresh/operator/tenant/assumed) → 204. Token revocation does NOT end the SAS HttpSession that backs `/oauth2/authorize`.
- `console-web/app/api/auth/login/route.ts` builds `/oauth2/authorize` with **no `prompt=login`** → with a live IdP session, the authorization endpoint silently re-issues a code (no form).
- GAP `platform-console-web` client (`V0015`) `client_settings` has only `require-proof-key` + `require-authorization-consent` — **no `post-logout-redirect-uris`** (V0016 fixed fan/ecommerce typing but explicitly left platform-console-web untouched because it had none).

# Goal

A console logout that ends the **whole** session — console cookies **and** the GAP IdP session — so the next login presents the GAP credential form. Standard OIDC RP-initiated logout. Degrade safely (if no `id_token` available, fall back to the current local-only logout → `/login`).

# Scope

## In scope

### A. GAP auth-service (project-internal)
- New Flyway `V0021__add_post_logout_redirect_uris_platform_console.sql`: add `settings.client.post-logout-redirect-uris` to the `platform-console-web` row's `client_settings` JSON, in the SAS default-typed array form (mirror `V0016`: `["java.util.ArrayList",["http://console.local/login","http://localhost:3000/login"]]`). MySQL `JSON_SET` structural mutation + NULL-safe idempotency guard (the V0016 cycle-3 pattern — no user variables, inlined JSON path, `JSON_SEARCH(... 'java.util.ArrayList') IS NULL` guard). Forward-only.
  - Register BOTH the dev (`http://console.local/login`) and the e2e/local (`http://localhost:3000/login`) post-logout targets, matching the `redirect_uris` pair already seeded in V0015.
- A test pinning the seed (mirror `OAuthClientPostLogoutRedirectUriSeedIntegrationTest` or a unit assertion on the mapper) — the registered URIs round-trip through `OAuthClientMapper` (typed array deserializes; the console's exact post-logout URI is present).

### B. platform-console console-web (project-internal)
- `shared/lib/session.ts`: add `ID_TOKEN_COOKIE = 'console_id_token'` (same `tokenCookieOpts`) + `getIdToken()`; document it is the `id_token_hint` source for RP-initiated logout (never a credential).
- `app/api/auth/callback/route.ts`: the token response already parses `id_token` (optional) — store it in `ID_TOKEN_COOKIE` alongside the access token; on the operator-exchange fail-closed path that drops the GAP cookies, also drop `ID_TOKEN_COOKIE` (no orphan).
- `app/api/auth/refresh/route.ts`: capture the rotated `id_token` (add to schema, optional) and update `ID_TOKEN_COOKIE` so the hint stays fresh; on the two whole-session-drop paths, also clear `ID_TOKEN_COOKIE`.
- `app/api/auth/logout/route.ts`: read `id_token` (+ access/refresh) BEFORE clearing; keep the best-effort `/oauth2/revoke`; clear ALL cookies INCLUDING `ID_TOKEN_COOKIE`; **build the end-session URL** `${OIDC_ISSUER_URL}/connect/logout?id_token_hint=<id_token>&post_logout_redirect_uri=<NEXT_PUBLIC_APP_URL>/login&client_id=<OIDC_CLIENT_ID>` and return it as `{ logoutUrl }` (200). **Fallback**: if no `id_token` cookie, return `{ logoutUrl: <NEXT_PUBLIC_APP_URL>/login }` (current local-only behavior — never break logout).
- `features/auth/components/LogoutButton.tsx`: on click, `POST /api/auth/logout`, read `{ logoutUrl }`, `window.location.assign(logoutUrl)` (replaces the hard-coded `/login`). Keep `data-testid="logout-button"` + the disabled/busy state.

### C. Contract / spec (additive)
- `console-integration-contract.md` (or the auth section): note logout is RP-initiated (end_session_endpoint) + the `platform-console-web` `post-logout-redirect-uris` registration. Additive (HARDSTOP-04).

## Out of scope
- `prompt=login` on the authorize request (alternative B — rejected: it does not terminate the IdP session and SAS support is version-dependent; RP-initiated logout is the correct standard fix).
- Operator-token / assume-tenant flows (unchanged).
- Other clients' post-logout config (fan/ecommerce already handled by V0016).

# Acceptance Criteria

- **AC-1 (IdP session terminated)**: after logout, navigating to login presents the GAP credential form again (no silent re-auth). Verified on the live federation-e2e stack: login as `multi-operator@example.com` → logout → "GAP 계정으로 로그인" → **the auth-service login form appears** (not an immediate dashboard).
- **AC-2 (RP-initiated)**: logout browser navigation hits `${issuer}/connect/logout` with `id_token_hint` + `post_logout_redirect_uri=${app}/login`; SAS terminates the session and 302s back to `${app}/login`.
- **AC-3 (post-logout-redirect registered)**: GAP `platform-console-web` client carries `post-logout-redirect-uris = [http://console.local/login, http://localhost:3000/login]` in the SAS typed-array form; round-trips through `OAuthClientMapper` (no `InvalidTypeIdException`). Tested.
- **AC-4 (cookies fully cleared)**: access/refresh/operator/tenant/assumed/**id_token** cookies all cleared on logout (cookie clear remains the source of truth — even if `/connect/logout` or revoke fails, the console session ends).
- **AC-5 (safe degradation)**: if no `id_token` cookie exists (legacy session), logout falls back to local-only clear + `/login` (never errors, never hangs).
- **AC-6 (no regression)**: callback/refresh still establish a working session; `isAuthenticated()` unchanged; the assume-tenant D4 switch flow unaffected. console-web typecheck + existing auth-route tests GREEN; auth-service `:integrationTest` GREEN (new seed test + existing client mapper tests).

# Related Specs / Code

- `projects/platform-console/apps/console-web/src/app/api/auth/{logout,callback,refresh}/route.ts` + `shared/lib/session.ts` + `features/auth/components/LogoutButton.tsx`.
- `projects/global-account-platform/apps/auth-service/src/main/resources/db/migration/V0021__...sql` (new) + `V0015` (client seed) + `V0016` (typed-array form reference) + `OAuthClientMapper` + `OAuthClientPostLogoutRedirectUriSeedIntegrationTest`.
- OIDC discovery `end_session_endpoint` (`/connect/logout`); `auth-api.md` (logout/revoke sections).

# Edge Cases / Failure Scenarios

- **No id_token (legacy/initial)**: fall back to local-only logout → `/login`. AC-5.
- **Expired id_token_hint**: SAS tolerates an expired (but signature-valid) `id_token_hint` for logout; the session cookie is what's invalidated. Acceptable; the refresh-side id_token update keeps it fresh anyway.
- **post_logout_redirect_uri exact-match**: SAS matches the sent URI against the registered set exactly — send `${NEXT_PUBLIC_APP_URL}/login` with NO query string; register that exact value (both hosts).
- **revoke/end_session unavailable**: cookie clear is the source of truth — the console session ends regardless; the browser still navigates to the end-session URL (best-effort IdP logout).
- **CSRF**: `/connect/logout` with a valid `id_token_hint` is not a blind-CSRF logout vector (the hint binds to the client+session); without id_token_hint SAS would show a confirmation page (our fallback path doesn't hit end_session at all).

# Notes

- Cross-project atomic PR (GAP migration + console-web). Verify AC-1 live on the running federation-e2e stack (hot-rebuild auth-service + console-web images). console-web vitest is not a CI gate; auth-service `:integrationTest` IS.
