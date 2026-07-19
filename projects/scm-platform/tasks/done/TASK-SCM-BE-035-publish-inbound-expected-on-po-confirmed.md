# Task ID

TASK-SCM-BE-035

# Title

scm procurement — PO CONFIRMED/취소 시 inbound-expected 이벤트 발행 (outbox) (ADR-MONO-050 D1/D2/D4/D6)

# Status

done

# Owner

scm / backend

# Task Tags

- backend
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

[ADR-MONO-050](../../../../docs/adr/ADR-MONO-050-scm-procurement-wms-inbound-expected.md) D1/D2 구현: scm `procurement-service` 가 PO 를 `CONFIRMED` 로 전이할 때 `scm.procurement.inbound-expected.v1` 을 **transactional outbox** 로 발행(PO 상태변경과 같은 TX → 유실 0). PO 취소(비-terminal)시 `scm.procurement.inbound-expected.cancelled.v1` 발행(D6.3). 3PL 목적지는 producer-side 필터(D4).

선행=SCM-BE-034(스키마). wms lane(BE-506/507)과 병렬.

---

# Scope

## In Scope

1. PO `CONFIRMED` 전이 지점에 outbox write 추가 — 페이로드는 PO 라인 + `sku_supplier_map`(lead_time→`expectedArrivalDate`, currency) + 원 alert 의 `warehouseId`→`destinationWarehouseId`(D3). `destinationNodeType=WMS_WAREHOUSE`.
2. **D4 producer-side 필터**: 목적지가 3PL 노드면 이벤트 미발행(로그/메트릭).
3. **D6.3 취소**: PO 가 아직 미수령(비-terminal)에서 취소/철회되면 `.cancelled.v1` 발행.
4. `libs/java-messaging` outbox scaffolding 재사용(ADR-MONO-004). 토픽 매핑(`OutboxPublisher` 계열)에 `inbound-expected`→`scm.procurement.inbound-expected.v1` 등록.
5. 단위/슬라이스 테스트: CONFIRMED→발행 1건(페이로드 필드 단언), 취소→cancel 발행, 3PL 목적지→미발행, 재발행 멱등(outbox dedupe).

## Out of Scope

- wms 소비/InboundExpectation 0 (BE-507).
- 수량 amendment 0 (D6.4 v2).
- 신규 PO 상태 0 (기존 CONFIRMED 재사용).

---

# Acceptance Criteria

1. PO CONFIRMED 시 `inbound-expected.v1` outbox 발행, 페이로드 = SCM-BE-034 스키마 필드 전량(주소지정 `destinationWarehouseId` 포함).
2. 발행이 PO 상태변경과 **동일 TX**(outbox), wms 다운에도 scm TX 무영향(D8).
3. 3PL 목적지 미발행(D4) + PO 취소→cancel 발행(D6.3).
4. 테스트: CONFIRMED→발행 / 취소→cancel / 3PL→미발행 / mutation(발행줄 제거→단언 RED) 확인.
5. scm 무회귀: `:procurement-service:test` GREEN.

---

# Related Specs

- [ADR-MONO-050](../../../../docs/adr/ADR-MONO-050-scm-procurement-wms-inbound-expected.md) D1/D2/D4/D6/D8
- [TASK-SCM-BE-034](TASK-SCM-BE-034-inbound-expected-event-contract.md) — 스키마(선행)
- ADR-MONO-004 — outbox scaffolding
- ADR-MONO-027 — replenishment(이 발행의 상류: PO 는 reorder suggestion 승인 산물)

---

# Related Contracts

- `scm-procurement-events.md` (SCM-BE-034)

---

# Target Service / Component

- `projects/scm-platform/apps/procurement-service/` (PO CONFIRMED/취소 전이 + outbox)

---

# Edge Cases

1. `sku_supplier_map` 에 lead_time/currency 없는 SKU → 기본값 정책(ADR-027 D3 unmapped 처리 참조) 또는 fail-closed. 착수 시 procurement 코드 실측.
2. 원 warehouseId 부재(직접 생성 PO 등) → destinationWarehouseId 결정 규칙 명시.
3. outbox 토픽 매핑 특수케이스(OutboxPublisher special-case 패턴, wms inventory-service 선례) 동형.

---

# Failure Scenarios

## A. PO CONFIRMED 전이 지점이 여러 곳

→ 전수 확인 후 공통 지점(도메인 이벤트/상태기계)에 1회 배선. 누락 시 일부 PO 가 wms 에 안 알려짐(straggler).

## B. warehouseId 매핑 불가

→ fail-closed(미발행+ops 메트릭), 조용한 잘못된 창고 배정 금지.

---

# Test Requirements

- 발행 슬라이스(CONFIRMED/취소/3PL) + outbox 멱등. mutation 적용여부 선확인.
- `:procurement-service:test` --rerun GREEN.

---

# Definition of Done

- [ ] CONFIRMED→inbound-expected 발행(outbox, 주소지정)
- [ ] 취소→cancel 발행 + 3PL 필터
- [ ] 테스트(발행/취소/3PL/mutation)
- [ ] scm 무회귀

---

# Notes

- **Recommended impl model**: **Opus** (event-driven outbox + 상태기계 배선 + 매핑 판단). 선행=SCM-BE-034. 병렬 lane=wms BE-506/507.
