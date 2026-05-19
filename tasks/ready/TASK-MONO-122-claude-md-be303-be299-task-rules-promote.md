# Task ID

TASK-MONO-122

# Title

CLAUDE.md § Task Rules 에 BE-303(객관 머지 검증) + BE-299(git mv review→done re-stage) 운영 규율 승격 — /audit-memory 2026-05-19 promote

# Status

ready

# Owner

monorepo / docs

# Task Tags

- monorepo
- docs
- claude-md
- rule-promote
- audit-memory-followup

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

# Goal

`/audit-memory` 2026-05-19 감사 Phase 5 에서 사용자가 **P1+P2 승격을 명시 동의**했다. 두 운영 규율이 현재 agent-memory(`project_refactor_sweep_status.md` L197 "운영 메타")에만 존재하나 **레포 전체·모든 AI 세션·개발자에 동일 적용되는 close-chore governance** 이므로 catalog 인 프로젝트 `CLAUDE.md § Task Rules` 로 승격한다:

- **BE-303 — 객관 머지 검증**: close chore(review→done) 전, 사용자의 "머지했어" 진술이 아니라 `gh pr view <n> --json state,mergedAt,mergeCommit` + `git log origin/main` tip-match 로 **객관 검증**. 미머지면 STOP (green-wash 금지). 본 세션에서만 #615(premature OPEN 1회 포착)·#620~#629 매 PR 에 실증된 핵심 규율.
- **BE-299 — `git mv review→done` re-stage 검증**: `git mv` 가 index 에 *review-Status* blob 을 stage → Status `review→done` Edit 후 `git add <done-path>` 재스테이지 + `git show :<done-path>` 로 staged blob=`done` 검증 필수. 누락 시 `Status: review` 파일이 `done/` 에 landing.

**승격 = CLAUDE.md 에 catalog 2-bullet 추가** (메모리에서 규칙 제거 아님). 메모리는 worked-example·재발-가드 detail 로 **유지**, `project_refactor_sweep_status.md` 가 이미 `**[Promoted — audit-memory 2026-05-19]**` self-annotation 보유 (catalog=CLAUDE.md / detail=memory, 중복 아님). 선례 동형: branch_hygiene → CLAUDE.md § Cross-Project Changes "Post-merge branch hygiene", ci_path_filter_074 → CLAUDE.md "CI path-filter constraint", be_276 branch-naming → CLAUDE.md "Branch name constraint" (모두 audit-memory promote, 메모리는 detail 보존).

**P3(push &&-chain 금지)/P4(PS Set-Content BOM)/P5(WSL 테스트 환경)/P6(R4j trap)/P7(@JdbcTypeCode trap)** = audit 에서 **미선택** → 메모리 유지, 본 task scope 아님.

# Scope

## In Scope

### 1. `CLAUDE.md § Task Rules` — catalog 2-bullet append

`# Task Rules` 리스트의 `- Tasks must contain all required sections: ...` bullet **직후**, `Lifecycle and review rules:` 줄 **앞**에 정확히 아래 2 bullet 삽입 (기존 5 bullet byte-unchanged, append-only):

```
- **Objective merge verification before any close chore** — a "merged it" statement is not proof. Run `gh pr view <n> --json state,mergedAt,mergeCommit` **and** confirm `git log origin/main` tip matches the squash commit before moving `review/ → done/`. If not actually merged: STOP (do not close — green-wash prohibited).
- **`git mv review/ → done/` re-stage check** — `git mv` stages the *review*-state blob; after editing the task's Status `review → done` you MUST `git add <done-path>` again and verify with `git show :<done-path>` that the staged blob reads `done`. (Skipping this lands a `Status: review` file under `done/`.)
```

## Out of Scope

- CLAUDE.md 의 그 외 어떤 섹션·bullet 변경 금지 (Task Rules 5 기존 bullet + Lifecycle 줄 byte-unchanged, append-only 2 bullet 만).
- 규칙 의미 신규/변경 금지 — 메모리에 이미 존재·실증된 규율의 catalog 표면화일 뿐 (reality-alignment, competing convention 무 → 신규 ADR 불요; audit-memory promote 선례 동형).
- agent-memory 파일 변경 금지 — `project_refactor_sweep_status.md` self-annotation 은 audit-memory 2026-05-19 에 이미 적용됨(repo 외부, 본 task scope 아님). 메모리에서 BE-303/BE-299 규칙 **제거 안 함** (detail 보존).
- P3/P4/P5/P6/P7 (audit 미선택 후보) 일체.
- 코드/projects/빌드/CI/다른 ADR 변경 0 (doc-only).

# Acceptance Criteria

1. `CLAUDE.md § Task Rules` 에 위 2 bullet 정확 삽입 — 위치 = "Tasks must contain all required sections" bullet 직후 / "Lifecycle and review rules:" 앞.
2. 기존 Task Rules 5 bullet + "Lifecycle and review rules:" 줄 **byte-unchanged** (impl PR `git diff` = pure add, context 외 `-` 0).
3. catalog 간결성 유지 (terse, full detail=memory pointer 정신 — 2 bullet 이 장황하지 않게).
4. git diff scope = 정확히 `CLAUDE.md` 1 파일 (+ lifecycle: spec PR=task+INDEX, impl PR=task move). 코드/projects/빌드/CI 0.
5. impl PR description 에 "memory 에서 규칙 미제거(detail 보존), 신규-ADR-불요(reality-alignment·promote 선례 동형)" 명시.
6. Lifecycle: spec PR(본 파일+루트 INDEX ready) ↔ impl PR(task move + CLAUDE.md 2-bullet) 분리. close chore(review→done)는 impl PR 머지 후 (BE-303 객관검증·BE-299 staged-blob — 본 규율 자기적용).

# Related Specs

- 프로젝트 [`CLAUDE.md`](../../CLAUDE.md) § Task Rules (승격 대상)
- 메모리 `project_refactor_sweep_status.md` L197 (규칙 출처, self-annotation 보유)
- 선례: `project_branch_hygiene_policy.md` / `project_ci_path_filter_074_075_quirk.md` / `project_be_276_133_052_spec_pipeline.md` (audit-memory → CLAUDE.md promote 동형, 메모리=detail 보존)
- [`tasks/INDEX.md`](../INDEX.md) § "When to Use Root vs Project Tasks" (CLAUDE.md=monorepo-level shared → root task 정당)

# Related Contracts

- 없음 (governance doc, 외부 contract/API/event 무관).

# Edge Cases

1. **append-only 위반 risk**: Edit 가 기존 5 bullet 또는 "Lifecycle" 줄 변경 시 catalog 무결성 훼손. 완화: old_string = "Tasks must contain all required sections … Failure Scenarios**.\n\nLifecycle and review rules:" 안정 anchor, new_string = 그 사이 2 bullet 삽입(기존 텍스트 보존); impl 후 `git diff` add-only 자가검증.
2. **HARDSTOP-03 (shared 파일 project-token)**: 추가 2 bullet 은 project-agnostic (gh/git 일반 명령, 서비스/도메인名 0) → hook clean. 검증: impl 후 `grep -nE 'projects/|apps/' CLAUDE.md` 신규 라인 무.
3. **HARDSTOP-05 (no task = no shared impl)**: 본 task 가 그 authorizing task — spec PR 머지로 tasks/ready/ landing 후 impl. 정당.
4. **catalog vs memory 중복 우려**: CLAUDE.md=catalog(2-bullet pointer), memory=worked-example detail. branch_hygiene/ci_path_filter 선례와 동일 — 중복 아님(메모리 self-annotation 이 명시).

# Failure Scenarios

A. **impl diff 가 기존 bullet 수정 (append-only 위반)** → `git diff` 에 context 외 `-` 발견 시 revert, anchor 재설정, 2 bullet 만 순수 삽입 재시도. 머지 전 add-only 입증.
B. **hook HARDSTOP-03 fire (오탐)** → 2 bullet 에 project token 0 이므로 미발생 예상. fire 시 STOP·문구 재검토(일반 명령어만 유지).
C. **다른 audit 후보를 함께 넣으라는 압력** → scope-out 엄수 (P3~P7 audit 미선택). 동반 변경 금지, green-wash·scope-creep 차단.

---

# Implementation Notes (작성 시 참고)

- 분석=Opus 4.7 / 구현 권장=Sonnet 4.6 (catalog 2-bullet append, 설계결정 무 — 본 spec 이 문구·위치 확정). impl 후 dispatcher add-only 자가검증(`git diff --numstat CLAUDE.md` = `2 0` 기대).
- Lifecycle: spec PR=본 task md + 루트 INDEX `## ready`. impl PR=task ready→review(lifecycle commit) + CLAUDE.md 2-bullet(별 commit). close chore=3-step bundle 1 commit(git mv review→done + Status[BE-299 staged-blob 재검증] + INDEX `## review`→empty/`## done` 1줄) — 본 task 가 승격하는 BE-303/BE-299 규율을 close chore 에서 자기적용(dogfood).
- 신규 ADR 불요 — reality-alignment(메모리 실증 규율의 catalog 표면화)·competing convention 무·audit-memory promote 선례(branch_hygiene/ci_path_filter/be_276) 동형. impl PR description 명시.
- **분량**: very small — 1 파일, 2 bullet append.
- dependency: 선행 없음 (CLAUDE.md 현 main = clean, working-tree revert 됨). 후속 없음 (P3~P7 별건·미선택).
