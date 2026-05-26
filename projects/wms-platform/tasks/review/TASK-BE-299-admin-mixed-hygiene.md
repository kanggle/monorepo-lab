# Task ID

TASK-BE-299

# Title

admin-service mixed hygiene — Cohort C3: OperationsController service 위임 + RoleService silent catch 정정 + InventoryProjectionService magic constant + onAdjusted method split

# Status

review

# Owner

backend

# Task Tags

- code

---

# Goal

`/refactor-code wms admin-service` (2026-05-26 dry-run) 의 **Cohort C3 = L1+L2+L3+L5 mixed hygiene** 4 finding closure. behavior-neutral, low risk.

직전 BE-297 (Cohort C1, L5+L6 cascade dedup) closure 직속 후속. dry-run 결과 4 mixed finding:

- **F-L1-1**: `OperationsController` 가 `application.repository.AdminEventDedupeRepository` 직접 import (line 4) → architecture.md § Layer Rules rule 1 ("controllers thin, call application services") 위배. Layered style 에서 controller 가 application repository interface 직접 호출은 borderline; service 위임이 일관 패턴.
- **F-L2-1**: `RoleService.deserialisePermissions` (L193-200) silent catch (`catch (Exception e) { return List.of(); }`). `coding-rules.md § Exceptions` ("do not swallow exceptions silently") 위배. corrupt `permissionsJson` 마스킹.
- **F-L3-1**: `InventoryProjectionService.applySnapshot` (L327) `boolean lowStock = availableQty <= 10; // v1 default threshold` magic constant. 산문 주석 "recomputed at projection" 가 의도 명시하나, named constant 추출이 cleanliness.
- **F-L5-3**: `InventoryProjectionService.onAdjusted` (L150-200) ~50 LOC, 두 책임 (audit row append L155-181 + snapshot 업데이트 L183-198). private method 2 추출 가능.

BE-297 의 `AssignmentEventHelper` 패턴 (Layered architecture override 의 helper boundary 자연스러움) 재사용. Sweep cluster wms admin-service 의 2번째 cohort closure.

---

# Scope

## In Scope

| L | 대상 | 변경 |
|---|---|---|
| L1 | `apps/admin-service/src/main/java/com/wms/admin/api/dashboard/OperationsController.java` + new `apps/admin-service/src/main/java/com/wms/admin/application/projection/ProjectionStatusService.java` | 새 `ProjectionStatusService` (application/projection/) 신설. controller 의 직접 `dedupeRepository.countLifetime()` + `lagProbe.probe()` 호출 + `registry.getListenerContainers()` 폴백 로직 모두 service 로 이동. controller 가 `ProjectionStatusService.computeStatus()` 1-line 호출만. `AdminEventDedupeRepository` + `KafkaLagProbe` + `KafkaListenerEndpointRegistry` 모두 service field 로 이동. controller 의 import: `application.repository.AdminEventDedupeRepository` 제거 + `infra.observability.KafkaLagProbe` 제거 (둘 다 service 로 이동). |
| L2 | `apps/admin-service/src/main/java/com/wms/admin/application/role/RoleService.java` L193-200 | `deserialisePermissions` 의 silent catch 정정. `log.warn("Failed to deserialise permissions json: {}", permissionsJson, e)` 추가 + 기존 빈 list 반환 보존 (behavior-neutral). `org.slf4j.Logger` + `org.slf4j.LoggerFactory` import + `private static final Logger log = LoggerFactory.getLogger(RoleService.class)` field 추가 (기존에 없으면). |
| L3 | `apps/admin-service/src/main/java/com/wms/admin/application/projection/InventoryProjectionService.java` L327 | `boolean lowStock = availableQty <= 10;` → `boolean lowStock = availableQty <= DEFAULT_LOW_STOCK_THRESHOLD;` + class top 에 `private static final int DEFAULT_LOW_STOCK_THRESHOLD = 10;` 추가. 주석 보존 (`// v1 default threshold; recomputed at projection.`). |
| L5 | `apps/admin-service/src/main/java/com/wms/admin/application/projection/InventoryProjectionService.java` L150-200 `onAdjusted` | `onAdjusted` 의 두 phase 를 2 private method 로 추출: (a) `appendAuditRowIfAbsent(ProjectionEnvelope envelope, JsonNode p, Instant occurredAt)` ~25 LOC, (b) `updateSnapshotFromAdjustment(JsonNode p, Instant occurredAt): DedupeOutcome` ~15 LOC. main `onAdjusted` ~10 LOC: (1) calls (a) (2) calls (b) returns. behavior-neutral (모든 read/write site 보존). |

## Out of Scope

- BE-297 C1 회귀 verify only (cascade-revoke dedup 패턴 보존).
- **C2** (F-L6-2 4 ProjectionConsumer onMessage 16-line dispatch dedup) — medium risk Kafka IT 의존, 별 task BE-298 후보.
- `Setting` repository 도입으로 lowStock threshold dynamic 화 — 새 기능, refactor 영역 밖.
- `RoleService.deserialisePermissions` 의 throw 변경 (re-throw, runtime exception, etc.) — behavior change, current callers (`appendCreatedEvent` / `appendUpdatedEvent`) 의 처리 가정 변경 가능성 = scope 밖.
- API / event contract / schema 변경 (모두 internal hygiene).
- 다른 service (master / inventory / outbound / inbound / notification / gateway) 변경.
- libs/ 변경.

---

# Acceptance Criteria

- [ ] (A1-L1) 신규 `application/projection/ProjectionStatusService.java` 신설; `@Service` annotated. constructor 가 `AdminEventDedupeRepository` + `ObjectProvider<KafkaListenerEndpointRegistry>` + `ObjectProvider<KafkaLagProbe>` + `@Value("${spring.kafka.consumer.group-id:admin-projection}")` 받음. `public ProjectionStatusResponse computeStatus()` 메서드 = controller 의 기존 로직 그대로 transcribe.
- [ ] (A1-L1) `OperationsController` 가 `ProjectionStatusService` 만 inject. `dedupeRepository` / `registry` / `lagProbe` / `consumerGroup` field 모두 제거. controller method `projectionStatus()` 가 `service.computeStatus()` 1-line.
- [ ] (A1-L1) controller 의 `application.repository.AdminEventDedupeRepository` + `infra.observability.KafkaLagProbe` import = 0. controller 가 application service 만 import.
- [ ] (A2-L2) `RoleService.deserialisePermissions` 가 `log.warn(...)` 호출 1회 추가 (silent swallow 정정). 기존 빈 list 반환 보존 (behavior-neutral). `Logger` import + field 추가.
- [ ] (A3-L3) `InventoryProjectionService.DEFAULT_LOW_STOCK_THRESHOLD` static final field 추가 (value=10). L327 의 `<= 10` → `<= DEFAULT_LOW_STOCK_THRESHOLD` 변경. 주석 보존.
- [ ] (A4-L5) `InventoryProjectionService.onAdjusted` 가 2 private method 호출로 단축. method body 가 ~10 LOC. `appendAuditRowIfAbsent` + `updateSnapshotFromAdjustment` private methods 추가, 기존 동작 byte-identical.
- [ ] (A5) `./gradlew :projects:wms-platform:apps:admin-service:check --rerun-tasks` BUILD SUCCESSFUL.
- [ ] (A6) BE-297 회귀 0 (cascade-revoke dedup 패턴 보존: `AssignmentEventHelper.cascadeRevoke` + 2 caller).
- [ ] (A7) contract / event schema 변경 0, `GET /api/v1/admin/operations/projection-status` 응답 shape byte-identical, `admin.role.*` outbox event payload byte-identical.
- [ ] (A8) zero-retrofit invariant — `git diff --stat origin/main -- 'projects/{scm,global-account,erp,fan,ecommerce-microservices,finance,platform-console}-platform/' 'libs/'` = empty.

---

# Related Specs

> **Before reading Related Specs**: `platform/entrypoint.md` Step 0 (PROJECT.md = wms, domain=wms, traits=[transactional, integration-heavy]).

- `platform/refactoring-policy.md` — refactor 정의 (no behavior change).
- `platform/coding-rules.md` § Exceptions — silent catch 금지 (F-L2-1 fix 근거).
- `projects/wms-platform/specs/services/admin-service/architecture.md` § Architecture Style (Layered override) + § Layer Rules rule 1 ("controllers thin, call application services" — F-L1-1 fix 근거) + § Package Structure (api/application/domain/infra 4 layer).
- `rules/traits/transactional.md` — `@Transactional` 보존 (L2 변경은 application layer).

# Related Skills

- `.claude/skills/backend/refactoring/SKILL.md` — Extract Method (F-L5-3) + Move to Service Layer (F-L1-1) + Replace Magic Number (F-L3-1) + Add Logging (F-L2-1).

---

# Related Contracts

- `projects/wms-platform/specs/contracts/http/admin-service-api.md` § 6.2 — `/api/v1/admin/operations/projection-status` 응답 ProjectionStatusResponse shape (변경 0).
- `projects/wms-platform/specs/contracts/events/admin-events.md` — `admin.role.created` / `admin.role.updated` outbox events (deserialisePermissions 호출이 payload 의 permissions field 영향, behavior-neutral).

---

# Target Service

- `admin-service` (단일 service scope)

---

# Architecture

admin-service = Layered (architecture.md override). 본 task 의 4 fix 모두 layer 내부:

- **L1 (OperationsController)**: api/ → application/ 위임. `ProjectionStatusService` 가 application/projection/ 에 위치 (기존 `*ProjectionService` 패턴 답습). controller 는 service만 의존, repository + infra 의존 제거.
- **L2 (RoleService)**: application/ 내부 logging 추가. logging 은 application/ + domain/ 어디서나 허용 (SLF4J).
- **L3 (InventoryProjectionService)**: application/ 내부 constant. behavior-neutral.
- **L5 (InventoryProjectionService.onAdjusted)**: application/ 내부 method extract. behavior-neutral.

inter-layer 변경 = L1 만 (controller field 제거, service 도입). nontrivial 변경이지만 SCM-BE-017 의 L0 `SkuBreakdownCachePort` 도입 패턴 (presentation → application service 위임) 답습.

---

# Implementation Notes

1. **Pre-verify (BE-301 패턴 7회째)**: impl 단계에서 dispatcher main session 이 직접 (a) controller 의 직접 repository import grep / (b) RoleService silent catch 위치 grep / (c) InventoryProjectionService magic constant + onAdjusted 메서드 위치 grep 재실행. agent report 의 숫자/주장 불신, 직접 재검증.
2. **L1 method signature 권장**: `ProjectionStatusService` 의 constructor 가 `OperationsController` 의 기존 constructor 와 동일 signature (4 인자) — controller field 4개를 service field 4개로 1:1 이동. controller method body 가 service 로 그대로 transcribe.
3. **L2 logging 추가**: SLF4J 표준 패턴. `private static final Logger log = LoggerFactory.getLogger(RoleService.class);` field 추가 (RoleService 에 이미 logger field 있는지 확인). `log.warn("Failed to deserialise permissions json: {}", permissionsJson, e);` 추가 + 빈 list 반환 보존.
4. **L3 constant**: class top 의 `private static final String AGGREGATE_TYPE = ...;` 같은 기존 constant 옆에 배치. 주석 보존.
5. **L5 method extract**: `appendAuditRowIfAbsent(envelope, p, occurredAt)` + `updateSnapshotFromAdjustment(p, occurredAt)` 2 private method. main `onAdjusted` 가 두 method 호출만. 반환값 DedupeOutcome 보존.
6. **Branch**: `task/be-299-admin-mixed-hygiene` (substring `master` 없음 확인 ✓).
7. **Spec PR + impl PR + close-chore PR** 3 분리 (PR Separation Rule).
8. (분석=Opus 4.7 / 구현 권장=Sonnet 4.6 — 4 mechanical mixed hygiene, low risk; L1 의 새 class 도입은 medium-low 단순한 Move to Service 패턴)

---

# Edge Cases

- L1: `OperationsController` 의 `ObjectProvider<KafkaListenerEndpointRegistry>` + `ObjectProvider<KafkaLagProbe>` 가 standalone profile 에서 null 반환 (현재 동작). service 가 동일 `ObjectProvider.getIfAvailable()` 패턴 보존 — null 처리 그대로.
- L2: `permissionsJson` 이 null 인 경우 — 기존 코드는 `objectMapper.readValue(null, ...)` 시 NPE 또는 JsonProcessingException → catch 으로 빈 list. log.warn 추가 + 빈 list 보존.
- L3: 향후 `Setting` repository 로 dynamic 화 시 constant 의 위치 변경. 본 task scope 밖.
- L5: `onAdjusted` 의 `auditRepo.existsById(auditId)` 가드 동작 보존 — extract method 가 동일 조건 보존.

---

# Failure Scenarios

- L1 service 도입 시 Spring DI 실패 (typo / circular dependency) → ApplicationContext 시작 실패. CI `:check` 가 즉시 detection.
- L1 service 도입이 다른 test (예: `OperationsControllerTest`) 의 fixture 변경 요구 → test 의 constructor mock 업데이트 필요. BE-297 의 test fixture 3 site 일괄 패턴 답습.
- L2 logger field 가 RoleService 에 이미 있는지 미확인 → 중복 field. 사전 grep 확인.
- L5 method extract 가 변수 scope mismatch → compile fail. method body 의 모든 local variable 가 method param 또는 method 내부 declaration 으로 처리.

---

# Test Requirements

- baseline: main `dbeb6551` (post-BE-297 close-chore) `./gradlew :projects:wms-platform:apps:admin-service:check --rerun-tasks` GREEN.
- post-impl: 동일 명령어 GREEN. 추가 test 작성 불요 (모든 변경 = behavior-neutral, 기존 test 가 cover).
- 특히 `OperationsControllerTest` (있다면) 의 mock 패턴이 service 도입과 호환되는지 verify. 만약 unit test 가 4 dependency 직접 mock 했다면 새 service mock 1 dependency 로 단순화.
- CI Linux runner 가 Testcontainers IT 의 권위적 verify.

---

# Definition of Done

- [ ] (A1-A8) 모두 PASS.
- [ ] Branch: `task/be-299-admin-mixed-hygiene` (substring `master` 검증).
- [ ] PR: `refactor(wms-admin):` impl PR + close-chore PR (PR Separation Rule).
- [ ] Lifecycle: `ready/` → `review/` → `done/`.
- [ ] BE-303 3-dim verify ALL GREEN per stage.
- [ ] (분석=Opus 4.7 / 구현 권장=Sonnet 4.6)
