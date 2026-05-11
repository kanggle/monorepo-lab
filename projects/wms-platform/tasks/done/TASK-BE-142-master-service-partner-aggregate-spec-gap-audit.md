# Task ID

TASK-BE-142

# Title

master-service Partner aggregate spec gap audit — 의도/누락 결정 + 결과에 따라 architecture 정정 또는 backlog 신규 task 분기

# Status

done

# Owner

backend

# Task Tags

- code
- adr

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Goal

wms/master-service sweep dry-run (2026-05-11) 의 **spec gap finding** 의 closure.

발견 내용: [`projects/wms-platform/specs/services/master-service/architecture.md`](../../specs/services/master-service/architecture.md) § Responsibility 가 **6 aggregate** 를 선언 (Warehouse / Zone / Location / SKU / **Partner** / Lot) 했으나, production code 에 `Partner` 관련 자산이 모두 부재:

- `domain/model/Partner.java` 없음
- `application/port/{in,out}/.*Partner.*` 없음
- `application/service/PartnerService.java` 없음
- `adapter/in/web/controller/PartnerController.java` 없음
- `adapter/out/persistence/{entity,mapper,adapter}/.*Partner.*` 없음
- `domain/event/.*Partner.*` 없음

`Lot.supplierPartnerId` 만 `UUID` 자유 필드로 처리 (외부 참조 검증 없음). spec 의 § Out of Scope / § v1 simplification 어디에도 "Partner is deferred to v2" 같은 명시적 status 없음.

본 task 는 두 가지 분기 중 하나로 closure:

- **Branch A (의도적 v1 deferred)**: architecture.md § Responsibility 의 6 aggregate 리스트에 명시적 status 표시 ("Partner — v2") + domain-model.md / master-service-api.md / master-events.md 의 Partner 관련 schema 도 같은 status 로 정합. backlog 신규 task 발행 없음.
- **Branch B (누락)**: backlog 신규 task `TASK-BE-XXX-master-service-partner-aggregate-bootstrap` 발행 — Partner domain model / port / service / controller / persistence / outbox event 풀 implementation. spec 정정 없음 (현행 spec 유지가 정답).

---

# Scope

## In Scope

- spec audit:
  - `architecture.md` § Responsibility / § Out of Scope / § Open Items / § Extensibility Notes 의 Partner 언급 모두 grep
  - `domain-model.md` 의 Partner 엔티티 정의 유무
  - `specs/contracts/http/master-service-api.md` 의 Partner endpoint 유무
  - `specs/contracts/events/master-events.md` 의 Partner 이벤트 schema 유무
- code audit (이미 dry-run 에서 확인됨, 재확인용):
  - `apps/master-service/src/main/java/com/wms/master/**/Partner*` 의 부재 확인
  - `Lot.supplierPartnerId` 의 외부 참조 검증 정책 확인
- usage audit:
  - `apps/inbound-service/` 와 `apps/outbound-service/` 가 Partner 개념을 어떻게 다루는지 (supplier_id / customer_id 등 free UUID 처리 여부)
  - 외부 시스템 (ERP/PIM) 의 Partner 동기화 design 여부
- **결정 + 분기** (Branch A 또는 Branch B):
  - **Branch A**: `architecture.md` 의 Partner 항목에 "v2" 또는 명시적 deferred status 추가 + `domain-model.md` § 5 (Partner) 와 § 6 (Lot) 정합 + `master-service-api.md` 의 Partner 영역 deferred 명시 + `master-events.md` 의 Partner 이벤트 deferred 명시. spec-only PR.
  - **Branch B**: backlog 신규 task 발행 (예: `TASK-BE-143-master-service-partner-aggregate-bootstrap.md`) + Goal/Scope/AC 작성. INDEX.md backlog 표 등재.

## Out of Scope

- 실제 Partner aggregate 구현 (Branch B 가 발행하는 별도 task 의 scope)
- 다른 spec gap audit (Partner 외 다른 aggregate)
- inbound/outbound 의 supplier_id / customer_id 의 typed reference 전환 (별도 평가)
- ERP/PIM 외부 동기화 (architecture.md § Extensibility Notes 에 이미 v2 로 deferred)

---

# Acceptance Criteria

- [ ] spec audit 결과가 task review comment 또는 PR description 에 명시 (어떤 spec file 의 어떤 § 에 Partner 언급이 있고 어떤 형태인지).
- [ ] code audit 결과 production code 의 Partner 자산 부재 재확인 (grep 결과 0 명시).
- [ ] usage audit 결과 inbound/outbound 의 supplier_id / customer_id 처리 방식 1줄 요약.
- [ ] **Branch 결정**: 의도/누락 결정 명시 + 근거 1-2 sentence.
- [ ] **Branch A choice** → `architecture.md` § Responsibility 의 Partner 항목에 명시적 status (v2 또는 deferred) + 다른 3 spec file 정합 정정 + PR open.
- [ ] **Branch B choice** → backlog 신규 task `.md` 발행 (Goal/Scope/AC 완비) + `INDEX.md` backlog 표 등재 + PR open.
- [ ] 두 분기 모두 spec drift 없음 (한쪽이 정정/등재 하면 다른 쪽은 변경 0).

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read `PROJECT.md`, then load `rules/common.md` plus any `rules/domains/<domain>.md` and `rules/traits/<trait>.md` matching the declared classification.

- [`specs/services/master-service/architecture.md`](../../specs/services/master-service/architecture.md) — § Responsibility / § Out of Scope / § Open Items / § Extensibility Notes
- `specs/services/master-service/domain-model.md`
- `specs/contracts/http/master-service-api.md`
- `specs/contracts/events/master-events.md`
- `rules/domains/wms.md` — Master Data bounded context

# Related Skills

- `.claude/skills/backend/refactor-spec` (Branch A choice: spec-only refactor)
- `.claude/skills/backend/implement-task` (Branch B choice: backlog task 발행)

---

# Related Contracts

- Branch A: HTTP/event contract 의 Partner 영역 status 정정 (impl 아님, 메타데이터만)
- Branch B: 변경 없음 (impl task 가 별도)

---

# Target Service

- `master-service`

---

# Architecture

해당 없음 (audit + 결정 분기 task, 코드 변경 0 또는 spec-only)

---

# Implementation Notes

- 본 task 는 **결정 task** 이지 implementation task 가 아니다. agent 또는 사용자가 audit 후 Branch A/B 결정 + 결정에 따른 단순 chore 수행.
- 결정 근거 단서:
  - architecture.md § Responsibility 의 Partner 항목이 "v2" 또는 "deferred" 명시 없이 다른 5 aggregate 와 동등하게 나열됨 = **의도 명시 부재**.
  - architecture.md § Open Items 에 Partner domain-model.md 의 entity definition 요구 항목 있는지 → 있으면 (가능성) Branch B (의도 = 구현이지만 누락), 없으면 Branch A (의도 = deferred 였으나 status 표기 누락).
  - inbound/outbound 의 supplier_id 처리가 무 검증 free UUID 라면 → master 가 Partner 를 의도적으로 deferred 한 패턴과 일치 = Branch A.
- Branch A choice 시 정정 범위:
  - architecture.md § Responsibility 의 Partner bullet 에 "(v2)" 또는 새 § "v1 Aggregate Status" 표 신설.
  - domain-model.md 의 Partner 절을 § Out of Scope 또는 § Future Extensions 로 이동.
  - master-service-api.md 의 Partner endpoint 절을 § Future Endpoints (v2) 로 이동.
  - master-events.md 의 Partner 이벤트 schema 를 § Deferred Events (v2) 로 이동.
- Branch B choice 시 발행할 신규 task 의 Goal 초안:
  - master-service 에 Partner aggregate (entity / port / service / controller / persistence / outbox event) 풀 implementation. `Lot.supplierPartnerId` 의 외부 참조 검증 활성화. inbound `AsnLine.supplierId` / outbound `Order.customerId` 의 typed reference 전환 검토 (별도 평가).

---

# Edge Cases

- spec 의 어떤 file 에 Partner 가 "in scope" 로 명시되어 있고 다른 file 에는 부재 — 두 file 사이 drift. 이 경우 audit 결과에 drift 발견 자체를 기록 + Branch A/B 중 하나의 정정 시 drift 동시 해결.
- code 에 Partner 의 일부 자산만 존재 (예: `domain/model/Partner.java` 만 있고 service/controller 없음) — Branch B 의 가능성이 높음 (부분 implementation 누락). dry-run 에서는 모두 부재로 확인되었지만 본 task 에서 재확인 필수.
- inbound/outbound 가 typed Partner reference 를 이미 사용 중 — 가능성 매우 낮으나 확인 필수. 사용 중이라면 master-service 의 Partner 부재 = silent fail 가능성 = Branch B + 우선순위 ↑.

---

# Failure Scenarios

- 본 task 자체는 audit + 결정. 코드 변경 0 또는 spec/task-file 변경 only. 신규 failure mode 도입 0.

---

# Test Requirements

- 코드 테스트 추가 없음 (audit + chore).
- Branch A choice 의 경우: spec 변경 검증은 reviewer 의 spec read.
- Branch B choice 의 경우: 신규 task 의 자체 AC 가 향후 implementation 시 검증.

---

# Definition of Done

- [ ] Audit 3종 (spec + code + usage) 결과 PR description 또는 task comment 에 명시
- [ ] Branch A 또는 Branch B 결정 + 근거 명시
- [ ] Branch A: 4 spec file 정정 + PR open
- [ ] Branch B: 1 신규 task `.md` 발행 + INDEX backlog 표 등재 + PR open
- [ ] master-service architecture.md 의 6 aggregate 리스트가 status 표기 완비 (impl/deferred-v2/dropped)
- [ ] Ready for review
