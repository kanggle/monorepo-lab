# Task ID

TASK-BE-077

# Title

쿠폰/프로모션 시스템 — 할인 쿠폰 발급, 사용, 프로모션 관리 기능

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

쿠폰 발급/사용 및 프로모션 관리 기능을 구현한다.

관리자가 프로모션을 생성하고 쿠폰을 발급할 수 있으며, 사용자가 주문 시 쿠폰을 적용하여 할인을 받을 수 있다.

---

# Scope

## In Scope

- promotion-service 신규 서비스 부트스트랩
- 프로모션 CRUD API (관리자)
- 쿠폰 발급/조회/사용 API
- 주문 시 쿠폰 적용 및 할인 금액 계산
- 쿠폰 사용 이벤트 발행
- 쿠폰 만료 배치 처리

## Out of Scope

- 복합 할인 (쿠폰 중첩 적용)
- 외부 제휴 쿠폰 연동
- 프론트엔드 UI (별도 FE 태스크)

---

# Acceptance Criteria

- [x] 프로모션 생성/조회/수정/삭제 API 동작
- [x] 쿠폰 발급 및 조회 API 동작
- [x] 주문 시 쿠폰 적용으로 할인 금액 반영
- [x] 사용된 쿠폰 재사용 불가
- [x] 만료 쿠폰 자동 처리
- [x] 쿠폰 사용 이벤트 발행

---

# Related Specs

- `specs/platform/architecture-decision-rule.md`
- `specs/platform/service-boundaries.md`
- `specs/platform/coding-rules.md`
- `specs/services/promotion-service/architecture.md`

# Related Skills

- `.claude/skills/backend/`

---

# Related Contracts

- `specs/contracts/http/promotion-api.md`
- `specs/contracts/events/promotion-events.md`
- `specs/contracts/http/order-api.md` (쿠폰 적용 연동)
- `specs/contracts/events/order-events.md`

---

# Target Service

- `promotion-service` (신규)

---

# Architecture

- DDD-style Architecture (see `specs/services/promotion-service/architecture.md`)

---

# Implementation Notes

- order-service는 쿠폰 적용 시 promotion-service에 동기 HTTP 호출
- 쿠폰 사용/만료 이벤트는 Kafka로 발행

---

# Edge Cases

- 쿠폰 수량 한정 시 동시 발급 요청
- 쿠폰 적용 후 주문 취소 시 쿠폰 복원
- 프로모션 기간 만료 직전 사용 요청

---

# Failure Scenarios

- 쿠폰 적용 중 order-service 장애
- 쿠폰 발급 수량 초과
- 이벤트 발행 실패 시 쿠폰 상태 불일치

---

# Test Requirements

- unit test
- integration test
- 동시성 테스트 (쿠폰 수량 제한)

---

# Definition of Done

- [x] Implementation completed
- [x] Tests added
- [x] Tests passing
- [x] Contracts updated if needed
- [x] Specs updated first if required
- [x] Ready for review
