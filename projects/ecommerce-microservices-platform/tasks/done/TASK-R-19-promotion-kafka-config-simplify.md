# Task ID

TASK-R-19

# Title

promotion-service KafkaConsumerConfig 단순화 (수동 팩토리 제거)

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

promotion-service만 ConcurrentKafkaListenerContainerFactory를 수동으로 구성하고 dlqKafkaTemplate 빈을 별도로 생성하고 있다. 다른 서비스들은 Spring Boot 자동 구성 + DefaultErrorHandler 설정만 사용한다. 다른 서비스와 패턴을 통일하여 유지보수성을 높인다.

---

# Scope

## In Scope

- promotion-service KafkaConsumerConfig에서 수동 ConcurrentKafkaListenerContainerFactory 빈 제거
- 수동 dlqKafkaTemplate 빈 제거
- Spring Boot 자동 구성 기반으로 DefaultErrorHandler + DeadLetterPublishingRecoverer 설정
- ExponentialBackOff 설정 (base 1s, multiplier 2, max 30s, max retries 3)
- DLQ 토픽 네이밍 .dlq 패턴 적용
- 관련 테스트 수정

## Out of Scope

- Kafka 프로듀서 설정 변경
- 이벤트 페이로드 변경
- 다른 서비스의 Kafka 설정 변경
- @KafkaListener 어노테이션 변경

---

# Acceptance Criteria

- [ ] promotion-service에서 수동 ConcurrentKafkaListenerContainerFactory 빈이 제거되었다
- [ ] promotion-service에서 수동 dlqKafkaTemplate 빈이 제거되었다
- [ ] Spring Boot 자동 구성 기반으로 Kafka consumer가 동작한다
- [ ] DefaultErrorHandler + DeadLetterPublishingRecoverer가 올바르게 설정되었다
- [ ] ExponentialBackOff가 정책에 맞게 설정되었다 (base 1s, multiplier 2, max 30s)
- [ ] DLQ 토픽 이름이 {original-topic}.dlq 패턴을 따른다
- [ ] 기존 Kafka 관련 테스트가 모두 통과한다

---

# Related Specs

- `specs/platform/event-driven-policy.md`

# Related Skills

- `.claude/skills/backend/refactoring.md`

---

# Related Contracts

- 해당 없음 (인프라 설정 리팩토링, 이벤트 계약 변경 없음)

---

# Target Service

- `promotion-service`

---

# Dependencies

- TASK-R-04 (표준 템플릿이 존재할 경우 해당 패턴을 따름)

---

# Architecture

Follow:

- `specs/platform/event-driven-policy.md`
- `specs/services/promotion-service/architecture.md`

---

# Implementation Notes

- 다른 서비스(payment-service, product-service 등)의 KafkaConsumerConfig를 참고하여 동일한 패턴 적용
- Spring Boot의 ConcurrentKafkaListenerContainerFactory 자동 구성을 활용하고, ConcurrentKafkaListenerContainerFactoryConfigurer를 통해 에러 핸들러만 커스터마이즈
- @KafkaListener에 containerFactory가 명시되어 있다면 제거하거나 기본값 사용으로 변경

---

# Edge Cases

- @KafkaListener에 containerFactory 속성이 명시적으로 지정된 경우 -> 기본 팩토리를 사용하도록 속성 제거
- 수동 빈이 다른 빈에 주입되어 사용되는 경우 -> 의존 관계 확인 후 수정
- DLQ 전송에 수동 dlqKafkaTemplate이 직접 사용되는 경우 -> DeadLetterPublishingRecoverer 방식으로 전환

---

# Failure Scenarios

- 자동 구성 전환 후 consumer 설정이 달라져 메시지 역직렬화 실패 -> 자동 구성에서도 동일한 직렬화 설정 확인
- DLQ 전송 방식 변경으로 에러 메타데이터 누락 -> DeadLetterPublishingRecoverer의 헤더 전달 확인
- 기존 수동 팩토리에 설정된 커스텀 속성 누락 -> 수동 팩토리의 모든 설정을 확인하고 필요한 항목 반영

---

# Test Requirements

- promotion-service의 기존 Kafka 통합 테스트 통과 확인
- DLQ 전송 테스트 (메시지 처리 실패 시 .dlq 토픽으로 전송 확인)
- 재시도 정책이 ExponentialBackOff인지 확인

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
