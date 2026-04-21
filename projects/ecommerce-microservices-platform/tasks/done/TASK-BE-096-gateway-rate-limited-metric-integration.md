# Task ID

TASK-BE-096

# Title

gateway-service rate_limited 메트릭 실제 연동 — RequestRateLimiter 429 응답 시 gateway_rate_limited_total 기록

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

`GatewayMetrics.incrementRateLimited()` 메서드가 정의되어 있지만 어디에서도 호출되지 않는다. Spring Cloud Gateway의 `RequestRateLimiter` 필터가 429 응답을 반환할 때 `gateway_rate_limited_total` 메트릭을 기록하도록 연동한다.

observability 스펙에 `gateway_rate_limited_total` 메트릭이 명시되어 있으므로 실제로 동작해야 한다.

gateway-service 코드 리뷰에서 발견된 이슈.

---

# Scope

## In Scope

- Rate limit 발생 시 (429 응답) `gateway_rate_limited_total` 메트릭 기록
- 기존 `GatewayMetrics.incrementRateLimited()` 활용
- 연동 방식 결정: `RequestLoggingFilter`의 `doFinally`에서 429 상태 코드 감지 또는 별도 GlobalFilter 추가

## Out of Scope

- rate limiting 설정값 변경
- `GatewayMetrics` 클래스 구조 변경
- 다른 메트릭 변경

---

# Acceptance Criteria

- [ ] Rate limit 초과 시 (429 응답) `gateway_rate_limited_total` 카운터가 올바른 `route` 태그와 함께 증가한다
- [ ] Rate limit이 발생하지 않는 정상 요청에서는 메트릭이 기록되지 않는다
- [ ] 기존 메트릭 (`requests_routed`, `jwt_validation_failure`, `upstream_error`) 동작에 영향 없음
- [ ] 단위 테스트 추가

---

# Related Specs

- `specs/services/gateway-service/observability.md` — `gateway_rate_limited_total` 메트릭 정의
- `specs/services/gateway-service/architecture.md`
- `specs/platform/api-gateway-policy.md`

# Related Skills

- `.claude/skills/backend/`

---

# Related Contracts

- 없음

---

# Target Service

- `gateway-service`

---

# Architecture

Follow:

- `specs/services/gateway-service/architecture.md`

---

# Implementation Notes

- 429 응답 감지는 `RequestLoggingFilter`의 `doFinally` 블록에서 처리하는 것이 자연스러움 (이미 상태 코드별 분기 존재)
- `statusCode == 429`일 때 `gatewayMetrics.incrementRateLimited(targetService)` 호출
- 또는 별도 GlobalFilter를 만들어 429 감지 전담 가능 — 아키텍처 판단 필요
- observability 스펙의 태그는 `route`이지만, 일관성을 위해 `RouteService.resolveTargetService()` 결과를 사용

---

# Edge Cases

- Rate limit 429 응답이 Spring Cloud Gateway 내부에서 생성되므로, GlobalFilter의 `doFinally`에서 상태 코드를 읽을 수 있는지 확인 필요
- 여러 라우트에서 동시에 rate limit 발생 시 메트릭 태그가 올바르게 구분되는지 확인

---

# Failure Scenarios

- `RequestRateLimiter`가 응답을 설정하기 전에 필터 체인이 종료되면 상태 코드를 읽을 수 없음 → `doFinally` 시점에는 응답 설정 완료 상태이므로 안전
- `RouteService`가 "unknown" 반환 → 메트릭 태그가 "unknown"으로 기록됨 (허용 가능)

---

# Test Requirements

- `RequestLoggingFilter` 또는 새 필터에서 429 응답 시 `incrementRateLimited` 호출 검증
- 정상 응답 (200, 401 등) 시 `incrementRateLimited` 미호출 검증
- 통합 테스트에서 Redis 기반 rate limiting 시 메트릭 기록 확인 (가능한 경우)

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Ready for review
