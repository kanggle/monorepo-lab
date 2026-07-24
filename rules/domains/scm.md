# Domain: scm

> **Activated when**: `PROJECT.md` declares `domain: scm`.

---

## Scope

Supply Chain Management — 수요예측·조달(procurement)·생산계획·운송·재고가시성·정산을 통합 관리하는 공급망 시스템. 핵심은 **cross-functional 흐름** — 외부 공급자(Supplier) 로부터 시작해 운송(Carrier) 을 거쳐 창고(Warehouse) 로 도착, 다시 생산/판매로 이어지고, 마지막에 정산(Settlement)으로 닫히는 다단계 파이프라인.

이 도메인은 "물건과 자금이 공급망의 어느 단계에 있고, 다음 단계로 어떻게 넘어가는가"에 집중한다. 단일 창고 운영(`wms`)은 스코프 밖 — wms 는 한 노드의 내부 동선이고, scm 은 노드 간·기업 간 흐름이다. 전사 회계·HR(`erp`) 도 스코프 밖 — scm 은 정산까지 책임지되 GL 분개·결산은 erp 가 받아간다.

---

## Bounded Contexts (표준)

scm 도메인 프로젝트가 일반적으로 가지는 bounded context 묶음. 실제 서비스 분할은 트래픽·소유권·변경 빈도에 따라 달라진다.

| Bounded Context | 책임 |
|---|---|
| **Procurement** | PO(구매 발주) 작성·확정·취소, supplier 와의 발주 ack 교환, ASN 수신 후 입고 정합성 확인 |
| **Supplier** | supplier 마스터 (회사 정보, 계약, SLA, 카탈로그), supplier 별 통합 adapter, catalog sync |
| **Demand Planning** | 수요 예측, 안전재고/재주문점 계산, 발주 추천, batch 단위 재계산 |
| **Logistics Coordination** | shipment(운송) 단위 생성·조회, carrier 연동, ETA/추적, 출발지·도착지 라우팅 |
| **Inventory Visibility** | cross-node(자사 창고들 + supplier 보유 + in-transit) 재고 가시성. read-model. wms 등에서 받는 스냅샷 통합 |
| **Settlement** | 정산 기간 생성·잠금, PO ↔ ASN ↔ invoice reconciliation, 차감/지급 계산, ERP 로 분개 feed |
| **Admin / Operations** | 운영 콘솔, supplier 등록·제재, 정책(리드타임/안전재고) 설정, 대시보드 |

각 context 는 자체 데이터 저장소를 가지며, context 간 통신은 **이벤트** 또는 **잘 정의된 내부 HTTP** 로만 이루어진다. 외부 공급자/운송사 통합은 반드시 adapter layer 를 거친다.

---

## Ubiquitous Language

- **PO (Purchase Order)** — 구매 발주서. 구매자 → 공급자 발행. 상태 머신: `DRAFT → SUBMITTED → ACKNOWLEDGED → CONFIRMED → (PARTIALLY_RECEIVED →) RECEIVED → SETTLED → CLOSED`.
- **ASN (Advance Shipment Notice)** — 입고 예정 통보. 공급자가 "PO X 에 대해 어떤 물건을 언제 보내겠다"고 알리는 선행 정보. wms 도메인의 ASN 과 의미는 같으나, scm 에서는 supplier 시스템과의 통합 진입점.
- **Supplier** — 공급자(벤더). 자체 계약·SLA·결제 조건을 가짐. 활성/정지/계약만료 상태.
- **Buyer** — 구매 담당자(또는 자동 발주 봇). PO 작성·승인 권한.
- **Carrier** — 운송사. 자체 운송 capacity·SLA·요금표를 가짐.
- **Shipment** — 운송 단위. 1 PO 가 N shipment 로 쪼개질 수 있고, 1 shipment 가 N PO 를 묶을 수도 있음.
- **Lead Time** — 발주~입고 소요 시간. supplier × SKU 조합별로 관리. 수요 예측의 입력.
- **Reorder Point** — 재주문 시점. 가용 재고가 이 수준 이하로 떨어지면 발주 추천.
- **Safety Stock** — 안전 재고. 수요 변동·리드타임 변동에 대비한 버퍼.
- **Demand Forecast** — 수요 예측치. 일/주/월 단위 batch 산출.
- **Settlement Period** — 정산 기간 (예: 월별). lock 후에는 그 기간의 거래가 immutable.
- **Reconciliation** — 정산 대조. PO 의 confirmed quantity ↔ ASN 의 received quantity ↔ supplier invoice amount 가 일치하는지 검증.
- **Catalog Sync** — supplier 카탈로그(상품 마스터)를 주기적으로 받아오는 워크로드.
- **3PL (Third-Party Logistics)** — 외부 물류 위탁사. 자사 창고 외부에 재고가 위치할 때 inventory visibility 의 노드.
- **In-transit** — 출발했으나 도착 전인 운송 중 재고. inventory visibility 의 한 상태.
- **SLA** — supplier 또는 carrier 와의 service level agreement. lead time, 정시 배송률, 응답 시간 등.

이 용어들은 코드·API·문서에서 일관되게 사용되어야 한다.

---

## Standard Error Codes

scm 도메인에서 공통으로 발생하는 에러는 [../../platform/error-handling.md](../../platform/error-handling.md) 의 전역 카탈로그에 등록되어야 한다. 본 도메인 특유의 코드:

### Procurement
- `PO_NOT_FOUND` — 존재하지 않는 PO
- `PO_ALREADY_CONFIRMED` — 이미 확정된 PO 의 수정/취소 시도
- `PO_QUANTITY_EXCEEDED` — supplier ack 수량이 PO 수량 초과
- `PO_STATUS_TRANSITION_INVALID` — 허용되지 않는 PO 상태 전이 (422). 플랫폼 레지스트리는 TASK-MONO-352 전까지 이 코드를 `PO_STATE_TRANSITION_INVALID` 라는 **아무도 발행하지 않는 오타 문자열**로 등록해 두었다 — 코드·계약·이 파일은 처음부터 `_STATUS_` 로 일치했다
- `REQUEST_ERROR` — `ResponseStatusException` 핸들러의 폴백 코드. **상태는 예외의 것을 그대로 통과**시키고 코드만 고른다(401→`UNAUTHORIZED`, 403→`PERMISSION_DENIED`, 그 외→`REQUEST_ERROR`). 도메인 개념이 아니라 프레임워크 경유 코드다
- `ASN_OVERRECEIPT` — ASN 수량이 PO 잔여 수량 초과
- `CATALOG_SKU_UNKNOWN` — supplier 카탈로그에 없는 SKU 로 PO 작성 시도

### Supplier
- `SUPPLIER_NOT_FOUND` — 존재하지 않는 supplier
- `SUPPLIER_INACTIVE` — 비활성 또는 계약만료 supplier 에 PO 시도
- `SUPPLIER_CONTRACT_EXPIRED` — 계약 만료 후 거래 시도
- `SLA_VIOLATION` — supplier/carrier 의 SLA 미달 (감지/기록용)
- `CATALOG_SYNC_TIMEOUT` — supplier 카탈로그 동기화 응답 시간 초과

### Demand Planning
- `FORECAST_PERIOD_INVALID` — 잘못된 예측 기간 (과거·미래 너무 멀음)
- `REORDER_POINT_NEGATIVE` — 음수 재주문점 설정 시도
- `FORECAST_DATA_INSUFFICIENT` — 예측에 필요한 과거 데이터 부족
- `SAFETY_STOCK_BELOW_MINIMUM` — 안전 재고가 정책 최소치 미만

### Logistics
- `DISPATCH_NOT_FOUND` — 존재하지 않는 dispatch (운영자 inspect / `:retry` 대상 없음, SCM-BE-042)
- `CARRIER_UNROUTABLE` — shipment 에 vendor 를 라우팅할 수 없음 (null carrierCode + 기본 vendor 없음 / 미매핑 region; `CarrierRouter` degrade, SCM-BE-043 예약, SCM-BE-042 등록)
- `DISPATCH_ALREADY_COMPLETED` — 이미 DISPATCHED 된 dispatch 의 전이 시도 (409, SCM-BE-042)
- `SHIPMENT_NOT_FOUND` — 존재하지 않는 shipment
- `CARRIER_TIMEOUT` — carrier API 응답 시간 초과
- `ROUTE_UNAVAILABLE` — 출발지·도착지 간 경로 없음
- `ETA_EXPIRED` — ETA 가 이미 지났는데 도착 미확인

### Inventory Visibility
- `NODE_UNREACHABLE` — 외부 node (creditor 창고, 3PL) snapshot 수신 실패
- `SNAPSHOT_STALE` — 최신 스냅샷이 staleness threshold 초과
- `NODE_TYPE_CONFLICT` — 등록 요청의 externalId 가 이미 다른 node type 으로 등록되어 있음 (예: wms 창고를 3PL 로 재등록 시도)

### Settlement
- `SETTLEMENT_PERIOD_LOCKED` — 잠긴 정산 기간 수정 시도
- `RECONCILIATION_DISCREPANCY` — PO ↔ ASN ↔ invoice 수량/금액 불일치
- `INVOICE_AMOUNT_MISMATCH` — supplier invoice 금액이 PO 금액과 불일치
- `SETTLEMENT_NOT_READY` — 미완료 입고가 남아있어 정산 불가

### Cross
- `PERMISSION_DENIED` — 운영자/buyer 권한 없음

---

## Integration Boundaries

### 외부 (플랫폼 경계 바깥)

- **Supplier ERP / EDI / API** — PO 송신, ASN 수신, invoice 수신, catalog sync. supplier 별 adapter. **idempotency key** 와 **circuit breaker** 필수 (`integration-heavy` trait).
- **Carrier API** — shipment 생성, 추적 이벤트 수신, ETA 업데이트.
- **Bank / Payment Gateway** — settlement 결과의 실 지급.
- **ERP** — settlement 결과의 GL 분개 feed (out-bound). scm 은 정산 계산까지, ERP 가 회계 처리.
- **wms-platform (모노레포 동거 가능)** — wms 의 inventory snapshot / receipt 이벤트를 inventory-visibility 가 구독.
- **알림 채널** — supplier SLA violation, settlement 지연, reorder 추천 등 운영 알림.

### 내부 (같은 프로젝트 내 다른 서비스)

- gateway → 모든 service: OIDC token 검증, `tenant_id=scm`, `X-Account-Id` 헤더 전파.
- procurement → supplier: PO 작성 시 supplier 정보 조회, contract/SLA 확인.
- procurement → demand-planning: 발주 추천 조회 (reorder point 도달 SKU 목록).
- procurement → logistics: PO confirm 시 shipment 생성 트리거.
- logistics → inventory-visibility: shipment 출발/도착 이벤트로 in-transit 상태 업데이트.
- inventory-visibility ← (외부 wms / 3PL): snapshot 이벤트 구독.
- settlement → procurement: PO 의 received 상태 조회.
- settlement → ERP: 분개 데이터 batch 발신.

### 내부 이벤트 카탈로그 (권장)

- `<prefix>.procurement.po.submitted` / `.po.acknowledged` / `.po.confirmed` / `.po.received` / `.po.closed`
- `<prefix>.procurement.asn.received`
- `<prefix>.supplier.created` / `.deactivated` / `.contract.expired` / `.sla.violated`
- `<prefix>.supplier.catalog.synced`
- `<prefix>.demand.forecast.completed` / `.reorder.recommended`
- `<prefix>.logistics.shipment.created` / `.dispatched` / `.in-transit` / `.delivered`
- `<prefix>.inventory.snapshot.published` (cross-node)
- `<prefix>.settlement.period.opened` / `.period.locked` / `.reconciliation.completed` / `.discrepancy.detected`

---

## Mandatory Rules

### S1. Multi-leg 흐름의 단계별 상태 전이는 멱등 + 트랜잭션 보호
PO → ASN → receipt → invoice → settled 의 각 단계 전이는 (a) idempotent (재시도해도 동일 결과), (b) 트랜잭션 안에서 상태 변경 + 이벤트 발행 (outbox 패턴). 중간 단계에서 부분 적용 상태 발생 금지.

### S2. Supplier / Carrier 외부 호출은 idempotency key 부착
catalog sync, PO 송신, ASN ack, shipment 생성 등 외부 시스템에 발신하는 모든 요청은 idempotency key 를 포함한다. supplier 시스템이 동일 key 의 재요청을 중복 처리하지 않도록.

### S3. Settlement 기간은 lock 후 immutable
정산 기간이 lock 된 후에는 해당 기간의 PO/ASN/invoice 데이터를 수정할 수 없다. 수정이 필요하면 다음 정산 기간에 보정 분개로 처리한다. lock 자체는 audit trail 에 기록.

### S4. Demand Forecast 는 reproducibility 보장
같은 입력 데이터(과거 거래·외부 신호) + 같은 모델 버전 → 같은 forecast 결과. forecast 결과에는 입력 데이터 스냅샷의 hash + 모델 버전을 함께 저장.

### S5. Cross-node Inventory Visibility 는 eventual consistency 허용
inventory-visibility 의 read-model 은 외부 노드(wms / 3PL)의 최신 스냅샷 도착에 따라 eventual consistency. 단, 각 노드별 staleness threshold 를 모니터링하고 임계 초과 시 `SNAPSHOT_STALE` 경보. 절대 inventory-visibility 를 trustworthy single-source 로 PO 발주 결정에 직접 사용하지 말 것 (PO 결정은 procurement 의 자체 데이터 + demand recommendation 으로).

### S6. Supplier credentials / contract 데이터는 암호화
supplier API key, EDI 인증 정보, 계약 가격표 등은 secrets manager / DB 컬럼 암호화. 평문 저장 금지.

### S7. State transition audit trail
PO confirm, supplier 비활성화, settlement period lock, contract 변경 등 주요 상태 전이는 actor + timestamp + before/after 를 audit log 에 기록. application 레벨 audit_log 테이블 또는 동등한 저장소.

### S8. Reconciliation discrepancy 는 자동 close 금지
reconciliation 결과 PO ↔ ASN ↔ invoice 수량·금액 불일치가 감지되면 운영자 검토 큐에 진입한다. 자동 정산 close 하지 말 것 — 회계 누락/과다 위험.

---

## Forbidden Patterns

- ❌ **단일 창고 내부 동선 로직을 scm 에 결합** — 입고 검수, 적치, 피킹은 wms 도메인. scm 은 노드 간/기업 간 흐름만.
- ❌ **잠긴 settlement 기간의 데이터 mutation** (S3 위반) — 보정 분개로만 수정.
- ❌ **PO confirm 시 supplier ack 없이 즉시 shipment 생성** — supplier 가 받지 못한 발주는 shipment 가 무의미.
- ❌ **Supplier credentials 를 코드 / config 에 하드코딩** (S6 위반).
- ❌ **Reconciliation discrepancy 자동 close** (S8 위반).
- ❌ **외부 API 호출에 idempotency key 누락** (S2 위반) — 재시도 시 중복 처리 위험.
- ❌ **inventory-visibility 의 read-model 을 PO 결정에 single-source 로 사용** (S5 위반).
- ❌ **demand forecast 에 입력 hash / 모델 버전 미기록** (S4 위반) — reproducibility 깨짐.

---

## Required Artifacts

1. **PO 상태 다이어그램** — `DRAFT → SUBMITTED → ACKNOWLEDGED → CONFIRMED → (PARTIALLY_RECEIVED →) RECEIVED → SETTLED → CLOSED`. 위치: `specs/services/<procurement-service>/state-machines/po-status.md`.
2. **Settlement workflow** — period 생성·잠금·reconciliation·외부 ERP feed. 위치: `specs/services/<settlement-service>/workflows/settlement-flow.md`.
3. **Supplier integration adapter map** — supplier 시스템 종류(EDI/REST/SFTP) 별 adapter 와 책임 경계. 위치: `specs/services/<supplier-service>/integration/adapters.md`.
4. **Reconciliation flow** — PO/ASN/invoice 매칭 알고리즘과 discrepancy 분류. 위치: `specs/services/<settlement-service>/workflows/reconciliation.md`.
5. **Demand forecast pipeline** — 입력 → 모델 → 출력 + 스냅샷/버전 저장 정책. 위치: `specs/services/<demand-planning-service>/pipelines/forecast.md`.
6. **에러 코드 등록** — 위 Standard Error Codes 가 [../../platform/error-handling.md](../../platform/error-handling.md) 에 존재.
7. **Bounded context 맵** — 위 7 contexts 의 데이터 소유와 통신 방향 도식. 위치: `specs/services/` 전반 또는 `knowledge/architecture/context-map.md`.

> **Library 경계**: 본 파일에는 구체 service 명을 직접 적지 않는다 — `<procurement-service>` 등 placeholder 또는 bounded context 이름 사용. 실제 service 명은 각 프로젝트의 `PROJECT.md` Service Map 과 `specs/services/` 가 담당.

---

## Interaction with Common Rules

- [../../platform/architecture.md](../../platform/architecture.md) 의 서비스 경계 원칙을 따르되, 위 bounded context 구분을 참조한다.
- [../../platform/error-handling.md](../../platform/error-handling.md) 에 위 Standard Error Codes 가 등록되어야 한다.
- [../traits/transactional.md](../traits/transactional.md) 의 트랜잭션·멱등성·outbox 규칙이 S1·S2·S7 에 직접 적용.
- [../traits/integration-heavy.md](../traits/integration-heavy.md) 의 circuit breaker / retry / backoff / DLQ 규칙이 supplier·carrier·ERP·bank 연동에 적용.
- batch-heavy trait 활성화 시 [../traits/](../traits/) 의 해당 파일이 settlement / demand forecast / catalog sync 의 chunking·restartability·체크포인트 규칙을 추가한다.

---

## Checklist (Review Gate)

- [ ] PO / settlement / shipment 등 다단계 상태 전이가 멱등 + 트랜잭션으로 보호되는가? (S1)
- [ ] 외부 supplier / carrier 호출에 idempotency key 가 부착되는가? (S2)
- [ ] Settlement 기간 lock 후 데이터 immutable 한가? (S3)
- [ ] Demand forecast 결과에 입력 hash + 모델 버전이 함께 저장되는가? (S4)
- [ ] Inventory-visibility 가 PO 결정에 직접 사용되지 않는가? (S5)
- [ ] Supplier credentials 가 secrets manager / 암호화 컬럼에 보관되는가? (S6)
- [ ] 주요 상태 전이가 audit trail 에 기록되는가? (S7)
- [ ] Reconciliation discrepancy 가 자동 close 되지 않고 운영자 큐로 가는가? (S8)
- [ ] PO 상태 다이어그램과 settlement / reconciliation / forecast workflow 문서가 존재하는가?
- [ ] 표준 에러 코드가 플랫폼 카탈로그에 등록되어 있는가?
- [ ] 외부 supplier 별 adapter 가 별도 분리되어 있고 도메인 코어에 침투하지 않는가?
