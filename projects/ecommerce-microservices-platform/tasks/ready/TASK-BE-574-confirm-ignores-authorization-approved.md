# Task ID

TASK-BE-574

# Title

`PaymentConfirmService.confirm` 이 `PaymentAuthorization.approved()` 를 읽지 않는다 — 거절을 반환하는 게이트웨이는 성공으로 기록된다

# Status

ready

# Owner

backend

# Task Tags

- code
- test

---

# 배경 — `TASK-BE-572` AC-0 이 발견했다

데모 mock PG 를 만들면서 결제 확정 경로를 읽다가 나온 것이다. **결함 자체는 데모와 무관하다.**

`PaymentConfirmService.confirm` 은 게이트웨이 응답의 승인 플래그를 **한 번도 보지 않는다**:

```java
pgResult = paymentGateway.verify(new PaymentVerificationRequest(paymentKey, amount, "KRW", orderId));
// ... (취소 레이스 가드) ...
payment.confirm(paymentKey, pgResult.paymentMethod(), pgResult.receiptUrl());   // ← approved() 미검사
```

`PaymentAuthorization` 은 `approved` 를 들고 있고 `PaymentAuthorization.declined()` 라는 팩토리도 있다.
그런데 ecommerce 에서 그 값을 읽는 코드는 **없다** — `.approved()` 호출처를 전수로 세면 전부
fan-platform 이다(`SubscribeUseCase` · `RenewMembershipUseCase` · `AutoRenewMembershipsUseCase` ·
`WebhookReconcileUseCase`).

## 오늘 터지지 않는 이유 (= 잠복이라는 뜻이지 안전하다는 뜻이 아니다)

ecommerce 의 현행 게이트웨이 셋 다 `declined()` 를 반환하지 않는다:

| 게이트웨이 | 거절 표현 |
|---|---|
| `TossPaymentsAdapter` (실 PG) | `PgConfirmFailedException` **예외** (4xx 번역) |
| `StandaloneConfig` 스텁 | 항상 승인 |
| `DemoPaymentGatewayConfig` (BE-572) | 예외 — **이 사실 때문에 일부러 그렇게 만들었다** |

즉 **거절을 값으로 표현하는 게이트웨이가 하나라도 추가되는 순간** 그 결제는 돈을 받지 않은 채
`COMPLETED` 로 기록되고 `PaymentCompleted` 가 발행되어 주문 확정·배송·정산이 전부 진행된다.

`libs/payment-core` 는 공유 라이브러리이고 fan 은 같은 포트를 **값으로** 쓰고 있으므로, 다음
게이트웨이 작성자가 fan 쪽 관례를 따르는 것은 자연스럽다. 그게 이 결함의 도화선이다.

---

# Goal

`confirm` 이 승인 여부를 명시적으로 판정한다 — 값으로 온 거절이 성공으로 기록되지 않는다.

---

# Scope

## In Scope

- `PaymentConfirmService.confirm` 의 승인 판정 추가와 그때의 상태 전이 결정
- 같은 문제가 있는 **형제 호출처 전수 확인** (`PaymentProcessingService` 등 `verify` 를 부르는 곳)
- 회귀 방지 테스트

## Out of Scope

- 게이트웨이 어댑터 변경 (Toss 의 예외 계약은 그대로 둔다)
- 결제 상태 머신 재설계
- 데모 프로파일 — `TASK-BE-572` 에서 완료

---

# Acceptance Criteria

- [ ] **AC-0 (착수 = 재측정)** — 위 표를 그대로 믿지 않는다. `verify` 호출처와 `.approved()`
      호출처를 **다시 전수로 세고**, `declined()` 를 반환할 수 있는 경로가 정말 없는지 확인한다
- [ ] **AC-1** — 값으로 온 거절(`approved=false`)이 `COMPLETED` 로 기록되지 않는다.
      어느 상태로 갈지(FAILED? 예외?)는 Toss 의 예외 경로와 **같은 결과**가 되도록 맞추고 근거를 적는다
- [ ] **AC-2** — 그 판정이 없으면 RED 가 되는 테스트를 추가하고, 판정을 제거해 **실제로 RED 가
      되는지 확인**한다(네거티브)
- [ ] **AC-3 (형제 파리티)** — `verify` 를 부르는 다른 호출처도 같은 문제인지 확인하고 결과를 기록한다.
      **선행 숫자를 물려받지 말 것**
- [ ] **AC-4** — 기존 결제 테스트 전건 통과(단위 + 통합). 실 Toss 경로 동작 무변경

---

# Related Specs

- `projects/ecommerce-microservices-platform/specs/services/payment-service/architecture.md`
- `docs/adr/ADR-MONO-056-payment-gateway-abstraction.md`
- `libs/payment-core/src/main/java/com/example/libs/payment/PaymentAuthorization.java`

# Related Contracts

- `specs/contracts/events/` — `PaymentCompleted` 는 **성공한 결제에만** 발행되어야 한다

---

# Target Service

- `payment-service`

---

# Edge Cases

- `approved=false` 인데 `vendorPaymentRef` 가 채워져 있을 수 있다 — 승인 판정은 플래그가 기준이다
- 취소 레이스(post-capture auto-refund) 가드와 순서가 얽힌다. 값-거절은 **capture 자체가 없었다**는
  뜻이므로 자동환불을 부르면 안 된다
- fan 은 같은 포트를 값으로 쓰고 있다 — 공유 라이브러리의 계약을 바꾸는 방향은 blast radius 가 크다

---

# Failure Scenarios

- **예외 경로와 다른 상태로 보낸다** → 같은 "거절" 인데 표현 방식에 따라 주문 상태가 갈린다
- **테스트가 픽스처로만 참** → 프로덕션에서 공존 불가능한 입력을 단언하면 아무것도 증명하지 않는다

---

# Test Requirements

- 단위: 값-거절을 반환하는 게이트웨이 스텁 → `COMPLETED` 아님 + `PaymentCompleted` 미발행
- 네거티브: 판정 제거 시 위 테스트 RED
- 회귀: 기존 payment-service `test` + `integrationTest` 전건

---

# Definition of Done

- [ ] 구현 + 테스트 (네거티브 확인 포함)
- [ ] 형제 호출처 파리티 기록
- [ ] Ready for review
