# Task ID

TASK-FE-036

# Title

TASK-FE-033 리뷰 수정 — 잔여 인라인 스타일 9곳 추출

# Status

done

# Owner

frontend

# Task Tags

- code
- test

---

# Goal

TASK-FE-033 리뷰에서 발견된 4개 컴포넌트의 잔여 인라인 스타일 9곳을 컴포넌트 외부 상수로 추출한다. 동적/조건부 스타일은 정적 기본 스타일을 상수로 분리하고 동적 부분만 최소한으로 인라인 유지하거나, 조건별 스타일 상수를 사전 정의한다.

---

# Scope

## In Scope

- AddressList.tsx: 5곳 인라인 스타일 추출
- ProductForm.tsx: 2곳 인라인 스타일 추출
- CheckoutForm.tsx: 1곳 인라인 스타일 추출
- AddressForm.tsx: 1곳 인라인 스타일 추출

## Out of Scope

- 스타일 시스템 변경
- 디자인 변경
- 다른 컴포넌트 수정

---

# Acceptance Criteria

- [ ] AddressList.tsx 렌더 함수 내에 `style={{...}}` 객체 리터럴이 없다
- [ ] ProductForm.tsx 렌더 함수 내에 `style={{...}}` 객체 리터럴이 없다
- [ ] CheckoutForm.tsx 렌더 함수 내에 `style={{...}}` 객체 리터럴이 없다
- [ ] AddressForm.tsx 렌더 함수 내에 `style={{...}}` 객체 리터럴이 없다
- [ ] 기존 UI 외관이 변경되지 않았다
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

- `apps/admin-dashboard`
- `apps/web-store`

---

# Implementation Notes

- 동적 스타일 처리 방식: 조건별 스타일 상수를 사전 정의하여 삼항 연산자로 선택
  - 예: `style={isValid ? styles.submitButton : styles.submitButtonDisabled}`
- spread 연산자로 확장하는 패턴(`{...styles.x, color: 'red'}`)도 외부 상수로 통합
- `useMemo`는 사용하지 않고 컴포넌트 외부 상수로 추출

---

# Edge Cases

- 동적 값이 여러 조건으로 분기하는 경우 → 조건별 상수를 각각 정의
- 스타일 상수명 충돌 → 기존 styles 객체에 키 추가

---

# Failure Scenarios

- 조건부 스타일 분리 미스로 시각적 회귀
- 스타일 상수 참조 오류

---

# Test Requirements

- 기존 컴포넌트 테스트 통과 확인
- 빌드 성공 확인

---

# Definition of Done

- [ ] UI implemented
- [ ] Tests passing
- [ ] Ready for review
