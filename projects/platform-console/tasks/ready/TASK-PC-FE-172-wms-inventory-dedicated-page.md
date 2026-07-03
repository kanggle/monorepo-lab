# TASK-PC-FE-172 — WMS "재고" 전용 메뉴/페이지 분리 + 미노출 재고 기능 표면화

**Status:** ready
**Area:** platform-console / console-web · **New route:** `app/(console)/wms/inventory` · **Nav:** `WMS ▸ 재고`
**Follows:** TASK-PC-FE-170 (개요에서 부적합 요소 정리) — 같은 원칙으로 재고 조회 테이블을 개요에서 전용 페이지로 분리.
**Analysis model:** Opus 4.8 · **Impl model:** Sonnet (frontend-engineer 위임) · **검증:** Opus 재검증.
**Implemented:** branch `task/pc-fe-172-wms-inventory-dedicated-page`. `pnpm lint` + `tsc --noEmit` + `vitest`(wms 9 files / 102 tests) 독립 green.

## Goal

WMS 개요(`/wms`)는 현재 개요 스냅샷 밴드 + **재고 조회 테이블(필터·페이징)** + 배송 테이블 + 알림 테이블을 한 페이지에
쌓아 렌더한다. 필터·페이징을 갖춘 **재고 조회 테이블은 "개요"(glance)에 부적합**하다(PC-FE-170 알림-타일 정리와
동일 원칙). 이를 **전용 `/wms/재고` 페이지로 분리**하고, 그 과정에서 producer가 제공하지만 지금 콘솔에 안 나온
재고 기능(미노출 필터·컬럼 + `getInventoryByKey` 상세 조회)을 표면화한다.

분리 후 개요는 재고를 **스냅샷 count 타일(WmsOverview, PC-FE-166/170)로만** 보여준다(글랜스 유지) — 재고 count
타일은 개요에 남고, 전체 조회 테이블만 전용 페이지로 이동.

## 배경 사실 (검증됨)

- WMS는 이미 단일 라우트가 아님: `/wms`(개요) + `/wms/outbound`(출고). Nav에 `WMS ▸ 개요/출고` 드릴인 부모 존재
  (`ConsoleSidebarNav.tsx`). "재고" leaf 추가는 1줄.
- ecommerce가 area별 서브라우트 컨벤션(`products/`,`orders/` …)을 정착 — `wms/inventory/page.tsx`가 정확히 그 형태.
- `getInventoryByKey`(GET `/dashboard/inventory/by-key`, 복합키 location+sku+lot) + `getThroughput`는 API 코드가 이미
  있으나 **소비처 0**. 상세 조회는 복합키라 단일 `[id]` 라우트에 안 맞음 → **행별 "상세" 인라인 패널**로 활성화(정석).
- 미노출 재고 필터: `locationId`·`lotId`·`minOnHand`(파라미터·프록시는 이미 지원, 폼만 없음).
- 미노출 재고 컬럼: `damagedQty`(손상)·`lastAdjustedAt`(최근 조정)(스키마엔 있음, 테이블 미표시).

## Scope

### 신규
1. **`app/(console)/wms/inventory/page.tsx`** — 서버 컴포넌트. 자격 워터폴은 `wms/outbound/page.tsx`를 그대로 미러
   (registry `productKey='wms'` 자격 → registryDegraded → notEligible → forbidden → degraded → happy). 제목 "WMS 재고".
   `getWmsInventoryState(eligible)`로 seed 후 `<WmsInventoryScreen inventory=… lagSeconds=… />` 렌더.
2. **`features/wms-ops/api/inventory-state.ts`** — `getWmsInventoryState(eligible)` → `{ inventory, notEligible, forbidden,
   degraded, lagSeconds }`. `wms-state.ts` 패턴 미러(단일 `listInventory({page:0,size:20})` read; 401→whole-session
   `redirect('/login')`, 403→forbidden, `WmsUnavailableError`/기타→degraded).
3. **`features/wms-ops/components/WmsInventoryScreen.tsx`** — `'use client'` 화면. 재고 필터 state(확장) + query +
   페이지네이션 + **상세 조회 패널** state를 소유. lag 배너 + 확장 `WmsInventoryTable` + 상세 패널 렌더. 제목 헤딩
   `wms-inventory-heading`("WMS 재고").
4. **`app/api/wms/inventory/by-key/route.ts`** — GET 프록시. `app/api/wms/inventory/route.ts`를 미러하되
   `getInventoryByKey({locationId,skuId,lotId})` 호출, `result.data` 반환, `mapWmsError`(404 포함). READ-ONLY.
5. **`useWmsInventoryByKey`** 훅(`hooks/use-wms-ops.ts`에 추가) — `/api/wms/inventory/by-key?…` GET →
   `InventoryRowSchema.parse`. `enabled` 게이트(선택된 키가 있을 때만). 404는 "재고 없음"으로 구분 가능하게 error 전달.
6. **테스트** `WmsInventoryScreen.test.tsx`(신규): 확장 필터(위치/로트/최소보유 포함) submit·컬럼(손상/최근조정 포함)·
   페이지네이션·상세 조회(성공/404)·forbidden/degraded/empty. `wms-inventory-state.test.ts`(신규):
   `getWmsInventoryState` not-eligible/eligible/403/503/401. (선택) `wms-proxy.test.ts`에 by-key 프록시 케이스 추가.

### 수정
7. **`features/wms-ops/components/wms-ops-helpers.ts`** — `InvFilterState`에 `locationId:string`·`lotId:string`·
   `minOnHand:string`(폼 입력은 문자열, submit 시 숫자 파싱) 추가. `EMPTY_INV_FILTERS` 확장.
8. **`features/wms-ops/components/WmsInventoryTable.tsx`** — (a) 필터 폼에 위치 ID·로트 ID·최소 보유(number) 입력
   추가(testid `wms-inv-filter-location`/`-lot`/`-minonhand`); (b) 테이블에 컬럼 손상(`damagedQty`)·최근 조정
   (`lastAdjustedAt`, `formatDateTime`, null→"—") 추가; (c) 각 행에 "상세" 버튼(`onSelect(row)`, testid
   `wms-inv-detail-<i>`). props에 `onSelect`, 신규 필터 필드 fid 추가. 개요/재고 양쪽에서 쓰이지 않고 **재고 화면
   전용**이 되므로 필드 확장 자유.
9. **`features/wms-ops/components/WmsOpsScreen.tsx`** — 재고 섹션 제거: `WmsInventoryTable` 렌더 + inv filter/query
   state + `useWmsInventory` 사용 제거. `inventory` prop 제거. 부제 "재고 스냅샷 · 알림" → "배송 · 알림"(재고 문구
   제거). 배송·알림·ack 다이얼로그·lag 배너·overview 슬롯은 유지.
10. **`features/wms-ops/api/wms-state.ts`** — `getWmsSectionState`에서 `listInventory` fan-out 및 `WmsSectionState.inventory`
    제거(알림+배송만 seed). lag는 alerts/shipments 기준.
11. **`app/(console)/wms/page.tsx`** — `WmsOpsScreen`에 `inventory` 전달 제거; degraded 가드 `!state.inventory` 제거.
12. **`shared/ui/ConsoleSidebarNav.tsx`** — wms `children`에 `{ href: '/wms/inventory', label: '재고', testid:
    'nav-wms-inventory' }`를 개요와 출고 사이에 추가(순서: 개요 → 재고 → 출고).
13. **`features/wms-ops/index.ts`** — `WmsInventoryScreen`, `getWmsInventoryState` + `WmsInventorySectionState` 타입 export.
14. **테스트 조정**: `wms-nav.test.tsx`(WmsOpsScreen render에서 `inventory` prop 제거 + `wms-inv-empty` 단언 제거;
    배송/알림 empty만), `WmsOpsScreen.test.tsx`(재고 전용 케이스는 신규 `WmsInventoryScreen.test.tsx`로 이관, 재고
    prop/단언 제거; 배송/알림 케이스 유지), `wms-state.test.ts`(inventory 단언 제거, 배송+알림만).
15. **스펙**: `specs/contracts/console-integration-contract.md`(§ 2.4.5 wms 재고 read 서브섹션 — 재고 조회는 전용
    `/wms/inventory` 표면; by-key 상세 + 미노출 필터/컬럼 표면화; 개요는 count 타일만) + `specs/services/console-web/
    architecture.md`(wms 라우트 트리에 `wms/inventory` 추가, 개요에서 재고 테이블 제거 반영).

## Out of Scope (의도적 유지)

- **배송/알림 테이블은 개요에 유지** — 이 task는 "재고"만 분리. 개요를 순수 스냅샷으로 만드는 후속(배송/알림도 각자
  라우트로)은 별도 판단(⚠️ 무단 확장 금지). 잔여 부조화(개요에 배송/알림 full 테이블)는 후속 후보로만 기록.
- **`getThroughput`/adjustments/ASN 뷰**는 이 task 범위 아님(범위 C). by-key 상세까지만.
- 재고 쓰기(조정) — producer에 엔드포인트 없음. 읽기/조회 전용 유지.

## Acceptance Criteria

- [x] **AC-1** `/wms/inventory` 라우트가 자격 워터폴(notEligible/forbidden/degraded/happy)을 갖고 "WMS 재고" 화면을
  렌더. Nav `WMS ▸ 재고`(개요와 출고 사이) 클릭으로 진입.
- [x] **AC-2** 재고 화면 필터에 창고/SKU/저재고 + **위치 ID·로트 ID·최소 보유**가 있고 submit이 각 파라미터로 재조회.
- [x] **AC-3** 재고 테이블에 기존 7컬럼 + **손상(damagedQty)·최근 조정(lastAdjustedAt)**이 표시(null→"—").
- [x] **AC-4** 행 "상세" → `getInventoryByKey`(by-key 프록시) 조회 → 상세 패널에 전 필드(가용/예약/손상/보유/최근조정/
  최근이벤트/version) 표시. 404 → "재고 없음" 구분 표시(크래시 없음).
- [x] **AC-5** 개요(`/wms`)에서 재고 조회 테이블이 제거됨(재고 count 타일=WmsOverview는 유지). `getWmsSectionState`는
  inventory를 더 이상 fetch/노출하지 않음. 개요 배송/알림/ack/overview는 회귀 없음.
- [x] **AC-6** by-key 프록시/inventory-state의 회복탄력성(403→권한 없음, 503/timeout→점검 필요, 401→whole-session
  redirect, 404→재고 없음) 커버.
- [x] **AC-7** `pnpm lint` + `tsc --noEmit` + `vitest`(wms 전체) green.

## Edge Cases

- minOnHand 빈 입력/비숫자 → 파라미터 미전송(undefined). 0 은 유효값으로 전송.
- 상세 조회 대상 행이 lotId 없음 → by-key는 lotId 생략 호출. 404(재고 0) → 패널에 "재고 없음", 목록은 유지.
- 개요에서 inventory 제거 후에도 WmsOverview 재고 count 타일은 독립 fan-out(overview-state.ts)이라 정상 동작.

## Failure Scenarios

- `WmsSectionState.inventory` 제거 시 `wms/page.tsx`/WmsOpsScreen/테스트에 잔존 참조 → tsc RED로 즉시 검출(가드).
- by-key 프록시 404를 degrade로 오처리 → 상세 패널이 "점검 필요"로 오표시. 프록시 테스트가 404=재고없음 구분 단언.
- 재고 필터 state 확장 누락 시 신규 입력이 submit에 반영 안 됨 → 화면 테스트가 위치/로트/최소보유 재조회 단언으로 가드.

## Related Specs / Contracts

- `specs/contracts/console-integration-contract.md` § 2.4.5 (wms read 서브섹션) — 재고 조회 전용 표면 + by-key 상세.
- `specs/services/console-web/architecture.md` — wms 라우트 트리 + 개요/재고 분리.
- Producer: wms `admin-service-api.md` § 1.1 inventory + by-key (소비만, 계약 변경 없음).
