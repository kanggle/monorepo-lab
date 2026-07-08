# TASK-PC-FE-232 — ERP 도메인 메뉴 정석 정렬: 가이드 신설 + 개요 파리티(`/erp`=개요, 마스터 표면 이동)

**Status:** ready
**Area:** platform-console / console-web · **New routes:** `app/(console)/erp` (개요로 전환) · `app/(console)/erp/guide` · **Moved route:** `app/(console)/erp/page.tsx` (마스터) → `app/(console)/erp/masters/page.tsx` · **Nav:** `ERP ▸ 개요·가이드·마스터·통합 조회·결재함·위임`
**Analysis model:** Opus 4.8 · **Impl model:** Sonnet (frontend-engineer 위임) — 개요 fan-out은 PC-FE-161 `getErpMastersOverviewState` 카운트 로직 재사용+확장, 가이드는 `scm-guide` 정적 패턴, 마스터 이동은 라우트 relocation (분석=Opus 4.8 / 구현 권장=Sonnet) · **검증:** Opus 재검증.
**Supersedes(부분):** TASK-PC-FE-161 (erp masters 임베드 overview, done) — 임베드를 독립 `개요`로 승격(아래 §PC-FE-161 임베드 승격). **Pattern:** TASK-PC-FE-229 (Finance 메뉴 정석 정렬 — `/finance`=개요 + 계좌 표면 이동 + 가이드, #2325 merged)와 **동일 구조**. **Compatible:** PC-FE-230(결재함 알림 딥링크, done) · PC-FE-231(erp architecture.md reconcile, done).

---

## Goal

ERP는 **콘솔 6개 도메인 중 유일하게 `가이드`가 없고**, `개요`도 독립 메뉴가 아니라 **마스터 페이지 상단에 카운트 타일로 임베드**(PC-FE-161)돼 있어 도메인 루트 `/erp`가 개요가 아닌 마스터다. 최근 기능↔메뉴 배치 감사 웨이브(ecommerce PC-FE-221 / WMS 222~224 / SCM 220 / IAM 225 / Finance 229)에서 정립된 **`개요 → 가이드 → 기능` 파리티**의 마지막 비정렬 도메인.

이 태스크는 ERP nav를 정석 파리티로 정렬한다 (Finance PC-FE-229와 동일 패턴):

1. **`/erp` = 개요**로 통일 — 마스터 5종 카운트(부서·직원·직급·원가센터·거래처) + 결재 대기 건수 + 활성 위임 건수를 fan-out 집계한 독립 개요(ERP는 Finance와 달리 목록 GET이 있어 정직한 count 개요가 가능).
2. **`/erp/guide` = 가이드** 신설 — masterdata·approval·read-model·notification 서비스 구성과 6개 콘솔 화면의 의미를 설명하는 정적 참조(iam/wms/scm/ecommerce/finance 가이드와 동일 패턴).
3. 기존 **마스터 표면을 `/erp/masters`로 이동**(라벨 `마스터` 유지, testid `nav-erp-masters` 유지·route만 이동). 임베드된 overview 슬롯은 제거하고 독립 개요로 승격.

완료 후 ERP drill 자식 순서 = `개요 → 가이드 → 마스터 → 통합 조회 → 결재함 → 위임`.

## PC-FE-161 임베드 승격 (근거)

PC-FE-161은 ERP overview를 **별도 `개요` 대신 마스터 페이지에 임베드**했다("single-route masters screen — PC-FE-168 deviation"). 그 근거("마스터가 단일 라우트라 개요를 얹는다")는 당시엔 합리적이었으나, 이후 웨이브에서 **모든 도메인이 독립 `개요` + 루트=개요로 표준화**(Finance PC-FE-229가 계좌 표면을 `/finance/accounts`로 빼고 `/finance`=개요로 만든 것과 동일)됨에 따라 ERP만 남은 예외가 됐다. 사용자 승인(2026-07-08): ERP도 독립 개요로 승격 + 가이드 신설.

- 승격은 **폐기가 아니라 relocation+확장**: PC-FE-161의 `getErpMastersOverviewState`(5 masterdata count, `meta.totalElements`·`?asOf` 스레딩, per-cell degrade) 로직을 독립 개요 state로 옮기고, 결재 대기·활성 위임 count를 **추가**(ERP는 approval inbox·delegations 목록 GET이 `meta.totalElements`를 주므로 신규 producer endpoint 불필요 — ADR-MONO-017 D3.B 동일 원칙).
- 마스터 페이지(`/erp/masters`)는 임베드 슬롯을 떼고 순수 master 목록 화면으로 환원.

**AC-0 (착수 전 검증, REAL-GAP 규율):** ① `features/erp-ops/api/overview-state.ts`의 `getErpMastersOverviewState`가 존재하고 5 count를 `meta.totalElements`로 뽑는지, ② approval inbox(`/api/erp/approval/inbox`)·delegations(`/api/erp/read-model/delegations` 또는 `/api/erp/approval/delegations`) 목록 read가 `meta.totalElements`(또는 count 가능한 envelope)를 주는지, ③ 현재 마스터 화면이 `/erp`(=`app/(console)/erp/page.tsx`, `getErpMastersState`/`ErpMastersScreen`)이고 `erp-guide`·`/erp/guide`가 실제로 부재한지. 하나라도 불일치 시 STOP.

## 배경 사실 (검증됨, 2026-07-08)

- **ERP nav 현재**: `마스터(/erp) · 통합 조회(/erp/orgview) · 결재함(/erp/approval) · 위임(/erp/delegation)` — 개요·가이드 없음(`shared/ui/ConsoleSidebarNav.tsx` ERP children). `/erp`는 PC-FE-076에서 마스터 자식으로 이중화됨.
- **개요 임베드**: PC-FE-161이 `getErpMastersOverviewState` + `ErpMastersOverview`로 5 masterdata count를 마스터 페이지 상단 슬롯에 렌더(비-링크 stat 타일). 독립 `개요` 라우트/nav 없음.
- **가이드 부재**: `features/erp-guide/` 없음, `app/(console)/erp/guide/` 없음(glob 확인). 타 5도메인은 전부 `features/*-guide` + `/*/guide` 보유 → ERP만 유일 결여.
- **producer 커버리지 완전**: erp-platform HTTP 계약 4개(`read-model-api.md`→통합 조회 · `masterdata-api.md`→마스터 · `approval-api.md`→결재함+위임 · `notification-api.md`→벨 aggregator 통합, ADR-043 P2 DECLINED). ecommerce 정산 같은 "통째 공백" 없음 — 이 task는 순수 **메뉴 구조 파리티**(신규 도메인 표면 없음).
- **콘솔 프록시 재사용**: `/api/erp/masterdata/**`(부서·직원·직급·원가센터·거래처) · `/api/erp/approval/**`(inbox·requests·delegations) · `/api/erp/read-model/**`(employees·delegations) 전부 존재 — 개요 fan-out은 이들 목록 read의 `meta.totalElements`(`?size=1`)만 소비, 신규 배선 없음.
- **최근 인접 작업**: PC-FE-230(결재함 알림 딥링크 — `/erp/approval` 라우트 무변경이라 호환) · PC-FE-231(console-web architecture.md erp 섹션을 실제 표면과 reconcile — 이 task의 architecture.md 편집은 그 reconcile 위에 개요/가이드/마스터 이동을 얹어 일관 유지).

## Scope

### 신규 — 개요 (`/erp` = 개요)
1. **`app/(console)/erp/page.tsx`를 개요로 교체** — 서버 컴포넌트(`force-dynamic`). 자격 워터폴은 기존 erp 페이지 패턴 미러(registry `productKey='erp'` → registryDegraded → notEligible → forbidden → degraded → happy; 401→`redirect('/login')`). `?asOf=` 쿼리 파라미터 유지(E3, masterdata effective-dating). 제목 "ERP 개요". `getErpOverviewState(eligible, asOf)` seed 후 `<ErpOverviewScreen … />`.
2. **`features/erp-ops/api/overview-state.ts` 확장** — 기존 `getErpMastersOverviewState`를 독립 개요 state `getErpOverviewState(eligible, asOf)`로 승격/확장: 마스터 5 count + **결재 대기 건수**(approval inbox `meta.totalElements`) + **활성 위임 건수**(delegations `meta.totalElements`). 각 count leg는 per-cell degrade(403→권한없음/503→점검필요/count만 실패), 401→whole-session `redirect('/login')`, notEligible 단락. `?asOf=`는 masterdata count leg에만 스레딩(approval/delegation은 asOf 무관).
3. **`features/erp-ops/components/ErpOverviewScreen.tsx`** — 마스터 count 타일 + 결재 대기/활성 위임 타일 + `마스터(/erp/masters)`·`결재함(/erp/approval)`·`위임(/erp/delegation)`·`통합 조회(/erp/orgview)`·`가이드(/erp/guide)` 링크. 헤딩 `erp-overview-heading`. (PC-FE-161 `ErpMastersOverview` 타일 렌더 로직 재사용.)

### 신규 — 가이드 (`/erp/guide`)
4. **`app/(console)/erp/guide/page.tsx`** — 정적(`force-dynamic` 불필요). `<ErpGuideScreen />` 렌더. `scm/guide/page.tsx` 미러.
5. **`features/erp-guide/data.ts` + `components/ErpGuideScreen.tsx` + `index.ts`** — 타입 있는 정적 배열 + 정적 화면. 내용: erp-platform 도메인 서비스 맵(masterdata-service · approval-service · read-model · notification) + 6개 콘솔 화면(개요·마스터·통합 조회·결재함·위임·가이드) 의미 + 개념(effective-dating `asOf` · 결재 상태머신 · 위임 grant/scope · org read-model projection · 알림=벨 aggregator 통합). SoT 주석: `erp-platform/specs/contracts/http/{masterdata-api.md,approval-api.md,read-model-api.md,notification-api.md}` + 콘솔 소비 타입(`features/erp-ops/api/types/**`). `scm-guide/data.ts` 정책(구조만 테스트, 텍스트는 사람이 스펙과 대조).

### 수정 — 마스터 표면 이동 + nav
6. **마스터 라우트 이동**: 기존 `app/(console)/erp/page.tsx`(마스터)를 **`app/(console)/erp/masters/page.tsx`로 `git mv`**(히스토리 보존) 후, 임베드 overview 슬롯 제거(`ErpMastersScreen`의 `overview` 슬롯 prop + 페이지의 overview fan-out 호출 삭제 — 로직은 개요 state로 이관). 마스터 화면 제목/목록/필터/write·retire·move-parent 등 기능은 무변경. `/api/erp/masterdata/**` 프록시 무변경.
7. **`shared/ui/ConsoleSidebarNav.tsx`** — ERP `children` 재편성:
   ```
   { href: '/erp',          label: '개요',     testid: 'nav-erp-overview' },
   { href: '/erp/guide',    label: '가이드',   testid: 'nav-erp-guide' },
   { href: '/erp/masters',  label: '마스터',   testid: 'nav-erp-masters' },
   { href: '/erp/orgview',  label: '통합 조회', testid: 'nav-erp-orgview' },
   { href: '/erp/approval', label: '결재함',   testid: 'nav-erp-approval' },
   { href: '/erp/delegation', label: '위임',   testid: 'nav-erp-delegation' },
   ```
   순서 `개요 → 가이드 → 마스터 → 통합 조회 → 결재함 → 위임`. `nav-erp`(부모 토글) 유지. **주의**: `activeHref` longest-match가 `/erp`(개요)를 `/erp/masters`·`/erp/orgview` 등에서 오점등시키지 않도록(Finance `/finance` vs `/finance/accounts`와 동일) — nav 테스트로 가드.
8. **`features/erp-ops/index.ts` · `features/erp-guide/index.ts`** — screen·state·타입 export.
9. **테스트**:
   - `tests/unit/erp-nav.test.tsx` — 새 자식 6개 active·딥링크(`/erp`→ERP drill 오픈 + 개요 active; `/erp/masters`→마스터 active + 개요 NOT active; 나머지 active 유지).
   - 신규 `erp-overview-state.test.ts`(notEligible/eligible happy/403/503/401 + 마스터·결재·위임 count 독립 degrade + `?asOf` 스레딩), `ErpOverviewScreen.test.tsx`(타일 렌더·링크), `ErpGuideScreen.test.tsx`(섹션/행 구조).
   - **기존 마스터 테스트 경로 갱신**: `/erp`(마스터)를 참조하던 테스트를 `/erp/masters`로 갱신. 임베드 overview를 단언하던 기존 `erp-masters-overview` 테스트는 독립 개요 테스트로 이관/갱신. 회귀 없음 확인.
10. **스펙**: `specs/contracts/console-integration-contract.md`(§2.4.8 erp 서브섹션 — nav 개요/가이드/마스터/통합 조회/결재함/위임, 마스터 `/erp/masters` 이동, 개요=masterdata+approval+delegation fan-out) + `specs/services/console-web/architecture.md`(erp 라우트 트리: `/erp`=개요, `/erp/masters`, `/erp/guide` — **PC-FE-231 reconcile 위에 일관 반영**).

## Out of Scope (의도적 유지)
- **신규 producer endpoint / `/summary`** — 개요는 기존 목록 read의 `meta.totalElements`(`?size=1`)만 소비(PC-FE-161 ADR-MONO-017 D3.B 원칙 유지).
- **결재/위임/통합 조회/마스터 화면 내부 기능·write 변경** — nav 순서 + 개요/가이드 신설 + 마스터 route 이동만. approval transition·delegation grant/revoke·masterdata write는 무변경.
- **알림(notification)을 독립 ERP 메뉴로 재노출** — 벨 aggregator 통합 유지(ADR-043 P2 DECLINED, PC-FE-137/138). 재발굴 금지.
- **PC-FE-230 결재함 알림 딥링크 로직** — `/erp/approval` 라우트 무변경이라 무접촉(호환).

## Acceptance Criteria
- **AC-0** (§PC-FE-161 임베드 승격 §AC-0) 착수 전 배경 사실 3건 재확인 통과.
- **AC-1** `/erp`가 자격 워터폴을 갖고 "ERP 개요" 독립 화면 렌더. Nav `ERP ▸ 개요` 최상단, 딥링크·부모 클릭으로 ERP drill 자동 오픈 + 개요 active.
- **AC-2** 개요가 마스터 5 count(부서·직원·직급·원가센터·거래처, `meta.totalElements`, `?asOf` 스레딩) + 결재 대기 건수 + 활성 위임 건수를 표시. 각 count leg per-cell degrade(한 leg 503이 다른 타일·전체 개요를 blank 시키지 않음).
- **AC-3** `/erp/guide`가 정적 ERP 가이드(도메인 서비스 맵 + 6화면 의미 + effective-dating/결재/위임/read-model/알림 개념)를 게이트 없이 렌더. Nav `ERP ▸ 가이드` 개요 다음.
- **AC-4** 마스터 표면이 `/erp/masters`(라벨 `마스터`, testid `nav-erp-masters`)로 이동, 기능 회귀 없음(목록/필터/write/retire/move-parent 그대로, 임베드 overview 슬롯만 제거). `/api/erp/masterdata/**` 프록시 무변경.
- **AC-5** `/erp`(개요)와 `/erp/masters`(마스터)가 longest-match로 정확히 구분(개요가 하위 경로에서 오점등 안 됨). 통합 조회/결재함/위임 active 유지.
- **AC-6** `pnpm lint` + `tsc --noEmit` + `vitest`(erp 전체) GREEN. (`[[env_console_web_local_verify_needs_lint]]` — lint 누락 시 no-unused-vars가 CI에서만 적발.)

## Edge Cases
- masterdata count 일부만 실패(예: 거래처 503, 나머지 200) → 성공 타일은 값, 실패 타일만 "일시적 불가"(per-cell, PC-FE-161 유지).
- 결재 inbox·위임 count leg가 masterdata와 독립 degrade(approval-service 503이어도 masterdata count는 렌더).
- `?asOf=` 부분/미래 일자 → masterdata count에만 verbatim 스레딩, approval/delegation count는 asOf 무관(현재 시점).
- `/erp` 딥링크가 재편성 후 개요로 진입 — 기존 `/erp`(마스터) 북마크는 개요로 랜딩; 마스터는 `/erp/masters`로 이동했음을 개요 링크에서 안내(하드 브레이크 최소화, PC-FE-229 계좌 이동과 동일 처리).
- 권한 없는 운영자 — 기존 erp 게이팅 관례 그대로(notEligible 블록).

## Failure Scenarios
- 마스터 이동 중 `/api/erp/masterdata/**` 프록시나 masters write/retire 로직을 실수로 변경 → 마스터 기능 회귀. 프록시·write는 무변경, 페이지 파일 이동 + 임베드 슬롯 제거만. 마스터 테스트가 `/erp/masters`에서 GREEN 유지로 가드.
- nav `/erp`(개요)가 `/erp/masters` 등에서 오점등 → longest-match 회귀. `erp-nav` active 테스트가 `/erp/masters`에서 마스터만 active 단언.
- 개요 count leg를 단일 try/catch로 묶어 한 503이 개요 전체 degrade → AC-2 per-cell 위반. state 테스트가 "거래처 503 + 나머지 200 → 나머지 타일 렌더" 케이스로 가드.
- 마스터 임베드 overview를 제거하면서 `getErpMastersOverviewState` 참조가 masters 페이지에 남음 → tsc/lint no-unused 또는 dead import. 개요로 이관 후 masters 페이지에서 완전 제거 확인.
- 기존 마스터/overview 테스트가 `/erp` 경로·임베드 슬롯을 참조 → stale. `vitest run` 전체 + `pnpm lint`로 확인(`[[env_console_web_local_verify_needs_lint]]`).

## Related Specs / Contracts
- `specs/contracts/console-integration-contract.md` §2.4.8 (erp 서브섹션) — nav 재편성 + 마스터 `/erp/masters` 이동 + 개요 fan-out 집계 소스.
- `specs/services/console-web/architecture.md` — erp 라우트 트리(`/erp`=개요, `/erp/masters`, `/erp/guide`), PC-FE-231 reconcile 위에 일관 반영.
- Producer(소비만, 계약 변경 없음): `erp-platform/specs/contracts/http/{masterdata-api.md,approval-api.md,read-model-api.md,notification-api.md}`.
- Supersedes(부분): `tasks/done/TASK-PC-FE-161-erp-landing-overview-snapshot.md`(임베드 → 독립 개요 승격).
- 패턴 참조: `tasks/*/TASK-PC-FE-229-finance-overview-guide-menu.md`(동일 구조, #2325 merged) · `features/scm-guide/**`(가이드) · `features/erp-ops/api/overview-state.ts`(재사용) · `features/erp-ops/api/erp-state.ts`(마스터 워터폴).
