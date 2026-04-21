# Task ID

TASK-FE-053-fix-001

# Title

TASK-FE-053 리뷰 수정 — useAuth 훅 규칙 위반, window.alert SSR 호환, 구매검증 에러 처리

# Status

review

# Owner

frontend

# Task Tags

- code
- test

# Goal

TASK-FE-053 리뷰에서 발견된 critical/warning 이슈를 수정한다.

# Scope

## In Scope

- ReviewList.tsx의 `useAuth` try/catch 제거 → 훅 최상단에서 호출하도록 수정
- use-create-review.ts, use-update-review.ts, use-delete-review.ts에서 `window.alert()` 제거 → 인라인 에러 상태 또는 mutation 에러 전파 방식으로 변경
- use-create-review.ts에서 422 `PRODUCT_NOT_PURCHASED` 에러에 대한 명확한 한국어 메시지 처리
- 중복 에러 처리 제거 (mutation onError + ReviewForm catch 이중 처리)
- my-reviews-page.test.tsx의 미사용 mock 정리
- 409 중복 리뷰 작성 테스트 추가

## Out of Scope

- 기능 추가
- 컨트랙트 변경

# Acceptance Criteria

- [ ] `useAuth`가 try/catch 없이 훅 최상단에서 호출된다
- [ ] `window.alert` 사용이 모두 제거된다
- [ ] `PRODUCT_NOT_PURCHASED` 에러 시 "구매한 상품에만 리뷰를 작성할 수 있습니다" 메시지가 표시된다
- [ ] 에러 처리가 이중으로 되지 않는다
- [ ] 기존 테스트가 통과하고 신규 테스트가 추가된다

# Related Specs

- `specs/contracts/http/review-api.md`

# Related Contracts

- `specs/contracts/http/review-api.md`

# Edge Cases

- useAuth Provider가 없는 환경에서의 동작

# Failure Scenarios

- SSR 환경에서 window 미존재
