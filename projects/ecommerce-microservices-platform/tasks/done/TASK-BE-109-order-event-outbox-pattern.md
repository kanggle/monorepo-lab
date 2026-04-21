# Task ID

TASK-BE-109

# Title

order-service 이벤트 발행 Transactional Outbox 패턴 적용

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

현재 `OrderEventKafkaHandler`에서 `@TransactionalEventListener(AFTER_COMMIT)` 이후 Kafka 전송이 실패하면 이벤트가 유실된다. Transactional Outbox 패턴을 적용하여 도메인 이벤트가 DB 트랜잭션과 함께 outbox 테이블에 저장되고, 별도 메커니즘으로 Kafka에 안정적으로 전달되도록 한다.

---

# Scope

## In Scope

- outbox 테이블 설계 및 Flyway 마이그레이션 추가
- 이벤트 발행 시 outbox 테이블에 저장하는 로직 구현
- outbox 테이블에서 미발행 이벤트를 폴링하여 Kafka로 전송하는 스케줄러 구현
- 발행 완료된 이벤트의 상태 업데이트 처리
- 기존 `OrderEventKafkaHandler`의 직접 Kafka 전송 로직 제거

## Out of Scope

- CDC(Change Data Capture) 기반 outbox 구현
- 다른 서비스의 outbox 패턴 적용
- Kafka 클러스터 설정 변경

---

# Acceptance Criteria

- [ ] outbox 테이블이 Flyway 마이그레이션으로 생성된다
- [ ] OrderPlaced, OrderCancelled 이벤트가 트랜잭션 내에서 outbox 테이블에 저장된다
- [ ] 폴링 스케줄러가 미발행 이벤트를 Kafka로 전송한다
- [ ] Kafka 전송 성공 시 outbox 레코드가 발행 완료로 표시된다
- [ ] Kafka 전송 실패 시 재시도가 가능하다
- [ ] 기존 도메인 트랜잭션이 롤백되면 outbox 레코드도 롤백된다
- [ ] 이벤트 발행 순서가 보장된다

---

# Related Specs

- `specs/platform/event-driven-policy.md`
- `specs/platform/architecture.md`
- `specs/services/order-service/architecture.md`
- `specs/services/order-service/observability.md`

# Related Skills

- `.claude/skills/backend/`

---

# Related Contracts

- `specs/contracts/events/order-events.md`

---

# Target Service

- `order-service`

---

# Architecture

Follow:

- `specs/services/order-service/architecture.md`

---

# Implementation Notes

- outbox 테이블 스키마: id, aggregate_type, aggregate_id, event_type, payload(JSON), created_at, published_at, status(PENDING/PUBLISHED)
- 폴링 주기는 설정값으로 외부화 (기본 1초)
- `SpringOrderEventPublisher`에서 ApplicationEvent 대신 outbox 테이블에 직접 저장하도록 변경
- `OrderEventKafkaHandler`는 폴링 기반으로 전환

---

# Edge Cases

- 폴링 스케줄러가 동시에 여러 인스턴스에서 실행되는 경우 (SELECT FOR UPDATE 또는 분산 락 필요)
- outbox 테이블에 대량의 미발행 이벤트가 쌓이는 경우 (배치 크기 제한)
- Kafka가 장시간 다운된 경우 outbox 테이블 사이즈 관리

---

# Failure Scenarios

- Kafka 전송 실패 시 재시도 횟수 초과 → 알림 발행 및 수동 처리 필요
- outbox 폴링 스케줄러 장애 → 이벤트 발행 지연 (데이터 유실 없음)
- DB 트랜잭션 롤백 시 → outbox 레코드도 함께 롤백되어 정합성 유지

---

# Test Requirements

- unit test: outbox 저장 로직, 폴링 스케줄러 로직
- integration test: 트랜잭션 커밋/롤백 시 outbox 레코드 상태 확인
- integration test: 폴링 → Kafka 전송 → 상태 업데이트 흐름

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
