# Use Case: 상품 검색 및 조회

---

## UC-1: 상품 목록 조회

### 액터

- 사용자 (User) — 인증 불필요

### 사전조건

- 없음

### 정상 흐름

1. 사용자가 상품 목록 페이지에 접근한다.
2. 클라이언트가 GET /api/products 요청을 보낸다.
3. product-service가 필터 조건(name, categoryId, status)에 따라 상품을 조회한다.
4. 페이지네이션된 상품 목록을 반환한다 (productId, name, price, status, thumbnailUrl 등).

### 대안 흐름

- **AF-1: 필터 적용** — 사용자가 상품명, 카테고리, 상태(ON_SALE, SOLD_OUT, HIDDEN)로 필터링할 수 있다.
- **AF-2: 결과 없음** — 조건에 맞는 상품이 없으면 빈 목록을 반환한다.

### 예외 흐름

- **EF-1: 잘못된 요청** — 유효하지 않은 파라미터 시 400 오류를 반환한다.

---

## UC-2: 상품 상세 조회

### 액터

- 사용자 (User) — 인증 불필요

### 사전조건

- 해당 상품이 시스템에 존재함

### 정상 흐름

1. 사용자가 상품 목록에서 특정 상품을 선택한다.
2. 클라이언트가 GET /api/products/{productId} 요청을 보낸다.
3. product-service가 상품 상세 정보와 variants 목록을 반환한다.

### 대안 흐름

- 없음

### 예외 흐름

- **EF-1: 상품 미존재** — productId에 해당하는 상품이 없으면 `PRODUCT_NOT_FOUND` 오류를 반환한다 (404).

---

## UC-3: 상품 전문 검색

### 액터

- 사용자 (User) — 인증 불필요

### 사전조건

- search-service의 Elasticsearch 인덱스가 product-service와 동기화되어 있음 (최종 일관성)

### 정상 흐름

1. 사용자가 검색어를 입력한다.
2. 클라이언트가 GET /api/search/products?q={keyword} 요청을 보낸다.
3. search-service가 Elasticsearch에서 키워드 매칭 검색을 수행한다.
4. 검색 결과를 반환한다:
   - content: 상품 목록 (productId, name, price, status, thumbnailUrl, categoryId, score)
   - facets: 카테고리 집계, 가격 범위 집계
   - pagination 정보

### 대안 흐름

- **AF-1: 필터 적용** — categoryId, minPrice, maxPrice, status 필터를 조합하여 검색 범위를 좁힐 수 있다. status 기본값은 ON_SALE이다.
- **AF-2: 정렬 변경** — relevance(기본), price_asc, price_desc, newest 중 선택하여 정렬할 수 있다.
- **AF-3: 페이지네이션** — 페이지당 최대 100건까지 조회할 수 있다.
- **AF-4: 결과 없음** — 조건에 맞는 상품이 없으면 빈 content 목록과 facets를 반환한다.

### 예외 흐름

- **EF-1: 검색어 누락** — q 파라미터가 없으면 `INVALID_SEARCH_REQUEST` 오류를 반환한다 (400).
- **EF-2: 인덱스 동기화 지연** — product-service와 search-service 간 최종 일관성 특성상 최신 데이터가 즉시 반영되지 않을 수 있다. batch-worker가 주기적으로 정합성을 검증한다.

---

## UC-4: 검색 인덱스 동기화

### 액터

- 시스템 (product-service → search-service)

### 사전조건

- product-service가 상품 변경 이벤트를 발행함

### 정상 흐름

1. product-service가 상품 생성/수정/삭제/재고 변경 시 이벤트를 발행한다:
   - `ProductCreated`, `ProductUpdated`, `ProductDeleted`, `StockChanged`
2. search-service가 이벤트를 수신하여 Elasticsearch 인덱스를 갱신한다.

### 대안 흐름

- **AF-1: 일괄 정합성 검증** — batch-worker가 주기적으로 product-service와 search-service 간 데이터 정합성을 검증하고 불일치를 보정한다.

### 예외 흐름

- **EF-1: 이벤트 유실** — 이벤트 처리 실패 시 재시도 메커니즘을 통해 최종 일관성을 보장한다.

---

## Related Contracts
- HTTP: `specs/contracts/http/product-api.md`, `specs/contracts/http/search-api.md`
- Events: `specs/contracts/events/product-events.md`
