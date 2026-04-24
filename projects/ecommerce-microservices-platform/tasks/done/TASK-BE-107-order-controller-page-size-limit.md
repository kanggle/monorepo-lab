# Task ID

TASK-BE-107

# Title

order-service OrderController 페이지 사이즈 상한 제한 추가 — 과도한 size 파라미터에 의한 DB 과부하 방지

# Status

review

# Owner

backend

# Task Tags

- code
- api
- test

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

If any section is missing or incomplete, this task must not be implemented.

---

# Goal

`OrderController.listOrders()`의 `size` 파라미터에 상한 제한이 없어, 악의적인 요청자가 매우 큰 값(예: `size=100000`)을 설정하여 DB에 과부하를 줄 수 있는 문제를 수정한다.

`size` 파라미터에 최대값을 설정하고, 초과 시 최대값으로 클램핑하거나 에러를 반환한다.

---

# Scope

## In Scope

- `OrderController.listOrders()`의 `size` 파라미터에 최대값(예: 100) 제한 추가
- `page` 파라미터의 최소값(0) 검증 추가
- 테스트 추가

## Out of Scope

- 다른 서비스의 페이지네이션 제한
- API 스펙에 페이지 사이즈 제한 추가 (필요 시 별도 스펙 업데이트)

---

# Acceptance Criteria

- [ ] `size` 파라미터가 최대값(100)을 초과할 경우 최대값으로 클램핑된다
- [ ] `size` 파라미터가 1 미만일 경우 기본값(20)이 사용된다
- [ ] `page` 파라미터가 0 미만일 경우 0이 사용된다
- [ ] 정상 범위의 `size` 파라미터는 그대로 사용된다
- [ ] 테스트가 경계값을 커버한다

---

# Related Specs

- `specs/services/order-service/architecture.md`

# Related Skills

_(해당 없음)_

---

# Related Contracts

- `specs/contracts/http/order-api.md`

---

# Target Service

- `order-service`

---

# Architecture

Follow:

- `specs/services/order-service/architecture.md`

---

# Implementation Notes

- Presentation layer에서 `size` 값을 클램핑하여 Application layer에 전달한다.
- `Math.min(size, MAX_PAGE_SIZE)`, `Math.max(page, 0)` 패턴을 사용한다.
- 상수는 `OrderController` 내부에 `private static final`로 선언한다.

---

# Edge Cases

- `size=0` — 기본값(20)으로 대체
- `size=-1` — 기본값(20)으로 대체
- `size=100` — 허용 (최대값과 동일)
- `size=101` — 100으로 클램핑
- `page=-1` — 0으로 대체

---

# Failure Scenarios

- 클램핑 로직이 Application layer가 아닌 Presentation layer에서 처리되어야 함 — 다른 진입점(이벤트 등)에서는 페이지네이션을 사용하지 않으므로 문제 없음

---

# Test Requirements

- 단위 테스트: `size` 최대값 초과 시 클램핑 확인
- 단위 테스트: `size` 최소값 미만 시 기본값 사용 확인
- 단위 테스트: `page` 음수 시 0 대체 확인
- 단위 테스트: 정상 범위 값 그대로 전달 확인

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Ready for review
