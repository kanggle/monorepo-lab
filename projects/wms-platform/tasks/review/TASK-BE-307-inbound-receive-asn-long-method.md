# TASK-BE-307 — inbound-service ReceiveAsnService method extraction (F-L3-1 long-method polish)

Status: review

## Goal

`inbound-service` 의 `/refactor-code` strategic-sampling dry-run 결과 식별된 F-L3-1 single finding 의 closure. `ReceiveAsnService.receive` (~58 LOC) 안의 4 inline logical block (warehouse 해결 + partner 해결 + asnNo 해결 + AsnLine 구성) 을 private helper method 로 추출하여 main method 책임을 단축.

dry-run 결과 base rate (inbound-service 199 main file scope — large surface, strategic-sampling ~15 file Read + grep verify):
- L0 (layer-violation) = 0 (Hexagonal port/adapter 깨끗, webhook signature/replay validation 이 ErpAsnWebhookController + HmacSignatureVerifier + TimestampWindowVerifier adapter 안에 위치 — domain-only Layer Rule #3 준수)
- L1 (auth) = 0 (`AuthorizationGuards.requireRole` utility 이미 추출 + role check 가 application service layer 에 위치 = inbound architecture.md 명시 패턴)
- L2 (dead-code) = 0
- L3 (long-method ≥30 LOC) = **1 (본 task scope F-L3-1)** + 1 borderline (F-L3-2 below, Out 명시)
- L4 (pattern-mismatch) = 0 (Hexagonal uniform across 7 wms backend services, MasterEventConsumer single-dispatcher + 6 strategy projectors 명시적 architecture.md 패턴)
- L6 (duplication) = 0 (`WebhookInboxStoreAdapter.ingest` + `EventDedupeRepositoryImpl.process` 의 `DataIntegrityViolationException duplicate` 2 catch 가 다른 outcome enum / 다른 callback / 다른 Propagation = dedup 불가능)
- intentional preservation 6개 인지 (verify exemption):
  1. **6 MasterXxxProjector strategy pattern** — `MasterEventConsumer.dispatch()` 가 dedupe / MDC / log 공통 처리, 6 projector 는 strategy implementation. `MasterEventConsumer` javadoc L27-32 명시: "intentionally departs from a base-class template. PR #304 (admin) showed pushing @Transactional onto a final template method breaks Awaitility ITs through self-invocation timing edge cases." = **admin BE-300 AbstractProjectionService 실패 lesson 이 이미 inbound 에 적용**.
  2. **`EventDedupeRepositoryImpl.process` `Propagation.MANDATORY` + `Runnable work` callback** — javadoc 명시: "Mirrors inventory-service's TASK-BE-027 pattern." = BE-027 호출자-tx-mandatory pattern 이미 적용.
  3. **`ErpWebhookInboxProcessor` + `WebhookInboxStatusUpdater` 분리** — javadoc L33-37 명시: "previous implementation used `@Lazy self` self-injection ... eliminated by extracting the status flip into dedicated bean — cross-bean delegation, no self-invocation hazard." = Spring AOP self-invocation 함정 이미 회피.
  4. **`MasterReadModelRepositoryImpl` 6 `upsert<Aggregate>`** — per-aggregate native SQL (다른 table / 다른 column list 5~8 / 다른 parameter binding 5~8). 외관상 cross-class duplication 처럼 보이지만 template builder 도입 시 SQL DSL readability 손실 + over-abstraction. 보존.
  5. **`ErpAsnWebhookController.receive`** — body 67 LOC 이지만 이미 4 private method 로 분리 (validateTimestampWindow / resolveSecret / verifySignature / parseAndValidate) + `ParseResult` inner record. 추가 extraction 거부.
  6. **`InstructPutawayService.instruct` (30 LOC main + 5 private method) + `InspectionService.record` (49 LOC main + 4 private method) + `CloseAsnService.close` (30 LOC main + computeSummary)** — 이미 BE-303 inventory TransferStockService 동일 패턴으로 method extraction 완료. 보존.

## Scope

In:

### Finding 1: F-L3-1 — ReceiveAsnService.receive() long-method polish

`ReceiveAsnService.receive` (L56-115, 본문 ~58 LOC) 안의 4 inline logical block 을 4 private method 로 추출:

```java
// Before (L56-115)
@Override
@Transactional
public AsnResult receive(ReceiveAsnCommand command) {
    if (!isSystemActor(command.actorId())) {
        AuthorizationGuards.requireRole(command.callerRoles(), InboundRoles.ROLE_INBOUND_WRITE);
    }

    // (1) warehouse resolution + active check — 5 LOC
    WarehouseSnapshot warehouse = masterReadModel.findWarehouse(command.warehouseId())
            .orElseThrow(() -> new WarehouseNotFoundInReadModelException(command.warehouseId()));
    if (!warehouse.isActive()) {
        throw new WarehouseNotFoundInReadModelException(command.warehouseId());
    }

    // (2) partner resolution + canSupply check — 6 LOC
    PartnerSnapshot partner = masterReadModel.findPartner(command.supplierPartnerId())
            .orElseThrow(() -> new PartnerInvalidTypeException(command.supplierPartnerId(), "not found in read model"));
    if (!partner.canSupply()) {
        throw new PartnerInvalidTypeException(command.supplierPartnerId(),
                "status=" + partner.status() + " type=" + partner.partnerType());
    }

    // (3) asnNo resolution + duplicate check — 6 LOC
    String asnNo = (command.asnNo() == null || command.asnNo().isBlank())
            ? asnNoSequence.nextAsnNo()
            : command.asnNo();
    if (asnPersistence.existsByAsnNo(asnNo)) {
        throw new AsnNoDuplicateException(asnNo);
    }

    Instant now = clock.instant();
    UUID asnId = UuidV7.randomUuid();

    // (4) AsnLine 구성 + SKU active check (loop) — 11 LOC
    List<AsnLine> lines = new ArrayList<>();
    int lineNo = 1;
    for (ReceiveAsnCommand.Line cmdLine : command.lines()) {
        SkuSnapshot sku = masterReadModel.findSku(cmdLine.skuId())
                .orElseThrow(() -> new SkuInactiveException(cmdLine.skuId()));
        if (!sku.isActive()) {
            throw new SkuInactiveException(cmdLine.skuId());
        }
        lines.add(new AsnLine(UuidV7.randomUuid(), asnId, lineNo++,
                cmdLine.skuId(), cmdLine.lotId(), cmdLine.expectedQty()));
    }

    // (5) Asn 생성 + save — 7 LOC
    Asn asn = new Asn(asnId, asnNo, AsnSource.valueOf(command.source()),
            command.supplierPartnerId(), command.warehouseId(),
            command.expectedArriveDate(), command.notes(),
            AsnStatus.CREATED, 0L, now, command.actorId(), now, command.actorId(), lines);
    Asn saved = asnPersistence.save(asn);

    // (6) event 발행 — 6 LOC
    List<AsnReceivedEvent.Line> eventLines = buildEventLines(saved, partner, masterReadModel);
    AsnReceivedEvent event = new AsnReceivedEvent(
            saved.getId(), saved.getAsnNo(), saved.getSource().name(),
            saved.getSupplierPartnerId(), partner.partnerCode(),
            saved.getWarehouseId(), saved.getExpectedArriveDate(),
            eventLines, now, command.actorId());
    eventPort.publish(event);

    log.info("asn_received asnId={} asnNo={} source={}", saved.getId(), saved.getAsnNo(), saved.getSource());
    return toResult(saved);
}
```

추출 후 (4 private method + main method ~28 LOC):

```java
@Override
@Transactional
public AsnResult receive(ReceiveAsnCommand command) {
    if (!isSystemActor(command.actorId())) {
        AuthorizationGuards.requireRole(command.callerRoles(), InboundRoles.ROLE_INBOUND_WRITE);
    }

    resolveActiveWarehouseOrThrow(command.warehouseId());
    PartnerSnapshot partner = resolveSupplierPartnerOrThrow(command.supplierPartnerId());
    String asnNo = resolveAsnNoOrThrow(command.asnNo());

    Instant now = clock.instant();
    UUID asnId = UuidV7.randomUuid();
    List<AsnLine> lines = buildAsnLines(command.lines(), asnId);

    Asn asn = new Asn(asnId, asnNo, AsnSource.valueOf(command.source()),
            command.supplierPartnerId(), command.warehouseId(),
            command.expectedArriveDate(), command.notes(),
            AsnStatus.CREATED, 0L, now, command.actorId(), now, command.actorId(), lines);
    Asn saved = asnPersistence.save(asn);

    List<AsnReceivedEvent.Line> eventLines = buildEventLines(saved, partner, masterReadModel);
    AsnReceivedEvent event = new AsnReceivedEvent(
            saved.getId(), saved.getAsnNo(), saved.getSource().name(),
            saved.getSupplierPartnerId(), partner.partnerCode(),
            saved.getWarehouseId(), saved.getExpectedArriveDate(),
            eventLines, now, command.actorId());
    eventPort.publish(event);

    log.info("asn_received asnId={} asnNo={} source={}", saved.getId(), saved.getAsnNo(), saved.getSource());
    return toResult(saved);
}

private WarehouseSnapshot resolveActiveWarehouseOrThrow(UUID warehouseId) {
    WarehouseSnapshot warehouse = masterReadModel.findWarehouse(warehouseId)
            .orElseThrow(() -> new WarehouseNotFoundInReadModelException(warehouseId));
    if (!warehouse.isActive()) {
        throw new WarehouseNotFoundInReadModelException(warehouseId);
    }
    return warehouse;
}

private PartnerSnapshot resolveSupplierPartnerOrThrow(UUID supplierPartnerId) {
    PartnerSnapshot partner = masterReadModel.findPartner(supplierPartnerId)
            .orElseThrow(() -> new PartnerInvalidTypeException(supplierPartnerId, "not found in read model"));
    if (!partner.canSupply()) {
        throw new PartnerInvalidTypeException(supplierPartnerId,
                "status=" + partner.status() + " type=" + partner.partnerType());
    }
    return partner;
}

private String resolveAsnNoOrThrow(String suppliedAsnNo) {
    String asnNo = (suppliedAsnNo == null || suppliedAsnNo.isBlank())
            ? asnNoSequence.nextAsnNo()
            : suppliedAsnNo;
    if (asnPersistence.existsByAsnNo(asnNo)) {
        throw new AsnNoDuplicateException(asnNo);
    }
    return asnNo;
}

private List<AsnLine> buildAsnLines(List<ReceiveAsnCommand.Line> cmdLines, UUID asnId) {
    List<AsnLine> lines = new ArrayList<>();
    int lineNo = 1;
    for (ReceiveAsnCommand.Line cmdLine : cmdLines) {
        SkuSnapshot sku = masterReadModel.findSku(cmdLine.skuId())
                .orElseThrow(() -> new SkuInactiveException(cmdLine.skuId()));
        if (!sku.isActive()) {
            throw new SkuInactiveException(cmdLine.skuId());
        }
        lines.add(new AsnLine(UuidV7.randomUuid(), asnId, lineNo++,
                cmdLine.skuId(), cmdLine.lotId(), cmdLine.expectedQty()));
    }
    return lines;
}
```

`resolveActiveWarehouseOrThrow` 의 return 값은 caller 에서 사용하지 않지만 (warehouse 변수가 `receive` body 안 외부 use 없음) — 일관성 위해 return 보존 (`Asn` 생성 시 `command.warehouseId()` 직접 사용, warehouse snapshot 은 fence-only). 또는 `void` 로 단순화 가능 (둘 다 byte-identical behavior). **return-form 선택**: `void` (사용처 없음) — main method `WarehouseSnapshot warehouse = ...` 변수 제거.

Out:

- **F-L3-2 borderline: `ConfirmPutawayLineService.confirm()` ~55 LOC** — single linear flow (load → validate qty → location lookup → active/warehouse check → save confirmation → confirmLine transition → save instruction → asn lookup → publishIfTerminal → log → return). 단일 추출 가능 block 은 location validation (~8 LOC) 뿐 — single use-site + magnitude 작음 → marginal value. 본 task scope 외 (BE-305/306 의 single-finding conservative scope 답습). 미래 task 에서 같이 묶을 가치 검토 가능.
- **`MasterReadModelRepositoryImpl` 6 `upsert<Aggregate>` SQL** — per-aggregate native SQL (다른 table / column count / parameter count). template builder 도입 시 over-abstraction. 보존.
- **6 MasterXxxProjector strategy pattern** — `MasterEventConsumer` javadoc L27-32 명시 admin BE-300 lesson 적용. intentional preservation.
- **`EventDedupeRepositoryImpl` `Propagation.MANDATORY` + `Runnable work`** — BE-027 pattern javadoc 명시. intentional preservation.
- **`WebhookInboxStoreAdapter.ingest` + `EventDedupeRepositoryImpl.process` 2 catch handler** — 다른 outcome enum + 다른 callback + 다른 Propagation = dedup 불가능. 보존.
- **`ErpAsnWebhookController.receive`** — 67 LOC 이지만 이미 4 private method + ParseResult record 로 분리됨. 추가 extraction 거부.
- **`InstructPutawayService.instruct` + `InspectionService.record` + `CloseAsnService.close`** — 이미 BE-303 동일 패턴 method extraction 완료. 보존.
- 다른 service / cluster 변경 0.
- API / event contract / DB schema / domain method signature 변경 0.
- Domain layer / port interface 변경 0.
- Test 변경 = mechanical fixture update 만 (test what 의 verify 는 unchanged).

## Acceptance Criteria

AC-1. `ReceiveAsnService` 에 4 신규 private method:

```java
private void resolveActiveWarehouseOrThrow(UUID warehouseId);  // 5-line body byte-identical
private PartnerSnapshot resolveSupplierPartnerOrThrow(UUID supplierPartnerId);  // 6-line body byte-identical
private String resolveAsnNoOrThrow(String suppliedAsnNo);  // 6-line body byte-identical
private List<AsnLine> buildAsnLines(List<ReceiveAsnCommand.Line> cmdLines, UUID asnId);  // 11-line body byte-identical
```

각 method body 의 statement 순서 + thrown exception 종류 + error message format = byte-identical 보존.

AC-2. `receive()` body 가 ~28 LOC 로 축소:
- 4 inline block 이 4 private method 호출로 치환
- `WarehouseSnapshot warehouse` 변수 제거 (사용처 없음, `resolveActiveWarehouseOrThrow(UUID) → void`)
- `PartnerSnapshot partner` 변수 보존 (event 발행 시 `partner.partnerCode()` 사용)
- `String asnNo` 변수 보존 (Asn 생성 시 사용)
- `List<AsnLine> lines` 변수 보존 (Asn 생성 시 사용)

AC-3. `isSystemActor` + `buildEventLines` + `toResult` static method 3 개 + `start(StartInspectionCommand)` 같은 외부 사용처 (InspectionService 가 import) 모두 unchanged byte-identical 보존.

AC-4. `@Override @Transactional` annotation 위치 unchanged. authorization guard (L59-61) 위치 + actor check unchanged.

AC-5. CI 19/20 GREEN authoritative (`Integration (master-service + notification-service, Testcontainers)` + `E2E (gateway-master live-pair smoke, Testcontainers)` + GAP IT + scm/finance/erp/console-bff IT + 4 E2E job 포함; FAILURE = 0). `ReceiveAsnService` 가 inbound-service 의 ASN entry path 핵심 — webhook + manual REST 양쪽 경로 모두 호출하므로 inbound IT (existing) 가 verification authoritative.

AC-6. cross-service drift 없음 — `projects/wms-platform/apps/` 의 inbound-service 외 다른 6 service + `projects/{scm,global-account,erp,fan,ecommerce-microservices,finance,platform-console}-platform/` + `libs/` 변경 0. (**42회째 zero-retrofit invariant** 검증.)

AC-7. `AsnReceivedEvent` payload 의 6 field (asnId / asnNo / source / supplierPartnerId / partnerCode / warehouseId / expectedArriveDate / lines / occurredAt / actorId) byte-identical 보존. `log.info("asn_received ...")` format string + 3 placeholder byte-identical 보존.

AC-8. Domain method `Asn` constructor 13-arg signature unchanged. `AsnPersistencePort.save(Asn)` + `AsnPersistencePort.existsByAsnNo(String)` + `AsnNoSequencePort.nextAsnNo()` + `MasterReadModelPort.findWarehouse(UUID)` / `findPartner(UUID)` / `findSku(UUID)` interface unchanged.

AC-9. test 변경 = mechanical only — `ReceiveAsnServiceTest` (if exists) 의 mock interaction order + thrown exception assertion byte-identical 보존. test 의 verify 행위 변경 X.

## Related Specs

- `projects/wms-platform/specs/services/inbound-service/architecture.md` (Hexagonal port/adapter, ASN state machine § State Machine T4, § Key Domain Invariants, § Webhook Reception integration-heavy I6)
- `projects/wms-platform/specs/services/inbound-service/domain-model.md` (Asn aggregate, AsnLine, AsnStatus)
- `projects/wms-platform/specs/contracts/http/inbound-service-api.md` (변경 없음)
- `projects/wms-platform/specs/contracts/events/inbound-events.md` (변경 없음; `wms.inbound.asn.received.v1` payload 보존)
- `platform/refactoring-policy.md` § Allowed Refactoring Categories: Extract Method (Low risk)
- `rules/traits/transactional.md` § T1 idempotency + T3 outbox + T4 state-machine + T5 invariants
- `rules/traits/integration-heavy.md` § I6 webhook validation + I10 failure-mode tests
- `.claude/skills/backend/architecture/hexagonal/SKILL.md`
- `.claude/skills/backend/refactoring/SKILL.md`

## Related Contracts

- 본 task 는 contract 변경 0. API / event schema unchanged.
- `wms.inbound.asn.received.v1` payload 의 field 순서 + 값 byte-identical 보존.

## Edge Cases

- **System actor bypass (L59-61)**: `isSystemActor` (actorId.startsWith("system:")) 일 때 `AuthorizationGuards.requireRole` 건너뜀. 본 task 의 method extraction 이 이 bypass 위치 변경 X — body 의 첫 line 으로 유지.
- **`WarehouseNotFoundInReadModelException` 두 곳 throw**: (a) findWarehouse Optional empty + (b) isActive false. 둘 다 같은 exception type + 같은 warehouseId param 으로 byte-identical 보존.
- **`PartnerInvalidTypeException` 두 곳 throw**: (a) findPartner Optional empty (message: "not found in read model") + (b) canSupply false (message: "status=X type=Y"). message 두 종 byte-identical 보존.
- **`AsnNoDuplicateException`**: asnNo 가 sequence-generated 일 때도 duplicate check 수행 (race condition 방어). byte-identical 보존.
- **lineNo 1-based + post-increment**: `int lineNo = 1` → `lineNo++` byte-identical 보존. `buildAsnLines` 안에서 동일 lifecycle.
- **`UuidV7.randomUuid()` 호출 횟수**: receive() 안에서 1번 (asnId) + line loop 안 N번 (AsnLine.id). 추출 후 동일 횟수 + 동일 lifecycle 보존.

## Failure Scenarios

- **Method ordering 변경 시**: `receive` body 의 3 resolve method 호출 순서 (warehouse → partner → asnNo) byte-identical 보존 의무. 순서 변경 시 partial-failure state 가 변하여 IT 영향 가능.
- **Authorization guard 누락**: L59-61 의 system-actor bypass + role check 가 `receive` body 의 첫 statement 로 유지. private helper 안에 들어가면 webhook path (system actor) 의 role check skip 동작이 바뀜.
- **`warehouse` 변수 제거 안전성**: receive() body 안에서 `warehouse` variable 의 use 가 없음 (validateActive 후 즉시 discard, partner snapshot 만 후속 사용). 변수 제거 시 byte-identical behavior.
- **resolution method 의 `Throws` declaration**: 본문에서 throw 하는 RuntimeException 3 종 (WarehouseNotFoundInReadModelException / PartnerInvalidTypeException / AsnNoDuplicateException / SkuInactiveException) 은 RuntimeException 계열 — explicit `throws` 선언 불필요.

## Approach Notes

- Refactoring policy § Allowed Refactoring Categories: Extract Method (Low risk per policy, 본 task 는 same-class private method 4 추출, public surface 변경 0).
- BE-303 inventory TransferStockService.transfer() 의 3 method + 2 record 추출 (82 → 59 LOC) 답습. BE-303 의 8-arg shrink + 5 record 도입 패턴은 본 task 에 미적용 (4 method 만으로 충분, record 도입 시 over-abstraction).
- 같은 클래스 안 private method extraction — Spring AOP self-invocation 함정 없음 (caller `@Transactional` scope 안에서 그대로 실행).
- wms cluster 의 **6번째 service sweep** = inbound-service. 잔존 = outbound-service 1 service. **wms 7/7 TRUE 0 도달까지 1**.
- 199 file scope 의 strategic-sampling dry-run = master-service 214 file scope 의 15 file Read 답습. ReceiveAsnService 의 finding 하나 + 5 borderline verify exemption = base rate ≈ 0.005/file (master-service 와 동일 수준).

## 분석/구현 권장 모델

- 분석=Opus 4.7 (verification + 6 intentional preservation 검증)
- 구현 권장=Sonnet 4.6 (in-class private method 4 추출 + main method 1-line 치환 4 회, mechanical)
