# Task ID

TASK-MONO-032

# Title

.claude/ audit — skills / agents / commands / hooks stale + 중복 + 미사용 검증

# Status

ready

# Owner

backend

# Task Tags

- audit
- claude-config
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

공통규칙·스펙 정리 시리즈 5개 task 중 네 번째. `.claude/` (skills / agents / commands / hooks / config) 의 stale / 중복 / 미사용 항목 audit + 정리.

TASK-MONO-029 가 `.claude/config/` (domains / traits / activation-rules) 의 cross-file 정합성을 검증했다면, 본 task 는 그 카탈로그가 가리키는 실제 skill / agent / command / hook 본문의 stale / 중복 / 미사용 검증.

---

# Scope

## In Scope

### 1. `.claude/skills/` audit

- `.claude/skills/INDEX.md` ↔ 실제 SKILL.md 파일 매칭 (PR #156 에서 1건 fix, 다른 orphan 또는 dangling 검증)
- 각 SKILL.md 의 `description` 이 stale 한지 (참조하는 spec / API / 패턴이 현재 monorepo 에 존재)
- skill bundle (`activation-rules.md` 의 trait/domain → skill 매핑) 과 실제 SKILL.md 의 frontmatter (`triggers` / `domains` / `traits`) 일관성
- 미사용 skill 후보 (어떤 trait/domain bundle 에도 매핑 안 된 skill)

### 2. `.claude/agents/` audit

- `.claude/agents/{common,domain}/README.md` ↔ 실제 agent .md 파일 매칭
- agent frontmatter (`service_types` / `domains` / `traits`) 정확성
- common agent 의 project-specific 예시 leak (PR #58 직후 nested .claude/ 정리에서 본 패턴 — common agent frontmatter 에 `domains: [web-store, admin-dashboard]` 같은 앱 이름 박혀있는지)
- 미사용 agent 후보

### 3. `.claude/commands/` audit

- 각 slash command 가 실제로 사용되는지 (사용자 invocation history / git log 의 command 실행 흔적)
- command 본문이 stale 한지 (참조 spec / 룰 / 파일 경로 존재)
- 중복 (예: `process-tasks` ↔ `implement-task` 가 거의 동일 흐름)

### 4. `.claude/hooks/` audit

- hook script 가 stale 한지 (참조 환경변수 / 외부 도구 / cwd assumption)
- 사용자가 force-push 등 명시적 우회 케이스 추가 (TASK-MONO-011) 후 추가 우회 필요 케이스 발견 여부

### 5. `.claude/config/` 추가 검증

PR #156 에서 4-way 동기화 PASS 확인. 본 task 는 추가:
- `activation-rules.md` 의 trait → skill 매핑이 실제 SKILL.md 의 triggers 와 양방향 일관

## Out of Scope

- skill 본문의 *내용 품질* (어떤 절차가 더 좋은지) — 별도 refactor task
- agent prompt 튜닝 — 본 task 는 stale / 미사용 / 중복만
- `.claude/config/{domains,traits,activation-rules}.md` 4-way 동기화 — TASK-MONO-029 에서 처리됨
- new skill / agent 추가 — 본 task 는 정리만

---

# Acceptance Criteria

- [ ] skills / agents / commands / hooks 4개 카테고리별 검증 결과 PR body 첨부.
- [ ] orphan / dangling reference 카탈로그.
- [ ] 미사용 후보 카탈로그.
- [ ] 중복 후보 카탈로그.
- [ ] project-specific leak 검출 결과.
- [ ] Critical (Hard Stop / 활성화 깨짐) 본 PR fix.
- [ ] Warning / Suggestion 분류.

---

# Related Specs

- `.claude/skills/INDEX.md`
- `.claude/agents/{common,domain}/README.md`
- `.claude/commands/`
- `.claude/hooks/`
- `.claude/config/activation-rules.md`
- `tasks/done/TASK-MONO-029-rules-validation-audit.md` (선행 — config 정합성)

---

# Related Skills

- `validate-rules` (광범위 검사 — skills/agents/commands 도 다룸)
- `audit-memory` (audit 패턴 reference)

---

# Target Component

- `.claude/skills/`
- `.claude/agents/`
- `.claude/commands/`
- `.claude/hooks/`

---

# Architecture

audit + chore. agent / command / skill 본문 변경 가능 (Critical fix 한정).

---

# Implementation Notes

- 미사용 후보 검증은 어렵다 (사용자가 인지 / 비인지 invoke 가능). 보수적 접근:
  - **명백한 미사용**: activation-rules 매핑 0개 + 어떤 agent 도 invoke 안 하는 skill
  - **잠재 미사용**: 매핑은 있지만 최근 6개월 git log 에 invoke 흔적 0
  - 분류 후 사용자 확인 권장 (삭제는 본 PR scope 외)
- common agent 의 project-specific leak 은 nested .claude/ 정리 (PR #58 후속) 패턴 참고.

---

# Edge Cases

- **slash command 가 새로 추가되어 git log 에 흔적 부족**: 본 task 머지 직전 추가된 command 는 미사용 카운트에서 제외.
- **사용자 메모리 / preference 와 묶인 hook**: feedback_proceed_without_confirmation 메모리가 hook 동작 가정. 메모리 ↔ hook 일관성 검증 추가.

---

# Failure Scenarios

- **삭제했는데 user-invocable**: 사용자가 명시적으로 invoke 가능한 skill / command 를 잘못 미사용으로 분류. 본 task 는 카탈로그까지만, 삭제는 별도 fix task + 사용자 확인 후.
- **agent dispatch 깨짐**: agent frontmatter 변경 시 dispatch 매칭 실패 가능. 변경 후 sample dispatch 로 검증.

---

# Test Requirements

- audit 자체가 검증.
- agent frontmatter 변경 시 sample dispatch (e.g. `Agent(subagent_type="backend-engineer")` 가 정상 매칭되는지).

---

# Definition of Done

- [ ] 4개 카테고리 검증 결과 PR body.
- [ ] Critical fix.
- [ ] Ready for review.

---

# Prerequisites

- ✅ TASK-MONO-029 완료
- 권장: TASK-MONO-030 / -031 후 진행 (spec / libs 정리가 skill / agent 의 valid 참조에 영향)
