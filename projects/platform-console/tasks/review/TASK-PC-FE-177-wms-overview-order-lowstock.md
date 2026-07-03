# TASK-PC-FE-177 — WMS 개요 순서 재배치(규모→주의→활동) + 재고 저재고 건수

> **Renumbered from PC-FE-176** (concurrent-session task-id collision — two other PC-FE-176 tasks landed in `origin/main` while this was in flight: brand-link sidebar reset #2176, operator-create password optional #2175). Content unchanged.

**Status:** review
**Area:** platform-console / console-web · **Route:** `/wms` (개요) · **Nav:** 변경 없음
**Follows:** PC-FE-166/170/174/175 (WMS 개요 스냅샷 시리즈). 개요 정보구조 폴리시.
**Analysis model:** Opus 4.8 · **Impl model:** Sonnet (기존 fan-out/타일 패턴 재사용 프런트 소량 변경, 신규 아키텍처 결정 없음)

---

## Goal

WMS 개요(`/wms`)의 **정보 순서**와 **재고 타일 내용**을 개선한다.

**① 순서(규모 → 주의 → 활동).** 현재 개요는 `카운트(재고/배송) → 알림 상태 분포 → 최근 출고`(밴드) 뒤에 별도로 `알림 테이블`을 렌더한다. 알림 콘텐츠(분포 + 테이블)가 **최근 출고에 의해 분리**돼 있고, 운영자의 핵심 action 신호(미확인 알림)가 흩어진다. WMS 카운트 타일은 nav 링크가 아니므로(PC-FE-168) "카운트 먼저" 근거도 약하다. 재배치:
`카운트(규모) → 알림 상태 분포 → 알림 테이블(주의: 분포 glance 바로 뒤 액션 테이블) → 최근 출고(활동, 맨 마지막)`.

**② 재고 저재고 건수.** 재고 타일은 현재 단일 total(=SKU-위치 행 수)만 보여 빈약하다. producer가 이미 주는 `lowStockOnly=true` 필터로 **저재고 건수**(재입고 필요 SKU-위치 수 = 재고판의 attention 신호)를 타일에 추가한다. 기존 `totalElements`(size=1) 패턴 그대로 — ADR-MONO-017 D3.B 준수(집계·producer 리트로핏 없음).

## 배경 사실 (검증됨)

- `lowStockOnly`/`lowStockFlag`는 producer 실기능(`InventoryDashboardController` 파라미터 + `InventorySnapshotEntity` 컬럼), 테넌트 전역(warehouse 불요). 콘솔 `InventoryQueryParams.lowStockOnly` + `listInventory` 이미 지원.
- `LOW_STOCK`은 실제 알림 타입 → 저재고는 알림과 **부분 중복**이나 상태(라이브) vs 이벤트(확인/미확인)라 상보적.
- 개요 fan-out(`getWmsOverviewState`)은 `listInventory({page:0,size:1})`로 재고 total만 읽음 → 저재고 leg 한 줄 병렬 추가.
- 최근 출고는 현재 `WmsOverview` 밴드 내부에서 렌더. 알림 테이블은 `WmsOpsScreen`(client, ack 상태). 순서상 최근 출고를 알림 테이블 **뒤로** 보내려면 슬롯 분리 필요.

## Scope

### 수정 (`features/wms-ops/`)
1. **`api/overview-state.ts`** — fan-out에 `cell(listInventory({ lowStockOnly: true, page:0, size:1 }))` 추가. `WmsAreaCount`에 `lowStock?: number | null` 추가(재고만 세팅; 저재고 sub-read degrade 시 null, 타일은 total 기준 ok 유지 = 배송 period sub-read 패턴과 동일). `areaCount`(재고)가 lowStock cell을 받아 매핑.
2. **`components/WmsOverview.tsx`** — (a) 재고 `CountTile`에 **저재고 sub-line**(`저재고 M`, testid `wms-inventory-lowstock`; M>0이면 amber 강조, degrade면 "—"). (b) `RecentShipments`를 밴드 return에서 제거하고 **`WmsRecentShipments({ state })`로 export**(밴드 = 카운트 + 알림 분포만).
3. **`components/WmsOpsScreen.tsx`** — `recentActivity?: React.ReactNode` prop 추가, **알림 테이블 뒤**에 렌더.
4. **`app/(console)/wms/page.tsx`** — `WmsOpsScreen`에 `recentActivity={<WmsRecentShipments state={overviewState} />}` 전달.
5. **`features/wms-ops/index.ts`** — `WmsRecentShipments` export.

### 테스트
6. **`wms-overview-state.test.ts`** — 저재고 leg mock + `byKey.inventory.lowStock` 매핑 단언(+ 저재고 sub-read degrade 시 total ok·lowStock null).
7. **`wms-overview.test.tsx`** — 재고 타일 `wms-inventory-lowstock` 렌더 단언; 최근 출고는 `WmsOverview`에서 제거됐으므로 recent-shipments 케이스를 신규 `WmsRecentShipments.test.tsx`로 이관.
8. **NEW `WmsRecentShipments.test.tsx`** — 최근 출고 렌더/empty/degraded(구 wms-overview 케이스 이관).
9. **`WmsOpsScreen.test.tsx`** — `recentActivity` 슬롯이 알림 테이블 뒤에 렌더되는지(옵셔널 prop, 기존 케이스 무영향) 케이스 추가.

## Out of Scope
- **총 보유 수량 합산 금지**(D3.B 클라이언트 합성 집계 금지, producer 합계 엔드포인트 없음). **throughput 금지**(warehouseId 필수 — 테넌트 전역 개요 부적합).
- 알림 테이블/분포 자체 로직 변경 없음(순서만). 배송 타일(PC-FE-174) 변경 없음. producer/contract 변경 없음.

## Acceptance Criteria
- **AC-1** 개요 DOM 순서 = 카운트 → 알림 상태 분포 → 알림 테이블 → 최근 출고. 알림 분포와 알림 테이블이 인접(최근 출고에 의해 분리되지 않음).
- **AC-2** 재고 타일에 저재고 건수(`wms-inventory-lowstock`)가 total과 함께 표시. 저재고 sub-read degrade 시 재고 타일은 total 기준 ok 유지, 저재고만 "—".
- **AC-3** 최근 출고는 알림 테이블 **뒤에** 렌더(`recentActivity` 슬롯). 최근 출고 empty/degraded 표시 회귀 없음.
- **AC-4** Nav·배송 타일·알림 ack·resilience(401 redirect / per-cell degrade) 회귀 없음. producer/contract 변경 없음.
- **AC-5** `pnpm lint` + `tsc --noEmit` + `vitest run` green.

## Edge Cases
- 저재고 leg 403/503 → 재고 타일 total은 정상, 저재고만 "—"(배송 period sub-read와 동일). 재고 total leg 자체 degrade → 타일 전체 "점검 필요"(기존).
- 저재고 0 → "저재고 0"(정상, 강조 없음). 저재고 > 0 → amber 강조(색만이 아니라 "저재고 M" 텍스트가 신호).
- notEligible → 개요 null(기존).

## Failure Scenarios
- `WmsRecentShipments` 밴드에서 분리 후 page 슬롯 배선 누락 → 최근 출고 미표시 → WmsOpsScreen/page 테스트가 슬롯 렌더 단언으로 가드.
- 저재고 leg 누락 시 재고 타일이 예전대로 total만 → wms-overview.test `wms-inventory-lowstock` 단언이 가드.

## Related Specs / Contracts
- `specs/contracts/console-integration-contract.md` § 2.4.5.2 (wms 운영 개요 스냅샷) — 카운트에 저재고(재고 attention) 추가 + 순서 note(소량).
- Producer: wms `admin-service-api.md` § 1.1 inventory(`lowStockOnly`) — 소비만, 계약 변경 없음.
