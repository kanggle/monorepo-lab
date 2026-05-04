# Task ID

TASK-MONO-038

# Title

docs/guides/monorepo-workflow.md 신설 — TEMPLATE.md 가 참조하는 to-be-authored 가이드

# Status

review

# Owner

backend

# Task Tags

- docs
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

TASK-MONO-033 (TEMPLATE.md audit, PR #166) 에서 TEMPLATE.md 가 여러 곳에서 `docs/guides/monorepo-workflow.md` 를 reference 하는데 파일이 없음을 catch. 본 task 는 그 가이드 문서 신설.

---

# Scope

## In Scope

### 1. `docs/guides/monorepo-workflow.md` 신설

다음 섹션 포함:

- **개요** — monorepo 의 일상 dev workflow (편집 / 커밋 / PR / 머지)
- **branch 패턴** — feature / chore / spec / fix branch 명명 규칙
- **task lifecycle** — INDEX.md 의 PR Separation Rule (spec / impl / chore PR 분리) 요약 + cross-ref
- **agent dispatch** — `.claude/agents/` 사용 패턴 (worktree isolation, model 선택, common vs domain agent)
- **sync-portfolio.sh** — standalone 추출 사용법 (`./scripts/sync-portfolio.sh <project>` + `--dry-run`)
- **CI 잡 영역** — Build & Test / Frontend / Integration / E2E 분리 + pre-existing infra fail 영역
- **hook 우회 규칙** — protect-main-branch.ps1 의 동작 + portfolio-sync 우회 (`/tmp/portfolio-sync/`)
- **자주 발생하는 conflict 패턴** — INDEX.md 의 review 헤더 누락 등 알려진 lifecycle chore PR 패턴 + 해결법
- **참조 문서 cross-ref** — CLAUDE.md / TEMPLATE.md / tasks/INDEX.md / 각 프로젝트의 PROJECT.md

### 2. TEMPLATE.md 의 reference 갱신

TEMPLATE.md 가 `docs/guides/monorepo-workflow.md` 를 reference 하는 모든 위치 (gap-integration / standalone freeze / GAP IdP 섹션 등) 가 정확한 anchor link 로 갱신 (`#section-name`).

## Out of Scope

- 다른 docs/guides/ 신설 — 본 task 는 monorepo-workflow.md 만
- CLAUDE.md / TEMPLATE.md narrative 큰 폭 재구성 — 본 task 는 monorepo-workflow.md 만

---

# Acceptance Criteria

- [ ] `docs/guides/monorepo-workflow.md` 신설.
- [ ] 위 9개 섹션 모두 포함.
- [ ] TEMPLATE.md 의 reference link 모두 정확한 anchor 로 갱신.
- [ ] 본 가이드의 narrative 가 CLAUDE.md / TEMPLATE.md 와 일관 (중복 시 cross-ref).

---

# Related Specs

- `TEMPLATE.md`
- `CLAUDE.md`
- `tasks/INDEX.md`
- `tasks/done/TASK-MONO-033-template-md-consistency-audit.md`

---

# Edge Cases

- CLAUDE.md 와 중복되는 영역은 본 가이드가 cross-ref (CLAUDE.md 가 master, 본 가이드는 dev workflow practical guide).
- "자주 발생하는 conflict 패턴" 은 본 시리즈 (TASK-MONO-029~034) 에서 발견된 INDEX.md review 헤더 누락 등 실제 사례 기반.

---

# Failure Scenarios

- 가이드가 narrative 만 큼 → maintenance 부담. 핵심 short reference + 다른 문서 cross-ref 위주로 작성.

---

# Test Requirements

- docs-only 변경 → sample build 영향 없음.
- (선택) lint 또는 markdown link checker 실행.

---

# Definition of Done

- [ ] monorepo-workflow.md 신설.
- [ ] TEMPLATE.md reference 갱신.
- [ ] Ready for review.

---

# Prerequisites

- ✅ TASK-MONO-033 완료
