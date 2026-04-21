# Task ID

TASK-FE-018

# Title

FilterBar searchValue prop 동기화 버그 수정

# Status

ready

# Owner

frontend

# Task Tags

- code
- test

# Goal

TASK-FE-017 리뷰에서 발견된 FilterBar 컴포넌트의 `searchValue` prop 동기화 누락 버그를 수정한다. `useState` 초기값만 반영되고 이후 prop 변경이 `localSearch` 상태에 반영되지 않는 문제를 해결한다.

# Scope

## In Scope

- `apps/admin-dashboard/src/shared/ui/FilterBar.tsx`: `useEffect`를 추가하여 `searchValue` prop 변경 시 `localSearch` 상태 동기화
- 기존 FilterBar 테스트 업데이트 및 prop 동기화 테스트 추가

## Out of Scope

- FilterBar의 디자인 변경
- 디바운싱 추가
- FilterBar 외 다른 컴포넌트 수정

# Acceptance Criteria

- [ ] `searchValue` prop이 변경될 때 `localSearch` 상태가 동기화됨
- [ ] URL 직접 진입 시 검색 입력 필드에 올바른 값이 표시됨
- [ ] 뒤로가기/앞으로가기 시 검색 입력 필드가 URL과 동기화됨
- [ ] 기존 테스트 통과
- [ ] prop 동기화 관련 테스트 추가
- [ ] 빌드 성공

# Related Specs

- `specs/platform/coding-rules.md`
- `specs/platform/testing-strategy.md`

# Related Contracts

- 해당 없음

# Target App

- `apps/admin-dashboard`

# Implementation Notes

`FilterBar.tsx` 27번째 줄의 `useState(searchValue)`는 초기 렌더링 시에만 값을 설정한다. `searchValue` prop이 외부(URL 파라미터 등)에서 변경될 때 `localSearch` 상태가 업데이트되지 않으므로, `useEffect`를 추가하여 동기화해야 한다.

```tsx
// 수정 방향
useEffect(() => {
  setLocalSearch(searchValue);
}, [searchValue]);
```

# Edge Cases

- searchValue가 undefined에서 문자열로 변경되는 경우
- searchValue가 문자열에서 빈 문자열로 변경되는 경우
- 사용자가 입력 중일 때 외부에서 searchValue가 변경되는 경우

# Failure Scenarios

- useEffect 의존성 배열 누락으로 무한 렌더링 발생
- localSearch와 searchValue 간 상태 불일치

# Test Requirements

- FilterBar 컴포넌트 테스트: searchValue prop 변경 시 입력 필드 값 동기화 확인
- rerender를 활용한 prop 업데이트 시나리오 테스트

# Definition of Done

- [ ] useEffect 동기화 구현
- [ ] Tests added
- [ ] Tests passing
- [ ] Ready for review
