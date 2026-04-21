# Task ID

TASK-BE-115

# Title

order-service 예외 클래스 패키지 위치 정리

# Status

done

# Owner

backend

# Task Tags

- code

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

`UnauthorizedOrderAccessException`이 `application.service` 패키지에, `InvalidOrderStatusException`이 `presentation` 패키지 루트에 위치하여 네이밍 컨벤션과 패키지 구조 일관성이 떨어진다. 각 예외 클래스를 적절한 패키지로 이동한다.

---

# Scope

## In Scope

- `UnauthorizedOrderAccessException`을 `application.exception` 패키지로 이동
- `InvalidOrderStatusException`을 `presentation.exception` 패키지로 이동
- import 경로 업데이트

## Out of Scope

- 예외 클래스의 동작 변경
- 새로운 예외 클래스 추가
- 도메인 레이어 예외 패키지 변경 (이미 `domain.exception`으로 정리됨)

---

# Acceptance Criteria

- [ ] `UnauthorizedOrderAccessException`이 `com.example.order.application.exception` 패키지에 위치한다
- [ ] `InvalidOrderStatusException`이 `com.example.order.presentation.exception` 패키지에 위치한다
- [ ] 모든 import 경로가 업데이트된다
- [ ] 기존 테스트가 모두 통과한다

---

# Related Specs

- `specs/platform/naming-conventions.md`
- `specs/services/order-service/architecture.md`

# Related Skills

- `.claude/skills/backend/`

---

# Related Contracts

- N/A

---

# Target Service

- `order-service`

---

# Architecture

Follow:

- `specs/services/order-service/architecture.md`

---

# Implementation Notes

- 패키지 이동만 수행, 클래스 내용 변경 없음
- `GlobalExceptionHandler`의 import 경로 업데이트 필요
- `OrderController`의 import 경로 업데이트 필요
- 테스트 파일의 import 경로도 업데이트

---

# Edge Cases

- 없음 (순수 리팩토링)

---

# Failure Scenarios

- import 경로 누락 → 컴파일 에러 (빌드 시 즉시 발견)

---

# Test Requirements

- 기존 테스트 통과 확인 (별도 신규 테스트 불필요)

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
