# Task ID

TASK-BE-043

# Title

order-service PaymentCompleted 이벤트 소비 — 결제 완료 시 주문 결제 상태 반영

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

payment-service가 발행하는 `PaymentCompleted` 이벤트를 order-service에서 소비하여, 해당 주문의 결제 완료 상태를 반영한다.

이 태스크 완료 후: 결제가 완료되면 order-service가 해당 이벤트를 수신하고, 주문에 결제 완료 정보(paymentId, paidAt)를 기록한다.

---

# Scope

## In Scope

- `PaymentCompletedEventConsumer` — `payment.payment.completed` 토픽 구독, `@KafkaListener` 구현
- Order 도메인에 결제 완료 반영 메서드 추가 (`markPaymentCompleted`)
- 결제 완료 처리 애플리케이션 서비스 (`PaymentConfirmationService`)
- 멱등성 처리 — 동일 이벤트 중복 수신 시 안전하게 무시
- DLQ 설정 — 처리 실패 시 데드레터 큐 라우팅

## Out of Scope

- Order 상태 전이 변경 (기존 PENDING → CONFIRMED 흐름 유지)
- 결제 실패 처리 (PaymentFailed 이벤트는 별도 태스크)
- 새로운 API 엔드포인트 추가

---

# Acceptance Criteria

- [ ] `payment.payment.completed` 토픽을 `order-service` groupId로 구독한다
- [ ] 이벤트 수신 시 orderId로 주문을 조회하고 결제 완료 정보(paymentId, paidAt)를 기록한다
- [ ] 동일 이벤트를 2회 수신해도 결과가 동일하다 (멱등성)
- [ ] orderId에 해당하는 주문이 없으면 warn 로그를 남기고 정상 완료한다
- [ ] 이벤트 처리 실패 시 DLQ로 라우팅된다
- [ ] 수신 성공 시 info 로그가 기록된다
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

- `specs/contracts/events/payment-events.md` (PaymentCompleted — 소비)

---

# Target Service

- `order-service`

---

# Architecture

Follow:

- `specs/services/order-service/architecture.md`

수정 대상 계층:
- Domain: Order 엔티티에 `markPaymentCompleted(paymentId, paidAt)` 메서드 추가
- Application: `PaymentConfirmationService` — 결제 완료 처리 로직
- Infrastructure: `PaymentCompletedEventConsumer` — Kafka 컨슈머

---

# Implementation Notes

### 이벤트 페이로드

```json
{
  "eventId": "uuid",
  "eventType": "PaymentCompleted",
  "occurredAt": "ISO 8601",
  "source": "payment-service",
  "payload": {
    "paymentId": "uuid",
    "orderId": "uuid",
    "userId": "uuid",
    "amount": 50000,
    "paidAt": "ISO 8601"
  }
}
```

### 컨슈머 구현 패턴

- 기존 `StockChangedEventConsumer` 패턴을 따른다
- JSON 디시리얼라이즈 → null 체크 → 필수 필드 검증 → 서비스 호출
- 예외 발생 시 warn 로그 후 DLQ 라우팅

### 멱등성

- Order에 paymentId가 이미 기록되어 있으면 중복 이벤트로 판단하고 무시

---

# Edge Cases

- 동일 PaymentCompleted 이벤트 2회 수신 → 멱등 처리, 두 번째는 무시
- orderId에 해당하는 주문이 없음 → warn 로그, 정상 완료 (ack)
- 이벤트 페이로드에 null 필드 → 필수 필드 누락 시 warn 로그, DLQ
- 이미 CANCELLED 상태인 주문에 PaymentCompleted 수신 → warn 로그, 무시

---

# Failure Scenarios

- Kafka 브로커 장애 → 컨슈머 자동 재연결, 이벤트 재처리
- DB 장애 → 예외 발생, DLQ 라우팅
- 디시리얼라이즈 실패 → warn 로그, DLQ 라우팅

---

# Test Requirements

- 단위 테스트: `PaymentConfirmationService` — 정상 처리, 멱등성, 주문 미존재
- 단위 테스트: Order 도메인 `markPaymentCompleted()` 메서드
- 통합 테스트: `PaymentCompletedEventConsumer` — 이벤트 수신 및 처리 검증
- 기존 테스트 전체 통과

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
