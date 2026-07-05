# TASK-PC-FE-199 — E-Commerce 개요·상품·주문·사용자 god-file 컴포넌트 분할 (ecommerce-ops · 1/2)

**Status:** done
**Area:** platform-console / console-web · **Refactor:** behavior-preserving god-file split
**Analysis model:** Opus 4.8 · **Impl model:** Opus 4.8 (frontend-engineer 디스패치 — testid/markup byte-보존)

---

## Goal

WMS 도메인 분할(PC-FE-197/198)에 이어 E-Commerce 콘솔 `ecommerce-ops` 피처의 god-file 컴포넌트를 콘솔 god-file split 시리즈(PC-FE-098~153) 휴리스틱대로 프레젠테이션 조각으로 분할한다(**전면 sweep 1/2 — 개요·리스트 화면**). **behavior-preserving** — 마크업·testid·props·데이터 흐름·훅·렌더 출력 전부 불변. 기존 테스트가 계약(무수정 통과).

대상 god-file(components/):
- `EcommerceOverview.tsx`(~340) · `ProductsScreen.tsx`(~264) · `OrdersScreen.tsx`(~251) · `UsersScreen.tsx`(~264)

## Scope

각 god-file에서 응집된 **프레젠테이션** 조각을 같은 `components/` 디렉터리의 신규 sibling 파일로 추출; 원본은 orchestration(query/seed state·list-state 분기·필터 폼·delete confirm-gate·페이지네이션 핸들러)을 유지하는 얇은 컨테이너로 축소. 모든 `data-testid`/`aria-*`/className/요소 순서/key/조건 렌더/텍스트 verbatim 보존, export 심볼·시그니처 불변. **ecommerce 콘솔 컨벤션 준수**: 공유 DetailHeader ghost 버튼, `dl` 순서(명칭→상태→식별자→날짜) 무변경.

**Out of scope:** `api/`·`hooks/`·proxy 라우트·producer·contract·테스트 무변경. `PromotionsScreen`·`SellersScreen`·`NotificationsScreen`·`PromotionForm`·`TemplateForm`·`PromotionDetail`·`ImageUploadField`은 **PC-FE-200(sweep 2/2)** 대상 — 미접촉. `index.ts` barrel 공개 API 불변.

## Acceptance Criteria
- **AC-1** 대상 4개 god-file이 의미 있게 축소되고, 추출 조각이 원본 렌더 출력을 byte-동일하게 재현.
- **AC-2** 모든 testid(인덱스 템플릿 포함: `product-row-${i}`·`order-detail-${i}`·`user-row-status-${i}`·`products-count-*`·`<key>-service-status`·`<key>-count-degraded`·페이지네이션 `*-prev/next/pageinfo`)·aria·요소 순서 보존.
- **AC-3** `index.ts` 공개 API 불변(신규 파일=내부 조각).
- **AC-4** `tsc --noEmit` 0 + `next lint` 0 + `vitest`(ecommerce 전 스위트) green, 회귀 0. 신규 테스트 불필요.

## Edge Cases / Failure Scenarios
- **EcommerceOverview는 순수 server component** → 추출 조각도 `'use client'` 없이 server-compatible 유지. 리스트 3화면의 테이블 조각은 `'use client'` 부착(원본 동일).
- **delete confirm-gate 보존**: 테이블 조각의 `onDelete({id,name})` 콜백이 컨테이너의 `setDelError(null); setToDelete(product)`로 되돌아가 원본 인라인 클로저와 동일 — `ConfirmDialog`+delete state는 컨테이너 잔류.
- **리스트 화면은 orchestration-heavy** → 필터 폼은 강제 추출 안 함(테이블+페이지네이션 shell만 추출; PC-FE-197 ShippingsTable 선례 동형).
- **페이지네이션**: `setQuery` 핸들러·prev/next-disabled·pageinfo 분리 보존.

## Related
- 미러: TASK-PC-FE-197/198 (wms 컴포넌트 분할).
- 후속: TASK-PC-FE-200 (ecommerce 프로모션/셀러/알림 화면 + 폼 분할, sweep 2/2).
- 컨벤션: [[proj_console_ecommerce_detail_conventions]].
- 기존 테스트(계약): `tests/unit/{ecommerce-overview,ProductsScreen,ecommerce-list-refresh-after-create}.test.tsx` + orders/users/products proxy·nav.
