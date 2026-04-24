# Task ID

TASK-FE-034

# Title

TASK-FE-031 리뷰 수정 — re-export 파일 제거, @repo/ui 빌드 오류 및 테스트 import 수정

# Status

done

# Owner

frontend

# Task Tags

- code
- test

---

# Goal

TASK-FE-031 리뷰에서 발견된 3가지 이슈를 수정한다:
1. web-store/admin-dashboard의 `shared/ui/`에 남아있는 re-export 파일 제거 및 import 경로를 `@repo/ui`로 직접 변경
2. `@repo/ui` tsconfig.json에서 테스트 파일 제외하여 빌드 오류 해결
3. admin-dashboard `ErrorMessage.test.tsx`에 vitest import 추가

---

# Scope

## In Scope

- web-store `shared/ui/`의 LoadingSpinner.tsx, EmptyState.tsx, ErrorMessage.tsx re-export 파일 삭제
- admin-dashboard `shared/ui/`의 LoadingSpinner.tsx, EmptyState.tsx, ErrorMessage.tsx re-export 파일 삭제
- 양쪽 앱에서 해당 컴포넌트를 사용하는 모든 import 경로를 `@repo/ui`로 변경
- `packages/ui/tsconfig.json`에서 테스트 디렉토리 제외 설정 추가
- `apps/admin-dashboard/src/__tests__/shared/ui/ErrorMessage.test.tsx`에 vitest import 추가

## Out of Scope

- 새로운 컴포넌트 추가
- 스타일 변경
- 기능 변경

---

# Acceptance Criteria

- [ ] web-store `shared/ui/`에서 LoadingSpinner.tsx, EmptyState.tsx, ErrorMessage.tsx가 삭제되었다
- [ ] admin-dashboard `shared/ui/`에서 LoadingSpinner.tsx, EmptyState.tsx, ErrorMessage.tsx가 삭제되었다
- [ ] 양쪽 앱의 모든 import가 `@repo/ui`를 직접 참조한다
- [ ] `@repo/ui` 패키지 빌드가 성공한다
- [ ] admin-dashboard ErrorMessage.test.tsx에 vitest import가 포함되어 있다
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

- 해당 없음 (내부 수정)

---

# Target App

- `packages/ui`
- `apps/web-store`
- `apps/admin-dashboard`

---

# Implementation Notes

- `@/shared/ui` 경로로 import하는 모든 파일을 찾아 `@repo/ui`로 변경한다.
- DataTable.tsx 등 상대 경로로 import하는 곳도 확인한다.
- tsconfig.json에 `"exclude": ["src/__tests__"]` 추가로 빌드 시 테스트 파일 제외.

---

# Edge Cases

- shared/ui/index.ts에서 re-export하는 배럴 파일이 있으면 함께 정리
- 다른 컴포넌트가 shared/ui/ 경로에 남아있는 경우 디렉토리 유지

---

# Failure Scenarios

- import 경로 변경 누락으로 빌드 실패
- shared/ui/ 디렉토리에 다른 컴포넌트가 있어 디렉토리 삭제 불가

---

# Test Requirements

- 모든 기존 테스트 통과 확인
- @repo/ui 빌드 성공 확인

---

# Definition of Done

- [ ] UI implemented
- [ ] API integration completed
- [ ] Loading/error/empty states handled
- [ ] Tests added
- [ ] Tests passing
- [ ] Ready for review
