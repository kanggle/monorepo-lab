# Task ID

TASK-BE-077-FIX2-missing-tests-dlq-index

# Title

promotion-service: restore 테스트 누락, DLQ 미설정, order_id 인덱스 없음 수정

# Status

ready

# Owner

backend

# Task Tags

- code
- test
- event
- performance

---

# Goal

TASK-BE-077-FIX 리뷰에서 발견된 세 가지 이슈를 수정한다.

1. **Critical - Acceptance Criteria 미충족 (테스트 누락)**: `Coupon.restore()` 도메인 단위 테스트, consumer 서비스 레이어 단위 테스트, 쿠폰 복원 통합 테스트가 모두 누락되어 있다.
2. **Warning - DLQ 미설정**: `event-driven-policy.md`는 모든 consumer group에 DLQ를 필수로 요구하지만 `application.yml`에 DLQ 설정이 없다. Kafka 리스너에 `DefaultErrorHandler` + DLQ 연동이 필요하다.
3. **Warning - `order_id` 인덱스 누락**: `findByOrderIdAndStatus(orderId, status)` 쿼리를 실행하지만 `coupons` 테이블에 `order_id` 컬럼 인덱스가 없어 풀 스캔이 발생할 수 있다.

---

# Scope

## In Scope

- `CouponTest.java`에 `restore()` 시나리오 단위 테스트 추가
  - USED → ISSUED 복원 성공
  - EXPIRED 쿠폰 복원 시 `CouponRestoreNotAllowedException` 발생
  - 이미 ISSUED 상태인 쿠폰 복원 시 멱등 처리(no-op)
- `OrderCancelledEventConsumerTest.java` (또는 `CouponCommandServiceRestoreTest.java`) 단위 테스트 추가
  - 정상 orderId로 USED 쿠폰이 ISSUED로 복원되는 경우
  - orderId에 연결된 쿠폰이 없는 경우 이벤트 무시
  - payload가 null인 경우 이벤트 무시
  - orderId가 blank인 경우 이벤트 무시
- 쿠폰 복원 통합 테스트 추가 (`CouponRestoreIntegrationTest.java`)
  - 쿠폰 적용 후 OrderCancelled 이벤트 consumer 직접 호출 → DB에서 쿠폰 상태 ISSUED로 복원 검증
  - EXPIRED 쿠폰은 복원되지 않음 검증
  - 동일 이벤트 중복 처리 시 멱등성 검증
- Kafka `DefaultErrorHandler` + DLQ 설정 (`KafkaConsumerConfig.java` 신규 추가)
  - 재시도 3회, 지수 백오프 (1s, 2s, 4s, max 30s)
  - DLQ 토픽: `order.order.cancelled.dlq`
  - `DefaultErrorHandler`를 `KafkaListenerContainerFactory`에 등록
- Flyway 마이그레이션 추가: `coupons` 테이블 `order_id` 컬럼 인덱스 생성
  - 파일명: `V4__add_index_coupons_order_id.sql`
  - `CREATE INDEX idx_coupons_order_id ON coupons (order_id);`

## Out of Scope

- 기존 기능 로직 변경
- 이벤트 계약 변경
- 복합 쿠폰 시나리오

---

# Acceptance Criteria

- [ ] `CouponTest.java`에 `restore()` 관련 테스트 3개 이상 추가 (성공, EXPIRED 예외, 이미 ISSUED 멱등 처리)
- [ ] `OrderCancelledEventConsumer`의 `handle()` 메서드를 직접 호출하는 단위 테스트 추가 (정상 처리 + 4가지 엣지케이스)
- [ ] `CouponCommandService.restoreCouponsByOrderId()` 직접 테스트 추가 (쿠폰 없는 경우 포함)
- [ ] 통합 테스트에서 consumer 호출 후 DB 상태 검증 (USED → ISSUED, EXPIRED 불변, 멱등성)
- [ ] `application.yml` 또는 Java Config에 Kafka `DefaultErrorHandler` + 지수 백오프 + DLQ(`order.order.cancelled.dlq`) 설정 추가
- [ ] `V4__add_index_coupons_order_id.sql` 마이그레이션 파일 추가
- [ ] 모든 테스트 메서드 이름이 `{scenario}_{condition}_{expectedResult}` 패턴 준수

---

# Related Specs

- `specs/services/promotion-service/architecture.md`
- `specs/platform/event-driven-policy.md`
- `specs/platform/testing-strategy.md`

# Related Contracts

- `specs/contracts/events/order-events.md` (OrderCancelled 이벤트 소비)

---

# Edge Cases

- EXPIRED 쿠폰은 복원 불가
- 이미 ISSUED 상태인 쿠폰에 restore() 호출 시 no-op (멱등)
- orderId에 연결된 USED 쿠폰이 없는 경우 이벤트 무시
- payload 또는 orderId가 null/blank인 이벤트 무시
- 동일 이벤트 중복 처리 시 쿠폰 상태 중복 변경 없음

---

# Failure Scenarios

- DLQ 미설정 상태에서 consumer 예외 발생 시 이벤트 유실 위험 (이번 태스크로 해결)
- DB 장애 시 트랜잭션 롤백 후 Kafka 재처리 (기존 `@Transactional` 이미 적용됨)
