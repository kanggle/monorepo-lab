# Task ID

TASK-MONO-806

# Title

`ADR-MONO-084` P1 — 실적 지표 공용 바탕: `KstPeriodBounds` 에 직전 구간 · 일별 경계 · 공용 응답 DTO / 콘솔 공용 «실적 줄» 컴포넌트

# Status

review

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

- [x] **AC-0** — 재측정: `KstPeriodBounds` 의 소비자 전수(지금 E-Commerce 7 서비스) · 콘솔 `kst-period.ts` 의 소비자 · 차트 라이브러리 현황(콘솔이 이미 쓰는 것 — 새 의존성을 들이지 않는다) · 공용 응답 모양을 적을 문서 위치. 결과를 이 파일에 기록. — ✅ § AC-0 기록.
- [x] **AC-1** — 경계 시험(🔴 `Clock` 주입, 호스트 시간대 무관): KST 00:00 직전/직후 · 일요일 23:59 → 월요일 00:00 · 3월 30일의 `month` 직전 구간이 2월 말일에서 멈춤 · 1월의 직전 달 = 전년 12월. 각 경우 현재/직전 `from`/`to` 와 날짜 목록 길이. — ✅ 공용 픽스처 8 케이스(`platform/contracts/fixtures/performance-metrics-windows.json`) × 주 · 월 · 시계 시간대 3 (`KstPeriodBoundsWindowsFixtureTest`). 일요일 → 월요일은 «월요일 00:00:30» 케이스로 잡았다(새 주가 하루짜리). 🔴 bite: 말일 멈춤을 끄면 3월 30일 케이스가 빨강(`expected 2026-03-01T00:00:00+09:00 but was 2026-03-02T14:00:00+09:00`) → 복원 후 초록.
- [x] **AC-2** — 🔴 `TZ=UTC` 와 `TZ=Asia/Seoul` 두 환경에서 같은 시험이 같은 답을 낸다(이 저장소 작성 호스트 = KST, CI = UTC — 로컬 초록 · CI 빨강이 나는 자리). — ✅ Java: `JAVA_TOOL_OPTIONS=-Duser.timezone=UTC` · `=Asia/Seoul` 두 번 `:libs:java-common:test` rc=0(13 시험) + 시험 안에서 JVM 기본 시간대 4종 전환. 콘솔: `kst-metrics-windows.test.ts` 가 `TZ` 3종(UTC · Asia/Seoul · America/Los_Angeles)을 돌린다 · 전체 스위트는 § 검증.
- [x] **AC-3** — 기존 `KstPeriodBounds` 메서드의 결과가 변경 전후 같다(대조군) · E-Commerce `/summary` 시험 초록. — ✅ `KstPeriodBoundsTest.existing_accessors_are_unchanged`(고정값 8개, 재계산 아님). 기존 메서드 본문은 한 줄도 안 바꿨다(diff = 추가만). E-Commerce 7 서비스 `/summary` 시험 = § 검증.
- [x] **AC-4** — 콘솔 컴포넌트 시험: 정상 · 증감 0 · 직전 값 0(증감 % 대신 «—») · 음수 증감 · 칸 실패 · 401. 숫자 · 돈 표기. — ✅ `performance-format.test.ts`(증감 반올림 · 0 · 직전 0 · 음수 · 2^53 초과 정밀도 · 돈/건수 표기) · `performance-metrics-cell.test.ts`(숫자 값 거부 · 중복 키 · 단위/통화 불일치 · 403 · 5xx · 네트워크 · 401 재던짐) · `performance-strip.test.tsx`(KPI · 창 문구 · 차트 · 기간 링크 · 실패 칸에 숫자 0개 · 권한 없음 · 빈 목록). 🔴 bite: `BigInt(Number(current))` 를 넣으면 F5 가드가 파일:줄로 빨강 + 정밀도 시험 빨강 → 복원.
- [x] **AC-5** — 콘솔 경계(`kst-period.ts`)와 백엔드 경계가 같은 고정 시각에서 같은 날짜 목록을 낸다. — ✅ 두 쪽이 **같은 픽스처 파일**을 읽는다(Java `../../platform/…` · 콘솔 `../../../../../../platform/…`, 둘 다 파일 존재 · 케이스 ≥ 8 을 먼저 단언). 🔴 bite: 콘솔의 말일 멈춤을 끄면 3월 30일 케이스가 시간대 3종 모두 빨강 → 복원.

## AC-0 기록 (2026-10-23 UTC, origin/main `1f4308cc6`)

- `KstPeriodBounds` 소비자 = E-Commerce 7 서비스(notification · order · product ×2(상품 · 셀러) · promotion · shipping · user)의 `…QueryService` / `…Service` + `PeriodSummary` javadoc. 모두 `now()` · `*StartInstant/Local()` · `now*()` 만 쓴다 → 이 메서드들은 **추가만** 하고 본문을 안 바꾼다.
- 콘솔 `kst-period.ts` 소비자 = `features/wms-ops/api/overview-state.ts` + 시험 2개. 🔵 ADR-084 D3 «콘솔은 한 곳» 에 따라 `features/wms-ops/api/` → **`shared/lib/kst-period.ts`** 로 옮기고 import 3곳을 고쳤다(동작 무변경).
- 차트 라이브러리 = **없다**(`package.json` 에 0). 기존 차트(`WmsThroughputChart` · `RankingBarChart` · `FxRateHistoryChart`)는 전부 CSS/flex 막대 → 같은 방식, 새 의존성 0.
- 공용 모양의 자리 = **`platform/contracts/performance-metrics-contract.md`**(형제: `notification-inbox-contract.md` — 도메인 무관 shape 만 공유, 경로 · 인증은 도메인 소유). `platform/README.md` 표 · `entrypoint.md` `api` 행에 등록.
- 🔴🔴 **ADR 예시와 다른 점 — 값은 JSON 숫자가 아니라 정수 문자열.** 콘솔의 F5 규칙(`console-integration-contract.md` § 2.4.7 · `shared/lib/money.ts`): 돈은 문자열로만 그리고 `Number(` · `parseFloat(` · `parseInt(` 는 `shared/lib/chart-scale.ts` 한 곳 한 번만 허용(`f5-chart-geometry-exception.test.ts`). ADR D2 예시(`"current": 12340000`)대로 숫자로 내리면 콘솔이 받자마자 규칙을 어긴다. ⇒ 계약은 **모든 값을 정수 문자열**로(건수 포함 — 파서 하나), 증감 %는 `BigInt` 로 정확히 계산, 막대 높이만 `chartMagnitude`. ADR History 에 «구현 메모» 를 덧붙였다(본문 무변경). 새 콘솔 파일 3개를 F5 가드 목록에 넣었다.
- `libs/java-common` 에 Jackson 이 없다 → DTO 는 어노테이션 없는 record(문자열 · record · `null` 만). 시험 전용 `jackson-databind` 하나를 더해 «기본 ObjectMapper 로 계약 모양이 나온다» 를 단언한다.
- 증감 색: 🔵 **색을 칠하지 않는다**(화살표 ▲▼ 와 문장만). 매출 증가는 좋고 로그인 실패 증가는 나쁜데 공용 컴포넌트는 그 뜻을 모른다.
- 기간 전환 = 페이지가 만든 링크 둘(`?period=`). 서버 컴포넌트 · 클라이언트 상태 0 · 새로고침 후에도 유지.

## 검증 (2026-10-23 UTC, 로컬 · Windows 호스트)

| 무엇 | 명령 | 결과 |
|---|---|---|
| Java 공용 (UTC) | `JAVA_TOOL_OPTIONS=-Duser.timezone=UTC ./gradlew :libs:java-common:test --rerun-tasks` | rc=0 |
| Java 공용 (KST) | 같은 명령, `-Duser.timezone=Asia/Seoul` | rc=0 · 새 시험 3 클래스 13 / 실패 0 |
| E-Commerce `/summary` (AC-3 대조군) | notification · order · product · promotion · shipping · user `*Summary*` | rc=0 · 9 스위트 26 / 실패 0 |
| 콘솔 새 · 영향 시험 (UTC · KST) | `TZ=UTC` · `TZ=Asia/Seoul` `vitest run` 7 파일 | 둘 다 rc=0 · 53 / 53 |
| 콘솔 타입 | `tsc --noEmit` | rc=0 |
| 콘솔 린트 | `next lint` | rc=0 (기존 경고만) |
| 콘솔 전체 스위트 | `TZ=UTC vitest run --maxWorkers=2 --minWorkers=1` | 🔴 rc=1 · 5258 / 5260 — 아래 |

🔴 **콘솔 전체 스위트 빨강은 이 변경 탓이 아니다 (실측).** 이 호스트에서 전체 스위트는 돌 때마다 **다른** 2~7개가 `Test timed out in 5000ms` / jsdom `Not implemented: navigation` 으로 빨강이었다(1회차: OperatorsScreen · TenantsScreen 7 · 2회차: LedgerOpsScreen · ecommerce-idempotency 2). 그 파일들은 이 변경의 모듈을 하나도 import 하지 않는다(grep 0). 🔴🔴 대조군: **같은 5 파일 명령을 `main`(`1f4308cc6`) 체크아웃에서 돌려도 4개가 타임아웃으로 빨강** — 호스트 부하 flake 다(메모리 `env_console_web_vitest_flake_unhandled_error` ④). 같은 파일을 단독으로 다시 돌리면 통과했다가 다시 빨강이 되기도 했다(같은 코드 · 다른 결과). ⇒ 판정은 **CI(Linux)의 Frontend unit 잡**에 맡긴다. ⚪ 전체 스위트를 `TZ=Asia/Seoul` 로는 돌리지 않았다(같은 flake 로 판정이 안 나고, 시간대에 걸린 새 시험은 위 7 파일 KST 실행이 덮는다).

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
