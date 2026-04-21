# Task ID

TASK-BE-070

# Title

전 서비스 한국어 로그 메시지 영어 통일 — 운영 일관성 확보

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

TASK-INT-012 크로스 리뷰에서 발견된 이슈 수정. 전체 백엔드 서비스의 로그 메시지를 영어로 통일하여 로그 집계/분석 도구와의 호환성 및 운영 일관성을 확보한다.

---

# Scope

## In Scope

- auth-service: `SpringAuthEventPublisher` 한국어 로그 ("이벤트 발행 실패") 영어 전환
- user-service: `KafkaUserProfileEventPublisher` 한국어 로그 ("이벤트 발행 실패", "이벤트 직렬화 실패") 영어 전환
- product-service: `KafkaProductEventPublisher` 한국어 로그 ("이벤트 발행 실패") 영어 전환
- order-service: `OrderPlacementService`, `OrderCancellationService`, `PaymentRefundConfirmationService` 한국어 로그 영어 전환
- user-service: `GlobalExceptionHandler` 한국어 에러 메시지 ("서버 오류가 발생했습니다") 영어 전환

## Out of Scope

- 프론트엔드 UI 메시지 (사용자 대면 메시지는 한국어 유지)
- 테스트 코드의 @DisplayName 한국어 (testing-strategy.md 에서 허용)
- 로그 구조 변경 (메시지 언어만 변경)

---

# Acceptance Criteria

- [ ] 전체 백엔드 서비스의 로그 메시지가 영어로 통일된다
- [ ] GlobalExceptionHandler의 에러 응답 메시지가 영어로 변경된다
- [ ] 기존 테스트가 모두 통과한다
- [ ] 로그 레벨은 변경하지 않는다

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

- auth-service, user-service, product-service, order-service

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
