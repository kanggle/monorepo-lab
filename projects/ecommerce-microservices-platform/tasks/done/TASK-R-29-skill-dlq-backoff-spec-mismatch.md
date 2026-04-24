# Task ID

TASK-R-29

# Title

consumer-retry-dlq 스킬 문서 재시도 백오프 전략 스펙 불일치 수정 (FixedBackOff → ExponentialBackOff)

# Status

review

# Owner

backend

# Task Tags

- docs
- refactor

---

# Required Sections (must exist)

- Goal
- Scope
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Goal

TASK-R-24 리뷰 중 발견된 이슈: `.claude/skills/messaging/consumer-retry-dlq.md`의 코드 예시가 `FixedBackOff(1000L, 3)`을 사용하고 있으나, `specs/platform/event-driven-policy.md`의 Retry Policy는 Exponential Backoff(Base 1s, 배수 2^n, 최대 30s, 최대 3회)를 명시하고 있다. 참고로 8개 서비스 중 5개(order/user/shipping/promotion/notification)는 이미 `ExponentialBackOff`를 사용하지만, 3개(payment/search/auth)는 여전히 `FixedBackOff`를 사용하여 동일한 스펙 위반 상태다. 본 태스크는 스킬 문서만 수정 범위로 하며, 서비스 코드의 스펙 위반은 별도 태스크에서 다룬다. 스펙과 일치하도록 스킬 문서 코드 예시를 수정한다.

---

# Scope

## In Scope

- `.claude/skills/messaging/consumer-retry-dlq.md` 코드 예시의 백오프 전략 수정
  - `FixedBackOff(1000L, 3)` → `ExponentialBackOff` (initialInterval=1000ms, multiplier=2.0, maxInterval=30000ms, maxAttempts=3)
  - 코드 예시가 `specs/platform/event-driven-policy.md` Retry Policy 및 실서비스 구현과 일치하도록 수정

## Out of Scope

- 실제 서비스의 `KafkaConsumerConfig.java` 코드 변경 (이미 올바른 ExponentialBackOff 사용 중)
- `event-driven-policy.md` 정책 변경
- DLQ 토픽 네이밍 관련 수정 (TASK-R-24에서 완료)
- 다른 스킬 문서 수정

---

# Acceptance Criteria

- [ ] 스킬 문서 코드 예시가 `ExponentialBackOff(1000L, 2.0)` + `maxInterval(30000L)` + `maxAttempts(3)` 방식으로 수정되어 있다
- [ ] `specs/platform/event-driven-policy.md`의 Retry Policy(Base 1s, 지수 배율, 최대 30s, 최대 3회)와 완전히 일치한다
- [ ] `apps/order-service/src/main/java/com/example/order/infrastructure/config/KafkaConsumerConfig.java` 등 실구현과 패턴이 일치한다
- [ ] `FixedBackOff` 잔재가 스킬 문서에 남아있지 않다

---

# Related Specs

- `specs/platform/event-driven-policy.md` (Retry Policy 섹션)

# Related Skills

- `.claude/skills/messaging/consumer-retry-dlq.md` (수정 대상)

---

# Related Contracts

- 해당 없음 (문서 정합성 수정, 계약 영향 없음)

---

# Target Service

- 해당 없음 (공용 스킬 문서)

---

# Architecture

Follow:

- `specs/platform/event-driven-policy.md`

---

# Implementation Notes

- 실제 구현 레퍼런스: `apps/order-service/src/main/java/com/example/order/infrastructure/config/KafkaConsumerConfig.java`
  ```java
  ExponentialBackOff backOff = new ExponentialBackOff(1000L, 2.0);
  backOff.setMaxInterval(30000L);
  backOff.setMaxAttempts(3);
  ```
- 동일 패턴이 notification-service, promotion-service, shipping-service, user-service, payment-service, search-service, auth-service 에도 적용되어 있음

---

# Edge Cases

- 문서 내 `FixedBackOff` 언급이 다른 위치에 남아있는 경우 → 전체 검색으로 확인 후 모두 수정

---

# Failure Scenarios

- `FixedBackOff` 수정 누락으로 스펙 불일치 유지 → 문서 내 `FixedBackOff` 전체 grep으로 확인
- 코드 파라미터 오기재로 스펙 수치와 다른 경우 → `event-driven-policy.md` Retry Policy 수치와 대조

---

# Test Requirements

- 문서 수정이므로 자동 테스트 없음
- 수동 검증: `FixedBackOff` 문자열이 스킬 문서에 남아있지 않은지 확인
- 수동 검증: 코드 예시 수치가 `event-driven-policy.md` Retry Policy와 일치하는지 확인
- 수동 검증: 실서비스 `KafkaConsumerConfig.java`와 패턴 일치 확인

---

# Definition of Done

- [x] Implementation completed
- [x] Tests added (N/A - doc only)
- [x] Tests passing (N/A - doc only)
- [x] Contracts updated if needed (N/A)
- [x] Specs updated first if required (N/A - spec already correct)
- [x] Ready for review

---

## 구현 결과

### 변경 파일
- `.claude/skills/messaging/consumer-retry-dlq.md`

### 변경 내역

1. **Retry Configuration 섹션 코드 예시 수정**
   - 설명 문장을 "Use Spring Kafka's `DefaultErrorHandler` with `FixedBackOff`." → "Use Spring Kafka's `DefaultErrorHandler` with `ExponentialBackOff`, matching `specs/platform/event-driven-policy.md` Retry Policy (Base 1s, multiplier 2.0, max 30s, max 3 attempts)."로 변경
   - 임포트 라인 `import org.springframework.util.backoff.ExponentialBackOff;` 추가
   - `FixedBackOff backOff = new FixedBackOff(1000L, 3);` 제거
   - 다음 패턴으로 교체 (order-service `KafkaConsumerConfig.java`와 동일):
     ```java
     ExponentialBackOff backOff = new ExponentialBackOff(1000L, 2.0);
     backOff.setMaxInterval(30000L);
     backOff.setMaxAttempts(3);
     ```

2. **Common Pitfalls 테이블 수정**
   - "Use bounded `FixedBackOff` or `ExponentialBackOff`" → "Use bounded `ExponentialBackOff` with `setMaxAttempts(3)`"로 변경

### 검증
- `grep -n "FixedBackOff" .claude/skills/messaging/consumer-retry-dlq.md` 결과 0 매치
- 코드 예시 수치(1000ms base, 2.0 multiplier, 30000ms max interval, 3 max attempts)가 `specs/platform/event-driven-policy.md` Retry Policy(Base 1s, Exponential × 2^attempt, Max 30s, Max retries 3)와 일치
- `apps/order-service/src/main/java/com/example/order/infrastructure/config/KafkaConsumerConfig.java` 실구현 패턴과 동일

### 변경 없음
- 다른 섹션(DLQ Topic Naming, Consumer Error Handling, Retry vs Skip vs DLQ 테이블, Testing DLQ Behavior)은 원본 그대로 유지
- 서비스 코드(`apps/*/KafkaConsumerConfig.java`)는 수정 범위 아님 — TASK-R-30에서 처리
- `specs/platform/event-driven-policy.md`는 수정 없음 (source of truth)
