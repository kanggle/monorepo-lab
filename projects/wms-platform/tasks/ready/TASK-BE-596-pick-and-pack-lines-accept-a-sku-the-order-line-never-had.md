# Task ID

TASK-BE-596

# Title

피킹 확정·패킹 라인이 **주문 라인에 없는 SKU 를 받아 저장하고 발행한다** — 계약의 «must match `OrderLine.sku_id`» 를 아무도 검사하지 않는다

# Status

ready (2026-09-24 UTC — 기안. `TASK-MONO-724` 구현 중 곁발견, 창 없이 정적으로 측정함)

# Owner

wms-platform

# Task Tags

- outbound
- validation
- contract-drift

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet — 검증 몇 줄과 테스트다. 🔴 단 **오류 코드는 계약 결정**이라 AC-1 전에 소유자 답이 필요하다.

---

# Goal

`outbound-service-api.md` 는 두 엔드포인트에 같은 규칙을 적는다:

| 엔드포인트 | 계약 문장 | 구현 |
|---|---|---|
| §2.3 `POST …/picking-requests/{id}/confirmations` | `lines[].skuId`: *"must match the `OrderLine.sku_id`"* (483행) · `lines[].actualLocationId`: *"must resolve to `ACTIVE` Location in the same warehouse"* (487행) | `ConfirmPickingService.validateLines`(234–267행)는 orderLineId 소속 · 수량 · LOT 필수 · lot 대체만 본다. **skuId 대조 0 · location 해소 0** |
| §3.1 `POST …/orders/{id}/packing-units` | `lines[].orderLineId`: *"must belong to this order"* (622행) · `lines[].skuId`: *"must match the `OrderLine.sku_id`"* (623행) · LOT 필수(624행) | `PackingService.create`(113–124행)는 요청 라인을 **그대로** `PackingUnitLine` 으로 만든다. **소속·SKU·LOT 검사 0**. `PackingUnit` 생성자도 «라인 ≥ 1» 만 본다 |

## 🔴 가장 나쁜 한 줄 — LOT 필수 판정이 **요청의** SKU 로 찾는다

```java
// ConfirmPickingService.java:256
SkuSnapshot sku = masterReadModel.findSku(cl.skuId()).orElse(null);
if (sku != null && sku.requiresLot() && cl.lotId() == null) throw new LotRequiredException(...)
```

주문 라인이 LOT 추적 SKU 여도 요청에 **LOT 추적이 아닌 다른 SKU** 를 적으면 `LOT_REQUIRED` 가 안 걸린다. 즉 검증 하나가 없어서 **다른 검증 하나가 우회된다**.

## 무엇이 그 값을 싣고 나가나 (코드로 추적, 창 없이)

- `picking_confirmation_line.sku_id` 에 요청 값이 저장된다(`buildConfirmation` :186).
- `outbound.picking.completed` 이벤트가 요청 값을 싣는다(`emitPickingCompletedOutbox` :217). 소비자 = admin-service `OutboundProjectionConsumer`.
- `packing_unit_line.sku_id` 와 `outbound.packing.completed` 이벤트도 요청 값(`PackingService` :121 · :349).
- 🔵 **출하 확정은 안전하다** — `ConfirmShippingService:233` 은 **주문 라인의** `getSkuId()` 를 쓴다. 그래서 inventory 의 `shipping.confirmed` 매칭(`TASK-MONO-706` 의 그 자리)은 이 결함으로 DLT 에 가지 않는다. ⇒ 피해는 **기록과 투영이 사실과 다르게 남는 것**이고, 재고 차감은 아니다(AC-0 이 확인).

# Scope

## 포함

- AC-0 의 bite(고치기 전 트리에서 빨간 테스트).
- 피킹 확정 §2.3: skuId 대조 · actualLocationId 해소(ACTIVE · 같은 창고) · LOT 판정을 **주문 라인의 SKU** 로.
- 패킹 §3.1: orderLineId 소속 · skuId 대조 · LOT 필수.
- 계약의 Errors 줄에 선택된 오류 코드를 반영(계약 먼저).

## 제외

- 이미 저장된 행의 정정(데모 볼륨은 신선 부팅마다 새로 쓰이고, 운영 데이터는 없다 — AC-0 에서 한 줄로 확인).
- 패킹 수량 합 규칙(계약상 출하 시점의 `PACKING_INCOMPLETE` 가 맡는다).

# Acceptance Criteria

## AC-0 — 🔴 bite 먼저 (고치기 전 트리)

- [ ] `ConfirmPickingServiceTest` 에 칸 셋, **지금 트리에서 빨간지** 확인:
      ① skuId 가 주문 라인과 다르면 거절 ② actualLocationId 가 미해소/비ACTIVE/다른 창고면 거절
      ③ 🔴 LOT 추적 주문 라인 + **비LOT SKU 를 적은 요청** + lotId null → `LOT_REQUIRED` (지금은 통과한다)
- [ ] `PackingService` 에 칸 셋: 남의 orderLineId · 다른 skuId · LOT 추적인데 lotId 없음.
- [ ] 🔴 **대조군**: 올바른 요청은 계속 통과한다(성공 칸이 없으면 «전부 거절» 도 초록이다).
- [ ] 출하 확정이 주문 라인 SKU 를 쓰는 것을 **테스트로** 한 칸 핀(«재고는 안전하다» 를 산문이 아니라 단언으로).

## AC-1 — 🔴 소유자 결정: 오류 코드

계약 §2.3·§3.1 의 Errors 목록에 SKU/라인 불일치용 코드가 **없다**. 갈래:

| | 코드 | 비고 |
|---|---|---|
| ⓐ | `VALIDATION_ERROR` (400) | 새 코드 없음. 단 «형식 오류» 와 «업무 규칙 위반» 이 한 코드로 섞인다 |
| ⓑ | 새 422 코드(예: `PICK_LINE_MISMATCH`) | 이 표의 다른 업무 규칙(`LOT_SUBSTITUTION_NOT_ALLOWED` 등)과 같은 결. 🔴 `platform/error-handling.md` 레지스트리 등록 + 가드(`check-error-code-registry`) 대상 |

위치 미해소는 기존 코드가 있는지(`LOCATION_*`) 먼저 찾아보고, 없으면 같은 결정에 얹는다.

## AC-2 — 고친다

- [ ] 계약 먼저, 그다음 코드. AC-0 칸이 전부 초록 + outbound-service 전체 `test` rc=0.
- [ ] 🔴 **데모 시드가 여전히 통과하는가** — `infra/demo/seed/seed-wms.sh` 의 피킹·패킹 요청이 새 검증에 걸리지 않는지 **읽어서** 확인(시드가 skuId 를 주문 라인과 다르게 보내면 신선 부팅이 멈춘다). 걸리면 시드부터 고친다.

# Related Specs

- `projects/wms-platform/specs/contracts/http/outbound-service-api.md` §2.3 (477–490행) · §3.1 (613–625행) · 오류 표(130–158행)
- `projects/wms-platform/specs/services/outbound-service/architecture.md`
- `tasks/done/TASK-MONO-724-…` (같은 메서드에 lot 대체 거절을 넣은 티켓 — 이 곁발견의 출처)

# Related Contracts

- `outbound-service-api.md` (위) · `outbound-events.md` §5(picking.completed 라인 모양) — 이벤트 모양은 안 바뀐다. 값이 사실에 맞게 될 뿐이다.
- `platform/error-handling.md` — AC-1 ⓑ 일 때.

# Edge Cases

| 상황 | 기대 |
|---|---|
| 요청 skuId 가 주문 라인과 같고 SKU 스냅샷이 없다(`findSku` 빈 값) | 지금 동작 유지 여부를 AC-0 에서 적는다 — 스냅샷 부재는 이 티켓이 아니라 read-model 의 문제 |
| 패킹 라인이 같은 orderLineId 를 두 박스에 나눠 담는다 | 허용(계약 627행) — 소속·SKU 만 본다 |
| LOT 추적 SKU 인데 주문 라인 lot 이 any-lot(NULL) | 피킹에서 lotId 를 받아야 한다(기존 `LOT_REQUIRED`) — 판정 SKU 만 주문 라인 것으로 바뀐다 |

# Failure Scenarios

1. 🔴 **LOT 판정을 요청 SKU 로 둔 채 skuId 대조만 넣는다** → 대조가 먼저 걸러 주긴 하지만, 두 검증의 순서가 바뀌는 날 우회가 돌아온다. 판정 SKU 는 **주문 라인** 것이어야 한다.
2. **시드를 안 읽고 머지한다** → 다음 신선 부팅에서 wms 시드가 피킹에서 멈추고 `TASK-MONO-706` 의 PASS 가 조용히 무효가 된다.
3. **«출하가 안전하니 급하지 않다» 로 닫는다** → 투영(admin)과 피킹 기록이 사실과 다른 SKU 를 말하는 상태가 남는다. 그것도 결함이다.
