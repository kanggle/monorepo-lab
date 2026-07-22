# Use Case: 위시리스트 관리

> 인증된 사용자가 관심 상품을 위시리스트에 담고 조회·삭제·포함여부를
> 확인한다. 소유 서비스는 `user-service`, 상품 표시 정보는 조회 시점에
> `product-service` 에서 enrich 된다. 계약: [`specs/contracts/http/wishlist-api.md`](../contracts/http/wishlist-api.md).

---

## UC-1: 위시리스트에 상품 추가

### 액터

- 인증된 사용자 (Authenticated User)

### 사전조건

- 사용자가 로그인되어 있고 `user_profiles` 행이 존재함
- 추가하려는 상품의 `productId` 를 알고 있음

### 정상 흐름

1. 사용자가 상품 상세/목록에서 "위시리스트 추가"를 요청한다.
2. 클라이언트가 `POST /api/wishlists` 에 `productId` 를 담아 호출한다 (`X-User-Id` 헤더는 gateway-service 가 주입).
3. user-service 가 사용자 프로필 존재와 중복 여부를 검증한다.
4. user-service 가 `wishlist_items` 에 항목을 저장한다.
5. 시스템이 `wishlistItemId`, `productId` 를 포함한 201 응답을 반환한다.

### 대안 흐름

- 없음

### 예외 흐름

- **EF-1: 입력값 오류** — `productId` 누락/형식 오류 시 `VALIDATION_ERROR` (400).
- **EF-2: 미인증** — `X-User-Id` 헤더 부재 시 `UNAUTHORIZED` (401).
- **EF-3: 프로필 없음** — 프로필 행이 없는 사용자 요청 시 `USER_PROFILE_NOT_FOUND` (404).
- **EF-4: 이미 존재** — 동일 상품이 이미 위시리스트에 있으면 `ALREADY_IN_WISHLIST` (409). 동시 중복 삽입 백스톱은 `DATA_INTEGRITY_VIOLATION` (409).
- **EF-5: 한도 초과** — 위시리스트가 100개에 도달한 경우 `WISHLIST_LIMIT_EXCEEDED` (409).

---

## UC-2: 위시리스트 조회

### 액터

- 인증된 사용자 (Authenticated User)

### 사전조건

- 사용자가 로그인되어 있음

### 정상 흐름

1. 사용자가 위시리스트 페이지에 접근한다.
2. 클라이언트가 `GET /api/wishlists/me?page=&size=` 를 호출한다.
3. user-service 가 페이지네이션된 항목 목록을 조회한다.
4. 각 항목에 대해 product-service 를 호출하여 `productName`, `productPrice`, `productStatus` 를 enrich 한다.
5. 시스템이 `content`, `page`, `size`, `totalElements` 를 포함한 200 응답을 반환한다.

### 대안 흐름

- **AF-1: 상품 정보 조회 불가** — product-service 가 응답 불가하거나 상품이 삭제된 경우, 해당 항목은 `productStatus="DELETED"` + `productName=null` 로 반환된다 (위시리스트 조회 자체는 실패하지 않는다 — graceful degradation).

### 예외 흐름

- **EF-1: 미인증** — `X-User-Id` 헤더 부재 시 `UNAUTHORIZED` (401).

---

## UC-3: 위시리스트에서 상품 삭제

### 액터

- 인증된 사용자 (Authenticated User)

### 사전조건

- 사용자가 로그인되어 있고 삭제할 `wishlistItemId` 를 보유

### 정상 흐름

1. 사용자가 위시리스트 항목의 "삭제"를 요청한다.
2. 클라이언트가 `DELETE /api/wishlists/{wishlistItemId}` 를 호출한다.
3. user-service 가 해당 항목이 요청 사용자 소유인지 검증한다.
4. user-service 가 항목을 삭제한다.
5. 시스템이 204 응답을 반환한다.

### 대안 흐름

- 없음

### 예외 흐름

- **EF-1: 미인증** — `X-User-Id` 헤더 부재 시 `UNAUTHORIZED` (401).
- **EF-2: 타 사용자 소유** — 항목이 요청 사용자 소유가 아니면 `ACCESS_DENIED` (403) (조용한 성공 금지).
- **EF-3: 항목 없음** — 해당 ID 항목이 없으면 `WISHLIST_ITEM_NOT_FOUND` (404).

---

## UC-4: 위시리스트 포함 여부 확인

### 액터

- 인증된 사용자 (Authenticated User)

### 사전조건

- 사용자가 로그인되어 있음

### 정상 흐름

1. 스토어프론트가 상품 상세 진입 시 토글 상태를 렌더링하기 위해 포함 여부를 확인한다.
2. 클라이언트가 `GET /api/wishlists/me/check?productId=...` 를 호출한다.
3. user-service 가 `productId`, `inWishlist`, `wishlistItemId` 를 반환한다 (`wishlistItemId` 는 `inWishlist=true` 일 때만 채워짐).

### 대안 흐름

- 없음

### 예외 흐름

- **EF-1: 입력값 오류** — `productId` 파라미터 누락 시 `VALIDATION_ERROR` (400).
- **EF-2: 미인증** — `X-User-Id` 헤더 부재 시 `UNAUTHORIZED` (401).

---

## Related Contracts
- HTTP: `specs/contracts/http/wishlist-api.md`
- Events: 없음 (위시리스트 변경은 v1 에서 이벤트 발행 없음)
