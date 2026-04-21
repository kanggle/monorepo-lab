# Task ID

TASK-FE-065

# Title

web-store `widgets/` 디렉토리 명명 규칙을 kebab-case로 통일

# Status

ready

# Owner

frontend

# Task Tags

- refactor
- naming

# Goal

`apps/web-store/src/widgets/` 하위 디렉토리 명명 규칙이 불일치하여 FSD 컨벤션과 `specs/platform/naming-conventions.md`에 어긋난다. kebab-case(`header`, `footer`, `hero`)와 PascalCase(`ProductDetailWithCart`, `ProductListWithWishlist`)가 혼재되어 있으므로 전부 kebab-case로 통일한다.

# Scope

## In Scope

- 디렉토리 rename:
  - `apps/web-store/src/widgets/ProductDetailWithCart/` → `product-detail-with-cart/`
  - `apps/web-store/src/widgets/ProductListWithWishlist/` → `product-list-with-wishlist/`
- 모든 import 경로 일괄 갱신 (앱 코드 + 테스트 코드):
  - `@/widgets/ProductDetailWithCart` → `@/widgets/product-detail-with-cart`
  - `@/widgets/ProductListWithWishlist` → `@/widgets/product-list-with-wishlist`
  - 상대 경로 형태도 포함
- 컴포넌트(파일명/export)는 기존 PascalCase 유지 — 디렉토리만 변경
- 관련 barrel `index.ts` export 경로 확인

## Out of Scope

- 컴포넌트 자체 이름 변경
- 다른 FSD 레이어(`features/`, `entities/` 등) 디렉토리 명명 변경 (필요 시 별도 태스크)
- 기능/동작 변경

# Acceptance Criteria

- [ ] `apps/web-store/src/widgets/` 하위 모든 디렉토리가 kebab-case
- [ ] 프로젝트 전체에서 `widgets/ProductDetailWithCart` / `widgets/ProductListWithWishlist` 경로 참조 0건
- [ ] `npm --prefix apps/web-store test` 전부 통과 (베이스라인과 동일)
- [ ] `npm --prefix apps/web-store build` (또는 type-check) 통과
- [ ] 브라우저에서 홈/상품 상세 페이지 정상 렌더링 확인

# Related Specs

- `specs/platform/naming-conventions.md`
- `specs/services/web-store/architecture.md`

# Related Contracts

해당 없음 (내부 리팩토링)

# Target Service

- `web-store`

# Architecture

Follow:

- `specs/services/web-store/architecture.md` (FSD)
- `specs/platform/naming-conventions.md`

# Implementation Notes

- Git rename으로 히스토리 보존: `git mv widgets/ProductDetailWithCart widgets/product-detail-with-cart` (Windows에서는 중간 단계 필요 시 `git mv A A_tmp && git mv A_tmp a`)
- 대규모 import 경로 변경이므로 한 번에 일괄 치환 후 타입체크/테스트로 검증
- 영향 범위 예상:
  - `app/(store)/products/[id]/page.tsx` 주변
  - `app/(store)/page.tsx` (홈)
  - 관련 테스트 파일
- tsconfig/next.config 경로 alias는 `@/*` 루트만 사용하므로 추가 설정 불필요

# Edge Cases

- Windows 파일시스템 대소문자 무시로 인한 rename 실패 → 두 단계 rename으로 해결
- 순환 import 유발 가능성 낮음(widgets는 상위 레이어)

# Failure Scenarios

- 누락된 import 경로 존재 시 빌드 실패 → 즉시 수정
- 테스트 mock 경로(`vi.mock('@/widgets/...')`)도 같이 갱신 필요

# Test Requirements

- 기존 web-store 테스트 전부 통과
- 빌드 성공
- (권장) 실제 `npm --prefix apps/web-store dev` 후 홈/상품 상세 페이지 스모크 확인

# Definition of Done

- [ ] 디렉토리 rename 완료
- [ ] 모든 import 경로 갱신 완료
- [ ] 테스트 통과
- [ ] 빌드 통과
- [ ] Ready for review
