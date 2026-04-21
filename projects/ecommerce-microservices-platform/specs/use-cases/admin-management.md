# Use Case: 관리자 상품/주문 관리

---

## UC-1: 상품 등록

### 액터

- 관리자 (Admin)

### 사전조건

- 관리자가 인증되어 있음

### 정상 흐름

1. 관리자가 상품 등록 페이지에 접근한다.
2. 관리자가 상품 정보(name, description, price, categoryId, variants)를 입력한다.
3. 클라이언트가 POST /api/admin/products 요청을 보낸다.
4. product-service가 입력값을 검증한다.
5. 상품을 생성하고 productId를 발급한다.
6. product-service가 `ProductCreated` 이벤트를 발행한다 (productId, name, description, price, status, categoryId, variants).
7. search-service가 이벤트를 수신하여 검색 인덱스에 반영한다.
8. 시스템이 productId를 포함한 201 응답을 반환한다.

### 대안 흐름

- 없음

### 예외 흐름

- **EF-1: 입력값 오류** — 필수 필드 누락 또는 형식 오류 시 400 오류를 반환한다.
- **EF-2: 권한 없음** — 관리자 권한이 없으면 `ACCESS_DENIED` 오류를 반환한다 (403).

---

## UC-2: 상품 수정

### 액터

- 관리자 (Admin)

### 사전조건

- 관리자가 인증되어 있음
- 해당 상품이 존재함

### 정상 흐름

1. 관리자가 상품 상세에서 수정할 정보를 변경한다.
2. 클라이언트가 PATCH /api/admin/products/{productId} 요청을 보낸다 (부분 업데이트).
3. product-service가 낙관적 잠금(optimistic locking)으로 동시 수정을 방지한다.
4. 상품 정보를 업데이트한다.
5. product-service가 `ProductUpdated` 이벤트를 발행한다.
6. search-service가 이벤트를 수신하여 검색 인덱스를 갱신한다.
7. 시스템이 수정된 상품 정보를 포함한 200 응답을 반환한다.

### 대안 흐름

- 없음

### 예외 흐름

- **EF-1: 상품 미존재** — productId에 해당하는 상품이 없으면 404 오류를 반환한다.
- **EF-2: 낙관적 잠금 충돌** — 다른 관리자가 동시에 수정한 경우 409 충돌 오류를 반환한다.
- **EF-3: 권한 없음** — 관리자 권한이 없으면 `ACCESS_DENIED` 오류를 반환한다 (403).

---

## UC-3: 재고 조정

### 액터

- 관리자 (Admin)

### 사전조건

- 관리자가 인증되어 있음
- 해당 상품과 variant가 존재함

### 정상 흐름

1. 관리자가 재고 조정 요청을 입력한다 (variantId, quantity, reason).
2. 클라이언트가 PATCH /api/admin/products/{productId}/stock 요청을 보낸다.
3. product-service가 재고를 조정한다.
   - reason: RESTOCK (입고), ADMIN_ADJUSTMENT (관리자 수동 조정)
4. product-service가 `StockChanged` 이벤트를 발행한다 (productId, variantId, previousStock, currentStock, delta, reason).
5. search-service와 order-service가 이벤트를 수신한다.
6. 시스템이 200 응답을 반환한다.

### 대안 흐름

- 없음

### 예외 흐름

- **EF-1: 잘못된 요청** — 유효하지 않은 variantId 또는 quantity 시 400 오류를 반환한다.
- **EF-2: 권한 없음** — 관리자 권한이 없으면 `ACCESS_DENIED` 오류를 반환한다 (403).

---

## UC-4: 사용자 목록 조회 (관리자)

### 액터

- 관리자 (Admin)

### 사전조건

- 관리자가 인증되어 있음

### 정상 흐름

1. 관리자가 사용자 관리 페이지에 접근한다.
2. 클라이언트가 GET /api/admin/users 요청을 보낸다.
3. user-service가 사용자 목록을 페이지네이션하여 반환한다.

### 대안 흐름

- **AF-1: 필터 적용** — status(ACTIVE, SUSPENDED, WITHDRAWN) 또는 email로 필터링할 수 있다.
- **AF-2: 결과 없음** — 조건에 맞는 사용자가 없으면 빈 목록을 반환한다.

### 예외 흐름

- **EF-1: 권한 없음** — 관리자 권한이 없으면 `ACCESS_DENIED` 오류를 반환한다 (403).

---

## UC-5: 사용자 상세 조회 (관리자)

### 액터

- 관리자 (Admin)

### 사전조건

- 관리자가 인증되어 있음
- 해당 사용자가 존재함

### 정상 흐름

1. 관리자가 사용자 목록에서 특정 사용자를 선택한다.
2. 클라이언트가 GET /api/admin/users/{userId} 요청을 보낸다.
3. user-service가 사용자 상세 정보를 반환한다.

### 대안 흐름

- 없음

### 예외 흐름

- **EF-1: 사용자 미존재** — userId에 해당하는 사용자가 없으면 404 오류를 반환한다.
- **EF-2: 권한 없음** — 관리자 권한이 없으면 `ACCESS_DENIED` 오류를 반환한다 (403).

---

## Related Contracts
- HTTP: `specs/contracts/http/product-api.md`, `specs/contracts/http/user-api.md`
- Events: `specs/contracts/events/product-events.md`
