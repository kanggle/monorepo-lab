# Task ID

TASK-SCM-BE-038

# Title

scm demand-planning — alert warehouseCode 는 additive(선택), 부재 시 DLT 금지 (ADR-MONO-050 D9 producer/consumer 비대칭 수정)

# Status

done

# Owner

scm / backend

# Task Tags

- backend
- correctness
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

[ADR-MONO-050](../../../../docs/adr/ADR-MONO-050-scm-procurement-wms-inbound-expected.md) §7 D9 롤아웃이 만든 **producer/consumer 비대칭**을 바로잡는다. SCM-INT-004 E2E 작성 중 발견:

- wms producer(`LowStockDetectionService`)는 `warehouseCode` 를 **best-effort/nullable** 로 발행(warehouse-master 스냅샷 race 시 null).
- scm consumer(`WmsLowStockAlertConsumer`)는 이를 **mandatory** 로 취급 → blank 이면 `NonRetryableConsumerException` → **DLT**.

⇒ warehouse 마스터가 inventory-service 에 아직 안 채워진 상태에서 alert 이 뜨면 scm 이 그 alert 을 DLT 하여 **기존 ADR-027 재주문 루프까지** 해당 SKU 에 끊긴다(신규 inbound-expected leg 뿐 아니라). "additive 필드" 정의(하위호환·구 소비자 무시)와 정면 모순.

**본 changeset(ADR-050 통합 브랜치)에서 이미 수정** — graceful degrade: warehouseCode 부재 시 WARN + null 로 통과, 재주문 제안은 그대로 발생, downstream inbound-expected leg 만 null-code 로 fail-close(배치 sweep 경로와 동일). 이 티켓은 그 수정의 추적·검증 근거.

---

# Scope

## In Scope (본 changeset 적용됨)

1. `WmsLowStockAlertConsumer`: warehouseCode 부재 시 throw/DLT 제거 → WARN + `warehouseCode=null` 로 `evaluateFromAlert` 호출.
2. CI 가드 테스트: `AlertConsumerIntegrationTest.alertWithoutWarehouseCode_stillRaisesSuggestion_degradesGracefully_BE038` (부재→suggestion 발생·warehouseCode null·DLT 아님) + `AbstractDemandPlanningIntegrationTest.alertEnvelope` nullable 오버로드.

## Out of Scope

- wms producer 변경 0 (best-effort 발행은 정당 — 부재 원인은 스냅샷 race, SCM-BE-037 이 mutation 경로 warehouseCode 로 별도 해소).
- downstream null-code fail-close 0 (scm reconcile 에서 이미 구현, 배치 경로 테스트가 커버).

---

# Acceptance Criteria

1. warehouseCode 부재 alert → 재주문 suggestion 발생(warehouseCode=null), **DLT 아님**.
2. warehouseCode 존재 alert → 기존대로 코드 threaded(무회귀).
3. downstream inbound-expected leg 는 null-code 에 fail-close(제안·PO 는 정상, wms 입고예정만 미발행) — 배치 경로와 동형.
4. `:demand-planning-service:test` GREEN(신규 가드 케이스 포함, CI Linux 권위).

---

# Related Specs

- [ADR-MONO-050](../../../../docs/adr/ADR-MONO-050-scm-procurement-wms-inbound-expected.md) §7 D9 (additive 정의)
- [TASK-SCM-INT-004](TASK-SCM-INT-004-inbound-expected-cross-service-e2e.md) — 발견 출처
- ADR-MONO-027 (보호 대상 기존 루프)

---

# Related Contracts

- wms `inventory-events.md`(`wms.inventory.alert.v1` warehouseCode = additive/optional 명확화 후보)

---

# Target Service / Component

- `projects/scm-platform/apps/demand-planning-service/` (`WmsLowStockAlertConsumer` + 통합 테스트)

---

# Edge Cases

1. warehouseCode 부재가 항상 발생하면(마스터 미시드) 전 alert 이 inbound-expected leg 없이 재주문만 → 운영상 배치 sweep(SCM-BE-037)이 보조. 라이브 leg 는 마스터 시드로 해소.

---

# Failure Scenarios

## A. skuCode/locationId 부재는 여전히 DLT

→ 그것들은 alert 의 필수 코어 필드(additive 아님). warehouseCode 만 additive 취급. 혼동 금지.

---

# Test Requirements

- 부재→suggestion·null·no-DLT / 존재→무회귀. `:demand-planning-service:test` GREEN.

---

# Definition of Done

- [x] consumer graceful degrade(WARN+null, no DLT)
- [x] CI 가드 테스트 추가
- [ ] CI Linux GREEN 확인(원자 PR 시)

---

# Notes

- **Recommended impl model**: Sonnet(타깃 correctness fix — 본 changeset 은 Opus 오케스트레이터가 인라인 적용). 발견=SCM-INT-004 E2E. 우선순위=높음(기존 ADR-027 루프 보호).
