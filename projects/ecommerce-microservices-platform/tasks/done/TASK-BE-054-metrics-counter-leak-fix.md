# Task ID

TASK-BE-054

# Title

order-service, gateway-service Metrics Counter 등록 메모리 누수 수정

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

OrderMetrics와 GatewayMetrics에서 매 호출마다 Counter를 새로 등록하는 메모리 누수 패턴을 수정한다.

현재 상태: `Counter.builder(...).register(registry)` 가 메서드 호출 시마다 실행되어 중복 Counter 인스턴스가 생성된다.

---

# Scope

## In Scope

- OrderMetrics의 `incrementStatusTransition()`, `addOrderAmount()` 메서드에서 Counter 사전 등록 또는 캐싱
- GatewayMetrics의 `incrementRequestsRouted()`, `incrementRateLimited()`, `incrementUpstreamError()` 메서드 수정
- 기존 ProductMetrics의 동일 패턴 확인 및 수정

## Out of Scope

- 새로운 메트릭 추가
- Prometheus/Grafana 대시보드 변경
- AuthMetrics의 account_deactivated 미분류 문제 (별도 태스크 가능)

---

# Acceptance Criteria

- [ ] OrderMetrics에서 Counter가 생성자 또는 초기화 시점에 한 번만 등록된다
- [ ] GatewayMetrics에서 동적 태그가 필요한 Counter는 `Metrics.counter()` 또는 `MeterRegistry.counter()` 방식으로 캐싱된다
- [ ] 동일 메서드를 1000번 호출해도 MeterRegistry에 중복 등록되지 않음을 테스트로 검증한다
- [ ] 기존 메트릭 이름과 태그가 변경되지 않는다

---

# Related Specs

- `specs/services/order-service/architecture.md`
- `specs/services/gateway-service/architecture.md`
- `specs/platform/testing-strategy.md`

# Related Skills

- `.claude/skills/backend/testing-backend.md`

---

# Related Contracts

_(없음 — 내부 인프라 코드)_

---

# Target Service

- `order-service`
- `gateway-service`
- `product-service` (해당 시)

---

# Architecture

Follow:

- `specs/services/order-service/architecture.md`
- `specs/services/gateway-service/architecture.md`

---

# Implementation Notes

- 동적 태그(from/to 상태 전이)가 필요한 Counter는 `MeterRegistry.counter(name, tags...)` 사용 — Micrometer가 내부적으로 캐싱
- 고정 태그 Counter는 생성자에서 `Counter.builder(...).register(registry)` 후 필드에 보관
- GatewayMetrics는 이미 일부 Counter를 생성자에서 등록 중 — 나머지도 동일 패턴으로 통일

---

# Edge Cases

- 동적 태그 조합이 매우 많은 경우 (cardinality explosion) — 상태 전이 조합은 유한하므로 문제 없음
- MeterRegistry가 null인 경우 (테스트 환경)

---

# Failure Scenarios

- Counter 이름 변경 시 기존 대시보드/알림 깨짐 — 이름 유지 필수
- 리팩토링 중 태그 순서 변경 시 Micrometer가 다른 Counter로 인식

---

# Test Requirements

- 단위 테스트: Counter 등록 후 동일 메서드 반복 호출 시 count 정확성 검증
- 단위 테스트: MeterRegistry에 등록된 meter 수 확인

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
