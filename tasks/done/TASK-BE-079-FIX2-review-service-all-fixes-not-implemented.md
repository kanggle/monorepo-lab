# Task ID

TASK-BE-079-FIX2-review-service-all-fixes-not-implemented

# Title

review-service / order-service TASK-BE-079-FIX 미구현 항목 전체 재수정

# Status

ready

# Owner

backend

# Task Tags

- code
- bugfix

---

# Goal

TASK-BE-079-FIX에서 수정해야 했던 4가지 결함이 모두 미구현 상태로 확인되었다.
아래 항목을 전부 구현하고 테스트를 추가한다.

---

# Scope

## In Scope

1. `OrderServiceClient.hasUserPurchasedProduct()` N+1 HTTP 호출 제거 — `/api/orders/verify-purchase?productId=` 단일 호출로 교체
2. `OrderController`에 `GET /api/orders/verify-purchase` 엔드포인트 추가 (VerifyPurchaseResponse 및 OrderQueryService.hasUserPurchasedProduct는 이미 구현되어 있음)
3. review-service DB UNIQUE 제약 소프트 삭제 불일치 수정 — V2 Flyway 마이그레이션 파일 추가
4. `ReviewCommandService` 이벤트 발행 실패 처리 — 예외 재던지기 또는 @TransactionalEventListener 적용
5. `ReviewRepositoryAdapter.findByUserId()` — productName null 반환 해결 (리뷰 저장 시 productName 컬럼 저장 또는 허용 가능한 대안을 계약에 명시)
6. 각 수정 사항에 대한 단위/통합 테스트 추가

## Out of Scope

- 신규 기능 추가
- 아키텍처 변경

---

# Acceptance Criteria

- [ ] `OrderServiceClient.hasUserPurchasedProduct()`가 `/api/orders/verify-purchase?productId={productId}` 단일 HTTP 요청으로 구매 확인
- [ ] `OrderController`에 `GET /api/orders/verify-purchase` 엔드포인트가 존재하고 계약(`specs/contracts/http/order-api.md`)과 일치
- [ ] review-service에 V2 Flyway 마이그레이션 파일이 존재하고, UNIQUE 제약이 소프트 삭제와 호환됨 (partial index 또는 status 포함 복합 unique)
- [ ] 소프트 삭제된 리뷰가 있는 상품을 재구매한 사용자가 해당 상품에 리뷰를 다시 작성할 수 있음
- [ ] 이벤트 발행 실패 시 트랜잭션 롤백 또는 명확한 복구 전략이 적용됨 (try-catch 삼키기 제거)
- [ ] `GET /api/reviews/me` 응답의 `productName` 필드가 계약(`specs/contracts/http/review-api.md`)에 명시된 대로 null이 아닌 값으로 반환되거나, 계약이 nullable을 명시하도록 먼저 업데이트됨
- [ ] `OrderQueryServiceTest`에 `hasUserPurchasedProduct` 단위 테스트 추가
- [ ] `OrderControllerTest` 또는 `OrderApiContractTest`에 `GET /api/orders/verify-purchase` 테스트 추가
- [ ] review-service 통합 테스트에 소프트 삭제 후 재리뷰 시나리오 테스트 추가

---

# Related Specs

- `specs/platform/architecture-decision-rule.md`
- `specs/services/review-service/architecture.md`
- `specs/platform/coding-rules.md`

---

# Related Contracts

- `specs/contracts/http/review-api.md`
- `specs/contracts/events/review-events.md`
- `specs/contracts/http/order-api.md`

---

# Edge Cases

- 주문 목록이 빈 경우 구매 확인 결과 false 반환
- 소프트 삭제된 리뷰가 존재하는 상품의 재구매 후 리뷰 작성
- 이벤트 발행 인프라(Kafka) 장애 시 처리 흐름

---

# Failure Scenarios

- order-service 구매 확인 API 장애 시 예외 전파 (기존 동작 유지)
- Kafka 이벤트 발행 실패 시 트랜잭션 롤백 또는 재시도 전략 적용

---

# Background

## 현황 (리뷰 결과)

### Issue 1 (Critical): N+1 HTTP 호출 — 미수정

`apps/review-service/src/main/java/com/example/review/infrastructure/client/OrderServiceClient.java`

현재 코드가 여전히 주문 목록(최대 100건) 조회 후 각 주문에 대해 개별 상세 조회 HTTP 요청을 발생시키고 있다.
`/api/orders/verify-purchase?productId=` 단일 호출로 교체해야 한다.

### Issue 2 (Critical): order-service verify-purchase 엔드포인트 미구현

`apps/order-service/src/main/java/com/example/order/presentation/OrderController.java`

`OrderQueryService.hasUserPurchasedProduct()`와 `VerifyPurchaseResponse`는 존재하지만 `OrderController`에 엔드포인트가 없다.
계약(`specs/contracts/http/order-api.md`)에는 `GET /api/orders/verify-purchase`가 명시되어 있다.

### Issue 3 (Critical): V2 마이그레이션 파일 미존재 — UNIQUE 제약 미수정

`apps/review-service/src/main/resources/db/migration/` 디렉터리에 V2 파일이 없다.
V1의 `CONSTRAINT uq_reviews_user_product UNIQUE (user_id, product_id)`가 그대로이다.
소프트 삭제된 리뷰가 있으면 동일 사용자가 동일 상품을 재구매해도 리뷰를 작성할 수 없다.

### Issue 4 (Warning): 이벤트 발행 실패 처리 — 미수정

`apps/review-service/src/main/java/com/example/review/application/service/ReviewCommandService.java`

createReview, updateReview, deleteReview 모두 여전히 try-catch로 예외를 삼기고 있다.
DB 저장은 성공하고 이벤트 발행 실패 시 데이터 불일치가 발생할 수 있다.

### Issue 5 (Warning): productName null 반환 — 미수정

`apps/review-service/src/main/java/com/example/review/infrastructure/persistence/ReviewRepositoryAdapter.java` (line 116)

`null` 주석이 그대로이며 계약(`specs/contracts/http/review-api.md`)의 `productName: "string"` 타입을 위반하고 있다.

### Issue 6 (Warning): 테스트 미추가

- `OrderQueryServiceTest`에 `hasUserPurchasedProduct` 테스트 없음
- `OrderControllerTest`/`OrderApiContractTest`에 verify-purchase 엔드포인트 테스트 없음
- review-service 통합 테스트에 소프트 삭제 후 재리뷰 시나리오 테스트 없음

---

# Test Requirements

- unit test: OrderQueryService.hasUserPurchasedProduct 단위 테스트
- unit test: OrderController GET /api/orders/verify-purchase 슬라이스 테스트
- unit test: OrderApiContractTest에 verify-purchase 컨트랙트 검증 추가
- unit test: ReviewCommandService 이벤트 발행 실패 시 예외 전파 확인
- integration test: review-service DB UNIQUE 제약 — 소프트 삭제 후 재리뷰 가능 시나리오
- unit test: OrderServiceClient가 단일 HTTP 요청으로 구매 확인하는지 검증

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
