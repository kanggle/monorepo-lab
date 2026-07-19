# Task ID

TASK-FE-092

# Title

web-store FSD refactoring sweep — layer-import hygiene, dead-code removal, duplication extraction (Tier 1+2)

# Status

done

# Owner

frontend

# Task Tags

- code
- refactor

# Goal

`web-store` 전체 서비스 스캔(FSD 준수 / dead-code·중복 / 복잡도·네이밍 3-축)에서 발굴된, **동작 무변경** 리팩토링 기회 중 고가치·저~중위험 항목(Tier 1+2)을 정리한다. 아키텍처 결함이나 버그를 고치는 것이 아니라, 성숙한 FSD 코드베이스의 위생·중복을 다듬는 순수 리팩토링이다. `platform/refactoring-policy.md`를 전면 준수한다: **동작 무변경**, **카테고리당 커밋 분리**, **전후 테스트 GREEN**, **선언 아키텍처 방향으로만** 이동.

# Scope

## In Scope

각 항목은 독립 커밋(카테고리별 분리)으로 처리한다. 대상 파일은 모두 `apps/web-store/src/` 하위.

### Tier 1 — layer/pattern 준수 + dead-code (저~무위험)

1. **cross-feature import 위생 (layer-violation)** — 4개 파일이 `useAuth`를 `@/features/auth`(cross-feature)에서 import한다. 훅의 실제 출처는 `shared/lib/auth-context.ts`이며 `features/auth`는 단순 재노출일 뿐이다. 소비자를 실제 출처로 재지정:
   - `features/cart/model/cart-context.tsx`
   - `features/cart/ui/AddToCartButton.tsx`
   - `features/wishlist/ui/WishlistButton.tsx`
   - `features/review/ui/ReviewList.tsx`
   - 재지정 대상: `@/shared/lib/auth-context`(또는 먼저 `shared/lib` barrel에 `auth-context` 추가 후 그 경로). FSD 허용 방향(`features` → `shared`) 준수.
2. **dead-code 삭제 (confirmed, grep 0)**:
   - `shared/ui/icons/oauth/` 디렉터리 전체(3 아이콘 + barrel) — 외부 소비자 0.
   - `shared/auth/session.ts`의 `isAuthenticated()` 서버 함수 — 호출부 0.
   - `features/auth/model/types.ts`, `features/search/model/types.ts` — whole-file 미사용 re-export/interface.
   - `entities/product/index.ts`의 타입 re-export(`ProductSummary`/`ProductDetail`/`ProductVariant`) — 소비자 전원 `@repo/types` 직접 사용.

### Tier 2 — duplication 추출 (저~중위험, 기존 테스트 커버)

3. **`withMockFallback` 추출** — `entities/user/api/address-api.ts`의 4개 CRUD 함수가 동일한 `if(useMock)→mock; else try{real}catch{fallback}` 형태를 반복. `withMockFallback(mockFn, realFn)` 헬퍼로 통합.
4. **`mapQueryError` 추출** — `features/order/model/use-order-detail.ts`, `features/notification/model/use-notification-detail.ts`, `features/order/model/use-shipping-tracking.ts`가 `isApiError(x) && x.code === 'XXX_NOT_FOUND' ? msgA : msgB` 매핑을 반복. `shared/lib`에 `mapQueryError(error, {notFoundCode, notFoundMessage, fallbackMessage})` 추출.
5. **`createListQueryKeys` 추출** — `coupon/notification/order/review`의 `model/query-keys.ts`가 `all/lists/list` 팩토리 형태를 복붙. `shared/lib`에 제네릭 `createListQueryKeys(scope)` 추출.
6. **`decodeServerJwt` 추출** — `shared/auth/session.ts`와 `shared/auth/federated-logout.ts`가 쿠키명 해석 + `getToken({...})` 조립 로직을 복붙(보안-민감, 사일런트 드리프트 위험). 공유 `decodeServerJwt()` 헬퍼로 통합.
7. **`SearchFilters` param 헬퍼 확장** — `features/search/ui/SearchFilters.tsx`의 price-range 버튼이 `updateParam`(바로 위)이 일반화한 URLSearchParams 변형 로직을 인라인 재구현. `updateParam`을 다중 키 지원(`updateParams`)으로 확장 후 재사용.
8. **`Pagination` 통합** — `features/review/ui/Pagination.tsx`와 `shared/ui/Pagination.tsx`가 API만 다른 두 페이지네이션 primitive. 단일 shared primitive로 통합(review 소비자를 shared로 이관).

## Out of Scope

- **동작/UX 변경 일체** — 순수 리팩토링만.
- **API·이벤트 계약 변경** — 없음.
- **아래 "리팩토링 아님 — 별도 판단 필요" 항목**(별도 task 후보):
  - `features/coupon/api/coupon-api.ts`의 `applyCoupon`(test-only) — 서버측 쿠폰 적용이 staged 기능일 수 있어 맹목 삭제 금지.
  - `features/product/ui/ProductDetail.tsx`(test-only, `widgets/product-detail-with-cart`로 대체됨) — 삭제 유력하나 확인 필요.
  - `/orders/*` vs `/my/orders/*` 중복 route 트리 — 통합은 redirect = 동작 변경.
  - `PriceDisplay` 미채택(~8개 컴포넌트 인라인 `toLocaleString()`) — 다수 파일 + 포맷 동치 확인 필요.
- **Tier 3(컴포넌트 구조: ReviewCard props, AddressForm 훅 등) / Tier 4(폴리시)** — 본 task에서 제외(후속 후보).
- **테스트 로직 변경** — import 경로/mock 대상 경로 갱신은 허용하되 테스트가 검증하는 내용은 불변(refactoring-policy: 프로덕션·테스트 동시 리팩토링 금지, 테스트는 별도 조정).

# Acceptance Criteria

- [ ] **AC-1** 4개 파일 어디에도 `@/features/auth`를 통한 `useAuth` cross-feature import가 없다(실제 출처 `@/shared/lib/auth-context` 경유). `architecture.md` Forbidden Dependencies 준수.
- [ ] **AC-2** `shared/ui/icons/oauth/`, `session.ts:isAuthenticated()`, `features/auth/model/types.ts`, `features/search/model/types.ts`, `entities/product/index.ts` 타입 re-export가 제거되고, 삭제 대상 참조 잔존 0(grep 재검증).
- [ ] **AC-3** Tier 2 중복 6종이 단일 헬퍼/모듈로 통합되고, 각 호출부가 그 헬퍼를 사용한다.
- [ ] **AC-4** 각 리팩토링이 **독립 커밋**(카테고리 혼합 금지)이며, `git diff`상 `src/main` 동작 산출물이 동작-불변(외부 관측 동작 동일).
- [ ] **AC-5** `pnpm --filter web-store test`(vitest) 전건 GREEN — 리팩토링 전후 모두. 기존 테스트 검증 내용 무변경(경로/mock만 갱신).
- [ ] **AC-6** `tsc --noEmit` 0 오류 + `next lint` 0 오류/경고.
- [ ] **AC-7** CI(`Frontend (unit/lint)` 등 web-store 레인) GREEN — 로컬 vitest 기동 불가(Node24↔vitest4) 가능성 때문에 **CI가 최종 권위**.

# Related Specs

- `specs/services/web-store/architecture.md` (FSD Allowed/Forbidden Dependencies, Boundary Rules)
- `platform/refactoring-policy.md`
- `platform/coding-rules.md`
- `platform/naming-conventions.md`

# Related Skills

- `.claude/skills/frontend/architecture/feature-sliced-design.md`
- `.claude/skills/backend/refactoring/SKILL.md`

# Related Contracts

- N/A (계약 변경 없음)

# Target App

- `apps/web-store`

# Implementation Notes

## 선례

- `TASK-FE-051`(checkout cross-feature import) / `TASK-FE-052`(entities↔features address-api 중복)가 동일 계열을 앞서 처리 — 같은 해결 패턴(공유 concern을 하위 레이어 출처로 재지정, `features`→`entities`/`shared` 방향) 적용.
- 본 task의 #3(`withMockFallback`)은 FE-052가 다룬 **파일 간** 중복과 다른, `entities/user/api/address-api.ts` **내부**의 4× try/catch 중복이다.

## 검증 환경 주의

- 이 개발 호스트에서 web-store vitest는 Node24↔vitest4 module evaluator 이슈로 로컬 기동 불가일 수 있다(memory `env_webstore_vitest4_node24_module_evaluator`). 그 경우 로컬은 `tsc`+`next lint`로 확인하고 **CI(Node20)가 테스트 권위**다.
- web-store 로컬 검증은 `pnpm lint` 필수(tsc+vitest가 못 잡는 CI 프런트 RED 존재 — memory `env_console_web_local_verify_needs_lint`의 web-store 대응).
- worktree는 pnpm node_modules 미populate — 메인 체크아웃 node_modules로 junction 후 검증(memory `env_worktree_pnpm_no_populate_verify_via_main`). worktree 제거 전 junction 선제거(cleanup hazard).

# Edge Cases

- **#1**: `shared/lib/index.ts`가 현재 `auth-context`를 재노출하지 않는다 — barrel에 추가하거나 `@/shared/lib/auth-context` 직접 import. 4개 파일의 기존 테스트가 `@/features/auth` 또는 `useAuth`를 mock하는 경우 mock 대상 경로 갱신 필요(검증 내용 불변).
- **#2 oauth icons**: 소셜 로그인 부활 계획이 있으면 삭제 대신 보존 — 현재 IAM OIDC 단일 경로라 계획 없음으로 판단, 그러나 커밋 메시지에 근거 명시.
- **#5 query-keys/#3 mock**: `entities/user`·각 feature의 barrel(`index.ts`) re-export 경로가 바뀔 수 있어 사용처 확인.
- **#8 Pagination**: 두 primitive의 prop/callback API가 달라 review 소비자 시그니처 조정 필요 — 렌더 결과·접근성 마크업 동일 유지.

# Failure Scenarios

- import 재지정 후 `useAuth`가 undefined가 되어 인증 게이팅(cart/wishlist/review) 동작이 깨지는 경우.
- dead-code로 판단해 삭제한 심볼이 실은 배럴/동적 경로로 소비되던 경우(→ grep 재검증으로 방지, AC-2).
- 헬퍼 추출 후 mock-fallback/에러-매핑 분기의 미묘한 동작(폴백 조건, not-found 메시지)이 원본과 달라지는 경우 → 기존 테스트가 잡아야 하며, 못 잡으면 테스트 갭(별도 기록).
- 테스트 mock 경로 갱신 누락으로 vitest RED.

# Test Requirements

- 기존 web-store vitest 전건 통과 유지(검증 내용 불변, import/mock 경로만 갱신 허용).
- 삭제/추출 대상에 대한 신규 테스트는 원칙적으로 불필요(동작 무변경 리팩토링) — 단, 헬퍼 추출(#3~#8)로 새 공용 함수가 생기면 해당 순수 함수 단위 테스트를 **별도 커밋**으로 추가 가능(프로덕션·테스트 동시 리팩토링 금지 준수).

# Definition of Done

- [ ] Tier 1(#1~#2) + Tier 2(#3~#8) 카테고리별 독립 커밋 완료
- [ ] 삭제 대상 참조 잔존 0(grep 재검증)
- [ ] FSD Forbidden Dependencies 위반 0
- [ ] `tsc --noEmit` 0 + `next lint` 0
- [ ] 로컬 vitest GREEN(가능 시) / CI web-store 레인 GREEN(권위)
- [ ] worktree 정리(junction 선제거 → `git worktree remove`)
