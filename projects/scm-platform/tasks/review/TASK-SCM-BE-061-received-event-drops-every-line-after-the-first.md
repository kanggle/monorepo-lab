# Task ID

TASK-SCM-BE-061

# Title

`wms.inventory.received.v1` 이벤트의 라인이 둘 이상이면 **첫 라인만 반영되고 나머지는 «중복» 으로 조용히 버려진다** — 라인마다 같은 `eventId` 로 dedupe 를 검사·기록한다

# Status

review

# Owner

scm-platform

# Task Tags

- bug
- event
- inventory-visibility

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet — 소비자 한 곳의 dedupe 단위를 «라인» 에서 «이벤트» 로 옮기는 수정 + 다중 라인 테스트. 형제 경로(`applyInventoryConfirmed`)가 이미 올바른 모양이다.

---

# Dependency Markers

- 출처: `TASK-MONO-768`(wms 데모 재고 시드) 구현 중 코드 읽기로 발견(2026-10-07 UTC). 768 은 이 결함을 피하려고 SKU 마다 한 줄짜리 ASN 을 만들었다 — 이 티켓이 닫히면 그 우회는 필요 없어진다(되돌릴지는 별도 판단).

# 배경 — 코드 실측 (2026-10-07 UTC)

`apps/inventory-visibility-service/.../adapter/inbound/messaging/WmsInventoryReceivedConsumer.consume`:

```java
for (Map<String, Object> line : lines) {
    ...
    applicationService.applyInventoryReceived(
            warehouseId, skuId, qtyReceived, warehouseCode,
            envelope.eventId(), envelope.occurredAt(), projectionTenant.id(), TOPIC);
}
```

`InventoryVisibilityApplicationService.applyInventoryReceived`:

```java
if (processedEventPort.isDuplicate(eventId)) { log.debug("Duplicate event skipped ..."); return; }
... apply one SKU ...
processedEventPort.markProcessed(eventId, ...);
```

⇒ 라인 1 이 `eventId` 를 처리됨으로 기록하고, 라인 2 부터는 같은 `eventId` 라 `isDuplicate` 가 참 → **debug 로그 한 줄만 남기고 반환.** 에러도 DLT 도 없다.

생산자(wms inventory-service `InventoryReceivedEvent`)는 `List<Line> lines` 를 싣는다 — 적치 완료(`inbound.putaway.completed`)에 라인이 여럿이면 다중 라인 이벤트가 나간다(`TASK-MONO-768` AC-0).

형제 경로 `applyInventoryConfirmed(warehouseId, List<ConfirmedLine> lines, eventId, ...)` 는 라인 목록을 한 번에 받아 dedupe 를 **이벤트당 한 번** 한다 — 올바른 모양이 이미 저장소에 있다.

# Goal

다중 라인 `wms.inventory.received.v1` 의 모든 라인이 scm 재고 스냅샷에 반영된다. 재전달(같은 `eventId`)은 여전히 전체가 한 번만 반영된다.

# Scope

## In Scope

- `applyInventoryReceived` 를 라인 목록을 받는 형태로(또는 dedupe 를 소비자 쪽 이벤트 단위로) 바꿔 **검사·기록을 이벤트당 한 번**, 라인 반영은 같은 트랜잭션 안에서 전부.
- 같은 모양의 결함이 `applyInventoryAdjusted` · `applyInventoryTransferred` 의 호출부에도 있는지 확인(그 이벤트가 다중 라인을 싣는지 계약에서 확인) — 있으면 같이, 없으면 «단일 라인 계약이라 해당 없음» 을 근거와 함께 적는다.
- 테스트: 라인 3개짜리 이벤트 → 스냅샷 3개 · 같은 이벤트 재전달 → 변화 없음 · 라인 중간 실패 → 전체 롤백(부분 반영 없음).

## Out of Scope

- wms 생산자 변경.
- 이미 버려진 과거 라인의 복구(데모 데이터는 재굽기로 새로 쌓인다).

# Acceptance Criteria

- [x] **AC-0** — 위 결함을 테스트로 먼저 재현(라인 2개 이벤트 → 수정 전 스냅샷 1개)하고, 수정 후 같은 테스트가 2개. `WmsInventoryReceivedConsumerMultiLineTest#twoLineEvent_appliesBothLines` — 수정 전 실행: `org.mockito.exceptions.verification.TooFewActualInvocations`(2회 기대한 `snapshotRepository.save` 가 1회만 — 라인 1 의 SKU-A 만 저장됨). 같은 테스트, 수정 후: GREEN(SKU-A/SKU-B 둘 다 저장). `ApplyInventoryReceivedUseCaseTest#threeLineEvent_createsThreeSnapshots` 로 3라인까지 추가 확인.
- [x] **AC-1** — 이벤트 단위 dedupe 로 수정. 재전달 멱등 테스트 초록. `ApplyInventoryReceivedUseCaseTest#redelivery_sameEventId_noChange`(동일 eventId 재호출 → save 추가 호출 없음, markProcessed 1회만) + `WmsInventoryReceivedConsumerIntegrationTest#receivedEvent_multiLine_duplicateEventId_skipped`(Testcontainers IT, Docker 미가용으로 로컬 미실행 — CI 의존, 아래 디비에이션 참조).
- [x] **AC-2** — adjusted/transferred 경로 판정: **해당 없음(단일 라인 계약)**. `projects/wms-platform/specs/contracts/events/inventory-events.md` §2 `inventory.adjusted`(L131-152) 의 payload 는 `lines[]` 배열이 아니라 단일 `delta`/`skuId`/`locationId` 객체, §3 `inventory.transferred`(L177-200) 의 payload 도 `lines[]` 없이 `source`/`target` 두 객체뿐 — 둘 다 "한 이벤트 = 한 라인" 계약이라 이 버그의 전제(한 eventId 로 여러 라인을 순회하며 같은 이벤트를 두 번째부터 중복으로 읽음)가 성립하지 않는다. `WmsInventoryAdjustedConsumer`/`WmsInventoryTransferredConsumer` 코드도 실제로 라인 루프 없이 단건 호출 1회(확인 완료, 수정 불필요).
- [x] **AC-3** — 모듈 테스트 rc=0: `./gradlew :projects:scm-platform:apps:inventory-visibility-service:test` rc=0, `:check` rc=0 (로컬, 2026-10-07 UTC). CI 초록은 PR 체크로 확인 — PR 본문에 링크.

# Related Specs

- `projects/scm-platform/specs/services/inventory-visibility-service/` (소비 · 멱등)

# Related Contracts

- `projects/wms-platform/specs/contracts/events/inventory-events.md` — `inventory.received` 의 `lines[]` (읽기만).

# Edge Cases

- 라인 중 하나가 잘못된 형식(skuId 없음) — 지금은 그 라인에서 예외 → 이벤트 재시도. 수정 뒤에도 부분 반영이 남지 않아야 한다(트랜잭션 경계). ✅ 확인: 수정 후 컨슈머는 **모든 라인을 먼저 파싱해 `List<ReceivedLine>` 을 완성한 다음** 애플리케이션 서비스를 한 번 호출한다 — 중간 라인의 `skuId` 결손은 리스트 생성 중(서비스 호출 이전)에 `InvalidEnvelopeException` 을 던지므로 그 전 라인조차 저장되지 않는다(부분 반영 원천 차단). `WmsInventoryReceivedConsumerIntegrationTest#receivedEvent_lineWithMissingSkuId_appliesNoLine` (IT, Docker 미가용으로 로컬 미실행) 로 node/snapshot 모두 생성되지 않음을 검증.
- 같은 SKU 가 한 이벤트에 두 번 — 합산되는지 확인(생산자가 그런 이벤트를 내는지 포함). ✅ 확인: 합산된다. `applySnapshotDelta` 는 `findByNodeIdAndSku` → 없으면 create, 있으면 `applyDelta` 이고, 같은 트랜잭션 안에서 두 번째 라인의 조회는 JPA 가 첫 번째 라인의 저장을 flush 한 뒤의 상태를 본다(Spring Data JPA `FlushMode.AUTO`). `ApplyInventoryReceivedUseCaseTest#sameSkuTwiceInOneEvent_sums` 로 단위 테스트에서 이 흐름을 모사해 10+5=15 를 확인. 생산자가 실제로 이런 이벤트를 내는지는 wms-platform 쪽 범위(Out of Scope — wms 생산자 변경 없음)이므로 "받으면 합산된다"까지만 보증.

# Failure Scenarios

1. **dedupe 를 라인 키(`eventId`+`skuId`)로 바꾼다** — 동작은 하지만 `processed_event` 스키마·의미가 바뀐다. 형제 경로와 같은 «이벤트당 한 번» 이 더 작다. (회피: 적용하지 않음 — 이벤트 단위 dedupe 유지, `applyInventoryConfirmed` 와 동일 모양.)
2. **테스트를 단일 라인으로만 쓴다** — 이 결함이 지금까지 안 보인 이유다. (회피: AC-0 재현 테스트는 2~3 라인으로 작성.)

---

# Implementation (TASK-SCM-BE-061, 2026-10-07 UTC)

**원인**: `WmsInventoryReceivedConsumer.consume` 이 이벤트의 `lines[]` 를 순회하며 (당시) 단일 라인 서명의 `applyInventoryReceived(warehouseId, skuId, qtyReceived, warehouseCode, eventId, ...)` 를 **라인마다** 호출했다. dedupe 검사/기록이 그 메서드 안에서 `eventId` 단위로 이루어지므로, 라인 1 호출이 `markProcessed(eventId)` 를 남기면 라인 2 호출의 `isDuplicate(eventId)` 가 참이 되어 조용히 skip 됐다.

**수정**:
1. `InventoryVisibilityApplicationService.applyInventoryReceived` 의 시그니처를 `(String warehouseId, List<ReceivedLine> lines, String warehouseCode, UUID eventId, Instant occurredAt, String tenantId, String sourceTopic)` 로 변경 — dedupe 검사/기록을 **메서드(=이벤트) 진입/종료 시 한 번만** 수행하고, 노드 해석(`resolveOrCreateNode`)도 한 번만, 그 사이에서 각 라인에 대해 `applySnapshotDelta` 를 반복한다. 형제 메서드 `applyInventoryConfirmed(warehouseId, List<ConfirmedLine> lines, ...)` 와 동일한 모양(새 레코드 `ReceivedLine(skuId, qtyReceived)` 도 `ConfirmedLine` 과 같은 패턴).
2. `WmsInventoryReceivedConsumer.consume` 이 라인마다 서비스를 호출하던 루프를 `List<ReceivedLine>` 를 먼저 완성한 뒤 서비스를 **한 번만** 호출하도록 변경.
3. 변경된 시그니처를 참조하던 기존 테스트 4개 파일(`ApplyWarehouseCodeUseCaseTest`, `ProcessedEventTest`, `ProjectionTenantConsumersTest`, 그리고 `AbstractInventoryVisibilityIntegrationTest` 의 `receivedEnvelope` 헬퍼에 다중 라인 오버로드 추가)을 새 시그니처에 맞춰 갱신.
4. 새 테스트: `WmsInventoryReceivedConsumerMultiLineTest`(AC-0 재현, 컨슈머+실서비스+mock 포트), `ApplyInventoryReceivedUseCaseTest`(3라인 적용/AC-1 재전달 멱등/중간 라인 실패 전파/동일 SKU 두 번 합산), `WmsInventoryReceivedConsumerIntegrationTest` 에 3개 IT 추가(3라인 성공/재전달 멱등/skuId 결손 라인 전체 미반영).

**디비에이션**: Testcontainers(PostgreSQL+Redis+Kafka) IT 는 이 Windows 호스트에 Docker 가 없어(`docker info` rc=1) 로컬 실행 불가 — 코드는 작성·커밋했고 CI(`integrationTest` 태스크, `@Tag("integration")`)에서 실행되는 것에 의존한다. 로컬에서는 `./gradlew :projects:scm-platform:apps:inventory-visibility-service:test`(단위+슬라이스, integrationTest 제외)와 `:check` 만 rc=0 확인.
