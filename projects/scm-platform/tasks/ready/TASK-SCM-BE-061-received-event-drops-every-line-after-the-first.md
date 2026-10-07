# Task ID

TASK-SCM-BE-061

# Title

`wms.inventory.received.v1` 이벤트의 라인이 둘 이상이면 **첫 라인만 반영되고 나머지는 «중복» 으로 조용히 버려진다** — 라인마다 같은 `eventId` 로 dedupe 를 검사·기록한다

# Status

ready

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

- [ ] **AC-0** — 위 결함을 테스트로 먼저 재현(라인 2개 이벤트 → 수정 전 스냅샷 1개)하고, 수정 후 같은 테스트가 2개.
- [ ] **AC-1** — 이벤트 단위 dedupe 로 수정. 재전달 멱등 테스트 초록.
- [ ] **AC-2** — adjusted/transferred 경로 판정(같이 고쳤거나, 해당 없음의 근거).
- [ ] **AC-3** — 모듈 테스트 rc=0 · CI 초록.

# Related Specs

- `projects/scm-platform/specs/services/inventory-visibility-service/` (소비 · 멱등)

# Related Contracts

- `projects/wms-platform/specs/contracts/events/inventory-events.md` — `inventory.received` 의 `lines[]` (읽기만).

# Edge Cases

- 라인 중 하나가 잘못된 형식(skuId 없음) — 지금은 그 라인에서 예외 → 이벤트 재시도. 수정 뒤에도 부분 반영이 남지 않아야 한다(트랜잭션 경계).
- 같은 SKU 가 한 이벤트에 두 번 — 합산되는지 확인(생산자가 그런 이벤트를 내는지 포함).

# Failure Scenarios

1. **dedupe 를 라인 키(`eventId`+`skuId`)로 바꾼다** — 동작은 하지만 `processed_event` 스키마·의미가 바뀐다. 형제 경로와 같은 «이벤트당 한 번» 이 더 작다.
2. **테스트를 단일 라인으로만 쓴다** — 이 결함이 지금까지 안 보인 이유다.
