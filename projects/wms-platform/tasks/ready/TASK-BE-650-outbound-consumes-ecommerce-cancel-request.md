# Task ID

TASK-BE-650

# Title

outbound-service 가 이커머스 출고 취소 요청(`ecommerce.fulfillment.cancel-requested.v1`)을 받는다 — 접수 · 피킹 중이면 자동 취소, 피킹 뒤면 경보 (ADR-MONO-022 D4 v2(d) · X-A)

# Status

ready

# Owner

backend

# Task Tags

- code
- test
- contract

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus (프로젝트 간 계약 + 상태별 취소 권한 판정)
> 기안 2026-10-23 UTC — `ADR-MONO-022` **D4 v2(d) ACCEPTED — X-A / Y-A** (소유자 선택창 2026-10-23). 이번 굽기에 싣지 않는다.

# Dependency Markers

- 선행: 없음 — **이 티켓이 계약을 먼저 놓는다**(양쪽 프로젝트 계약 파일 포함).
- 후속: 이커머스 `TASK-BE-649`(발행 쪽) · 콘솔 `TASK-PC-FE-367`(배송 «취소됨» 라벨).
- 선례: ADR-MONO-022 D4 v2(c) — `ManualShipConfirmConsumer` (TASK-MONO-305). 같은 모양으로 만든다.

# Goal

이커머스 주문이 확정 뒤 취소되면 이커머스가 WMS 에 출고 취소를 요청한다. WMS 는 출고 주문을 `orderNo` 로 찾아 **기존** `CancelOrderUseCase` 로 취소한다. 판정은 X-A 를 따른다.

| WMS 출고 상태 | 처리 |
|---|---|
| `RECEIVED` · `PICKING` | 자동 취소(`ROLE_OUTBOUND_WRITE` 와 같은 범위). 기존 보상(`outbound.picking.cancelled` → 예약 해제) · 기존 `wms.outbound.order.cancelled.v1` 발행 그대로 |
| `PICKED` · `PACKING` · `PACKED` | **취소하지 않는다.** `ALERT` 로그(orderNo · 상태) — 물건을 제자리에 돌려놓은 뒤 사람 관리자(`ROLE_OUTBOUND_ADMIN`)가 기존 경로로 취소 |
| `SHIPPED` | 기록만 하는 no-op (반품 흐름) |
| 이미 `CANCELLED` · 모르는 orderNo · 재전달 | no-op |

# Scope

## In Scope

- **계약 먼저** (스펙만, 한 커밋):
  - 이커머스 `projects/ecommerce-microservices-platform/specs/contracts/events/fulfillment-events.md` 에 `ecommerce.fulfillment.cancel-requested.v1` 추가. WMS camelCase 봉투(`manual-confirm-requested.v1` 과 같은 모양)이고, 페이로드는 `orderNo` · `tenantId` · `reason=ORDER_CANCELLED` · `requestedAt` 이다. 생산자는 shipping-service 이고, 배송이 `PREPARING` 이면서 `wmsRouted` 일 때만 낸다.
  - WMS `projects/wms-platform/specs/contracts/events/ecommerce-fulfillment-subscriptions.md` 에 구독 행과 위 상태 표를 넣는다.
  - outbound-service 스펙에서 구독 이벤트를 나열하는 곳이 있으면 거기에도 넣는다.
- outbound-service 에 `FulfillmentCancelRequestedConsumer` 를 둔다. `ManualShipConfirmConsumer` 의 토픽 · 그룹 · 역직렬화 · dedupe · DLQ 관례를 그대로 복사한다. 시스템 호출자로 `CancelOrderUseCase` 를 부르되 **WRITE 범위로 고정**한다.
- 시험: 상태별 표의 각 행 · 재전달 · 모르는 orderNo.

## Out of Scope

- 이커머스 발행(`TASK-BE-649`) · 콘솔 라벨(`TASK-PC-FE-367`).
- 피킹 뒤 단계 자동 취소(X-B 는 거부됐다).

# Acceptance Criteria

- [ ] **AC-0** — `CancelOrderService` 의 권한 판정이 시스템 호출자(보안 컨텍스트 없음)를 어떻게 다루는지 측정한다(file:line). D9 의 «internal = unrestricted» 와 충돌하지 않게, 시스템 요청이 ADMIN 범위로 새지 않는지 확인한다.
- [ ] **AC-1** — 계약 커밋이 구현보다 먼저.
- [ ] **AC-2** — 구현 + 시험 · CI 초록 (시험만 담은 커밋으로 CI 빨강을 먼저 본다).
- [ ] **AC-3** — 굽기 뒤 라이브.

# Related Specs

- `docs/adr/ADR-MONO-022-ecommerce-wms-fulfillment-integration.md` § D4 v2(d)
- `projects/wms-platform/specs/services/outbound-service/`

# Related Contracts

- `projects/ecommerce-microservices-platform/specs/contracts/events/fulfillment-events.md`
- `projects/wms-platform/specs/contracts/events/ecommerce-fulfillment-subscriptions.md`
- `projects/wms-platform/specs/contracts/events/outbound-events.md` (`wms.outbound.order.cancelled.v1` — 기존)

# Edge Cases

- WMS 가 자동 취소하면 `wms.outbound.order.cancelled.v1` 이 이커머스로 돌아간다. order-service 는 이미 `CANCELLED` 인 주문을 no-op 으로 넘기므로(`OrderBackorderCancellationService.java:55-58`) 루프가 생기지 않는다. shipping-service 쪽 경보 억제는 `TASK-BE-649` 가 맡는다.
- 출하 확정과 경합하면 `SHIPPED` 가 된다. 이것은 no-op 이고 경보만 남긴다.

# Failure Scenarios

1. 시스템 요청이 ADMIN 범위로 처리돼 포장된 물건이 사람 확인 없이 취소된다 → AC-0 · 시험이 막는다.
2. 계약 없이 구현이 먼저 머지된다 → AC-1.
