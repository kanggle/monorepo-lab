# Task ID

TASK-FE-081

# Title

web-store 상품상세 클라이언트/서버 컴포넌트 분리: `ProductDetailWithCart` 를 Server Component 로 전환해 정적 스캐폴드(breadcrumb·이미지·상품명·가격·설명)를 서버 렌더하고, 장바구니 선택 상태가 필요한 상호작용 부분만 새 client island `ProductPurchasePanel` 로 분리 — behavior-preserving

# Status

done

# Owner

frontend (Opus 4.8 분석 — 구현 권장=Opus; behavior-preserving 클라/서버 경계 분리, contract/spec/backend 무변경)

# Task Tags

- code
- test
- performance

---

# Dependency Markers

- **note (현 구조)**: `/products/[id]` page 는 Server Component(ISR `revalidate = 60`)로 `getProduct` 결과를 `<ProductDetailWithCart product={...}>` 에 넘긴다. 그런데 `ProductDetailWithCart` 가 최상단 `'use client'` 라 breadcrumb·`<ProductImage>`·상품명 `<h1>`·가격·설명까지 위젯 전체가 클라이언트 컴포넌트로 hydration 된다. 실제 상호작용 상태(`useProductVariantSelection` — 옵션 선택/수량/장바구니/즉시주문/토스트)는 위젯 하단(옵션 섹션 + 구매 요약)에만 필요하다.
- **note (분리 경계)**: 정적 영역(breadcrumb·이미지·이름·가격·설명·divider)은 데이터만으로 렌더되고 훅이 없다 → 서버. 상호작용 영역(`VariantSelector`[useRef+useClickOutside]·`SelectedItemsList`·`PurchaseSummary`·`Toast`)은 `useProductVariantSelection` 의 state/handler 가 필요 → client. `WishlistButton`·`ProductImage` 는 이미 각자 client island 라 서버가 그대로 자식으로 렌더 가능(직렬화 가능 prop 만 전달).
- **note (Toast 위치)**: `Toast` 는 `position: fixed`(zIndex 9999) 오버레이라 마운트 위치를 페이지 최상단 → 구매 패널 내부로 옮겨도 시각적으로 동일(behavior-preserving).

# Goal

상품상세 페이지의 정적 영역이 클라이언트로 hydration 되지 않고 서버에서 렌더되도록 `ProductDetailWithCart` 를 Server Component 로 전환한다. 장바구니 선택 상태가 필요한 상호작용 영역만 별도 client island(`ProductPurchasePanel`)로 떼어낸다.

behavior-preserving: 렌더 출력(breadcrumb·이미지·이름·가격·설명·옵션 드롭다운·선택목록·구매버튼·토스트)·옵션 선택/수량/삭제·장바구니 담기/즉시주문·토스트 동작 전부 불변. 변경은 client boundary 의 시작 위치(위젯 최상단 → 하단 구매 패널)뿐이다.

# Scope

## In Scope

- `src/widgets/product-detail-with-cart/ProductDetailWithCart.tsx` — 최상단 `'use client'` 제거, Server Component 화. 정적 스캐폴드(breadcrumb·`<ProductImage>`·이름+`<WishlistButton>`·가격·설명·divider) 직접 렌더 + 하단에 `<ProductPurchasePanel product={product} />` 렌더. `images` 배열 계산은 `useMemo` → 평범한 서버 계산으로 전환.
- `src/widgets/product-detail-with-cart/ProductPurchasePanel.tsx` (신규, client) — `'use client'`, `useProductVariantSelection(product)` 소유, `Toast` + 옵션 섹션(`VariantSelector`+`SelectedItemsList`) + `PurchaseSummary` 렌더. 기존 위젯 하단 JSX 를 그대로 이동.
- **검증** — `npx tsc --noEmit`(타입) + 가능 시 `pnpm build`(RSC 경계 위반 검출) + 기존 위젯 단위테스트 무회귀.

## Out of Scope

- `VariantSelector`/`SelectedItemsList`/`PurchaseSummary`/`use-product-variant-selection`/`types`/CSS — 내부 로직/마크업 변경 없음(패널로 import 위치만 동일 유지).
- 다른 web-store 페이지(장바구니·체크아웃·my/* 등 — 대부분 전체가 상호작용이라 분리 부적격, 조사 완료).
- `ProductImage`/`WishlistButton` 내부 변경.
- 백엔드/계약/스펙 변경.

# Acceptance Criteria

- [ ] `ProductDetailWithCart` 가 Server Component(파일 최상단 `'use client'` 없음)이며, `/products/[id]` 의 렌더 출력·breadcrumb·이미지·이름·가격·설명·옵션/구매 상호작용·토스트가 기존과 동일.
- [ ] 상호작용 영역이 `ProductPurchasePanel`(client)로 분리되어 옵션 선택/수량/삭제·장바구니 담기(addItem+토스트)·즉시주문(checkout 이동)이 기존과 동일하게 동작.
- [ ] 기존 `src/__tests__/product-detail-with-cart.test.tsx` 가 무변경으로 green(최상위 위젯을 렌더하므로 합성 DOM 동일).
- [ ] `npx tsc --noEmit` clean. (web-store 로컬 vitest 는 vitest4×Node24 비호환으로 미기동 가능 → 그 경우 CI Node20 이 테스트 권위, `env_webstore_vitest4_node24_module_evaluator`. `pnpm build` 로 RSC 경계 검증.)

# Related Specs

- web-store 상품상세 화면 스펙(해당 use-case/feature) — read-only, "상호작용 있는 부분만 client" 원칙 적용. 신규 규칙 없음.

# Related Contracts

- 변경 없음. client boundary 위치만 조정(동일 `getProduct` 소비).

# Target Service

- `ecommerce-microservices-platform` / `apps/web-store` — `src/widgets/product-detail-with-cart/{ProductDetailWithCart,ProductPurchasePanel}.tsx`. behavior-preserving 클라/서버 경계 분리.

# Architecture

- Next App Router RSC: Server Component 는 client 자식(`ProductPurchasePanel`·`WishlistButton`·`ProductImage`)을 직렬화 가능 prop(`product`·`productId`·`images: string[]`)으로 렌더할 수 있다. 정적 스캐폴드는 훅 없이 데이터만으로 렌더되므로 서버로 내려가고, 장바구니 상태 훅이 필요한 하단만 client island 로 남는다.
- 렌더 트리/DOM 구조 보존: 패널은 fragment 를 반환해 `styles.info` 내부 구조(옵션 섹션 + 구매 요약)를 그대로 유지. `Toast`(fixed)만 마운트 위치가 페이지 최상단 → 패널 내부로 이동(시각 동일).

# Edge Cases

- `product.images` 비었을 때 fallback `[/images/products/${id}.jpg]` — 서버 계산으로 동일 유지(`useMemo` 제거, 로직 보존).
- `product.variants.length === 0` 이면 옵션 섹션 미렌더 — 기존 조건부 그대로 패널로 이동.
- 테스트가 `@/shared/ui` Toast·`@/features/cart`·`@/features/wishlist` 등을 mock — import 가 패널로 옮겨가도 모듈 경로 동일하므로 mock 적용 그대로.

# Failure Scenarios

- 서버로 전환한 `ProductDetailWithCart` 가 실은 클라이언트 전용 API 를 쓰면 `pnpm build` 가 RSC 경계 에러로 검출 → build 검증.
- 위젯 단위테스트가 분리 후 DOM 변화로 RED → 합성 DOM 은 동일하므로 무회귀 기대; 차이 발생 시 테스트가 잡도록.
- web-store 로컬 vitest 미기동(Node24) → tsc + build + CI Node20 으로 커버(테스트 권위는 CI).

# Definition of Done

- [ ] `ProductDetailWithCart` Server Component 전환 + `ProductPurchasePanel` client island 분리
- [ ] 상품상세 behavior-preserving (tsc clean; build green; 위젯 테스트 무회귀)
- [ ] scope = web-store only, contract/spec/backend 무변경
- [ ] Acceptance Criteria 충족
- [ ] Ready for review

---

# Implementation Result (2026-06-27)

**변경**: `ProductDetailWithCart` 를 Server Component 로 전환, 상호작용 영역을 신규 `ProductPurchasePanel`(client island)로 분리.

- `ProductDetailWithCart.tsx` — `'use client'` 제거. breadcrumb·`<ProductImage>`·상품명 `<h1>`+`<WishlistButton>`·가격·설명·divider 를 서버 렌더. `images` 계산은 `useMemo` → 평범한 서버 계산. 하단에 `<ProductPurchasePanel product={product} />`.
- `ProductPurchasePanel.tsx` (신규, `'use client'`) — `useProductVariantSelection(product)` 소유 + `Toast`(fixed)·옵션 섹션(`VariantSelector`+`SelectedItemsList`)·`PurchaseSummary` 를 기존 JSX 그대로 렌더.

**번들 실측 (`pnpm build`, 동일 worktree·toolchain, stash before/after)**:

| route | First Load before | First Load after | Δ |
|---|---|---|---|
| `/products/[id]` | 167 kB | 166 kB | −1 kB |

정적 스캐폴드(breadcrumb/이름/가격/설명 마크업)가 클라이언트 hydration 에서 빠지고 서버 RSC 페이로드로 이동 → First Load JS −1 kB. (route 고유 "Size" 3.71→6.39 kB 는 분리된 패널이 라우트 전용 청크로 재분류 + 서버 컴포넌트 RSC 페이로드 집계 영향; 클라이언트 다운로드 지표인 First Load 는 감소.) 핵심 가치는 정적 영역의 불필요한 hydration 제거(아키텍처) — KB 절감은 부수.

**검증**: `npx tsc --noEmit` clean · `pnpm build` 성공(23/23, RSC 경계 위반 없음 — server 전환 정확). web-store 로컬 vitest 는 vitest@4.1.0 × Node v24.14.0 `ERR_PACKAGE_IMPORT_NOT_DEFINED #module-evaluator` 로 미기동 확정(`env_webstore_vitest4_node24_module_evaluator`) → 기존 `product-detail-with-cart.test.tsx` 는 최상위 위젯을 렌더해 합성 DOM 이 동일하므로 CI Node20 에서 무변경 green 기대(테스트 권위=CI). scope = web-store only, contract/spec/backend 무변경.
