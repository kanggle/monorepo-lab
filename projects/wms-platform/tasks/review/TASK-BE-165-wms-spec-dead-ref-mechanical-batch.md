# Task ID

TASK-BE-165

# Title

WMS specs dead-reference mechanical batch — 4 path corrections + 1 hedge cleanup (BE-147/161/163 author artifacts)

# Status

review

# Owner

wms-platform

# Task Tags

- wms
- spec
- mechanical-batch
- dead-reference
- cleanup

---

# Goal

`/refactor-spec all --dry-run` (2026-05-14, BE-153~164 portfolio gap 완전 종결 직후 audit) 의 Tier 1 finding 5 건 closure. **모두 NEW wms author 의 path depth / package miscount mechanical 결함**:

| # | File | Line | Issue |
|---|---|---|---|
| 1 | `specs/contracts/http/tms-shipment-api.md` | L390 | `[rules/traits/integration-heavy.md](../../../../...)` — 5-level deep file needs 5 `../` not 4 |
| 2 | `specs/contracts/http/tms-shipment-api.md` | L393 | `[platform/security-rules.md]` — same depth bug |
| 3 | `specs/services/master-service/database-design.md` | L195 | `ZoneType.java` path `domain/zone/` → actual `domain/model/` |
| 4 | `specs/services/outbound-service/database-design.md` | L793 | `state-machines/outbound-saga-status.md` → actual `state-machines/saga-status.md` |
| 5 | (bundled #4) | L793 | "(if present)" hedge phrasing drop after path fix |

**TASK-MONO-085/086 mechanical batch + post-merge re-verification 패턴** 답습 — single-PR closure. production code 0 / spec contract 0 변경 (markdown link / path string 수정만).

# Scope

## In Scope

- `projects/wms-platform/specs/contracts/http/tms-shipment-api.md` L390 + L393 — `../../../../` → `../../../../../` (5 levels deep: `projects/wms-platform/specs/contracts/http/` 에서 repo-root 까지 5 ups).
- `projects/wms-platform/specs/services/master-service/database-design.md` L195 — `domain/zone/ZoneType.java` → `domain/model/ZoneType.java` (verified by `find apps/master-service/src/main/java/com/wms/master -name ZoneType.java`).
- `projects/wms-platform/specs/services/outbound-service/database-design.md` L793 — basename `outbound-saga-status.md` → `saga-status.md` + drop "(if present)" hedge (sibling `outbound-saga.md` already links `saga-status.md` correctly).

## Out of Scope

- GAP 53 pre-existing `libs/*` dead-refs (Tier 3 candidate, separate task — pre-import era artifact, MONO-085 scope 누락).
- SCM 1 `scm-procurement-events.md` ADR-MONO-004 path (Tier 3 candidate, separate task).
- tasks/done/ archive dead-refs (lifecycle layer, spec refactor 범위 외).

# Acceptance Criteria

- [ ] 4 dead-references PASS (target file 실재 + path resolve to existing file).
- [ ] 1 hedge phrasing "(if present)" 제거 (L793 outbound DB design).
- [ ] `bash` link checker re-run (sample TASK-MONO-085 script pattern) over 3 modified files = 0 broken.
- [ ] Production code / spec contract / event payload / API schema 0 변경 (markdown only).
- [ ] sibling spec (outbound-saga.md, BE-157 inventory database-design.md) 와 link style 일관성 유지.

# Related Specs

- `projects/wms-platform/specs/contracts/http/tms-shipment-api.md` (BE-147 authoring)
- `projects/wms-platform/specs/services/master-service/database-design.md` (BE-161 authoring)
- `projects/wms-platform/specs/services/outbound-service/database-design.md` (BE-163 authoring)
- TASK-MONO-085/086 precedent (sibling mechanical batch closure pattern)

# Related Contracts

해당 없음 (link path 수정만, contract 변경 0).

# Target Service

해당 없음 — markdown link / path 수정만 (3 service: outbound TMS contract + master + outbound DB design).

# Edge Cases

- BE-147 author `tms-shipment-api.md` 의 다른 cross-ref (e.g. `outbound-service-api.md`, `external-integrations.md`) 는 sibling path (same directory) 라 영향 없음 (재검증 PASS).
- master `ZoneType.java` 의 production code package 가 `domain/model/` 로 v1 부터 안정 — Flyway V1 enum literal 6 value 와 별개 (DB enum 은 정상).
- outbound `saga-status.md` 가 이미 sibling `outbound-saga.md` 에서 정상 참조됨 — 다른 spec 에서도 같은 basename 일관성 보존.

# Failure Scenarios

- A: link checker 가 새 dead-ref 발견 (e.g. sed 의 over-match) → revert + per-file Edit 로 정확한 path 만 수정.
- B: `domain/model/ZoneType.java` 가 production code 에서 또 rename 됐을 경우 → `find` 재실행 + 정확한 현재 path 인용.

# Validation Plan

1. 3 file 의 수정 후 link checker (`/tmp/check_links2.sh` pattern):
   ```bash
   for f in projects/wms-platform/specs/contracts/http/tms-shipment-api.md \
            projects/wms-platform/specs/services/master-service/database-design.md \
            projects/wms-platform/specs/services/outbound-service/database-design.md; do
     grep -oE '\]\(([^)]+\.(md|java))\)' "$f" | sed -E 's/^\]\((.+)\)$/\1/' | while read p; do
       full="$(dirname "$f")/$p"
       [[ ! -e "$full" ]] && echo "BROKEN: $f -> $p"
     done
   done
   ```
   exit 1 (no output) 기대.
2. `git diff` 가 markdown 만 + 5 line edit 확인.

# Implementation Notes

- 2 commit / 1 branch: (1) ready/ task author, (2) fix 3 file + move ready/ → review/.
- branch name `task/be-165-wms-spec-dead-ref-batch` — CLAUDE.md § Cross-Project Changes "Branch name constraint" 준수 (no `master` substring).
- TASK-MONO-085/086 precedent 답습.

# Outcome

(완료 후 갱신)
