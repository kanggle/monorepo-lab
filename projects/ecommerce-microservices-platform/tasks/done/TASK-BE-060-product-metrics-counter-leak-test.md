# Task ID

TASK-BE-060

# Title

ProductMetrics 1000회 반복 호출 Counter 중복 등록 테스트 추가

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

TASK-BE-054 리뷰에서 발견된 누락 사항을 보완한다. ProductMetrics의 동적 태그 메서드(`incrementStockAdjusted`)에 대해 1000회 반복 호출 시 MeterRegistry에 Counter가 중복 등록되지 않음을 검증하는 단위 테스트를 추가한다.

---

# Scope

## In Scope

- ProductMetricsTest에 `incrementStockAdjusted` 1000회 반복 호출 테스트 추가

## Out of Scope

- ProductMetrics 구현 코드 수정 (이미 올바르게 구현됨)
- PaymentMetrics 메모리 누수 수정 (별도 태스크)

---

# Acceptance Criteria

- [ ] ProductMetricsTest에 `incrementStockAdjusted`를 1000회 호출해도 동일 태그 조합에 대해 Counter가 1개만 등록됨을 검증하는 테스트가 추가된다
- [ ] 기존 테스트가 모두 통과한다

---

# Related Specs

- `specs/platform/testing-strategy.md`

# Related Skills

- `.claude/skills/backend/testing-backend.md`

---

# Related Contracts

_(없음 — 내부 인프라 코드)_

---

# Target Service

- `product-service`

---

# Architecture

Follow:

- `specs/services/product-service/architecture.md`

---

# Implementation Notes

- OrderMetricsTest, GatewayMetricsTest의 기존 1000회 반복 호출 테스트 패턴을 따른다
- SimpleMeterRegistry를 사용하여 등록된 meter 수를 검증한다

---

# Edge Cases

- 서로 다른 태그 조합으로 호출 시 각각 별도의 Counter가 등록되는지 확인

---

# Failure Scenarios

- 테스트에서 meter count가 기대값과 다를 경우 → Counter 캐싱 메커니즘 검증 필요

---

# Test Requirements

- ProductMetricsTest에 `incrementStockAdjusted_repeated_noCounterLeak()` 테스트 추가

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
