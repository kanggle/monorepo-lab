# Task ID

TASK-BE-651

# Title

inventory-service 가 `wms.outbound.picking.cancelled.v1` 로 예약을 풀지 못한다 — 피킹 중 취소된 출고의 예약이 `RESERVED` 로 남고 사가가 `CANCELLATION_REQUESTED` 에 멈춘다(DLT 토픽 없음 · 리스너 예외 미기록)

# Status

ready

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

- [ ] **AC-0** — 위 ①~⑤ 를 file:line · 실행 출력으로. ① 의 원인이 «코드 읽기» 와 다르면 그 차이를 적고 수선 범위를 다시 정한다. ③ 에서 DLT 부재가 다른 소비자에도 해당하면 범위(이 티켓 / 별도)를 소유자에게 묻는다.
- [ ] **AC-1** — 계약 먼저(`inventory-events.md` § C3) → 구현 + 시험(시험만 담은 커밋으로 CI 빨강을 먼저 본다) · CI 의 wms 잡 초록.
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
