# Task ID

TASK-BE-649

# Title

shipping-service 가 `order.order.cancelled` 를 소비하지 않는다 — 출고 요청이 이미 나간 확정 주문을 취소해도 배송 · 출고 쪽은 모른다

# Status

ready

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
- [ ] **AC-1** — 소유자 결정 · 필요하면 ADR 먼저. 🔵 소유자 결정 2026-10-23 KST: **«아직 출고 전인 배송만 취소»**. 🔴 **그러나 이 결정만으로는 닫히지 않는다** — AC-0 ①·④ 가 보이듯 «출고 전» 배송은 기본 설정에서 **이미 WMS 출고 주문이 나가 있고**, 그것을 취소할 이커머스 → WMS 경로(이벤트 · 계약)가 없다. 결정을 이행하려면 새 프로젝트 간 이벤트 + ADR-MONO-022 개정이 먼저다(§ 착수 중단). 소유자 선택 대기. 🟡 2026-10-23 UTC 소유자 선택 «ADR 초안 · 굽기는 안 기다림» → ADR-MONO-022 **D4 v2(d) PROPOSED** 초안(선택지 X: WMS 자동 취소 깊이 · Y: 주문 · 환불 시점). 승인은 정확형 `ADR-MONO-022 D4 v2(d) ACCEPTED — X-? / Y-?` 만. 이번 굽기에 싣지 않는다.
- [ ] **AC-2** — 구현 + 시험 · CI 초록. 미착수 — 위 중단 때문에 구현 · 시험을 쓰지 않았다(로컬에서 돈 시험 없음).
- [ ] **AC-3** — 굽기 뒤 라이브.

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

# Related Specs

- `projects/ecommerce-microservices-platform/specs/services/shipping-service/` · `order-service/`

# Related Contracts

- `projects/ecommerce-microservices-platform/specs/contracts/events/order-events.md`
- 배송 · WMS 출고 연동 계약(AC-0 에서 이름 확인)

# Edge Cases

- 이미 배송완료된 주문의 취소 — 반품 흐름이지 취소가 아니다(범위 판정).

# Failure Scenarios

1. 배송 서비스만 취소를 받고 WMS 출고는 계속 → 출고된 물건 · 취소된 주문. AC-0 ① · ④ 가 연동 전체를 본다.
