# Task ID

TASK-MONO-765

# Status

ready

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

- [ ] **AC-0 (재측정)** — 착수 시 DLT 깊이·헤더·`findPartnerByCode`/`findWarehouseByCode`/
      `findSkuByCode` 호출부를 다시 확인한다. 이 티켓의 표는 2026-10-06 23차 창 실측이다.
- [ ] **AC-1** — 위 Scope 의 세 갈래(거래처·창고·SKU) 각각에 대해 ecommerce 가 실제로
      보내는 코드값과 wms 읍기 모델의 현재 상태를 **표로** 남긴다(빠진 것만 추측하지 않는다).
- [ ] **AC-2** — 거래처 `ECOMMERCE-STORE`(`canReceive()=true`) + 창고(실제 전송 코드) +
      ecommerce 주문 가능 SKU 전부를 시드에 추가한다. LOT 추적 SKU 라면 LOT 도 함께.
- [ ] **AC-3** — 재굽기·apply 뒤, 새 스토어 주문 1건이 wms DLT 로 가지 않고 outbound order
      로 들어간다(`outbound_order` 행 생성 · DLT 증가 없음).
- [ ] **AC-4** — wms 출고·재고 콘솔 화면과 scm 재고 가시성(`TASK-MONO-762`)이 그 주문을
      반영한다(762 연장 측정).
- [ ] **AC-5 (판정 보류)** — 기존 DLT 4건의 처리 방향(replay vs 폐기)은 **이 티켓에서
      정하지 않는다** — Edge Cases 에 옵션만 남기고 소유자 결정으로 넘긴다.

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
