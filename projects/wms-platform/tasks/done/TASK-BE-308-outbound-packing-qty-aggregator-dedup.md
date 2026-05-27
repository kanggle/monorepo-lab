# TASK-BE-308 — outbound-service PackingQtyAggregator cross-class utility extract (F-L6-1 sumByOrderLine dedup)

Status: done

## Goal

`outbound-service` 의 `/refactor-code` strategic-sampling dry-run 결과 식별된 F-L6-1 single finding 의 closure. `PackingService` (2 callsite) + `ConfirmShippingService` (1 callsite) 가 공유하는 **4-line `sumByOrderLine` `Map.merge` 블록** (3 callsite × byte-identical `PackingUnitLine` aggregate-by-`orderLineId` 패턴) 을 신규 package-private utility class `PackingQtyAggregator` 로 추출.

dry-run 결과 base rate (outbound-service 258 main file scope — wms cluster 가장 큰 surface, saga orchestrator):
- L0 (layer-violation) = 0 (Hexagonal port/adapter 깨끗, TMS 어댑터 외부 + ERP webhook signature/replay validation 모두 adapter-only)
- L1 (auth) = 0 (`AuthorizationGuards.requireAnyRole` 이미 추출 + role check 가 application service layer 에 위치 = outbound architecture.md 명시 패턴)
- L2 (dead-code) = 0
- L3 (long-method ≥30 LOC) = 0 (5 application service 모두 이미 5+ helper method-extracted: ReceiveOrderService 5 helper / ConfirmPickingService 4 helper + record / ConfirmShippingService 5 helper + record / PackingService 4 helper + 3 use case / CancelOrderService 이미 분리)
- L4 (pattern-mismatch) = 0 (Hexagonal uniform across 7 wms backend services, saga state machine + state-keeping orchestrator 패턴 정통)
- **L6 (duplication) = 1 (본 task scope F-L6-1)**
- intentional preservation 7 category 인지 (verify exemption):
  1. **`InventoryConsumerSupport`** — admin BE-300 `ProjectionConsumerSupport` 패턴 이미 적용 (`@Component` + non-`@Transactional` + dispatch helper + 3 saga consumer 사용). javadoc 명시: "Marking a method here `@Transactional` would re-introduce the Spring AOP self-invocation hazard that broke the admin-service Unit C refactor (cf. PR #304 revert). Do not add it."
  2. **`MasterEventConsumer` single-dispatcher** = inbound BE-307 와 동일 (6 listener × 1 strategy projector each, javadoc 명시 PR #304 admin lesson)
  3. **6 MasterXxxProjector strategy pattern** = admin BE-300 lesson 적용 — intentional preservation
  4. **`EventDedupeRepositoryImpl.process` `Propagation.MANDATORY` + `Runnable work` callback** — BE-027 pattern (inbound 와 동일)
  5. **3 DataIntegrityViolationException duplicate catches** (`TmsRequestDedupeRepositoryImpl` void return + `EventDedupeRepositoryImpl` `Outcome.IGNORED_DUPLICATE` + `WebhookInboxStoreAdapter` `IngestOutcome.Duplicate(previously)`) — **다른 outcome enum + 다른 log level + 다른 recovery logic** = dedup 불가능 (BE-307 inbound 동일 conclusion)
  6. **`MasterReadModelRepositoryImpl` per-aggregate native SQL** — inbound BE-307 와 동일 per-aggregate (warehouse_snapshot / zone_snapshot / location_snapshot / sku_snapshot / lot_snapshot / partner_snapshot 다른 table / column count / parameter count) — template builder 도입 시 over-abstraction
  7. **Already method-extracted 5 services**:
     - `ReceiveOrderService.receive` (~30 LOC + 5 helper: `validateCustomerPartner` / `buildOrderLines` / `buildOrderAggregate` / `emitOrderReceivedAndPickingRequested` / `isSystemActor`) — BE-307 ReceiveAsnService 와 동일 패턴 이미 적용
     - `ConfirmPickingService.confirm` (~50 LOC + 4 helper + `PickingAggregates` record: `loadPickingAggregates` / `buildConfirmation` / `emitPickingCompletedOutbox` / `validateLines`)
     - `ConfirmShippingService.confirm` (~50 LOC + 5 helper + `ShippingAggregates` record: `assertVersionAndStatus` / `loadShippingAggregates` / `buildShipmentRecord` / `buildShippingEventLines` / `emitShippingOutbox` / `generateShipmentNo`)
     - `PackingService` (3 use case 구현 + 5 helper: `allUnitsCoverAllLines` / `completePackingFromSeal` / `validatePackingCompleteness` / `emitPackingCompleted` / `toResult`)
     - `CancelOrderService` (149 LOC, compensation orchestrator, 이미 분리)

## Scope

In:

### Finding 1: F-L6-1 — PackingQtyAggregator cross-class utility extract

다음 3 callsite 가 동일 4-line `sumByOrderLine` `Map.merge` 블록 보유:

`PackingService.allUnitsCoverAllLines` (L211-216):

```java
Map<UUID, Long> sumByOrderLine = new HashMap<>();
for (PackingUnit u : units) {
    for (PackingUnitLine l : u.getLines()) {
        sumByOrderLine.merge(l.getOrderLineId(), (long) l.getQty(), Long::sum);
    }
}
```

`PackingService.validatePackingCompleteness` (L309-314):

```java
Map<UUID, Long> sumByOrderLine = new HashMap<>();
for (PackingUnit u : units) {
    for (PackingUnitLine l : u.getLines()) {
        sumByOrderLine.merge(l.getOrderLineId(), (long) l.getQty(), Long::sum);
    }
}
```

`ConfirmShippingService.buildShippingEventLines` (L231-236):

```java
Map<UUID, Long> packedSumByOrderLine = new HashMap<>();
for (PackingUnit u : packingUnits) {
    for (PackingUnitLine pul : u.getLines()) {
        packedSumByOrderLine.merge(pul.getOrderLineId(), (long) pul.getQty(), Long::sum);
    }
}
```

3 callsite 모두 byte-identical `PackingUnitLine.qty` aggregate-by-`OrderLineId` 패턴 (변수명 `l` vs `pul` 무관).

신규 utility class `com.wms.outbound.application.service.PackingQtyAggregator`:

```java
package com.wms.outbound.application.service;

import com.wms.outbound.domain.model.PackingUnit;
import com.wms.outbound.domain.model.PackingUnitLine;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Aggregates packed quantities by {@code orderLineId} across a list of
 * {@link PackingUnit}s.
 *
 * <p>Used by:
 * <ul>
 *   <li>{@link PackingService#allUnitsCoverAllLines} — compares the per-line
 *       packed sum against {@code OrderLine.qtyOrdered} during seal-time
 *       completion check.</li>
 *   <li>{@link PackingService#validatePackingCompleteness} — same map, strict
 *       equality assertion at confirm-time.</li>
 *   <li>{@link ConfirmShippingService#buildShippingEventLines} — safety map
 *       alongside the picking-confirmation source-of-truth.</li>
 * </ul>
 */
final class PackingQtyAggregator {

    private PackingQtyAggregator() {}

    /**
     * Returns a {@link Map} from {@code orderLineId} to the summed
     * {@code PackingUnitLine.qty} (as {@code Long}) across every
     * {@link PackingUnit} in {@code units}.
     *
     * <p>Behavior:
     * <ul>
     *   <li>Empty {@code units} → empty map.</li>
     *   <li>Iteration order is implementation-defined ({@link HashMap}).</li>
     *   <li>Each {@code PackingUnitLine.qty} is widened to {@code long}
     *       individually so a 1 000-line order with int-max quantities still
     *       sums cleanly.</li>
     * </ul>
     */
    static Map<UUID, Long> sumByOrderLine(List<PackingUnit> units) {
        Map<UUID, Long> sumByOrderLine = new HashMap<>();
        for (PackingUnit u : units) {
            for (PackingUnitLine l : u.getLines()) {
                sumByOrderLine.merge(l.getOrderLineId(), (long) l.getQty(), Long::sum);
            }
        }
        return sumByOrderLine;
    }
}
```

Package-private (`final class` + package-private + private constructor + package-private static method) — 같은 package `com.wms.outbound.application.service` 안 `PackingService` + `ConfirmShippingService` 만 사용. 외부 service / controller / config 노출 0.

3 callsite 의 4-line block → 1-line `PackingQtyAggregator.sumByOrderLine(...)` 호출 치환:

```java
// PackingService.allUnitsCoverAllLines
Map<UUID, Long> sumByOrderLine = PackingQtyAggregator.sumByOrderLine(units);

// PackingService.validatePackingCompleteness
Map<UUID, Long> sumByOrderLine = PackingQtyAggregator.sumByOrderLine(units);

// ConfirmShippingService.buildShippingEventLines
Map<UUID, Long> packedSumByOrderLine = PackingQtyAggregator.sumByOrderLine(packingUnits);
```

Out:

- **`PackingService.allUnitsCoverAllLines` 의 `units.isEmpty()` + per-unit `!isSealed()` check + post-aggregate `packed < ol.getQtyOrdered()` check** = boolean-return logic, body 가 다른 method 와 다른 outcome (return false vs throw exception). 본 task scope 외 (sumByOrderLine 4-line block 만 추출, sealed-check 와 per-orderLine check 는 callsite local).
- **`PackingService.validatePackingCompleteness` 의 `units.isEmpty()` + per-unit `!isSealed()` check + post-aggregate `packed != ol.getQtyOrdered()` check** = throw-exception logic, 같은 이유로 scope 외.
- **`ConfirmShippingService.buildShippingEventLines` 의 `confirmedLineByOrderLineId` map + per-OrderLine event line construction** = picking-confirmation 의 source-of-truth lookup + event line 생성 = 본 task scope 외.
- **outbound-service 의 다른 application service 들 (ReceiveOrderService / ConfirmPickingService / CancelOrderService / RetryTmsNotificationService / WebhookInboxProcessorService 등)** 변경 0 (sumByOrderLine 패턴 없음 — Order/Picking/Saga aggregate 사용).
- **outbound-service 의 adapter / domain / port / config / saga / command / result layer** 변경 0.
- **다른 cluster (admin / inventory / gateway / notification / master / inbound)** + **다른 project (scm / global-account / erp / fan / ecommerce / finance / platform-console)** + **libs/** 변경 0.
- API / event contract / DB schema / domain method signature 변경 0.

## Acceptance Criteria

AC-1. 신규 `com.wms.outbound.application.service.PackingQtyAggregator.java`:
- `final class PackingQtyAggregator` (package-private 접근, 외부 누출 0)
- `private PackingQtyAggregator()` constructor
- `static Map<UUID, Long> sumByOrderLine(List<PackingUnit> units)` — 6-line body byte-identical 보존:
  ```java
  Map<UUID, Long> sumByOrderLine = new HashMap<>();
  for (PackingUnit u : units) {
      for (PackingUnitLine l : u.getLines()) {
          sumByOrderLine.merge(l.getOrderLineId(), (long) l.getQty(), Long::sum);
      }
  }
  return sumByOrderLine;
  ```
- short javadoc cross-ref (사용처: PackingService 2 callsite + ConfirmShippingService 1 callsite)

AC-2. `PackingService.allUnitsCoverAllLines` L211-216 의 4-line `Map<UUID, Long> sumByOrderLine = new HashMap<>(); for (PackingUnit u : units) { for (PackingUnitLine l : u.getLines()) { sumByOrderLine.merge(l.getOrderLineId(), (long) l.getQty(), Long::sum); } }` → 1-line `Map<UUID, Long> sumByOrderLine = PackingQtyAggregator.sumByOrderLine(units);` 치환. 잔존 method body (`units.isEmpty()` + per-unit `!isSealed()` + per-orderLine `packed < ol.getQtyOrdered()` boolean-return logic) byte-identical 보존.

AC-3. `PackingService.validatePackingCompleteness` L309-314 의 동일 4-line 블록 → 1-line `Map<UUID, Long> sumByOrderLine = PackingQtyAggregator.sumByOrderLine(units);` 치환. 잔존 method body (`units.isEmpty()` + per-unit `!isSealed()` + per-orderLine `packed != ol.getQtyOrdered()` throw-exception logic) byte-identical 보존.

AC-4. `ConfirmShippingService.buildShippingEventLines` L231-236 의 동일 4-line 블록 → 1-line `Map<UUID, Long> packedSumByOrderLine = PackingQtyAggregator.sumByOrderLine(packingUnits);` 치환 (변수명 `packedSumByOrderLine` 보존 — caller-specific naming intent). 잔존 method body (`confirmedLineByOrderLineId` map + per-OrderLine event line construction) byte-identical 보존.

AC-5. `PackingService.create` / `PackingService.seal` / `PackingService.confirm` / `PackingService.completePackingFromSeal` / `PackingService.emitPackingCompleted` / `PackingService.toResult` method 변경 0. `ConfirmShippingService.confirm` / `assertVersionAndStatus` / `loadShippingAggregates` / `buildShipmentRecord` / `emitShippingOutbox` / `generateShipmentNo` method 변경 0.

AC-6. 불필요한 import 정리 — `PackingService` 의 `HashMap`/`Map` 사용 잔존 여부 (다른 method 에서 사용 시 보존). `ConfirmShippingService` 의 동일 import 보존 여부 (`confirmedLineByOrderLineId` 가 `HashMap`/`Map` 사용하므로 보존). 추출 후에도 사용처 있으면 import 보존.

AC-7. `./gradlew :projects:wms-platform:apps:outbound-service:check --rerun-tasks` 로컬 BUILD SUCCESSFUL (gradlew main 디렉토리 absent 시 CI authoritative substitute).

AC-8. CI 19/20 GREEN authoritative (`Integration (outbound-service + saga, Testcontainers)` + saga IT + wms IT/E2E + `E2E (gateway-master live-pair smoke, Testcontainers)` + GAP IT + scm/finance/erp/console-bff IT + 4 E2E job 포함; FAILURE = 0).

AC-9. cross-service drift 없음 — `projects/wms-platform/apps/` 의 outbound-service 외 다른 6 service + `projects/{scm,global-account,erp,fan,ecommerce-microservices,finance,platform-console}-platform/` + `libs/` 변경 0. (**43회째 zero-retrofit invariant** 검증.)

AC-10. Domain method / port interface / API contract / event payload / DB schema 변경 0.

AC-11. 3 callsite 의 Map result byte-identical 보존 — `Map<UUID, Long>` 의 keys / values / iteration order 모두 동일 (HashMap implementation iteration order 보존). `PackingUnitLine.qty` 가 `int` 인데 `(long) l.getQty()` widening 보존.

## Related Specs

- `projects/wms-platform/specs/services/outbound-service/architecture.md` (Hexagonal port/adapter, saga state machine § Outbound Saga, § Key Domain Invariants — `PICKING_INCOMPLETE`, `PACKING_INCOMPLETE`)
- `projects/wms-platform/specs/services/outbound-service/domain-model.md` (Order / OrderLine / PackingUnit / PackingUnitLine / OutboundSaga)
- `projects/wms-platform/specs/contracts/http/outbound-service-api.md` (변경 없음)
- `projects/wms-platform/specs/contracts/events/outbound-events.md` (변경 없음; `wms.outbound.packing.completed.v1` + `wms.outbound.shipping.confirmed.v1` payload 보존)
- `platform/refactoring-policy.md` § Allowed Refactoring Categories: Reduce Duplication (Medium risk; 본 task = Low risk because 3 byte-identical callsite + 4-line block + simple aggregation pattern)
- `rules/traits/transactional.md` § T4 state-machine + T5 invariants
- `.claude/skills/backend/architecture/hexagonal/SKILL.md`
- `.claude/skills/backend/refactoring/SKILL.md`

## Related Contracts

- 본 task 는 contract 변경 0. API / event schema unchanged.
- `wms.outbound.packing.completed.v1` 의 PackingCompletedEvent.Unit + PackingCompletedEvent.Line 의 8 field byte-identical 보존.
- `wms.outbound.shipping.confirmed.v1` 의 ShippingConfirmedEvent.Line 의 5 field byte-identical 보존.

## Edge Cases

- **빈 units list**: `sumByOrderLine(List.of())` → empty `HashMap` 반환. 3 callsite 모두 동일 동작 (PackingService 는 `units.isEmpty()` 사전 체크 후 false/throw; ConfirmShippingService 는 empty map → eventLines per-OrderLine 에서 `pcl != null` 분기로 처리).
- **단일 unit / 단일 line**: `Map.merge` 가 initial put 으로 동작. byte-identical.
- **여러 unit × 동일 orderLineId**: `Long::sum` aggregator 가 누적. byte-identical (예: unit1 line1 qty=3, unit2 line1 qty=5 → orderLineId map [line1=8]).
- **`PackingUnitLine.qty` overflow 가능성**: `int` → `long` widening 으로 1 line max 보존. 3 callsite 모두 동일 widening pattern — 추출 후 unchanged.
- **HashMap iteration order non-deterministic**: 추출 후에도 동일 HashMap 반환 — callers 의 iteration order assumption 변경 0.

## Failure Scenarios

- **Map type 변경 시**: utility 가 `HashMap` 으로 명시 instantiate (`new HashMap<>()`). 만약 미래에 `LinkedHashMap` 또는 `TreeMap` 변경 시 3 callsite 영향 동기. 본 task = byte-identical `HashMap` 보존 의무.
- **변수명 `sumByOrderLine` vs `packedSumByOrderLine` 차이**: ConfirmShippingService 의 caller-side 변수명 `packedSumByOrderLine` 보존 (caller-specific naming intent — `packed` prefix 가 "packing 수량 sum" 의 의미). utility return value variable assignment 시 caller 가 자유 naming. byte-identical Map content.
- **package-private access**: utility 가 `com.wms.outbound.application.service` package 안 — `PackingService` (same package) + `ConfirmShippingService` (same package) 모두 access 가능. 외부 controller / adapter / config / saga 에서 import 시 compile error (의도된 access control).
- **PackingUnit.getLines() null 가능성**: domain model 의 `PackingUnit.getLines()` 가 null 반환 시 NPE. 본 task = byte-identical 패턴 보존 (3 callsite 모두 null 가능성 미고려 가정 = domain invariant 가 보장).
- **새 callsite 추가 시**: 미래 task 가 새 service 에 동일 패턴 추가 시 `PackingQtyAggregator.sumByOrderLine` import 가능. utility 의 package-private access 가 outbound-service 내부만 — 다른 service 누출 불가 (의도된 boundary).

## Approach Notes

- Refactoring policy § Allowed Refactoring Categories: Reduce Duplication (Medium risk per policy, 본 task = Low risk because: 3 byte-identical callsite + 4-line block + Map.merge well-known aggregation pattern + same-package package-private access).
- BE-300 ProjectionConsumerSupport (admin) + BE-301 JwtHelper (inventory) + BE-304 ReactiveJwtAccess (gateway) + BE-306 PageableFactory (master) 의 cross-class utility 카테고리 답습. **wms cluster 6번째 helper extraction = helper extraction hexology** (BE-300/301/304/305/306/308; BE-307 ReceiveAsnService 는 in-class 분리, BE-305 markPermanentAndPersist 는 same-class private). cross-class utility 카테고리 = **5번째 instance** (BE-300/301/304/306 + BE-308).
- BE-307 의 same-class long-method extraction 과 다른 점: 본 task = cross-class utility (2 service × 3 callsite × 1 utility). public surface 변경 0.
- 단일 file 변경 magnitude (3 callsite × 4-line dedup ≈ 12 LOC reduction + 1 utility ~20 LOC 추가 ≈ +8 net) — 작은 dedup 이지만 cross-class utility extraction 카테고리 정립 가치.
- BE-306 6 instance × ~20 line (largest dedup -101 net) 와 비교 시 magnitude 작음. base rate 0.004/file = mature-implementation 신기록.
- **wms cluster 7/7 TRUE 0 milestone 도달**: outbound-service = 잔존 마지막 wms service. 본 task closure 시 wms 모든 7 service sweep coverage 완료 (admin + inventory + gateway + notification + master + inbound + **outbound**) → **TRUE 0 (7/7)** 달성.

## 분석/구현 권장 모델

- 분석=Opus 4.7 (verification + 7 intentional preservation 검증)
- 구현 권장=Sonnet 4.6 (utility class 추출 + 3 callsite 1-line 치환, mechanical)
