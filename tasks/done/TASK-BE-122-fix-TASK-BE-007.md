# Task ID

TASK-BE-122

# Title

Fix: ResilienceClientFactory 표준 설정 테스트 커버리지 보완 (TASK-BE-007 리뷰 지적사항)

# Status

ready

# Owner

backend

# Task Tags

- code
- test

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

TASK-BE-007 코드 리뷰에서 발견된 테스트 커버리지 누락을 수정한다.

`ResilienceClientFactoryTest.standardCircuitBreakerConfig_matchesDocumentedDefaults` 테스트가
CB 표준 설정 5개 항목 중 2개를 검증하지 않고 있다:

- `waitDurationInOpenState` (10s) — 미검증
- `permittedNumberOfCallsInHalfOpenState` (3) — 미검증

태스크 명세서 "defaults match originals verbatim" 요건 및 Task-BE-007 정의된 CircuitBreaker
기본값 (CB: 50% / TIME_BASED 10s / min 5 / 10s wait / 3 half-open) 을 완전히 검증해야 한다.

---

# Scope

## In Scope

- `libs/java-common/src/test/java/com/example/common/resilience/ResilienceClientFactoryTest.java`
  - `standardCircuitBreakerConfig_matchesDocumentedDefaults` 테스트에 `waitDurationInOpenState(10s)`
    및 `permittedNumberOfCallsInHalfOpenState(3)` 검증 추가
  - (선택) `standardRetryConfig` 테스트에 `intervalFunction` (500ms base) 관련 검증 강화
    — Resilience4j API가 `IntervalFunction`을 직접 노출하지 않으면 skip 가능

## Out of Scope

- 구현 코드(ResilienceClientFactory.java) 변경 없음
- 서비스 클라이언트 코드 변경 없음
- WireMock 기반 CB/Retry 동작 통합 테스트 (별도 태스크 범위)

---

# Acceptance Criteria

- [ ] `standardCircuitBreakerConfig_matchesDocumentedDefaults` 테스트가 다음 5개 항목을 모두 검증한다:
  - `failureRateThreshold == 50.0f`
  - `slidingWindowType == TIME_BASED`
  - `slidingWindowSize == 10`
  - `minimumNumberOfCalls == 5`
  - `waitDurationInOpenState == Duration.ofSeconds(10)`
  - `permittedNumberOfCallsInHalfOpenState == 3`
- [ ] `:libs:java-common:test` BUILD SUCCESSFUL

---

# Related Specs

- `platform/shared-library-policy.md`
- `platform/testing-strategy.md`

# Related Contracts

없음

---

# Edge Cases

- Resilience4j `CircuitBreakerConfig.getWaitDurationInOpenState()`가 `Duration`을 반환하므로
  `Duration.ofSeconds(10)`과 비교 가능. API 확인 필요.

---

# Failure Scenarios

- `getWaitDurationInOpenState()` 메서드가 존재하지 않을 경우: Resilience4j 버전 확인 후
  `CircuitBreakerConfig` public API 범위 내에서 대안 검증 방식 사용.

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed (N/A)
- [ ] Specs updated first if required (N/A)
- [ ] Ready for review
