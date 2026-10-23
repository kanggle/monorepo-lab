# Task ID

TASK-BE-651

# Title

inventory-service 가 `wms.outbound.picking.cancelled.v1` 로 예약을 풀지 못한다 — 피킹 중 취소된 출고의 예약이 `RESERVED` 로 남고 사가가 `CANCELLATION_REQUESTED` 에 멈춘다(DLT 토픽 없음 · 리스너 예외 미기록)

# Status

review

# Owner

backend

# Task Tags

- code
- test

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus (사가 보상 경로 · 생산자 ↔ 소비자 이벤트 계약 정합 · 운영자 취소까지 걸릴 수 있는 범위 · DLT 배선)
> 기안 2026-10-23 UTC — `TASK-BE-650` AC-3 라이브(33차 창)의 ❌. 서버 변경 ⇒ 배포는 **굽기**. 측정 먼저(AC-0).

# Dependency Markers

- 출처: `TASK-BE-650` § 라이브 확인 (2026-10-23 UTC · 33차 창) — AC-3 ❌ 일부 실패.
- 관련: `TASK-BE-437`(done — 같은 inventory 소비자 무리의 `outbound.shipping.confirmed` 가 낡은 `pickingRequestId` 모양을 읽어 실제 이벤트마다 NPE 했던 결함, 귀로 형제) · `TASK-BE-431`(done — 정방향 `picking.requested` 형제) · `TASK-BE-650`(review — X-A 자동 취소가 이 보상 경로를 탄다) · `TASK-BE-649`(review — 이커머스 쪽 취소 요청 생산자).
- 후속: 고친 뒤 다음 굽기 · 창에서 `TASK-BE-650` AC-3 의 «예약 해제» 를 다시 잰다(`TASK-MONO-672`).
- 선행: 없음. 🔴 이 티켓이 닫히기 전에는 예약을 잡은 채 취소되는 출고마다 예약이 남을 수 있다 — 데모 창에서 취소를 시험하면 SKU `reservedQty` 가 쌓인다.

# Goal

Fix issue found in TASK-BE-650. 출고가 예약을 잡은 상태에서 취소되면 outbound-service 는 `wms.outbound.picking.cancelled.v1` 을 내고(`apps/outbound-service/.../application/service/CancelOrderService.java:137-145`), inventory-service `PickingCancelledConsumer` 가 그 예약을 사유 `CANCELLED` 로 풀어 `inventory.released` 를 내야 한다 → 사가 `CANCELLATION_REQUESTED → CANCELLED`. 33차 창(2026-10-23 UTC)에서 이 해제가 일어나지 않았다:

- 출고 `01a168e0-1a3a-…`(orderNo `49eae2f6…`) 가 `PICKING` 에서 자동 취소(`FulfillmentCancelRequestedConsumer fulfillment_cancel_applied … previousStatus=PICKING`, `CancelOrderService order_cancelled … reservationHeld=true`) — 여기까지는 X-A 대로.
- 사가가 `CANCELLATION_REQUESTED` 에 멈춤(13:22 sweeper 재발행 1회). inventory `reservation 2f397b48-… status=RESERVED`, SKU …0013 `reservedQty=1`.
- inventory-service 가 그 메시지를 4회 재시도 뒤 DLT 로 보냈으나 DLT 토픽 `wms.outbound.picking.cancelled.v1.DLT` 가 **없다**(UNKNOWN_TOPIC). 예외 본문은 로그에 **안 찍혔다**.
- 메시지 페이로드 `reservationId = 01a168e0-1a3e-75f9-b61f-66166cd03ba1`(= sagaId) 인데 inventory 의 실제 예약 id 는 `2f397b48-…` 이고, 그 sagaId 값은 예약의 `picking_request_id` 칼럼에 있다. 창 기록은 «불일치가 유력 원인(추정, 소비자 코드 미확인)».

이 경로는 BE-650 이 새로 만든 것이 아니라 **기존 `CancelOrderUseCase` 보상 경로**다 — 운영자 취소로 피킹 중 출고를 취소해도 같은 결과일 가능성이 높다(AC-0 ② 에서 잰다).

## 기안 시 코드 읽기 (실행 안 함 — AC-0 이 확정한다)

- 소비자 `apps/inventory-service/.../adapter/in/messaging/outbound/PickingCancelledConsumer.java:111` — `UUID.fromString(payload.get("pickingRequestId").asText())`.
- 생산자 `apps/outbound-service/.../adapter/out/event/publisher/EventEnvelopeSerializer.java:153-163` — 페이로드는 `sagaId` · `reservationId` · `orderId` · `reason` 뿐이고, 주석이 «The legacy `pickingRequestId` duplicate is intentionally omitted» 라고 적는다. 그 `reservationId` 값 = `saga.pickingRequestId()`(`CancelOrderService.java:139-141`).
- ⇒ 페이로드에 `pickingRequestId` 가 없으면 `payload.get(...)` 은 `null` → `.asText()` 에서 `NullPointerException` → `config/KafkaConsumerConfig.java:63-71` 의 재시도(비재시도는 `IllegalArgumentException` 하나) → `<topic>.DLT`. 창의 «4회 재시도 뒤 DLT» 와 모양이 같다. 정방향 형제 `PickingRequestedConsumer.java:156` 은 이미 `reservationId` 를 `pickingRequestId` 로 읽는다(`TASK-BE-431` 이 고친 모양) — 창 기록의 «id 불일치» 는 키 이름의 불일치일 수 있다.
- 소비 쪽 계약 `specs/contracts/events/inventory-events.md:508-526` § C3 이 아직 `payload.pickingRequestId` · `warehouseId` 를 적는다 — `TASK-BE-437` 이 § C4 에서 고친 것과 같은 낡음. 생산자 정본은 `outbound-events.md` § 4(`:331`).
- `apps/inventory-service/src/test` 에 이 소비자의 시험이 없다(`git ls-files` 에서 `cancel` 0 건).
- 토픽 선생성 목록(`projects/wms-platform/docker-compose.yml`, `wms.outbound.picking.cancelled.v1` 은 `:211`)에 `.DLT` 토픽이 0 건 — 데모 · 운영 브로커의 자동 생성 설정은 AC-0 ③.

# Scope

## In Scope

- **AC-0 측정** (file:line · 실행 출력):
  - ① 원인 확정 — 생산자 직렬화기의 실제 출력(또는 데모 토픽의 한 건)으로 소비자를 돌려 예외 종류와 줄. 위 «코드 읽기» 가 맞는지, 아니면 창 기록의 «id 불일치» 가 따로 있는지.
  - ② 운영자 취소도 같은가 — REST 운영자 취소로 `PICKING` 출고를 취소하면 같은 메시지 · 같은 실패인가(코드 경로 + 가능하면 IT). 영향 범위: 예약을 잡은 채 취소되는 모든 출고(`RESERVATION_HELD_STATES`).
  - ③ DLT — inventory-service 가 소비하는 다른 토픽들의 `.DLT` 가 존재하는가(선생성 · 자동 생성 설정 · 데모 브로커). 없을 때 `DeadLetterPublishingRecoverer` 가 무엇을 하는가(메시지 유실 · 오프셋 커밋 여부).
  - ④ 로그 — 재시도 · DLT 경로에서 예외 본문이 왜 안 찍히는가(`DefaultErrorHandler` 로그 수준 · 로거 설정).
  - ⑤ 이미 멈춘 사가 · 남은 예약 — 고친 뒤 sweeper 재발행(`SagaSweeper.java:99`, 같은 생산자 모양)이 그것들을 풀어 주는가.
- 수선: 계약 먼저(`inventory-events.md` § C3 를 생산자 § 4 모양으로) → 소비자 · 시험 · DLT · 로그는 AC-0 결과대로.
- 시험: 생산자 직렬화기의 실제 출력을 소비자에 넣는 왕복 시험(낡은 계약 모양이 다시 못 들어오게 — `TASK-BE-437` 방식) · 예약 해제 → `inventory.released` · 이미 끝난 예약 no-op · 같은 `eventId` 1회.

## Out of Scope

- X-A 정책(포장 뒤 자동 취소 거부) — `TASK-BE-650`.
- 이커머스 쪽 취소 · 환불 — `TASK-BE-649` · `TASK-BE-646`.
- 사가 상태 기계 재설계.

# Acceptance Criteria

- [x] **AC-0** — 위 ①~⑤ 를 file:line · 실행 출력으로. ① 의 원인이 «코드 읽기» 와 다르면 그 차이를 적고 수선 범위를 다시 정한다. ③ 에서 DLT 부재가 다른 소비자에도 해당하면 범위(이 티켓 / 별도)를 소유자에게 묻는다. ✅ 2026-10-24 UTC — ③ 의 DLT 접미사 충돌은 소유자 선택창 «별도 루트 티켓으로» → `TASK-MONO-813`(저장소 전체가 `.dlq` = 이커머스 · iam · fan / `.DLT` = erp · finance · scm · wms 로 갈림, platform 규칙은 `.dlq`). 이 티켓 범위는 소비자 수정 · 원인 로그까지.
- [x] **AC-1** — 계약 먼저(`inventory-events.md` § C3) → 구현 + 시험(시험만 담은 커밋으로 CI 빨강을 먼저 본다) · CI 의 wms 잡 초록. ✅ 2026-10-24 UTC (메인 세션) — PR #4503. 계약 `0e8a8df44` → 시험만 담은 커밋 `6df523c63` 의 CI run `39973453427` 에서 `inventory-service:test` 271건 중 11건 빨강(**동작 실패** — `PickingCancelledConsumerTest` 8 · `KafkaConsumerConfigErrorLoggingTest` 3). 구현 `a983e2c75` · main 병합 `76b55c0c0` 뒤 head 체크 67개 pending 0 · fail 0(`Integration (inventory + inbound + gateway-service)` pass 포함).
- [ ] **AC-2** — 굽기 뒤 라이브: 피킹 중(`PICKING`) 출고 하나를 취소 → 예약 `RELEASED` · SKU `reservedQty` 원복 · 사가 `CANCELLED` · inventory 로그에 실패 없음(다음 데모 창). 33차 창에서 멈춘 사가가 볼륨에 남아 있으면 그것도 풀리는가 — 굽기가 볼륨을 새로 만들면 ⚪ + 이유.

# Related Specs

- `projects/wms-platform/specs/services/inventory-service/architecture.md`
- `projects/wms-platform/specs/services/inventory-service/idempotency.md`
- `projects/wms-platform/specs/services/inventory-service/sagas/reservation-saga.md`
- `projects/wms-platform/specs/services/outbound-service/workflows/outbound-flow.md` (취소 보상)

# Related Contracts

- `projects/wms-platform/specs/contracts/events/outbound-events.md` § 4 `outbound.picking.cancelled` (생산자 정본)
- `projects/wms-platform/specs/contracts/events/inventory-events.md` § C3 (소비 모양 — 낡음, 고친다)

# Edge Cases

- 예약이 이미 끝 상태(`RELEASED` TTL · `CONFIRMED`) — 지금처럼 WARN no-op.
- 같은 `eventId` 재전달 · sweeper 재발행(같은 사가) — 해제는 한 번.
- `picking.requested` 가 아직 소비되지 않아 예약이 없을 때 취소가 먼저 온다 — 지금은 WARN «unknown pickingRequestId» 후 무시. 그 뒤 예약이 생기면 남는다(AC-0 에서 도달 가능성만 적는다).

# Failure Scenarios

1. 소비자 시험을 계약 문서 모양(손으로 쓴 JSON)으로 써서 초록 — 생산자는 다른 모양을 낸다. `TASK-BE-437` 이 같은 함정을 기록했다 → 생산자 직렬화기의 실제 출력으로 시험.
2. 키 이름만 고치고 DLT 부재를 남겨, 다음 재시도 소진 실패가 다시 조용히 사라진다 → AC-0 ③ · ④.
3. 운영자 취소 경로를 «BE-650 만의 문제» 로 보고 범위를 좁힌다 → AC-0 ②.

---

# 구현 기록 (2026-10-23 UTC · 분석·구현=Opus 5.5 · 브랜치 `task/be-651-release-reservation`)

커밋: A `0e8a8df44`(계약) · B `6df523c63`(시험만 — 수정 전 빨강) · C `a983e2c75`(수정).

## AC-0 — 🟡 ①②④⑤ 코드 + 실행으로 확정 · ③ 은 소유자 결정 1건이 남는다

**① 원인 = «코드 읽기» 그대로. 따로 있는 «id 불일치» 는 없다.** 실행 출력(커밋 B, 수정 전):
`PickingCancelledConsumerTest` 9건 중 8건이 `NullPointerException: Cannot invoke "JsonNode.asText()" because the return value of "JsonNode.get(String)" is null` @ `PickingCancelledConsumer.applyCancel(PickingCancelledConsumer.java:111)`, 나머지 1건(«reservationId 없음 → IAE») 은 같은 NPE 를 받아 빨강.
- 생산자: `EventEnvelopeSerializer.java:153-163` 은 `sagaId · reservationId · orderId · reason` 만 쓴다. 값 `reservationId = saga.pickingRequestId()`(`CancelOrderService.java:139-141`). 정방향 `picking.requested` 도 같은 값을 `reservationId` 로 낸다(`ReceiveOrderService.java:282-284`) → inventory 는 그것을 예약의 `picking_request_id` 로 저장(`PickingRequestedConsumer.java:156`).
- 창 기록의 «reservationId(01a168e0…)= sagaId 인데 예약 id 는 2f397b48…» 은 **불일치가 아니다**: 사가는 `pickingRequestId` 를 자기 id 로 시작한다(`OutboundSaga.java:78` `sagaId /* pickingRequestId == sagaId in v1 scope */`), 그리고 그 값이 inventory `reservation.picking_request_id` 칼럼에 있다(창 실측과 일치). 소비자가 `findByPickingRequestId(reservationId)` 로 찾으면 정확히 그 예약이 나온다 — 키 **이름** 만 틀렸다.
- 계약: 생산자 정본 `outbound-events.md` § 4 가 이미 `reservationId` 를 적는다 ⇒ 생산자는 맞고 소비 쪽 § C3 이 낡았다(BE-431 § C2 · BE-437 § C4 와 같은 낡음). **수선 방향 = 소비자**(생산자 무변경).

**② 운영자 취소도 같은 경로 · 같은 실패다(코드 경로로 확정, IT 는 안 돌렸다 — Docker 없음).** `outbound.picking.cancelled` 를 내는 곳은 둘뿐이다:
- `CancelOrderService.java:138-146` — 운영자 REST(`OrderController.java:93`) 와 이커머스 취소 요청 소비자(`FulfillmentCancelRequestedConsumer.java:184`, BE-650) 가 **같은** `CancelOrderUseCase` 를 부른다. 발행 조건 = 사가가 `RESERVATION_HELD_STATES`(`RESERVED · PICKING_CONFIRMED · PACKING_CONFIRMED`, `:57-58`).
- sweeper 재발행 — `SagaSweeper.java:99` → `OutboxReEmitterAdapter.java:66-91` 가 **원래 outbox 행의 페이로드를 그대로 복제**(eventId · actorId · occurredAt 만 바꿈) ⇒ 같은 모양.
⇒ 영향 범위 = 예약을 잡은 채 취소된 **모든** 출고(운영자 · 이커머스 · sweeper). BE-650 만의 문제가 아니다.

**③ DLT — 🔴 «토픽 없음» 은 이 브로커에서 «레코드 유실» 이 아닐 공산이 크다. 그리고 선생성은 사양 충돌 때문에 이 티켓에서 고치지 않았다(소유자 결정).**
- 선생성 목록(`docker-compose.yml:187-240` `kafka-init`)은 모든 wms 토픽에 **`.dlq`**(소문자) 를 만든다(`:239`). 그런데 wms 의 **모든** 서비스 오류 처리기는 **`.DLT`** 로 보낸다 — inventory `KafkaConsumerConfig.java:38`, outbound `KafkaConfig.java:38`, inbound `KafkaConsumerConfig.java:38`, notification `KafkaConsumerConfig.java:36`, admin `ProjectionKafkaConsumerConfig.java:42`. ⇒ `.DLT` 를 선생성한 토픽은 **0 건**이고 `.dlq` 29 개에는 쓰는 쪽이 없다. inventory 가 소비하는 토픽 전부(`picking.requested · picking.cancelled · shipping.confirmed · inbound.putaway.completed · master.{warehouse,zone,location,sku,lot} · admin.settings`) 가 같은 처지 — picking.cancelled 만의 결함이 아니다.
- 🔴 **사양이 둘로 갈려 있다**: `platform/event-driven-policy.md:20,113` · `platform/service-types/event-consumer.md:35` · `platform/glossary.md:55` = `<topic>.dlq`, 반면 wms 계약 전부(`inventory-events.md:78,637`, `outbound-events.md:121,719`, `master-events.md:79`, `admin-events.md:107,529` …) 와 코드 = `<topic>.DLT`. 우선순위로는 platform(5) 이 project contracts(6) 위다. `kafka-init` 의 `.dlq` 는 platform 을, 코드는 계약을 따른 결과다. **어느 쪽으로 맞출지는 이 티켓의 범위(예약 해제)를 넘는 결정이라 손대지 않았다** → 소유자 결정(아래 «열린 질문»).
- 이 브로커에서의 실제 동작: 브로커는 `auto.create.topics.enable` 기본값(true, `docker-compose.yml:107-139` 에 끄는 설정 없음)이다. 실측 선례 — `TASK-MONO-706` 이 데모 브로커에서 `wms.outbound.shipping.confirmed.v1.DLT` 를 **파티션 3개**(0·1·2 = `KAFKA_NUM_PARTITIONS: 3`, 선생성이라면 `DLQ_PARTITIONS=1`)로 읽었고 레코드 1건이 들어 있었다 ⇒ `.DLT` 는 첫 송신 때 **자동 생성되고 레코드는 남는다**. 창의 «UNKNOWN_TOPIC» 은 KRaft 브로커가 자동 생성 중 첫 메타데이터 요청에 돌려주는 경고와 모양이 같다. ⚪ 이번 레코드가 실제로 `wms.outbound.picking.cancelled.v1.DLT` 에 있는지는 **안 쟀다**(창이 없다) — 다음 창에서 `kafka-get-offsets.sh --topic wms.outbound.picking.cancelled.v1.DLT` 로 확인.
- DLPR 동작(spring-kafka 3.3.1): 송신 성공 → 오프셋 커밋(레코드는 DLT 에), 송신 실패 → `recoveryFailed` → 같은 레코드 재전달(유실 아님, 대신 파티션이 막힌다). 이번 수정으로 둘 다 ERROR 로 찍힌다.

**④ 예외 본문이 안 찍힌 이유 — 실측(javap, `spring-kafka-3.3.1.jar`).** `KafkaMessageListenerContainer$ListenerConsumer` 는 `RecordInRetryException` 을 받으면 `logger.info("Record in retry and not yet recovered")` 만 부른다(원인 미첨부, `ldc #1920` → `LogAccessor.info(CharSequence)`). `FailedRecordTracker` 는 recoverer 가 **없을 때만** «Backoff … exhausted» 를 ERROR 로 찍고, DLPR 가 있으면 송신 성공 시 INFO 이상 출력이 없다. 서비스 쪽 로거 설정(`application.yml:94-99`, `logback-spring.xml`) 은 원인이 아니다(root INFO). 수정 전 시험 출력: `KafkaConsumerConfigErrorLoggingTest` 3건 — `[WARN|ERROR lines on the KafkaConsumerConfig logger] Expecting actual not to be empty`(재시도 경로 · DLT 송신 자체는 이미 일어난 상태에서).

**⑤ 이미 멈춘 사가 · 남은 예약.** sweeper 재발행은 같은 페이로드(`reservationId`)를 새 eventId 로 다시 내므로, 수정된 소비자가 받으면 예약을 풀고 `inventory.released`(`pickingRequestId` 회신 — `ReleaseReservationService.java:122-124`) → 사가 `CANCELLED`. 단 상한 `max-attempts: 5`(`outbound application.yml:137`) 를 넘긴 사가는 `STUCK_RECOVERY_FAILED`(끝 상태)라 더는 재발행되지 않는다 — 그 경우 예약은 예약 TTL(기본 86 400 s, `PickingRequestedConsumer.java:158-159`) 이 `EXPIRED` 로 푼다. 33차 창의 사가는 그 창이 끝날 때까지 상한에 닿았을 공산이 크고, 굽기가 볼륨을 새로 만들면 그 사가 자체가 없다 → AC-2 에서 ⚪ + 이유로 적힐 자리.

**Edge «취소가 예약보다 먼저»** — 도달 가능: 두 이벤트는 다른 토픽이라 순서 보장이 없다. 소비자는 WARN 후 무시(`PickingCancelledConsumer.java:119-122`), 뒤이어 온 `picking.requested` 가 예약을 만들면 그 예약은 TTL 까지 남는다. 🔵 `sagas/reservation-saga.md:247` 은 이 경우를 «cancel → DLT» 로 적어 코드와 다르다(이 티켓 이전부터) — 동작은 바꾸지 않았다.

## 수선 (커밋 A · C)

- **A** `inventory-events.md` § C3 = 생산자 § 4 모양(`sagaId · reservationId · orderId · reason`), `reservationId` → 예약의 `picking_request_id`, `sagaId` 로는 찾지 않음, 키 없음 = 비재시도 IAE. `outbound-events.md` § 4 의 `sagaId` 칸이 «inventory 가 이걸로 예약을 찾는다» 고 적어 바로 아래 칸과 모순 → 고침(모양 무변경).
- **C** `PickingCancelledConsumer.java:118` `requireUuid(payload, "reservationId")`(`:146`) — 없거나 문자열 아님 → `IllegalArgumentException`(비재시도 → 바로 DLT, NPE 로 재시도 4회를 태우지 않음).
- **C** `KafkaConsumerConfig.java:80,95` `CauseLoggingRetryListener` — 실패 전달마다 WARN(`kafka_listener_failed topic partition offset attempt cause` + 스택), DLT 로 보낼 때 ERROR(`kafka_listener_dead_lettered … dlt=…`), DLT 송신 실패 ERROR. 키 · 값은 찍지 않는다.
- **하위 호환 판단 — 소비자에 `pickingRequestId` 폴백을 넣지 않았다.** 이 토픽의 생산자는 그 키를 한 번도 낸 적이 없으므로(`EventEnvelopeSerializer.java:156-157` 주석) 폴백이 살릴 메시지가 없다. 이미 낸 메시지(원본 토픽 · sweeper 복제 · DLT 재처리)는 전부 `reservationId` 모양이라 수정된 소비자가 그대로 읽는다. 폴백은 «낡은 계약 모양» 을 다시 받아들이는 문이 될 뿐이다(Failure Scenario 1). `sagaId` 폴백도 넣지 않았다 — v1 에서 값이 같을 때가 많지만 `OutboundSaga.bindPickingRequest`(`:235`) 가 다시 묶을 수 있어 보장되지 않는다.
- 데이터 마이그레이션 없음 · 계약 모양 변경 없음 · 다른 프로젝트 무관.

## 시험 (커밋 B, C 에서 1건 조정)

- `PickingCancelledConsumerTest`(9) — 실제 `ReleaseReservationService` 까지. 픽스처 필드 집합 = `outbound-events.md` § 4 JSON 블록(저장소에서 읽음), `EventEnvelopeSerializerPickingCancelledTest`(outbound, 1) 가 실제 직렬화기 출력 = 같은 블록을 단언 ⇒ 직렬화기 = 계약 = 소비자 픽스처(서비스 모듈끼리 의존할 수 없어 계약 블록을 다리로 썼다).
- `KafkaConsumerConfigErrorLoggingTest`(3) — 실제 `DefaultErrorHandler` 빈. C 에서 조정: 로그에 붙는 예외는 컨테이너가 감싼 `ListenerExecutionFailedException` 이라 «최상위 = NPE» 가 아니라 «원인 사슬에 NPE» 로 본다.

## 로컬 실행 (2026-10-23 UTC · Windows 호스트 · Docker 없음)

- 수정 전(B): 새 시험 12건 중 11건 빨강(위 ①④ 출력), outbound 직렬화기 시험 1건 초록.
- 수정 후(C): `./gradlew :projects:wms-platform:apps:inventory-service:test :projects:wms-platform:apps:outbound-service:test` → `BUILD SUCCESSFUL`, rc=0. JUnit XML 합계 inventory **271 tests · 0 failures · 0 errors · 0 skipped**, outbound **322 tests · 0 failures · 0 errors · 4 skipped**(기존 skip).
- ⚪ **안 돌린 것**: `@Tag("integration")` Testcontainers IT 전부(`integrationTest` 태스크) — Docker 데몬 없음(`docker info` → `npipe … cannot find the file`). 운영자 취소 IT(AC-0 ② «가능하면 IT») 도 같은 이유로 없음.
- 가드: `check-cross-project-topic-relay.sh` rc=0 · `check-index-queue-drift.sh` rc=0 · `check-task-id-collision.sh` rc=0 · `check-walkthrough-ledger-drift.sh` rc=0(스테이지 후).

## 열린 질문 (소유자)

1. **DLT 철자 — `.dlq`(platform) 대 `.DLT`(wms 계약 · 코드).** `kafka-init` 이 만드는 29개 `.dlq` 는 아무도 쓰지 않고, 실제 쓰는 `.DLT` 는 자동 생성에 기대고 있다. (a) platform 을 `.DLT` 로 고치거나 wms 를 platform 의 예외로 `## Overrides` 에 적고 `kafka-init` 을 `.DLT` 로 바꾼다, (b) 다섯 서비스의 접미사를 `.dlq` 로 바꾼다(계약 다수 · 운영 습관 · DLT 를 읽는 IT 들이 바뀐다), 중 무엇인가. 이 티켓에서는 안 했다(사양 충돌 = 범위 밖).
2. **다른 wms 서비스의 같은 로그 공백.** outbound `KafkaConfig` · inbound `KafkaConsumerConfig` · notification `KafkaConsumerConfig` · admin `ProjectionKafkaConsumerConfig` 도 RetryListener 가 없다(같은 증상: 원인 없는 INFO 한 줄). 이 티켓은 inventory 만 고쳤다 — 별도 티켓 여부.

## 남은 것

- AC-1 «CI 의 wms 잡 초록» — 푸시 전이라 미측정.
- AC-2 라이브 — 다음 굽기 · 창(`TASK-MONO-672`). 창에서 함께 볼 것: `wms.outbound.picking.cancelled.v1.DLT` 의 끝 오프셋(③ 의 «유실 아님» 가설), inventory 로그에 `kafka_listener_failed` 가 없는지.
