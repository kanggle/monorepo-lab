# Task ID

TASK-MONO-074

# Title

`.github/workflows/ci.yml` path-filter — `*.md`-only PR 에 대한 e2e skip + `contracts/**` 변경 시 force-full e2e

# Status

ready

# Owner

monorepo

# Task Tags

- ci
- chore

---

# Goal

현재 path-filter (TASK-MONO-045 baseline + TASK-MONO-058 `tasks/**` exclusion 확장) 는 "어느 프로젝트의 어느 path 가 변경되었는가" 만 판정한다. 변경의 **성격** (markdown-only 문서 정리 vs. 실제 코드/contract 변경) 은 구분하지 않으므로 다음 false-positive 가 흔히 발생한다.

1. **markdown-only PR 이 e2e 를 트리거** — 예: `rules/common.md` 한 줄 수정, `projects/wms-platform/docs/guides/foo.md` 수정, `platform/lint-remediation-message-standard.md` 수정 시 각각 `libs` / `wms` flag = true → 모든 wms e2e 또는 모든 e2e 가 실행됨. 실제 코드 변화 0.
2. **contract 변경의 e2e 강도 보장 없음** — `projects/<n>/specs/contracts/**` 변경은 cross-service 호환성의 source of truth 이므로 모든 e2e 가 강제로 돌아야 안전한데, 현재는 단일 project flag 로만 잡혀 다른 project e2e 는 skip 될 수 있음 (consumer side breakage 누락).

Phase 1 scope (사용자가 합의한 e2e 3단계 전략 — **skip / light / full** — 의 첫 단계, `light/full` 분류는 Phase 2 별도 task) 는 다음 두 axis 를 path-filter 만으로 해소한다.

- **axis A — skip 자동화**: 모든 기존 flag 에 `'!**/*.md'` exclusion 추가 → markdown-only PR 은 어떤 flag 도 true 가 되지 않아 build-and-test / integration / e2e 모두 skip (단, push to main 은 항상 trigger fallback 유지).
- **axis B — force-full e2e**: 신규 `contracts` flag (`projects/*/specs/contracts/**`) 도입, 모든 e2e job 의 `if:` 조건에 `|| needs.changes.outputs.contracts == 'true'` 추가 → 어느 project 의 contract 만 변경되어도 5 project e2e + frontend e2e smoke 모두 강제 실행.

memory provenance: 사용자 2026-05-13 세션 "e2e 테스트 (스킵/가볍게/완전히) 전략" — Phase 1 (skip 자동화 + force-full) 단독 우선 진행 합의. Phase 2 (`@Tag("smoke")` 표준화 + `e2eSmokeTest` / `e2eFullTest` gradle task 분리) 와 Phase 3 (full e2e 의 nightly 이전 — gap/scm/fan/wms 에 ecommerce 패턴 확장) 는 별도 ADR + task 로 분리.

---

# Scope

## In Scope

- `.github/workflows/ci.yml` `changes` job filter section (L68 부근):
  - `libs` flag 에 `'!**/*.md'` exclusion 추가 (현재 positive: `libs/**`, `platform/**`, `rules/**`, `.claude/**`, `tasks/templates/**`, `build.gradle`, `settings.gradle`, `package.json`, `tsconfig.base.json`).
  - 5 project flag (`wms` / `ecommerce` / `gap` / `fan` / `scm`) 에 각각 `'!projects/<n>/**/*.md'` exclusion 추가 (`!projects/<n>/tasks/**` 유지).
  - 신규 `contracts` flag 정의: `projects/*/specs/contracts/**`.
- `outputs` 섹션에 `contracts: ${{ steps.filter.outputs.contracts }}` 추가.
- 모든 e2e 관련 job 의 `if:` 조건에 `|| needs.changes.outputs.contracts == 'true'` 추가:
  - `frontend-e2e-smoke` (~L519)
  - `e2e-tests` (wms gateway-master live-pair, ~L815)
  - `fan-platform-e2e` (~L907)
  - `scm-platform-e2e` (~L1007)
  - (gap 는 현재 `e2eTest` job 미정의 — 추가하지 않음, 별도 task 후보)
- 헤더 주석 블록 (L29~L66) 갱신:
  - filter 카탈로그에 `contracts` 추가.
  - "Edge case — markdown-only PR" 항목 추가 (모든 flag false → skip).
  - "Edge case — contracts-only PR" 항목 추가 (`contracts` flag → 모든 e2e force-full).
  - TASK-MONO-074 reference 추가.
- `tasks/INDEX.md` `## done` 섹션에 1-line outcome (PR 머지 후 close chore 단계에서 추가).

## Out of Scope

- `@Tag("smoke")` / `@Tag("full")` annotation 도입 → **Phase 2 별도 task** (ADR 동반).
- gradle `e2eSmokeTest` / `e2eFullTest` task 분리 → Phase 2.
- ecommerce 외 full e2e 의 nightly 이전 → Phase 3 별도 task.
- `nightly-e2e.yml` 변경 → 본 PR scope 외 (이미 cron + push-to-main fallback).
- `projects/<n>/docs/**` / `knowledge/**` 의 markdown 외 변경 (예: `.png`, `.puml`) — markdown-only exclusion 이외의 정교한 분류는 별도 ADR.
- `architecture.md` 의 markdown exclusion 위험 분석 → 본 Goal 에서는 "architecture.md 단독 변경 = 동반 코드 변경 없으면 e2e 의미 없음, 동반 코드 변경이 있으면 코드 path 가 별도 매칭됨" 으로 정리 (Edge Cases 섹션 참조).

---

# Acceptance Criteria

- [ ] `.github/workflows/ci.yml` `changes` job 의 `libs` flag 에 `'!**/*.md'` exclusion entry 추가.
- [ ] `wms` / `ecommerce` / `gap` / `fan` / `scm` 5 project flag 에 각각 `'!projects/<full-project-dir>/**/*.md'` exclusion 추가 (`!projects/<n>/tasks/**` 유지).
- [ ] 신규 `contracts` flag = `'projects/*/specs/contracts/**'` 추가, `outputs` 에 노출.
- [ ] 4 e2e job (`frontend-e2e-smoke`, `e2e-tests`, `fan-platform-e2e`, `scm-platform-e2e`) 의 `if:` 조건에 `|| needs.changes.outputs.contracts == 'true'` 추가.
- [ ] 헤더 주석 블록 (L29~L66) 의 filter 카탈로그 + edge-case 목록 갱신, TASK-MONO-074 reference 표기.
- [ ] **Self-CI 검증**: 본 PR 은 `.github/workflows/ci.yml` 변경 → `workflows` flag = true → 모든 job activate → full pipeline 회귀 가드 통과.
- [ ] **Deferred 검증 (다음 자연발생 PR)**:
  - markdown-only PR (예: `rules/common.md` 한 줄 수정) → 모든 flag false → e2e job 모두 SKIP, build-and-test 도 skip (PR 의 경우; push-to-main 은 fallback 으로 trigger).
  - `projects/wms-platform/specs/contracts/openapi.yaml` 만 변경한 PR → `contracts` flag = true → wms 외 ecommerce/fan/scm e2e 도 모두 trigger.

---

# Related Specs

- `tasks/INDEX.md` § Move Rules / PR Separation Rule
- `docs/guides/monorepo-workflow.md` § CI Job Areas (path-filter mechanics)
- `tasks/done/TASK-MONO-045-*.md` (baseline path-filter, 47x speedup)
- `tasks/done/TASK-MONO-058-path-filter-tasks-exclusion.md` (직접 선행 — `!tasks/**` exclusion 패턴 정확히 본 task 가 확장)
- Memory `project_ci_path_filter_045.md`

# Related Skills

N/A — CI infrastructure edit, single-file YAML.

---

# Related Contracts

None.

---

# Target Service

N/A — shared CI configuration only.

---

# Architecture

N/A — single-file YAML edit. 본 task 는 ADR 동반하지 않음 (`@Tag` 표준화 / nightly 이전 같은 의사결정 부담 없음). Phase 2/3 가 ADR-MONO-XXX-e2e-tag-taxonomy 로 별도 진행.

---

# Implementation Notes

## paths-filter v3 semantics

- 한 filter 내 패턴 리스트는 OR-eval. `!` prefix = negation.
- 파일이 "in" 으로 간주되려면: positive 패턴 최소 하나 매칭 AND negation 패턴 매칭 없음.
- flag 값 = 그 filter 에서 "in" 으로 분류된 파일이 한 개라도 있으면 `true`.

## markdown-only PR 매칭 흐름

- 예: PR 이 `rules/common.md` 만 변경.
- `libs` filter:
  - positive `rules/**` 매칭 → 후보로 "in".
  - negation `!**/*.md` 매칭 → "out".
  - in 파일 0 → flag false. ✅
- `wms` filter:
  - positive 패턴 `projects/wms-platform/**` 매칭 안 함 → 후보 자체 없음.
  - flag false. ✅
- `contracts` filter:
  - positive `projects/*/specs/contracts/**` 매칭 안 함.
  - flag false. ✅
- 결과: 모든 e2e + build-and-test skip.

## mixed PR (markdown + code) 매칭 흐름

- 예: PR 이 `rules/common.md` + `libs/java-common/src/main/java/Foo.java` 변경.
- `libs` filter:
  - `Foo.java`: positive `libs/**` 매칭, negation `!**/*.md` 매칭 안 함 → "in". ✅
  - `common.md`: positive `rules/**` 매칭, negation `!**/*.md` 매칭 → "out".
  - in 파일 1 → flag true.
- 결과: 정상적으로 e2e 트리거. **mixed PR 안전**.

## contracts-only PR 흐름

- 예: PR 이 `projects/wms-platform/specs/contracts/asn.yaml` 만 변경.
- `wms` filter:
  - positive `projects/wms-platform/**` 매칭 → "in".
  - negation `!projects/wms-platform/tasks/**`, `!projects/wms-platform/**/*.md` 매칭 안 함 → "in" 유지.
  - flag true → wms e2e trigger.
- `contracts` filter:
  - positive `projects/*/specs/contracts/**` 매칭 → "in".
  - flag true.
- 결과: wms 자체 flag + `contracts` flag 동시 true → wms + ecommerce + fan + scm + frontend-smoke e2e 모두 trigger (cross-service consumer 검증).

## architecture.md 의 markdown exclusion 위험

- `projects/wms-platform/specs/services/master-service/architecture.md` 단독 변경 PR:
  - `wms` filter: positive 매칭 → "in" 후보. negation `!projects/wms-platform/**/*.md` 매칭 → "out". flag false.
  - 결과: e2e skip.
- 이게 안전한가? **YES** — architecture.md 만의 단독 변경은 spec PR (per PR Separation Rule); 구현 commit 은 별도 PR 에서 코드 변경과 함께 들어오며 그 PR 에서 e2e 가 정상 트리거. spec-only PR 의 e2e 는 의미 없음 (구현 코드 변화 0).

## edge — 새 markdown 파일 추가 외에 .png/.puml/.txt 만 추가하는 PR

- 본 task 의 negation 은 `**/*.md` 만 제외. 다른 binary/doc 확장자는 그대로 매칭됨 → e2e 트리거.
- 잘못된 false-positive 이지만 빈도 매우 낮음 + 별도 task 후보 (out of scope 명시).

---

# Edge Cases

- **markdown-only PR**: 모든 flag false → CI 전체 skip (PR). push-to-main fallback 은 `github.event_name == 'push'` 조건으로 별도 유지 → main 의 markdown 푸시도 build-and-test 한 번은 돌도록 보장.
- **mixed code + markdown PR**: 코드 path 가 positive 매칭, markdown 은 negation 으로 out → 정상 e2e trigger.
- **contracts-only PR**: 단일 project contracts 변경 → `contracts` flag → 5 project e2e + frontend smoke 모두 trigger.
- **mixed contracts + markdown PR**: 코드/yaml 파일이 contracts/** 매칭 → flag true. markdown 은 out. 정상.
- **tasks/-only PR (TASK-MONO-058 base case)**: `!projects/<n>/tasks/**` + `!**/*.md` 둘 다 negation 으로 작동 → 모두 out → skip. **변화 없음**.
- **workflow self-change PR (본 PR)**: `workflows` flag = true → 모든 job activate. 회귀 가드.
- **`.claude/skills/*.md` 변경**: `libs` flag positive `.claude/**` 매칭, negation `!**/*.md` 매칭 → out. flag false → skip. 의도된 동작 (skill markdown 변경은 코드 영향 0).
- **`.claude/hooks/*.ps1` 변경**: positive `.claude/**` 매칭, negation 매칭 안 함 → in. flag true → 정상 trigger.
- **`projects/<n>/specs/services/<s>/architecture.md` 단독 변경**: 위 § architecture.md 위험 분석 참조 — 의도된 skip.
- **`projects/<n>/docs/adr/ADR-<n>.md` 단독 변경**: 의도된 skip (spec-only PR, 구현 commit 별도).
- **여러 project 의 contracts 동시 변경**: `contracts` flag = true (한 번만 true 면 됨), 모든 project flag 도 각자 true → 모든 e2e trigger. cross-project breaking change 완전 검증.

---

# Failure Scenarios

- **paths-filter v3 `!` 패턴 quirk** (예: positive 가 너무 좁아서 negation 이 아무것도 안 잡음): TASK-MONO-058 가 이미 동일 `!` 패턴을 5 project flag 에서 검증함 → 본 task 는 그 위에 `**/*.md` 만 추가하므로 동일 mechanism. self-CI 가 회귀 가드.
- **CI yaml parse error**: GitHub Actions 가 push 시 즉시 실패 표시, workflow 실행 자체가 막힘. 본 PR 의 self-CI 가 catch.
- **deferred 검증 실패** — markdown-only PR 이 e2e 를 트리거하는 회귀가 다음 자연발생 PR 에서 발견될 경우: 즉시 fix task (TASK-MONO-074-fix-XXX) ready/ 에 author, 본 PR 의 yaml hunk revert + 패턴 재설계.
- **deferred 검증 실패** — contracts-only PR 이 cross-project e2e 를 트리거 안 하는 경우: `contracts` filter pattern 의 `projects/*/specs/contracts/**` 글롭이 nested directory 까지 닿는지 확인 (`**`); 만약 닿지 않으면 `projects/**/specs/contracts/**` 로 보정.

---

# Test Requirements

- **Self-CI (본 PR)**: `workflows` flag → full pipeline. 모든 e2e job 그대로 green 통과해야 머지 가능. 회귀 0.
- **Deferred 자연 검증 1** (markdown-only PR): 다음 `/validate-rules` 또는 `/audit-memory` 또는 docs typo fix PR 에서 모든 e2e SKIP 확인. 본 task close chore 시점에 1 case 실측 기록.
- **Deferred 자연 검증 2** (contracts-only PR): 다음 contract 갱신 PR (예: openapi.yaml minor field 추가) 에서 5 project e2e + frontend smoke 모두 trigger 확인.

---

# Definition of Done

- [ ] `.github/workflows/ci.yml` 변경 (libs/5 project flag + 신규 contracts flag + outputs + 4 e2e job if 확장 + 주석 블록 갱신) 완료.
- [ ] self-CI green (workflows flag full-pipeline 회귀 가드 통과).
- [ ] `tasks/INDEX.md` `## done` 에 1-line outcome entry 추가 (close chore 단계).
- [ ] task 파일 `Status: ready → review → done` 진행.
- [ ] memory: Phase 2/3 follow-up 후보를 새 entry 또는 기존 `project_ci_path_filter_045.md` 갱신으로 기록.

---

# Provenance

Surfaced 2026-05-13 in user session — e2e 3단계 전략 (skip / light / full) 논의. Phase 1 단독 task 로 분리 (Phase 2 `@Tag` 표준화 + Phase 3 nightly 이전은 ADR 동반 별도 task). 직접 선행 = TASK-MONO-058 (path-filter `!tasks/**` 패턴 검증) + TASK-MONO-045 (baseline).

분석=Opus 4.7 / 구현 권장=Sonnet 4.6 (mechanical yaml edit + 잘 정의된 acceptance criteria; paths-filter v3 mechanics 는 TASK-MONO-058 에서 이미 검증된 기반).
