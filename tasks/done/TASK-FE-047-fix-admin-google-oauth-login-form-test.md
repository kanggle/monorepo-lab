# TASK-FE-047: TASK-FE-040에서 발견된 LoginForm oauth_failed 에러 메시지 테스트 누락 수정

## Goal
TASK-FE-040 리뷰에서 발견된 이슈를 수정한다.
LoginForm에서 `error=oauth_failed` 쿼리 파라미터를 받아 에러 메시지를 표시하는 기능이 구현되어 있으나,
이에 대한 컴포넌트 테스트가 누락되어 있다.

## Scope
- `apps/admin-dashboard/src/__tests__/features/auth/components/LoginForm.test.tsx` (신규)

## Acceptance Criteria
- [ ] LoginForm 컴포넌트에 대한 테스트 파일 생성
- [ ] `error=oauth_failed` 쿼리 파라미터가 있을 때 "Google 로그인에 실패했습니다. 다시 시도해 주세요." 에러 메시지가 표시되는지 검증
- [ ] `error` 파라미터가 없을 때 에러 메시지가 표시되지 않는지 검증
- [ ] 모든 테스트 통과

## Related Specs
- `specs/services/admin-dashboard/overview.md`
- `specs/platform/testing-strategy.md`

## Related Contracts
- `specs/contracts/http/auth-api.md` — GET /api/auth/oauth/google

## Edge Cases
- `error` 쿼리 파라미터가 `oauth_failed`가 아닌 다른 값일 때는 에러 메시지를 표시하지 않음

## Failure Scenarios
- `useSearchParams()` 또는 `useAuth()` mock 설정 오류로 테스트 실패
