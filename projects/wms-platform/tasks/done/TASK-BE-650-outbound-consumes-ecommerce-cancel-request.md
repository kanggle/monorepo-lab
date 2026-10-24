# Task ID

TASK-BE-650

# Title

outbound-service 가 이커머스 출고 취소 요청(`ecommerce.fulfillment.cancel-requested.v1`)을 받는다 — 접수 · 피킹 중이면 자동 취소, 피킹 뒤면 경보 (ADR-MONO-022 D4 v2(d) · X-A)

# Status

done

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
- [x] **AC-1** — 계약 커밋이 구현보다 먼저. ✅ 2026-10-23 UTC — 브랜치 `task/be-650-wms-cancel-consumer` 커밋 순서: `c3d3bd250`(계약만: 이커머스 `fulfillment-events.md` § Event 3 · WMS `ecommerce-fulfillment-subscriptions.md` · `outbound-events.md` · `architecture.md` · events `README.md`) → `955c985c1`(시험만) → 구현 커밋. 🔴 PR 이 스쿼시 머지되면 main 에는 한 커밋이 된다 — 순서의 증거는 PR 의 커밋 목록이다.
- [x] **AC-2** — 구현 + 시험 · CI 초록 (시험만 담은 커밋으로 CI 빨강을 먼저 본다). ✅ 2026-10-23 UTC (메인 세션) — PR #4488 에 시험만 담은 커밋 `955c985c1` 까지 먼저 올렸다. CI run `39912885526` 에서 `Build & Test` · `Integration (… outbound-service, Testcontainers)` 두 체크가 빨강이었고, 원인은 `outbound-service:compileTestJava FAILED`(새 시험 두 파일이 소비자 클래스를 못 찾음)다. 🔴 이것은 **컴파일 빨강**이다. 시험이 새 코드를 참조한다는 것만 증명하고, 동작이 틀리면 시험이 무는지는 증명하지 않는다(아래 ⚪ 변이 확인은 여전히 미측정). 구현 `f084d9cd3` 을 올린 뒤 head 체크 69개 pending 0 · fail 0 이다. Testcontainers 통합 레인(`ListenerTopicsPrecreatedIT` 포함)도 초록이다. 설정을 바꾸는 PR 이라 브랜치 nightly `39913529748` 을 돌렸고 success 다(콘솔 full-stack · 이커머스×WMS 이행 루프 e2e 포함). 🔵 생산자(`TASK-BE-649`)가 없어서 이 PR 만 굽기에 실려도 동작 변화는 없다.
  - 로컬에서 돈 것:
  - `./gradlew :projects:wms-platform:apps:outbound-service:test --no-daemon` → rc=0, BUILD SUCCESSFUL. 결과 XML 51 클래스 · 321 시험 · 실패 0 · 오류 0 · 건너뜀 4(`OrderJpaRepositoryFilterIT` — 이 티켓과 무관한 기존 건너뜀). 새 시험: `FulfillmentCancelRequestedConsumerTest` 15 · `FulfillmentCancelRequestedConsumerCancelPathTest` 6, 모두 통과.
  - `bash scripts/check-cross-project-topic-relay.sh` → rc=0 (19 routes / 5 flows). 물기 확인: 이 브랜치 전의 `mm2.properties` 로 돌리면 rc=1, `listener route [ecommerce->wms ecommerce.fulfillment.cancel-requested.v1] 가 릴레이 화이트리스트에 없다`.
  - ⚪ **돌지 않은 것**: `integrationTest` 레인(Testcontainers — `ListenerTopicsPrecreatedIT` 가 새 토픽 목록 줄을 확인하는 곳) 은 로컬에서 돌리지 않았다. CI 전용으로 본다.
  - ⚪ **재지 못한 것**: «ADMIN 을 넣으면 시험이 빨강이 되는가» 변이 확인. `SYSTEM_ROLES` 에 ADMIN 을 넣는 임시 편집을 자동 모드 분류기가 막았다(권한 상수 편집). 그래서 측정하지 않았다. 근거는 추론뿐이다: `containsExactly("ROLE_OUTBOUND_WRITE")` 는 원소가 둘인 집합에서 실패하고, `CancelPathTest` 의 피킹 뒤 시험은 ADMIN 이 있으면 주문이 `CANCELLED` 가 되어 실패한다.
  - 시험만 담은 커밋 `955c985c1` 은 소비자 클래스가 없어서 **컴파일 단계에서** 빨강이 된다(로컬에서 그 커밋만 따로 빌드해 보지는 않았다).
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
- 🔴 **열린 질문 (2026-10-23 UTC, 구현 중 발견) — 토픽 사이 순서.** 취소 요청과 출고 요청(`ecommerce.fulfillment.requested.v1`)은 다른 토픽이라 Kafka 가 순서를 보장하지 않는다. 취소 요청이 먼저 소비되면 `findByOrderNo` 가 비어 no-op 으로 끝나고, 뒤이어 출고 요청이 주문을 만든다. 그 주문을 취소할 것은 남지 않는다. ADR 과 이 티켓은 이 창을 다루지 않는다. 그래서 계약 두 곳에 «기록만, v1 에서 닫지 않음» 으로 적었고 코드는 만들지 않았다. 닫을지는 소유자가 정한다.

# Failure Scenarios

1. 시스템 요청이 ADMIN 범위로 처리돼 포장된 물건이 사람 확인 없이 취소된다 → AC-0 · 시험이 막는다.
2. 계약 없이 구현이 먼저 머지된다 → AC-1.

## 라이브 확인 (2026-10-23 UTC · 33차 창)

- 배포 상태: 굽기 `283fd39cf` → AMI `ami-02b3ba00a3b2ff622`(51분 36초 · 태그 RepoCommit 일치) · 핀 PR #4498(`c5e3ddad3`) · 인스턴스 `i-0f39aec49ddbaa899`. impl 머지 `bb4f73185`(#4488) 는 `283fd39cf` 의 조상.
- **AC-3 ❌ 일부 실패.**
  - ✅ X-A 대로인 부분: 출고 `01a168e0-1a3a-…`(orderNo `49eae2f6…`) 가 `PICKING` 에서 자동 취소 — `FulfillmentCancelRequestedConsumer fulfillment_cancel_applied … previousStatus=PICKING`, `CancelOrderService order_cancelled … reservationHeld=true`.
  - ❌ **예약이 풀리지 않는다**: 사가가 `CANCELLATION_REQUESTED` 에 멈춤(13:22 sweeper 재발행 1회), inventory `reservation 2f397b48-… status=RESERVED`, SKU …0013 `reservedQty=1`. inventory-service 가 `wms.outbound.picking.cancelled.v1` 을 4회 재시도 뒤 DLT 로 보냄 — DLT 토픽 `wms.outbound.picking.cancelled.v1.DLT` 는 **없음**(UNKNOWN_TOPIC). 예외 본문은 로그에 **안 찍힘**.
  - 메시지 페이로드 `reservationId = 01a168e0-1a3e-75f9-b61f-66166cd03ba1`(= sagaId) 인데 inventory 의 실제 예약 id 는 `2f397b48-…`(그 sagaId 는 `picking_request_id` 칼럼) — 창 기록은 «불일치가 유력 원인(추정, 소비자 코드 미확인)».
  - 이 경로는 이 티켓이 새로 만든 것이 아니라 기존 `CancelOrderUseCase` 보상 경로다(운영자 취소도 영향받을 것으로 추정).
- 새 티켓: **`TASK-BE-651`**(inventory-service 가 `picking.cancelled` 로 예약을 못 푼다 · DLT 토픽 없음 · 리스너 예외 미기록). 그것이 실린 뒤 이 AC-3 의 «예약 해제» 를 다시 잰다. PICKED–PACKED 거부(`ALERT ecommerce cancel request refused after pick`) 갈래는 이번 창에 그 상태의 주문이 없어 재지 않았다.
- ⇒ 이 티켓은 `review` 에 남는다(체크 칸은 동결 규칙으로 고치지 않음).

---

## CORRECTION — 34차 창 라이브 결과 (2026-10-24 UTC)

- **AC-3 — 닫힘.** 34차 창(2026-10-24 UTC): `TASK-BE-646` 의 취소 주문 `7e457d10-…` 에 대해 wms 출고가 `PICKING→CANCELLED` 16:30:34Z 로 자동 취소됐다. 로그 `CancelOrderService order_cancelled … reservationHeld=true`. 예약 해제 절반은 `TASK-BE-651` AC-2 에서 닫혔다(예약 `RELEASED`, SKU …2013 `reservedQty` 1→0). (증거 o07 · o11)
- (원문 체크박스는 동결 규칙(HARDSTOP-05)상 바꾸지 않았다 — 이 절이 그 AC 의 닫힘 기록이다. 위 «이 티켓은 `review` 에 남는다» 문장은 33차 창 시점의 기록이며 지금은 `done` 이다.)
