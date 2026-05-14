# admin-web — Architecture

This document declares the internal architecture of `admin-web`.
All implementation tasks targeting this service must follow this declaration
and `platform/architecture-decision-rule.md`.

---

## Identity

| Field | Value |
|---|---|
| Service name | `admin-web` |
| Project | `global-account-platform` |
| Service Type | `frontend-app` (single — see Service Type Composition below) |
| Architecture Style | **Layered by Feature** |
| Domain | saas |
| Primary language / stack | TypeScript strict, Next.js 15 (App Router) |
| Bounded Context | GAP operator console (account ops, audit, security, sessions, operators management) |
| Deployable unit | `apps/admin-web/` |
| Backend dependencies | `auth-service` (login / refresh / logout); `account-service` (account search/lock + audit + suspicious + login history); GAP `gateway-service` 라우팅 경계 통과 |

### Service Type Composition

`admin-web` is a single-type `frontend-app` service per
`platform/service-types/INDEX.md`. 선언된 서비스 타입의
[platform/service-types/frontend-app.md](../../../../../platform/service-types/frontend-app.md)
요구사항을 전부 상속한다 — HttpOnly 쿠키 인증, 타입 API 클라이언트,
server-component 우선, 성능/접근성/web-vitals 예산.

---

## Architecture Style

**Layered by Feature** — 기능 단위 폴더로 묶고 `shared/` 공통 레이어만 분리한다. Feature-Sliced Design 의 엄격한 5계층(`app / widgets / features / entities / shared`) 분리는 본 도메인 규모에 비해 오버엔지니어링이라 채택하지 않는다.

참조 skill: [.claude/skills/frontend/architecture/layered-by-feature/SKILL.md](../../../../../.claude/skills/frontend/architecture/layered-by-feature/SKILL.md)

## Why This Architecture

- **운영자 단일 도메인** — 모든 화면(계정, 감사, 보안, 세션, 운영자 관리)이 동일한 사용자 그룹(GAP operators)을 대상으로 한다. ecommerce `web-store` 처럼 고객·관리자·검색·결제 등 다양한 user journey 가 공존하지 않으므로 features 간 강한 격리(FSD `entities/` + `widgets/` 계층)가 주는 이득보다 ceremony 비용이 크다.
- **CRUD-heavy 페이지가 다수** — 계정 검색 → 상세 → 액션, 감사 검색, 로그인 이력 조회 등 list/detail 패턴이 반복된다. ecommerce `admin-dashboard/architecture.md` 가 동일 trade-off 로 Layered by Feature 를 선택한 이유와 일치한다.
- **Server-component 우선** — Next.js 15 App Router 의 서버 컴포넌트가 데이터 페칭의 1차 경로이므로, 클라이언트 측 features 격리가 가져다주는 React Query cache scope 같은 가치는 leaf-level client component 에 한정된다. layered-by-feature 가 이 모델에 자연스럽게 매핑된다.
- **백엔드 정렬** — auth-service / account-service 모두 Layered Architecture 를 채택했다(layered_by_layer 가 아니라 service-by-feature 라는 점은 다르나 의존성 방향 단순함은 공유). 동일 정신을 유지하면 cross-stack 리뷰 학습 비용이 낮다.

## Tech Stack

- **Next.js 15** (App Router)
- **React 19**
- **TypeScript 5**
- **Tailwind CSS** + **shadcn/ui** (component primitives)
- **React Query** (서버 상태 캐싱 + 변이; client-only)
- **zod** (폼 validation + API 응답 스키마 검증)
- **react-hook-form**
- **Vitest** + **@testing-library/react**
- **Playwright** (E2E)

## Internal Structure Rule

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

의존성은 **위에서 아래로만** 흐른다 (`app/ → features/ → shared/`). 같은 계층 내 `features/A → features/B` 상호 참조는 금지(공유 가치가 있으면 `shared/` 로 승격).

## Forbidden Dependencies

- `features/A` → `features/B` 상호 참조 (공유 가치가 있으면 `shared/` 로 승격)
- 컴포넌트에서 `fetch()` 직접 호출 — 모든 백엔드 호출은 `shared/api/client.ts` 경유
- JWT / refresh token 을 `localStorage` 또는 `sessionStorage` 에 저장 (HttpOnly 쿠키만 허용 — `frontend-app.md` § Mandatory Requirements 위반)
- 빌드 타임 시크릿 주입 — 런타임 env 만 허용
- 다른 앱(`fan-platform-web`, `web-store`, `admin-dashboard`)의 `features/` 직접 import — 공유는 `@repo/` 레벨 패키지로만

## Boundary Rules

- `app/` 라우트 컴포넌트는 비즈니스 로직을 담지 않고, `features/*` 와 `shared/*` 의 합성에 한정된다 (`features/<F>/components/` 의 컴포넌트를 호출하고 layout/sidebar 합성 정도만 담당).
- `features/<F>/` 내부에서 다른 feature 의 컴포넌트·훅을 직접 참조하지 않는다 — cross-feature 가치가 발견되면 `shared/` 로 승격하는 것이 유일한 경로.
- `shared/api/client.ts` 는 **유일한** 백엔드 진입점 — 모든 fetch / mutation 은 여기서 cookie 자동 전달과 401 처리(refresh route 호출)를 통과한다.
- `RoleGuard` 는 UX 게이트일 뿐, 실제 권한 검증은 백엔드(account-service)가 최종 책임진다 — 클라이언트 가드 우회 시도가 백엔드에서 403 으로 차단됨을 가정한다.

## Server vs Client Components

- **Default: Server Component** — 데이터 페칭은 서버에서 수행
- **`'use client'`**: 폼, 다이얼로그, 실시간 상호작용이 필요한 리프 컴포넌트만
- **React Query** 는 client component 에서만 사용. 서버에서는 `fetch` 직접 + `revalidate` / `cache`

## Auth Flow

1. `/login` 서버 컴포넌트 → `LoginForm` (client)
2. 클라이언트가 `POST /api/auth/login` (Next.js route handler) 호출
3. Route handler 가 auth-service `POST /api/auth/login` 호출 + operator scope 검증
4. 성공 시 `Set-Cookie: accessToken=...; HttpOnly; Secure; SameSite=Strict` + `refreshToken` 동일
5. 이후 모든 요청은 쿠키 자동 전달
6. 401 수신 시 client → `POST /api/auth/refresh` → 새 쿠키 → 원 요청 재시도
7. 로그아웃 시 route handler 가 auth-service 에 revoke 호출 + 쿠키 삭제

Access token 은 **절대 JavaScript 에서 접근 불가** (HttpOnly). 이는 `platform/service-types/frontend-app.md` 필수 요구.

## Role-Based Rendering

- 로그인 응답에 `roles: string[]` 포함
- `RoleGuard` 컴포넌트로 라우트 레이아웃·버튼·메뉴 가시성 제어
- 서버에서 역할 확인 (`layout.tsx` 에서 쿠키 파싱 → `/me` 호출 → roles) — 클라이언트 guard 는 UX용, 보안은 백엔드가 최종 책임 (§ Boundary Rules)

## Performance Budget

- **/login, /logout**: 180 KB gzipped
- **(console)/* 내부 페이지**: 250 KB gzipped
- CI 에서 `next-bundle-analyzer` 결과로 회귀 차단

## Error Boundaries

- 각 라우트에 `error.tsx` — 일반 에러
- `(console)/error.tsx` — 인증/권한 실패는 `/login` 으로 redirect
- 401 → refresh 시도; 403 → "권한 없음" 페이지; 5xx → 재시도 가능한 에러 화면

## Testing Strategy

- **Unit/Component**: Vitest + Testing Library — 모든 `features/<F>/components/*.tsx` 와 `hooks/*.ts`
- **Contract tests**: `shared/api/admin-api.test.ts` — admin-api.md 응답 스키마와 zod 파서 일치 검증
- **a11y**: `@axe-core/react` 통합, shadcn primitives 에 자동 검사
- **E2E (Playwright)**: 로그인 → 계정 검색 → lock → 감사 확인의 critical journey

## Observability

- **web-vitals**: LCP, INP, CLS, TTFB 를 `/api/web-vitals` route handler 로 전송 → 백엔드가 Prometheus 메트릭으로 노출 (별도 수집 서비스 미존재 시 콘솔로 대체)
- **Sentry (옵션)**: 운영 환경에서만 활성화, DSN 은 런타임 env
- **구조화 로그**: 서버 컴포넌트 / route handler 는 `requestId` + `operatorId` 컨텍스트 포함 JSON 로그

상세: [observability.md](observability.md)

## References

- [`platform/service-types/frontend-app.md`](../../../../../platform/service-types/frontend-app.md)
- [`auth-service/architecture.md`](../auth-service/architecture.md) — backend canonical 헤더 패턴
- [`fan-platform-web/architecture.md`](../../../../fan-platform/specs/services/fan-platform-web/architecture.md) — sibling frontend-app
- [`web-store/architecture.md`](../../../../ecommerce-microservices-platform/specs/services/web-store/architecture.md) — sibling frontend-app (FSD)
- [`admin-dashboard/architecture.md`](../../../../ecommerce-microservices-platform/specs/services/admin-dashboard/architecture.md) — sibling frontend-app (Layered by Feature)
- [`.claude/skills/frontend/architecture/layered-by-feature/SKILL.md`](../../../../../.claude/skills/frontend/architecture/layered-by-feature/SKILL.md)
- TASK-BE-275 — section pattern realignment to backend canonical
