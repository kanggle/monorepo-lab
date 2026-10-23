# Task ID

TASK-BE-650

# Title

outbound-service 가 이커머스 출고 취소 요청(`ecommerce.fulfillment.cancel-requested.v1`)을 받는다 — 접수 · 피킹 중이면 자동 취소, 피킹 뒤면 경보 (ADR-MONO-022 D4 v2(d) · X-A)

# Status

in-progress

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

- [x] **AC-0** — `CancelOrderService` 의 권한 판정이 시스템 호출자(보안 컨텍스트 없음)를 어떻게 다루는지 측정한다(file:line). D9 의 «internal = unrestricted» 와 충돌하지 않게, 시스템 요청이 ADMIN 범위로 새지 않는지 확인한다. 측정 2026-10-23 UTC — 아래 § AC-0 측정 결과. 결론: **아키텍처 변경 없이** 명령에 `callerRoles = {ROLE_OUTBOUND_WRITE}` 를 넣으면 WRITE 범위로 고정된다.
- [ ] **AC-1** — 계약 커밋이 구현보다 먼저.
- [ ] **AC-2** — 구현 + 시험 · CI 초록 (시험만 담은 커밋으로 CI 빨강을 먼저 본다).
- [ ] **AC-3** — 굽기 뒤 라이브.

## AC-0 측정 결과 (2026-10-23 UTC)

경로는 모두 `projects/wms-platform/apps/outbound-service/src/main/java/com/wms/outbound/` 아래다.

**① 권한은 두 축이고, 서로 다른 입력에서 온다.**

- **역할 축**은 보안 컨텍스트를 읽지 않는다. 명령에 담긴 `callerRoles` 만 본다. `application/service/CancelOrderService.java:90-95` 에서 `POST_PICK_STATES`(`:50-51` = `PICKED`/`PACKING`/`PACKED`)이면 `AuthorizationGuards.requireRole(callerRoles, ROLE_OUTBOUND_ADMIN)` 을 부르고, 그 밖이면 `requireAnyRole(callerRoles, WRITE, ADMIN)` 을 부른다. 가드는 `application/service/AuthorizationGuards.java:28-31, 41-50` 이고, `null`/빈 집합이면 `AccessDeniedException` 을 던진다. REST 는 이 집합을 JWT 권한에서 만든다(`adapter/in/web/controller/OrderController.java:87-92` → `RequestContext.callerRoles(authentication)`).
- **테넌트 가시성 축**(D9)은 `CancelOrderService.java:86` 의 `callerScopeProvider.current().requireOrderAccess(...)` 하나다. 보안 컨텍스트가 없으면 `adapter/out/security/SecurityContextCallerScopeProvider.java:75-77` 이 `CallerScope.unrestricted()` 를 돌려주고, `application/security/CallerScope.java:93-96` 에서 그 경우는 no-op 이다.

⇒ D9 의 «internal = unrestricted» 는 **어느 테넌트의 주문을 볼 수 있는가**만 넓힌다. **어떤 상태까지 취소할 수 있는가**는 넓히지 않는다. 그건 역할 축이고, 역할은 호출자가 명령에 직접 넣는다. 그래서 두 결정은 충돌하지 않는다.

**② WRITE 범위로 고정하는 방법 — 아키텍처 변경 없음.** 선례 `ManualShipConfirmConsumer.java:102` 가 이미 `SYSTEM_ROLES = Set.of("ROLE_OUTBOUND_WRITE")` 로 같은 일을 한다. 새 소비자도 `callerRoles = Set.of("ROLE_OUTBOUND_WRITE")` 를 넣는다. 그러면 피킹 뒤 상태에서 `CancelOrderService.java:91-92` 가 **서비스 자신이** `AccessDeniedException` 으로 거부한다. 소비자가 상태를 먼저 보고 `ALERT` 로 끝내는 것이 1차 방어이고, 서비스 가드가 2차 방어다(소비자가 상태를 읽은 뒤 같은 주문이 `PICKED` 로 넘어가는 경합을 막는다). REST 경로와 `CancelOrderService` · `CancelOrderCommand` 는 **손대지 않는다**.

**③ 그 밖에 소비자가 알아야 할 것.**

- 버전: `CancelOrderService.java:101` — `expectedVersion < 0` 이면 이른 낙관락 비교를 건너뛴다. 선례가 `-1` 을 넘긴다(`ManualShipConfirmConsumer.java:110`).
- 도메인 가드: `domain/model/Order.java:213-218` — `SHIPPED` 면 `OrderAlreadyShippedException`, `CANCELLED`/`BACKORDERED` 면 `StateTransitionInvalidException` 을 던진다. 그래서 소비자는 이 상태들을 **부르기 전에** no-op 으로 걸러야 한다(던지면 재시도 뒤 DLT 로 간다).
- 오류 분류: `config/KafkaConfig.java:71` — 재시도하지 않는 것은 `IllegalArgumentException` 하나다. `AccessDeniedException`(위 경합)은 재시도된다. 같은 트랜잭션의 dedupe 행도 함께 롤백되므로, 재시도 때 소비자는 새 상태(`PICKED`)를 읽고 `ALERT` 로 끝난다.
- 표에 없는 상태: `BACKORDERED` 는 이미 끝 상태다. 이미 `CANCELLED` 인 경우와 같이 no-op 으로 둔다(티켓 표에 없어서 계약에 적었다).
- 지시서가 말한 «TenantContext 관례»: wms outbound 소비자에는 `TenantContext` 가 없다(`adapter/in/messaging/consumer/` 전체 grep 0 건). 봉투의 `tenantId` 는 facet d 의 불투명 상관값이라 판정에 쓰지 않는다(`EventEnvelopeParser.java:37-40`).

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
