# TASK-PC-FE-223 — WMS "마스터" 참조 데이터 조회 메뉴 신설 (창고·로케이션·SKU·거래처 read-only)

**Status:** ready
**Area:** platform-console / console-web · **New route:** `app/(console)/wms/master` · **Nav:** `WMS ▸ 마스터` (`nav-wms-master`)
**Analysis model:** Opus 4.8 · **Impl model:** Sonnet (frontend-engineer 위임) — read-only 참조 테이블, 기존 refs 클라이언트 재사용 (분석=Opus 4.8 / 구현 권장=Sonnet) · **검증:** Opus 재검증.
**Priority:** 중(입고 PC-FE-222 다음). 운영자 참조·무결성 확인용 read 표면.

---

## Goal

WMS 마스터 데이터(창고/구역/로케이션/SKU/거래처/Lot)는 모든 운영의 참조 기반이나 콘솔에 조회 화면이 없다. producer admin read-model은 `GET /dashboard/refs/{type}` 로 참조 데이터를 노출하고, **콘솔 클라이언트 `features/wms-ops/api/wms-refs-api.ts`의 `listRefs(type)` 가 이미 존재하나 소비처가 0**이다. 이를 **`/wms/master` 읽기 전용 참조 조회 페이지**로 표면화 — 운영자가 로케이션/SKU/거래처 코드를 콘솔 내에서 확인할 수 있게 한다.

## 배경 사실 (검증됨)

- 콘솔 클라이언트 `wms-refs-api.ts` `listRefs(type)` → `GET /dashboard/refs/{type}` 이미 존재(소비처 0). gateway 배선 `WMS_ADMIN_BASE_URL` 재사용, **신규 env 불필요.**
- producer read-model이 노출하는 ref `type` 목록은 착수 시 `admin-service-api.md`(§`/dashboard/refs/{type}`)로 **확정 필요** — 후보: `warehouses`, `zones`, `locations`, `skus`, `partners`, `lots`. read-model이 지원하는 type만 탭으로 노출(미지원 type은 노출 금지).
- 원시 `master-service`(`/api/v1/master/**`, `MASTER_READ`)는 더 풍부한 조회(코드/바코드 단건 등)를 제공하나 **별도 프리픽스·역할** → 이 task 범위 밖(콘솔 admin read-model 소비 컨벤션 유지).

## Scope

### 신규
1. **`app/(console)/wms/master/page.tsx`** — 서버 컴포넌트(`force-dynamic`). 자격 워터폴 `wms/inventory/page.tsx` 미러. 제목 "WMS 마스터". `getWmsMasterState(eligible)` seed 후 `<WmsMasterScreen … />`.
2. **`features/wms-ops/api/master-state.ts`** — `getWmsMasterState(eligible)` → 기본 type(예: `locations`)의 첫 페이지 seed + `{ notEligible, forbidden, degraded, lagSeconds }`. 401→redirect, 403→forbidden, degrade 규칙 동일.
3. **`features/wms-ops/components/WmsMasterScreen.tsx`** — `'use client'`. ref **type 탭 선택**(read-model 지원 type만) + `q`/`status` 필터 + 페이지네이션. `useWmsRefs(type, params)` 훅으로 `/api/wms/master/refs/{type}` GET. 헤딩 `wms-master-heading`.
4. **`app/api/wms/master/refs/[type]/route.ts`** — GET 프록시(READ-ONLY, `mapWmsError`). `type` 화이트리스트 검증(지원 외 400).
5. **테스트**: `WmsMasterScreen.test.tsx`(탭 전환·필터·페이지네이션·forbidden/degraded/empty), `wms-master-state.test.ts`(not-eligible/eligible/403/503/401).

### 수정
6. **`shared/ui/ConsoleSidebarNav.tsx`** — WMS `children`에 `{ href: '/wms/master', label: '마스터', testid: 'nav-wms-master' }`를 **출고 다음**(참조/설정 성격은 흐름 뒤)에 삽입 → `개요 → 가이드 → (입고 →) 재고 → 출고 → 마스터`.
7. **`features/wms-ops/index.ts`** — `WmsMasterScreen`, `getWmsMasterState` + 타입 export.
8. **스펙**: `console-integration-contract.md`(§2.4.5 wms read에 마스터 참조 조회 표면) + `console-web/architecture.md`(라우트 트리 `wms/master`).

## Out of Scope
- 마스터 데이터 **생성/수정/삭제(write)** — producer read-model은 read 전용. SoT 변경은 원시 master-service 담당.
- 원시 `master-service`(`/api/v1/master/**`) 소비·코드/바코드 단건 조회 — 콘솔 admin read-model 컨벤션 유지.
- read-model이 지원하지 않는 ref type 노출 — 지원 type만.

## Acceptance Criteria
- **AC-1** `/wms/master` 라우트가 자격 워터폴을 갖고 "WMS 마스터" 화면을 렌더. Nav `WMS ▸ 마스터` 클릭·딥링크로 진입 + active.
- **AC-2** read-model이 지원하는 ref type이 탭으로 노출되고, 탭 전환 시 해당 type을 재조회. `q`/`status` 필터 + 페이지네이션 동작.
- **AC-3** 회복탄력성(403/503/401/빈 결과) 커버. 지원 외 type 프록시 호출 → 400 방어.
- **AC-4** `pnpm lint` + `tsc --noEmit` + `vitest`(wms 전체) green.

## Edge Cases
- read-model이 ref type을 하나도 제공하지 않거나 특정 type 미지원 → 미지원 탭 숨김(빈 화면 대신 안내).
- `q` 빈 입력 → 파라미터 미전송. status 필터 옵션은 `_meta/enums`가 아닌 read-model 실제 값 기반.

## Failure Scenarios
- type 화이트리스트 누락 시 임의 type 프록시 통과 → 404/500 노출. 프록시 테스트가 화이트리스트 400 단언.
- ref 응답 스키마 드리프트 → `RefPageSchema.parse` 실패. 화면 테스트가 목 응답 파싱 단언.

## Related Specs / Contracts
- `console-integration-contract.md` §2.4.5 (wms read) — 마스터 참조 조회 표면 신설.
- `console-web/architecture.md` — 라우트 트리 `wms/master`.
- Producer: wms `admin-service-api.md` `/dashboard/refs/{type}` (소비만). 원시 마스터 SoT: `specs/services/master-service/overview.md`, `master-service-api.md`.
