# TASK-BE-428 — 결제완료 기반 재고예약 + 백오더 saga (PaymentCompleted → reserve → confirm / backorder → restock-retry / operator-cancel)

- **Status**: done
- **Project**: ecommerce-microservices-platform
- **Services**: product-service (신규 예약 컨텍스트), order-service (상태머신 + consumer)
- **Analysis model**: Opus 4.8 / **Implementation model**: Opus 4.8 (event-driven saga · 상태머신 · 트랜잭션 설계)

## Goal

현재 v1 에선 주문 결제가 끝나도 **재고가 차감되지 않고**(reservation 미구현), 주문이 `PENDING` 에 머물러 자동 확정·배송이 일어나지 않는다(`StockChangedEventConsumer` 가 기다리는 `ORDER_RESERVED` 이벤트의 producer 가 없음). 이 task 는 그 빠진 producer 를 product-service 에 만들어 saga 를 완성한다:

1. **결제완료(PaymentCompleted) 시점에 재고를 전량(all-or-nothing) 차감**하고 `StockChanged(ORDER_RESERVED, orderId)` 를 발행 → 기존 consumer 가 주문을 `CONFIRMED` 로 전이 → 배송 생성.
2. **재고 부족 시 주문을 `BACKORDERED`(신규 상태)로 보존** — 취소·환불하지 않고 대기.
3. **재입고(관리자 재고증가 · WMS 입고) 시 백오더 주문을 FIFO 재예약** → 충족되면 `ORDER_RESERVED` → 자동 확정.
4. **운영자가 백오더 주문을 수동 취소** → 기존 `OrderCancelled` fan-out(결제 환불) 재사용.

설계 결정(확정): 다품목 주문은 **주문 전체 단위 백오더(all-or-nothing)** — 일부 품목만 부족해도 어떤 라인도 차감하지 않고 주문 전체를 백오더. 수동 취소 주체는 **운영자(콘솔)만**.

## Scope

### product-service (신규 예약 바운디드 컨텍스트 — 핵심 신규 작업)

- **신규 aggregate + 테이블** `stock_reservations` (+ child `stock_reservation_lines`):
  - `order_id`(unique), `tenant_id`, `status`(`NEW`|`RESERVED`|`BACKORDERED`|`RELEASED`), `payment_received`(bool), `@Version`, timestamps.
  - lines: `variant_id`, `product_id`, `quantity`.
  - Flyway 마이그레이션(메인 + h2 둘 다, 기존 `db/migration` / `db/migration-h2` 패턴 준수).
- **consumer 3종**(group `product-service-reservation`, dedup = `EventDeduplicationChecker` 동등 패턴):
  - `OrderPlaced`(topic `order.order.placed`) → 예약 upsert(라인 기록). 이미 `payment_received` 면 즉시 reserve 시도.
  - `PaymentCompleted`(topic `payment.payment.completed`) → 예약 upsert(`payment_received=true`). 라인이 이미 있으면 즉시 reserve 시도. **(두 토픽은 순서 보장 없음 → 어느 쪽이 먼저 와도 양쪽(lines + payment) 모이면 reserve 발화하도록 수렴 설계.)**
  - `OrderCancelled`(topic `order.order.cancelled`) → 예약 `RELEASED`. 직전이 `RESERVED` 였으면 재고복구 + `StockChanged(ORDER_CANCELLED, orderId)` 발행; `BACKORDERED`/`NEW` 였으면 복구 없이 해제.
- **ReservationService.reserve(orderId)** (all-or-nothing): 전 라인 가용 확인 → 충분하면 각 라인 차감(`Inventory.decrease`, 낙관락) → `RESERVED` → 라인별 `StockChanged(ORDER_RESERVED, orderId)` 발행. 한 라인이라도 부족하면 **무차감** → `BACKORDERED` → 신규 이벤트 `OrderReservationFailed(orderId, shortages[])` 발행.
- **재입고 재시도**: 재고가 양(+)으로 증가하는 모든 경로(`AdjustStockService` 양수 조정, `WmsInventoryReconciliationService` 양수 delta) 직후 `ReservationRetryService.onStockIncreased(variantId)` 호출 → 해당 variant 에 라인을 가진 `BACKORDERED` 예약을 **FIFO(`created_at ASC`)** 로 재예약 시도(각 예약은 자기 전 라인 재확인).
- **신규 발행 이벤트** `OrderReservationFailed` (envelope = 기존 `ProductEvent` 패턴, topic `product.product.reservation-failed`).

### order-service (상태머신 + consumer 확장)

- `OrderStatus` 에 `BACKORDERED` 추가. `isCancellable()` = `PENDING | CONFIRMED | BACKORDERED`.
- `Order.confirm(clock)`: `PENDING` **또는 `BACKORDERED`** 에서 `CONFIRMED` 허용(재입고 경로). idempotent 유지.
- `Order.markBackordered(clock)`: `PENDING → BACKORDERED`(idempotent; 이미 BACKORDERED/CONFIRMED/CANCELLED 면 no-op 또는 guard).
- **신규 consumer** `OrderReservationFailedConsumer`(topic `product.product.reservation-failed`, group `order-service`) → `OrderBackorderService.markBackordered(orderId)`.
- **운영자 수동 취소 API**: `InternalOrderController` 에 운영자용 취소 엔드포인트(`POST /api/internal/orders/{orderId}/cancel`, body `{reason}`) → 고객 소유권 검사 없이 `order.cancel(clock)`(이제 BACKORDERED 허용) → `OrderCancelled` 발행(→ 환불 fan-out 재사용). 메트릭 `recordOrderCancelled("operator")`.

### Out of scope

- 콘솔(platform-console) "백오더 주문 취소" 버튼 UI — 별도 후속 platform-console task(본 task 는 ecommerce 백엔드 API 까지). 운영자 취소는 내부 API 로 검증.
- 부분 이행(partial fulfillment) — all-or-nothing 으로 확정.
- product-service 트랜잭셔널 outbox 도입 — 기존 직접 Kafka 발행 + dedup/at-least-once 유지(StockChanged 와 동일 신뢰도). 별도 task 후보.
- search-service 변경 — `StockChanged(ORDER_RESERVED/ORDER_CANCELLED)` 는 기존 stock-sync consumer 가 이미 소비(델타 반영).

## Acceptance Criteria

- **AC-1 — 결제→예약→확정 happy path.** OrderPlaced + PaymentCompleted 가 모두 처리되고 전 라인 재고가 충분하면, 각 variant 재고가 주문수량만큼 차감되고 `StockChanged(ORDER_RESERVED, orderId)` 가 발행되어 주문이 `PENDING → CONFIRMED` 로 전이된다(기존 `StockChangedEventConsumer` 경유). 이후 배송이 생성된다.
- **AC-2 — 토픽 순서 무관 수렴.** PaymentCompleted 가 OrderPlaced 보다 먼저 도착해도(또는 그 반대) 양쪽 입력이 모이는 시점에 정확히 1회 reserve 가 발화한다(중복 차감 없음).
- **AC-3 — 재고부족 → 주문 전체 백오더.** 다품목 주문에서 한 라인이라도 부족하면 **어떤 라인도 차감하지 않고** 예약을 `BACKORDERED`, `OrderReservationFailed` 발행 → 주문 `PENDING → BACKORDERED`. 재고는 변동 없음.
- **AC-4 — 재입고 재시도(FIFO).** 백오더된 variant 재고가 (관리자 조정 또는 WMS 입고로) 충분히 증가하면, 대기 백오더 예약이 `created_at` 오름차순으로 재예약되어 충족분이 `RESERVED` + `ORDER_RESERVED` → 주문 자동 `CONFIRMED`. 부분 재입고면 충족 가능한 예약만 확정, 나머지는 BACKORDERED 유지.
- **AC-5 — 운영자 수동 취소.** 백오더(또는 PENDING/CONFIRMED) 주문을 운영자 내부 API 로 취소하면 `CANCELLED` + `OrderCancelled` 발행. RESERVED 였던 예약은 재고복구 + `StockChanged(ORDER_CANCELLED)`; BACKORDERED 였으면 재고복구 없이 예약 해제(이후 재입고가 취소된 주문에 잘못 묶이지 않음).
- **AC-6 — 멱등/동시성.** 모든 consumer 는 eventId dedup. 재고 차감/복구는 `@Version` 낙관락으로 동시 주문 경합을 안전 처리(음수 재고 불가 invariant 유지). 같은 주문에 ORDER_RESERVED 가 다중 발행(라인별)돼도 주문 확정은 1회(idempotent).
- **AC-7 — 게이트.** order-service + product-service `./gradlew :…:test` GREEN(신규 단위·슬라이스·계약 테스트 포함). Testcontainers IT 는 이 호스트에서 비활성이라 CI 가 권위(로컬은 비-IT 테스트로 검증).

## Related Specs

- `specs/use-cases/cart-and-order.md` — 주문 생애주기(재고 충분 precondition 의 실제 시행 지점이 이 task).
- `specs/services/order-service/architecture.md` — 상태머신에 BACKORDERED, 신규 consumer 추가.
- `specs/services/product-service/architecture.md` — product-service 가 order/payment 이벤트를 소비하게 됨(기존 "no event consumption" 정정) + 예약 aggregate.
- `docs/adr/ADR-MONO-022-ecommerce-wms-fulfillment-integration.md` — product-service stock = 주문시점 sellability gate(이 task 가 그 gate 를 reserve 로 시행). WMS 는 이행시점 SoT(무관, 별도).
- `docs/adr/ADR-002-saga-over-distributed-transaction.md` — 보상 트랜잭션(취소 시 재고복구)·event-driven saga 원칙.

## Related Contracts

- `specs/contracts/events/product-events.md` — `StockChanged` reason 유지(`ORDER_RESERVED`/`ORDER_CANCELLED` 의 실제 producer 가 생김) + **신규 `OrderReservationFailed` 이벤트** 추가(payload: `orderId`, `reason="INSUFFICIENT_STOCK"`, `shortages[]{variantId, requested, available}`).
- `specs/contracts/events/order-events.md` — 주문 생애주기에 `BACKORDERED` 명문화(`OrderConfirmed` 가 `BACKORDERED` 에서도 발생 가능).
- `specs/contracts/http/order-api.md` (또는 internal 문서) — 운영자 취소 내부 엔드포인트.

## Edge Cases

- **토픽 순서 역전**: PaymentCompleted 선도착 → 예약 stub(`payment_received=true`, lines 없음) 생성, OrderPlaced 도착 시 lines 채우고 reserve. 반대도 대칭. reserve 는 (lines∧payment∧status=NEW) 에서만 1회.
- **재입고 부분 충족**: 백오더 예약 A(needs 5)·B(needs 3) 대기 중 +6 입고 → FIFO 로 A 먼저 충족(RESERVED), B 는 잔여 1<3 라 BACKORDERED 유지.
- **취소 타이밍**: reserve 직후/재시도 직전 취소 → OrderCancelled 가 예약을 RELEASED 로; 재시도 서비스는 RELEASED/비-BACKORDERED 예약을 건너뜀(취소 주문에 재고 안 묶임).
- **중복 ORDER_RESERVED**: 다라인 주문은 라인 수만큼 StockChanged(ORDER_RESERVED) 발행 → order-service confirm 은 idempotent + eventId dedup 라 1회만 확정.
- **이미 확정/배송된 주문에 늦은 reservation-failed**: markBackordered 는 PENDING 에서만 전이(CONFIRMED/SHIPPED 면 no-op + 경고 로그).

## Failure Scenarios

- **부분 차감 후 중단**: reserve 가 일부 라인만 차감하고 실패하면 재고 불일치 → reserve 는 단일 트랜잭션 내 전 라인 확인-후-차감(all-or-nothing), 실패 시 전체 롤백으로 방지(AC-3·AC-6 가드).
- **재시도 폭주**: 재입고 시 대량 백오더 재예약이 동시 차감 경합 → 낙관락 충돌은 per-예약 트랜잭션 격리 + 다음 트리거에서 재시도(StalePaid/StuckDetector 패턴과 동일 격리).
- **취소 후 재입고 경합**: OrderCancelled 와 restock-retry 가 동시 → 예약 status 를 트랜잭션 내 재확인(BACKORDERED 아닌 것 skip)로 직렬화.
- **payment_received 만 오고 OrderPlaced 영영 안 옴**(비정상): reserve 미발화로 주문 PENDING 잔류 → 기존 StalePaid/StuckDetector 안전망이 포착(회귀 아님).
