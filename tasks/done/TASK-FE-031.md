# Task ID

TASK-FE-031

# Title

web-store, admin-dashboard 중복 공유 UI 컴포넌트 @repo/ui 통합 — LoadingSpinner, EmptyState, ErrorMessage

# Status

done

# Owner

frontend

# Task Tags

- code
- test

---

# Goal

web-store와 admin-dashboard에 동일한 구현의 `LoadingSpinner`, `EmptyState`, `ErrorMessage` 컴포넌트가 각각 존재한다. `@repo/ui` 패키지에 통합하여 중복을 제거하고 단일 소스로 관리한다.

---

# Scope

## In Scope

- `@repo/ui` 패키지에 `LoadingSpinner`, `EmptyState`, `ErrorMessage` 컴포넌트 추가
- web-store의 `shared/ui/` 에서 해당 컴포넌트 제거, `@repo/ui` import로 전환
- admin-dashboard의 `shared/ui/`에서 해당 컴포넌트 제거, `@repo/ui` import로 전환
- admin-dashboard의 `ErrorMessage`에만 있는 `color: 'red'` 등 미세한 차이는 props로 커스터마이징 가능하도록 처리

## Out of Scope

- 새로운 공유 컴포넌트 추가
- 스타일 시스템 변경
- 다른 공유 컴포넌트 통합

---

# Acceptance Criteria

- [ ] `@repo/ui`에서 `LoadingSpinner`, `EmptyState`, `ErrorMessage`가 export된다
- [ ] web-store의 `shared/ui/`에서 해당 3개 컴포넌트 파일이 제거되었다
- [ ] admin-dashboard의 `shared/ui/`에서 해당 3개 컴포넌트 파일이 제거되었다
- [ ] 양쪽 앱의 모든 import가 `@repo/ui`를 참조한다
- [ ] 기존 UI 동작/스타일이 변경되지 않았다
- [ ] 모든 기존 테스트가 통과한다

---

# Related Specs

- `specs/services/web-store/architecture.md`
- `specs/services/admin-dashboard/architecture.md`
- `specs/platform/coding-rules.md`

# Related Skills

- `.claude/skills/frontend/implementation-workflow.md`

---

# Related Contracts

- 해당 없음 (내부 리팩토링)

---

# Target App

- `packages/ui`
- `apps/web-store`
- `apps/admin-dashboard`

---

# Implementation Notes

- `@repo/ui` 패키지의 기존 구조와 export 방식을 따른다.
- ErrorMessage의 미세한 스타일 차이(color prop 등)는 optional props로 처리한다.
- 기존 컴포넌트의 props 인터페이스를 합집합으로 통합한다.

---

# Edge Cases

- admin-dashboard의 ErrorMessage가 추가 props를 가진 경우 → 통합 컴포넌트에 optional props 추가
- 앱별로 다른 스타일 테마를 적용하는 경우 → className 또는 style props로 오버라이드 지원
- @repo/ui 빌드 순서 문제 → turbo.json의 의존성 그래프 확인

---

# Failure Scenarios

- @repo/ui 빌드 실패로 양쪽 앱 모두 빌드 불가
- import 경로 변경 누락으로 런타임 에러
- SSR 환경에서 공유 컴포넌트 렌더링 이슈

---

# Test Requirements

- @repo/ui 컴포넌트 단위 테스트
- web-store, admin-dashboard 기존 테스트 통과 확인
- 빌드 성공 확인

---

# Definition of Done

- [ ] UI implemented
- [ ] API integration completed
- [ ] Loading/error/empty states handled
- [ ] Tests added
- [ ] Tests passing
- [ ] Ready for review
