# TASK-PC-FE-234 — tolerant "unknown-enum label" 헬퍼 shared 추출 (중복 10곳 통합)

**Status:** done
**Area:** platform-console / console-web · **New:** `src/shared/lib/tolerant-label.ts` · **Adopt:** ledger-ops(5)·finance-ops(2)·finance-overview(1) call sites; erp-ops(기존 보유)는 shared로 재배선
**Analysis model:** Opus 4.8 · **Impl model:** Sonnet (refactoring-engineer 위임) — 순수 함수 추출, 행동 불변 · **검증:** Opus 재검증.
**Source:** finance/erp 리팩토링 진단(2026-07-09) **P1**. **Pattern:** Reduce Duplication(3+ 재구현 → shared/lib 추출, `formatDateTime`/`clampPageSize`와 동일 위치).

---

## Goal

*"값이 known-enum 배열에 있으면 그대로, 없으면 `` `${value} (unknown)` `` 류로 렌더"* 하는 **tolerant label 헬퍼가 최소 10곳에서 각자 재구현**돼 있다. erp-ops는 이미 `features/erp-ops/api/types/common.ts`에 `labelForUnknown<T>()`(nullable-aware)로 중앙화했으나, ledger-ops·finance-ops·finance-overview는 채택하지 않고 같은 4~6줄 삼항을 반복 작성했다.

이 헬퍼를 **`shared/lib/tolerant-label.ts`로 추출**(erp-ops의 nullable-aware 시그니처가 superset)하고, 재구현 지점들이 이를 import하도록 재배선한다. **순수 함수 — 행동 불변**(feature-local 경계 위반 아님: shared/lib는 도메인 결합 0의 generic 유틸 위치, `formatDateTime`·`clampPageSize`와 동일).

## 중복 지점 (진단 근거)
- `features/erp-ops/api/types/common.ts` — `labelForUnknown<T>()` **이미 export**(nullable-aware). → shared 재-export 또는 shared import로 전환.
- `features/ledger-ops/components/`: `DiscrepancyQueue.tsx`(`typeLabel`/`statusLabel`) · `PeriodDetail.tsx` · `PeriodsTable.tsx` · `DiscrepancyDetail.tsx` · `JournalEntryDetail.tsx` — 5곳 로컬 재구현.
- `features/finance-ops/components/`: `TransactionsTable.tsx`(`labelForUnknown`) · `AccountDetail.tsx` — 2곳.
- `features/finance-overview/components/FinanceOverviewScreen.tsx` — 1곳 인라인 삼항.

## Scope
1. **신규 `src/shared/lib/tolerant-label.ts`** — erp-ops `common.ts`의 `labelForUnknown<T extends string>(value, known)`(nullable-aware superset)을 verbatim 이동, JSDoc 포함. 도메인 결합 0.
2. **재배선** — 위 지점들이 로컬 정의/인라인 삼항을 지우고 `@/shared/lib/tolerant-label`에서 import. `erp-ops/api/types/common.ts`는 shared를 re-export(기존 `from '../api/types'` 소비처 무변경)하거나 직접 shared import로 전환.
3. **테스트** — `shared/lib/tolerant-label.test.ts`(known→as-is, unknown→fallback, null/undefined nullable-aware). 기존 call-site 테스트(각 "(unknown)" fallback 단언)는 그대로 GREEN 유지.

## ⚠️ 핵심 제약 — per-site 출력 문자열 **정확 보존**
각 지점의 **fallback 렌더 문자열이 byte-identical한 경우에만 통합**한다. 착수자는 각 call site의 실제 출력(예: `` `${v} (unknown)` `` vs `(미지원)` vs 대문자화 등)을 대조해:
- 완전 동일 → shared 헬퍼로 치환.
- 미묘하게 다름(fallback 포맷·라벨 매핑이 다름) → 헬퍼를 파라미터화하거나 **그 지점은 통합에서 제외**(억지 통일 금지). 제외분은 리포트에 명시.
행동 변화 0이 절대 조건 — vitest가 각 fallback 문자열을 단언한다.

## Acceptance Criteria
- **AC-1** `shared/lib/tolerant-label.ts` 신설, nullable-aware `labelForUnknown`.
- **AC-2** byte-identical 지점 전부 shared import로 재배선, 로컬 재구현/인라인 삼항 제거. 미통합 지점은 리포트에 근거와 함께 명시.
- **AC-3** 각 call site 렌더 출력 불변(known/unknown/null 모두) — 기존 테스트 GREEN.
- **AC-4** `pnpm lint`(no-unused — 로컬 정의 제거 후 dangling import 없어야) + `tsc --noEmit` + `vitest`(전체) GREEN.

## Out of Scope
- 라벨 매핑 테이블(known 배열)·tone 매퍼 통합 — 도메인별로 다름, 이 task는 tolerant-fallback 헬퍼만.
- 다른 진단 후보(ApprovalDetail hook-split P2, guide atoms cross-domain) — 별개 task.

## Failure Scenarios
- 통일 과정에서 한 지점의 fallback 문자열이 미세 변경 → 출력 회귀. per-site 대조 + call-site 테스트가 가드.
- 로컬 정의 제거 후 import 누락 → tsc/lint no-unused. 전체 lint+tsc가 가드.
- `common.ts` re-export 전환 시 `from '../api/types'` 배럴 경로 누락 → erp 소비처 tsc 에러. 전체 tsc+vitest가 가드.

## Related
- 진단: finance/erp 리팩토링 스캔(2026-07-09) P1.
- 위치 선례: `shared/lib/{datetime,page-size}.ts`(generic 유틸).
