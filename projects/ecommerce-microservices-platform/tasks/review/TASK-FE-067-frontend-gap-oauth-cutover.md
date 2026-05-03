# Task ID

TASK-FE-067

# Title

ecommerce frontend (web-store + admin-dashboard) 의 GAP OIDC cutover — NextAuth v5 신규 도입 + 자체 auth flow 폐기

# Status

ready

# Owner

frontend

# Task Tags

- code
- security
- api
- breaking

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

ecommerce 의 두 frontend 앱 (`web-store`, `admin-dashboard`) 의 인증 flow 를 자체 ecommerce auth-service 직접 호출 패턴에서 GAP (global-account-platform) OIDC 표준 흐름으로 cutover 한다. TASK-MONO-027 (PR #145 머지됨, GAP V0012 시드 + ecommerce gateway 의 issuer-uri/validators) 머지 후 진행 가능 — 본 task 완료로 사용자가 GAP 에서 로그인 후 ecommerce 의 web-store / admin-dashboard 에 인증된 상태로 진입한다.

cutover 완료 후:

- web-store 가 next-auth v5 (auth.js) + GAP OIDC provider 를 사용. fan-platform-web 의 `src/shared/auth/auth.ts` 가 reference 패턴.
- admin-dashboard 도 동일 패턴, 다른 client_id (`ecommerce-admin-dashboard-client`) 사용.
- 두 앱 모두 PKCE + confidential client (client_secret) — V0012 시드의 redirect URIs 와 정확히 일치.
- 기존 자체 인증 코드 폐기: `src/features/auth/api/auth-actions.ts` (signup/login/logout), `auth-context.tsx`, `LoginForm`, signup page 등 큰 폭의 리팩터.
- account_type 기반 cross-app 차단 (consumer→admin-dashboard 거부, operator→web-store 거부).

---

# Important — 본 task 의 실제 작업 규모

⚠️ **이 task 는 단순 "OAuth provider 교체" 가 아니다.** 검증 결과 ecommerce 의 web-store / admin-dashboard 는 next-auth 를 **사용하지 않는** 상태이고, `@repo/api-client` 의 `createAuthApi` 로 직접 ecommerce auth-service 의 `/api/auth/login` 등을 호출한다. 따라서 본 task 는:

1. **next-auth v5 신규 도입** (의존성 추가, 환경변수 정리, route handler 신설)
2. **기존 자체 auth flow 의 큰 폭 폐기/대체** (auth-actions, auth-context, LoginForm, signup page, useAuth 훅 등 다수 파일)
3. **`@repo/api-client` 의 token 주입 방식 변경** — 현 패턴 (apiClient 가 자체 token 헤더 관리) → next-auth session 의 access_token 사용
4. **fan-platform-web 패턴 두 앱으로 복제** — 두 앱이 다른 client_id, 다른 redirect URI, 다른 scope (`ecommerce.consumer` vs `ecommerce.operator`)
5. **e2e (Playwright) 의 로그인 helper 거의 다시 작성** — 기존 `e2e/helpers/auth.ts` 가 자체 auth-service POST 호출 패턴이었을 것 → next-auth signIn / GAP authorize 흐름으로

작업 규모: **agent 위임 시 2-4시간, 큰 코드 churn**. fan-platform-web 패턴 (TASK-FAN-FE-001) 이 거의 그대로 복제 가능하지만 두 앱 동시 작업 + 기존 auth 코드 폐기라 비용 큼.

---

# Scope

## In Scope

### 1. `apps/web-store/` next-auth v5 신규 도입

- `package.json` 에 `next-auth@^5` 의존성 추가.
- `src/auth.ts` (또는 `src/shared/auth/auth.ts`) 신설 — fan-platform-web 의 `src/shared/auth/auth.ts` 패턴 그대로 복사 + 변경:
  - provider id `gap`, issuer = `OIDC_ISSUER_URL`, clientId = `ECOMMERCE_WEB_STORE_CLIENT_ID`, clientSecret = `ECOMMERCE_WEB_STORE_CLIENT_SECRET`
  - scope: `openid profile email tenant.read ecommerce.consumer`
  - callbacks: account_type / tenant_id / roles 를 session 으로 expose
  - authorized callback 에서 `account_type=CONSUMER` 검증 — `OPERATOR` 면 web-store 진입 거부 (`/login?error=account_type_mismatch`)
- `src/app/api/auth/[...nextauth]/route.ts` — `handlers` re-export
- `src/middleware.ts` — protected paths 기준 (fan-platform-web 패턴 reference)
- `.env.local.example` 신설 (또는 갱신): `NEXTAUTH_SECRET`, `NEXTAUTH_URL`, `OIDC_ISSUER_URL`, `ECOMMERCE_WEB_STORE_CLIENT_ID=ecommerce-web-store-client`, `ECOMMERCE_WEB_STORE_CLIENT_SECRET=ecommerce-dev`

### 2. `apps/web-store/` 기존 자체 auth flow 폐기

- `src/features/auth/api/auth-actions.ts` 삭제 또는 next-auth 의 signIn/signOut wrapper 로 변경
- `src/features/auth/model/auth-context.tsx` — next-auth 의 `useSession` 으로 대체
- `src/shared/lib/auth-context.ts` — next-auth 패턴으로 전환
- `LoginForm` 컴포넌트 — `signIn('gap')` 호출
- `/signup` 페이지 — GAP 의 signup endpoint 로 redirect 또는 GAP authorize 가 self-service signup 도 처리하므로 단순 redirect
- `__tests__/` 의 auth-context / auth-actions 테스트 갱신

### 3. `apps/admin-dashboard/` next-auth v5 신규 도입

- web-store 와 동일 패턴, 다른 점:
  - clientId = `ecommerce-admin-dashboard-client`
  - scope = `openid profile email tenant.read ecommerce.operator`
  - account_type 검증: `OPERATOR` 만 통과, `CONSUMER` 면 `/login?error=account_type_mismatch`
- 기존 admin-dashboard 의 인증 코드 폐기

### 4. `@repo/api-client` 의 token 주입 변경

- 기존: apiClient 가 자체 access_token 을 cookie 또는 store 에서 읽어 `Authorization: Bearer` 헤더 주입
- 변경: NextAuth 의 `auth()` (server-side) 또는 `useSession()` (client-side) 의 `session.accessToken` 을 사용
- server actions / RSC: `auth()` 로 session 가져와 fetch 호출
- client components: `useSession()` 또는 `getSession()` 으로 session 가져와 fetch
- (옵션) 공통 helper `getAccessToken()` 함수 — fan-platform-web 의 `src/shared/auth/session.ts` 패턴

### 5. e2e (Playwright) 갱신

- `apps/web-store/e2e/helpers/auth.ts` — 기존 self-service signup/login flow 가 next-auth signIn 흐름으로 변경
- `e2e/golden-flow.spec.ts`, `e2e/auth-redirect.spec.ts`, `e2e/cart-management.spec.ts`, `e2e/wishlist.spec.ts` — fixture 갱신
- e2e 시 GAP 가 띄워져 있어야 함 (TASK-MONO-014 frontend-e2e CI 잡 의 docker-compose 에 GAP 컨테이너 추가)
- account_type cross-app 시나리오 신규 추가:
  - consumer 가 web-store 로그인 → 200, admin-dashboard 로그인 → 거부
  - operator 가 admin-dashboard 로그인 → 200, web-store 로그인 → 거부

### 6. spec 갱신

- `projects/ecommerce-microservices-platform/specs/services/web-store/architecture.md` (또는 신설) — GAP 통합 섹션 추가
- `projects/ecommerce-microservices-platform/specs/services/admin-dashboard/architecture.md` 동일

### 7. standalone portfolio repo 분기 영향 명시

- `scripts/sync-portfolio.sh` 의 `SHARED_PATHS` 또는 sync 정책 검토 — 모노레포의 ecommerce GAP 통합 변경이 standalone `kanggle/ecommerce-microservices-platform` 으로 sync 되면 standalone repo 가 GAP 의존성을 갖게 됨. portfolio v1 (standalone) 은 자체 auth-service 모드 유지가 의도이므로:
  - **옵션 a (권장)**: sync-portfolio.sh 가 ecommerce GAP-related 변경 (V0012 시드 path, NextAuth code path) 을 standalone 으로 sync 하지 않도록 exclusion 추가
  - **옵션 b**: standalone 도 GAP 통합 — portfolio v2 신호로 standalone repo 도 갱신
  - **옵션 c**: standalone repo 를 archive 하고 모노레포로 portfolio 평가 통일 (사용자 결정 필요)
- 본 task 는 (a) 를 채택. 단순 dev sync exclusion. standalone v1 보존.

## Out of Scope

- 기존 web-store / admin-dashboard 사용자 세션 마이그레이션 — cutover 직후 모든 사용자 재로그인 필요. portfolio 범위라 영향 없음.
- 자체 ecommerce auth-service 컴포넌트 제거 — TASK-BE-132 가 담당 (본 task 후속).
- ecommerce 사용자 계정 데이터 GAP 마이그레이션 — portfolio 범위 밖.
- web-store 의 social login provider (Google / Naver / Kakao) — GAP 이 외부 IdP 와 통합 후 제공. 본 task 는 GAP 자체 가입 / 로그인만.
- portfolio v1 standalone repo 의 GAP 통합 — 본 task 는 (a) 옵션으로 standalone 영향 차단. 통합은 별도 사용자 결정.

---

# Acceptance Criteria

- [ ] web-store 의 NextAuth provider 가 GAP `/oauth2/authorize` 로 redirect.
- [ ] web-store callback URL `http://localhost:3000/api/auth/callback/gap` (dev) / `http://web.ecommerce.local/api/auth/callback/gap` (Traefik) 동작.
- [ ] admin-dashboard 의 NextAuth provider 가 `ecommerce-admin-dashboard-client` 사용 + GAP redirect.
- [ ] admin-dashboard callback URL `http://localhost:3001/api/auth/callback/gap` / `http://admin.ecommerce.local/api/auth/callback/gap` 동작.
- [ ] consumer 가 web-store 로그인 → 200 진입, admin-dashboard 로그인 → 거부.
- [ ] operator 가 admin-dashboard 로그인 → 200 진입, web-store 로그인 → 거부.
- [ ] Playwright golden-flow + auth-redirect e2e 갱신 + 통과.
- [ ] 기존 `auth-actions.ts` / `auth-context.tsx` / `LoginForm` 의 자체 auth flow 코드 제거.
- [ ] `@repo/api-client` 의 token 주입이 next-auth session 기반으로 동작.
- [ ] `pnpm --filter web-store run lint && pnpm --filter web-store run test` PASS.
- [ ] `pnpm --filter admin-dashboard run lint && pnpm --filter admin-dashboard run test` PASS.
- [ ] sync-portfolio.sh 가 GAP-related 경로를 standalone 으로 sync 하지 않도록 exclusion 추가.

---

# Related Specs

- `projects/ecommerce-microservices-platform/specs/integration/gap-integration.md` — GAP 통합 본문 (TASK-MONO-027 산출물)
- `projects/fan-platform/web/fan-platform-web/src/shared/auth/auth.ts` — primary reference (NextAuth v5 + GAP OIDC)
- `projects/fan-platform/web/fan-platform-web/src/shared/auth/session.ts` — getAccessToken helper reference
- `projects/global-account-platform/specs/features/consumer-integration-guide.md` § Phase 4 (PKCE flow)
- `tasks/done/TASK-MONO-026-gap-v0011-fan-platform-oidc-clients.md` — fan-platform 의 V0011 시드 + frontend cutover 패턴

# Related Contracts

- `projects/global-account-platform/specs/contracts/http/auth-api.md` § OAuth2 / OIDC Endpoints
- `projects/global-account-platform/specs/contracts/http/gateway-api.md` — `X-Tenant-Id` 헤더 전파

---

# Target Service

- `apps/web-store/`
- `apps/admin-dashboard/`
- `packages/api-client/` (token 주입 변경)
- `scripts/sync-portfolio.sh` (sync exclusion)

---

# Architecture

`platform/architecture-decision-rule.md` 따름. fan-platform-web 의 NextAuth v5 패턴을 두 앱에 복제.

---

# Implementation Notes

- **fan-platform-web reference**: `projects/fan-platform/web/fan-platform-web/src/shared/auth/auth.ts` 의 authConfig 거의 그대로 복사 → ecommerce 의 client_id / scope / account_type guard 만 변경.
- **agent dispatch**: 작업이 큼. frontend-engineer agent worktree (opus 모델) 에 위임 권장. context: fan-platform-web 패턴 + 두 앱 + sync-portfolio.sh exclusion.
- **점진 진행**: 본 task 를 web-store 만 / admin-dashboard 만 으로 분해 가능. 다만 두 앱이 같은 GAP 시드 (V0012) 를 공유하므로 cutover 가 함께 가는 게 일관됨.
- **standalone portfolio 분기**: 옵션 (a) 채택. sync-portfolio.sh 의 SHARED_PATHS exclusion 또는 file filter 추가. standalone v1 의 자체 auth-service 보존.

---

# Edge Cases

- **redirect URI mismatch**: V0012 시드의 redirect_uris 에 두 환경 (`localhost` + `*.local`) 모두 등록되어 있어야. 누락되면 GAP `/oauth2/authorize` 가 `invalid_redirect_uri` 응답.
- **PKCE state cookie 만료**: NextAuth 가 Cookie 기반 state 사용 — 동일 도메인 다른 path 접근 시 cookie 격리.
- **session JWT vs OIDC access token**: NextAuth 의 session JWT (frontend → backend 식별용) 는 access_token 자체를 옮기는 게 아니라 separate stateless cookie. backend API 호출 시 `session.accessToken` 을 `Authorization: Bearer` 헤더로 전송.
- **operator 가 web-store 로그인 시도**: GAP 토큰 `account_type=OPERATOR` → web-store 의 authorized callback 또는 session callback 에서 reject.
- **dev 환경에서 GAP 미가용**: NextAuth 가 lazy provider config 라 GAP 없어도 boot/lint/smoke-test 통과 (fan-platform-web 패턴). 그러나 실제 sign-in 시도 시 discovery doc fetch 실패 → 에러.

---

# Failure Scenarios

- **GAP 미가용 시 로그인 시도**: NextAuth 가 GAP `/oauth2/authorize` redirect 자체 실패. 사용자에게 명확한 에러 페이지.
- **client_secret 오타 / hash 불일치**: GAP `/oauth2/token` `invalid_client` 응답. NextAuth 콘솔 로그 확인.
- **PKCE code_verifier mismatch**: NextAuth 가 code_verifier 를 cookie 에 저장 — incognito 또는 cookie 삭제 후 callback 진입 시 발생. 재로그인 안내.
- **기존 프로덕션 사용자**: portfolio dev 환경이라 기존 사용자 없음 — N/A.

---

# Test Requirements

- 단위: NextAuth provider config 단위 테스트 (있으면) — clientId / scope / endpoint 정확성. account_type guard 단위 테스트.
- 슬라이스: web-store / admin-dashboard 의 LoginForm + middleware 슬라이스 테스트.
- e2e: Playwright `auth-redirect.spec.ts` + `golden-flow.spec.ts` 갱신
  - golden flow: consumer 가입 → web-store 로그인 → cart 추가 → 주문 → 200.
  - account_type mismatch: operator 가 web-store 로그인 시도 → 거부.
  - account_type mismatch: consumer 가 admin-dashboard 로그인 시도 → 거부.

---

# Definition of Done

- [ ] web-store / admin-dashboard 의 NextAuth provider GAP cutover.
- [ ] 두 앱의 e2e (golden + auth-redirect) 통과.
- [ ] frontend env 정리 (자체 auth-service 환경변수 제거).
- [ ] `@repo/api-client` token 주입 next-auth session 기반.
- [ ] sync-portfolio.sh 의 ecommerce GAP-related 경로 sync exclusion.
- [ ] spec 갱신 (web-store / admin-dashboard architecture).
- [ ] Ready for review.

---

# Prerequisites

- ✅ TASK-MONO-027 머지 완료 (PR #145, GAP V0012 시드 + ecommerce gateway issuer-uri/validators).
- 권장: 본 task 머지가 TASK-BE-132 (auth-service 컴포넌트 제거) 보다 **먼저 또는 같이** 머지. 그래야 dev 환경이 cutover 중간 단계에서 깨지지 않음.
