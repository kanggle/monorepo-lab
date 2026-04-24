# Task ID

TASK-BE-046

# Title

order-service Kafka DLQ 설정 추가 — 전 컨슈머 DeadLetterPublishingRecoverer 적용

# Status

review

# Owner

backend

# Task Tags

- code
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

TASK-BE-043, TASK-BE-044, TASK-BE-045 리뷰에서 공통으로 발견된 DLQ 미구현 이슈를 수정한다.

`specs/platform/event-driven-policy.md`는 "Dead-letter queues (DLQ) must be configured for all consumer groups"를 요구하지만, order-service에는 `KafkaConsumerConfig`가 존재하지 않아 모든 컨슈머에서 처리 실패 시 DLQ 라우팅이 되지 않는다.

user-service의 `KafkaConsumerConfig` 패턴(`DeadLetterPublishingRecoverer` + `DefaultErrorHandler` + `FixedBackOff`)을 order-service에 동일하게 적용한다.

이 태스크 완료 후: order-service의 모든 Kafka 컨슈머에서 역직렬화 실패 및 처리 실패 시 `{topic}.DLT` 토픽으로 메시지가 라우팅된다.

---

# Scope

## In Scope

- `KafkaConsumerConfig` 클래스 생성 — `DefaultErrorHandler` + `DeadLetterPublishingRecoverer` 빈 등록
- `FixedBackOff(1000L, 2)` 재시도 정책 적용
- `JsonProcessingException`을 non-retryable 예외로 등록
- 기존 4개 컨슈머의 예외 처리 패턴 조정 — DLQ 라우팅을 위해 역직렬화 실패 시 예외를 전파하도록 변경
  - `StockChangedEventConsumer`
  - `PaymentCompletedEventConsumer`
  - `PaymentRefundedEventConsumer`
  - `UserWithdrawnEventConsumer`

## Out of Scope

- 컨슈머 비즈니스 로직 변경
- DLQ 모니터링/알림 구현
- 새로운 API 엔드포인트 추가

---

# Acceptance Criteria

- [ ] `KafkaConsumerConfig` 클래스가 order-service infrastructure.config 패키지에 생성된다
- [ ] `DeadLetterPublishingRecoverer`가 `{topic}.DLT` 패턴으로 DLQ 토픽을 설정한다
- [ ] `DefaultErrorHandler`에 `FixedBackOff(1000L, 2)` 재시도 정책이 적용된다
- [ ] `JsonProcessingException`이 non-retryable 예외로 등록된다
- [ ] 4개 컨슈머 모두 역직렬화 실패 시 예외가 전파되어 DLQ로 라우팅된다
- [ ] 처리 실패(DB 장애 등) 시 재시도 후 DLQ로 라우팅된다
- [ ] 테스트가 추가되고 전체 테스트가 통과한다

---

# Related Specs

- `specs/platform/event-driven-policy.md`
- `specs/platform/error-handling.md`
- `specs/services/order-service/architecture.md`
- `specs/platform/testing-strategy.md`

# Related Skills

- `.claude/skills/backend/springboot-api.md`
- `.claude/skills/backend/testing-backend.md`
- `.claude/skills/backend/implementation-workflow.md`

---

# Related Contracts

- (없음 — 인프라 설정 태스크)

---

# Target Service

- `order-service`

---

# Architecture

Follow:

- `specs/services/order-service/architecture.md`

수정 대상 계층:
- Infrastructure: `KafkaConsumerConfig` 생성 (config 패키지)
- Infrastructure: 기존 4개 컨슈머의 `onMessage()` 메서드 — 역직렬화 실패 시 예외 전파로 변경

참고 구현:
- `apps/user-service/src/main/java/com/example/user/infrastructure/config/KafkaConsumerConfig.java`

---

# Implementation Notes

### user-service 참고 패턴

user-service의 `KafkaConsumerConfig`를 그대로 따른다:
- `DeadLetterPublishingRecoverer` — `{topic}.DLT` 토픽으로 라우팅
- `DefaultErrorHandler` — `FixedBackOff(1000L, 2)` (1초 간격, 최대 2회 재시도)
- `JsonProcessingException`을 non-retryable로 등록

### 컨슈머 예외 처리 변경

현재 4개 컨슈머 모두 `JsonProcessingException`을 내부에서 catch하고 로그만 남기는 패턴:
```java
catch (JsonProcessingException e) {
    log.error("Failed to deserialize ...: {}", e.getMessage());
    // 메시지 소실됨
}
```

변경 후: `JsonProcessingException`을 `RuntimeException`으로 감싸서 전파 → `DefaultErrorHandler`가 DLQ로 라우팅
```java
catch (JsonProcessingException e) {
    throw new RuntimeException("Failed to deserialize ...", e);
}
```

---

# Edge Cases

- DLQ 토픽이 존재하지 않는 경우 → Kafka auto-create 설정에 의존 (기존 인프라 설정 확인 필요)
- 재시도 2회 후에도 실패 → DLQ로 라우팅
- DLQ 발행 자체가 실패 → 에러 로그 기록

---

# Failure Scenarios

- Kafka 브로커 장애 → DLQ 발행 실패, 에러 로그 기록
- DLQ 토픽 미존재 → Kafka 설정에 따라 자동 생성 또는 에러
- DeadLetterPublishingRecoverer 초기화 실패 → 애플리케이션 기동 실패

---

# Test Requirements

- 단위 테스트: `KafkaConsumerConfig` 빈 생성 검증
- 통합 테스트: 역직렬화 실패 시 DLQ 라우팅 검증
- 기존 테스트 전체 통과

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
