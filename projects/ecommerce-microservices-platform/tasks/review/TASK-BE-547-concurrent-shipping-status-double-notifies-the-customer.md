# TASK-BE-547 — 동시 배송 상태 전이가 고객에게 배송 알림을 두 번 보낸다 (`ShippingStatusChanged` 소비자 dedup 이 무력화됨)

**Status:** review

**Type:** TASK-BE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Opus 4.8 (event_id 채번 규약 판정 + 계약/아웃박스 상호작용. "가드 하나 추가"가 아니다)

> `TASK-BE-537`(done, PR #2802) 조사에서 확인된 **유일한 실사용 피해**를 별 티켓으로 분리한 것. 저쪽은 조사 티켓이라 재현·근본원인 규명까지였고, 근본 수정은 계약 변경을 동반해 명시적으로 후속으로 미뤘다.

---

## Goal

`TASK-BE-537` 이 재현했다: 두 동시 `PUT /api/shippings/{id}/status` (→ SHIPPED) 가 인메모리 상태기계를 **둘 다 통과해 둘 다 커밋**하고, 아웃박스에 **`ShippingStatusChanged` 행이 2개** 남는다. WMS 재고 이중 차감은 아니었지만(파티션 순서 + saga 종단 no-op 이 막는다), **고객이 배송 알림을 두 번 받는다.** 이것이 이 조사의 유일한 실사용 피해다.

왜 기존 dedup 이 못 막는가 — 두 소비자 모두 **event_id 로 중복을 거른다**:

- `order-service` `ShippingStatusChangedEventConsumer:40` — `eventDeduplicationChecker.isDuplicate(event.eventId(), ...)`
- `notification-service` `ShippingStatusChangedEventConsumer:49` — `event.eventId()` 를 `SendNotificationCommand` 의 dedup 키로 전달(→ `uq_notifications_event_id`, `TASK-BE-539` 이 `(tenant_id, event_id, channel)` 로 정의)

그런데 `SpringShippingEventPublisher:56` 이 **발행마다 `UUID.randomUUID()`** 를 event_id 로 채번한다. ⇒ 동시 이중 발행은 **서로 다른 event_id** 를 갖고, 두 소비자의 event_id dedup 을 **둘 다 통과**한다.

**그런데 결과가 갈린다:**

| 소비자 | event_id dedup | 실제 방어 | 결과 |
|---|---|---|---|
| order-service | 무력화됨 | `Order.ship()` 상태기계(`OrderShippingService:39` `changed` 플래그, 이미 SHIPPED 면 no-op) | **안전** — 이중 전이 무해 |
| notification-service | 무력화됨 | **없음** — `notificationSendService.sendNotification(command)` 를 무조건 호출(`:59`) | **알림 2건** |

즉 notification 이 무방비인 이유는 우연이다 — order 는 뒤에 상태기계가 한 겹 더 있고 notification 은 없다. **같은 dedup 결함을 두 소비자가 공유하는데 한쪽만 증상이 난다.**

---

## Scope

### In Scope

1. 동시 이중 `ShippingStatusChanged` 발행이 **고객 알림을 한 번만** 만들도록 한다.
2. 그 판정의 근거와 선택한 메커니즘 기록(아래 § 메커니즘 후보).
3. 동시 케이스를 실제로 모는 테스트. 로컬 Windows Testcontainers 는 FLAKY — **CI Linux 가 권위.**

### Out of Scope

- **`TASK-BE-537` 의 근본 경합 자체(`@Version` 추가)** — 그것은 두 번째 커밋을 `OptimisticLockException` 으로 만들고, 지금 그건 **500 으로 샌다.** 고쳐서 409 로 만들려면 **에러 응답 계약 변경(409 + 에러코드 등록) + 마이그레이션**이 필요하다. BE-537 이 "가장 작은 가드가 아니다"라며 후속으로 미룬 바로 그것이다. 이 티켓이 아래 (B) 를 고르면 그 계약 작업이 선행돼야 하므로, 그 경우 **범위가 이 티켓을 넘어선다** — 그때는 STOP 후 별 계약 task 를 세운다.
- `ManualShipConfirmRequested` 다리 — 이미 파티션 순서 + saga 종단 no-op 으로 방어됨(BE-537 확인). 건드리지 않는다.
- WMS 재고 경로 — 이중 차감 없음(BE-537 확인).

---

## 메커니즘 후보 (AC-1 이 판정한다 — 미리 정답을 가정하지 말 것)

**(A) `ShippingStatusChanged` 의 event_id 를 결정적으로 채번한다.** 랜덤 UUID 대신 전이에서 유도(예: `(shippingId, newStatus)` 또는 전이 튜플). 선형 상태기계(`ALLOWED_TRANSITIONS` 는 상태당 후속 1개, BE-537 확인)라 **한 상태는 배송 생애에 한 번만 진입**한다 ⇒ 같은 `(shippingId, newStatus)` 두 번은 **정확히 경합-중복일 때만** 발생한다. 그러면 두 소비자의 **기존 event_id dedup 이 그대로 작동**한다.

- **🔴 반드시 확인할 상호작용**: event_id 는 아웃박스 행 PK 이기도 하다(`SpringShippingEventPublisher:70` `UUID.fromString(message.eventId())`). 결정적 채번이면 두 번째 발행의 아웃박스 INSERT 가 **PK 충돌**한다 — 이는 T2 를 커밋에서 실패시키고, shipping-service 의 DIVE 백스톱(`GlobalExceptionHandler:147`, `TASK-BE-542`)이 그걸 **409 로 매핑**한다. 즉 (A) 는 "계약 무변경"이 **아닐 수 있다** — (B) 와 같은 409-vs-500 질문을 아웃박스 PK 로 옮겨 오는 것이다. **이 경로를 실측으로 확인하고, 409 가 이 엔드포인트의 옳은 응답인지도 판정하라.** (경합 패자가 받는 409 가 정당한가, 아니면 조용히 흡수돼야 하는가.)

**(B) 소비자 측(notification)에 상태/전이 가드를 추가한다.** notification 이 event_id 가 아니라 `(shippingId, newStatus)` 또는 사용자-대면 전이 단위로 중복을 거르게 한다. 발행 측 계약을 안 건드리지만, **같은 결함(event_id 채번)이 order 소비자에 남는다** — 지금은 상태기계가 가려 주지만, 그건 우연한 방어이고 다음 소비자가 추가되면 재발한다. **공급원(발행 측)이 계속 무력한 키를 만든다** — 소비자마다 우회를 심는 것은 [[feedback_workaround_becomes_the_contract]] 의 지문이다.

**(C) 근본 경합을 고친다(BE-537 `@Version`).** 두 커밋 중 하나가 롤백돼 아웃박스 행이 애초에 1개가 된다. **가장 옳지만 가장 비싸다** — § Out of Scope 의 계약 변경. 이 티켓 단독으로 감당하면 안 된다.

**판정 지침**: (A) 가 유력해 보이나 아웃박스 PK 상호작용이 (C) 와 같은 계약 질문을 부를 수 있으므로 **실측 없이 "가볍다"고 단정하지 말 것.** 세 후보 중 무엇을 고르든, 고른 이유와 **버린 이유**를 적는다.

---

## Acceptance Criteria

- **AC-0 (gate — 재측정)** — 착수 시 다음을 **직접 다시 확인**한다. 위 서술은 2026-07-20 스냅샷이고 출처가 아니라 가설이다.
  1. `SpringShippingEventPublisher` 가 여전히 `ShippingStatusChanged` event_id 를 `UUID.randomUUID()` 로 채번하는가.
  2. 두 소비자(order·notification)가 여전히 event_id 로 dedup 하는가, 그리고 **notification 에 여전히 상태/전이 가드가 없는가.**
  3. notification dedup 키가 실제로 event_id 인지(`SendNotificationCommand` → `existsByEventId` 경로). BE-539 이 인덱스를 `(tenant_id, event_id, channel)` 로 바꿨으므로 **채널 팬아웃과의 상호작용도 본다** — 같은 event_id 라도 채널이 다르면 별 행이다.
  탐지식은 known-positive 로 자기검증(예: shipping 아웃박스 발행 지점은 hit 가 있어야 한다). 어느 전제가 이미 달라졌으면 STOP 후 보고.
- **AC-1** — (A)/(B)/(C) 판정 + 근거. (A) 를 고르면 **아웃박스 PK 충돌 경로를 실측**하고 그 결과 응답(409 등)이 옳은지 판정한다. (C) 로 기운다면 계약 변경이 필요하므로 **STOP 후 별 task**.
- **AC-2** — 동시 이중 전이가 고객 알림을 **한 번만** 만든다. 알림 **행 수**(또는 발송 호출 수)로 단언한다 — dedup 행이 존재한다가 아니라 **부수효과가 한 번**임을. `TASK-BE-537` 의 `ConcurrentStatusTransitionIntegrationTest` 하네스를 재사용/확장할 수 있다.
- **AC-3** — **정당한 서로 다른 전이는 여전히 각각 알린다**: 같은 배송의 SHIPPED 알림과 이후 DELIVERED 알림은 둘 다 가야 한다. 순진한 "같은 shippingId 두 번째는 버린다" 가드가 깨뜨릴 회귀이므로 별 테스트로 고정한다.
- **AC-4** — 가드가 **동시**에서도 성립함을 보인다(순차 재생이 아니라). read-then-write 검사에 아무 제약/락이 없으면 두 요청이 둘 다 통과한다 — 무엇이 중재자인지(아웃박스 PK / 유니크 제약 / 락) 명시한다.
- **AC-5** — 계약 표면이 바뀌면(응답 코드, 이벤트 채번 규약) `specs/contracts/` 를 **구현 전에** 갱신한다. event_id 채번 규약이 바뀌면 이벤트 계약에 적는다.
- **AC-6** — `shipping-service`(+ 만졌다면 notification-service) 빌드 + 테스트 GREEN. **로컬은 판정 자격 없음 — CI Linux 가 권위.**

---

## Related Specs

- `apps/shipping-service/.../SpringShippingEventPublisher.java:52-92` — `ShippingStatusChanged` 발행 + 랜덤 event_id 채번
- `apps/order-service/.../ShippingStatusChangedEventConsumer.java:40` — event_id dedup (상태기계가 뒤를 받침)
- `apps/notification-service/.../ShippingStatusChangedEventConsumer.java:49,59` — event_id dedup + 무조건 발송(무방비 다리)
- `apps/order-service/.../OrderShippingService.java:37-48` — `Order.ship()` 의 `changed` 플래그(order 를 살리는 상태기계)
- `tasks/done/TASK-BE-537-shipping-status-concurrent-transition-investigation.md` — 재현 + 근본원인 + 이 다리를 후속으로 남긴 근거
- `tasks/done/TASK-BE-539-*.md` — notification event_id 인덱스를 `(tenant_id, event_id, channel)` 로 정의

## Related Contracts

- `specs/contracts/` 의 shipping 이벤트 계약 — event_id 채번 규약이 바뀌면((A)) 선행 갱신.
- `specs/contracts/http/` 의 shipping status 엔드포인트 — 경합 패자 응답이 바뀌면((A) 아웃박스 PK 경로 또는 (C)) 선행 갱신.

---

## Edge Cases

1. **🔴 (A) 의 아웃박스 PK 상호작용** — event_id = 아웃박스 PK 이므로 결정적 채번은 두 번째 발행을 PK 위반으로 만든다. 이게 흡수돼야 하는지(경합 중복이니 조용히) 아니면 409 로 노출돼야 하는지 판정. § 메커니즘 후보 (A) 참조.
2. **채널 팬아웃 상호작용** — notification 인덱스가 `(tenant_id, event_id, channel)` 이라 event_id 를 결정적으로 만들어도 채널당 1행은 정상이다. 목표는 "채널당 1행"을 지키면서 "같은 전이의 중복 event 를 접는" 것 — 둘을 섞지 말 것.
3. **정당한 재전이 없음 확인** — 선형 상태기계라 한 상태는 한 번만 진입한다는 전제가 (A) 의 정당성이다. `ALLOWED_TRANSITIONS` 가 실제로 상태당 후속 1개인지 재확인(BE-537 이 확인했으나 가설로 물려받지 말 것).
4. **carrier-webhook / refresh-tracking 도 같은 이벤트를 발행한다** — 이들 경로의 동시성도 같은 지문을 낼 수 있는지 표본 확인. 상태기계 가드가 발행 전에 막으면 무해하지만, 경합 창은 같다.

---

## Failure Scenarios

- **F1 — notification 에만 우회를 심고 order 의 event_id 결함을 방치한다((B)).** 지금은 상태기계가 가려 주지만 공급원은 여전히 무력한 키를 만든다. 다음 소비자가 재발시킨다. [[feedback_workaround_becomes_the_contract]]
- **F2 — (A) 를 "계약 무변경"으로 단정하고 아웃박스 PK 충돌을 놓친다.** 경합 패자가 500 으로 새면 증상을 알림에서 아웃박스로 옮긴 것뿐이다. Edge 1.
- **F3 — 순차 재생만 테스트한다.** 이 결함은 정의상 동시성이다. 순차 재전이는 상태기계가 이미 막는다(BE-537 확인) — 순차 테스트는 항상 초록이라 아무것도 증명 못 한다. AC-4.
- **F4 — AC-3 회귀.** 순진한 shippingId 단위 dedup 이 정당한 SHIPPED→DELIVERED 알림을 삼킨다.

---

## Test Requirements

- 동시 이중 SHIPPED 전이 → 고객 알림/발송 **1회** 단언(부수효과 기준, 행 존재 아님).
- 정당한 SHIPPED 후 DELIVERED → 알림 **2회**(서로 다른 전이).
- (A) 채택 시 아웃박스 행이 1개이고 경합 패자의 응답이 판정한 코드(409 등)임을 확인.
- 로컬 Testcontainers FLAKY — **CI Linux 가 권위.** 로컬 `BUILD SUCCESSFUL` 이 Docker-down SKIPPED 를 가릴 수 있으니 test-report XML 의 실제 test 수를 확인.

---

## Definition of Done

- [x] AC-0 재측정 (채번·두 소비자 dedup·notification 무방비 재확인, 탐지식 자기검증) — 전제 전부 그대로 확인: `SpringShippingEventPublisher:56` 여전히 `UUID.randomUUID()`; order 소비자 event_id dedup+상태기계, notification 소비자엔 dedup 부재이고 downstream `NotificationSendService:57` 이 `existsByEventId(eventId, tenantId)` 로 dedup(무조건 발송 `:59`); `ShippingStatus.ALLOWED_TRANSITIONS` 선형·self-transition 없음; event_id = `shipping_outbox` PK(`:70`).
- [x] AC-1 (A)/(B)/(C) 판정 + 근거 — **(A) 채택** (아래 § Implementation Decision). (A) 아웃박스 PK 경로 실측: 결정적 채번 → 두 번째 동시 발행 PK(23505) 충돌 → 롤백 → **기존** BE-542 DIVE 백스톱(`GlobalExceptionHandler:147-160`)이 이미 409 매핑. 새 마이그레이션/예외/에러코드 불필요.
- [x] AC-2 동시 이중 전이 → 알림 1회 (부수효과 단언) — `ConcurrentStatusTransitionIntegrationTest` 가 `ShippingStatusChanged` 아웃박스 행 **1** 단언(발행 원천에서 접힘 ⇒ notification event_id dedup 로 1회). `ManualShipConfirmRequested` 도 1(패자 전체 롤백 — wms 이중 차감 위험 덤 해소).
- [x] AC-3 정당한 서로 다른 전이는 각각 알림 — 결정적 키가 `(shippingId, newStatus)` 라 SHIPPED·DELIVERED 서로 다른 event_id(publisher 단위 `..._isDeterministicPerTransition`); notification 은 event_id(shippingId 아님)로 dedup(기존 `sendNotification_duplicateEvent_skips`) ⇒ F4 회귀 없음. notification-service 무변경.
- [x] AC-4 동시 중재자 명시 — read-then-write 앱 검사가 아니라 **DB 강제 아웃박스 PK 유니크 제약**(보존 outbox 행 ⇒ 영구 idempotency 키). IT 가 XOR(정확히 한 스레드 23505 롤백)로 단언.
- [x] AC-5 계약 표면 변경 시 선행 갱신 — `specs/contracts/events/shipping-events.md`(event_id 채번 규약) + `specs/contracts/http/shipping-api.md`(PUT status 409 DATA_INTEGRITY_VIOLATION on concurrent conflict).
- [ ] AC-6 GREEN (CI Linux 권위) — 로컬: 전체 컴파일 + `SpringShippingEventPublisherTest`(결정론 포함) GREEN. 동시성 IT 는 Testcontainers(로컬 Windows FLAKY) ⇒ **CI Linux 가 권위.**

---

## Implementation Decision (AC-1)

**채택 = (A) — `ShippingStatusChanged` event_id 를 `(shippingId, newStatus)` 에서 결정적 채번, 아웃박스 PK = event_id 결합 유지** (`UUID.nameUUIDFromBytes("ShippingStatusChanged:"+shippingId+":"+newStatus)`).

- **근거**: 결함을 **발행 원천**에서 고쳐 두 소비자의 기존 event_id dedup 을 동시에 되살린다(한쪽만 우회하는 (B) 아님). 선형·forward-only 상태기계라 한 shipment 은 각 status 에 한 번만 진입 ⇒ 같은 `(shippingId, newStatus)` 재발은 동시/재시도 중복일 때뿐 ⇒ 정당한 전이는 안 건드림. 매개자 = DB 강제 아웃박스 PK(보존 행 ⇒ 영구 idempotency 키) ⇒ 동시성 성립(AC-4).
- **아웃박스 PK / 409 판정**: 두 번째 동시 발행 PK(23505) 충돌 → 롤백 → 이벤트가 토픽 미도달. 패자 → 기존 DIVE 백스톱 → **409**. 409 는 정당한 경합 충돌 응답이며 (C) 의 `@Version` OptimisticLock 이 낼 계약과 **동일**하되 version 컬럼·마이그레이션·새 예외 없이 **이미 등록된** 에러코드 재사용 ⇒ (C) 의 무거운 계약 작업을 끌어오지 않음. 조용히 흡수(200)는 중단 트랜잭션 재도출이 필요하고 실제 충돌을 운영자에게 숨기므로 기각.
- **기각 (B)**: order 소비자에 동일 event_id 결함 방치(상태기계가 우연히 가림 — 다음 소비자에서 재발). F1 / [[feedback_workaround_becomes_the_contract]]. 매개자도 약함(read-then-write).
- **기각 (C)**: 가장 옳지만 가장 비쌈(version+마이그레이션+새 예외). 범위 밖. (A) 가 같은 클라이언트 계약을 더 싸게 달성. (A) 는 root 이중 UPDATE(같은 값, 무해)는 남기나 **피해**(중복 이벤트/알림/wms 차감)는 완전 종결. root 경합 제거가 필요하면 별 티켓.

---

## Notes

- **분량**: medium. 파일은 적게 바뀌나 채번 규약·아웃박스 PK·계약 상호작용 판정이 실질이다.
- **dependency**: `선행` = `TASK-BE-537`(done, 재현·근본원인). `잠재 후속` = (C) 를 고르면 `@Version` + 409 계약 변경 task(별건).
- **형제**: `TASK-BE-538` 의 D3 판정 — 같은 "동시 요청에서도 결정적으로 한 번만"의 배송 판. 거기 제안 문장의 적용 범위에 이 이벤트 경로가 걸리는지도 참고.
- **이 task 가 방어하는 실패 모드**: **우연한 방어를 설계로 착각하지 마라.** order 와 notification 은 같은 event_id 결함을 공유하는데 한쪽만 증상이 났다 — 상태기계가 있어서다. 증상 난 쪽만 고치면 결함은 남고, 다음에 상태기계 없는 소비자가 붙는 순간 되돌아온다. 옳은 질문은 "왜 공급원이 계속 무력한 키를 만드는가"다. [[feedback_workaround_becomes_the_contract]] [[project_enforcement_straggler_sibling_parity]] [[env_test_fixture_impossible_input_proves_nothing]]
