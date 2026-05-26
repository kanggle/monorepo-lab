# Task ID

TASK-BE-301

# Title

inventory-service duplication sweep — Cohort A: `MasterRefValidator` + AdjustmentController guard helpers + `JwtHelper.actorId` (3 finding)

# Status

done

# Owner

backend

# Task Tags

- code

---

# Goal

`/refactor-code wms inventory-service` (2026-05-27 dry-run) 의 **Cohort A = L6 duplication sweep 3 finding** closure. behavior-neutral, low risk.

wms admin-service 8/8 sweep TRUE 0 직속 후속 — same-cluster (wms) 의 next service sweep. dry-run 결과 8 finding (L0=1 + L1=1 + L5=3 + L6=3), 본 task 는 L6 duplication 3 finding (가장 안전, mechanical) 우선 closure. L0/L1 (architecture compliance + saga review) 는 별 task. L5 (long-method) 는 별 cohort.

3 finding:

- **F-L6-1**: `validateMasterRefs` 가 `AdjustStockService.java:232-256` + `ReceiveStockService.java:140-164` 두 application service 에 byte-near-identical (Location active + SKU active + Lot active/expired 3-stage check, 24 LOC × 2 service = 48 LOC dup). dry-run agent 가 `TransferStockService.validateSku` 도 3번째 instance 라고 분류했으나 main session verify 결과 transfer 의 함수는 `requiresLot` 추가 검증 포함 (다른 로직) → scope 제외, **2 service 만 dedup**.
- **F-L6-2**: `AdjustmentController.java` 의 3 endpoint (`createAdjustment` L72-77 / `markDamaged` L101-106 / `writeOffDamaged` L126-131) 가 `Idempotency-Key` 헤더 부재 throw + `reasonNote` 3-char 검증 throw 의 byte-identical 패턴. 6-line × 3 endpoint = 18 LOC dup in single controller.
- **F-L6-3**: `actorId(Jwt jwt)` private static method 가 `AdjustmentController.java:193-198` + `ReservationController.java:138-143` + `TransferController.java:91-96` 3 controller 에 byte-identical (4-line method). 12 LOC dup across 3 file.

본 task 는 3 helper extraction:
1. `application/service/MasterRefValidator` — Location+SKU+Lot 3-stage active check
2. `AdjustmentController` 내부 private static methods `requireIdempotencyKey` + `requireReasonNote` (single file 내부)
3. `adapter/in/web/JwtHelper.actorId(Jwt)` static utility (adapter/in/web/ package)

BE-297 의 `AssignmentEventHelper.cascadeRevoke` 패턴 (Layered helper boundary) + BE-300 의 `ProjectionConsumerSupport` 패턴 (static utility) 답습. Hexagonal architecture 의 strict layer direction 보존 — 모든 helper 가 layer-correct (application → port-out, adapter.in → application).

---

# Scope

## In Scope

| 대상 | 변경 |
|---|---|
| 신규 `apps/inventory-service/src/main/java/com/wms/inventory/application/service/MasterRefValidator.java` | `@Component` (또는 plain class with `@Service`). constructor inject `MasterReadModelPort`. method `public void validate(UUID locationId, UUID skuId, UUID lotId)` — Location 활성 check + SKU 활성 check + lotId != null 시 Lot 활성/expired check (24-line block 정확한 transcribe). `MasterRefInactiveException` factory 호출 invariant 보존. |
| `apps/inventory-service/src/main/java/com/wms/inventory/application/service/AdjustStockService.java` L232-256 | private `validateMasterRefs` 메서드 제거. caller 가 `masterRefValidator.validate(inventory.locationId(), inventory.skuId(), inventory.lotId())` 1-line 호출. constructor 에 `MasterRefValidator` field/parameter 추가. |
| `apps/inventory-service/src/main/java/com/wms/inventory/application/service/ReceiveStockService.java` L140-164 | 동일 패턴 — `validateMasterRefs(line, expectedWarehouseId)` 의 master-ref 부분 (`line.locationId()`, `line.skuId()`, `line.lotId()`) 만 `masterRefValidator.validate(...)` 호출로 대체. warehouse 검증 부분이 있다면 보존. |
| `apps/inventory-service/src/main/java/com/wms/inventory/adapter/in/web/controller/AdjustmentController.java` L72-77, L101-106, L126-131 + L193-198 | `requireIdempotencyKey(String key)` + `requireReasonNote(String note)` 2 private static methods 신규. 3 endpoint 의 6-line guard → 각 2-line 호출 (`requireIdempotencyKey(idempotencyKey); requireReasonNote(request.reasonNote());`). `actorId` private static method (L193-198) 도 제거 → 신규 `JwtHelper.actorId` 호출. |
| `apps/inventory-service/src/main/java/com/wms/inventory/adapter/in/web/controller/ReservationController.java` L138-143 | `actorId` private static method 제거. `JwtHelper.actorId(jwt)` 호출로 대체. |
| `apps/inventory-service/src/main/java/com/wms/inventory/adapter/in/web/controller/TransferController.java` L91-96 | 동일 패턴 — `actorId` 제거 + `JwtHelper.actorId(jwt)` 호출. |
| 신규 `apps/inventory-service/src/main/java/com/wms/inventory/adapter/in/web/JwtHelper.java` | `public final class` + private constructor (utility pattern). `public static String actorId(Jwt jwt)` — null 가드 + `getSubject()` 우선, `getClaimAsString("actorId")` fallback (기존 4-line body 정확한 transcribe). |

## Out of Scope

- **F-L0-1** (`ShippingConfirmedConsumer` 가 `ReservationRepository` 직접 inject, Hexagonal direction 위반) — saga review 필요, 별 task (BE-302 후보).
- **F-L1-1** (`Inventory.writeOffDamaged` Javadoc + application layer auth 이동) — medium risk + security boundary, 별 task (BE-303 후보).
- **F-L5-1/F-L5-2/F-L5-3** (long-method polish: persistAdjustmentResult 12-arg + transfer() 82 LOC + listViews WHERE builder) — 별 cohort B 후보.
- **`TransferStockService.validateSku`** — `requiresLot` 추가 검증 포함 (다른 로직), 본 task scope 제외. transfer 의 validation 함수는 보존.
- API / event contract / schema 변경.
- 다른 service / libs/ 변경.

---

# Acceptance Criteria

- [ ] (A1) 신규 `application/service/MasterRefValidator.java` 신설; constructor inject `MasterReadModelPort`. 단일 메서드 `validate(UUID locationId, UUID skuId, UUID lotId)`. `MasterRefInactiveException` factory 호출 invariant 보존 (locationInactive / skuInactive / lotExpired / lotInactive).
- [ ] (A2) `AdjustStockService.validateMasterRefs` 제거; caller 가 `masterRefValidator.validate(...)` 호출. `MasterRefValidator` constructor inject.
- [ ] (A3) `ReceiveStockService.validateMasterRefs` 의 master-ref 부분 `masterRefValidator.validate(...)` 호출로 대체. warehouse 검증 등 추가 로직 있다면 보존.
- [ ] (A4) `AdjustmentController` 의 3 endpoint guard (Idempotency-Key + reasonNote) → 2 private static methods (`requireIdempotencyKey` + `requireReasonNote`). 3 endpoint body 각 2-line 호출.
- [ ] (A5) `AdjustmentController.actorId` private static method 제거. `JwtHelper.actorId(jwt)` 호출.
- [ ] (A6) `ReservationController.actorId` 제거. `JwtHelper.actorId(jwt)` 호출.
- [ ] (A7) `TransferController.actorId` 제거. `JwtHelper.actorId(jwt)` 호출.
- [ ] (A8) 신규 `adapter/in/web/JwtHelper.java` 신설; `public final class` + private constructor. `public static String actorId(Jwt jwt)` 기존 4-line body 정확한 transcribe.
- [ ] (A9) `./gradlew :projects:wms-platform:apps:inventory-service:check --rerun-tasks` BUILD SUCCESSFUL.
- [ ] (A10) wms admin BE-297/299/300 회귀 0 + inventory 기존 동작 보존:
  - REST API 응답 shape byte-identical
  - `MasterRefInactiveException` throw site invariant 보존 (location/sku/lot inactive/expired 4 throw 패턴)
  - `Idempotency-Key` 헤더 부재 시 `InventoryValidationException` throw 동일 message
  - `reasonNote` 3-char 미달 시 `AdjustmentReasonRequiredException` throw 동일 message
  - `actorId` fallback (null → "anonymous", subject → claim) byte-identical
- [ ] (A11) `TransferStockService.validateSku` byte-unchanged (Out of Scope).
- [ ] (A12) contract / event schema 변경 0, public HTTP API + outbox event payload byte-identical.
- [ ] (A13) zero-retrofit invariant — `git diff --stat origin/main -- 'projects/{scm,global-account,erp,fan,ecommerce-microservices,finance,platform-console}-platform/' 'libs/'` = empty.

---

# Related Specs

> **Before reading Related Specs**: `platform/entrypoint.md` Step 0 (PROJECT.md = wms, domain=wms, traits=[transactional, integration-heavy]).

- `platform/refactoring-policy.md` — refactor 정의 (no behavior change). transactional trait state-transition path 변경 금지 (본 task 는 validation/guard 영역만, state machine 무관).
- `platform/coding-rules.md` — duplication 제거 정책.
- `projects/wms-platform/specs/services/inventory-service/architecture.md` — Hexagonal layer structure + Internal Patterns (application 의 validation 책임 / adapter.in 의 controller guard 책임).
- `rules/traits/transactional.md` — T1 (idempotency key) 의 controller-level enforcement 보존.

# Related Skills

- `.claude/skills/backend/refactoring/SKILL.md` — Extract Method + Extract Helper Class patterns + § Baseline Check (compile + test GREEN before/after).
- BE-297 `AssignmentEventHelper.cascadeRevoke` 패턴 + BE-300 `ProjectionConsumerSupport` 패턴 (해당 task closure 의 메타 lessons 답습).

---

# Related Contracts

- `projects/wms-platform/specs/contracts/http/inventory-service-api.md` — `/api/v1/inventory/...` 응답 shape (변경 0건).
- `projects/wms-platform/specs/contracts/events/inventory-events.md` — outbox event envelope (변경 0건).

---

# Target Service

- `inventory-service` (단일 service scope)

---

# Architecture

inventory-service = **Hexagonal (Ports & Adapters)** (wms 표준, override 없음). 본 task 의 3 helper 모두 layer-correct:

- `MasterRefValidator` (application/service/) = application layer 의 validation helper. `MasterReadModelPort` (application/port/out) 만 의존. Hexagonal direction 보존.
- `requireIdempotencyKey` + `requireReasonNote` = `AdjustmentController` (adapter/in/web/) 내부 private static methods. layer 내부.
- `JwtHelper.actorId` (adapter/in/web/) = inbound adapter 의 inbound adapter-local utility. layer 내부.

inter-layer 변경 0. Hexagonal direction violation 없음.

BE-297 (`AssignmentEventHelper.cascadeRevoke`) 의 helper boundary 패턴 + BE-300 (`ProjectionConsumerSupport`) 의 static utility 패턴 답습. 두 패턴 모두 wms admin-service (Layered) 에서 검증됐고, 본 task 는 inventory-service (Hexagonal) 에서 동일 helper extraction pattern 적용.

---

# Implementation Notes

1. **Pre-verify (BE-301 패턴 9회째)**: impl 단계에서 dispatcher main session 이 직접 (a) 2 service `validateMasterRefs` byte-identical block verify / (b) `AdjustmentController` 3 endpoint guard pattern verify / (c) 3 controller `actorId` byte-identical body verify. agent dry-run 의 TransferStockService over-zealous 분류 confirmed-out (이미 verify, scope 정정).
2. **MasterRefValidator constructor**: `@Component` annotated (Spring DI). constructor `MasterRefValidator(MasterReadModelPort masterReadModel)`. Spring autowire.
3. **AdjustmentController 내부 helpers**: `private static void requireIdempotencyKey(String key)` + `private static void requireReasonNote(String note)`. throw exception types 보존 (`InventoryValidationException` / `AdjustmentReasonRequiredException`).
4. **JwtHelper static util**: `public final class JwtHelper` + `private JwtHelper() {}` + `public static String actorId(Jwt jwt)`. adapter/in/web/ package (3 controller 와 동일).
5. **AdjustStockService + ReceiveStockService constructor injection**: `MasterRefValidator masterRefValidator` field/parameter 추가. 기존 `MasterReadModelPort masterReadModel` field 보존 (다른 사용처 있을 수 있음 — grep verify). 만약 `validateMasterRefs` 가 유일한 `masterReadModel` 사용자라면 field 제거, 그렇지 않으면 보존.
6. **Test fixture impact**: `AdjustStockServiceTest` / `ReceiveStockServiceTest` 의 mock setup 이 `MasterReadModelPort` mock 했을 가능성 — 이제 `MasterRefValidator` mock 또는 real instance 로 변경 필요. BE-297 의 `AssignmentEventHelper` test fixture 일괄 패턴 답습 (3 test site).
7. **Branch**: `task/be-301-inventory-duplication-sweep` (substring `master` 검증 ⚠️ — branch 명에 `master` 가 있음! "MasterRefValidator" 또는 "master-ref-validator" 회피 필요. 안전한 이름 = `task/be-301-inventory-dedup-sweep` 또는 `task/be-301-inventory-l6-dedup`). 이름 신중 선택.
8. **Spec PR + impl PR + close-chore PR** 3 분리 (PR Separation Rule). impl PR 의 CI authoritative verify 필수 (Hexagonal + Testcontainers IT).
9. (분석=Opus 4.7 / 구현 권장=Opus 4.7 — 3 helper extraction + 2 service + 3 controller dedup, Hexagonal layer 신중)

---

# Edge Cases

- `MasterRefValidator.validate` 가 lotId=null 일 때 Lot check skip — 기존 `if (inventory.lotId() != null)` 조건 보존.
- `AdjustStockService` 의 `validateMasterRefs(inventory)` vs `ReceiveStockService` 의 `validateMasterRefs(line, expectedWarehouseId)` signature 차이 — `ReceiveStockService` 에 warehouse 검증 추가 로직 있다면 helper 호출 후 warehouse 검증 별도 보존.
- `actorId` 의 `jwt == null` 가드 — `JwtHelper` 가 동일 가드 (null → "anonymous"). 3 controller 의 호출 site 가 jwt nullable 가능성.
- `Idempotency-Key` 헤더 부재 시 `InventoryValidationException` throw — `requireIdempotencyKey` helper 가 동일 throw + message.

---

# Failure Scenarios

- `MasterRefValidator` Spring DI 실패 (typo / circular dependency) → ApplicationContext 시작 실패. CI `:check` 가 즉시 detection.
- test fixture mock 누락 (`@MockBean MasterRefValidator` 또는 `@MockBean MasterReadModelPort` mismatch) → test fail. 사전 grep 으로 affected test site 식별.
- branch 명에 `master` substring 잠재 위험 — 신중 검증 필요 (sandbox push regex). `task/be-301-inventory-dedup-sweep` 권장.
- `TransferStockService.validateSku` 의 다른 로직 보존 누락 → 본 task scope 외 의도치 않은 변경 → CI fail. AC-11 (`TransferStockService.validateSku` byte-unchanged) 강제.

---

# Test Requirements

- baseline: main `b8c6bb35` (post-BE-300 close-chore) `./gradlew :projects:wms-platform:apps:inventory-service:check --rerun-tasks` GREEN.
- post-impl: 동일 명령어 GREEN. test 의 mock setup 업데이트 (BE-297 패턴 3 site mechanical).
- CI Linux runner 가 Testcontainers IT 의 권위적 verify (per `project_testcontainers_docker_desktop_blocker.md`).

---

# Definition of Done

- [ ] (A1-A13) 모두 PASS.
- [ ] Branch: `task/be-301-inventory-dedup-sweep` (substring `master` 회피 검증 + 명시).
- [ ] PR: `refactor(wms-inventory):` impl PR + close-chore PR (PR Separation Rule).
- [ ] Lifecycle: `ready/` → `review/` → `done/`.
- [ ] BE-303 3-dim verify ALL GREEN per stage. impl PR 의 CI 19/20 GREEN authoritative.
- [ ] (분석=Opus 4.7 / 구현 권장=Opus 4.7)
