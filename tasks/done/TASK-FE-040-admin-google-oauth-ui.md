# TASK-FE-040: admin-dashboard Google OAuth 로그인 UI 구현

## Goal
admin-dashboard 로그인 페이지에 Google 로그인 버튼과 OAuth 콜백 페이지를 추가한다.
백엔드는 TASK-BE-114에서 구현한 공통 OAuth 엔드포인트를 사용한다.

## Scope
- `apps/admin-dashboard`
  - `src/features/auth/components/SocialLoginButtons.tsx` (신규)
  - `src/app/oauth/callback/page.tsx` (신규)
  - `src/features/auth/components/LoginForm.tsx` (수정 — 소셜 버튼 추가)

## Acceptance Criteria
- [ ] 로그인 폼 하단에 구분선("또는")과 Google 로그인 버튼 표시
- [ ] Google 버튼 클릭 시 `GET {API_BASE}/api/auth/oauth/google?callbackUrl={adminCallbackUrl}` 로 리다이렉트
  - `adminCallbackUrl` = `{NEXT_PUBLIC_ADMIN_URL}/oauth/callback` (예: `http://localhost:3001/oauth/callback`)
- [ ] `/oauth/callback` 페이지: `accessToken`, `refreshToken` 쿼리 파라미터를 저장 후 `/dashboard`로 이동
- [ ] `error=oauth_failed` 쿼리 파라미터가 있으면 `/login?error=oauth_failed`로 이동
- [ ] 로그인 페이지에서 `error=oauth_failed` 쿼리 파라미터가 있으면 에러 메시지 표시
- [ ] 컴포넌트 테스트 추가

## Related Specs
- `specs/services/admin-dashboard/overview.md`

## Related Contracts
- `specs/contracts/http/auth-api.md` — GET /api/auth/oauth/google

## Implementation Notes
- web-store의 `SocialLoginButtons`, `/oauth/callback` 구현을 참고해 동일 패턴 적용
- `callbackUrl` 파라미터에 전달할 admin 도메인은 `NEXT_PUBLIC_ADMIN_URL` 환경변수로 관리
  - `apps/admin-dashboard/.env.example`에 `NEXT_PUBLIC_ADMIN_URL=http://localhost:3001` 추가
- `saveTokens`는 `@repo/api-client`에서 import (web-store와 동일)
- Instagram 버튼은 추가하지 않음 (어드민은 Google만)

## Edge Cases
- `accessToken` 또는 `refreshToken`이 없는 콜백 → `/login?error=oauth_failed` 이동
- 콜백 도달 전 사용자가 이미 로그인 상태 → `/dashboard`로 바로 이동

## Failure Scenarios
- `NEXT_PUBLIC_ADMIN_URL` 미설정 → 빌드 타임 경고 또는 fallback `http://localhost:3001`
- 백엔드 OAuth 실패 → error 쿼리 파라미터로 로그인 페이지 에러 표시
