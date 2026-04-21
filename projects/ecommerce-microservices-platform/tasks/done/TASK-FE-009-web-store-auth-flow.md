# TASK-FE-009: web-store 인증 플로우 구현 — 로그인, 회원가입, 세션 관리

## Goal
web-store에 인증 플로우를 구현한다.
회원가입, 로그인, 로그아웃 기능과 세션 상태 관리(AuthProvider/useAuth)를 추가한다.

## Scope
- `features/auth/` — 인증 피처 모듈 구현
  - `api/` — auth API 호출 래퍼 (login, signup, logout)
  - `model/` — AuthContext, useAuth 훅, 타입 정의
  - `ui/` — LoginForm, SignupForm 컴포넌트
  - `index.ts` — 공개 API
- `app/(auth)/login/page.tsx` — 로그인 페이지 구현
- `app/(auth)/signup/page.tsx` — 회원가입 페이지 구현
- `app/layout.tsx` — AuthProvider 래핑
- 테스트 추가

## Acceptance Criteria
- 회원가입 폼: email, password, name 입력 → 성공 시 로그인 페이지로 이동
- 로그인 폼: email, password 입력 → 성공 시 토큰 저장 후 홈으로 이동
- AuthProvider가 앱 전체를 감싸고 인증 상태를 제공한다
- useAuth 훅으로 { user, isAuthenticated, login, signup, logout } 접근 가능
- 로그아웃 시 토큰 제거 후 로그인 페이지로 이동
- 에러 발생 시 사용자에게 에러 메시지 표시 (EMAIL_ALREADY_EXISTS, INVALID_CREDENTIALS 등)
- 폼 validation: email 형식, password 최소 8자, name 필수
- 기존 빌드가 깨지지 않는다

## Related Specs
- `specs/services/web-store/architecture.md`
- `specs/contracts/http/auth-api.md`
- `specs/platform/api-gateway-policy.md`

## Related Contracts
- `specs/contracts/http/auth-api.md`

## Edge Cases
- 이미 로그인된 상태에서 login/signup 페이지 접근 시 홈으로 리다이렉트
- 토큰 만료 후 페이지 새로고침 시 로그아웃 상태로 전환
- 네트워크 오류 시 에러 메시지 표시
- 폼 제출 중 중복 클릭 방지 (로딩 상태)

## Failure Scenarios
- API 서버 미응답 시 NETWORK_ERROR 폴백 메시지
- 잘못된 이메일/비밀번호 시 INVALID_CREDENTIALS 에러 표시
- 이메일 중복 시 EMAIL_ALREADY_EXISTS 에러 표시
