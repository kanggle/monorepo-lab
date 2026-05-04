# Task ID

TASK-MONO-039

# Title

`rule-consistency-check.ps1` README false-positive fix + `agents/common/README.md` 신설

# Status

ready

# Owner

backend

# Task Tags

- chore
- claude-config
- hook

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

TASK-MONO-036 (PR #173) 에서 deferred 된 W6 항목 처리.

`.claude/agents/common/README.md` 가 부재 (반대로 `.claude/agents/domain/` 에는 README.md 존재 — 일관성 drift). README 신설 시 `.claude/hooks/rule-consistency-check.ps1` 의 line 24 정규식 `\.claude[\\/]agents[\\/]` 가 `README.md` 도 agent 파일로 오인해 frontmatter (`name`, `tools`, `Does NOT`) 강제 → README 작성 차단.

본 task 는:
1. hook 정규식에 README.md 제외 조건 추가 (false-positive fix)
2. `.claude/agents/common/README.md` 신설

**전제**: hook self-modification 사용자 명시 승인 받음 (2026-05-04 세션).

---

# Scope

## In Scope

### 1. `.claude/hooks/rule-consistency-check.ps1` line 24 fix

```diff
-    elseif ($filePath -match '\.claude[\\/]agents[\\/]') {
+    elseif ($filePath -match '\.claude[\\/]agents[\\/]' -and $filePath -notmatch 'README\.md$') {
         $isRuleFile = $true
         $fileType = "agent"
     }
```

같은 패턴이 다른 fileType (skill / command) 에도 적용 필요한지 확인 + 일관 fix.

### 2. `.claude/agents/common/README.md` 신설

`.claude/agents/domain/README.md` 패턴 따라 작성:
- common agent 의 역할 설명
- agent 카탈로그 (현재 13 agent)
- frontmatter 컨벤션
- dispatch 패턴

## Out of Scope

- agent 본문 변경 — 본 task 는 README + hook fix 만
- domain README 갱신 — 별도

---

# Acceptance Criteria

- [ ] `rule-consistency-check.ps1` 의 정규식 fix 적용 (agent + skill + command 일관).
- [ ] `.claude/agents/common/README.md` 신설.
- [ ] sample edit (예: dummy agent .md 파일 생성) 로 hook 가 정상 frontmatter 검사 (README 외 .md 는 차단) 확인.
- [ ] `.claude/agents/common/README.md` 작성 시 hook block 안 됨.

---

# Related Specs

- `tasks/done/TASK-MONO-036-claude-config-followups.md` (deferred 사유)
- `.claude/hooks/rule-consistency-check.ps1`
- `.claude/agents/common/` (README 부재)
- `.claude/agents/domain/README.md` (참고 패턴)

---

# Edge Cases

- skill / command 디렉토리에도 README.md 가 있을 수 있음 — hook 의 같은 false-positive 가능. 일관 fix.
- README.md 외 다른 non-agent 파일 (예: `.gitignore`, `.gitkeep`) 도 hook 에 잡히는지 확인.

---

# Failure Scenarios

- hook fix 후 정상 agent 파일이 검사 안 받음 → frontmatter 누락 슈도 PASS. 실제 sample edit 로 검증.

---

# Test Requirements

- sample edit 으로 hook 동작 확인 (README 통과 + agent 차단 시나리오).

---

# Definition of Done

- [ ] hook fix.
- [ ] README 신설.
- [ ] sample 검증 PASS.
- [ ] Ready for review.

---

# Prerequisites

- ✅ TASK-MONO-036 완료 (W1-W5)
- ✅ hook self-modification 사용자 명시 승인 (2026-05-04)
