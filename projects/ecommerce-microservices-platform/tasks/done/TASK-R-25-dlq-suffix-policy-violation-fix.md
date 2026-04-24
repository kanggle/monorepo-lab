# Task ID

TASK-R-25

# Title

payment/search/auth-service DLQ 토픽 suffix 정책 위반 수정 (.DLT → .dlq)

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

`specs/platform/event-driven-policy.md:72`는 DLQ 토픽 네이밍을 `{original-topic}.dlq`로 강제하지만, payment-service / search-service / auth-service의 `KafkaConsumerConfig`는 Spring Kafka 기본값인 `.DLT` suffix를 사용하고 있어 정책을 위반한다. 다른 5개 서비스(order, user, shipping, promotion, notification)는 이미 `.dlq`를 사용하고 있으므로, 이 3개 서비스도 동일하게 맞춰 전 서비스의 DLQ 토픽 네이밍을 정책에 일치시킨다.

---

# Scope

## In Scope

- `apps/payment-service/src/main/java/com/example/payment/config/KafkaConsumerConfig.java`의 destination resolver suffix를 `.DLT` → `.dlq`로 변경
- `apps/search-service/src/main/java/com/example/search/config/KafkaConsumerConfig.java`의 destination resolver suffix를 `.DLT` → `.dlq`로 변경
- `apps/auth-service/src/main/java/com/example/auth/infrastructure/config/KafkaConsumerConfig.java`의 destination resolver suffix를 `.DLT` → `.dlq`로 변경
- 각 파일 내 로그 메시지("Sending record to DLT") → "Sending record to DLQ" 문구 일치
- 세 서비스 모두 컴파일 및 기존 테스트 통과 확인

## Out of Scope

- 이미 `.dlq`를 사용 중인 5개 서비스(order, user, shipping, promotion, notification) 변경
- DLQ 재처리(replay) 로직 추가
- DLQ 소비자(컨슈머) 구현
- DLQ 알림/모니터링 추가 (이미 정책상 존재 — 이 태스크 범위 밖)
- 기존 `.DLT` 토픽에 남아있을 수 있는 메시지 마이그레이션 (개발/테스트 환경이라 영향 없음)

---

# Acceptance Criteria

- [ ] payment-service `KafkaConsumerConfig`의 DLQ suffix가 `.dlq`이다
- [ ] search-service `KafkaConsumerConfig`의 DLQ suffix가 `.dlq`이다
- [ ] auth-service `KafkaConsumerConfig`의 DLQ suffix가 `.dlq`이다
- [ ] 세 서비스의 DLQ 관련 로그 메시지에 `DLT`가 남아있지 않다
- [ ] 세 서비스 빌드 및 기존 테스트가 모두 통과한다
- [ ] `.claude/skills/messaging/consumer-retry-dlq.md` 및 `specs/platform/event-driven-policy.md`와 완전히 일치한다

---

# Related Specs

- `specs/platform/event-driven-policy.md` (DLQ Policy 섹션, `.dlq` suffix 강제)

# Related Skills

- `.claude/skills/messaging/consumer-retry-dlq.md`

---

# Related Contracts

- 해당 없음 (토픽 네이밍 내부 규칙, 외부 계약 없음)

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

- 레퍼런스 구현: [order-service KafkaConsumerConfig.java:20-25](apps/order-service/src/main/java/com/example/order/infrastructure/config/KafkaConsumerConfig.java#L20-L25)
- `DeadLetterPublishingRecoverer` 생성자에 destination resolver 람다를 전달하여 `record.topic() + ".dlq"` 반환
- `addNotRetryableExceptions(JsonProcessingException.class)` 기존 설정은 유지
- `FixedBackOff` 기존 설정은 유지 (auth-service는 `(1000L, 2)`, payment/search는 `(1000L, 3)` — 기존값 유지)
- 변경 대상은 suffix 문자열 및 로그 문구만

## 구현 결과

- payment-service / search-service / auth-service의 `KafkaConsumerConfig` 모두 `.DLT` → `.dlq`, 로그 `DLT` → `DLQ` 문구 수정
- 세 서비스 `compileJava` 성공 (`:apps:{payment,search,auth}-service:classes --rerun-tasks` BUILD SUCCESSFUL)
- 세 서비스 테스트 실행 결과:
  - **search-service**: 전체 통과 (24개 테스트 파일, 실패 0)
  - **payment-service**: `PaymentRefundIntegrationTest` 1건 실패 — 원인은 Toss Payments API `401 UNAUTHORIZED_KEY`(시크릿 키 인증 실패). 본 태스크와 무관한 기존 환경/자격증명 문제이며, 변경 범위는 로그 상수 및 DLQ suffix 문자열뿐이라 Toss 인증 경로에 영향 없음
  - **auth-service**: `compileTestJava` 실패 — `OAuthServiceTest.java:59`에서 `OAuthService` 생성자 인자 수 불일치(9 vs 10, `AuthEventPublisher` 누락). 본 태스크와 무관한 기존 문제(세션 시작 시점 git status에 `OAuthService.java` / `SecurityConfig.java`가 modified 상태로 존재), `KafkaConsumerConfig`와 무관
- 두 기존 실패 모두 본 태스크의 구현 대상(`KafkaConsumerConfig`의 문자열 상수)과 관련 없음이 명확하여 별도 fix 태스크로 분리 가능

---

# Edge Cases

- 로그 메시지 "Sending record to DLT" 문구가 남아있으면 수정 누락으로 간주 → 전체 grep 확인
- yml/properties에 DLQ 토픽 명시가 있는 경우 → grep 확인 결과 없음, 자동 생성 방식이라 설정 불필요
- 테스트가 `.DLT` 토픽명을 직접 검증하는 경우 → grep 확인 결과 없음

---

# Failure Scenarios

- 일부 파일만 수정되어 서비스 간 불일치 유지 → 3개 파일 모두 변경 확인
- 로그 문구 누락으로 운영 시 혼동 유발 → "DLT" 전체 grep으로 확인
- 람다 시그니처 오타로 컴파일 실패 → 각 서비스 빌드 실행

---

# Test Requirements

- 세 서비스의 기존 단위/통합 테스트 통과
- 별도 신규 테스트 불필요 (문자열 상수 변경, 기존 동작 불변)
- 수동 검증: 각 서비스 디렉토리에서 `DLT` grep 결과 없음

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
