# Task ID

TASK-BE-070-fix

# Title

TASK-BE-070 리뷰 수정 — order-service, product-service 잔여 한국어 에러 메시지 영어 전환

# Status

ready

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

TASK-BE-070 리뷰에서 발견된 이슈 수정. order-service와 product-service에 남아있는 한국어 에러 메시지를 영어로 전환하여 AC #2(GlobalExceptionHandler 에러 응답 메시지 영어 변경)를 충족한다.

---

# Scope

## In Scope

- order-service `OrderPlacementService.java:33` — `"userId는 필수입니다"` → 영어 전환
- order-service `OrderPlacementService.java:36` — `"주문 항목이 비어있습니다"` → 영어 전환
- order-service `UnauthorizedOrderAccessException.java:6` — `"해당 주문에 접근할 권한이 없습니다"` → 영어 전환
- order-service `GlobalExceptionHandler.java:24` — `"입력값이 유효하지 않습니다"` → 영어 전환
- order-service `GlobalExceptionHandler.java:57` — `"동시 수정 충돌이 발생했습니다. 다시 시도해 주세요."` → 영어 전환
- product-service `GlobalExceptionHandler.java:64` — `"동시 수정 충돌이 발생했습니다. 다시 시도해 주세요."` → 영어 전환

## Out of Scope

- 프론트엔드 UI 메시지
- 테스트 코드의 @DisplayName 한국어
- 로그 구조 변경

---

# Acceptance Criteria

- [ ] order-service의 모든 에러 메시지가 영어로 변경된다
- [ ] product-service GlobalExceptionHandler의 에러 메시지가 영어로 변경된다
- [ ] 기존 테스트가 모두 통과한다
- [ ] 에러 메시지를 검증하는 테스트가 있다면 영어 메시지로 업데이트한다

---

# Related Specs

- `specs/platform/coding-rules.md` (Logging 섹션)

# Related Skills

_(없음)_

---

# Related Contracts

_(없음)_

---

# Target Service

- order-service, product-service

---

# Architecture

각 서비스의 기존 아키텍처를 따른다.

---

# Edge Cases

- 에러 메시지 변경 시 프론트엔드에서 메시지 문자열로 분기하는 코드가 있는지 확인 (code 기반 분기만 사용해야 함)

---

# Failure Scenarios

_(없음)_

---

# Test Requirements

- 기존 테스트 통과 확인
- 에러 응답 메시지를 검증하는 테스트가 있다면 영어 메시지로 업데이트

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
