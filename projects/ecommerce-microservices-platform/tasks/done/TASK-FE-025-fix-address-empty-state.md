# Task ID

TASK-FE-025

# Title

TASK-FE-022 리뷰 수정 — 빈 상태 UI 중복 렌더링 수정

# Status

ready

# Owner

frontend

# Task Tags

- code
- test

# Goal

TASK-FE-022에서 발견된 이슈를 수정한다. 배송지가 0개일 때 EmptyState 컴포넌트와 "첫 배송지 추가하기" 버튼이 별도 조건 블록으로 중복 렌더링되는 문제를 해결한다.

# Scope

## In Scope

- AddressesPage의 빈 상태 렌더링 로직 통합 (EmptyState + 추가 버튼을 하나의 블록으로)
- 관련 테스트 확인 및 업데이트

## Out of Scope

- 배송지 관리 기능의 동작 변경
- EmptyState 컴포넌트 자체의 변경

# Acceptance Criteria

- [ ] 배송지가 0개일 때 빈 상태 안내와 추가 버튼이 하나의 통합된 블록으로 렌더링된다
- [ ] 중복 조건 블록이 제거된다
- [ ] 기존 빈 상태 테스트가 통과한다
- [ ] 기존 배송지 관리 기능에 영향 없음

# Related Specs

- `specs/services/web-store/architecture.md`

# Related Skills

- `.claude/skills/frontend/architecture/feature-sliced-design.md`

# Related Contracts

- `specs/contracts/http/user-api.md`

# Target App

- `apps/web-store`

# Edge Cases

- 빈 상태에서 추가 버튼 클릭 시 정상 동작 확인

# Failure Scenarios

- 통합 후 빈 상태 UI가 표시되지 않는 경우

# Test Requirements

- 빈 상태 렌더링 테스트 통과 확인
- 추가 버튼 클릭 동작 테스트 확인

# Definition of Done

- [ ] Empty state rendering consolidated
- [ ] Tests passing
- [ ] Ready for review
