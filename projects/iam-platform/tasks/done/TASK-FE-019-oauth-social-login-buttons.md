---
id: TASK-FE-019
title: "로그인 페이지 소셜 로그인 버튼 UI (Google / Kakao / Microsoft)"
status: ready
area: frontend
service: admin-web
---

## Goal

admin-web 로그인 페이지(`/login`)에 소셜 로그인 버튼(Google, Kakao, Microsoft)을 추가한다. 버튼 클릭 시 auth-service의 OAuth 흐름(`GET /api/auth/oauth/authorize` → provider 리다이렉트 → `POST /api/auth/oauth/callback`)을 거쳐 로그인을 완료하고, 기존 이메일·패스워드 로그인과 동일하게 HttpOnly 쿠키에 JWT를 저장한다.

## Scope

### In

**Next.js Route Handlers** (BFF — auth-service의 OAuth 엔드포인트를 서버 사이드에서 중계):
- `apps/admin-web/src/app/api/auth/oauth/authorize/route.ts`
  - `GET ?provider=google|kakao|microsoft&redirectUri=...`
  - auth-service `GET /api/auth/oauth/authorize` 호출 → `{ authorizationUrl, state }` 클라이언트에 반환
- `apps/admin-web/src/app/api/auth/oauth/callback/route.ts`
  - `POST { provider, code, state, redirectUri }`
  - auth-service `POST /api/auth/oauth/callback` 호출
  - 성공 시 accessToken / refreshToken을 HttpOnly 쿠키에 설정 (기존 `/api/auth/login` route handler와 동일 방식)
  - 응답: `{ ok: true, isNewAccount: boolean }`

**UI**:
- `apps/admin-web/src/features/auth/components/SocialLoginButtons.tsx`
  - 구분선("또는") 포함
  - Google / Kakao / Microsoft 버튼 (아이콘 + 레이블)
  - 클릭 시: `/api/auth/oauth/authorize?provider=<provider>&redirectUri=<callback-url>` 호출 → `authorizationUrl`로 `window.location.assign()`
  - 로딩·에러 상태 관리
- `apps/admin-web/src/app/(auth)/oauth/callback/page.tsx`
  - provider redirect로 돌아오는 페이지 (`?code=&state=&provider=`)
  - `/api/auth/oauth/callback` POST 호출 → 성공 시 `/accounts`로 라우팅
  - 에러 시 `/login?error=<code>`로 라우팅
- `apps/admin-web/src/app/(auth)/login/page.tsx`
  - `<SocialLoginButtons />` 컴포넌트 포함

**에러 처리**:
- `ACCOUNT_LOCKED`, `ACCOUNT_DORMANT`, `ACCOUNT_DELETED` → 로그인 페이지에 한국어 에러 메시지 표시
- `INVALID_STATE` → "세션이 만료되었습니다. 다시 시도해주세요."
- `EMAIL_REQUIRED` → "소셜 계정에서 이메일 정보를 가져올 수 없습니다."
- `PROVIDER_ERROR` → "소셜 로그인 서비스에 일시적인 문제가 발생했습니다."
- `messageForCode` 헬퍼(`shared/api/errors`)에 위 코드 추가

### Out

- 소셜 계정 연결 해제 UI
- 이미 로그인된 계정에 소셜 provider 추가 연결 UI
- admin-service를 통한 운영자 OAuth 로그인 (현재 admin-service에 OAuth 지원 없음)
- Apple 로그인 버튼
- 모바일 앱 딥링크 처리

## Acceptance Criteria

- [ ] `/login` 페이지에 Google, Kakao, Microsoft 소셜 로그인 버튼이 표시됨
- [ ] 버튼 클릭 시 해당 provider의 OAuth 화면으로 이동
- [ ] OAuth callback 후 JWT 쿠키 설정 완료 → `/accounts`로 정상 리다이렉트
- [ ] `isNewAccount: true`인 경우에도 로그인 완료 처리 (신규 계정 자동 생성 결과 수용)
- [ ] `INVALID_STATE` (state 만료 or 불일치) → `/login` 에러 메시지 표시
- [ ] `EMAIL_REQUIRED` (Kakao 이메일 미동의) → `/login` 에러 메시지 표시
- [ ] `PROVIDER_ERROR` (provider 장애) → `/login` 에러 메시지 표시
- [ ] `ACCOUNT_LOCKED` / `ACCOUNT_DORMANT` / `ACCOUNT_DELETED` → `/login` 에러 메시지 표시
- [ ] `pnpm --filter admin-web build` 성공 (TypeScript 에러 없음)
- [ ] `pnpm --filter admin-web test` 통과

## Related Specs

- `specs/features/oauth-social-login.md` — BFF 패턴, provider token 비저장 원칙, 계정 연결 전략
- `specs/contracts/http/auth-api.md` — GET /api/auth/oauth/authorize, POST /api/auth/oauth/callback

## Related Contracts

- `specs/contracts/http/auth-api.md` (`GET /api/auth/oauth/authorize`, `POST /api/auth/oauth/callback`)
- 변경 없음 (기존 계약 소비만)

## Edge Cases

- `state` 파라미터는 서버(Next.js route handler)에서 관리 — 클라이언트 JS가 state를 localStorage에 저장하지 않도록 주의 (BFF 패턴: state는 auth-service Redis에 저장됨, 클라이언트는 단순 중계)
- OAuth callback URL(`redirectUri`)은 admin-web 도메인의 `/oauth/callback` 경로로 고정. `getServerEnv().NEXT_PUBLIC_APP_URL` 등에서 읽음
- 동일 state로 중복 callback 요청 시 두 번째는 auth-service에서 `INVALID_STATE` 반환 → 에러 페이지 처리
- provider redirect 중 사용자가 뒤로가기 → `/oauth/callback` 미도달 → state는 10분 후 자연 만료
- Kakao는 이메일 동의를 선택적으로 요구하므로, 동의하지 않은 경우 `EMAIL_REQUIRED` 에러

## Failure Scenarios

- **auth-service 장애**: authorize 또는 callback route handler에서 upstream 5xx → 502 에러 응답 → 로그인 페이지 "소셜 로그인 서비스에 일시적인 문제" 표시
- **Redis 장애 (auth-service 내부)**: state 저장 실패 → authorize 단계에서 502 → 에러 메시지 표시
- **Next.js route handler 환경 변수 누락**: `NEXT_PUBLIC_API_BASE_URL` 미설정 → getServerEnv() 실패 → 500 에러

## Test Requirements

- **Unit Tests** (`SocialLoginButtons.tsx`):
  - 각 버튼이 렌더링됨
  - 버튼 클릭 시 `/api/auth/oauth/authorize?provider=<provider>&redirectUri=...` 호출
  - 로딩 상태 중 버튼 비활성화
  - 에러 발생 시 에러 메시지 표시
- **Unit Tests** (`/oauth/callback/page.tsx`):
  - `?code=&state=&provider=` 파라미터 읽어 `/api/auth/oauth/callback` POST 호출
  - 성공 → router.push('/accounts')
  - 에러 → `/login?error=<code>`로 라우팅
- **Route Handler Tests** (`/api/auth/oauth/authorize`, `/api/auth/oauth/callback`):
  - auth-service upstream 호출 파라미터 검증
  - callback 성공 시 accessToken / refreshToken 쿠키 Set 확인
  - upstream 에러 응답 전달 확인
