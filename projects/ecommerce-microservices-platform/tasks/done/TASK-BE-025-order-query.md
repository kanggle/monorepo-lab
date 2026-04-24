# Task ID

TASK-BE-025

# Title

주문 조회 API — GET /api/orders, GET /api/orders/{orderId}

# Status

ready

# Owner

backend

# Task Tags

- code
- api

---

# Goal

사용자가 자신의 주문 목록과 주문 상세를 조회할 수 있는 API를 구현한다.
본인 주문이 아닌 경우 403을 반환한다.

---

# Scope

## In Scope

- `GET /api/orders` — 페이지네이션 기반 주문 목록 조회 (본인 주문만)
- `GET /api/orders/{orderId}` — 주문 상세 조회
- 소유자 검증 (userId 불일치 시 403)
- 단위 테스트: OrderQueryService
- 슬라이스 테스트: OrderController
- 통합 테스트: 주문 생성 후 조회 확인

## Out of Scope

- 어드민용 전체 주문 조회
- 주문 상태별 필터링
- 정렬 옵션

---

# Acceptance Criteria

- [ ] `GET /api/orders` 정상 요청 시 200과 페이지네이션 응답 반환
- [ ] `GET /api/orders/{orderId}` 정상 요청 시 200과 주문 상세 반환
- [ ] 다른 사용자의 주문 조회 시 403 `UNAUTHORIZED` 반환
- [ ] 존재하지 않는 orderId 조회 시 404 `ORDER_NOT_FOUND` 반환
- [ ] `X-User-Id` 헤더 누락 시 400 반환
- [ ] 주문 목록은 생성일 내림차순 정렬

---

# Related Specs

- `specs/platform/coding-rules.md`
- `specs/platform/testing-strategy.md`
- `specs/platform/error-handling.md`
- `specs/services/order-service/architecture.md`

# Related Skills

- `.claude/skills/backend/springboot-api.md`
- `.claude/skills/backend/dto-mapping.md`
- `.claude/skills/backend/testing-backend.md`

---

# Related Contracts

- `specs/contracts/http/order-api.md`

---

# Target Service

- `order-service`

---

# Architecture

Follow:

- `specs/services/order-service/architecture.md`

---

# Implementation Notes

- `userId`는 `X-User-Id` 헤더에서 추출
- 목록 조회는 `content`, `page`, `size`, `totalElements` 구조로 반환
- 목록의 각 항목: `orderId`, `status`, `totalPrice`, `itemCount`, `createdAt`
- 상세 조회는 `items`, `shippingAddress` 포함

---

# Edge Cases

- 주문이 하나도 없는 사용자: 빈 content와 totalElements=0 반환
- page가 전체 범위를 초과하는 경우: 빈 content 반환

---

# Failure Scenarios

- DB 조회 실패 시 500 반환

---

# Test Requirements

- `OrderQueryService` 단위 테스트 (Mock 기반)
  - 정상 조회
  - 소유자 불일치 예외
  - 존재하지 않는 주문 예외
- `OrderController` 슬라이스 테스트
  - 200 응답 구조 검증
  - 403, 404 케이스
- 통합 테스트
  - 주문 생성 후 목록/상세 조회 확인

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
