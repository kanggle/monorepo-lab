# Task ID

TASK-BE-026

# Title

주문 취소 API — POST /api/orders/{orderId}/cancel + OrderCancelled 이벤트

# Status

ready

# Owner

backend

# Task Tags

- code
- api
- event

---

# Goal

사용자가 자신의 주문을 취소할 수 있는 API를 구현한다.
PENDING 또는 CONFIRMED 상태의 주문만 취소 가능하며, 취소 시 `OrderCancelled` 이벤트를 발행한다.

---

# Scope

## In Scope

- `POST /api/orders/{orderId}/cancel` 엔드포인트 구현
- 소유자 검증 (userId 불일치 시 403)
- 취소 가능 상태 검증 (PENDING, CONFIRMED만 허용)
- Order 상태를 CANCELLED로 변경 후 DB 저장
- `OrderCancelled` 이벤트 발행 (Spring ApplicationEvent)
- 응답: `{ "orderId": "string", "status": "CANCELLED" }` 200
- 단위 테스트: OrderCancellationService, Order 도메인 전이
- 슬라이스 테스트: OrderController
- 통합 테스트: 취소 후 상태 확인

## Out of Scope

- 환불 처리 (payment-service 연동)
- 재고 복구 연동 (product-service 연동)

---

# Acceptance Criteria

- [ ] `POST /api/orders/{orderId}/cancel` 정상 요청 시 200과 CANCELLED 상태 반환
- [ ] SHIPPED 또는 DELIVERED 상태 주문 취소 시 422 `ORDER_CANNOT_BE_CANCELLED` 반환
- [ ] 다른 사용자의 주문 취소 시 403 `UNAUTHORIZED` 반환
- [ ] 존재하지 않는 orderId 취소 시 404 `ORDER_NOT_FOUND` 반환
- [ ] 이미 CANCELLED된 주문 취소 시 422 `ORDER_CANNOT_BE_CANCELLED` 반환
- [ ] 취소 성공 시 `OrderCancelled` 이벤트가 발행된다
- [ ] DB에 CANCELLED 상태로 저장된다

---

# Related Specs

- `specs/platform/coding-rules.md`
- `specs/platform/testing-strategy.md`
- `specs/platform/error-handling.md`
- `specs/services/order-service/architecture.md`

# Related Skills

- `.claude/skills/backend/springboot-api.md`
- `.claude/skills/backend/exception-handling.md`
- `.claude/skills/backend/transaction-handling.md`
- `.claude/skills/messaging/event-implementation.md`
- `.claude/skills/backend/testing-backend.md`

---

# Related Contracts

- `specs/contracts/http/order-api.md`
- `specs/contracts/events/order-events.md`

---

# Target Service

- `order-service`

---

# Architecture

Follow:

- `specs/services/order-service/architecture.md`

취소 로직은 Order 애그리거트 내부에서 상태 전이 규칙을 강제한다. 서비스 레이어는 오케스트레이션만 담당한다.

---

# Implementation Notes

- `userId`는 `X-User-Id` 헤더에서 추출
- 취소 가능 상태: `PENDING`, `CONFIRMED`
- 취소 불가 상태: `SHIPPED`, `DELIVERED`, `CANCELLED`
- 상태 전이 로직은 `Order.cancel()` 도메인 메서드에 구현
- 이벤트는 Spring ApplicationEvent로 발행

---

# Edge Cases

- 동시에 같은 주문을 취소하는 요청 (낙관적 락 또는 DB 트랜잭션으로 처리)
- 이미 취소된 주문을 다시 취소하는 경우

---

# Failure Scenarios

- DB 저장 실패 시 500 반환, 이벤트 발행하지 않음
- 이벤트 발행 실패가 취소 결과에 영향을 주지 않아야 함

---

# Test Requirements

- `Order.cancel()` 도메인 단위 테스트
  - PENDING → CANCELLED 성공
  - SHIPPED에서 취소 시 예외
  - CANCELLED에서 재취소 시 예외
- `OrderCancellationService` 단위 테스트 (Mock 기반)
  - 이벤트 발행 검증
  - 소유자 불일치 예외
- `OrderController` 슬라이스 테스트
  - 200, 403, 404, 422 케이스
- 통합 테스트
  - 주문 생성 → 취소 → DB 상태 확인

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
