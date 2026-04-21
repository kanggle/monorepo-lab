# Task ID

TASK-FE-032

# Title

대형 프론트엔드 컴포넌트 분해 — ProductForm(352줄), AddressForm(339줄), AddressList(263줄)

# Status

done

# Owner

frontend

# Task Tags

- code
- test

---

# Goal

admin-dashboard의 `ProductForm`(352줄), web-store의 `AddressForm`(339줄), `AddressList`(263줄)가 과도하게 큰 단일 컴포넌트로 유지보수성이 낮다. 각 컴포넌트에서 독립적인 관심사를 하위 컴포넌트 또는 커스텀 훅으로 추출하여 분해한다.

---

# Scope

## In Scope

- admin-dashboard `ProductForm`: Variant 관리 로직을 `VariantEditor` 하위 컴포넌트로 분리
- web-store `AddressForm`: 폼 검증 로직을 `useAddressFormValidation` 커스텀 훅으로 추출
- web-store `AddressList`: 삭제 확인 모달 로직을 `DeleteConfirmation` 하위 컴포넌트로 분리
- 각 컴포넌트의 분해 후 200줄 이하 달성

## Out of Scope

- 컴포넌트 기능 변경
- 스타일 변경
- 다른 컴포넌트의 분해
- ProfileForm(246줄) — 경계선이므로 이번 범위에서 제외

---

# Acceptance Criteria

- [ ] `ProductForm`이 200줄 이하이다
- [ ] `VariantEditor` 하위 컴포넌트가 생성되었다
- [ ] `AddressForm`이 200줄 이하이다
- [ ] `useAddressFormValidation` 커스텀 훅이 생성되었다
- [ ] `AddressList`가 200줄 이하이다
- [ ] `DeleteConfirmation` 하위 컴포넌트가 생성되었다
- [ ] 기존 UI 동작이 변경되지 않았다
- [ ] 모든 기존 테스트가 통과한다

---

# Related Specs

- `specs/services/admin-dashboard/architecture.md`
- `specs/services/web-store/architecture.md`
- `specs/platform/coding-rules.md`

# Related Skills

- `.claude/skills/frontend/implementation-workflow.md`
- `.claude/skills/frontend/form-handling.md`

---

# Related Contracts

- 해당 없음 (내부 리팩토링)

---

# Target App

- `apps/admin-dashboard`
- `apps/web-store`

---

# Implementation Notes

- ProductForm의 Variant 관련 상태(variants useState, addVariant, removeVariant, updateVariant)를 VariantEditor에 위임한다.
- AddressForm의 validateFields 함수와 관련 상태를 커스텀 훅으로 추출한다.
- AddressList의 confirmDeleteId, deletingId 상태와 삭제 확인 UI를 별도 컴포넌트로 분리한다.
- 분해 시 props drilling이 과도해지면 컴포넌트 합성(composition) 패턴을 사용한다.

---

# Edge Cases

- 하위 컴포넌트로 분리 시 리렌더링 최적화 필요 (React.memo 검토)
- 커스텀 훅에서 상태 변경 시 부모 컴포넌트 전체 리렌더링 → 적절한 상태 분리
- 폼 제출 시 하위 컴포넌트의 상태를 부모가 수집해야 하는 경우 → 콜백 또는 ref 활용

---

# Failure Scenarios

- 컴포넌트 분해 후 이벤트 핸들러 바인딩 누락
- 상태 끌어올리기(state lifting) 미스로 데이터 불일치
- 테스트에서 하위 컴포넌트 mock 필요성 증가로 테스트 복잡도 상승

---

# Test Requirements

- 분해된 하위 컴포넌트별 단위 테스트
- 커스텀 훅 단위 테스트
- 기존 통합/페이지 테스트 통과 확인

---

# Definition of Done

- [ ] UI implemented
- [ ] API integration completed
- [ ] Loading/error/empty states handled
- [ ] Tests added
- [ ] Tests passing
- [ ] Ready for review
