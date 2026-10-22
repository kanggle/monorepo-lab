# Task ID

TASK-BE-645

# Title

운영자 주문 상태 변경 API 가 `BACKORDERED → CONFIRMED` 를 받아 준다 — 재고 재예약 없이 확정돼 재입고 사가와 이중이 되거나 재고 없이 출고로 넘어갈 수 있다

# Status

in-progress

# Owner

backend

# Task Tags

- code
- test
- contract

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus (주문 상태 기계 · 운영자 API · 재입고 사가의 경계)
> 기안 2026-10-22 UTC — `TASK-PC-FE-365` AC-0 ③ 의 곁 발견(소유자 결정 2026-10-22 KST: «이커머스 ready 티켓»). **ready 만 — 착수는 소유자 지시 때.** 서버 변경 ⇒ 배포는 **굽기**.

# Dependency Markers

- 출처: `projects/platform-console/tasks/*/TASK-PC-FE-365-*.md` § AC-0 ③.
- 콘솔은 365 부터 백오더 주문에 «취소» 만 낸다 — 이 티켓은 **API 를 직접 부르는 경로**를 막는다.

# Goal

- 운영자 상태 변경: `apps/order-service/.../application/service/AdminOrderStatusService.java:33-35` — 목표가 `CONFIRMED` 면 `order.confirm(clock)`.
- 도메인: `apps/order-service/.../domain/model/Order.java:213-215` — `confirm` 은 `PENDING` **또는 `BACKORDERED`** 에서 허용한다(주석: «a later replenishment re-reserved the held order — TASK-BE-428»). 이 두 번째 갈래는 **재입고 재예약 사가**를 위한 것이다.
- ⇒ 운영자가 API 로 백오더 주문을 «확정» 하면 재고 예약 없이 `CONFIRMED` 가 된다. 그 뒤 재입고 사가가 같은 주문을 다시 확정하려 하거나(이중), 재고 없는 주문이 배송 흐름으로 넘어간다.
- 계약(`specs/contracts/events/order-events.md:218` 근처)은 백오더에서 운영자가 할 수 있는 것을 «취소» 로 적는다(AC-0 에서 원문 확인).

# Scope

## In Scope

- **AC-0 측정** (file:line): ① 운영자 API 의 계약(`order-api.md` 의 상태 변경 절)이 허용 전이를 어떻게 적는가 ② `confirm` 을 부르는 곳 전수(결제 완료 · 재입고 재예약 · 운영자) ③ 운영자 확정이 지금 재고 예약을 거치는가(거치지 않는다는 것이 이 티켓의 전제 — 틀리면 STOP) ④ 이미 운영자가 백오더를 확정한 데이터가 있을 수 있는가(데모 · 시드).
- 수선(AC-0 뒤): 운영자 경로에서 `BACKORDERED → CONFIRMED` 를 거부(400 · 기존 오류 어휘) — 도메인 `confirm` 을 둘로 나누거나(`confirmAfterPayment` · `confirmAfterReReservation`) 운영자 서비스에서 막는다(AC-0 이 정한 쪽). 재입고 사가 경로는 그대로.
- 계약 먼저: 운영자 상태 변경 절에 «백오더에서는 취소만» 을 명시.
- 시험: 운영자 백오더 → 확정 = 400 · 운영자 백오더 → 취소 = 200 · 재입고 사가의 백오더 → 확정 = 그대로 성공 · 대기 → 확정(운영자) = 그대로.

## Out of Scope

- 콘솔(365 가 이미 취소만 낸다).
- 재입고 사가 자체 변경.

# Acceptance Criteria

- [x] **AC-0** — 위 ①~④. ③ 이 틀리면 STOP. → 아래 `## AC-0 측정` (③ 전제 확인됨 — STOP 아님).
- [ ] **AC-1** — 계약 먼저 → 구현 + 시험 · CI 의 ecommerce 잡 초록.
- [ ] **AC-2** — 굽기 뒤 라이브: 데모에서 백오더 주문을 운영자 API 로 확정하면 400(다음 데모 창 · 백오더 주문이 있을 때).

# Related Specs

- `projects/ecommerce-microservices-platform/specs/services/order-service/` (상태 기계 — AC-0 에서 이름 확인)

# Related Contracts

- `projects/ecommerce-microservices-platform/specs/contracts/http/order-api.md` (운영자 상태 변경 — 갱신 대상)
- `projects/ecommerce-microservices-platform/specs/contracts/events/order-events.md` (`:218` 근처 — 읽기)

# Edge Cases

- 운영자 확정 요청과 재입고 재예약이 거의 동시에 오면 — 재예약 쪽이 이겨야 한다(운영자 요청은 400).
- 이미 `CONFIRMED` 인 주문에 운영자 확정 → 지금 동작 유지(멱등 · 또는 기존 오류).

# Failure Scenarios

1. 도메인 `confirm` 에서 `BACKORDERED` 갈래를 지워 재입고 사가까지 막는다 → 사가 시험이 막는다.
2. 운영자 경로만 막고 계약을 안 고쳐 계약이 거짓 → AC-1.

---

## AC-0 측정 (2026-10-22 UTC)

기준 트리: `origin/main` `02329254b` (worktree `task/be-645-impl`). 경로는 `projects/ecommerce-microservices-platform/` 기준.

**① 계약이 적는 허용 전이** — `specs/contracts/http/order-api.md:404-406` 은 운영자 전이를 `PENDING → CONFIRMED` · `PENDING|CONFIRMED → CANCELLED` 두 줄로만 적는다. `BACKORDERED` 는 이 절에 **한 번도 나오지 않는다** — 확정도 취소도 적혀 있지 않다(같은 파일 `Order Status Values` 표 `:245-251` 에도 `BACKORDERED` 행이 없다). 잘못된 값·전이의 오류는 `:433` `400 INVALID_ORDER_REQUEST`. 이벤트 계약 `specs/contracts/events/order-events.md:216-219` 는 «재예약이 성공하면 `BACKORDERED → CONFIRMED`(정상 `OrderConfirmed`)» · «`BACKORDERED` 주문은 운영자가 **취소**할 수 있다» 로 적는다 — 운영자 확정은 어디에도 없다. ⇒ 계약은 이미 운영자 확정을 허용하지 않는다고 읽히지만 명시가 없다. 코드가 계약보다 넓다.

**② `Order.confirm` 을 부르는 곳 전수** (`src/main` 에서 `\.confirm\(` — 3곳):

| 호출자 | file:line | 들어오는 상태 | 재고 예약 |
|---|---|---|---|
| 재고 예약 사가 (최초 · **재입고 재예약 둘 다**) | `OrderConfirmationService.java:35` ← `StockChangedEventConsumer.java:47-57` (`reason=ORDER_RESERVED`) | `PENDING`(최초) · `BACKORDERED`(재입고) | 예약 **뒤** — product-service `ReservationService.java:150` 이 차감 후 `ORDER_RESERVED` 를 낸다. 재입고는 `ReservationRetryService.java:107-114` → 같은 `tryReserve` |
| 결제 후 확정 유실 복구 (batch) | `StalePaidOrderConfirmHandler.java:70-78` → `OrderConfirmationService.confirmOrder` | `PENDING` 만(`:70` 이 거른다) | (사가와 같은 경로) |
| **운영자** | `AdminOrderStatusService.java:34` | 아무 상태 — 도메인이 `PENDING|BACKORDERED` 만 통과(`Order.java:214`) | **없음** |

결제 완료 소비자(`PaymentCompletedEventConsumer`)는 `confirm` 을 부르지 않는다(결제 → product-service 예약 → `ORDER_RESERVED` → 위 첫 행). ⇒ **재입고 사가는 `OrderConfirmationService` 만 탄다.** 운영자 서비스에서 막으면 사가 경로는 코드상 닿지 않는다.

**③ 운영자 확정이 재고 예약을 거치는가 — 거치지 않는다 (전제 확인, STOP 아님).** `AdminOrderStatusService.java:24-47` 은 `findById` → `order.confirm(clock)` → `save` 가 전부다. 예약 포트·이벤트 발행자가 주입돼 있지 않다(`:21-22` 필드는 `OrderRepository`·`Clock` 뿐). 그래서 백오더 주문을 운영자가 확정하면: (a) 재고 차감 없음 (b) **`OrderConfirmed` 도 안 나간다** — 출고(WMS)·배송이 시작되지 않는 `CONFIRMED` 가 남는다 (c) product-service 예약은 여전히 `BACKORDERED` 라 다음 재입고 때 재고를 차감하고 `ORDER_RESERVED` 를 내지만 order-service 는 `confirm` 이 `false`(이미 CONFIRMED, `Order.java:209-211`) 라 이벤트 없이 끝난다 — 재고는 빠지고 출고는 끝내 안 일어난다.

**④ 이미 운영자가 백오더를 확정한 데이터** — 리포 안에서는 **없다고 판정할 근거만 있다**: 데모 시드 `infra/demo/seed/seed-ecommerce.sh:63,347-359` 는 결제 → 사가 확정만 쓰고 `/api/admin/orders/{id}/status` 를 확정에 쓰지 않으며, 백오더 자체를 시드 실패로 본다(`seed-wms.sh:569`). order-service 마이그레이션·시드에 `BACKORDERED` 를 넣는 SQL 은 0건(`*.sql` 검색, product-service `V16` 주석뿐). ⚪ **라이브 DB 는 잴 수 없었다** — 데모 창 밖이고, 365 이전 콘솔이 백오더에 «확정» 을 냈는지에 따라 기존 행이 있을 수 있다. 조회 쿼리(다음 데모 창): `SELECT order_id, updated_at FROM orders WHERE status='CONFIRMED'` 중 product `stock_reservations.status='BACKORDERED'` 인 주문 — 두 DB 를 맞대야 한다.

**곁 발견 (이 티켓 범위 밖 — 별도 티켓 필요).** 같은 운영자 엔드포인트의 **취소**도 `OrderCancelled` 를 내지 않는다(`AdminOrderStatusService.java:35` · 발행자 없음; `publishOrderCancelled` 를 부르는 곳은 `OperatorOrderCancellationService`(내부 API) 등 5곳이고 이 서비스는 아니다). 이벤트 계약 `order-events.md:218-219` 는 «운영자 취소 → `OrderCancelled` → 환불 fan-out» 이라 적는다. 결과: 콘솔(`console-web/.../orders-api.ts:155` 가 이 엔드포인트를 쓴다)이 백오더 주문을 취소하면 환불이 안 나가고, product 예약이 `BACKORDERED` 로 남아 다음 재입고에서 재고를 차감 → `ORDER_RESERVED` → order-service `confirm` 이 `CANCELLED` 에서 `InvalidOrderException`(`Order.java:214-215`) 으로 소비 실패. 이 티켓은 Scope 대로 «백오더 → 취소 = 200» 을 유지하고 계약도 그 이상을 약속하지 않게만 적는다.
