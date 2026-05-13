# Task ID

TASK-MONO-087

# Title

`platform/error-handling.md` 의 `LOT_INACTIVE` 미등록 1-line backfill (TASK-BE-152 audit § #9 error codes closure)

# Status

ready

# Owner

monorepo

# Task Tags

- platform
- error-handling
- spec
- governance
- retrospective

---

# Goal

`TASK-BE-152` (2026-05-14 머지, commit `ec2bb73c`) inventory-service Open Items audit § "#9 error codes" finding closure.

inventory-service 의 `MasterRefInactiveException.lotInactive(lotId)` ([apps/inventory-service/src/main/java/com/wms/inventory/domain/exception/MasterRefInactiveException.java:35-39](../../projects/wms-platform/apps/inventory-service/src/main/java/com/wms/inventory/domain/exception/MasterRefInactiveException.java)) 는 `LOT_INACTIVE` (422) 를 emit. 5 spec file (inventory-service 의 `domain-model.md` / `architecture.md` / `sagas/reservation-saga.md` + contracts `inventory-service-api.md` / `inbound-service-api.md`) 모두 이 code 를 nominal 로 명시.

그러나 [`platform/error-handling.md`](../../platform/error-handling.md) registry 에는 **`LOT_INACTIVE` 미등록** — sibling `LOT_NOT_FOUND` (L156) / `LOT_NO_DUPLICATE` (L157) / `LOT_EXPIRED` (L158) 와 짝인 entry 가 누락. BE-152 audit 가 "5/6 등록" 으로 surface 한 마지막 1건.

[`platform/error-handling.md`](../../platform/error-handling.md) § Change Rule L588 ("New error codes must be added to this document before being used in implementation.") 위반에 대한 retrospective backfill.

본 task = Master Data `[domain: wms]` 섹션의 `LOT_EXPIRED` 바로 위에 `LOT_INACTIVE | 422 | ...` 1 line 추가.

provenance: TASK-BE-152 review-task audit § "#9 error codes ⚠️ 5/6 등록 (LOT_INACTIVE 미등록; TRANSFER_CROSS_WAREHOUSE 의도된 VALIDATION_ERROR fold 보존)" finding.

---

# Scope

## In Scope

### A. `platform/error-handling.md` Master Data `[domain: wms]` 섹션에 `LOT_INACTIVE` 1-line 추가

위치: line 157 (LOT_NO_DUPLICATE) 와 line 158 (LOT_EXPIRED) 사이. sibling LOT-* 코드와 인접 배치.

Wording 패턴 (sibling `LOT_EXPIRED` 답습 + cross-service emitter 표기 `SKU_INACTIVE` L181 패턴 답습):

```
| LOT_INACTIVE | 422 | Requested operation is not allowed on a Lot whose `status = INACTIVE`. Emitted by inventory-service mutation guards via `MasterRefInactiveException.lotInactive(...)` (cross-service: master-service surface inactivation, inventory-service consumer enforcement) |
```

### B. 검증

- code emitter (`MasterRefInactiveException.lotInactive(...)`) 의 message wording 과 registry description 정합성 확인.
- post-edit grep `^\| LOT_INACTIVE` platform/error-handling.md = 1 hit.
- platform/*.md cross-ref 영향 0 (1-line append, body 무변경).
- HARDSTOP-03 hook PASS (project-specific content 잔존 0; "Master Data [domain: wms]" 는 기존 declared domain section 이므로 신규 project-bound content 추가 0).

## Out of Scope

- `SKU_INACTIVE` (L181) / `LOCATION_INACTIVE` (L182) 의 Master Data 섹션 재배치 (현재 Inbound section 위치 — historical placement 차이, 별 audit task 후보, 본 scope 밖).
- BE-152 audit § "#9 error codes" 의 `TRANSFER_CROSS_WAREHOUSE` 의도된 VALIDATION_ERROR fold (의도된 design, 변경 0).
- inventory-service / inbound-service 내 다른 잠재 미등록 code (BE-152 audit 가 surface 한 5/6 외 후속 finding 0 — 본 task 완료 시 6/6 도달).

---

# Acceptance Criteria

### Impl PR

- [ ] `platform/error-handling.md` 의 Master Data `[domain: wms]` 섹션에 `LOT_INACTIVE` 1-line 추가 (LOT_NO_DUPLICATE 다음, LOT_EXPIRED 직전).
- [ ] Description wording = sibling `LOT_EXPIRED` 패턴 + cross-service emitter (`MasterRefInactiveException.lotInactive`) 명시.
- [ ] post-edit grep `^\| LOT_INACTIVE` [`platform/error-handling.md`](../../platform/error-handling.md) = 1 hit.
- [ ] platform/*.md cross-ref 영향 0 (1-line append, body 무변경).
- [ ] HARDSTOP-03 hook PASS.
- [ ] task lifecycle ready → review (in-progress 우회 — mechanical 1-line, single-PR closure 패턴: TASK-MONO-084/085/086 precedent).
- [ ] [`tasks/INDEX.md`](../INDEX.md) 동기.

### Close chore PR

- [ ] task Status review → done.
- [ ] git mv tasks/review → tasks/done.
- [ ] [`tasks/INDEX.md`](../INDEX.md) ## review 제거, ## done append 1-line outcome.

---

# Related Specs

- [`platform/error-handling.md`](../../platform/error-handling.md) § Master Data [domain: wms]
- [`projects/wms-platform/specs/services/inventory-service/domain-model.md`](../../projects/wms-platform/specs/services/inventory-service/domain-model.md) § LOT-tracking guards
- [`projects/wms-platform/specs/services/inventory-service/architecture.md`](../../projects/wms-platform/specs/services/inventory-service/architecture.md) § Error handling
- [`projects/wms-platform/specs/services/inventory-service/sagas/reservation-saga.md`](../../projects/wms-platform/specs/services/inventory-service/sagas/reservation-saga.md)
- [`projects/wms-platform/specs/contracts/http/inventory-service-api.md`](../../projects/wms-platform/specs/contracts/http/inventory-service-api.md)
- [`projects/wms-platform/specs/contracts/http/inbound-service-api.md`](../../projects/wms-platform/specs/contracts/http/inbound-service-api.md)
- [`projects/wms-platform/tasks/done/TASK-BE-152-inventory-service-open-items-audit-and-list-correction.md`](../../projects/wms-platform/tasks/done/TASK-BE-152-inventory-service-open-items-audit-and-list-correction.md) § #9 error codes (audit source)

---

# Related Contracts

본 task = platform-wide error registry governance backfill. HTTP API / event payload contract 변경 0. 단 [`inventory-service-api.md`](../../projects/wms-platform/specs/contracts/http/inventory-service-api.md) / [`inbound-service-api.md`](../../projects/wms-platform/specs/contracts/http/inbound-service-api.md) 가 `LOT_INACTIVE` 를 nominal response 로 명시한 부분과의 정합성 회복.

---

# Target Service

`platform/` shared layer (project-agnostic). BE-152 audit § domain owner = inventory-service.

---

# Architecture

shared error registry 의 governance section 누락 backfill. sibling `LOT_EXPIRED` 인접 배치로 LOT-lifecycle 코드 cohesion 유지.

placement 결정 근거: `LOT_NOT_FOUND` / `LOT_NO_DUPLICATE` / `LOT_EXPIRED` 모두 Master Data `[domain: wms]` 섹션에 있음 (inventory-service 로컬에서 emit 되더라도 Lot resource lifecycle 의 source owner 가 master-service). `SKU_INACTIVE` / `LOCATION_INACTIVE` 의 Inbound section 배치는 historical (first emitter location 기준) — 본 task scope 밖, 별 audit 후보.

---

# Implementation Notes

## Code emitter

[`MasterRefInactiveException.java:35-39`](../../projects/wms-platform/apps/inventory-service/src/main/java/com/wms/inventory/domain/exception/MasterRefInactiveException.java):

```java
public static MasterRefInactiveException lotInactive(String lotId) {
    return new MasterRefInactiveException(
            "LOT_INACTIVE",
            "Lot " + lotId + " is INACTIVE — cannot mutate inventory for this lot");
}
```

javadoc 명시: "Maps to one of: LOCATION_INACTIVE, SKU_INACTIVE, LOT_INACTIVE, or LOT_EXPIRED — all 422 per the HTTP contract."

## Insertion location

`platform/error-handling.md` Master Data `[domain: wms]` 섹션 (line 139-160) 의 LOT-* 묶음:

- L156 `LOT_NOT_FOUND | 404`
- L157 `LOT_NO_DUPLICATE | 409`
- **신규** `LOT_INACTIVE | 422`
- L158 `LOT_EXPIRED | 422`

## D4 churn impact

- 1 file platform/ touch (`platform/error-handling.md`).
- 1-line addition, body 무변경.
- ADR-MONO-003a § D1.1 IN-scope (B common rule cleanup 연장선 — error registry retrospective backfill) — D4 OVERRIDE 적용.
- 직전 TASK-MONO-084/085/086 (2026-05-14) 와 동일 single-PR closure precedent.

---

# Edge Cases

- Master Data section 의 `LOT_EXPIRED` 위치가 LOT-lifecycle 코드의 canonical placement → `LOT_INACTIVE` 도 동일 section 에 배치 (Inbound section 의 `SKU_INACTIVE` / `LOCATION_INACTIVE` 와는 다른 위치 — historical placement 차이는 본 scope 밖).
- `MasterRefInactiveException` 의 cross-service emitter — inventory-service local 만 emit (master-service 자체 emission 0). description 에 emitter 표기 정확성 spot-check 필수.

---

# Failure Scenarios

- 본 task 가 backfill 후 다른 미등록 LOT-* code 추가 발견 시 → 별 task 후보. 현재 grep 기준 `LOT_INACTIVE` 가 유일한 미등록 LOT-* code (BE-152 audit 5/6 의 마지막 1건 = LOT_INACTIVE 로 명시 확정).
- description wording 과 code 실제 emit message 사이 불일치 → spot-check 필수 (cf. `MasterRefInactiveException.lotInactive` 의 message 패턴).

---

# Test Requirements

- HARDSTOP-03 hook PASS (project-specific content 잔존 0).
- post-edit grep `^\| LOT_INACTIVE` platform/error-handling.md = 1 hit.
- production code = 0 (spec only).
- self-CI = markdown-only path-filter 자연 검증 (MONO-084 precedent 답습 — 15 SKIP + 1 changes PASS).

---

# Definition of Done

### Impl PR

- [ ] AC 완료.
- [ ] task lifecycle ready → review.

### Close chore PR

- [ ] review → done, [`tasks/INDEX.md`](../INDEX.md) 동기.

---

# Provenance

- TASK-BE-152 (2026-05-14 머지, commit `ec2bb73c`) inventory-service Open Items audit § "#9 error codes" finding closure (5/6 → 6/6).
- TASK-MONO-084 / 085 / 086 (2026-05-14) precedent 답습: mechanical small backfill = single-PR closure (in-progress 우회).
- D4 OVERRIDE: ADR-MONO-003a § D1.1 (B common rule cleanup 연장선) 적용.
- 분석=Opus 4.7 / 구현 권장=Sonnet 4.6 (1-line spec backfill, mechanical).
