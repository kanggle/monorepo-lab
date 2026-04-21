# Task ID

TASK-FE-040

# Title

users/page.tsx Suspense 로딩 테스트 추가 (TASK-FE-037 fix)

# Status

review

# Owner

frontend

# Task Tags

- test

# Goal

TASK-FE-037 리뷰에서 발견된 누락 이슈 수정. users/page.tsx에 대한 Suspense 로딩 상태 렌더링 테스트가 누락되어 있다. orders/page.test.tsx와 동일한 패턴으로 users/page.test.tsx를 추가한다.

Fix issue found in TASK-FE-037.

# Scope

## In Scope

- `apps/admin-dashboard/src/__tests__/app/users/page.test.tsx` 테스트 파일 생성
- Suspense fallback으로 LoadingSpinner 표시 확인 테스트
- 데이터 로드 후 사용자 목록 표시 확인 테스트

## Out of Scope

- users/page.tsx 구현 변경
- 다른 페이지 테스트 추가

# Acceptance Criteria

- [ ] users/page.tsx에 대한 Suspense 로딩 상태 렌더링 테스트가 존재한다
- [ ] 테스트가 통과한다

# Related Specs

- `specs/services/admin-dashboard/architecture.md`
- `specs/platform/testing-strategy.md`

# Related Contracts

없음

# Edge Cases

- 없음

# Failure Scenarios

- 없음

# Test Requirements

- Suspense fallback에서 LoadingSpinner가 렌더링되는지 확인
- 데이터 로드 완료 후 사용자 목록이 정상 표시되는지 확인

# Definition of Done

- [ ] Tests added
- [ ] Tests passing
- [ ] Ready for review
