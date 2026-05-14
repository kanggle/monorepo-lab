# Task ID

TASK-MONO-094

# Title

ADR-MONO-012 PROPOSED → ACCEPTED transition (cross-project architecture.md canonical form, WMS Identity-table form)

# Status

done

# Owner

monorepo

# Task Tags

- monorepo
- adr
- governance
- architecture-canonical-form

---

# Goal

ADR-MONO-012 (cross-project `architecture.md` canonical form) status PROPOSED → ACCEPTED. user-explicit nod via "/audit-memory → 다음 작업 추천 → Option B" sequence. D1 WMS Identity-table canonical 확정 + D3 migration order (SCM → GAP → ecommerce) 동시 발효.

본 task = governance authoring 만 (status flip + § History row 추가 + INDEX row update). 실제 migration (24 architecture.md edit + HARDSTOP-10 hook propagation) 은 별 follow-up task 로 분리:

- **TASK-MONO-095**: SCM 3 architecture.md migration (procurement / inventory-visibility / gateway)
- **TASK-MONO-096**: HARDSTOP-10 hook propagation cross-project enforce
- **TASK-MONO-097**: GAP 8 architecture.md migration
- **TASK-MONO-098**: ecommerce 13 architecture.md migration

# Scope

## In Scope

- `docs/adr/ADR-MONO-012-cross-project-architecture-md-canonical-form.md` § Status: `PROPOSED` → `ACCEPTED`.
- `docs/adr/ADR-MONO-012-cross-project-architecture-md-canonical-form.md` § History row 추가 (ACCEPTED 2026-05-15 transition).
- `docs/adr/INDEX.md` ADR-MONO-012 row Status: `PROPOSED` → `ACCEPTED`.

## Out of Scope

- 실제 24 architecture.md migration — 별 follow-up tasks (MONO-095/097/098).
- HARDSTOP-10 hook 코드 update — 별 task (MONO-096).
- fan-platform partial-align catch-up — § Outstanding follow-ups 명시, 별 task 가치 평가 (D1 "required when dual" 명시이므로 fan single-type service 는 skip 가능, 검토만).
- ADR-MONO-012 § 본문 (D1-D5 content) 수정 — option C-1 audit-only 패턴 답습, ACCEPTED transition 만 § Status + § History 갱신.

# Acceptance Criteria

- [ ] `docs/adr/ADR-MONO-012-...md` § Status = `ACCEPTED` (검증: `grep -c "^\*\*Status:\*\* ACCEPTED" docs/adr/ADR-MONO-012-*.md` = 1).
- [ ] `docs/adr/ADR-MONO-012-...md` § History 에 ACCEPTED row 추가 (검증: `grep -c "ACCEPTED 2026-05-15" docs/adr/ADR-MONO-012-*.md` ≥ 1).
- [ ] `docs/adr/INDEX.md` ADR-MONO-012 row Status = `ACCEPTED` (검증: `grep "ADR-MONO-012.*ACCEPTED" docs/adr/INDEX.md` ≥ 1 hit).
- [ ] Production code / spec contract / 24 architecture.md content = 0 변경 (본 task scope out).

# Related Specs

- `docs/adr/ADR-MONO-012-cross-project-architecture-md-canonical-form.md` (target — § Status + § History)
- `docs/adr/INDEX.md` (target — ADR row)
- `docs/adr/ADR-MONO-009-chrome-devtools-mcp-visual-regression.md` (PROPOSED → ACCEPTED transition precedent, ADR-MONO-003b 답습)
- `docs/adr/ADR-MONO-003b-phase-5-launch-criteria.md` (ACCEPTED transition pattern — TASK-MONO-070 답습)

# Related Contracts

해당 없음.

# Target Service

해당 없음 — ADR governance authoring.

# Edge Cases

- A: ADR-MONO-009 precedent (Chrome DevTools MCP) 가 indefinite-PROPOSED 도 legitimate 명시. 본 ACCEPTED 결정은 user-explicit nod 가 있으므로 자연 ACCEPTED. 단 후속 migration cycle 비용 (24 file + hook propagation) 사용자가 명시 인지 필요 — task body 에 5-PR sequence 명시.

# Failure Scenarios

- A: ACCEPTED transition 후 migration cycle 도중 사용자가 비용 부담 결정 시 → ADR § D5 (indefinite-PROPOSED) 회귀 어려움. 대신 follow-up task 단위 deferred → 자연 partial state 가능 (예: SCM migration 만 완료, GAP/ecommerce deferred). § Verification post-ACCEPTED 의 Verification 항목들이 partial 가능성 명시 안 함 — task body 본 § A 가 그 안내.

# Validation Plan

1. ADR-012 § Status header 가 `ACCEPTED` 인지 grep.
2. ADR-012 § History 에 ACCEPTED transition row 추가 인지 grep.
3. docs/adr/INDEX.md row 의 Status 가 `ACCEPTED` 인지 확인.
4. `git diff --stat` = 2 file (ADR + INDEX) / ~3 line edit.

# Implementation Notes

- **D4 OVERRIDE applied** per ADR-MONO-003a § D1.1 (governance authoring, refactor-spec follow-up — ADR-012 자체가 refactor-spec Tier 3 reconsider 의 governance escalation). MONO-092 sibling pattern (ADR-009 답습).
- monorepo-level shared 영역 → root `tasks/`.
- 2 commit / 1 branch: (1) ready/ task author + INDEX.md ready row + ADR + ADR INDEX edits, (2) lifecycle move ready/ → review/ + INDEX.md done row.
- branch name `task/mono-094-adr-mono-012-accepted-transition` — CLAUDE.md § Cross-Project Changes "Branch name constraint" 준수.
- single-PR closure 패턴 (ready → review 직접, in-progress 우회) — MONO-084~093 precedent 답습.
- 후속 migration cycle sequence (refactor-spec / validate-rules cycle 의 portfolio governance escalation 11번째 task 부터):
  - MONO-095 SCM 3 file (smallest, build confidence)
  - MONO-096 HARDSTOP-10 hook propagation (cross-project enforce)
  - MONO-097 GAP 8 file
  - MONO-098 ecommerce 13 file

# Outcome

(to be filled after impl commit)
