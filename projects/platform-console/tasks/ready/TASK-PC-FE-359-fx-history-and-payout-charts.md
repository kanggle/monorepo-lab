# Task ID

TASK-PC-FE-359

# Title

그래프 2차 묶음 ① — 원장 환율 추이 선 · 정산 기간별 판매자 지급액 막대 · 원장 기간별 전표 수 막대 (문자열 금액은 «그래프 폭 계산» 한 함수에서만 숫자로)

# Status

ready

# Owner

frontend

# Task Tags

- code
- test
- spec

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus (금액 규칙 F5 의 스펙 예외 · 라이브러리 없는 SVG 선 그래프)
> 기안 2026-10-21 UTC — 콘솔 그래프 조사(`TASK-PC-FE-357` · `358` 과 같은 조사)의 2차 묶음. 소유자 결정 2026-10-22 KST: **금액 · 환율 문자열은 그래프 폭 계산에만 숫자로 바꾸는 예외 허용**(화면의 금액 글자는 원문 그대로).

# Dependency Markers

- 선행 없음. 같은 2차 묶음: `TASK-PC-FE-360`(독립). 1차 = `TASK-PC-FE-357` · `358`(review).
- 🔴 스펙 먼저: F5 money 규칙(`projects/platform-console/specs/services/console-web/architecture.md` — 금융 표면 «F5 money string 전달 (Number 강제 안 함)») 에 예외 한 줄을 더한 뒤 구현.

# Goal

| # | 화면 | 지금 | 더하는 것 | 데이터 |
|---|---|---|---|---|
| 1 | `/ledger` 환율 탭 «이력» | 표(`FxRateHistoryTable.tsx`) | 그 표 위에 **환율 추이 선**(SVG · 라이브러리 없음) | `GET …/fx-rates/{foreignCurrency}/history` 의 `quotes[] {rate, asOf, …}` — 시계열(최신순, 기본 50 · 상한 500). `rate` = 소수 문자열 |
| 2 | 정산 기간 상세 `/ecommerce/settlements/periods/[id]` | 지급 표(`PeriodPayoutsTable.tsx`) | 판매자별 **지급액 막대**(`payableNetMinor`) + 지급 상태 표시 | `Payout[]` — 🔴 첫 페이지 20건만 받는다 ⇒ «전체 N건 중 이 페이지 M건» 을 밝힌다(또는 AC-0 이 더 큰 페이지 크기를 고른다) |
| 3 | `/ledger` 기간 탭 | 표 | 기간별 **전표 수 막대**(`entryCount` — 숫자 필드, 예외 불필요) | 기간 목록 — 페이지 20 ⇒ «최근 N개 기간» 을 밝힌다 |

# Scope

## In Scope

- **AC-0 측정**: ① F5 규칙의 정본 위치 전부(스펙 · 계약 § 2.4.7 · 시험의 grep 단언 — `tests/unit/ledger-api.test.ts` · `finance-api.test.ts` · `ledger-account-api.test.ts` · `ledger-statement-api.test.ts` 가 무엇을 막는가) ② 이커머스 정산의 금액도 같은 규칙인가(`minorToWon` 이 무엇을 쓰는가) ③ 환율 이력 화면이 지금 몇 건을 받는가(`limit`) ④ 지급 목록 페이지 크기 · `totalElements` ⑤ 샘플 고정값에 세 자리 값이 있는가.
- **스펙 예외 (AC-1)**: «그래프의 기하(막대 폭 · 선 높이)를 계산하는 **한 함수**(예 `shared/lib/chart-scale.ts` 의 `chartMagnitude(decimalString)`) 만 문자열 금액 · 환율을 `Number` 로 바꾼다. 결과는 화면 글자 · 합계 · 비교 판정에 쓰지 않는다. 정밀도 손실은 픽셀 단위라 무해하다.» + grep 시험의 허용 목록에 그 파일 하나.
- 구현: 357 의 `shared/ui/InlineBar.tsx` 재사용(막대) · 선 = 인라인 SVG(`aria-hidden`, 최소 · 최대 · 최신 값은 글자로 — 원문 문자열) · 숫자 글자 · 기존 testid 유지.
- 시험: 예외 함수(정상 · 음수 · 지수 표기 · 빈 문자열 · 아주 큰 값) · 선 좌표(점 1개 · 모두 같은 값 · 최신순 → 시간순 뒤집기) · 막대 비율 · 페이지 밝힘 문구 · F5 grep 시험이 예외 파일 밖의 `Number(` 를 여전히 문다(bite).

## Out of Scope

- 금액 합계 · 비교를 숫자로 하는 것(예외는 기하만).
- 환율 이력 기본 건수 변경(AC-0 이 바꿀 필요를 보이면 소유자에게 묻는다).
- 감사 로그 그래프(소유자 결정 2026-10-22: 보류).

# Acceptance Criteria

- [ ] **AC-0** — 위 ①~⑤ 를 file:line 과 함께.
- [ ] **AC-1** — 스펙 예외 한 줄 + grep 시험 허용 목록 → 구현.
- [ ] **AC-2** — **사용자에게 보이는 새 문구 목록을 소유자에게 보이고 승인** 받은 뒤 머지.
- [ ] **AC-3** — 시험 위 목록 + 기존 스위트(`--maxWorkers=4 --minWorkers=2`) · `tsc --noEmit` · `next lint` · **`node scripts/check-client-graph-server-only.mjs` · `check-client-graph-backend-origins.mjs`**(로컬, 스테이지 뒤) rc=0. SSR 화면이 바뀌므로 머지 전 브랜치 nightly 콘솔 잡.
- [ ] **AC-4** — 배포(Vercel Production 기록) · 익명 샘플 화면 확인.

# Related Specs

- `projects/platform-console/specs/services/console-web/architecture.md` (F5 money — 갱신 대상)

# Related Contracts

- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4.7 · § 2.4.7.1 (finance · ledger) · 이커머스 정산 절(AC-0 에서 이름 확인)
- `projects/finance-platform/specs/contracts/http/ledger-api.md` § 14.1 (환율 이력 — 읽기만)

# Edge Cases

- 환율 이력 점 1개 → 선 대신 점 하나 · 모든 값 같음 → 가운데 수평선(0 나누기 방지).
- `rate` 가 파싱 안 되는 문자열 → 그 점만 빼고 그린다 · 전부 실패 → 그래프 없이 표만.
- 지급액 음수(되돌림) → 막대는 0 기준 양수만, 음수는 글자로(또는 AC-0 이 정함).
- 통화가 섞인 원장 기간 — 전표 수는 통화와 무관(예외 불필요).

# Failure Scenarios

1. 예외 함수의 결과가 화면 금액 글자로 새어 나가 반올림된 값이 보인다 → 시험이 «글자는 원문» 을 단언한다.
2. 예외가 허용 목록 없이 넓어져 F5 grep 시험이 무력해진다 → bite 시험.
3. 20건 페이지를 전체처럼 그린다 → 밝힘 문구 + 시험.
