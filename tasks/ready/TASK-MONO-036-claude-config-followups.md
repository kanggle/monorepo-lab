# Task ID

TASK-MONO-036

# Title

.claude/ cleanup — TASK-MONO-032 의 W1-W6 follow-up 일괄 정리

# Status

ready

# Owner

backend

# Task Tags

- chore
- claude-config

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

TASK-MONO-032 (.claude audit, PR #164) 의 6건 Warning (W1-W6) 을 일괄 정리.

---

# Scope

## In Scope

### W1-W4 — agent domains 카탈로그 정규화

`.claude/agents/common/{devops-engineer,frontend-engineer,data-engineer,ml-engineer}.md` 의 frontmatter 의 `domains` 필드 값이 `.claude/config/domains.md` 카탈로그 외 값. 정규화:

- 카탈로그 값으로 변경
- 또는 `[all]` (TASK-MONO-032 fix 의 backend-engineer 패턴)

dispatcher scoring 에 실질 영향 없으나 일관성 회복.

### W5 — spec-check.ps1 dead 패턴

`.claude/hooks/spec-check.ps1` 의 `specs/platform/` 패턴이 dead (실제 경로는 `platform/`). 패턴 수정 또는 hook 제거 검토.

### W6 (있다면) — 기타 .claude audit 잔여

PR #164 body 의 W6 항목 확인 후 정리.

## Out of Scope

- agent prompt 본문 튜닝 — 본 task 는 frontmatter / hook 수정만
- 새 agent / skill 추가

---

# Acceptance Criteria

- [ ] 4 agent (devops/frontend/data/ml) frontmatter `domains` 정규화.
- [ ] spec-check.ps1 dead 패턴 fix.
- [ ] sample agent dispatch 1건으로 frontmatter 변경 영향 없음 확인 (예: `Agent(subagent_type="devops-engineer")`).

---

# Related Specs

- `tasks/done/TASK-MONO-032-claude-config-audit.md`
- `.claude/config/domains.md` (카탈로그)
- `.claude/agents/common/*.md`
- `.claude/hooks/spec-check.ps1`

---

# Edge Cases

- agent 가 multi-domain 영향이라면 `[all]` 이 더 정확 (단 dispatcher 가 special-case 처리하는지 확인).
- spec-check.ps1 이 다른 dead 패턴 추가 보유 가능 — grep 으로 일괄 확인.

---

# Failure Scenarios

- frontmatter 변경 후 dispatch 실패 — sample dispatch 로 catch.
- hook 패턴 fix 후 의도치 않은 false positive — sample edit/write 로 검증.

---

# Test Requirements

- sample dispatch 1건.
- (선택) 본 PR 내에서 sample edit (예: dummy spec 파일 생성) → spec-check.ps1 hook 통과 확인.

---

# Definition of Done

- [ ] W1-W6 모두 fix.
- [ ] sample dispatch / hook 검증 PASS.
- [ ] Ready for review.

---

# Prerequisites

- ✅ TASK-MONO-032 완료
