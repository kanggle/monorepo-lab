# Task ID

TASK-BE-094

# Title

gateway-service public 라우트 요청 라우팅 메트릭 누락 수정

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

`JwtAuthenticationFilter`에서 public 라우트(`GET /api/products/**`, `GET /api/search/**`, `POST /api/auth/signup` 등)는 JWT 검증을 건너뛰면서 바로 `chain.filter(exchange)`를 반환한다. 이 경로에서는 `gatewayMetrics.incrementRequestsRouted()`가 호출되지 않아 라우팅 메트릭이 누락된다.

public 라우트에서도 `gateway_requests_routed_total` 메트릭을 기록하도록 수정한다.

코드 리뷰에서 발견된 이슈.

---

# Scope

## In Scope

- `JwtAuthenticationFilter.filter()`에서 public 라우트 분기에 `incrementRequestsRouted` 호출 추가

## Out of Scope

- public 라우트 목록 변경
- JWT 검증 로직 변경
- 다른 메트릭 변경

---

# Acceptance Criteria

- [ ] public 라우트 요청 시 `gateway_requests_routed_total` 메트릭이 올바른 target service 태그와 함께 기록된다
- [ ] 인증 필요 라우트의 기존 메트릭 동작에 영향 없음
- [ ] actuator health 엔드포인트는 메트릭 기록에서 제외 가능 (target이 "unknown"이므로)
- [ ] 테스트 추가

---

# Related Specs

- `specs/services/gateway-service/observability.md`
- `specs/services/gateway-service/overview.md`

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

- public 라우트 분기에서 `routeService.resolveTargetService(path)`로 target service를 구하고 `gatewayMetrics.incrementRequestsRouted(targetService)` 호출
- actuator health 경로는 "unknown"으로 기록되거나, 메트릭 기록 자체를 건너뛸 수 있음

---

# Edge Cases

- `/actuator/health` 요청 → target이 "unknown" → 메트릭 기록 여부 판단 필요
- 존재하지 않는 public 경로 → Spring Cloud Gateway가 404 반환하므로 필터 도달 여부 확인

---

# Failure Scenarios

- `RouteService.resolveTargetService`가 null 반환 → 현재 "unknown" 반환하므로 안전

---

# Test Requirements

- `JwtAuthenticationFilterTest`에 public 라우트 요청 시 `incrementRequestsRouted` 호출 검증 테스트 추가

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Ready for review
