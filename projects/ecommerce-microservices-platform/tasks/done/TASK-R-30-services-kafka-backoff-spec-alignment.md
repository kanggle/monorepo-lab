# Task ID

TASK-R-30

# Title

payment/search/auth-service KafkaConsumerConfig 백오프 전략 스펙 정합 (FixedBackOff → ExponentialBackOff)

# Status

review

# Owner

backend

# Task Tags

- code
- refactor

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

`specs/platform/event-driven-policy.md:61-68`의 Retry Policy는 컨슈머 재시도를 **Exponential Backoff**(Base 1s, 배수 2^n, 최대 30s, 최대 3회)로 강제하지만, payment-service / search-service / auth-service의 `KafkaConsumerConfig`는 여전히 `FixedBackOff(1000L, N)`을 사용하여 스펙을 위반한다. 이미 5개 서비스(order/user/shipping/promotion/notification)는 `ExponentialBackOff`를 사용 중이므로, 이 3개 서비스도 동일하게 맞춰 8개 서비스의 백오프 전략을 스펙과 완전 일치시킨다. TASK-R-24 리뷰 중 발견됐으며 TASK-R-29(스킬 문서 수정)와 분리된 코드 범위 태스크다.

---

# Scope

## In Scope

- `apps/payment-service/src/main/java/com/example/payment/config/KafkaConsumerConfig.java`의 `FixedBackOff(1000L, 3)` → `ExponentialBackOff` (initialInterval=1000ms, multiplier=2.0, maxInterval=30000ms, maxAttempts=3)
- `apps/search-service/src/main/java/com/example/search/config/KafkaConsumerConfig.java` 동일 변경
- `apps/auth-service/src/main/java/com/example/auth/infrastructure/config/KafkaConsumerConfig.java`의 `FixedBackOff(1000L, 2)` → 동일 `ExponentialBackOff` 설정 (maxAttempts=3으로 스펙 일치)
- 기존 import 정리(`FixedBackOff` → `ExponentialBackOff`)
- `addNotRetryableExceptions(JsonProcessingException.class)` 기존 설정 유지
- 세 서비스 빌드 및 기존 테스트 통과 확인

## Out of Scope

- 이미 `ExponentialBackOff`를 사용 중인 5개 서비스(order/user/shipping/promotion/notification) 변경
- DLQ suffix 관련 변경(TASK-R-24, TASK-R-25에서 완료)
- 스킬 문서 수정(TASK-R-29 담당)
- `event-driven-policy.md` 정책 변경
- 재시도 파라미터(base/max interval/attempts) 값 자체의 조정

---

# Acceptance Criteria

- [ ] payment-service `KafkaConsumerConfig`가 `ExponentialBackOff(1000L, 2.0)` + `setMaxInterval(30000L)` + `setMaxAttempts(3)` 조합을 사용한다
- [ ] search-service `KafkaConsumerConfig`가 동일 설정을 사용한다
- [ ] auth-service `KafkaConsumerConfig`가 동일 설정을 사용한다 (`maxAttempts`가 기존 2에서 3으로 스펙에 맞춰 변경)
- [ ] 세 서비스 내 `FixedBackOff` import 및 사용이 제거되었다
- [ ] 세 서비스 빌드 및 기존 테스트 모두 통과한다
- [ ] 8개 서비스 전체가 `ExponentialBackOff`로 통일되어 `event-driven-policy.md` Retry Policy와 완전 일치한다

---

# Related Specs

- `specs/platform/event-driven-policy.md` (Retry Policy 섹션 61-68행)

# Related Skills

- `.claude/skills/messaging/consumer-retry-dlq.md` (TASK-R-29에서 스펙 일치로 수정됨)

---

# Related Contracts

- 해당 없음 (내부 재시도 정책, 외부 계약 없음)

---

# Target Service

- `payment-service`
- `search-service`
- `auth-service`

---

# Architecture

Follow:

- `specs/platform/event-driven-policy.md`
- 각 서비스의 `specs/services/<service>/architecture.md`

---

# Implementation Notes

- 레퍼런스 구현: [order-service KafkaConsumerConfig.java:27-32](apps/order-service/src/main/java/com/example/order/infrastructure/config/KafkaConsumerConfig.java#L27-L32) 또는 [user-service KafkaConsumerConfig.java:31-33](apps/user-service/src/main/java/com/example/user/infrastructure/config/KafkaConsumerConfig.java#L31-L33)
- 표준 패턴:
  ```java
  ExponentialBackOff backOff = new ExponentialBackOff(1000L, 2.0);
  backOff.setMaxInterval(30000L);
  backOff.setMaxAttempts(3);
  var errorHandler = new DefaultErrorHandler(recoverer, backOff);
  ```
- auth-service의 기존 `FixedBackOff(1000L, 2)` — maxAttempts가 2로 스펙(3)과 다르지만, 본 태스크에서 스펙 기준 3으로 통일. 의도적 축소였다면 추후 논의
- TASK-R-25에서 세 서비스의 DLQ suffix를 `.dlq`로 맞췄고, 본 태스크는 동일 파일의 백오프 전략을 추가 정리하는 후속 작업

---

# Edge Cases

- auth-service maxAttempts 2→3 변경에 따라 전체 리트라이 시간이 기존 1초×2=2초에서 최대 1+2+4=7초로 증가 → 운영 영향 없음(정책 준수)
- `FixedBackOff` import 삭제 누락 시 컴파일 경고 → 전체 grep으로 확인
- 기존 테스트가 구체적인 재시도 횟수/간격을 검증한다면 실패 가능 → 테스트 실행 결과 확인 후 대응

---

# Failure Scenarios

- 파라미터 오타(예: `1000L` vs `1_000L`)로 의도와 다른 간격 적용 → 레퍼런스와 대조 검증
- `ExponentialBackOff` import 경로 오인(`org.springframework.util.backoff.ExponentialBackOff`) → import 확인
- 세 파일 중 하나만 수정되어 부분 불일치 → 변경 후 전체 grep으로 `FixedBackOff` 잔재 확인

---

# Test Requirements

- 세 서비스의 기존 단위/통합 테스트 전부 통과
- 별도 신규 테스트 불필요 (설정 변경, 기존 동작 로직 불변)
- 수동 검증: 세 서비스 디렉토리에서 `FixedBackOff` grep 결과 0

---

# Definition of Done

- [x] Implementation completed
- [x] Tests added
- [x] Tests passing
- [x] Contracts updated if needed (해당 없음)
- [x] Specs updated first if required (해당 없음, 스펙 변경 없음)
- [x] Ready for review

---

## 구현 결과

### 변경 파일

1. `apps/payment-service/src/main/java/com/example/payment/config/KafkaConsumerConfig.java`
   - import: `org.springframework.util.backoff.FixedBackOff` → `org.springframework.util.backoff.ExponentialBackOff`
   - `new FixedBackOff(1000L, 3)` → `ExponentialBackOff(1000L, 2.0)` + `setMaxInterval(30000L)` + `setMaxAttempts(3)`
   - `addNotRetryableExceptions(JsonProcessingException.class)` 및 DLQ recoverer 유지

2. `apps/search-service/src/main/java/com/example/search/config/KafkaConsumerConfig.java`
   - 동일한 패턴으로 변경 (`FixedBackOff(1000L, 3)` → `ExponentialBackOff` 표준 조합)

3. `apps/auth-service/src/main/java/com/example/auth/infrastructure/config/KafkaConsumerConfig.java`
   - 동일한 패턴으로 변경. 기존 `FixedBackOff(1000L, 2)` → `ExponentialBackOff` 표준 조합 (`maxAttempts` 2 → 3으로 스펙 일치)

4. `apps/payment-service/src/test/java/com/example/payment/config/KafkaConsumerConfigTest.java`
   - 기존 테스트가 `FixedBackOff`를 직접 검증하고 있어 컴파일 실패 방지 위해 함께 수정
   - `ExponentialBackOff`의 `initialInterval=1000L`, `multiplier=2.0`, `maxInterval=30000L`, `maxAttempts=3` 검증으로 변경

5. `apps/search-service/src/test/java/com/example/search/config/KafkaConsumerConfigTest.java`
   - 위와 동일 (search-service 테스트 업데이트)

### `FixedBackOff` grep 결과

- `apps/payment-service`: 매칭 없음
- `apps/search-service`: 매칭 없음
- `apps/auth-service`: 매칭 없음

8개 서비스 전체가 `ExponentialBackOff` 표준 구성으로 통일됨.

### 빌드 결과

- `./gradlew :apps:payment-service:classes :apps:search-service:classes :apps:auth-service:classes --rerun-tasks` → **BUILD SUCCESSFUL** (20s)

### 테스트 결과

- `./gradlew :apps:payment-service:test` → **BUILD SUCCESSFUL** (1m 16s)
- `./gradlew :apps:auth-service:test` → **BUILD SUCCESSFUL** (2m 17s)
- `./gradlew :apps:search-service:test` → **BUILD SUCCESSFUL** (2m 32s)

### 비고

- auth-service maxAttempts 2 → 3 변경으로 최대 재시도 시간이 1+2+4=7초로 증가 (스펙 준수)
- 테스트 파일은 원래 스코프 외이나, 기존 테스트가 내부 필드(`failureTracker.backOff`)를 직접 타입 캐스팅 검증하고 있어 프로덕션 코드만 변경할 경우 컴파일 실패 → 동일 스코프로 함께 업데이트
- DLQ 토픽 네이밍(`.dlq`)과 `addNotRetryableExceptions(JsonProcessingException.class)` 설정은 그대로 유지
