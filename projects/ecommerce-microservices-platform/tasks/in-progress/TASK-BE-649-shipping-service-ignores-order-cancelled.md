# Task ID

TASK-BE-649

# Title

shipping-service 가 `order.order.cancelled` 를 소비하지 않는다 — 출고 요청이 이미 나간 확정 주문을 취소해도 배송 · 출고 쪽은 모른다

# Status

in-progress

# Owner

backend

# Task Tags

- code
- test
- contract

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus (주문 · 배송 · WMS 출고에 걸친 취소 흐름 설계)
> 기안 2026-10-22 UTC — `TASK-BE-646` AC-0 곁 발견 (b). 소유자 결정 2026-10-22 KST: **«ready 티켓만»** — 이번 굽기에 싣지 않는다. 착수는 소유자 지시 때.

# Dependency Markers

- 출처: `projects/ecommerce-microservices-platform/tasks/review/TASK-BE-646-*.md` § AC-0 곁 발견 (b).
- 관련: WMS 출고 취소(`projects/wms-platform` outbound-service `CANCELLED` · 사가 `CANCELLATION_REQUESTED`) — 이커머스 → WMS 출고 연동(ADR-MONO-022 계열)이 있는 범위에서.

# Goal

- `OrderCancelled` 의 소비자는 payment · product · promotion 이다(`order-events.md` 소비자 목록 — BE-646 이 product 를 보탬). **shipping-service 는 없다**.
- 확정(`CONFIRMED`) 주문은 배송 생성 · (설정에 따라) WMS 출고 요청까지 갈 수 있다. 그 뒤 운영자 · 사용자가 취소하면 환불 · 재고 해제는 되지만(BE-646) 배송 · 출고는 계속 진행될 수 있다.

# Scope

## In Scope

- **AC-0 측정** (file:line): ① 확정 뒤 배송 · 출고 요청이 만들어지는 시점과 경로 ② 배송 상태별(준비중 · 발송 · 배송중 · 배송완료) 취소 가능성 · WMS 출고 상태별 취소 가능성(정상 흐름 · 곁가지) ③ 지금 «출고 뒤 취소» 가 실제로 일어날 수 있는가(주문 취소 가능 상태 `isCancellable` = 대기 · 확정 · 백오더 — 배송 시작 뒤에는?) ④ 계약 · ADR 이 출고 취소를 어떻게 적는가.
- **설계 결정 (AC-0 뒤 소유자 선택창)**: 배송이 시작되기 전까지만 주문 취소 허용 vs 배송 · 출고 취소 보상 흐름 신설 — ADR 이 필요한지 판정.
- 구현은 결정 뒤.

## Out of Scope

- 환불 · 재고 해제(BE-646 에서 됨).

# Acceptance Criteria

- [x] **AC-0** — 위 ①~④. 측정 2026-10-23 UTC — 아래 § AC-0 측정 결과.
- [x] **AC-1** — 소유자 결정 · 필요하면 ADR 먼저. ✅ 2026-10-23 UTC **ADR-MONO-022 D4 v2(d) ACCEPTED — X-A / Y-A** (소유자 선택창, 정확형). 구현 순서: 계약 → WMS 티켓 → 이 티켓 → 콘솔 «취소됨» 배송 라벨 티켓. 🔵 소유자 결정 2026-10-23 KST: **«아직 출고 전인 배송만 취소»**. 🔴 **그러나 이 결정만으로는 닫히지 않는다** — AC-0 ①·④ 가 보이듯 «출고 전» 배송은 기본 설정에서 **이미 WMS 출고 주문이 나가 있고**, 그것을 취소할 이커머스 → WMS 경로(이벤트 · 계약)가 없다. 결정을 이행하려면 새 프로젝트 간 이벤트 + ADR-MONO-022 개정이 먼저다(§ 착수 중단). 소유자 선택 대기. 🟡 2026-10-23 UTC 소유자 선택 «ADR 초안 · 굽기는 안 기다림» → ADR-MONO-022 **D4 v2(d) PROPOSED** 초안(선택지 X: WMS 자동 취소 깊이 · Y: 주문 · 환불 시점). 승인은 정확형 `ADR-MONO-022 D4 v2(d) ACCEPTED — X-? / Y-?` 만. 이번 굽기에 싣지 않는다.
- [ ] **AC-2** — 구현 + 시험 · CI 초록. 🟡 2026-10-23 UTC 구현 · 시험 커밋됨(계약 `08baf0e55` → 시험 `b45296e0f` → 구현). 로컬에서 돈 것 = 단위 시험만(아래 § 로컬에서 돈 것). **Testcontainers 통합 시험은 로컬에서 돌지 않았다**(Docker 없음) — CI `ecommerce-integration-tests` 가 판정한다. CI 초록 전이라 닫지 않는다.
- [ ] **AC-3** — 굽기 뒤 라이브.

## ACCEPTED 뒤 범위 (2026-10-23 UTC — ADR-MONO-022 D4 v2(d) · X-A / Y-A)

- **선행**: WMS `TASK-BE-650` 이 양쪽 계약(`fulfillment-events.md` 의 `ecommerce.fulfillment.cancel-requested.v1` · WMS 구독 파일)과 소비자를 먼저 놓는다.
- **이 티켓**:
  - 계약: `order-events.md` 의 `OrderCancelled` 소비자 목록에 shipping-service 를 넣는다. `shipping-events.md` 와 shipping-service 스펙의 상태 기계에 끝 상태 `CANCELLED`(`PREPARING` 에서만 갈 수 있음)를 넣는다. `ShippingStatusChanged(CANCELLED)` 를 내면 order-service 소비자가 그것을 무시하는지 확인한다.
  - 구현: shipping-service 가 `order.order.cancelled` 를 소비한다. `PREPARING` 이면 `CANCELLED` 로 보내고, `wmsRouted` 면 같은 트랜잭션에서 아웃박스로 취소 요청을 쓴다. `SHIPPED` 이후 · 모르는 주문 · 재전달은 no-op 이다.
  - 경보 억제: shipping-service `WmsOutboundCancelledConsumer` 는 배송이 이미 `CANCELLED` 면 경보를 내지 않는다(자기가 요청한 취소의 메아리).
  - 시험: 위 각 경우와 경보 억제. 시험만 담은 커밋으로 CI 빨강을 먼저 본다.
- **Y-A**: 주문 취소 · 환불은 지금처럼 즉시 끝난다. 새 주문 상태는 없다. WMS 가 거부하면 경보만 남는다.
- 콘솔 라벨 = `TASK-PC-FE-367`.

## AC-0 측정 결과 (2026-10-23 UTC)

**① 확정 뒤 배송 · 출고 요청이 만들어지는 시점과 경로.** `OrderConfirmedEventConsumer` (`apps/shipping-service/.../infrastructure/event/OrderConfirmedEventConsumer.java:30-33`, 토픽 `order.order.confirmed`, 그룹 `shipping-service`) 가 한 트랜잭션 안에서 (a) `Shipping` 을 `PREPARING` 으로 만들고(`:67` → `ShippingCommandService.java:28-38`, `Shipping.java:32`), (b) `fulfillment.enabled` 이면 **곧바로** `ecommerce.fulfillment.requested.v1` 을 아웃박스에 쓰고(`:72`, `:87-110`) (c) `wmsRouted=true` 를 찍는다(`:73`). `fulfillment.enabled` 기본값은 **`true`** (`apps/shipping-service/src/main/resources/application.yml:72`), `require-sku-mapping` 기본값 `false`(`:74`) 라 매핑 없는 SKU 도 그대로 통과한다(`FulfillmentAcl.java:82-87`). ⇒ **기본 설정에서 모든 확정 주문은 배송 생성과 같은 순간에 WMS 출고 요청이 나간다.** WMS 쪽은 `FulfillmentRequestedConsumer` → `ReceiveOrderUseCase` 로 출고 주문 + 사가를 만든다(`projects/wms-platform/specs/contracts/events/ecommerce-fulfillment-subscriptions.md:27`).

**② 상태 기계 · «출고 전» 판정.** 이커머스 `ShippingStatus` = `PREPARING → SHIPPED → IN_TRANSIT → DELIVERED`, 취소 상태 없음(`ShippingStatus.java:8-17`). «출고 전» = `PREPARING` 하나. 그런데 이커머스 `PREPARING` 은 WMS 쪽 물리 진행(`RECEIVED`/`PICKING`/`PICKED`/`PACKING`/`PACKED`)을 **전부** 덮는다 — `SHIPPED` 로 넘어가는 것은 WMS 출하 확정(`wms.outbound.shipping.confirmed.v1` → `ShippingCommandService.java:69-88`) 이나 운영자 수동 전이 때다. WMS 출고 취소 가능성: `CancelOrderService` (`projects/wms-platform/apps/outbound-service/.../application/service/CancelOrderService.java:33-40, 90-95, 106`) — `RECEIVED`/`PICKING` = `ROLE_OUTBOUND_WRITE`, `PICKED`/`PACKING`/`PACKED` = **`ROLE_OUTBOUND_ADMIN` 만**, `SHIPPED` = 금지. 예약을 잡고 있으면 `outbound.picking.cancelled` 보상으로 재고 해제(`:57-58, :137-`).

**③ «출고 뒤 취소» 가 일어날 수 있는가.** 주문 `isCancellable` = `PENDING`/`CONFIRMED`/`BACKORDERED` (`apps/order-service/.../domain/model/OrderStatus.java:12-14`, 가드 `Order.java:290-294`). 주문이 `SHIPPED` 가 되는 것은 `ShippingStatusChanged(SHIPPED)` 소비 때(`order-service/.../ShippingStatusChangedEventConsumer.java:35, 63-64`) — 아웃박스 폴링 + 소비라는 **비동기 창**이 있다. 그 창 안에서는 배송이 이미 `SHIPPED` 인데 주문은 `CONFIRMED` 라 취소가 받아진다. 다만 소유자 결정(«출고 전만 취소», 출고 뒤는 반품)이 이 경우를 «건드리지 않음» 으로 덮으므로 정책 공백은 아니다 — 창의 존재만 기록한다. 🔴 **더 큰 창은 그 앞이다**: WMS 가 피킹 · 포장 중인 동안 이커머스는 계속 `PREPARING` 이라 주문 취소가 정상적으로 받아진다. 이 구간이 «출고 전» 의 대부분이다.

**④ 계약 · ADR 이 출고 취소를 어떻게 적는가 · 연동 실재 여부.** 연동은 **실재한다** — 정방향 `ecommerce.fulfillment.requested.v1`, 역방향 `wms.outbound.shipping.confirmed.v1` · `wms.outbound.order.cancelled.v1` (`specs/services/shipping-service/architecture.md:22-23, 197-218`; `docs/adr/ADR-MONO-022-ecommerce-wms-fulfillment-integration.md:59, 92`). 취소는 **WMS → 이커머스 한 방향만** 있다(백오더 · WMS 쪽 취소 → order-service `WmsOutboundCancelledConsumer` 가 주문 자동 취소 + 환불, `order-service/.../WmsOutboundCancelledConsumer.java:48`). **이커머스 → WMS 출고 취소 이벤트는 없다** — WMS 가 구독하는 이커머스 토픽은 `ecommerce.fulfillment.requested.v1` · `ecommerce.shipping.manual-confirm-requested.v1` 둘뿐이고(`ecommerce-fulfillment-subscriptions.md:25-28`), 저장소 전체에 `fulfillment.cancel*` / `FulfillmentCancel*` 은 0 건이다. WMS `CancelOrderUseCase` 에 닿는 입구는 REST `OrderController` 하나다.

## 착수 중단 (2026-10-23 UTC) — 위임 지시의 중단 조건 (a)

- **«출고 전» 배송에는 기본 설정에서 WMS 출고 요청이 이미 나가 있고**(①), **그것을 취소하려면 새 프로젝트 간 계약 · 이벤트와 ADR 이 필요하다**(④). 이커머스 `Shipping` 행만 취소하면 이 티켓의 Failure Scenario 1 (배송은 취소 · WMS 는 계속 피킹 → 출고) 을 **그대로 구현하게 된다**. 그래서 계약 · 코드를 손대지 않았다.
- `wmsRouted=false` 인 배송(`fulfillment.enabled=false` · `require-sku-mapping=true` 의 미매핑 SKU)만 취소하는 부분 구현은 가능하지만, 기본 설정에서 그 모집단은 사실상 0 이라 결정을 이행한 것이 아니다 — 하지 않았다.
- **결정에 필요한 것 (소유자 선택창 재료)**:
  1. 새 이벤트 `ecommerce.fulfillment.cancel-requested.v1`(가칭, 이커머스 `fulfillment-events.md` + WMS `ecommerce-fulfillment-subscriptions.md`) + WMS 소비자 → `CancelOrderUseCase` 와 ADR-MONO-022 개정(D4 계열).
  2. WMS 가 **포장 뒤(`PICKED`/`PACKING`/`PACKED`) 자동 취소를 받을지** — 지금은 사람 관리자(`ROLE_OUTBOUND_ADMIN`)만 할 수 있다. 시스템 호출에 그 권한을 줄지, 아니면 포장 뒤는 거부하고 이커머스에 «취소 실패» 를 돌려줄지는 정책 결정이다.
  3. 거부 · 경합(WMS 가 이미 출하 확정)일 때 이커머스 주문 · 환불을 어떻게 할지 — 지금 주문 취소는 동기로 끝나고 환불도 이미 나간다. «출고 전에만 취소» 를 지키려면 취소를 WMS 응답까지 미루는 사가(`CANCELLATION_REQUESTED` 류) 가 필요할 수 있다 = 명세에 없는 아키텍처 결정(중단 조건 (b) 도 닿는다).
  4. 루프 확인: WMS 취소는 `outbound.order.cancelled` 를 다시 내고 이커머스 order-service 가 그것을 자동 취소로 소비한다 — 이미 `CANCELLED` 인 주문에서 no-op 인지 확인해야 한다.

## 구현 기록 (2026-10-23 UTC)

### 결정 — `ShippingStatusChanged(CANCELLED)` 는 내지 않는다

`shipping.shipping.status-changed` 의 `src/main` 소비자는 저장소 전체에 둘이다(검색어 `shipping.shipping.status-changed`, `projects/**/src/main/**`).

| 소비자 | `CANCELLED` 를 받으면 | 근거 |
|---|---|---|
| order-service `ShippingStatusChangedEventConsumer` | 무시(no-op) — `SHIPPED` · `DELIVERED` 가 아니면 바로 돌아간다 | `apps/order-service/.../infrastructure/event/ShippingStatusChangedEventConsumer.java:51-56` |
| notification-service `ShippingStatusChangedEventConsumer` | **고객에게 알림을 보낸다** — 상태를 가리지 않고 `SHIPPING_STATUS_CHANGED` 템플릿에 날 문자열 `newStatus` 를 넣는다 | `apps/notification-service/.../adapter/in/event/ShippingStatusChangedEventConsumer.java:46-59` |

settlement · search · batch-worker 는 이 토픽을 구독하지 않는다(0 건). 내면 고객이 «배송 상태 `CANCELLED`» 알림을 받는다. 그 문구는 소유자 승인이 없다(ADR-MONO-022 D4 v2(d) «Known costs» 가 콘솔 문구도 승인 대상으로 적는다). 그래서 **내지 않는다**. 취소는 배송 조회 API 의 `status` · `statusHistory` 로 보인다. 나중에 내려면 알림 문구를 먼저 정해야 하고, 그때는 덧붙이는 변경이다. 계약: `shipping-events.md` § ShippingStatusChanged.

### 결정 — 운영자는 배송을 `CANCELLED` 로 보낼 수 없다

`ShippingController.parseStatus` 는 `ShippingStatus.valueOf` 라(`ShippingController.java:101-108`) enum 에 `CANCELLED` 를 넣는 순간 `PUT /api/shippings/{id}/status` 로 들어올 수 있다. 그러면 환불도 WMS 취소 요청도 없이 배송만 취소된다 — 계약에 없는 API 다. 그래서 `Shipping.transitionTo` 는 `CANCELLED` 를 거부하고(→ `422 INVALID_STATUS_TRANSITION`), `CANCELLED` 로 가는 길은 `Shipping.cancel` 하나다. 계약: `shipping-api.md` § Allowed Status Transitions.

### 결정 — `CANCELLED` 배송에 WMS 출하 확정이 오면 경보 + no-op

X-A 의 포장 뒤 거부나 출하 확정과의 경합이면, 취소된 배송에 `wms.outbound.shipping.confirmed.v1` 이 올 수 있다. 지금 `markShippedByOrderId`(`ShippingCommandService.java:74-80`)는 `SHIPPED`/`IN_TRANSIT`/`DELIVERED` 만 건너뛰므로 `CANCELLED` 에서 `transitionTo(SHIPPED)` 가 예외를 내고, 재시도 뒤 DLQ 로 간다. 이것을 `ALERT` 로그 + no-op 으로 바꾼다(되살리지 않는다 — Y-A 에서 그것은 반품 흐름이다).

### 측정 — DB 에 상태 제약 없음 → 마이그레이션 없음

`shippings.status` · `shipping_status_history.status` 는 `VARCHAR(50)` 이고 CHECK 제약이 없다(`V1__create_shippings_table.sql:5`, `V2__create_shipping_status_history_table.sql:4`). JPA 는 `@Enumerated(EnumType.STRING)`(`ShippingJpaEntity.java:32`, `StatusHistoryJpaEntity.java:27`). Flyway 는 필요 없다.

### 구현 (2026-10-23 UTC)

- 도메인: `ShippingStatus` 에 `CANCELLED`(맨 뒤 — 정방향 전진기가 ordinal 로 걷기 때문), 허용 전이를 상태별 **집합**으로(`PREPARING → {SHIPPED, CANCELLED}`). `Shipping.cancel(Clock)` 이 유일한 입구, `transitionTo` 는 `CANCELLED` 거부.
- 응용: `ShippingCommandService.cancelByOrderId(CancelShippingCommand)` — `PREPARING` → `CANCELLED` · `wmsRouted` 면 같은 트랜잭션에서 `publishFulfillmentCancelRequested`. `SHIPPED`+ = WARN no-op, 이미 `CANCELLED` · 행 없음 = no-op. `ShippingStatusChanged` 안 냄. `markShippedByOrderId` 는 `CANCELLED` 에서 `ALERT` + no-op.
- 소비자: `OrderCancelledEventConsumer`(`order.order.cancelled`, 그룹 `shipping-service` — 같은 order-service 토픽을 읽는 `OrderConfirmedEventConsumer` 와 같은 그룹). dedupe `OrderCancelled`, 봉투 `tenant_id` → `TenantContext`(없으면 행의 tenant) · `finally` 해제, null payload / 빈 orderId = WARN 건너뜀, JSON · UUID 실패 = 기존 `KafkaConsumerConfig` 가 `order.order.cancelled.dlq` 로.
- 발행: `FulfillmentCancelRequestedMessage` — 계약 Event 3 필드 그대로(봉투 `eventId/eventType/occurredAt/aggregateType/aggregateId/tenantId/payload`, payload `orderNo/tenantId/reason/requestedAt`), outbox 라우팅 키 `FulfillmentCancelRequested` → `ecommerce.fulfillment.cancel-requested.v1`. `eventId` = `UUID.nameUUIDFromBytes("FulfillmentCancelRequested:" + orderId)`(한 배송은 한 번만 취소 — 동시 이중 취소는 outbox PK 충돌로 롤백). WMS 소비자(`FulfillmentCancelRequestedConsumer.java:124,130-150`)의 토픽 · 봉투 파서 요구(UUID `eventId` · `aggregateId`, `payload.orderNo`)와 맞다.
- 경보 억제: `WmsOutboundCancelledConsumer` — 배송 행이 이미 `CANCELLED` 면 `ALERT` 대신 INFO. 판정은 배송 상태이지 메아리의 `reason` 이 아니다(WMS 관리자가 포장 뒤 사람 손으로 끝낸 취소도 우리가 요청한 것이다).
- DB: 마이그레이션 없음(위 측정).

### 로컬에서 돈 것 (2026-10-23 UTC, 이 worktree)

| 명령 | 결과 |
|---|---|
| `./gradlew :projects:ecommerce-microservices-platform:apps:shipping-service:test` | `BUILD SUCCESSFUL` — JUnit XML 합계 **239 tests · 0 failures · 0 errors**. 새 시험 포함 확인: `OrderCancelledEventConsumerTest` 8, `WmsOutboundCancelledConsumerTest` 8, `SpringShippingEventPublisherTest` 8, `ShippingStatusTest` 10, `ShippingTest` 20, `ShippingCommandServiceTest` 30, `ShippingForwardAdvancerTest` 4, `ShippingOutboxPublisherTest` 5. `@Tag("integration")` 시험은 이 태스크에서 제외된다(설계). 통합 시험 소스는 `compileTestJava` 로 컴파일만 됐다. |
| 같은 명령, 구현에 결함 4개를 일부러 넣고(cancel-request 발행 끔 · 경보 억제 끔 · `transitionTo` 의 `CANCELLED` 거부 뺌 · `CANCELLED` 를 `DELIVERED` 앞으로) | **13 failed** — 넷 모두 겨냥한 시험이 빨강(`cancel-requested 발행`, `ALERT 억제`, `transitionTo 는 CANCELLED 를 거부`, `정방향 사슬의 맨 뒤` 등). 원복 뒤 다시 239/0. |
| `bash scripts/check-cross-project-topic-relay.sh` | `rc=0` — 19 routes, `ecommerce.fulfillment.cancel-requested.v1` 은 BE-650 이 넣은 `mm2.properties:110` 화이트리스트에 있다. |
| `bash scripts/check-service-type-drift.sh` | `rc=0` |
| `check-index-queue-drift.sh` · `check-task-id-collision.sh` · `check-walkthrough-ledger-drift.sh` (스테이지 뒤) | 셋 다 `rc=0` |
| `FulfillmentIntegrationTest`(Testcontainers) 새 5건 | **돌지 않음** — 이 호스트에 Docker 없음(`docker info` rc=1). |
| 시험만 담은 커밋 `b45296e0f` 단독 | **돌리지 않았다.** 새 API(`CANCELLED` · `cancel` · `cancelByOrderId` · 새 소비자)를 참조하므로 그 커밋에서 CI 빨강은 행동 실패가 아니라 `compileTestJava` 실패다. |

### 배송 상태를 늘어놓는 곳 (전수, 2026-10-23 UTC)

| 곳 | 처리 |
|---|---|
| `apps/shipping-service/.../domain/model/ShippingStatus.java` | 바꿈 |
| `.../domain/model/Shipping.java` (`transitionTo` 의 SHIPPED 분기) | 바꿈 (`CANCELLED` 거부 + `cancel`) |
| `.../application/service/ShippingCommandService.java` `markShippedByOrderId` 의 `SHIPPED/IN_TRANSIT/DELIVERED` 나열 | 바꿈 (`CANCELLED` → ALERT no-op) |
| `.../application/service/ShippingForwardAdvancer.java` (`values()` ordinal) | 동작은 enum 순서로 지킴, javadoc 만 |
| `.../infrastructure/persistence/ShippingRepositoryImpl.java:111-112` `IN_FLIGHT = {SHIPPED, IN_TRANSIT}` | 안 바꿈 — 취소된 배송은 추적 대상이 아니다 |
| `.../application/service/CarrierStatusMapper.java:38-69` | 안 바꿈 — 택배사 코드는 취소로 매핑되지 않는다(취소는 주문에서만 온다) |
| `.../interfaces/rest/controller/ShippingController.java:101-108` `valueOf` | 안 바꿈 — 목록 `?status=CANCELLED` 는 받고, PUT 은 도메인이 422 |
| order-service `ShippingStatusChangedEventConsumer.java:51-56` | 안 바꿈 — `CANCELLED` 를 받지 않는다(안 냄) |
| notification-service `ShippingStatusChangedEventConsumer.java:46-59` | 안 바꿈 — 같은 이유 |
| 계약 `shipping-api.md` · `shipping-events.md` · `order-events.md` · `fulfillment-events.md` · `wms-shipment-subscriptions.md`, 서비스 스펙 3 · order-service `dependencies.md` | 바꿈 (계약 커밋) |
| `tests/e2e/.../FulfillmentLoopE2ETest.java` · `KafkaTestProducer.java` | 안 바꿈 — backorder 시나리오는 주문 `CANCELLED` 와 `order.cancelled` 만 단언한다. 이제 그 취소가 배송도 `CANCELLED` 로 보내고 cancel-request 행을 쓰지만 단언에 닿지 않는다(nightly 전용 — 다음 nightly 를 한 번 볼 것). |
| `infra/demo/seed/seed-ecommerce.sh:299,502-` 배송 진행 | 안 바꿈 — 시드가 취소하는 주문은 슬롯 1 하나이고 결제 전 취소라 배송 행이 없다(no-op). 배송 하한 2건(슬롯 4 · 5)은 그대로. |
| web-store `packages/types/src/shipping.ts:3` · `apps/web-store/src/features/order/lib/shipping-steps.ts:3-12` · `ShippingTracker.tsx:51` | **안 바꿈 (범위 밖 · 후속 필요)** — 구매자 주문 상세가 `CANCELLED` 배송을 받으면 `getStepIndex` = -1 → 단계 표시에 켜진 단계가 없다. 깨지지는 않는다. 지금까지 취소 주문은 «상품 준비중» 으로 보였다. |
| platform-console `features/ecommerce-ops/api/shipping-types.ts:30-73` · `components/shipping-labels.ts:9-30` | **안 바꿈 — `TASK-PC-FE-367`.** 콘솔이 받을 것: 목록 · 상세의 `status: "CANCELLED"` 와 `statusHistory[].status: "CANCELLED"`(Zod 는 `z.string()` 이라 통과), `?status=CANCELLED` 필터 응답. 지금 콘솔은 라벨 없이 날 `CANCELLED` · 톤 `neutral` · 다음 전이 버튼 없음(`allowedNextStatus` = null)으로 그린다. 필터 선택지(`STATUS_FILTER_OPTIONS`)에는 없다. |

# Related Specs

- `projects/ecommerce-microservices-platform/specs/services/shipping-service/` · `order-service/`

# Related Contracts

- `projects/ecommerce-microservices-platform/specs/contracts/events/order-events.md`
- 배송 · WMS 출고 연동 계약(AC-0 에서 이름 확인)

# Edge Cases

- 이미 배송완료된 주문의 취소 — 반품 흐름이지 취소가 아니다(범위 판정).

# Failure Scenarios

1. 배송 서비스만 취소를 받고 WMS 출고는 계속 → 출고된 물건 · 취소된 주문. AC-0 ① · ④ 가 연동 전체를 본다.
