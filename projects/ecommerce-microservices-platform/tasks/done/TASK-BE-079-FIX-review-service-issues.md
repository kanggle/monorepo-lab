# Task ID

TASK-BE-079-FIX-review-service-issues

# Title

review-service 리뷰 시스템 결함 수정 — N+1 HTTP 호출, UNIQUE 제약 불일치, 이벤트 유실, productName null 반환

# Status

done

# Owner

backend

# Task Tags

- code
- bugfix

---

# Goal

TASK-BE-079 리뷰에서 발견된 Critical/Warning 수준의 결함을 수정한다.

---

# Scope

## In Scope

- `OrderServiceClient.hasUserPurchasedProduct()` N+1 HTTP 호출 제거
- DB UNIQUE 제약 조건과 소프트 삭제 로직 불일치 수정 (재구매 후 재리뷰 불가 문제)
- 이벤트 발행 실패 시 데이터 불일치 방지 (Outbox 패턴 또는 트랜잭션 내 처리)
- `GET /api/reviews/me` 응답의 `productName` null 처리 — 계약 준수

## Out of Scope

- 신규 기능 추가
- 아키텍처 변경

---

# Acceptance Criteria

- [ ] `hasUserPurchasedProduct()`가 단일 HTTP 요청으로 구매 확인 가능
- [ ] 소프트 삭제된 리뷰가 있는 상품을 재구매한 사용자가 해당 상품에 리뷰를 다시 작성할 수 있음
- [ ] 이벤트 발행이 DB 저장 트랜잭션과 함께 원자적으로 처리되거나, 실패 시 명확한 복구 전략이 적용됨
- [ ] `GET /api/reviews/me` 응답의 `productName` 필드가 계약(specs/contracts/http/review-api.md)에 명시된 대로 제공됨

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

## Issue 1 (Critical): N+1 HTTP 호출

`OrderServiceClient.hasUserPurchasedProduct()`는 주문 목록(최대 100건)을 가져온 뒤 각 주문에 대해 개별 상세 조회 HTTP 요청을 발생시킨다. 최악의 경우 1 + 100번의 HTTP 요청이 발생한다.

수정 방향: order-service의 구매 확인 전용 API를 호출하도록 변경한다. order-service에 `/api/orders/verify-purchase?productId={productId}` 형태의 엔드포인트가 없다면 `specs/contracts/http/order-api.md`에 먼저 추가하고, 해당 엔드포인트 구현 후 review-service 클라이언트를 교체한다.

관련 파일: `apps/review-service/src/main/java/com/example/review/infrastructure/client/OrderServiceClient.java`

## Issue 2 (Critical): UNIQUE 제약 vs 소프트 삭제 불일치

`V1__create_reviews_table.sql`의 `CONSTRAINT uq_reviews_user_product UNIQUE (user_id, product_id)` 제약은 status 컬럼을 포함하지 않는다. 따라서 소프트 삭제된 리뷰가 존재하면 동일 사용자가 동일 상품을 재구매해도 리뷰를 작성할 수 없다.

또한 `existsByUserIdAndProductId()`는 ACTIVE 상태만 확인하므로 중복 체크는 통과하지만 DB INSERT 시 UNIQUE 제약 위반이 발생한다.

수정 방향:
- UNIQUE 제약을 `(user_id, product_id, status)` 또는 `(user_id, product_id) WHERE status = 'ACTIVE'` (부분 인덱스)로 변경하는 마이그레이션 파일 추가
- Flyway V2 마이그레이션 파일로 추가

관련 파일: `apps/review-service/src/main/resources/db/migration/V1__create_reviews_table.sql`

## Issue 3 (Warning): 이벤트 발행 실패 시 데이터 불일치

`ReviewCommandService`에서 DB 저장 후 Kafka 이벤트 발행 실패를 `try-catch`로 삼켜 경고 로그만 남긴다. DB는 저장되었으나 search-service, product-service에 평점 변경이 전파되지 않아 데이터 불일치가 발생할 수 있다.

수정 방향: `@TransactionalEventListener` 또는 Outbox 패턴을 적용하여 이벤트 발행을 트랜잭션과 결합한다. 최소 수정으로는 이벤트 발행 실패 시 예외를 다시 던져 트랜잭션 롤백을 유발한다.

관련 파일: `apps/review-service/src/main/java/com/example/review/application/service/ReviewCommandService.java`

## Issue 4 (Warning): `productName` null 반환 — 계약 불일치

`GET /api/reviews/me` 응답에서 `productName`은 계약상 `"string"` 타입이다. 현재 항상 null을 반환하고 있어 계약을 위반한다.

수정 방향: product-service HTTP 호출을 통해 상품명을 조회하거나, 리뷰 작성 시 상품명을 별도 컬럼에 저장(denormalization)하는 방식으로 처리한다. 계약 변경이 필요한 경우 `specs/contracts/http/review-api.md`를 먼저 업데이트한다.

관련 파일:
- `apps/review-service/src/main/java/com/example/review/infrastructure/persistence/ReviewRepositoryAdapter.java` (line 116)
- `specs/contracts/http/review-api.md`

---

# Test Requirements

- unit test: 각 수정 사항에 대한 단위 테스트 추가/수정
- integration test: DB UNIQUE 제약 변경에 대한 통합 테스트 추가

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
