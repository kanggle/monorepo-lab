# Task ID

TASK-PC-FE-328

# Title

콘솔 가이드 화면 전부(전역 가이드 + 도메인 가이드 여섯)의 카드를 **두 열 → 한 열**로 쌓는다

# Status

done

# Owner

platform-console

# Task Tags

- platform-console
- guide

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet — Tailwind 반응형 열 클래스 제거 + 소스 가드 시험.

---

# Dependency Markers

- 출처: 2026-10-09 UTC 소유자 대화 — «콘솔 가이드에서 두 열씩 표기되는 거 한 열씩 표기되도록 수정». 범위 질문에 «모든 가이드 화면» 선택.
- 관련: `TASK-PC-FE-298`(done — 전역 가이드) · `TASK-PC-FE-321`(done — 「도메인 한눈에」) · `TASK-PC-FE-322`/`323`(done — 가이드 쉬운 말 재작성).

# Goal

가이드 화면의 카드 묶음이 넓은 화면(`md` 이상)에서 두 열(IAM 가이드 한 곳은 세 열)로 배치돼, 위에서 아래로 읽는 글이 지그재그로 읽힌다. 모든 가이드 화면에서 카드를 한 열로 쌓는다.

# Scope

## In Scope

`md:grid-cols-2` / `md:grid-cols-3` 제거(`grid gap-*` 는 유지 — 한 열 그리드):

| 파일 | 곳 | 쓰는 화면 |
|---|---|---|
| `shared/guide/DomainFeatureSummary.tsx` | 1 | 전역 가이드 「도메인 한눈에」 + 각 도메인 가이드 기능 요약 |
| `shared/guide/PermissionMapTable.tsx` (`MenuDescriptions`) | 1 | 도메인 가이드 메뉴 설명 카드 |
| `features/global-guide/.../GlobalGuideScreen.tsx` (`FactCards`) | 1 | 전역 가이드 설명 카드 |
| `features/iam-guide/.../IamGuideScreen.tsx` | 4 (2열 3 · 3열 1) | IAM 가이드 |
| `features/wms-guide/.../WmsGuideScreen.tsx` | 1 | WMS 가이드 |
| `features/ecommerce-guide/.../EcommerceGuideScreen.tsx` | 2 | E-Commerce 가이드 |

SCM · Finance · ERP 가이드는 자체 다열 블록이 없고 위 공용 카드로만 두 열이었다 — 공용 카드 수정으로 함께 한 열이 된다.

- 가드 시험 `tests/unit/guide-single-column.test.ts`: `src/features/*-guide/**` + `src/shared/guide/**` 의 `.tsx` 에 `grid-cols-[2-9]` 가 있으면 빨강. 모집단이 일곱 가이드 + 공용 가이드 디렉터리를 모두 포함하는지(비공허) 함께 단언.

## Out of Scope

- 표(`<table>`)의 열 — 카드 배치가 아니라 데이터 열이므로 그대로.
- 가이드가 아닌 운영 화면의 그리드.
- 카드 내용 · 문구.

# Acceptance Criteria

- **AC-1** ✅ 위 6개 파일 10곳에서 다열 클래스 제거 — 모든 가이드 화면의 카드가 어느 폭에서나 한 열.
- **AC-2** ✅ 가드 시험 2개(모집단 비공허 + 다열 클래스 0). bite: `DomainFeatureSummary.tsx` 에 `md:grid-cols-2` 를 되살리면 정확히 다열 시험 1개가 그 파일 이름을 들고 빨강 → 복원.
- **AC-3** ✅ `tsc --noEmit` rc=0 · `pnpm lint` rc=0 · 전체 vitest 364 파일 / 4,124 시험 rc=0. 스크린샷 비교 시험(`toHaveScreenshot`) 의존 0건.
- **AC-4** ⚪ 라이브 데모 화면 확인 — 다음 데모 창.

# Related Specs

- `projects/platform-console/specs/services/console-web/architecture.md` (가이드 feature 구조 — 배치 규칙은 적혀 있지 않아 변경 없음)

# Related Contracts

- 없음 (정적 화면 배치).

# Edge Cases

- 좁은 화면: 원래도 `md` 미만에선 한 열이었으므로 변화 없음.
- 카드 높이가 제각각이던 두 열 배치의 빈 공간이 사라지고 세로 길이는 늘어난다(의도).

# Failure Scenarios

- 새 가이드 화면이 두 열 그리드를 다시 들여오는 경우 → 가드 시험이 `features/*-guide` 디렉터리를 이름 규칙으로 모으므로 새 가이드도 자동 포함되어 빨강.
- 가이드 디렉터리 이름 규칙이 바뀌어 모집단이 줄어드는 경우 → 비공허 시험이 일곱 가이드 이름을 하나씩 단언하므로 빨강.

## CORRECTION — close (2026-10-09 UTC, 4차원 검증)

- **AC-4 닫힘**: 소유자가 데모 창에서 가이드 화면 카드가 한 열로 쌓이는 것을 눈으로 확인했다(2026-10-09 UTC 대화 — «PC-FE-328 (가이드 카드 한 열) 확인했어»). 위 AC 목록의 ⚪ 는 이 절이 닫는다.
- (a) #4271 `MERGED` 2026-10-09T13:01:46Z, squash `e28c32306`.
- (b) `e28c32306` 이 `origin/main` 에 있음.
- (c) 머지 시점 체크 SUCCESS 12 · SKIPPED 54 · FAILURE 0.
- (d) AC-1~4 전부 닫힘.
