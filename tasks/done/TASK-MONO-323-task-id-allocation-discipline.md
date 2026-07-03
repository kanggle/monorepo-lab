# TASK-MONO-323 — task ID 발급 동시성 규율 명문화 (tasks/INDEX.md)

**Status:** done
**Area:** monorepo root — `tasks/INDEX.md` (shared lifecycle doc)
**Type:** process-doc (규율 명문화; 코드/동작 무변경)
**Implemented:** branch `task/mono-323-task-id-allocation-discipline` → **#2183 merged** (squash `5f45c58f3`). verification = doc(재독 + 링크 정합); 코드 없음.
**Analysis model:** Opus 4.8 · **Impl model:** Opus.

## Goal

병행 세션이 순차 task ID(`MONO`/`PC-FE`/`BE` …)를 각자 "최대+1"로 골라 **반복 충돌**하는 문제(관측: 이번
세션에서 PC-FE-176 중복, 앞서 170→172 재조정)를 줄이도록, **동시성-안전 발급 규율**을 `tasks/INDEX.md`에
명문화한다. 근본 원인 = 락 없는 낙관적 순차 발급 + 미push 구간 비가시성(time-of-check ≠ time-of-use) +
모두가 동일 "최대+1" 휴리스틱.

## Scope (implemented)

- `tasks/INDEX.md` — "Task Types" 직후 신규 섹션 **"Task ID Allocation (concurrency-safe)"** 추가:
  1. 클레임 전 검증(`done/ready/review` + `worktree list` + `ls-remote` + `log --all`),
  2. **선점-즉시-push**(스텁 task 파일 → 작업 전 브랜치 push로 비가시 창 최소화),
  3. hot frontier 버퍼(`max+N` / 세션별 대역),
  4. **먼저 머지된 쪽이 번호 보유 → 나머지 재번호**(rename chore, 작업 손실 아님; MONO-170→172 선례).
  + 미래 hard fix(reservation registry / 비순차 ID) 유보 각주.

## Acceptance Criteria

- [x] **AC-1** `tasks/INDEX.md`에 발급 규율 섹션이 4단계 + 근본원인 + 유보각주로 존재.
- [x] **AC-2** 모든 네임스페이스(MONO + 프로젝트 PC-FE/BE …)에 적용됨을 명시.
- [x] **AC-3** 기존 "Move Rules"/"Task Types" 서술과 모순 없음(순차 컨벤션 유지, 충돌완화만 추가).
- [x] **AC-4** 코드/동작 무변경 — docs-only(fast-lane `changes` 통과 예상).

## Related Specs / Contracts

- `tasks/INDEX.md` §Task Types(순차·MONO namespace) / §Move Rules(lifecycle) — 본 섹션이 그 사이에 삽입.
- 운영 메모리 `env_concurrent_session_task_id_collision`(에이전트 측 대응)와 상보 — 이 task는 repo-doc 측.

## Edge Cases

- 순차 가독성 유지: 규율은 "버퍼/재번호"까지만, 비순차 ID 전환은 유보(각주).
- 프로젝트 INDEX는 미변경 — root INDEX가 canonical, 프로젝트별로 재기술 안 함(DRY).

## Failure Scenarios

- 규율만으로 충돌 0 보장 못 함(낙관적 스킴) → 문서가 "possible but not fatal + first-merged-wins 재번호"로 명시해 오해 방지.
