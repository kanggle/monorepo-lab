# Task ID

TASK-BE-506

# Title

wms inbound-service — scm inbound-expected 구독 컨트랙트 (`scm-inbound-expected-subscriptions.md`, consumer-driven) (ADR-MONO-050 D1/D5/D7)

# Status

done

# Owner

wms / backend

# Task Tags

- contract
- event
- wms

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

[ADR-MONO-050](../../../../docs/adr/ADR-MONO-050-scm-procurement-wms-inbound-expected.md) D7 이 정한 **consumer-driven 구독 컨트랙트**를 wms 측에 작성한다. wms `inbound-service` 가 scm `scm.procurement.inbound-expected.v1`(+`.cancelled.v1`) 을 소비하는 subset 을 문서화하고, 권위 스키마는 scm `scm-procurement-events.md`(SCM-BE-034)로 defer. 027 `replenishment-subscriptions.md` 의 역할 반전 미러(wms=consumer).

**최초 scm→wms 커플링**의 wms 측 계약. scm lane(SCM-BE-034/035)과 병렬.

---

# Scope

## In Scope

1. `projects/wms-platform/specs/contracts/events/scm-inbound-expected-subscriptions.md` 신규:
   - 구독 토픽 `scm.procurement.inbound-expected.v1` / `.cancelled.v1`, consumer group `wms-inbound-scm-expected-v1`.
   - 소비 subset 필드(D1): `eventId`, `poId`, `poNumber`, `supplierId`, `destinationWarehouseId`, `destinationNodeType`, `expectedArrivalDate`, `lines[]`.
   - 처리 시맨틱(D5/D6): `eventId` dedup + `(poNumber,line)` business dedup → `InboundExpectation`(status EXPECTED, source=SCM_PROCUREMENT) 생성 → 기존 입고 흐름 재사용. 취소→EXPECTED 를 CANCELLED(미수령시).
   - 검증(D3/D4): 미지/비활성 `destinationWarehouseId` → DLT fail-closed; `destinationNodeType != WMS_WAREHOUSE` → 방어적 reject.
   - 권위 스키마 = scm `scm-procurement-events.md` 로 defer(경로 명기).
2. wms `inbound-events.md`/README 에 이 구독 등록(있으면 index 갱신).

## Out of Scope

- impl 0 (consumer/entity/Flyway 는 BE-507).
- scm 발행 0 (SCM-BE-035).

---

# Acceptance Criteria

1. `scm-inbound-expected-subscriptions.md` 신규, 소비 subset + group + 처리 시맨틱(D5/D6) + 검증(D3/D4) 명시.
2. 권위 스키마를 scm 파일로 defer(consumer-driven; 전체 페이로드 재정의 금지).
3. 최초 scm→wms 커플링 방향 명기(양방향화, D7).
4. 계약 doc-only.

---

# Related Specs

- [ADR-MONO-050](../../../../docs/adr/ADR-MONO-050-scm-procurement-wms-inbound-expected.md) D1/D3/D4/D5/D6/D7
- scm `replenishment-subscriptions.md` / `inventory-visibility-subscriptions.md` — consumer-driven 패턴(역할 반전)
- scm `scm-procurement-events.md`(SCM-BE-034) — 권위 스키마(defer 대상)

---

# Related Contracts

- `scm-inbound-expected-subscriptions.md`(본 task 신규)
- wms `inbound-events.md`(index)

---

# Target Service / Component

- `projects/wms-platform/specs/contracts/events/scm-inbound-expected-subscriptions.md`

---

# Edge Cases

1. wms 기존 구독 doc 형태(예: notification-subscriptions.md / ecommerce-fulfillment-subscriptions.md) 확인 후 동형.
2. scm 스키마 파일 경로/앵커 안정성(SCM-BE-034 와 정합).

---

# Failure Scenarios

## A. scm 스키마 미확정 상태에서 작성

→ ADR-050 D1 이 페이로드를 고정하므로 그에 맞춰 작성(스키마 shape=ADR 권위). SCM-BE-034 와 필드 불일치시 scm 이 권위.

---

# Test Requirements

- 계약 doc-only, markdown lint green. 실 소비 검증은 BE-507 + SCM-INT-004.

---

# Definition of Done

- [ ] `scm-inbound-expected-subscriptions.md` 신규(subset+group+시맨틱+검증)
- [ ] 권위 스키마 scm defer + 최초 scm→wms 커플링 명기
- [ ] doc-only diff

---

# Notes

- **Recommended impl model**: Sonnet(계약 doc). 병렬 lane=scm SCM-BE-034 동시. 후속=BE-507.
