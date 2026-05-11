# Task ID

TASK-BE-141

# Title

master-service `requireVersionMatch` + `saveWithOptimisticLock` 5-service 복붙 → `AggregateVersionGuard` 정적 utility 추출

# Status

done

# Owner

backend

# Task Tags

- code
- test

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Goal

`master-service` 의 5 application service (`WarehouseService`, `ZoneService`, `LocationService`, `SkuService`, `LotService`) 가 각각 보유한 두 private helper 메서드 — `requireVersionMatch(UUID, long, long)` 와 `saveWithOptimisticLock(T)` — 가 거의 동일한 boilerplate 로 5번 복사되어 있다.

5-way 복붙을 `AggregateVersionGuard` 정적 utility 1개로 통합하여 ~40 LOC 감축 + 향후 신규 aggregate 추가 시 boilerplate 재발생 방지.

본 task 는 wms/master-service sweep dry-run (2026-05-11) 의 **Finding B1 cherry-pick** 이며, 동일 dry-run 의 B2/B3/B4 는 명시적 DEFER (메모리 `project_refactor_sweep_status.md` master-service 섹션 참조).

---

# Scope

## In Scope

- 새 utility class 추가: `apps/master-service/src/main/java/com/wms/master/application/service/AggregateVersionGuard.java` (final, private constructor, 2 static method).
- `WarehouseService`, `ZoneService`, `LocationService`, `SkuService`, `LotService` 의 private `requireVersionMatch(...)` + `saveWithOptimisticLock(...)` 메서드 5쌍을 모두 제거.
- 5 service 의 기존 호출부를 `AggregateVersionGuard.requireMatch(...)` / `AggregateVersionGuard.saveWithOptimisticLock(...)` 호출로 교체.
- 기존 unit/slice 테스트 PASS 유지. utility 자체에 대한 단위 테스트 2개 추가 (version mismatch → `ConcurrencyConflictException` + save 시 `ObjectOptimisticLockingFailureException` wrap).

## Out of Scope

- 다른 finding (sweep dry-run 의 B2 `WarehouseStatus` rename, B3 `List*Query` wrapper 제거, B4 `LotExpirationBatchProcessor` 패키지 이동) — DEFER.
- C cleanup (Partner aggregate 미구현은 별도 추적 — 메모리 기록만).
- 다른 wms service (inventory/inbound/outbound/admin) 의 동일 패턴 검사 — 별도 평가.
- HTTP / event contract 변경 0 (utility 추출 only, 도메인 동작 보존).

---

# Acceptance Criteria

- [ ] `apps/master-service/src/main/java/com/wms/master/application/service/AggregateVersionGuard.java` 가 존재. final class, private constructor, 두 static method 만 노출.
- [ ] `requireMatch(String aggregateType, UUID id, long expected, long actual)` — expected != actual 시 `ConcurrencyConflictException(aggregateType, id.toString(), expected, actual)` 던짐.
- [ ] `saveWithOptimisticLock(String aggregateType, UUID id, Supplier<T> saveOperation)` — `saveOperation.get()` 반환, `ObjectOptimisticLockingFailureException` catch 후 `ConcurrencyConflictException(aggregateType, id.toString())` 던짐.
- [ ] `WarehouseService`, `ZoneService`, `LocationService`, `SkuService`, `LotService` 의 private `requireVersionMatch` + `saveWithOptimisticLock` 메서드 5쌍 모두 제거 (grep 0).
- [ ] 5 service 의 호출부 모두 utility 호출로 교체. 기존 caller signature/동작 보존.
- [ ] 신규 `AggregateVersionGuardTest` 단위 테스트 2 method (version mismatch + save wrap) — JUnit5, Mockito 없음 (정적 utility).
- [ ] `./gradlew :master-service:test` PASS, 회귀 없음.
- [ ] LOC 순감 ≥ 30 (5 service × ~8 LOC 제거 - utility 신규 ~15 LOC + test ~20 LOC).

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read `PROJECT.md`, then load `rules/common.md` plus any `rules/domains/<domain>.md` and `rules/traits/<trait>.md` matching the declared classification. Unknown tags are a Hard Stop per `CLAUDE.md`.

- [`specs/services/master-service/architecture.md`](../../specs/services/master-service/architecture.md) — Hexagonal layer rules, optimistic locking (T5)
- [`platform/service-types/rest-api.md`](../../../../platform/service-types/rest-api.md)
- `rules/common.md`
- `rules/domains/wms.md` (이 task 의 invariant 와 무관, 참고만)
- `rules/traits/transactional.md` — T5 optimistic locking

# Related Skills

- `.claude/skills/backend/implement-task` (구현)
- `.claude/skills/backend/refactoring` (참고 — 본 task 는 sweep 가 아닌 targeted cherry-pick)

---

# Related Contracts

- HTTP / event contract 변경 0. `specs/contracts/` 무 영향.

---

# Target Service

- `master-service`

---

# Architecture

Follow:

- [`specs/services/master-service/architecture.md`](../../specs/services/master-service/architecture.md) — Hexagonal (Ports & Adapters), § Concurrency Control (`@Version` optimistic locking T5)

---

# Implementation Notes

- Utility class 위치: `application/service/AggregateVersionGuard.java` — 같은 패키지에 두면 5 service 가 import 없이 호출 가능 (방안 1) 또는 별도 위치 + import (방안 2). 방안 1 권장 (단순성).
- 메서드 signature 확정 (`WarehouseService.java:143-155` 의 기존 동작과 1:1 매칭):
  ```java
  public static void requireMatch(String aggregateType, UUID id, long expected, long actual) {
      if (expected != actual) {
          throw new ConcurrencyConflictException(aggregateType, id.toString(), expected, actual);
      }
  }

  public static <T> T saveWithOptimisticLock(String aggregateType, UUID id, Supplier<T> saveOperation) {
      try {
          return saveOperation.get();
      } catch (ObjectOptimisticLockingFailureException e) {
          throw new ConcurrencyConflictException(aggregateType, id.toString());
      }
  }
  ```
- Caller migration 예시 (Warehouse):
  ```java
  // before
  private Warehouse saveWithOptimisticLock(Warehouse warehouse) { ... }
  Warehouse saved = saveWithOptimisticLock(warehouse);

  // after
  Warehouse saved = AggregateVersionGuard.saveWithOptimisticLock(
      AGGREGATE_TYPE, warehouse.getId(), () -> persistencePort.update(warehouse));
  ```
- `ConcurrencyConflictException` 의 두 생성자 signature (4-arg + 2-arg) 가 기존에 존재하는지 확인. 없으면 utility 가 2-arg 호출만 하도록 fallback 가능 — 단 기존 5 service 가 4-arg 호출도 하므로 양쪽 생성자 보존 확인.
- Domain / adapter / port 코드 변경 0. application/service/ 안에서만 작업.

---

# Edge Cases

- `expected == actual` (정상 case) — utility 가 정상 통과, 예외 없음.
- `saveOperation` 이 정상 반환 — utility 가 그대로 반환.
- `ObjectOptimisticLockingFailureException` 외의 RuntimeException (e.g. `DataIntegrityViolationException`) — utility 가 catch 안 하고 그대로 propagate.
- null `aggregateType` 또는 null `id` — 기존 동작 그대로 (utility 가 null check 추가 안 함, 호출자 책임).
- Generic type `T` 가 도메인 모델 (Warehouse / Zone / Location / Sku / Lot) — utility 는 type-agnostic, 어떤 T 도 허용.

---

# Failure Scenarios

- Optimistic lock 충돌 → `ConcurrencyConflictException` (기존 HTTP 409 매핑 유지).
- Version mismatch → `ConcurrencyConflictException` (기존 HTTP 409 매핑 유지).
- 신규 failure mode 도입 0.

---

# Test Requirements

- unit: `AggregateVersionGuardTest` 2 method 신규 (version mismatch + save wrap).
- 기존 unit: `WarehouseServiceTest`, `ZoneServiceTest`, `LocationServiceTest`, `SkuServiceTest`, `LotServiceTest` 가 utility 사용 후에도 PASS (동작 보존).
- IT: 신규 추가 없음. 기존 persistence/IT 테스트는 utility 사용 전후 동일하게 PASS.

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added (`AggregateVersionGuardTest` 2 method)
- [ ] Tests passing (`./gradlew :master-service:test`)
- [ ] 5 service 의 `requireVersionMatch` + `saveWithOptimisticLock` private 메서드 grep 0
- [ ] LOC 순감 ≥ 30 (impl 검증)
- [ ] Contract 변경 없음 (HTTP/event)
- [ ] Specs 변경 없음 (architecture.md drift 0)
- [ ] Ready for review
