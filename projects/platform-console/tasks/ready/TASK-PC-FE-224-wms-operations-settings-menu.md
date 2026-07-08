# TASK-PC-FE-224 — WMS "운영설정" 조회 메뉴 신설 (예약 TTL·저재고 임계치·프로젝션 상태 read-only)

**Status:** ready
**Area:** platform-console / console-web · **New route:** `app/(console)/wms/operations` · **Nav:** `WMS ▸ 운영설정` (`nav-wms-operations`)
**Analysis model:** Opus 4.8 · **Impl model:** Sonnet (frontend-engineer 위임) — read-only 운영 상태·설정 조회, projection-status 클라이언트 재사용 + settings GET 신규 (분석=Opus 4.8 / 구현 권장=Sonnet) · **검증:** Opus 재검증.
**Priority:** 하(입고 PC-FE-222·마스터 PC-FE-223 다음). 운영 파라미터 가시성 — 소규모.

---

## Goal

admin-service는 운영 설정(예약 TTL `inventory.reservation.ttl_hours`, 저재고 기본 임계치 `inventory.low_stock.default_threshold_qty`)과 프로젝션(read-model) 상태를 노출하나, 콘솔에는 표면이 없다. 개요의 저재고/재고 배지가 **어떤 임계치·설정으로 계산되는지** 운영자가 확인할 곳이 없고(가이드 PC-FE-183가 "이중 임계 메커니즘"을 텍스트로만 설명), read-model 지연의 근거(프로젝션 상태)도 볼 수 없다. 이를 **`/wms/operations` 읽기 전용 운영 상태/설정 조회 페이지**로 표면화한다.

## 배경 사실 (검증됨)

- **프로젝션 상태**: 콘솔 클라이언트 `wms-refs-api.ts`의 `getProjectionStatus()` → `GET /operations/projection-status` 이미 존재(소비처 0). `WMS_ADMIN_BASE_URL` 재사용.
- **운영 설정**: producer `GET /api/v1/admin/settings`, `GET /settings/{key}`(§5.1/5.2) read는 `WMS_VIEWER` 이상. **콘솔 클라이언트에 settings GET 함수는 아직 없음 → 신규 추가 필요**(같은 `WMS_ADMIN_BASE_URL` 아래, `callWmsAdmin`, **신규 env 불필요**).
- settings/operations **write**(설정 변경)는 `WMS_ADMIN` 이상 + 콘솔 write-admin 범위 밖(§§2–5 v1 콘솔 범위 밖) → 이 task는 read 전용.

## Scope

### 신규
1. **`app/(console)/wms/operations/page.tsx`** — 서버 컴포넌트(`force-dynamic`). 자격 워터폴 `wms/inventory/page.tsx` 미러. 제목 "WMS 운영설정". `getWmsOperationsState(eligible)` seed.
2. **`features/wms-ops/api/wms-settings-api.ts`** (신규) — `listSettings()` → `GET /settings`, `getSetting(key)` → `GET /settings/{key}`. `SettingSchema`/`SettingPageSchema`를 `types.ts`에 추가. `callWmsAdmin` 사용.
3. **`features/wms-ops/api/operations-state.ts`** — `getWmsOperationsState(eligible)` → `{ settings, projection, notEligible, forbidden, degraded }`. settings + `getProjectionStatus()` 병렬 fan-out(각 셀 독립 degrade, 401만 re-throw → redirect).
4. **`features/wms-ops/components/WmsOperationsScreen.tsx`** — `'use client'` 또는 server 렌더. 두 섹션: **운영 설정**(키·값·설명 테이블: 예약 TTL, 저재고 임계치) + **프로젝션 상태**(read-model lag·last-processed 등). 헤딩 `wms-operations-heading`.
5. **`app/api/wms/settings/route.ts`** (GET 프록시, READ-ONLY) — projection-status는 기존 프록시 있으면 재사용, 없으면 `app/api/wms/operations/projection-status/route.ts` 추가.
6. **테스트**: `WmsOperationsScreen.test.tsx`(설정/프로젝션 렌더·부분 degrade·forbidden/empty), `wms-operations-state.test.ts`(not-eligible/eligible/403/503/401, 한쪽 degrade 시 다른 섹션 유지).

### 수정
7. **`shared/ui/ConsoleSidebarNav.tsx`** — WMS `children` **맨 끝**에 `{ href: '/wms/operations', label: '운영설정', testid: 'nav-wms-operations' }` 추가 → `… → 출고 → (마스터 →) 운영설정`.
8. **`features/wms-ops/index.ts`** — export.
9. **스펙**: `console-integration-contract.md`(§2.4.5 wms read에 운영설정/프로젝션 조회 표면) + `console-web/architecture.md`(라우트 트리 `wms/operations`).

## Out of Scope
- 설정 **변경(write)**·운영자 RBAC(users/roles/assignments) 조회 — `WMS_ADMIN` 권한 + 콘솔 write-admin 범위 밖. 이 task는 `WMS_VIEWER` read만.
- 개요 저재고 배지 로직 변경 — 운영설정은 임계치를 "표시"만 하고 계산은 producer 유지.

## Acceptance Criteria
- **AC-1** `/wms/operations` 라우트가 자격 워터폴을 갖고 "WMS 운영설정" 화면을 렌더. Nav `WMS ▸ 운영설정`(맨 끝) 클릭·딥링크로 진입 + active.
- **AC-2** 운영 설정 섹션에 예약 TTL·저재고 기본 임계치가 키·값·설명으로 표시된다(producer가 제공하는 키 기준).
- **AC-3** 프로젝션 상태 섹션에 read-model 처리 상태(lag 등)가 표시된다. 한 섹션 degrade 시 다른 섹션은 유지(독립 셀).
- **AC-4** 회복탄력성(403→권한 없음, 503→점검 필요, 401→redirect) 커버. `pnpm lint` + `tsc --noEmit` + `vitest`(wms 전체) green.

## Edge Cases
- producer가 특정 설정 키를 미제공 → 해당 행 생략(빈 값 강제 표시 금지).
- settings는 ok인데 projection-status degrade(또는 반대) → 각 섹션 독립 상태 렌더.
- settings read가 `WMS_VIEWER`로 403 → 권한 안내(뷰어인데도 403이면 producer 역할 매핑 이슈로 기록).

## Failure Scenarios
- settings/projection 한쪽 실패를 전체 degrade로 오처리 → 정상 섹션까지 숨김. state 테스트가 셀별 독립 degrade 단언.
- `SettingSchema` 드리프트 → parse 실패. 화면 테스트가 목 응답 파싱 단언.

## Related Specs / Contracts
- `console-integration-contract.md` §2.4.5 (wms read) — 운영설정/프로젝션 조회 표면 신설.
- `console-web/architecture.md` — 라우트 트리 `wms/operations`.
- Producer: wms `admin-service-api.md` §5(settings) + §6.2(projection-status) (소비만, 계약 변경 없음). 저재고 이중 임계 개념: `features/wms-guide/data.ts`.
