# Task ID

TASK-BE-091

# Title

gateway-service RequestLoggingFilter resolveTargetService 중복 제거 — RouteService 위임

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

If any section is missing or incomplete, this task must not be implemented.

---

# Goal

`RequestLoggingFilter`에 private으로 중복 구현된 `resolveTargetService` 메서드를 제거하고, `RouteService.resolveTargetService`를 사용하도록 변경한다.

현재 `RequestLoggingFilter.resolveTargetService`는 `user-service` 매핑이 누락되어 있고, `/api/admin/**` 경로도 처리하지 못한다. 이로 인해 upstream 5xx 에러 메트릭이 해당 서비스에 대해 "unknown"으로 기록된다.

코드 리뷰에서 발견된 이슈.

---

# Scope

## In Scope

- `RequestLoggingFilter`에 `RouteService` 의존성 주입
- `RequestLoggingFilter`의 private `resolveTargetService` 메서드 제거
- upstream error 메트릭 기록 시 `RouteService.resolveTargetService` 호출로 대체
- 기존 테스트 수정

## Out of Scope

- `RouteService` 로직 변경
- 메트릭 이름 또는 태그 변경
- 다른 필터 수정

---

# Acceptance Criteria

- [ ] `RequestLoggingFilter`에서 private `resolveTargetService` 메서드가 제거된다
- [ ] `RequestLoggingFilter`가 `RouteService`를 주입받아 `resolveTargetService`를 호출한다
- [ ] user-service 경로(`/api/users/**`, `/api/admin/users/**`) 요청의 5xx 에러가 "user-service"로 메트릭에 기록된다
- [ ] admin 경로(`/api/admin/products/**`) 요청의 5xx 에러가 "product-service"로 메트릭에 기록된다
- [ ] 기존 테스트가 수정되어 통과한다

---

# Related Specs

- `specs/services/gateway-service/overview.md`
- `specs/services/gateway-service/architecture.md`
- `specs/services/gateway-service/observability.md`

# Related Skills

- `.claude/skills/backend/`

---

# Related Contracts

- 없음 (내부 리팩토링)

---

# Target Service

- `gateway-service`

---

# Architecture

Follow:

- `specs/services/gateway-service/architecture.md`

---

# Implementation Notes

- `RequestLoggingFilter`는 현재 `@RequiredArgsConstructor`를 사용하므로 `RouteService` 필드 추가만으로 주입 가능
- `RouteService`의 `resolveTargetService`는 이미 user-service, admin 경로를 모두 처리함

---

# Edge Cases

- 알 수 없는 경로의 5xx 에러 → `RouteService`가 "unknown" 반환 (기존 동작 유지)

---

# Failure Scenarios

- `RouteService` 빈이 없는 경우 → Spring 컨텍스트 로딩 실패 (기존 빈이므로 발생 불가)

---

# Test Requirements

- `RequestLoggingFilter` 단위 테스트에서 `RouteService` mock 주입 및 호출 검증
- user-service, admin 경로에 대한 upstream error 메트릭 기록 테스트

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Ready for review
