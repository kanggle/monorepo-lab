# Task ID

TASK-SCM-BE-011

# Title

`inventory-visibility-subscriptions.md` published alert envelope 5-field align with sibling `scm-procurement-events.md` standard shape (refactor-spec all 2026-05-13 audit SCM critical #1)

# Status

in-progress

# Owner

scm-platform

# Task Tags

- scm
- spec
- contracts
- events
- refactor
- envelope

---

# Goal

`/refactor-spec all --dry-run` (2026-05-13) audit 의 SCM critical #1 finding closure.

`projects/scm-platform/specs/contracts/events/inventory-visibility-subscriptions.md` 의 `## Published Events` § `scm.inventory.alert.v1` envelope 5 필드가 sibling `scm-procurement-events.md` 의 standard envelope shape (libs/java-messaging `BaseEventPublisher.writeEvent`) 와 불일치. cross-service envelope uniformity 회복 — downstream consumer 가 dual-source 파싱 시 동일 shape 가정 가능.

fix mapping (1:1):

| 현재 (inventory-visibility) | sibling standard (procurement) | 조치 |
|---|---|---|
| `eventVersion` (integer) | `schemaVersion` (integer) | rename |
| `producer` (string) | `source` (string) | rename + value 표기 (`"scm-platform-inventory-visibility-service"`) |
| `aggregateType` (string) | (없음) | drop |
| `aggregateId` (string) | `partitionKey` (string) | rename (현재 `aggregateId` = `nodeId` 와 동등, sibling 의 `partitionKey` 로 통합) |

결과 envelope 7 필드 = `eventId` / `eventType` / `source` / `occurredAt` / `schemaVersion` / `partitionKey` / `payload` (procurement standard 와 byte-identical shape).

provenance: `/refactor-spec all --dry-run` 2026-05-13 SCM audit Top-1 (envelope field naming mismatch → contract drift), TASK-BE-144 (WMS notification eventVersion int 1 align) + TASK-SCM-BE-010 (SCM HTTP error code align) 직접 sibling 패턴.

---

# Scope

## In Scope

### A. `inventory-visibility-subscriptions.md` envelope fix

`projects/scm-platform/specs/contracts/events/inventory-visibility-subscriptions.md` L49-80 의 `## Published Events` § `scm.inventory.alert.v1` 영역:

1. envelope JSON 의 4 field rename + 1 field drop (위 mapping table 참조).
2. sibling 패턴에 따라 envelope field 표 추가 (procurement-events.md L58-66 의 `| Envelope field | Type | Notes |` 표 답습).
3. 외부 reference: 사용하는 publisher 가 `BaseEventPublisher` 패턴인지 (libs/java-messaging) 확인 + spec 에 cite. **Best-effort (no outbox)** 표기는 보존 (현 envelope 본문의 alert 특성).

### B. publisher code 일관성 검증 (read-only — scope 한정)

- `projects/scm-platform/apps/inventory-visibility-service/.../KafkaAlertPublisherAdapter.java` 의 실제 emit field 가 spec 의 새 envelope (5 field align) 과 일치하는지 verify.
- 일치하지 않으면: 최소 production touch (field rename 4건 + drop 1건) 까지 본 task scope 에 포함. 동일 PR 에서 1 commit 분리.
- 일치 (이미 standard) 하면: spec-only fix 로 closure.

## Out of Scope

- `eventType` 값 prefix align (현재 `"inventory.alert.snapshot_stale"` vs sibling `"scm.procurement.po.submitted"` 의 `scm.` prefix 일관성). 별 follow-up 후보.
- `wms.inventory.{received,adjusted,transferred}.v1` consumer 영역 (본 file 의 `## Subscriptions` § L1-43) 무변경.
- 다른 SCM contract file (alert 외 publish 가 없으므로 N/A).

---

# Acceptance Criteria

### Impl PR

- [ ] `inventory-visibility-subscriptions.md` L55-72 envelope JSON 5 field align (eventVersion → schemaVersion / producer → source / aggregateType drop / aggregateId → partitionKey).
- [ ] envelope field 표 (sibling procurement-events.md L58-66 패턴) 추가 — 7 필드 description.
- [ ] partition key 표기 정합 — 본문 L53 `**Partition key**: nodeId` 와 envelope `partitionKey` 가 동일 value 가정 명시.
- [ ] `KafkaAlertPublisherAdapter` 실제 emit field verify — spec 과 일치하면 production 무변경, 불일치하면 production code align (별 commit).
- [ ] HARDSTOP-03 hook PASS (project-specific content 잔존 0).
- [ ] CI self-CI PASS (path-filter scm-platform — Integration + E2E + boot jars 자연 trigger 가능).
- [ ] task lifecycle ready → in-progress → review.
- [ ] tasks/INDEX.md 동기 (scm project + 영향 시 root INDEX).

### Close chore PR

- [ ] task Status review → done.
- [ ] git mv tasks/review → tasks/done.
- [ ] tasks/INDEX.md (scm) ## review 제거, ## done append 1-line outcome.

---

# Related Specs

- `projects/scm-platform/specs/contracts/events/inventory-visibility-subscriptions.md` (수정 대상).
- `projects/scm-platform/specs/contracts/events/scm-procurement-events.md` (sibling standard envelope source-of-truth).
- `projects/scm-platform/specs/services/inventory-visibility-service/architecture.md` (alert publish 영역 — cross-ref 갱신 필요 여부 spot-check).
- `docs/adr/ADR-MONO-004` (libs/java-messaging envelope v1 vs v2 distinction).

---

# Related Contracts

본 task = 직접 contract spec 수정. envelope field 표 rename + drop 1.

HTTP API: 무관 (event contract 단일).

**Breaking change scope**:

- 본 alert event consumer = downstream 미존재 (현재 spec 상 publish 만 정의, 구독 service 무). 즉 wire-level breaking impact 0.
- 다만 future consumer 가 본 file 을 source-of-truth 로 쓸 때 sibling 과 일관된 standard envelope 으로 정렬됨 = portfolio engineering signal.

---

# Target Service

`projects/scm-platform/apps/inventory-visibility-service/` (publish source).

---

# Architecture

scm-platform 의 inventory-visibility-service publish surface. event-consumer trait 이 주이지만 alert publish 도 가짐 (best-effort, no outbox per spec L51).

---

# Implementation Notes

## Envelope new shape (target state)

```json
{
  "eventId": "uuid",
  "eventType": "inventory.alert.snapshot_stale",
  "source": "scm-platform-inventory-visibility-service",
  "occurredAt": "2026-05-01T10:05:00Z",
  "schemaVersion": 1,
  "partitionKey": "node-uuid",
  "payload": {
    "nodeId": "node-uuid",
    "tenantId": "scm",
    "alertType": "SNAPSHOT_STALE",
    "stalenessStatus": "STALE",
    "detectedAt": "2026-05-01T10:05:00Z"
  }
}
```

## sibling envelope field 표 추가 (procurement-events.md L58-66 답습)

| Envelope field | Type | Notes |
|---|---|---|
| `eventId` | string (UUID v7) | Generated per envelope at publish time. |
| `eventType` | string | `"inventory.alert.<type>"` (예: `"inventory.alert.snapshot_stale"`). |
| `source` | string | Always `"scm-platform-inventory-visibility-service"`. |
| `occurredAt` | string (ISO 8601 UTC) | Wall-clock at envelope construction. |
| `schemaVersion` | integer | `1` for v1 envelope. |
| `partitionKey` | string | `nodeId` (matches Kafka record key for per-node ordering). |
| `payload` | object | Per-event shape (alertType, stalenessStatus 등). |

## publisher verify command

```bash
grep -rn "eventVersion\|producer\|aggregateType\|aggregateId" \
  projects/scm-platform/apps/inventory-visibility-service/
```

emit code 가 새 envelope (4 field rename + 1 drop) 과 일치 verify.

---

# Edge Cases

- envelope field 표가 본문 외부 어떤 file 도 reference 안 함 → spec-internal change.
- alert 의 second type `NODE_UNREACHABLE` 도 동일 envelope 적용 (이미 본문 L77 명시).
- `Best-effort (no outbox)` 표기는 alert characteristic 이라 보존 (envelope shape 와 별 dimension).

---

# Failure Scenarios

- publisher code 가 spec 의 새 envelope 과 어긋남 → production code touch 필요. 본 task scope 에 포함 (분리 commit).
- CI 회귀 — Integration (scm-platform) IT 가 envelope field 검증한다면 영향. cycle 1 verify 필요.
- 향후 consumer 가 본 alert event 구독 시 standard envelope 기반으로 parser 구현 가능 (현 v1 wire-level breaking impact 0).

---

# Test Requirements

- HARDSTOP-03 hook PASS.
- CI self-CI PASS (16/16 또는 path-filter 영향에 따라 15 SKIP + 1 PASS).
- 본 spec 의 linked-from cross-ref 검증 (procurement-events.md, architecture.md spot-check).
- production code 변경 시 unit / IT 영향 0.

---

# Definition of Done

### Impl PR

- [ ] AC 완료.
- [ ] task lifecycle ready → in-progress → review.

### Close chore PR

- [ ] review → done, INDEX 동기.

---

# Provenance

- `/refactor-spec all --dry-run` 2026-05-13 SCM audit critical #1 finding (envelope field naming mismatch).
- Sibling 답습 패턴: TASK-BE-144 (WMS notification eventVersion int 1 align, PR #451) + TASK-SCM-BE-010 (SCM HTTP error code align, PR #453) + TASK-MONO-083 (platform jwt-standard-claims cleanup, PR #455) — 모두 same-day single-PR closure.
- 분석=Opus 4.7 / 구현 권장=Sonnet 4.6 (mechanical envelope field rename + drop + sibling table 답습).
