# Task ID

TASK-PC-FE-319

# Status

done

# Title

운영자 그룹 «멤버 추가» 선택기에서, 그 테넌트에 배정만 된(HOME 이 다른) 운영자를 «○○ 소속 · 배정만 됨» 으로 비활성 표시한다 — 고른 뒤 422 로 거절당하던 것을 고르기 전에 막는다

# Owner

platform-console

# Task Tags

- console-web
- frontend
- operator-groups

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet 5 — optional 필드 소비 + 행 비활성. (실제 구현: Opus 5.5)
>
> 🔵 **소유자 결정(2026-10-08 UTC):** 선택지 A(목록에 home 표시) — iam `TASK-BE-626` 참고(B 계열 보류 사유 포함).

---

# Dependency Markers

- 선행(같은 PR): iam `TASK-BE-626` — `GET /api/admin/operators` 항목의 `homeTenantId`.
- 선행: `TASK-PC-FE-317`(선택기 본체, #4218 머지) — 그 AC-0 이 이 빈틈을 기록했다.
- 같은 시기: 다른 세션이 `TASK-PC-FE-318` 을 쓴다(번호 합의). 겹치는 파일 없음.

# Background (착수 전 측정, `origin/main` `779195d40`)

| 사실 | 근거 |
|---|---|
| 선택기는 그룹 테넌트의 목록(HOME ∪ 배정)을 보여준다 | `apps/console-web/src/features/operator-groups/hooks/use-group-member-candidates.ts` |
| 그룹은 HOME == 그룹 테넌트만 받는다 — 배정-only 를 고르면 사유 단계 뒤 `422 GROUP_MEMBER_TENANT_MISMATCH` | iam `GroupAdminUseCase.java:171-176` · `TASK-PC-FE-317` AC-0 |
| 플랫폼 운영자(HOME `*`)도 회사 그룹엔 못 들어간다(`*` ≠ 그룹 테넌트) | 같은 판정 |
| 스키마는 비-strict zod | `shared/api/iam-operators-types.ts` `OperatorSummarySchema` |

# Goal

배정으로만 그 테넌트에 속한 운영자(플랫폼 운영자 포함)를 선택기에서 고를 수 없게 하고, 이유를 행에 보여준다. HOME 운영자와 `homeTenantId` 가 없는 항목(옛 producer · 샘플 픽스처)은 지금처럼 고를 수 있다.

# Scope

## In Scope

- `OperatorSummarySchema` 에 `homeTenantId: string` **optional**.
- `GroupMemberDialog`: `homeTenantId` 가 **있고** 그룹 테넌트와 다르면 행 비활성 + «`<home>` 소속 · 배정만 됨» (title 에 규칙 설명). 없으면 «모름» → 고를 수 있다(서버가 판정, 422 는 그대로 표시).
- 컴포넌트 주석(317 의 «producer 변경 없이는 못 막음» 문단 → 이 티켓으로 닫힘).
- 단위 시험.

## Out of Scope

- 운영자 관리 화면에 home 표시 — 필요해지면 별도.
- 그룹 멤버 규칙 변경 — 보류.

# Acceptance Criteria

- [x] **AC-1** — HOME 이 다른 운영자(예 `demo-corp`)와 플랫폼 운영자(`*`)는 비활성, 행에 «demo-corp 소속 · 배정만 됨». 클릭해도 «다음» 비활성.
- [x] **AC-2** — HOME 운영자는 고를 수 있다.
- [x] **AC-3** — `homeTenantId` 없는 항목은 고를 수 있다(«모름» 을 «HOME 아님» 으로 추측하지 않는다).
- [x] **AC-4** — bite: `origin/main` 의 다이얼로그에서 AC-1 시험 빨강(1 실패 / 7 통과), 복원 후 8/8.
- [x] **AC-5** — `tsc --noEmit` · `next lint` rc=0 · 대상 시험 통과(아래 기록).
- [ ] **AC-6** — 라이브: 다음 데모 창에서 배정-only 운영자가 비활성으로 보이는지. ⚪ 허용.

# Related Specs

- `projects/platform-console/specs/services/console-web/architecture.md`

# Related Contracts

- `projects/iam-platform/specs/contracts/http/admin-api.md` § GET /api/admin/operators `homeTenantId` 행(iam `TASK-BE-626`)

# Edge Cases

- 이미 멤버 + HOME 다름 → «이미 멤버» 만 표시(두 표시가 겹치지 않게 `otherHome` 은 비멤버에만).
- 샘플(방문자) 모드 픽스처엔 `homeTenantId` 가 없다 → 전부 고를 수 있는 지금 동작 유지(AC-3).

# Failure Scenarios

- 필드가 없을 때 막아 버림 → 옛 producer 와 섞인 배포 순간 선택기가 전부 비활성 → AC-3 시험.

# Implementation Record (2026-10-08 UTC)

- 변경: `shared/api/iam-operators-types.ts` · `features/operator-groups/components/GroupMemberDialog.tsx` · `tests/unit/features/operator-groups/GroupMemberDialog.test.tsx`(+2).
- 대상 시험 4파일 49/49 · bite 1 실패/7 통과(rc=1) → 복원 8/8 · `tsc --noEmit` rc=0 · `next lint` rc=0.
- 전량 vitest 는 CI `Frontend unit tests` 단독 레인으로 판정(로컬 동시 부하에서 5s 타임아웃이 나는 것을 313·315 에서 실측).

## CORRECTION — close (2026-10-08 UTC, 4차원 검증)

- **AC-6 닫힘**: 소유자가 2026-10-08 데모 창(24차, i-0445d76661ef0013d)에서 확인했다 — 대화 원문 «확인». 화면: `demo@demo.com` · `demo-corp` 그룹 «멤버 추가» 에서 `Store Staff (demo-corp assignment only)`(`assigned-only@demo.com`, HOME `ecommerce`) 행이 «ecommerce 소속 · 배정만 됨» 으로 비활성. 위 AC 목록의 `[ ]` 는 이 절이 닫는다.
- 🔵 그 운영자는 원래 시드에 없었다(데모 SUPER_ADMIN 이 관리하는 `demo-corp` 안에 배정-only 조합 0). 소유자 승인으로 같은 창의 iam DB 에 SSM 1회 삽입(다른 세션 실행, 확인 쿼리 3개 기대값 일치), 영구 시드는 iam `TASK-BE-628`(#4246 `276f74d52`) — 다음 굽기부터 기본 포함.
- 🔴 첫 안내(`ecommerce` 그룹 생성)는 틀렸다: demo 의 SUPER_ADMIN grant 가 `demo-corp` 에 묶여 그룹 생성이 `TENANT_SCOPE_DENIED`. 그룹 변경은 «배정» 이 아니라 «관리 grant» 범위를 본다(`GroupAdminUseCase.java:85,319`).
- (a) #4222 `MERGED` 2026-10-07T15:35:51Z, squash `07328d826`.
- (b) `07328d826` 이 `origin/main` 에 있음.
- (c) 머지 시점 체크 SUCCESS 22 · SKIPPED 45 · FAILURE 0.
- (d) AC-1~6 전부 닫힘.
