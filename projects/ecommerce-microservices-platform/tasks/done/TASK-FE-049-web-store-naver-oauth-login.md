# TASK-FE-049: web-store 네이버 로그인 버튼 추가

## Goal
web-store 로그인 폼에 네이버 로그인 버튼을 추가한다.
TASK-BE-120에서 구현한 백엔드 엔드포인트를 호출한다.

## Scope
- `apps/web-store/src/features/auth/ui/LoginForm.tsx`

## Acceptance Criteria
- [ ] 로그인 폼에 "네이버로 로그인" 버튼 추가
- [ ] 클릭 시 `GET /api/auth/oauth/naver?callbackUrl={origin}/oauth/callback`으로 이동
- [ ] 네이버 브랜드 컬러(#03C75A) 및 아이콘 적용
- [ ] 기존 Google, Instagram 버튼과 동일한 레이아웃 유지

## Related Specs
- `specs/services/auth-service/overview.md`

## Related Contracts
- `specs/contracts/http/auth-api.md`

## Edge Cases
- OAuth 콜백 페이지는 프로바이더 무관하게 토큰만 처리하므로 변경 불필요

## Failure Scenarios
- 백엔드 미구현 시 404 → 사용자에게 별도 안내 없음 (기존 패턴 동일)
