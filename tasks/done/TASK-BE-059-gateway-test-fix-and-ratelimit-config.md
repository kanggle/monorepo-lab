# Task ID

TASK-BE-059

# Title

gateway-service 테스트 컴파일 오류 수정 및 Rate Limiter 설정 외부화

# Status

backlog

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

If any section is missing or incomplete, this task must not be implemented.

---

# Goal

gateway-service의 JwtAuthenticationFilterTest에서 생성자 인자 불일치로 인한 컴파일 오류를 수정하고, RateLimiterConfig의 하드코딩된 값을 application.yml로 외부화한다.

현재 상태:
1. JwtAuthenticationFilterTest가 2개 인자로 생성자 호출하지만 실제 클래스는 3개 인자 필요 (GatewayMetrics 누락)
2. RateLimiterConfig의 rate limit 값 (100/200, 10/20)이 하드코딩

---

# Scope

## In Scope

- JwtAuthenticationFilterTest 생성자 호출 수정 (GatewayMetrics mock 추가)
- RateLimiterConfig에서 rate limit 값을 `@ConfigurationProperties` 또는 `@Value`로 외부화
- application.yml에 기본값 설정

## Out of Scope

- Rate Limiter 알고리즘 변경
- Gateway 라우팅 규칙 변경
- 분산 Rate Limiter (Redis 기반)

---

# Acceptance Criteria

- [ ] JwtAuthenticationFilterTest가 컴파일되고 통과한다
- [ ] RateLimiterConfig의 rate limit 값이 application.yml에서 읽힌다
- [ ] 기본값이 현재 하드코딩 값과 동일하다 (기존 동작 유지)
- [ ] 기존 gateway-service 테스트가 모두 통과한다

---

# Related Specs

- `specs/services/gateway-service/architecture.md`
- `specs/platform/testing-strategy.md`

# Related Skills

- `.claude/skills/backend/testing-backend.md`

---

# Related Contracts

_(없음)_

---

# Target Service

- `gateway-service`

---

# Architecture

Follow:

- `specs/services/gateway-service/architecture.md`

---

# Implementation Notes

- JwtAuthenticationFilterTest: `GatewayMetrics` mock 생성 후 생성자에 주입
- RateLimiterConfig: `@ConfigurationProperties(prefix = "gateway.rate-limit")` 또는 `@Value` 사용
- application.yml에 설명 주석 포함 (requests per second / burst capacity)

---

# Edge Cases

- Rate limit 값이 0 또는 음수인 경우 방어 코드
- 설정 파일 누락 시 기본값 사용

---

# Failure Scenarios

- 잘못된 설정 값으로 인한 RateLimiter 초기화 실패
- GatewayMetrics null 주입 시 NPE

---

# Test Requirements

- JwtAuthenticationFilterTest 컴파일 및 실행 확인
- RateLimiterConfig 설정 바인딩 테스트
- 기존 테스트 회귀 확인

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
