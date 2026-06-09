# Task ID

TASK-PC-FE-068

# Title

console-web 도메인 상태 개요에 "← 통합 개요로 돌아가기" 백링크 추가 + 왼쪽 사이드바에서 "도메인 상태" 항목 제거 (개요 카드 "전체 보기 →"로만 도달)

# Status

review

# Owner

frontend (Opus 4.8 analysis / Sonnet 4.6 impl). console-web only. No contract/backend change.

# Task Tags

- code
- test

---

# Dependency Markers

- **user request (2026-06-09)** — (a) 개요 > "도메인 상태 요약" 카드의 "전체 보기 →"로 들어가는 `/dashboards/health`에 "← 통합 개요로 돌아가기"(→`/dashboards/overview`) 추가. (b) 왼쪽 메뉴바에서 "도메인 상태"(nav-domain-health) 제거.
- **결과 동선**: 도메인 상태 개요는 이제 **개요 페이지의 "도메인 상태 요약" 카드(PC-FE-061) "전체 보기 →"로만** 도달 + 백링크로 복귀. 사이드바 top group은 개요/카탈로그 2개 leaf만 남음.
- **relates**: TASK-PC-FE-061(개요 도메인상태 요약 카드 + /dashboards/health 링크), TASK-PC-FE-059(사이드바 drill 엔진).

# Goal

`/dashboards/health` 페이지 상단에 `/dashboards/overview`로 가는 "← 통합 개요로 돌아가기" 링크(모든 분기: 정상/noTenant/bffUnavailable). `ConsoleSidebarNav.tsx` GROUPS 첫 그룹에서 `nav-domain-health` leaf 제거.

# Scope

## In Scope

- **`src/app/(console)/dashboards/health/page.tsx`** — 각 return 분기 상단에 `<Link href="/dashboards/overview" data-testid="domain-health-back">← 통합 개요로 돌아가기</Link>`. 정상 분기는 `<><BackLink/><DomainHealthScreen/></>`로 감쌈(또는 공통 헤더 위에). 기존 카탈로그 링크/문구 불변.
- **`src/shared/ui/ConsoleSidebarNav.tsx`** — 첫 그룹 items에서 `{ href: '/dashboards/health', label: '도메인 상태', testid: 'nav-domain-health' }` 제거(개요/카탈로그만 남김). 엔진/다른 그룹 불변.
- **Tests**:
  - `tests/unit/domain-health-nav.test.tsx` — 사이드바 가드 갱신: nav-domain-health "유지" 단언(2건: line 61-66 + line 84-89) → **제거** 단언으로 교체(`not.toContain('nav-domain-health')` / `not.toContain("'/dashboards/health'")` / `not.toContain('도메인 상태')`), 개요/카탈로그/관리/도메인운영 잔존 단언 유지.
  - `tests/unit/domain-health-nav.test.tsx` 또는 신규 — `/dashboards/health` 페이지가 `domain-health-back`(→`/dashboards/overview`) 링크를 포함(소스/렌더 단언).

## Out of Scope

- accounts 권한(별 task MONO-202), 도메인 health 데이터/카드 로직, 개요 요약 카드(PC-FE-061 불변).
- /dashboards/health 라우트 자체 제거(아님 — 카드로 도달 유지).

# Acceptance Criteria

- [ ] `/dashboards/health`(정상·noTenant·bffUnavailable)에 "← 통합 개요로 돌아가기"(→`/dashboards/overview`, testid `domain-health-back`) 표시.
- [ ] 사이드바에 "도메인 상태"(nav-domain-health) 없음; 개요(`/dashboards/overview`)·카탈로그(`/console`)는 유지.
- [ ] 도메인 상태 개요는 개요 페이지 "도메인 상태 요약" 카드 "전체 보기 →"로 도달 가능(PC-FE-061 불변).
- [ ] `vitest` + `tsc --noEmit` green; scope = console-web only.

# Related Specs

- 없음(UI 네비게이션, 계약 무관).

# Related Contracts

- 변경 없음.

# Target Service

- `platform-console` / `apps/console-web` — health page + sidebar + 테스트.

# Architecture

- 도메인 상태 개요를 top-level 사이드바 항목에서 개요의 **드릴 자식(카드 경유)**으로 강등 — 개요 1클릭 홈(PC-FE-034/061) 유지하며 두 대시보드를 카드+백링크로 연결. 사이드바 top group 간결화.

# Edge Cases

- noTenant/bffUnavailable 분기에서도 백링크 표시(복귀 동선 보장).
- 사이드바 제거 후 /dashboards/health 직접 URL 진입 = 정상 동작(라우트 유지), 백링크로 개요 복귀.

# Failure Scenarios

- 백링크 정상 분기에만 넣고 gate 분기 누락 → noTenant일 때 복귀 불가. AC가 3분기 모두 단언.
- 사이드바 제거했는데 도달 경로 끊김 → PC-FE-061 카드 "전체 보기 →" 존재로 보장(불변 단언).

# Definition of Done

- [ ] health 3분기 백링크 + 사이드바 nav-domain-health 제거
- [ ] vitest(가드 갱신) + tsc green; scope = console-web only
- [ ] Acceptance Criteria 충족
- [ ] Ready for review
