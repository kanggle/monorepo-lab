# Task ID

TASK-MONO-765

# Status

review

# Title

ecommerce→wms 풀필먼트 요청이 **전량 DLT** — `FulfillmentRequestedConsumer` 가 필요로 하는 거래처·창고·SKU 마스터가 데모 시드에 없다

# Owner

monorepo

# Task Tags

- demo
- seed
- infra
- bug

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet — 시드 데이터 보강이 본체이고 재굽기(AMI apply)가 있어야 데모에 반영된다.

---

# Dependency Markers

- 출처: `TASK-MONO-764` 23차 창(데모 기능 점검표, 흐름 6·14 — 주문→배송 교차 확인 중 발견).

---

# 배경 — 23차 창 라이브 실측 (2026-10-06 UTC)

ecommerce shipping-service 는 주문마다 `ecommerce.fulfillment.requested.v1` 을 발행하고
로그에 `Fulfillment requested published: orderId=1e240f8b-…, lines=1` 과
`Shipping marked wmsRouted` 를 남긴다 — **발행은 정상이다.**

wms 쪽 측정:

```
토픽 ecommerce.fulfillment.requested.v1       end offset 4 (partition 0)
소비자 그룹 wms-outbound-service               lag 0
토픽 ecommerce.fulfillment.requested.v1.DLT    4 messages (partition 1:1, 2:3)
```

4건 = 시드 주문 3 + 이번 창의 주문 1. **소비자 그룹 lag 은 0 인데 결과가 전부 DLT** — 즉
소비는 되고 있고 매번 같은 이유로 실패해 DLT 로 간다.

DLT 헤더:

```
cause      java.lang.IllegalArgumentException
message    Listener method 'public void com.wms.outbound.adapter.in.messaging.consumer
           .FulfillmentRequestedConsumer.onMessage(...)' threw exception;
           Partner not found in read model: code=ECOMMERCE-STORE
```

`ECOMMERCE-STORE` 문자열은 wms 저장소 전체에서 **테스트 코드에만** 나타난다
(`FulfillmentRequestedConsumerTest` · `FulfillmentRequestedConsumerIT` ·
`InventoryReserveFailedConsumerIT`) — `infra/demo/seed/` 어디에도 없다.

콘솔 확인: wms outbound 화면(2행) · 재고 화면(SKU-APPLE-001 avail 85)이 이번 창의 스토어
주문 뒤에도 **변하지 않았다** — 즉 매번 조용히 DLT 로 빠지고 아무도 모른다.

## 코드 — 결함이 요구하는 마스터 전부 (`FulfillmentRequestedConsumer.toCommand`)

`projects/wms-platform/apps/outbound-service/src/main/java/com/wms/outbound/adapter/in/messaging/consumer/FulfillmentRequestedConsumer.java`:

```java
PartnerSnapshot partner = masterReadModel.findPartnerByCode(customerPartnerCode)
        .orElseThrow(...);                     // code = payload.customerPartnerCode
if (!partner.canReceive()) throw ...;

WarehouseSnapshot warehouse = masterReadModel.findWarehouseByCode(warehouseCode)
        .orElseThrow(...);                     // code = payload.warehouseCode
if (!warehouse.isActive()) throw ...;

// per line:
SkuSnapshot sku = masterReadModel.findSkuByCode(skuCode).orElseThrow(...);  // code = line.skuCode
if (!sku.isActive()) throw ...;
// lotNo 가 있으면 LotSnapshot 도 필요
```

ecommerce 쪽(shipping-service `application.yml`):

```yaml
fulfillment:
  enabled: true
  default-warehouse-code: WH-MAIN      # ${FULFILLMENT_DEFAULT_WAREHOUSE_CODE:WH-MAIN}
  require-sku-mapping: false           # sku-map: {} → ecommerce SKU 코드를 그대로 wms skuCode 로 보낸다
```

즉 **거래처 하나, 창고 하나(코드로), 그리고 ecommerce 가 파는 SKU 전부(코드로)** 가 wms
읽기 모델에 ACTIVE 로 있어야 한다. `infra/demo/seed/seed-wms.sh` 는 `SKU-APPLE-001` 하나만
시드한다 — ecommerce 카탈로그의 다른 SKU 코드는 애초에 wms 에 없다.

---

# 구현 — AC-0 재측정 + AC-1 표 (2026-10-06 UTC)

## AC-0 재측정

착수 시 코드를 다시 읽었다(가정하지 않음). `FulfillmentRequestedConsumer.toCommand`
(apps/outbound-service/.../consumer/FulfillmentRequestedConsumer.java:143-201)는 위
배경 절의 세 호출부(`findPartnerByCode` → `findWarehouseByCode` → 라인별
`findSkuByCode` → 있으면 `findLotBySkuAndLotNo`)를 **그대로** 갖고 있다 — 코드 변경
없음, 진단 그대로 유효.

## AC-1 표 — ecommerce 가 실제로 보내는 값 vs wms 읽기 모델(수정 전)

| 갈래 | ecommerce 가 보내는 값(코드·file:line) | wms 읽기 모델(수정 전) |
|---|---|---|
| 거래처 | `"ECOMMERCE-STORE"` 고정 상수 — `FulfillmentAcl.CUSTOMER_PARTNER_CODE`, ecommerce-microservices-platform apps/shipping-service src/main/java/com/example/shipping/infrastructure/event/FulfillmentAcl.java:32 | **없음** (`outbound_db.partner_snapshot` 에 `CUST-001`/`BOTH-001` 뿐) |
| 창고 | `fulfillment.default-warehouse-code` 설정값, 기본 `WH-MAIN` — shipping-service application.yml:73 (`${FULFILLMENT_DEFAULT_WAREHOUSE_CODE:WH-MAIN}`, 데모 compose 에 오버라이드 없음 → 실제로 `WH-MAIN` 이 나간다) | **없음** (`outbound_db.warehouse_snapshot` 에 `WH01` 뿐 — 다른 코드) |
| SKU | `require-sku-mapping=false` → `FulfillmentAcl.resolveSkuCode` 가 identity passthrough(FulfillmentAcl.java:81-92)로 order-service 의 주문 라인 `sku` 필드를 그대로 보낸다. 그 필드는 `OrderConfirmationService.toLine` 이 **variantId**(없으면 productId)로 채운다 — apps/order-service/.../OrderConfirmationService.java:58-64. 즉 skuCode = product-service `product_variants.id` 그 자체(소문자 UUID 문자열), `SKU-APPLE-001` 류 인간 코드가 아니다. | `SKU-APPLE-001` **하나뿐**(`outbound_db.sku_snapshot`) — ecommerce 변형 UUID 0건 |
| LOT | `FulfillmentAcl.toFulfillmentRequested` 가 이 경로에서 `lotNo` 를 항상 `null` 로 보낸다(FulfillmentAcl.java:76) — 실측으로 확정, LOT 불필요 | 해당 없음 |

🔴 **티켓 원문의 가정 하나가 틀렸다** — "SKU 코드가 그대로 wms skuCode" 는 맞지만, 그
실제 값이 `SKU-APPLE-001` 같은 인간 코드가 아니라 **product-service 의 변형(variant)
UUID 문자열**이라는 것은 코드를 다시 읽어서만 드러났다(AC-0 의 "가정하지 말 것" 지시가
여기서 실제로 값을 낸 지점).

🔴🔴 **변형 전수도 처음 셈이 틀렸다** — `grep INSERT INTO product_variants`로 마이그레이션
전체를 센 결과 V8(28)+V19(37) **만이 아니라 V21__seed_artist_goods.sql(21, 팬 아티스트
굿즈)도 있어 총 86종**이다. V21 은 "데모 서버는 재굽기 전까지 갖지 않는다"(V21 자신의
헤더)지만, 다음 재굽기부터는 있다 — 65종만 심었다면 이 티켓이 경고하는 바로 그 SKU-드리프트
재발을 **이 티켓 스스로** 만들 뻔했다. 86종 전부를 심었다(아래 AC-2).

## AC-2 — 적용한 변경

마스터 데이터는 API 로 넣을 수 없다(`MASTER_WRITE` 가 어떤 신원으로도 열리지 않음 —
`infra/demo/wms-devseed.override.yml` 헤더, TASK-MONO-514) — 그래서 `seed-wms.sh` 에
API 호출을 추가하지 않았다. 대신 이 저장소가 이미 wms 마스터에 쓰는 경로(Flyway
`db/seed/R__*`, 각 서비스가 own-mirror 로 가짐)로 추가했다:

- `projects/wms-platform/apps/master-service/src/main/resources/db/seed/R__01_seed_dev_warehouse.sql`
  — 창고 `WH-MAIN` 행 추가(기존 `WH01` 은 유지·이름 변경 안 함 — 다른 inbound/outbound
  픽스처가 그 UUID 를 참조한다).
- `.../master-service/.../db/seed/R__05_seed_dev_partners.sql` — 거래처
  `ECOMMERCE-STORE`(`CUSTOMER`·`ACTIVE`) 행 추가.
- `.../outbound-service/src/main/resources/db/seed/R__seed_dev_masterref.sql` —
  `FulfillmentRequestedConsumer.masterReadModel` 이 **실제로 읽는** 테이블(outbound-service
  자신의 로컬 읽기 모델 미러)에 위 둘을 미러 + SKU 86종(`sku_snapshot`, `tracking_type=NONE`,
  `sku_code` = ecommerce 변형 UUID 그대로, 소문자) 추가.
- `infra/demo/seed/seed-wms.sh` — API 로 만드는 대신 **읽어서 검증**하는 블록 추가(§0):
  master_db.warehouses/partners + outbound_db.warehouse_snapshot/partner_snapshot/
  sku_snapshot(count=86) 를 `dbquery`(읽기 전용, `--why` 불필요)로 확인하고 다르면
  `seed_fail`.

🔴 **master-service 자신의 `skus` 테이블에는 SKU 86종을 안 심었다** — 그 테이블의
`CHECK (sku_code = UPPER(sku_code))` 제약(V5__init_sku.sql:35)이 소문자 UUID 를
거부한다. 대문자로 올리면 제약은 통과하지만 실제 이벤트가 보내는 값(소문자)과
달라져 **아무 의미가 없다.** `FulfillmentRequestedConsumer` 가 조회하는 테이블은
outbound-service 자신의 `sku_snapshot`(제약 없음)뿐이므로 거기에만 정확한 대소문자로
심었다 — 전문은 그 파일의 TASK-MONO-765 주석.

🔴 **알려진 잔여 격차(이 티켓 범위 밖, AC-4 에 영향)** — inventory-service 자신의
`db/seed/R__seed_dev_masterref.sql` 미러는 아직 `WH-MAIN`/이 86 SKU 를 안 갖고 있다.
AC-3(DLT 회피·주문 생성)은 이 변경만으로 충분하지만, AC-4(재고 반영)는 그 예약 사가가
inventory-service 자신의 로컬 캐시를 또 거치므로 이 SKU 들에 대해서는 불완전할 수
있다 — 후속 티켓 대상.

---

# Goal

데모에서 ecommerce 스토어 주문이 발생하면, 그 fulfillment 요청이 wms outbound-service 에서
DLT 로 가지 않고 정상적으로 outbound order 로 들어간다(재굽기 뒤 확인).

---

# Scope

## In Scope

- `FulfillmentRequestedConsumer.toCommand` 가 요구하는 마스터 세 종(거래처·창고·SKU, 선택적
  LOT)을 **전수로 다시 확인**한다 — 이 티켓이 가정하지 말고, 착수 시 코드를 다시 읽어라.
- ecommerce 가 실제로 보내는 값(라이브 DLT 페이로드 · shipping-service 설정값)과 wms
  읽기 모델의 현재 시드를 **둘 다** 확인해 빠진 쪵을 정확히 센다:
  - 거래처 코드 `ECOMMERCE-STORE` (또는 그 자리를 대체하는 실제 값) — `canReceive()=true`
  - 창고 코드 — shipping-service `fulfillment.default-warehouse-code`(기본 `WH-MAIN`) 가
    실제로 그 값을 보내는지, wms 에 그 코드의 창고가 있는지
  - SKU 코드 — `require-sku-mapping: false` 이므로 **ecommerce 의 SKU 코드가 그대로 wms
    skuCode 다.** ecommerce 데모 카탈로그의 주문 가능한 SKU 전부가 wms 에 ACTIVE 로 있어야
    한다(`SKU-APPLE-001` 하나만으론 부족할 가능성이 높다 — 전수로 세어라).
  - LOT 추적 SKU(`SKU-APPLE-001` 처럼)라면 LOT 도 필요한지 — fulfillment 라인에 `lotNo` 가
    오는지부터 확인(현재 B2B 경로는 lot 을 안 보내는 것으로 보이나, 실측으로 확정할 것).
- `infra/demo/seed/seed-wms.sh`(또는 wms master-service 의 다른 시드 경로)에 위에서 센
  마스터를 추가.

## Out of Scope

- wms 소비자 코드 로직 변경(해석 자체는 올바르다 — 빠진 것은 데이터다).
- ecommerce shipping-service 의 기본값 변경(`WH-MAIN`/`sku-map`) — 시드 쪽에서 맞춘다.
- 이미 DLT 에 쌓인 4건의 재처리/replay — 별도 판단(Edge Cases 참조). 이 티켓은 **앞으로의**
  주문이 DLT 로 가지 않게 하는 것이 목표다.
- 재굽기·apply 실행 자체 — 소유자 승인 필요(이 티켓은 시드 스크립트만 바꾼다).

---

# Acceptance Criteria

- [x] **AC-0 (재측정)** — 착수 시 DLT 깊이·헤더·`findPartnerByCode`/`findWarehouseByCode`/
      `findSkuByCode` 호출부를 다시 확인한다. 이 티켓의 표는 2026-10-06 23차 창 실측이다.
      → 위 "구현 — AC-0 재측정" 절. 코드 변경 없음 — 진단 그대로 유효함을 확인했다.
- [x] **AC-1** — 위 Scope 의 세 갈래(거래처·창고·SKU) 각각에 대해 ecommerce 가 실제로
      보내는 코드값과 wms 읽기 모델의 현재 상태를 **표로** 남긴다(빠진 것만 추측하지 않는다).
      → 위 "AC-1 표". SKU 쪽이 "인간 코드" 가 아니라 ecommerce 변형 UUID 라는 것과,
      그 변형이 65 종이 아니라 86 종(V21 포함)이라는 것 둘 다 이 재측정에서 드러났다.
- [x] **AC-2** — 거래처 `ECOMMERCE-STORE`(`canReceive()=true`) + 창고(실제 전송 코드) +
      ecommerce 주문 가능 SKU 전부를 시드에 추가한다. LOT 추적 SKU 라면 LOT 도 함께.
      → 위 "AC-2 — 적용한 변경". LOT 은 불필요(이 경로의 `lotNo` 는 항상 null, 실측 확정).
- [ ] **AC-3** — 재굽기·apply 뒤, 새 스토어 주문 1건이 wms DLT 로 가지 않고 outbound order
      로 들어간다(`outbound_order` 행 생성 · DLT 증가 없음). ⏳ **재굽기 필요 — 오케스트레이터가
      재굽기 창에서 측정.** 정적 변경(시드·Flyway)은 이 PR 에 포함돼 있다.
- [ ] **AC-4** — wms 출고·재고 콘솔 화면과 scm 재고 가시성(`TASK-MONO-762`)이 그 주문을
      반영한다(762 연장 측정). ⏳ **재굽기 필요 — 오케스트레이터가 재굽기 창에서 측정.**
      🔴 위 "잔여 격차" 메모 참고 — inventory-service 자신의 미러가 아직 이 SKU 들을
      모르므로 이 AC 는 재굽기 뒤에도 바로 안 닫힐 수 있다.
- [ ] **AC-5 (판정 보류)** — 기존 DLT 4건의 처리 방향(replay vs 폐기)은 **이 티켓에서
      정하지 않는다** — Edge Cases 에 옵션만 남기고 소유자 결정으로 넘긴다. ⚪ 소유자 결정
      대기 — 이 티켓이 바꾸는 것은 **앞으로의** 요청뿐이다.

---

# Related Specs

- `projects/wms-platform/specs/contracts/events/ecommerce-fulfillment-subscriptions.md`
- `projects/wms-platform/specs/services/outbound-service/architecture.md`
- `projects/wms-platform/apps/master-service/src/main/java/com/wms/master/domain/exception/PartnerNotFoundException.java`

# Related Contracts

- `ecommerce.fulfillment.requested.v1` (consumed by `FulfillmentRequestedConsumer`)

---

# Edge Cases

- 거래처는 시딜 수 있어도 **SKU 는 ecommerce 카탈로그가 바뀔 때마다 드리프트**한다
  (`require-sku-mapping: false` 의 정체성 패스스루이므로, 다음 번 ecommerce 상품 추가가
  다시 이 결함을 재현할 수 있다) — 이 티켓은 지금 시점의 카탈로그만 고정으로 시드한다;
  영구 해결(코드 매핑 테이블 또는 SKU 코드 네이밍 컨벤션 통일)은 범위 밖으로 기록한다.
- 기존 DLT 4건 — replay 하려면 `TASK-BE-145`(notification-service 의 DLT replay 런북,
  유사 패턴)를 참고할 것. 폐기한다면 그 3개 시드 주문의 fulfillment 상태가 영구히 "요청됨"
  으로 남는다는 뜻 — 데모 화면에 그 불일치가 보이는지 확인할 것.
- 창고 코드가 ecommerce 설정값(`WH-MAIN`)과 wms 기존 창고 코드가 **다를 수 있다** — 이
  경우 어느 쪽을 맞출지(ecommerce 기본값을 바꾸는 쪽은 Out of Scope) 결정이 필요.

# Failure Scenarios

- **지금 DLT 에 쌓인 4건의 정확한 SKU 코드만 시드한다** — 다음 번 다른 상품을 주문하면
  다시 DLT 로 간다. AC-1 이 "ecommerce 주문 가능 SKU 전부" 를 요구하는 이유다.
- **거래처만 시드하고 창고/SKU 를 빠뜻린다** — `IllegalArgumentException` 이 자리만 바뀌어
  같은 증상(DLT)이 재발한다.
- **코드 레벨에서 fallback/기본 매핑을 임의로 추가한다** — 이 티켓은 시드 데이터 문제로
  진단했다(코드는 올바르다); 코드 변경이 필요하다고 판단되면 범위를 다시 연다.
