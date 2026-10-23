# Task ID

TASK-BE-648

# Title

운영자 주문 «확정»(`PENDING → CONFIRMED`)을 없앤다 — 운영자 확정은 `OrderConfirmed` · 재고 예약을 거치지 않아, 나중에 결제가 끝나도 확정 이벤트가 영영 나가지 않고 멈춘 주문 감지에서도 빠진다

# Status

review

# Owner

backend

# Task Tags

- code
- test
- contract

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus (주문 상태 기계 · 계약 충돌 해소)
> 기안 2026-10-22 UTC — `TASK-BE-646` AC-0 ④ 의 판정. 소유자 결정 2026-10-22 KST: **«운영자 확정 없애기 · 티켓»**(굽기 전 구현). 서버 변경 ⇒ 배포는 **굽기**.

# Dependency Markers

- 선행: `TASK-BE-645` · `TASK-BE-646`(같은 파일 `AdminOrderStatusService` — 둘 다 머지됨).
- 짝: `projects/platform-console/tasks/ready/TASK-PC-FE-366-*.md`(콘솔 상태 변경 선택지에서 «확정» 제거 — 콘솔은 먼저 머지돼도 안전하다).

# Goal

- 관리자 상태 변경(`POST /api/admin/orders/{id}/status`)은 지금 `PENDING → CONFIRMED` 를 허용한다(`AdminOrderStatusService.java` 의 `CONFIRMED` 갈래 → `Order.confirm`, BE-645 가 `BACKORDERED` 만 막았다).
- 이 경로는 `OrderConfirmed` 를 내지 않고 재고 예약도 하지 않는다. 계약끼리 충돌한다: `order-events.md:84-89`(확정 시 항상 `OrderConfirmed`) vs `order-api.md:406`(운영자 확정 허용).
- 결과(BE-646 AC-0 ④): 운영자가 결제 전 주문을 확정 → 나중에 결제가 끝나 product 가 재고를 차감해도 `Order.confirm` 이 이미 `CONFIRMED` 라 false → `OrderConfirmed` 가 영영 안 나간다. 결제가 끝내 안 되면 멈춘 주문 감지(대상 = `PENDING` 만)에서도 빠진다.
- 확정은 결제 · 재고 예약 사가(`OrderConfirmationService`)만 한다.

# Scope

## In Scope

- **AC-0 측정** (file:line): ① 운영자 확정을 쓰는 곳 전수(콘솔 · 다른 클라이언트 · 시드 · 시험 — 데모 시드가 운영자 확정에 기대는가) ② 운영자가 «결제는 됐는데 멈춘 주문» 을 손으로 확정할 정당한 필요가 있는가(그 경우는 이미 `StalePaidOrderConfirmHandler` · 멈춘 주문 감지가 맡는가) ③ 거부 응답의 오류 어휘(BE-645 와 같은 400 `INVALID_ORDER_REQUEST`).
- 계약 먼저: `order-api.md` 운영자 상태 변경 — 운영자가 정할 수 있는 상태는 `CANCELLED` 하나(대기 · 확정 · 백오더에서). 두 계약의 충돌 해소를 이력으로 남긴다.
- 수선: 관리자 상태 변경에서 목표 `CONFIRMED` 를 거부(모든 현재 상태). 사가 · `StalePaidOrderConfirmHandler` 경로 불변.
- 시험: 운영자 대기 → 확정 400 · 저장 없음 · 사가의 대기 → 확정 + `OrderConfirmed` 불변 · 운영자 취소 불변(BE-646).

## Out of Scope

- 콘솔(`TASK-PC-FE-366`).

# Acceptance Criteria

- [x] **AC-0** — 위 ①~③. ② 에서 정당한 필요가 있으면 STOP(소유자에게). → 아래 `## AC-0 측정` — 정당한 필요 없음 · 이미 확정된 주문도 400.
- [x] **AC-1** — 계약 먼저 → 구현 + 시험 · CI 의 ecommerce 잡 초록(가능하면 시험만 커밋으로 빨강 먼저).
- [ ] **AC-2** — 굽기 뒤 라이브: 운영자 API 로 대기 주문 확정 → 400(다음 데모 창).

# Related Specs

- `projects/ecommerce-microservices-platform/specs/services/order-service/architecture.md` (상태 기계)

# Related Contracts

- `projects/ecommerce-microservices-platform/specs/contracts/http/order-api.md` (갱신 대상)
- `projects/ecommerce-microservices-platform/specs/contracts/events/order-events.md` (`:84-89` — 읽기)

# Edge Cases

- 이미 `CONFIRMED` 인 주문에 운영자 확정 → 지금은 멱등 200 — 이제 400 으로 바뀌는가(AC-0 이 정함 · 콘솔은 366 부터 안 낸다).

# Failure Scenarios

1. 도메인 `confirm` 을 고쳐 사가 경로까지 막는다 → 사가 시험이 막는다(운영자 서비스에서만 거부).
2. 콘솔이 아직 «확정» 을 내는데 서버만 바뀌어 운영자에게 400 이 보인다 → 366 을 먼저(또는 함께) 머지.

---

## AC-0 측정 (2026-10-22 UTC)

경로는 `projects/ecommerce-microservices-platform/` 기준(콘솔 · 시드는 리포 루트 기준). 기준 트리 = `origin/main` `40d7c6263`.

### ① 운영자 확정을 쓰는 곳 전수

- **서버 갈래** — `apps/order-service/src/main/java/com/example/order/application/service/AdminOrderStatusService.java:37-52`: 목표 `CONFIRMED` → `BACKORDERED` 만 거부(BE-645, `:46-50`) → 나머지는 `order.confirm(clock)` `:51` → `save` `:76`. 발행자(`OrderEventPublisher`)는 주입조차 안 돼 있다(`:23-25`) — 확정 이벤트도 지표도 없다.
- **도메인** — `domain/model/Order.java:208-209`: `confirm` 은 이미 `CONFIRMED` 면 `false`(멱등). 사가 `OrderConfirmationService.java:47-52` 는 이 `false` 를 보고 `OrderConfirmed` 를 **안 낸다** — 그래서 운영자 확정 뒤의 결제 완료는 확정 이벤트를 영영 못 낸다(BE-646 AC-0 ④ 재확인).
- **콘솔** — `projects/platform-console/apps/console-web/src/features/ecommerce-ops/api/order-types.ts:79` `PENDING: ['CONFIRMED', 'CANCELLED']` · `components/OrderStatusDialog.tsx:20`(설명 주석) · `:46`(«확인» 라벨). **유일한 실제 호출자.** 짝 `TASK-PC-FE-366` 이 이 선택지를 뺀다.
- **다른 클라이언트** — `packages/api-client/src/services/admin-order-api.ts:19-20` `changeStatus(orderId, status)` 는 상태를 고르지 않는 범용 래퍼이고, 리포 안 `.ts/.tsx` 호출자 0건(`index.ts:26` 의 재수출뿐). 콘솔 `*.spec.ts` 에 주문 상태 변경 0건.
- **데모 시드 — 기대지 않는다.**
  - `infra/demo/seed/seed-ecommerce.sh:67-70`: 상태 변경 엔드포인트로는 상태를 만들지 않는다고 명시하고, 실제로 **배송**을 움직인다(`:518` `PUT /api/shippings/{id}/status`). `:356` `order_wait_status … CONFIRMED SHIPPED DELIVERED` 는 사가가 만든 확정을 **기다릴** 뿐이다. `/api/admin/orders` 는 목록 읽기(`:699` · `:704` · `:752`)뿐.
  - `apps/order-service/src/test/java/com/example/order/demoseed/EcommerceOrderDemoSeedTest.java:197-225`: 시드가 `"$GW/api/admin/orders/` 를 **부르지 않음**을 이미 핀으로 박아 두었다(`:213`). 그 둘째 단언(`:216-224` — `AdminOrderStatusService` 에 `OrderEventPublisher` 가 없다)은 이 수선 뒤에도 그대로 참이다(거부는 발행자를 들이지 않는다). 설명 문구(«CONFIRMED there is worse than useless») 는 이제 «거부된다» 가 더 정확하지만, 핀이 무는 것은 바뀌지 않으므로 손대지 않았다.
  - `scripts/console-demo/seed/*.sql` 7개 — ecommerce 주문을 건드리는 줄 0건(`06-scm-procurement.sql:22-32` 의 `purchase_orders` 는 SCM 발주 · `03-erp.sql:42` 는 `display_order` 컬럼).
- **시험** — 운영자 확정을 성공으로 기대하는 것: `AdminOrderStatusServiceTest.java:88-101`(대기 → 확정 200) · `:210-222`(확정 뒤 `save`) · `:255-267`(확정 → 확정 멱등 200) · `:344-356`(확정은 `OrderCancelled` 를 안 냄) · `AdminOrderControllerSliceTest.java:123-139`(서비스는 목, 200 형태). 이 다섯을 이 티켓이 바꾼다. `AdminOrderBusinessRoleGateSliceTest.java:69-70` 은 `CONFIRMED` 를 보내지만 서비스가 목이고 «403 이냐 아니냐» 만 재므로(`:102-106`) 무관 — 그대로 둔다. `OrderEventPublishIntegrationTest.java:154-172` 는 `CANCELLED` 만 쓴다.

### ② 운영자가 손으로 확정할 정당한 필요 — **없다(STOP 아님)**

- «결제는 됐는데 멈춘 주문»(`PENDING ∧ payment_id IS NOT NULL`) 은 이미 시스템이 확정한다: `StalePaidOrderConfirmHandler.java:59-80` → `OrderConfirmationService.confirmOrder` — 운영자 확정과 달리 **`OrderConfirmed` 를 낸다**(`specs/contracts/http/order-api.md:470-486` · `internal/order-confirm-paid-stale.md`). 운영자 확정은 이 경로를 대신하지 못하고 오히려 망가뜨린다(확정된 주문은 `:70` 에서 `SKIPPED` — 이벤트 없음).
- «결제가 안 된 채 멈춘 주문»(`payment_id IS NULL`) 은 `application/saga/OrderStuckDetector.java:44` 가 맡는다(`order-api.md:485` — 확정하지 않고 종결). 결제 전 주문을 확정할 업무상 이유는 계약 어디에도 없다.
- 재입고로 풀리는 백오더는 재예약 사가가 확정한다(BE-645).
- 결론: 운영자에게 남는 정당한 상태 변경은 **취소** 하나(BE-646 이 보상까지 붙였다).

### ③ 거부 응답의 오류 어휘

- BE-645 와 같은 `InvalidOrderException` → `presentation/GlobalExceptionHandler.java:109-112` → **400 `INVALID_ORDER_REQUEST`**(메시지 그대로). BE-645 슬라이스 `AdminOrderControllerSliceTest.java:156-173` 이 이미 이 형태를 잰다. 새 오류 코드는 만들지 않는다.

### Edge Case 판정 — 이미 `CONFIRMED` 인 주문에 운영자 확정

- **400 으로 바꾼다**(지금은 `Order.confirm` 멱등 → `save` → 200, `AdminOrderStatusServiceTest.java:255-267`).
- 이유: (a) 계약을 «운영자가 정할 수 있는 상태는 `CANCELLED` 하나» 로 좁히면 목표 `CONFIRMED` 는 **현재 상태와 무관하게** 이 엔드포인트의 어휘 밖이다 — `SHIPPED`·`DELIVERED`·`PENDING` 목표가 현재 상태와 무관하게 400 인 것과 같은 규칙. (b) 200 을 남기면 «운영자 확정» 이 계약에 한 갈래 살아 있어, 다른 클라이언트가 그것을 근거로 확정을 다시 내기 쉽다. (c) 잃는 것이 없다 — 상태를 안 바꾸는 요청이었고, 콘솔은 `TASK-PC-FE-366` 부터 확정을 안 낸다.
- 거부는 `findById` **뒤** 에 한다: 없는 주문은 지금처럼 404(`AdminOrderStatusServiceTest.java:159-168`), 있는 주문은 상태와 무관하게 400 · 저장 없음.

---

## 구현 (2026-10-22 UTC · Opus)

브랜치 `task/be-648-impl`(기준 `origin/main` `40d7c6263`). 커밋 순서:

1. `5e71a5ae9` — 착수(ready → in-progress · INDEX 행 이동 · AC-0 측정).
2. `50a662f32` — **시험만**. `AdminOrderStatusServiceTest`: 목표 `CONFIRMED` 를 `OrderStatus` 전체(7개)에서 거부 · 상태/`updatedAt` 그대로 · `save` 없음 · 발행/지표/`clock` 상호작용 없음(파라미터화) · `PENDING` 거부 뒤 `PENDING` 유지 · `CONFIRMED → CONFIRMED` 도 400 · 거부가 `OrderCancelled` 로 새지 않음. 확정을 쓰던 `save` 검증은 취소로 바꿨다. BE-646 운영자 취소 시험은 그대로. `AdminOrderControllerSliceTest`: 200 형태 시험은 `CANCELLED` 로 · `PENDING → CONFIRMED` 400 `INVALID_ORDER_REQUEST`(code · message · timestamp) 추가. `OrderConfirmationServiceTest`: 사가의 `PENDING → CONFIRMED` + `OrderConfirmed` 시험은 단언 불변(표시 이름만 연결).
   - **현재 코드에서 빨강 실측**: `./gradlew :projects:ecommerce-microservices-platform:apps:order-service:test` → `rc=1`, `462 tests completed, 9 failed` — 실패 9개가 전부 이 커밋의 신규/개정 시험(파라미터화 6개: `PENDING` · `CONFIRMED` · `SHIPPED` · `DELIVERED` · `CANCELLED` · `STUCK_RECOVERY_FAILED` → `CONFIRMED`, + `PENDING` 유지 · `OrderCancelled` 무발행 · `CONFIRMED → CONFIRMED` 400). `BACKORDERED → CONFIRMED` 는 BE-645 가 이미 막아 처음부터 초록.
3. `ee066be17` — 계약 `specs/contracts/http/order-api.md` § `POST /api/admin/orders/{orderId}/status`: 운영자가 정할 수 있는 상태 = `CANCELLED` 하나(대기 · 확정 · 백오더에서) · 목표 `CONFIRMED` 는 모든 현재 상태에서 400 · `order-events.md:84-89` 와의 충돌 해소를 **History** 블록으로 · 예시를 `CANCELLED` 로 · `Order Status Values` 의 `CONFIRMED` 설명에서 «admin» 제거.
4. (이 커밋) — 수선 `AdminOrderStatusService.java` 만: `CONFIRMED` 갈래가 현재 상태와 무관하게 `InvalidOrderException`(→ 400 `INVALID_ORDER_REQUEST`, 메시지에 현재 상태). 모든 갈래가 던지거나 돌려주게 되어 switch 뒤의 `save` 꼬리를 지웠다. 도메인 `Order.confirm` · `OrderConfirmationService` · `StalePaidOrderConfirmHandler` 불변.
   - **초록 실측**: 같은 명령 `rc=0`, JUnit XML 합계 `tests=462 failures=0 errors=0 skipped=0`(`AdminOrderStatusServiceTest` 27 · `AdminOrderControllerSliceTest` 10 · `OrderConfirmationServiceTest` 6 · `EcommerceOrderDemoSeedTest` 9 — 전부 0 실패). Testcontainers IT 는 로컬 Docker 없음 → CI 몫(AC-1).

남은 것: `Clock` 필드는 이제 이 서비스에서 쓰이지 않지만 생성자 시그니처 · 시험 배선을 바꾸지 않으려고 남겼다. `EcommerceOrderDemoSeedTest.java:216-224` 의 설명 문구는 여전히 참인 핀이라 손대지 않았다(AC-0 ①). 콘솔 선택지 제거 = `TASK-PC-FE-366`(Failure Scenario 2 — 366 을 먼저 또는 함께 머지).

## CI 빨강 → 초록 증거 · 머지 전 확인 (2026-10-22 UTC · 분석=Opus 5.5)

- 메인 세션이 **시험만 담은 커밋 `50a662f32`** 까지 올려 PR #4485(draft) CI run `39850742272` 을 봤다 — `order-service:test` 462 중 **9 failed**, 정확히 새 시험들(목표 `CONFIRMED` 파라미터 6 · 대기 확정 거부 · 확정→확정 400 · 거부가 취소로 새지 않음). ⇒ 결함 실재 · 시험이 문다.
- 계약 `ee066be17` · 수선 `5485efe34` 를 앞으로 푸시 → head `5485efe34` 체크 pending 0 · fail 0 → AC-1 닫힘.
- 콘솔 짝 `TASK-PC-FE-366`(#4484 `c8b7262d8`)을 **먼저** 머지 — 콘솔은 더 이상 «확정» 을 내지 않는다.
- 열림: **AC-2**(굽기 뒤 라이브 — 운영자 API 로 대기 주문 확정 → 400).
