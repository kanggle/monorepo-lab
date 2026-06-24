# TASK-MONO-303 — 주문 SHIPPED/DELIVERED 운영자 직접 전이 제거 (배송 이벤트 단일 구동)

- **Status**: review
- **Type**: cross-project (ecommerce-microservices-platform + platform-console) — atomic PR
- **Analysis model**: Opus 4.8 / **Implementation model**: Sonnet 4.6 (소규모 제거 + 스펙/계약 동기화 + 테스트)

## Goal

주문 운영 화면에서 운영자가 주문 상태를 직접 `배송 시작(SHIPPED)`/`배송 완료(DELIVERED)`로
바꿀 수 있어, **배송(Shipping) 애그리거트를 거치지 않고 주문만 점프**시킬 수 있다. 그 결과
"주문=SHIPPED 인데 배송=준비중(PREPARING)" 같은 두 애그리거트 간 divergence 가 발생한다.

ADR-MONO-022 §D7 의 설계 의도는 **주문의 SHIPPED·DELIVERED 는 배송 진행의 투영**이다 —
배송이 SHIPPED/DELIVERED 되면 `ShippingStatusChanged` 이벤트가 발행되고 order-service 의
`ShippingStatusChangedEventConsumer` 가 주문을 따라 전이시킨다(SHIPPED 는 기존, DELIVERED 는
TASK-BE-429 에서 추가). 이미 이 단일 구동 경로가 완성돼 있으므로, **중복·충돌하는 운영자
직접 경로를 제거**하여 주문 상세에서는 SHIPPED/DELIVERED 를 **조회 전용**으로 만든다.

운영자가 주문에서 할 수 있는 전이는 `CONFIRMED`(확인)와 `CANCELLED`(취소)만 남는다. 배송
시작/완료는 배송 운영 화면(`PUT /api/shippings/{id}/status`)에서만 수행하며, 주문은 이벤트로
자동 반영된다.

## Scope

**In scope** (cross-project, 하나의 atomic PR):

ecommerce-microservices-platform (order-service — 운영자 직접 경로 제거):
1. `apps/order-service/.../application/service/AdminOrderStatusService.java` — `switch` 에서
   `case SHIPPED -> order.ship(clock)` 와 `case DELIVERED -> order.deliver(clock)` 제거. 두
   값은 `default` 로 떨어져 `IllegalArgumentException` → 400 `INVALID_ORDER_REQUEST`(기존
   `GlobalExceptionHandler` 매핑)로 거부된다. 이유를 명시하는 주석 추가.
   - **불변(건드리지 않음)**: `Order.ship()`/`Order.deliver()` 도메인 메서드, `OrderShippingService`,
     `ShippingStatusChangedEventConsumer` — 이벤트 구동 경로는 동일 `Order.ship()`/`deliver()` 를
     계속 호출하므로 보존. 운영자 경로의 **호출지점만** 제거한다.
2. `apps/order-service/.../test/.../AdminOrderStatusServiceTest.java` — 기존 성공 테스트
   `confirmedToShipped`/`shippedToDelivered` 를 "운영자 직접 SHIPPED/DELIVERED → IllegalArgumentException"
   으로 재작성. 나머지(CONFIRMED/CANCELLED/PENDING/notFound/invalid-enum) 불변.

ecommerce-microservices-platform (스펙/계약 정정):
3. `specs/contracts/http/order-api.md` — `POST /api/admin/orders/{orderId}/status` 의 "Allowed
   transitions" 에서 `CONFIRMED → SHIPPED`, `SHIPPED → DELIVERED` 제거. 두 전이는 배송 이벤트
   구동(operator 비허용)임을 명시.
4. `specs/contracts/events/shipping-events.md` — `ShippingStatusChanged` 소비 설명을 SHIPPED +
   DELIVERED 양쪽으로 확장하고, 이것이 주문이 SHIPPED/DELIVERED 에 도달하는 **유일 경로**(admin
   엔드포인트는 더 이상 제공하지 않음)임을 명시.

platform-console (console-web — 운영자 UI 제거):
5. `apps/console-web/src/features/ecommerce-ops/api/order-types.ts` — `TRANSITIONS` 에서
   `CONFIRMED: ['SHIPPED','CANCELLED'] → ['CANCELLED']`, `SHIPPED: ['DELIVERED'] → []`. 헤더
   docstring 상태머신 주석을 operator-initiated 기준으로 갱신.
6. `apps/console-web/src/features/ecommerce-ops/components/OrderStatusDialog.tsx` — 더 이상
   버튼으로 렌더되지 않는 `SHIPPED`/`DELIVERED` 라벨 제거 + docstring 갱신.
7. `apps/console-web/tests/unit/ecommerce-orders-state.test.ts` — `CONFIRMED → ['CANCELLED']`,
   `SHIPPED → []` 로 단언 갱신.
8. `projects/platform-console/specs/contracts/console-integration-contract.md` — 주문 상태변경
   엔드포인트(#17) 설명에 허용 전이 정정 노트 추가(SHIPPED/DELIVERED = 배송 이벤트 구동, 조회 전용).

**Out of scope**:
- 배송 도메인(shipping-service) 변경 — 이미 SHIPPED/DELIVERED 로의 `ShippingStatusChanged` 발행.
- 이벤트 구동 전파 로직 변경 — `ShippingStatusChangedEventConsumer`/`OrderShippingService` 불변.
- `Order.ship()`/`Order.deliver()` 도메인 메서드 시그니처/로직 변경.
- 운영자 수동 보정(이벤트 유실/WMS 장애 시 강제 SHIPPED)용 별도 보정 경로 — 필요 시 별도 task.

## Acceptance Criteria

- **AC-1 — 운영자 직접 SHIPPED 거부.** admin status 엔드포인트에 `status=SHIPPED` 제출 시
  `Order.ship()` 이 호출되지 않고 400 `INVALID_ORDER_REQUEST` 로 거부된다(저장 없음).
- **AC-2 — 운영자 직접 DELIVERED 거부.** 동일하게 `status=DELIVERED` 도 400 으로 거부된다(저장 없음).
- **AC-3 — 운영자 CONFIRM/CANCEL 무회귀.** `PENDING→CONFIRMED`, `PENDING|CONFIRMED→CANCELLED`
  는 그대로 동작. SHIPPED 주문에 CONFIRMED/CANCELLED 시도 시 기존 예외(InvalidOrderException /
  OrderCannotBeCancelledException) 동작 유지.
- **AC-4 — 이벤트 구동 전파 무회귀.** 배송 SHIPPED → 주문 SHIPPED, 배송 DELIVERED → 주문
  DELIVERED 의 이벤트 경로(`ShippingStatusChangedEventConsumer` → `OrderShippingService`)는
  변경 없이 동작(기존 테스트 GREEN).
- **AC-5 — 콘솔 UI 조회 전용.** 주문 상세에서 `CONFIRMED` 행은 '취소' 버튼만 노출(‘배송 시작’ 없음),
  `SHIPPED` 행은 전이 버튼 없음(‘배송 완료’ 없음). 주문은 SHIPPED/DELIVERED 상태를 표시만 한다.
- **AC-6 — 계약 정합.** `order-api.md` 의 허용 전이와 `console-integration-contract.md` 의 주문
  전이 설명이 코드와 일치. `shipping-events.md` 가 SHIPPED/DELIVERED 단일 구동 경로를 명시.
- **AC-7 — 게이트.** order-service `./gradlew :…:order-service:test` GREEN(재작성 테스트 포함).
  console-web `pnpm lint` + `pnpm tsc --noEmit` + `pnpm vitest run`(상태머신 테스트 포함) GREEN.
  Testcontainers IT 는 이 호스트 비활성 → CI 권위.

## Related Specs

- `projects/ecommerce-microservices-platform/specs/services/order-service/architecture.md` —
  주문 상태머신(`PENDING → CONFIRMED → SHIPPED → DELIVERED`) + 배송 이벤트 소비. SHIPPED/DELIVERED
  를 운영자 액션으로 귀속하지 않으므로 변경 불필요(검증만).
- `docs/adr/ADR-MONO-022-ecommerce-wms-fulfillment-integration.md` §D7 — return-leg(배송 진행 →
  주문 반영). 본 task 는 그 return-leg 를 주문의 **유일** SHIPPED/DELIVERED 경로로 확정한다.
- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4.10 #17 —
  주문 상태변경 consumer 계약.

## Related Contracts

- `projects/ecommerce-microservices-platform/specs/contracts/http/order-api.md` —
  `POST /api/admin/orders/{orderId}/status` 허용 전이(정정 대상).
- `projects/ecommerce-microservices-platform/specs/contracts/events/shipping-events.md` —
  `ShippingStatusChanged`(계약 payload 변경 없음, 소비 설명만 확장).

## Edge Cases

- 운영자가 (구버전 클라이언트/직접 호출로) `status=SHIPPED|DELIVERED` 를 보내도 400 으로 거부 —
  주문/배송 divergence 가 구조적으로 차단됨.
- 콘솔에서 `allowedTransitions('SHIPPED')` 는 `[]` → `OrderStatusDialog` 가 `null` 렌더(버튼 영역 없음).
  기존 `targets.length === 0 → return null` 분기로 graceful.
- 배송 이벤트가 순서 역전/유실되어 주문이 SHIPPED 에 못 미친 상태로 멈춰도, 운영자 강제 보정은
  본 task 범위 밖(별도 보정 경로 필요 시 후속 task). 현재는 배송 화면에서 정상 전이 시 이벤트로 복구.

## Failure Scenarios

- `case SHIPPED`/`case DELIVERED` 제거 시 `case CONFIRMED`/`CANCELLED` 를 깨면 운영자 전이 회귀 →
  AC-3 테스트(CONFIRMED/CANCELLED 성공 + SHIPPED 주문에서의 예외)로 가드.
- 이벤트 구동 경로(`OrderShippingService.markShipped/markDelivered`)가 같은 `Order.ship()/deliver()`
  를 호출하므로, 도메인 메서드를 잘못 제거하면 이벤트 전파가 깨짐 → 도메인 메서드는 보존, 호출지점만
  제거. `OrderShippingServiceTest`/`ShippingStatusChangedEventConsumerTest` GREEN 으로 가드(AC-4).
