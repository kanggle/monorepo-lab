# Task ID

TASK-MONO-806

# Title

`ADR-MONO-084` P1 — 실적 지표 공용 바탕: `KstPeriodBounds` 에 직전 구간 · 일별 경계 · 공용 응답 DTO / 콘솔 공용 «실적 줄» 컴포넌트

# Status

ready

# Owner

monorepo

# Task Tags

- code
- lib
- platform-console
- test

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus (기간 경계 계산 · 공용 계약의 모양 확정 — 뒤의 여섯 단계가 이 모양을 그대로 쓴다)
> 기안 2026-10-23 UTC — `ADR-MONO-084` ACCEPTED (정확형, OD 기본값).

# Dependency Markers

- **선행**: 없음. `ADR-MONO-084` ACCEPTED.
- **후속**: `TASK-MONO-807`~`812`(P2~P7)이 전부 이 티켓의 DTO · 경계 · 콘솔 컴포넌트를 쓴다. 🔴 P2~P7 은 이 티켓이 `main` 에 머지된 뒤 착수한다.

# Goal

도메인 여섯 곳이 같은 실적 응답(ADR-084 D2)을 같은 경계(D3)로 내려주고, 콘솔이 같은 컴포넌트 하나로 그리게 하는 공용 바탕을 만든다. 도메인 지표 자체는 이 티켓에서 만들지 않는다.

# Scope

## In Scope

- `libs/java-common` `com.example.common.time.KstPeriodBounds` 확장: `period=week|month` 의 현재 구간 · **같은 길이 직전 구간**(짧은 달은 그 달 말일에서 멈춤, ADR-084 D2) · 현재 구간의 **KST 날짜 목록**(일별 0 채우기용). 기존 메서드와 동작은 바꾸지 않는다(E-Commerce `/summary` 7곳이 쓴다).
- `libs/java-common` 공용 응답 DTO: `period` · `timezone` · `current{from,to}` · `previous{from,to}` · `metrics[]{key, unit, currency?, current, previous}` · `series[]{date, values}`. 기술 DTO 만이다 — 지표 `key` 목록 같은 도메인 내용은 넣지 않는다(HARDSTOP-03).
- 계약: 공용 응답 모양을 한 곳에 적는다(위치는 AC-0 에서 정한다 — 후보 `platform/` 의 API 규약 문서). 도메인 계약은 그 절을 인용만 한다.
- 콘솔 `console-web`: 공용 «실적 줄» 컴포넌트 — 기간 선택(주 · 월, OD-4 기본값) · KPI 카드(현재 값 · 직전 대비 증감 % · 증감 방향) · 일별 추세 차트 하나 · 칸별 실패(그 줄만 «불러올 수 없음», 401 → 재로그인). 응답 파서(zod) 하나. 돈 표기는 `currency` 로 한다.
- 콘솔 `kst-period.ts` 와 백엔드 경계가 같은 입력에 같은 답을 내는지 대조하는 시험.

## Out of Scope

- 도메인별 `…/metrics` 엔드포인트 · 지표 정의(P2~P7).
- 도메인 개요 화면에 붙이는 일(P2~P7 이 각자 붙인다).
- 테넌트별 시간대(ADR-084 D3).

# Acceptance Criteria

- [ ] **AC-0** — 재측정: `KstPeriodBounds` 의 소비자 전수(지금 E-Commerce 7 서비스) · 콘솔 `kst-period.ts` 의 소비자 · 차트 라이브러리 현황(콘솔이 이미 쓰는 것 — 새 의존성을 들이지 않는다) · 공용 응답 모양을 적을 문서 위치. 결과를 이 파일에 기록.
- [ ] **AC-1** — 경계 시험(🔴 `Clock` 주입, 호스트 시간대 무관): KST 00:00 직전/직후 · 일요일 23:59 → 월요일 00:00 · 3월 30일의 `month` 직전 구간이 2월 말일에서 멈춤 · 1월의 직전 달 = 전년 12월. 각 경우 현재/직전 `from`/`to` 와 날짜 목록 길이.
- [ ] **AC-2** — 🔴 `TZ=UTC` 와 `TZ=Asia/Seoul` 두 환경에서 같은 시험이 같은 답을 낸다(이 저장소 작성 호스트 = KST, CI = UTC — 로컬 초록 · CI 빨강이 나는 자리).
- [ ] **AC-3** — 기존 `KstPeriodBounds` 메서드의 결과가 변경 전후 같다(대조군) · E-Commerce `/summary` 시험 초록.
- [ ] **AC-4** — 콘솔 컴포넌트 시험: 정상 · 증감 0 · 직전 값 0(증감 % 대신 «—») · 음수 증감 · 칸 실패 · 401. 숫자 · 돈 표기.
- [ ] **AC-5** — 콘솔 경계(`kst-period.ts`)와 백엔드 경계가 같은 고정 시각에서 같은 날짜 목록을 낸다.

# Related Specs

- `docs/adr/ADR-MONO-084-domain-overview-performance-metrics.md` D2 · D3 · OD-4
- `platform/shared-library-policy.md` (기술 DTO 만 — 도메인 내용 금지)
- `projects/platform-console/specs/services/console-web/architecture.md`

# Related Contracts

- 공용 실적 응답 모양 — 이 티켓이 새로 적는다(AC-0 위치).
- `projects/ecommerce-microservices-platform/specs/contracts/http/order-api.md` § `GET /api/admin/orders/summary` (불변 대조군)

# Edge Cases

- 직전 구간 값이 0 → 증감 % 는 정의되지 않는다. 0 으로 나누지 않고 «—» 로 그린다.
- 지금이 월요일 00:00:30 → `week` 현재 구간 길이 30초, 직전 구간도 30초.
- 날짜 목록은 현재 구간이 하루 안이어도 1개 이상이다.

# Failure Scenarios

- 호스트 시간대에 기댄 시험 → KST 호스트에서 초록, CI 에서 빨강(AC-2 가 막는다).
- DTO 에 도메인 지표 키를 상수로 넣는다 → 공용 라이브러리에 도메인 내용(HARDSTOP-03).
- 콘솔이 실패 칸을 0 으로 그린다 → 「매출 0」 으로 읽힌다. 실패는 숫자가 아니라 «불러올 수 없음» 이다.
