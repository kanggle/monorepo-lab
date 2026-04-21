# Task ID

TASK-BE-022

# Title

search-service 상품 검색 API — GET /api/search/products (키워드, 필터, 정렬, 페이지네이션, 팩싯)

# Status

ready

# Owner

backend

# Task Tags

- code
- api

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

If any section is missing or incomplete, this task must not be implemented.

---

# Goal

사용자가 키워드와 필터로 상품을 검색할 수 있는 API를 구현한다. Elasticsearch를 통해 관련도 기반 검색, 필터, 정렬, 팩싯 집계를 제공한다.

이 태스크 완료 후: 클라이언트가 `GET /api/search/products`로 상품을 검색하고, 카테고리·가격 범위 팩싯 정보를 함께 받을 수 있다.

---

# Scope

## In Scope

- `GET /api/search/products` — 상품 검색 API
  - 파라미터: `q` (필수), `categoryId`, `minPrice`, `maxPrice`, `status`, `sort`, `page`, `size`
  - 응답: `content`, `facets`, `page`, `size`, `totalElements`, `query`
- Application service: `SearchProductService` — 검색 유스케이스
- Outbound adapter: `ElasticsearchQueryAdapter` — `SearchQueryPort` 구현체
- 팩싯 집계: categories (term aggregation), priceRanges (range aggregation)
- 정렬: `RELEVANCE` (기본), `PRICE_ASC`, `PRICE_DESC`, `NEWEST`
- 기본 status 필터: `ON_SALE`

## Out of Scope

- 자동완성(autocomplete) API
- 검색어 교정(typo correction)
- 개인화 랭킹
- 관리자 검색 설정 API
- 검색 로그/분석

---

# Acceptance Criteria

- [ ] `GET /api/search/products?q=키워드` 호출 시 200과 계약에 정의된 응답 구조 반환
- [ ] `q` 파라미터 누락 또는 빈 값 시 400 + `{ "code": "INVALID_SEARCH_REQUEST" }` 반환
- [ ] `categoryId` 필터 적용 시 해당 카테고리 상품만 반환된다
- [ ] `minPrice` / `maxPrice` 필터가 정상 동작한다
- [ ] `status` 파라미터 미전달 시 기본값 `ON_SALE` 적용된다
- [ ] `sort=price_asc` / `price_desc` / `newest` / `relevance` 정렬이 동작한다
- [ ] `size` 최대값은 100이며, 초과 요청 시 100으로 제한된다
- [ ] 응답 `facets.categories`에 카테고리별 상품 수가 포함된다
- [ ] 응답 `facets.priceRanges`에 가격대별 상품 수가 포함된다
- [ ] `SearchProductService`가 application 레이어에 위치하고 `SearchQueryPort`를 통해서만 Elasticsearch에 접근한다
- [ ] `ElasticsearchQueryAdapter`가 outbound adapter 레이어에 위치한다
- [ ] 단위 테스트 및 통합 테스트가 추가된다
- [ ] 기존 모든 테스트가 통과한다

---

# Related Specs

- `specs/services/search-service/architecture.md`
- `specs/platform/error-handling.md`
- `specs/platform/testing-strategy.md`

# Related Skills

- `.claude/skills/search/elasticsearch-query.md`
- `.claude/skills/backend/springboot-api.md`
- `.claude/skills/backend/testing-backend.md`

---

# Related Contracts

- `specs/contracts/http/search-api.md`

---

# Target Service

- `search-service`

---

# Architecture

Follow:

- `specs/services/search-service/architecture.md`

Hexagonal Architecture 계층 배치:
- Domain: `SearchFilter`, `SearchSort`, `FacetResult`, `SearchQueryPort` (TASK-BE-020에서 정의됨)
- Application: `SearchProductService`, `SearchProductQuery`, `SearchProductResult`
- Inbound Adapters: `SearchController`, 요청/응답 DTO
- Outbound Adapters: `ElasticsearchQueryAdapter`
- Exception Handler: `GlobalExceptionHandler` (400, 500 처리)

---

# Implementation Notes

### 검색 쿼리 전략

- keyword → Elasticsearch `multi_match` (name, description 필드)
- categoryId → `term` filter
- minPrice/maxPrice → `range` filter
- status → `term` filter
- sort: relevance → `_score` desc, price_asc → `price` asc, price_desc → `price` desc, newest → `_id` desc (또는 createdAt 필드 추가 시)

### 팩싯 집계

```json
{
  "aggs": {
    "categories": { "terms": { "field": "categoryId", "size": 20 } },
    "price_ranges": {
      "range": {
        "field": "price",
        "ranges": [
          { "to": 10000 },
          { "from": 10000, "to": 50000 },
          { "from": 50000, "to": 100000 },
          { "from": 100000 }
        ]
      }
    }
  }
}
```

### size 제한

size 파라미터가 100 초과 시 100으로 cap 처리. `SearchFilter`에서 강제.

---

# Edge Cases

- 검색 결과가 0건 → 200 반환, content 빈 배열, totalElements=0
- keyword에 특수문자 포함 → Elasticsearch가 쿼리 파싱 후 처리 (예외 발생 시 400 반환)
- minPrice > maxPrice → `SearchFilter` 생성 시 도메인 레이어에서 예외 → 400 반환
- Elasticsearch 장애 시 → 500 반환
- size=0 요청 → 400 반환

---

# Failure Scenarios

- Elasticsearch 클러스터 다운 → 500 반환
- 인덱스 없음 → Elasticsearch가 404 반환 → 500으로 매핑 (인덱스는 서비스 기동 시 생성되어야 함)
- 집계 결과 없음 → facets에 빈 배열 반환 (정상 응답)

---

# Test Requirements

- 단위 테스트: `SearchProductServiceTest` — 검색 성공, 빈 결과, 필터 조합, SearchQueryPort 호출 검증
- 단위 테스트: `SearchFilterTest` — 유효성 검증 (TASK-BE-020에 있으면 확장)
- 컨트롤러 슬라이스: `SearchControllerTest` — q 누락 400, 정상 200, 응답 구조 검증
- 통합 테스트: `SearchQueryIntegrationTest` — Testcontainers ES에 데이터 색인 후 검색 결과 및 팩싯 검증

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
