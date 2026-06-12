# Task ID

TASK-MONO-242

# Title

CLAUDE.md § "Concurrent-session worktree isolation" 에 **서브에이전트 worktree 위임 경로-안전 규칙** 1 bullet 추가 — 서브에이전트(Agent 툴)가 상대경로를 세션 cwd(파킹된 메인 체크아웃)로 해석해 편집을 보호 대상 메인 체크아웃에 흘리는 hazard 의 예방·진단·복구를 규칙화. TASK-MONO-241 실측 사고의 규칙 승급.

# Status

review

# Owner

backend

# Task Tags

- monorepo
- docs
- worktree-isolation
- agent-dispatch

---

# Dependency Markers

- **선행 (done)**: `TASK-MONO-235` (worktree-isolation 규칙 CLAUDE.md 승급) · `TASK-MONO-241` (이번 규칙의 실측 사고 발생처 — ecommerce 콘솔 health 카드/드릴인 구현 중 backend-engineer subagent 가 상대경로를 메인 체크아웃으로 해석해 편집 오착지).
- **근거**: TASK-MONO-241 세션에서 서브에이전트 위임 시 상대경로가 세션 cwd(메인 체크아웃 `monorepo-lab`)로 해석되어 platform-console 편집 22 수정 + 4 신규가 보호 대상 메인 체크아웃에 착지. 서브에이전트가 worktree `git status` 무변화로 자가-적발 후 절대경로 재적용했으나, 메인의 stray 사본은 classifier 가 서브에이전트의 복구를 차단해 오케스트레이터가 surgical 복구(`git restore` + `Remove-Item -LiteralPath`, 데모 트리 무접촉). 동일 family hazard(메인 체크아웃 오염)의 새 경로 → 규칙 승급.
- **scope 결정 (user, 2026-06-13)**: 메모리 기록만으로는 부족, CLAUDE.md worktree-isolation 섹션에 한 줄 추가하기로 사용자 승인("추천대로 진행").
- **model**: 분석=Opus 4.8 / 구현 권장=Sonnet/Haiku (단일 doc bullet 추가). 본 세션은 Opus 로 직접 수행(맥락 보유).

# Goal

CLAUDE.md 의 worktree-isolation 규율에 **서브에이전트 위임 변종 hazard**를 명문화한다. 기존 섹션은 *세션 간 메인 체크아웃 공유*를 다루지만, *서브에이전트 위임 시 상대경로 cwd-해석으로 인한 메인 오염*은 누락돼 있었다. TASK-MONO-241 실측 사고를 근거로 예방(절대경로 명시) + 진단(`git status --porcelain -- <project>`) + 복구(`git restore` + named untracked 제거, 데모 트리 무접촉) + classifier 제약(서브에이전트는 메인 복구 불가 → 오케스트레이터 책임)을 1 bullet 로 추가한다.

# Scope

## In scope

- **`CLAUDE.md`** (repo-root, shared) — § "Concurrent-session worktree isolation" bullet 목록 끝(Worktree-add Windows pitfalls bullet 다음, "**Post-merge branch hygiene**" 앞)에 **"Dispatching a subagent into a worktree"** bullet 1개 추가. project-agnostic(서비스명·도메인 무관, `<project>`/`<path>` placeholder).

## Out of scope

- 자동 가드(hook) — 본 hazard 는 서브에이전트 cwd-해석 메커니즘이라 PreToolUse hook 으로 잡기 어렵고(편집 경로가 메인 체크아웃이라 정당한 편집과 구분 불가), discipline 규칙 + 위임-직후 status 확인으로 충분. hook 화는 별 task(필요시).
- 메모리 갱신 — 이미 `project_adr030_ecommerce_multivendor_saas` 본문 + 인덱스에 기록(이 task 와 독립).
- 다른 CLAUDE.md 섹션·platform·rules 변경 없음.

# Acceptance Criteria

- **AC-1**: CLAUDE.md worktree-isolation 섹션에 서브에이전트 위임 경로-안전 bullet 1개 추가 — 예방(절대경로) + 진단(`git status --porcelain -- <project>`) + 복구(`git restore` + named untracked 제거 + 데모 트리 무접촉) + classifier 제약(orchestrator 가 복구) 포함.
- **AC-2**: bullet 은 project-agnostic (HARDSTOP-03 통과 — 서비스명/API 경로/도메인 엔티티 0).
- **AC-3**: 기존 bullet·섹션 구조 불변(추가만, 삭제·재배치 0). "Post-merge branch hygiene" 헤더 위치 불변.
- **AC-4**: TASK-MONO-241 worked-incident 참조 포함(추적성).

# Related Specs / Code

- `CLAUDE.md` § "Cross-Project Changes" → "Concurrent-session worktree isolation" (line ~193-198, 본 추가 위치).
- 선례: TASK-MONO-235(worktree-isolation 규칙 승급), TASK-MONO-236(warn-shared-checkout-switch.ps1 hook), TASK-MONO-239(/start-task 커맨드) — worktree-격리 triad.
- 메모리: `env_concurrent_git_branch_switch_hazard`, `env_git_worktree_verify_windows`, `project_adr030_ecommerce_multivendor_saas`(MONO-241 사고 상세).

# Related Contracts

- 없음 (doc-only).

# Edge Cases / Failure Scenarios

- **project-agnostic 위반**: bullet 에 실 서비스명/경로가 들어가면 HARDSTOP-03 — `<project>`/`<path>` placeholder 유지.
- **hook 오인 기대**: 본 hazard 를 자동 가드로 막을 수 있다고 오해 금지 — 편집이 메인 체크아웃의 정당 경로로 보여 PreToolUse 구분 불가. discipline + 위임-직후 status 확인이 실효 방어(bullet 에 명시).
- **doc fast-lane**: CLAUDE.md-only 변경은 `dorny/paths-filter` code-changed 미해당 → fast-lane(`changes` pass, 코드 잡 skip). 정상.

# Notes

- ⚠️ JDT.LS OOM 권고 — 본 세션 facet (MONO-241) 종결 후 작은 doc 후속 1건. 격리 worktree `mlab-mono242`(origin/main `782e59754` 기준)에서만, 메인 체크아웃 무접촉.
- 머지 후 close-chore: review→done Status 만, closure narrative 는 커밋 메시지(HARDSTOP-05). 3-dim 검증.
