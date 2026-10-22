# Task ID

TASK-BE-646

# Title

관리자 주문 상태 변경의 «취소» 가 `OrderCancelled` 를 내지 않는다 — 콘솔에서 취소한 주문은 환불 · 재고 해제가 일어나지 않고, 백오더 주문은 다음 재입고 때 재고가 차감된 뒤 소비 실패한다

# Status

in-progress

# Owner

backend

# Task Tags

- code
- test
- contract

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus (주문 상태 기계 · 보상 이벤트 · 환불 fan-out)
> 기안 2026-10-22 UTC — `TASK-BE-645` 구현 중 곁 발견. 소유자 결정 2026-10-22 KST: **«티켓 + 굽기 전 구현»** (돈 · 재고가 걸린 결함이라 다음 굽기에 싣는다). 서버 변경 ⇒ 배포는 **굽기**.

# Dependency Markers

- 🔴 **선행: `TASK-BE-645`** (같은 파일 `AdminOrderStatusService` 를 고친다 — 645 머지 뒤 착수).
- 관련: `TASK-PC-FE-365`(콘솔이 백오더 주문에도 «취소» 를 열었다 — 이 결함의 범위가 넓어졌다).

# Goal

- 콘솔은 주문 취소를 `POST /api/admin/orders/{id}/status` `{status: CANCELLED}` 로 한다(`console-web/.../ecommerce-ops/api/orders-api.ts:155-165`).
- 그 엔드포인트의 서비스 `apps/order-service/.../application/service/AdminOrderStatusService.java:35` 는 `order.cancel(clock)` → 저장뿐이다 — **이벤트 발행자가 없다**.
- `OrderCancelled` 를 내는 운영자 취소 서비스는 따로 있다: `OperatorOrderCancellationService.java:54-61`(지표 `operator` + `publishOrderCancelled`) — 그러나 **내부 엔드포인트** `InternalOrderController.java:105` `POST /api/internal/orders/{id}/cancel` 에만 붙어 있다.
- 계약 `specs/contracts/events/order-events.md:218-219`: «A `BACKORDERED` order may be cancelled by an operator (`OrderCancelled` → refund fan-out)».
- 결과(코드 읽기 — AC-0 이 확인):
  - `CONFIRMED`(재고 예약 · 결제 완료) 주문을 콘솔에서 취소 → 환불 없음 · 예약 재고 해제 없음.
  - `BACKORDERED` 주문을 콘솔에서 취소 → product 쪽 예약이 남아, 다음 재입고 때 재고 차감 + `ORDER_RESERVED` → order-service 가 `CANCELLED` 주문에 `confirm` 을 시도하다 소비 실패.
  - 곁: 운영자의 `PENDING → CONFIRMED` 도 `OrderConfirmed` 를 내지 않는다(`TASK-BE-645` 보고) — 이 티켓에서 함께 판정(AC-0 ④).

# Scope

## In Scope

- **AC-0 측정** (file:line): ① 콘솔 · 다른 소비자가 관리자 상태 변경 «취소» 를 쓰는 경로 전수 ② `OperatorOrderCancellationService` 와 `OrderCancellationService`(사용자 취소)의 차이 — 결제 상태별 환불 · 재고 해제 · 지표 · 멱등 ③ 운영 데이터에 이미 이벤트 없이 취소된 주문이 있을 수 있는가(데모 · 시드 — 보정 필요 여부) ④ 운영자 `PENDING → CONFIRMED` 가 `OrderConfirmed` 를 내지 않는 것이 결함인가(결제 전 대기 주문을 운영자가 확정하는 것의 의미 · 계약) — 결함이면 이 티켓에서 고칠지 소유자에게.
- 계약 먼저: `order-api.md` 운영자 상태 변경 절에 «취소 = `OrderCancelled`(사유 operator) 발행 · 환불 fan-out» 명시.
- 수선: 관리자 상태 변경의 `CANCELLED` 갈래가 `OperatorOrderCancellationService` 를 타게(같은 트랜잭션 · 같은 이벤트 · 같은 지표). 응답 모양 · 오류 어휘는 그대로.
- 시험: 관리자 취소(대기 · 확정 · 백오더) → `OrderCancelled` 1회 · 같은 요청 두 번 → 1회(멱등) · 이미 취소된 주문 → 기존 동작 · 내부 엔드포인트 동작 불변 · 백오더 취소 뒤 재입고 `ORDER_RESERVED` 가 와도 소비가 실패하지 않는가(product 쪽 예약 해제 경로 — AC-0 ② 가 정한 범위).

## Out of Scope

- 콘솔 변경(엔드포인트 그대로).
- 환불 서비스 자체.

# Acceptance Criteria

- [x] **AC-0** — 위 ①~④. ④ 의 범위는 소유자 결정. → 아래 `## AC-0 측정` (④ = 결함, 고치지 않고 `### 소유자 결정 필요` 로 넘김 · ③ 라이브 DB 는 ⚪ 다음 데모 창).
- [ ] **AC-1** — 계약 먼저 → 구현 + 시험 · CI 의 ecommerce 잡 초록.
- [ ] **AC-2** — 굽기 뒤 라이브: 데모에서 콘솔로 주문 하나 취소 → `OrderCancelled` · 환불 · 재고 해제 확인(다음 데모 창).

# Related Specs

- `projects/ecommerce-microservices-platform/specs/services/order-service/` (상태 기계 · 보상)

# Related Contracts

- `projects/ecommerce-microservices-platform/specs/contracts/http/order-api.md` (갱신 대상)
- `projects/ecommerce-microservices-platform/specs/contracts/events/order-events.md` (`:211-220` — 읽기)

# Edge Cases

- 결제 전 `PENDING` 취소 — 환불 대상 없음(이벤트는 내되 소비자가 무시하는가 — AC-0 ②).
- 동시 취소(콘솔 + 사용자) — 한 번만 발행.

# Failure Scenarios

1. 이벤트만 더하고 재고 해제 · 환불 소비자가 운영자 사유를 모른다 → AC-0 ② · 시험.
2. 645 와 같은 파일을 병렬로 고쳐 충돌 · 회귀 → 645 머지 뒤 착수.

---

## AC-0 측정 (2026-10-22 UTC)

기준 트리: `origin/main` `52c0c2a03` (`TASK-BE-645` 머지 `5f06aad3b` 포함, worktree `task/be-646-impl`). 경로는 `projects/ecommerce-microservices-platform/` 기준(콘솔은 `projects/platform-console/`).

**① 관리자 상태 변경 «취소» 를 쓰는 경로 전수.**

| 호출자 | file:line | 취소를 내는가 |
|---|---|---|
| 콘솔 주문 상태 대화상자 | `platform-console/.../ecommerce-ops/api/order-types.ts:79-86` (`PENDING → {CONFIRMED, CANCELLED}` · `BACKORDERED → {CANCELLED}` · `CONFIRMED → {CANCELLED}`) → BFF `app/api/ecommerce/orders/[id]/status/route.ts:46` → `orders-api.ts:155-171` `POST {ECOMMERCE_ADMIN_BASE_URL}/orders/{id}/status` | **예 — 세 상태 모두** |
| ecommerce 프런트 공용 클라이언트 | `packages/api-client/src/services/admin-order-api.ts:19-20` `changeStatus` | 정의 · `index.ts:26` 재수출뿐 — 리포 안 호출자 0건(`createAdminOrderApi` 검색) |
| 데모 시드 | `infra/demo/seed/seed-ecommerce.sh:324-331` 는 **소유자 취소** `POST /api/orders/{id}/cancel` 을 쓴다 | 아니오 — 이 엔드포인트 호출 0건, `EcommerceOrderDemoSeedTest.java:213` 이 고정 |
| 서버 내부 | `AdminOrderController.java:95-104` → `AdminOrderStatusService.java:51` `case CANCELLED -> order.cancel(clock)` → `:60` `save` | 발행자 · 지표 포트가 **주입돼 있지 않다**(`:22-23` 필드 = `OrderRepository` · `Clock` 뿐) |

이벤트를 내는 운영자 취소(`OperatorOrderCancellationService.java:46-66`)는 `InternalOrderController.java:105-112` `POST /api/internal/orders/{id}/cancel` 에만 붙어 있고, 그 내부 엔드포인트의 리포 안 호출자는 0건이다(`internal/orders/…/cancel` 검색 — 요청 DTO 주석뿐). ⇒ **실제로 운영자가 쓰는 유일한 취소 경로가 이벤트 없는 쪽이다.**

**② `OperatorOrderCancellationService` vs `OrderCancellationService`(사용자 취소).**

| | 운영자(`OperatorOrderCancellationService`) | 사용자(`OrderCancellationService`) | 관리자 상태 변경(현행) |
|---|---|---|---|
| 조회 | `findByIdAcrossTenants` `:48` (테넌트 무관 — 시스템 경로) | `findById` `:31`(테넌트 범위) + 소유자 확인 `:34-36` | `findById` `:29`(테넌트 범위 — `OrderRepositoryImpl.java:124-128`) |
| 도메인 | `order.cancel(OPERATOR)` `:52` | `order.cancel(OPERATOR)` `:39` | `order.cancel(clock)` = `OPERATOR` (`Order.java:278-280`) |
| 지표 | `recordOrderCancelled("operator")` + 상태 전이 `:54-55` | `("user")` + 상태 전이 `:41-42` | **없음** |
| 이벤트 | `OrderCancelled(cancelReason=OPERATOR)` `:61-63` | 같음 `:46-48` | **없음** |
| 반복 요청 | 두 번째 호출은 `CANCELLED` 에서 `Order.cancel` 이 `OrderCannotBeCancelledException`(`Order.java:291-293`) → `422 ORDER_CANNOT_BE_CANCELLED`, 이벤트 0 | 같음 | 같음 |
| 동시 취소 | `OrderJpaEntity.java:71` `@Version` + outbox 같은 트랜잭션(`SpringOrderEventPublisher.java:58-59`) — 진 쪽은 outbox 행째 롤백 ⇒ **발행 1회** | 같음 | (발행 자체가 없음) |

두 서비스가 내는 `OrderCancelled` 는 **바이트 단위로 같은 모양**이다(`OrderCancelledEvent.of(orderId, userId, updatedAt, OPERATOR, clock)`). 소비자는 `cancelReason` 으로 갈라지지 않는다 — 운영자 사유를 «몰라서» 놓치는 소비자는 없다(Failure Scenario 1 해소).

소비자 행렬 (`order.order.cancelled`, src/main 전수 — 세 서비스):

| 주문 상태 | payment-service `PaymentRefundService.java:72-91` | product-service `ReservationService.java:160-180` (← `OrderCancelledReservationConsumer.java:35,51-52`) | promotion-service `OrderCancelledEventConsumer.java:45-47` |
|---|---|---|---|
| `PENDING`, 결제 전 | 결제 없음 → no-op `:74-76` / 결제 `PENDING` → **void** `:83-85`(늦은 승인이 못 잡게) | 예약 `NEW`(또는 결제-선행 스텁) → 재고 변화 없이 `RELEASED` `:172-177` | 쿠폰 복원 |
| `CONFIRMED` | `COMPLETED`/`PARTIALLY_REFUNDED` → **환불** `:80-82` | `RESERVED` → 줄마다 **재고 복원** + `StockChanged(ORDER_CANCELLED)` `:172-176` | 쿠폰 복원 |
| `BACKORDERED` | (백오더는 결제 뒤에만 생긴다) `COMPLETED` → **환불** | `BACKORDERED` → 재고 변화 없이 `RELEASED` `:172-177`; 재입고 재시도는 `findBackorderedHoldingVariant`(`ReservationRetryService.java:69`)와 트랜잭션 안 재확인 `:111` 로 **풀린 예약을 건너뛴다** ⇒ 재입고가 취소 주문에 붙지 않는다 | 쿠폰 복원 |
| 반복 이벤트 | 종료 상태 → no-op `:86-89` | `isReleased()` → no-op `:168-170` + envelope `event_id` 디듀프 | (서비스 쪽 멱등) |

⇒ 현행 관리자 취소의 결과(코드 읽기): `CONFIRMED` — 결제 `COMPLETED` 그대로(환불 없음) · 예약 `RESERVED` 그대로(재고 미복원) · 쿠폰 사용 상태. `BACKORDERED` — 예약이 `BACKORDERED` 로 남아 다음 재입고에서 `tryReserve` 가 재고를 차감하고 `StockChanged(ORDER_RESERVED)` 를 낸다(`ReservationService.java:133-137,142-151`) → order-service `StockChangedEventConsumer.java:47-57` → `OrderConfirmationService.java:35` `Order.confirm` 이 `CANCELLED` 에서 `InvalidOrderException`(`Order.java:214-215`) → **소비 실패(재시도 → DLT)**, 차감된 재고는 영영 안 돌아온다. `PENDING` — 결제가 void 되지 않아, 사용자가 나중에 결제를 끝내면 돈이 잡히고 order-service 는 `markPaymentCompleted` 에서 거부(`Order.java:304-305`), 예약은 그때 `RESERVED` 로 가서 위와 같은 소비 실패.

**범위 판정(시험 «백오더 취소 뒤 재입고 `ORDER_RESERVED`»).** 수선 뒤 정상 순서에서는 product 쪽 해제가 재입고를 막으므로 `ORDER_RESERVED` 자체가 오지 않는다(위 행렬 — 이 사슬은 product-service 시험으로 고정한다). 그러나 **경합 창이 하나 남는다**: 재입고 재예약이 `OrderCancelled` 소비보다 먼저 커밋되면 product 는 이미 `RESERVED` 로 차감 · `ORDER_RESERVED` 발행 → 뒤이어 `OrderCancelled` 를 소비해 **재고를 복원한다**(`:172-176`, 재고는 안전). 이때 order-service 쪽 `ORDER_RESERVED` 소비만 `CANCELLED` 에서 던져 실패한다 — 이것은 운영자 경로만의 것이 아니고 사용자 취소 · 결제 시한 취소에도 같은 창이 있다(기존). `CANCELLED` 주문은 다시 확정될 수 없고, 재고는 product 가 `OrderCancelled` 소비로 되돌리므로, **`OrderConfirmationService` 가 `CANCELLED` 주문의 `ORDER_RESERVED` 를 경고 로그 + no-op 으로 끝내는 것**이 안전한 쪽이다(재시도 · DLT 소음만 없어진다). 도메인 `Order.confirm` 은 건드리지 않는다 — 관리자 `CANCELLED → CONFIRMED` 는 그대로 `400` 이어야 한다. ⇒ 이 티켓 범위에 넣는다.

**곁 발견 (범위 밖 — 기록만).** ⓐ 이벤트 계약 `specs/contracts/events/order-events.md:132` 는 `OrderCancelled` 소비자를 «payment-service, promotion-service» 로만 적는다 — **product-service(예약 해제)가 빠져 있다**. 계약 커밋에서 소비자 목록만 바로잡는다(동작 변경 아님). ⓑ shipping-service 는 `order.order.cancelled` 를 소비하지 않는다(src/main 검색 0건) — `CONFIRMED` 뒤 이미 `OrderConfirmed` → 이행 요청이 나간 주문을 어느 경로로 취소하든(사용자 취소 포함) 출고 쪽은 모른다. 기존 결함이고 이 티켓의 수선과 무관 — 별도 판단 필요.

**③ 이벤트 없이 이미 취소된 주문이 있을 수 있는가.** 리포 안 시드로는 **없다**: 데모 시드는 소유자 취소만 쓰고(`seed-ecommerce.sh:324-331`), 관리자 상태 엔드포인트 호출은 0건이며 시험이 고정한다(`EcommerceOrderDemoSeedTest.java:213`). order-service 마이그레이션 · 시드 SQL 에 `CANCELLED` 0건(`src/main/resources` 검색). ⚪ **라이브 DB 는 잴 수 없었다** — 데모 창 밖이고, 콘솔은 이 결함이 있는 내내 세 상태에서 «취소» 를 냈으므로(①) 기존 행이 있을 수 있다. 다음 데모 창 조회: order DB `orders.status='CANCELLED'` 인 주문 중 ⓐ payment DB `payments.status='COMPLETED'` (환불 안 됨) ⓑ product DB `stock_reservations.status IN ('NEW','RESERVED','BACKORDERED')` (해제 안 됨) — 세 DB 를 맞대야 한다. 🔴 **보정 경로가 API 에 없다**: 이미 `CANCELLED` 인 주문에 내부 취소를 다시 부르면 `422`(②의 반복 요청 행) — 발견되면 보정 방법(수동 환불 · 예약 해제)은 소유자 결정.

**④ 운영자 `PENDING → CONFIRMED` 가 `OrderConfirmed` 를 내지 않는 것은 결함인가 — 결함이다(계약 위반 + 되돌릴 수 없는 결과). 이 티켓에서는 고치지 않는다.**
- 계약: `order-events.md:84-89` «Published when an order is confirmed (status transitions `PENDING → CONFIRMED` …)» — 조건 없는 문장이다. 그런데 `order-api.md:406` 은 운영자 `PENDING → CONFIRMED` 를 허용 전이로 적는다. 두 계약을 동시에 지키는 구현은 지금 없다: `AdminOrderStatusService.java:49` 는 `order.confirm` → 저장뿐이다.
- 결과(코드 읽기): ⓐ `OrderConfirmed` 가 없으니 출고 · 배송이 시작되지 않는다. ⓑ 재고 예약을 거치지 않는다(예약은 product 가 `OrderPlaced` + `PaymentCompleted` 를 모아서 한다 — `ReservationService.java:59-91`). ⓒ **나중에 결제가 끝나면** product 가 차감하고 `ORDER_RESERVED` 를 내지만, order-service `Order.confirm` 은 이미 `CONFIRMED` 라 `false`(`Order.java:209-210`) → `OrderConfirmationService.java:37` 이 발행을 건너뛴다 ⇒ **재고는 빠지고 `OrderConfirmed` 는 영영 안 나간다**(645 의 백오더 확정과 같은 모양). ⓓ 결제가 끝내 안 되면 결제 대기 주문을 치우는 stuck-detector(`PENDING` 만 쓸어 담는다)에서도 빠져, 돈 없는 `CONFIRMED` 가 남는다. ⓔ 이 동작은 이미 시험이 사실로 고정해 두었다(`EcommerceOrderDemoSeedTest.java:215-224` «changeStatus(CONFIRMED) … publishes NOTHING»).

### 소유자 결정 필요

운영자 `PENDING → CONFIRMED` 를 어떻게 할지 — 이 티켓은 손대지 않는다.
- **A (권장) — 운영자 확정을 없앤다.** `order-api.md` 허용 전이에서 `PENDING → CONFIRMED` 를 지우고 서버는 `400 INVALID_ORDER_REQUEST`(645 와 같은 어휘), 콘솔 전이표 `order-types.ts:79` 에서 `CONFIRMED` 를 뺀다. 확정은 결제 → 예약 사가만 한다. 서버 + 콘솔 두 티켓, 둘 다 작다.
- **B — 운영자 확정을 사가 경로로 보낸다.** 결제 완료(`payment_id` 있음)인 `PENDING` 만 허용하고 `OrderConfirmationService` 를 탄다. 그러나 확정의 전제인 재고 예약은 product 가 하므로, 예약 없이 `OrderConfirmed` 를 내면 재고 없는 출고가 된다 — 사실상 «예약 재시도 요청» 이라는 새 경로가 필요하다(계약 · 설계 결정).
- **C — 그대로 두고 계약에 «운영자 확정은 이벤트를 내지 않는다» 를 적는다.** ⓒ 의 재고 누수가 남는다.
