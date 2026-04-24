# TASK-FE-005: web-store 가격 필터 버그 및 UX 개선 수정

## Goal
TASK-FE-003 리뷰에서 발견된 버그와 개선 사항을 수정한다.
가격 필터 이중 네비게이션 버그를 수정하고, 검색 실패 시 사용자 알림 및 로딩 상태 표시를 보완한다.

## Scope

### In Scope
- `SearchFilters.tsx` 가격 필터 이중 네비게이션 버그 수정
- 검색 실패 시 사용자에게 폴백 상태 알림 메시지 추가
- `Suspense` 컴포넌트에 `LoadingSpinner` fallback 연결

### Out of Scope
- 새로운 페이지 추가
- 검색 로직 변경
- 상품 상세 페이지 수정

## Acceptance Criteria
- [ ] 가격 필터 클릭 시 네비게이션이 1회만 발생한다
- [ ] 가격 필터 클릭 시 minPrice와 maxPrice가 모두 정상 적용된다
- [ ] search-service 실패 시 사용자에게 "검색을 사용할 수 없어 전체 상품을 표시합니다" 등의 안내 메시지가 표시된다
- [ ] 페이지 로딩 중 LoadingSpinner가 표시된다

## Related Specs
- `specs/services/web-store/architecture.md`
- `specs/contracts/http/search-api.md`

## Related Contracts
- `specs/contracts/http/search-api.md`

## Target App
- `apps/web-store`

## Edge Cases
- 가격 필터와 카테고리 필터를 동시에 적용한 경우 파라미터 유지 확인
- 검색 실패 후 다시 검색 시도 시 정상 동작

## Failure Scenarios
- search-service 장애가 지속될 경우 반복적인 에러 메시지 방지
