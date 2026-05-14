# Task ID

TASK-MONO-096

# Title

HARDSTOP-10 hook canonical form fixture coverage + ADR-MONO-012 § 1.4/D1 wording audit-trail (option C-1)

# Status

review

# Owner

monorepo

# Task Tags

- monorepo
- hooks
- adr-mono-012
- hardstop-10

---

# Goal

ADR-MONO-012 D4 (hook propagation) follow-up. SCM migration (MONO-095) 진행 중 발견된 두 가지 정합성 finding 처리:

**Finding 1 — ADR-MONO-012 § 1.4 wording 부정확**: 본문 명시 "hook currently fires on `### Service Type Composition` absence in **WMS service architecture.md only**" — 실제 hook 는 이미 cross-project 동작 중. SCM procurement Edit 시 fire 가 확인됨.

**Finding 2 — ADR-MONO-012 D1 wording 부정확**: 본문 명시 "Service Type Composition recommended (required when service combines multiple types)" — 실제 WMS practice + hook detection logic = always present (single 도 short body 필요, hook 의 `^#+\s*Service\s+Type` heading 패턴 매칭 trigger).

**처리 방식 (option C-1 audit-only)**: ADR 본문 직접 수정 X (시점적 결정 record 보존, MONO-088/089/090 precedent 답습). 본 task body + INDEX outcome 에 audit-trail 만. hook source 자체는 미터치 (canonical form 호환 이미 확인됨).

**fixture 갱신**: 현재 fixture (`hardstop-10-service-type-missing.ps1`) negative case 가 구 form (`## Service Type` H2 + body) 만 cover. canonical form (Identity table + `### Service Type Composition` H3) negative case 추가 — next batch (GAP/ecommerce migration) 의 회귀 가드.

# Scope

## In Scope

- `.claude/hooks/__tests__/hardstop-10-service-type-missing.ps1` — 3번째 case 추가 (canonical form negative — Identity table + `### Service Type Composition` H3 형식이 hook PASS 검증).
- `tasks/INDEX.md` outcome row 에 ADR-MONO-012 § 1.4 + D1 wording correction audit-trail 명시 (option C-1).

## Out of Scope

- ADR-MONO-012 본문 § 1.4 + D1 직접 수정 — option C-1 audit-only 패턴 (시점적 결정 record 보존, MONO-088/089/090/091 precedent 답습).
- hook source `.claude/hooks/hardstop-detect.ps1` 변경 — canonical form 호환 이미 확인됨 (SCM migration 시 PASS).
- GAP / ecommerce migration — 별 task MONO-097 / MONO-098.

# Acceptance Criteria

- [ ] `.claude/hooks/__tests__/hardstop-10-service-type-missing.ps1` 에 canonical form negative case (Identity table + Composition H3) 추가 (검증: `grep -c "Identity\|Service Type Composition" .claude/hooks/__tests__/hardstop-10-service-type-missing.ps1` ≥ 2).
- [ ] 추가 case `Assert-Allowed` 호출 (hook PASS 검증).
- [ ] `pwsh .claude/hooks/__tests__/hardstop-10-service-type-missing.ps1` 실행 시 3/3 PASS (positive + 구-form negative + canonical-form negative).
- [ ] INDEX outcome row 에 § 1.4 + D1 wording correction audit-trail 명시.
- [ ] Production code / spec contract / ADR 본문 = 0 변경.

# Related Specs

- `docs/adr/ADR-MONO-012-cross-project-architecture-md-canonical-form.md` § 1.4 (audit target) + § D1 (audit target) + § D4 (hook propagation context)
- `.claude/hooks/hardstop-detect.ps1` (canonical form 호환 검증, 미터치)
- `.claude/hooks/__tests__/hardstop-10-service-type-missing.ps1` (target, fixture 갱신)
- `.claude/hooks/__tests__/run-all.ps1` (fixture runner)

# Related Contracts

해당 없음.

# Target Service

해당 없음 — shared hook fixture polish.

# Edge Cases

- A: canonical form fixture 의 정확한 형식 결정 — Identity table row `| Service Type | \`rest-api\` ... |` 만 (Composition H3 부재) 경우 hook 가 detect 못 함 (procurement migration 시 확인됨). 따라서 fixture 는 Composition H3 포함 형식 (single-type short body).
- B: WMS canonical sample (`projects/wms-platform/specs/services/inventory-service/architecture.md`) 의 form 그대로 fixture 본문 답습 — 실제 portfolio form 과 일치.

# Failure Scenarios

- A: fixture 갱신 후 기존 case (구 form `## Service Type` H2) 가 회귀 가능성 — 두 case (구 form + canonical form) 동시 보존 필요 (hook 가 두 form 모두 PASS 해야 portfolio 의 historical 구 form 도 미회귀).

# Validation Plan

1. fixture 갱신 후 `pwsh .claude/hooks/__tests__/hardstop-10-service-type-missing.ps1` 실행 → 3/3 PASS 확인.
2. `pwsh .claude/hooks/__tests__/run-all.ps1` 실행 → 전체 fixture suite green 확인.
3. `git diff --stat` = 1 file (fixture) / ~20 line edit.

# Implementation Notes

- **D4 OVERRIDE applied** per ADR-MONO-003a § D1.1 (project-internal spec polish, ADR-MONO-012 governance follow-up). 12번째 누적 task (MONO-085~095 + 본 task).
- monorepo-level scope (`.claude/hooks/__tests__/` 변경).
- 2 commit / 1 branch: (1) ready/ task author + tasks/INDEX.md ready row + fixture 갱신, (2) lifecycle move ready → review + INDEX done row.
- branch name `task/mono-096-hardstop-10-hook-canonical-form-fixture` — CLAUDE.md § Cross-Project Changes "Branch name constraint" 준수.
- single-PR closure 패턴.

# Outcome

(to be filled after impl commit)
