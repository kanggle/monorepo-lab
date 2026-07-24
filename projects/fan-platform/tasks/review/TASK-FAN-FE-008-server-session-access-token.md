# Task ID

TASK-FAN-FE-008

# Title

fan-platform-web: Server Components send no bearer — getFanSession reads the stripped session, not the JWT

# Status

review

# Owner

frontend

# Task Tags

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

# Goal

Every authenticated Server-Component data fetch in fan-platform-web went to the gateway **with no bearer token** → gateway `401` → the "디렉토리를 불러올 수 없습니다" / "피드를 불러올 수 없습니다 · 백엔드 게이트웨이가 응답하지 않을 수 있습니다" error states on every page. The gateway and backend are healthy; the token simply never left the web tier.

Root cause: `shared/auth/session.ts` `getFanSession()` read the access token off the **session object** returned by `auth()`:
```ts
const accessToken = (session as { accessToken?: string }).accessToken ?? null; // always null
```
but `sessionCallback` (auth-callbacks.ts) **deliberately strips the access/refresh/id tokens off the public session (F2)** — and a unit test asserts `session.accessToken` is `undefined`. So `getFanSession().accessToken` was always `null`, and all 14 pages (`getArtists(session.accessToken, …)`, `getFeed(session.accessToken, …)`, membership, notifications, posts) fetched unauthenticated. The module's own comment ("we attached the token onto the JWT … re-read it here via auth()") contradicted the actual F2 design. Latent because the browser login path had never been driven end-to-end before.

After this task: `getFanSession()` reads the bearer from the **raw encrypted session JWT** (`getToken()` from `next-auth/jwt`), server-side only. The F2 invariant is untouched (the token still never appears on the public session), and Server-Component fetches carry the bearer → 200.

Live-verified after the fix (headless next-auth login → SSR fetch): `/artists` and `/` render real artist/feed data with no error banner (previously the error state).

---

# Scope

## In Scope

- `shared/auth/session.ts` `getFanSession()`: decode the session cookie with `getToken({ req: { headers: await headers() }, secret: NEXTAUTH_SECRET, secureCookie: <https?> })` and read `accessToken` from the JWT. Return null when there is no cookie/secret or a prior silent refresh flagged `RefreshAccessTokenError` (do not send a known-stale token).
- Extract the pure token-selection decision into `selectAccessToken(jwt)` in `auth-callbacks.ts` (the next-auth-free, unit-testable home) and unit-test it.

## Out of Scope

- `sessionCallback` / the F2 invariant — unchanged; the token stays off the public session (its "accessToken undefined" test stays green).
- The 14 page components / feature `getX(accessToken, …)` fetchers — unchanged (they already accept the token from `getFanSession()`).
- Gateway / backend (healthy — a manually-minted token returns 200).
- Silent-refresh logic in the `jwt` callback (unchanged).

---

# Acceptance Criteria

- [ ] After browser login, a Server Component fetch carries `Authorization: Bearer <token>` → gateway returns 200 and the page renders real data (no error banner).
- [ ] `session.accessToken` on the PUBLIC session remains `undefined` (F2 invariant preserved — existing test still passes).
- [ ] `selectAccessToken` returns the token for a healthy JWT, and `null` for: `RefreshAccessTokenError`, missing/empty/non-string `accessToken`, and null/undefined JWT.
- [ ] `tsc --noEmit`, `next lint`, and `vitest` pass; production `next build` succeeds.

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read `PROJECT.md`, then load `rules/common.md` plus any `rules/domains/<domain>.md` and `rules/traits/<trait>.md` matching the declared classification. Unknown tags are a Hard Stop per `CLAUDE.md`.

- `specs/services/fan-platform-web/architecture.md` (server-only token boundary, F2)
- `specs/integration/iam-integration.md`

# Related Skills

- `.claude/skills/frontend/auth-client/` (see `.claude/skills/INDEX.md`)

---

# Related Contracts

- `specs/contracts/http/*` — consumed via the gateway `/api/v1/**` (unchanged)

---

# Target App

- `fan-platform-web`

---

# Implementation Notes

- `getToken` defaults: `cookieName`/`salt` → `authjs.session-token` (or `__Secure-authjs.session-token` when `secureCookie`); the local demo is http → unprefixed. `secret` is required — read `NEXTAUTH_SECRET`.
- Keep `session.ts` thin glue over the pure `selectAccessToken`; the `server-only` + `next/headers` boundary is why the decision logic lives in `auth-callbacks.ts` (mirrors why the silent-refresh logic was extracted there — vitest can't load `NextAuth()`).

---

# Edge Cases

- No session cookie → null token → page redirects to /login via existing middleware.
- Silent-refresh previously failed (`RefreshAccessTokenError`) → null token (do not send a stale bearer); session already degrades to anonymous in `sessionCallback`.
- `secureCookie` must match the deployment scheme so `getToken` looks for the right cookie name (http demo = unprefixed).

---

# Failure Scenarios

- Regression: reading the token off the session again → silent 401 on every page (the live smoke + the F2-preserved unit tests are the guard).
- Leaking the token onto the public session to "fix" it → F2 violation (explicitly rejected; the token stays server-side via the raw JWT).
- Wrong `cookieName`/`secureCookie` → `getToken` returns null → 401; verify against the actual cookie the demo writes.

---

# Test Requirements

- Unit (`auth-callbacks.test.ts`): `selectAccessToken` — healthy token, `RefreshAccessTokenError` → null, missing/empty/non-string → null, null/undefined JWT → null. Existing `sessionCallback` "accessToken undefined" test stays green (F2).
- Live smoke: headless next-auth login → SSR `/artists` + `/` render data (no error banner).

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed (none expected)
- [ ] Specs updated first if required (none expected)
- [ ] Ready for review
