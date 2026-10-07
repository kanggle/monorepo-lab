# Task ID

TASK-PC-FE-315

# Status

review

# Title

IAM 드릴의 «권한»·«권한 세트» 순서를 바꿔 «권한 세트» 를 먼저 둔다 — 배정에 실제로 붙는 단위가 먼저, 그 안의 키가 다음

# Owner

platform-console

# Task Tags

- console-web
- frontend
- navigation

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet 5 (Haiku 로도 충분) — 메뉴 두 항목 순서 + 가이드 표 순서. (실제 구현: Opus 5.5)
>
> 🔵 **소유자 결정(2026-10-07 UTC):** 대화 «IAM 메뉴 순서가 어울리는지» → 추천 «권한 세트 ↔ 권한» · «운영자 → 운영자 그룹 유지(그룹 초대 연동 `TASK-PC-FE-316` 때 뒤집는다)» → «진행».

---

# Dependency Markers

- 선행: `TASK-PC-FE-313`(done 대기 — IAM 드릴을 7항목으로 만든 티켓, #4214 머지).
- 후속: `TASK-PC-FE-316`(backlog) 이 같은 드릴에서 «운영자 그룹» 을 «운영자 관리» 위로 올린다.
- 같은 시기 `TASK-PC-FE-314`(운영자 그룹 선택기)는 `operator-groups` · `iam-operators-read` 를 건드린다 — 겹치는 소스 없음, `tasks/INDEX.md` 만 공유 → 직렬 머지.

# Background (착수 전 측정, `origin/main` `5e213a8e7`)

| 사실 | 근거 |
|---|---|
| IAM 드릴 = 가이드 · 개요 · 운영자 관리 · 운영자 그룹 · **권한 · 권한 세트** · 감사·보안 | `apps/console-web/src/shared/ui/console-nav-config.ts`(착수 전 `:173-186`) |
| «권한 세트» = 테넌트 배정에 붙이는 묶음(= 역할), «권한» = 그 안의 키 카탈로그. 같은 데이터(`GET /api/admin/roles`), 같은 게이트 `operator.manage` | `features/iam-guide/data.ts` `CONSOLE_MENUS` 두 항목 · `shared/guide/permission-map.ts` 두 행 |
| 권한 지도 표 순서는 `GROUPS` 에서 파생 — 손댈 순서 없음 | `permission-map.ts` `resolvePermissionMap()` |
| 순서를 단언하는 시험 = `sidebar-iam-group.test.tsx` 드릴 href 목록 하나. e2e 는 두 메뉴 순서를 단언하지 않는다 | 저장소 grep |

# Goal

배정에 실제로 붙는 단위(권한 세트)를 먼저, 그 내용(권한)을 다음에 둔다. AWS IAM 의 Roles → Policies 와 같은 방향. 라우트·게이트·testid 무변경.

# Scope

## In Scope

- `console-nav-config.ts` 두 항목 순서 + 주석.
- `features/iam-guide/data.ts`: `CONSOLE_MENUS` 두 항목 순서 · `SCREEN_ACCESS` 행 라벨 «권한 세트 · 권한» · 순서 주석.
- `tests/unit/sidebar-iam-group.test.tsx` 드릴 href 순서 단언.

## Out of Scope

- «운영자 그룹» 을 «운영자 관리» 위로 — `TASK-PC-FE-316`(그룹 초대 연동이 들어올 때 함께).
- 두 화면 합치기 — 소유자 대화에서 «지금은 하지 않음».

# Acceptance Criteria

- [x] **AC-1** — IAM 드릴 링크 순서가 `/iam/guide · /iam · /operators · /operator-groups · /permission-sets · /permissions · /audit`.
- [x] **AC-2** — IAM 가이드 메뉴 표에서 «권한 세트» 가 «권한» 앞, 접근 매트릭스 행 라벨 «권한 세트 · 권한».
- [x] **AC-3** — bite: `origin/main` 의 nav 설정에서 AC-1 시험이 빨강(1 실패 / 17 통과), 새 설정에서 18/18.
- [x] **AC-4** — `tsc --noEmit` · `next lint` rc=0, 대상 시험 5파일 72/72. 전량 vitest 는 아래 기록 참고.
- [ ] **AC-5** — 라이브: 다음 데모 창에서 IAM 드릴 순서 눈 확인. ⚪ 허용.

# Related Specs

- `projects/platform-console/specs/services/console-web/architecture.md` (라우트 트리 — 순서 표기 없음, 변경 불필요)

# Related Contracts

- 없음.

# Edge Cases

- 딥링크 `/permissions` · `/permission-sets` 의 활성 표시는 순서와 무관(접두 일치) — 기존 딥링크 시험이 그대로 덮는다.

# Failure Scenarios

- 순서를 단언하는 숨은 e2e → grep 0건. 머지 뒤 nightly 한 번 확인.

# Implementation Record (2026-10-07 UTC)

- 변경: `console-nav-config.ts` · `features/iam-guide/data.ts` · `sidebar-iam-group.test.tsx`. `permission-map.ts` 는 순서가 nav 파생이라 무변경.
- 게이트: `tsc --noEmit` rc=0 · `next lint` rc=0 · 대상 5파일 72/72 · bite 1 실패 / 17 통과(rc=1) → 복원 후 통과.
- 전량 vitest: 🔴 **로컬에서 판정 불가**. 같은 시각 `TASK-PC-FE-314` 구현 에이전트가 다른 worktree 에서 vitest 를 돌리고 있었고(node 프로세스 24개), 전량 3801/3821 · 실패 파일 12개 중 다수가 `Test timed out in 5000ms`(폼 입력 시험 — 이 변경과 무관, 어느 것도 nav 를 import 하지 않음). 실패 파일만 다시 돌려도 부하가 남아 18 실패. ⇒ CI 의 단독 레인(`Frontend unit tests`)을 판정으로 삼는다 — 결과는 PR 체크에 남는다.
