# Task ID

TASK-PC-FE-379

# Title

운영자 통합 개요 카드의 «이번 주 실적» 에 «이번 주 vs 지난 주» 막대 두 개 — 증감 숫자만 있던 자리를 한눈에 비교

# Status

ready

# Owner

frontend

# Task Tags

- code
- console
- ui

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet (카드 headline 렌더러 1곳 + 순수 막대 함수 + 테스트 — 계약·서버·호출 불변)
> Drafted 2026-10-27 UTC. 출처: 소유자 질문 «운영자 통합 개요에도 시각적으로 도움이 될 그래프가 있을까» → 후보 3개 중 권장안(1번) 소유자 채택 2026-10-27.

# Dependency Markers

- 선행 없음. 관련: `TASK-PC-FE-368`(카드 headline 도입 — ADR-MONO-084 D7), `TASK-PC-FE-375`(«실적» 막대 hover), `TASK-MONO-806`(공용 «실적» 스트립).
- 검토한 대안(이 티켓 밖): 카드별 일별 스파크라인 — headline 이 `series` 를 싣지 않아(계약 § 2.4.9.1 «The daily `series` is not carried») 계약·조합 서버 변경이 필요하고, 주간 창이 요일에 따라 1~7일이라 기간 결정(ADR-MONO-084 D7)이 먼저다. 도메인 상태 요약 비율 막대 — 소유자가 이번에 고르지 않음.

# Goal

`/dashboards/overview` 의 각 도메인 카드 headline(이번 주 대표 실적 · 직전 같은 길이 구간)이 숫자·증감 텍스트에 더해 **두 막대(이번 주 · 지난 주)** 를 보인다. 막대는 같은 지표의 두 값을 같은 눈금으로 그려 «늘었나 줄었나» 를 한눈에 보이게 하고, 색으로 좋고 나쁨을 말하지 않는다.

# Scope

## In Scope

- **AC-0 사실 (기안 중 확인, 2026-10-27 UTC):**
  - 렌더러: `src/features/operator-overview/components/DomainCardHeadline.tsx` — `ok` headline 의 각 metric 마다 라벨 · 값(`formatMetricValue(m.current)`) · `formatDelta` · `직전 {previous}` 텍스트. 막대 없음. 주석이 «Direction is never colour-coded: a rise is good for revenue and bad for failed logins, and the card does not know which».
  - 데이터: `shared/lib/operator-overview-headline.ts` `OkHeadlineSchema.metrics[]` = `PerformanceMetric`(`current` · `previous` = **정수 문자열**, F5). 돈 지표(scm `poAmount:<CUR>` · finance `transferAmount:<CUR>`)는 **통화별 한 항목** — 통화끼리 합치지 않는다.
  - 계약 `specs/contracts/console-integration-contract.md` § 2.4.9.1 «Card headline metric»(`:3268-`)은 와이어와 실패 문구만 정하고 그리는 방식은 정하지 않는다 ⇒ **계약 변경 없음**.
  - F5 가드 `tests/unit/f5-chart-geometry-exception.test.ts`: 숫자 강제 변환은 `shared/lib/chart-scale.ts` 에서만 허용. 기존 막대(`performance-format.ts` `seriesBars`)는 `chartMagnitude` 로 기하만 계산한다 — 같은 길로 간다.
- **1. 순수 함수:** `shared/lib/performance-format.ts` 에 두 값 비교 막대 기하 함수(예: `comparisonBars(current, previous)` → 각 막대 %)를 추가. `chartMagnitude` 사용, 둘 다 0 · 한쪽 0 · 음수/읽을 수 없는 값 · 아주 큰 값(정수 문자열)에서 NaN/Infinity/음수 폭 없음, 0 보다 큰 값은 보이는 최소 폭(기존 `seriesBars` 의 2% 규칙과 같게).
- **2. 렌더러:** `DomainCardHeadline` 의 각 metric 아래 두 막대 — «이번 주» · «지난 주» 라벨과 함께. 막대는 `aria-hidden`(숫자는 이미 텍스트로 있다 — `InlineBar`·`PerformanceStrip` 규칙), **같은 중립 색 계열**(이번 주 진하게 · 지난 주 옅게), 증감 방향으로 색을 바꾸지 않는다. 통화별 metric 은 각자 자기 막대 쌍(통화끼리 한 눈금에 섞지 않는다).
- **3. 시험:** 순수 함수 단위(정상 · 둘 다 0 · 한쪽 0 · 이전 > 현재 · 큰 값 · 읽을 수 없는 값) · `headline-metric.test.tsx` 계열에 렌더 단언(막대 두 개 · `aria-hidden` · 기존 텍스트·testid 불변 · `degraded`/`forbidden` headline 에는 막대 없음).

## Out of Scope

- 일별 스파크라인(위 Dependency Markers — 계약·기간 결정 선행).
- 도메인 상태 요약 비율 막대.
- headline 지표 선택 · 기간(ADR-MONO-084 D7) · 조합 서버 · 계약 변경.
- 6개 업무 지표를 한 그래프에 나란히 그리기(단위가 달라 길이 비교가 거짓말).

# Acceptance Criteria

- **AC-1** `ok` headline 의 각 metric 이 «이번 주» · «지난 주» 막대 두 개를 보이고, 두 막대는 그 metric 의 `current`·`previous` 를 **같은 눈금**으로 그린다. 기존 값·증감·직전 텍스트와 testid 는 그대로다.
- **AC-2** 막대는 `aria-hidden` 이고 색이 증감 방향에 따라 바뀌지 않는다(같은 색 계열 두 단계).
- **AC-3** 막대 기하는 순수 함수로 분리돼 단위 시험이 있고, 둘 다 0 · 한쪽 0 · 읽을 수 없는 값 · 큰 정수 문자열에서 NaN/Infinity/음수 폭을 내지 않는다. F5 가드 초록(새 강제 변환 없음).
- **AC-4** `degraded`·`forbidden`·부재 headline 에는 막대가 없다(기존 문구 그대로).
- **AC-5** console-web `lint` · `tsc` · vitest(대상 + `headline-metric` · `f5-chart-geometry-exception` · `tests/unit/sample*`) 초록, CI 프런트 단위 · E2E smoke 초록.
- **AC-6** 배포 뒤 운영 `https://console.hubwang.com/dashboards/overview` 샘플 방문자 화면에서 6개 카드의 headline 막대가 보인다(Playwright 실측, 데모 창 불필요).

# Related Specs

- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4.9.1 «Card headline metric» — 읽기 전용(변경 없음).

# Related Contracts

- 위와 같음.

# Edge Cases

- **둘 다 0** — 막대 둘 다 0 폭(«비교할 것 없음» 이지 오류가 아니다).
- **한쪽만 0** — 0 쪽은 0 폭, 다른 쪽은 100%.
- **돈 지표 통화 여러 개** — 통화마다 자기 막대 쌍.
- **샘플 방문자** — 같은 렌더러가 샘플 headline 으로 그려진다(AC-6).

# Failure Scenarios

- 증감에 초록/빨강을 칠한다 → 로그인 실패 증가를 «좋음» 으로 보이게 하는 거짓말(렌더러 주석이 금지).
- 정수 문자열을 `Number()` 로 직접 바꾼다 → F5 가드 빨강(허용 파일 밖 강제 변환).
- 6개 카드 막대를 한 눈금으로 맞춘다 → 단위가 다른 지표의 길이 비교. 눈금은 **metric 하나 안에서만**.
