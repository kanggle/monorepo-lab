# Task ID

TASK-BE-057

# Title

전 서비스 이벤트 발행 실패 모니터링 — 실패 메트릭 및 로그 레벨 통일

# Status

done

# Owner

backend

# Task Tags

- code
- event
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

전 서비스에서 Kafka 이벤트 발행 실패 시 로그 레벨이 불일치하고 실패 메트릭이 없는 문제를 수정한다. 이벤트 발행은 best-effort로 유지하되, 실패를 추적할 수 있도록 메트릭을 추가한다.

현재 상태: 이벤트 발행 실패가 warn/error 로그로만 기록되며, 모니터링 메트릭이 없어 발행 실패를 감지할 수 없다.

---

# Scope

## In Scope

- 각 서비스의 EventPublisher에 발행 실패 카운터 메트릭 추가
- 로그 레벨 통일 (발행 실패 = ERROR)
- 메트릭 태그: service, event_type, error_type

## Out of Scope

- Transactional Outbox 패턴 도입
- 이벤트 발행 실패 시 재시도 로직 추가
- Dead Letter Queue 구성 (발행 측)

---

# Acceptance Criteria

- [ ] auth-service, product-service, order-service, payment-service, user-service의 이벤트 발행 실패 시 `event_publish_failure_total` 메트릭이 증가한다
- [ ] 모든 서비스에서 이벤트 발행 실패 로그가 ERROR 레벨로 통일된다
- [ ] 메트릭에 service, event_type 태그가 포함된다
- [ ] 기존 이벤트 발행 동작(best-effort)이 변경되지 않는다

---

# Related Specs

- `specs/platform/testing-strategy.md`

# Related Skills

- `.claude/skills/backend/testing-backend.md`

---

# Related Contracts

- `specs/contracts/events/auth-events.md`
- `specs/contracts/events/product-events.md`
- `specs/contracts/events/order-events.md`
- `specs/contracts/events/payment-events.md`
- `specs/contracts/events/user-events.md`

---

# Target Service

- `auth-service`
- `product-service`
- `order-service`
- `payment-service`
- `user-service`

---

# Architecture

Follow:

- 각 서비스의 `specs/services/<service>/architecture.md`

---

# Implementation Notes

- 각 서비스의 Metrics 클래스에 `incrementEventPublishFailure(String eventType)` 메서드 추가
- 기존 try-catch 블록에서 catch 시 메트릭 증가 호출 추가
- libs/java-observability에 공통 메트릭 이름 상수 정의 가능 (선택)
- 로그 메시지에 eventType, targetTopic 포함

---

# Edge Cases

- Kafka 브로커 전체 장애 시 대량 메트릭 발생
- 동일 이벤트 반복 실패 시 메트릭 cardinality 문제 없음 (태그 조합이 유한)

---

# Failure Scenarios

- MeterRegistry 미등록 시 NullPointerException — 방어 코드 필요
- 메트릭 수집기(Prometheus) 다운 시에도 애플리케이션에 영향 없어야 함

---

# Test Requirements

- 각 서비스 단위 테스트: 이벤트 발행 실패 시 메트릭 증가 확인
- 로그 레벨 검증 (LogCaptor 또는 유사 라이브러리)

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
