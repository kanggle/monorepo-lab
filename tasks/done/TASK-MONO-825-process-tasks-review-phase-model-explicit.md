# Task ID

TASK-MONO-825

# Title

`/process-tasks` Phase 2(리뷰) 가 리뷰 에이전트에 `model=` 을 명시하라는 지시를 빠뜨렸다 — 정본 `/review-task` 와 어긋남

# Status

done

# Owner

monorepo

# Task Tags

- docs
- claude-commands

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Haiku (문서 한 줄)
> 기안 2026-11-03 UTC. 출처 = 같은 날 `/validate-rules` 전수 스캔(142 파일, Critical 0 · Warning 1). 소유자 요청 «진행». 사실은 origin/main `d9d9bdd6d` 에서 읽었다.

# Dependency Markers

- 없음. 선행·후속 티켓 없음.

# Goal

`.claude/commands/process-tasks.md` Phase 2 의 리뷰 에이전트 발주 단계가 정본(`/review-task` batch mode)과 같은 `model=` 명시 규칙을 싣는다.

# Scope

## In Scope

**AC-0 사실 (기안 중 실측, `d9d9bdd6d`):**

| # | 사실 |
|---|---|
| F1 | `process-tasks.md:59`(Phase 1) 은 *"Pass `model=` explicitly on every Agent call, per `/implement-task` Phase 5 (canonical) and `CLAUDE.md` § Recommending Tasks and Dispatching Agents"* 를 싣는다. |
| F2 | `process-tasks.md:73`(Phase 2 step 5) 은 `isolation: "worktree"` · `subagent_type: "code-reviewer"` 만 적고 `model=` 지시가 **없다**. 같은 파일 Rules 절(108–118행)에도 없다. |
| F3 | 정본 `review-task.md:146` 은 *"Pass `model=` explicitly on every Agent call — do not rely on session inheritance"* + 복잡도별 tier(opus/sonnet)를 요구한다. |
| F4 | `process-tasks.md:67` 이 스스로 `/review-task` (batch mode) 를 *"canonical source"* 라 선언하고 «정본을 먼저 고치고 여기 미러하라» 고 적는다 ⇒ 고칠 쪽은 `process-tasks.md`. |

- `process-tasks.md` Phase 2 step 5 아래에 F1 과 같은 형식의 한 항목 추가(정본 `review-task.md` + `CLAUDE.md` § 를 가리키고 tier 표를 재진술하지 않는다).

## Out of Scope

- `review-task.md` · `implement-task.md` · `CLAUDE.md` 수정(이미 옳다).
- 같은 스캔의 Info 3건(admin-web 묘비 문서 · 템플릿 예시 워크플로 경로 · domain README 예시 경로) — 결함 아님.

## 영향 범위

- 공유 경로: `.claude/commands/process-tasks.md` 한 파일.
- `projects/<name>/` 영향: **없음**.

# Acceptance Criteria

- **AC-1** `process-tasks.md` Phase 2 리뷰 발주 단계에 `model=` 명시 지시가 있고, 정본 `review-task.md` § Batch mode 와 `CLAUDE.md` § Recommending Tasks and Dispatching Agents 를 가리킨다.
- **AC-2** 변경은 그 파일 한 곳뿐이고 문구는 project-agnostic 이다(HARDSTOP-03).
- **AC-3** impl PR CI 에서 실패한 체크 0.

# Related Specs

- `.claude/commands/review-task.md` § Batch mode (정본)
- `CLAUDE.md` § Recommending Tasks and Dispatching Agents

# Related Contracts

없음.

# Edge Cases

- tier 표를 `process-tasks.md` 에 복사하면 사본이 하나 더 생긴다 — 정본을 가리키기만 한다(F1 의 형식).

# Failure Scenarios

- 정본 이름을 틀리게 적으면 링크가 끊긴다 → 리뷰에서 경로·절 이름을 대조한다.

# Implementation — 2026-11-03 UTC

- `process-tasks.md` Phase 2 step 5 아래 첫 하위 항목으로 `model=` 명시 지시 1줄 추가(`git diff --stat` = 1 file, 1 insertion). Phase 1(59행)과 같은 형식, tier 표는 복사하지 않음.

# Verification

- **AC-1** ✅ 추가 문구가 `/review-task` batch mode(`review-task.md:113` `## Batch Mode`) 와 `CLAUDE.md` § Recommending Tasks and Dispatching Agents(`CLAUDE.md:223`)를 가리킨다 — 두 절 모두 실재 확인.
- **AC-2** ✅ 구현 diff = `.claude/commands/process-tasks.md` 한 파일. 문구에 서비스명·프로젝트명 없음.
- **AC-3** ⏳ impl PR CI 결과로 닫는다(close chore 의 4-dim (c)).

---

## CORRECTION — 2026-11-03 UTC (close chore)

위 AC-3 의 ⏳ 는 이제 사실이 아니다. **AC-3 ✅** — impl PR #4622(squash `6f9357a2d`) 머지 전 체크: 실패 0 · SUCCESS 7(필수 4개 `changes` · `INDEX queue drift …` · `Task ID collision …` · `Walkthrough limitation ledger drift …` 전부 실제로 돌았다 + `Claude reference integrity` · `Lifecycle stage dirs` · `Owner-only demo operator`) · SKIPPED 61. 🔵 그 PR 은 머지 직전 #4620 과 `tasks/INDEX.md` 인접 행에서 충돌해 `origin/main` 을 병합해 풀었다(824 행은 main 문구 채택) — 위 체크는 그 병합 뒤 head `122ac5e05` 의 것이다. 구현 줄은 `origin/main` 의 `process-tasks.md` 에 1건 있다.
