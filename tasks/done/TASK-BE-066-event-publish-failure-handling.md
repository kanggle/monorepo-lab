# Task ID

TASK-BE-066

# Title

전 서비스 이벤트 소비 실패 처리 개선 — 예외 로깅 강화, 메트릭 추가

# Status

done

# Owner

backend

# Task Tags

- code, event, test

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Goal

TASK-INT-012 크로스 리뷰에서 발견된 Major 이슈 수정. 여러 서비스의 이벤트 컨슈머에서 예외를 getMessage()로만 로깅하고 스택트레이스를 누락하는 문제, 이벤트 발행 실패 시 한국어/영어 혼용 로그 문제를 수정한다.

---

# Scope

## In Scope

- payment-service OrderPlacedEventConsumer/OrderCancelledEventConsumer: 예외 로깅에 스택트레이스 포함
- payment-service: 이벤트 소비 실패 메트릭 카운터 추가
- auth-service SpringAuthEventPublisher: 로그 메시지 영어 통일
- product-service KafkaProductEventPublisher: 로그 메시지 영어 통일
- order-service PaymentRefundedEventConsumer.parseRefundedAt(): PaymentCompletedEventConsumer와 동일한 예외 처리 패턴 적용

## Out of Scope

- 이벤트 재시도 정책 변경
- DLQ 설정 변경

---

# Acceptance Criteria

- [ ] 모든 이벤트 컨슈머 catch 블록에 예외 객체가 로그 파라미터로 전달된다
- [ ] 이벤트 발행/소비 실패 로그가 영어로 통일된다
- [ ] payment-service 이벤트 소비 실패 메트릭이 추가된다
- [ ] PaymentRefundedEventConsumer와 PaymentCompletedEventConsumer의 에러 처리가 일관된다

---

# Related Specs

- `specs/platform/coding-rules.md`

# Related Contracts

_(없음)_

---

# Edge Cases

- 메시지 역직렬화 실패 vs 비즈니스 로직 실패 구분

---

# Failure Scenarios

_(없음)_

---

# Test Requirements

- 이벤트 소비 실패 시 메트릭 증가 테스트
- 예외 발생 시 스택트레이스 포함 로깅 확인
