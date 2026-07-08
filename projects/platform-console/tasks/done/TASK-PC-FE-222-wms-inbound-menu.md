# TASK-PC-FE-222 — WMS "입고" 전용 메뉴/페이지 신설 + 미노출 입고(ASN·검수·적치) 표면화

**Status:** done
**Completed:** 2026-07-08, 3-dim verified — impl PR #2321 (squash `8dae87b4`). tsc 0 · next lint clean · vitest inbound 26/26 (state/nav/screen, forbidden/degraded/401/404 resilience). 3-dim: (a) MERGED+`8dae87b4`; (b) origin/main tip 일치; (c) pre-merge CLEAN(전 required SUCCESS, ecommerce IT 레인은 무관). 분석=Opus 4.8 / 구현=Sonnet(frontend-engineer).
**Area:** platform-console / console-web · **New route:** `app/(console)/wms/inbound` · **Nav:** `WMS ▸ 입고` (`nav-wms-inbound`)
**Analysis model:** Opus 4.8 · **Impl model:** Sonnet (frontend-engineer 위임) — 기존 재고/출고 화면 패턴 미러 (분석=Opus 4.8 / 구현 권장=Sonnet) · **검증:** Opus 재검증.
**Follows:** TASK-PC-FE-173 (재고 전용 페이지 분리) — 동일 원칙: producer가 제공하지만 콘솔에 소비처가 0인 read 표면을 전용 페이지로 표면화.

---

## Goal

WMS nav는 현재 `개요 · 가이드 · 재고 · 출고`만 있어 **창고 운영의 앞단 절반인 입고(ASN·검수·적치)가 콘솔에 전혀 노출되지 않는다.** 출고(`/wms/outbound`)는 전용 메뉴가 있는데 입고는 대칭 메뉴가 없다 — WMS 도메인 기능 배치의 가장 큰 비대칭 갭.

producer(admin-service read-model)는 `GET /dashboard/asns`, `GET /dashboard/asns/{asnId}/inspection` 을 이미 노출하고, **콘솔 API 클라이언트(`features/wms-ops/api/wms-shipments-api.ts`)에도 대응 read 함수가 이미 존재하나 소비처가 0**이다(overview fan-out은 shipments/adjustments만 호출). 이를 **전용 `/wms/inbound` 페이지로 표면화**한다 — 재고(PC-FE-173)·출고와 동일한 자격 워터폴 + 읽기 전용 테이블 패턴.

## 배경 사실 (검증됨)

- admin read-model 계약(`projects/wms-platform/specs/contracts/http/admin-service-api.md`): `GET /dashboard/asns`(§1.5 계열, 필터: status/warehouseId/supplierPartnerId/날짜), `GET /dashboard/asns/{asnId}/inspection`. `WMS_VIEWER` 이상 read.
- 콘솔 클라이언트 `wms-shipments-api.ts`에 ASN/inspection read 함수가 이미 존재(소비처 0) — PC-FE-173의 `getInventoryByKey`와 동일한 "코드 있음·화면 없음" 상태.
- gateway 배선 재사용: ASN read는 `WMS_ADMIN_BASE_URL`(`/api/v1/admin`, `wms.local`) 아래 `/dashboard/asns` → **`callWmsAdmin` 그대로 사용, 신규 env 불필요.**
- 원시 `inbound-service`(`/api/v1/inbound/**`, 적치 지시/확정 등 write 포함)는 별도 서비스로 존재하나, **콘솔은 admin read-model만 소비**하는 기존 컨벤션(§2.4.5) 유지 — 이 task는 read-model 기반 조회 전용.

## Scope

### 신규
1. **`app/(console)/wms/inbound/page.tsx`** — 서버 컴포넌트(`force-dynamic`). 자격 워터폴은 `wms/outbound/page.tsx`를 그대로 미러(registry `productKey='wms'` → registryDegraded → notEligible → forbidden → degraded → happy). 제목 "WMS 입고". `getWmsInboundState(eligible)` seed 후 `<WmsInboundScreen … />`.
2. **`features/wms-ops/api/inbound-state.ts`** — `getWmsInboundState(eligible)` → `{ asns, notEligible, forbidden, degraded, lagSeconds }`. `inventory-state.ts` 패턴 미러(단일 `listAsns({page:0,size:20})`; 401→whole-session `redirect('/login')`, 403→forbidden, `WmsUnavailableError`/기타→degraded).
3. **`features/wms-ops/components/WmsInboundScreen.tsx`** — `'use client'`. ASN 필터 state(status/warehouse/공급처/날짜) + query + 페이지네이션 + **검수 상세 인라인 패널**(행 "검수" → `GET /dashboard/asns/{asnId}/inspection`, by-key 상세와 동일한 인라인 패널 방식). lag 배너 + `WmsAsnTable`. 헤딩 `wms-inbound-heading`.
4. **`WmsAsnTable.tsx`** (+ 필요 시 `AsnInspectionPanel.tsx`) — ASN 목록 컬럼(ASN번호·상태·창고·공급처·예정일·라인수 등) + 행별 "검수" 버튼(testid `wms-asn-inspection-<i>`).
5. **`app/api/wms/inbound/asns/route.ts`** + **`.../asns/[asnId]/inspection/route.ts`** — GET 프록시(READ-ONLY). `app/api/wms/...` 기존 프록시 미러, `mapWmsError`(404 포함).
6. **테스트**: `WmsInboundScreen.test.tsx`(필터 submit·페이지네이션·검수 조회 성공/404·forbidden/degraded/empty), `wms-inbound-state.test.ts`(not-eligible/eligible/403/503/401), `wms-nav.test.tsx`에 `nav-wms-inbound` active/딥링크 케이스 추가.

### 수정
7. **`shared/ui/ConsoleSidebarNav.tsx`** — WMS `children`에 `{ href: '/wms/inbound', label: '입고', testid: 'nav-wms-inbound' }`를 **가이드와 재고 사이**에 삽입 → 순서 `개요 → 가이드 → 입고 → 재고 → 출고`(물류 흐름 입고→재고→출고).
8. **`features/wms-ops/index.ts`** — `WmsInboundScreen`, `getWmsInboundState` + 타입 export.
9. **스펙**: `specs/contracts/console-integration-contract.md`(§2.4.5 wms read 서브섹션에 입고 조회 전용 표면 + 검수 상세 추가) + `specs/services/console-web/architecture.md`(wms 라우트 트리에 `wms/inbound` 추가).

## Out of Scope (의도적 유지)
- **적치(putaway) 지시/확정·ASN 생성·검수 확정 등 write** — producer read-model에 없음(원시 inbound-service 전용). 읽기/조회 전용 유지.
- 원시 `inbound-service`(`/api/v1/inbound/**`) 직접 소비 — 콘솔은 admin read-model만 소비하는 컨벤션 유지(신규 env·프로파일 도입 금지).
- 개요(`/wms`)에 입고 count 타일 추가 — 별도 후속 판단(무단 확장 금지). 이 task는 전용 페이지만.

## Acceptance Criteria
- **AC-1** `/wms/inbound` 라우트가 자격 워터폴(notEligible/forbidden/degraded/happy)을 갖고 "WMS 입고" 화면을 렌더. Nav `WMS ▸ 입고`(가이드와 재고 사이) 클릭·딥링크로 진입, WMS drill 자동 오픈 + `입고` active.
- **AC-2** ASN 테이블에 상태/창고/공급처/날짜 필터가 있고 submit이 각 파라미터로 재조회, 페이지네이션 동작.
- **AC-3** 행 "검수" → `GET /dashboard/asns/{asnId}/inspection` 조회 → 인라인 패널에 검수 결과(라인별 예정/실입고 수량·판정) 표시. 404(검수 미존재) → "검수 내역 없음" 구분 표시(크래시 없음).
- **AC-4** 회복탄력성: 403→권한 없음, 503/timeout→점검 필요, 401→whole-session redirect, 404→검수 없음 커버.
- **AC-5** `pnpm lint` + `tsc --noEmit` + `vitest`(wms 전체) green.

## Edge Cases
- 필터 빈 입력 → 파라미터 미전송(undefined). 날짜 범위 부분 입력(from만/to만) 허용.
- ASN에 검수 레코드 없음 → 검수 프록시 404 → 패널 "검수 내역 없음", 목록 유지.
- read-model 지연(`X-Read-Model-Lag-Seconds`) → lag 배너 표면화(재고/배송과 동일).

## Failure Scenarios
- 검수 프록시 404를 degrade로 오처리 → 패널이 "점검 필요"로 오표시. 프록시 테스트가 404=검수없음 구분 단언으로 가드.
- nav 배열 자식 1개 추가 → 기존 WMS drill 딥링크(재고/출고 active) 회귀. `wms-nav` longest-match active 테스트가 가드.
- ASN read 함수의 응답 스키마 드리프트 → `AsnRowSchema.parse` 런타임 실패. 화면 테스트가 목 응답으로 파싱 경로 단언.

## Related Specs / Contracts
- `specs/contracts/console-integration-contract.md` §2.4.5 (wms read 서브섹션) — 입고 조회 전용 표면 신설.
- `specs/services/console-web/architecture.md` — wms 라우트 트리에 `wms/inbound` 추가.
- Producer: wms `admin-service-api.md` §1.x ASN dashboard + inspection (소비만, 계약 변경 없음). 원시 입고 SoT: `specs/services/inbound-service/{overview.md,workflows/inbound-flow.md,state-machines/asn-status.md}`.
- 가이드 입고 개념: `features/wms-guide/data.ts`(입고 이벤트 — 이미 문서화됨).
