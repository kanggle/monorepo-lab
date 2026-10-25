# Task ID

TASK-BE-645

# Title

운영자 주문 상태 변경 API 가 `BACKORDERED → CONFIRMED` 를 받아 준다 — 재고 재예약 없이 확정돼 재입고 사가와 이중이 되거나 재고 없이 출고로 넘어갈 수 있다

# Status

done

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
- [x] **AC-1** — 계약 먼저 → 구현 + 시험 · CI 의 ecommerce 잡 초록.
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

## 구현 (2026-10-22 UTC · Opus)

**고른 수선 — 운영자 서비스에서 막는다 (도메인 `confirm` 은 그대로).** 이유: AC-0 ② 에서 `BACKORDERED → CONFIRMED` 를 정당하게 쓰는 호출자는 `OrderConfirmationService` 하나뿐이고, 잘못 쓰는 호출자도 `AdminOrderStatusService` 하나뿐이다. 도메인을 `confirmAfterPayment`·`confirmAfterReReservation` 으로 나누면 사가 쪽 호출자를 바꿔야 해서(Failure Scenario 1 의 위험면이 넓어진다) «사가 경로는 그대로» 를 코드로 보장하기 어렵다. 운영자 서비스의 한 갈래만 바꾸면 사가 경로는 diff 에 등장하지 않는다. 오류는 기존 어휘 `InvalidOrderException` → `400 INVALID_ORDER_REQUEST`(`GlobalExceptionHandler.java:109-113`) — 이미 `SHIPPED → CONFIRMED` 가 같은 예외·같은 코드로 거부된다(`AdminOrderStatusServiceTest` 기존 시험).

경합(Edge Case 1): 운영자 요청이 `BACKORDERED` 를 읽으면 무조건 400 이고 아무것도 쓰지 않는다 — 재예약 쪽이 이긴다. 재예약이 먼저 커밋됐다면 운영자는 `CONFIRMED` 를 읽고 기존 멱등 no-op(200). 둘이 같은 행을 쓰는 경로는 남지 않는다. Edge Case 2(이미 `CONFIRMED`) = 기존 동작 유지, 시험으로 고정.

**바뀐 파일**
- 계약(먼저, 별도 커밋 `a650ade75`): `specs/contracts/http/order-api.md` — 운영자 상태 변경 절에 «`BACKORDERED` 에서는 `CANCELLED` 만», `BACKORDERED → CONFIRMED` = `400 INVALID_ORDER_REQUEST`, `CONFIRMED → CONFIRMED` 멱등. 상태 값 표에 `BACKORDERED` 행, 오류 표에 이미 있던 동작 `422 ORDER_CANNOT_BE_CANCELLED`, Notes 의 취소 가능 상태를 `isCancellable` 과 일치.
- `apps/order-service/src/main/java/com/example/order/application/service/AdminOrderStatusService.java` — `CONFIRMED` 갈래에서 이전 상태가 `BACKORDERED` 면 `InvalidOrderException`(저장 없음).
- 시험: `AdminOrderStatusServiceTest`(+3: 백오더→확정 거부·저장 없음·상태/`updatedAt` 불변 · 백오더→취소 성공 · 확정→확정 멱등), `OrderConfirmationServiceTest`(+1: 백오더 주문이 사가로 확정되고 `OrderConfirmed` 발행 — 이 경로의 시험이 **없었다**), `AdminOrderControllerSliceTest`(+1: 400 `INVALID_ORDER_REQUEST` · `message` · `timestamp` 형태). 대기→확정(운영자) 기존 시험 그대로 초록.

**시험 결과**
- 로컬: `./gradlew :projects:ecommerce-microservices-platform:apps:order-service:test` rc=0 — XML 63개 · **445 tests · 0 failures · 0 errors · 0 skipped**. 대상 넷: AdminOrderStatusServiceTest 14 · OrderConfirmationServiceTest 4 · AdminOrderControllerSliceTest 9 · OrderTest 42, 전부 통과.
- ⚪ bite(가드를 끄고 새 시험이 빨개지는지) **측정 못 함** — 가드를 임시로 무력화하는 편집이 자동 모드 분류기에 막혔다. 논리 근거만: 가드가 없으면 `Order.confirm` 이 `BACKORDERED` 에서 성공한다는 것을 `OrderTest`(«BACKORDERED 상태에서 confirm 호출 시 CONFIRMED로 전이») 가 이미 증명하므로 `assertThatThrownBy` 가 실패한다.
- ⚪ CI 전용: `integrationTest`(Testcontainers) 는 로컬에서 돌리지 않았다 — 이 경로를 타는 IT 는 없다(`src/test` 에서 `admin/orders/.*/status` 를 부르는 파일은 위 슬라이스 둘 · 서비스 단위 · 데모 시드 시험뿐). CI 의 ecommerce 잡 결과는 PR 후 — AC-1 은 그때 닫는다.

## 머지 기록 (2026-10-22 UTC · 분석=Opus 5.5 · TASK-BE-646 PR 에서 함께 기록)

- impl PR #4474(head `fb1962f41`) 체크 69 · pending 0 · fail 0 → 스쿼시 `5f06aad3b` 머지 → AC-1 닫힘(CI 의 ecommerce 잡 초록).
- 🔴 머지 때 이 티켓을 review 로 옮기는 기록을 빠뜨려 in-progress 에 남아 있었다 — `TASK-BE-646` PR 에서 바로잡는다.
- bite(가드를 꺼서 시험이 빨개지는지)는 자동 모드 분류기가 «보안 시험 제거» 로 막아 ⚪ — 우회하지 않음. 근거는 논리(가드가 없으면 `OrderTest` 가 보여 주듯 백오더에서 `confirm` 이 성공 → 새 시험의 `assertThatThrownBy` 실패).
- 열림: **AC-2**(굽기 뒤 라이브 — 백오더 주문을 운영자 API 로 확정하면 400).

## 라이브 확인 (2026-10-23 UTC · 33차 창)

- 배포 상태: 굽기 `283fd39cf` → AMI `ami-02b3ba00a3b2ff622`(51분 36초 · 태그 RepoCommit 일치) · 핀 PR #4498(`c5e3ddad3`) · 인스턴스 `i-0f39aec49ddbaa899`. impl 머지 `5f06aad3b`(#4474) 는 `283fd39cf` 의 조상.
- **AC-2 ⚪ 측정 못 함 (이유)** — 데모에 `BACKORDERED` 주문이 0 건이다.
- ⇒ 이 티켓은 `review` 에 남는다(체크 칸은 동결 규칙으로 고치지 않음).

---

## CORRECTION — 34차 창 라이브 결과 (2026-10-24 UTC)

- **AC-2 — 34차 창 결과 (열린 채 유지).** 34차 창(2026-10-24 UTC): ⚪ 볼륨에 `BACKORDERED` 주문이 0건이라 잴 대상이 없다.

---

## CORRECTION — 35차 창 라이브 결과 (2026-10-25 UTC)

- **AC-2 ✅.** 35차 창(굽기 `54b79233c`, 신선 볼륨): 구매자 토큰으로 수량 100000 (오버핏 후드 집업) 주문 → 결제 확인 200 → 10 초 안에 `BACKORDERED`.
  - 운영자(ecommerce) `POST /api/admin/orders/{id}/status {"status":"CONFIRMED"}` → **400 `INVALID_ORDER_REQUEST`**, 본문 «An order cannot be confirmed by an operator (current status: BACKORDERED) …».
  - 주문은 `BACKORDERED` 그대로.
- 볼륨 잔여: 주문 `d48c1c34…` 를 `CANCELLED` 로 정리했다(결제 환불은 재지 않았다).

⇒ 판정: 모든 AC 닫힘 — 닫기 chore 대상
