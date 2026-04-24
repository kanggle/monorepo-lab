# Task ID

TASK-R-15

# Title

order-service, user-service KafkaConsumerConfig ExponentialBackOff + .dlq 통일

# Status

review

# Owner

backend

# Task Tags

- refactor
- event

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

order-service와 user-service의 KafkaConsumerConfig가 FixedBackOff와 .DLT 토픽 네이밍을 사용하고 있다. event-driven-policy.md 정책에 맞게 ExponentialBackOff(1초 base, 최대 30초, 3회 재시도)와 .dlq 토픽 네이밍으로 수정한다.

---

# Scope

## In Scope

- order-service KafkaConsumerConfig의 FixedBackOff를 ExponentialBackOff로 변경 (base 1s, multiplier 2, max 30s, max retries 3)
- order-service DLT 토픽 네이밍을 .DLT에서 .dlq로 변경
- user-service KafkaConsumerConfig의 FixedBackOff를 ExponentialBackOff로 변경
- user-service DLT 토픽 네이밍을 .DLT에서 .dlq로 변경
- 관련 테스트 수정

## Out of Scope

- 다른 서비스의 Kafka 설정 변경
- Kafka 프로듀서 설정 변경
- 이벤트 페이로드 변경
- CommonErrorHandler 외의 consumer 설정 변경

---

# Acceptance Criteria

- [ ] order-service KafkaConsumerConfig가 ExponentialBackOff를 사용한다 (base 1s, multiplier 2, max interval 30s)
- [ ] order-service 최대 재시도 횟수가 3회이다
- [ ] order-service DLQ 토픽 이름이 {original-topic}.dlq 패턴을 따른다
- [ ] user-service KafkaConsumerConfig가 ExponentialBackOff를 사용한다 (base 1s, multiplier 2, max interval 30s)
- [ ] user-service 최대 재시도 횟수가 3회이다
- [ ] user-service DLQ 토픽 이름이 {original-topic}.dlq 패턴을 따른다
- [ ] 기존 Kafka 관련 테스트가 모두 통과한다

---

# Related Specs

- `specs/platform/event-driven-policy.md`

# Related Skills

- `.claude/skills/backend/refactoring.md`

---

# Related Contracts

- 해당 없음 (이벤트 계약 변경 없음, 인프라 설정만 변경)

---

# Target Service

- `order-service`
- `user-service`

---

# Dependencies

- TASK-R-04 (표준 템플릿이 존재할 경우 해당 패턴을 따름)

---

# Architecture

Follow:

- `specs/platform/event-driven-policy.md`
- 각 서비스의 `specs/services/<service>/architecture.md`

---

# Implementation Notes

- event-driven-policy.md Retry Policy: Max retries 3, Exponential backoff, base interval 1s, max interval 30s
- event-driven-policy.md DLQ Policy: Topic naming {original-topic}.dlq
- 다른 서비스(payment-service, product-service 등)의 KafkaConsumerConfig를 참고하여 동일한 패턴 적용
- ExponentialBackOff 설정: initialInterval=1000, multiplier=2.0, maxInterval=30000, maxElapsedTime 계산 주의

---

# Edge Cases

- DLQ 토픽 이름 변경 시 기존 .DLT 토픽에 남아있는 메시지 -> 운영 환경에서는 마이그레이션 필요하나, 개발 단계에서는 토픽 재생성으로 해결
- ExponentialBackOff의 maxElapsedTime 설정이 없어 무한 재시도 -> maxRetries 또는 maxElapsedTime을 명시적으로 설정

---

# Failure Scenarios

- ExponentialBackOff 파라미터 오류로 재시도 동작 변경 -> 통합 테스트에서 재시도 횟수 검증
- DLQ 토픽 네이밍 변경 후 DLQ 전송 실패 -> 통합 테스트에서 DLQ 전송 확인
- 기존 FixedBackOff에 의존하는 테스트 실패 -> 테스트 수정

---

# Test Requirements

- order-service, user-service의 기존 Kafka 통합 테스트 통과 확인
- DLQ 토픽 네이밍이 .dlq인지 확인하는 테스트
- 재시도 정책이 ExponentialBackOff인지 확인하는 설정 테스트 (선택)

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
