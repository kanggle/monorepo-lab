# Task ID

TASK-MONO-724

# Title

🔴 피킹에서 lot 을 **다른 구체 lot 으로 바꾸면**(예약 lot A → 집은 lot B) 출하 확정이 여전히 DLT 로 간다 — 생산자 계약이 허용하는 흐름이다

# Status

review (2026-09-23 UTC — AC-0~AC-3 전부 닫힘: 대체는 도달 가능했다(측정) → 소유자 ① → 피킹 확정에서 거절(`LOT_SUBSTITUTION_NOT_ALLOWED`) · 창 판정 없음)

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

- [x] 🟢 **AC-0 — 대체가 도달 가능한가 (verify-then-act).** → **도달 가능**(아래 § AC-0). outbound 의 피킹 확정 경로(`LotRequiredException` 이 사는 곳부터)를 **읽고**, 계획 lot 이 non-null 인 라인에 다른 lot 으로 확정 요청이 **받아들여지는지** 단위/슬라이스 테스트로 잰다. 🔴 grep 으로 «막는 검사가 없다» 를 판정하지 마라 — 2026-09-23 grep(`substitut|LotMismatch|!Objects.equals(..lot`) 0건은 **판정이 아니다**. 거절되면(= 도달 불가) 그 사실과 근거 테스트를 적고 **닫는다**(§5 의 «may differ» 문구를 현실에 맞게 고치는 한 줄 포함).
- [x] 🟢 **AC-1 — (도달 가능하면) 갈래 표 → 소유자 정확형 결정.** → 소유자 **①** (2026-09-23). 최소 후보: ① 피킹 확정에서 대체를 거절(422) ② 확정 시 A 예약 해제 + B 차감(재고 행 두 개를 건드린다 — 트랜잭션·이벤트 모양) ③ 대체를 허용하되 inventory 가 별 이벤트로 조정. 추천 표시하되 **추천 ≠ 선택**.
- [x] 🟢 **AC-2 — 계약 먼저 → 구현 → bite.** → 아래 § 구현. 결정된 갈래로 `outbound-events.md` §5/§7 · `inventory-events.md` §C4 를 먼저 고치고, 대체 시나리오 테스트가 **고치기 전 트리에서 빨간 것**을 확인한 뒤 고친다.
- [x] 🟢 **AC-3 — 706 의 경계 핀과 정합.** → **의도적으로 유지**(심층 방어) — 이유는 §C4 에 적었다(아래 § 구현). `ShippingConfirmedConsumerTest` 의 `concreteLotSubstitutionStillThrows` 는 이 티켓의 결정에 따라 **의도적으로** 바뀌거나 유지된다 — 바뀌면 그 이유를 테스트 DisplayName 과 §C4 에 적는다(조용히 뒤집지 마라).

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

---

# 🟢 AC-0 — 대체는 도달 가능하다 (2026-09-23 UTC · 창 없음 · 분석=Opus 5.5)

## 사슬 (세 고리, 각각 근거)

1. **피킹 확정이 대체를 받는다 — 테스트로 측정.** `ConfirmPickingServiceTest.concreteLotSubstitution_isAccepted_andCarriedOnPickingCompleted`:
   계획 lot A(`OrderLine.lotId` · `PickingRequestLine.lotId`)인 LOT 추적 라인을 lot B 로 확정 → 주문 `PICKED` · 결과 라인 lot = **B** · `outbound.picking.completed` 1건. 스위트 **5/5** 통과.
   🔵 대조군 = 같은 클래스의 `lotTrackedSkuWithoutLot_raisesLotRequired` — 같은 `validateLines` 를 지나 **거절**된다 ⇒ 검증 경로가 실제로 돈다(«검증을 건너뛰어서 통과» 가 아니다).
   원인(코드): `ConfirmPickingService.validateLines`(233–259행)는 수량 · «LOT 추적이면 lot 존재» 만 본다 — **계획 lot 과의 대조가 없다**.
2. **계약이 그것을 허용한다.** `outbound-service-api.md:443-445` *"The confirmed `lotId` may differ from the `PickingRequestLine.lotId` if the operator substituted (allowed and logged)."* · `outbound-events.md` §5 *"may differ from planned if operator substituted"*.
   🔴 «and logged» 는 **거짓**이다 — 대체를 알리는 로그·이벤트 필드가 코드에 없다(`log.info` 는 주문/피킹/사가 id 만).
3. **출하가 B 를 싣는다.** `ConfirmShippingService.java:234` — 출하 라인 lot = `pcl != null ? pcl.getLotId() : ol.getLotId()`(피킹 확정 우선). ⇒ `shipping.confirmed` 가 B 를 싣고, 예약이 A 로 잡힌 inventory 는 706 ⓐ 규칙(정확 → any-lot 폴백 → 에러)에서 **에러** → DLT → `inventory.confirmed` 없음 → 사가 `SHIPPED` 체류.

## 곁발견 (이 티켓 범위 밖 — 적기만 한다)

- `validateLines` 는 확정 라인의 `skuId` 가 주문 라인의 SKU 와 **같은지도** 안 본다. 다른 SKU 로 확정해도 통과할 것이다(측정 안 함 — 코드 독해만). 필요하면 별 티켓.

## ⏳ AC-1 — 소유자 결정 대기 (추천 ≠ 선택)

| | 갈래 | 장부 | 대가 |
|---|---|---|---|
| **① (추천)** | 피킹 확정에서 **계획 lot 이 구체이면 같은 lot 만** 받는다(다르면 422). 계획이 NULL(any-lot)이면 지금처럼 아무 lot | 항상 옳다 — 예약한 행을 차감 | 계약 §443 «allowed» 를 뒤집는다. 대체가 필요하면 운영자가 취소·재주문 |
| ② | 출하 확정 시 inventory 가 A 예약 해제 + B 행 차감 | B 행이 **예약 없이** 차감됨 — B 재고가 모자라면 음수/실패 경로가 필요 | inventory 두 행을 한 TX 에서 · 실패 시 사가 보상 설계 |
| ③ | 피킹 확정 시 outbound → inventory 재예약(A→B) 이벤트, 성공 후에야 확정 | 옳다 | 새 사가 단계·이벤트 계약 둘 — 이 티켓 규모를 넘는다(ADR 급) |

🔵 ① 을 추천하는 이유: 706 ⓐ 와 **같은 원리**(«NULL = 아무 lot, 구체 = 그 lot») 를 생산자 쪽에서 강제할 뿐이고, 장부가 틀릴 길이 사라진다. 데모 시드의 주문은 전부 계획 lot NULL 이라 **데모 흐름은 안 바뀐다**.

---

# 🟢 구현 — 소유자 결정 ① (2026-09-23 UTC)

## 계약 먼저

- `outbound-service-api.md` §2.3 — *"allowed and logged"* 를 **«대체 불가»** 로 교체(이유 박스 포함 — 옛 문장이 무엇을 약속했고 왜 지킬 수 없었는지) · 검증 목록 · 오류 목록 · 오류 표에 `LOT_SUBSTITUTION_NOT_ALLOWED`(422).
- `outbound-events.md` §5 `lines[].lotId` 설명 교정 · 소비자 기대의 «notification-service 대체 알림» 제거(대체할 것이 없다 — 그런 구독자도 코드에 없다, grep 확인).
- `inventory-events.md` §C4 규칙 3 — 생산자가 이제 거절한다는 한 줄(하드 에러는 **심층 방어로 유지**).
- `platform/error-handling.md` § Outbound — 코드 등록(project-agnostic 문구).
- `specs/services/outbound-service/state-machines/order-status.md` — `PICKING → PICKED` 가드 · 예외↔코드 표.

## 코드

- `LotSubstitutionNotAllowedException`(422 — `GlobalExceptionHandler` 의 도메인 기본값, 매핑 추가 불필요).
- `ConfirmPickingService.validateLines` — `OrderLine.lotId != null && !equals(confirmed)` ⇒ 거절. 계획 NULL 은 그대로 통과.

## 테스트 · bite

| 칸 | 새 코드 | 검사를 끈 트리(bite) |
|---|---|---|
| 대체(A→B) → 거절 · 확정/아웃박스/상태 **변화 0** | 🟢 | 🔴 **FAIL**(유일) — *"Expecting code to raise a throwable"* |
| 계획 lot 그대로 → 통과 | 🟢 | 🟢 |
| 계획 NULL(any-lot) + 운영자 지정 lot → 통과 (**데모 흐름**) | 🟢 | 🟢 |

🔵 AC-0 의 측정 칸(«받아들여진다»)은 첫째 칸으로 **의도적으로 뒤집혔다** — Javadoc 에 그 이력을 적었다.
outbound-service `test` 전체 **289 / 0 failures / 0 errors / 4 skipped**(skip 4 는 기존). bite 는 백업→치환→복원(`cmp` 일치), `git checkout` 안 씀.

## 영향 범위 (확인한 것)

- **console-web** 피킹 프록시(`app/api/wms/outbound/[orderId]/pick/route.ts`)는 **계획대로 확정**(picking request 의 `lotId` 를 그대로 넘긴다) ⇒ 대체를 만들 수 없다. 새 코드는 기존 오류 매핑으로 흐른다.
- **데모 시드**(`seed-wms.sh`)는 계획 lot NULL + 확정 시 lot 지정 ⇒ 셋째 칸이 지킨다.

## 곁발견 — 이번엔 안 고쳤다 (결정 범위 밖)

- 계약 `outbound-service-api.md` §2.3 검증 목록은 *"`lines[].skuId`: must match the `OrderLine.sku_id`"* 라고 적는데 `validateLines` 는 **그것을 검사하지 않는다**(AC-0 곁발견과 같은 것 — 이제 **계약 위반**으로 확인). 필요하면 별 티켓.
