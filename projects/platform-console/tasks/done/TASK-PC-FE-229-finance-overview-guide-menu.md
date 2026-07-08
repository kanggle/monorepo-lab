# TASK-PC-FE-229 — Finance 도메인 메뉴 정석 정렬: 개요·가이드 신설 + `/finance`=개요 통일 + 계좌 표면 이동

**Status:** done
**Area:** platform-console / console-web · **New routes:** `app/(console)/finance` (개요로 전환) · `app/(console)/finance/guide` · **Moved route:** `app/(console)/finance` → `app/(console)/finance/accounts` (기존 계좌 표면) · **Nav:** `Finance ▸ 개요·가이드·계좌·원장`
**Analysis model:** Opus 4.8 · **Impl model:** Sonnet (frontend-engineer 위임) · **검증:** Opus 재검증.
**Supersedes:** TASK-PC-FE-160 (finance landing overview, PARKED/DECLINED). **Pattern:** TASK-PC-FE-225 (IAM nav 정석 재편성), TASK-PC-FE-222 (WMS 메뉴 정렬), TASK-PC-FE-188 (scm-guide 정적 가이드), TASK-PC-FE-072 (ledger 워터폴).

---

## ✅ 구현 완료 (RECONCILE 노트, 2026-07-08)

이 task는 **구현·머지 완료** 상태다. PR **#2325**(squash `63fdd26c8a42f0fe14568864b686f1f0a344f374`, base=main) 로 머지, CI **22 checks GREEN**(Testcontainers 통합·E2E·Frontend unit·lint 전부), 로컬 `pnpm lint`+`tsc --noEmit`+`vitest run` **2490/2490 GREEN**. 3-dim 머지 검증(state=MERGED / origin/main tip=squash commit / 0 fail)까지 통과.

> **task 파일 소급 복원**: 이 task 파일은 원래 authoring 브랜치(impl/pc-fe-225-iam-nav)에 커밋됐다가, 그 브랜치가 그대로 머지되지 않고 삭제되면서 유실됐다(코드는 #2325로 별도 머지·라이브). 큐 정직성을 위해 done/에 소급 복원(PC-FE-231 "architecture reconcile"과 동일 성격의 정리, PC-FE-232와 동반 PR).

**구현 시 스펙 대비 편차(반영됨):**
- "미마감(OPEN) 기간 수"는 `listPeriods`에 상태 필터가 없어 **정직한 bounded count**(첫 페이지 size 20, UI 캡션 명시)로 구현. "미해소 대사 차이 수"는 producer 필터 `meta.totalElements`(정확).
- nav 테스트는 `finance-nav.test.tsx`(제목) + `sidebar-drilldown.test.tsx`(drill-in longest-match) 양쪽 갱신. 신규 테스트는 이 repo 관례대로 `tests/unit/`에 중앙집중(colocated 아님).
- 개요 `/finance` 페이지가 legacy `?accountId=`를 읽어 `/finance/accounts?accountId=…` 직접 링크로 안내(하드 브레이크 최소화).

---

## Goal

Finance nav는 `운영(/finance) · 원장(/ledger)` 두 자식만 있어, **타 도메인이 모두 갖춘 `개요` · `가이드`가 없는 유일한 도메인**이었다. 도메인 루트 `/finance`도 개요가 아니라 계좌(운영) 표면이라 "루트=개요" 관례에서 이탈. 이 태스크는 Finance nav를 정석(orthodox) 파리티로 정렬한다:

1. **`/finance` = 개요**로 통일 — 원장 집계 타일 + 운영자 기본계좌 스냅샷.
2. **`/finance/guide` = 가이드** 신설 — account-service·ledger-service 구성과 4개 콘솔 화면 의미를 설명하는 정적 참조.
3. 기존 **계좌(운영) 표면을 `/finance/accounts`로 이동**(라벨 `운영` → `계좌`, testid `nav-finance-ops` → `nav-finance-accounts`). route·프록시·api·기능 무변경.

완료 후 Finance drill 자식 순서 = `개요 → 가이드 → 계좌 → 원장`.

## PC-FE-160 재개 정합성

PC-FE-160(`/finance` landing overview)은 PARKED/DECLINED("finance v1은 계좌 list/search GET이 없음 → count 개요 불가·synthetic ₩ 불가")였다. 이 task는 그 반려 사유를 우회한다: 개요를 계좌 집계가 아니라 **원장(ledger-service) browsable index read**(시산표 `inBalance`·미마감 기간·미해소 대사 차이·FX 신선도) + 운영자 본인 **기본계좌 단건**(`getFinanceDefaultAccountId`→`getAccount`/`getBalances`)로 구성 → 계좌 목록·synthetic ₩ 없이 정직하게 개요 성립. 사용자 승인(2026-07-08): 정석 파리티(`/finance`=개요) + 원장 집계 + 기본계좌 스냅샷.

## Scope (구현됨)

### 개요 (`/finance` = 개요)
- `features/finance-overview/api/overview-state.ts` — `getFinanceOverviewState(eligible)`: 원장 leg(시산표 `inBalance`/OPEN 기간/OPEN 대사 차이 total/FX 신선도) + 계좌 leg(`getFinanceDefaultAccountId` → 단건 `getAccount`/`getBalances`) 각자 try/catch **독립 degrade**(`ledgerDegraded`/`accountDegraded`), 공유 401→whole-session redirect, 403→forbidden. 계좌 목록/검색 endpoint 미호출(정직 제약, 테스트 단언).
- `features/finance-overview/components/FinanceOverviewScreen.tsx` — 원장 타일 + 기본계좌 스냅샷 카드(F5 `formatMoney`) + 계좌/원장/가이드 링크 + 기본계좌 미설정 안내.
- `features/finance-overview/index.ts`, 신규 `app/(console)/finance/page.tsx`(force-dynamic, `ledger/page.tsx` 워터폴 미러).

### 가이드 (`/finance/guide`)
- `features/finance-guide/{data.ts,components/FinanceGuideScreen.tsx,index.ts}` — 정적(도메인 서비스 맵 + 4화면 의미 + 규제상태/KYC/F5/복식부기/대사 개념), `app/(console)/finance/guide/page.tsx`.

### 계좌 이동 + nav
- `git mv` `app/(console)/finance/page.tsx` → `app/(console)/finance/accounts/page.tsx`(제목 "Finance 운영"→"Finance 계좌"), `features/finance-ops/**`·`/api/finance/accounts/**` 무변경.
- `shared/ui/ConsoleSidebarNav.tsx` Finance children → `개요(nav-finance-overview)/가이드(nav-finance-guide)/계좌(nav-finance-accounts)/원장(nav-ledger)`.
- 테스트: `finance-nav` + `sidebar-drilldown` 갱신, 신규 `finance-overview-state`/`FinanceOverviewScreen`/`FinanceGuideScreen`.
- 스펙: `console-integration-contract.md` §2.4.7.2 + `console-web/architecture.md` finance 라우트 트리.

## Out of Scope (유지됨)
- 계좌 list/search GET 도입·synthetic ₩·cross-account 집계(PC-FE-160 정직 제약). 개요는 원장 browsable read + 기본계좌 단건만.
- 자금 이동·분개 기표·기간 마감 write. 계좌 화면 내부 기능·프록시 변경(위치만 이동).

## Acceptance Criteria (전부 충족)
- **AC-1** `/finance`=개요 워터폴 렌더 + Nav 개요 최상단·딥링크 active. ✅
- **AC-2** 원장 집계 타일, 원장 leg 503→타일만 degrade. ✅
- **AC-3** 기본계좌 스냅샷(F5), 계좌 leg 503→스냅샷만 degrade, 미설정 안내. ✅
- **AC-4** `/finance/guide` 정적 렌더, Nav 가이드 개요 다음. ✅
- **AC-5** 계좌 `/finance/accounts` 이동·기능 회귀 없음·프록시 무변경. ✅
- **AC-6** `/finance` vs `/finance/accounts` longest-match 구분. ✅
- **AC-7** `pnpm lint`+`tsc`+`vitest` GREEN (2490/2490). ✅

## Related Specs / Contracts
- `specs/contracts/console-integration-contract.md` §2.4.7 / §2.4.7.2 · `specs/services/console-web/architecture.md` finance 라우트 트리.
- Producer(소비만): `finance-platform/specs/contracts/http/{account-api.md,ledger-api.md,reconciliation-api.md}`.
- Supersedes: `TASK-PC-FE-160`(PARKED, count-overview 형태는 여전히 DECLINED). 패턴: `TASK-PC-FE-232`(ERP 동일 구조).
