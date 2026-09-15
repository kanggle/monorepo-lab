# Task ID

TASK-INT-026

# Title

쿠폰 할인이 결제 금액에 닿지 않는다 — 재현하고, 호출 주체를 정하고, 고친다

# Status

in-progress

# Owner

integration

# Task Tags

- api
- event
- code
- test

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

If any section is missing or incomplete, this task must not be implemented.

---

# Goal

체크아웃에서 쿠폰을 고른 구매가 **결제 승인까지 성공**하고, 그 결과가 서버에서 일치해야 한다:
토스에 요청한 금액 = payment-service 가 기억하는 금액 = 서버가 계산한 할인 후 금액, 그리고 그 쿠폰은 `USED` 이다.

🔴 **이 결함은 코드를 읽어 추론한 것이고 아직 재현하지 않았다** (2026-09-15, 구매 흐름 정리 중 발견). 그래서 첫 AC 는 재현이고, 재현되지 않으면 이 티켓은 거기서 닫힌다.

## 코드에서 읽은 것

| # | 사실 | 위치 |
|---|---|---|
| 1 | 할인액은 쿠폰 목록(`GET /api/coupons/me`)을 받아 **브라우저가 계산**한다 | `apps/web-store/src/features/coupon/model/use-coupon-selection.ts` → `lib/calculate-discount.ts` |
| 2 | `applyCoupon()`(→ `POST /api/coupons/{couponId}/apply`)은 **정의만 있고 호출하는 곳이 없다** | `apps/web-store/src/features/coupon/api/coupon-api.ts:19` (web-store 전체 grep 결과 호출 0건) |
| 3 | 주문 요청에 쿠폰 필드가 없다 — `items` + `shippingAddress` 뿐 | `apps/order-service/.../presentation/dto/PlaceOrderRequest.java`, `specs/contracts/http/order-api.md` § POST /api/orders |
| 4 | 토스에는 `totalAmount − discountAmount` 를 보낸다 | `apps/web-store/src/features/checkout/ui/CheckoutForm.tsx:18,75` |
| 5 | 결제 레코드 금액은 `OrderPlaced.totalPrice`(할인 전)로 만들어진다 | `apps/payment-service/.../adapter/in/event/OrderPlacedEventConsumer.java:35` |
| 6 | 승인 때 요청 금액이 PENDING 금액과 다르면 `400 AMOUNT_MISMATCH` | `apps/payment-service/.../application/service/PaymentConfirmService.java:57` |

⇒ 추론: 쿠폰을 고르면 `confirm(할인 후 금액) ≠ PENDING(할인 전 금액)` 이라 승인이 거절된다. 그리고 쿠폰은 서버에서 한 번도 사용 처리되지 않으므로 `CouponUsed` 도 발행되지 않는다.

## 스펙 충돌 — 구현 전에 결정이 필요하다

같은 질문(«쿠폰 적용은 누가 부르나»)에 스펙이 두 방향으로 답한다:

| 쪽 | 문장 | 위치 |
|---|---|---|
| order-service 가 promotion-service 를 **동기 호출** | "Apply coupons at order placement — synchronous HTTP from `order-service`" | `specs/services/promotion-service/overview.md:22` |
| 〃 | "order-service communicates coupon application via synchronous HTTP call to promotion-service" | `specs/services/promotion-service/architecture.md:106` |
| 〃 | "Called by order-service during order placement." | `specs/contracts/http/promotion-api.md:258` |
| 〃 | "`promotion-service` (optional sync HTTP for coupon apply)" | `specs/services/order-service/overview.md:68` |
| order-service 는 **나가는 HTTP 호출이 없다** | "no outbound service-to-service HTTP calls initiated" | `specs/services/order-service/dependencies.md:24` |

코드는 뒤쪽과 일치한다(order/payment/promotion/shipping/product-service 에 `RestClient`·`WebClient`·`FeignClient`·`RestTemplate` 0건). 곁가지로 `promotion-service/overview.md:36` 은 경로를 `/api/coupons/{code}/apply` 로 적었지만 실제 경로 변수는 `couponId` 다.

### 선택지

- **ⓐ order-service 가 주문 생성 중 promotion-service `apply` 를 동기 호출** — promotion 스펙 네 곳과 일치. `PlaceOrderRequest` 에 `couponId` 를 받고, 서버가 계산한 할인 후 금액을 `totalPrice` 로(또는 할인 전/할인액/최종액을 나눠) `OrderPlaced` 에 싣는다. 대가: order-service 에 첫 나가는 HTTP 호출이 생기고(`dependencies.md:24` 개정), «쿠폰은 USED 인데 주문 저장은 실패» 한 고아를 되돌리는 보상이 필요하다.
- **ⓑ 이벤트로 — order-service 는 `couponId` 만 기록하고 promotion-service 가 `OrderPlaced` 를 구독해 사용 처리** — 나가는 HTTP 없음. 대가: 할인액을 누가 **결제 전에** 확정하나가 비어 있다(promotion 이 비동기로 계산하면 payment 가 PENDING 금액을 만들 때 아직 모른다). 이미 사용된 쿠폰으로 할인받은 결제가 생길 수 있어 보상이 결제 뒤로 밀린다.
- **ⓒ 당장은 쿠폰 선택을 결제 경로에서 막는다** — 결함만 차단(체크아웃의 `CouponSelector` 비노출/비활성). 기능 설계는 후속 티켓.

🔵 분석자 추천은 **ⓐ** 다 — 스펙 다섯 곳 중 네 곳이 이미 그쪽이고, 금액의 권위를 결제 전에 서버가 쥐는 유일한 안이다. 🔴 **추천은 결정이 아니다.** AC-1 이 소유자 선택을 기록하기 전에는 구현을 시작하지 않는다(스펙 충돌 = HARDSTOP-06).

### AC-1 결정 기록 (2026-09-15 UTC)

소유자 답, 원문 그대로: **「ⓐ 동기 호출」**

단서(rider)는 붙지 않았다. 이 결정에서 구현이 추가로 정한 것 — 결정의 일부가 아니라 구현 판단이며 스펙에 적었다:

- `totalPrice` 는 **결제할 금액(할인 반영 후)** 을 뜻한다. 할인 전 금액·할인액을 따로 싣는 대신 `couponId`·`discountAmount` 를 **덧붙였다**(additive). 쿠폰 없는 주문의 `OrderPlaced` 는 의미가 그대로다.
- 보상 = order-service 가 apply **직전에** 트랜잭션 동기화를 걸어, 배치가 커밋되지 않으면 promotion-service 의 새 내부 경로 `POST /api/internal/coupons/{couponId}/release` 를 부른다. release 는 **그 orderId 로 쓰인 쿠폰만** 되돌린다. 이 경로는 게이트웨이 라우트가 없고, 있어서는 안 된다(사용자가 부를 수 있으면 할인받은 주문의 쿠폰을 풀어 재사용한다).
- promotion-service `apply` 는 **같은 orderId 에 대해 멱등**이 됐다(재시도 안전). 다른 주문이면 여전히 `COUPON_ALREADY_USED`.
- 할인 후 1원 미만이면 `422 COUPON_NOT_APPLICABLE`(PG 는 0원을 청구하지 못한다). promotion-service 무응답이면 `503 COUPON_SERVICE_UNAVAILABLE` — 할인 없이 주문을 만들지 않는다.
- 남는 위험(닫지 않음, 기록만): apply 가 **release 보다 늦게** promotion-service 에서 커밋되면 쿠폰이 존재하지 않는 주문에 `USED` 로 남는다.

---

# Scope

## In Scope

- AC-0 재현과 그 기록
- 소유자 결정(ⓐ/ⓑ/ⓒ) 기록
- 결정에 맞춘 **스펙·계약 선행 정렬** — order-service `dependencies.md`·`overview.md`, promotion-service `overview.md`·`architecture.md`, `promotion-api.md`, `order-api.md`, `order-events.md`(OrderPlaced 금액 필드의 의미)
- 선택안 구현: web-store 체크아웃, order-service, promotion-service, payment-service 중 선택안이 요구하는 곳
- 테스트(아래 Test Requirements)

## Out of Scope

- 쿠폰 코드 직접 입력, 한 주문에 쿠폰 여러 장
- 부분 환불 시 쿠폰 복원 정책
- 정산 수수료를 할인 전·후 어느 금액에 매길지의 **변경** — 이 티켓은 영향만 기록하고(Edge Cases 참고) 변경이 필요하면 후속 티켓을 기안한다
- 상품 단가를 서버가 재검증하지 않는 문제(`verify-order-lines.ts` 머리 주석) — 별개 결함

---

# Acceptance Criteria

- [ ] **AC-0 (재현 먼저)** — «쿠폰 선택 → 주문 생성 → 할인 후 금액으로 `POST /api/payments/confirm`» 을 실제로 돌려 `400 AMOUNT_MISMATCH` 가 나는지 측정하고, 명령·응답을 이 파일에 적는다. 로컬 스택이든 payment-service 통합 테스트든 좋다. 🔴 재현되지 않으면 위 표의 **어느 사실이 틀렸는지**를 적고, 나머지 AC 없이 이 티켓을 닫는다.
- [ ] **AC-1 (결정)** — 소유자가 고른 선택지(ⓐ/ⓑ/ⓒ, 또는 다른 안)를 **소유자의 말 그대로** 이 파일에 적는다. 분석자 추천을 결정으로 옮겨 적지 않는다.
- [ ] **AC-2 (스펙 먼저)** — 스펙 충돌 표의 다섯 문장과 `promotion-service/overview.md:36` 경로 표기가 결정과 **같은 방향**을 가리킨다. 반대 문장이 한 곳이라도 남으면 미완료. 코드 변경은 이 AC 이후 커밋에만 들어간다.
- [ ] **AC-3** — 쿠폰을 고른 주문에서 `토스 요청 금액 == payment PENDING 금액 == 서버가 계산한 할인 후 금액` 이고 승인이 성공한다. 테스트로 고정한다. *(ⓒ 선택 시: 체크아웃에서 쿠폰을 고를 수 없고, 주문 요약·결제 버튼 금액에 할인이 나타나지 않는다.)*
- [ ] **AC-4** — 쿠폰 없는 주문은 요청·`OrderPlaced` 페이로드·결제 금액이 변경 전과 같다(회귀 테스트).
- [ ] **AC-5** — 결제까지 끝난 쿠폰 주문의 쿠폰은 `USED` 이고 그 `orderId` 를 가진다. 그 주문을 취소하면 기존 `order.order.cancelled` 복원 경로로 `ISSUED` 가 된다. *(ⓒ 선택 시 해당 없음 — 이유 기록.)*
- [ ] **AC-6** — 사용됨·만료·남의 쿠폰으로는 할인된 결제가 만들어지지 않고, 사용자에게 무엇이 문제인지 보인다(`COUPON_ALREADY_USED` / `COUPON_EXPIRED` / `COUPON_NOT_OWNED`). *(ⓒ 선택 시 해당 없음.)*
- [ ] **AC-7** — 브라우저가 계산한 할인액을 서버가 금액으로 쓰지 않는다. 브라우저 계산은 표시용으로만 남거나 사라진다. *(ⓒ 선택 시 해당 없음.)*

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read `PROJECT.md`, then load `rules/common.md` plus any `rules/domains/<domain>.md` and `rules/traits/<trait>.md` matching the declared classification. Unknown tags are a Hard Stop per `CLAUDE.md`.

- `rules/traits/transactional.md` (금액·보상·멱등성)
- `specs/services/order-service/architecture.md`, `dependencies.md`, `overview.md`
- `specs/services/promotion-service/architecture.md`, `overview.md`, `dependencies.md`
- `specs/services/payment-service/architecture.md`
- `specs/services/web-store/` (체크아웃)
- `specs/use-cases/cart-and-order.md`, `specs/use-cases/payment-and-refund.md`
- `specs/features/order-processing.md`, `specs/features/payment-processing.md`

# Related Skills

- `.claude/skills/INDEX.md` 에서 backend(서비스 간 HTTP 클라이언트·보상) / frontend(api-client) / messaging 항목 — 선택안에 해당하는 것만

---

# Related Contracts

- `specs/contracts/http/order-api.md` § POST /api/orders
- `specs/contracts/http/promotion-api.md` § POST /api/coupons/{couponId}/apply
- `specs/contracts/http/payment-api.md` § POST /api/payments/confirm
- `specs/contracts/events/order-events.md` § OrderPlaced
- `specs/contracts/events/promotion-events.md` § CouponUsed
- `specs/contracts/events/settlement-subscriptions.md` (영향 확인)

---

# Participating Components

- `apps/web-store` — 체크아웃 쿠폰 선택·주문 요청·결제 금액
- `apps/gateway-service` — 새 요청 필드/경로가 생기면 라우팅 확인
- `apps/order-service` — 주문 생성, `OrderPlaced` 금액
- `apps/promotion-service` — 쿠폰 적용·사용 처리·복원
- `apps/payment-service` — PENDING 금액과 승인 비교
- `apps/settlement-service` — 영향 확인만

---

# Trigger

사용자가 체크아웃 화면에서 쿠폰을 고르고 「결제하기」를 누른다.

---

# Expected Flow

ⓐ 가 선택됐을 때의 예시 흐름이다. 다른 안이면 AC-1 에서 이 절을 고쳐 쓴다.

1. web-store 가 `POST /api/orders` 에 `couponId` 를 함께 보낸다.
2. order-service 가 promotion-service `POST /api/coupons/{couponId}/apply` 를 호출해 할인액·최종액을 받는다. 실패하면 주문을 만들지 않고 사유를 돌려준다.
3. order-service 가 할인이 반영된 금액으로 주문을 `PENDING` 저장하고 `OrderPlaced` 를 발행한다.
4. payment-service 가 그 금액으로 결제 PENDING 을 만든다.
5. web-store 는 **주문 응답의 금액**으로 토스 결제를 요청한다(브라우저 계산값이 아니라).
6. 승인 금액이 일치해 `PaymentCompleted` 이후 기존 흐름이 이어진다.

---

# Edge Cases

- **멱등 재시도** — 같은 `Idempotency-Key` 로 주문을 다시 보내면 원래 주문을 돌려줘야 하고, 쿠폰이 두 번 적용되거나 `COUPON_ALREADY_USED` 로 재시도가 실패하면 안 된다.
- **쿠폰은 USED, 주문 저장은 실패**(ⓐ) — 고아 쿠폰을 되돌리는 경로가 필요하다.
- **결제하지 않고 타임아웃 취소** — `OrderStuckDetector` 의 `PAYMENT_TIMEOUT` 취소 뒤 쿠폰이 `ISSUED` 로 돌아온다.
- **할인액 ≥ 주문 금액** — 0원 결제가 된다. 토스 결제 경로가 0원을 받는지 확인하고 정책을 정한다.
- **체크아웃 중 쿠폰 만료·최소 주문금액 미달** — 화면 표시와 서버 판정이 갈리면 서버가 이긴다.
- **데모 결제 모드**(`use-toss-payment.ts` 의 demo 분기) — 같은 금액이 쓰이는지.
- **정산** — settlement-service 는 `OrderPlaced` 의 `unitPrice × quantity` 로 gross 를 잡고, 환불 역분개는 «captured 합계 == 적립 gross» 를 전제한다(`settlement-subscriptions.md` § Proportional clawback rule). 할인으로 captured < gross 가 되면 이 전제가 깨진다. 이 티켓에서는 **영향을 측정해 기록**하고, 변경이 필요하면 후속 티켓을 기안한다.

---

# Failure Scenarios

- promotion-service 가 죽었거나 타임아웃(ⓐ) — 쿠폰을 빼고 조용히 진행하지 않는다. 주문 생성을 실패시키고 사용자에게 다시 시도하라고 알린다.
- 쿠폰 적용 거절(404/422) — 사유별 메시지. 할인 없는 금액으로 몰래 바꿔 결제하지 않는다.
- `OrderPlaced` 금액 필드 의미 변경이 기존 구독자(payment / product / settlement / notification)의 가정을 깬다 — 계약 문서에 의미를 명시하고 구독자별로 확인한다.
- 승인 뒤 주문이 취소돼 환불이 필요한 쿠폰 주문 — 환불 금액이 할인 후 금액이다.

---

# Test Requirements

- AC-0 재현 테스트(수정 전 빨강 → 수정 후 초록으로 남긴다)
- order-service: 쿠폰 있는/없는 주문 생성 단위·통합 테스트, promotion 호출 실패·타임아웃 테스트(ⓐ)
- promotion-service: 적용 멱등성, 사용됨·만료·소유자 불일치
- payment-service: 할인 후 금액으로 PENDING 생성 → 승인 성공
- web-store: 체크아웃이 서버 금액으로 결제를 요청하는 테스트, 쿠폰 거절 메시지
- 계약 변경이 있으면 계약 테스트

---

# Definition of Done

- [ ] AC-0 재현 기록
- [ ] AC-1 소유자 결정 기록
- [ ] 스펙·계약 선행 정렬
- [ ] 구현 완료
- [ ] 테스트 추가·통과
- [ ] 정산 영향 기록(필요 시 후속 티켓 기안)
- [ ] Ready for review

---

분석=Opus 5 / 구현 권장=Opus — 금액·계약·서비스 경계가 함께 바뀌고 보상 설계가 필요하다. ⓒ 가 선택되면 Sonnet 으로 충분하다.

---

# 진행 기록 (in-progress 작업 문서)

## 정산 영향 — 측정 (2026-09-15 UTC)

- settlement-service 는 수수료 기준(gross)을 **`OrderPlaced.items[]` 의 `unitPrice × quantity`** 로 잡는다 — `apps/settlement-service/.../infrastructure/event/OrderPlacedSnapshotConsumer.java:72`. `totalPrice` 는 읽지 않는다.
- ⇒ 쿠폰 주문에서 수수료는 **할인 전 금액**에 매겨지고, 셀러 순수익은 할인을 전혀 부담하지 않는다. 할인은 사실상 플랫폼(수수료를 덜 받지 않음) 또는 아무도 장부에 기록하지 않는 돈이 된다.
- 환불 역분개는 «captured 합계 == 적립 gross» 를 전제한다(`settlement-subscriptions.md` § Proportional clawback rule). 쿠폰 주문은 captured(`totalPrice`) < gross 라서 **부분 환불의 비례 역분개가 과소**하게 계산된다. 마지막 환불(`fullyRefunded=true`)이 남은 전액을 되돌리므로 **완전 환불로 끝나면 주문 단위로는 0 이 맞는다.**
- 🔴 이 티켓은 정산을 바꾸지 않는다(Out of Scope). **«쿠폰 할인은 누가 부담하나 — 플랫폼 / 셀러 / 비례»** 는 소유자 결정이 필요한 후속 질문이다. 후속 티켓 기안 대상.

## 로컬 검증 (2026-09-15 UTC, worktree `int-026-impl`)

| 무엇 | 명령 | 결과 |
|---|---|---|
| order·promotion 단위/슬라이스/계약 | `./gradlew :…:order-service:test :…:promotion-service:test --continue` | `BUILD SUCCESSFUL`. 새 클래스 실행 확인(XML): `OrderPlacementServiceCouponTest` 9, `OrderCouponDiscountTest` 8, `PromotionServiceCouponClientTest` 7, `CouponApplyReplayAndReleaseTest` 5, `CouponCommandServiceReplayReleaseTest` 4, `InternalCouponControllerSliceTest` 2 — 실패 0 |
| web-store 타입 | `npx tsc --noEmit` | rc=0 |
| web-store lint | `npx next lint` | rc=0 |
| 에러 코드 등록부 | `check-error-code-registry.sh`, `check-domain-error-code-registry.sh` | rc=0 / rc=0 |

## `OrderPlaced` 구독자 — 덧붙인 필드가 소비를 깨지 않나 (2026-09-15 UTC)

| 구독자 | 근거 | 판정 |
|---|---|---|
| settlement-service | `OrderPlacedEvent` 에 `@JsonIgnoreProperties(ignoreUnknown = true)` | 안전 |
| product-service | `ReservationInboundEvents.OrderPlacedMessage` 에 `ignoreUnknown = true` | 안전 |
| notification-service | DTO 가 `orderId/userId/totalPrice` 만 선언하는데 **지금도** 와이어엔 `items`·`shippingAddress` 가 실려 온다. 커스텀 ObjectMapper 없음(Spring Boot 기본 = 모르는 필드 무시) | 이미 모르는 필드를 받고 있다 ⇒ 안전 |
| payment-service | 같은 논리 — DTO `OrderItem` 에 `sellerId` 가 없는데 와이어엔 이미 있다. 커스텀 ObjectMapper 없음 | 안전 |

🔵 `POST /api/orders` 응답을 쓰는 다른 곳: load-tests 는 `orderId` 만 읽고, `tests/e2e` 는 쿠폰 없이 주문한다. platform-console 은 관리자 조회 경로만 부른다.

**미측정 (로컬에서 못 잰 것):** web-store vitest(Node 24 에서 vitest 4 기동 불가 — CI Node 20 이 권위), order-service Testcontainers 통합 테스트(로컬 Docker 차단 — CI), 실 스택 종단(쿠폰 선택 → 토스 승인 성공)은 돌리지 않았다.
