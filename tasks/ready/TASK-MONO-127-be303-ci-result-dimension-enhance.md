# Task ID

TASK-MONO-127

# Title

CLAUDE.md § Task Rules — BE-303 (객관 머지 검증) 룰에 *CI 결과 차원* 추가 (`gh pr checks` 머지 직전 GREEN 확인 의무화) — TASK-PC-BE-002 회귀 회복 saga 2026-05-20 의 root-level prevention

# Status

ready

# Owner

monorepo / docs

# Task Tags

- monorepo
- docs
- claude-md
- rule-enhance
- be303-enhancement

---

# Required Sections

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Dependency Markers

- **enhances**: TASK-MONO-122 (BE-303/BE-299 promote, done) 의 BE-303 bullet 만. BE-299 bullet 은 byte-unchanged.
- **driven by**: TASK-PC-BE-002 saga (PR #676/#677/#678, 2026-05-20) — TASK-PC-FE-011 PR #672 가 머지 직전 마지막 PR-time check `Integration (platform-console console-bff, ...): fail 55s` 상태로 squash-merge → main 4 회 연속 (`b378b201` / `71974cf6` 등) console-bff IT RED → 1순위 TASK-PC-FE-012 (PR #675) 의 CI 가 회귀 mirror 로 FAIL → TASK-PC-BE-002 0순위 promotion 으로 회복.
- **detail in memory**: [`feedback_spring_boot_diagnostic_patterns.md`](C:\Users\kangdow\.claude\projects\c--Users-kangdow-dev-project-ai-project-monorepo-lab\memory\feedback_spring_boot_diagnostic_patterns.md) § 10 (catalog=CLAUDE.md / detail=memory pattern, MONO-122 와 동형).
- **no agent-memory mutation**: 메모리 § 10 은 detail 보존 (audit-memory promote 선례 동형). 메모리 § 10 의 self-annotation 추가만 별 작업 가능 (본 task 의 out-of-scope; audit-memory cycle 에서 추후 동기).

# Goal

현 `CLAUDE.md § Task Rules` 의 BE-303 룰 (MONO-122 ACCEPTED, 2-bullet promote 후 적용):

```
- **Objective merge verification before any close chore** — a "merged it" statement is not proof. Run `gh pr view <n> --json state,mergedAt,mergeCommit` **and** confirm `git log origin/main` tip matches the squash commit before moving `review/ → done/`. If not actually merged: STOP (do not close — green-wash prohibited).
```

은 **머지 상태 차원** (state=MERGED + mergeCommit + main tip 일치) 만 검증. 그러나 PR #672 같이 **PR-time CI 가 RED 상태로 squash-merge** 된 케이스는 머지 상태 차원에서는 정상 (state=MERGED, mergeCommit 일치) 이지만 main 에 회귀를 남김 — 4 회 연속 console-bff IT RED + 1순위 fix-task hold + 0순위 promotion 으로 회복.

본 task 는 BE-303 룰에 **CI 결과 차원** 을 추가한다. 머지 직전 `gh pr checks <n>` snapshot 의 모든 check 가 SUCCESS (skipping 포함) 임을 확인 — RED job 가 있으면 STOP. 이는 MONO-122 의 BE-303 promote 와 동형 enhancement: catalog 룰 1-bullet 확장, agent-memory § 10 은 detail 보존.

# Scope

## In Scope

### 1. `CLAUDE.md § Task Rules` — BE-303 bullet *in-place enhance*

기존 bullet 의 일부 텍스트를 in-place 강화 (1-bullet only, BE-299 bullet + 다른 5 bullet + Lifecycle 줄 byte-unchanged):

**Before** (line 107):
```
- **Objective merge verification before any close chore** — a "merged it" statement is not proof. Run `gh pr view <n> --json state,mergedAt,mergeCommit` **and** confirm `git log origin/main` tip matches the squash commit before moving `review/ → done/`. If not actually merged: STOP (do not close — green-wash prohibited).
```

**After**:
```
- **Objective merge verification before any close chore** — a "merged it" statement is not proof. Verify **three dimensions** before moving `review/ → done/`: (a) `gh pr view <n> --json state,mergedAt,mergeCommit,statusCheckRollup` returns `state=MERGED`; (b) `git log origin/main` tip matches the squash commit; (c) the impl PR's pre-merge `gh pr checks <n>` snapshot had **0 failing required checks** (CI-RED-at-merge time creates a main regression — `statusCheckRollup` of the merged PR is the authoritative record). If any of the three fails: STOP. CI-RED-at-merge requires a separate fix-task that restores main GREEN before close chore. (TASK-PC-BE-002 회귀 회복 saga 2026-05-20 — PR #672 squash-merged with `console-bff IT: fail 55s` → main 4 회 연속 RED → 0순위 promotion 으로 회복.)
```

### 2. No memory mutation in this task

`feedback_spring_boot_diagnostic_patterns.md` § 10 (이미 작성됨, BE-303 CI dimension narrative 보유) 은 catalog promote 후 self-annotation `**[Promoted — TASK-MONO-127 2026-05-20]**` 추가 가능하나 **본 task scope 가 아님** (audit-memory cycle 의 자체 작업, MONO-122 promote 와 동형 — agent-memory 외부 paths 에 있어 task 외).

## Out of Scope

- BE-299 bullet 변경 금지 — byte-unchanged.
- 다른 `CLAUDE.md` 섹션 / bullet 변경 금지.
- 룰 의미 신규 — 본 enhancement 는 reality-alignment (PR #672 회귀 saga 가 catalog 보강 필요성 증명). competing convention 부재 → 신규 ADR 불요.
- `feedback_spring_boot_diagnostic_patterns.md` § 10 변경 — 본 task scope 외 (메모리는 agent home `~/.claude/projects/.../memory/` 아래라 repo paths 외, audit-memory cycle 자체 작업).
- 자동화 hook 도입 (`.claude/hooks/` 에 pre-close-chore validation) — 별 task. 본 task 는 catalog 룰만.
- 다른 audit-memory promote 후보 (P3/P4/P5/P6/P7 from MONO-122) — 본 task scope 외.
- 코드 / projects / 빌드 / CI / ADR 변경 0 (doc-only).

# Acceptance Criteria

1. `CLAUDE.md` line 107 의 BE-303 bullet 가 새 3-dimension 텍스트로 교체됨.
2. `CLAUDE.md` 의 다른 모든 bullet / 섹션 / 줄 byte-unchanged (BE-299 line 108 byte-unchanged 포함).
3. `tasks/INDEX.md` 의 ready entry 추가 (spec PR 단계) / 그리고 done entry (close chore 단계).
4. `projects/<name>/` 변경 0.
5. `libs/` / `platform/` / `rules/` / `.claude/` 변경 0 (doc-only enhance, hook 도입 없음).
6. self-CI ALL GREEN (path-filter docs-only → 모든 code job skipping, `changes` PASS).
7. 향후 모든 close chore PR description 의 BE-303 ledger 가 3 차원 명시 — (a)+(b)+(c) 줄 모두 기록.

# Related Specs

- `tasks/INDEX.md` (root) § Move Rules — close chore 의 lifecycle 정의.
- `CLAUDE.md § Task Rules` BE-303/BE-299 (MONO-122 promote).

# Related Contracts

- 없음 (doc-only catalog enhancement).

# Edge Cases

- **`statusCheckRollup` 가 비어있는 PR** (e.g. CI 가 한 번도 실행되지 않은 spec PR with path-filter skip): `statusCheckRollup` array 가 비어있으면 "0 failing" 단언 충족 — 정상 머지 가능. 단 `changes` job 자체가 missing 이면 별 회귀 가능성, GREEN 정의 = "RED 0개" (PENDING / IN_PROGRESS / SUCCESS / SKIPPED 모두 OK).
- **squash-merge `--admin` override** 으로 status-check 우회된 케이스: GitHub UI 에서 강제 머지 시 BE-303 (c) 가 fail signal — close chore 진입 시 발견되면 fix-task 필요. 그러나 머지 자체는 막을 수 없음 (repo policy 차원).
- **PR-time check 가 ON-PROGRESS 상태로 머지된 케이스**: `statusCheckRollup` 가 PENDING 포함 — `gh pr view --json statusCheckRollup` 의 `state` 가 SUCCESS / FAILURE 둘 다 아닐 수 있음. RED 0 정의는 `state=FAILURE` 또는 `conclusion=failure` 인 check 0 개.

# Failure Scenarios

| 조건 | 본 PR 의 반응 |
|---|---|
| 이미 main 에 머지된 RED 회귀 PR 가 본 bullet 적용 후 발견 | 별 fix-task. 본 PR 의 retroactive 영향 0 (룰이 catalog 화된 이후의 close chore 에만 적용). |
| 룰 텍스트가 모호 | 본 PR review 단계에서 추가 명확화. 단 핵심 메시지 ((a)+(b)+(c) 3 dimension + STOP + fix-task path) 는 in-text. |
| close chore PR 자체의 CI 가 (c) 위반 | 본 PR 의 close chore docs-only path-filter skip 라 mass code job 부재 (changes PASS) → 자체 위반 가능성 0. |
| MONO-122 bullet 의 byte-unchanged 가 의도외 변경 | dispatcher 독립 재검증 — `git diff origin/main -- CLAUDE.md` 라인 별 inspect, BE-299 bullet line 108 + Lifecycle 줄 byte-unchanged 확인. |

---

# Implementation Notes (impl PR 단계 reference)

`CLAUDE.md` line 107 의 단일 bullet in-place 교체:

```diff
- **Objective merge verification before any close chore** — a "merged it" statement is not proof. Run `gh pr view <n> --json state,mergedAt,mergeCommit` **and** confirm `git log origin/main` tip matches the squash commit before moving `review/ → done/`. If not actually merged: STOP (do not close — green-wash prohibited).
+ **Objective merge verification before any close chore** — a "merged it" statement is not proof. Verify **three dimensions** before moving `review/ → done/`: (a) `gh pr view <n> --json state,mergedAt,mergeCommit,statusCheckRollup` returns `state=MERGED`; (b) `git log origin/main` tip matches the squash commit; (c) the impl PR's pre-merge `gh pr checks <n>` snapshot had **0 failing required checks** (CI-RED-at-merge time creates a main regression — `statusCheckRollup` of the merged PR is the authoritative record). If any of the three fails: STOP. CI-RED-at-merge requires a separate fix-task that restores main GREEN before close chore. (TASK-PC-BE-002 회귀 회복 saga 2026-05-20 — PR #672 squash-merged with `console-bff IT: fail 55s` → main 4 회 연속 RED → 0순위 promotion 으로 회복.)
```

검증:

```bash
git diff origin/main -- CLAUDE.md | head -10  # 단일 bullet 만 차이
git diff origin/main -- . | wc -l             # 본 PR scope = CLAUDE.md + tasks/INDEX.md 만
```

---

# Approval

- 분석 = Opus 4.7
- 구현 권장 = Opus 4.7 (룰 텍스트 정확성 + reality-alignment 의도 보존)
- 리뷰 = Opus 4.7 (dispatcher 독립 재검증 + acceptance criteria 7/7 단언)
