# TASK-PC-FE-092 — Ledger-ops FX 환율 피드 대시보드 탭 (FIN-BE-033 read EP 소비)

- **Status**: done
- **Project**: platform-console
- **App**: console-web (Next.js, ledger-ops 피처)
- **Analysis model**: Opus 4.8 / **Implementation model**: Sonnet 4.6 (read-only FE 슬라이스 — PC-FE-091 미러)

## Goal

platform-console `ledger-ops` 화면에 **"FX 환율 피드" 7번째 탭** 추가. FIN-BE-033 read EP
(`GET /api/finance/ledger/fx-rates`)를 소비해 피드 캐시의 현재 환율을 운영자에게 시각화 —
통화쌍·환율·출처·as-of/fetched-at·**나이(age)·stale 여부**, 그리고 top-level **`feedEnabled`**(폴백 활성 여부).
운영자가 "피드가 지금 어떤 환율을 들고 얼마나 오래됐나"(= `FX_RATE_UNAVAILABLE` 폴백 성패의 근거)를 본다.
read-only·mutation 0. PC-FE-091("FX 포지션 로트") 슬라이스를 미러하되 **입력폼 없음**(전역 리스트 대시보드).

## Scope

**In scope** (console-web only):

1. **프록시 라우트** `src/app/api/ledger/fx-rates/route.ts` — **GET 전용**, `runtime='nodejs'`, 새 api-client
   `getFxRates()` 호출 + `mapLedgerError()`(`../_proxy`). 멱등키·X-Operator-Reason·X-Tenant-Id·body 없음
   (PC-FE-091 lots 라우트 미러). 업스트림 base = `LEDGER_BASE_URL`.
2. **api-client** `src/features/ledger-ops/api/ledger-api.ts` += `getFxRates(): Promise<FxRatesResponse>`
   — `callLedger()` 사용, **`getDomainFacingToken()`**(절대 `getOperatorToken()` 아님), path
   `/api/finance/ledger/fx-rates`, `logPath` 동일(값 없음 — 인자 없음), `FxRatesResponseSchema.parse`.
3. **zod 스키마** `src/features/ledger-ops/api/types.ts` +=
   - `FxRateSchema = z.object({ baseCurrency: z.string().min(3).max(3), foreignCurrency: z.string().min(3).max(3),
     rate: z.string(), asOf: z.string(), source: z.string(), fetchedAt: z.string(),
     ageSeconds: z.number().int(), stale: z.boolean() }).passthrough()` — **rate 는 문자열**(decimal, F5; `Number()`/`parseFloat()` 금지).
   - `FxRatesResponseSchema = z.object({ feedEnabled: z.boolean(), rates: z.array(FxRateSchema) }).passthrough()`.
   - `FxRate` / `FxRatesResponse` 타입 export.
4. **react-query 훅** `src/features/ledger-ops/hooks/use-ledger-ops.ts` +=
   `fxRatesKey()` = `[LEDGER_KEY, 'fx-rates']`; `useFxRates()` = `useQuery({ queryKey, queryFn: fetchFxRates,
   staleTime: 30_000, refetchOnWindowFocus:false, retry:false })` — **입력 없는 전역 조회**라 `enabled` 게이트 없음(항상 활성).
   `fetchFxRates` = `apiClient.get('/api/ledger/fx-rates')` → `FxRatesResponseSchema.parse`.
5. **컴포넌트** `src/features/ledger-ops/components/FxRatesTable.tsx`:
   - props `{ data: FxRatesResponse | null }`.
   - top-level **feedEnabled 배지**(활성/비활성 — 비활성이면 "환율 폴백이 꺼져 있습니다" 안내).
   - 테이블: base/foreign 쌍 · rate(문자열 그대로) · source · asOf · fetchedAt · age(초→사람친화 표기 권장, 예 "2일 전"/"방금")
     · **stale 행 강조**(stale=true → 경고 스타일 + "STALE" 배지).
   - 빈 캐시(rates=[]) → 빈 상태 안내("적재된 환율 quote 가 없습니다.")(404 아님 — read EP 가 200+빈배열).
   - 새로고침 버튼(react-query refetch/invalidate) — 운영자 수동 갱신(선택, 권장).
   - data-testid: `ledger-fx-rates-table`, `ledger-fx-rates-feed-badge`, `ledger-fx-rates-row-${i}`, `ledger-fx-rates-refresh`, `ledger-fx-rates-empty`.
   - (PC-FE-091 의 Lookup 입력폼은 **불필요** — 전역 대시보드라 조회 파라미터 없음.)
6. **탭 배선** `src/features/ledger-ops/components/LedgerOpsScreen.tsx`:
   - `TABS` 배열에 `{ key: 'fx-rates', label: 'FX 환율 피드' }` 7번째 추가.
   - 패널 `<div role="tabpanel" id="ledger-panel-fx-rates" hidden={active!=='fx-rates'}>`:
     `useFxRates()` → 로딩/에러(403→`TENANT_FORBIDDEN`, 기타→`messageForCode`)/성공 시 `<FxRatesTable data=...>`.
     입력 없음 — 탭 활성 시 자동 조회.
7. **테스트**:
   - `tests/unit/ledger-fx-rates-api.test.ts` — `getDomainFacingToken` 사용·GET-only(멱등키·Reason·Tenant 헤더 없음)·
     `FxRatesResponseSchema` 라운드트립(rate 문자열 보존)·빈배열 200·에러매핑(400/403→ApiError, 503/timeout→LedgerUnavailable)·
     logPath sanitized·모듈 export `getFxRates`. (`ledger-lots-api.test.ts` 미러.)
   - `tests/unit/ledger-fx-rates-proxy.test.ts` — 프록시 라우트 GET 위임·에러 매핑.
   - `tests/unit/LedgerFxRates.test.tsx` — 컴포넌트: feedEnabled 배지·stale 행 강조·빈 상태·rate 문자열 렌더(Number 코어션 없음).
   - **갱신**: `LedgerOpsScreen.test.tsx` 탭 수 assertion **6 → 7**; `ledger-api.test.ts` 의 export-list assertion 에 `getFxRates` 추가(있으면).

**Out of scope**: 수동 refresh **트리거 백엔드**(폴러는 FIN-BE-031; 여기선 react-query refetch 만), 실 외부 API,
per-pair drill/history, mutation 일체.

## Acceptance Criteria

- **AC-1 — read-only.** mutation 0. 기존 ledger-ops 6탭 표면 무변경. `getDomainFacingToken` 사용(절대 operator 토큰 아님).
- **AC-2 — 데이터 렌더.** 적재된 각 통화쌍 행 표시(base/foreign·rate 문자열·source·asOf·fetchedAt·age), top-level
  feedEnabled 배지. (base,foreign) 정렬은 백엔드가 보장(그대로 렌더).
- **AC-3 — staleness 가시화.** `stale=true` 행 시각 강조 + 배지. 빈 캐시 → 빈 상태(200, 404 아님).
- **AC-4 — F5.** rate 는 문자열로 파싱·렌더(`Number()`/`parseFloat()` 미사용 — ledger-ops grep 가드 통과).
- **AC-5 — 탭.** 7번째 "FX 환율 피드" 탭 정상 동작, 활성 시 자동 조회. 기존 6탭 회귀 0.
- **AC-6 — 3 게이트.** `pnpm lint` clean + `npx tsc --noEmit` clean + `pnpm exec vitest run` 전건 GREEN(신규+기존).

## Related Specs

- `projects/finance-platform/specs/contracts/http/ledger-api.md` § FX rates (read) — FIN-BE-033 계약(소비 대상)
- `projects/platform-console/tasks/done/TASK-PC-FE-091-ledger-fx-position-lots-console-drill.md` — 미러 슬라이스

## Related Contracts

- `GET /api/finance/ledger/fx-rates` (FIN-BE-033) → `{ data: { feedEnabled, rates: [{baseCurrency, foreignCurrency, rate, asOf, source, fetchedAt, ageSeconds, stale}] }, meta }`

## Edge Cases

- 빈 캐시 → 빈 상태(피드 미적재/disabled). feedEnabled=false 면 배지로 "폴백 비활성" 표시.
- ageSeconds 음수(시계 skew) → 그대로/방어적 표기(예 "방금"). 클램프 불요.
- rate 정밀도(예 JPY 큰 수·소수 8자리) → 문자열 그대로(절단/반올림 금지).

## Failure Scenarios

- 403 → `TENANT_FORBIDDEN` 메시지(엔타이틀 안 된 테넌트). 503/timeout → ledger 일시 불가 안내.
- 미인증 → 로그인 흐름(기존 콘솔 가드).
