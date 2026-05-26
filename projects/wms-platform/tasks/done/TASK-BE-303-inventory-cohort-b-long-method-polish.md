# TASK-BE-303 — inventory-service Cohort B long-method polish (3 finding)

Status: done

## Goal

BE-301 dry-run 의 Cohort B 잔존 3 finding 을 closure 한다 (L5 long-method polish, low risk, behavior-preserving extraction). BE-302 의 spec amend (F-L0-1 + F-L1-1 resolution-via-spec) 와 합쳐서 inventory-service 8/8 effective sweep coverage → **5번째 cluster TRUE 0** 도달.

Cohort B 의 3 finding:

| # | Finding | File | Method | Issue |
|---|---|---|---|---|
| 1 | F-L5-1 | `application/service/AdjustStockService.java` L177-198 | `persistAdjustmentResult` | 12 parameter long parameter list; 일부는 entity 에서 derivable |
| 2 | F-L5-2 | `application/service/TransferStockService.java` L77-160 | `transfer` | 82 LOC, 9 distinct phase (validate → resolve → load → build → apply → persist → event → side-effects → return) |
| 3 | F-L5-3 | `adapter/out/persistence/inventory/InventoryRepositoryImpl.java` L110-143 | `listViews` | WHERE clause builder + bind-filters method 의 8-conditional dual-walk (where + binding 짝지어진 conditional) |

## Scope

In:

### Finding 1: F-L5-1 — `persistAdjustmentResult` 12-arg shrink

- `AdjustStockService.persistAdjustmentResult` 의 12 parameter 를 entity-derivable 으로 축소.
- Derivable from `StockAdjustment` instance:
  - `delta` ← `adjustment.delta()`
  - `bucket` ← `adjustment.bucket()`
  - `reasonCode` ← `adjustment.reasonCode()`
  - `reasonNote` ← `adjustment.reasonNote()`
  - `now` ← `adjustment.createdAt()` (또는 호출 site 의 `now` 그대로 전달; 두 값 일치성은 caller 가 보장)
  - `actorId` ← `adjustment.actorId()`
- Method-specific 유지: `movementType`, `reducedAvailable`, `counter`
- 결과 signature: `persistAdjustmentResult(StockAdjustment adjustment, InventoryMovement movement, Inventory inventory, MovementType movementType, boolean reducedAvailable, Counter counter)` = **6 params** (12 → 6).
- 호출 site (`doAdjust` L122-125) 도 명시적 인자 6개로 갱신.

### Finding 2: F-L5-2 — `TransferStockService.transfer` 82 LOC extraction

`transfer(...)` 의 9 phase 중 3 phase 를 private method 로 추출:

- `loadOrCreateTarget(command, warehouseId, now)` → `record TargetResolution(Inventory target, boolean wasCreated)` 반환. L98-104 의 14 LOC 발췌.
- `applyLegs(source, target, transfer, quantity, actorId, now)` → `record LegPair(InventoryMovement firstLeg, InventoryMovement secondLeg, Inventory first, Inventory second)` 반환. L113-120 의 9 LOC 발췌 (id-ascending 결정 + 두 leg apply).
- `buildTransferredEvent(savedTransfer, persistedSource, persistedTarget, warehouseId, command, targetWasCreated, now)` → `InventoryTransferredEvent` 반환. L132-146 의 15 LOC 발췌 (locationCode lookup + Endpoint 2 개 + event 본문).

`transfer(...)` 본문은 ~82 LOC → ~50 LOC.

추출 unit 의 책임:
- `loadOrCreateTarget`: target 행 존재 시 그대로, 없을 시 `Inventory.createEmpty(...)` 로 생성, `wasCreated` flag 동반 반환. side-effect 없음 (DB 쓰기 없음).
- `applyLegs`: id-ascending 으로 두 Inventory 행에 transferOut/transferIn 도메인 메서드 호출, 2 movement 와 first/second 식별자를 함께 반환.
- `buildTransferredEvent`: `masterReadModel.findLocation(...)` 두 번 + Endpoint 2 개 build + event 본문 생성. side-effect 없음.

### Finding 3: F-L5-3 — `listViews` WHERE/bind dual-walk dedup

`InventoryRepositoryImpl` 에 private static record (또는 inner class) `Filter` 도입:

```java
private record Filter(String whereFragment, String paramName,
                      java.util.function.Function<InventoryListCriteria, Object> valueSupplier,
                      java.util.function.Predicate<InventoryListCriteria> isEnabled) {}
```

- Filter[] 정적 배열 (6 entry: warehouseId, locationId, skuId, lotId, hasStock, minAvailable) 정의.
- `listViews` body:
  - `where` builder: `for (Filter f : FILTERS) { if (f.isEnabled().test(c)) where.append(' ').append(f.whereFragment()); }`
  - `bindFilters` body: `for (Filter f : FILTERS) { if (f.isEnabled().test(c) && f.paramName() != null) { dataQuery.setParameter(f.paramName(), f.valueSupplier().apply(c)); countQuery.setParameter(...); } }`
- `hasStock` filter 의 `paramName=null` (no parameter binding) 처리 — Filter 정의에서 paramName nullable.
- 결과: WHERE-clause append + bind-filters 의 8 conditional × 2 walk = 16 conditional → Filter[] 6 entry 와 두 loop 4 + 4 = 8 lines. SQL semantics + parameter binding **byte-identical** 보존.

Out:

- `doMarkDamaged` + `doWriteOffDamaged` 의 inline persistence flow 통합 (각 method 가 다른 return type — markDamaged 는 `List<InventoryMovement>` 반환; over-zealous extension). 본 task 에서는 `persistAdjustmentResult` shrink 만 진행.
- Domain method (`Inventory.adjust` / `transferOut` / `transferIn` / `markDamaged` / `writeOffDamaged`) 변경 0 — pure application-service layer + adapter layer 의 internal extraction.
- API / event contract 변경 0.
- DB schema / Flyway migration 변경 0.
- Domain invariant (T1 idempotency / T3 outbox / W1 atomic transfer / W2 movement append-only / W4-W6 reserve-confirm) 변경 0.
- Test 코드 변경 = mechanical fixture update 만 (test what 의 verify 는 unchanged).

## Acceptance Criteria

AC-1. `AdjustStockService.persistAdjustmentResult` signature 가 6 parameter (StockAdjustment, InventoryMovement, Inventory, MovementType, boolean reducedAvailable, Counter counter). 호출 site `doAdjust` L122-125 가 새 signature 로 갱신. `doAdjust` 의 외부 observable behavior (4 가지 mutation: adjustmentRepository.insert / inventoryRepository.updateWithVersionCheck / movementRepository.save / outboxWriter.write / counter.increment / lowStockDetection.evaluate / log.info) byte-identical 순서로 보존.

AC-2. `TransferStockService.transfer` body 가 ≤ 60 LOC (현재 ~82 LOC 에서 ≥25% 감소). 3 신규 private method (`loadOrCreateTarget`, `applyLegs`, `buildTransferredEvent`) 도입. id-ascending lock order, target wasCreated flag, locationCode lookup, low-stock evaluation, log emission 모두 byte-identical 순서로 보존.

AC-3. `InventoryRepositoryImpl.listViews` 의 WHERE clause + parameter binding 이 Filter[] 정적 배열 + 단일 loop 로 통합. SQL 출력 (WHERE 부절 순서 + parameter binding 순서) 가 기존과 byte-identical. `bindFilters` private method 삭제 또는 단순화.

AC-4. `./gradlew :projects:wms-platform:apps:inventory-service:check --rerun-tasks` 로컬 BUILD SUCCESSFUL.

AC-5. CI 19/20 GREEN authoritative (`Integration (master-service + notification-service, Testcontainers)` + wms IT/E2E job 포함). FAILURE = 0.

AC-6. cross-service / cross-project drift 없음 — `projects/scm-platform/` + `projects/global-account-platform/` + `projects/erp-platform/` + `projects/finance-platform/` + `projects/fan-platform/` + `projects/ecommerce-microservices/` + `projects/platform-console/` + `libs/` 변경 0. (**38회째 zero-retrofit invariant** 검증.)

AC-7. Domain method signatures 변경 0 — `Inventory.adjust/transferOut/transferIn/markDamaged/writeOffDamaged` byte-unchanged.

## Related Specs

- `projects/wms-platform/specs/services/inventory-service/architecture.md` (특히 § Layer Rules, § Key Domain Invariants, § Transfer Atomicity W1)
- `projects/wms-platform/specs/services/inventory-service/domain-model.md`
- `projects/wms-platform/specs/contracts/http/inventory-service-api.md` (변경 없음)
- `projects/wms-platform/specs/contracts/events/inventory-events.md` (변경 없음)
- `platform/refactoring-policy.md` § Allowed Refactoring Categories: Extract Method (Low risk)
- `platform/coding-rules.md` (있을 시)
- `rules/domains/wms.md` § Inventory bounded context (W1 transactional protection)
- `rules/traits/transactional.md` § T3 outbox (변경 0 보존)
- `.claude/skills/backend/architecture/hexagonal/SKILL.md`
- `.claude/skills/backend/refactoring/SKILL.md`

## Related Contracts

- 본 task 는 contract 변경 0. API / event schema unchanged.

## Edge Cases

- **`persistAdjustmentResult` 와 `doAdjust` 외 호출 site**: 현재 단일 caller (`doAdjust`) 만 사용. 다른 doMarkDamaged / doWriteOffDamaged 는 inline 으로 persistence 수행 — 본 task 의 shrink 가 다른 method 에 영향 0.
- **`transfer()` 의 id-ascending lock order**: deadlock 방지의 핵심 invariant. `applyLegs` 가 동일 id 비교 + leg apply 순서 보존 의무. side-effect 가 추출 후에도 정확히 같은 순서로 발생함을 unit test 가 verify (기존 TransferStockServiceTest 가 cover).
- **`Inventory.createEmpty` 가 application layer 에서 호출**: domain factory 정상 사용. `loadOrCreateTarget` 의 `target` 변수가 lambda 안에서 `UUID.randomUUID()` 호출 — 동일 동작 보존.
- **`listViews` 의 `hasStock` filter**: paramName 없는 conditional WHERE (OR 절). Filter 정의에서 paramName nullable + binding loop 가 paramName != null 일 때만 bind 처리.
- **`Filter` 정의 layer placement**: `InventoryRepositoryImpl` 내부 private static record 으로 한정 (adapter-internal, 외부 layer 로 누출 0). port 에는 도입 0.

## Failure Scenarios

- **`persistAdjustmentResult` 호출 후 counter.increment 누락**: counter 매개변수 호출이 정확한 위치에 보존되어야 함. AC-1 grep verify (`adjustCounter.increment` + `markDamagedCounter.increment` + `writeOffCounter.increment` 3 호출 위치).
- **`transfer()` 의 outbox event 가 변경된 순서로 emit**: AC-2 의 byte-identical 순서 보존 검증으로 catch. 기존 통합 테스트가 event payload 검증 → CI verify.
- **`listViews` 의 SQL 빌드 결과 변경**: WHERE 절 conditional 순서 변경 시 SQL 출력이 byte-shift. Filter[] 순서가 기존 conditional 순서 (warehouseId → locationId → skuId → lotId → hasStock → minAvailable) 와 일치 의무. 기존 QueryInventoryRepositoryIT (Testcontainers Postgres) 가 검증.
- **Saga 영향**: TransferStockService 의 reservation-saga (T8 EventDedupe + Propagation.MANDATORY) 는 W4 reserve/confirm/release 경로 — 본 task 의 transfer() 와 무관. 그러나 architecture.md § Saga Participation 의 reservation TTL (Category D) 이 본 service 안에 있으므로 saga review 의무. `transfer()` 는 saga state-transition 경로 **아님** (Category A 의 outbound saga 의 callee 일 뿐, 본 method 는 W1 transfer atomicity 만 다룸). saga review = "scope 외" 확인 결과.

## Approach Notes

- Refactoring policy § Extract Method (Low risk) 카테고리에 해당. T5 optimistic-lock retry / T3 outbox / W2 movement append-only invariant 모두 method extraction 내부에서 보존 가능.
- 본 task 는 production code 변경 + test fixture mechanical update 가능 (예: TransferStockServiceTest 의 internal field 접근 변경 시).
- spec PR + impl PR + close-chore PR 3-PR sequence 사용. impl PR 의 CI 가 wms IT + 4 E2E job authoritative verify.
- BE-301 cycle 의 9th-instance "agent finding category verify 의무" 답습 — 본 task scope 정의 시 main session 이 3 finding 모두 실재 verified (각 method 의 file + line + LOC count 확인 완료).

## 분석/구현 권장 모델

- 분석=Opus 4.7 (verification + scope definition)
- 구현 권장=Sonnet 4.6 (3 method extraction, mechanical, no behavior change)
