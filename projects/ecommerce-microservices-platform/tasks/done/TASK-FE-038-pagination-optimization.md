# Task ID

TASK-FE-038

# Title

프론트엔드 페이지네이션 대량 페이지 렌더링 최적화 — 말줄임 표시 적용

# Status

done

# Owner

frontend

# Task Tags

- code
- test

# Goal

admin-dashboard의 DataTable과 web-store의 Pagination 컴포넌트가 전체 페이지 버튼을 모두 렌더링하여 100+ 페이지 시 성능 저하 및 UX 문제가 발생한다.

"1 2 3 ... 98 99 100" 형태의 말줄임(ellipsis) 패턴을 적용하여 최대 7-9개 페이지 버튼만 렌더링하도록 개선한다.

# Scope

## In Scope

- admin-dashboard DataTable 페이지네이션 말줄임 적용
- web-store Pagination 컴포넌트 말줄임 적용
- 현재/첫/끝 페이지 주변만 표시하는 로직 구현
- 테스트 추가

## Out of Scope

- 무한 스크롤 도입
- 커서 기반 페이지네이션 전환
- 백엔드 페이지네이션 로직 변경

# Acceptance Criteria

- [ ] 10페이지 이상일 때 말줄임(`...`)이 표시된다
- [ ] 첫 페이지, 마지막 페이지, 현재 페이지 ±2가 항상 표시된다
- [ ] 10페이지 미만일 때는 기존과 동일하게 전체 페이지가 표시된다
- [ ] 페이지 이동 기능이 정상 동작한다
- [ ] 기존 테스트가 통과한다

# Related Specs

- `specs/services/admin-dashboard/architecture.md`
- `specs/services/web-store/architecture.md`

# Related Skills

- `.claude/skills/frontend/react-patterns.md`

# Related Contracts

없음

# Target App

- `apps/admin-dashboard`
- `apps/web-store`

# Implementation Notes

- 페이지 번호 배열 생성 유틸 함수를 공유 패키지(`@repo/ui` 또는 로컬 util)에 구현
- 예시: `buildPageNumbers(current: 5, total: 100)` → `[1, '...', 3, 4, 5, 6, 7, '...', 100]`
- 말줄임은 클릭 불가능한 `<span>` 요소로 렌더링

# Edge Cases

- 총 1페이지인 경우
- 현재 페이지가 첫 번째 또는 마지막 근처인 경우
- 총 페이지가 정확히 10인 경우 (경계값)

# Failure Scenarios

- 페이지 번호 클릭 후 데이터 로드 실패 시 기존 에러 처리 유지
- totalPages가 0인 경우 페이지네이션 미표시

# Test Requirements

- buildPageNumbers 유틸 함수 단위 테스트 (다양한 케이스)
- 말줄임 렌더링 컴포넌트 테스트
- 경계값 테스트

# Definition of Done

- [ ] UI implemented
- [ ] API integration completed
- [ ] Loading/error/empty states handled
- [ ] Tests added
- [ ] Tests passing
- [ ] Ready for review
