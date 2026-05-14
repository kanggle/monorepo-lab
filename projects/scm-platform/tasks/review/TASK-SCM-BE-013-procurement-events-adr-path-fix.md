# Task ID

TASK-SCM-BE-013

# Title

SCM scm-procurement-events.md ADR-MONO-004 path fix (refactor-spec Tier 3 #2 closure, 1-line mechanical)

# Status

review

# Owner

scm-platform

# Task Tags

- scm
- spec
- mechanical
- dead-reference
- cleanup

---

# Goal

`/refactor-spec all --dry-run` (2026-05-14, BE-165 + BE-283 직속 후속) **Tier 3 #2 closure** — SCM 의 마지막 dead-ref 1건 1-line fix.

**Finding**: `projects/scm-platform/specs/contracts/events/scm-procurement-events.md:44` 의 `[ADR-MONO-004](../../../docs/adr/)`:
- 파일 depth = 4 levels (`projects/scm-platform/specs/contracts/events/X.md`)
- 3 `../` from dir = `projects/scm-platform/` (project root, repo root 아님)
- 실제 ADR-MONO-004 file = `docs/adr/ADR-MONO-004-shared-messaging-scaffolding.md` (repo root)
- → 5 `../` 필요 + 정확한 filename 포함 navigation 권장 (BE-283 libs/* 패턴 답습)

**Fix**: `../../../docs/adr/` → `../../../../../docs/adr/ADR-MONO-004-shared-messaging-scaffolding.md`

**Origin**: TASK-SCM-BE-009 (scm-procurement-events.md authoring 2026-05-11) 시점의 path depth miscount. SCM project root 가리키도록 author 가 의도했을 가능성도 있지만, ADR file 이 repo root `docs/adr/` 에만 존재 → broken navigation. BE-165 (WMS) + BE-283 (GAP) sibling precedent.

# Scope

## In Scope

- `projects/scm-platform/specs/contracts/events/scm-procurement-events.md:44` L44 — 1-line Edit (link path).

## Out of Scope

- 다른 SCM spec file — `bash /tmp/check_gap_links.sh` style checker 결과 SCM scope 에서 잔존 dead-ref 0건 (이 1건만).
- TASK-BE-284 PiiMaskingUtils Tier 2 (GAP, 별 task — judgment required).

# Acceptance Criteria

- [ ] 1 dead-reference PASS — link target file 실재 + navigation 정상.
- [ ] Production code / spec contract / event payload / API schema 0 변경 (markdown link only).

# Related Specs

- `projects/scm-platform/specs/contracts/events/scm-procurement-events.md` (TASK-SCM-BE-009 authoring)
- `docs/adr/ADR-MONO-004-shared-messaging-scaffolding.md` (repo root, link target)
- TASK-BE-165 / TASK-BE-283 precedent (sibling mechanical batch closure)

# Related Contracts

해당 없음 (link path 수정만).

# Target Service

해당 없음 — events contract spec link path 수정만.

# Edge Cases

- A: ADR-MONO-004 rename 가능성 — repo root 의 file 명 확인됨 (`docs/adr/ADR-MONO-004-shared-messaging-scaffolding.md`, BE-283 시점에 검증).

# Failure Scenarios

- A: SCM spec 의 다른 `../../../docs/adr/` 패턴 존재 → grep 으로 single hit 검증 후 진행.

# Validation Plan

1. Edit 후 `[ -e projects/scm-platform/specs/contracts/events/../../../../../docs/adr/ADR-MONO-004-shared-messaging-scaffolding.md ]` exit 0.
2. `git diff --stat` = 1 file / 1 line.

# Implementation Notes

- 2 commit / 1 branch: (1) ready/ task author, (2) Edit + lifecycle move ready/ → review/.
- branch name `task/scm-be-013-procurement-events-adr-path-fix` — CLAUDE.md § Cross-Project Changes "Branch name constraint" 준수 (no `master` substring).
- TASK-BE-165/283 precedent 답습 (gradually shrinking scope: WMS 5 → GAP 47 → SCM 1).

# Outcome

(완료 후 갱신)
