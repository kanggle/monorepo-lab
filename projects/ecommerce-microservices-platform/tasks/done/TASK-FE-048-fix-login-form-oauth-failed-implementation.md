# TASK-FE-048: TASK-FE-047 리뷰에서 발견된 LoginForm oauth_failed 처리 로직 누락 수정

## Goal
TASK-FE-047 리뷰에서 발견된 이슈를 수정한다.
`LoginForm.tsx`에 `useSearchParams`를 사용해 `error=oauth_failed` 쿼리 파라미터를 읽고
"Google 로그인에 실패했습니다. 다시 시도해 주세요." 에러 메시지를 표시하는 로직이 없어
테스트가 실패한다.

## Scope
- `apps/admin-dashboard/src/features/auth/components/LoginForm.tsx` (수정)

## Acceptance Criteria
- [ ] `LoginForm.tsx`에서 `useSearchParams`를 import하여 `error` 쿼리 파라미터를 읽는다
- [ ] `error === 'oauth_failed'`일 때 초기 에러 상태를 "Google 로그인에 실패했습니다. 다시 시도해 주세요."로 설정한다
- [ ] `error` 파라미터가 없거나 `oauth_failed`가 아닌 다른 값이면 에러 메시지를 표시하지 않는다
- [ ] 기존 `LoginForm.test.tsx`의 3개 테스트가 모두 통과한다

## Related Specs
- `specs/services/admin-dashboard/overview.md`
- `specs/platform/testing-strategy.md`

## Related Contracts
- `specs/contracts/http/auth-api.md` — GET /api/auth/oauth/google

## Edge Cases
- `error` 쿼리 파라미터가 `oauth_failed`가 아닌 다른 값일 때는 에러 메시지를 표시하지 않음
- `error` 파라미터가 없을 때 에러 메시지 미표시

## Failure Scenarios
- `useSearchParams()` 호출 시 Next.js Suspense 경계 필요 여부 확인
