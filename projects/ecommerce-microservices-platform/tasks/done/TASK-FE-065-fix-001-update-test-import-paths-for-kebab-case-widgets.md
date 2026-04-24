# Task ID

TASK-FE-065-fix-001

# Title

TASK-FE-065 리뷰: 테스트 파일 3개의 widgets import 경로를 kebab-case로 갱신

# Status

done

# Owner

frontend

# Task Tags

- fix
- naming
- test

# Goal

TASK-FE-065 리뷰 결과 발견된 이슈: `purchase-summary.test.tsx`, `selected-items-list.test.tsx`, `use-product-variant-selection.test.ts` 3개 테스트 파일이 아직 PascalCase 디렉토리 경로를 참조하고 있어 `npm --prefix apps/web-store test` 실패. kebab-case 경로로 갱신하여 테스트를 통과시킨다.

# Scope

## In Scope

- 아래 3개 파일의 import 경로 수정:
  - `apps/web-store/src/__tests__/purchase-summary.test.tsx` line 3:
    `@/widgets/ProductDetailWithCart/PurchaseSummary` → `@/widgets/product-detail-with-cart/PurchaseSummary`
  - `apps/web-store/src/__tests__/selected-items-list.test.tsx` line 4:
    `@/widgets/ProductDetailWithCart/SelectedItemsList` → `@/widgets/product-detail-with-cart/SelectedItemsList`
  - `apps/web-store/src/__tests__/use-product-variant-selection.test.ts` line 16:
    `@/widgets/ProductDetailWithCart/use-product-variant-selection` → `@/widgets/product-detail-with-cart/use-product-variant-selection`

## Out of Scope

- 컴포넌트/훅 구현 변경 없음
- 다른 파일 변경 없음

# Acceptance Criteria

- [ ] 프로젝트 전체에서 `widgets/ProductDetailWithCart` / `widgets/ProductListWithWishlist` 경로 참조 0건 (테스트 파일 포함)
- [ ] `npm --prefix apps/web-store test` 전부 통과

# Related Specs

- `specs/platform/naming-conventions.md`
- `specs/services/web-store/architecture.md`

# Related Contracts

해당 없음 (테스트 경로 수정만)

# Edge Cases

- Windows 파일시스템은 대소문자 무시하므로 로컬에서는 성공처럼 보일 수 있으나 CI에서는 실패. 반드시 kebab-case 경로로 수정 필요.

# Failure Scenarios

- 수정 후에도 다른 테스트 파일에서 이전 경로 참조가 남아 있을 경우 빌드 실패
