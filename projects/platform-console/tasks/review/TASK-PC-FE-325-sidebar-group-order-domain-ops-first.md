# Task ID

TASK-PC-FE-325

# Title

사이드바 1뎁스 그룹 순서를 **가이드 · 개요 → 도메인 운영 → 관리 → 조직 설정 → 고객 신원** 으로 바꾼다

# Status

review

# Owner

platform-console

# Task Tags

- platform-console
- nav

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet — `GROUPS` 배열의 그룹 순서만 바꾸는 단순 변경.

---

# Dependency Markers

- 출처: 2026-10-09 UTC 소유자 대화(«콘솔에서 가이드, 개요, 도메인 운영, 관리, 조직 설정, 고객 신원 으로 메뉴 순서 변경») — 소유자 결정.
- 관련: `TASK-PC-FE-313`(done — 「조직 설정」 그룹 신설, 설정 먼저 순서) · `TASK-PC-FE-225`(done — 「고객 신원」 분리) · `TASK-PC-FE-314`(done — 역할별 메뉴 숨김) · `TASK-PC-FE-297`(done — 도메인 드릴 안의 가이드 → 개요 순서, 이 태스크는 손대지 않음).
- 같은 대화에서 파생: `TASK-PC-FE-326`(CS 2선 `SUPPORT_LOCK` 이 계정 운영을 못 여는 결함) — 서로 다른 파일을 고치고 의존 없음.

# Goal

지금 순서(가이드 · 개요 → 관리 → 고객 신원 → 조직 설정 → 도메인 운영)는 «설정 먼저» 원칙으로 짜였다. 그래서 매일 여는 도메인 운영 화면(WMS·SCM·Finance·ERP·E-Commerce)이 맨 아래에 있다. 소유자 결정으로 **매일 쓰는 것 먼저, 관리·설정은 뒤로** 바꾼다.

「고객 신원」(계정 운영)은 맨 끝에 둔다. 이 메뉴를 볼 수 있는 역할은 플랫폼 CS·보안 역할(`account.*` 권한)뿐이다. 나머지 운영자에게는 `TASK-PC-FE-314` 가 이 메뉴를 숨기므로, 위치는 그 역할들에게만 의미가 있다.

# Scope

## In Scope

- `console-nav-config.ts` `GROUPS` 의 그룹 순서 변경 + 순서 근거 주석.
- 「데이터 순서 = 사이드바 순서」를 선언한 `domain-features.ts` `DOMAIN_FEATURES` 를 같은 순서로(IAM 을 맨 끝으로).
- 그룹 순서를 고정하는 단위 시험.

## Out of Scope

- 각 그룹 안의 항목 순서, 도메인 드릴 안의 순서(가이드 → 개요 → 기능).
- 라우트 · testid · 라벨 · 권한 게이트 — 모두 그대로.
- 온보딩 안내 문구(`OnboardingWhatHappens` 의 «조직 생성 → 도메인 구독 → 운영자 초대 · 도메인 운영»). 이건 메뉴 순서가 아니라 처음 쓸 때의 할 일 순서라서 그대로 맞다.

# Acceptance Criteria

- **AC-1** ✅ 렌더된 사이드바의 1뎁스 순서 = 가이드 · 개요 → 도메인 운영 → 관리 → 조직 설정 → 고객 신원. `sidebar-nav-order-icons.test.tsx` 에 두 시험 추가: 설정 자체의 그룹 라벨 순서 + 렌더된 DOM 에서 각 그룹 대표 항목(`nav-global-guide`·`nav-dashboards`·`nav-wms`·`nav-iam`·`nav-org-hierarchy`·`nav-accounts`)의 순서.
- **AC-2** ✅ 전역 가이드의 도메인 섹션 순서(`DOMAIN_FEATURES`)가 사이드바와 같다(WMS·SCM·Finance·ERP·E-Commerce·fan → IAM). 전역 가이드의 «전체 메뉴» 표는 `navLeaves()` 가 `GROUPS` 를 그대로 펴므로 손대지 않아도 자동으로 따라온다.
- **AC-3** ✅ bite: `console-nav-config.ts` 를 `origin/main` 판으로 되돌리면 새 시험 **정확히 2개만** 빨강(17개 중 15개 통과) → 복원.
- **AC-4** ✅ `tsc --noEmit` rc=0 · `pnpm lint` rc=0 · 타깃 vitest 8개 파일 110개 시험 rc=0 · 전체 vitest 356개 파일 4,054개 시험 rc=0.
- **AC-5** ✅ e2e 스펙(`*.spec.ts`)에 그룹 이름·그룹 순서에 기대는 단언 없음(grep 0건) — 야간 e2e 회귀 위험 없음.
- **AC-6** ⚪ 라이브 데모 화면 확인 — 다음 데모 창에서 소유자 확인.

# Related Specs

- `projects/platform-console/specs/services/console-web/architecture.md` (사이드바 그룹 설명 — 순서는 적혀 있지 않아 변경 없음)

# Related Contracts

- 없음 (화면 배치만 바뀌고 API·라우트 변경 없음)

# Edge Cases

- 역할 때문에 그룹이 통째로 숨겨지는 경우(예: 고객 신원): `visibleGroups()` 가 빈 그룹을 빼므로 남은 그룹의 상대 순서는 그대로다.
- fan 디렉터리(`productKey: 'fan'`)는 도메인 운영 그룹 맨 끝 그대로 — 플랫폼 운영자에게만 보인다.

# Failure Scenarios

- 그룹 블록을 옮기다 항목이 빠지거나 겹치는 경우 → `permission-map-drift`·`domain-features-drift` 시험이 nav 의 모든 href 를 대조하므로 빨강이 된다(통과 확인).
- 주석의 «위/아래» 표현이 새 순서와 어긋나는 경우 → 「고객 신원」은 관리 그룹 «아래», 「조직 설정」은 IAM «아래» 라는 기존 주석이 새 순서에서도 그대로 참임을 확인했다.
