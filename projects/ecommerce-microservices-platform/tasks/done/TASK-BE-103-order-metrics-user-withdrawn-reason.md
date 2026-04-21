# Task ID

TASK-BE-103

# Title

order-service OrderMetrics 취소 reason 매핑 누락 수정 — user_withdrawn 카운터 추가

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

`OrderMetrics.incrementOrderCancelled()`에서 `"user_withdrawn"` reason이 switch-case에 없어 default인 `"user"` 카운터로 집계되는 문제를 수정한다.

`UserWithdrawalOrderService`가 `"user_withdrawn"` reason으로 호출하지만, 현재 메트릭에서는 일반 사용자 취소(`user`)와 구분되지 않아 모니터링 시 회원탈퇴 기인 취소를 추적할 수 없다.

---

# Scope

## In Scope

- `OrderMetrics`에 `order_cancelled_total{reason="user_withdrawn"}` 카운터 추가
- `incrementOrderCancelled()` switch-case에 `"user_withdrawn"` 분기 추가
- 테스트 추가

## Out of Scope

- 다른 서비스의 메트릭 변경
- Grafana 대시보드 업데이트
- 알림 규칙 변경

---

# Acceptance Criteria

- [ ] `"user_withdrawn"` reason으로 호출 시 `order_cancelled_total{reason="user_withdrawn"}` 카운터가 증가한다
- [ ] `"user"` reason으로 호출 시 기존 `order_cancelled_total{reason="user"}` 카운터가 증가한다 (변경 없음)
- [ ] 단위 테스트가 추가된다

---

# Related Specs

- `specs/services/order-service/observability.md`

# Related Skills

- `.claude/skills/backend/micrometer-metrics.md`

---

# Related Contracts

_(해당 없음)_

---

# Target Service

- `order-service`

---

# Architecture

Follow:

- `specs/services/order-service/architecture.md`

---

# Implementation Notes

- 기존 카운터 등록 패턴을 따른다: `Counter.builder("order_cancelled_total").tag("reason", "user_withdrawn").register(registry)`
- switch-case에 `"user_withdrawn"` 분기를 추가한다.

---

# Edge Cases

- 예상하지 못한 reason 값 — 기존 default 처리(user 카운터) 유지

---

# Failure Scenarios

- 카운터 등록 중복 — Micrometer는 동일 이름+태그 조합의 중복 등록을 자동 처리

---

# Test Requirements

- 단위 테스트: `"user_withdrawn"` reason 호출 시 올바른 카운터 증가 확인
- 단위 테스트: 기존 reason들의 동작 유지 확인

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Ready for review
