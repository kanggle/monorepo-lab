# Task ID

TASK-PC-FE-148

# Title

console-web `features/ledger-ops/hooks/use-ledger-ops.ts`(558줄 fat 데이터-쿼리 훅 파일)를 ledger 서브-도메인별 훅 모듈(periods · entries · reconciliation · fx) + 공유 leaf(`use-ledger-shared`)로 분할하고, 원본 `use-ledger-ops.ts` 경로는 named re-export barrel 로 유지 — import-site 0 변경, behavior-preserving

# Status

done

# Owner

frontend (Opus 4.8 분석 — 구현 권장=Sonnet 4.6 또는 Opus; behavior-preserving 구조 분할, contract/spec/backend 무변경)

# Task Tags

- code
- refactor
- test

---

# Dependency Markers

- **builds on**: TASK-PC-FE-072(ledger-ops 섹션 도입 — § 2.4.7.1) + TASK-PC-FE-073(discrepancy RESOLVE — ledger 유일 mutation) + TASK-PC-FE-074(Account balance/entries) + TASK-PC-FE-075(Statement 드릴) + TASK-PC-FE-091(FX Position Lots) + TASK-PC-FE-092(FX Rates 피드) + TASK-PC-FE-104(FX Rate History 드릴) + TASK-MONO-300(FX refresh mutation). 본 task 는 그 위에서 **훅 정의 위치만** 재배치한다(쿼리 동작·queryKey·시그니처 불변).
- **동형 선례**: TASK-PC-FE-099(`use-erp-ops.ts` → `use-erp-shared` + `use-erp-masters` + `use-erp-approval` + `use-erp-delegation` + barrel). 동일 패턴(공유 leaf + 도메인 모듈 + `export *` barrel)을 ledger 에 적용.
- **혼동 주의(별건)**: `components/use-ledger-ops-state.ts`(TASK-PC-FE-106 에서 추출한 **화면 상태** 훅)는 본 대상이 아니다. 본 task 대상은 `hooks/use-ledger-ops.ts`의 **react-query 데이터 쿼리/뮤테이션** 훅들이다. `use-ledger-ops-state.ts`는 barrel 을 통해 동일 심볼을 그대로 import 하므로 무변경으로 통과한다.

# Goal

558줄 한 파일에 응집돼 있던 ledger-ops 데이터 쿼리/뮤테이션 훅을 ledger 서브-도메인 경계로 분할해 가독성·소유 경계를 명확히 한다. 공개 표면(`@/features/ledger-ops/hooks/use-ledger-ops` import 경로 + 모든 export 심볼)은 불변 — 원본 파일은 named re-export barrel 로 남기고 import-site 는 0 변경.

behavior-preserving: 모든 훅의 이름·시그니처·`queryKey`·`enabled`/`staleTime`/`refetchOnMount`/`retry` 옵션·`onSuccess` invalidation·useQuery/useMutation 호출 순서가 분할 전과 byte-identical. money 값(`amount`/`exchangeRate`)에 대한 `Number()`/`parseFloat()` 변환 경로는 신규/이동 코드 어디에도 도입하지 않는다(F5 — ledger-ops dir-walk grep 가드가 신규 파일도 자동 포함).

# Scope

## In Scope

- **신규 `src/features/ledger-ops/hooks/use-ledger-shared.ts`** (leaf): 모듈-private 였던 `LEDGER_KEY` 상수 + `clampSize` 페이지-사이즈 클램프를 추출(공개 표면 아님 — barrel 에서 re-export 하지 않음). 도메인 모듈이 import 하는 공유 코어. barrel↔도메인↔shared 순환 없음(shared 는 어떤 hooks 모듈도 import 하지 않는 leaf).
- **신규 `use-ledger-periods.ts`**: `trialBalanceKey`/`useTrialBalance` + `periodsKey`/`buildPeriodsQs`/`usePeriods` + `periodKey`/`usePeriod`.
- **신규 `use-ledger-entries.ts`**: `journalEntryKey`/`useJournalEntry` + `accountBalanceKey`/`useAccountBalance` + `accountEntriesKey`/`buildAccountEntriesQs`/`useAccountEntries`.
- **신규 `use-ledger-reconciliation.ts`**: `discrepanciesKey`/`buildDiscrepanciesQs`/`useDiscrepancies` + `discrepancyKey`/`useDiscrepancy` + `statementKey`/`useStatement` + `ResolveDiscrepancyArgs`/`useResolveDiscrepancy`(ledger 유일 read-mutation; `onSuccess` 가 `[LEDGER_KEY,'discrepancies']` + `discrepancyKey(id)` invalidate).
- **신규 `use-ledger-fx.ts`**: `positionLotsKey`/`usePositionLots` + `fxRatesKey`/`useFxRates` + `clampFxHistoryLimit`(모듈-private 유지)/`fxRateHistoryKey`/`useFxRateHistory` + `useRefreshFxRates`(`onSuccess` 가 `fxRatesKey()` invalidate).
- **`use-ledger-ops.ts` → barrel**: 4개 도메인 모듈을 `export *` 로 re-export. 상단 `'use client'` + 섹션 docstring 보존(MODULE SPLIT 노트 추가). `LEDGER_KEY`/`clampSize`/`clampFxHistoryLimit` 은 분할 전에도 export 되지 않던 모듈-private 심볼이므로 barrel 에서도 미노출(공개 표면 동일).
- **Tests**: `features/ledger-ops/**` 기존 vitest(특히 `LedgerOpsScreen.test.tsx` — 훅을 barrel 경유로 구동) 무회귀 통과.

## Out of Scope

- 훅의 쿼리/뮤테이션 **동작 변경**(queryKey·enabled·staleTime·refetch·retry·onSuccess invalidation·호출 순서 일체 불변).
- `components/use-ledger-ops-state.ts`(화면 상태 훅, PC-FE-106) 변경 — barrel 경유 import 라 무변경 통과.
- api/types·api/ledger-api·proxy route·money helper 변경.
- 백엔드/계약/스펙/ledger read·mutation API 변경.

# Acceptance Criteria

- [x] 원본 import 경로 `@/features/ledger-ops/hooks/use-ledger-ops`(상대 경로 `../hooks/use-ledger-ops`)가 그대로 유지되고, 모든 importer(DiscrepancyDetail/DiscrepancyQueue/PeriodDetail/PeriodsTable/use-ledger-ops-state)는 0 변경으로 동일 심볼을 import 한다.
- [x] 모든 훅의 이름·시그니처·`queryKey`·옵션(enabled/staleTime/refetchOnMount/retry/READ_QUERY_REFETCH)·`onSuccess` invalidation·호출 순서가 분할 전과 byte-identical(behavior-preserving).
- [x] `LEDGER_KEY`/`clampSize`/`clampFxHistoryLimit` 은 분할 전과 동일하게 비공개(barrel 미노출).
- [x] 신규 4개 도메인 훅 모듈 + shared leaf 모두 최상단 `'use client'` 보존.
- [x] barrel↔도메인↔shared 순환 없음(shared 는 leaf, 도메인 모듈이 단방향 import).
- [x] F5: 신규/이동 코드 어디에도 `amount`/`exchangeRate` 줄에 `Number()`/`parseFloat()`/`parseInt()` 변환 경로 미도입(JSX/로직 이동만; ledger-ops dir-walk grep 가드가 신규 파일 자동 포함, green).
- [x] `npx tsc --noEmit` clean / `npx next lint` clean / `npx vitest run ledger` green(19 파일 / 306 테스트 pass), scope = console-web only.

# Related Specs

- `projects/platform-console/specs/services/console-web/architecture.md` § Server vs Client Components(React Query client-only) — 소비만, 변경 없음.
- `projects/platform-console/specs/contracts/console-integration-contract.md` §2.4.7.1(ledger 섹션) — read + mutation 계약 소비, 변경 없음(동일 호출).

# Related Contracts

- 변경 없음. 훅 정의 위치만 재배치(동일 ledger read/mutation API·동일 호출·동일 queryKey).

# Target Service

- `platform-console` / `apps/console-web` — `src/features/ledger-ops/hooks/use-ledger-ops.ts`(barrel) + 신규 `use-ledger-shared.ts` / `use-ledger-periods.ts` / `use-ledger-entries.ts` / `use-ledger-reconciliation.ts` / `use-ledger-fx.ts`. behavior-preserving 구조 분할.

# Architecture

- Feature-Sliced Design hooks 모듈 분할. 단일 fat 훅 파일이 ledger 전 서브-도메인 react-query 훅을 응집 → 서브-도메인별 cohesive 모듈로 분리하고, 공통 코어(`LEDGER_KEY` + 페이지-사이즈 clamp)는 leaf(`use-ledger-shared`)로 추출, 원본 경로는 `export *` barrel 로 안정 공개 표면 유지(TASK-PC-FE-099 erp 동형). leaf 는 도메인 모듈을 import 하지 않아 순환 불가. 공개 표면·런타임 동작 불변(import graph 재배치만).

# Edge Cases

- `LedgerOpsScreen.test.tsx`(53 테스트)는 훅을 barrel 경유 사용 → `export *` 가 동일 심볼을 노출하므로 무회귀.
- `use-ledger-ops-state.ts`가 import 하는 훅(useTrialBalance/usePeriods/usePeriod/useJournalEntry/useDiscrepancies/useDiscrepancy/useAccountBalance/useAccountEntries/useStatement/usePositionLots/useFxRates/useFxRateHistory/useRefreshFxRates 등) 전부 barrel re-export 로 동일 경로 유지.
- 모듈-private 심볼(`LEDGER_KEY`/`clampSize`/`clampFxHistoryLimit`)을 실수로 barrel 노출하면 공개 표면 확대 → `export *` 가 각 도메인 모듈의 export 만 전파하고 shared leaf 는 re-export 하지 않으므로 비공개 유지.
- F5 money 가드: 신규 파일도 `features/ledger-ops/` dir-walk grep 테스트에 자동 포함 → 변환 경로 미도입 확인(이동된 clamp 들은 page-size/row-count 정수 산술이며 money 아님).

# Failure Scenarios

- barrel 에서 도메인 모듈 누락 → importer 가 심볼 못 찾음(tsc RED) → 4개 모듈 전부 `export *`, tsc clean 으로 가드.
- shared leaf 가 도메인 모듈을 import 하면 순환 → leaf 는 `@/shared/lib/pagination` + `../api/types` 만 import(도메인 모듈 미import).
- `'use client'` 누락 시 RSC 빌드에서 client 훅 사용 에러 → 신규 5개 파일 전부 최상단 `'use client'`.
- money 변환 경로 도입 시 F5 위반 → 이동은 코드 verbatim, ledger-ops dir-walk grep 가드 green.
- lint(no-unused-vars: barrel 전환 후 잔여 import) / tsc(타입) RED → push 전 `tsc --noEmit` + `next lint` 필수(green 확인).

# Definition of Done

- [x] `use-ledger-ops.ts` barrel 화(`export *` × 4) + 신규 shared leaf + 4 도메인 훅 모듈, 공개 표면 불변
- [x] 모든 훅 name/signature/queryKey/옵션/onSuccess/호출순서 behavior-preserving
- [x] `'use client'` × 5 보존, barrel↔도메인↔shared 순환 없음, 모듈-private 심볼 비공개 유지
- [x] F5 money 가드 green(변환 경로 미도입), import-site 0 변경
- [x] `npx tsc --noEmit` clean / `npx next lint` clean / `npx vitest run ledger` green(19 파일·306 테스트), scope = console-web only
- [x] Acceptance Criteria 충족
- [x] Ready for review
