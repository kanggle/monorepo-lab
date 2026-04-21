# TASK-FE-007: web-store 컴포넌트 및 페이지 테스트 추가

## Goal
TASK-FE-003 리뷰에서 발견된 테스트 부재 이슈를 수정한다.
web-store 앱의 주요 컴포넌트와 페이지에 대한 테스트를 추가한다.

## Scope
- 테스트 환경 설정: Vitest + React Testing Library 구성
- 컴포넌트 단위 테스트:
  - `ProductCard`: 상품 정보 표시, 품절 상태 표시, 링크 동작
  - `ProductList`: 상품 목록 렌더링, 빈 상태 처리
  - `SearchBar`: 검색어 입력 및 폼 제출
  - `SearchFilters`: 카테고리/가격 필터 동작
  - `SearchResults`: 검색 결과 표시, 빈 결과 처리
  - `Pagination`: 페이지 링크 생성, 현재 페이지 표시
  - `ErrorMessage`: 에러 메시지 표시
  - `EmptyState`: 빈 상태 메시지 표시
- ProductDetail 이미지 경로 하드코딩 문제 해결 (선택적)

## Acceptance Criteria
- Vitest + React Testing Library 테스트 환경이 구성된다
- 주요 UI 컴포넌트에 대한 단위 테스트가 존재하고 통과한다
- ProductCard 품절 상태, SearchResults 빈 결과 등 엣지 케이스가 테스트된다
- `pnpm test` 또는 `pnpm vitest`로 테스트 실행이 가능하다
- 기존 빌드가 깨지지 않는다

## Related Specs
- `specs/platform/testing-strategy.md`
- `specs/services/web-store/architecture.md`

## Related Contracts
- `specs/contracts/http/product-api.md`
- `specs/contracts/http/search-api.md`

## Edge Cases
- 상품 데이터가 없는 경우 빈 상태 렌더링
- 품절 상품의 시각적 표시 검증
- 검색 결과 0건 시 빈 상태 메시지
- 특수문자 포함 검색어 처리

## Failure Scenarios
- API 호출 실패 시 에러 메시지 컴포넌트 렌더링
- 잘못된 props 전달 시 컴포넌트 방어적 렌더링
