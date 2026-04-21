# Task ID

TASK-BE-081

# Title

배송 추적 서비스 — 주문 배송 상태 관리 및 추적

# Status

done

# Owner

backend

# Task Tags

- code
- api
- event

---

# Goal

주문 확정 후 배송 상태를 관리하고 추적할 수 있는 기능을 구현한다.

배송 상태 변경 이벤트를 발행하여 주문 서비스 및 알림 서비스와 연동한다.

---

# Scope

## In Scope

- shipping-service 신규 서비스 부트스트랩
- 배송 생성 (OrderConfirmed 이벤트 소비)
- 배송 상태 업데이트 API (관리자)
- 배송 상태 조회 API (사용자)
- 배송 상태 변경 이벤트 발행 (ShippingStatusChanged)
- 배송 상태: PREPARING → SHIPPED → IN_TRANSIT → DELIVERED

## Out of Scope

- 외부 택배사 API 연동
- 실시간 위치 추적
- 반품/교환 처리
- 프론트엔드 UI (별도 FE 태스크)

---

# Acceptance Criteria

- [ ] OrderConfirmed 이벤트 소비하여 배송 레코드 생성
- [ ] 배송 상태 업데이트 API 동작 (관리자)
- [ ] 배송 상태 조회 API 동작 (사용자 — 주문 ID 기반)
- [ ] 배송 상태 변경 시 ShippingStatusChanged 이벤트 발행
- [ ] 배송 상태 전이 규칙 준수 (역방향 전이 불가)

---

# Related Specs

- `specs/platform/architecture-decision-rule.md`
- `specs/platform/service-boundaries.md`
- `specs/platform/event-driven-policy.md`
- `specs/services/shipping-service/architecture.md`

# Related Skills

- `.claude/skills/backend/`

---

# Related Contracts

- `specs/contracts/http/shipping-api.md`
- `specs/contracts/events/shipping-events.md`
- `specs/contracts/events/order-events.md`
- `specs/contracts/http/order-api.md`

---

# Target Service

- `shipping-service` (신규)

---

# Architecture

- DDD-style Architecture (see `specs/services/shipping-service/architecture.md`)

---

# Implementation Notes

- OrderConfirmed 이벤트 소비로 배송 레코드 자동 생성
- ShippingStatusChanged 이벤트를 order-service, notification-service가 소비

---

# Edge Cases

- 주문 취소 후 배송 생성 요청 수신
- 배송 상태 역전이 시도
- 동일 주문에 대한 중복 배송 생성

---

# Failure Scenarios

- OrderConfirmed 이벤트 소비 실패
- 배송 상태 변경 이벤트 발행 실패
- order-service 장애 시 주문 정보 조회 불가

---

# Test Requirements

- unit test
- integration test
- 이벤트 소비/발행 테스트

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
