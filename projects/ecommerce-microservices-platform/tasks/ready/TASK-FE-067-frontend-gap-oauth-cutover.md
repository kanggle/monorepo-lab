# Task ID

TASK-FE-067

# Title

ecommerce frontend (web-store + admin-dashboard) NextAuth provider GAP authorize cutover

# Status

ready

# Owner

frontend

# Task Tags

- code
- security
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

ecommerce 의 두 frontend 앱 (`web-store`, `admin-dashboard`) 의 OAuth provider 를 자체 ecommerce auth-service 에서 GAP (global-account-platform) 의 `/oauth2/authorize` endpoint 로 cutover 한다. TASK-MONO-027 (PR 머지됨, GAP V0012 시드 + ecommerce gateway 의 issuer-uri/validators) 머지 후 진행 가능 — 본 task 완료로 사용자가 GAP 에서 로그인 후 ecommerce 의 web-store / admin-dashboard 에 인증된 상태로 진입할 수 있게 된다.

cutover 완료 후:

- web-store 의 NextAuth (또는 OAuth client) 가 `ecommerce-web-store-client` client_id + GAP authorize / token / jwks endpoint 사용.
- admin-dashboard 의 NextAuth 가 `ecommerce-admin-dashboard-client` client_id + GAP endpoint 사용.
- 두 앱 모두 PKCE + confidential client (client_secret) 패턴 (fan-platform-web 의 NextAuth GAP 통합과 동일 — TASK-FAN-FE-001 reference).
- callback URL 이 V0012 시드의 redirect URIs 와 정확히 일치 (`/api/auth/callback/gap`).
- 기존 ecommerce auth-service 의 OAuth flow (Google login → ecommerce auth-service callback → JWT 발급) 코드 경로는 본 task 에서 제거.

---

# Scope

## In Scope

### 1. `apps/web-store/` NextAuth GAP provider

- `apps/web-store/src/app/api/auth/[...nextauth]/route.ts` (또는 동급 파일) 의 OAuth provider 를 GAP 으로 전환:
  - clientId / clientSecret 환경 변수 (`OIDC_CLIENT_ID`, `OIDC_CLIENT_SECRET`)
  - issuer / authorize / token / jwks URL = `OIDC_ISSUER_URL`
  - PKCE 강제, `scope=openid profile email tenant.read ecommerce.consumer`
- 기존 Google OAuth 또는 ecommerce auth-service 자체 callback 핸들러 제거.
- session callback 에서 `account_type=CONSUMER` 검증 — `OPERATOR` 면 web-store 로그인 차단 (admin-dashboard 로 안내).
- `tenant_id=ecommerce` claim 검증 (defense-in-depth — gateway 가 이미 검증하지만 frontend 도 redirect 단계에서 1차 차단).

### 2. `apps/admin-dashboard/` NextAuth GAP provider

- web-store 와 동일 패턴, 다른 점:
  - clientId = `ecommerce-admin-dashboard-client`
  - `scope=openid profile email tenant.read ecommerce.operator`
  - session callback 에서 `account_type=OPERATOR` 검증 — `CONSUMER` 면 admin-dashboard 진입 차단.

### 3. 환경 변수 정리

- `apps/web-store/.env.local.example` (있으면) + `apps/admin-dashboard/.env.local.example` 에 `OIDC_ISSUER_URL`, `OIDC_CLIENT_ID`, `OIDC_CLIENT_SECRET`, `NEXTAUTH_URL` 등 표준화.
- 기존 Google OAuth env (`AUTH_GOOGLE_CLIENT_ID` 등) 제거.

### 4. 통합 테스트 / e2e 갱신

- web-store / admin-dashboard 의 기존 Playwright e2e (TASK-MONO-013 / 014) 가 GAP login flow 시나리오 추가:
  - `/login` → GAP `/oauth2/authorize` → callback → 인증된 페이지 진입 (golden flow).
  - operator 가 web-store 로그인 시도 → 거부.
  - consumer 가 admin-dashboard 로그인 시도 → 거부.

### 5. spec 갱신

- `projects/ecommerce-microservices-platform/specs/services/web-store/architecture.md` (또는 frontend 스펙 파일) 에 GAP 통합 섹션 추가.
- `projects/ecommerce-microservices-platform/specs/services/admin-dashboard/architecture.md` 동일.

## Out of Scope

- 기존 web-store / admin-dashboard 사용자 세션 마이그레이션 — cutover 직후 모든 사용자 재로그인 필요. portfolio 범위라 영향 없음.
- 자체 ecommerce auth-service 컴포넌트 제거 — TASK-BE-132 가 담당.
- ecommerce 사용자 계정 데이터 GAP 마이그레이션 — portfolio 범위 밖.
- web-store 의 social login provider (Google / Naver / Kakao) 는 GAP 가 외부 IdP 로 통합 후 제공 — 본 task 는 GAP 자체 가입 / 로그인만.

---

# Acceptance Criteria

- [ ] web-store 의 NextAuth provider 가 GAP `/oauth2/authorize` 로 redirect.
- [ ] web-store callback URL `http://localhost:3000/api/auth/callback/gap` (dev) / `http://web.ecommerce.local/api/auth/callback/gap` (Traefik) 동작.
- [ ] admin-dashboard 의 NextAuth provider 가 GAP `/oauth2/authorize` 로 redirect, `ecommerce-admin-dashboard-client` 사용.
- [ ] admin-dashboard callback URL `http://localhost:3001/api/auth/callback/gap` / `http://admin.ecommerce.local/api/auth/callback/gap` 동작.
- [ ] consumer 가 web-store 로그인 → 200 진입, admin-dashboard 로그인 → 거부.
- [ ] operator 가 admin-dashboard 로그인 → 200 진입, web-store 로그인 → 거부.
- [ ] Playwright golden-flow + auth-redirect e2e 갱신 + 통과.
- [ ] 기존 Google OAuth / ecommerce auth-service callback 코드 제거.

---

# Related Specs

- `projects/ecommerce-microservices-platform/specs/integration/gap-integration.md` — GAP 통합 본문 (TASK-MONO-027 산출물)
- `projects/ecommerce-microservices-platform/specs/services/web-store/` — 갱신 대상
- `projects/ecommerce-microservices-platform/specs/services/admin-dashboard/` — 갱신 대상
- `projects/global-account-platform/specs/features/consumer-integration-guide.md` § Phase 4 (사용자 인증 위임 PKCE flow)
- `projects/fan-platform/apps/fan-platform-web/` — fan-platform-web NextAuth GAP integration reference (TASK-FAN-FE-001)

# Related Contracts

- `projects/global-account-platform/specs/contracts/http/auth-api.md` § OAuth2 / OIDC Endpoints
- `projects/global-account-platform/specs/contracts/http/gateway-api.md` — `X-Tenant-Id` 헤더 전파

---

# Target Service

- `apps/web-store/`
- `apps/admin-dashboard/`

---

# Architecture

`platform/architecture-decision-rule.md` 따름. NextAuth (또는 next-auth v5) 의 OAuth provider 만 교체 — UI 흐름 변경 없음.

---

# Implementation Notes

- fan-platform-web 의 NextAuth GAP integration 코드 (`projects/fan-platform/apps/fan-platform-web/` 의 NextAuth route handler) 가 가장 가까운 reference — 거의 그대로 복사 + client_id 만 다름.
- PKCE: next-auth v5 가 자동 처리 (default `pkce: true`).
- session callback 의 token 검증: NextAuth `jwt` callback 에서 access_token 의 `account_type` claim 을 `session.user.accountType` 으로 expose → page-level guard.
- environment 분리: `.env.local` 은 `localhost:3000` / `localhost:3001` 로컬 dev, `.env.production` (또는 `NEXTAUTH_URL` env) 은 Traefik hostname 사용.

---

# Edge Cases

- **redirect URI mismatch**: V0012 시드의 redirect_uris 에 두 환경 (`localhost` + `*.local`) 모두 등록되어 있어야. 누락되면 GAP `/oauth2/authorize` 가 `invalid_redirect_uri` 응답.
- **PKCE state cookie 만료**: NextAuth 가 Cookie 기반 state 사용 — 동일 도메인 다른 path 접근 시 cookie 격리. dev 시 `localhost` 와 `*.local` 가 별도 도메인이라 동시 사용 시 cookie 분리.
- **session JWT vs OIDC access token**: NextAuth 의 session JWT (frontend → backend 식별용) 는 access_token 자체를 옮기는 게 아니라 separate 한 stateless cookie. backend API 호출 시 `session.accessToken` 을 `Authorization: Bearer` 헤더로 전송.
- **operator 가 web-store 에서 Google 로그인 시도**: GAP 가 발급한 토큰의 `account_type=OPERATOR` 면 web-store 의 session callback 이 reject → `/login?error=account_type_mismatch`.

---

# Failure Scenarios

- **GAP 미가용 시 web-store 로그인 시도**: NextAuth 가 GAP `/oauth2/authorize` redirect 자체 실패. 사용자에게 명확한 에러 페이지 (`error=oauth_provider_unavailable`).
- **client_secret 오타 / 잘못된 hash**: GAP `/oauth2/token` 이 `invalid_client` 응답. NextAuth 콘솔 로그 확인.
- **PKCE code_verifier mismatch**: NextAuth 가 code_verifier 를 cookie 에 저장 — 사용자가 incognito 모드 또는 cookie 삭제 후 callback 진입 시 발생. 재로그인 안내.

---

# Test Requirements

- 단위: NextAuth provider config 단위 테스트 (있으면) — clientId / scope / endpoint 정확성.
- e2e: Playwright `auth-redirect.spec.ts` 갱신
  - golden flow: consumer 가입 → web-store 로그인 → cart 추가 → 주문 → 200.
  - account_type mismatch: operator 가 web-store 로그인 시도 → 거부.
  - account_type mismatch: consumer 가 admin-dashboard 로그인 시도 → 거부.

---

# Definition of Done

- [ ] web-store / admin-dashboard 의 NextAuth provider GAP cutover.
- [ ] 두 앱의 e2e (golden + auth-redirect) 통과.
- [ ] frontend env 정리 (Google OAuth 제거).
- [ ] spec 갱신 (web-store / admin-dashboard architecture).
- [ ] Ready for review.

---

# Prerequisites

- TASK-MONO-027 머지 완료 (GAP V0012 시드 + ecommerce gateway issuer-uri/validators).
