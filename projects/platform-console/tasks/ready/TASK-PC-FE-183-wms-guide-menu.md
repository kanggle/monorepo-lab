# TASK-PC-FE-183 — WMS 가이드 메뉴 신설 (재고 · 출고 정적 참조 화면)

**Status:** ready
**Area:** platform-console / console-web · **Route:** `/wms/guide` (신규) · **Nav:** WMS drill 자식에 `가이드` 추가 (`nav-wms-guide`)
**Analysis model:** Opus 4.8 · **Impl model:** Opus 4.8 (도메인 상태머신·재고 시맨틱 정확도 요구 — 콘텐츠 집약)

---

## Goal

WMS 도메인 운영자에게 **재고(재고 현황)**와 **출고(주문 상태머신·택배/출고)**의 개념을 한 화면에서 설명하는 **정적 참조 가이드**를 신설한다. IAM 가이드(`/iam/guide`, `IamGuideScreen`, TASK-PC-FE-163/180)와 동일한 패턴 — 순수 server component, 데이터 페치·권한 게이트 없음(콘솔 진입자 누구나 열람), 콘텐츠는 `data.ts`로 분리하고 화면은 Card/table로 렌더.

배경: WMS nav는 현재 `개요 · 재고 · 출고`만 있고, 각 화면(재고 현황 테이블 / 출고 상태)의 **의미**(수량 버킷 가용·예약·보유·손상, 저재고, 최종 일관성 읽기모델 지연 / 주문 8상태·saga·TMS 통보)를 설명하는 곳이 없다. IAM처럼 `가이드` 자식을 추가한다.

## Scope

**신규 feature `features/wms-guide/`** (iam-guide 미러):
- `data.ts` — 타입 있는 정적 콘텐츠 배열: 재고 수량 버킷(가용/예약/보유/손상 시맨틱, `보유=가용+예약+손상` 파생), 저재고 이중 정의(테이블 배지 고정 임계 vs 알림 임계), 재고 이벤트(입고/조정/이동/예약/해제/확정), 예약 lifecycle(RESERVED→CONFIRMED/RELEASED), 주문 상태(8), saga 상태(요약), TMS 통보 상태(3), 도메인 롤(INVENTORY_*/OUTBOUND_*).
- `components/WmsGuideScreen.tsx` — 정적 렌더(server component, no `'use client'`). `data-testid="wms-guide"` 루트 + 섹션별 testid.
- `index.ts` — `export { WmsGuideScreen }`.

**신규 라우트** `app/(console)/wms/guide/page.tsx` → `<WmsGuideScreen />` (force-dynamic 불필요 — 정적).

**Nav** `shared/ui/ConsoleSidebarNav.tsx` — WMS `children`에 `{ href: '/wms/guide', label: '가이드', testid: 'nav-wms-guide' }`를 **개요 다음**(재고 앞)에 삽입 — IAM의 개요→가이드 순서와 일치.

**Out of scope:** 재고/출고 라이브 화면(`/wms/inventory`·`/wms/outbound`) 로직 변경 없음. producer/contract 무변경. 권한 게이트 없음(가이드는 공개). 백엔드 무변경.

## Acceptance Criteria
- **AC-1** `/wms/guide` 진입 시 재고·출고 설명이 렌더된다(`wms-guide` testid). WMS nav를 열면 `가이드`(`nav-wms-guide`) 자식이 개요와 재고 사이에 보이고 클릭 시 `/wms/guide`로 이동, 딥링크 시 WMS drill이 자동 오픈되고 `가이드`가 active.
- **AC-2** 재고 섹션: 수량 버킷 4종(가용·예약·보유·손상)의 시맨틱과 `보유=가용+예약+손상` 관계, 예약 흐름(가용→예약→확정/해제), 저재고 의미, 읽기모델 최종 일관성(지연 배너 이유)을 설명.
- **AC-3** 출고 섹션: 주문 상태머신(정상 6단계 + 취소/이월 2 종료), TMS 통보 상태, saga의 존재를 설명.
- **AC-4** `pnpm lint` + `tsc --noEmit` + `vitest run` green. 가이드 화면·nav 테스트 통과.

## Edge Cases / Failure Scenarios
- 가이드는 정적이라 백엔드 장애와 무관(페치 없음) — 재고/출고 서비스가 degrade여도 가이드는 항상 열림.
- nav 배열에 자식 1개 추가 → 기존 WMS drill 딥링크(재고/출고 active) 회귀 없어야 함(`wms-nav` 테스트가 longest-match active를 단언).
- 재고 수량 시맨틱은 inventory-service 도메인 모델을 SoT로 하며, 드리프트 시 가이드 카피를 동반 갱신(테스트는 구조만 단언, 설명 텍스트는 사람이 맞춤 — iam-guide data.ts 동일 원칙). 특히 **저재고 이중 메커니즘**(테이블 배지=admin 읽기모델 고정 `가용<=10` / 알림=inventory-service 설정형 임계)은 서로 불일치할 수 있어 가이드에서 구분 명시.

## Related
- 미러 패턴: `features/iam-guide/`(TASK-PC-FE-163 신설 / -180 relocate) — 정적 참조 화면 + data.ts 분리.
- 재고 SoT: `projects/wms-platform/apps/inventory-service/`(수량 버킷 `domain-model.md`·예약 상태머신·저재고 `LowStockDetectionService`) + admin-service `admin_inventory_snapshot` 읽기모델(`InventorySnapshotEntity`, `InventoryProjectionService`).
- 출고 SoT: `projects/wms-platform/specs/services/outbound-service/state-machines/{order-status,saga-status}.md` + `domain/model/{OrderStatus,SagaStatus,TmsStatus}.java`.
- 콘솔 소비 화면: `features/wms-ops/`(WmsInventoryTable · 택배/출고 shipments-state, TASK-PC-FE-175).
