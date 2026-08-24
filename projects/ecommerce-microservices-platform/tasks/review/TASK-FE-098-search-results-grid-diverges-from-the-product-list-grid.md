# Task ID

TASK-FE-098

# Title

검색 결과 그리드가 다른 목록 페이지와 열이 어긋난다 — 인라인 사본이 모바일 2열 규칙을 안 가진다

# Status

review

# Owner

frontend

# Task Tags

- code

---

# 배경

`/products?q=…` (검색 결과)와 `/products` · `/` (전체 상품 · 인기 상품)은 같은
`ProductCard` 를 같은 열 수로 깔아야 하는데, **그리드 정의가 세 벌로 갈라져 있었다.**

| 위치 | 정의 | ≤768px 규칙 |
|---|---|---|
| `features/product/ui/ProductList.module.css` | `repeat(auto-fill, minmax(220px, 1fr))` / `gap: --space-6` | **있음** — `repeat(2, 1fr)` / `gap: --space-3` |
| `shared/ui/Skeleton.module.css` (`SkeletonProductGrid`) | 같음 | **있음** |
| `features/search/ui/SearchResults.tsx` | 같은 값을 **인라인 style 로 복사** | **없음** |

데스크톱 폭에서는 셋이 우연히 같아 보이지만, **좁은 폭에서 검색 결과만 갈라진다** —
다른 목록은 강제 2열(간격 `--space-3`)인데 검색 결과는 `auto-fill` 이 그대로 살아
1열(간격 `--space-6`)이 된다. 인라인 style 은 미디어 쿼리를 가질 수 없으므로,
이 어긋남은 값을 맞춰 적는 것으로는 고칠 수 없고 **사본이 존재하는 한 재발한다.**

같은 화면의 로딩 스켈레톤(`SkeletonProductGrid`)도 별도 사본이라, 반응형 규칙을 한 번
바꾸면 세 곳 중 두 곳만 고쳐질 수 있는 상태였다.

---

# Goal

검색 결과 목록이 다른 상품 목록 페이지와 **모든 폭에서 같은 열 수·같은 간격**으로 깔린다.

---

# Scope

## In Scope

- 상품 카드 그리드 정의를 `shared/ui/ProductGrid` 한 곳으로 수렴
- 소비자 3곳 전환: `ProductList`(전체 상품·홈), `SearchResults`(검색 결과), `SkeletonProductGrid`(로딩)
- 중복 정의 제거 (`ProductList.module.css` 삭제, `Skeleton.module.css` 의 `.grid` 제거)

## Out of Scope

- 카드 내용 차이 — 검색 결과에는 위시리스트 버튼이 없다(`/products` 는 있다). 열 정렬과
  무관한 별개 판단이므로 건드리지 않는다.
- 검색 결과 화면의 제목(`page-title`) 부재 등 그리드 밖 레이아웃 차이
- 열 수·간격 값 자체의 변경 (기존 목록 페이지 값을 그대로 정본으로 삼는다)

---

# Acceptance Criteria

- [x] **AC-0 (재측정)** — 사본이 몇 벌인지, 어느 사본에 반응형 규칙이 빠졌는지 실제로 센다
- [x] **AC-1** — 검색 결과 · 전체 상품 · 홈 인기 상품 · 로딩 스켈레톤이 **같은 클래스**를 쓴다
- [x] **AC-2** — 빌드 산출물에서 그리드 규칙이 **정확히 1회**만 정의된다
      (사본이 남아 있으면 이 판정이 2 이상이 된다)
- [x] **AC-3** — 인라인 `auto-fill, minmax(220px …)` 문자열이 빌드 산출물에서 **0건**이다
- [x] **AC-4** — `tsc --noEmit` · `next lint` 무경고, `next build` 컴파일·정적 생성 통과

---

# Related Specs

- `projects/ecommerce-microservices-platform/apps/web-store/src/features/search/ui/SearchResults.tsx`
- `projects/ecommerce-microservices-platform/apps/web-store/src/features/product/ui/ProductList.tsx`
- `projects/ecommerce-microservices-platform/apps/web-store/src/shared/ui/Skeleton.tsx`

# Related Contracts

- 없음 (프런트 레이아웃)

---

# Edge Cases

- 검색 결과 0건 — 그리드가 아니라 `EmptyState` 가 그려진다(기존 동작 유지, 문구도 유지)
- 로딩 스켈레톤 → 실제 결과 전환 시 열 수가 바뀌면 레이아웃이 튄다 ⇒ 스켈레톤도 같은
  그리드를 쓰게 한 이유다
- `ProductGrid` 는 서버 컴포넌트에서 쓰이므로 `'use client'` 를 붙이지 않는다
  (배럴 `@/shared/ui` 대신 모듈 경로로 직접 import 해 클라이언트 컴포넌트 배럴을 끌어오지 않는다)

# Failure Scenarios

- **값만 맞춰 인라인 style 에 다시 적는다** → 인라인은 미디어 쿼리를 못 가지므로 좁은 폭에서
  똑같이 어긋난다. AC-3 이 그것을 잡는다
- **`SearchResults` 에만 CSS 모듈을 새로 만든다** → 사본이 3벌에서 4벌로 늘 뿐이다. AC-2 가 잡는다

# Test Requirements

- 기존 `search-results.test.tsx` · `product-list.test.tsx` 통과 (렌더 결과 불변)
- 빌드 산출물 판정(AC-2/AC-3)

# Definition of Done

- [x] 수정
- [x] 빌드 산출물 검증
- [x] Ready for review

---

# 결과 (2026-08-24)

## AC-0 재측정 — 사본은 **3벌**, 반응형이 빠진 것은 검색 결과 1벌

위 표가 실측이다. `grid-template-columns` 를 web-store `src/` 전체에서 세었을 때 상품
그리드 성격의 정의는 `ProductList.module.css` · `Skeleton.module.css` · `SearchResults.tsx`
셋이고, 이 중 **미디어 쿼리를 가진 것은 CSS 모듈 두 벌뿐**이다. 인라인 style 은 구조적으로
가질 수 없다 — 즉 이것은 "값이 어긋난 것"이 아니라 **표현 매체가 규칙을 담을 수 없는 것**이다.

## 수정

`shared/ui/ProductGrid.tsx` + `ProductGrid.module.css` 를 정본으로 두고 소비자 3곳을
전환했다. 중복 정의는 지웠다(`ProductList.module.css` 파일 삭제, `Skeleton.module.css` 의
`.grid` + 미디어 쿼리 블록 제거). 값 자체는 기존 목록 페이지의 것을 그대로 옮겼으므로
`/products` · 홈의 렌더 결과는 불변이고, **바뀌는 것은 검색 결과 화면뿐**이다.

## AC 별 결과

| AC | 결과 |
|---|---|
| AC-0 | ✅ 사본 3벌, 반응형 누락 1벌(검색) |
| AC-1 | ✅ 4개 소비 지점이 `ProductGrid_grid__*` 단일 클래스 |
| AC-2 | ✅ 빌드 CSS 전체에서 `grid-template-columns:repeat(auto-fill,minmax(220px,1fr))` **1건** |
| AC-3 | ✅ `.next/server` 전체에서 인라인 `auto-fill, minmax(220px` **0건** |
| AC-4 | ✅ `tsc --noEmit` rc=0 · `next lint` "No ESLint warnings or errors" · `next build` 컴파일 성공 + 정적 23/23 생성 |

빌드 산출물 실측:

```
$ grep -o "grid-template-columns:repeat(auto-fill,minmax(220px,1fr))" .next/static/css/*.css | sort | uniq -c
      1 f411eb24a92fafca.css:grid-template-columns:repeat(auto-fill,minmax(220px,1fr))

$ grep -o "@media (max-width:768px){[^}]*grid-template-columns:repeat(2,1fr)[^}]*}" .next/static/css/*.css
f411eb24a92fafca.css:@media (max-width:768px){.ProductGrid_grid__eZL_G{grid-template-columns:repeat(2,1fr);gap:var(--space-3)}
```

프리렌더된 `index.html`(홈) · `cart.html`(로딩 스켈레톤)의 RSC 페이로드에서 같은 클래스명이
확인된다. 검색 결과는 쿼리가 있어야 렌더되는 동적 경로라 프리렌더 산출물이 없고, 소스 상
같은 `<ProductGrid>` 를 쓰는 것으로 확인했다.

## 🔴 로컬 vitest 는 기동 불가 — 단위 테스트는 CI 가 권위다

`vitest 4 × Node 24` 의 `#module-evaluator` 로 러너가 기동조차 못 한다(이 저장소에 이미
기록된 블로커, 로컬 Node 24 / CI 20). 기존 렌더 테스트 통과 증거는 **CI Frontend unit 레인**이다.
`next build` 의 standalone 트레이스 복사 단계가 Windows `EPERM: symlink` 로 실패하는데,
이는 컴파일·정적 생성(23/23)이 끝난 뒤의 심볼릭 링크 권한 문제로 이 변경과 무관하다.

분석·구현=Opus 5.
