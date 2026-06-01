# Task ID

TASK-FAN-FE-002

# Title

fan-platform-web logout = **RP-initiated OIDC logout** (GAP `end_session`). Today the logout server action only calls NextAuth `signOut` (clears the local session cookie) — the GAP (SAS) **IdP session survives**, so the next sign-in silently re-authenticates WITHOUT the credential form. Same defect class as the platform-console (TASK-PC-FE-033) bug. Fix: capture the `id_token` on the JWT and redirect the browser to GAP `/connect/logout` with `id_token_hint` + the registered `post_logout_redirect_uri`, so the IdP terminates its own session.

# Status

done

> **완료 (2026-06-01)**: impl PR #1000 (squash `158253bf`, fan+ecommerce 묶음). fan-platform-web RP-initiated OIDC logout — id_token 캡처(jwt callback) + `federated-logout.ts`(server-only `getToken`→GAP end_session URL, post_logout_redirect_uri=app origin+`/` exact-match V0011) + Header server-action `signOut({redirectTo: endSession ?? '/login'})`. BE-328(매퍼가 post-logout→RegisteredClient 복사)로 V0011 fan URI effective. tsc+lint clean, CI green. **AC-1 라이브 브라우저(로그인→로그아웃→폼 재노출)는 후속 스모크**(잔여 리스크 낮음: GAP 수락 BE-328 일반증명 + null→local-only fallback; (A) 결정). 3차원 ✓.

# Owner

frontend

# Task Tags

- code
- security
- auth

---

# Dependency Markers

- **depends on**: TASK-BE-328 (GAP `OAuthClientMapper` now copies `post-logout-redirect-uris` onto the SAS `RegisteredClient` — without it the V0011-seeded fan post-logout URIs are inert and `/connect/logout` 403s). On `origin/main`.
- **mirrors**: TASK-PC-FE-033 / BE-328 (platform-console RP-initiated logout — the revoke-≠-session-termination rationale + end_session 3-element contract).

# Goal

fan-platform-web logout terminates the GAP IdP session so the next login re-presents the credential form (no silent re-auth).

# Scope

- `auth.ts` jwt callback: capture `account.id_token` → `token.idToken` (server-side only; never copied onto the public session).
- `types.d.ts`: add `idToken?: string` to the JWT augmentation.
- New `shared/auth/federated-logout.ts` (`server-only`): read the id_token from the encrypted NextAuth cookie via `getToken` and build the GAP `end_session` URL (`id_token_hint` + `post_logout_redirect_uri` = app origin + `/` + `client_id`). Returns null when there is no id_token.
- `widgets/header/Header.tsx` logout server action: `signOut({ redirectTo: <end_session URL> ?? '/login' })`.

# Acceptance Criteria

- **AC-1** After logout, navigating to login presents the GAP credential form again (no silent re-auth). Verified live: login → logout → the auth-service login form appears.
- **AC-2** The `id_token` is read server-side (`getToken`) and never exposed via `/api/auth/session`.
- **AC-3** `post_logout_redirect_uri` exactly matches a registered V0011 URI (app origin + `/`), so GAP `/connect/logout` returns 302 (not 403).
- **AC-4** No id_token (legacy session) → fallback to a local-only logout (`/login`).
- **AC-5** `next lint` + `tsc --noEmit` clean; existing e2e-smoke unaffected.

# Related Specs

- `projects/fan-platform/specs/integration/gap-integration.md`
- `projects/global-account-platform/specs/features/consumer-integration-guide.md`

# Related Contracts

- `projects/global-account-platform/specs/contracts/auth-api.md` (§ OIDC `end_session` / `/connect/logout`)

# Edge Cases

- Legacy session minted before id_token capture → `getToken` returns no idToken → local-only logout.
- Secure (`__Secure-`) vs plain cookie name → helper detects which cookie exists and passes the matching salt/cookieName to `getToken`.

# Failure Scenarios

- GAP unreachable at logout → the browser still navigated away via `signOut`; the local session is cleared regardless (the end_session redirect best-effort terminates the IdP session).
- `post_logout_redirect_uri` mismatch → GAP 403; AC-3 guards the exact app-origin + `/` form.
