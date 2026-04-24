# Task ID

TASK-FE-024

# Title

TASK-FE-021 리뷰 수정 — 토스트 알림 미구현 및 메시지 auto-dismiss 누락

# Status

ready

# Owner

frontend

# Task Tags

- code
- test

# Goal

TASK-FE-021에서 발견된 이슈를 수정한다. 프로필 수정 성공/실패 시 인라인 `<p>` 태그 대신 토스트 알림을 사용하고, 메시지가 일정 시간 후 자동으로 사라지도록 한다.

# Scope

## In Scope

- ProfileForm 컴포넌트의 성공/에러 메시지를 토스트 알림으로 변경
- 성공/에러 메시지 auto-dismiss 구현 (3~5초 후 자동 제거)
- 기존 프로젝트 패턴에 맞는 Toast 컴포넌트 구현 (없는 경우)
- 관련 테스트 업데이트

## Out of Scope

- ProfileForm 외 다른 컴포넌트의 알림 변경
- 프로필 페이지의 기능적 변경

# Acceptance Criteria

- [ ] 프로필 수정 성공 시 토스트 알림이 표시된다
- [ ] 프로필 수정 실패 시 토스트 알림으로 에러가 표시된다
- [ ] 토스트 알림이 3~5초 후 자동으로 사라진다
- [ ] auto-dismiss 동작에 대한 테스트가 존재한다
- [ ] 기존 프로필 기능에 영향 없음

# Related Specs

- `specs/services/web-store/architecture.md`

# Related Skills

- `.claude/skills/frontend/architecture/feature-sliced-design.md`

# Related Contracts

- `specs/contracts/http/user-api.md`

# Target App

- `apps/web-store`

# Edge Cases

- 연속으로 여러 번 수정 시 토스트 메시지 중복 표시
- 토스트 표시 중 페이지 이동 시 메모리 누수 방지

# Failure Scenarios

- setTimeout cleanup 누락으로 인한 메모리 누수
- 토스트가 사라지기 전 새 토스트 발생 시 이전 토스트 처리

# Test Requirements

- 토스트 알림 렌더링 테스트
- auto-dismiss 타이머 테스트 (jest.useFakeTimers)
- 기존 ProfileForm 테스트 통과 확인

# Definition of Done

- [ ] Toast notification implemented
- [ ] Auto-dismiss working
- [ ] Tests added/updated
- [ ] Tests passing
- [ ] Ready for review
