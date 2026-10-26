# Task ID

TASK-PC-FE-375

# Title

개요 «실적» 일별 막대에 마우스를 올리면 그 날의 값(건수·금액)이 보이게 한다

# Status

done

# Owner

frontend

# Task Tags

- code
- console
- ui

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet (공용 컴포넌트 한 곳의 CSS 툴팁 + 단위 테스트)
> Drafted 2026-10-26 UTC. 출처: 소유자 요청 «개요 실적 막대에 마우스를 올리면 그 막대가 몇 건인지 알 수 있게».

# Dependency Markers

- 선행 없음. 관련: `TASK-MONO-806`(«실적» 스트립 도입), `TASK-PC-FE-374`(샘플 막대 밀도).

# Goal

6개 도메인 개요(ecommerce · wms · scm · erp · finance · iam)의 «실적» 일별 추이 막대에 마우스를 올리면 그 날짜와 값이 막대 위에 바로 보인다. 지금은 값이 스크린리더 전용 텍스트(`sr-only`)에만 있어 눈으로는 막대 높이만 비교할 수 있다.

# Scope

## In Scope

- **AC-0 사실:** 막대는 `projects/platform-console/apps/console-web/src/shared/ui/PerformanceStrip.tsx` 의 `TrendChart` 한 곳에서 그린다(6개 도메인 개요 공용). 서버 컴포넌트이고 클라이언트 상태가 없다. 값 텍스트는 `formatMetricValue`(금액=₩ 표기, 수량=정수)로 이미 만든다. Tailwind 3.4.
- **1.** `TrendChart` 의 각 날짜 열(`<li>`)에 hover 시 나타나는 툴팁을 단다 — 날짜 · 지표 이름 · 값(예: `9/14 주문 수 3`, `9/14 매출 ₩48,000`). 순수 CSS(`group` / `group-hover`)로 하고 클라이언트 컴포넌트로 바꾸지 않는다.
- **2.** hover 대상은 막대 자체가 아니라 날짜 열 전체 — 값 0 이거나 2% 짜리 막대도 올릴 수 있게.
- **3.** 툴팁은 `aria-hidden`(같은 정보가 이미 `sr-only` 로 있다 — 스크린리더에 두 번 읽히지 않게). 카드 가장자리 열에서 잘리지 않게 한다(차트의 `overflow-x-auto` 가 툴팁을 자르면 위치/오버플로를 조정).
- **4.** `tests/unit/performance-strip.test.tsx` 에 툴팁 텍스트(금액·수량 각 1건)와 `aria-hidden` 단언을 추가한다.

## Out of Scope

- WMS 처리량 카드(`WmsThroughputChart`) 등 «실적» 스트립 밖의 차트.
- 키보드 포커스로 툴팁 띄우기(열이 날짜 수만큼 탭 정지를 늘린다. 값은 `sr-only` 로 이미 접근 가능).

# Acceptance Criteria

- **AC-1** «실적» 막대의 날짜 열에 hover 하면 `날짜 지표명 값` 툴팁이 보이고, 떼면 사라진다(값 0 인 날 포함).
- **AC-2** 툴팁은 `aria-hidden="true"` 이고 기존 `sr-only` 텍스트(`10/1: ₩410,000` 형식)는 그대로다.
- **AC-3** 첫·마지막 열의 툴팁이 카드 밖으로 잘리지 않는다.
- **AC-4** console-web `lint` · `tsc` · `performance-strip.test.tsx` 초록, CI 프런트 단위·E2E smoke 초록.

# Related Specs

- 없음(표시 방식만 바뀐다).

# Related Contracts

- 없음.

# Edge Cases

- 월간 창 30일+ 열은 폭이 좁다 — 툴팁은 열 폭과 무관하게 한 줄(`whitespace-nowrap`).
- 통화별 지표(`amount:KRW`)는 `metricLabel` 로 이름을 만든다.

# Failure Scenarios

- 툴팁 텍스트를 `sr-only` 와 똑같이 만들면 `getByText` 가 두 개를 찾아 기존 테스트가 깨진다 — 형식을 다르게(지표명 포함) 한다.
- `overflow-x-auto` 가 세로 넘침도 잘라 툴팁이 안 보일 수 있다 — 툴팁은 막대 영역(`h-24`) 안쪽 위에 두어 넘치지 않게 한다.

# Implementation Notes (2026-10-26 UTC)

- `TrendChart` 날짜 열 `<li>` 에 `group`, 막대 영역에 `relative`, 툴팁 `<span aria-hidden>` 은 `invisible … group-hover:visible`(막대 영역 안 `top-0`). 앞쪽 절반 열은 `left-0`(오른쪽으로 펼침), 뒤쪽 절반은 `right-0`. hover 한 막대는 `group-hover:bg-primary` 로 진해진다. 서버 컴포넌트 유지.
- AC-1·AC-3 실측(로컬 `next dev`, 샘플 방문자, Playwright hover): `/ecommerce?period=month` 15열 — 첫 열 `9/1 매출 ₩0` 툴팁 x 277..364 · 끝 열 `9/15 매출 ₩28,000` x 1101..1227, 차트 x 277..1227 안. `/scm?period=week` 2열 — `9/14 발주 건수 1`·`9/15 발주 건수 1`, 둘 다 차트 안. hover 전 `isVisible=false` → 후 `true`.
- AC-2·AC-4: `performance-strip.test.tsx` 에 툴팁 문구(금액·수량)·`aria-hidden`·정렬 클래스·`sr-only` 비중복 단언 추가. `performance-strip` + `f5-chart-geometry-exception` 18건 rc=0 · `tsc` rc=0 · `lint` rc=0.
