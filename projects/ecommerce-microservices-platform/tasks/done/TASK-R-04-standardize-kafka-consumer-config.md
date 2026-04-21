# Task ID

TASK-R-04

# Title

KafkaConsumerConfig 표준 템플릿 정의 및 전 서비스 통일

# Status

review

# Owner

backend

# Task Tags

- refactor
- event
- infrastructure

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

event-driven-policy.md 정책에 맞는 KafkaConsumerConfig 표준 패턴을 정의하고, 모든 이벤트 소비 서비스(order-service, user-service, notification-service, shipping-service, promotion-service)에 적용한다. DLQ suffix를 `.dlq`로 통일하고, 재시도 전략을 ExponentialBackOff(1s base, 2x multiplier, max 30s, max 3회)로 표준화한다.

---

# Scope

## In Scope

- event-driven-policy.md 기반 KafkaConsumerConfig 표준 패턴 정의
- 모든 이벤트 소비 서비스의 DLQ topic suffix를 `.dlq`로 통일 (기존 `-dlq`, `-DLQ` 등 비표준 네이밍 수정)
- 모든 이벤트 소비 서비스의 재시도 전략을 ExponentialBackOff로 통일:
  - Base interval: 1초
  - Multiplier: 2배
  - Max interval: 30초
  - Max retries: 3회
- DefaultErrorHandler + DeadLetterPublishingRecoverer 설정 통일
- 에러 분류(Transient → retry, Deserialization/Business rule → DLQ 즉시) 적용

## Out of Scope

- Kafka producer 설정 변경
- 새로운 이벤트 컨슈머 추가
- 이벤트 핸들러 비즈니스 로직 변경
- Kafka 브로커 설정 변경
- libs/에 KafkaConsumerConfig 추출 (각 서비스 인프라 레이어에 유지, 패턴만 통일)

---

# Acceptance Criteria

- [ ] order-service KafkaConsumerConfig가 표준 패턴을 따른다
- [ ] user-service KafkaConsumerConfig가 표준 패턴을 따른다
- [ ] notification-service KafkaConsumerConfig가 표준 패턴을 따른다
- [ ] shipping-service KafkaConsumerConfig가 표준 패턴을 따른다
- [ ] promotion-service KafkaConsumerConfig가 표준 패턴을 따른다
- [ ] 모든 서비스의 DLQ topic suffix가 `.dlq`이다 (`{original-topic}.dlq` 형식)
- [ ] 모든 서비스의 재시도 전략이 ExponentialBackOff(1s base, 2x multiplier, max 30s, max 3회)이다
- [ ] Deserialization 오류는 재시도 없이 DLQ로 즉시 전송된다
- [ ] Business rule 위반 예외는 재시도 없이 DLQ로 즉시 전송된다
- [ ] Transient 오류(네트워크, DB 연결 등)는 재시도 후 실패 시 DLQ로 전송된다
- [ ] DLQ 메시지에 원본 헤더(event_id, event_type, occurred_at, source)가 유지된다
- [ ] DLQ 메시지에 에러 메타데이터(error_message, retry_count, failed_at)가 포함된다
- [ ] 모든 기존 테스트가 통과한다

---

# Related Specs

- `specs/platform/event-driven-policy.md`

# Related Skills

- `.claude/skills/backend/refactoring.md`

---

# Related Contracts

- 해당 없음 (내부 리팩토링, 이벤트 계약 변경 없음. DLQ topic 네이밍만 정책 기준으로 통일)

---

# Target Service

- `order-service`
- `user-service`
- `notification-service`
- `shipping-service`
- `promotion-service`

---

# Architecture

Follow:

- `specs/platform/event-driven-policy.md`
- 각 서비스의 `specs/services/<service>/architecture.md`

---

# Implementation Notes

- KafkaConsumerConfig는 각 서비스의 infrastructure 레이어에 유지한다 (libs로 추출하지 않음).
- 표준 패턴:
  ```
  DefaultErrorHandler + DeadLetterPublishingRecoverer
  ExponentialBackOff(1000L initial, 2.0 multiplier, 30000L maxInterval)
  maxRetries = 3
  ```
- not-retryable 예외 등록: `DeserializationException`, `SerializationException`, 서비스별 비즈니스 규칙 위반 예외
- DLQ KafkaTemplate은 `byte[]` 값 직렬화를 사용하여 원본 메시지를 그대로 전달한다.
- 기존 `-dlq` 또는 `-DLQ` suffix를 사용하는 서비스는 `.dlq`로 변경한다.

---

# Edge Cases

- 서비스별 not-retryable 예외 목록이 다른 경우 -> 공통 예외(Deserialization 등)는 통일, 서비스별 비즈니스 예외는 각 서비스에서 추가 등록
- 기존 DLQ topic에 이미 메시지가 쌓여 있는 경우 -> 기존 DLQ topic은 수동 마이그레이션 필요 (이 태스크 범위 외)
- ExponentialBackOff 설정이 기존과 다른 서비스 -> 정책 기준으로 통일 (기존 설정 덮어씀)

---

# Failure Scenarios

- DLQ topic 네이밍 변경 시 Kafka 브로커에 새 topic 자동 생성 실패 -> auto.create.topics.enable 설정 확인 또는 topic 사전 생성
- not-retryable 예외 등록 누락으로 무한 재시도 -> 테스트에서 예외 분류 검증
- ExponentialBackOff 설정 오류로 재시도 간격 비정상 -> 단위 테스트에서 backoff 설정값 검증
- DLQ KafkaTemplate 타입 불일치로 직렬화 오류 -> byte[] serializer 사용 확인

---

# Test Requirements

- 각 서비스 KafkaConsumerConfig 단위 테스트 (ExponentialBackOff 설정값 검증)
- 각 서비스 DLQ 라우팅 테스트 (not-retryable 예외 -> DLQ 즉시, transient 예외 -> 재시도 후 DLQ)
- 각 서비스 기존 이벤트 컨슈머 통합 테스트 통과 확인
- DLQ topic 네이밍이 `.dlq` suffix를 따르는지 검증

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
