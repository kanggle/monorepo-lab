# Domain: ecommerce

> **Activated when**: `PROJECT.md` declares `domain: ecommerce`.
> **Scope**: 단일 판매자 기반 B2C/B2B 상거래. marketplace(다수 판매자)는 별도 domain.

---

## Definition and Boundaries

**In scope (이 도메인의 규칙이 적용되는 범위)**:

- 상품(Product) 카탈로그·가격·재고 관리
- 장바구니(Cart)·찜(Wishlist)
- 주문(Order) 생명주기 (생성 → 결제 → 배송 → 완료/취소/환불)
- 결제(Payment) 처리 (PG 연동 포함)
- 프로모션(Promotion)·쿠폰·포인트
- 배송(Shipping)·추적·반품
- 리뷰(Review)·평점
- 알림(Notification) — 주문 상태, 배송 상태, 마케팅
- 사용자(User) — 구매자, 회원 등급

**Out of scope (이 도메인이 다루지 않는 것)**:

- 셀러 온보딩·정산·수수료 → `marketplace` 도메인
- 상품 제조·공정 추적 → `mes`
- 기업 회계 장부 → `accounting-system`, `erp`
- 물류 창고 운영 내부 → `wms`, `logistics`
- 라스트마일 배차 → `delivery-platform`

---

## Core Bounded Contexts

이커머스 시스템의 canonical bounded context. 서비스 분할 시 이 경계를 따른다.

| Context | 주요 Aggregate | 소유 데이터 |
|---|---|---|
| **Catalog** | Product, Category, Brand | 상품 정보, 분류, 노출 상태 |
| **Inventory** | StockItem, Reservation | 재고 수량, 예약/차감 이력 |
| **Cart** | Cart, CartItem | 세션/사용자별 장바구니 |
| **Wishlist** | Wishlist, WishlistItem | 찜 목록 |
| **Order** | Order, OrderLine, OrderStatus | 주문, 라인 아이템, 상태 전이 |
| **Payment** | Payment, PaymentAttempt, Refund | 결제 승인·취소·환불 |
| **Promotion** | Coupon, CampaignRule, IssuedCoupon | 쿠폰·할인 규칙·발행 이력 |
| **Shipping** | Shipment, TrackingEvent | 배송 요청·추적 이벤트 |
| **Review** | Review, Rating | 구매 후기·평점 |
| **Notification** | NotificationRequest, DeliveryChannel | 알림 요청·채널별 전달 기록 |
| **Identity** | User, Address, Membership | 사용자·주소록·등급 |

bounded context 간 상호 참조는 **ID와 이벤트로만** 이뤄져야 하며, 직접 엔티티를 공유하지 않는다. (cross-context domain 모델 공유 금지)

> 현재 ecommerce-microservices-platform의 구현은 이 맵과 일치한다. 참고:
> [apps/product-service/](../../../apps/product-service/), [apps/order-service/](../../../apps/order-service/), [apps/payment-service/](../../../apps/payment-service/), [apps/promotion-service/](../../../apps/promotion-service/), [apps/shipping-service/](../../../apps/shipping-service/), [apps/review-service/](../../../apps/review-service/), [apps/notification-service/](../../../apps/notification-service/), [apps/user-service/](../../../apps/user-service/).

---

## Ubiquitous Language

| 용어 | 정의 |
|---|---|
| **SKU** | Stock Keeping Unit — 재고 관리 단위. 동일 상품의 사이즈/색상 조합마다 다름 |
| **Product Variant** | 상품의 옵션 조합 (예: 색상+사이즈). 하나의 SKU에 대응 |
| **Order Line** | 주문 내 한 상품(Variant/SKU)에 대한 수량·가격 스냅샷 |
| **Reservation** | 재고 차감 직전의 "임시 점유" 상태. 일정 시간 후 expire 가능 |
| **Authorization** | 결제 승인(가처리). 실제 출금은 capture에서 발생 |
| **Capture** | 결제 확정 — 판매자에게 실제 청구 |
| **Chargeback** | 카드사·구매자 요청에 의한 결제 되돌림 |
| **Fulfillment** | 주문을 배송 가능한 형태로 준비·출고하는 과정 |
| **POD** | Proof of Delivery — 배송 완료 증빙 |
| **RTO** | Return to Origin — 미수령/거부로 인한 반송 |
| **Issue vs Redeem (Coupon)** | Issue = 사용자에게 쿠폰 발급, Redeem = 주문 시 실제 적용·차감 |

이 용어는 서비스 이름·API 필드·이벤트 페이로드·테이블 칼럼·문서에서 **일관되게** 사용한다. 동일 개념을 다른 용어로 부르는 것은 금지.

---

## Mandatory Rules

이 도메인에서 반드시 준수해야 하는 규칙:

### M1. Order는 immutable state transition을 가진다
Order의 상태는 사전 정의된 상태 기계(state machine)로만 전이되며, 과거 상태는 덮어쓰지 않고 이력으로 남긴다. 직접 status 필드 update 금지 — 항상 상태 전이 명령을 통해 변경.

### M2. Inventory 차감은 Order 생성과 동일 트랜잭션 경계에서 원자적으로 처리
재고 부족 시 주문 생성 자체가 실패해야 한다. 이 규칙은 `transactional` trait의 idempotency 규칙과 결합되어 "중복 호출에도 재고가 두 번 차감되지 않음"을 보장해야 한다. 상세: [../traits/transactional.md](../traits/transactional.md).

### M3. 가격·할인은 주문 시점에 스냅샷으로 고정
Order Line은 **주문 시점의 가격**을 저장한다. Catalog의 가격이 변경돼도 기존 Order의 가격은 변하지 않아야 한다. Promotion도 마찬가지 — 주문 시 적용된 할인 규칙과 금액을 스냅샷으로 저장.

### M4. Payment와 Order는 분리된 bounded context
Payment 결과(성공/실패)는 **이벤트**로 Order 컨텍스트에 전달된다. Payment 테이블을 Order 서비스가 직접 조회하는 것은 금지 — 이벤트 기반 상태 동기화만 허용.

### M5. Refund는 별도 상태 흐름으로 취급
환불은 Payment 취소와 다른 프로세스다. Refund는 독립적인 상태 기계(requested → approved → processing → completed/failed)를 가지며 Order 상태와 별도로 추적한다.

### M6. Coupon redemption은 idempotent해야 한다
동일 주문에 같은 쿠폰이 두 번 적용되어선 안 된다. Coupon은 (userId, couponCode, orderId) unique constraint 또는 동등한 메커니즘으로 중복 사용을 차단.

### M7. Review는 구매 검증(proof of purchase) 후에만 작성 가능
주문이 `DELIVERED` 또는 `COMPLETED` 상태인 userId만 해당 상품에 리뷰를 남길 수 있다. 이 검증은 Review 서비스가 Order 이벤트 또는 API로 확인.

### M8. Notification은 비동기 경로를 기본값으로 한다
주문 성공·배송 변경 등의 알림은 **이벤트 → Notification 서비스** 경로로 처리한다. 동기 HTTP 호출로 알림 전송하는 것은 특별한 이유(동기 확인이 비즈니스적으로 필수) 없이 금지.

### M9. Cart는 주문 생성 후 **명시적으로** 클리어
자동 삭제 금지. 주문 생성 성공 이벤트에 반응해 Cart 서비스가 해당 라인을 클리어한다.

### M10. 개인 식별 정보(PII) 최소 노출
주소·전화번호·이메일은 필요한 서비스에만 전달한다. 예: Shipping 서비스는 주소가 필요하지만 Catalog 서비스는 필요 없다. 이벤트 페이로드에 PII를 무분별하게 포함하지 않는다.

---

## Standard Error Codes

이커머스 도메인의 에러 코드는 [../../platform/error-handling.md](../../platform/error-handling.md)에 등록되어 있으며, 파일 내 `[domain: ecommerce]` 태그가 붙은 섹션이 이 도메인 소유다. 이 파일은 **복사하지 않고 참조만** 한다 — platform/error-handling.md가 단일 진실 소스.

이 도메인에 속한 섹션 (platform/error-handling.md에서 직접 확인):

- `Product [domain: ecommerce]` — 상품/재고/옵션
- `Search [domain: ecommerce]` — 검색 요청 검증
- `Order [domain: ecommerce]` — 주문 상태·검증
- `Payment [domain: ecommerce]` — 결제 승인·확정·금액
- `User [domain: ecommerce]` — 사용자 프로필·주소
- `Promotion [domain: ecommerce]` — 쿠폰·캠페인
- `Notification [domain: ecommerce]` — 알림 템플릿·선호
- `Review [domain: ecommerce]` — 리뷰·구매 검증
- `Wishlist [domain: ecommerce]` — 찜 목록
- `Shipping [domain: ecommerce]` — 배송 상태·검증

**변경 프로토콜**:
- 신규 에러 코드가 필요하면 [../../platform/error-handling.md](../../platform/error-handling.md)의 해당 `[domain: ecommerce]` 섹션에 먼저 등록.
- 이 문서에는 코드 표를 복사하지 않는다.
- 새 프로젝트(다른 domain)로 이식 시 `[domain: ecommerce]` 태그가 붙은 섹션만 제거하고 그 자리에 해당 domain의 에러 코드 섹션을 추가한다. `Platform-Common` 섹션은 그대로 유지.

---

## Integration Surface

이커머스 프로젝트가 외부 시스템과 연동하는 표준 경계:

| 외부 시스템 종류 | 예시 | 내부 연동 서비스 |
|---|---|---|
| Payment Gateway | 토스페이먼츠, 아임포트, Stripe | Payment Service |
| 배송사 API | CJ대한통운, 한진, 로젠 | Shipping Service |
| 알림 채널 | SMS, Email (SES/Sendgrid), Push (FCM/APNs), Kakao | Notification Service |
| 소셜 로그인 | Google, Kakao, Naver, Apple | Auth Service |
| 검색 엔진 | Elasticsearch, OpenSearch, Algolia | Search Service |
| CDN | CloudFront, Cloudflare | Frontend, Product Images |

이 통합이 많다는 것 자체가 `integration-heavy` trait의 근거다. 상세 규칙: [../traits/integration-heavy.md](../traits/integration-heavy.md).

---

## Checklist (Review Gate)

이커머스 도메인의 구현·리뷰 시 아래 항목을 확인한다.

- [ ] Bounded context 경계가 이 문서의 표와 일치하는가? 교차 참조 시 ID/이벤트만 사용하는가?
- [ ] Ubiquitous Language를 API 필드명·이벤트명·DB 칼럼에 일관되게 썼는가?
- [ ] Order 상태 전이가 상태 기계로 모델링되어 있고 직접 update가 없는가? (M1)
- [ ] Inventory 차감이 Order 생성과 원자적이고 idempotent한가? (M2 + transactional trait)
- [ ] Order Line이 가격·할인 스냅샷을 저장하는가? (M3)
- [ ] Order ↔ Payment가 이벤트 기반이며 직접 DB 조회가 없는가? (M4)
- [ ] Refund가 Payment 취소와 별도 상태 흐름을 가지는가? (M5)
- [ ] Coupon redemption이 idempotent한가? (M6)
- [ ] Review 생성이 구매 검증을 거치는가? (M7)
- [ ] Notification이 동기 호출이 아닌 이벤트 기반인가? (M8)
- [ ] Cart 클리어가 이벤트 기반으로 명시 처리되는가? (M9)
- [ ] PII 전파가 최소 노출 원칙을 따르는가? (M10)
- [ ] 새 에러 코드가 [../../platform/error-handling.md](../../platform/error-handling.md)에 등록되어 있는가?
