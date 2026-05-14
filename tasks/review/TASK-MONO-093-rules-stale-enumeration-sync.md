# Task ID

TASK-MONO-093

# Title

Service Type catalog enumeration drift sync (architecture.md + coordinator.md) + entrypoint.md Step 0 monorepo phrasing — validate-rules report 직속 후속

# Status

review

# Owner

monorepo

# Task Tags

- monorepo
- platform
- agents
- governance
- validate-rules

---

# Goal

`/validate-rules` (2026-05-15) report 의 Warning 3건 closure. 모두 mechanical text edit, ~5 line 미만, 정합성 polish.

**Finding (Warning, 3건)**:

1. **`platform/architecture.md:40`** — Service Type catalog 괄호 enumeration 이 7개로 stale (omits `identity-platform`). Authoritative source `platform/service-types/INDEX.md` = 8 types. MONO-091 (glossary.md L30) sibling drift, 같은 cycle 의 잔존.
2. **`.claude/agents/common/coordinator.md:21`** — 동일 stale enumeration (7개, omits `identity-platform`).
3. **`platform/entrypoint.md:19`** — Step 0 문구 "Read `PROJECT.md` at repository root." 가 monorepo 레이아웃과 어긋남. CLAUDE.md § Identify the Target Project 명시: "walk up from the working location to the nearest ancestor with a `PROJECT.md` (typically `projects/<name>/PROJECT.md`)". CLAUDE.md master ruleset > entrypoint.md → entrypoint.md 보정 필요.

**Fix**:
- (1)+(2): 7-type enumeration → 8-type (add `identity-platform`) OR pointer-only 으로 simplify (drift class 차단 효과 측면에서 MONO-091 답습 가능). 본 task 는 8-type enumeration 유지 (narrative 정확성 보존), 향후 9th type 추가 시 두 site 동시 sync 의무는 INDEX.md catalog change 의 표준 절차로 충분.
- (3): "Read `PROJECT.md` at repository root." → "Read the active project's `PROJECT.md` (walk up from the working location to the nearest ancestor with `PROJECT.md` — typically `projects/<name>/PROJECT.md` in monorepo mode, repo root in single-project mode)." — TEMPLATE.md 추출 시점의 single-project 시나리오도 함께 cover.

# Scope

## In Scope

- `platform/architecture.md` L40 — enumeration 7 → 8 (add `identity-platform`).
- `.claude/agents/common/coordinator.md` L21 — enumeration 7 → 8 (add `identity-platform`).
- `platform/entrypoint.md` L19 — Step 0 문구 monorepo-aware 표현 보정.

## Out of Scope

- Info-tier 9 `PROJECT.md` symbolic link 인스턴스 — template-friendly placeholder 의도, deferred. 별도 cosmetic task 후보 (낮은 가치).
- `platform/service-types/INDEX.md` 자체 (이미 8 types 정합, source of truth 유지).
- 다른 enumeration drift 자동 audit — 본 task 는 validate-rules 가 surface 한 3건만 처리.

# Acceptance Criteria

- [ ] `platform/architecture.md:40` enumeration 에 `identity-platform` 포함 (검증: `grep -E "identity-platform" platform/architecture.md` ≥ 1 hit).
- [ ] `.claude/agents/common/coordinator.md:21` enumeration 에 `identity-platform` 포함 (검증: `grep -E "identity-platform" .claude/agents/common/coordinator.md` ≥ 1 hit).
- [ ] `platform/entrypoint.md:19` Step 0 문구가 monorepo 레이아웃과 일치 (검증: `grep "at repository root" platform/entrypoint.md` exit 1).
- [ ] Production code / spec contract / requirement 0 변경 (rule library text polish only).
- [ ] HARDSTOP-03 hook PASS (platform/ shared 영역 변경이지만 project-specific content 0).

# Related Specs

- `platform/architecture.md` (target)
- `platform/entrypoint.md` (target)
- `platform/service-types/INDEX.md` (source of truth — 변경 안 함)
- `.claude/agents/common/coordinator.md` (target)
- `CLAUDE.md` § Identify the Target Project (Read First) — entrypoint.md 문구 정렬 기준
- `.claude/commands/validate-rules.md` (audit driver)

# Related Contracts

해당 없음.

# Target Service

해당 없음 — rule library polish.

# Edge Cases

- A: architecture.md L40 의 enumeration 이 narrative parenthetical 이라 pointer-only 으로 simplify 도 가능 (MONO-091 답습). 본 task 는 enumeration 유지 결정 — sibling coordinator.md L21 도 동일 narrative 패턴, 일관성 우선.
- B: entrypoint.md 의 문구 보정이 TEMPLATE.md 추출 시점의 single-project 시나리오와 호환 필요 — "monorepo mode / single-project mode" dual phrasing 으로 양쪽 모두 cover.

# Failure Scenarios

- A: 향후 9th service type 추가 시 architecture.md + coordinator.md + INDEX.md + entrypoint.md 표 4 site 동시 sync 필요 — INDEX.md catalog change 의 표준 절차로 충분 (MONO-091 의 pointer-only 패턴 대비 약간 더 복잡하지만 narrative 정확성 trade-off).

# Validation Plan

1. Edit 후 `grep -c "identity-platform" platform/architecture.md` ≥ 1.
2. `grep -c "identity-platform" .claude/agents/common/coordinator.md` ≥ 1.
3. `grep "at repository root" platform/entrypoint.md` exit 1 (문구 제거).
4. `git diff --stat` = 3 file / ~5 line 미만 edit.
5. validate-rules 재실행 시 Warning 3건 사라지는지 확인 (Info 9건은 의도된 deferred).

# Implementation Notes

- **D4 OVERRIDE applied** per ADR-MONO-003a § D1.1 (B common rule cleanup 연장선 — rule library drift polish, MONO-091 sibling, 같은 cycle 잔존). User-explicit nod via "/validate-rules → 권장 진행사항 → 진행" sequence.
- monorepo-level shared 영역 → root `tasks/` (project-internal 아님).
- 2 commit / 1 branch: (1) ready/ task author + INDEX.md ready row, (2) 3 file Edit + lifecycle move ready/ → review/ + INDEX.md done row.
- branch name `task/mono-093-rules-stale-enumeration-sync` — CLAUDE.md § Cross-Project Changes "Branch name constraint" 준수 (`master` substring 없음).
- single-PR closure 패턴 (ready → review 직접, in-progress 우회) — MONO-084~091 precedent 답습, mechanical < 5 line.
- precedent: MONO-091 (glossary Service Type pointer, 1-line) + MONO-084 (platform Change Rule batch backfill, 14 file) + MONO-087 (error code backfill, 1-line).

# Outcome

(to be filled after impl commit)
