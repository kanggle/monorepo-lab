# Task ID

TASK-MONO-029

# Title

rules/ 라이브러리 정합성 audit + cross-file drift 수정

# Status

in-progress

# Owner

backend

# Task Tags

- audit
- rules
- chore

---

# Required Sections

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Edge Cases
- Failure Scenarios

---

# Goal

monorepo 가 4 프로젝트 동거 (wms + ecommerce + GAP + fan-platform) 상태로 안정화된 시점에 (`project_monorepo_template_strategy.md` § Phase 4 트리거 충족) `rules/` 라이브러리 + 라우팅 레이어 (`.claude/config/`) 의 cross-file 정합성을 audit 하고 발견된 drift 를 수정한다.

본 task 는 **공통규칙·스펙 정리 시리즈 5개 task 중 첫 번째** — `rules/` 영역만 다룬다. 후속:
- TASK-MONO-030 candidate: spec drift (gap-integration.md / architecture.md 패턴 4 프로젝트 일관성)
- TASK-MONO-031 candidate: `libs/` audit (사용 빈도 + 중복 + dead code + project-specific leak)
- TASK-MONO-032 candidate: `.claude/` audit (skills/agents/commands stale / 중복 / 미사용)
- TASK-MONO-033 candidate: `TEMPLATE.md` ↔ 실제 monorepo 정합성

**전제**: ready/ + review/ 큐 모두 비어있음 (인터럽트 없는 audit 가능). 4 프로젝트 동거 + 큰 cutover 시리즈 (ecommerce GAP + fan-platform v1 + wms 5 active + GAP IdP) 모두 main 머지 완료.

---

# Scope

## In Scope

### 1. `/validate-rules` skill 실행 (자동 발견)

`Skill: validate-rules` 실행하여 rule 파일 간 inconsistency 자동 발견:
- `CLAUDE.md` § Source of Truth Priority 가 실제 파일 구조와 일치하는지
- `rules/taxonomy.md` 의 domain/trait narrative 정의 ↔ `.claude/config/domains.md` / `.claude/config/traits.md` 카탈로그 멤버십 ↔ `.claude/config/activation-rules.md` 매핑이 모두 동기화되어 있는지
- `rules/domains/<domain>.md` / `rules/traits/<trait>.md` 파일 존재 여부 ↔ taxonomy 에 선언된 항목 일치
- 각 프로젝트 `PROJECT.md` 의 `domain` / `traits` 가 모두 taxonomy 에 등록된 값인지 (Hard Stop 룰 위반 사전 catch)

### 2. taxonomy 정합성 매뉴얼 검증

`/validate-rules` 가 catch 못 할 수 있는 항목:
- `rules/taxonomy.md` 에 정의된 narrative 가 실제 `rules/domains/<domain>.md` 본문과 일관되는가
- `rules/traits/<trait>.md` 의 trait rule 이 다른 trait 와 conflict 하지 않는가 (예: `transactional` ↔ `read-heavy` 의 일관성)
- domain/trait 파일 안에 `## Overrides` 블록이 있다면 정확한 common 룰을 reference 하는가

### 3. 발견 항목 → fix commit (분할 권장)

audit 결과를 **상황별 분류**하여 처리:

| 분류 | 처리 방식 |
|---|---|
| Critical (Hard Stop 위반 / 빌드 깨짐 위험) | 본 PR 안에서 즉시 fix |
| Warning (drift / 일관성 부족) | 본 PR 안에서 fix 또는 PR body 에 명시 + 별도 follow-up |
| Suggestion (개선 권장) | PR body 에만 명시, 별도 follow-up |

같은 PR 안에서 정리하되, fix commit 은 영역별로 분리 (예: `fix(rules): taxonomy ↔ domains 동기화`, `fix(rules): activation-rules 매핑 누락 추가`).

### 4. on-demand 정책 명시 검증

`rules/README.md` 의 "Missing domain/trait files mean no additional constraints beyond common — do not auto-generate stubs" on-demand 정책이 실제로 위반되지 않았는지 확인. 예: `rules/domains/erp.md` / `scm.md` / `mes.md` 가 stub 생성됐는지 (없어야 정상).

## Out of Scope

- spec drift (각 프로젝트의 `specs/integration/gap-integration.md`, `specs/services/<service>/architecture.md` 의 일관성) — 별도 TASK-MONO-030
- `libs/` audit — 별도 TASK-MONO-031
- `.claude/skills/`, `.claude/agents/`, `.claude/commands/` 의 stale / 중복 / 미사용 검증 — 별도 TASK-MONO-032
- `TEMPLATE.md` 와 monorepo 현 상태 정합성 (신규 프로젝트 부트스트랩 instruction 정확성) — 별도 TASK-MONO-033
- `platform/` audit — 본 task scope 외 (필요 시 후속)
- 새 domain/trait 추가 (scm/erp/mes 도메인 파일 신설 등) — on-demand policy 따라 해당 도메인 프로젝트 시작 시점에

---

# Acceptance Criteria

- [ ] `/validate-rules` skill 실행 + 결과 PR body 에 첨부.
- [ ] Critical 항목 (있으면) 본 PR 안에서 모두 fix.
- [ ] Warning 항목은 fix 하거나 별도 follow-up task spec 으로 발행 (PR body 에 명시).
- [ ] Suggestion 항목은 PR body 에 카탈로그.
- [ ] taxonomy ↔ rules/domains/ ↔ rules/traits/ ↔ .claude/config/ 4-way 동기화 확인 결과 한 줄로 명시 (PASS / 항목별 issue 표기).
- [ ] on-demand 정책 위반 (불필요한 stub 파일) 없음 확인.

---

# Related Specs

- `rules/README.md` — rule 로딩 순서 + on-demand 정책
- `rules/taxonomy.md` — domain/trait narrative 정의 (authoritative)
- `.claude/config/domains.md` / `.claude/config/traits.md` — 카탈로그 멤버십
- `.claude/config/activation-rules.md` — trait/domain → 활성화 규칙 매핑
- `CLAUDE.md` § Source of Truth Priority + Hard Stop Rules
- `.claude/skills/audit/validate-rules/SKILL.md` (있으면) — skill 의 정확한 검사 범위

---

# Related Skills

- `validate-rules` (필수)
- `audit-memory` (참고 — memory audit 패턴)

---

# Target Component

- `rules/` 디렉토리 전체
- `.claude/config/` (domains.md / traits.md / activation-rules.md)
- 각 프로젝트 `PROJECT.md` 의 frontmatter (domain / traits 선언)

---

# Architecture

`platform/architecture-decision-rule.md` 따름. 본 task 는 코드 변경 없는 audit + chore.

---

# Implementation Notes

- `/validate-rules` 가 발견 못 한 항목은 매뉴얼 grep 으로 보강:
  - `grep -rn "domain:" projects/*/PROJECT.md` 로 모든 프로젝트의 domain 선언 수집
  - `grep -rn "traits:" projects/*/PROJECT.md` 동일
  - taxonomy 의 domain/trait list 와 비교
- `rules/taxonomy.md` 의 narrative 와 `rules/domains/<d>.md` 본문이 시간 차로 drift 했을 가능성 — 특히 ecommerce / wms / fan-platform / saas 4개 도메인이 가장 risk.
- `.claude/config/activation-rules.md` 의 매핑 테이블에서 누락된 활성화 항목 확인 (예: 새 trait `regulated` 가 추가됐는데 activation 매핑은 없음 등).

---

# Edge Cases

- **Skill `/validate-rules` 자체가 stale 한 경우**: skill 정의가 4 프로젝트 동거 시점 이전에 작성되어 새 프로젝트 (fan-platform, GAP) 를 검사 대상으로 인식 못 할 수 있음. skill 출력의 검사 대상 목록을 PR body 에 명시하여 사용자 확인.
- **Critical 항목이 너무 많아 본 PR 분량 초과**: Critical 만 본 PR, Warning/Suggestion 은 별도 task 로 분리.
- **on-demand 정책 위반**: stub 파일 발견 시 삭제 vs 보존 결정. 본 task 는 삭제 default (정책 따름), 단 사용자 명시 보존 의도가 있다면 fix 보류.

---

# Failure Scenarios

- **`/validate-rules` skill 실행 실패**: skill 정의 파일 없음 / 잘못된 invocation. 매뉴얼 검증으로 fallback (위 Implementation Notes 의 grep 패턴).
- **수정 시 다른 프로젝트의 PROJECT.md 가 깨짐**: domain/trait 이름 변경 시 모든 프로젝트의 PROJECT.md 동시 갱신 필요. 본 task 는 cross-project atomic PR 로 처리.
- **taxonomy 변경이 major bump 필요한 경우** (rules/taxonomy.md § Versioning): version bump + 모든 PROJECT.md 마이그레이션. 본 task scope 외 — 별도 task 로 분리.

---

# Test Requirements

- audit 자체가 검증 활동. 추가 테스트 코드 없음.
- 수정 후 `/validate-rules` 재실행 → PASS 확인.
- 4 프로젝트의 `./gradlew check` sample run (ecommerce 의 한 service + wms 의 한 service + GAP gateway + fan-platform community) 로 rules 변경이 빌드에 영향 없음 확인.

---

# Definition of Done

- [ ] `/validate-rules` 실행 + 결과 PR body 첨부.
- [ ] Critical 모두 fix.
- [ ] Warning / Suggestion 분류 + PR body 명시.
- [ ] `/validate-rules` 재실행 PASS.
- [ ] sample build 통과.
- [ ] follow-up task (있으면) candidate 명시.
- [ ] Ready for review.

---

# Prerequisites

- ✅ 4 프로젝트 동거 (wms + ecommerce + GAP + fan-platform) 안정화 완료
- ✅ ready/ + review/ 큐 비어있음 (인터럽트 없음)
