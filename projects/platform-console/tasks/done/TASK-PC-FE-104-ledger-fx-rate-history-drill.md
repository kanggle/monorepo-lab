# TASK-PC-FE-104 — Ledger-ops FX 환율 history 드릴 (FIN-BE-040 read EP 소비)

- **Status**: done
- **Project**: platform-console
- **App**: console-web (Next.js, ledger-ops 피처)
- **Analysis model**: Opus 4.8 / **Implementation model**: Opus 4.8 (read-only FE 슬라이스 — PC-FE-092 미러 + per-pair 드릴)

## Goal

platform-console `ledger-ops` 화면의 **기존 "FX 환율 피드" 탭 안에** per-pair **history 드릴**을 추가.
FIN-BE-040 read EP (`GET /api/finance/ledger/fx-rates/{foreignCurrency}/history?limit=N`)를 소비해, 운영자가
한 통화쌍(`KRW/{foreign}`)의 **시간순 환율 변화**(`fetched_at DESC`, newest-first)를 감사할 수 있게 한다 —
auto-applied 시장 환율이 어떻게 움직였는지(rate·as-of·fetched-at·source)를 본다. **새 탭 추가 없음**(FE-075 의
대사-statement 드릴, FE-074 의 시산표→계정 드릴과 동일하게 기존 탭에 배선). read-only·mutation 0.
드릴 트리거 두 경로: (a) 피드 테이블 행의 통화쌍 클릭, (b) 수동 통화 입력폼. 수동 새로고침 버튼(react-query refetch).

## Scope

**In scope** (console-web only):

1. **프록시 라우트** `src/app/api/ledger/fx-rates/[foreignCurrency]/history/route.ts` — **GET 전용**,
   `runtime='nodejs'`, `getFxRateHistory(foreignCurrency, limit?)` 호출 + `mapLedgerError()`(`../../../_proxy`).
   `?limit=` query 를 파싱해 number 로 전달(NaN/부재 → undefined, 백엔드 기본 50). 멱등키·Reason·Tenant·body 없음.
2. **api-client** `src/features/ledger-ops/api/ledger-reads-api.ts` +=
   `getFxRateHistory(foreignCurrency, limit?): Promise<FxRateHistoryResponse>` — `callLedger()` 사용,
   **`getDomainFacingToken()`**(절대 operator 아님), path `/api/finance/ledger/fx-rates/{enc(foreign)}/history[?limit=]`,
   `logPath = /api/finance/ledger/fx-rates/{currency}/history`(통화 sanitize), `data` 언랩 후 `FxRateHistoryResponseSchema.parse`.
3. **zod 스키마** `src/features/ledger-ops/api/types.ts` +=
   - `FX_HISTORY_DEFAULT_LIMIT = 50`, `FX_HISTORY_MAX_LIMIT = 500`.
   - `FxRateHistoryQuoteSchema = z.object({ rate: z.string(), asOf, fetchedAt, source }).passthrough()` — **rate 문자열**(F5).
   - `FxRateHistoryResponseSchema = z.object({ base: 3, foreign: 3, quotes: array }).passthrough()`.
   - `FxRateHistoryQuote` / `FxRateHistoryResponse` 타입 + `FxRateHistoryQueryParams { limit? }` export.
4. **react-query 훅** `src/features/ledger-ops/hooks/use-ledger-ops.ts` +=
   `fxRateHistoryKey(foreign, limit)`, `useFxRateHistory(foreign, limit=50, enabled)` — `enabled`-게이트
   (`foreign` 비었으면 미발화 + 탭 비활성 시 미발화). `clampFxHistoryLimit`(≤0→1·cap 500·기본 50).
   `staleTime 30s`, `retry:false`, refetch-storm 없음(기존 reads 미러).
5. **컴포넌트**:
   - `components/FxRateHistoryTable.tsx` — props `{ data: FxRateHistoryResponse | null; onRefresh? }`. 헤딩(통화쌍),
     새로고침 버튼, 빈 상태(quotes=[] → "이력이 없습니다"; 200 — 404 아님), 테이블(rate 문자열 그대로·asOf·fetchedAt·source).
     `data=null` → 아무것도 렌더 안 함. data-testid: `ledger-fx-history-table`/`-row-${i}`/`-empty`/`-refresh`/`-heading`.
   - `components/FxRateHistoryLookup.tsx` — 단일 통화 입력폼(대문자화, maxLength 3) + 조회 버튼. PositionLotsLookup 미러.
     data-testid: `ledger-fx-history-currency-input`/`-submit`.
6. **드릴 배선**:
   - `FxRatesTable.tsx` += optional `onSelectPair?(foreign)`; 제공 시 통화쌍 셀을 버튼화(클릭→드릴). 미제공 시 기존 평문(회귀 0).
   - `LedgerOpsScreen.tsx`: fx-rates 패널 안, 피드 테이블 **아래**에 `FxRateHistoryLookup` + 드릴(none/forbidden/error/`FxRateHistoryTable`).
     state `fxHistoryForeign`; `useFxRateHistory(fxHistoryForeign, FX_HISTORY_DEFAULT_LIMIT, active==='fx-rates')`. **새 탭 없음**(탭 수 7 유지).
7. **테스트**:
   - `tests/unit/ledger-fx-history-api.test.ts` — domain-facing 토큰·GET-only·URL(currency+limit)·F5 rate 문자열·빈 quotes 200·
     에러매핑(400/403→ApiError, 503/timeout→LedgerUnavailable)·logPath sanitize(통화 미로깅)·export `getFxRateHistory`.
   - `tests/unit/ledger-fx-history-proxy.test.ts` — 프록시 GET 위임·limit 전달·에러 매핑·GET-only export.
   - `tests/unit/LedgerFxHistory.test.tsx` — FxRateHistoryTable(행·rate verbatim·빈 상태·refresh·null) + FxRateHistoryLookup(submit·trim·대문자).
   - 기존 회귀: `LedgerFxRates.test.tsx`(onSelectPair 미전달 → 평문 유지)·`LedgerOpsScreen.test.tsx`(탭 7 유지)·`ledger-api.test.ts`(route-walk GET-only·export-list).

**Out of scope**: per-pair rate **override** 입력, ShedLock 폴러, 실 외부 API, mutation 일체, 차트 시각화(테이블만).

## Acceptance Criteria

- **AC-1 — read-only.** mutation 0. 기존 ledger-ops 7탭 표면 무변경(새 탭 없음). `getDomainFacingToken` 사용(절대 operator 아님).
- **AC-2 — 드릴.** 피드 행 통화쌍 클릭 또는 수동 입력 → 해당 `KRW/{foreign}` history 시계열 렌더(newest-first, 백엔드 정렬 그대로).
- **AC-3 — 데이터.** 각 quote 행: rate(문자열 그대로)·asOf·fetchedAt·source. 빈 이력 → 빈 상태(200, 404 아님).
- **AC-4 — F5.** rate 문자열 파싱·렌더(`Number()`/`parseFloat()`/`parseInt()` 미사용 — ledger-ops grep 가드 통과).
- **AC-5 — limit.** 훅이 limit 클램프(≤0→1·cap 500·기본 50) 후 프록시→백엔드 전달. 백엔드도 동일 floor/cap(이중 방어).
- **AC-6 — 3 게이트.** `pnpm lint` clean + `npx tsc --noEmit` clean + `pnpm exec vitest run` 전건 GREEN(신규+기존).

## Related Specs

- `projects/finance-platform/specs/contracts/http/ledger-api.md` § 14.1 GET `.../fx-rates/{foreignCurrency}/history` — FIN-BE-040 계약(소비 대상)
- `projects/platform-console/tasks/done/TASK-PC-FE-092-ledger-fx-rates-dashboard.md` — 미러 슬라이스(FX 피드 탭)
- `projects/platform-console/tasks/done/TASK-PC-FE-091-ledger-fx-position-lots-console-drill.md` — Lookup 폼 패턴

## Related Contracts

- `GET /api/finance/ledger/fx-rates/{foreignCurrency}/history?limit=N` (FIN-BE-040) → `{ data: { base, foreign, quotes: [{rate, asOf, fetchedAt, source}] }, meta }` — newest-first(`fetched_at DESC, id DESC`), `limit` 기본 50/cap 500/≤0→1, unknown/never-polled → 200 quotes:[].

## Edge Cases

- unknown/never-polled 통화(예 `XXX`) → 200 빈 quotes(빈 상태 안내). 404 아님.
- limit ≤0 → 1, >500 → 500, 부재 → 50(훅 클램프 + 백엔드 floor/cap).
- rate 정밀도(소수 8자리·JPY 큰 수) → 문자열 그대로(절단/반올림 금지).
- foreign = 기준통화(KRW) → 백엔드가 빈/해당없음 처리(콘솔은 그대로 렌더, 강제 차단 안 함).

## Failure Scenarios

- 403 → `TENANT_FORBIDDEN`(엔타이틀 안 된 테넌트). 503/timeout → ledger 일시 불가 안내(드릴 섹션만 degrade, 피드/타 탭 무관).
- 미인증 → 콘솔 로그인 흐름(기존 가드).
