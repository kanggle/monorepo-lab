# Task ID

TASK-FE-099

# Title

검색으로 찾은 상품은 목록에서 찜할 수 없다 — 검색 결과 카드에만 위시리스트 버튼이 없다

# Status

ready

# Owner

frontend

# Task Tags

- code
- test

---

# 배경

`/products`(전체 상품)와 `/`(홈 인기 상품)는 `ProductListWithWishlist` 위젯을 거쳐
카드마다 하트 버튼(`WishlistButton`)을 단다. `/products?q=…`(검색 결과)는
`SearchResults` 가 `ProductCard` 를 직접 그리면서 `action` 을 안 넘겨 **하트가 없다.**

사용자에게 보이는 차이는 이것이다 — **같은 상품인데, 검색해서 찾으면 목록에서 바로 찜을
못 하고 전체 상품 목록에서 찾으면 찜이 된다.** 검색은 상품을 찾는 주된 경로이므로 찜 동선이
가장 필요한 화면에서 빠져 있는 셈이다.

`TASK-FE-098`(그리드 수렴)에서 열 정렬은 맞췄고, 이 티켓은 그때 범위 밖으로 명시해 둔
**카드 내용 차이**를 닫는다.

## 왜 위젯을 그대로 재사용하지 않는가 (계층)

`ProductListWithWishlist` 는 **widget** 이고 `SearchResults` 는 **feature** 다. feature 가
widget 을 import 하면 FSD 계층이 뒤집힌다. 실측: 이 앱의 `features/` 는 현재 **다른 feature도
widget도 import 하지 않는다**(각각 0건) — 즉 깨질 규칙이 실제로 지켜지고 있다.

그래서 이미 같은 문제를 풀어 둔 `ProductList` 의 방식을 그대로 쓴다 — `renderAction` 렌더
프롭. 주입은 두 계층을 다 아는 **app 레이어**(`products/page.tsx`)가 한다.

---

# Goal

검색 결과 카드에서도 전체 상품 목록과 똑같이 하트 버튼으로 찜을 추가/해제할 수 있다.

---

# Scope

## In Scope

- `SearchResults` · `SearchResultsSection` 에 `renderAction` 옵셔널 프롭 추가 (`ProductList` 과 동일 시그니처)
- `products/page.tsx` 검색 분기에서 `WishlistButton` 주입
- 배선 회귀 테스트

## Out of Scope

- `WishlistButton` 자체의 동작 (이미 `wishlist-button.test.tsx` 가 덮는다)
- 비로그인 사용자 처리 방식 변경 — 버튼이 `/login` 으로 보내는 기존 동작을 그대로 쓴다
- 검색 결과에 위시리스트 **상태**를 서버에서 미리 실어 오는 최적화 (카드마다 `checkWishlist`
  질의가 나가는 것은 전체 상품 목록과 동일한 기존 성질이다)

---

# Acceptance Criteria

- [x] **AC-0 (재측정)** — 하트가 붙는 목록과 안 붙는 목록을 전수로 세고, feature→widget /
      feature→feature import 가 실제로 0건인지 확인한다 (계층 판단의 근거)
- [x] **AC-1** — 검색 결과 카드에 위시리스트 버튼이 붙는다
- [x] **AC-2** — 기존 `SearchResults` 렌더 테스트가 **수정 없이** 통과한다
      (`renderAction` 은 옵셔널 — 안 넘기면 지금과 같은 렌더)
- [x] **AC-3** — 배선이 끊기면 실패하는 테스트가 붙는다
- [x] **AC-4** — `tsc --noEmit` · `next lint` 무경고 · `next build` 통과
- [x] **AC-5** — FSD 계층 불변: `features/` 에서 `@/widgets` · 다른 `@/features` import **0건 유지**

---

# Related Specs

- `projects/ecommerce-microservices-platform/apps/web-store/src/features/search/ui/SearchResults.tsx`
- `projects/ecommerce-microservices-platform/apps/web-store/src/features/product/ui/ProductList.tsx` (`renderAction` 선례)
- `projects/ecommerce-microservices-platform/apps/web-store/src/app/(store)/products/page.tsx`

# Related Contracts

- 없음 (기존 위시리스트 API 를 그대로 쓴다)

---

# Edge Cases

- 검색 결과 0건 — `EmptyState` 라 버튼 자체가 없다
- 비로그인 — 버튼은 보이고, 누르면 `/login` (기존 `WishlistButton` 동작)
- 검색 결과의 상품이 이미 찜 상태 — 하트가 채워져 보여야 한다(`useWishlistCheck` 가 상품별로 판단)
- 하트 클릭이 카드 링크(상품 상세)로 새지 않아야 한다 — `WishlistButton` 이 이미
  `preventDefault`/`stopPropagation` 한다

# Failure Scenarios

- **`SearchResults` 가 `@/widgets/product-list-with-wishlist` 를 직접 import 한다** → 화면은
  되지만 feature→widget 계층 역전이 생긴다. AC-5 가 그것을 잡는다
- **`renderAction` 을 필수 프롭으로 만든다** → 기존 렌더 테스트와 다른 호출부가 깨진다. AC-2 가 잡는다

# Test Requirements

- `SearchResults` 가 넘겨받은 `renderAction` 을 카드마다 부른다는 회귀 테스트
- `SearchResultsSection` 이 그 프롭을 `SearchResults` 로 전달한다는 회귀 테스트

# Definition of Done

- [x] 수정 + 테스트
- [x] 게이트 통과
- [x] Ready for review
