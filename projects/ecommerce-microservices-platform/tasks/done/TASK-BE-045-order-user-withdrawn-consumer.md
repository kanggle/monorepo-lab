# Task ID

TASK-BE-045

# Title

order-service UserWithdrawn 이벤트 소비 — 탈퇴 사용자 활성 주문 취소 처리

# Status

review

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

user-service가 발행하는 `UserWithdrawn` 이벤트를 order-service에서 소비하여, 탈퇴한 사용자의 활성 주문(PENDING, CONFIRMED)을 일괄 취소하고 `OrderCancelled` 이벤트를 발행한다.

이 태스크 완료 후: 사용자가 탈퇴하면 해당 사용자의 미완료 주문이 자동으로 취소되고, 각 취소 건에 대해 OrderCancelled 이벤트가 발행되어 payment-service 등 하류 서비스가 후속 처리할 수 있다.

---

# Scope

## In Scope

- `UserWithdrawnEventConsumer` — `user.user.withdrawn` 토픽 구독, `@KafkaListener` 구현
- 사용자 탈퇴 처리 애플리케이션 서비스 (`UserWithdrawalOrderService`)
- 사용자의 활성 주문(PENDING, CONFIRMED) 일괄 취소
- 취소된 각 주문에 대해 `OrderCancelled` 이벤트 발행
- 멱등성 처리 — 동일 이벤트 중복 수신 시 안전하게 무시
- DLQ 설정 — 처리 실패 시 데드레터 큐 라우팅

## Out of Scope

- 사용자 데이터 삭제/익명화 (user-service 소관)
- 이미 완료된 주문(SHIPPED, DELIVERED) 처리
- auth-service의 UserWithdrawn 소비 (별도 태스크)
- 새로운 API 엔드포인트 추가

---

# Acceptance Criteria

- [ ] `user.user.withdrawn` 토픽을 `order-service` groupId로 구독한다
- [ ] 이벤트 수신 시 userId로 활성 주문(PENDING, CONFIRMED)을 조회한다
- [ ] 활성 주문이 있으면 모두 취소하고 각각 `OrderCancelled` 이벤트를 발행한다
- [ ] 활성 주문이 없으면 info 로그를 남기고 정상 완료한다
- [ ] 동일 이벤트를 2회 수신해도 결과가 동일하다 (멱등성)
- [ ] 이벤트 처리 실패 시 DLQ로 라우팅된다
- [ ] 수신 성공 시 info 로그가 기록된다 (취소된 주문 수 포함)
- [ ] 테스트가 추가되고 전체 테스트가 통과한다

---

# Related Specs

- `specs/platform/event-driven-policy.md`
- `specs/platform/error-handling.md`
- `specs/services/order-service/architecture.md`
- `specs/platform/testing-strategy.md`

# Related Skills

- `.claude/skills/backend/springboot-api.md`
- `.claude/skills/backend/architecture/ddd.md`
- `.claude/skills/backend/testing-backend.md`
- `.claude/skills/backend/implementation-workflow.md`

---

# Related Contracts

- `specs/contracts/events/user-events.md` (UserWithdrawn — 소비)
- `specs/contracts/events/order-events.md` (OrderCancelled — 발행)

---

# Target Service

- `order-service`

---

# Architecture

Follow:

- `specs/services/order-service/architecture.md`

수정 대상 계층:
- Domain: OrderRepository에 활성 주문 조회 메서드 추가
- Application: `UserWithdrawalOrderService` — 사용자 탈퇴 시 주문 일괄 취소
- Infrastructure: `UserWithdrawnEventConsumer` — Kafka 컨슈머

---

# Implementation Notes

### 이벤트 페이로드

```json
{
  "eventId": "uuid",
  "eventType": "UserWithdrawn",
  "occurredAt": "ISO 8601",
  "source": "user-service",
  "payload": {
    "userId": "uuid",
    "withdrawnAt": "ISO 8601"
  }
}
```

### 컨슈머 구현 패턴

- 기존 `StockChangedEventConsumer` 패턴을 따른다
- JSON 디시리얼라이즈 → null 체크 → 필수 필드 검증 → 서비스 호출

### 일괄 취소 처리

- `OrderRepository.findByUserIdAndStatusIn(userId, [PENDING, CONFIRMED])` 로 활성 주문 조회
- 각 주문에 대해 `order.cancel()` 호출
- 각 취소 건에 대해 `OrderCancelled` 이벤트 발행
- 트랜잭션 단위: 전체를 하나의 트랜잭션으로 처리

### 멱등성

- 이미 CANCELLED 상태인 주문은 cancel() 호출 시 무시 (기존 멱등성 활용)
- 활성 주문이 0건이면 이미 처리된 것으로 판단

---

# Edge Cases

- 동일 UserWithdrawn 이벤트 2회 수신 → 멱등 처리, 활성 주문 0건으로 무시
- 활성 주문이 0건 → info 로그, 정상 완료
- 활성 주문이 다수 → 하나의 트랜잭션으로 일괄 취소
- SHIPPED/DELIVERED 상태 주문 → 취소 대상에서 제외 (조회 조건으로 필터)
- 이벤트 페이로드에 null userId → warn 로그, DLQ

---

# Failure Scenarios

- Kafka 브로커 장애 → 컨슈머 자동 재연결, 이벤트 재처리
- DB 장애 → 예외 발생, 트랜잭션 롤백, DLQ 라우팅
- 일부 주문 취소 실패 → 전체 트랜잭션 롤백, DLQ 라우팅 후 재처리
- OrderCancelled 이벤트 발행 실패 → 주문 취소는 완료, 이벤트 발행 실패 로그

---

# Test Requirements

- 단위 테스트: `UserWithdrawalOrderService` — 활성 주문 일괄 취소, 활성 주문 0건, 멱등성
- 단위 테스트: 각 취소 건에 대한 OrderCancelled 이벤트 발행 검증
- 통합 테스트: `UserWithdrawnEventConsumer` — 이벤트 수신 및 처리 검증
- 기존 테스트 전체 통과

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
