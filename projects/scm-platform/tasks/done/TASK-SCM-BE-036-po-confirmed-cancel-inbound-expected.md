# Task ID

TASK-SCM-BE-036

# Title

scm procurement — CONFIRMED→CANCELED 상태 전이 허용 + 확정후취소 시 inbound-expected-cancelled 발행 (ADR-MONO-050 D6.3 후속)

# Status

done

# Owner

scm / backend

# Task Tags

- backend
- state-machine
- scm

---

# Required Sections

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Goal

[ADR-MONO-050](../../../../docs/adr/ADR-MONO-050-scm-procurement-wms-inbound-expected.md) D6.3 의 **미결 후속**을 종결한다. ADR-050 impl(SCM-BE-035) 착수 중 발견: PO 상태기계가 **`CONFIRMED → CANCELED` 를 금지**한다(DRAFT/SUBMITTED/ACKNOWLEDGED 만 취소 가능). 그런데 `inbound-expected.v1` 은 `CONFIRMED` 에 발행되므로 — **정작 wms 입고예정을 유령으로 만드는 "확정 후 취소" 케이스가 v1 에서 도달 불가**하다. 현재 `.cancelled.v1` 은 pre-CONFIRMED 취소 경로에만 붙어있고(wms 무해 no-op), 진짜 필요한 경로(확정 후 취소)는 열려있지 않다.

이 task 는 상태기계에 `CONFIRMED → CANCELED`(미수령 한정)를 추가하고, 그 전이에서 `scm.procurement.inbound-expected.cancelled.v1` 을 발행해 wms 가 EXPECTED ASN 을 CANCELLED 처리하도록 한다. wms 소비 측(BE-507 CancelScmInboundExpectationService)은 이미 구현돼 있음 — 발행 측만 열면 폐루프 취소가 실동작.

---

# Scope

## In Scope

1. procurement PO 상태기계에 `CONFIRMED → CANCELED` 전이 추가 — **미수령(not-yet-RECEIVED) 한정**(RECEIVED 후 취소는 별개 반품 도메인, 범위 밖).
2. 그 전이에서 `inbound-expected.cancelled.v1` 발행(기존 `publishInboundExpectedCancelled` 재사용, warehouse-addressed PO 한정).
3. `po.canceled` 계약 + procurement architecture 의 상태기계 문서 갱신(CONFIRMED→CANCELED 허용 반영).
4. 테스트: CONFIRMED PO 취소→cancel 발행 + 상태=CANCELED / RECEIVED PO 취소→여전히 거부 / wms no-op 계약 유지.

## Out of Scope

- wms 소비 측 변경 0 (BE-507 CancelScmInboundExpectationService 이미 구현).
- RECEIVED 후 반품/역물류 0 (별 도메인).
- 수량 amendment 0 (D6.4, 여전히 v2).

---

# Acceptance Criteria

1. 상태기계가 `CONFIRMED → CANCELED`(미수령) 허용, RECEIVED 는 여전히 취소 불가.
2. 확정후취소 시 `inbound-expected.cancelled.v1` 발행(warehouse-addressed PO), wms 가 EXPECTED→CANCELLED.
3. `po.canceled` 계약 + 상태기계 문서 정합.
4. `:procurement-service:test` GREEN + 전이/발행/거부 케이스 테스트.

---

# Related Specs

- [ADR-MONO-050](../../../../docs/adr/ADR-MONO-050-scm-procurement-wms-inbound-expected.md) D6.3
- [TASK-SCM-BE-035](TASK-SCM-BE-035-publish-inbound-expected-on-po-confirmed.md) — 발견 출처(선행)
- wms `scm-inbound-expected-subscriptions.md`(BE-506) — 소비 측 취소 시맨틱

---

# Related Contracts

- `scm-procurement-events.md`(`inbound-expected.cancelled.v1`, `po.canceled`)

---

# Target Service / Component

- `projects/scm-platform/apps/procurement-service/` (PO 상태기계 + cancel 전이 발행)

---

# Edge Cases

1. CONFIRMED→CANCELED 허용이 기존 `po.canceled` 소비자에 파급되는지 확인(상태 전이 넓힘).
2. wms 가 이미 수령 완료한 뒤 cancel 도착 → wms no-op(BE-507 이미 처리), scm 은 미수령만 취소하므로 정상 케이스 아님.

---

# Failure Scenarios

## A. 상태기계 확장이 다른 전이 규칙과 충돌

→ CONFIRMED→CANCELED 만 추가, 나머지 불변. 회귀 테스트로 가드.

---

# Test Requirements

- CONFIRMED 취소→cancel 발행+상태 / RECEIVED 취소 거부 / mutation 확인. `:procurement-service:test` GREEN.

---

# Definition of Done

- [ ] CONFIRMED→CANCELED(미수령) 전이 + cancel 발행
- [ ] `po.canceled`/상태기계 문서 정합
- [ ] 테스트 + 무회귀

---

# Notes

- **Recommended impl model**: Opus(상태기계 전이 + 계약 파급). 선행=ADR-050 랜딩(SCM-BE-035). wms 소비 측 이미 구현됨.
