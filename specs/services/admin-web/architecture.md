# admin-web — Architecture

## Service Type

`frontend-app` — 선언된 서비스 타입. [platform/service-types/frontend-app.md](../../../platform/service-types/frontend-app.md) 요구사항을 전부 상속한다.

## Architecture Pattern

**Layered by Feature** — 단일 도메인(운영자 콘솔)이고 팀 규모가 작기 때문에 Feature-Sliced Design의 엄격한 레이어 분리는 오버엔지니어링이다. 기능 단위 폴더로 묶고 `shared/` 공통 레이어만 분리한다.

참조 skill: [.claude/skills/frontend/architecture/layered-by-feature/SKILL.md](../../../.claude/skills/frontend/architecture/layered-by-feature/SKILL.md)

## Tech Stack

- **Next.js 15** (App Router)
- **React 19**
- **TypeScript 5**
- **Tailwind CSS** + **shadcn/ui** (component primitives)
- **React Query** (서버 상태 캐싱 + 변이)
- **zod** (폼 validation + API 응답 스키마 검증)
- **react-hook-form**
- **Vitest** + **@testing-library/react**
- **Playwright** (E2E)

## Package Structure

```
apps/admin-web/
├── src/
│   ├── app/                           # Next.js App Router routes
│   │   ├── (auth)/
│   │   │   └── login/page.tsx
│   │   ├── (console)/                 # 인증 필수 레이아웃
│   │   │   ├── layout.tsx             # sidebar + topbar
│   │   │   ├── accounts/
│   │   │   │   ├── page.tsx           # 계정 검색
│   │   │   │   └── [id]/page.tsx      # 계정 상세
│   │   │   ├── audit/page.tsx
│   │   │   ├── security/
│   │   │   │   ├── login-history/page.tsx
│   │   │   │   └── suspicious/page.tsx
│   │   │   ├── dashboards/page.tsx    # Grafana iframe
│   │   │   └── operators/page.tsx     # SUPER_ADMIN only
│   │   ├── api/                       # Next.js Route Handlers (auth proxy only)
│   │   │   ├── auth/login/route.ts
│   │   │   ├── auth/logout/route.ts
│   │   │   └── auth/refresh/route.ts  # HttpOnly cookie refresh
│   │   └── layout.tsx
│   ├── features/                      # 기능 단위
│   │   ├── accounts/
│   │   │   ├── components/            # AccountSearchForm, AccountDetail, LockDialog, ...
│   │   │   ├── hooks/                 # useAccount, useLockAccount, ...
│   │   │   └── schemas.ts             # zod
│   │   ├── audit/
│   │   ├── security/
│   │   ├── sessions/
│   │   ├── operators/
│   │   └── auth/
│   │       ├── components/LoginForm.tsx
│   │       ├── hooks/useOperatorSession.ts
│   │       └── guards/RoleGuard.tsx
│   └── shared/
│       ├── api/                       # typed API client
│       │   ├── client.ts              # fetch wrapper with credentials + retry
│       │   ├── admin-api.ts           # generated types from admin-api.md
│       │   └── errors.ts
│       ├── ui/                        # shadcn/ui primitives
│       ├── lib/                       # utils (dates, formatters, masking)
│       ├── observability/
│       │   ├── web-vitals.ts
│       │   └── sentry.ts
│       └── config/
│           ├── env.ts                 # zod-validated env
│           └── routes.ts
├── public/
├── tests/
│   ├── unit/
│   └── e2e/
├── next.config.mjs
├── tsconfig.json
├── package.json
└── vitest.config.ts
```

## Allowed Dependencies

| Layer | 허용 import |
|---|---|
| `app/` | `features/*`, `shared/*` |
| `features/<F>/` | 자체 폴더 내부, `shared/*` |
| `shared/` | 자체만 (features / app 금지) |

**금지**:
- `features/A` → `features/B` 상호 참조 (있다면 `shared/`로 승격)
- 컴포넌트에서 직접 `fetch()` (반드시 `shared/api/client.ts` 경유)

## Server vs Client Components

- **Default: Server Component** — 데이터 페칭은 서버에서 수행
- **`'use client'`**: 폼, 다이얼로그, 실시간 상호작용이 필요한 리프 컴포넌트만
- **React Query**는 client component에서만 사용. 서버에서는 `fetch` 직접 + `revalidate` / `cache`

## Auth Flow

1. `/login` 서버 컴포넌트 → `LoginForm` (client)
2. 클라이언트가 `POST /api/auth/login` (Next.js route handler) 호출
3. Route handler가 auth-service `POST /api/auth/login` 호출 + operator scope 검증
4. 성공 시 `Set-Cookie: accessToken=...; HttpOnly; Secure; SameSite=Strict` + `refreshToken` 동일
5. 이후 모든 요청은 쿠키 자동 전달
6. 401 수신 시 client → `POST /api/auth/refresh` → 새 쿠키 → 원 요청 재시도
7. 로그아웃 시 route handler가 auth-service에 revoke 호출 + 쿠키 삭제

Access token은 **절대 JavaScript에서 접근 불가** (HttpOnly). 이는 `platform/service-types/frontend-app.md` 필수 요구.

## Role-Based Rendering

- 로그인 응답에 `roles: string[]` 포함
- `RoleGuard` 컴포넌트로 라우트 레이아웃·버튼·메뉴 가시성 제어
- 서버에서 역할 확인 (`layout.tsx`에서 쿠키 파싱 → `/me` 호출 → roles) — 클라이언트 guard는 UX용, 보안은 백엔드가 최종 책임

## Performance Budget

- **/login, /logout**: 180 KB gzipped
- **(console)/* 내부 페이지**: 250 KB gzipped
- CI에서 `next-bundle-analyzer` 결과로 회귀 차단

## Error Boundaries

- 각 라우트에 `error.tsx` — 일반 에러
- `(console)/error.tsx` — 인증/권한 실패는 `/login`으로 redirect
- 401 → refresh 시도; 403 → "권한 없음" 페이지; 5xx → 재시도 가능한 에러 화면

## Forbidden Patterns

- JWT를 localStorage에 저장
- `features/A`에서 `features/B` 내부 import
- 컴포넌트에서 `fetch()` 직접 호출
- 빌드 타임에 시크릿 주입

## Testing Strategy

- **Unit/Component**: Vitest + Testing Library — 모든 `features/<F>/components/*.tsx`와 `hooks/*.ts`
- **Contract tests**: `shared/api/admin-api.test.ts` — admin-api.md 응답 스키마와 zod 파서 일치 검증
- **a11y**: `@axe-core/react` 통합, shadcn primitives에 자동 검사
- **E2E (Playwright)**: 로그인 → 계정 검색 → lock → 감사 확인의 critical journey

## Observability

- **web-vitals**: LCP, INP, CLS, TTFB를 `/api/web-vitals` route handler로 전송 → 백엔드가 Prometheus 메트릭으로 노출 (별도 수집 서비스 미존재 시 콘솔로 대체)
- **Sentry (옵션)**: 운영 환경에서만 활성화, DSN은 런타임 env
- **구조화 로그**: 서버 컴포넌트/route handler는 `requestId` + `operatorId` 컨텍스트 포함 JSON 로그

상세: [observability.md](observability.md)
