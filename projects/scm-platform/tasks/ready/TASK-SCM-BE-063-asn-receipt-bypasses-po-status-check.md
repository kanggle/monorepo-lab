# Task ID

TASK-SCM-BE-063

# Title

공급사 ASN 입고가 발주 상태 검사를 건너뛴다 — 전량 입고면 SUBMITTED · ACKNOWLEDGED · CANCELED 발주도 곧장 `RECEIVED`

# Status

ready

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

- [ ] **AC-0** — 재측정: 지금 각 상태(DRAFT · SUBMITTED · ACKNOWLEDGED · CONFIRMED · PARTIALLY_RECEIVED · CANCELED)에서 ASN 전량 · 부분이 무엇을 하는지 표 · 공급사 mock / 데모 시드가 CONFIRMED 전에 ASN 을 보내는 경로가 있는지 · 잘못 `RECEIVED` 가 된 행 수(데모 볼륨에서 잴 수 있으면).
- [ ] **AC-1** — 🔴 대조군: CANCELED · SUBMITTED · ACKNOWLEDGED 발주에 전량 ASN → 422 · 상태 · 줄 입고 수량 · 이력 무변경. CONFIRMED · PARTIALLY_RECEIVED 는 지금처럼 진행.
- [ ] **AC-2** — 계약과 코드가 같은 말을 한다(오류 코드 · 상태).
- [ ] **AC-3** — 배포 상태 · `TASK-MONO-810` 의 계약 각주(«ASN 이 상태 검사를 건너뛴다») 제거.

# Related Specs

- `projects/scm-platform/specs/services/procurement-service/architecture.md` § 상태 기계

# Related Contracts

- `projects/scm-platform/specs/contracts/http/procurement-api.md` § ASN webhook · § PO status transitions

# Edge Cases

- 부분 입고 뒤 남은 줄이 취소 · 같은 ASN 재전송(멱등) — 422 가 재전송 멱등성과 어떻게 만나는지 AC-0 에서.

# Failure Scenarios

- 가드를 웹훅 컨트롤러에만 둔다 → 다른 호출 경로(재시도 · 내부)가 여전히 우회.
- 422 를 내면서 ASN 행은 이미 저장 → 반쯤 반영된 입고.
