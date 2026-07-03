# TASK-PC-FE-175 — WMS 개요의 택배/출고 조회 테이블을 출고(/wms/outbound) 페이지로 이동

**Status:** done
**Area:** platform-console / console-web · **Route:** `/wms/outbound` (기존) · **Nav:** 변경 없음 (WMS ▸ 개요 · 재고 · 출고)
**Follows:** TASK-PC-FE-173 (재고 조회 테이블을 개요 → 전용 페이지로 분리) — 같은 원칙을, 이번엔 **기존 출고 페이지로 이동**.
**Analysis model:** Opus 4.8 · **Impl model:** Sonnet (PC-FE-173 재고 분리의 대칭 리팩터, 신규 아키텍처 결정 없음)

---

## Goal

WMS 개요(`/wms`)는 아직 **택배/출고 조회 테이블(창고 ID·택배사 코드 필터 + 페이징)**을 개요 밴드 아래에 렌더한다. 필터·페이징을 갖춘 조회 테이블은 "개요"(glance)에 부적합하다(PC-FE-170/173 동일 원칙). 재고 때와 달리 **이미 `/wms/outbound`(출고) 페이지가 존재**하므로(출고 주문 운영 화면, `OutboundOpsScreen`), 새 라우트를 만들지 않고 이 read-only 조회 테이블을 **출고 페이지로 이동**한다.

이동 후:
- **개요(`/wms`)** = 개요 스냅샷 밴드(재고/배송 count 타일 + 알림 분포 + 최근 출고, PC-FE-166/174) + **알림 테이블**만. 배송은 개요에서 count 타일(오늘/주간/월간/전체) + 최근 출고 glance로만 표현.
- **출고(`/wms/outbound`)** = 출고 주문 운영(기존 `OutboundOpsScreen`) + 그 아래 **택배/출고 조회 테이블**(확정 출고의 택배사·운송장·출고시각 read view). 출고 운영(쓰기)과 그 결과(확정 출고 read)를 한 화면에 배치 — 자연스러운 짝.

## 배경 사실 (검증됨)

- WMS는 이미 3라우트: `/wms`(개요) + `/wms/inventory`(재고, PC-FE-173) + `/wms/outbound`(출고). Nav `WMS ▸ 개요·재고·출고` (`ConsoleSidebarNav.tsx`). **Nav 변경 없음.**
- `/wms/outbound`는 `wms-outbound-ops` 피처(`OutboundOpsScreen` — 출고 주문 pick/pack/ship 운영). 택배/출고 read 테이블은 `wms-ops` 피처(`WmsShipmentsTable` + `useWmsShipments`, `outbound.shipping.confirmed` 투영 read model). 앱 라우트 레이어는 두 피처를 조합 가능(architecture.md § Allowed Dependencies — app layer composes features).
- 배송 producer 파라미터/프록시(`GET /dashboard/shipments`, `shippedAtFrom/To` 포함)는 이미 존재 — **producer/contract 변경 0**.
- 배송 조회는 `WMS_VIEWER`, 출고 운영은 별도 롤이라 **독립 403 가능** → 택배/출고 섹션은 자체 resilience(forbidden/degraded)를 갖는다.

## Scope

### 신규 (`features/wms-ops/`)
1. **`api/shipments-state.ts`** — `getWmsShipmentsState(eligible)` → `WmsShipmentsSectionState { shipments, notEligible, forbidden, degraded, lagSeconds }`. `inventory-state.ts` 패턴 미러(단일 `listShipments({page:0,size:20})`; 401→whole-session `redirect('/login')`, 403→forbidden, `WmsUnavailableError`/기타→degraded).
2. **`components/WmsShipmentsScreen.tsx`** — `'use client'` 섹션 화면. ship 필터 state + query + 페이지네이션을 소유(구 `WmsOpsScreen`에서 이관). `useWmsShipments`로 client 재조회, forbidden/degraded 파생. lag 배너(`wms-ship-lag-hint`) + prop-driven `WmsShipmentsTable` 렌더. 페이지 h1(출고) 하위의 **섹션**이므로 `<section aria-label="택배 / 출고 조회">`(h2 헤딩은 `WmsShipmentsTable` 내부 유지).

### 수정
3. **`components/WmsOpsScreen.tsx`** — 배송 섹션 완전 제거: `WmsShipmentsTable` 렌더 + ship filter/query state + `useWmsShipments` 제거. `shipments` prop 제거. 부제 "배송 · 알림 (읽기 + 알림 확인)" → "알림 (읽기 + 확인)". 개요 슬롯 · lag 배너 · 알림 테이블 · ack 다이얼로그는 유지.
4. **`api/wms-state.ts`** — `getWmsSectionState`에서 `listShipments` fan-out 및 `WmsSectionState.shipments` 제거(알림만 seed). lag는 alerts 기준.
5. **`app/(console)/wms/page.tsx`** — `WmsOpsScreen`에 `shipments` 전달 제거; degraded 가드에서 `!state.shipments` 제거(`!state.alerts`만).
6. **`app/(console)/wms/outbound/page.tsx`** — `getOutboundSectionState`와 `getWmsShipmentsState`를 병렬 fetch. 기존 출고 워터폴(registryDegraded/notEligible/forbidden/degraded)은 outbound 기준 유지. happy 시 `<OutboundOpsScreen>` 뒤에 shipments 섹션을 형제로 렌더: `shipmentsState.forbidden`→forbidden 공지 / `degraded`||`!shipments`→degraded 공지 / else `<WmsShipmentsScreen>`.
7. **`features/wms-ops/index.ts`** — `WmsShipmentsScreen`, `getWmsShipmentsState` + `WmsShipmentsSectionState` export.

### 테스트
8. **NEW `WmsShipmentsScreen.test.tsx`** — 구 `WmsOpsScreen.test.tsx`의 배송 케이스 이관: 테이블 렌더(택배사/운송장/출고번호)·nullable carrier `—`·empty state·tolerant unknown field·필터 submit 재조회(`/api/wms/shipments?carrierCode=`)·페이지네이션·503 degraded·403 forbidden·lag 배너·read-only(행 버튼 없음)·axe.
9. **NEW `wms-shipments-state.test.ts`** — `wms-inventory-state.test.ts` 미러: not-eligible/eligible/403/503/401.
10. **MODIFY `WmsOpsScreen.test.tsx`** — `shipments` prop + 모든 배송 케이스 제거(→ 8로 이관). lag 테스트는 `wms-alerts-table` 기준으로 조정. 알림 렌더 + ack(confirm-gated/reason-free/idempotency/422) + a11y 유지.
11. **MODIFY `wms-nav.test.tsx`** — `WmsOpsScreen`에서 `shipments` prop + `wms-ship-empty` 단언 제거(알림 empty만).
12. **MODIFY `wms-state.test.ts`** — `shipments` 단언 제거(알림만 seed), 부제 문구는 미검증 유지.

### 스펙
13. **`specs/contracts/console-integration-contract.md`** § 2.4.5 — 택배/출고 read 표면을 `/wms` 개요 → `/wms/outbound`로 이동 반영(개요는 배송 count 타일 + 최근 출고 glance만).
14. **`specs/services/console-web/architecture.md`** — wms 라우트 트리에서 배송 조회 테이블 위치를 개요 → 출고로 반영.

## Out of Scope (의도적 유지)
- **알림 테이블은 개요에 유지** — 알림은 ack(확인) 액션이 붙은 attention 서피스라 랜딩에 남는다(이커머스 주문상태 분포 대비 order 유사). 배송만 이동.
- **개요의 배송 count 타일(오늘/주간/월간/전체, PC-FE-174) + 최근 출고 glance는 유지** — 개요다운 배송 표현.
- producer/contract 변경 없음(기존 `GET /dashboard/shipments` 소비만). 새 Nav 항목 없음.

## Acceptance Criteria
- **AC-1** 개요(`/wms`)에서 택배/출고 조회 테이블이 제거됨(`wms-ship-*` 없음). 개요의 배송 count 타일 + 최근 출고 glance + 알림 테이블 + ack는 회귀 없음. `getWmsSectionState`는 shipments를 더 이상 fetch/노출하지 않음.
- **AC-2** 출고(`/wms/outbound`)에 `OutboundOpsScreen`(기존) 아래로 택배/출고 조회 테이블이 렌더된다(필터·페이징·행 read-only). 창고/택배사 필터 submit이 `/api/wms/shipments`로 재조회.
- **AC-3** 택배/출고 섹션 resilience: client 재조회 403→인라인 `wms-ship-forbidden`, 503/timeout→`wms-ship-degraded`; 서버 seed 403/503도 섹션만 degrade(출고 운영·콘솔 셸 무영향). 401→whole-session redirect. lag 배너(`wms-ship-lag-hint`).
- **AC-4** Nav 변경 없음(개요·재고·출고 3항목). 출고 페이지 h1 "WMS 출고" 하위 섹션으로 배치(중복 h1 없음).
- **AC-5** `pnpm lint` + `tsc --noEmit` + `vitest run` green.

## Edge Cases
- 출고 운영은 정상인데 배송 seed가 403/503 → 출고 화면은 정상 렌더, 택배/출고 섹션만 공지. 반대(배송 정상·출고 degraded)는 페이지가 출고 기준으로 degrade(기존 동작 유지).
- 배송 empty(`totalElements:0`) → `wms-ship-empty`(정상 read), degraded 아님.
- nullable carrier/tracking → `—`(크래시 없음). unknown/future 필드 → tolerant passthrough.

## Failure Scenarios
- `WmsSectionState.shipments` 제거 시 `wms/page.tsx`/`WmsOpsScreen`/테스트 잔존 참조 → tsc RED로 즉시 검출(가드).
- 출고 페이지가 배송 seed 실패를 degrade로 오처리 → 섹션 공지. 출고 운영이 이에 영향받지 않음을 테스트로 가드(독립 섹션).

## Related Specs / Contracts
- `specs/contracts/console-integration-contract.md` § 2.4.5 (wms read 서브섹션) — 택배/출고 read를 출고 표면으로 이동.
- `specs/services/console-web/architecture.md` — wms 라우트 트리.
- Producer: wms `admin-service-api.md` § 1.3 shipments (소비만, 계약 변경 없음).
