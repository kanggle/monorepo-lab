# Task ID

TASK-BE-592

# Title

쿠폰 할인은 정산에서 누가 부담하나 — 지금은 아무도 장부에 적지 않는다

# Status

done

# Owner

backend

# Task Tags

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

쿠폰으로 할인된 주문의 정산이 **실제로 결제된 금액**과 맞게 기록되어야 한다 — 그리고 그 할인을 플랫폼·셀러 중 누가 부담하는지가 **소유자 결정으로** 정해져 장부에 드러나야 한다.

`TASK-INT-026` 이 쿠폰 할인을 서버가 확정해 `OrderPlaced.totalPrice`(결제할 금액)에 싣게 했다. 그 티켓이 정산은 바꾸지 않기로 하고(Out of Scope) 영향만 측정해 남겼다. 이 티켓이 그 뒤를 잇는다.

## 이미 측정된 것 (TASK-INT-026 진행 기록, 2026-09-15 UTC)

| # | 사실 | 위치 |
|---|---|---|
| 1 | settlement-service 는 수수료 기준(gross)을 `OrderPlaced.items[]` 의 `unitPrice × quantity` 로 잡는다 — `totalPrice`·`discountAmount` 를 읽지 않는다 | `apps/settlement-service/.../infrastructure/event/OrderPlacedSnapshotConsumer.java:72` |
| 2 | ⇒ 쿠폰 주문의 수수료는 **할인 전 금액**에 매겨지고, 셀러 순수익도 할인 전 기준이다. 할인액은 어느 쪽 장부에도 없다 | 〃 |
| 3 | 환불 역분개는 «captured 합계 == 적립 gross» 를 전제한다. 쿠폰 주문은 captured < gross 라 **부분 환불의 비례 역분개가 과소**하다. 마지막 환불(`fullyRefunded=true`)이 남은 전액을 되돌려 완전 환불이면 주문 단위로는 0 이 맞는다 | `specs/contracts/events/settlement-subscriptions.md` § Proportional clawback rule |
| 4 | `OrderPlaced` 에는 이제 `couponId`·`discountAmount` 가 있다(덧붙임, 쿠폰 없으면 `null`/`0`) | `specs/contracts/events/order-events.md` § OrderPlaced |

🔴 사실 2·3 은 코드와 계약을 **읽어** 얻었다. 숫자로 돌린 것은 아니다 — AC-0 이 그 일이다.

## 결정이 필요한 것

- **ⓐ 플랫폼 부담** — 셀러 순수익은 할인 전 기준 그대로, 할인액은 플랫폼 수수료에서 뺀다(수수료가 음수가 될 수 있는지 함께 정해야 한다).
- **ⓑ 셀러 부담** — gross 를 할인 후 금액으로 잡는다. 여러 셀러 주문이면 할인을 줄마다 **어떻게 나누나**(gross 비례 + 반올림 잔차를 누구에게)가 따라온다.
- **ⓒ 분담** — 비율을 정한다(프로모션마다 다를 수 있나).

🔴 분석자 추천은 적지 않는다 — 돈을 누가 내는지는 제품·계약 결정이다. AC-1 이 소유자 답을 기록하기 전에는 구현하지 않는다.

### AC-1 결정 기록 (2026-09-15 UTC)

소유자에게 선택창으로 물었다(분석자 추천을 함께 보였다 — 소유자가 «추천과 함께» 를 요청했다). 소유자 답, 원문 그대로:

| 질문 | 소유자 답 |
|---|---|
| 쿠폰 할인액을 정산에서 누가 부담할까요? | **「플랫폼 부담 (Recommended)」** |
| 플랫폼이 부담한다면, 할인액을 장부에 어떻게 적을까요? | **「별도 프로모션 비용 행 (Recommended)」** |

선택창에 보인 추천 근거(분석자): 쿠폰은 셀러가 아니라 테넌트 운영자가 발급한다 — `specs/services/promotion-service/architecture.md` 가 «promotion/coupon are tenant-scoped operator entities, not seller» 라고 적는다. 별도 행은 수수료를 음수로 만들지 않아 `commission_accrual` 의 `ck_commission_accrual_split` 와 셀러 payout fold 를 건드리지 않는다.

이 결정에서 구현이 추가로 정한 것(결정의 일부가 아니라 구현 판단 — 스펙에 적었다):

- 새 append-only 원장 `promotion_cost` — 주문 단위 한 행(`COST`, 양수)을 결제 캡처 때, 환불마다 `REVERSAL`(음수, 부모 `COST` 에 연결). 셀러별로 나누지 않는다.
- 환불 비율의 분모를 «적립 gross» 에서 **«실제 결제액 = 적립 gross − 할인»** 으로 바꾼다. 수수료 역분개와 프로모션 비용 역분개가 같은 비율을 쓴다.
- 주문 단위 불변식: `Σ commission_accrual.gross − Σ promotion_cost.amount = 결제액`.
- 쿠폰 필드가 없는 `OrderPlaced` 는 할인 0 — 행이 생기지 않고 숫자가 변경 전과 같다.

### AC-0 측정 기록 (2026-09-15 UTC)

**방법.** 수정 전 코드에 대한 특성(characterization) 테스트 `SettlementCouponDiscountBaselineTest` — 모든 단언이 **지금 코드가 내는 숫자**다. 수정 전 트리(worktree `be-592-settlement`, HEAD `35bd9d293`)에서
`./gradlew :…:settlement-service:test --tests "…SettlementCouponDiscountBaselineTest"` → `BUILD SUCCESSFUL`, 결과 XML `tests=3 failures=0 errors=0 skipped=0` (세 테스트 모두 실행 확인). 초록이므로 단언된 숫자가 곧 측정값이다.

주문 모양: 라인 소계 30,000, 쿠폰 할인 5,000, 고객 결제 **25,000**.

| 경우 | 지금 남는 숫자 | 뜻 |
|---|---|---|
| 단일 셀러 30,000 @10% | ACCRUAL gross 30,000 · commission 3,000 · seller_net 27,000. 할인 5,000 은 **어느 행에도 없음** | 장부 gross − 결제액 = 5,000 이 설명되지 않는다. 셀러에게 27,000 을 줘야 하는데 들어온 돈은 25,000 — 플랫폼은 실제로 2,000 손해인데 장부는 +3,000 |
| 두 셀러 20,000 @10% + 10,000 @0% | seller_net 합 28,000 · commission 합 2,000 | 셀러 순수익 합이 결제액을 3,000 넘는다 |
| 부분 환불 10,000 → 나머지 15,000 완전 환불 | 1차 REVERSAL gross −10,000 · commission −1,000 · net −9,000 (분모 30,000 → 1/3). 2차는 잔여 전액(−20,000 / −2,000 / −18,000)이라 주문 합은 0 | 결제액 기준(10,000/25,000 = 40%)이면 1차는 gross −12,000 이어야 한다 — **부분 환불 역분개가 2,000 과소** |

🔵 스냅샷 레코드(`OrderSnapshot`)에 할인 필드 자체가 없어서, 할인은 이 서비스에 **들어올 길이 없다** — 위 테스트가 할인을 어디에도 넘기지 않는 것은 넘길 자리가 없기 때문이다. 위 표의 사실 2·3 은 측정으로 확인됐다.

### 구현 후 로컬 검증 (2026-09-15 UTC)

| 무엇 | 결과 |
|---|---|
| `./gradlew :…:settlement-service:test` (unit · slice, `integration` 태그 제외) | `BUILD SUCCESSFUL`. 결과 XML 26 파일 · **164 tests · failures+errors 0** |
| 새 테스트 실행 확인 | `SettlementCouponDiscountTest` 9 · `PromotionCostTest` 4 · `OrderPlacedSnapshotConsumerDiscountTest` 3 — 전부 실패 0 |
| 기존 테스트 무수정 통과 | `SettlementServiceTest` 9 · `SettlementConsumersTest` 10 (쿠폰 없는 경로 = 변경 전 숫자) |

같은 주문 모양(30,000 @10%, 할인 5,000, 결제 25,000)에서 AC-0 표와 비교한 수정 후 숫자:

| 경우 | 수정 전 | 수정 후 |
|---|---|---|
| 캡처 | commission 3,000 · net 27,000 · 할인 기록 없음 | commission 3,000 · net 27,000 (그대로) + `promotion_cost` COST 5,000 → `Σgross − 비용 = 25,000` |
| 부분 환불 10,000 | gross −10,000 (분모 30,000) | gross −12,000 · commission −1,200 · net −10,800 + 비용 −2,000 → 되돌린 돈 12,000 − 2,000 = 10,000 |
| 이어서 완전 환불 15,000 | 잔여 전액, 합 0 | gross −18,000 · 비용 −3,000, **두 원장 모두 합 0** |

**미측정 (로컬 불가):** `SettlementPromotionCostIntegrationTest`(V7 마이그레이션·`ck_promotion_cost_sign`·JPA 매핑을 실제 Postgres 로) — 로컬 Docker 차단, CI ecommerce integration lane 이 권위. 컴파일은 위 `test` 태스크에서 통과.

🔵 기준 특성 테스트 `SettlementCouponDiscountBaselineTest` 는 AC-0 증거 커밋(`dd3048b96`)에 남기고 구현 커밋에서 지운다 — 수정 후에도 초록이지만(스냅샷에 할인이 없으면 숫자가 같다) 이름이 «수정 전 측정» 이라 남기면 오해를 부른다. 같은 모양의 수정 후 기대값은 `SettlementCouponDiscountTest` 가 갖는다.

---

# Scope

## In Scope

- AC-0 측정: 쿠폰 주문의 적립·부분 환불·완전 환불이 지금 어떤 숫자를 남기는지 settlement-service 테스트로 재현
- 소유자 결정 기록
- 결정에 맞춘 스펙 선행 정렬: `settlement-subscriptions.md`, 필요 시 `order-events.md` 소비 규칙
- 적립(`ACCRUAL`)·역분개(`REVERSAL`) 계산 수정과 테스트
- 역분개의 «captured == gross» 전제 제거 또는 결정에 맞춘 재정의

## Out of Scope

- 쿠폰 발급·프로모션 예산 회계
- 정산 기간마감·지급(payout) — 별도 facet
- 이미 적립된 과거 행의 소급 재계산(필요하면 결정 후 별도 티켓)

---

# Acceptance Criteria

- [x] **AC-0 (측정 먼저)** — 할인 5,000 이 붙은 30,000 짜리 주문(단일 셀러, 그리고 두 셀러에 걸친 주문)에 대해 지금 코드가 남기는 `ACCRUAL` 행과, 10,000 부분 환불 → 나머지 완전 환불 뒤의 `REVERSAL` 합계를 테스트로 돌려 숫자를 이 파일에 적는다. 위 사실 2·3 과 다르면 어느 쪽이 틀렸는지 적는다.
  - 닫힘: 수정 전 트리(`35bd9d293`)에서 `SettlementCouponDiscountBaselineTest` **tests=3 failures=0** 실행, 숫자는 § AC-0 측정 기록의 표. 증거 커밋 `dd3048b96`(그 테스트만). 사실 2·3 이 맞았다 — 틀린 쪽 없음.
- [x] **AC-1 (결정)** — ⓐ/ⓑ/ⓒ(또는 다른 안)에 대한 소유자 답을 원문 그대로 적는다.
  - 닫힘: § AC-1 결정 기록 — 「플랫폼 부담 (Recommended)」 + 「별도 프로모션 비용 행 (Recommended)」, 소유자 원문 그대로.
- [x] **AC-2 (스펙 먼저)** — `settlement-subscriptions.md` 가 결정한 부담 주체와 할인 반영 규칙(여러 셀러 배분·반올림 포함)을 적는다. 코드 변경은 이 AC 이후 커밋에만.
  - 닫힘: 스펙 커밋 `b98e9bcab`(코드 없음)이 구현 커밋 `c4eb5aeb0` 보다 앞선다. `settlement-subscriptions.md` § 「Coupon discount — who bears it」 + § Proportional clawback rule(분모 = 결제액, 프로모션 비용 반올림·클램프), `settlement-service/architecture.md` § promotion-cost ledger, `marketplace-settlement.md` § 3.1. 🔵 **여러 셀러 «배분» 규칙은 필요 없어졌다** — 플랫폼 부담이라 할인이 셀러 줄에 나뉘지 않는다. 스펙에 그렇게 적혀 있다(주문 단위 한 행).
- [x] **AC-3** — 쿠폰 주문에서 주문 단위 `Σ gross` 가 결정한 규칙대로 결제 금액과 맞고, `commission + seller_net = gross` 불변식(DB `ck_commission_accrual_split`)이 행마다 유지된다.
  - 닫힘: 규칙은 `Σ accrual gross − Σ promotion cost = 결제액`. `SettlementCouponDiscountTest` 가 단일/두 셀러 모두 `30,000 − 5,000 = 25,000` 으로 단언. DB 제약은 실제 Postgres 에서 — `SettlementPromotionCostIntegrationTest` **PASSED** (CI run `34976167259`, job `104404381983`; `settlement-service:integrationTest` 22 tests / 실패 0).
- [x] **AC-4** — 쿠폰 주문의 부분 환불 → 완전 환불 뒤 주문·셀러 단위 순적립이 정확히 0 이다.
  - 닫힘: `SettlementCouponDiscountTest` 의 부분→완전 환불 케이스(수수료 원장·프로모션 원장 각각 합 0, 반올림 잔차 30,001 케이스 포함)와 IT 의 라운드트립(셀러 잔액 0 · 프로모션 비용 합 0).
- [x] **AC-5** — 쿠폰 없는 주문(`discountAmount` 0 또는 필드 없는 옛 이벤트)은 적립·역분개 숫자가 변경 전과 같다(회귀).
  - 닫힘: 기존 `SettlementServiceTest` 9 · `SettlementConsumersTest` 10 이 **수정 없이** 통과, `SettlementCouponDiscountTest.reverse_noDiscount_sameAsBefore`(분모 30,000 유지 · 프로모션 원장 미접촉), `OrderPlacedSnapshotConsumerDiscountTest.wireJson_withoutCouponFields_isNoDiscount`(옛 이벤트), IT `orderWithoutCoupon_writesNoPromotionCost`.

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read `PROJECT.md`, then load `rules/common.md` plus any `rules/domains/<domain>.md` and `rules/traits/<trait>.md` matching the declared classification. Unknown tags are a Hard Stop per `CLAUDE.md`.

- `specs/services/settlement-service/architecture.md`
- `specs/features/marketplace-settlement.md`
- `rules/traits/transactional.md`

# Related Skills

- `.claude/skills/INDEX.md` 의 backend · messaging 항목 중 필요한 것

---

# Related Contracts

- `specs/contracts/events/settlement-subscriptions.md`
- `specs/contracts/events/order-events.md` § OrderPlaced
- `specs/contracts/events/payment-events.md` § PaymentCompleted / PaymentRefunded

---

# Target Service

- `settlement-service`

---

# Architecture

Follow:

- `specs/services/settlement-service/architecture.md`

---

# Implementation Notes

- 스냅샷은 `OrderPlaced` 한 번뿐이다 — 할인 배분에 필요한 값은 그때 저장해야 한다(`PaymentCompleted` 에는 줄 정보가 없다).
- 옛 이벤트(필드 없음)는 `discountAmount = 0` 으로 읽는다.

---

# Edge Cases

- 두 셀러에 걸친 쿠폰 주문 — 할인 배분과 반올림 잔차
- 할인이 한 줄의 gross 보다 큰 경우(배분 규칙에 따라 줄 gross 가 음수가 되면 안 된다)
- 부분 환불 여러 번 뒤 완전 환불
- `OrderPlaced` 보다 `PaymentCompleted` 가 먼저 오는 순서 역전(기존 재시도 → DLQ 규칙 유지)
- 결정 이전에 적립된 쿠폰 주문 행

---

# Failure Scenarios

- 배분 반올림이 줄 합과 1원 어긋남 — 불변식 위반으로 DB 제약에 걸림
- 역분개 계산이 적립보다 크게 되돌림 — 누적 상한(clamp) 유지 확인
- 스냅샷 없이 온 결제 이벤트 — 기존 재시도 → DLQ

---

# Test Requirements

- AC-0 측정 테스트(수정 전 숫자를 남기고, 수정 후 기대값으로 바뀐다)
- 적립·역분개 단위 테스트(단일/다중 셀러, 부분/완전 환불)
- 옛 이벤트 회귀 테스트

---

# Definition of Done

- [ ] AC-0 측정 기록
- [ ] AC-1 소유자 결정 기록
- [ ] 스펙 선행 정렬
- [ ] 구현 완료
- [ ] 테스트 추가·통과
- [ ] Ready for review

---

분석=Opus 5 / 구현 권장=Opus — 돈의 배분·반올림·역분개 불변식이 함께 바뀐다.
