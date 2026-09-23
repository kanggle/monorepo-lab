# Task ID

TASK-MONO-724

# Title

🔴 피킹에서 lot 을 **다른 구체 lot 으로 바꾸면**(예약 lot A → 집은 lot B) 출하 확정이 여전히 DLT 로 간다 — 생산자 계약이 허용하는 흐름이다

# Status

ready

# Owner

monorepo

# Task Tags

- wms
- kafka
- dlt
- inventory

---

# Goal

`TASK-MONO-706` 이 출하 확정 DLT 의 뿌리를 **계약 두 개의 충돌**로 지목했고, 소유자 결정 **ⓐ** 로 그 절반을 닫았다 — `inventory-events.md` §C4 의 **Line matching rule**: 정확 `(skuId, lotId)` → 없으면 그 sku 의 **any-lot(NULL) 라인이 정확히 하나일 때** 그것 → 그 외 hard error.

ⓐ 는 **구체 lot 대체**를 일부러 남겼다: 예약 라인이 lot **A** 로 잡혔는데 운영자가 피킹에서 lot **B** 를 집으면, 생산자 `outbound-events.md` §5 는 그것을 허용하고(*"Actual lot picked; may differ from planned if operator substituted"*) §7 은 B 를 싣지만, 소비자는 A≠B 라 hard error → DLT → `inventory.confirmed` 가 안 나가 사가가 `SHIPPED` 에 머문다.

ⓐ 가 이것을 폴백으로 안 푼 이유는 **정책이 없기 때문**이다 — A 행의 예약을 확정(차감)하면 실물 B 가 나갔는데 A 의 재고가 줄어 **장부가 틀린다**. 옳은 처리가 무엇인지(대체를 피킹에서 막는가 · 확정 시 A 예약 해제 + B 차감인가 · 대체를 금지하고 재예약을 강제하는가)는 도메인 결정이다.

---

# Scope

## 포함

- 대체가 **실제로 일어날 수 있는지** 먼저 잰다(outbound 피킹 확정이 계획 lot 과 다른 lot 을 받아들이는가 — 코드 독해 + 테스트).
- 일어날 수 있으면 갈래를 표로 올려 **소유자 결정**을 받고, 계약(§5/§7 · §C4)을 먼저 고친 뒤 구현 + bite.

## 제외

- any-lot 예약의 폴백 — `TASK-MONO-706` 이 닫았다(재론 금지).
- 부분 출하(v1 은 `qtyConfirmed == ReservationLine.quantity` 정확히).

---

# Acceptance Criteria

- [ ] **AC-0 — 대체가 도달 가능한가 (verify-then-act).** outbound 의 피킹 확정 경로(`LotRequiredException` 이 사는 곳부터)를 **읽고**, 계획 lot 이 non-null 인 라인에 다른 lot 으로 확정 요청이 **받아들여지는지** 단위/슬라이스 테스트로 잰다. 🔴 grep 으로 «막는 검사가 없다» 를 판정하지 마라 — 2026-09-23 grep(`substitut|LotMismatch|!Objects.equals(..lot`) 0건은 **판정이 아니다**. 거절되면(= 도달 불가) 그 사실과 근거 테스트를 적고 **닫는다**(§5 의 «may differ» 문구를 현실에 맞게 고치는 한 줄 포함).
- [ ] **AC-1 — (도달 가능하면) 갈래 표 → 소유자 정확형 결정.** 최소 후보: ① 피킹 확정에서 대체를 거절(422) ② 확정 시 A 예약 해제 + B 차감(재고 행 두 개를 건드린다 — 트랜잭션·이벤트 모양) ③ 대체를 허용하되 inventory 가 별 이벤트로 조정. 추천 표시하되 **추천 ≠ 선택**.
- [ ] **AC-2 — 계약 먼저 → 구현 → bite.** 결정된 갈래로 `outbound-events.md` §5/§7 · `inventory-events.md` §C4 를 먼저 고치고, 대체 시나리오 테스트가 **고치기 전 트리에서 빨간 것**을 확인한 뒤 고친다.
- [ ] **AC-3 — 706 의 경계 핀과 정합.** `ShippingConfirmedConsumerTest` 의 `concreteLotSubstitutionStillThrows` 는 이 티켓의 결정에 따라 **의도적으로** 바뀌거나 유지된다 — 바뀌면 그 이유를 테스트 DisplayName 과 §C4 에 적는다(조용히 뒤집지 마라).

---

# Related Specs

- `projects/wms-platform/specs/contracts/events/outbound-events.md` §5 `outbound.picking.completed` · §7 `outbound.shipping.confirmed`
- `projects/wms-platform/specs/contracts/events/inventory-events.md` §C2 · §C4 (Line matching rule, TASK-MONO-706)
- `tasks/review/TASK-MONO-706-one-shipping-confirmed-event-sits-in-the-dlt-on-a-fresh-volume.md` § AC-2

# Related Contracts

- 위 두 이벤트 계약. 🔴 바꾸면 계약 먼저, 생산자·소비자 한 PR.

---

# Edge Cases

| 상황 | 기대 |
|---|---|
| 계획 lot NULL · 집은 lot 구체 | 706 ⓐ 폴백이 처리 — 이 티켓 범위 아님 |
| 계획 lot A · 집은 lot A | 정확 매칭 — 변화 없음 |
| 계획 lot A · 집은 lot B | 이 티켓 |
| 한 주문 라인이 두 재고 행에 걸쳐 예약됨(할당 분할) | 정확 매칭도 수량 불일치로 실패할 수 있다 — AC-0 에서 **따로** 도달 가능성을 적고 섞지 마라 |

# Failure Scenarios

1. **§C4 폴백을 sku-only 로 넓혀 초록을 만든다** → 706 에서 기각된 ⓑ(다른 lot 행을 조용히 차감)를 뒷문으로 들이는 것이다.
2. **grep 0건을 «대체는 일어나지 않는다» 로 읽고 닫는다** → AC-0 은 테스트로 잰다.

---

# 분석 / 구현 권장

분석=Opus 5.5 / 구현 권장=**Opus**(재고 장부 정책 · 교차 서비스 계약) — AC-0 만이면 Sonnet 충분.
