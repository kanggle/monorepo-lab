# Domain: ecommerce

> **Activated when**: `PROJECT.md` declares `domain: ecommerce`.

---

## Scope

B2C 이커머스 플랫폼. 고객이 상품을 탐색하고 주문하고 결제하고 배송받기까지의 전 과정을 다룬다. 주문·결제·재고는 강한 일관성, 상품·리뷰·검색은 읽기 최적화가 지배한다.

이 도메인은 "고객 구매 여정의 각 단계가 신뢰 가능하게 일어나는가"에 집중한다. 관리자의 카탈로그 운영·프로모션 설정·정산 리포트도 포함되지만, 오픈마켓(셀러 온보딩·수수료·정산)의 복잡도는 `marketplace` 도메인으로 분리한다.

---

## Bounded Contexts (표준)

| Bounded Context | 책임 |
|---|---|
| **Identity** | 회원 가입·로그인·소셜 로그인·세션·OAuth 토큰 |
| **Catalog** | 상품 등록·카테고리·옵션·재고 노출·이미지/미디어 |
| **Cart** | 장바구니·위시리스트·익명 세션 ↔ 로그인 병합 |
| **Order** | 주문 생성·상태 전이·취소·반품 |
| **Payment** | 결제 승인·취소·환불·외부 PG 연동 |
| **Inventory** | 재고 잔량·예약·차감·입고·조정 (WMS의 Inventory와 다름: 판매 가능 수량 중심) |
| **Promotion** | 쿠폰·할인·적립금·추천 코드 |
| **Shipping** | 배송 준비·트래킹·배송 상태 알림 |
| **Review** | 상품 리뷰·평점·사진·신고·노출 정책 |
| **Search** | 상품 검색·자동완성·추천·필터링·정렬 |
| **Notification** | 주문/배송/프로모션 알림 (이메일·SMS·푸시·인앱) |
| **Admin / Operations** | 대시보드, 정산, 운영자 권한, 콘텐츠 관리 |

각 context는 자체 데이터 저장소를 가지며, 통신은 **이벤트** 또는 **내부 HTTP**로만. 전형적으로 `read-heavy` + `transactional` + `content-heavy` + `integration-heavy` trait와 함께 선언된다.

---

## Ubiquitous Language

이 도메인의 핵심 용어 정의. 서비스·API·이벤트·문서는 **같은 용어로 같은 개념**을 가리켜야 한다. Bounded Contexts 표의 책임과 겹치지 않는 선에서 용어 자체의 정의를 명시.

| Term | Definition |
|---|---|
| **User** | 가입된 계정. UUID 로 식별. email, hashed password, profile name 보유. `Customer` 와 동의어로 혼용하지 않고 `User` 로 통일. |
| **Order** | 고객이 하나 이상의 Product 를 구매하기 위해 생성한 요청. `order-service` 가 소유. Order ID 는 UUID v7 권장. |
| **Payment** | Order 에 연결된 금융 거래. `payment-service` 가 소유. Order 와 별도 aggregate (rule E2). |
| **Product** | 판매 가능한 품목. 가격·재고·설명·미디어 보유. `product-service` 가 소유. SKU 개념이 필요하면 `Variant` 로 분리. |
| **Variant** | 동일 Product 의 옵션 조합(사이즈·색상 등). 각 Variant 가 독립된 가격·재고를 가질 수 있음. |
| **Inventory** | Product/Variant 의 판매 가능 수량. rule E3 에 따라 `available` 과 `reserved` 상태로 분리 관리. |
| **Cart** | 구매 전 담아둔 항목들. 익명(쿠키) 또는 로그인(User ID) 기준. rule E4 에 따라 로그인 시 merge. |
| **Promotion** | 쿠폰·할인·적립금 정책. 주문 시점 스냅샷 저장 (rule E7). |
| **Coupon** | Promotion 에서 발급된 사용자별 이용권. |
| **Review** | 실제 구매 이력(`DELIVERED` Order) 있는 User 가 Product 에 대해 작성한 평가. rule E6. |
| **Wishlist** | User 가 관심 표시한 Product 목록. Cart 와 구분 (구매 의도 아님). |
| **PG (Payment Gateway)** | 외부 결제 대행사 (토스페이먼츠·나이스 등). Payment 가 호출하며 `Idempotency-Key` 필수 (rule E2). |

용어가 변경될 때는 이 표를 먼저 수정하고, 서비스·API·이벤트 명세를 뒤따라 업데이트한다.

---

## Standard Error Codes

도메인 에러 코드 표 자체는 플랫폼 레지스트리인 [`platform/error-handling.md`](../../platform/error-handling.md) 에서 `[domain: ecommerce]` 태그가 붙은 섹션에 유지된다. 중복을 피하기 위해 이 파일에서는 표를 반복하지 않고, 섹션 대응만 나열한다:

| Bounded Context | Error section in `platform/error-handling.md` |
|---|---|
| Catalog (Product, Variant) | `Product`, `Search` |
| Order | `Order` |
| Payment | `Payment` |
| Cart / Wishlist | `Wishlist` |
| Promotion | `Promotion` |
| Review | `Review` |
| Shipping | `Shipping` |
| Identity / User | `User` |
| Notification | `Notification` |
| Settlement (marketplace commission/payout, ADR-MONO-030) | `Settlement` |

플랫폼 공통 에러 (`VALIDATION_ERROR`, `UNAUTHORIZED`, `FORBIDDEN`, `RATE_LIMIT_EXCEEDED`, `CONFLICT`, `STATE_TRANSITION_INVALID`, …) 는 모든 도메인이 공유한다. 미디어 업로드 실패는 Content-Heavy Trait 공통 코드(`STORAGE_UNAVAILABLE`, `MEDIA_NOT_FOUND`, `MEDIA_VALIDATION_FAILED`) 를 사용한다.

새 에러 코드 추가 시:
1. 먼저 `platform/error-handling.md` 의 해당 섹션에 등록.
2. 필요하면 이 표에서 섹션이 신설됐는지 확인.
3. 해당 서비스 코드에서 사용.

---

## Integration Boundaries

### 외부 (플랫폼 경계 바깥)

- **PG (결제 대행사)** — Payment 가 승인/취소/환불을 위해 호출한다. `Idempotency-Key` 필수이며, 외부 호출 실패/타임아웃은 Saga 보상으로 처리한다 (E2). 카드 번호는 저장하지 않고 PG 빌링 키만 보유한다 (E8).
- **알림 채널 (SMS / Email / Push provider)** — 주문·배송·프로모션 알림을 외부 발송 대행으로 내보낸다 (Notification context).
- **물류사 / 배송 트래킹 API** — 배송 준비·트래킹·배송 상태를 외부 물류사와 주고받는다 (Shipping context).
- **검색 엔진 / CDN** — 상품 읽기 모델을 검색 인덱스·CDN 정적 스냅샷으로 투영한다. 원본 카탈로그와의 동기화 지연은 SLA 로 명시한다 (E5).
- **고객 공개 트래픽** — B2C 도메인이므로 익명 탐색·가입·주문 경로가 외부에 공개된다 (`internal-system` 도메인과 달리 공개 표면이 정상). 인증·레이트리밋·PII 보호는 게이트웨이/서비스 계층에서 강제한다.

### 내부 (같은 프로젝트 내 다른 서비스)

- gateway → 모든 service: 인증 토큰 검증, 레이트리밋, 권한 클레임 전파.
- order ↔ payment: 주문 확정과 결제 승인을 **별도 aggregate + Saga** 로 조정한다 (E2). 단일 DB 트랜잭션으로 묶지 않는다.
- order → inventory: 주문 생성 시 `reserve`, 결제 완료 시 `decrement`, 실패/타임아웃 시 `release` (E3).
- cart → catalog: 장바구니 항목의 상품·가격·옵션 유효성 확인 (E4).
- order → promotion: 주문 시점 프로모션 스냅샷을 확정한다 (E7).
- review → order: 리뷰 작성 자격(`DELIVERED` 구매 이력) 확인 (E6).
- catalog write → read projection: 쓰기 카탈로그가 읽기 모델(검색 인덱스/캐시/CDN)로 eventual 하게 투영된다 (E5).

### 내부 이벤트 카탈로그 (권장)

- `<prefix>.order.created` / `.paid` / `.preparing` / `.shipped` / `.delivered` / `.cancelled` / `.returned`
- `<prefix>.payment.approved` / `.failed` / `.timeout` / `.refunded`
- `<prefix>.inventory.reserved` / `.decremented` / `.released`
- `<prefix>.promotion.applied`

---

## Mandatory Rules

### E1. 주문 라이프사이클 상태 기계 (이벤트-소싱 가능)

`Order`는 명시적 상태 기계로 모델링한다. 허용 전이:

```
CREATED → PAID → PREPARING → SHIPPED → DELIVERED
        ↘ CANCELLED                       ↘ RETURNED
```

- 모든 전이는 이벤트로 기록 (event sourcing or event log)
- 비허용 전이 시도 → 422 `STATE_TRANSITION_INVALID`
- `CANCELLED` 이후 `REFUNDED`로의 Payment 전이는 별개 state machine이 관리

### E2. Payment는 Order와 **별도 aggregate**, Saga로 조정

Order 생성 → Payment 요청 → PG 응답 → Order 확정은 분산 트랜잭션. 단일 DB 트랜잭션으로 묶지 않는다.

- Payment 승인/실패/타임아웃 모두 이벤트 → Order가 subscribe
- Saga 보상: Payment 승인됐으나 Order confirm 실패 시 자동 환불 이벤트 발행
- PG 외부 호출은 `Idempotency-Key` (transactional trait T1 준수)

### E3. 재고 예약 (reservation) vs 차감 (decrement) 분리

`Inventory`는 두 종류의 상태를 관리:

- **Available quantity**: 주문 가능한 수량
- **Reserved quantity**: 결제 대기 중 묶인 수량

주문 생성 시 `reserve`, 결제 완료 시 `decrement`, 결제 실패/타임아웃 시 `release`.

- Reservation은 timeout을 가져야 하며 (기본 15분), 만료되면 자동으로 해제
- Decrement 이후 재고 부족 발생 시 → 운영자 개입 (oversold exception, alert)

### E4. 장바구니는 익명 세션 + 로그인 전환 가능

비회원(익명) 세션 장바구니와 로그인 사용자 장바구니를 모두 지원. 로그인 시점에 익명 카트를 사용자 카트로 merge.

- 익명 카트: 클라이언트 쿠키 ID + 서버 Redis (TTL 14일 권장)
- 로그인 카트: 사용자 ID 기준 DB 영구 저장
- 머지 전략: 동일 상품 중복 시 수량 합산, 옵션 다른 경우 별도 항목으로 유지

### E5. 상품 카탈로그는 읽기 복제본으로 분리 가능

`Product` 쓰기(관리자 카탈로그 등록)와 읽기(고객 상품 탐색)는 트래픽 비율이 1:100+. 별도 read replica / read-optimized projection 허용.

- 쓰기 모델: 원본 카탈로그 스키마 (trait `content-heavy` rule C3 참조)
- 읽기 모델: 검색 엔진 인덱스, 캐시 레이어, CDN-ready 정적 스냅샷 (trait `read-heavy` rule R1 참조)
- 동기화 지연 (eventual consistency) 허용; 최대 지연 SLA 명시 필수 (권장 5s)

### E6. 리뷰·평점은 익명 투표 아님

리뷰 작성은 **실제 구매 내역** 확인 필수. Order가 `DELIVERED` 상태인 Product에 대해서만 review 작성 허용.

- 평점은 1-5 정수
- 스팸·부적절 리뷰 신고 → hidden 상태로 전환 (soft delete)
- 작성자 본인만 수정/삭제 가능 (관리자 hide는 별개)

### E7. 프로모션 / 할인 계산은 **주문 시점에 확정**

쿠폰·할인은 주문 생성 시점의 스냅샷을 저장. 이후 쿠폰 정책이 바뀌어도 이미 생성된 주문의 금액은 변하지 않는다.

- 주문 JSON에 `appliedPromotions[]` 고정
- 재주문·환불 시 해당 시점 프로모션 재검증 안 함 (스냅샷 사용)

### E8. PII 최소 저장 + 마스킹

`data_sensitivity: pii`는 기본. 다음 데이터는 암호화 또는 토큰화:
- 결제 카드 번호: **절대 직접 저장 금지**, PG의 빌링 키만
- 주민번호/사업자번호: 마스킹 후 저장 (서비스에 필요시에만)
- 이메일·전화번호: 민감도 낮지만 로그 출력 시 마스킹 권장

compliance 요구사항은 프로젝트 `PROJECT.md` 의 `compliance` 필드 선언에 따라 추가 rule 적용 (PCI-DSS, GDPR 등).

---

## Forbidden Patterns

- Order와 Payment를 단일 트랜잭션으로 묶기 — 외부 PG 실패 시 전체 롤백 불가
- 재고 예약 없이 바로 차감 — 동시성 충돌 많음
- 카탈로그 읽기를 원본 DB에서 직접 조회 — 쓰기와 읽기 경합 발생
- Review 작성자 인증 없이 허용 — 스팸 / 조작
- 쿠폰 가격 계산을 주문 조회 시점에 실시간 재계산 — 소급 변경 사고

---

## Required Artifacts

1. **주문 상태 다이어그램** — `CREATED → PAID → PREPARING → SHIPPED → DELIVERED` (+ `CANCELLED` / `RETURNED`), 전이 조건과 이벤트 기록 방식. 위치: `specs/services/<order-service>/state-machines/order-status.md`.
2. **Order ↔ Payment Saga 정의** — 결제 요청/승인/실패/타임아웃 단계, 보상 액션(승인 후 confirm 실패 시 자동 환불), 타임아웃. 위치: `specs/services/<order-or-payment-service>/sagas/` 또는 `specs/features/<checkout-feature>.md`.
3. **재고 예약 모델** — `available` / `reserved` 상태, `reserve`/`decrement`/`release` 전이, reservation timeout(기본 15분), oversold 처리. 위치: `specs/services/<inventory-service>/data-model.md`.
4. **장바구니 merge 전략 문서** — 익명(쿠키+Redis) ↔ 로그인(User ID+DB) 병합 규칙, TTL. 위치: `specs/services/<cart-service>/cart-merge.md`.
5. **카탈로그 읽기/쓰기 분리 + 동기화 SLA** — 쓰기 원본 ↔ 읽기 투영(검색 인덱스/캐시/CDN)의 동기화 방식과 최대 지연 SLA. 위치: `specs/services/<product-service>/read-model.md` 또는 `knowledge/architecture/catalog-projection.md`.
6. **PII 처리 / 마스킹 정책** — 카드 번호 비저장(PG 빌링 키), 민감 필드 마스킹/토큰화, 로그 마스킹. 위치: `specs/services/<service>/security.md` 또는 `knowledge/architecture/pii-policy.md`.
7. **에러 코드 등록** — 위 Standard Error Codes 의 각 섹션이 [../../platform/error-handling.md](../../platform/error-handling.md) 의 `[domain: ecommerce]` 태그 섹션에 존재.
8. **Bounded context 맵** — 위 contexts 의 데이터 소유와 통신 방향(이벤트/내부 HTTP) 도식. 위치: `specs/services/` 전반 또는 `knowledge/architecture/context-map.md`.

> **Library 경계**: 본 파일에는 구체 service 명을 직접 적지 않는다 — `<order-service>` 등 placeholder 또는 bounded context 이름 사용. 실제 service 명은 각 프로젝트의 `PROJECT.md` Service Map 과 `specs/services/` 가 담당.

---

## Interaction with Common Rules

- [../../platform/architecture.md](../../platform/architecture.md) 의 서비스 경계 원칙을 따르되, 위 bounded context 구분을 참조한다.
- [../../platform/error-handling.md](../../platform/error-handling.md) 에 위 Standard Error Codes 가 등록되어야 한다.
- [../traits/transactional.md](../traits/transactional.md) **(필수)**: Order·Payment·Inventory·Promotion 이 모두 idempotency + 상태기계를 요구한다. T1~T8 전체가 E1·E2·E3·E7 에 직접 적용된다.
- [../traits/content-heavy.md](../traits/content-heavy.md) **(강력 권장)**: Product·Review·Promotion 이 콘텐츠 자산이다. C1~C6 이 E5·E6 에 적용되며, 미디어 저장은 [../../platform/object-storage-policy.md](../../platform/object-storage-policy.md) 를 준수한다.
- [../traits/read-heavy.md](../traits/read-heavy.md) **(강력 권장)**: 쇼핑·검색·상품 조회가 쓰기의 수십 배다. R1~R5 가 E5 (읽기 복제본 분리) 에 적용된다.
- [../traits/integration-heavy.md](../traits/integration-heavy.md) **(선택)**: PG·SMS/Email provider·물류사 API·광고 플랫폼 등 외부 연동이 많은 경우 I1~I10 이 E2 (PG 호출) 와 위 Integration Boundaries 의 외부 연동에 적용된다.

---

## Checklist (Review Gate)

- [ ] Order 가 정의된 상태기계로만 전이되고 모든 전이가 이벤트로 기록되며 비허용 전이가 `STATE_TRANSITION_INVALID` 로 거부되는가? (E1)
- [ ] Order 와 Payment 가 별도 aggregate 이고 Saga + 보상으로 조정되며 PG 호출이 `Idempotency-Key` 를 쓰는가? 단일 트랜잭션으로 묶이지 않는가? (E2)
- [ ] 재고가 `reserve` / `decrement` / `release` 로 분리되고 reservation timeout 이 있는가? 예약 없이 바로 차감하지 않는가? (E3)
- [ ] 장바구니가 익명 세션 ↔ 로그인 merge 를 지원하고 병합 전략이 정의되어 있는가? (E4)
- [ ] 카탈로그 읽기가 읽기 모델/투영에서 조회되고 원본 DB 직접 조회가 없으며 동기화 SLA 가 명시되는가? (E5)
- [ ] 리뷰가 `DELIVERED` 구매 이력 확인 후에만 작성되고 작성자 인증이 강제되는가? (E6)
- [ ] 프로모션/할인이 주문 시점 스냅샷으로 확정되고 조회 시점 재계산이 없는가? (E7)
- [ ] 카드 번호가 저장되지 않고(PG 빌링 키만) 민감 필드가 마스킹/토큰화되며 로그가 마스킹되는가? (E8)
- [ ] 주문 상태 다이어그램·Saga 정의·재고 모델·장바구니 merge·카탈로그 투영 SLA·PII 정책 문서가 존재하는가?
- [ ] 표준 에러 코드가 플랫폼 카탈로그(`[domain: ecommerce]`)에 등록되어 있는가?
- [ ] 금지 패턴(Order+Payment 단일 트랜잭션, 예약 없는 차감, 원본 DB 직접 읽기, 미인증 리뷰, 쿠폰 실시간 재계산)이 코드베이스에 존재하지 않는가?
