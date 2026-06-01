# TASK-FE-071: admin-dashboard logout = RP-initiated OIDC logout (GAP end_session)

> **Status: DONE (2026-06-01)** — impl PR #1002 (squash `25e7fff7`). admin-dashboard RP-initiated OIDC logout — web-store(FE-070) client-driven 패턴 기계적 미러(client=`ecommerce-admin-dashboard-client`, port 3001, OPERATOR). id_token 캡처(jwt callback) + `federated-logout.ts`(server-only `getToken`→GAP end_session URL, post_logout_redirect_uri=app origin+`/` exact-match V0012) + route `/api/auth/end-session-url` + `auth-context` logout=URL fetch→`signOut({redirect:false})`→navigate(fallback `/login`). 테스트 갱신 불필요(AuthGuard/Sidebar가 `useAuth` 모킹). BE-328로 V0012 admin URI effective. tsc+lint clean, CI green. **= 모든 GAP-OIDC 프런트(console/fan/web-store/admin) 로그아웃 패리티 sweep 완결.** **AC-1 라이브 브라우저는 후속 스모크**((A) 결정; 잔여리스크 낮음=GAP수락 BE-328 일반증명+graceful local-only fallback). 3차원 ✓.

## Goal

admin-dashboard logout terminates the GAP IdP session so the next sign-in re-presents the credential form (no silent re-auth). Today `logout()` only calls NextAuth `signOut` (clears the local session cookie); the GAP (SAS) IdP session survives → silent re-authentication on the next login. Completes the RP-initiated-logout parity sweep across all GAP-OIDC frontends (platform-console PC-FE-033, fan-platform-web FAN-FE-002, web-store FE-070, and now admin-dashboard).

## Scope

- `shared/auth/auth.ts` jwt callback: capture `account.id_token` → `token.idToken` (server-side only; never copied onto the public session).
- `shared/auth/types.d.ts`: add `idToken?: string` to the JWT augmentation.
- New `shared/auth/federated-logout.ts` (`server-only`): read the id_token from the encrypted NextAuth cookie via `getToken` and build the GAP `end_session` URL (`id_token_hint` + `post_logout_redirect_uri` = app origin + `/` + `client_id=ecommerce-admin-dashboard-client`). Returns null when there is no id_token.
- New route `app/api/auth/end-session-url/route.ts` (GET): returns `{ url }` (literal segment → precedes the NextAuth `[...nextauth]` catch-all).
- `shared/hooks/auth-context.tsx` `logout()`: client-driven, so fetch the end_session URL FIRST (while the id_token cookie still exists), then `signOut({ redirect: false })`, then `window.location.href = url ?? '/login'`.

Near-mechanical mirror of TASK-FE-070 (web-store, client-driven variant), adapted for the admin client (`ecommerce-admin-dashboard-client`, port 3001, OPERATOR). No existing logout test asserts the `logout()` internals (the AuthGuard/Sidebar tests mock `useAuth`), so no test update is required.

## Acceptance Criteria

- **AC-1** After logout, the next sign-in presents the GAP credential form again (no silent re-auth). Verified live: login → logout → the auth-service login form appears.
- **AC-2** The `id_token` is read server-side (`getToken`) and never exposed via `/api/auth/session`.
- **AC-3** `post_logout_redirect_uri` exactly matches a registered V0012 URI (app origin + `/`), so GAP `/connect/logout` returns 302 (not 403).
- **AC-4** No id_token / lookup failure → fallback to a local-only logout (`/login`).
- **AC-5** `next lint` + `tsc --noEmit` clean; existing admin-dashboard vitest suite stays green (CI gate).

## Related Specs

- `projects/ecommerce-microservices-platform/specs/integration/gap-integration.md`
- `projects/global-account-platform/specs/features/consumer-integration-guide.md`

## Related Contracts

- `projects/global-account-platform/specs/contracts/auth-api.md` (§ OIDC `end_session` / `/connect/logout`)

## Edge Cases

- Legacy session minted before id_token capture → `getToken` returns no idToken → local-only logout (`/login`).
- `/api/auth/end-session-url` network failure → caught → local-only logout (`/login`).
- Secure (`__Secure-`) vs plain cookie name → helper detects which cookie exists and passes the matching salt/cookieName to `getToken`.
- After GAP returns to the registered root `/`, the now-anonymous request is bounced to `/login` by the `authorized` middleware.

## Failure Scenarios

- GAP unreachable at logout → the local session is still cleared (`signOut`) and the browser navigates to `/login`; end_session is best-effort.
- `post_logout_redirect_uri` mismatch → GAP 403; AC-3 guards the exact app-origin + `/` form.

## Dependency Markers

- **depends on**: TASK-BE-328 (GAP `OAuthClientMapper` now copies `post-logout-redirect-uris` onto the SAS `RegisteredClient` — without it the V0012-seeded admin post-logout URIs are inert and `/connect/logout` 403s). On `origin/main`.
- **mirrors**: TASK-PC-FE-033 / BE-328 + TASK-FAN-FE-002 + TASK-FE-070.
