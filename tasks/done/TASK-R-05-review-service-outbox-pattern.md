# Task ID

TASK-R-05

# Title

review-service Outbox 패턴 적용 (이벤트 발행 정책 준수)

# Status

review

# Owner

backend

# Task Tags

- refactor
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

review-service의 이벤트 발행이 event-driven-policy의 "publish after transaction commits" 정책을 위반하고 있다. 트랜잭션 내부에서 Kafka로 직접 이벤트를 발행하면 트랜잭션 롤백 시에도 이벤트가 전송되는 문제가 발생한다.

Outbox 패턴을 적용하여 이벤트를 outbox 테이블에 저장한 뒤, 트랜잭션 커밋 후 별도 프로세스로 Kafka에 발행하도록 변경한다. TASK-R-02에서 추출한 Outbox 공유 라이브러리에 의존한다.

---

# Scope

## In Scope

- review-service 이벤트 발행 로직을 Outbox 패턴으로 전환
- ReviewCreated, ReviewUpdated, ReviewDeleted 이벤트에 Outbox 적용
- TASK-R-02에서 추출한 libs/outbox 라이브러리 의존성 추가
- outbox 테이블 Flyway 마이그레이션 추가
- 기존 직접 Kafka 발행 코드 제거

## Out of Scope

- Outbox 라이브러리 자체 구현 (TASK-R-02 범위)
- 다른 서비스의 Outbox 적용
- Kafka 토픽 구조 변경
- 이벤트 페이로드 변경

---

# Acceptance Criteria

- [ ] ReviewCreated, ReviewUpdated, ReviewDeleted 이벤트가 Outbox 테이블을 통해 발행된다
- [ ] 트랜잭션 롤백 시 이벤트가 발행되지 않는다
- [ ] 트랜잭션 커밋 후 이벤트가 Kafka에 정상 발행된다
- [ ] libs/outbox 라이브러리에 의존한다
- [ ] outbox 테이블 Flyway 마이그레이션이 존재한다
- [ ] 기존 직접 Kafka 발행 코드가 제거되었다
- [ ] 기존 이벤트 컨트랙트(페이로드, 토픽명)가 변경되지 않았다

---

# Related Specs

- `specs/platform/event-driven-policy.md`
- `specs/services/review-service/architecture.md`
- `specs/platform/shared-library-policy.md`

# Related Skills

- `.claude/skills/backend/architecture/ddd.md`
- `.claude/skills/backend/testing-backend.md`

---

# Related Contracts

- `specs/contracts/events/review-events.md`

---

# Target Service

- `review-service`

---

# Architecture

Follow:

- `specs/services/review-service/architecture.md`

---

# Dependencies

- TASK-R-02 (Outbox libs 추출) 완료 후 구현 가능

---

# Implementation Notes

- event-driven-policy.md Producer Rules: "Producers must publish events after the transaction commits (transactional outbox pattern or equivalent)."
- infrastructure 레이어에서 Outbox 어댑터를 구현하고 application 레이어의 port를 통해 접근한다
- 기존 KafkaEventPublisher를 OutboxEventPublisher로 교체한다

---

# Edge Cases

- Outbox 테이블 저장 실패 시 전체 트랜잭션 롤백 확인
- Outbox relay 프로세스가 중단되었다가 재시작 시 미전송 이벤트 재발행
- 동일 이벤트 중복 발행 시 consumer 측 멱등성으로 처리

---

# Failure Scenarios

- Outbox 테이블 저장 실패: 비즈니스 트랜잭션과 함께 롤백되므로 데이터 불일치 없음
- Outbox relay Kafka 연결 실패: 재시도 후 발행, at-least-once 보장
- 마이그레이션 충돌: 버전 번호 확인 후 적용

---

# Test Requirements

- 단위 테스트: OutboxEventPublisher가 outbox 테이블에 이벤트를 저장하는지 검증
- 통합 테스트: 트랜잭션 커밋 후 Kafka에 이벤트가 발행되는지 검증 (Testcontainers)
- 통합 테스트: 트랜잭션 롤백 시 outbox에 이벤트가 저장되지 않는지 검증

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
