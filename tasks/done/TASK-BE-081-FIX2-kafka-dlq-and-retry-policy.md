# Task ID

TASK-BE-081-FIX2

# Title

배송 서비스 Kafka DLQ 토픽 명명 및 재시도 정책 수정

# Status

ready

# Owner

backend

# Task Tags

- code
- fix

---

# Goal

`TASK-BE-081` 리뷰에서 발견된 Kafka Consumer 설정 이슈를 수정한다.

1. DLQ 토픽 명명 규칙을 스펙에 맞게 수정 (`.DLT` → `.dlq`)
2. 재시도 정책을 스펙에 맞게 수정 (FixedBackOff → Exponential, max 3회)

---

# Scope

## In Scope

- `KafkaConsumerConfig.java` DLQ 토픽 suffix 수정 (`.DLT` → `.dlq`)
- `KafkaConsumerConfig.java` 재시도 정책 수정:
  - FixedBackOff → ExponentialBackOff
  - base interval: 1초
  - max interval: 30초
  - max retry: 3회 (not retryable 제외)

## Out of Scope

- Kafka 토픽 인프라 변경
- 다른 서비스의 Kafka 설정

---

# Acceptance Criteria

- [ ] DLQ 토픽이 `{original-topic}.dlq` 형식을 따름
- [ ] 재시도 정책이 Exponential backoff (base 1s, max 30s, max retries 3)를 따름
- [ ] 기존 not-retryable 예외 설정 유지 (JsonProcessingException, IllegalArgumentException)
- [ ] 관련 단위 테스트 업데이트

---

# Related Specs

- `specs/platform/event-driven-policy.md` — Retry Policy, DLQ Policy 섹션

---

# Related Contracts

- `specs/contracts/events/order-events.md`

---

# Edge Cases

- ExponentialBackOff 최대 간격이 30초를 초과하지 않아야 함

---

# Failure Scenarios

- 재시도 설정 오류로 인해 메시지가 DLQ로 즉시 전달되는 경우

---

# Test Requirements

- `KafkaConsumerConfig` 단위 테스트 또는 설정 검증 테스트

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added or updated
- [ ] Tests passing
- [ ] Ready for review

---

# Review Findings (from TASK-BE-081 review)

## Critical

- [apps/shipping-service/src/main/java/com/example/shipping/infrastructure/config/KafkaConsumerConfig.java:23]
  DLQ 토픽 suffix가 `.DLT`로 되어 있으나 `specs/platform/event-driven-policy.md`는 `.dlq`를 요구함.
  ```java
  record.topic() + ".DLT"  // 현재
  record.topic() + ".dlq"  // 수정 필요
  ```

- [apps/shipping-service/src/main/java/com/example/shipping/infrastructure/config/KafkaConsumerConfig.java:27]
  `FixedBackOff(1000L, 2)`는 스펙의 Exponential backoff (base 1s, max 30s, max retries 3) 정책과 다름.
  `ExponentialBackOff`로 교체 필요.

## Warning

- [apps/shipping-service/src/main/java/com/example/shipping/infrastructure/event/EventDeduplicationChecker.java:19]
  `Propagation.MANDATORY`로 설정되어 있어 트랜잭션 없는 컨텍스트에서 `isDuplicate()`를 직접 호출하면 예외 발생.
  `OrderConfirmedEventConsumerTest`는 `handle()`을 직접 호출하므로 테스트에서는 트랜잭션이 없어 MANDATORY 조건이 충족되지 않을 수 있음.
  `Propagation.REQUIRED`로 변경하거나, 테스트에서 `@Transactional`을 추가하는 방식 검토 필요.
