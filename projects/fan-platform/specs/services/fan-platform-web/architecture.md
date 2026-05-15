# fan-platform-web — Architecture

This document declares the internal architecture of `fan-platform/web/fan-platform-web`,
the Next.js 15 App Router frontend for fan-platform v1. All implementation
tasks targeting this app must follow this declaration plus the relevant rule
layers (`rules/common.md`, `rules/traits/content-heavy.md`,
`rules/traits/read-heavy.md`, `rules/traits/multi-tenant.md`).

---

## Identity

| Field | Value |
|---|---|
| Service name | `fan-platform-web` |
| Project | `fan-platform` |
| Service Type | `frontend-app` |
| Architecture Style | **Feature-Sliced Design (lite)** |
| Primary stack | Next.js 15 App Router, React 19, TypeScript 5, Tailwind CSS 3.4, next-auth v5 (auth.js), TanStack Query v5 |
| Bounded Context | `fan-platform-frontend` |
| Deployable unit | `web/fan-platform-web/` |
| Backend dependencies | gateway-service (`http://fan-platform.local`), GAP IdP (`http://gap.local`) |

### Service Type Composition

`fan-platform-web` is a single-type `frontend-app` service per
`platform/service-types/INDEX.md`. Next.js 15 App Router server-rendered
+ client-side hybrid delivery with next-auth v5 PKCE flow against GAP
IdP. No backend service-type composition.

---

## Architecture Style Rationale

The directory structure mirrors `apps/web-store/` from the
ecommerce-microservices-platform — Feature-Sliced Design with `features/`,
`entities/`, `widgets/`, and `shared/` layers. This was the primary
reference for the bootstrap (TASK-FAN-FE-001 § Reference patterns) and gives
the frontend the same review heuristics used elsewhere in the monorepo.

The "lite" qualifier captures two scope choices for v1:

- No `features/<x>/model/` layer — server-side data flows through Server
  Components, so domain hooks (`useFollow`, etc.) are minimal in v1.
- `widgets/` only contains the navigation `Header`. As more composite blocks
  appear (e.g. an aggregated artist banner combining ArtistCard + FollowButton
  + recent posts), they are promoted from `features/` into `widgets/`.

---

## Package Layout

```
src/
├── app/                            ← Next.js App Router
│   ├── (auth)/login/page.tsx       ← public, OIDC sign-in trigger
│   ├── (main)/                     ← gated by middleware
│   │   ├── layout.tsx              ← Header + main shell
│   │   ├── page.tsx                ← / (feed)
│   │   ├── artists/page.tsx
│   │   ├── artists/[id]/page.tsx
│   │   ├── posts/[id]/page.tsx
│   │   ├── membership/page.tsx
│   │   └── me/page.tsx
│   ├── api/auth/[...nextauth]/route.ts ← next-auth v5 handler
│   ├── error.tsx                   ← global error boundary
│   ├── not-found.tsx
│   ├── layout.tsx                  ← root <html>
│   ├── providers.tsx               ← client QueryClientProvider
│   └── globals.css
├── middleware.ts                   ← /login redirect for unauth'd visits
├── widgets/
│   └── header/Header.tsx
├── features/
│   ├── feed/{api,ui}/              ← getFeed RSC fetcher + FeedList
│   ├── post/{api,ui}/              ← getPost, setReaction, PostCard, ReactionBar
│   ├── artist/{api,ui}/            ← getArtists, getArtist, ArtistCard
│   └── follow/{api,ui}/            ← followArtist/unfollowArtist actions, FollowButton
├── entities/
│   ├── post/types.ts
│   └── artist/types.ts
└── shared/
    ├── api/{client.ts, errors.ts}  ← typed gateway client + ApiError
    ├── auth/{auth.ts,session.ts}   ← next-auth config + server-only session
    ├── config/env.ts
    └── ui/                         ← Button, EmptyState, ErrorState, LoadingState, Pagination
```

### Allowed dependencies

- `next` (15.x), `react` / `react-dom` (19.x)
- `next-auth@5.x` (auth.js v5 beta) — custom OIDC provider
- `@tanstack/react-query@5.x` — client-side cache for any future client mutations
- `tailwindcss@3.x`, `autoprefixer`, `postcss`
- `@playwright/test`, `vitest`, `@testing-library/react`, `@testing-library/jest-dom`, `msw`

### Forbidden dependencies

- localStorage / sessionStorage for any auth token (HttpOnly cookies only).
- Direct DB / Kafka / Redis clients — fan-platform-web is a pure frontend.
- Implicit token forwarding to client components — `accessToken` lives on
  the JWT session cookie and is read **only** in Server Components / Server
  Actions / route handlers via `getFanSession()`.

### Boundary rules

- `app/` MUST NOT import from `features/<x>/api/*` directly when the import
  is from a Client Component. Client UI dispatches Server Actions via the
  feature's index.ts (e.g. `features/follow` → `followArtist`).
- `features/<x>/` MUST NOT import from another feature's internals (only its
  `index.ts` public API). Cross-feature data goes via the `entities/` types
  or via composing in a `widgets/` block.
- `entities/` is types-only — no runtime code.
- `shared/api/client.ts` is the **only** module that calls `fetch()` against
  the gateway. Features import `gatewayFetch` from there.
- `shared/auth/session.ts` is `'server-only'` — importing it from a Client
  Component triggers a build error.
- Server Actions live alongside the relevant feature (`features/<x>/api/*`)
  marked with `'use server'`. They read tokens via `getFanSession()` —
  bearer tokens never cross into client bundles.

---

## Authentication (next-auth v5 + GAP OIDC)

Flow:

1. User clicks "GAP 로 로그인" on `/login` — Server Action calls
   `signIn('gap', { redirectTo })`.
2. next-auth performs OIDC discovery against `OIDC_ISSUER_URL`
   (`/.well-known/openid-configuration`), generates PKCE verifier + state,
   and redirects the browser to GAP's `/oauth2/authorize`.
3. GAP authenticates the user, redirects back to
   `/api/auth/callback/gap?code=...&state=...`.
4. next-auth exchanges the code for tokens at GAP's `/oauth2/token`
   (PKCE verifier supplied), validates ID-token signature against GAP's
   JWKS, and persists `access_token` + `refresh_token` + `tenant_id` claim
   onto an HttpOnly JWT session cookie.
5. The `session` callback exposes `accountId` / `tenantId` / `roles` to RSC
   pages. The bearer token stays on the JWT and is read only by
   `getFanSession()` inside server-only modules.

**Tokens never reach the client bundle.** All data fetches go through
Server Components or Server Actions; the gateway client pulls the bearer
token from the session via `'server-only'`.

### OIDC client registration

GAP V0011 seed (TASK-MONO-026 머지 완료) 가 `fan-platform-user-flow-client`
+ `fan-platform-realm-internal-services-client` + `fan-platform` tenant 시드 적용.
end-to-end OIDC `signIn('gap')` round-trip 정상 작동. dev secret = `fan-platform-dev`.

---

## Data Fetching

| Path | Pattern |
|---|---|
| `/` (feed) | RSC fetch via `getFeed()` |
| `/artists` | RSC fetch via `getArtists()` |
| `/artists/[id]` | RSC fetch via `getArtist()` |
| `/posts/[id]` | RSC fetch via `getPost()` |
| Reactions / Follow / Unfollow | Server Actions (`'use server'`) |

Page-level Suspense boundaries surface `LoadingState`. Errors propagate as
`ApiError`; pages catch and render `ErrorState` (or `notFound()` for 404).
React Query is wired at the root (`Providers`) for any future client-side
mutation that needs cache coordination — currently unused in v1 paths.

---

## Multi-tenant + Cross-tenant Defense

The browser does not enforce `tenant_id` — that responsibility is the
backend's. The frontend simply forwards GAP's bearer token via
`Authorization: Bearer <token>`; the gateway/community-service/artist-service
each re-validate `tenant_id == fan-platform`. A wrong-tenant token surfaces
as `ApiError(403, TENANT_FORBIDDEN)` and the page renders an `ErrorState`
("이 서비스에 접근할 권한이 없습니다") via the global `app/error.tsx`.

---

## Failure Modes

| Situation | UX response |
|---|---|
| Missing/expired session | middleware → `/login?from=<path>` |
| Backend gateway 5xx / network down | RSC `try/catch` → `ErrorState` placeholder |
| `MEMBERSHIP_REQUIRED` (post detail) | inline "멤버십이 필요합니다" CTA → `/membership` |
| Unknown server error | `app/error.tsx` boundary + `Reset` button |
| OIDC discovery / token exchange failure | next-auth `error` page redirects to `/login?error=...` |

---

## Testing Strategy

- **Unit (Vitest + Testing Library)** — `__tests__/` covers core component
  rendering (`PostCard`, `ArtistCard`, shared UI primitives, `Pagination`),
  the `ApiError` typing/parsing, and the `gatewayFetch` request shape via
  `vi.fn()` mock fetch (substitutes for MSW for now — MSW is wired for any
  future deeper fetch test).
- **Smoke (Playwright)** — `e2e-smoke/` runs against `next start` with the
  OIDC issuer / gateway URL forced to a closed loopback host. Three specs:
  - `home.spec.ts` — `/` redirects to `/login` when unauth'd.
  - `login.spec.ts` — `/login` page renders with the GAP sign-in button.
  - `auth-guard.spec.ts` — protected routes redirect with `?from=` preserved.
- **Full-stack E2E** — deferred to a follow-up task (`TASK-FAN-INT-002`),
  which boots backend services + GAP + WireMock and clicks through the
  fan demo path.

---

## CI Integration

Three jobs are extended in `.github/workflows/ci.yml`:

- `frontend-checks` — runs `pnpm fan-platform:install` + `lint` + `build`
  alongside the ecommerce job (separate steps, same runner).
- `frontend-unit-tests` — adds a fan-platform vitest step.
- `frontend-e2e-smoke` — adds a fan-platform Playwright smoke step (3 specs).

The fan-platform jobs reuse the same Node 20 + pnpm 9.15.0 setup and run
inside `projects/fan-platform/` working directory.

---

## Out of Scope (v1)

- Comment composer UI (read-only post detail in v1)
- Post / fan-post composer (FAN posts are scoped to v1 spec but UI is
  deferred to TASK-FAN-FE-002)
- Notifications drawer, real-time updates
- A/B testing, telemetry, performance budgets
- i18n (Korean only in v1)
- PWA / mobile-app shell
- Admin UI (artist registration, post moderation) — v2 admin app

---

## References

- `platform/architecture-decision-rule.md`
- `platform/service-types/frontend-app.md` (if present)
- `projects/fan-platform/PROJECT.md` § Frontend
- `projects/fan-platform/specs/integration/gap-integration.md`
- `projects/fan-platform/specs/contracts/http/community-api.md`
- `projects/fan-platform/specs/contracts/http/artist-api.md`
- `projects/global-account-platform/specs/features/consumer-integration-guide.md`
- `projects/ecommerce-microservices-platform/apps/web-store/` (reference)
- `.claude/skills/frontend/auth-client/SKILL.md`
- `.claude/skills/frontend/architecture/feature-sliced-design/SKILL.md`
