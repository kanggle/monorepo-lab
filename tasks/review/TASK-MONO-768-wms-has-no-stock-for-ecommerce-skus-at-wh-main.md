# Task ID

TASK-MONO-768

# Title

ecommerce 주문이 wms 출고 주문까지는 가지만 **재고 예약에서 멈춘다** — `WH-MAIN` 에 로케이션도, ecommerce SKU 86종의 재고도 없다

# Status

review

# Owner

monorepo

# Task Tags

- demo
- seed
- infra

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus — 시드지만 세 서비스(master · inbound · inventory)의 읽기 모델 UUID 가 서로 맞아야 하고, 재고는 실제 입고 경로로 만들어야 scm 까지 이어진다.

---

# Dependency Markers

- 선행: `TASK-MONO-765`(review) — 그 티켓의 «🔴 알려진 잔여 격차» 절이 이 티켓의 출처다. 765 는 DLT 를 막았고(AC-3), 재고 반영(AC-4)은 이 티켓이 있어야 닫힌다.
- 24차 재굽기 **전에** 머지해야 한다 — 시드는 구워진 클론에서 돈다(재굽기 한 번에 765 와 함께 실린다).

# 배경 — 코드로 확인한 예약 경로 (2026-10-07 UTC)

765 이후의 흐름:

1. ecommerce shipping → `ecommerce.fulfillment.requested.v1` (skuCode = product-service 변형 UUID 소문자, 창고 `WH-MAIN`, 거래처 `ECOMMERCE-STORE`)
2. wms outbound `FulfillmentRequestedConsumer` → outbound 자체 읽기 모델(`sku_snapshot` 86종 · `warehouse_snapshot` WH-MAIN — 765 가 심음) → 출고 주문 생성 → `wms.outbound.picking.requested.v1` (라인 = `skuId` = outbound `sku_snapshot.id`, 즉 `01910000-0000-7000-8000-00000000{2001..2086}`, `warehouseId` = WH-MAIN 의 id `01910000-0000-7000-8000-000000000002`, `locationId` 보통 null)
3. wms inventory `PickingRequestedConsumer.resolve` → `(warehouseId, skuId, lotId)` 로 **`inventory` 행 중 `available_qty > 0`** 을 찾는다(`apps/inventory-service/.../adapter/in/messaging/outbound/PickingRequestedConsumer.java`).
   - 행이 0개 → `signalReserveFailed` → `inventory.reserve.failed` → 출고 **BACKORDERED**. DLT 는 아니지만 데모 흐름은 여기서 멈춘다.

지금 데모 시드의 재고는 `seed-wms.sh` 의 입고 한 벌(`SKU-APPLE-001`, WH01 로케이션)뿐이다. 그리고:

- `WH-MAIN` 에는 **존·로케이션이 하나도 없다**(master-service `R__02/R__03` · 각 서비스 미러 어디에도) — 재고 행은 `location_id NOT NULL` 이므로 로케이션이 먼저 있어야 한다.
- inventory-service 미러(`db/seed/R__seed_dev_masterref.sql`)에는 WH-MAIN · 86 SKU 가 없다. 미러 부재는 `MasterRefValidator` 상 «의견 없음(허용)» 이므로 그것만으로 예약이 막히지는 않는다 — **막는 것은 재고 행 부재다.**
- scm inventory-visibility 는 `wms.inventory.received.v1` 이 먼저 와야 그 노드·SKU 스냅샷을 만들고, 출고 확정(`wms.inventory.confirmed.v1`)을 그 스냅샷에서 뺀다(없으면 `InventorySnapshotNotFoundException`). ⇒ **재고를 SQL 로 `inventory` 테이블에 직접 넣으면** 예약은 되지만 received 이벤트가 없어 scm 쪽이 깨진다.

# 구현 — AC-0 재측정 + 적용한 변경 (2026-10-07 UTC)

## AC-0 표 — 착수 시 코드를 다시 읽고 적었다 (가정 없음)

| # | 질문 | 코드로 확인한 답 (file:line) | 설계에 미친 영향 |
|---|---|---|---|
| ① | 입고 경로가 읽는 미러와 키 | REST 는 **id** 를 받는다(`CreateAsnRequest`: `supplierPartnerId`·`warehouseId`·`lines[].skuId` 모두 UUID). `ReceiveAsnService.receive` 가 inbound 자신의 `warehouse_snapshot`(id, ACTIVE — :90-96) · `partner_snapshot`(id, `canSupply()` — :98-106) · `sku_snapshot`(id, ACTIVE — :118-131)를 읽는다. 검수 `InspectionService.buildInspectionLineFromCommand`(:143-163)는 `sku_snapshot` 으로 LOT 요구만 본다. 적치 지시 `InstructPutawayService.buildPutawayLines`(:179-186)·확정 `ConfirmPutawayLineService.confirm`(:64-71)은 `location_snapshot` 을 id 로, **ACTIVE + ASN 창고와 같은 창고**여야 한다. `zone_snapshot` 은 어느 경로도 안 읽는다. | inbound 미러에 WH-MAIN · 로케이션(+존, 완결성) · SKU 86종 추가. 공급 거래처는 기존 `SUP-001`(`…0801`) 재사용 — 새 거래처 불필요. |
| ② | inventory 입고 반영의 `skuId`/`locationId`/`warehouseId` 출처 | `PutawayCompletedConsumer.parse`(:93-114)는 `inbound.putaway.completed` 의 `payload.warehouseId` 와 라인의 `skuId`·`locationId`·`lotId` 를 **그대로** 쓴다. 그 값은 `PutawayCompletionPublisher.buildEventLines`(:86-97) — `PutawayLine.skuId`(= ASN 라인의 skuId, 즉 **inbound sku_snapshot id**) · 확정의 `actualLocationId` · 지시의 `warehouseId`(= ASN 의 warehouseId). `ReceiveStockService.receive`(:91-120)가 `(locationId, skuId, lotId)` 행을 upsert 하고, `warehouseCode` 는 inventory 자신의 `warehouse_snapshot` 에서 붙인다(:125-126 — 없으면 null). | inventory 미러에 WH-MAIN `warehouse_snapshot` 을 넣어야 scm 노드가 코드 `WH-MAIN` 을 받는다(없으면 null). |
| ③ | outbound `picking.requested` 의 `skuId`·`warehouseId` 가 ②와 같은 UUID 인가 | `FulfillmentRequestedConsumer.toCommand` 가 라인에 `sku.id()`(:201, **outbound sku_snapshot id**)와 `warehouse.id()`(:166)를 싣고, `EventEnvelopeSerializer.pickingRequestedPayload`(:130-151)가 그대로 직렬화한다. inventory `PickingRequestedConsumer.resolve`(:154-197) → `findAvailableByWarehouseSkuLot` 은 **UUID 동등**으로만 찾는다(`InventoryJpaRepository`:34-45, `lotId` null ↔ `lot_id IS NULL`). | ⇒ inbound 미러의 SKU id 는 **outbound 의 `01910000-…-00000000{2001..2086}` 와 같아야** 한다. 같은 code→id 쌍을 그대로 복제했고 `EcommerceSeedParityTest` 가 고정한다. |
| ④ | ASN 라인 수 상한 · `NONE` SKU 의 lot 요구 | 상한 **없음**(`CreateAsnRequest` 에 `@Size` 없음 · `asn_line` 에 개수 제약 없음). LOT 요구는 `sku.requiresLot()` 일 때만(`InspectionService`:147-150 · `InstructPutawayService`:174-177) — `NONE` 은 lot 없이 통과, 재고 행은 `lot_id NULL`. | 상한 때문에 나눌 필요는 없다. **그런데 아래 🔴 때문에 1 ASN = 1 SKU 로 나눴다.** |

🔴🔴 **표 밖에서 나온 발견 — scm 이 다중 라인 `inventory.received` 의 첫 라인만 반영한다.** 티켓이 «scm
가시성에 나타난다» 를 요구하므로 소비자까지 읽었다. `WmsInventoryReceivedConsumer.consume`
(scm-platform apps/inventory-visibility-service …/WmsInventoryReceivedConsumer.java:75-83)는 **라인마다**
`applyInventoryReceived(…, envelope.eventId(), …)` 를 부르고, 그 메서드
(`InventoryVisibilityApplicationService`:78-95)는 `isDuplicate(eventId)` → 반영 → `markProcessed(eventId)`
순서다. 키가 eventId 하나뿐이라 **두 번째 라인부터 «중복» 으로 버려진다.** scm 테스트 픽스처
(`AbstractInventoryVisibilityIntegrationTest.receivedEnvelope`)는 1라인 이벤트만 만들어서 이 경로가 한 번도
안 재졌다. ⇒ 86라인 ASN 한 장이면 wms 재고는 86종 생기지만 **scm 에는 1종만** 보이고, 나머지 85종의 출고
확정은 scm 에서 `InventorySnapshotNotFoundException` → DLT 다. **이 티켓의 «서비스 코드 변경 금지» 범위
안에서 피하는 방법**은 ASN 을 SKU 마다 한 장(1라인)으로 나누는 것이고(티켓 Edge Case 의 «ASN 을 나눈다,
번호 규칙 고정, 멱등 유지» 와 같은 처방), 그렇게 했다. 🔴 **scm 결함 자체는 남아 있다** — 다른 경로(SKU
여러 개를 한 ASN 으로 받는 실제 입고)는 여전히 scm 에 첫 라인만 남긴다. **후속 티켓 필요**(이 티켓은
기안하지 않았다 — 오케스트레이터 판단). 이 판단은 코드 읽기에서 나왔고 실행으로 재지는 않았다.

부수 확인: ecommerce product-service 의 `WmsInventoryReconciliationConsumer` 도 `wms.inventory.received.v1`
을 받지만, 자신의 `wms_sku_snapshot`(master.sku 이벤트로만 채워짐)에 이 sku id 가 없으면 건너뛴다
(`WmsInventoryReconciliationService`:70-74) — 시드 SQL 은 이벤트를 내지 않으므로 ecommerce 재고는 **안
바뀐다**(부작용 없음). outbound 피킹 확정은 `location_snapshot` 이 있을 때만 창고 일치를 본다
(`ConfirmPickingService`:280, `ifPresent`) ⇒ **outbound 미러에는 아무것도 추가하지 않았다.**

## 적용한 변경 (AC-1)

- master-service `R__02_seed_dev_zones.sql` — WH-MAIN 존 `Z-A`(`…0201`), `R__03_seed_dev_locations.sql` —
  로케이션 `WH-MAIN-A-01-01-01`(`…1101`, STORAGE). **별도 INSERT 문으로 추가** — WH01 행 바이트 불변.
  🔵 그 코드는 `Location.CODE_PATTERN`(`^WH\d{2,3}-…`)을 만족할 수 없다 — 부모 `WH-MAIN` 자체가
  `Warehouse.CODE_PATTERN` 밖이다(765 의 선택). `reconstitute` 는 재검증하지 않으므로 로드는 된다(주석에 기록).
- inbound-service `R__seed_dev_masterref.sql` — WH-MAIN `warehouse_snapshot` · `zone_snapshot` ·
  `location_snapshot` · SKU 86종 `sku_snapshot`(outbound 와 같은 id·code, NONE·ACTIVE).
- inventory-service `R__seed_dev_masterref.sql` — WH-MAIN `warehouse_snapshot` · `location_snapshot` · SKU 86종
  (`base_uom='EA'` — 이 테이블만 NOT NULL 컬럼이 하나 더 있다, V4).
- R__ 파일 이름은 하나도 안 바꿨다(전부 기존 파일에 append). 모든 INSERT 는 `ON CONFLICT DO NOTHING`.
- `infra/demo/seed/seed-wms.sh` — **1b)** WH-MAIN 에 SKU 마다 ASN 1장(`ASN-DEMO-EC-001..086`, 수량 100)을
  ASN → 검수 시작 → 검수(합격 100, lot 없음) → 적치 지시 → 적치 확정, **실제 API 로**. 멱등: WH-MAIN ASN
  목록을 **전 페이지** 읽어 번호별 상태를 모으고 종착(PUTAWAY_DONE·CLOSED)이면 건너뜀 · 중간 상태면 실패 ·
  목록을 못 읽으면 아무것도 만들지 않음. **3)** 끝 검증 — `inventory_db.inventory` 에 WH-MAIN · 그 로케이션 ·
  lot NULL · `available_qty > 0` 인 SKU 가 86종인지 읽기 전용 `dbquery` 로 최대 180초 폴링(예약 소비자가 찾는
  조건 그대로).
  🔴 덤으로 고친 것: 기존 1) 의 `ASN-DEMO-0001` 존재 확인이 `?size=100`(최신순) 전체 목록이었다 — 86장이
  그보다 **나중에** 쌓이므로 몇 번의 실행 뒤 첫 페이지 밖으로 밀려 «없음» → 재생성 409 로 실패했을 것이다.
  `warehouseId=WH01` 필터를 붙였다(동작 변화는 그 질문의 모집단을 WH01 로 좁힌 것뿐).
- **검사로 고정** — `inventory-service …/seed/EcommerceSeedParityTest`(단위, 도커 불필요): outbound ·
  inbound · inventory 세 seed 파일의 SKU 86쌍(code→id)이 같고, `seed-wms.sh` 의 id 생성식
  (`EC_SKU_ID_FORMAT`·`EC_SKU_ID_BASE`·`EC_SKU_COUNT`)을 다시 돌린 집합도 같고, WH-MAIN id(master R__01 ·
  outbound · inbound · inventory · 스크립트)와 로케이션 id/창고/존(master R__02·R__03 · inbound · inventory ·
  스크립트)이 같은지 본다. 추출마다 «최소 1건» 을 단언한다(정규식이 아무것도 못 찾으면 빈 맵 둘이 «같다»
  가 되지 않게).
  **bite 1회 시연**: inventory 시드의 `…2086` 한 줄을 `…2087` 로 바꾸자 `rc=1`
  (`[inventory sku_snapshot must equal outbound's (the reservation lookup)]`, 3 중 1 실패) → 백업 복사본으로
  되돌리고 `rc=0`. (`git checkout` 으로 되돌리지 않았다 — 커밋 전 작업분이 지워진다.)

## 테스트 (AC-2)

- `ExistingSeedVolumeMigrationOrderIT`(master) — zones/locations **3→4**, 조인 4 + «새 로케이션이 WH-MAIN 에
  매달린다» 1건 단언 추가.
- 🆕 `DevSeedMigrationIT`(inbound · inventory 각각, `@Tag("integration")`) — 지금까지 **이 두 서비스의
  `db/seed` 를 실제 DB 에 적용하는 테스트가 하나도 없었다**(데모가 첫 실행 장소였다). 신선 Postgres 에
  migration+seed 적용 → 행 단언 → R__ 이력 삭제 후 재적용(체크섬 변경 상황) → 중복 없음 단언.
- `ScmInboundExpectedDemoSeedShapeDltTest`(inbound) — 시드 파일을 «단일 행 INSERT» 로 가정하던 파서가
  다중 행 VALUES 의 첫 튜플만 읽게 돼 있었다 → 튜플 분할로 고치고 기대치를 `WH01, WH-MAIN` · SKU 87 로 갱신.
- 로컬 실행(Windows, `$?` 를 파이프 없이 읽음):
  `:inventory-service:test` + `:inbound-service:test` + `:master-service:compileTestJava` → **rc=0**
  (실패 스위트 0). 🔴 **Testcontainers IT 셋(master `ExistingSeedVolumeMigrationOrderIT` · inbound/inventory
  `DevSeedMigrationIT`)은 이 호스트에서 못 돌렸다** — `docker ps` rc=1(도커 데몬 없음). 컴파일만 확인, 판정은
  CI `integrationTest` 레인(ci.yml 이 세 모듈의 `integrationTest` 를 돈다).
- `bash -n infra/demo/seed/seed-wms.sh` → **rc=0**. 1b) 의 응답 파싱(`re_get` · 목록 상태 맵 · id 생성식)은
  모의 JSON 으로 따로 돌려 기대값을 확인했다(실제 서버 응답 아님).

## 남은 것 / 범위 밖

- ⚪ AC-3 실측 · AC-4 — 재굽기 창. 시드는 구워진 클론에서 돈다.
- 🔴 scm 다중 라인 received 결함(위) — 후속 티켓 필요.
- 🔵 admin-service 미러(`admin_*_ref`)에는 WH-MAIN 계열이 없다(765 도 안 넣었다) — 콘솔 재고·입고 화면에서
  WH-MAIN 행의 창고/로케이션/SKU **코드 표시**가 비어 보일 수 있다. 예약 경로와 무관해 이 티켓 범위 밖.

# Goal

데모에서 스토어 주문 → wms 출고 주문 → **재고 예약 성공(RESERVED)** 까지 간다. 그 재고는 실제 입고 경로로 만들어져 scm 재고 가시성에도 나타난다.

# Scope

## In Scope

- master-service 시드: WH-MAIN 의 존 1 · 로케이션 1(이상) — `R__02_seed_dev_zones.sql` · `R__03_seed_dev_locations.sql` 에 **추가**(WH01 행 불변).
- 입고 경로가 요구하는 미러(inbound-service `db/seed/R__seed_dev_masterref.sql`)에 WH-MAIN · 그 존/로케이션 · SKU 86종(UUID·코드 **outbound 미러와 같은 값**) · 입고에 필요한 공급 거래처(기존 것 재사용 가능하면 재사용) 추가.
- inventory-service 미러에도 같은 WH-MAIN · 로케이션 · SKU 86종 추가(예약 경로에서 «의견 없음» 이 아니라 실제로 ACTIVE 로 검증되도록).
- outbound-service 미러에 WH-MAIN 존·로케이션이 필요하면(피킹 확정 경로) 추가 — AC-0 에서 판단.
- `infra/demo/seed/seed-wms.sh` — WH-MAIN 에 ecommerce SKU 86종을 **입고 API 로**(ASN → 검수 → 적치 지시 → 적치 확정) 넣는 두 번째 입고 흐름. 수량은 SKU 당 넉넉히(예: 100). 기존 SKU-APPLE 흐름과 같은 멱등 규칙(ASN 번호로 존재 확인 → 건너뜀).
- 시드 끝 검증: `inventory_db.inventory` 에 WH-MAIN · 86 SKU 행 `available_qty > 0`(읽기 전용 `dbquery`).

## Out of Scope

- 서비스 코드 변경(예약·입고 로직은 올바르다 — 빠진 것은 데이터다).
- ecommerce 쪽 SKU 매핑 정책(`require-sku-mapping`) 변경.
- 기존 BACKORDERED/DLT 건 재처리.

# Acceptance Criteria

- [x] **AC-0** — 착수 시 다시 읽고 표로 적는다(가정 금지): ① 입고 경로(inbound ASN 생성 · 검수 · 적치)가 읽는 미러 테이블과 키(코드 vs id), ② inventory 의 입고 반영 소비자가 쓰는 `skuId`/`locationId`/`warehouseId` 가 어디서 오는지, ③ outbound 의 `picking.requested` 가 싣는 `skuId`·`warehouseId` 가 ②와 **같은 UUID** 가 되는지, ④ ASN 라인 수 상한(있으면) · tracking_type `NONE` SKU 의 lot 요구 여부.
      → 위 «AC-0 표». 설계는 유효(입고 경로로 받을 수 있다). 🔴 표 밖 발견 하나가 ASN 단위를 바꿨다 — scm 이 다중 라인 received 의 첫 라인만 반영 ⇒ SKU 당 ASN 1장.
- [x] **AC-1** — 위 시드 추가. 세 서비스의 SKU 86종 UUID 가 서로 같다는 것을 **검사로**(단위 테스트 또는 시드 스크립트 검증 블록) 고정 — 한 서비스만 바뀌면 빨강.
      → `EcommerceSeedParityTest`(inventory-service, 단위). bite 1회 시연(rc=1 → 복원 rc=0) — 위 «적용한 변경».
- [ ] **AC-2** — 로컬(또는 CI IT)에서 시드 SQL 이 각 서비스 Flyway 에 적용된다(기존 `ExistingSeedVolumeMigrationOrderIT` 류 기대치 갱신 포함).
      → ⏳ **이 PR 의 CI `integrationTest` 레인이 판정한다** — 로컬은 도커 데몬이 없어 Testcontainers 를 못 돌렸다(`docker ps` rc=1). 기대치 갱신(master 3→4) + inbound/inventory `DevSeedMigrationIT` 신설은 이 PR 에 있다. 머지 전 CI 결과로 닫을 것.
- [ ] **AC-3** — `seed-wms.sh` 가 두 번 돌아도 멱등(두 번째 실행에서 새 ASN 0 · 실패 0). `bash -n` 통과 + 가능하면 로컬 스택에서 1회 실측, 불가하면 «⚪ 재굽기 창에서 측정» 으로 적는다.
      → `bash -n` rc=0 · 파싱 헬퍼 모의 실행 확인. ⚪ **재굽기 창에서 측정**(로컬 스택 없음).
- [ ] **AC-4** — (재굽기 창) 스토어 주문 1건 → wms 출고 주문 `RESERVED`(BACKORDERED 아님) · scm 재고 가시성에 WH-MAIN 노드의 해당 SKU 가 보인다. 이 티켓 머지 시점에는 ⚪ 로 남기고 창에서 닫는다.
      → ⚪ 재굽기 창 대기.

# Related Specs

- `projects/wms-platform/specs/services/inventory-service/` (예약 · 입고 반영)
- `projects/wms-platform/specs/services/inbound-service/` (ASN · 적치)
- `TASK-MONO-765` 본문(코드 경로 · 86 SKU 전수 근거)

# Related Contracts

- `projects/wms-platform/specs/contracts/` 의 inbound API · `inbound.putaway.completed` · `outbound.picking.requested` · `inventory.received` — 읽기만, 변경 없음.

# Edge Cases

- 재굽기 전 DB 볼륨에는 이 시드가 없다 — repeatable(`R__`) 이므로 체크섬 변경으로 재적용된다. 모든 INSERT 는 `ON CONFLICT DO NOTHING`.
- SKU 86종 중 V21(아티스트 굿즈 21종)은 다음 재굽기부터 product-service 에 생긴다 — 765 와 같은 이유로 86종 전부.
- 입고 API 가 한 ASN 의 라인 수를 제한하면 ASN 을 나눈다(번호 규칙 고정, 멱등 유지).

# Failure Scenarios

1. **재고를 SQL 로 직접 넣는다** — 예약은 되지만 scm 가시성이 received 이벤트를 못 받아 출고 확정에서 깨진다(배경 참조).
2. **UUID 가 서비스마다 다르다** — outbound 가 보낸 `skuId` 로 inventory 가 행을 못 찾아 BACKORDERED. AC-1 의 일치 검사가 막는다.
3. **WH01 행을 고친다** — 기존 SKU-APPLE 입고·출고 픽스처가 깨진다. 추가만.
