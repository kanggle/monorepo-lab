# Task ID

TASK-BE-285

# Title

WMS specs mechanical naming + clarity batch — Service Type Composition + Architecture Style 정규화 + 2 hedge cleanup (refactor-spec non-deadref Tier 1)

# Status

done

# Owner

wms-platform

# Task Tags

- wms
- spec
- mechanical-batch
- naming
- consistency
- clarity
- refactor-spec

---

# Goal

`/refactor-spec all --dry-run` (refactor-spec Tier classification + cycle pattern promoted to `.claude/commands/refactor-spec.md` PR #517) 의 non-deadref category audit 결과 Tier 1 mechanical batch closure.

| # | File | Line | Issue | Fix |
|---|---|---|---|---|
| F-04 | `notification-service/architecture.md` | L23 | `### Service Type — single` (단독 형식) vs 6 sibling canonical `### Service Type Composition` | rename to sibling canonical |
| F-05a | `admin-service/architecture.md` | L72 | `## Architecture Style: Layered (Deliberate Exception)` 접미사 mixed | drop suffix → `## Architecture Style` (body 가 Layered 선택 정당화) |
| F-05b | `gateway-service/architecture.md` | L51 | `## Architecture Style Rationale` 접미사 mixed | drop suffix → `## Architecture Style` |
| F-05c | `notification-service/architecture.md` | L75 | `## Architecture Style: Hexagonal` 접미사 mixed | drop suffix → `## Architecture Style` |
| F-09 | `admin-service/domain-model.md` | L138 | `Unknown strings → SETTING_VALIDATION_ERROR (reused; specific code TBD).` — `TBD` hedge | drop `; specific code TBD` → `(reused)` 보존 |
| F-10 | `inventory-service/database-design.md` | L235-236 | `payload is JSONB ... event shape if needed without re-parsing` — `if needed` hedge | drop `if needed` |

**6 fix / 5 file / ~6 line edit / 0 production code change** — sibling alignment + hedge cleanup. TASK-BE-165 + TASK-BE-283 mechanical batch precedent 답습 (4th cycle in 2026-05-14~15).

# Scope

## In Scope

- F-04 `notification-service/architecture.md` L23 rename
- F-05a/b/c 3 architecture.md `Architecture Style` H2 suffix drop
- F-09 admin-service `domain-model.md` TBD hedge drop
- F-10 inventory-service `database-design.md` "if needed" hedge drop

## Out of Scope (re-classification)

### F-03 — zero-state vs active-integration intentional divergence (Tier 1 → SKIP)

- **agent finding**: 4 zero-state (`## Evolution Paths (Not In v1)`) vs 3 active-integration (`## N. Not In v1`, numbered) sibling drift.
- **Re-classification rationale**: zero-state files have **no numbered section sequence** (narrative throughout), active-integration files have **numbered vendor catalog** ending in numbered "Not In v1" item. The two heading forms reflect different doc structures, NOT author drift. Both forms are semantically appropriate for their context.
- **Decision**: SKIP — not mechanical, structural divergence intentional. Could be unified via separate task if future spec authoring (e.g., new active-integration service) re-evaluates the convention.

### F-01/F-02/F-06/F-07/F-08 — Tier 2 (별 task)

- F-01+F-02 reservation-saga + reservation-status shape (별 task 후보, sibling emulation Tier 2)
- F-06+F-07 outbox/processed_events schema authority (HIGH risk, libs/java-messaging cross-check 필수, Tier 2 paired)
- F-08 glossary Service Type pointer (Tier 2 governance fix, 1-line)

# Acceptance Criteria

- [x] F-04 notification `### Service Type — single` → `### Service Type Composition` 정확 align (7 WMS service 모두 동일 H3 검증).
- [x] F-05 3 file `## Architecture Style: X` 또는 `Rationale` 접미사 모두 drop → canonical `## Architecture Style` (7 WMS service 모두 동일 검증).
- [x] F-09 `(reused; specific code TBD)` → `(reused)` — hedge 제거, `reused` 의미 보존.
- [x] F-10 `event shape if needed without re-parsing` → `event shape without re-parsing` — `if needed` hedge 제거.
- [x] F-03 SKIP rationale (zero-state vs active-integration intentional divergence) 명시 — § Out of Scope 에 기록됨.
- [x] Production code / spec contract / event payload / API schema / business logic 0 변경 (markdown heading + word edits only).

# Related Specs

- `projects/wms-platform/specs/services/{admin,gateway,notification,inventory}-service/` (모든 4 영향 file)
- `.claude/commands/refactor-spec.md` § Operational Patterns (Tier classification + Mechanical batch closure pattern)
- TASK-BE-165/283/SCM-BE-013/BE-284 precedent (refactor-spec sibling cycle, 4th task)

# Related Contracts

해당 없음 — spec polish only.

# Target Service

해당 없음 — 4 WMS service spec polish (admin / gateway / notification / inventory).

# Edge Cases

- A: F-05 `admin-service/architecture.md` Suffix 가 "Layered (Deliberate Exception)" 라 의미적 — drop 시 body content 가 Layered 선택 정당화 보존 여부 확인 필요 (body 에 별도 § Layered Architecture Rationale 또는 동등 영역 존재 확인).
- B: F-04 notification 의 "— single" 표기가 의도적 marker 일 가능성 — 6 sibling 의 "Composition" 형식이 standard pattern, semantic loss 없음 확인.

# Failure Scenarios

- A: F-05 drop 후 본문이 style 선택 정당화 안 함 → 본문 추가 author 필요 (별 task).
- B: F-04 align 후 notification body 의 "single" 의미 약화 → body 의 "single — event-consumer pure" 표현 유지로 보강.

# Validation Plan

1. Edit 후 `grep -n "^### Service Type" projects/wms-platform/specs/services/*/architecture.md` = 7 line 모두 `### Service Type Composition`.
2. Edit 후 `grep -n "^## Architecture Style" projects/wms-platform/specs/services/*/architecture.md` = 7 line 모두 `## Architecture Style` (no suffix).
3. Edit 후 `grep -n "specific code TBD" projects/wms-platform/specs/services/admin-service/domain-model.md` exit 1 (no output).
4. Edit 후 `grep -n "if needed" projects/wms-platform/specs/services/inventory-service/database-design.md` exit 1 (no output, 또는 다른 context 의 "if needed" 만 잔존).
5. `git diff --stat` ~5 file / ~6 line edit.

# Implementation Notes

- 2 commit / 1 branch: (1) ready/ task author, (2) 6 fix + lifecycle move ready/ → review/.
- branch name `task/be-285-wms-spec-mechanical-naming-clarity-batch` — CLAUDE.md § Cross-Project Changes "Branch name constraint" 준수.
- 4th refactor-spec mechanical batch cycle (BE-165/283/SCM-BE-013/BE-284 sibling precedent). Tier 1 of non-deadref audit.
- F-03 SKIP rationale documented inline 이라 future agent 가 같은 finding 재감지 시 SKIP path 인식.

# Outcome

**Status: DONE** (PR #518 squash `eb82d7be`).

6 mechanical fixes / 5 file / 7 line edit / 0 production code change — non-deadref Tier 1 batch closure (4th refactor-spec mechanical batch cycle).

**Fix detail**:
- F-04: notification/architecture.md L23 `### Service Type — single` → `### Service Type Composition`
- F-05a/b/c: admin/gateway/notification architecture.md `## Architecture Style: X` / `Rationale` suffix drop → canonical `## Architecture Style`
- F-09: admin/domain-model.md L138 drop `; specific code TBD` hedge (preserve `(reused)`)
- F-10: inventory/database-design.md L235-236 drop `if needed` hedge
- F-03: SKIP — zero-state vs active-integration intentional structural divergence (numbered vendor catalog vs narrative), not author drift

**CI**: 1 pass (`changes`) / 16 SKIPPED / 0 fail (admin/inventory/notification/gateway specs 변경 — contracts 안 건드림 → Frontend E2E SKIP). mergeStateStatus CLEAN.

**Verification**: 7/7 WMS architecture.md `### Service Type Composition` + `## Architecture Style` canonical aligned. `grep "specific code TBD"` + `grep "shape if needed"` = 0 hit.

**Provenance**: refactor-spec non-deadref audit Tier 1. **Tier 2 backlog (별 task)**: F-01+F-02 reservation shape / F-06+F-07 outbox schema authority (HIGH risk) / F-08 glossary Service Type pointer. Tier 3 (out-of-scope): 3 pre-existing cross-project divergence.

**refactor-spec cycle progression**:

| # | Task | Scope | Category | Fix | PRs |
|---|---|---|---|---|---|
| 1 | BE-165 | WMS Tier 1 (deadref) | mechanical | 5 | #509+#510 |
| 2 | BE-283 | GAP Tier 3 #1 (deadref) | mechanical | 47 | #511+#512 |
| 3 | SCM-BE-013 | SCM Tier 3 #2 (deadref) | mechanical | 1 | #513+#514 |
| 4 | BE-284 | GAP Tier 2 (deadref) | judgment | 1 | #515+#516 |
| 5 | **BE-285 (this)** | WMS Tier 1 (non-deadref naming/clarity) | mechanical | 6 | #518+(close) |

Total: **5 task / 10 PR / 60 fix / portfolio dead-ref + non-deadref Tier 1 모두 closure**.
