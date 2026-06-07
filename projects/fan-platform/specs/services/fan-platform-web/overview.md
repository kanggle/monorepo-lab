# fan-platform-web — Overview

> 1-pager: responsibilities, public surface (pages), key invariants.

## Service identity

| Field | Value |
|---|---|
| Service name | `fan-platform-web` |
| Project | `fan-platform` |
| Service Type | `frontend-app` |
| Architecture Style | **Feature-Sliced Design (lite)** — see [architecture.md § Architecture Style Rationale](architecture.md) |
| Stack | Next.js 15 App Router, React 19, TypeScript 5, Tailwind CSS 3.4, next-auth v5 (auth.js), TanStack Query v5 |
| Deployable unit | `web/fan-platform-web/` |
| Bounded Context | `fan-platform-frontend` |
| Persistent stores | none — HttpOnly JWT session cookie only (no localStorage / sessionStorage tokens) |
| Backend dependencies | `gateway-service` (`http://fan-platform.local`), IAM IdP (`http://iam.local`) |

## Responsibilities

- **5-page user journey** — feed (`/`), artists directory (`/artists`), artist detail (`/artists/[id]`), post detail (`/posts/[id]`), membership stub (`/membership`), me (`/me`).
- **Authentication via next-auth v5 + IAM OIDC PKCE** — `/login` → Server Action `signIn('iam')` → OIDC discovery + PKCE → IAM `/oauth2/authorize` → callback `/api/auth/callback/iam` → JWT session cookie (`access_token` / `refresh_token` / `tenant_id` on HttpOnly).
- **Server Components data fetch** — feed / artists / posts pages fetch via RSC (`getFeed()`, `getArtists()`, `getArtist()`, `getPost()`); bearer token attached via `'server-only'` `getFanSession()`.
- **Server Actions** — reactions (`setReaction`), follow / unfollow (`followArtist` / `unfollowArtist`) marked `'use server'`; tokens read on server only.
- **Middleware redirect** — `middleware.ts` redirects unauthenticated visits to gated paths → `/login`.
- **Tenant forwarding only** — frontend does NOT enforce `tenant_id` (backend gateway/community/artist services re-validate); wrong-tenant token surfaces as `ApiError(403, TENANT_FORBIDDEN)` → `ErrorState` UI.
- **Error / Loading boundaries** — Suspense surfaces `LoadingState`, `ApiError` → `ErrorState` (or `notFound()` for 404), global `app/error.tsx`.

## Public surface (pages)

자세한 스펙은 [architecture.md § Package Layout](architecture.md) + § Data Fetching 참조.

| Path | Pattern | Auth | Purpose |
|---|---|---|---|
| `/login` | Public RSC | unauth | OIDC sign-in trigger (`signIn('iam')`) |
| `/api/auth/[...nextauth]` | Route handler | unauth | next-auth v5 OIDC callback + token exchange |
| `/` | RSC + Suspense | gated | feed (followed artists' PUBLISHED posts) |
| `/artists` | RSC + Suspense | gated | artist directory (PUBLISHED only) |
| `/artists/[id]` | RSC + Suspense | gated | artist detail + FollowButton |
| `/posts/[id]` | RSC + Suspense | gated | post detail + ReactionBar |
| `/membership` | RSC | gated | membership stub (v1 placeholder; v2 결제 flow) |
| `/me` | RSC | gated | account profile + session info |

middleware-gated paths 외에는 모두 `/login` 으로 redirect.

## Key invariants

1. **HttpOnly cookie auth only** — `localStorage` / `sessionStorage` 에 어떤 token 도 저장 금지. all tokens live on next-auth JWT session cookie.
2. **Server-only access token** — `accessToken` 은 Server Components / Server Actions / route handlers 안에서만 `getFanSession()` 통해 read; `shared/auth/session.ts` 는 `'server-only'` 모듈 (Client Component import 시 build error).
3. **Single fetch boundary** — `shared/api/client.ts` 의 `gatewayFetch` 가 gateway 호출 유일 진입점; feature 들은 모두 이 모듈 import.
4. **Tenant locked = `fan-platform`** — frontend 는 enforce 안 함; backend (gateway/community/artist) 가 모두 `tenant_id == fan-platform` 재검증, 다른 tenant token 은 `ApiError(403, TENANT_FORBIDDEN)` 으로 자연 surface.
5. **MEMBERS_ONLY / PREMIUM 시각화 v1 stub** — membership-service 미존재 v1 에서 backend 가 PREMIUM 항상 통과 + WARN, frontend 는 결과 그대로 표시; v2 에서 hard fail-close + 결제 flow 추가.
6. **Cross-feature isolation** — `features/<x>/` 는 자기 `index.ts` public API 만 노출; cross-feature data 는 `entities/` types 또는 `widgets/` 합성 경유.

## Out of scope (v1)

- 댓글 composer UI — `community-service` 의 comment API 는 backend 존재, 본 frontend 는 read-only display.
- 모더레이션 UI (포스트 HIDDEN/DELETED 전이) — v2 admin-service / admin dashboard.
- 멤버십 결제 flow — v2 membership-service 도입 시 (`/membership` 가 stub 페이지로 유지).
- artist self-service (artist 본인이 자기 프로필 수정) — backend 자체가 v1 admin-only.
- 미디어 업로드 / 미디어 viewer — backend 가 reference URL 만 저장; v2 S3/MinIO 통합 시 추가.
- 알림 (in-app push, FCM, APNs) — v2 notification-service 도입 시.
- search UI (search-service) — v2.
- client-side state for content (React Query cache for reads) — v1 은 RSC fetch only; client mutations 만 사용.
