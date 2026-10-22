# Task ID

TASK-PC-FE-359

# Title

그래프 2차 묶음 ① — 원장 환율 추이 선 · 정산 기간별 판매자 지급액 막대 · 원장 기간별 전표 수 막대 (문자열 금액은 «그래프 폭 계산» 한 함수에서만 숫자로)

# Status

done

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

- [x] **AC-0** — 위 ①~⑤ 를 file:line 과 함께. (아래 § AC-0 측정)
- [x] **AC-1** — 스펙 예외 한 줄 + grep 시험 허용 목록 → 구현. (스펙 커밋 `61560c806` 먼저 · 기존 가드엔 허용 목록이 없어 새 가드에 둠 — § AC-0 ①)
- [x] **AC-2** — **사용자에게 보이는 새 문구 목록을 소유자에게 보이고 승인** 받은 뒤 머지.
- [x] **AC-3** — 시험 위 목록 + 기존 스위트(`--maxWorkers=4 --minWorkers=2`) · `tsc --noEmit` · `next lint` · **`node scripts/check-client-graph-server-only.mjs` · `check-client-graph-backend-origins.mjs`**(로컬, 스테이지 뒤) rc=0. SSR 화면이 바뀌므로 머지 전 브랜치 nightly 콘솔 잡.
- [ ] **AC-4** — 배포(Vercel Production 기록) · 익명 샘플 화면 확인.

## AC-0 측정 (2026-10-21 UTC)

줄 번호는 착수 시점 `origin/main`(`b1e5a9271`) 기준.

**① F5 규칙의 정본 위치 전부**

| 집 | file:line | 무엇을 말하나 |
|---|---|---|
| 스펙 개요 | `specs/services/console-web/architecture.md:15` | finance F5 = minor-units 문자열, «`amount` 에 `Number()`/`parseFloat`/`parseInt` 없음» |
| 스펙 개요 | `architecture.md:17` | ledger 가 § 2.4.7 F5 를 재사용 |
| 디렉터리 트리 | `architecture.md:119` | finance 프록시 «F5 money string 전달 (Number 강제 안 함)» |
| 디렉터리 트리 | `architecture.md:190` · `:197` | finance-ops · ledger-ops api — «`Number()`/`parseFloat`/`parseInt` 금지·테스트가 grep 검증» (ledger 는 `money`+`exchangeRate`+`baseAmount`) |
| 통합 규칙 | `architecture.md:379` · `:380` | finance «float/JS `Number` 강제 절대 금지» · ledger «`amount`·`exchangeRate` 에 절대 적용 안 됨(`features/ledger-ops/` grep 단언)» |
| 계약 § 2.4.7 | `specs/contracts/console-integration-contract.md:1469-1482` | «MUST NOT coerce … anywhere» + 시험: `features/finance-ops/` 의 `amount` |
| 계약 § 2.4.7.1 | `console-integration-contract.md:1818-1831` | `amount`·`exchangeRate` · `features/ledger-ops/` |
| 생산자 | `finance-platform/specs/contracts/http/ledger-api.md:623` · `:670` | FX `rate` = 정확한 소수 문자열(F5) — § 14 · § 14.1 |

시험의 grep 단언 4개가 실제로 막는 것 — **네 개 모두 같은 술어**: 주석 줄(`*`·`//`·`/*` 로 시작) 을 빼고, **같은 줄에 `\bamount\b`(또는 `\bexchangeRate\b`) 가 있을 때만** `Number(`/`parseFloat(`/`parseInt(` 를 문다.

| 시험 | file:line | 훑는 파일 | 토큰 |
|---|---|---|---|
| ledger-api | `tests/unit/ledger-api.test.ts:498-536` | `features/ledger-ops` 만 | `amount` · `exchangeRate` (`:525`) |
| finance-api | `tests/unit/finance-api.test.ts:474-535` | `features/finance-ops` + `shared/lib/money.ts` + `shared/api/finance-accounts-read.ts` (`:480-484`) | `amount` 만 (`:522`) |
| ledger-account-api | `tests/unit/ledger-account-api.test.ts:311-369` | `features/ledger-ops` + `money.ts` + `shared/api/ledger-types` + `ledger-client.ts` + `ledger-overview-read.ts` (`:317-323`) | `amount` · `exchangeRate` (`:358`) |
| ledger-statement-api | `tests/unit/ledger-statement-api.test.ts:268-306` | `features/ledger-ops` 만 | `amount` · `exchangeRate` (`:295`) |

🔴 **발견 — 허용 목록은 지금 없다(만들 대상이 없다), 그리고 `rate` 는 안 막힌다.** 네 시험 모두 «허용 목록» 개념이 없고(파일 단위 제외 0), 토큰이 `amount`/`exchangeRate` 뿐이라 `Number(q.rate)` · `Number(lot.carryingBaseMinor)` 는 **네 시험을 전부 통과**한다. 그런데 `src/shared/api/ledger-types/fx.ts:114` 주석은 «the ledger-ops grep guard covers `rate` as well as `amount`/`exchangeRate`» 라고 적고 있다 — **주석이 가드보다 넓게 말한다.** 이번 예외 함수가 바로 `rate` 를 숫자로 바꾸므로, 기존 가드에 «허용 목록 한 줄» 을 더하는 것으로는 아무것도 지켜지지 않는다. ⇒ 새 가드(`tests/unit/f5-chart-geometry-exception.test.ts`)가 F5 표면 전체에서 **실행 줄의 강제 호출 0** 을 단언하고(토큰 무관), 허용 목록 = `shared/lib/chart-scale.ts` 한 파일 · 그 안 1곳. 착수 시점 F5 표면의 실행 줄 강제 호출 = **0건**(전부 주석 줄 — Grep 실측) 이라 더 엄격한 술어가 기존 코드를 깨지 않는다. 기존 네 시험은 그대로 둔다(여전히 초록, 범위가 더 좁을 뿐). ADR 이 필요한 자리는 아니다 — 규칙은 스펙 · 계약 · 시험에 있고 소유자 결정으로 스펙 한 줄을 더하는 일이다.

**② 이커머스 정산 금액은 F5 가 아니다.** `src/features/ecommerce-ops/api/settlement-types.ts:27-31` 「integer minor-unit amount (`number`, KRW long)」, `:222` `payableNetMinor: z.number()`, `minorToWon(minor: number)` `:51-54` = 부호 + `Math.abs(minor).toLocaleString('ko-KR')` — 정수 서식화만. ⇒ 지급액 막대에는 예외가 **필요 없다**(숫자 그대로 `ProportionBar`).

**③ 환율 이력 `limit`** — 화면은 `FX_HISTORY_DEFAULT_LIMIT` = **50** 을 보낸다: `src/features/ledger-ops/hooks/use-ledger-ops-state.ts:219-223` → `use-ledger-fx.ts:166-171`(clamp) · 상수 `src/shared/api/ledger-types/fx.ts:179-180`(기본 50 · 상한 500). 생산자 기본도 50(`ledger-api.md:647`). 바꿀 필요 없음 — 그래프 캡션이 «받아 온 최근 N건» 을 밝힌다.

**④ 지급 목록 페이지 크기** — SSR 첫 페이지 `size: 20` 고정(`src/features/ecommerce-ops/api/settlements-state.ts:105`), 화면 상태도 `initialPayouts.size || SETTLEMENT_DEFAULT_PAGE_SIZE`(=20, `settlement-types.ts:248`) · `PeriodPayoutsScreen.tsx:46-49`. `totalElements` 는 응답 스키마에 필수(`settlement-types.ts:239`)이고 이미 «총 N건» 페이지 글자에 쓰인다(`PeriodPayoutsScreen.tsx:155`). 페이지 크기는 바꾸지 않았다 — 막대는 **이 페이지 안**에서 비교하고 그렇다고 밝힌다(«이 페이지 M건 … (전체 N건)»). 원장 기간 목록도 페이지 20(`PeriodsTable.tsx:41`), 생산자 정렬 = «most-recent window first»(`ledger-api.md:202`) ⇒ 첫 페이지는 «최근 N개 기간».

**⑤ 샘플 고정값** — 세 자리 모두 값이 있다: 환율 이력 USD 2점(`1300.00000000` · `1298.50000000`) · JPY 1점(`9.10000000`) — `src/shared/sample/fixtures/ledger.ts:693-701`(JPY 가 «점 1개» 가장자리를 샘플에서 보인다); 원장 기간 2개 `entryCount` 2 · 1 — `ledger.ts:430-455`; 지급 2건 `payableNetMinor` 82800 · 52800 — `src/shared/sample/fixtures/ecommerce.ts:1071-1095`(음수 지급 샘플은 없다).

## 새 문구 (소유자 승인 2026-10-22 KST)

| # | file:line | 문구 | 언제 보이나 |
|---|---|---|---|
| 1 | `src/features/ledger-ops/components/FxRateHistoryChart.tsx:58` | `최신 {rate}` | 환율 이력에 그릴 수 있는 점이 1개 이상일 때 항상 (rate = 원문 문자열) |
| 2 | `FxRateHistoryChart.tsx:62` | `최저 {rate}` | 위와 같음 + 일반 소수 형식 rate 가 1개 이상 (지수 표기만 있으면 숨김) |
| 3 | `FxRateHistoryChart.tsx:67` | `최고 {rate}` | 2와 같음 |
| 4 | `FxRateHistoryChart.tsx:71` | `받아 온 최근 {N}건 · 왼쪽이 오래된 값, 오른쪽이 최신 값` | 그래프가 보일 때 항상 (N = 받은 이력 건수, 기본 최대 50) |
| 5 | `FxRateHistoryChart.tsx:75` | `숫자로 읽을 수 없는 {k}건은 그래프에서 뺐습니다 (아래 표에는 있습니다).` | 파싱 안 되는 rate 가 1건 이상 섞였을 때 |
| 6 | `src/features/ecommerce-ops/lib/payout-bars.ts:27` | `막대는 이 페이지 {M}건 안에서 가장 큰 지급액 대비 길이입니다 (전체 {N}건).` | 정산 기간 상세 — 이 페이지 건수 < 전체 건수 |
| 7 | `payout-bars.ts:28` | `막대는 이 기간 지급 {N}건 중 가장 큰 지급액 대비 길이입니다.` | 정산 기간 상세 — 한 페이지에 전부 들어올 때 |
| 8 | `src/features/ecommerce-ops/components/PeriodPayoutsTable.tsx:55` | ` 0원이거나 음수(되돌림)인 지급액은 막대 없이 금액만 표시합니다.` | 이 페이지에 0 이하 지급액이 1건 이상일 때 (6·7 뒤에 이어짐) |
| 9 | `src/features/ledger-ops/lib/period-entry-bars.ts:32` | `막대는 회계 기간 {N}개 전체 중 가장 많은 분개 수 대비 길이입니다.` | 원장 기간 탭 — 한 페이지에 전부 들어올 때 |
| 10 | `period-entry-bars.ts:35` | `막대는 최근 {M}개 기간 안에서 가장 많은 분개 수 대비 길이입니다 (전체 {N}개).` | 원장 기간 탭 — 첫 페이지 · 전체가 더 많을 때 |
| 11 | `period-entry-bars.ts:36` | `막대는 이 페이지 {M}개 기간 안에서 가장 많은 분개 수 대비 길이입니다 (전체 {N}개).` | 원장 기간 탭 — 둘째 페이지 이후 |

새 `aria-label` 은 없다 — SVG 와 막대는 모두 `aria-hidden`, 읽히는 것은 위 글자와 기존 표의 숫자.

## 구현 (2026-10-21 UTC · Opus)

- **스펙(AC-1, 먼저 커밋)**: `architecture.md` § Integration Rules 에 «F5 의 유일한 예외 — 그래프 기하» 한 줄(`:381`) + `shared/lib/` 트리 줄(`:218`) 에 `chart-scale.ts`. 계약 § 2.4.7 F5 끝에 «Sole exception — chart geometry»(`:1483-1490`), § 2.4.7.1 에 그 참조(`:1840-1842`).
- **예외 함수**: `src/shared/lib/chart-scale.ts` `chartMagnitude(decimalString: unknown): number | null` — 일반 소수 · 부호 · 지수 표기 허용, 빈 문자열 · 비소수 · `Infinity`/`NaN` 글자 · 유한하지 않은 값 → `null`. 강제 호출 1곳.
- **가드**: `tests/unit/f5-chart-geometry-exception.test.ts` — F5 표면(`features/{finance-ops,ledger-ops,finance-overview}` + `shared/lib/{money,chart-scale}.ts` + `shared/api/{ledger-types,ledger-client,ledger-overview-read,finance-accounts-read,finance-accounts-types}`) 의 실행 줄 강제 호출 0 · 허용 목록 = `shared/lib/chart-scale.ts` 1파일 · 그 파일 안 강제 호출 정확히 1 · bite(허용 목록 밖에 심은 `Number(q.rate)` 등 4줄 → 4건 물림, JSX/블록/줄 주석은 안 물림, 같은 디렉터리의 `chart-scale-extra.ts` 는 물림).
- **환율 추이 선**: `features/ledger-ops/lib/fx-history-line.ts`(기하 — 최신순 입력을 뒤집어 시간순, 점 1개 → 가운데 점, 모두 같음 → 가운데 수평선, 파싱 실패 점 제외 · 개수, 전부 실패 → `null`; 최저 · 최고는 `compareDecimalStrings` 로 **문자열 정확 비교** — 동률은 더 최근 시점) + `components/FxRateHistoryChart.tsx`(인라인 SVG `aria-hidden`, figcaption 에 원문 문자열) → `FxRateHistoryTable` 의 표 위.
- **지급액 막대**: `features/ecommerce-ops/lib/payout-bars.ts` + `PeriodPayoutsTable` 지급액 칸에 `ProportionBar`(상태 톤 색) · 0 이하 → 막대 없음 · 위에 범위 문장. `PeriodPayoutsScreen` 이 `totalElements` 를 넘긴다.
- **전표 수 막대**: `features/ledger-ops/lib/period-entry-bars.ts` + `PeriodsTable` 분개 수 칸에 `ProportionBar` · null(OPEN) → 막대 없음 · 위에 범위 문장.
- `shared/ui/InlineBar.tsx` 는 바꾸지 않았다(재사용만). 기존 testid · 숫자 글자 · 페이지 글자 그대로.
- 시험: `tests/unit/fx-history-chart.test.tsx` · `tests/unit/settlement-and-period-bars.test.tsx` · `tests/unit/f5-chart-geometry-exception.test.ts`.

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

## 소유자 승인 · 머지 전 확인 (2026-10-21 UTC · 분석=Opus 5.5)

- 새 문구 11개 = **소유자 승인**(선택창, 2026-10-22 KST, 머지 전) → AC-2 닫힘.
- `main`(`ea299d688`, `TASK-MONO-804` 의 순회기 수정 포함) 위로 rebase — 충돌 없음. 새 순회기로 `check-client-graph-server-only.mjs` · `check-client-graph-backend-origins.mjs` rc=0 · console-web hits=0 · 큐 가드 셋 rc=0.
- 브랜치 nightly run `39664325985`(head `c18e0066a`) **success** — `Package platform-console e2e boot jars (nightly)` · `Platform Console E2E full-stack (Playwright + docker compose)` 둘 다 success. PR #4455 같은 head 체크 pending 0 · fail 0 → AC-3 닫힘.
- 열림: **AC-4**(Vercel Production 기록 · 익명 샘플 화면 — 환율 이력 · 정산 기간 상세 · 원장 기간).

## CORRECTION (2026-10-22 UTC · close chore · 4차원 확인)

- **AC-4 닫힘** — 위 AC 목록의 AC-4 칸은 review 시점 기록이라 빈 채로 둔다. Vercel Production `7243610439` success. 익명 샘플 화면: 환율 추이 선(`/ledger` FX 환율 피드 → USD 이력 → `ledger-fx-history-chart-svg` · «최신 1300.00000000» · «최저 1298.50000000» · «최고 1300.00000000» · «받아 온 최근 2건 …») · 기간 전표 수 막대(«막대는 회계 기간 2개 전체 중 …») = 2026-10-22 UTC 헤드리스 브라우저(Playwright · 메인 세션, 익명 샘플 `https://console.hubwang.com`); 지급액 막대(`/ecommerce/settlements/periods/period-sample-0001` → `payout-row-bar-0` 100% · `-1` 63.8% · «막대는 이 기간 지급 2건 중 …») = 같은 날 서버 렌더 HTML.
- 4차원: (a) #4455 `state=MERGED` · (b) 스쿼시 `14acf1440` 가 `origin/main` 조상(tip `e35c77765`) · (c) 머지 시점 `statusCheckRollup` 67 · 실패 0 · 대기 0 · (d) AC 원문을 열어 남은 칸 = AC-4 하나 — 위 기록으로 그 동사(«배포 · 확인»)대로 닫힘.
