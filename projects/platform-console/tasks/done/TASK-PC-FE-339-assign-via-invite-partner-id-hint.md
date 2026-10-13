# Task ID

TASK-PC-FE-339

# Title

운영자 «테넌트 배정»은 이메일 초대를 주 경로로 · 파트너십 초대의 상대 테넌트 ID 는 유지 + 안내 (ID 입력 정리의 소유자 결정 2건)

# Status

done — 구현 완료(2026-10-13 UTC). AC-1~5 ✅. 라이브 확인은 AC 밖(데모 창).

# Owner

platform-console

# Task Tags

- operators
- partnerships
- ux

---

> **분석 모델:** Opus 5.5 / **구현:** Opus 5.5(직접 — 문구 · 접기 두 곳의 작은 변경). 백엔드 변경 없음.

---

# Dependency Markers

- 선행: `TASK-PC-FE-334`(묶음 ① — 이 두 칸을 «소유자 결정 대기»로 제외했다) · `TASK-MONO-784` S5(초대 수락 → 소속 추가: 다른 회사에 이미 있는 운영자도 초대로 들어올 수 있다).

# Goal

ID 입력 정리(묶음 ①~④)에서 남긴 두 칸은 «다른 회사의 사람 · 회사를 콘솔이 찾아 보여 줘도 되나» 라는 같은 질문이었다. 소유자 결정(2026-10-13, 선택창):

| 칸 | 결정 | 이유 |
|---|---|---|
| 운영자 «테넌트 배정»(`AssignOperatorForm`) | **이메일 초대를 주 경로로** — ID 배정은 «ID 를 아는 경우» 로 접어 유지 | 대상은 정의상 이 테넌트 밖이라 어떤 목록에도 없다. 이메일 초대(`POST /api/admin/operator-invitations`)는 본인 수락으로 소속을 더하므로 찾기도 노출도 필요 없다 |
| 파트너십 초대 «파트너 조직 ID»(`PartnershipInviteForm`) | **ID 입력 유지 + 안내** | 상대 회사가 알려 주는 읽히는 slug(`globex-corp`)이고, 상대가 수락해야 성립한다. 다른 회사 목록은 보여 주지 않는다 |

# Scope

## In

- `AssignOperatorForm` — 맨 위 안내(`assign-operator-invite-first`): «다른 회사 사람이나 아직 이 테넌트에 없는 사람을 넣는 기본 방법은 운영자 초대 — 수락할 때 소속이 추가, ID 몰라도 됨». 같은 화면에 초대 폼이 있으면(`inviteAvailable`) `#invite-operator-form` 링크, 없으면(플랫폼 범위 `*`) 이름만. ID 입력 + 배정 버튼은 `<details>`(`assign-operator-by-id`, 기본 접힘) «운영자 ID 를 아는 경우 — 수락 없이 바로 배정» 안으로.
- `InviteOperatorForm` — 링크 대상 `id="invite-operator-form"`.
- `OperatorsScreen` — `inviteAvailable={canInvite}`(초대 폼이 그려지는 조건과 같은 값).
- `PartnershipInviteForm` — 안내(`partnership-invite-partner-hint`): ID 는 상대 회사에게 받아 입력 · 다른 회사 목록은 안 보여 줌 · 상대가 수락해야 성립.

## Out

- 다른 테넌트 운영자 이메일 검색 · 상대 회사 이름 확인 · 파트너 디렉터리(선택창에서 고르지 않음 — 모두 백엔드 + 노출 정책).
- ID 배정 경로의 제거(유지하기로 함).

# Acceptance Criteria

- [x] **AC-1** — 구체 테넌트에서 배정 폼 맨 위에 초대 안내가 있고 `#invite-operator-form` 링크가 같은 화면의 초대 폼을 가리킨다. 실측: `operators-assignment-ui.test.tsx` «leads with the email invitation…»(링크 href · 대상 `id` 단언).
- [x] **AC-2** — ID 입력은 기본 접힌 `<details>` 안에 있고 기존 배정 흐름(확인 → POST)은 그대로다. 실측: 같은 시험(`DETAILS` · `open` 없음 · 안에 `assign-operator-id`) + 기존 «assign form → confirm → POST» 시험 무수정 통과.
- [x] **AC-3** — 플랫폼 범위(`*`)에선 초대 폼이 없으므로 안내는 이름만, 죽은 링크 없음. 실측: «platform scope (*) …» 시험.
- [x] **AC-4** — 파트너 초대 폼은 ID 입력을 유지하고 안내 문구(받아 입력 · 상대 수락)를 보인다. 실측: `PartnershipsScreen.test.tsx` 신규 1칸.
- [x] **AC-5** — 각각 독립 실행 · 명시 rc: 관련 4 파일 `vitest run` rc=0(36) · `tsc --noEmit` rc=0 · `pnpm lint` rc=0. 전체 vitest · `next build` 는 CI 권위(로컬 미실행 — 문구 · 마크업만 바뀐 변경). e2e 스펙 grep(`assign-operator` · `partnership-invite-partner` · «테넌트 배정») 0건.

# Related Specs

- `specs/services/console-web/architecture.md`.

# Related Contracts

- iam `admin-api.md` § `POST /api/admin/operator-invitations`(소비만 — 기존 초대 폼) · § `POST /api/admin/partnerships`(`partnerTenantId` — 존재하는 ACTIVE 테넌트, 상대 `:accept` 로 성립).

# Edge Cases

| 상황 | 기대 |
|---|---|
| 활성 테넌트 = `*` | 초대 폼 없음 → 안내는 이름만 |
| 활성 테넌트 없음 | 배정 폼 자체가 없음(기존) |

# Failure Scenarios

| 실패 | 기대 |
|---|---|
| 배정 POST 실패 | 기존 확인 창 오류 그대로(변경 없음) |

## CORRECTION

- 2026-10-13 UTC — **번호 변경 338 → 339** (tasks/INDEX.md rule 4). 다른 세션의 `TASK-PC-FE-338-domain-permission-sets-console.md`(ADR-MONO-083 P4, #4337 `945db2169`)가 **먼저** main 에 들어갔고, 이 작업의 impl PR #4336 은 그 전에 CI 를 돌려 충돌을 못 본 채 뒤에 머지됐다 → main 의 필수 체크 `Task ID collision` 이 빨강(같은 ID 가 ready · review 둘 다에). 먼저 들어간 쪽이 번호를 갖는다 → 이 파일을 339 로 옮긴다. 🔴 impl 커밋 `8c95379ee` 의 제목 · PR #4336 제목은 «TASK-PC-FE-338» 로 남아 있다(이미 머지됨 — 고칠 수 없다). 그 «338» 은 이 작업(339)이다.
- 2026-10-13 UTC close chore — 4-dim verified: (a) PR #4336 `MERGED` (b) squash `8c95379ee` 가 `origin/main` 조상 (c) 머지 시점 statusCheckRollup 실패 0 · 성공 12(콘솔 전체 vitest · Playwright 스모크 포함) — 🔴 단, 머지 **뒤** main 은 위 ID 충돌로 빨강이었고 이 정리가 그것을 푼다 (d) `# Acceptance Criteria` 5/5 `[x]`.
