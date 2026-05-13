# Task ID

TASK-BE-152

# Title

inventory-service § Open Items audit + retrospective backfill list correction — § Open Items 목록 자체의 staleness 정정 (architecture.md + domain-model.md)

# Status

done

# Owner

wms-platform

# Task Tags

- wms
- inventory-service
- spec
- audit
- backfill
- be

---

# Goal

[TASK-BE-151](../done/TASK-BE-151-inventory-service-reservation-saga-and-state-machine-spec.md) (commit `c917a2da` + fix `66dcca85` + close `8ca54e01`, 2026-05-14 머지) 가 inventory-service `architecture.md § Open Items` #6 (sagas/reservation-saga.md) + #7 (state-machines/reservation-status.md) 두 항목을 ✅ 표기로 closure 했으나, **나머지 #1-5 + #8-10 항목은 BE-151 후 stale 상태 — 각 항목의 실 file 존재 / error code 등록 / gateway route 등록 상태가 list 의 "pending" 표기와 어긋남**.

직접 audit 결과:

- #1 domain-model.md ✅ 존재 (BE-030 era)
- #2 inventory-service-api.md ✅ 존재
- #3 inventory-events.md ✅ 존재
- #4 consumer-side schema cross-link ✅ 존재
- #5 idempotency.md ✅ 존재 (BE-030 era)
- #6 sagas/reservation-saga.md ✅ BE-151
- #7 state-machines/reservation-status.md ✅ BE-151
- #8 external-integrations.md ❌ MISSING
- #9 error codes ⚠️ 5/6 등록 (LOT_INACTIVE 미등록; TRANSFER_CROSS_WAREHOUSE 의도된 VALIDATION_ERROR fold 보존)
- #10 gateway route ❌ MISSING — **portfolio-wide gap**: `gateway-service/application.yml` 이 master-service routes 만 등록 (inbound/inventory/outbound/notification/admin 5 service 모두 미등록), TASK-MONO-024 의 Traefik hostname routing 으로 우회되었을 가능성

domain-model.md § Open Items 도 동일하게 stale (database-design.md / idempotency.md / error codes 항목들이 outdated 표기).

이 상태는:

- **spec layer 7 (services) 의 prerequisite list 가 실제 service 진척과 어긋남** — service 이미 production (BE-030 series + BE-141/151 etc 머지) 인데 § Open Items 가 여전히 "Before First Implementation Task" 게이트 표현 사용. CLAUDE.md § Core Principles ("Specifications are the source of truth") 위반 잠재.
- **portfolio cleanup 시리즈 (MONO-085/086 + BE-147~151) 의 meta closure 미완** — BE-148/149 가 `(Open Item)` 마커는 정리했고 BE-151 이 #6/#7 ✅ 처리했으나, § Open Items list 자체의 정확성은 audit 되지 않음.

본 task = **audit + list 정정만** (~30 line edit per file, 신규 file authoring 0). production code = 0 변경. **outstanding 4 items (#8 external-integrations.md / #9 LOT_INACTIVE error code / #10 gateway route audit / domain-model database-design.md) 는 별 task 후보 로 list 본문에 명시** — 본 task scope 에서 authoring 하지 않음.

retrospective audit + list correction 답습 패턴: BE-149 (services/ stale marker batch audit) + BE-150 (inbound architecture audit + fix). 본 task = same-day single-PR closure 13번째 entry 후보 (BE-141/142/FAN-BE-006/MONO-084/BE-281/BE-145/146/147/148/149/150/151 precedent).

---

# Scope

## In Scope

### A. `architecture.md § Open Items` (L577-602) audit + correction

대상: `projects/wms-platform/specs/services/inventory-service/architecture.md`.

작업:
- Section 머리말 정정: "Before First Implementation Task" → "Retrospective Backfill Audit" (service 이미 production, prerequisite gate 표현 무효).
- 각 항목 (#1-10) 에 ✅ done / ⚠️ partial / ❌ outstanding status 부여:
  - #1 domain-model.md → ✅ link 활성화 + BE-030 era attribution
  - #2 inventory-service-api.md → ✅ link 활성화 + BE-030 era attribution
  - #3 inventory-events.md → ✅ link 활성화 + BE-030 era attribution
  - #4 consumer schema cross-link → ✅ link 활성화 + cross-ref 명시
  - #5 idempotency.md → ✅ link 활성화 + BE-030 era attribution
  - #6 sagas/reservation-saga.md → ✅ 유지 (BE-151)
  - #7 state-machines/reservation-status.md → ✅ 유지 (BE-151)
  - #8 external-integrations.md → ❌ outstanding + sibling 답습 pattern (outbound-service/external-integrations.md 의 inverse — zero vendor surface declaration) + 별 TASK-BE-* 후보 명시
  - #9 error codes → ⚠️ partial — 5/6 registered with each code's HTTP status + LOT_INACTIVE outstanding + 별 TASK-MONO-* 후보 명시 (shared path)
  - #10 gateway route → ❌ outstanding **AND portfolio-wide gap** — `gateway-service/application.yml` 이 master-service routes 만 가지고 있음 명시 + TASK-MONO-024 hostname routing 가능성 + 별 audit (TASK-MONO-*) 후보 명시

### B. `domain-model.md § Open Items` (L675-689) audit + correction

대상: `projects/wms-platform/specs/services/inventory-service/domain-model.md`.

작업:
- Section 머리말에 BE-152 audit 시점 + architecture.md cross-link 명시.
- 각 항목 정정:
  - database-design.md → ❌ outstanding + Flyway migration 이 de-facto schema 라는 fact 명시 + 별 TASK-BE-* 후보
  - state-machines/reservation-status.md → ✅ 유지 (BE-151)
  - sagas/reservation-saga.md → ✅ 유지 (BE-151)
  - idempotency.md → ✅ done (BE-030 era), link 활성화
  - error codes → ⚠️ 5/6 registered + LOT_INACTIVE outstanding + TRANSFER_CROSS_WAREHOUSE 의도된 VALIDATION_ERROR fold 보존 (v1 simplification per § 5 StockTransfer invariants)

### C. 별 task 후보 명시 (본 PR 에서 file 작성 0)

audit 결과 surfaced 4 outstanding items 는 list 본문 inline 으로만 "후보 candidate" 명시 — 본 task 에서 새 `tasks/ready/` file 작성 안 함. 사용자가 향후 backlog 처리 결정 시 그 시점에서 별 task 파일 작성.

candidate inventory:
1. `TASK-BE-N` — `inventory-service/external-integrations.md` zero-state authoring (~80-100 line, integration-heavy Required Artifact 1)
2. `TASK-BE-N+1` — `inventory-service/database-design.md` retrospective (Flyway migration 통합 spec, ~200-400 line)
3. `TASK-MONO-N` — `platform/error-handling.md` 의 `LOT_INACTIVE` 등록 (1-line addition, sibling row pattern 답습)
4. `TASK-MONO-N+1` — portfolio-wide gateway routes audit (gateway-service application.yml 이 master 만 routing 하는 이유 검증, TASK-MONO-024 hostname routing 과의 관계 정리, 필요 시 routes 추가)

### D. WMS-specific concerns

- inventory-service 가 portfolio 의 transactional T1-T8 + integration-heavy I1-I4 reference implementation (production 검증 완성) — § Open Items list 의 정확성은 portfolio 평가자 진입 자료로 직접 노출.
- audit 머리말의 "BE-030 era" attribution 은 정확한 commit / PR # 추적 안 함 (BE-030 task ID 자체가 portfolio 자료에서 deprecated 일 가능성) — 단지 "service inception 시점에 authored" 의미.

## Out of Scope

- production code 변경 — 0.
- 신규 spec file authoring — 본 task 는 list correction only.
- 4 outstanding items (#8 / #9 LOT_INACTIVE / #10 gateway / domain database-design) 의 직접 closure — 별 task 후보 만 명시.
- `TRANSFER_CROSS_WAREHOUSE` error code 등록 — domain-model.md 가 명시적으로 "(or fold into VALIDATION_ERROR)" 선언했고 v1 의도된 simplification per § 5 StockTransfer invariants.
- inventory-service 의 다른 spec (`overview.md` / `architecture.md` 본문 / `domain-model.md` 본문) 의 audit — 본 task 는 § Open Items section 만.

---

# Acceptance Criteria

### Impl PR

- [ ] `architecture.md § Open Items` (L577-602) section 머리말 "Before First Implementation Task" → "Retrospective Backfill Audit" + BE-152 attribution.
- [ ] #1-5 항목 모두 ✅ + link 활성화 + attribution (BE-030 era for #1-5; BE-151 for #6-7 유지).
- [ ] #8 ❌ outstanding + 별 TASK-BE-* 후보 명시 + sibling pattern (outbound-service/external-integrations.md inverse).
- [ ] #9 ⚠️ partial — 5/6 registered (각 code 의 HTTP status 명시) + LOT_INACTIVE outstanding + 별 TASK-MONO-* 후보 명시.
- [ ] #10 ❌ outstanding + portfolio-wide gap 명시 (gateway-service application.yml 이 master-service routes only 라는 fact 인용) + TASK-MONO-024 hostname routing 가능성 + 별 TASK-MONO-* audit 후보.
- [ ] `domain-model.md § Open Items` (L675-689) section 머리말에 BE-152 audit 시점 + architecture.md cross-link.
- [ ] domain-model.md database-design.md ❌ + Flyway migration de-facto schema fact + 별 TASK-BE-* 후보.
- [ ] domain-model.md idempotency.md ✅ link 활성화.
- [ ] domain-model.md error codes ⚠️ partial + TRANSFER_CROSS_WAREHOUSE 의도된 fold 보존 명시.
- [ ] `grep -rn "(Open Item" projects/wms-platform/specs/` 잔존 = **0** (BE-151 outcome 유지, 본 task 가 그 outcome 깨뜨리지 않음).
- [ ] cross-ref 검증 — 신규 link 활성화한 sibling path 모두 dead-reference 0.
- [ ] HARDSTOP-03 PASS — wms project-specific spec.
- [ ] CI markdown-only path-filter (15 SKIP + 1 changes PASS).
- [ ] task lifecycle ready → review 직접.
- [ ] wms `tasks/INDEX.md` 동기.

### Close chore PR

- [ ] task Status review → done.
- [ ] git mv tasks/review → tasks/done.
- [ ] INDEX 동기.

---

# Related Specs

- `projects/wms-platform/specs/services/inventory-service/architecture.md` § Open Items (L577-602) — 본 task 의 audit 대상 #1.
- `projects/wms-platform/specs/services/inventory-service/domain-model.md` § Open Items (L675-689) — 본 task 의 audit 대상 #2.
- `projects/wms-platform/specs/services/inventory-service/idempotency.md` / `sagas/reservation-saga.md` / `state-machines/reservation-status.md` / `../../contracts/http/inventory-service-api.md` / `../../contracts/events/inventory-events.md` — audit 시 ✅ 확인된 sibling spec.
- `platform/error-handling.md` — audit 시 `RESERVATION_NOT_FOUND` / `RESERVATION_QUANTITY_MISMATCH` / `LOCATION_INACTIVE` / `SKU_INACTIVE` / `LOT_EXPIRED` 5 entry 확인 + `LOT_INACTIVE` 부재 확인.
- `projects/wms-platform/apps/gateway-service/src/main/resources/application.yml` — audit 시 master-service routes only 확인.
- `docs/adr/ADR-MONO-024-*` (TASK-MONO-024 PORT_PREFIX deprecation) — hostname routing 으로 routing 통일 reference.

# Related Contracts

- (audit 만, 변경 없음) `projects/wms-platform/specs/contracts/http/inventory-service-api.md`, `projects/wms-platform/specs/contracts/events/inventory-events.md` — section A #2 / #3 ✅ 확인.

# Edge Cases

- **현재 stale list 에 추가로 ✅ 처리해야 하는 항목 발견**: BE-151 closure 이후 BE-030 era authored file 들 (#1-5) 의 실 존재가 list 표기와 어긋남 — 본 task 가 그 stale 표기를 통째로 정정.
- **portfolio-wide gateway gap 발견**: #10 audit 중 gateway-service application.yml 이 5 service 모두 미등록 사실 노출 — 본 task scope 외 (별 TASK-MONO-* 후보 만 명시).
- **TRANSFER_CROSS_WAREHOUSE 의도된 deferral**: domain-model.md 본문이 "(or fold into VALIDATION_ERROR)" 명시 — 본 task 는 등록 outstanding 아닌 "intentional fold" 로 분류, 별 task 후보 NOT.

# Failure Scenarios

- **§ Open Items list 정정의 새 stale**: 본 task 가 BE-030 era 표현 사용했으나 실제 commit / PR # 추적 안 함 — 향후 portfolio 평가자가 BE-030 task ID 추적 시도 시 deprecated link 발견 가능. 의도된 abstraction — 정확한 commit 추적이 필요하면 별 audit.
- **scope creep — 4 outstanding 의 직접 closure 시도**: 명시적으로 별 task 후보로만 분류 → 본 task 에서 file authoring 하지 않음. Acceptance Criteria 의 "별 task 후보 명시" 가 강제.
- **cross-ref dead-link 도입**: 신규 활성화한 link 가 sibling path 와 어긋날 위험 — Acceptance Criteria 의 cross-ref 검증 checkbox.

# Notes

- 분석=Opus 4.7 / 구현 권장=Sonnet 4.6 (audit + list edit routine).
- 본 task 의 single-PR closure 시 D4 churn freeze 영향 0 (project-internal spec, project-specific only).
- 답습 same-day single-PR closure 패턴 — 본 task = 13번째 entry.
- 머지 후 inventory-service 의 § Open Items list 의 정확성이 portfolio 평가자 진입 자료의 자체 검증 가능 (status icon 만 보면 즉시 가능).
