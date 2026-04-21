# TASK-FE-003: web-store 상품 목록 및 검색 페이지 구현

## Goal
web-store의 첫 번째 사용자 기능으로 상품 목록 조회와 검색 페이지를 구현한다.
search-service API를 통한 키워드 검색, 필터링, 페이지네이션을 포함한다.

## Scope
- 메인 페이지: 상품 목록 표시 (SSR)
- 검색 기능: 키워드 검색, 카테고리 필터, 가격 범위 필터
- 상품 카드 컴포넌트: 이미지, 이름, 가격, 재고 상태 표시
- 페이지네이션 또는 무한 스크롤
- 상품 상세 페이지: 개별 상품 정보 표시 (SSR)
- FSD 구조에 맞게 entities/product, features/search, widgets/product-list, pages/ 구성

## Acceptance Criteria
- 메인 페이지에서 상품 목록이 서버 사이드 렌더링으로 표시된다
- 검색 입력 시 search-service API가 호출되고 결과가 표시된다
- 카테고리, 가격 필터가 동작한다
- 페이지네이션이 동작한다
- 상품 클릭 시 상세 페이지로 이동한다
- 상품 상세 페이지에서 product-service API로 개별 상품 정보를 표시한다
- 로딩 상태와 에러 상태가 적절히 표시된다

## Related Specs
- `specs/services/web-store/architecture.md`
- `specs/contracts/http/product-api.md`
- `specs/contracts/http/search-api.md`

## Related Contracts
- `specs/contracts/http/product-api.md`
- `specs/contracts/http/search-api.md`

## Edge Cases
- 검색 결과가 0건일 때 빈 상태 메시지 표시
- 상품이 품절(재고 0)인 경우 시각적 표시
- 네트워크 오류 시 재시도 또는 에러 메시지 표시
- 검색어에 특수문자가 포함된 경우 적절히 이스케이프

## Failure Scenarios
- search-service 장애 시 product-service 직접 조회로 폴백 (검색 없이 목록만)
- SSR 렌더링 실패 시 클라이언트 사이드 폴백
