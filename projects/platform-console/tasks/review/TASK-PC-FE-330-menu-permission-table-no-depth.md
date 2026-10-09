# Task ID

TASK-PC-FE-330

# Title

도메인 가이드 「권한 안내 › 메뉴별 권한」 표에서 **뎁스** 열을 뺀다

# Status

review

# Owner

platform-console

# Task Tags

- platform-console
- guide

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet — 표 열 하나를 옵션으로 끄는 변경.

---

# Dependency Markers

- 출처: 2026-10-10 UTC 소유자 대화 — «메뉴별 권한에서는 뎁스 빼줘».
- 관련: `TASK-PC-FE-298`(done — 권한·기능 매핑 표) · `TASK-PC-FE-329`(review — 도메인 가이드 7탭).

# Goal

도메인 가이드 6개의 「권한 안내」 탭 «메뉴별 권한» 표에서 «뎁스» 열을 없앤다. 같은 표 컴포넌트(`PermissionMapTable`)를 쓰는 전역 가이드 「전체 메뉴 소개」 표는 사이드바 전체의 모양(1·2뎁스)을 보여 주는 곳이라 뎁스 열을 유지한다 — 요청이 «메뉴별 권한에서는» 으로 범위를 정했다.

# Scope

## In Scope

- `PermissionMapTable` 에 `showDepth`(기본 `true`) prop — 머리 칸과 행 칸을 함께 끈다.
- `DomainGuideTabs` 의 «메뉴별 권한» 표에 `showDepth={false}`.
- 시험: 도메인 가이드 공용 헬퍼가 «메뉴별 권한» 표 머리에 «메뉴명» 은 있고 «뎁스» 는 없음을 단언 · 전역 가이드 «전체 메뉴 소개» 표에는 «뎁스» 가 남아 있음을 대조로 단언.

## Out of Scope

- 전역 가이드 「전체 메뉴 소개」 표의 뎁스 열(유지).
- 메뉴명 칸 아래 사이드바 경로 표시(그대로 — 뎁스 정보는 경로로 여전히 읽힌다).

# Acceptance Criteria

- **AC-1** ✅ 도메인 가이드 6개의 «메뉴별 권한» 표에 «뎁스» 열 없음.
- **AC-2** ✅ 전역 가이드 «전체 메뉴 소개» 표에는 «뎁스» 열 유지(대조 시험).
- **AC-3** ✅ bite: `showDepth={true}` 로 되돌리면 도메인 가이드 시험 6개가 «뎁스 를 포함하면 안 된다» 로 빨강, 전역 가이드 대조는 초록 → 복원.
- **AC-4** ✅ `tsc --noEmit` rc=0 · `pnpm lint` rc=0 · 전체 vitest 364 파일 / 4,125 시험 rc=0. (첫 전체 실행은 무관한 7 파일에서 11건 적색 — 시간 초과 · 비동기 대기; 그 7 파일 단독 재실행 51/51 통과, 두 번째 전체 실행 전부 통과 → 실행 시점 부하로 판정.)
- **AC-5** ⚪ 라이브 데모 화면 확인 — 다음 데모 창.

# Related Specs

- `projects/platform-console/specs/services/console-web/architecture.md` § guide (열 구성은 적혀 있지 않아 변경 없음)

# Related Contracts

- 없음 (정적 가이드 화면).

# Edge Cases

- 표가 선택되지 않은 탭(숨김 패널) 안에 있으므로 시험은 `getAllByRole('columnheader', { hidden: true })` 로 머리 칸을 읽는다 — 첫 작성 때 `hidden` 없이 써서 7개 모두 «columnheader 없음» 으로 빨강(시험 결함, 화면 아님)이었다.

# Failure Scenarios

- 머리 칸만 끄고 행 칸을 남기면 열이 한 칸씩 밀린다 → 같은 prop 이 두 곳을 함께 끈다.
- 전역 가이드까지 뎁스가 사라지는 경우 → 대조 시험이 잡는다.
