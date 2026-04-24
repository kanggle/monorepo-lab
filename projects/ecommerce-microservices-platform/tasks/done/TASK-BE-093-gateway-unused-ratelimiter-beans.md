# Task ID

TASK-BE-093

# Title

gateway-service 미사용 RateLimiter Bean 및 Properties 제거

# Status

done

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

`RateLimiterConfig`에서 생성하는 `defaultRateLimiter`, `authRateLimiter` Bean과 `RateLimitProperties` 클래스가 실제로 사용되지 않는다. `application.yml`의 각 라우트에서 `redis-rate-limiter.replenishRate/burstCapacity` 값을 인라인으로 직접 지정하고 있어 해당 Bean들은 dead code다.

미사용 코드를 제거하여 코드베이스를 정리한다.

코드 리뷰에서 발견된 이슈.

---

# Scope

## In Scope

- `RateLimiterConfig`에서 `defaultRateLimiter()`, `authRateLimiter()` Bean 메서드 제거
- `RateLimitProperties` 클래스 제거
- `application.yml`의 `gateway.rate-limit` 설정 섹션 제거
- `RateLimiterConfig`에서 `@EnableConfigurationProperties` 제거 (properties 제거에 따라)
- `RateLimiterConfig`의 생성자에서 `RateLimitProperties` 의존성 제거
- 관련 테스트 파일 제거 (`RateLimitPropertiesTest`, `RateLimiterConfigTest` 중 미사용 Bean 테스트)
- `ipKeyResolver` Bean은 라우트에서 `#{@ipKeyResolver}`로 참조하므로 유지

## Out of Scope

- `application.yml` 라우트별 인라인 rate limit 값 변경
- rate limiting 동작 변경

---

# Acceptance Criteria

- [ ] `RateLimitProperties` 클래스가 삭제된다
- [ ] `RateLimiterConfig`에서 `defaultRateLimiter`, `authRateLimiter` Bean이 제거된다
- [ ] `application.yml`에서 `gateway.rate-limit` 섹션이 제거된다
- [ ] `ipKeyResolver` Bean은 유지된다
- [ ] `RateLimiterConfig` 클래스가 `ipKeyResolver`만 포함하도록 단순화된다
- [ ] 관련 테스트가 정리된다
- [ ] 기존 rate limiting 동작에 영향 없음

---

# Related Specs

- `specs/services/gateway-service/overview.md`
- `specs/services/gateway-service/architecture.md`

# Related Skills

- `.claude/skills/backend/`

---

# Related Contracts

- 없음 (내부 정리)

---

# Target Service

- `gateway-service`

---

# Architecture

Follow:

- `specs/services/gateway-service/architecture.md`

---

# Implementation Notes

- `ipKeyResolver`는 `application.yml`에서 `key-resolver: "#{@ipKeyResolver}"`로 참조되므로 반드시 유지
- `RateLimiterConfig` 클래스 자체는 `ipKeyResolver` Bean을 위해 유지하되, properties 관련 코드만 제거

---

# Edge Cases

- `RateLimiterConfig`에서 `@EnableConfigurationProperties` 제거 시 다른 properties가 영향받지 않는지 확인

---

# Failure Scenarios

- `ipKeyResolver` Bean이 실수로 제거되면 라우트 rate limiting이 동작하지 않음 → `ipKeyResolver`는 유지해야 함

---

# Test Requirements

- `RateLimiterConfigTest`에서 `ipKeyResolver` Bean 생성 테스트 유지
- `RateLimitPropertiesTest` 파일 삭제
- 통합 테스트에서 rate limiting 동작 확인

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests updated
- [ ] Tests passing
- [ ] Ready for review
