# Task ID

TASK-MONO-095

# Title

SCM 3 architecture.md migration to WMS Identity-table canonical form (ADR-MONO-012 D1 발효, D3 first batch)

# Status

review

# Owner

monorepo

# Task Tags

- monorepo
- scm-platform
- architecture-canonical-form
- adr-mono-012

---

# Goal

ADR-MONO-012 ACCEPTED (D1 WMS Identity-table form, D3 SCM first) 발효 후 첫 migration batch. SCM 3 architecture.md (procurement + inventory-visibility + gateway) 를 canonical form 으로 align.

# Scope

## In Scope

- `projects/scm-platform/specs/services/procurement-service/architecture.md` (light, ~2 line):
  - Remove standalone `## Service Type` H2 section (L3-4) — redundant with Identity table row.
  - Rename `## Service Identity` H2 → `## Identity` (canonical) — L19.
  - Provenance blockquote (L11-15) 보존 (service-specific narrative, canonical form 명세에 미포함이지만 정당화).

- `projects/scm-platform/specs/services/inventory-visibility-service/architecture.md` (medium, ~12 line):
  - Rename `## Service Identity` H2 → `## Identity` — L3.
  - Add 6 missing Identity rows: `Primary language / stack`, `Bounded Context`, `Deployable unit`, `Data store`, `Event publication`, `Event consumption` (본문에서 값 backfill).
  - Add `### Service Type Composition` H3 sub-section (dual `rest-api + event-consumer`, ADR-012 D1 명시 "required when dual").
  - 기존 `## Service Type Compliance` H2 (L41) 보존 — canonical form 외 service-specific compliance check.

- `projects/scm-platform/specs/services/gateway-service/architecture.md` (light, ~1 line):
  - Rename `## Service Identity` H2 → `## Identity` — L9.
  - `Shared state` Identity row 보존 (canonical 표 외 추가 row, gateway-specific).

## Out of Scope

- ADR-MONO-012 § D4 HARDSTOP-10 hook propagation cross-project — 별 task **TASK-MONO-096** 분리.
- GAP 8 architecture.md migration — 별 task **TASK-MONO-097**.
- ecommerce 13 architecture.md migration — 별 task **TASK-MONO-098**.
- fan-platform partial-align catch-up — fan service 단일 type 만 보유, D1 명시 "required when dual" 이므로 Service Type Composition H3 skip 가능. 별 평가 task 후보.
- production code / spec contract / Service Type 값 자체 변경 = 0 (canonical form alignment only).

# Acceptance Criteria

- [ ] `grep -c "^## Identity$" projects/scm-platform/specs/services/*/architecture.md` = 3 (3 service 모두 canonical Identity H2 보유).
- [ ] `grep -c "^## Service Identity$" projects/scm-platform/specs/services/*/architecture.md` = 0 (구 H2 이름 흔적 없음).
- [ ] `grep -c "^## Service Type$" projects/scm-platform/specs/services/procurement-service/architecture.md` = 0 (standalone Service Type H2 제거 확인 — Identity table row 만 source-of-truth).
- [ ] `grep -c "^### Service Type Composition$" projects/scm-platform/specs/services/inventory-visibility-service/architecture.md` ≥ 1 (dual Service Type service 에 Composition H3 추가).
- [ ] inventory-visibility-service Identity table 의 6 backfilled row (`Primary language`, `Bounded Context`, `Deployable unit`, `Data store`, `Event publication`, `Event consumption`) 모두 존재.
- [ ] Production code / spec contract / Service Type 값 = 0 변경.
- [ ] HARDSTOP-10 hook 회귀 없음 (현재 WMS-only enforce, SCM 변경 시 trigger 안 함 예상).

# Related Specs

- `docs/adr/ADR-MONO-012-cross-project-architecture-md-canonical-form.md` § D1 (canonical form), § D3 (migration order)
- `projects/wms-platform/specs/services/inventory-service/architecture.md` (canonical sample, dual-type with Service Type Composition H3)
- `projects/scm-platform/specs/services/procurement-service/architecture.md` (target)
- `projects/scm-platform/specs/services/inventory-visibility-service/architecture.md` (target)
- `projects/scm-platform/specs/services/gateway-service/architecture.md` (target)

# Related Contracts

해당 없음.

# Target Service

scm-platform (3 service: procurement / inventory-visibility / gateway).

# Edge Cases

- A: inventory-visibility-service 의 기존 `## Service Type Compliance` H2 가 canonical form 의 `### Service Type Composition` H3 와 의미 다름:
  - `## Service Type Compliance` = service-type spec 규정 준수 카탈로그 (rest-api / event-consumer / batch-job 각각의 compliance check)
  - `### Service Type Composition` = dual-type service 의 type 별 책임 분담 설명
  - 두 section 모두 보존, 위치 분리 (Composition = Identity table 직후, Compliance = Architecture Style 뒤).
- B: procurement-service Provenance blockquote (L11-15) 가 canonical form 명세에 미포함이지만 service-specific 가치 있는 narrative 라 보존. 다른 SCM service 는 해당 blockquote 없음.
- C: gateway-service 의 `Shared state` Identity row 가 canonical 표 에 미열거되지만 service-specific (Redis rate-limit ephemeral) 이라 보존. Identity table 의 추가 row 는 canonical 표가 minimum spec — 추가 row 자유 (WMS inventory-service 도 sibling 추가 row 보유).

# Failure Scenarios

- A: inventory-visibility-service Identity table 의 backfill 시 값 의미 보존 검증 — 본문 § Responsibilities + § Architecture Style Rationale + § Layer Structure 에서 데이터 store / event consumer / language 정보 추출. 5분 검증 cycle.

# Validation Plan

1. 3 file Edit 후 위 § Acceptance Criteria 각 grep 검증.
2. `git diff --stat` = 3 file / ~15 line 미만 edit.
3. HARDSTOP-10 hook PASS (현재 WMS-only enforce, SCM 영역 trigger 안 함 예상; 단 hook propagation 발효 후 TASK-MONO-096 closure 시 재검증).
4. 본문 의미 보존 확인 (Service Type 값 / Architecture Style / data store 등 backfill 값이 본문 narrative 와 일치).

# Implementation Notes

- **D4 OVERRIDE applied** per ADR-MONO-003a § D1.1 (project-internal spec polish, refactor-spec / ADR-012 governance follow-up). 11번째 task 누적 (MONO-085~094 + 본 task).
- monorepo-level task 분류 (root `tasks/`) — ADR-MONO-012 의 D3 migration order 의 첫 batch, scope 가 single SCM project 만이지만 governance escalation 의 implementation phase.
- 2 commit / 1 branch: (1) ready/ task author + tasks/INDEX.md ready row + 3 file Edit, (2) lifecycle move ready/ → review/ + tasks/INDEX.md done row.
- branch name `task/mono-095-scm-architecture-md-migration` — CLAUDE.md § Cross-Project Changes "Branch name constraint" 준수.
- single-PR closure 패턴 (ready → review 직접) — MONO-084~094 precedent 답습.
- WMS inventory-service 가 canonical sample reference, fan-platform 의 community-service 가 partial alignment sample.

# Outcome

(to be filled after impl commit)
