# TASK-BE-429 — 배송 DELIVERED → 주문 DELIVERED 전파 (주문 생애주기 종착 연결)

- **Status**: done
- **Project**: ecommerce-microservices-platform
- **Service**: order-service
- **Analysis model**: Opus 4.8 / **Implementation model**: Sonnet 4.6 (작은 additive lifecycle 연결 + 테스트)

## Goal

주문 생애주기 `PENDING → CONFIRMED → SHIPPED → DELIVERED` 에서, 현재 주문은 배송이 `SHIPPED` 되면 `SHIPPED` 까지만 따라가고 **`DELIVERED` 에는 영영 도달하지 못한다**. `ShippingStatusChangedEventConsumer` 가 `SHIPPED` edge 만 처리하고 `DELIVERED` 는 명시적으로 무시하기 때문(`Order.deliver()`(SHIPPED→DELIVERED)는 이미 도메인에 존재하나 호출되는 곳이 없음). 배송이 `DELIVERED` 로 보고되면 주문도 `DELIVERED` 로 전이되도록 그 마지막 한 칸을 잇는다.

## Scope

**In scope** (order-service only — 기존 `SHIPPED` 전파와 대칭의 additive 변경):

1. `application/service/OrderShippingService.java` — `markDelivered(String orderId)` 추가(`markShipped` 미러: `findByIdAcrossTenants` → `order.deliver(clock)` → save → 변경 시 `recordStatusTransition`; `InvalidOrderException` 은 warn 로그로 흡수; 주문 부재 시 warn+return).
2. `infrastructure/event/ShippingStatusChangedEventConsumer.java` — `newStatus` 라우팅: `SHIPPED → markShipped`, **`DELIVERED → markDelivered`**, 그 외(IN_TRANSIT 등)는 기존대로 무시. dedup·null-payload·blank-orderId 가드는 그대로 적용(현 early-return 구조 유지).
3. 테스트:
   - `OrderShippingServiceTest` — `markDelivered`: SHIPPED→DELIVERED 전이 + 메트릭, 이미 DELIVERED면 no-op(idempotent), 비-SHIPPED(CONFIRMED 등)면 전이 없음+warn(InvalidOrderException 흡수), 주문 부재 warn.
   - `ShippingStatusChangedEventConsumerTest` — `DELIVERED` 이벤트 → `markDelivered` 호출; `SHIPPED` 는 여전히 `markShipped`; IN_TRANSIT 등은 둘 다 미호출; dedup/blank-orderId 스킵.

**Out of scope**:
- 주문취소 → 배송취소 전파(Gap 2, 별도 task — WMS 이행 취소까지 얽힘).
- `IN_TRANSIT` 등 중간 상태의 주문 반영(주문 생애주기에 해당 상태 없음).
- 배송 도메인(shipping-service) 변경 — 이미 `DELIVERED` 로의 `ShippingStatusChanged` 를 발행함(consumer 측만 보강).

## Acceptance Criteria

- **AC-1 — DELIVERED 전파.** 배송이 `DELIVERED` 로 바뀌어 `ShippingStatusChanged(newStatus=DELIVERED, orderId)` 가 발행되면, 해당 주문이 `SHIPPED → DELIVERED` 로 전이된다.
- **AC-2 — SHIPPED 무회귀.** 기존 `SHIPPED` 전파 동작은 그대로(배송 SHIPPED → 주문 SHIPPED).
- **AC-3 — 멱등/안전.** 이미 `DELIVERED` 인 주문에 중복 DELIVERED 이벤트 → no-op(이벤트 미발행·상태불변). 아직 `SHIPPED` 가 아닌(CONFIRMED 등) 주문에 DELIVERED 이벤트가 와도 예외로 컨슈머가 죽지 않고 warn 로그 후 스킵(`InvalidOrderException` 흡수). eventId dedup 유지.
- **AC-4 — 무시 상태 보존.** `IN_TRANSIT` 등 비-SHIPPED/비-DELIVERED 전이는 주문에 아무 영향 없음.
- **AC-5 — 게이트.** order-service `./gradlew :…:order-service:test` GREEN(신규 테스트 포함). Testcontainers IT 는 이 호스트 비활성 → CI 권위.

## Related Specs

- `specs/services/order-service/architecture.md` — 주문 상태머신(`SHIPPED → DELIVERED`) + 배송 이벤트 소비.
- `docs/adr/ADR-MONO-022-ecommerce-wms-fulfillment-integration.md` §D7 — return-leg(배송 진행 → 주문 반영). 이 task 는 그 return-leg 의 종착(DELIVERED)을 잇는다.

## Related Contracts

- `specs/contracts/events/shipping-events.md` — `ShippingStatusChanged` 의 `newStatus` 값(PREPARING/SHIPPED/IN_TRANSIT/DELIVERED). 계약 변경 없음(이미 DELIVERED 를 발행). consumer 가 추가 값 하나를 더 처리하는 것뿐.

## Edge Cases

- 배송이 SHIPPED 이벤트 없이(혹은 순서 역전으로) DELIVERED 가 먼저 도착 → 주문이 아직 SHIPPED 아님 → `Order.deliver()` 가 `InvalidOrderException` → warn 흡수, 주문 SHIPPED 미경유 상태 유지(다음 SHIPPED/DELIVERED 재처리 여지). 단 일반 흐름은 SHIPPED 후 DELIVERED.
- 중복 DELIVERED(at-least-once) → eventId dedup + `deliver()` idempotent(이미 DELIVERED면 false) 이중 가드.
- CANCELLED/STUCK_RECOVERY_FAILED 주문에 늦은 DELIVERED → `deliver()` 가 not-SHIPPED 로 throw → warn 흡수(상태 불변).

## Failure Scenarios

- markDelivered 가 `InvalidOrderException` 을 흡수하지 않으면 컨슈머가 예외→리밸런스/DLQ 루프 → markShipped 와 동일하게 try/catch warn 으로 흡수(AC-3 가드).
- DELIVERED 분기 추가 시 SHIPPED 라우팅을 깨면 회귀 → AC-2 테스트로 가드.
