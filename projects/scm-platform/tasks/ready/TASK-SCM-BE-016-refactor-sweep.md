# Task ID

TASK-SCM-BE-016

# Title

scm refactor sweep (2026-05-25 scan)

# Status

ready

# Owner

backend

# Task Tags

- code

---

# Goal

2026-05-25 scan 에서 scm-platform 3 서비스에 식별된 L1~L6 hotspot 을 단일 PR 로 정리. 외부 동작 변경 없음. `search()` sort 손실은 기능 정합성 회복 (버그 fix).

---

# Scope

## In Scope

| L | 대상 | 변경 |
|---|---|---|
| L1 | `apps/procurement-service/.../infrastructure/persistence/jpa/{Asn,AuditLog,PoStatusHistory,PurchaseOrder,Supplier}RepositoryAdapter.java` (5개) | → `*RepositoryImpl` |
| L1 | `apps/inventory-visibility-service/.../adapter/outbound/persistence/adapter/{EventDedupe,InventoryNode,InventorySnapshot,NodeStaleness}RepositoryAdapter.java` (4개) | → `*RepositoryImpl` |
| L1 | `apps/gateway-service/.../filter/JwtHeaderEnrichmentFilter.java:133` `multi.stream().collect(Collectors.joining(","))` | `String.join(",", multi)` |
| L5 | `apps/procurement-service/.../application/PurchaseOrderApplicationService.java:149` `acknowledge()` if-chain | `Set<PoStatus> ALREADY_PAST_SUBMITTED` 상수 추출 |
| L6 | `apps/procurement-service/.../application/PurchaseOrderApplicationService.java:109,137,166,187,208,261` (6 메서드) | `recordTransition(po, previous, next, actor, note)` private helper 로 `auditLogRepository.save(AuditLog.of(...))` + `historyRepository.save(PoStatusHistory.record(...))` 쌍 통합 |
| L5 (bug) | `apps/procurement-service/.../application/PurchaseOrderApplicationService.java:284` `search()` | `PageRequest.of(pageQuery.page(), pageQuery.size())` → `pageQuery.toPageable()` (sort/direction 보존). PageQuery 에 `toPageable()` 가 없으면 추가 |
| L3 | `apps/procurement-service/.../presentation/controller/AsnWebhookController.java:37,57` `verifySignature()` | `WebhookSignatureVerifier` infrastructure 컴포넌트로 분리. controller 는 verifier 주입받아 호출만. (또는 `OncePerRequestFilter`로 분리하면 controller 에서 검증 코드 완전 제거) |
| L6 | `apps/inventory-visibility-service/.../adapter/inbound/messaging/Wms*Consumer.java` (3개) `getStringField` / `getLongField` helper | 공통 `WmsEnvelopeParser` 유틸 추출 |
| L6 | `apps/inventory-visibility-service/.../adapter/inbound/messaging/{WmsInventoryAdjusted,WmsInventoryTransferred}Consumer.java` | `WmsInventoryReceivedConsumer.InvalidEnvelopeException` inner-class 참조 → `WmsEnvelopeParser.InvalidEnvelopeException` 또는 top-level 로 분리 |
| L5+L6 | `apps/inventory-visibility-service/.../application/service/InventoryVisibilityApplicationService.java:67,101,139,140` 3 apply 메서드 5-step 공통 흐름 (`applyInventoryReceived` / `Adjusted` / `Transferred`) | `applySnapshotDelta(nodeId, sku, qty, isAddition, eventId, occurredAt, tenantId, sourceTopic)` private helper 로 통합. `applyInventoryTransferred` 의 src/dst 두 호출은 helper 2회 호출 |

## Out of Scope

- API/event contract 변경
- `PageQuery.toPageable()` 가 libs/common 에 있다면 사용; 없으면 service-local 추가 (libs 변경 회피)

---

# Acceptance Criteria

- [ ] L1 9개 file rename + gateway 1 line 축약
- [ ] `ALREADY_PAST_SUBMITTED` 상수 도입, `acknowledge()` if-chain 단순화
- [ ] `recordTransition` helper 도입, audit+history `save` 인라인 호출 0 (6 use-case 모두 helper 호출만)
- [ ] `search()` 가 `pageQuery.sort()` / `direction()` 정보 사용 — 단위 테스트로 sort param 이 query 에 반영되는지 확인
- [ ] `WebhookSignatureVerifier` (또는 OncePerRequestFilter) 클래스 존재, `AsnWebhookController` 의 `verifySignature` private method 제거
- [ ] `WmsEnvelopeParser` 유틸 클래스 도입, 3 consumer 의 `getStringField`/`getLongField` private method 0
- [ ] `InvalidEnvelopeException` 이 inner class 가 아닌 top-level (또는 `WmsEnvelopeParser.InvalidEnvelopeException`)
- [ ] `applySnapshotDelta` helper 도입, 3 apply 메서드 본문 line 수 30 미만
- [ ] `./gradlew :projects:scm-platform:apps:{procurement,inventory-visibility,gateway}-service:check` 3개 모두 GREEN
- [ ] contract / schema 변경 0건

---

# Related Specs

> **Before reading Related Specs**: `platform/entrypoint.md` Step 0.

- `platform/refactoring-policy.md`
- `platform/naming-conventions.md`
- `platform/coding-rules.md`
- `projects/scm-platform/specs/services/{procurement,inventory-visibility,gateway}-service/architecture.md`
- `rules/traits/audit-heavy.md` — `recordTransition` 통합 시 A1 (모든 transition → audit row) 보장
- `rules/traits/integration-heavy.md` — webhook 검증 분리

# Related Skills

- `.claude/skills/backend/refactoring/SKILL.md`

# Related Contracts

- inventory-visibility consumer 의 event envelope shape — 변경 없음 보장

---

# Target Services

- procurement-service, inventory-visibility-service, gateway-service

---

# Implementation Notes

- `search()` sort 손실은 기능 버그 — refactor 가 아니라 fix. unit/IT test 추가 필요 (sort 적용 검증).
- `WebhookSignatureVerifier` 분리 위치: `infrastructure/security/` 또는 `infrastructure/webhook/`. service-local 컴포넌트로 두면 됨.
- `WmsEnvelopeParser` 위치: `adapter/inbound/messaging/` 아래 utility class. Spring bean 일 필요 없음 (static method).

---

# Edge Cases

- `recordTransition` 추출 시 audit row 와 history row 의 timestamp 가 다른 `Clock.instant()` 호출이면 timestamp drift — helper 안에서 단일 호출.
- `WebhookSignatureVerifier` 추출 후 `@Value("${...webhook-secret}")` 은 verifier 로 이동 — properties binding 확인.
- `search()` sort 적용 후 DB 쿼리 plan 이 바뀌면 성능 영향 — 큰 테이블이면 index 검토.

---

# Failure Scenarios

- `WebhookSignatureVerifier` 분리 후 signature 검증이 누락된 경로가 생기면 보안 결함 → IT 로 검증 (잘못된 서명 → 401).
- `WmsEnvelopeParser` 의 exception 변경 시 기존 catch block 의 type 이 안 맞으면 NoClassDefFoundError.
- `applySnapshotDelta` 통합 시 src/dst transferred 의 transactional boundary 가 다르면 atomicity 위반 — 같은 `@Transactional` scope 안 확인.

---

# Test Requirements

- 기존 단위 + IT 전부 통과
- `search()` sort 적용 검증 단위 테스트 1개 (`asc`/`desc` 각각 reflected)
- webhook 잘못된 signature → 401 IT 1개

Test command:

```
./gradlew :projects:scm-platform:apps:procurement-service:check :projects:scm-platform:apps:inventory-visibility-service:check :projects:scm-platform:apps:gateway-service:check
```

---

# Definition of Done

- [ ] 10개 변경 항목 전부 구현
- [ ] 3 service `:check` BUILD SUCCESSFUL
- [ ] `search()` sort fix 검증
- [ ] commit + push
- [ ] Ready for review
