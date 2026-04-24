# Task ID

TASK-BE-024

# Title

주문 생성 API — POST /api/orders + OrderPlaced 이벤트

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

사용자가 상품을 주문할 수 있는 API를 구현한다.
`POST /api/orders` 요청을 받아 Order 애그리거트를 생성하고, `OrderPlaced` 이벤트를 발행한다.

---

# Scope

## In Scope

- `POST /api/orders` 엔드포인트 구현
- 요청 유효성 검증 (items 비어있음, quantity <= 0)
- Order 애그리거트 생성 및 DB 저장
- `OrderPlaced` 이벤트 발행 (Spring ApplicationEvent)
- 응답: `{ "orderId": "string" }` 201
- 단위 테스트: OrderPlacementService
- 슬라이스 테스트: OrderController
- 통합 테스트: 주문 생성 후 DB 저장 확인

## Out of Scope

- product-service 재고 확인 연동
- 인증 토큰 실제 검증 (userId는 요청 헤더 `X-User-Id`에서 추출)
- 결제 연동

---

# Acceptance Criteria

- [ ] `POST /api/orders` 정상 요청 시 201과 `orderId` 반환
- [ ] items가 빈 배열이면 400 `INVALID_ORDER_REQUEST` 반환
- [ ] quantity가 0 이하인 item이 있으면 400 `INVALID_ORDER_REQUEST` 반환
- [ ] Order가 DB에 PENDING 상태로 저장된다
- [ ] `OrderPlaced` 이벤트가 발행된다
- [ ] `X-User-Id` 헤더 누락 시 400 반환

---

# Related Specs

- `specs/platform/coding-rules.md`
- `specs/platform/testing-strategy.md`
- `specs/platform/error-handling.md`
- `specs/services/order-service/architecture.md`

# Related Skills

- `.claude/skills/backend/springboot-api.md`
- `.claude/skills/backend/validation.md`
- `.claude/skills/backend/dto-mapping.md`
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

---

# Implementation Notes

- `userId`는 `X-User-Id` 요청 헤더에서 추출 (실제 인증 필터는 별도 태스크)
- `totalPrice`는 애그리거트 생성 시점에 계산 (unitPrice는 요청에서 받음)
- 이벤트는 Spring ApplicationEvent로 발행 (Kafka 전환은 별도 태스크)
- productName, optionName은 요청 body에 포함 (order-service가 product-service를 호출하지 않음)

---

# Edge Cases

- items 배열이 null이거나 비어있는 경우
- quantity가 0 또는 음수인 경우
- unitPrice가 0 이하인 경우
- 동일 variantId가 중복으로 포함된 경우 (허용 — 클라이언트 책임)

---

# Failure Scenarios

- DB 저장 실패 시 500 반환, 이벤트 발행하지 않음
- 이벤트 발행 실패가 주문 생성 결과에 영향을 주지 않아야 함 (ApplicationEvent는 동기 발행이므로 예외 처리 필요)

---

# Test Requirements

- `OrderPlacementService` 단위 테스트 (Mock 기반)
  - 정상 주문 생성 및 이벤트 발행 검증
  - 빈 items 예외 검증
- `OrderController` 슬라이스 테스트 (`@WebMvcTest`)
  - 정상 201, 유효성 실패 400
- 통합 테스트 (`@SpringBootTest`)
  - 주문 생성 후 DB에서 Order 조회 확인

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
