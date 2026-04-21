# Task ID

TASK-BE-020

# Title

search-service 부트스트랩 — 프로젝트 구조, Elasticsearch 설정, 도메인 모델

# Status

ready

# Owner

backend

# Task Tags

- code

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

search-service의 기반 구조를 구축한다. 프로젝트 골격, Elasticsearch 연동 설정, 핵심 도메인 모델을 완성하여 이후 이벤트 소비 및 검색 API 태스크들이 이 위에서 시작할 수 있도록 한다.

이 태스크 완료 후: search-service가 기동되고, Elasticsearch 연결이 확인되며, 검색 인덱스 도메인 모델이 정의된다.

---

# Scope

## In Scope

- Gradle 멀티모듈 설정 확인 및 의존성 추가 (apps/search-service)
- application.yml 기본 설정 (Elasticsearch, Kafka placeholder)
- 도메인 모델 구현:
  - `SearchDocument` — 검색 인덱스 문서 도메인 모델
  - `SearchFilter` — 검색 필터 값 객체 (keyword, categoryId, minPrice, maxPrice, status)
  - `SearchSort` — 정렬 값 객체 (`RELEVANCE`, `PRICE_ASC`, `PRICE_DESC`, `NEWEST`)
  - `FacetResult` — 팩싯 집계 결과 값 객체
  - `ProductStatus` — 상품 상태 열거형 (`ON_SALE`, `SOLD_OUT`, `HIDDEN`)
- Outbound port 인터페이스:
  - `SearchIndexPort` — 인덱스 upsert/delete 포트
  - `SearchQueryPort` — 검색 쿼리 포트
- 기본 헬스체크 (`GET /actuator/health`)
- Elasticsearch 인덱스 초기화 설정 (인덱스 매핑 정의)

## Out of Scope

- 이벤트 소비 구현 (TASK-BE-021)
- 검색 API 구현 (TASK-BE-022)
- Elasticsearch 아웃바운드 어댑터 실구현 (TASK-BE-021, 022에서)
- 검색 랭킹 튜닝

---

# Acceptance Criteria

- [ ] `apps/search-service` 모듈이 빌드된다
- [ ] Elasticsearch 연결 설정이 완료되고 헬스체크에서 확인된다
- [ ] `SearchDocument` 도메인 모델이 정의된다 (`productId`, `name`, `description`, `price`, `status`, `categoryId`, `stock`, `score`)
- [ ] `SearchFilter` 값 객체가 정의된다 (keyword 필수, 나머지 선택)
- [ ] `SearchSort` 열거형이 정의된다 (`RELEVANCE`, `PRICE_ASC`, `PRICE_DESC`, `NEWEST`)
- [ ] `FacetResult` 값 객체가 정의된다 (categories, priceRanges)
- [ ] `SearchIndexPort` 인터페이스가 도메인 레이어에 위치한다
- [ ] `SearchQueryPort` 인터페이스가 도메인 레이어에 위치한다
- [ ] 서비스가 기동되고 `/actuator/health`가 200을 반환한다
- [ ] 단위 테스트: `SearchFilterTest`, `SearchDocumentTest` — 도메인 모델 유효성 검증

---

# Related Specs

- `specs/services/search-service/architecture.md`
- `specs/services/search-service/overview.md`
- `specs/services/search-service/dependencies.md`
- `specs/platform/coding-rules.md`
- `specs/platform/naming-conventions.md`
- `specs/platform/testing-strategy.md`

# Related Skills

- `.claude/skills/backend/springboot-api.md`
- `.claude/skills/backend/testing-backend.md`
- `.claude/skills/backend/implementation-workflow.md`
- `.claude/skills/search/elasticsearch-index.md`

---

# Related Contracts

- `specs/contracts/http/search-api.md`
- `specs/contracts/events/product-events.md`

---

# Target Service

- `search-service`

---

# Architecture

Follow:

- `specs/services/search-service/architecture.md`

Hexagonal Architecture (Ports and Adapters):

계층 배치:
- Domain: `SearchDocument`, `SearchFilter`, `SearchSort`, `FacetResult`, `ProductStatus`, `SearchIndexPort`, `SearchQueryPort`
- Application: (TASK-BE-021, 022부터)
- Outbound Adapters: Elasticsearch 어댑터 (TASK-BE-021, 022에서)
- Inbound Adapters: HTTP 핸들러, 이벤트 컨슈머 (TASK-BE-021, 022에서)

---

# Implementation Notes

### 인덱스 문서 구조

```java
public class SearchDocument {
    private String productId;  // Elasticsearch document ID
    private String name;
    private String description;
    private long price;
    private String status;     // ON_SALE, SOLD_OUT, HIDDEN
    private String categoryId;
    private int totalStock;    // 전체 variant stock 합계
}
```

### Elasticsearch 인덱스 매핑

```json
{
  "mappings": {
    "properties": {
      "productId": { "type": "keyword" },
      "name": { "type": "text", "analyzer": "standard" },
      "description": { "type": "text", "analyzer": "standard" },
      "price": { "type": "long" },
      "status": { "type": "keyword" },
      "categoryId": { "type": "keyword" },
      "totalStock": { "type": "integer" }
    }
  }
}
```

### SearchFilter 유효성

- keyword는 null/blank 불가 (최소 1자)
- minPrice, maxPrice는 0 이상
- minPrice > maxPrice이면 예외

---

# Edge Cases

- Elasticsearch 연결 실패 시 → 서비스 기동 차단 (헬스체크 DOWN)
- keyword가 blank인 SearchFilter 생성 시 → 도메인 레이어에서 예외
- minPrice > maxPrice → 도메인 레이어에서 예외
- 인덱스가 존재하지 않을 때 → 서비스 기동 시 자동 생성

---

# Failure Scenarios

- Elasticsearch 클러스터 다운 → 헬스체크 DOWN, 서비스 요청 실패
- 인덱스 매핑 변경 충돌 → 서비스 기동 실패 (명시적 에러)
- JVM OOM (대용량 집계) → Elasticsearch 서버사이드에서 처리

---

# Test Requirements

- 단위 테스트: `SearchFilterTest` — keyword 필수, minPrice/maxPrice 유효성, 정상 생성
- 단위 테스트: `SearchDocumentTest` — 도메인 모델 생성 및 필드 검증
- 통합 테스트: `ElasticsearchConnectionTest` — Testcontainers ES로 연결 및 인덱스 생성 확인

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
