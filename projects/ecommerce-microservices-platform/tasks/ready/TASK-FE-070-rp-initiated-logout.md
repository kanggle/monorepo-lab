# TASK-FE-070: web-store logout = RP-initiated OIDC logout (GAP end_session)

## Goal

web-store logout terminates the GAP IdP session so the next sign-in re-presents the credential form (no silent re-auth). Today `logout()` only calls NextAuth `signOut` (clears the local session cookie); the GAP (SAS) IdP session survives → silent re-authentication on the next login. Same defect class as platform-console (TASK-PC-FE-033) and fan-platform-web (TASK-FAN-FE-002).

## Scope

- `shared/auth/auth.ts` jwt callback: capture `account.id_token` → `token.idToken` (server-side only; never copied onto the public session).
- `shared/auth/types.d.ts`: add `idToken?: string` to the JWT augmentation.
- New `shared/auth/federated-logout.ts` (`server-only`): read the id_token from the encrypted NextAuth cookie via `getToken` and build the GAP `end_session` URL (`id_token_hint` + `post_logout_redirect_uri` = app origin + `/` + `client_id`). Returns null when there is no id_token.
- New route `app/api/auth/end-session-url/route.ts` (GET): returns `{ url }` for the current session (literal segment → precedes the NextAuth `[...nextauth]` catch-all).
- `features/auth/model/auth-context.tsx` `logout()`: web-store logout is client-driven, so fetch the end_session URL FIRST (while the id_token cookie still exists), then `signOut({ redirect: false })`, then `window.location.href = url ?? '/'`.
- Update `auth-context.test.tsx` + `logout-cart-integration.test.tsx` for the new flow (fetch → `signOut({ redirect: false })` → navigate).

## Acceptance Criteria

- **AC-1** After logout, the next sign-in presents the GAP credential form again (no silent re-auth). Verified live: login → logout → the auth-service login form appears.
- **AC-2** The `id_token` is read server-side (`getToken`) and never exposed via `/api/auth/session`.
- **AC-3** `post_logout_redirect_uri` exactly matches a registered V0012 URI (app origin + `/`), so GAP `/connect/logout` returns 302 (not 403).
- **AC-4** No id_token / lookup failure → fallback to a local-only logout (`/`).
- **AC-5** `next lint` + `tsc --noEmit` clean; the two updated vitest specs pass (CI gate: "Frontend unit tests (ecommerce + fan-platform, vitest)").
- **AC-6** Cart + token-bridge cleanup on logout is preserved (existing behaviour).

## Related Specs

- `projects/ecommerce-microservices-platform/specs/integration/gap-integration.md`
- `projects/global-account-platform/specs/features/consumer-integration-guide.md`

## Related Contracts

- `projects/global-account-platform/specs/contracts/auth-api.md` (§ OIDC `end_session` / `/connect/logout`)

## Edge Cases

- Legacy session minted before id_token capture → `getToken` returns no idToken → local-only logout.
- `/api/auth/end-session-url` network failure → caught → local-only logout (`/`).
- Secure (`__Secure-`) vs plain cookie name → helper detects which cookie exists and passes the matching salt/cookieName to `getToken`.

## Failure Scenarios

- GAP unreachable at logout → the local session is still cleared (`signOut`) and the browser navigates to `/`; end_session is best-effort.
- `post_logout_redirect_uri` mismatch → GAP 403; AC-3 guards the exact app-origin + `/` form.

## Dependency Markers

- **depends on**: TASK-BE-328 (GAP `OAuthClientMapper` now copies `post-logout-redirect-uris` onto the SAS `RegisteredClient` — without it the V0012-seeded web-store post-logout URIs are inert and `/connect/logout` 403s). On `origin/main`.
- **mirrors**: TASK-PC-FE-033 / BE-328 + TASK-FAN-FE-002.
