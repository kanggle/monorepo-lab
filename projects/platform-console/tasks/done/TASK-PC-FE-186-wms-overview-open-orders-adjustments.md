# TASK-PC-FE-186 — WMS 개요: 미출고 주문 타일 + 최근 재고 조정 glance

**Status:** done
**Area:** platform-console / console-web · **Route:** `/wms` (개요) · **Nav:** 변경 없음
**Analysis model:** Opus 4.8 · **Impl model:** Sonnet (기존 fan-out/타일 패턴 확장 — PC-FE-174/177과 동형)

---

## Goal

WMS 운영 개요는 현재 **재고(총계+저재고) · 배송(완료 shipments, 오늘/주간/월간/전체) · 알림 · 최근 출고**만 보여준다. 출고 쪽은 **완료된 배송만** 보여 **대기 중인 출고 물량(파이프라인)이 전혀 안 보이고**, 재고 쪽은 활동 glance가 없다. 두 가지를 추가한다:

1. **미출고 주문 타일** — 접수됐지만 아직 출고/취소되지 않은 열린 주문 수. `배송`(완료)의 파이프라인 짝.
2. **최근 재고 조정 glance** — `최근 출고`의 재고측 활동 짝.

제약(ADR-MONO-017 D3.B): producer `/summary` 금지 → 기존 dashboard list 엔드포인트의 `totalElements`(필터+`size=1`)로만 카운트 유도. 두 지표 모두 **기존 엔드포인트+콘솔 래퍼로 충족**(신규 producer 필터 불필요).

## 근거 (feasibility 확인 완료)

- **미출고 주문** = `listOrders({status:'RECEIVED'})` `totalElements`. admin 읽기모델은 주문 상태를 **RECEIVED/SHIPPED/CANCELLED로만** 투영하고(피킹/패킹 이벤트는 흡수, `sagaState` 미사용), 주문은 출고/취소 전까지 `RECEIVED`에 머문다 → `status=RECEIVED` = **열린 출고 백로그**. (피킹중/패킹중 세분은 읽기모델에 없어 불가.)
- **최근 재고 조정** = `listAdjustments({size:5})` — `/dashboard/adjustments`(append-only, `inventory.adjusted` 투영). 필드: bucket·delta·reasonCode·occurredAt.

## Scope

**`api/types.ts`** — `AdjustmentRowSchema`(tolerant: 전 필드 optional/nullable + passthrough) 신설, `AdjustmentPageSchema = wmsPage(AdjustmentRowSchema)`(기존 GenericRow → 타입화, 하위호환).

**`api/overview-state.ts`**:
- fan-out에 2 leg 추가: `listOrders({status:'RECEIVED', page:0, size:1})`, `listAdjustments({page:0, size:5})`.
- `counts`에 `openOrders`('미출고 주문') LEVEL 타일을 재고와 배송 사이 삽입(재고 → 미출고 → 배송).
- `WmsOverviewState`에 `recentAdjustments`/`recentAdjustmentsStatus` 추가.

**`components/WmsOverview.tsx`** — `WmsRecentAdjustments` 슬롯 컴포넌트 신설(`WmsRecentShipments` 미러, `data-testid="wms-recent-adjustments"`). 미출고 타일은 기존 `CountTile`의 LEVEL 분기가 그대로 렌더(컴포넌트 변경 불필요).

**`app/(console)/wms/page.tsx`** — `recentActivity` 슬롯에 `<WmsRecentAdjustments>`를 `<WmsRecentShipments>`와 함께(fragment) 배치. **`index.ts`** — `WmsRecentAdjustments` export.

**Out of scope:** 손상/예약 재고 카운트(producer 필터 부재 → 별건), 피킹/패킹 세분·BACKORDERED·TMS 실패(읽기모델 미보유). producer/contract 무변경.

## Acceptance Criteria
- **AC-1** 개요 counts 밴드가 `재고 · 미출고 주문 · 배송` 3타일을 이 순서로 렌더. 미출고 타일(`wms-openOrders-count`)은 단일 총계(period 없음).
- **AC-2** `recentActivity`에 `최근 출고`와 `최근 재고 조정`(`wms-recent-adjustments`)이 함께 렌더. 조정 행은 reasonCode·bucket·delta·시각 표시. 데이터 없으면 "최근 항목이 없습니다.".
- **AC-3** 리질리언스 회귀 없음: 각 신규 leg는 자체 cell(403→forbidden/503→degraded/401→whole-session redirect). 한 leg degrade가 다른 타일/섹션을 지우지 않음.
- **AC-4** `pnpm lint` + `tsc --noEmit` + `vitest run` green. overview-state·overview·WmsRecentAdjustments 테스트 통과.

## Edge Cases / Failure Scenarios
- `admin_order_summary`가 비어있으면 미출고=0(정상 렌더, degrade 아님). 조정 0건이면 glance는 빈 상태.
- `openOrders`/`recentAdjustments` leg가 503/timeout이면 해당 타일/섹션만 degrade(형제 무영향) — 기존 cell 패턴.
- `AdjustmentPageSchema` 타입화는 passthrough라 기존 소비자(generic 접근) 무영향.

## Related
- 동형 선행: PC-FE-174(배송 period), PC-FE-177(재고 저재고 sub + 최근 출고 slot 분리).
- 데이터 소스: admin-service `OrderDashboardController`(`admin_order_summary`, status 필터) · `AdjustmentAuditController`(`admin_adjustment_audit`).
- 데모 배선: fed-e2e admin-service의 outbound-order 투영 토픽을 `wms.outbound.order.received.v1`로 repoint해야 `admin_order_summary`가 채워짐(별도 ops, 로컬 override).
