# Task ID

TASK-SCM-BE-063

# Title

공급사 ASN 입고가 발주 상태 검사를 건너뛴다 — 전량 입고면 SUBMITTED · ACKNOWLEDGED · CANCELED 발주도 곧장 `RECEIVED`

# Status

done

# Owner

backend

# Task Tags

- code
- bug
- scm
- test

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus (상태 기계 · 공급사 웹훅의 바뀌는 응답 — 공급사가 받는 오류가 새로 생긴다)
> 출처: `TASK-MONO-810` AC-0(2026-10-24 UTC) 조사 중 발견. 그 티켓은 «입고 완료 건수» 를 이 결함이 있는 채로 세고 계약에 적었다.

# Dependency Markers

- 선행 없음. 관련: `TASK-MONO-810`(실적 `receivedCount` 가 이 결함의 영향을 받는다).

# Goal

`PurchaseOrder.applyAsnLine`(`apps/procurement-service/src/main/java/com/example/scmplatform/procurement/domain/po/PurchaseOrder.java:301-335`)이 줄이 전량 입고되면 **현재 상태를 보지 않고** `status = RECEIVED` 로 바꾼다. `PurchaseOrderApplicationService.receiveAsn`(`:371-423`)에도 가드가 없다. 그래서 확인(CONFIRMED) 전 발주, 심지어 **취소된 발주**도 입고 완료가 된다. 계약은 422 `PO_STATUS_TRANSITION_INVALID` 를 약속한다(`specs/contracts/http/procurement-api.md:400-405`). 상태 기계(`PoStatusMachine` SYSTEM: CONFIRMED → PARTIALLY_RECEIVED/RECEIVED · PARTIALLY_RECEIVED → RECEIVED)를 통과하게 한다.

# Scope

## In Scope

- ASN 적용 경로가 `PoStatusMachine` 의 SYSTEM 전이 규칙을 지나게 한다 — 허용 안 되는 상태면 422 `PO_STATUS_TRANSITION_INVALID`, 발주 · 줄 · ASN 행 무변경.
- 여러 줄 ASN 이 이력 두 행(→ PARTIALLY_RECEIVED, → RECEIVED)을 쓰는 동작이 맞는지 AC-0 에서 정하고 계약에 적는다.
- 계약 문구(`procurement-api.md` § ASN webhook)와 코드 일치.

## Out of Scope

- 이미 잘못 `RECEIVED` 가 된 발주를 되돌리는 데이터 정정(AC-0 이 수를 세고, 있으면 별도 판단).
- SETTLED · CLOSED 전이 구현.

# Acceptance Criteria

- [x] **AC-0** — 재측정: 지금 각 상태(DRAFT · SUBMITTED · ACKNOWLEDGED · CONFIRMED · PARTIALLY_RECEIVED · CANCELED)에서 ASN 전량 · 부분이 무엇을 하는지 표 · 공급사 mock / 데모 시드가 CONFIRMED 전에 ASN 을 보내는 경로가 있는지 · 잘못 `RECEIVED` 가 된 행 수(데모 볼륨에서 잴 수 있으면).
  - → **닫힘 2026-10-24 UTC** (origin/main `59c600694`, 경로는 `apps/procurement-service` 기준).
  - **결함:** `PurchaseOrder.applyAsnLine`(`PurchaseOrder.java:301-335`)은 줄 수량을 먼저 바꾸고(`:308`), 모든 줄이 전량이면 **현재 상태를 보지 않고** `RECEIVED` 로 바꿨다(`:324-327`). `PoStatusMachine` 을 거치지 않았다.
  - **수정 전 응답:**
    - DRAFT · SUBMITTED · ACKNOWLEDGED · 🔴 **CANCELED**: 부분 ASN 은 200 에 줄 수량만 바뀌고, 전량 ASN 은 200 에 `RECEIVED` + `po.received` 발행. CANCELED 는 종결 상태를 빠져나갔다.
    - CONFIRMED · PARTIALLY_RECEIVED: 정상.
    - RECEIVED · SETTLED · CLOSED: 422 `ASN_OVERRECEIPT`.
  - **실적 지표:** `receivedCount`(`PurchaseOrderMetricsQueryImpl.java:28`)는 `→RECEIVED` 이력 행을 센다. 그래서 잘못된 입고도 세어졌다.
  - **CONFIRMED 전에 ASN 을 보내는 경로:** 시드 · mock 에는 없다(웹훅을 부르는 곳은 `AsnReceiveE2ETest` 하나이고 CONFIRMED 뒤에 보낸다).
  - ⚪ **잘못 RECEIVED 가 된 행 수:** 측정 불가. Docker 가 꺼져 있어 데모 볼륨을 못 읽었다. AC 는 «잴 수 있으면» 이다. 데이터 정정은 Out of Scope 이고, 행이 있으면 별도로 판단한다 → 다음 데모 창에서 잴 일.
  - **형제 코드:** wms `Asn.java:85-135` 는 상태를 먼저 검사하고 나서 값을 바꾼다. 같은 서비스의 `PurchaseOrder.transition()` 도 같은 순서다. 수정은 이 둘을 따랐다.
- [x] **AC-1** — 🔴 대조군: CANCELED · SUBMITTED · ACKNOWLEDGED 발주에 전량 ASN → 422 · 상태 · 줄 입고 수량 · 이력 무변경. CONFIRMED · PARTIALLY_RECEIVED 는 지금처럼 진행.
  - → `applyAsn` 이 ASN 전체를 먼저 검증한 뒤에 값을 바꾼다. 검증 순서: 줄 존재 → 양수 → 초과입고 → SYSTEM 전이.
  - 거절되면 발주 · 줄 · 이력 · ASN 행 · 감사 · outbox 어디에도 쓰지 않는다.
  - 🔵 **티켓보다 넓다:** 부분 ASN 과 DRAFT 도 422 다. 근거는 Scope 의 «허용 안 되는 상태면 422» 와 SYSTEM 규칙이다.
  - 시험 (`PurchaseOrderApplyAsnTest` 20 · `PurchaseOrderApplicationServiceTest` +5 · 웹훅 slice +1):
    - CONFIRMED · PARTIALLY_RECEIVED 대조군은 지금처럼 진행된다.
    - 상태 검사를 끄면 59건 중 정확히 거절 케이스 14건이 빨강이 된다(되돌림 후 324/324 초록).
  - MySQL IT(`AsnPoStatusGuardIntegrationTest`)는 CI 에서 돈다.
- [x] **AC-2** — 계약과 코드가 같은 말을 한다(오류 코드 · 상태).
  - → 계약은 원래부터 `PO_STATUS_TRANSITION_INVALID`(422, 예: CANCELED 에 ASN)를 약속했다(`procurement-api.md:508`). 그래서 공급사가 받는 **새** 오류 코드나 본문 형식은 없다. 코드가 계약을 따르지 않던 것을 맞췄다.
  - § ASN webhook 에 «Status guard» 표 · 검사 순서 · 전부 아니면 무 · 이력 규칙 · 재전송 규칙을 적었다.
  - 🔵 **이력:** ASN 하나당 한 행이다(여러 줄 ASN 이 CONFIRMED 를 완료하면 `CONFIRMED→RECEIVED` 한 행). 커밋된 적 없는 중간 상태를 이력에 남기지 않는다.
  - **재전송:** 받아들인 ASN 을 다시 보내면 저장본으로 200 이다. 거절된 ASN 은 저장되지 않으므로 다시 보내면 새로 평가된다.
- [ ] **AC-3** — 배포 상태 · `TASK-MONO-810` 의 계약 각주(«ASN 이 상태 검사를 건너뛴다») 제거.
  - 부분: `TASK-MONO-810` 의 `receivedCount` 각주를 교체했다 — 결함 설명 대신 «CONFIRMED / PARTIALLY_RECEIVED 만 RECEIVED 이력을 쓰고, 거절된 ASN 은 세지 않는다». 배포 상태는 굽기 뒤에 적는다.

# Related Specs

- `projects/scm-platform/specs/services/procurement-service/architecture.md` § 상태 기계

# Related Contracts

- `projects/scm-platform/specs/contracts/http/procurement-api.md` § ASN webhook · § PO status transitions

# Edge Cases

- 부분 입고 뒤 남은 줄이 취소 · 같은 ASN 재전송(멱등) — 422 가 재전송 멱등성과 어떻게 만나는지 AC-0 에서.

# Failure Scenarios

- 가드를 웹훅 컨트롤러에만 둔다 → 다른 호출 경로(재시도 · 내부)가 여전히 우회.
- 422 를 내면서 ASN 행은 이미 저장 → 반쯤 반영된 입고.

---

## CORRECTION — 34차 창 라이브 결과 (2026-10-24 UTC)

- **AC-3 — 닫힘.** 34차 창(2026-10-24 UTC): 배포 = #4523 `4763a2ac2` 가 34차 AMI `ami-0d9f8da7d20d7a180`(`d5bb28f14`)에 실렸다. 각주 제거 확인 = `projects/scm-platform/specs` 를 «건너뛴다» · «상태 검사» · bypass 로 grep 해 결함을 설명하는 각주가 0건(남은 `bypass` 는 게이트웨이 HMAC 우회 설명뿐이다). 라이브 ⚪ — 볼륨에 `CANCELED` 발주가 없어 ASN 을 보내 422 를 볼 대상이 없었다.
- (원문 체크박스는 동결 규칙(HARDSTOP-05)상 바꾸지 않았다 — 이 절이 그 AC 의 닫힘 기록이다.)
