# Task ID

TASK-SCM-BE-034

# Title

scm procurement inbound-expected 이벤트 컨트랙트 — `scm.procurement.inbound-expected.v1` (+`.cancelled.v1`) 권위 스키마 (ADR-MONO-050 D1/D6/D7)

# Status

done

# Owner

scm / backend

# Task Tags

- contract
- event
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

[ADR-MONO-050](../../../../docs/adr/ADR-MONO-050-scm-procurement-wms-inbound-expected.md) D1/D7 이 정한 **권위 이벤트 스키마**를 scm 측에 작성한다. scm `procurement-service` 가 PO `CONFIRMED` 시 발행할 `scm.procurement.inbound-expected.v1` + PO 취소 시 `scm.procurement.inbound-expected.cancelled.v1` 의 봉투/페이로드를 `scm-procurement-events.md` 에 정의한다. 이 스키마는 wms `inbound-service` 가 소비한다 (BE-506 consumer-driven doc 이 이 파일을 권위로 참조).

**scm lane 의 첫 task** — impl(SCM-BE-035)의 선행. wms lane(BE-506/507)과 **병렬** (프로젝트 disjoint).

---

# Scope

## In Scope

1. `projects/scm-platform/specs/contracts/events/scm-procurement-events.md` 에 두 이벤트 추가:
   - `scm.procurement.inbound-expected.v1` — `aggregateType=purchase_order`, `aggregateId=PO id`. 페이로드(ADR-050 D1): `eventId`(UUID v7), `occurredAt`, `poId`, `poNumber`, `supplierId`, `destinationWarehouseId`, `destinationNodeType`(v1=`WMS_WAREHOUSE`), `expectedArrivalDate`, `currency`, `lines[]{skuCode, expectedQty, uom}`. 플랫폼 이벤트 봉투 규약(camelCase 10필드, `event-driven-policy.md`) 준수.
   - `scm.procurement.inbound-expected.cancelled.v1` — 취소(D6.3): `eventId`, `occurredAt`, `poId`, `poNumber`, `lines[]{skuCode}`(취소 대상). phantom expectation 방지용.
2. wms `inbound-service` 를 **sanctioned cross-project consumer** 로 1줄 명기 (문서 parity, D7).
3. 발행 트리거(D2=`CONFIRMED`)·멱등키(`eventId` + business `(poNumber,line)`)·3PL 필터(D4=producer-side `WMS_WAREHOUSE` only) 를 스키마 노트로 기술.

## Out of Scope

- impl 0 (발행 코드는 SCM-BE-035).
- wms 측 구독 doc 0 (BE-506).
- 수량 amendment 이벤트 0 (D6.4 v2-defer; v1=cancel+재발행).

---

# Acceptance Criteria

1. `scm-procurement-events.md` 에 두 이벤트 스키마 실재, ADR-050 D1 페이로드 필드 전량 + 봉투 규약(camelCase) 준수.
2. `destinationWarehouseId` 주소지정(D3) + `destinationNodeType` v1=`WMS_WAREHOUSE`(D4) 명시.
3. wms consumer 1줄 명기 (D7 parity).
4. HARDSTOP-03 무관(scm 자기 스펙). 계약 doc-only.
5. wms BE-506 이 이 파일을 권위로 참조 가능 (경로/앵커 안정).

---

# Related Specs

- [ADR-MONO-050](../../../../docs/adr/ADR-MONO-050-scm-procurement-wms-inbound-expected.md) D1/D2/D3/D4/D6/D7
- `projects/scm-platform/specs/contracts/events/replenishment-subscriptions.md` — 역할 반전 미러(scm=consumer→여기선 producer)
- `platform/event-driven-policy.md` — 봉투 규약(camelCase 10필드)

---

# Related Contracts

- `scm-procurement-events.md` (본 task 가 확장)
- wms `scm-inbound-expected-subscriptions.md` (BE-506, 이 스키마 소비)

---

# Target Service / Component

- `projects/scm-platform/specs/contracts/events/scm-procurement-events.md`

---

# Edge Cases

1. 기존 `scm-procurement-events.md` 봉투 형태 확인 후 동형 추가 (신규 형식 도입 금지).
2. `expectedArrivalDate` = `lead_time_days`(sku_supplier_map) 기반 — 스키마 노트로 출처 명기.

---

# Failure Scenarios

## A. 봉투 규약과 기존 계약 불일치 발견

→ `event-driven-policy.md` 권위. 기존 scm 이벤트가 이미 camelCase 면 동형; 불일치면 STOP+보고.

---

# Test Requirements

- 계약 doc-only, markdown lint green. 실 스키마 검증은 SCM-BE-035(발행) + SCM-INT-004(E2E).

---

# Definition of Done

- [ ] 두 이벤트 스키마 추가 (ADR-050 D1 페이로드 전량)
- [ ] D3 주소지정 + D4 nodeType + D7 consumer 명기
- [ ] doc-only diff

---

# Notes

- **Recommended impl model**: Sonnet (계약 doc). 병렬: wms BE-506 동시. 선행 없음(ADR ACCEPTED). 후속=SCM-BE-035.
