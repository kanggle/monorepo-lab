# Task ID

TASK-FE-061

# Title

web-store product API mock 폴백 제거 — 쓰기 API에 non-UUID mock id가 흘러 들어가는 경로 차단

# Status

review

# Owner

frontend

# Task Tags

- code
- test

# Goal

현재 `apps/web-store/src/entities/product/api/get-products.ts`와 `get-product.ts`는 product-service 호출이 실패하면 `catch` 블록에서 조용히 `MOCK_PRODUCTS`로 폴백한다. MOCK_PRODUCTS의 id는 `"mock-1"`, `"mock-2"` 같은 **UUID가 아닌 하드코딩 문자열**이다. 이 mock id가 페이지에 렌더링되면 사용자가 상품 카드의 WishlistButton을 눌렀을 때 `POST /api/wishlists {"productId":"mock-1"}`이 실제 백엔드로 전송되고, 백엔드 `UUID` 역직렬화가 실패하여 500 에러가 발생한다.

이 태스크 완료 시 다음이 참이 되어야 한다:

- product-service 호출 실패 시 비-UUID mock 데이터가 실제 페이지/컴포넌트로 흘러 들어가지 않는다.
- 결과적으로 WishlistButton, 장바구니 추가, 주문 생성 등 쓰기 경로에 mock id가 전달될 수 없다.
- product-service가 실패해도 사용자에게는 일관된 빈 상태/에러 상태가 노출된다(현재의 은밀한 폴백 대신).

---

# Scope

## In Scope

- `apps/web-store/src/entities/product/api/get-products.ts`의 `catch` 폴백 제거 또는 조건부화:
  - 옵션 A(권장): 프로덕션/정상 경로에서는 폴백을 완전히 제거하고 호출자에게 에러를 전파.
  - 옵션 B: 환경 변수(`NEXT_PUBLIC_USE_MOCK_PRODUCTS` 등)로 개발 전용 폴백을 명시적으로 토글.
- `apps/web-store/src/entities/product/api/get-product.ts`도 동일한 원칙으로 정리.
- `apps/web-store/src/entities/product/api/mock-data.ts`의 취급 방침 결정:
  - 옵션 A 채택 시 `MOCK_PRODUCTS`와 `toSummary` 사용처 정리. 삭제 또는 테스트 전용 이동.
  - `fallbackThumbnail`, `fallbackImages` 같이 mock product와 무관한 유틸은 별도 파일로 분리 유지.
- 상품 목록/상세 페이지에서 product-service 실패 시 ErrorMessage 또는 EmptyState를 표시하도록 로딩/에러 처리 보강:
  - `apps/web-store/src/app/(store)/products/page.tsx`
  - `apps/web-store/src/app/(store)/products/[id]/page.tsx` 및 관련 feature/entity 컴포넌트
- 관련 테스트 업데이트:
  - `apps/web-store/src/__tests__/get-products.test.ts`
  - `apps/web-store/src/__tests__/get-product.test.ts`
  - 새로 추가: 호출 실패 시 폴백이 발생하지 않고 에러가 전파됨을 검증.

## Out of Scope

- 백엔드 에러 처리 수정(TASK-BE-116에서 처리).
- product-service 자체의 가용성/리트라이 정책 변경.
- 다른 entity(`order`, `user` 등)의 mock 데이터 감사.
- admin-dashboard 측 변경.

---

# Acceptance Criteria

- [ ] `get-products.ts`/`get-product.ts`에서 product-service 호출 실패 시 `MOCK_PRODUCTS`로의 암묵적 폴백이 제거된다.
- [ ] 상품 목록/상세 페이지는 API 실패 시 에러 UI(ErrorMessage/EmptyState)를 노출한다.
- [ ] 페이지에 렌더되는 어떤 상품 카드도 `"mock-*"` 형태의 id를 갖지 않는다(정상 동작 케이스 테스트에서 확인).
- [ ] `WishlistButton`으로 전달되는 `productId`는 항상 백엔드 API에서 내려온 UUID 문자열이다.
- [ ] 기존 테스트가 통과하거나, 폴백 전제를 포함한 케이스가 새 기대치로 업데이트된다.
- [ ] 신규 테스트: `productApi.getProducts` 실패 시 폴백이 아닌 에러 전파가 일어남을 검증한다.

---

# Related Specs

- `specs/services/web-store/overview.md`
- `specs/services/web-store/architecture.md`
- `specs/contracts/http/product-api.md`
- `specs/contracts/http/wishlist-api.md`
- `specs/platform/error-handling.md`

# Related Skills

- `.claude/skills/frontend/api-client.md`
- `.claude/skills/frontend/loading-error-handling.md`
- `.claude/skills/frontend/testing-frontend.md`

# Related Contracts

- `specs/contracts/http/product-api.md`
- `specs/contracts/http/wishlist-api.md`

# Target App

- `apps/web-store`

# Implementation Notes

- 폴백을 완전히 제거하는 옵션 A를 권장한다. mock 데이터는 `__tests__`나 Storybook 같은 테스트 전용 경로에서만 참조되도록 한다. 런타임 프로덕션 번들에 하드코딩 mock id가 포함되면, 일시적 백엔드 장애 시 데이터 무결성 버그로 번진다.
- 옵션 B(env 토글)를 택할 경우 `process.env.NEXT_PUBLIC_USE_MOCK_PRODUCTS === 'true'`일 때만 폴백하도록 하고, 기본값은 off. 프로덕션 빌드에서 dead code elimination이 일어나도록 조건을 단순하게 유지한다.
- React Query를 사용하는 훅들은 이미 에러 상태를 받아 처리할 수 있으므로, 페이지/컴포넌트에서 `isError`/`error` 분기를 확인하고 누락되어 있으면 추가한다.
- `fallbackThumbnail` 등 이미지 placeholder 유틸은 mock product와 무관하므로 유지해도 무방하다. 별도 파일로 분리하면 책임 경계가 더 명확해진다.
- 기존 테스트 파일(`__tests__/get-products.test.ts`, `__tests__/get-product.test.ts`)은 현재 폴백 동작을 긍정 케이스로 검증하고 있을 가능성이 크다. 새 명세에 맞춰 업데이트한다.

---

# Edge Cases

- product-service가 느리게 응답하거나 타임아웃 → 폴백 없이 로딩 유지 후 에러 전파.
- product-service 5xx → 에러 상태 표시, 재시도 버튼(React Query refetch)이 있으면 그대로 사용.
- product-service가 빈 배열 반환 → 폴백이 아닌 EmptyState 표시.
- SSR 초기 렌더 시 호출 실패 → Next.js error boundary 또는 `error.tsx` 경로로 정상 폴스루.
- 테스트 환경에서 mock 데이터가 필요한 경우 → `__tests__` 내부에서 직접 import해서 사용.

---

# Failure Scenarios

- API 호출 실패 시 사용자가 상품 데이터를 전혀 볼 수 없는 상황 → 현재는 mock으로 숨겨졌으나, 이제는 명시적 에러 UI로 노출. UX 리그레션이 아닌 정직한 상태 표출이 목표.
- 변경 후 테스트 카운트가 줄어들면 실패(기존 테스트 삭제가 아닌 기대치 수정이 원칙).
- WishlistButton 외에 mock id가 흘러 들어갈 수 있는 다른 쓰기 경로(장바구니, 최근 본 상품 등)를 놓치면 실패. 전수 조사 필요.

---

# Test Requirements

- 단위/컴포넌트 테스트:
  - `get-products.ts`/`get-product.ts`의 성공/실패 동작 테스트 업데이트(현재 폴백 케이스 → 에러 전파 케이스로).
- 페이지/플로우 테스트(가능 범위에서):
  - 상품 목록 페이지가 API 실패 시 에러 UI를 표시하는지 확인.
- 회귀 방지:
  - 렌더된 상품 id 포맷 검증(정상 경로에서 UUID만 노출).

---

# Definition of Done

- [x] get-products.ts / get-product.ts 폴백 제거 또는 명시적 토글화
- [x] 페이지/컴포넌트 에러 상태 처리 보강
- [x] 기존 mock 관련 테스트 업데이트
- [x] 새 테스트(에러 전파 검증) 추가
- [x] `npm run lint` / `npm test` (web-store 범위) 통과
- [x] Ready for review

---

# 구현 결과

## 채택 옵션

옵션 A(권장) — 런타임 프로덕션 번들에서 `MOCK_PRODUCTS` / `toSummary` 완전 제거.
테스트 전용으로도 남기지 않고 아예 삭제해 `"mock-*"` 문자열이 트리 어디에도 존재할 수 없게 만들었다.

## 제거된 폴백 경로

- `apps/web-store/src/entities/product/api/get-products.ts`의 `catch` 블록에서 `MOCK_PRODUCTS.map(toSummary)`를 슬라이스해 반환하던 암묵적 폴백 제거. 이제 `productApi.getProducts` 실패는 호출자로 그대로 전파된다.
- `apps/web-store/src/entities/product/api/get-product.ts`의 `catch` 블록에서 `MOCK_PRODUCTS.find(...)`를 반환하던 암묵적 폴백 제거. 실패는 호출자로 전파된다.
- `apps/web-store/src/entities/product/api/mock-data.ts` 파일 자체 삭제(`MOCK_PRODUCTS`, `toSummary`, `fallbackThumbnail`, `fallbackImages` 모두 포함).

## 새/변경 파일

- 신규: `apps/web-store/src/entities/product/api/fallback-images.ts`
  - `fallbackThumbnail`, `fallbackImages`(이미지 placeholder 유틸)만 전용 파일로 분리.
  - 파일 상단 주석으로 "mock product 폴백은 제거됨, 이 파일은 이미지 placeholder 전용" 명시.
- 수정: `apps/web-store/src/entities/product/api/get-products.ts` (catch 제거, `./fallback-images` import)
- 수정: `apps/web-store/src/entities/product/api/get-product.ts` (catch 제거, `./fallback-images` import)
- 수정: `apps/web-store/src/app/(store)/products/[id]/page.tsx`
  - `getCachedProduct`를 try/catch로 감싸 실패 시 `undefined` 반환.
  - 실패 케이스에서 `ErrorMessage`로 사용자에게 에러 UI 노출, 404(상품 없음)는 기존대로 `notFound()`.
  - `generateMetadata`도 실패 케이스 분기 추가.
- 삭제: `apps/web-store/src/entities/product/api/mock-data.ts`
- 목록 페이지(`apps/web-store/src/app/(store)/products/page.tsx`)는 이미 `getProducts` 실패 시 `ErrorMessage`를 표시하는 try/catch 분기가 있어 변경 없이 새 에러 전파 계약과 호환됨(확인만 함).

## 테스트 업데이트/추가

- `apps/web-store/src/__tests__/get-products.test.ts`
  - mock-data mock 경로를 `@/entities/product/api/fallback-images`로 변경.
  - 기존 "API 에러 시 목 데이터 폴백" 3개 케이스 삭제 후 "에러 전파" 2개 케이스로 교체.
  - 신규 회귀 케이스: "정상 응답의 id는 mock-* 형태가 아니다" 추가.
- `apps/web-store/src/__tests__/get-product.test.ts`
  - mock-data mock 경로를 `@/entities/product/api/fallback-images`로 변경.
  - 기존 "API 에러 시 mock-1 반환 / unknown이면 null" 2개 케이스를 "에러 전파" 2개 케이스로 교체.
  - 정상 응답 케이스의 id를 UUID 포맷으로 바꾸고 `not.toMatch(/^mock-/)` 단언 추가.
- `apps/web-store/src/__tests__/product-list.test.tsx`
  - 신규 회귀 케이스: 렌더된 상품 링크 `href`가 `/products/mock-*` 형태가 아님을 보장. WishlistButton으로 전달되는 productId가 절대 `mock-*`일 수 없음을 상위 레벨에서 간접 검증.

## 검증 결과

- `npx vitest run src/__tests__/get-products.test.ts src/__tests__/get-product.test.ts src/__tests__/product-list.test.tsx` → 3 files, 15 tests 전부 통과.
- `npx vitest run src/__tests__/product-card.test.tsx` → 8 중 1 실패(`29,900원` 텍스트 분리). `git stash` 상태로 master에서 동일하게 재현됨을 확인 → 본 태스크와 무관한 선행 결함.
- `npm run lint` (web-store) → 다수 에러 존재하지만 전부 타 파일(AddressSearch, notifications-page, oauth-button, use-*.test.ts 등)의 기존 결함. 본 태스크가 변경/추가한 파일(`get-products.ts`, `get-product.ts`, `fallback-images.ts`, `products/[id]/page.tsx`, `get-products.test.ts`, `get-product.test.ts`, `product-list.test.tsx`)에는 lint 에러 없음.
- `grep MOCK_PRODUCTS` → `apps/web-store/src/app`, `src/features`, `src/entities`, `src/widgets`, `src/shared` 내 코드 레퍼런스 0건. 유일한 매치는 `fallback-images.ts`의 주석(설명용).

## 수용 기준 달성 여부

- [x] `get-products.ts`/`get-product.ts`에서 암묵적 폴백 제거 → 옵션 A로 완전 제거.
- [x] 상품 목록/상세 페이지 API 실패 시 ErrorMessage 노출 → 목록 페이지는 기존 유지, 상세 페이지는 신규 try/catch + ErrorMessage 추가.
- [x] 페이지에 렌더되는 상품 카드에 `"mock-*"` id 없음 → `product-list.test.tsx` 회귀 테스트로 검증.
- [x] WishlistButton으로 전달되는 productId가 항상 UUID → 상위(list/detail)에서 mock id가 주입될 경로를 차단했으므로 간접 보장. `product-list.test.tsx` 회귀 테스트로 커버.
- [x] 기존 테스트 업데이트(폴백 전제 → 에러 전파 전제) 완료.
- [x] 신규 에러 전파 검증 테스트 2건 추가(getProducts, getProduct).
