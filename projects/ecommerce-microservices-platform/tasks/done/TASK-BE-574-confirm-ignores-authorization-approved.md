# Task ID

TASK-BE-574

# Title

`PaymentConfirmService.confirm` 이 `PaymentAuthorization.approved()` 를 읽지 않는다 — 거절을 반환하는 게이트웨이는 성공으로 기록된다

# Status

done

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

# 🟢 착수 실측 (2026-08-06)

## AC-0 — 재측정: 티켓의 표는 맞았고, **한 줄이 빠져 있었다**

`PaymentGatewayPort.verify` 호출처 전수(테스트 제외):

| 호출처 | `approved()` 를 읽는가 |
|---|---|
| ecommerce `PaymentConfirmService:63` | **아니오** ← 이 결함 |
| fan `SubscribeUseCase:122` | 예 (`if (!result.approved())`) |
| fan `RenewMembershipUseCase:106` | 예 |
| fan `AutoRenewMembershipsUseCase:159` | 예 (2곳) |
| fan `WebhookReconcileUseCase:101` | **로그로만** — 문서화된 no-op 멱등 읽기라 정상 |

🔴🔴 **티켓이 "다음 게이트웨이가 추가되는 순간" 이라고 쓴 도화선은 이미
저장소에 있다.** `libs/payment-portone/PortOnePaymentAdapter` 가 `verify` 에서
**9곳에서 `declined()` 를 반환**하고 클래스 javadoc 이 "this adapter **NEVER** throws for a
failed verification" 라고 못 박는다. 다만 ecommerce payment-service 의 `build.gradle` 은
`payment-core` + `payment-toss` 만 의존하므로 **아직 배선되지 않았을 뿐**이다.
도화선 길이는 `implementation project(':libs:payment-portone')` **한 줄**이고,
fan 은 이미 그 어댑터를 쓴다.

🔵 **포트가 이 소비자 의무를 명시하고 있었다** — `PaymentGatewayPort` javadoc:
"An implementation MAY **return declined()** … / MAY instead **throw** …
**A consumer wiring a specific adapter must handle that adapter's declared failure shape.**"
ecommerce 의 유일한 소비자가 두 shape 중 하나만 처리했다.

🔴 **미뤄 둔 수렴이 조용히 사라졌다** — 같은 javadoc 이 두 실패 계약의 통합을
`TASK-MONO-479`(membership) / `TASK-MONO-480`(payment) 로 **연기**한다고 적었는데, 둘 다
`tasks/done/` 이고 본문에 `declin`/`approv`/`converg` 가 **한 번도 나오지 않는다**.
연기된 통합이 수행되지 않은 채 티켓만 닫혔다. 이 티켓은 그 통합이 아니라 **소비자 측
방어**만 한다(포트 계약은 건드리지 않음 — blast radius).

## AC-3 — 형제 파리티: 티켓의 추측은 **틀렸다**

티켓은 "`PaymentProcessingService` 등" 을 의심했지만 그 클래스는 **게이트웨이를 주입하지
않는다**(`PaymentRepository` + `PaymentMetricRecorder` 뿐). ecommerce 전체에서
`PaymentGatewayPort` 를 주입하는 곳은 **`PaymentConfirmService` 하나뿐**이다.
`PaymentRefundService` 는 `RefundablePaymentGateway` 를 쓰는데 그 포트의 `refund` 는
**`void`** 라 무시할 인가 값 자체가 없다(예외 전용). ⇒ **형제 결함 없음.**
(선행 숫자를 물려받지 않고 다시 셌다.)

## AC-1 — 무엇으로 보낼지, 그리고 왜

**`PgGatewayUnavailableException` + 행 상태 미변경**(503, 재시도 가능).

근거는 `declined()` 비트가 **Toss 의 두 예외 클래스의 합집합**이라는 데 있다 — 포트는
"failed/forged/tampered/**unreachable**" 전부에 `declined()` 를 허용하고, PortOne 어댑터는
"a PortOne 4xx/5xx, a network error, or an unparsable body" 를 **모두** 같은 값으로 닫는다.
따라서 그 비트는 확정성을 담지 못한다.

두 오류의 비용이 대칭이 아니다:

- **확정 거절인데 PENDING 으로 둔다** → 재시도가 또 거절될 뿐. COMPLETED 아님, 돈 안 움직임.
- **일시 거절인데 FAILED 로 잠근다** → verify-model 벤더는 **고객이 이미 결제한 뒤**에
  verify 가 도는데, 주문이 죽은 채 수동 환불 대상이 된다.

⇒ 보수적인 쪽을 택했고, 그건 **이 메서드가 이미 불확정 응답에 적용하는 정책**이다
(세 줄 위: "PG actual state is unknown — DO NOT transition to FAILED. Propagate so the
`@Transactional` boundary rolls back and the user can idempotently retry"). 새 결과 shape 를
만들지 않았다. 이름이 "gateway unavailable" 인 것이 답을 준 게이트웨이에 어색하다는 비용은
**알고 지불했고**, 로그 문구로 운영자가 구별할 수 있게 했다("The gateway answered; it did
not fail to answer"). 확정성을 **증명할 수 있는** 어댑터는 `declined()` 대신
`PgConfirmFailedException` 을 던지면 된다 — 포트가 이미 그 shape 를 제공한다.

**배치**: 승인 판정을 post-capture 자동환불 가드 **앞**에 뒀다. 값-거절은 capture 가
없었다는 뜻이므로 PG 에 취소를 부르면 안 된다(Edge Case). 테스트가 순서를 단언한다.

## AC-2 — 네거티브 실제 확인

판정 블록을 **실제로 제거하고** 돌렸다 → 신규 3건 **전부 RED**:

```
TASK-BE-574: approved=false 인 값-거절은 COMPLETED 로 … FAILED
TASK-BE-574: 값-거절은 행을 FAILED 로 잠그지 않는다 …    FAILED
TASK-BE-574: 값-거절에서는 자동환불을 부르지 않는다 …     FAILED
```

복원 후 초록. 픽스처가 프로덕션에서 공존 가능한 입력인지도 확인했다 —
`PaymentAuthorization.declined()` 는 팩토리 메서드이고 PortOne 어댑터가 실제로 반환한다.

## AC-4 — 회귀

payment-service **단위 208건 · 통합 24건, 실패 0 · 스킵 0**(Testcontainers IT 실제 실행).
Toss 실 PG 경로는 예외 기반이라 이 분기에 **도달하지 않는다** — 동작 무변경.

## 🔴 함께 발견했으나 이 티켓에서 고치지 않은 것

`PgConfirmFailedException` 분기의 `payment.fail(); save(); throw e;` 는 `@Transactional`
에 `noRollbackFor` 가 없어 **던지는 순간 롤백된다** — 즉 "FAILED 로 잠근다" 는 주석의
주장이 실제로 커밋되는지 의심스럽다. 단위 테스트는 리포지터리를 목으로 두고 `save()`
**호출**만 단언하므로(`PaymentConfirmServiceTest:151` captor) 이 차이를 잡지 못한다.
**선행 동작이고 이 티켓의 범위 밖**이라 손대지 않았다 — 별도 티켓이 필요하다.

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

- [x] **AC-0 (착수 = 재측정)** — 위 표를 그대로 믿지 않는다. `verify` 호출처와 `.approved()`
      호출처를 **다시 전수로 세고**, `declined()` 를 반환할 수 있는 경로가 정말 없는지 확인한다
- [x] **AC-1** — 값으로 온 거절(`approved=false`)이 `COMPLETED` 로 기록되지 않는다.
      어느 상태로 갈지(FAILED? 예외?)는 Toss 의 예외 경로와 **같은 결과**가 되도록 맞추고 근거를 적는다
- [x] **AC-2** — 그 판정이 없으면 RED 가 되는 테스트를 추가하고, 판정을 제거해 **실제로 RED 가
      되는지 확인**한다(네거티브)
- [x] **AC-3 (형제 파리티)** — `verify` 를 부르는 다른 호출처도 같은 문제인지 확인하고 결과를 기록한다.
      **선행 숫자를 물려받지 말 것**
- [x] **AC-4** — 기존 결제 테스트 전건 통과(단위 + 통합). 실 Toss 경로 동작 무변경

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

- [x] 구현 + 테스트 (네거티브 확인 포함)
- [x] 형제 호출처 파리티 기록
- [x] Ready for review
