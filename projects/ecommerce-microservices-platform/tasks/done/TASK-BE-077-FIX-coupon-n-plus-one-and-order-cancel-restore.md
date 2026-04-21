# Task ID

TASK-BE-077-FIX-coupon-n-plus-one-and-order-cancel-restore

# Title

promotion-service N+1 쿼리 개선 및 주문 취소 시 쿠폰 복원 구현

# Status

done

# Owner

backend

# Task Tags

- code
- event
- performance

---

# Goal

두 가지 이슈를 해결한다.

1. **N+1 쿼리**: `CouponQueryService.getMyCoupons()`는 쿠폰 목록을 가져온 뒤 각 쿠폰마다 `promotionRepository.findById()`를 개별 호출하여 N+1 쿼리가 발생한다.

2. **주문 취소 시 쿠폰 복원 미구현**: 태스크 Edge Cases에 "쿠폰 적용 후 주문 취소 시 쿠폰 복원"이 명시되어 있으며, `specs/contracts/events/order-events.md`에 `OrderCancelled` 이벤트가 정의되어 있다. 그러나 promotion-service에 이 이벤트를 consume하여 쿠폰 상태를 ISSUED로 복원하는 로직이 없다.

---

# Scope

## In Scope

- `CouponQueryService.getMyCoupons()`의 N+1 쿼리 개선: 쿠폰에 연결된 promotionId 목록으로 프로모션을 일괄 조회하도록 변경 (`PromotionRepository.findAllByIds()` 추가)
- `OrderCancelled` 이벤트 consumer 구현: Kafka topic에서 이벤트를 수신하여 해당 orderId로 연결된 USED 쿠폰을 ISSUED 상태로 복원
- Coupon 도메인에 `restore()` 메서드 추가 (USED → ISSUED 복원, 이미 EXPIRED인 쿠폰은 복원 불가)
- 관련 단위 테스트 및 통합 테스트 추가

## Out of Scope

- 복합 쿠폰 중첩 적용
- 외부 이벤트 계약 변경

---

# Acceptance Criteria

- [ ] `CouponQueryService.getMyCoupons()` 호출 시 N+1 쿼리가 발생하지 않음 (쿠폰 조회 1회 + 프로모션 일괄 조회 1회)
- [ ] `promotion.order.cancelled` 또는 `order.order.cancelled` 토픽의 `OrderCancelled` 이벤트를 수신하여 쿠폰 상태를 USED → ISSUED로 복원함
- [ ] 이미 EXPIRED된 쿠폰은 주문 취소 시에도 복원되지 않음
- [ ] orderId로 연결된 쿠폰이 없을 경우 이벤트를 무시함 (idempotent)
- [ ] 중복 이벤트 처리 시 멱등성이 보장됨
- [ ] 단위 테스트: `Coupon.restore()` 도메인 메서드 테스트
- [ ] 단위 테스트: consumer 서비스 레이어 테스트
- [ ] 통합 테스트: 쿠폰 적용 후 주문 취소 이벤트 수신 → 쿠폰 상태 복원 검증

---

# Related Specs

- `specs/services/promotion-service/architecture.md`
- `specs/platform/event-driven-policy.md`
- `specs/platform/coding-rules.md`

# Related Contracts

- `specs/contracts/events/order-events.md` (OrderCancelled 이벤트 소비)
- `specs/contracts/events/promotion-events.md`
- `specs/contracts/http/promotion-api.md`

---

# Edge Cases

- 주문 취소 이벤트가 중복 수신될 때 쿠폰이 이중 복원되지 않아야 함
- 쿠폰이 EXPIRED 상태인 경우 복원 불가 (이미 만료된 쿠폰은 재사용 불가)
- 쿠폰이 이미 ISSUED 상태인 경우 (이미 복원된 경우) 무시
- orderId에 연결된 쿠폰이 없는 경우 이벤트 무시

---

# Failure Scenarios

- OrderCancelled 이벤트 consumer 처리 실패 시 DLQ로 라우팅
- 이벤트 처리 중 DB 장애 시 트랜잭션 롤백 후 재처리

