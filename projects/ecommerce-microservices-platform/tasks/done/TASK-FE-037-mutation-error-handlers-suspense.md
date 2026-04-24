# Task ID

TASK-FE-037

# Title

admin-dashboard mutation 에러 핸들러 및 Suspense 일관성 추가

# Status

review

# Owner

frontend

# Task Tags

- code
- test

# Goal

admin-dashboard의 mutation 훅들에 `onError` 핸들러가 누락되어 API 호출 실패 시 사용자에게 피드백이 없다. 또한 페이지별 Suspense 적용이 불일치하여 로딩 상태 처리가 일관되지 않는다.

수정 대상:
1. mutation 훅(use-cancel-order, use-create-product, use-update-product, use-adjust-stock)에 `onError` 콜백 추가
2. orders/page.tsx, users/page.tsx에 Suspense 래퍼 추가 (products/page.tsx와 동일하게)
3. OrderDetail의 취소 버튼에 mutation pending 시 disabled 상태 추가

# Scope

## In Scope

- mutation 훅 4개에 `onError` 콜백 추가 (alert 또는 에러 상태 반영)
- OrderDetail 취소 버튼에 `disabled={cancelMutation.isPending}` 추가
- orders/page.tsx, users/page.tsx에 Suspense + LoadingSpinner 추가
- 테스트 추가

## Out of Scope

- 토스트 알림 시스템 도입
- Optimistic update 적용
- web-store 컴포넌트 수정

# Acceptance Criteria

- [ ] mutation 실패 시 사용자에게 에러 메시지가 표시된다
- [ ] OrderDetail 취소 버튼이 mutation 진행 중 비활성화된다
- [ ] orders, users 페이지에 로딩 스피너가 표시된다
- [ ] 기존 테스트가 통과한다

# Related Specs

- `specs/services/admin-dashboard/architecture.md`
- `specs/platform/error-handling.md`

# Related Skills

- `.claude/skills/frontend/react-patterns.md`

# Related Contracts

없음

# Target App

- `apps/admin-dashboard`

# Implementation Notes

- `onError` 콜백에서 `window.alert()` 또는 에러 상태 변수를 사용하여 피드백 제공
- Suspense fallback은 기존 products/page.tsx 패턴과 동일하게 `<LoadingSpinner />`
- mutation isPending 상태를 버튼 disabled + 텍스트 변경에 활용

# Edge Cases

- 네트워크 오류 시 에러 메시지 표시
- mutation 중 페이지 이동 시
- 동시에 여러 mutation 실행 시

# Failure Scenarios

- API 500 에러 시 사용자 안내
- 타임아웃 시 에러 처리
- 권한 없음(403) 시 적절한 메시지

# Test Requirements

- mutation 에러 시 에러 메시지 표시 컴포넌트 테스트
- Suspense 로딩 상태 렌더링 테스트
- 버튼 disabled 상태 테스트

# Definition of Done

- [ ] UI implemented
- [ ] API integration completed
- [ ] Loading/error/empty states handled
- [ ] Tests added
- [ ] Tests passing
- [ ] Ready for review
