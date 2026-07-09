# TASK-PC-FE-233 — ledger-ops `api/types.ts` god-file 분할 (810줄 → 도메인별 모듈)

**Status:** done
**Area:** platform-console / console-web · **Target:** `src/features/ledger-ops/api/types.ts` (810줄) → `src/features/ledger-ops/api/types/` 모듈 디렉터리
**Analysis model:** Opus 4.8 · **Impl model:** Sonnet (refactoring-engineer 위임) — 순수 기계적 분할, 행동 불변 · **검증:** Opus 재검증.
**Pattern:** god-file split 시리즈(TASK-PC-FE-102 ledger-api-god-file-split, PC-FE-109 erp-masters-types-split 등)와 동일 렌즈. **후속(별개):** finance/erp 리팩토링 진단 리포트의 다른 P-항목(개별 task).

---

## Goal

`features/ledger-ops/api/types.ts`는 **810줄**로, 원장 도메인의 서로 다른 concept(money/fx-primitives · shared meta · trial-balance · journal-entry · period · reconciliation/discrepancy · statement · fx-rates · account balance/entries)의 zod 스키마 + 파생 타입 + enum + tone 매퍼를 한 파일에 담은 god-file이다. 각 concept 블록은 이미 `// ---` 섹션으로 구분돼 있어 **응집도 높은 도메인별 모듈로 깔끔히 분할**된다.

이 task는 그 파일을 **`api/types/` 디렉터리의 concept별 모듈 + 재-export barrel(`index.ts`)** 로 분할한다. **순수 구조 변경 — 행동/타입 표면 불변, import 경로 무변경**(`from './types'` / `from '../api/types'` 등은 `types/index.ts`로 그대로 resolve).

## Scope

### 분할 (제안 경계 — 착수자가 실제 섹션 경계로 확정)
`api/types.ts` → `api/types/`:
- `money.ts` — `Money`/`MoneySchema` · `DEFAULT_CURRENCY_SCALES` · `formatMoney`(있으면) · `ExchangeRate` · 공유 `LedgerMeta`.
- `trial-balance.ts` — `TrialBalanceAccount(Schema)` · `TrialBalance(Schema)`.
- `journal.ts` — `KNOWN_SOURCE_TYPES`/`KNOWN_DIRECTIONS` · `JournalSource`/`JournalLine`/`JournalEntry` (+Schema).
- `period.ts` — `KNOWN_PERIOD_STATUSES` · `PeriodSnapshot(Account)` · `Period`/`PeriodsResponse` (+Schema).
- `reconciliation.ts` — `KNOWN_DISCREPANCY_TYPES/STATUSES` · `DiscrepancyResolution`/`Discrepancy`/`Statement` (+Schema) + 관련 tone/enum 매퍼.
- `fx.ts` — FX rate/feed 스키마·타입(존재 시).
- `account.ts` — 원장 계정 balance/entries 스키마·타입(존재 시).
- `index.ts` — 위 모듈 전부 `export *` 재-export barrel. **기존 `./types` import 경로가 이 barrel로 그대로 resolve되어 import 문 무변경.**

> 실제 export 목록·경계는 착수자가 파일을 읽고 확정(위는 810줄 섹션 헤더 기반 초안). concept 간 상호 의존(예: 여러 스키마가 `Money`/`LedgerMeta` 참조)은 하위 모듈이 `money.ts`를 import.

### 무변경
- 모든 소비처(`ledger-*-api.ts`, `ledger-ops` 컴포넌트/훅/state, 테스트)의 import 문 — barrel resolve로 그대로 동작. 부득이 경로 변경이 필요하면 최소화하고 목록화.
- 스키마 로직·정규식·tolerant-parser·tone 매핑·`formatMoney` 등 **모든 런타임 동작 불변**.

## Acceptance Criteria
- **AC-1** `api/types.ts` 810줄이 `api/types/` concept별 모듈 + `index.ts` barrel로 분할, 각 모듈은 단일 concept 응집.
- **AC-2** 기존 `from '.../types'` import가 전부 그대로 동작(barrel). 소비처 import 문 변경 0(또는 불가피 시 최소·목록화).
- **AC-3** 행동/타입 표면 불변 — 추가·삭제·이름변경된 export 0(순수 이동). `git diff`가 이동+barrel 외 로직 변경 없음.
- **AC-4** `pnpm lint` + `tsc --noEmit` + `vitest`(ledger 전체 + 전체) GREEN. (`[[env_console_web_local_verify_needs_lint]]`)

## Out of Scope
- 스키마 병합·필드 추가·enum 확장 등 **의미 변경 일체**(순수 분할).
- ledger-ops의 다른 파일(components/hooks/state) 분할 — 별개 task(진단 리포트 참조).
- finance/erp 다른 리팩토링 후보 — 별개 task.

## Failure Scenarios
- 순환 import(모듈 A↔B 상호 참조) → `money.ts` 등 공유 기반을 leaf로 두고 단방향 의존 유지. tsc가 가드.
- barrel 누락 export → 소비처 tsc 에러. `tsc --noEmit` + 전체 vitest가 가드.
- 이동 중 스키마 로직 실수 변경 → AC-3 위반. `git diff`로 로직 무변경 확인 + 전체 vitest.

## Related
- 패턴: `tasks/done/TASK-PC-FE-102-ledger-api-god-file-split.md`, `TASK-PC-FE-109-erp-masters-types-split.md`.
- 후속 진단: finance/erp 리팩토링 전체 스캔 리포트(2026-07-09).
