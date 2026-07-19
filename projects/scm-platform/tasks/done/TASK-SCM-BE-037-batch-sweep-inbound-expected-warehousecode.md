# Task ID

TASK-SCM-BE-037

# Title

scm 배치 sweep inbound-expected leg — IVS 가 warehouseCode 를 나르게 하여 배치기원 PO 도 wms 입고예정 발행 (ADR-MONO-050 D9 후속)

# Status

done

# Owner

scm / backend

# Task Tags

- backend
- batch
- scm
- cross-project

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

[ADR-MONO-050](../../../../docs/adr/ADR-MONO-050-scm-procurement-wms-inbound-expected.md) §7 D9 정합 중 발견된 **미결 후속**. 라이브 alert 경로는 warehouseCode 를 실어 폐루프가 닫히지만, **배치 sweep 경로(`SweepReorderUseCase`)는 IVS(inventory-visibility) read-model 에서 warehouse UUID 만 받고 코드가 없다.** 그래서 배치기원 PO 는 `destinationWarehouseId`/`destinationNodeType` 를 **omit** 하고 `inbound-expected` 를 **미발행**(fail-closed — UUID 유출은 안 함, 하지만 배치 재주문은 wms 입고예정으로 안 이어짐).

이 task 는 IVS read-model 이 `warehouseCode` 를 나르게 하여 배치기원 재주문도 폐루프에 들어오게 한다.

## 근본: IVS 는 warehouse 코드를 어디서 얻나

IVS 는 wms `inventory.{received,adjusted,transferred}.v1` mutation 이벤트를 구독해 스냅샷을 만든다. 이 mutation 이벤트들은 현재 warehouseId(UUID)만 나른다(alert 만 D9 로 warehouseCode 획득). ⇒ **cross-project 의존**: wms mutation 이벤트도 additive `warehouseCode` 를 나르거나(권장, alert 와 동형), IVS 가 자체 warehouse 코드 해석. 착수 시 실측 후 결정.

---

# Scope

## In Scope

1. **wms 의존 확정**: `inventory.{received,adjusted,transferred}.v1` 에 additive `warehouseCode` 추가(alert 의 9f24b606f 와 동형 패턴 재사용) — **또는** IVS 측 warehouse 코드 해석. (cross-project 면 원자 PR.)
2. IVS read-model(`InventorySnapshot` / node)이 `warehouseCode` 보존.
3. `SweepReorderUseCase` → `ReorderSuggestion.raiseFromBatch` 가 warehouseCode 를 세팅(현재 null) → 승인 시 `ProcurementDraftPoClient` 가 destination 코드 emit.
4. 테스트: 배치 sweep→warehouseCode 있는 suggestion→승인→PO destination=코드→inbound-expected 발행(alert 경로와 동일 결과).

## Out of Scope

- 라이브 alert 경로 변경 0 (이미 D9 로 작동).
- IVS 의 다른 노드타입(SUPPLIER/3PL/IN_TRANSIT) 0 (여전히 v2, 무접촉).

---

# Acceptance Criteria

1. 배치기원 재주문 PO 가 `destinationWarehouseId`=warehouse **코드** 로 `inbound-expected.v1` 발행(현재 미발행 해소).
2. IVS 가 warehouseCode 를 보존(wms mutation 이벤트 additive 확장 또는 IVS 해석 — 택1, 근거 문서).
3. cross-project 면 원자 PR(wms 이벤트 + scm IVS/배치).
4. alert 경로 무회귀 + `:demand-planning-service:test`/`:inventory-visibility-service:test` GREEN.

---

# Related Specs

- [ADR-MONO-050](../../../../docs/adr/ADR-MONO-050-scm-procurement-wms-inbound-expected.md) §7 D9 (배치 leg 후속 명기)
- wms `inventory-events.md`(mutation 이벤트 스키마 — warehouseCode 추가 대상 후보)
- scm `inventory-visibility-subscriptions.md`(IVS 구독)
- [TASK-SCM-BE-035] — 발견 출처

---

# Related Contracts

- wms `inventory-events.md`(`inventory.{received,adjusted,transferred}.v1`)
- scm `scm-procurement-events.md`(`inbound-expected.v1`)

---

# Target Service / Component

- scm `inventory-visibility-service` + `demand-planning-service`(SweepReorderUseCase)
- (cross-project 시) wms `inventory-service` mutation 이벤트

---

# Edge Cases

1. wms mutation 이벤트 warehouseCode 도 best-effort/nullable(스냅샷 race) — IVS 도 fail-closed(코드 없으면 배치 미발행, UUID 유출 금지).
2. IVS 스냅샷에 기존 warehouse UUID 노드가 이미 있으면 마이그레이션/backfill 고려.

---

# Failure Scenarios

## A. wms mutation 이벤트 확장이 IVS 외 소비자에 파급

→ additive 유지. IVS 외 소비자 무영향 확인.

## B. IVS 코드 자체 해석이 신규 마스터 의존 유발

→ alert 처럼 wms 이벤트가 코드를 나르는 게 더 깨끗(중복 마스터 회피). 착수 시 두 안 비교.

---

# Test Requirements

- 배치 sweep→PO destination=코드→발행 E2E-slice + alert 경로 무회귀.

---

# Definition of Done

- [ ] IVS warehouseCode 보존(경로 결정+구현)
- [ ] 배치기원 PO inbound-expected 발행(코드)
- [ ] cross-project 원자 + 무회귀

---

# Notes

- **Recommended impl model**: Opus(cross-project 이벤트 확장 + IVS/배치 배선). 선행=ADR-050 랜딩. alert 경로(라이브)는 이미 완결이라 우선순위 中(배치는 보조 경로).
