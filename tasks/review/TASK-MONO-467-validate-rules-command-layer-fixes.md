# Task ID

TASK-MONO-467

# Title

`/validate-rules` 2026-07-22 scan — command-layer 정합성 4건 정리 (dead-path 1 Critical + drift 3 Warning)

# Status

review

# Owner

monorepo (root tasks/ — shared `.claude/commands/`)

# Task Tags

- onboarding

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Dependency Markers

- **origin**: `/validate-rules` full scan 2026-07-22 (read-only). Critical 1, Warning 3, Info 2. 본 task = Critical 1 + Warning 3 closure. Info 2건(命名 일관성 확인 / deprecated auth-service 가시성)은 비차단·조치 불요.
- **prerequisite for**: nothing (rule-library / tooling hygiene).
- **execution constraint**: 4 수정 모두 `.claude/commands/` — auto-mode classifier 의 `.claude/hooks|settings.json` block 대상 **아님** (`commands/` 는 실측 통과: MONO-409/424 [[env_classifier_claude_self_mod_block]]). agent 가 edit + commit + push 전부 수행 가능.
- **model**: 분석=Opus 4.8 / 구현=Opus 4.8 (single-session 직접) / 구현 권장=Sonnet (단순 doc-ref fix).

---

# Goal

`/validate-rules` 가 보고한 Critical 1 + Warning 3 을 해소하여 command 라이브러리를 다시 클린(Critical 0 / Warning 0)으로 만든다. 전부 동작 영향 0 의 문서·정합성 수정 (`.claude/commands/` 한정).

---

# Scope

## In Scope

1. **`.claude/commands/validate-rules.md` — dead-path `skills/INDEX.md` → `.claude/skills/INDEX.md` (3곳: L67, L76, L79). [Critical]**
   - 문제: repo 루트에 `skills/` 디렉터리 없음 — 실제 파일은 `.claude/skills/INDEX.md`. 다른 모든 command/agent/skill/hook 은 full-prefix 형태 사용. validate-rules 자기 자신 안의 dead-path (아이러니).
   - 수정: 3 인라인 코드 참조를 `.claude/skills/INDEX.md` 로 정규화. 대상 파일은 존재하므로 prefix drift.

2. **`.claude/commands/refactor-code.md` — Phase 2 Prioritization 을 `platform/refactoring-policy.md` § Prioritization 과 정합. [Warning]**
   - 문제: platform 은 `complexity` 를 #5(long-method 와 tie, naming 위)에 두나, command 는 `complexity` 를 naming 과 tie 로 최하위(#4)에 둠. command 가 platform 을 authoritative 로 명시적 defer 하므로 실 drift.
   - 수정: platform 순서(layer/pattern → dead-code → duplication → long-method+complexity → naming)로 command Phase 2 재정렬 + "mirrors policy" 주석.

3. **`.claude/commands/process-tasks.md` — contract-change dispatch 의 `+` → `or` 정정 (L59). [Warning]**
   - 문제: `"api-designer"` + `"event-architect"` 는 "둘 다 동시 dispatch" 로 오독됨. canonical `/implement-task` Phase 5 는 contract type 에 따라 api-designer **or** event-architect 를 contract step 에 먼저, 그다음 구현 엔지니어.
   - 수정: type-dependent "or" 의미 + sequence 복원.

4. **`.claude/commands/start-task.md` — CLAUDE.md anchor 라벨 정정 (L8). [Warning]**
   - 문제: `"Concurrent-session worktree isolation"` 인용하나 실제 CLAUDE.md 불릿 라벨은 `**Concurrent-session isolation**` ("worktree" 없음).
   - 수정: 인용 라벨을 verbatim 일치.

## Out of Scope

- Info 2건 (command 命名 kebab-case 일관성 확인 / `auth-service-deprecated` 가시성) — 비차단, 조치 불요.
- 다른 `.claude/commands/` 파일, `platform/`, `.claude/skills/`, agent 정의 — 무변경.

---

# Acceptance Criteria

- [ ] **AC-1 [Critical]**: `validate-rules.md` 에 bare `` `skills/INDEX.md` `` 0건 — 3 참조 전부 `.claude/skills/INDEX.md`.
- [ ] **AC-2 [Warning]**: `refactor-code.md` Phase 2 순서가 `refactoring-policy.md` § Prioritization 과 rank-정합 (complexity 가 long-method 와 함께, naming 위).
- [ ] **AC-3 [Warning]**: `process-tasks.md` L59 이 api-designer **or** event-architect (type-dependent, sequential) 로 표기 — `+` 제거.
- [ ] **AC-4 [Warning]**: `start-task.md` 의 CLAUDE.md 인용 라벨이 `"Concurrent-session isolation"` verbatim.
- [ ] **AC-5 (scope-lock)**: `git diff origin/main` 이 위 4 command 파일 + 본 task lifecycle 파일만 건드림.
- [ ] **AC-6**: 재-scan 시 해당 1 Critical + 3 Warning 재현 안 됨 (Critical 0 / Warning 0).

---

# Related Specs

- `.claude/commands/validate-rules.md` (#1) / `.claude/skills/INDEX.md` (참조 대상).
- `.claude/commands/refactor-code.md` (#2) / `platform/refactoring-policy.md` § Prioritization (정전 기준).
- `.claude/commands/process-tasks.md` (#3) / `.claude/commands/implement-task.md` Phase 5 (canonical 기준).
- `.claude/commands/start-task.md` (#4) / `CLAUDE.md` § Cross-Project Changes (anchor 원문).

# Related Contracts

- None. Command-library / tooling only.

---

# Edge Cases

- **replace_all 이 이미-full-form 참조를 건드리나?** — 아니오. 검색 토큰은 backtick+`skills`(즉 `` `skills/INDEX.md` ``); full form `` `.claude/skills/INDEX.md` `` 는 `skills` 앞이 `/` 이므로 매치 안 됨.
- **refactor-code 카테고리 라벨 vs 우선순위** — 카테고리 표(L42-53)는 무변경; Phase 2 순서 문장만 수정.

---

# Failure Scenarios

- **다른 파일이 수정됨** → AC-5 fail; 본 task 는 4 command 파일 한정.
- **command 동작 변경** → 발생 안 함; command 는 agent-읽기용 문서, 런타임 코드 아님.

---

# Verification

- 2026-07-22, `task/mono-467-validate-rules-fixes` 브랜치 (off `main` @ 65f2d1a74, MONO-464 머지 후).
- 4 수정 적용 완료. `git diff origin/main --stat` = 4 command 파일 + 본 task 파일.
- CI `code-changed` fast-lane 기준 GREEN 예상 (`.claude/**/*.md` = non-code path-filter).
- 3-dim merge 검증은 close chore 시 수행.
- 분석=Opus 4.8 / 구현=Opus 4.8.
