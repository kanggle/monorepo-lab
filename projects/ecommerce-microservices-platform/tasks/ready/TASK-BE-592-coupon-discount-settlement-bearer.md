# Task ID

TASK-BE-592

# Title

쿠폰 할인은 정산에서 누가 부담하나 — 지금은 아무도 장부에 적지 않는다

# Status

ready

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

- [ ] **AC-0 (측정 먼저)** — 할인 5,000 이 붙은 30,000 짜리 주문(단일 셀러, 그리고 두 셀러에 걸친 주문)에 대해 지금 코드가 남기는 `ACCRUAL` 행과, 10,000 부분 환불 → 나머지 완전 환불 뒤의 `REVERSAL` 합계를 테스트로 돌려 숫자를 이 파일에 적는다. 위 사실 2·3 과 다르면 어느 쪽이 틀렸는지 적는다.
- [ ] **AC-1 (결정)** — ⓐ/ⓑ/ⓒ(또는 다른 안)에 대한 소유자 답을 원문 그대로 적는다.
- [ ] **AC-2 (스펙 먼저)** — `settlement-subscriptions.md` 가 결정한 부담 주체와 할인 반영 규칙(여러 셀러 배분·반올림 포함)을 적는다. 코드 변경은 이 AC 이후 커밋에만.
- [ ] **AC-3** — 쿠폰 주문에서 주문 단위 `Σ gross` 가 결정한 규칙대로 결제 금액과 맞고, `commission + seller_net = gross` 불변식(DB `ck_commission_accrual_split`)이 행마다 유지된다.
- [ ] **AC-4** — 쿠폰 주문의 부분 환불 → 완전 환불 뒤 주문·셀러 단위 순적립이 정확히 0 이다.
- [ ] **AC-5** — 쿠폰 없는 주문(`discountAmount` 0 또는 필드 없는 옛 이벤트)은 적립·역분개 숫자가 변경 전과 같다(회귀).

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
