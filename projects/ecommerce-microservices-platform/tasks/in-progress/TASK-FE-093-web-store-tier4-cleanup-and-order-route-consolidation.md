# Task ID

TASK-FE-093

# Title

web-store Tier 4 polish + ProductDetail dead-code removal + /orders → /my/orders route consolidation

# Status

in-progress

# Owner

frontend

# Task Tags

- code
- refactor

# Goal

TASK-FE-092 전수 스캔의 후속. 두 성격의 작업을 카테고리별 커밋으로 분리해 처리한다:

1. **순수 리팩토링(동작 무변경)** — Tier 4 폴리시(미사용 export/barrel 정리, 중복 `useRequireAuth` 제거, 미사용 prop, 스켈레톤 primitive) + `ProductDetail` dead-code 삭제.
2. **동작/UX 변경(리팩토링 아님)** — 중복 route 트리 `/orders/*`를 `/my/orders/*`로 통합(정경 URL = `/my/orders`, 구 URL은 redirect). 이 부분은 URL이 바뀌므로 `platform/refactoring-policy.md`의 "동작 무변경" 대상이 아니라 별도 커밋으로 명확히 분리한다.

FE-092와 동일하게 `pnpm exec tsc --noEmit`+`next lint`를 로컬 게이트로, CI(Node20)를 테스트 권위로 둔다(로컬 vitest 는 Node24↔vitest4 로 기동 불가).

# Scope

## In Scope

### Tier 4 — polish (순수 리팩토링)

1. **중복 `useRequireAuth()` 제거** — `my/*` 페이지 중 `my/layout.tsx`가 이미 인증 게이팅을 하는데도 자식 페이지에서 `useRequireAuth()`를 중복 호출하는 곳 제거(다른 `my/*` 페이지는 이미 생략). 동작 무변경.
2. **미사용 type export 정리** — 어떤 외부 소비자도 참조하지 않는 hook 반환/상태 타입 interface의 `export` 제거(소비자는 추론 타입 사용). tsc 가 실사용 여부를 검증(사용 중이면 컴파일 에러 → 되돌림).
3. **미사용 barrel re-export 트림** — feature `index.ts`가 재노출하나 배럴 경로로 소비되지 않는 항목 정리(tsc 로 실사용 검증).
4. **`ReviewCard` 미사용 `reviewId` prop 제거**.
5. **스켈레톤 loading primitive 추출** — `app/(store)/**/loading.tsx`들의 반복 스켈레톤 스캐폴딩을 공유 primitive로(동작/시각 무변경).

### Bucket B — dead-code

6. **`ProductDetail` 삭제** — `features/product/ui/ProductDetail.tsx`(+ `ProductDetail.module.css` + `__tests__/product-detail.test.tsx` + `features/product/index.ts`의 배럴 항목). 소비자는 자기 테스트뿐이며 프로덕션은 `widgets/product-detail-with-cart/ProductDetailWithCart`가 대체(`ProductListWithWishlist`는 `ProductList`를 사용, ProductDetail 아님).

### Bucket C — route consolidation (동작/UX 변경)

7. **`/orders/*` → `/my/orders/*` 통합** — 두 트리가 동일 컴포넌트(`OrderHistory`/`OrderDetailView`)를 렌더. 정경 = `/my/orders`(my/layout 이 auth+container 제공). 조치: `/orders`·`/orders/[id]`를 각각 `/my/orders`·`/my/orders/[id]`로 redirect(App Router `redirect()`), 내부 링크(Footer·ProfileDropdown 등 `/orders` 가리키는 곳) 전부 `/my/orders`로 갱신. E2E/테스트가 `/orders`를 직접 치면 갱신.

## Out of Scope

- **PriceDisplay 채택** — 인라인 `toLocaleString()원`을 PriceDisplay로 바꾸면 원 렌더가 시각 변경(작고 회색 span) → UI 일관성 결정 사안, 본 task 제외.
- **`applyCoupon` 삭제** — 기존 백엔드 엔드포인트(`@repo/api-client`/`@repo/types` 계약 존재)의 프런트 래퍼. 서버측 쿠폰 적용 배선 계획 가능성 → **KEEP**, 무조치.
- **Tier 3 컴포넌트 구조**(ReviewCard props 객체화·AddressForm 훅·AddressSection·OrderItemsSection) — 후속.
- API/이벤트 계약 변경 없음.

# Acceptance Criteria

- [ ] **AC-1** `my/*` 자식 페이지에 중복 `useRequireAuth()` 없음(layout 게이팅 유지, 인증 동작 무변경).
- [ ] **AC-2** 미사용 type export/배럴 re-export 제거, 잔여 실사용 참조 0(tsc 통과가 증거).
- [ ] **AC-3** `ReviewCard`에 미사용 `reviewId` prop 없음.
- [ ] **AC-4** 반복 loading 스켈레톤이 공유 primitive로 통합(렌더 결과 동일).
- [ ] **AC-5** `ProductDetail` 컴포넌트/CSS/테스트/배럴 항목 제거, 잔여 참조 0.
- [ ] **AC-6** `/orders`·`/orders/[id]` 접근이 `/my/orders`·`/my/orders/[id]`로 redirect. 내부 링크 중 `/orders` 잔존 0(의도적 redirect 스텁 제외).
- [ ] **AC-7** 각 항목 카테고리별 독립 커밋. 순수 리팩토링과 route 변경(동작 변경)은 커밋 분리.
- [ ] **AC-8** `tsc --noEmit` 0 + `next lint` 0.
- [ ] **AC-9** CI web-store 레인(Frontend unit/lint&build/E2E) GREEN — 최종 권위.

# Related Specs

- `specs/services/web-store/architecture.md`
- `platform/refactoring-policy.md`
- `platform/coding-rules.md` / `platform/naming-conventions.md`

# Related Skills

- `.claude/skills/frontend/architecture/feature-sliced-design.md`

# Related Contracts

- N/A

# Target App

- `apps/web-store`

# Implementation Notes

- 선행 TASK-FE-092(#2691) 스캔 산물. FE-092에서 보류한 Tier 4 + ProductDetail + route 통합을 사용자 결정(2026-07-19)으로 착수.
- 검증 환경: 로컬 vitest 불가(Node24↔vitest4, memory `env_webstore_vitest4_node24_module_evaluator`) → tsc+lint 로컬, CI 권위. worktree node_modules 는 `pnpm install --frozen-lockfile`로 populate(junction 아님, memory `env_worktree_pnpm_no_populate_verify_via_main` 2026-07-19 갱신분).

# Edge Cases

- **#2/#3**: 타입 export/배럴 항목을 하나 지웠는데 실은 외부 소비 → tsc 즉시 에러 → 그 항목만 되돌림(개별 검증).
- **#5 skeleton**: loading.tsx 는 route 세그먼트 파일이라 default export 시그니처 유지 필수(내부만 공유 primitive 사용).
- **#7 route**: `/orders/[id]` redirect 시 `[id]` 파라미터를 목적지로 전달. 인증 필요 경로였으므로 redirect 후에도 my/layout 게이팅이 걸림. 외부(북마크/링크) `/orders` 접근도 redirect 로 흡수. E2E auth-helper 가 `/orders`를 치는지 확인.

# Failure Scenarios

- 중복 useRequireAuth 제거로 인증 게이팅이 풀려 미인증 접근 노출(→ my/layout 게이팅 존재 재확인).
- 배럴/타입 export 제거가 실사용을 끊어 빌드 실패(→ tsc 게이트).
- ProductDetail 삭제가 살아있는 소비자를 끊음(→ grep 재검증, 소비자=테스트뿐 확인).
- route redirect 무한 루프 또는 파라미터 유실(→ redirect 대상 검증, `[id]` 전달 확인).

# Test Requirements

- 기존 web-store vitest 전건 통과 유지(경로/mock 갱신 허용, 검증 내용 불변).
- ProductDetail 테스트는 컴포넌트와 함께 삭제.
- route redirect 는 기존 E2E(orders 경로)가 있으면 목적지 기준으로 갱신.

# Definition of Done

- [ ] Tier 4(#1~#5) + ProductDetail(#6) + route 통합(#7) 카테고리별 커밋
- [ ] `tsc` 0 + `next lint` 0
- [ ] CI web-store 레인 GREEN
- [ ] worktree 정리(pnpm-install real node_modules → PowerShell 삭제 → prune)
