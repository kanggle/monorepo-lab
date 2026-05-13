# review-service — Overview

> 1-pager: responsibilities, public surface, key invariants.

## Service identity

| Field | Value |
|---|---|
| Service name | `review-service` |
| Project | `ecommerce-microservices-platform` |
| Service Type | `rest-api` |
| Architecture Style | **DDD-style** — see [architecture.md § Architecture Style](architecture.md) |
| Stack | Java 21, Spring Boot 3.4, PostgreSQL, Kafka, `libs/java-messaging` (transactional outbox) |
| Deployable unit | `apps/review-service/` |
| Bounded Context | `Review / Rating` |
| Persistent stores | PostgreSQL (review aggregate + average rating cache) + Kafka outbox table |
| Event publication | `review.review.created`, `review.review.updated`, `review.review.deleted` |

## Responsibilities

- Review CRUD with purchase verification — synchronous HTTP to `order-service` (`GET /api/orders?userId=&productId=&status=DELIVERED`) before allowing review create.
- Enforce one-review-per-user-per-product (unique constraint + domain check).
- Maintain average rating per product (write-through cache or materialized view).
- Soft-delete reviews (`status → DELETED`); 데이터 보존 (분쟁 / audit trail).
- Publish `ReviewCreated` / `ReviewUpdated` / `ReviewDeleted` events (consumed by `search-service` for index rating sync).

## Public surface

| Channel | Endpoint / Topic | Auth | Purpose |
|---|---|---|---|
| REST | `GET /api/reviews/products/{productId}` | none (public) | review list per product |
| REST | `POST /api/reviews` | JWT | create review (purchase 검증 후) |
| REST | `PUT /api/reviews/{id}` | JWT (owner) | update review |
| REST | `DELETE /api/reviews/{id}` | JWT (owner / ROLE_ADMIN) | soft-delete review |
| REST | `GET /api/users/{userId}/reviews` | JWT (owner / ROLE_ADMIN) | user review history |
| Kafka publish | `review.review.{created,updated,deleted}` | — | search-service rating sync |

자세한 spec 은 [`../../contracts/http/review-api.md`](../../contracts/http/review-api.md) + [`../../contracts/events/review-events.md`](../../contracts/events/review-events.md) 참조.

## Key invariants

1. **One review per (user, product)** — unique constraint + domain check. 중복 시 `ReviewAlreadyExists`.
2. **Rating ∈ [1, 5]** — domain-enforced; 범위 밖 → `IllegalRatingValue`.
3. **Purchase verification required** — `order-service` sync HTTP call 가 `DELIVERED` 주문 확인 후만 review create 허용 (rules/domains/ecommerce.md § E6).
4. **Soft-delete only** — physical delete 금지. `status = DELETED` 로 표시, audit / 분쟁 대비.
5. **No product / order ownership** — review 만 소유; product 정보 / order 상태 직접 변경 금지.

## Owned Data

- review (`reviewId`, `userId`, `productId`, `rating`, `title`, `content`, `status`, timestamps)
- average rating per product (cache / materialized view; product-service 와는 event 만 동기)

## Published Interfaces

- [`../../contracts/http/review-api.md`](../../contracts/http/review-api.md) (HTTP)
- [`../../contracts/events/review-events.md`](../../contracts/events/review-events.md) — `ReviewCreated`, `ReviewUpdated`, `ReviewDeleted`

## Dependent Systems

- PostgreSQL — review persistence
- Kafka — event publication
- `order-service` — sync HTTP for purchase verification

## Out of scope (v1)

- Product catalog — `product-service`.
- Order processing — `order-service`.
- Search indexing of reviews — `search-service` 가 event 로 자기 index 유지.
- Review moderation / NLP (스팸 자동 분류) — v2.
- 이미지 / 비디오 review attachment — v2 (object-storage policy 적용 시).
