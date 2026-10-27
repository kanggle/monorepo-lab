# Task ID

TASK-PC-FE-381

# Title

운영자 통합 개요 «도메인 상태 요약» 에 정상 · 주의 · 점검 불가 비율 막대 한 줄

# Status

done

# Owner

frontend

# Task Tags

- code
- console
- ui

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet (기존 `StackedBar` 재사용 · 컴포넌트 1곳 · 테스트)
> Drafted 2026-10-27 UTC. 출처: 운영자 개요 그래프 후보 3번 — 소유자 채택 2026-10-27.

# Dependency Markers

- 선행 없음. 관련: `TASK-PC-FE-061`(도메인 상태 요약 카드), `TASK-PC-FE-357`(공용 `InlineBar` · `StackedBar`), `TASK-PC-FE-379`(같은 화면의 headline 막대).

# Goal

`/dashboards/overview` 의 «도메인 상태 요약» 이 개수 텍스트(«정상 N · 주의 N · 점검 불가 N / 6개 도메인») 위나 아래에 같은 세 값을 한 줄 비율 막대로 보인다.

# Scope

## In Scope

- **AC-0 사실 (기안 중 확인, 2026-10-27 UTC):**
  - `src/features/domain-health/components/DomainHealthSummaryCard.tsx:87-110` `SummaryBody` 가 `healthTone(card)` 로 `healthy` · `attention` · `unknown` 을 세서 텍스트로만 보인다(`domain-health-summary-counts`). 배지는 점 색 `bg-green-500` · `bg-red-500` · `bg-muted-foreground/40`(`:29-33`).
  - 공용 `shared/ui/InlineBar.tsx` `StackedBar({ segments, expected, testid })` — `aria-hidden`, 숫자는 텍스트로 따로, 톤은 `StatusTone`(`success` · `danger` · `neutral` …). 같은 모양의 선례: `ecommerce-ops/components/OrderStatusBar.tsx:49`, `erp-ops/components/ApprovalStatusBar.tsx:53`.
  - 상태 색은 «좋음/나쁨» 이 분명하다(정상 = 좋음) — `TASK-PC-FE-379` 의 «방향 색 금지» 는 증감 지표 얘기이고 여기에 해당하지 않는다. 배지와 같은 의미 색을 쓴다.
- **1.** `SummaryBody` 에 `StackedBar` 한 줄: `healthy → success`, `attention → danger`, `unknown → neutral`, `expected = cards.length`. 개수 텍스트 · 배지 · testid 불변.
- **2.** 시험: 기존 요약 카드 시험 옆에 — 세 구간 폭(손으로 센 값) · `aria-hidden` · 톤 · 전부 정상(한 색 100%) · 불러올 수 없음 상태엔 막대 없음.

## Out of Scope

- `/dashboards/health` 전체 화면, 상태 판정 로직(`healthTone`), 배지.

# Acceptance Criteria

- **AC-1** 요약 카드가 정상 · 주의 · 점검 불가 세 구간 비율 막대를 보이고, 각 폭 = 개수 / 도메인 수(손으로 센 값으로 시험).
- **AC-2** 막대는 `aria-hidden` 이고 개수 텍스트 · 배지는 그대로다. 색은 배지와 같은 의미(정상 초록 · 주의 빨강 · 점검 불가 회색).
- **AC-3** «불러올 수 없음» 상태(`health === null`)에는 막대가 없다.
- **AC-4** console-web `lint` · `tsc` · vitest(대상 + `domain-health` 계열) 초록, CI 프런트 단위 · E2E smoke 초록.
- **AC-5** 배포 뒤 운영 `https://console.hubwang.com/dashboards/overview` 샘플 방문자 화면에서 막대가 보인다(Playwright 실측).

# Related Specs

- 없음(표시만).

# Related Contracts

- 없음.

# Edge Cases

- 도메인 6개 전부 같은 상태 → 한 구간 100%.
- 0 개인 구간은 그리지 않는다(`StackedBar` 가 0% 구간을 생략).

# Failure Scenarios

- 새 색 체계를 만든다 → `StatusTone` 팔레트 재사용 규칙 위반.
- 막대만 남기고 개수 텍스트를 지운다 → 색·길이만으로 읽히는 접근성 실패.

# Implementation Notes (2026-10-27 UTC)

- `DomainHealthSummaryCard.tsx` `SummaryBody` — 개수 텍스트 바로 아래 `StackedBar`(`domain-health-summary-bar`, `expected = cards.length`, 구간 `healthy → success` · `attention → danger` · `unknown → neutral`). 개수 텍스트 · 배지 · 기존 testid 불변.
- **AC-1 · AC-2 · AC-3** `domain-health-summary.test.tsx` 2셀 추가 + 1단언: 2/2/1 of 5 → 폭 `40%` · `40%` · `20%`(손 계산) · `aria-hidden` · 색 클래스(`bg-green-500` · `bg-red-500` · `bg-muted-foreground`) · 개수 텍스트 유지 / 전부 정상 → `healthy` 100% · 빈 구간 미렌더 / `health === null` → 막대 없음.
- **AC-4 (로컬)** vitest `domain-health-summary` · `overview-catalog-fold` · `overview-page-parallel` 18건 rc=0 · `tsc` rc=0 · `lint` rc=0.
- **AC-5 열림** — 배포 뒤 운영 샘플 실측.

---

## CORRECTION — AC-5 닫힘 · done (2026-10-27 UTC, 운영 실측)

- **AC-5 ✅** — Vercel `Production – kanggle-console` 배포 id `7358443701`, sha `eb856b5ac…`(= 이 티켓 squash) `success`. 운영 `https://console.hubwang.com/dashboards/overview` 샘플 방문자(Playwright): `domain-health-summary-bar` 보임 · `aria-hidden="true"` · 구간 `healthy 100%`(샘플 6개 도메인 전부 정상 — 개수 텍스트 «정상 6 · 주의 0 · 점검 불가 0 / 6개 도메인» 과 일치), 1280px · 390px 모두 가로 넘침 없음(`scrollWidth` = 뷰포트).
- 4차원: impl PR #4588 `state=MERGED` · squash `eb856b5ac` ⊂ `origin/main` · 머지 시점 `statusCheckRollup` FAILURE 0 · AC-1~5 전부 닫힘 ⇒ **done**.
