# TASK-MONO-267 — CLAUDE.md branch-hygiene: classifier "attempt-first" 정정

- **Status**: ready
- **Level**: monorepo (shared — `CLAUDE.md`)
- **Analysis model**: Opus 4.8 / **Implementation model**: Opus (doc 1-line, 판단 동반)

## Goal

`CLAUDE.md` § Cross-Project Changes → "Post-merge branch hygiene" 의 한 불릿(현 line 205)이
**"auto-mode classifier blocks mass `git push origin --delete` … must be run in the user's own shell"**
로 단정하나, 2026-06-15 agent 가 **머지 확인된 6개 배치 `git push origin --delete` 를 직접 실행해 통과**시켰다
(fin-be-033/pc-fe-092/identity 잔재 정리). classifier 는 하드 규칙이 아니라 **위험도×복구가능성×직전 승인 맥락**
LLM 판정이므로, "사용자 셸만 가능" 단정은 over-위임이다. 사용자 방침(2026-06-15) = **agent 가 시도부터 하고
실제 차단 시에만 셸로 넘긴다**. 이 불릿을 그 방침으로 정정한다.

## Scope

**In scope** (shared, 1 파일):
- `CLAUDE.md` line 205 한 불릿만 정정 — "blocks … must be run in user's own shell" → "context-sensitive
  higher safety layer, **may** gate but not a hard rule (2026-06-15 머지-stale 배치 통과); **attempt first**
  when refs are confirmed merge-stale; 실제 차단 시에만 셸 위임". "On an actual block: STOP … do not reformulate
  to bypass" 문장은 **보존**(시도-우선과 충돌 없음).

**Out of scope**: 같은 섹션의 다른 불릿(203/204/206/207 — 전부 현행 유효), 코드·타 문서, classifier/hook 구현.

## Acceptance Criteria

- **AC-1** — line 205 가 "agent attempt-first, 실제 차단 시에만 사용자 셸" 의미로 읽힌다. "must be run in the
  user's own shell" 단정 제거.
- **AC-2** — "do not reformulate to bypass on an actual block" 가드 보존(우회 금지 규율 유지).
- **AC-3** — 머지-stale 한정 삭제 + `gh pr create`/`merge --squash` pass + local `git branch -D` 가능 사실 유지.
- **AC-4** — 다른 불릿·섹션 무변경. CLAUDE.md catalog 성격 유지(상세는 project memory `project_branch_hygiene_policy`).

## Related Specs

- `CLAUDE.md` § Cross-Project Changes → Post-merge branch hygiene (line 201~207)
- project memory `project_branch_hygiene_policy` (본 정정의 detail SoT — 이미 attempt-first 로 갱신됨)

## Related Contracts

- 없음 (doc-only).

## Edge Cases

- 실제 차단 케이스: STOP→정확 명령 사용자 위임(우회 변형 금지) — 보존.

## Failure Scenarios

- 없음 (doc-only).
