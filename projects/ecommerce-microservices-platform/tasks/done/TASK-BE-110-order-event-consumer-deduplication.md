# Task ID

TASK-BE-110

# Title

order-service 이벤트 컨슈머 event_id 기반 중복 처리 구현

# Status

done

# Owner

backend

# Task Tags

- code
- event

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

현재 이벤트 컨슈머(PaymentCompleted, PaymentRefunded, StockChanged, UserWithdrawn)에서 `event_id` 기반 중복 처리가 구현되어 있지 않다. 스펙에서 요구하는 "Use `event_id` for deduplication"을 충족하도록 처리된 `event_id`를 추적하고 중복 이벤트를 무시하는 메커니즘을 구현한다.

---

# Scope

## In Scope

- 처리 완료된 event_id를 저장하는 테이블 설계 및 Flyway 마이그레이션
- 모든 이벤트 컨슈머에 event_id 중복 체크 로직 적용
- 중복 이벤트 수신 시 로그 기록 후 무시
- 오래된 event_id 레코드 정리 정책

## Out of Scope

- 다른 서비스의 이벤트 컨슈머 수정
- Redis 기반 중복 처리 (DB 기반으로 구현)

---

# Acceptance Criteria

- [ ] processed_events 테이블이 Flyway 마이그레이션으로 생성된다
- [ ] PaymentCompletedEventConsumer가 event_id 중복 체크를 수행한다
- [ ] PaymentRefundedEventConsumer가 event_id 중복 체크를 수행한다
- [ ] StockChangedEventConsumer가 event_id 중복 체크를 수행한다
- [ ] UserWithdrawnEventConsumer가 event_id 중복 체크를 수행한다
- [ ] 동일한 event_id로 두 번 이벤트가 들어오면 두 번째는 무시된다
- [ ] 중복 이벤트 수신 시 WARN 레벨로 로그가 기록된다

---

# Related Specs

- `specs/platform/event-driven-policy.md`
- `specs/services/order-service/architecture.md`

# Related Skills

- `.claude/skills/backend/`

---

# Related Contracts

- `specs/contracts/events/order-events.md`
- `specs/contracts/events/payment-events.md`
- `specs/contracts/events/product-events.md`

---

# Target Service

- `order-service`

---

# Architecture

Follow:

- `specs/services/order-service/architecture.md`

---

# Implementation Notes

- processed_events 테이블: event_id(PK), event_type, processed_at
- 이벤트 처리 시 event_id INSERT를 트랜잭션에 포함 (UNIQUE 제약으로 중복 방지)
- 공통 중복 체크 로직은 인프라 레이어에 배치 (도메인 레이어에 넣지 않음)
- 오래된 레코드 정리: 30일 이상된 레코드 삭제 스케줄러

---

# Edge Cases

- event_id가 null인 이벤트 수신 시 (로그 경고 후 처리 진행, 중복 체크 스킵)
- 동시에 동일 event_id 이벤트가 2개 들어오는 경우 (UNIQUE 제약으로 하나만 성공)
- processed_events 테이블 INSERT 실패 시 (이벤트 처리도 롤백)

---

# Failure Scenarios

- DB 연결 실패 → 이벤트 처리 실패 → Kafka 재시도로 복구
- processed_events 테이블 용량 초과 → 정리 스케줄러가 오래된 레코드 제거
- UNIQUE 제약 위반 → 중복 이벤트로 판단하여 정상 스킵

---

# Test Requirements

- unit test: 중복 체크 로직
- integration test: 동일 event_id로 2회 처리 시 1회만 반영 확인
- integration test: 각 컨슈머별 중복 처리 동작 확인

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
