# TASK-PC-FE-126 — E-Commerce 운영 목록이 등록(별도 페이지) 후 새로고침 전까지 새 행을 안 보여주는 버그

- **Status**: ready
- **Project**: platform-console
- **Service**: console-web
- **Analysis model**: Opus 4.8 / **Implementation model**: Opus 4.8 (react-query 캐시 무효화 동작 버그픽스)

## Goal

콘솔 E-Commerce 운영에서 **알림 템플릿·상품·프로모션·셀러 등 "등록은 별도 `/new` 페이지에서, 목록은 부모 페이지에서"** 하는 화면들이, 등록 성공 후 목록으로 돌아와도 **새로 등록한 행이 보이지 않는** 버그를 고친다(하드 리로드해야만 보임). 운영자가 방금 만든 항목을 즉시 확인할 수 있게 한다.

근본 원인: ecommerce-ops 의 seeded 목록 쿼리는 `staleTime: 30s` + `refetchOnMount: false`(첫 방문 시 SSR seed 가 권위) 이다. 그런데 등록/수정은 **별도 `/new`·`[id]/edit` 페이지**에서 일어나, mutation 의 `invalidate` 가 호출되는 시점에 목록 페이지는 **언마운트(쿼리 inactive)** 상태다. `invalidateQueries` 기본 `refetchType:'active'` 는 inactive 쿼리를 건너뛰고, **`refetchType:'all'`·`refetchQueries({type:'all'})` 로도 SSR seed 로만 채워진(실제 fetch 한 적 없는) inactive 쿼리는 refetch 되지 않는다**(실측 확인). 결과적으로 목록으로 돌아오면 stale 캐시(옛 행)가 `router.refresh()` 가 만든 fresh SSR seed 를 가려, 새 행이 staleTime(30s) 경과/하드 리로드 전까지 안 보인다.

## Scope

**In scope** (console-web only) — 각 hook 의 mutation `invalidate` 헬퍼에서 목록 키에 대해 `removeQueries({ type: 'inactive' })` 를 추가(기존 `invalidateQueries` 는 유지):

1. `features/ecommerce-ops/hooks/use-ecommerce-notifications.ts`
2. `features/ecommerce-ops/hooks/use-ecommerce-products.ts`
3. `features/ecommerce-ops/hooks/use-ecommerce-promotions.ts`
4. `features/ecommerce-ops/hooks/use-ecommerce-sellers.ts`
5. `features/ecommerce-ops/hooks/use-ecommerce-orders.ts`
6. `features/ecommerce-ops/hooks/use-ecommerce-shippings.ts`
7. `features/ecommerce-ops/hooks/use-ecommerce-images.ts`
8. `tests/unit/ecommerce-list-refresh-after-create.test.tsx` — cross-page create 후 inactive 캐시 제거 + remount 시 fresh seed 로 새 행 노출 회귀 테스트.

**Out of scope**: 백엔드(목록/등록 producer 정상 — DB 에 행은 정상 persist 됨), seeded 쿼리의 `staleTime`/`refetchOnMount` 정책 변경(첫-로드 SSR-seed 최적화 보존), 비-ecommerce-ops 의 다른 목록 hook.

## Acceptance Criteria

- **AC-1 — cross-page 등록 즉시 반영.** 별도 `/new` 에서 항목을 등록하고 목록으로 돌아오면(앱 내 네비게이션, 하드 리로드 없이) 새 행이 보인다.
- **AC-2 — in-place mutation flash 없음.** 목록이 mounted 인 상태의 mutation(상품 삭제, 주문/배송 상태 변경)은 기존처럼 `invalidateQueries` 가 백그라운드 refetch 로 갱신하며, 로딩 placeholder 로의 깜빡임이 없다(`removeQueries` 는 `type:'inactive'` 한정이라 active 쿼리는 건드리지 않음).
- **AC-3 — 회귀 테스트.** seed 1행 목록을 마운트→언마운트(inactive)→create 성공 시, inactive 목록 캐시가 제거되고, fresh SSR seed(2행)로 remount 하면 2행(새 행 포함)이 노출됨을 검증.
- **AC-4 — 게이트.** console-web `pnpm lint` + `tsc --noEmit` + `vitest run` GREEN(신규 테스트 포함).

## Related Specs

- console-integration-contract § 2.4.10 — ecommerce 운영 surface(상품/프로모션/셀러/알림 등록은 `/new`, 목록은 부모 페이지; mutation 성공 시 `router.refresh()` + 목록 재진입).
- TASK-PC-FE-081/086/089/090 — 각 facet 의 seeded 목록 + create-on-/new 패턴 도입(이 버그의 공통 출처).

## Related Contracts

- 해당 없음(클라이언트 캐시 동작 한정 — producer 계약·와이어 변경 없음).

## Edge Cases

- seeded 쿼리(page 0)만 `refetchOnMount:false` → 본 버그 대상. 비-seeded 페이지(page≥1)는 `staleTime:0`+`refetchOnMount:true` 라 영향 없음(remove 해도 정상 재조회).
- detail 쿼리는 `staleTime:0`+`refetchOnMount:true(default)` 라 이미 항상 갱신 → 본 수정은 **목록** 키에 한정.
- 동일 QueryClient 를 공유하는 SPA 네비게이션 전제(콘솔 구조). 전체 페이지 재로드는 애초에 새 캐시라 무관.

## Failure Scenarios

- `removeQueries` 에 `type:'inactive'` 필터를 빠뜨리면 active(마운트된) 목록까지 제거돼 in-place mutation 시 로딩 깜빡임 회귀 → AC-2 로 가드.
- 향후 새 ecommerce-ops facet 추가 시 동일 패턴(seeded 목록 + create-on-/new) 이면 같은 무효화 조합을 적용해야 함(미적용 시 동일 stale-list 함정 재현).
