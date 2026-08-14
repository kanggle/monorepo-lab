# Task ID

TASK-BE-586

# Title

출고가 **`PICKING` 을 넘지 못한다** — 계약 §2.1 의 picking-request 생성이 미구현(405)이고 사가도 행을 만들지 않는다

# Status

ready

# Owner

wms-platform

# Task Tags

- bug
- demo
- contract-drift

---

# 배경 — `TASK-BE-584` AC-3/AC-5 가 발굴

`TASK-BE-584` 는 `ADR-MONO-065` 의 격리 표면 **2개**(orders · shipments)를 차등 대조군으로 재려
했다. `orders` 는 성립했고 `shipments` 는 **판정 불가**로 남았다 — `admin_shipment_summary` 가
0행이라 두 테넌트가 같은 0 을 내고, 그 0 은 *"격리가 동작한다"* 와 *"테이블이 비었다"* 를 구별하지
못한다.

제품 API 로 채우려 했으나(§ dbexec 금지) **출고를 `SHIPPED` 까지 몰 수 없었다.**

## 실측 (2026-08-14, 재시드 직후 신선 볼륨)

```
outbound_order      SO-DEMO-0001 | status=PICKING | tenant_id=demo-corp
outbound_saga       status = RESERVED
picking_request     0행        ← 사가가 만들지 않았다
picking_confirmation 0행 · packing_unit 0행 · shipment 0행
admin_shipment_summary 0행
```

운영자 토큰(`OUTBOUND_WRITE` 보유)으로 계약대로 재진입을 시도:

```
POST /api/v1/outbound/orders/{id}/picking-requests
  → HTTP 405 METHOD_NOT_ALLOWED
```

### 원인 ① — 계약이 적은 엔드포인트가 **구현에 없다**

`specs/contracts/http/outbound-service-api.md` § 2.1 은 이 엔드포인트를 명시한다
(*"This endpoint exists for **re-entry / manual saga recovery**"*). 그러나 구현 전수:

```
OrderQueryController:111   @GetMapping("/{id}/picking-requests")      ← GET 뿐
PickingController:42       @RequestMapping("/api/v1/outbound/picking-requests")
                           (= confirmations 루트. orders 하위 POST 아님)
```

⇒ **POST 는 어디에도 없다.** 405 는 라우팅의 정직한 응답이고, 계약이 사실이 아니다.

### 원인 ② — 사가가 `picking_request` 행을 만들지 않는다

계약 § 5 는 `POST /orders` 가 `outbound.picking.requested` 를 발행한다고 적고, 실제로 예약은
일어났다(`outbound_saga=RESERVED`). 그런데 `picking_request` 는 **0행**이다.
⇒ `POST /picking-requests/{id}/confirmations` 에 넘길 **id 자체가 존재하지 않는다.**

두 원인이 겹쳐 **어떤 경로로도 피킹 확정에 도달할 수 없다** — 정상 흐름(사가)도, 계약이 약속한
수동 재진입도.

🔵 `TASK-MONO-528` 이 *"시드가 멈추는 진짜 지점은 피킹 확정(시드가 안 부른다)"* 이라고 적어 뒀는데,
**부르려 해도 부를 수 없다**는 것이 이 티켓의 실측이다. "시드가 안 부른다" 는 원인의 절반이었다.

## 파급

- `ADR-MONO-065` 의 **격리 2개 표면 중 `shipments` 가 라이브 검증 불가** — 계약의 절반이 행사되지
  않은 채로 남는다(`TASK-BE-584` AC-3)
- 데모가 `RESERVED` 에서 멈춘다 — 콘솔 출고 화면이 출하까지 가는 흐름을 보여주지 못한다
- `admin_shipment_summary` · `admin_throughput_outbound_daily` 는 **구조적으로 영원히 0행**이다
  (프로젝션 결함이 아니라 상류가 안 생긴다)

---

# Goal

출고 주문이 **제품 API 만으로** `PICKING → PICKED → PACKED → SHIPPED` 에 도달한다. 그 결과
`admin_shipment_summary` 에 행이 생기고, `ADR-MONO-065` 의 shipments 격리 표면을 차등 대조군으로
잴 수 있게 된다.

---

# Scope

## In Scope

- **AC-0 실측**: 사가가 `picking_request` 를 만드는 지점 전수 — 만드는 코드가 있는데 안 도는 것인지,
  애초에 없는 것인지. 🔴 **두 답은 고치는 법이 다르다**(배선 vs 미구현).
  `outbound.picking.requested` 컨슈머 · 발행 측 · inventory 예약 콜백 경로를 함께 본다
- **AC-1 결정 + 구현**: 계약 § 2.1 을 **구현할지**, 아니면 **계약에서 걷을지**.
  🔴 사가가 행을 만든다면 § 2.1 은 재진입용이므로 없어도 흐름은 성립한다 ⇒ 둘은 독립 결정이다
- **AC-2**: 출하까지 도달하는 경로를 시드(또는 데모 흐름)에 반영 — `seed-wms.sh` 가 출고를
  어디까지 미는지가 데모의 완성도다

## Out of Scope

- `dbexec` 로 `shipment` · `admin_shipment_summary` 행을 만드는 것 — 제품이 만들 수 없는 행 위의
  검증은 무효다(`TASK-BE-581` § Out of Scope 가 이미 금지)
- `ADR-MONO-065` 의 격리 로직 변경 — 이 티켓은 **잴 수 있게** 만드는 것이지 축을 바꾸지 않는다
- TMS/배송 외부 연동 — `ADR-MONO-053` §D8 이후 outbound 는 TMS 를 부르지 않는다

---

# Acceptance Criteria

- [ ] **AC-0 (실측)** — `picking_request` 를 만드는 코드 경로 전수. 없으면 "없다" 를,
      있는데 안 돌면 **왜 안 도는지**를 실측으로 적는다(로그·오프셋·컨슈머 상태)
- [ ] **AC-1 (결정 + 구현)** — 계약 § 2.1 구현 / 계약에서 삭제 중 하나 + 근거.
      계약과 구현이 **일치**하는 것이 이 AC 의 통과 조건이다
- [ ] **AC-2 (라이브)** — 신선 데모에서 운영자 토큰만으로 `SHIPPED` 도달.
      `shipment` ≥1행 · `admin_shipment_summary` ≥1행을 DB 로 확인
- [ ] **AC-3 (`TASK-BE-584` 의 열린 칸을 닫는다)** — shipments 표면의 **차등 대조군**:
      `demo-corp` ≥1 vs 타 테넌트(`ecommerce`) 0. 🔴 이것이 이 티켓의 진짜 산출물이다 —
      065 격리 계약의 나머지 절반이 여기서 처음 행사된다
- [ ] **AC-4 (회귀)** — 흐름이 다시 끊기면 무는 가드. 🔴 `picking_request` 0행을 통과시키는
      가드는 이 결함을 못 본다(현재 상태가 그대로 초록이므로) — **행이 생기는 것**을 단언할 것

# Related Specs

- `projects/wms-platform/tasks/ready/TASK-BE-584-*.md` § AC-3 · AC-5 — 이 티켓의 출처
- `projects/wms-platform/specs/services/outbound-service/architecture.md` — 사가 단계 정의
- `TASK-MONO-528` — *"시드가 멈추는 지점은 피킹 확정"* (원인의 절반만 적혀 있었다)

# Related Contracts

- `projects/wms-platform/specs/contracts/http/outbound-service-api.md` § 2.1 · § 2.3 · § 3 · § 4 · § 5
- `projects/wms-platform/specs/contracts/events/outbound-events.md` — `outbound.picking.requested`

# Edge Cases

- `admin_shipment_summary` 는 프로젝션이라 원본보다 뒤쳐질 수 있다(`TASK-BE-583` § Edge Cases) —
  AC-2/AC-3 판정 전에 **DB 행 존재를 먼저 확정**할 것
- 재고 예약은 이미 성립한다(`TASK-MONO-528` 실측) — 이 티켓이 여는 것은 **그 다음 단계**다
- 🔴 `demo.env` 미소스 기동은 전건 401 을 낸다 — **도메인 판정이 아니다**

# Failure Scenarios

- **계약 § 2.1 만 지우고 닫는다** → 계약은 일치하지만 흐름은 여전히 `PICKING` 에서 멈춘다.
  AC-2 가 막는다
- **시드에 한 줄 더 넣어 화면만 채운다** → 사가가 행을 안 만드는 원인이 남아 다음 리셋에 재발
- **`dbexec` 로 shipment 를 만든다** → 제품이 만들 수 없는 상태. 그 위의 065 검증이 전부 무효

# Definition of Done

- [ ] AC-0 ~ AC-4 전부
- [ ] `TASK-BE-584` AC-3 의 shipments 칸이 차등으로 닫힌다
- [ ] Ready for review
