# ADR-MONO-066 — wms 피킹 요청의 생성 지점과 로케이션 배정 소유권

**Status:** ACCEPTED
**Date:** 2026-08-14
**주관 티켓:** `TASK-BE-586` (AC-1)
**선행:** [`ADR-MONO-022`](ADR-MONO-022-ecommerce-wms-fulfillment-integration.md) § D9 (풀필먼트 사가) · `TASK-BE-038` / `TASK-BE-040` (pick-pack-ship 도메인 — 둘 다 `done/`)
**출처:** [`ADR-MONO-065`](ADR-MONO-065-wms-admin-read-plane-tenant-axis.md) 의 검증(`TASK-BE-584` AC-3)이 **잴 수 없는 칸**을 남겼고, 그 원인을 파다 이 질문이 나왔다

## History

- 2026-08-14 — PROPOSED. `TASK-BE-586` AC-0 실측이 티켓의 전제를 바꿨다. 티켓은 이것을
  *"미구현 엔드포인트(405) + 사가가 행을 안 만듦"* 이라는 **배선 결함**으로 적었으나, 실측하면
  **스펙이 이름까지 지정한 도메인 서비스(`PickingPlanner`)가 존재하지 않고**, 그 자리를 임시로
  누가 메울 것인가에 대해 **스펙과 코드 주석이 서로 다른 소유자**를 가리킨다. 소유권 결정이므로
  `platform/architecture-decision-rule.md` § The ACCEPTED Gate 에 걸린다.
  🔴 **ACCEPT 는 소유자가 한다.** 아래 § 추천은 추천일 뿐이다.
- 2026-08-15 — **ACCEPTED: `B + R1-a + R2-b`** (소유자 정확형 지정). 추천과 일치하지만
  **추천이 승인이 된 것이 아니라** 소유자가 별도로 지정했다 — `platform/architecture-decision-rule.md`
  § The ACCEPTED Gate 는 `진행`/`추천대로` 같은 긍정 신호로는 열리지 않는다. `TASK-BE-586` AC-1
  착수 가능.

---

## Context

`TASK-BE-584` 가 `ADR-MONO-065` 의 격리 표면 2개(`/dashboard/orders` · `/dashboard/shipments`)를
차등 대조군으로 재려 했다. `orders` 는 성립했고 **`shipments` 는 판정 불가**로 남았다 —
`admin_shipment_summary` 가 0행이라 두 테넌트가 같은 0 을 내고, 그 0 은 *"격리가 동작한다"* 와
*"테이블이 비었다"* 를 구별하지 못한다.

제품 API 로 채우려 했으나 출고가 `PICKING` 에서 더 나아가지 않았다. 왜 그런지가 이 ADR 의 질문이다.

### 이 질문이 열린 이유 — 닫힌 티켓의 산출물이 도달 불가였다

`TASK-BE-038`(pick-pack-ship 도메인)은 `done/` 이고 `TASK-BE-040` 이 그 후속 정리까지 끝냈다.
그런데 그 도메인의 **입구**(피킹 요청 생성)가 프로덕션에 존재하지 않는다. 테스트는 초록인데,
그 초록은 **제품이 만들 수 없는 상태를 픽스처가 심어** 얻은 것이다.

---

## 이 ADR 이 서 있는 실측 (2026-08-14, `main` = `9cc275add`)

### M1 — 🔴🔴 `PickingPersistencePort.save()` 의 호출처는 **프로덕션 0건 · 테스트 2건**이다

```
포트 선언        application/port/out/PickingPersistencePort.java
어댑터 구현      adapter/out/persistence/adapter/PickingRepositoryImpl.java:35  save(PickingRequest)

프로덕션 호출처   0건   (application 계층 전수 — Confirm/Query 서비스는 findBy* 만 쓴다)
테스트 호출처     2건   ConfirmPickingServiceTest:202 · ConfirmShippingServiceTest:217
                       (+ FakePickingPersistencePort — 인메모리 페이크)
```

⇒ **행을 만드는 코드가 없다.** 어댑터는 구현돼 있고 포트도 선언돼 있는데, 부르는 프로덕션 경로가
하나도 없다. 두 테스트가 그 포트로 직접 심고 그 위에서 확정/출하 로직을 검증하므로,
**"피킹 요청이 존재한다" 는 전제가 프로덕션에서 성립한 적이 없다는 사실을 어떤 테스트도 볼 수 없다.**
[[env_test_fixture_impossible_input_proves_nothing]]

### M2 — 계약 § 2.1 의 엔드포인트는 구현되지 않았다 (라이브 405)

```
POST /api/v1/outbound/orders/{id}/picking-requests   →  405 METHOD_NOT_ALLOWED   (라이브, 운영자 토큰)

구현 전수
  OrderQueryController:111   @GetMapping("/{id}/picking-requests")     ← GET 뿐
  PickingController:42       @RequestMapping(".../picking-requests")   ← confirmations 루트
```

계약(`outbound-service-api.md` § 2.1)은 이 엔드포인트를 *"re-entry / manual saga recovery"* 로
명시한다. 구현에는 없다.

### M3 — 컨슈머도 만들지 않는다

`InventoryReservedConsumer` 는 `wms.inventory.reserved.v1` 을 받아 **사가를 `RESERVED` 로 올릴 뿐**이다
(javadoc 원문: *"advances the matching saga to RESERVED via OutboundSagaCoordinator"*).
`PickingRequest` 를 만들지 않는다. outbound 의 컨슈머 8개 어디에도 그 저장이 없다(M1 의 0건과 같은 사실).

### M4 — 🔴🔴 스펙은 담당자를 **이름까지 지정**한다. 그 이름은 코드에 없다

```
specs/services/outbound-service/sagas/outbound-saga.md:92
  "4. `PickingPlanner` (domain service) computes per-line `location_id` from …"
specs/services/outbound-service/domain-model.md:185
  "`location_id` … assigned by `PickingPlanner` domain service at `RequestPickingUseCase` time"
specs/contracts/events/outbound-events.md:300
  "`lines[].locationId` … Assigned picking source location (`PickingPlanner` domain service result)"

PickingPlanner 의 Java 구현        0건
PickingPlanner 를 언급하는 Java   1건 — 주석
  ReceiveOrderService:269
    null /* locationId — assigned by inventory until PickingPlanner ships in BE-038 */
```

⇒ 스펙 3곳은 **outbound 의 `PickingPlanner`** 가 로케이션을 정한다고 적고, 코드 주석은
**inventory 가 정한다(당분간)** 고 적는다. **소유자가 두 개다.** 그리고 `TASK-BE-038` 은 `done/` 이라
*"곧 온다"* 는 그 주석은 가리킬 티켓을 잃었다.

🔵 `RequestPickingUseCase`(스펙이 로케이션 배정 시점으로 지목한 그 유스케이스) = 계약 § 2.1 이고,
M2 대로 존재하지 않는다.

### M5 — 🔴 § 2.1 만 구현해도 **데모는 풀리지 않는다**

계약 § 2.1 의 전제: *"Allowed only when `Order.status == PICKING` AND no `PickingRequest` yet exists …
If the saga has advanced past `REQUESTED`, returns `422`."*

실측 상태:

```
outbound_order.status = PICKING     ✅ 전제 충족
picking_request        0행           ✅ 전제 충족
outbound_saga.status   RESERVED      ❌ REQUESTED 를 이미 지났다  → 422
```

예약은 주문을 넣는 순간 자동으로 일어난다(`TASK-MONO-528` 실측 — `ReceiveOrderService` 가 같은 TX 로
`picking.requested` 를 발행하고 inventory 컨슈머가 예약한다). ⇒ 운영자가 § 2.1 을 부를 수 있는 창은
**사실상 존재하지 않는다.** 재진입 엔드포인트만 구현하는 것은 **이 결함을 고치지 못한다.**

### M6 — 🔵 inventory 는 이미 로케이션을 **정해서 실어 보내고 있다**

`specs/contracts/events/inventory-events.md` § 4 (`wms.inventory.reserved.v1`):

```json
"payload": {
  "reservationId": "uuid", "pickingRequestId": "uuid", "warehouseId": "uuid",
  "lines": [ { "reservationLineId":"uuid", "inventoryId":"uuid",
               "locationId":"uuid", "skuId":"uuid", "lotId":"uuid-or-null",
               "quantity":5, ... } ]
}
```

⇒ 선택지 B 는 **새 도메인 로직을 한 줄도 요구하지 않는다.** 값은 이미 경계를 넘어와 있고,
지금은 아무도 그것을 저장하지 않을 뿐이다. 같은 문서 상단이 *"correlate … using `pickingRequestId`"*
라고 적는 것도 이 방향과 정합한다 — 사가는 그 id 를 이미 들고 있다(`OutboundSaga.pickingRequestId`).

### M7 — 파급: 이것이 `ADR-MONO-065` 의 절반을 검증 불가로 묶고 있다

```
picking_request 0행 → picking_confirmation 0 → packing_unit 0 → shipment 0
                                                              → admin_shipment_summary 0행
⇒ /dashboard/shipments 는 어떤 테넌트로 재도 0  ⇒ 격리와 공백이 구별되지 않는다
```

`ADR-MONO-065` 는 격리 표면을 **2개**로 정의했다. 그중 1개가 **한 번도 행사된 적 없고**, 이 결함이
풀리기 전에는 행사될 수 없다(`TASK-BE-584` AC-3).

---

## 선택지

### A — 스펙대로 `PickingPlanner` 를 구현한다

outbound 에 `PickingPlanner` 도메인 서비스를 만들어 재고 읽기모델에서 로케이션을 고르고,
`RequestPickingUseCase`(계약 § 2.1)를 구현해 그 시점에 `PickingRequest` 를 만든다.

- ✅ 스펙 3곳(M4)과 **문서 수정 없이** 일치한다. 로케이션 배정이 outbound 도메인에 남는다
- ❌ 범위가 가장 크다 — 도메인 서비스 + 유스케이스 + 재고 읽기모델 조회 경로
- ❌ 🔴 **M5 를 함께 풀어야 한다.** § 2.1 의 `saga > REQUESTED → 422` 전제 때문에, 정상 흐름이
  그것을 부르도록 바꾸지 않으면 데모는 그대로 막힌다 ⇒ 사실상 "정상 흐름에서 생성" 을 함께 정해야 한다
- ❌ outbound 가 로케이션을 고르고 inventory 가 **다시 고른다**(예약 시) — 두 번 고르는 것을
  어떻게 화해시킬지가 새 질문이다(inventory 의 예약 결과가 outbound 의 계획과 다르면 누가 이기는가)

### B — inventory 가 정한 값을 수용하고, 예약 응답에서 행을 만든다 *(M6 이 여는 선택지)*

`InventoryReservedConsumer` 가 `wms.inventory.reserved.v1` 의 `lines[].locationId` 로
`PickingRequest` 를 저장한다(사가는 이미 `pickingRequestId` 를 들고 있으므로 id 도 이미 있다).

- ✅ **새 도메인 로직 0** — 값이 이미 넘어와 있다(M6). 두 번 고르는 문제가 애초에 생기지 않는다
- ✅ 코드 주석이 적어 둔 *"assigned by inventory"* 를 **정식화**한다(지금은 임시라고 적혀 있고
  임시가 영구가 됐다)
- ✅ 정상 흐름이 스스로 닫힌다 — 운영자 조작 없이 `RESERVED` 도달 시 행이 생긴다
- ❌ 🔴 **스펙 3곳(M4)을 정정해야 한다** — `PickingPlanner` 가 로케이션을 정한다는 문장을 철회하고
  실제 소유자를 적는다. 문서 수정 없이 코드만 바꾸면 `ADR-MONO-064` 가 정정해야 했던 것과 같은
  드리프트를 새로 만든다
- ❌ 로케이션 최적화(피킹 동선 등)를 outbound 가 나중에 갖고 싶어지면 소유권을 되돌려야 한다

### C — 계약 § 2.1 만 구현한다 *(M5 가 배제한다)*

재진입 엔드포인트만 만든다.

- ❌ **M5 가 이것을 배제한다** — 사가가 `RESERVED` 라 422 이고, 예약은 주문 생성과 같은 TX 에서
  자동으로 시작된다. 부를 수 있는 창이 없다. **증상을 고치지 못한다**
- 🔵 다만 A 든 B 든 재진입 경로 자체는 별개 가치가 있다(§ R2)

### D — 계약 § 2.1 을 계약에서 걷어낸다

구현되지 않은 엔드포인트를 계약에서 삭제해 문서와 코드를 일치시킨다.

- ✅ 드리프트 하나가 사라진다
- ❌ **이 ADR 의 질문에 답하지 않는다** — 행은 여전히 아무도 만들지 않는다. A/B 와 배타적이지 않다

---

## 추천 (소유자 결정 아님)

**B + R1-a + R2-b.**

근거는 M6 하나다 — **값이 이미 경계를 넘어와 있다.** A 는 outbound 가 로케이션을 고르게 하지만,
inventory 는 예약하면서 어차피 다시 고른다(그것이 실제 재고를 잠그는 결정이다). 두 결정이 다를 때
어느 쪽이 이기는지를 정하는 비용이, B 가 요구하는 문서 3문장 정정보다 크다.

🔴 그리고 이 추천은 **`TASK-BE-038` 이 이미 한 번 밟은 자리**다 — 그 티켓은 `PickingPlanner` 를
스펙에 남긴 채 구현하지 않았고, 코드에 *"until PickingPlanner ships"* 라는 임시 주석을 남겼다.
**임시가 영구가 된 것을 정식화하든가, 진짜로 구현하든가 둘 중 하나를 이번에 끝내야 한다.**
세 번째로 미루면 다음 사람은 *"곧 온다"* 는 주석을 또 물려받는다.

---

## 라이더 — 어느 선택지를 고르든 함께 답해야 하는 것

### R1 — `PickingPlanner` 를 지목하는 스펙 3문장을 어떻게 하는가

- **a.** B 를 고르면 **철회하고 실제 소유자를 적는다**(`outbound-saga.md` §4 · `domain-model.md`
  `location_id` · `outbound-events.md` L300). 🔴 코드만 바꾸고 문서를 두면 새 드리프트다
- **b.** A 를 고르면 그대로 둔다(문서가 맞고 코드가 따라간다)

### R2 — 계약 § 2.1(재진입)은 남는가

- **a.** 구현한다 — 단 M5 대로 `saga > REQUESTED → 422` 전제를 함께 손봐야 실제로 쓸 수 있다
- **b.** **계약에서 걷어낸다**(선택지 D) — 정상 흐름이 스스로 닫히면 재진입은 현재 수요가 없다.
  🔵 미구현 엔드포인트를 계약에 남겨 두는 것이 이 저장소가 반복해서 대가를 치른 그 모양이다
- **c.** 남기되 **미구현임을 계약에 명시**한다

### R3 — `TASK-BE-038` 의 종료를 감사하는가

`done/` 인 티켓의 핵심 산출물이 프로덕션에서 도달 불가였고, 그것을 어떤 테스트도 볼 수 없었다
(M1). 🔵 **소유자 지정**: `TASK-BE-586` 에 사실만 기록하기로 결정됨(2026-08-14) — done/ 본문은
고치지 않고, 같은 방식으로 닫힌 티켓이 더 있는지에 대한 전수 감사는 열어 두지 않는다.

---

## 결정

**`ADR-MONO-066 ACCEPTED — B + R1-a + R2-b`** (소유자 지정, 2026-08-15).

| 항목 | 확정 |
|---|---|
| **B** | 피킹 요청은 `wms.inventory.reserved.v1` 을 받는 지점에서 만든다. `lines[].locationId` 는 **inventory 가 정한 값**을 그대로 기록한다 |
| **R1-a** | `PickingPlanner` 를 소유자로 지목하는 스펙 문장을 **철회하고** 실제 소유자를 적는다 |
| **R2-b** | 계약 § 2.1(재진입 엔드포인트)은 **계약에서 걷어낸다** |

### 로케이션 소유권 — 이것이 이 ADR 의 본론이다

**`inventory-service` 가 소유한다.** 예약하면서 고르는 로케이션이 **실제 재고를 잠그는 결정**이고,
그 값은 이미 `wms.inventory.reserved.v1` 로 넘어와 있다(M6). outbound 는 **기록만** 한다 —
다시 고르지 않으므로 *"둘이 다르면 누가 이기는가"* 라는 질문 자체가 생기지 않는다.

**`PickingPlanner` 는 만들지 않는다.** 그 이름은 스펙에만 있었고 구현은 0건이었다(M4).

### 확정된 흐름

```
POST /orders  ─┬─ Order(PICKING) + OrderLines + OutboundSaga(REQUESTED)
   같은 TX     └─ outbox: outbound.picking.requested   (lines[].locationId = null)
                                    │
        inventory: 라인마다 로케이션을 고르고 예약          ← 소유권은 여기
                                    │
wms.inventory.reserved.v1 ─┬─ saga REQUESTED → RESERVED
   같은 TX (consumer)      └─ PickingRequest(SUBMITTED) + Lines 생성   ← BE-586 이 더한 것
                                    │
        이후는 이미 있던 경로: 피킹확정 → 포장 → 출하 → admin_shipment_summary
```

### `order_line_id` 는 왜 여기서 조인하는가

`inventory.reserved` 는 `orderLineId` 를 **싣지 않는다** — inventory 는 자기 `reservationLineId`
로 키를 잡는다. 그래서 매핑이 outbound 몫이고, 틀리면 **엉뚱한 주문 라인에 피킹 지시가 박힌다.**
추측이 아니라 두 사실로 정확하다:

1. inventory 는 요청 라인당 예약 라인을 **정확히 하나** 만든다(`ReserveStockService` 가
   `command.lines()` 를 순회 — 분할도 병합도 없다)
2. 요청 라인은 `order.getLines()` 에서 만들어졌다

⇒ `(skuId, lotId)` 로 조인하고, 같은 키가 반복되면 **순서대로** 소진한다. 개수가 안 맞거나
매칭이 없으면 **던진다** — 그럴듯한 행을 쓰면 확정·포장·출하가 전부 그 위에 쌓인다.

### 이 결정이 닫는 것

- 출고가 **제품 API 만으로** `PICKING → SHIPPED` 까지 간다
- `admin_shipment_summary` 가 채워져 `ADR-MONO-065` 의 **격리 표면 2개 중 나머지 1개**가
  처음으로 행사된다(`TASK-BE-584` AC-3 의 열린 칸)
- `TASK-BE-038` 이 남긴 *"until PickingPlanner ships"* 임시 주석이 **세 번째로 미뤄지지 않는다**

### 명시적으로 하지 않는 것

- 로케이션 **최적화**(피킹 동선 등)는 여기 없다. outbound 가 나중에 갖고 싶어지면 **이 ADR 을
  바꾸는** 별도 결정이 필요하다
- `TASK-BE-038` 종료 방식의 전수 감사는 열지 않는다(R3 — 2026-08-14 소유자 지정)
