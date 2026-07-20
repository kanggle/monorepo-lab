# TASK-BE-538 — ADR-002 Decision-3 의 문자적 요구를 만족하는 구현이 하나도 없다: 문구를 판정한다

**Status:** review

**Type:** TASK-BE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Opus 4.8 (ADR 문구 판정 + 20 엔드포인트 분류 재검. 구현이 아니라 판단이 실질이다)

> `TASK-BE-535`(PR #2747) 와 `TASK-BE-536`(PR #2754) 이 **양쪽 다** 명시적으로 범위 밖에 남기며 *"ADR 소유자의 결정이지 이 task 의 것이 아니다"* 라고 적은 항목. 두 티켓이 근거 데이터를 이미 남겨 뒀으므로 지금이 판정 적기다.

---

## ⚠️ ACCEPT 게이트 경계 (먼저 읽어라)

**이 task 는 ADR-002 를 개정하지 않는다.** `ADR-002` 는 `Accepted` 상태이고, ACCEPTED ADR 의 Decision 절을 고치는 것은 [`platform/architecture-decision-rule.md`](../../../../platform/architecture-decision-rule.md) § The ACCEPTED Gate 대상이다 — **맨 "진행" 으로는 열리지 않으며 사용자의 정확형 의사표시가 필요**하다.

이 task 의 산출물은 **판정과 근거**다: 문구가 틀렸는지 / 코드가 틀렸는지 / 둘 다 옳고 표현만 좁은지, 그리고 고친다면 **정확히 어떤 문장으로** 고칠지의 제안. 실제 개정은 그 제안이 승인된 뒤 별 task 다.

---

## Goal

[`ADR-002`](../../docs/adr/ADR-002-saga-over-distributed-transaction.md) Decision-3 은 이렇게 적혀 있다:

> **Idempotency Key**: 금전·재고 변경 API는 클라이언트 측 키를 받아 **중복 요청을 결정적으로 거부**.

2026-07-20 인구조사가 이 문장을 코드에 대고 측정했다. **범위 안 20 엔드포인트 중 이 문장을 문자 그대로 만족한 것은 0개였다.** 분포는 `0 ENFORCED / 1 OPTIONAL / 13 NATURAL-KEY / 6 NONE`.

그런데 **13 개(NATURAL-KEY)는 실제로 안전하다** — unique 제약이나 상태기계가 중복을 막는다. 즉:

- 이 ADR 은 **코드가 실제로 하는 안전 확보 방식(자연키·상태기계)을 인정하지 않는다.**
- 그 결과 **옳은 코드가 문자 그대로는 영구 위반 상태**로 남는다.
- 그리고 이 상태를 **어떤 검사도 잡지 못한다** — 잡았다면 13 건이 계속 RED 였을 것이다.

`TASK-BE-535`/`536` 이 NONE 6 건을 닫아 **문자적 만족이 0 → 6 으로 올라갔다.** 그러나 **13 건은 여전히 문자 그대로는 위반이면서 옳다.** 숫자가 방금 바뀌었으므로 지금이 문구를 현실에 맞출 시점이다.

### 이것이 왜 `TASK-MONO-444` 와 같은 클래스인가

`MONO-444` 는 `platform/error-handling.md` 가 **코드에 없는 동작을 선언**하는 건이다. 이 task 는 ADR 이 **코드가 실제로 쓰는 안전 수단을 인정하지 않는** 건이다. 방향은 다르지만 성질은 같다 — **권위 문서와 진실이 어긋나 있고, 자동 검사는 그 축을 보지 않는다.** 둘을 같이 읽으면 이 저장소의 "선언 ↔ 진실" 결함이 두 방향 모두에서 나타난다는 게 보인다.

---

## Scope

### In Scope

1. **AC-0 인구조사 재측정** (아래 AC-0). 20/0/1/13/6 은 **출처가 아니라 가설**이다.
2. 세 갈래 중 판정 + 근거 기록:
   - **(A) 문구가 좁다** — 자연키·상태기계도 "결정적 거부" 의 정당한 구현임을 인정하도록 D3 를 다시 쓴다. *현재로선 가장 유력해 보이나, AC-0 재측정이 뒤집을 수 있다.*
   - **(B) 문구가 옳고 코드가 미달** — 13 건 전부에 클라이언트 키를 붙인다. **BE-535/536 이 둘 다 "순수 비용" 이라며 명시적으로 거부한 방향**이므로, 고르려면 그 판단이 왜 틀렸는지를 적어야 한다.
   - **(C) 범위 정의가 문제** — "금전·재고 변경 API" 의 경계가 모호해서 20 이라는 모집단 자체가 흔들린다. 그렇다면 고칠 것은 요구가 아니라 **적용 범위 문장**이다.
3. 판정이 (A)/(C) 면 **제안 문장을 확정 형태로 작성**한다(개정 task 가 그대로 쓸 수 있게).
4. 개정을 수행할 후속 task 의 형태를 적는다(ACCEPT 게이트 절차 포함).

### Out of Scope

- **ADR-002 본문 수정 0** — § ACCEPT 게이트 경계 참조. 이 task 는 제안까지다.
- **코드 변경 0** — 어떤 엔드포인트도 건드리지 않는다. (B) 로 판정돼도 구현은 별 task.
- **`platform/error-handling.md` 0** — 그건 `TASK-MONO-444` 다.
- **`libs/java-web-servlet` `IdempotencyKeyFilter` 채택 재논의 0** — 비채택은 `TASK-BE-430` 에 기록된 별도 결정이다.

---

## Acceptance Criteria

- **AC-0 (gate — 인구조사 재측정)** — `0 ENFORCED / 1 OPTIONAL / 13 NATURAL-KEY / 6 NONE` 과 "범위 안 20 개" 를 **직접 다시 센다.** BE-535/536 이 6 건을 바꿨으므로 **분포는 이미 달라져 있어야 한다** — 달라지지 않았다면 내 측정이 틀린 것이다. 엔드포인트 목록도 재열거한다(모집단을 물려받지 않는다). **탐지식은 known-positive 로 자기검증한 뒤 쓴다** (예: `order-service` 는 멱등 관련 hit 가 많아야 한다 — 0 이 나오면 탐지식이 깨진 것).
- **AC-1** — (A)/(B)/(C) 판정 + 근거. 특히 (B) 를 고른다면 BE-535/536 의 "NATURAL-KEY 13 건에 키를 붙이는 것은 순수 비용" 판단을 **명시적으로 반박**해야 한다.
- **AC-2** — (A)/(C) 판정 시 **제안 문장이 확정 형태**로 존재한다. "이런 취지로 고치자" 가 아니라 그대로 붙여넣을 수 있는 문장이어야 한다.
- **AC-3** — 제안 문장이 **현재 코드베이스를 정확히 기술**하는지 20 개 전부에 대고 대조한다. 새 문구가 또 일부를 영구 위반으로 남기면 같은 결함을 반복하는 것이다.
- **AC-4** — 후속 개정 task 의 형태 기록(ACCEPT 게이트 절차 명시).
- **AC-5** — 문서만. `apps/**` diff 0, ADR 본문 diff 0.

---

## Related Specs

- [`ADR-002`](../../docs/adr/ADR-002-saga-over-distributed-transaction.md) § Decision 3 — 판정 대상
- [`platform/architecture-decision-rule.md`](../../../../platform/architecture-decision-rule.md) § The ACCEPTED Gate — 왜 이 task 가 개정까지 가지 않는가
- `tasks/done/TASK-BE-535-money-path-duplicate-request-guards.md` — 인구조사 출처 + money 2 건, "13 건에 키를 붙이지 마라" 판단
- `tasks/done/TASK-BE-536-inventory-coupon-path-duplicate-request-guards.md` — stock/coupon 4 건, 혼합 메커니즘이 옳다는 실증
- `../../../../tasks/ready/TASK-MONO-444-error-registry-describes-overlap-rejection-that-code-permits.md` — 같은 "선언 ↔ 진실" 클래스의 반대 방향 사례

---

## Related Contracts

- 없음 — 판정 task 다. 계약·스키마·코드 변경 0.

---

## Edge Cases

1. **🔴 인구조사 숫자를 물려받는 것** — 20/13/6 은 내가 만든 숫자고 **가설이다.** BE-535/536 이 6 건을 바꿨으니 재측정하면 달라야 정상이다. 같게 나오면 측정이 잘못된 것이다.
2. **"문서를 코드에 맞춘다" 의 함정** — 문구를 코드에 맞춰 느슨하게 고치면 *어떤 코드도 위반이 아닌* 무력한 문장이 될 수 있다. **새 문구도 무언가를 거부할 수 있어야 한다** — 그러지 못하면 규칙이 아니라 서술이다.
3. **NATURAL-KEY 13 건이 정말 안전한지** — "unique 제약이 있다" 와 "중복 요청이 실제로 거부된다" 는 다르다. `BE-536` 에서 **제약은 있는데 plain `save()` 라 커밋 시점에야 터져 500 으로 새는** 사례가 실제로 나왔다(`uq_product_variants_option`). **13 건 중 같은 지문이 더 있는지 표본이라도 확인하라** — 있으면 그것이 이 task 의 가장 큰 수확이다.
4. **(C) 가 과소평가되기 쉽다** — "금전·재고 변경 API" 라는 범위 문장이 모호하면 다음 인구조사도 다른 20 을 셀 것이다. 요구를 고치는 것보다 범위를 고치는 게 답일 수 있다.

---

## Edge 3 수행 결과 (2026-07-20 — 선행 완료)

> **🔴 이 절은 2026-07-20 재측정으로 무효화됐다.** 아래 500-유출 판정들은 `TASK-BE-542` 가 10개 서비스 전부에
> `@ExceptionHandler(DataIntegrityViolationException)` 을 배선하기 **전**의 상태다. 현재는 27건 중 500 유출 0건이다.
> 판정 근거로는 **§ AC-0 인구조사 재측정 결과**를 읽어라. 이 절은 판정 경위 기록으로만 남긴다.

Edge 3(*"unique 제약이 있다"* ≠ *"중복이 실제로 거부된다"*)를 **표본이 아니라 전수**로 수행했다. ecommerce `apps/**` 의 유니크 제약 전부를 열거하고 각 쓰기 경로를 추적했다. **이 task 의 가장 큰 수확이 맞았고, 남은 (A)/(B)/(C) 판정에 직접 영향을 준다.**

### 결정적 발견 — 중복 거부의 종착지가 대부분 500 이다

`@ExceptionHandler(DataIntegrityViolationException.class)` 보유 현황(형제 대조):

- **보유**: wms `outbound` · scm `procurement` · finance `account` · fan-platform 4개 · ecommerce `auth`·`user`
- **미보유**: ecommerce 나머지 **9개 서비스** — 잡히지 않은 제약 위반은 각 서비스 자기 `@ExceptionHandler(Exception.class)` 로 낙하해 **500 `INTERNAL_ERROR`**

이 서비스들의 `ConstraintViolationException` 핸들러는 **전부 `jakarta.validation`**(빈 검증)이라 DB 제약과 무관하다. 이름 유사성이 "이미 처리됨" 처럼 보이게 한다.

### 분류 결과

| 제약 | 서비스 | 판정 |
|---|---|---|
| `uq_product_variants_option` (`POST /{id}/variants`) | product | **SAFE** — `saveAndFlush`+catch → 409 |
| `uq_product_variants_option` (`POST /admin/products`) | product | **LEAKS-500** — 같은 제약의 두 번째 쓰기 경로, plain `save`, 미방어 |
| `uq_orders_idempotency` | order | **RACE-ONLY→500** — catch 가 plain `save` 를 감싸 **죽은 코드** |
| `uq_seller_commission_rate_tenant_seller` | settlement | **RACE-ONLY→500** |
| `uq_seller_payout_period_seller` | settlement | **RACE-ONLY→500** |
| `uq_reviews_user_product_active` | review | **RACE-ONLY→500** + 선체크/제약 범위 불일치 |
| `uq_template_tenant_type_channel` | notification | **RACE-ONLY→500** (선체크 컬럼=제약 컬럼, 가장 정합) |
| `uq_push_subscriptions_tenant_endpoint` | notification | **제약에 도달 불가** — 조회가 제약보다 넓어 INSERT 분기에 안 감 |
| `uq_notifications_event_id` | notification | **제약이 쓰기 모양과 어긋남** — 정상 최초 전송이 위반 → DLQ |
| `uq_stranded_refund_open_payment` | payment | **SAFE** — `REQUIRES_NEW` 라 flush 가 호출자 try 안에서 일어남 |
| `uq_wishlist_items_user_product` | user | **SAFE** — 선체크 409 + DIVE 핸들러가 경합까지 커버 |
| `uq_shippings_order_id` / `uq_stock_reservations_order_id` / `uq_user_profiles_user_id` | shipping·product·user | HTTP 미도달(컨슈머 전용), 컨슈머 내 경합은 재시도/DLQ |
| `uq_product_variants_sku` | product | **NOT-REACHABLE** — `sku` 를 쓰는 코드 없음 |

### Edge 3 가 (A)/(B)/(C) 판정에 주는 것

**"13 건은 실제로 안전하다" 는 전제가 무너졌다.** 안전한 것은 소수이고, 다수는 **순차 요청에 대해서만** 안전하며 동시 요청에서는 500 이다. 즉 D3 의 *"중복 요청을 결정적으로 거부"* 를 **자연키·상태기계도 만족한다고 인정하는 (A) 안**을 그대로 쓰면, **실제로는 500 을 내는 경로들까지 규정 준수로 인정**하게 된다.

⇒ **(A) 를 고르더라도 "자연키·상태기계"만으로는 부족하고, "동시 요청에서도 4xx 로 거부된다" 는 조건이 문장에 들어가야 한다.** 그래야 Edge 2("아무것도 거부하지 못하는 문장") 를 피한다. 이것이 Edge 3 의 판정 기여다.

### 파생 티켓 (이 task 는 여전히 코드 변경 0)

Edge 3 의 수확은 **전부 별 task 로 분리**했다 — 이 task 는 판정 문서로 남는다:

- `TASK-BE-539` — `uq_notifications_event_id` 모양 오류 → 정상 이벤트 DLQ 유실
- `TASK-BE-540` — 선체크 범위 ≠ 제약 범위 2건 (review · notification push)
- `TASK-BE-541` — 지연 flush 로 죽은 중복 방어 catch + 이를 가려 온 불가능 픽스처 테스트
- `TASK-BE-542` — 레지스트리가 선언한 `DATA_INTEGRITY_VIOLATION` 409 를 ecommerce 9개 서비스가 미구현

**미검증으로 남긴 것**: review 사례의 교차 테넌트 도달성(같은 `(user_id, product_id)` 가 두 테넌트에 존재 가능한지)은 재지 않았다 — `TASK-BE-540` AC-0 이 가른다. 도달 불가면 그 건은 "500 결함" 이 아니라 "정합성 결함" 으로 강등된다.

---

## AC-0 인구조사 재측정 결과 (2026-07-20 수행)

### 탐지식 자기검증 (선행)

모집단은 **키워드 검색이 아니라 `**/*Controller.java` 전수 열거**로 구성했다. 분류 탐지식은 신뢰 전에 known-positive 로 검증했다:

- `order-service` 멱등 hit 다수 — `V10__add_order_idempotency_key.sql`, `OrderPlacementService` ✔
- `shipping-service` `ProcessCarrierWebhookService.registerIfFirst:48` + PK `pk_processed_carrier_webhooks` ✔
- `product-service` `uq_product_variants_option`(`V5:14`) + `saveAndFlush`/`save` 두 경로 ✔

`ConstraintViolationException` 핸들러는 전부 import 를 열어 확인했다 — order `:12`, settlement `:12`, promotion `:19` 모두 `jakarta.validation` 이라 DB 제약 증거로 **채택하지 않았다.**

### 분포가 바뀌었다 — 그리고 모집단 크기 자체가 바뀌었다

| | 가설 (티켓 인용) | **재측정** |
|---|---|---|
| 모집단 | 20 | **27** |
| ENFORCED | 0 | **4** |
| OPTIONAL | 1 | **2** |
| NATURAL-KEY | 13 | **19** |
| NONE | 6 | **2** |

**서비스별**: order 5 · payment 3 · settlement 4 · promotion 5 · product 7 · shipping 3 = 27.
**모집단 0 확인**(컨트롤러 전수 열거로 부재 증명): user(4) · review(1) · notification(3) · auth(3) · search(2). `batch-worker`·`gateway-service` 는 HTTP 표면 자체가 0(`*Controller.java` 0건 + `RestController|RequestMapping|RouterFunction` grep 0건, 이중 확인).

### 🔴 이 티켓의 제목 전제가 이미 낡았다

*"문자적 요구를 만족하는 구현이 하나도 없다"* 는 **거짓이 됐다. 4건이 만족한다:**

| 엔드포인트 | 키 강제 지점 | 경합 중재자 |
|---|---|---|
| `POST /api/payments/{id}/refund` | `PaymentRefundService:223-226` → 400 | `uq_payment_refund_request_key` + `saveAndFlush` |
| `POST /api/promotions/{id}/coupons/issue` | `CouponCommandService:69-72` → 400 | `uq_coupon_issue_request_key` + `saveAndFlush` |
| `POST /api/admin/products` | `RegisterProductService:84` → 400 | `productCreateRequestRepository.insert`(`saveAndFlush`) |
| `PATCH /api/admin/products/{id}/stock` | `AdjustStockService:87` → 400 | 키 선점 후 재고 이동(`:117-123`) |

4건 모두 **컨트롤러 바인딩은 `required = false`** 이고 서비스가 `IdempotencyKeyRequiredException` 으로 하드 거부한다. 애노테이션만 보고 분류했으면 전부 OPTIONAL 로 오분류했을 자리다 — 탐지 지점을 컨트롤러가 아니라 **키를 실제로 강제하는 층**에 둬야 한다.

### 🔴 § Edge 3 의 중심 주장이 무효화됐다 — `TASK-BE-542` 가 고쳤다

Edge 3 절은 *"중복 거부의 종착지가 대부분 500"* 이고 ecommerce **9개 서비스가 `@ExceptionHandler(DataIntegrityViolationException)` 미보유**라고 적었다. **지금은 10개 서비스 전부 보유한다** — auth·notification·order·payment·product·promotion·review·settlement·shipping·user. `TASK-BE-542`(done)가 배선한 결과다.

⇒ **재측정 27건 중 500 으로 새는 행은 0건이다.** Edge 3 표의 `LEAKS-500` / `RACE-ONLY→500` 판정은 **전부 stale** 하다.

`product-service` `GlobalExceptionHandler:197-210` 이 그 구조를 보여준다 — unique 위반은 409 `DATA_INTEGRITY_VIOLATION`, FK/NOT NULL/CHECK 는 의도적으로 500 유지(BE-542 AC-1). `@Transactional` 커밋은 서비스 프록시 반환 시점, 즉 **컨트롤러 콜스택 안**에서 일어나므로 커밋 시 터진 제약 위반도 `@RestControllerAdvice` 에 도달한다.

> **하위 조사 1건 오보를 직접 반증함**: `PATCH /variants/{variantId}` 를 "500 LEAK, product-service 에 DIVE 핸들러 없음" 으로 본 중간 보고가 있었으나, 핸들러는 `GlobalExceptionHandler:197` 에 존재한다. 해당 행은 **NATURAL-KEY → 409** 로 정정했다. `saveAndFlush`+catch 와 plain `save` 의 실제 차이는 *"409 vs 500"* 이 아니라 **"도메인 특화 409(`DuplicateVariantOptionException`) vs 일반 409(`DATA_INTEGRITY_VIOLATION`)"** 다.

### 판정에 영향을 준 나머지 실측

- `DELETE /api/admin/products/{id}` — `findWithVariantsById` 가 `deletedAt IS NULL` 로 필터(`ProductJpaRepository:29`)하므로 재요청은 404. **NATURAL-KEY 확정.**
- `POST /api/shippings/carrier-webhook` — `JpaWebhookDeliveryStore:44-47` 이 null/blank `deliveryId` 를 **"최초" 로 취급해 통과**시킨다. PK dedupe 를 광고하지만 키 없는 경로는 무방비 ⇒ **OPTIONAL**. (`TASK-BE-536` § Related Contracts 가 이미 경고한 구멍이다.)
- `POST /api/orders` — `required = false`, 키가 없으면 `OrderPlacementService:35` 의 `idempotent` 가 false 가 되어 `Order.create` 로 그대로 낙하 ⇒ **OPTIONAL**.

---

## AC-1 판정 — **(A) + (C)**. (B) 는 기각한다

### (B) 기각 — BE-535/536 의 "순수 비용" 판단은 옳았다

(B) 는 *"NATURAL-KEY 19건에 클라이언트 키를 붙여라"* 인데, 재측정이 그 반대를 **지지**한다. BE-535/536 이 실제로 한 선택에 이미 기준이 드러나 있다:

| 상황 | 그들의 선택 | 왜 옳은가 |
|---|---|---|
| 부분 환불 · 재고 증감 | **클라이언트 키 필수** | 동일한 두 요청이 **둘 다 의도된 것일 수 있다.** 서버는 재시도와 정당한 반복을 구별할 정보가 없다 |
| 정산 기간 개설 | **자연키**(`ux_settlement_period_open_window` 부분 유니크) | `(테넌트, 윈도우)` 가 신원을 완전히 결정한다 |
| 주문 취소 · 결제 승인 · 쿠폰 사용 | **상태기계** | 두 번째 요청은 전이 불가 상태를 만나 결정적으로 거부된다 |

`POST /api/orders/{id}/cancel` 에 키를 붙여도 얻는 게 없다 — `Order.cancel` 이 `save` 이전에 인메모리로 던지므로 이미 결정적이다. **키는 결정성을 만들지 못하는 곳에서만 결정성을 산다.** (B) 는 19건 중 최소 17건에 대해 순수 비용이다.

### (A) — 요구 문장이 좁다

19/27 이 **옳으면서 문자 그대로 위반**이다. D3 는 `클라이언트 키 수령` AND `결정적 거부` 를 붙여 놓았는데, 이 저장소가 실제로 쓰는 결정성 확보 수단 중 **키는 셋 중 하나**일 뿐이다.

### (C) — 적용 범위 문장도 반드시 같이 고쳐야 한다 (Edge 4 가 옳았다)

**모집단이 20 → 27 로 바뀐 원인 중 엔드포인트 추가는 없다.** 순전히 *"금전·재고 변경 API"* 의 경계 해석 차이다. 두 차례 독립 열거가 **같은 부류에서 경계 판단을 유보**했다:

- 요율·가격 설정(`PUT …/commission-rates/{sellerId}`, `PATCH /api/admin/products/{id}`) — 잔액이 아니라 **잔액을 산출하는 파라미터**
- 할인 규칙 CRUD(`POST|PUT|DELETE /api/promotions`) — `discountValue`·`maxDiscountAmount` 는 금액 결정 요소
- `POST /api/payments` — PENDING 행만 만들고 자금은 안 움직이나 **권위 금액을 확정**
- `POST /api/internal/orders/confirm-paid-stale` — 배치 스윕이라 "중복 요청" 정의 자체가 모호

⇒ 범위를 안 고치면 **다음 인구조사도 또 다른 숫자를 센다.** (A) 만으로는 이 티켓이 방어하려는 실패 모드가 재발한다.

---

## AC-2 제안 문장 (확정 형태 — 개정 task 가 그대로 붙여넣는다)

> **3. 중복 요청 방어**: 아래 *적용 범위* 에 해당하는 HTTP 엔드포인트는 중복 요청에 대해 **부수효과를 한 번만 남기고, 두 번째 요청을 결정적으로 4xx 로 거부하거나 최초 결과를 그대로 반환**해야 한다. 순차 재시도뿐 아니라 **동시 요청에서도** 같아야 하며, 그 판정은 **응답 시점에 확정**돼야 한다 — DB 제약이 커밋 시점에 터져 5xx 로 새는 경로는 이 조항을 만족하지 않는다.
>
> 허용되는 메커니즘은 넷이고 **우열은 없다.** 어느 것을 썼는지는 해당 서비스의 `architecture.md` 에 적는다.
>
> - **도메인 상태기계** — 재요청이 전이 불가 상태를 만나 4xx 로 거부된다.
> - **자연키 UNIQUE 제약** — 단, 위반이 응답 시점에 4xx 로 변환돼야 한다(`saveAndFlush` + catch, 또는 `DataIntegrityViolationException` 백스톱이 unique 위반을 409 로 매핑).
> - **전체 치환(absolute set)** — 요청 본문이 결과 상태를 완전히 결정할 때만. **증분(delta) 연산에는 적용할 수 없다.**
> - **클라이언트 측 멱등 키** — **서버가 재시도와 정당한 반복을 구별할 수 없을 때는 이 방식이 필수다**(부분 환불, 재고 증감처럼 동일한 두 요청이 둘 다 의도된 것일 수 있는 경우). 키를 받는 엔드포인트는 키를 **필수로** 요구한다. **선택적 키는 이 조항을 만족하지 않는다** — 키 없는 경로가 무방비로 남기 때문이다.
>
> **적용 범위**: 다음 중 하나를 영속적으로 변경하는 HTTP 엔드포인트. (a) 금전 잔액·지급액·환불액, (b) 재고 수량·예약, (c) 쿠폰 발급 수량·사용 상태, (d) **위 값들을 산출하는 파라미터**(할인율·수수료율·판매가). 읽기 전용 엔드포인트와 투영(projection) 재구축은 제외한다. **Kafka 컨슈머 경로는 이 조항이 아니라 D1·D2(Saga·Outbox)의 재처리 규약을 따른다.**

---

## AC-3 제안 문장을 27건 전부에 대조

| 분류 | 건수 | 새 문장 하에서 |
|---|---|---|
| ENFORCED | 4 | **통과** — 키 필수 + `saveAndFlush` 중재자 |
| NATURAL-KEY (상태기계) | 12 | **통과** — `save` 이전 인메모리 throw, 응답 시점 확정 |
| NATURAL-KEY (유니크 제약) | 5 | **통과** — `saveAndFlush`+catch 또는 BE-542 백스톱이 409 로 매핑 |
| NATURAL-KEY (전체 치환) | 2 | **통과** — `PUT /api/promotions/{id}`, `PATCH /api/admin/products/{id}` |
| OPTIONAL | 2 | **🔴 거부** |
| NONE | 2 | **🔴 거부 1 / 통과 1** |

**거부되는 3건 (= 이 문장이 규칙인 이유, Edge 2):**

1. `POST /api/orders` — 선택적 키. *"선택적 키는 만족하지 않는다"* 조항에 직접 걸린다.
2. `POST /api/shippings/carrier-webhook` — blank `deliveryId` 를 최초로 취급하는 우회로. 같은 조항.
3. `POST /api/promotions` — 호출마다 새 `promotionId` 를 발급하므로 전체 치환이 아니다. 재요청 시 **두 번째 할인 규칙**이 산다.

`PATCH /api/admin/products/{id}` 는 절대값 치환이라 **의도적으로 통과**시켰다. 이걸 거부하도록 쓰면 정상적인 REST 갱신 전부를 위반으로 만든다 — Edge 2 의 반대편 과오다.

> **AC-3 의 의미**: 새 문장은 27건 중 **3건을 거부한다.** 아무것도 거부하지 못하는 서술이 아니고, 옳은 코드를 영구 위반으로 남기지도 않는다. 거부되는 3건은 전부 **실재하는 결함**이며 아래 파생 티켓으로 분리했다.

---

## AC-4 후속 개정 task 의 형태

**`TASK-BE-544`(가칭) — ADR-002 Decision-3 개정.**

- **ACCEPT 게이트**: `ADR-002` 는 `Accepted` 다. `platform/architecture-decision-rule.md` § The ACCEPTED Gate 상 **맨 "진행" 으로는 열리지 않으며 self-ACCEPT 도 금지**다. 사용자의 **정확형 의사표시**가 선행돼야 한다.
- **범위**: 위 § AC-2 블록을 `ADR-002` Decision 3 자리에 치환. 문장이 확정형이므로 개정 task 는 **재작성이 아니라 적용**이다.
- **동반 갱신**: `Consequences` 에 "메커니즘 선택은 서비스 `architecture.md` 에 기록" 한 줄 + 개정일자 노트.
- **금지**: 개정 task 가 코드를 건드리는 것. 거부되는 3건은 별 티켓이다.
- **선행 확인**: 착수 시 위 27건 분포를 **다시 센다.** 이 절의 숫자도 2026-07-20 스냅샷이며 출처가 아니라 가설이다.

---

## 파생 티켓 (이 task 는 코드 변경 0)

| 후보 | 내용 | 심각도 |
|---|---|---|
| `POST /api/promotions` 중복 방어 부재 | 재요청이 **두 번째 할인 규칙**을 만든다. 인구조사 원본이 promotion *발급* 은 셌지만 *생성* 은 못 셌다 | 실 결함 |
| `POST /api/orders` 키 선택적 | D3 원래 취지에 가장 정면으로 걸리는 잔여 구멍. `TASK-BE-430` 의 비채택 결정과 상호작용하므로 재논의 필요 | 판정 필요 |
| carrier-webhook blank-key 우회 | `registerIfFirst` 가 광고하는 PK dedupe 를 빈 키가 통과. 상태기계가 유일한 방어 | 실 결함 |
| `PaymentRefundService.refundPayment(String)` | **Kafka 컨슈머 자금 유출 경로**, 가드가 `status == REFUNDED` 조기반환 하나뿐. 새 문장은 컨슈머를 D1·D2 로 보내므로 **그쪽 규약으로 별도 감사 필요** | 미측정 축 |

마지막 행이 이 재측정의 사각지대다 — 모집단을 `API` 로 한정한 결과 **컨슈머 쓰기 경로는 한 건도 세지 않았다.** `product-service` 의 재고 예약(`StockReservation`)은 HTTP 엔드포인트가 **아예 없고** 컨슈머만 쓴다. 재고 멱등성 감사는 REST 층이 아니라 컨슈머 층을 봐야 한다.

---

## Failure Scenarios

- **F1 — 이 task 가 조용히 ADR 을 고친다.** ACCEPT 게이트 위반. 제안까지가 범위다.
- **F2 — 재측정 없이 20/13/6 을 그대로 인용.** 이 저장소가 반복해 대가를 치른 실패(모집단 물려받기). BE-535/536 이 방금 분포를 바꿨으므로 특히 위험하다.
- **F3 — 아무것도 거부하지 않는 문구를 제안.** Edge 2 참조. 통과하기 쉬운 문장은 규칙이 아니다.
- **F4 — Edge 3 을 건너뛴다.** 13 건을 "이미 안전" 으로 분류만 하고 실제 거동을 안 보면, BE-536 이 발견한 것과 같은 *제약은 있는데 500 으로 새는* 사례를 놓친다.

---

## Test Requirements

- 문서 task 이므로 자동 테스트 없음.
- 대신 **재측정 산출물**: 20(±) 엔드포인트 전체 목록 + 각각의 분류 + 분류 근거(파일:라인). 표로 남긴다.
- Edge 3 표본 검증 결과(NATURAL-KEY 중 최소 3 건의 실제 중복 거동).

---

## Definition of Done

- [x] AC-0 인구조사 전수 재측정 (탐지식 known-positive 자기검증 선행) — **27건**, 분포 `4/2/19/2`
- [x] (A)/(B)/(C) 판정 + 근거 — **(A) + (C)**, (B) 기각 근거 기록
- [x] (A)/(C) 시 제안 문장 확정 형태 — § AC-2
- [x] 제안 문장을 **27** 개 전부에 대조 (AC-3) — 3건 거부
- [x] **NATURAL-KEY 표본 실거동 확인 (Edge 3) — 2026-07-20 선행 수행 완료. 단 § AC-0 이 그 결론을 무효화했다(BE-542 배선 완료).**
- [x] 후속 개정 task 형태 기록 (ACCEPT 게이트 절차 포함) — § AC-4
- [x] 문서만 — 코드·ADR 본문 diff 0

---

## Notes

- **분량**: medium. 파일은 거의 안 바뀌지만 20 엔드포인트 재측정이 실질이다.
- **dependency**: `선행` = `TASK-BE-535`(done) · `TASK-BE-536`(done) — 둘이 분포를 바꿨고 근거 데이터를 남겼다. `후속` = ADR-002 개정 task(ACCEPT 게이트 통과 후).
- **형제**: `TASK-MONO-444` — 같은 "선언 ↔ 진실" 클래스, 반대 방향(문서가 코드에 없는 것을 약속). 둘을 같이 읽으면 축이 보인다.
- **이 task 가 방어하는 실패 모드**: **어떤 구현도 만족하지 못하는 요구는 규칙이 아니라 소음이다.** 문자 그대로 읽으면 13 개의 옳은 엔드포인트가 영구 위반이고, 그래서 아무도 그 문장을 문자 그대로 읽지 않으며, 그 결과 **진짜 위반(NONE 6 건)이 2026-07-20 까지 발견되지 않았다.** 지켜지지 않는 규칙은 지켜지는지 아무도 확인하지 않는 규칙이 된다.
