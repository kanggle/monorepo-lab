# Domain: wms

> **Activated when**: `PROJECT.md` declares `domain: wms`.

---

## Scope

창고 관리 시스템(Warehouse Management System). 물류 센터의 **입고 → 적치 → 보관 → 피킹 → 패킹 → 출하** 전 과정을 관리하는 시스템.

이 도메인은 "물건이 창고 안에서 어디에 있고, 어디로 옮겨야 하는가"에 집중한다. 상류(ERP/주문)와 하류(TMS/배송)는 연동 경계로만 다루며, 그 자체를 구현하지 않는다.

---

## Bounded Contexts (표준)

WMS 도메인 프로젝트가 일반적으로 가지는 bounded context 묶음. 실제 서비스 분할은 트래픽·소유권·변경 빈도에 따라 달라질 수 있다.

| Bounded Context | 책임 |
|---|---|
| **Inbound** | 입고 예정(ASN) 수신, 검수(inspection), 적치(putaway) 지시·확인 |
| **Inventory** | 로케이션별 재고 잔량, 재고 이동(transfer), 재고 조정(adjustment), 실시간 재고 조회 |
| **Outbound** | 출고 오더 수신, 피킹(picking) 지시·확인, 패킹(packing), 출하(shipping) 확인 |
| **Master Data** | 창고(warehouse), 구역(zone), 로케이션(location), SKU, 단위(UOM), 거래처(partner) |
| **Admin / Operations** | 대시보드, KPI, 사용자·권한 관리, 운영 설정 |
| **Notification** (선택) | 입출고 완료·재고 부족·이상 감지 등 이벤트 기반 알림 |

각 context는 자체 데이터 저장소를 가지며, context 간 통신은 **이벤트** 또는 **잘 정의된 내부 HTTP**로만 이루어진다.

---

## Ubiquitous Language

- **ASN (Advance Shipment Notice)** — 입고 예정 통보. 공급자가 "어떤 물건을 언제 보내겠다"고 알리는 선행 정보. 입고 서비스의 시작점.
- **Inspection** — 입고된 물품의 수량·품질·파손 여부를 검수하는 행위. 검수 결과에 따라 적치 또는 반품 결정.
- **Putaway** — 검수 완료된 물품을 지정된 로케이션에 배치하는 행위. 적치 규칙(같은 SKU끼리, FIFO 등)에 따라 로케이션 결정.
- **Location** — 창고 내 물리적 저장 위치. 계층 구조: Warehouse > Zone > Aisle > Rack > Level > Bin. 유일한 코드로 식별.
- **Zone** — 창고를 논리적으로 나눈 구역. 온도대(상온/냉장/냉동), 용도(벌크/소량/반품), 피킹 전략에 따라 분류.
- **SKU (Stock Keeping Unit)** — 재고 관리의 최소 단위 식별자. 동일 상품이라도 색상·사이즈 등 속성이 다르면 별도 SKU.
- **Lot** — 같은 SKU라도 입고 일자·제조 일자·유효기한이 다른 물량을 구분하는 단위. FIFO/FEFO 관리의 기준.
- **Inventory** — 특정 로케이션에 있는 특정 SKU(+Lot)의 가용 수량. 핵심 엔티티.
- **Picking** — 출고 오더에 따라 지정 로케이션에서 필요 수량을 꺼내는 행위. 피킹 방식: 단건(discrete), 다건(batch), 존(zone).
- **Packing** — 피킹된 물품을 출하 단위(박스/팔레트)로 포장하는 행위.
- **Shipping** — 패킹 완료된 물품을 운송 수단에 인계하는 행위. 출하 확인 시점에 재고 차감 확정.
- **Adjustment** — 실사, 파손, 분실 등으로 시스템 재고와 실물 재고의 차이를 보정하는 행위. 사유 기록 필수.
- **Transfer** — 같은 창고 내 또는 창고 간 재고를 이동하는 행위. 출발 로케이션 차감 + 도착 로케이션 증가가 원자적으로 수행.
- **UOM (Unit of Measure)** — 수량 단위. EA(개), BOX(박스), PLT(팔레트) 등. SKU마다 기본 UOM과 변환 계수를 가짐.

이 용어들은 코드·API·문서에서 일관되게 사용되어야 한다.

---

## Standard Error Codes

WMS 도메인에서 공통으로 발생하는 에러는 [../../platform/error-handling.md](../../platform/error-handling.md)의 전역 카탈로그에 등록되어야 한다. 이 도메인 특유의 코드:

### Inbound
- `ASN_NOT_FOUND` — 조회 대상 입고 예정이 존재하지 않음
- `ASN_ALREADY_CLOSED` — 이미 마감된 ASN에 대한 작업 시도
- `INSPECTION_QUANTITY_MISMATCH` — 검수 수량이 ASN 수량과 불일치
- `PUTAWAY_LOCATION_FULL` — 지정 로케이션에 적치 가능 용량 초과

### Inventory
- `INVENTORY_NOT_FOUND` — 해당 로케이션·SKU 조합에 재고 없음
- `INSUFFICIENT_STOCK` — 요청 수량이 가용 재고를 초과
- `LOCATION_NOT_FOUND` — 존재하지 않는 로케이션
- `ADJUSTMENT_REASON_REQUIRED` — 재고 조정 시 사유 누락
- `TRANSFER_SAME_LOCATION` — 출발지와 도착지가 동일한 이동 시도

### Outbound
- `ORDER_NOT_FOUND` — 출고 오더가 존재하지 않음
- `ORDER_ALREADY_SHIPPED` — 이미 출하 완료된 오더에 대한 작업 시도
- `PICKING_QUANTITY_EXCEEDED` — 피킹 수량이 오더 수량 초과
- `PACKING_INCOMPLETE` — 패킹이 완료되지 않은 상태에서 출하 시도

### Master Data
- `SKU_NOT_FOUND` — 존재하지 않는 SKU
- `WAREHOUSE_NOT_FOUND` — 존재하지 않는 창고
- `LOCATION_CODE_DUPLICATE` — 이미 존재하는 로케이션 코드
- `ZONE_NOT_FOUND` — 존재하지 않는 구역

---

## Integration Boundaries

### 외부(플랫폼 경계 바깥)
- **ERP** — 입고 예정(ASN) 수신, 출고 오더 수신, 재고 실적 보고 (`integration-heavy` trait 규칙을 따른다)
- **TMS (Transportation Management System)** — 출하 정보 전달, 배차 요청
- **바코드/RFID 스캐너** — 입고 검수, 피킹 확인, 적치 확인 시 물품·로케이션 식별
- **알림 채널** — 재고 부족, 입출고 완료 등 운영 이벤트 알림 (슬랙/이메일)

### 내부(같은 프로젝트 내 다른 서비스)
- Inbound → Inventory: 적치 확인 시 재고 증가 이벤트 발행
- Outbound → Inventory: 피킹 지시 시 재고 할당(reserve), 출하 확인 시 재고 확정 차감
- Master Data는 모든 서비스가 참조하는 공통 데이터. 변경 시 이벤트로 캐시 무효화.
- Admin은 모든 서비스의 read 경로 + 운영 설정 변경 경로를 가짐.

### 내부 이벤트 카탈로그 (권장)
- `<prefix>.inbound.asn.received` / `.inspection.completed` / `.putaway.completed`
- `<prefix>.inventory.adjusted` / `.transferred` / `.reserved` / `.released`
- `<prefix>.outbound.order.received` / `.picking.completed` / `.packing.completed` / `.shipped`
- `<prefix>.master.sku.created` / `.location.created` / `.location.updated`
- `<prefix>.alert.low-stock` / `.alert.anomaly-detected`

---

## Mandatory Rules

### W1. 재고 변동은 반드시 트랜잭션으로 보호
적치, 피킹, 조정, 이동 등 재고 수량이 변하는 모든 경로는 DB 트랜잭션으로 원자성을 보장한다. 부분 변경(출발지만 차감, 도착지는 미증가) 상태가 발생해서는 안 된다.

### W2. 모든 재고 변동에는 이력(history) 기록
재고가 변할 때마다 변동 사유(입고/피킹/조정/이동), 변동 전후 수량, 수행자, 타임스탬프를 이력 테이블에 기록한다. 이력은 삭제·수정 불가(append-only).

### W3. 로케이션 코드는 전체 창고에서 유일
창고 > 구역 > 위치의 계층 구조를 코드로 표현하되, 로케이션 코드 자체가 전체 시스템에서 유일해야 한다. 예: `WH01-A-01-02-03`.

### W4. 피킹은 할당(reserve) → 확인(confirm) 2단계
피킹 지시 시 가용 재고에서 할당(reserved) 처리하고, 실제 피킹 확인 시 확정 차감한다. 할당 상태의 재고는 다른 피킹 지시에 사용할 수 없다.

### W5. 출하 확인 전까지 재고 차감은 잠정적
출하(shipping) 확인이 완료되어야 재고가 최종 차감된다. 피킹·패킹 단계에서는 reserved/allocated 상태로만 관리.

### W6. Master Data 변경은 참조 무결성 검증 후 수행
SKU 삭제 시 해당 SKU의 재고가 0인지, 로케이션 비활성화 시 해당 로케이션에 재고가 없는지 등 참조 무결성을 반드시 검증한다.

---

## Forbidden Patterns

- ❌ **재고 수량을 직접 `UPDATE`로 변경** (변동 이력 없이)
- ❌ **피킹 시 할당(reserve) 없이 즉시 차감** (동시 피킹 충돌 위험)
- ❌ **로케이션 없이 재고를 관리** (위치 불명 재고 발생)
- ❌ **입고 검수 없이 적치** (수량·품질 불일치 시 추적 불가)
- ❌ **마스터 데이터(SKU/로케이션)를 재고가 있는 상태에서 삭제**
- ❌ **재고 조정에 사유(reason) 없이 수량 변경**

---

## Required Artifacts

1. **Bounded context 맵** — 각 context의 책임·소유 데이터·통신 방향. 위치: `specs/services/` 전반 또는 `platform/service-boundaries.md`
2. **재고 상태 다이어그램** — available / reserved / allocated / damaged 등 재고 상태 전이. 위치: `specs/services/inventory-service/state-machines/inventory-status.md`
3. **입고 워크플로** — ASN 수신 → 검수 → 적치의 상태 전이. 위치: `specs/services/inbound-service/workflows/inbound-flow.md`
4. **출고 워크플로** — 오더 수신 → 피킹 → 패킹 → 출하의 상태 전이. 위치: `specs/services/outbound-service/workflows/outbound-flow.md`
5. **에러 코드 등록** — 위 Standard Error Codes가 [../../platform/error-handling.md](../../platform/error-handling.md)에 존재
6. **로케이션 코드 체계** — 코드 포맷, 계층 구조, 명명 규칙

---

## Interaction with Common Rules

- [../../platform/architecture.md](../../platform/architecture.md)의 서비스 경계 원칙을 따르되, 위 bounded context 구분을 참조한다.
- [../../platform/error-handling.md](../../platform/error-handling.md)에 위 Standard Error Codes가 등록되어야 한다.
- [../traits/transactional.md](../traits/transactional.md)의 트랜잭션·멱등성 규칙이 모든 재고 변동 경로에 적용된다.
- [../traits/integration-heavy.md](../traits/integration-heavy.md)의 외부 연동 규칙이 ERP·TMS·스캐너 연동에 적용된다.

---

## Checklist (Review Gate)

- [ ] 모든 재고 변동이 트랜잭션으로 보호되는가? (W1)
- [ ] 재고 변동 시 이력이 append-only로 기록되는가? (W2)
- [ ] 로케이션 코드가 시스템 전체에서 유일한가? (W3)
- [ ] 피킹이 할당 → 확인 2단계로 동작하는가? (W4)
- [ ] 출하 확인 전까지 재고가 잠정적 상태인가? (W5)
- [ ] 마스터 데이터 삭제 시 참조 무결성이 검증되는가? (W6)
- [ ] 재고 조정에 사유가 기록되는가?
- [ ] Bounded context 맵과 워크플로 문서가 존재하는가?
- [ ] 표준 에러 코드가 플랫폼 카탈로그에 등록되어 있는가?
