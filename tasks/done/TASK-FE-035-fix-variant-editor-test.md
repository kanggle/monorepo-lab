# Task ID

TASK-FE-035

# Title

TASK-FE-032 리뷰 수정 — VariantEditor 단위 테스트 추가

# Status

done

# Owner

frontend

# Task Tags

- test

---

# Goal

TASK-FE-032 리뷰에서 발견된 VariantEditor 하위 컴포넌트의 단위 테스트 누락을 수정한다. DeleteConfirmation, useAddressFormValidation은 테스트가 있으나 VariantEditor만 누락되어 있다.

---

# Scope

## In Scope

- VariantEditor 컴포넌트 단위 테스트 작성

## Out of Scope

- VariantEditor 기능 변경
- 다른 컴포넌트 수정

---

# Acceptance Criteria

- [ ] VariantEditor 단위 테스트 파일이 생성되었다
- [ ] 옵션 추가 기능 테스트가 포함되어 있다
- [ ] 옵션 제거 기능 테스트가 포함되어 있다
- [ ] 옵션 업데이트(변경) 기능 테스트가 포함되어 있다
- [ ] 초기값 렌더링 테스트가 포함되어 있다
- [ ] 모든 테스트가 통과한다

---

# Related Specs

- `specs/services/admin-dashboard/architecture.md`
- `specs/platform/coding-rules.md`

# Related Skills

- `.claude/skills/frontend/implementation-workflow.md`

---

# Related Contracts

- 해당 없음

---

# Target App

- `apps/admin-dashboard`

---

# Implementation Notes

- 기존 ProductForm.test.tsx의 테스트 패턴을 참고하여 작성한다.
- VariantEditor의 Props: `{ variants, onChange, initialKeyCount }`
- onChange 콜백 호출 검증이 핵심이다.

---

# Edge Cases

- 빈 variants 배열로 렌더링
- 옵션 모두 제거 후 상태

---

# Failure Scenarios

- onChange 콜백이 올바른 인자로 호출되지 않는 경우

---

# Test Requirements

- VariantEditor 단위 테스트 최소 4-5개 케이스

---

# Definition of Done

- [ ] Tests added
- [ ] Tests passing
- [ ] Ready for review
