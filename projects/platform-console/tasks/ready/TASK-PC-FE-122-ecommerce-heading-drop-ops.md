# TASK-PC-FE-122 — 콘솔 E-Commerce 섹션 페이지 헤딩에서 "운영" 접미사 제거

- **Status**: ready
- **Project**: platform-console
- **App**: console-web (Next.js)
- **Analysis model**: Opus 4.8 / **Implementation model**: Opus 4.8 (헤딩 텍스트 정리)

## Goal

콘솔 E-Commerce 하위 섹션의 페이지 헤딩(h1)에서 군더더기 "운영" 접미사를 제거해 사이드바 메뉴 라벨(상품/주문/배송/프로모션/셀러/알림)과 톤을 맞춘다.

- `E-Commerce 상품 운영` → `E-Commerce 상품`
- `E-Commerce 주문 운영` → `E-Commerce 주문`
- `E-Commerce 배송 운영` → `E-Commerce 배송`
- `E-Commerce 프로모션 운영` → `E-Commerce 프로모션`
- `E-Commerce 셀러 운영` → `E-Commerce 셀러`
- `E-Commerce 알림 템플릿 운영` → `E-Commerce 알림 템플릿`

(사용자 헤딩은 이미 "E-Commerce 사용자", 최상위 "운영"(/ecommerce overview)은 대상 아님.)

## Scope

**In scope** (console-web only, 텍스트만):

각 섹션의 h1 이 **두 곳**에 있어 양쪽 모두 수정(에러/degrade 상태 = `page.tsx` 의 `heading`, 정상 렌더 = Screen 컴포넌트):

1. `src/app/(console)/ecommerce/{products,orders,shippings,promotions,sellers,notifications/templates}/page.tsx` (6)
2. `src/features/ecommerce-ops/components/{Products,Orders,Shippings,Promotions,Sellers,Notifications}Screen.tsx` (6)
3. 테스트 단언 갱신: `tests/unit/ProductsScreen.test.tsx`, `tests/e2e/federation-ecommerce-sellers-multi.spec.ts`

**Out of scope**: 사이드바 nav 라벨(이미 운영/상품/… — TASK-PC-FE-119), 라우트/testid/h1 `id`/기능 변경, 사용자 헤딩, 최상위 ecommerce overview 헤딩.

## Acceptance Criteria

- **AC-1 — 헤딩.** 6개 섹션 페이지(정상 + degrade/forbidden 상태 모두) h1 텍스트에서 " 운영" 이 제거된다(`E-Commerce <섹션명>`).
- **AC-2 — 불변식.** h1 의 `id`(`ecommerce-*-heading`)·class·testid·라우트 불변. "운영" 외 텍스트/요소 변경 없음.
- **AC-3 — 게이트.** ecommerce 스크린 단위테스트 GREEN(ProductsScreen 헤딩 단언을 새 텍스트로 갱신) + `tsc --noEmit` clean + `next lint` clean. CI `vitest run` GREEN. (셀러 e2e 단언도 갱신.)

## Related Specs

- 없음(순수 프레젠테이션 텍스트).

## Related Contracts

- 없음.

## Edge Cases

- 각 섹션 h1 은 page.tsx(에러상태)와 Screen(정상)에 중복 정의되어 있어, 한쪽만 고치면 상태에 따라 헤딩이 불일치 → **양쪽 모두** 수정해야 일관.

## Failure Scenarios

- page.tsx 또는 Screen 한쪽 누락 시 degrade/정상 전환에서 헤딩이 달라짐 → AC-1 이 양쪽 상태를 모두 요구.
