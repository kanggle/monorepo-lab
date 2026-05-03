# Task ID

TASK-FAN-FE-001

# Title

fan-platform frontend (`fan-platform-web`) Next.js 부트스트랩 — 5~7 페이지 demo path

# Status

ready

# Owner

frontend

# Task Tags

- code
- api

---

# Required Sections

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Goal

fan-platform 의 첫 frontend app 인 `fan-platform-web` 을 Next.js 15 (App Router) + Tailwind 로 부트스트랩한다. PROJECT.md § Frontend 의 5~7 페이지 — **portfolio 평가자가 로컬에서 띄워 실제 흐름을 클릭으로 따라갈 수 있는 demo path**.

GAP OIDC IdP (`http://gap.local`) 를 표준 IdP 로 사용 — `next-auth` v5 + custom OIDC provider 로 `authorization_code` + PKCE flow 적용 ([consumer-integration-guide](../../../global-account-platform/specs/features/consumer-integration-guide.md) 따름).

이 태스크 완료 후:

- `projects/fan-platform/web/` 디렉토리에 Next.js 15 App Router app
- 5 핵심 페이지: 피드 (`/`) / 아티스트 디렉토리 (`/artists`) / 아티스트 프로필 (`/artists/[id]`) / 포스트 상세 (`/posts/[id]`) / 로그인 (`/login`)
- 옵션 2 페이지: 멤버십 게이트 (`/membership`) / 마이페이지 (`/me`)
- next-auth v5 + GAP OIDC PKCE — HttpOnly cookie 세션 + server actions 로 토큰 보호
- Tailwind 디자인 토큰 + 5~6 reusable components (PostCard, ArtistCard, FeedList, etc.)
- API client: gateway (`http://fan-platform.local`) 로 향함 — community + artist API 통합
- React Query (또는 SWR) 로 데이터 페칭 + 캐싱
- Vitest + Testing Library 단위 테스트 + Playwright smoke (3 specs: home / login / auth-guard redirect)
- ecommerce frontend (`web-store`) 패턴 재사용 — 검증된 구조

이 frontend 가 동작하면 fan-platform v1 풀스택 demo path 완성 — backend 3 services + frontend 1 page suite.

---

# Scope

## In Scope

### 1. Project skeleton

- `projects/fan-platform/web/fan-platform-web/` 디렉토리 (또는 `projects/fan-platform/apps/fan-platform-web/` — ecommerce 패턴 따라 결정)
- `package.json`, `tsconfig.json`, `next.config.ts`, `tailwind.config.ts`, `postcss.config.mjs`, `.eslintrc.json`, `eslint.config.mjs` (flat config, ecommerce 의 web-store 패턴 따름)
- `pnpm-workspace.yaml` 의 fan-platform workspace 추가 (있다면; 없으면 monorepo root pnpm-workspace 에 추가)
- 루트 `package.json` 에 새 script:
  ```
  "fan-platform:install": "pnpm --dir projects/fan-platform install",
  "fan-platform:dev": "pnpm --dir projects/fan-platform dev",
  "fan-platform:build": "pnpm --dir projects/fan-platform build",
  "fan-platform:lint": "pnpm --dir projects/fan-platform lint",
  "fan-platform:web": "pnpm --dir projects/fan-platform --filter fan-platform-web dev",
  "fan-platform:pnpm": "pnpm --dir projects/fan-platform"
  ```

### 2. Page routes (App Router)

| Route | Page | Auth | Role |
|---|---|---|---|
| `/` | 피드 (followed artists 의 최근 post) | 인증 필수 | fan |
| `/login` | OIDC redirect to GAP | public | — |
| `/api/auth/[...nextauth]` | next-auth v5 callback | — | — |
| `/artists` | 아티스트 디렉토리 (검색 + 페이지네이션) | 인증 필수 | fan |
| `/artists/[id]` | 아티스트 프로필 + 그 artist 의 post 리스트 + follow/unfollow 버튼 | 인증 필수 | fan |
| `/posts/[id]` | post 상세 + 댓글 + 반응 | 인증 필수 | fan |
| `/me` (옵션) | 내 follow 목록 + 내가 작성한 post | 인증 필수 | fan |
| `/membership` (옵션) | 멤버십 안내 (PREMIUM 게이팅 — v1 mock 페이지) | 인증 필수 | fan |
| `/auth-guard` | 인증 미통과 시 redirect 페이지 (login → 원래 경로로 복귀) | public | — |

`fan` role 만 다룸 — admin (artist 등록 등) UI 는 v2 admin app 으로 분리.

### 3. Authentication (next-auth v5 + GAP OIDC)

- `auth.ts` (route handler) — next-auth config:
  - provider: custom OIDC provider, issuer = `OIDC_ISSUER_URL` (env), client_id = `fan-platform-web` (GAP V0010 seed 추가 필요 — out of scope, 별도 task 발행 시 명시)
  - PKCE 강제, `state` + `nonce` 검증
  - HttpOnly cookie session
- middleware.ts — protected routes 강제 (모든 페이지 except `/login`, `/api/auth/*`)
- Server Actions 로 access_token 을 server side 에서 보유 (client 에 노출 안 함) — fetch proxy 패턴
- ecommerce `web-store` 의 frontend/auth-client SKILL 활용

### 4. API client

- `src/api/` 또는 `lib/api-client/`:
  - `client.ts` — fetch wrapper, base URL `process.env.NEXT_PUBLIC_GATEWAY_URL` (default `http://fan-platform.local`)
  - 자동 inject `Authorization: Bearer <access_token>` (server-side fetch only — client side 는 server action 거침)
  - `community/` — getFeed, getPost, addComment, addReaction, follow/unfollow
  - `artists/` — listDirectory, getArtist, getFandom
  - 응답 envelope `{ data, meta }` unwrap helper
  - 에러 envelope `{ code, message, details? }` typed exception
- React Query 또는 SWR 로 cache + revalidation

### 5. UI components (Tailwind)

- `components/`:
  - `PostCard.tsx` — 단일 post (artist 작성 / fan 작성 구분 표시)
  - `ArtistCard.tsx` — 디렉토리 카드
  - `FollowButton.tsx` — 팔로우/언팔로우 toggle
  - `ReactionBar.tsx` — LIKE/LOVE/FIRE/SAD 토글
  - `CommentList.tsx` + `CommentForm.tsx`
  - `FeedList.tsx` — paginated feed
  - `Pagination.tsx`
  - `EmptyState.tsx` / `ErrorState.tsx` / `LoadingState.tsx` — frontend/loading-error-handling SKILL 따름
- 디자인 토큰: tailwind.config.ts 의 colors / spacing / typography. fan-platform 정체성 (K-pop 류) — accent color 제안 (purple/pink palette). 단, 디자인 자체는 spec 범위 — 구현은 reasonable defaults.
- 다크모드 지원 옵션 (Tailwind `dark:` prefix)

### 6. State management + data fetching

- React Query (또는 SWR) — server state cache + revalidation
- Zustand 또는 React context — UI 상태 (e.g., 토스트 알림)
- Form: react-hook-form + zod (post 작성, 댓글, 반응 — 만약 작성 페이지 포함 시 v1 에 추가)

### 7. Tests

- 단위: Vitest + Testing Library — components 5+ (PostCard, FollowButton, FeedList 등)
- API client: MSW (Mock Service Worker) 로 gateway 응답 mock
- Playwright smoke (`e2e-smoke/`):
  - `home.spec.ts` — 인증 없이 `/` 접근 → `/login` redirect
  - `login.spec.ts` — `/login` → GAP redirect URL 검증 (실제 GAP 통신 X, redirect URL 형식만)
  - `auth-guard.spec.ts` — protected route 에 인증 토큰 없이 접근 → guard 동작
- Playwright fullstack (옵션 — TASK-FAN-INT-002 로 분리 가능):
  - 실제 backend 컨테이너 + WireMock JWKS 띄워 클릭으로 시나리오 따라가기 — 본 task 는 smoke 까지로 제한, fullstack 은 별도

### 8. spec 작성

- `projects/fan-platform/specs/services/fan-platform-web/architecture.md` (신규)
  - Service Type=`frontend-app`, Architecture Style=Layered by Feature (또는 Feature-Sliced Design — 결정 필요)
  - File tree, allowed/forbidden imports, boundary rules
- `projects/fan-platform/specs/contracts/http/fan-platform-web.md` (신규, 옵션 — 백엔드 contract 만 따르므로 별도 안 만들 수도 있음)

### 9. CI 통합

- `.github/workflows/ci.yml` 의 `frontend-checks` job 확장:
  - `pnpm fan-platform:install` (또는 monorepo lockfile 가 cover)
  - `pnpm fan-platform:lint` + `pnpm fan-platform:build`
- `frontend-unit-tests` job 에 fan-platform vitest 추가
- `frontend-e2e-smoke` job 에 fan-platform Playwright smoke 추가
- ecommerce `web-store` 패턴 모방

### 10. docker-compose 통합 (옵션)

- 본 task 의 v1 demo path 는 `pnpm fan-platform:web` 로 dev server 띄움 (Next.js dev mode). production-style 컨테이너는 v2.
- `.env.example` 에 추가:
  ```
  NEXT_PUBLIC_GATEWAY_URL=http://fan-platform.local
  NEXTAUTH_URL=http://localhost:3000
  NEXTAUTH_SECRET=<generated>
  OIDC_ISSUER_URL=http://gap.local
  OIDC_CLIENT_ID=fan-platform-web
  OIDC_CLIENT_SECRET=<from GAP V0010 seed>
  ```

## Out of Scope

- Admin UI (artist 등록, post moderation) — v2 admin app
- Membership 결제 flow — v2 membership-service 와 함께
- 댓글 / 포스트 작성 페이지 — fan 이 작성하는 흐름은 v1 에 포함하나 운영자 모더레이션 화면은 제외
- Notification UI (배지, 토스트) — notification-service v2 와 함께
- Search UI 고도화 (자동완성, 필터) — v2 search-service 와 함께
- Performance budget 최적화 (bundle size, Core Web Vitals) — 별도 task
- A/B 테스트 / experiment framework
- i18n (다국어) — v2
- 모바일 PWA — v2

---

# Acceptance Criteria

- [ ] `pnpm fan-platform:install` + `pnpm fan-platform:build` 통과
- [ ] `pnpm fan-platform:lint` 통과
- [ ] `pnpm fan-platform:web` 로 dev server 기동 → `http://localhost:3000/` 접근 시 `/login` redirect
- [ ] backend 3 services + GAP IdP mock 띄운 상태에서 fan 토큰 로그인 → 피드 페이지 → artist 디렉토리 → 아티스트 프로필 → 팔로우 → 포스트 상세 클릭 흐름 동작
- [ ] Vitest 단위 테스트 통과 (`pnpm fan-platform --filter fan-platform-web test`)
- [ ] Playwright smoke 3 spec 통과 (`pnpm fan-platform --filter fan-platform-web e2e:smoke`)
- [ ] `specs/services/fan-platform-web/architecture.md` 작성 + Service Type=`frontend-app`
- [ ] CI `frontend-checks` + `frontend-unit-tests` + `frontend-e2e-smoke` job 에 fan-platform 추가 → GREEN
- [ ] `.env.example` 갱신 + 루트 `package.json` script 추가

---

# Related Specs

- `projects/fan-platform/PROJECT.md` § Frontend
- `projects/fan-platform/specs/integration/gap-integration.md` (JWT claim contract)
- `projects/fan-platform/specs/contracts/http/community-api.md`
- `projects/fan-platform/specs/contracts/http/artist-api.md`
- `projects/global-account-platform/specs/features/consumer-integration-guide.md` (PKCE flow)
- `projects/global-account-platform/docs/adr/ADR-001-oidc-adoption.md`
- `platform/service-types/frontend-app.md` (있다면)
- `rules/traits/content-heavy.md`, `rules/traits/read-heavy.md`
- `projects/ecommerce-microservices-platform/web/web-store/` (reference Next.js 15 App Router 패턴)
- `projects/ecommerce-microservices-platform/web/admin-dashboard/` (reference)

# Related Skills

- `.claude/skills/frontend/architecture/feature-sliced-design/SKILL.md` (또는 layered-by-feature)
- `.claude/skills/frontend/implementation-workflow/SKILL.md`
- `.claude/skills/frontend/api-client/SKILL.md`
- `.claude/skills/frontend/state-management/SKILL.md`
- `.claude/skills/frontend/form-handling/SKILL.md`
- `.claude/skills/frontend/loading-error-handling/SKILL.md`
- `.claude/skills/frontend/auth-client/SKILL.md` (HttpOnly cookies + refresh proxy)
- `.claude/skills/frontend/server-actions/SKILL.md`
- `.claude/skills/frontend/component-library/SKILL.md`
- `.claude/skills/frontend/testing-frontend/SKILL.md`
- `.claude/skills/frontend/bundling-perf/SKILL.md`

---

# Related Contracts

- `projects/fan-platform/specs/contracts/http/community-api.md` (consumer)
- `projects/fan-platform/specs/contracts/http/artist-api.md` (consumer)

---

# Target Service / Component

- `projects/fan-platform/web/fan-platform-web/` (또는 `projects/fan-platform/apps/fan-platform-web/`) — 신규
- `projects/fan-platform/specs/services/fan-platform-web/architecture.md` (신규)
- 루트 `package.json` script 추가
- 루트 `pnpm-workspace.yaml` 갱신
- `.github/workflows/ci.yml` (frontend job 확장)

---

# Architecture

`platform/architecture-decision-rule.md` 따름. Service Type = `frontend-app` (`projects/fan-platform/PROJECT.md` § service_types).

Architecture Style: **Layered by Feature** 또는 **Feature-Sliced Design** — 결정은 spec 작성 시. ecommerce `web-store` 패턴이 참고되면 일관성 있음.

```
projects/fan-platform/web/fan-platform-web/
├── src/
│   ├── app/                           ← App Router pages
│   │   ├── (auth)/login/page.tsx
│   │   ├── (main)/page.tsx            ← / (feed)
│   │   ├── (main)/artists/page.tsx
│   │   ├── (main)/artists/[id]/page.tsx
│   │   ├── (main)/posts/[id]/page.tsx
│   │   ├── api/auth/[...nextauth]/route.ts
│   │   └── layout.tsx
│   ├── components/                    ← reusable UI primitives
│   ├── features/                      ← domain features (feed, artists, posts, follow)
│   ├── lib/api-client/                ← typed API client
│   ├── lib/auth/                      ← next-auth config + helpers
│   └── lib/utils/
├── e2e-smoke/                         ← Playwright smoke specs
├── tests/                             ← vitest unit
├── public/
└── ...config files
```

---

# Implementation Notes

- **ecommerce `web-store` 를 reference 로 복제** + 다음 변경:
  - 도메인: shop/cart/order → feed/artists/posts/follow
  - auth flow: ecommerce 자체 auth-service → GAP OIDC PKCE
  - 디자인: ecommerce 디자인 토큰 (자체 K-pop) 으로 변경 — 너무 ecommerce 색감 그대로 가져오면 portfolio 차별화 어려움. 단, 컴포넌트 구조는 그대로 차용.
- next-auth v5 (`@auth/nextjs` v5) — App Router 호환. 단, v5 가 stable 인지 확인. unstable 이면 v4 fallback.
- React Query v5 권장 (Suspense 통합).
- `OIDC_CLIENT_ID=fan-platform-web` — GAP V0010 seed 에 client 추가 필요. 본 task 의 일부로 GAP DB seed migration 추가 (`projects/global-account-platform/apps/auth-service/src/main/resources/db/migration/` 에 V0011 또는 V0012 추가) — 또는 별도 task `TASK-MONO-026` 발행 후 본 task 에서 의존.
  - **권장**: GAP seed 변경은 별도 spec PR (cross-project 영향, 본 task 가 발행 단계에서 결정).
- portfolio 디자인은 *"실제로 prod 같이 보여야"* 함. lorem ipsum 대신 reasonable mock data + 가상 K-pop artist (kanggle 의 디자인 selection 권한).

---

# Edge Cases

- **인증 만료 (access_token 만료)**: server action 이 refresh_token 으로 자동 갱신 OR 갱신 실패 시 `/login` redirect.
- **토큰 검증 실패 (gateway 가 401)**: client side 에서 Sentry/console 기록 + `/login` redirect.
- **다른 tenant 토큰 (예: wms 도메인 토큰으로 fan-platform-web 접근)**: backend 에서 403 → frontend 가 *"이 서비스 접근 권한이 없습니다"* 에러 페이지 표시.
- **Empty state**: 팔로우 0명, 포스트 0개 → CTA *"디렉토리에서 아티스트를 찾아보세요"*.
- **네트워크 오류**: React Query retry + Toast 알림 + offline indicator.
- **다국어 (out of scope)**: 한국어 only v1.

---

# Failure Scenarios

- **GAP IdP 다운**: 로그인 시도 시 timeout → *"로그인 서비스 일시 오류"* 페이지 + retry 버튼.
- **gateway 다운**: `/api/community/*` 호출 5xx → React Query error state → 사용자 친화적 메시지.
- **NEXTAUTH_SECRET 미설정 (dev)**: dev server 부팅 시 next-auth 가 강제 fail-fast → README 설명 명시.
- **CORS 미스매치 (NEXT_PUBLIC_GATEWAY_URL 와 gateway-service 의 CORS_ALLOWED_ORIGINS 불일치)**: `localhost:3000` 가 양쪽에 있어야 함 — `.env.example` 주석으로 명시.

---

# Test Requirements

- 단위 (Vitest):
  - 5+ component tests
  - API client tests (MSW)
  - feature hooks (e.g., useFollow)
- E2E smoke (Playwright, 3 specs):
  - `home.spec.ts`, `login.spec.ts`, `auth-guard.spec.ts`
- Fullstack E2E — 별도 task (`TASK-FAN-INT-002` 또는 TASK-FAN-INT-001 의 확장)

---

# Definition of Done

- [ ] Next.js 15 app 부트스트랩 + 5 페이지 + 옵션 2 페이지 구현
- [ ] next-auth v5 + GAP OIDC PKCE 설정 + middleware
- [ ] API client (community + artist) + React Query
- [ ] Tailwind 디자인 토큰 + 5+ reusable components
- [ ] Vitest 단위 테스트 + Playwright smoke 통과
- [ ] `architecture.md` + `.env.example` + 루트 `package.json` script
- [ ] CI 3 frontend job 확장 + GREEN
- [ ] dev server 띄워 backend 3 services 와 e2e 클릭 흐름 검증 가능
- [ ] Ready for review
