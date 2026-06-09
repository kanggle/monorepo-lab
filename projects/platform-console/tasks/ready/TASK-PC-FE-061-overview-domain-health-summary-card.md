# Task ID

TASK-PC-FE-061

# Title

console-web 개요(operator overview): add a compact "도메인 상태 요약" card with a "전체 보기 →" link to `/dashboards/health` — bridges the two dashboards while keeping 개요 a one-click home (no sidebar drill-in)

# Status

ready

# Owner

frontend (Opus 4.8 analysis / Sonnet 4.6 impl) — additive overview-page composition; reuses the existing domain-health fetch; no contract/spec/backend change.

# Task Tags

- code
- test

---

# Dependency Markers

- **decision**: 사용자가 "개요를 드릴-인 부모로 만들지 않고(홈 1-click 유지) 개요 페이지 안에 도메인 상태 요약 카드 + 전체 보기 링크" 패턴을 선택(2026-06-09). 이 task가 그 결정을 구현.
- **builds on**: TASK-PC-FE-011 (operator overview `/dashboards/overview` MVP) + TASK-PC-FE-013 (domain health `/dashboards/health`, `§2.4.9.2` `getDomainHealthState`). 이 task는 두 feature를 **route(page) 레벨에서 합성**만 한다(각 feature 내부 불변).
- **note**: domain health = 각 도메인 `/actuator/health` fan-out(테넌트-무관 infra liveness, per-tenant 데이터 요청 아님). 요약 카드는 `getDomainHealthState()`를 그대로 재사용 — 동일 데이터.

# Goal

`/dashboards/overview`(개요) 페이지 성공 분기에 **"도메인 상태 요약"** 카드를 추가한다: 5개 도메인(iam/wms/scm/finance/erp)의 health를 `정상 N · 주의 M · 점검 불가 K` + 도메인별 미니 배지로 압축 표시하고, **"전체 보기 →"** 링크로 `/dashboards/health`로 보낸다. 개요는 사이드바에서 **1-click leaf 그대로**(드릴-인 안 함). resilient: health 미가용 시 카드가 컴팩트 "불러올 수 없음" 상태로 degrade(개요 절대 blank 안 됨), 링크는 계속 동작.

# Scope

## In Scope

- **`src/features/domain-health/components/DomainHealthSummaryCard.tsx`** (new) — server component. props `{ state: DomainHealthState }`.
  - `state.health` 있으면: 3-tone 분류(정상=`ok`+`UP` / 주의=`ok`+non-`UP`(DOWN/OUT_OF_SERVICE/UNKNOWN) / 점검 불가=`degraded`) → 카운트 줄 + `CARD_ORDER` 순 도메인 미니 배지(색 dot + 라벨).
  - `state.health === null`(bffUnavailable/unauthorized) → 컴팩트 "일시적으로 불러올 수 없음" note(개요 blank 금지). 어느 경우든 "전체 보기 →" Link(`/dashboards/health`) 항상 렌더.
  - testid: `domain-health-summary`, `domain-health-summary-viewall`, `domain-health-summary-counts`, `domain-health-summary-badge-{domain}`, `domain-health-summary-unavailable`.
- **`src/features/domain-health/index.ts`** — `DomainHealthSummaryCard` + `DomainHealthSummaryCardProps` barrel export.
- **`src/app/(console)/dashboards/overview/page.tsx`** — 성공 분기에서 `getDomainHealthState()` 호출 후 `<OperatorOverviewScreen>` 아래에 `<DomainHealthSummaryCard state={...}>` 렌더(fragment 래핑). noTenant/bffUnavailable/unauthorized 게이트는 기존 그대로(요약 카드는 성공 분기에서만 — 그 시점엔 active tenant 보장).
- **Tests** (`tests/unit/domain-health-summary.test.tsx`, new): 전부 정상(정상 5) / 혼합(DOWN+degraded → 카운트·tone) / `health=null`(unavailable note + 링크 유지) / "전체 보기" href=`/dashboards/health`.

## Out of Scope

- 사이드바 네비 변경(개요는 leaf 유지 — 드릴-인 안 함).
- domain-health 또는 operator-overview feature 내부 로직/스크린 변경.
- domain-health를 테넌트별 end-to-end probe로 바꾸는 것(별건; 현재는 actuator liveness).
- 백엔드/계약/스펙 변경.

# Acceptance Criteria

- [ ] `/dashboards/overview` 성공 시 5개 도메인 카드 아래 "도메인 상태 요약" 카드가 렌더되고 `정상/주의/점검 불가` 카운트 + 도메인별 배지(tone) 표시; "전체 보기 →"가 `/dashboards/health`로 링크.
- [ ] tone 분류: `ok`+`UP`=정상, `ok`+non-`UP`=주의, `degraded`=점검 불가.
- [ ] `state.health === null`(bff 미가용) → 컴팩트 "불러올 수 없음" note로 degrade(개요 다른 영역·콘솔 쉘 정상), "전체 보기" 링크 유지.
- [ ] 개요 사이드바 항목은 1-click leaf 그대로(네비 미변경).
- [ ] `pnpm exec vitest run` green(new + 기존 overview/domain-health suites 무회귀), `npx tsc --noEmit` clean; scope = console-web only.

# Related Specs

- `projects/platform-console/specs/contracts/console-integration-contract.md` §2.4.9.1(operator overview) / §2.4.9.2(domain health) — 소비만, 변경 없음.

# Related Contracts

- 변경 없음. page-레벨 feature 합성 + 기존 read 재사용.

# Target Service

- `platform-console` / `apps/console-web` — `features/domain-health` 신규 컴포넌트 + barrel + `dashboards/overview/page.tsx` 합성 + 단위 테스트. read-only/additive.

# Architecture

- Page-레벨 cross-feature 합성(route가 두 feature barrel을 조합 — Layered-by-Feature 허용). 요약 카드는 domain-health feature 소유(domain-health 타입/데이터 소비). 개요 성공 분기에서만 health fetch(게이트 통과 후라 active tenant 보장; 낭비 호출 없음).

# Edge Cases

- 전부 `UP` → 정상 5 / 주의 0 / 점검 불가 0.
- 일부 `degraded`(leg unreachable) → 점검 불가 카운트 + gray 배지, 나머지 정상 유지(per-domain isolation).
- `health=null`(bff 502/timeout) → unavailable note + 링크 유지(개요 blank 금지).
- 도메인 누락(스키마상 불가지만 방어) → 해당 배지 skip, 크래시 금지.

# Failure Scenarios

- health fetch 실패가 개요 전체를 blank → AC가 degrade-note + 링크 유지 단언.
- "전체 보기"가 잘못된 경로 → AC가 href=`/dashboards/health` 단언.
- 요약 카드가 개요를 드릴-인으로 바꿔버림(범위 오해) → 네비 미변경 단언.

# Definition of Done

- [ ] 요약 카드 + "전체 보기" 링크 동작; tone 분류 + degrade 정확
- [ ] vitest + tsc green, 무회귀; scope = console-web only
- [ ] Acceptance Criteria 충족
- [ ] Ready for review
