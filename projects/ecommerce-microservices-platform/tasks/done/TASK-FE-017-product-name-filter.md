# Task ID

TASK-FE-017

# Title

상품 목록 name 필터 구현 — API 계약 업데이트 및 프론트엔드 연동

# Status

ready

# Owner

frontend

# Task Tags

- code
- api

# Goal

TASK-FE-016 리뷰에서 발견된 name 필터 미전달 이슈를 수정한다. product-api 계약에 `name` 쿼리 파라미터를 추가하고, admin-dashboard의 `useProducts` 훅에서 name 필터를 API에 전달하도록 구현한다.

# Scope

## In Scope

- `specs/contracts/http/product-api.md`: GET /api/products 쿼리 파라미터에 `name` (optional) 추가
- `packages/types/src/product.ts`: `ProductListParams` 인터페이스에 `name` 필드 추가
- `apps/admin-dashboard/src/features/product-management/hooks/use-products.ts`: searchParams에서 `name` 추출 후 `getProducts()`에 전달
- `apps/admin-dashboard/src/features/product-management/components/ProductList.tsx`: FilterBar에 검색 입력을 name 필터로 연결
- 기존 테스트 업데이트 및 name 필터 테스트 추가

## Out of Scope

- 검색 디바운싱
- 백엔드 product-service 구현 (별도 백엔드 태스크)
- Elasticsearch 연동

# Acceptance Criteria

- [ ] `specs/contracts/http/product-api.md`에 `name` 쿼리 파라미터가 정의됨
- [ ] `ProductListParams`에 `name?: string` 필드가 존재
- [ ] `useProducts` 훅이 searchParams에서 `name`을 추출하여 API에 전달
- [ ] ProductList의 FilterBar 검색 입력이 name 필터와 연결
- [ ] name 필터가 빈 문자열일 때 API에 전달되지 않음
- [ ] 기존 테스트 통과 및 name 필터 관련 테스트 추가
- [ ] 빌드 성공

# Related Specs

- `specs/contracts/http/product-api.md`
- `specs/platform/coding-rules.md`
- `specs/platform/testing-strategy.md`

# Related Contracts

- `specs/contracts/http/product-api.md`

# Edge Cases

- name 필터가 빈 문자열인 경우 파라미터 제외
- name 필터와 status 필터 동시 적용
- name 필터 적용 시 page가 0으로 리셋

# Failure Scenarios

- 백엔드에서 name 파라미터를 아직 지원하지 않는 경우 (무시됨, 에러 없음)
- 검색어 입력 후 API 실패 시 에러 상태 표시
