# Demo Scenarios

이 문서는 프로젝트의 주요 비즈니스 흐름을 데모 시나리오로 정리합니다.

---

## Scenario 1: 고객 쇼핑 플로우

> 회원가입 → 상품 검색 → 장바구니 → 주문 → 결제 → 주문 확인

### 1-1. 회원가입 & 로그인

| Step | 화면 | API |
|------|------|-----|
| 회원가입 | `/signup` | `POST /api/auth/signup` |
| 로그인 | `/login` | `POST /api/auth/login` |
| OAuth 로그인 | `/oauth/callback` | OAuth 2.0 Flow |

- JWT 토큰 발급 (Access + Refresh)
- Redis 기반 세션 관리
- 동시 세션 제한

### 1-2. 상품 검색 & 조회

| Step | 화면 | API |
|------|------|-----|
| 상품 목록 | `/products` | `GET /api/products` |
| 검색/필터링 | `/products?keyword=...` | `GET /api/search/products` |
| 상품 상세 | `/products/{id}` | `GET /api/products/{id}` |

- Elasticsearch 기반 전문 검색
- 카테고리, 가격 범위 필터링
- 페이지네이션

### 1-3. 장바구니 & 주문

| Step | 화면 | API |
|------|------|-----|
| 장바구니 | `/cart` | 클라이언트 상태 관리 |
| 체크아웃 | `/checkout` | 배송지 입력 |
| 주문 생성 | `/checkout/payment` | `POST /api/orders` |

- 주문 생성 시 Outbox 패턴으로 OrderPlaced 이벤트 발행
- 주문 상태: `PENDING` → `CONFIRMED` → `SHIPPED` → `DELIVERED`

### 1-4. 결제

| Step | 화면 | API |
|------|------|-----|
| 결제 요청 | `/checkout/payment` | Toss Payments SDK |
| 결제 확인 | `/checkout/payment/success` | `POST /api/payments/confirm` |
| 결제 실패 | `/checkout/payment/fail` | 실패 화면 |
| 주문 완료 | `/checkout/complete` | 완료 화면 |

- Toss Payments 연동 (Hexagonal Architecture로 PG 격리)
- 결제 상태: `PENDING` → `COMPLETED` 또는 `FAILED`
- PaymentCompleted 이벤트 → 주문 확정

### 1-5. 주문 추적

| Step | 화면 | API |
|------|------|-----|
| 주문 목록 | `/my/orders` | `GET /api/orders` |
| 주문 상세 | `/my/orders/{id}` | `GET /api/orders/{id}` |
| 주문 취소 | 주문 상세 내 | `POST /api/orders/{id}/cancel` |

- PENDING/CONFIRMED 상태에서만 취소 가능
- 취소 시 환불 자동 처리

---

## Scenario 2: 상품 리뷰 & 위시리스트

> 구매 확인 → 리뷰 작성 → 위시리스트 관리

| Step | 화면 | API |
|------|------|-----|
| 리뷰 작성 | `/products/{id}` | `POST /api/reviews` |
| 내 리뷰 목록 | `/my/reviews` | `GET /api/reviews/my` |
| 위시리스트 추가 | `/products/{id}` | `POST /api/users/wishlist` |
| 위시리스트 조회 | `/my/wishlist` | `GET /api/users/wishlist` |

- 구매 이력 검증 후 리뷰 작성 허용 (`GET /api/orders/verify-purchase`)
- 별점 + 텍스트 리뷰

---

## Scenario 3: 프로모션 & 쿠폰

> 쿠폰 확인 → 주문 시 적용

| Step | 화면 | API |
|------|------|-----|
| 내 쿠폰 목록 | `/my/coupons` | `GET /api/coupons` |
| 쿠폰 적용 | `/checkout` | 주문 생성 시 포함 |

---

## Scenario 4: 알림

> 주문/결제/배송 이벤트 → 알림 수신

| Step | 화면 | API |
|------|------|-----|
| 알림 목록 | `/my/notifications` | `GET /api/notifications` |
| 알림 설정 | `/my/notifications/settings` | `PUT /api/notifications/preferences` |

- Event-Driven: OrderPlaced, PaymentCompleted, ShippingStatusChanged → Notification Service
- 멀티채널: Email / SMS / Push

---

## Scenario 5: 관리자 운영

> 상품 관리 → 주문 관리 → 프로모션 관리 → 알림 템플릿

### 5-1. 상품 관리

| Step | 화면 | API |
|------|------|-----|
| 상품 목록 | `/products` | `GET /api/admin/products` |
| 상품 등록 | `/products/new` | `POST /api/admin/products` |
| 상품 수정 | `/products/{id}/edit` | `PATCH /api/admin/products/{id}` |
| 재고 조정 | `/products/{id}` | `PATCH /api/admin/products/{id}/stock` |

- 상품 등록/수정 시 ProductCreated/Updated 이벤트 → Search Service 인덱스 갱신

### 5-2. 주문 관리

| Step | 화면 | API |
|------|------|-----|
| 전체 주문 | `/orders` | `GET /api/admin/orders` |
| 주문 상세 | `/orders/{id}` | `GET /api/admin/orders/{id}` |
| 상태 변경 | `/orders/{id}` | `POST /api/admin/orders/{id}/status` |

### 5-3. 프로모션 관리

| Step | 화면 | API |
|------|------|-----|
| 프로모션 목록 | `/promotions` | `GET /api/admin/promotions` |
| 프로모션 생성 | `/promotions/new` | `POST /api/admin/promotions` |
| 프로모션 수정 | `/promotions/{id}/edit` | `PATCH /api/admin/promotions/{id}` |

### 5-4. 알림 템플릿 관리

| Step | 화면 | API |
|------|------|-----|
| 템플릿 목록 | `/notifications/templates` | `GET /api/admin/notifications/templates` |
| 템플릿 생성 | `/notifications/templates/new` | `POST /api/admin/notifications/templates` |
| 템플릿 수정 | `/notifications/templates/{id}/edit` | `PATCH /api/admin/notifications/templates/{id}` |

---

## End-to-End Event Flow Summary

```
[고객]                                        [관리자]
  │                                              │
  ├─ 회원가입 ──▶ UserSignedUp ──▶ 환영 알림     │
  │                                              │
  │                          상품 등록 ◀──────────┤
  │                     ProductCreated ──▶ 검색 인덱스
  │                                              │
  ├─ 상품 검색 (Elasticsearch)                   │
  ├─ 주문 생성 ──▶ OrderPlaced                   │
  │                  ├──▶ 결제 요청               │
  │                  └──▶ 주문 접수 알림           │
  │                                              │
  ├─ 결제 완료 ──▶ PaymentCompleted              │
  │                  ├──▶ 주문 확정               │
  │                  └──▶ 결제 완료 알림           │
  │                                              │
  │              주문 상태 변경 (SHIPPED) ◀────────┤
  │           ShippingStatusChanged               │
  │                  └──▶ 배송 시작 알림           │
  │                                              │
  ├─ 상품 수령                                    │
  ├─ 리뷰 작성 ──▶ ReviewCreated                 │
  │                                              │
```
