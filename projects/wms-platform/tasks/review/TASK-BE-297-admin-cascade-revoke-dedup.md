# Task ID

TASK-BE-297

# Title

admin-service cascade-revoke dedup — `UserService.deactivate` + `RoleService.deactivate` 의 byte-identical 7-line loop 을 `AssignmentEventHelper.cascadeRevoke` 로 extract

# Status

review

# Owner

backend

# Task Tags

- code

---

# Goal

`/refactor-code wms admin-service` (2026-05-26 dry-run) 의 **Cohort C1 = L5+L6 deactivate-cascade dedup** finding closure. behavior-neutral, low risk.

dry-run 결과 핵심 finding:

- **F-L5-1**: `UserService.deactivate` ~29 LOC, 3-phase (active 검증 + cascade revoke + deactivate+emit).
- **F-L5-2**: `RoleService.deactivate` ~30 LOC, F-L5-1 과 structurally identical.
- **F-L6-1**: 두 service 의 cascade-revoke 7-line 루프 (`UserService:131-138` + `RoleService:137-144`) byte-identical 패턴 — `clock.instant()` → `findActiveBy{User|Role}Id` → for each: `a.revoke(now, actorId)` → `save` → `revokedIds.add(saved.id())` → `assignmentEventHelper.appendAssignmentRevokedEvent(saved, "{USER|ROLE}_DEACTIVATED", actorId, now)`. 차이점은 (a) repository finder method 명 + (b) cascadeReason 문자열 2개뿐.

본 task 는 cascade-revoke 7-line 루프를 `AssignmentEventHelper.cascadeRevoke` 새 메서드로 extract. `AssignmentEventHelper` 는 이미 `appendAssignmentRevokedEvent` 의 owning helper 라 cascade-revoke 도 자연스러운 boundary. `AssignmentRepository` 가 helper 의 새 field 로 추가됨.

SCM-BE-017 의 L6 dedup 패턴 재사용 (4 unit L0 cache port + L6 layer hygiene, behavior-neutral).

---

# Scope

## In Scope

| 대상 | 변경 |
|---|---|
| `apps/admin-service/src/main/java/com/wms/admin/application/assignment/AssignmentEventHelper.java` | 신규 메서드 `cascadeRevoke(List<UserRoleAssignment> active, String cascadeReason, String actorId, Instant now): List<UUID>` 추가. for-each 루프 안에 (a) `a.revoke(now, actorId)` (b) `assignmentRepository.save(revoked)` (c) `revokedIds.add(saved.id())` (d) `appendAssignmentRevokedEvent(saved, cascadeReason, actorId, now)`. `AssignmentRepository` field + constructor parameter 추가 (Spring DI). 메서드 javadoc 에 caller 의 `clock.instant()` instant 와 동일 instant 사용 의무 명시. |
| `apps/admin-service/src/main/java/com/wms/admin/application/user/UserService.java` | `deactivate` 의 cascade-revoke loop (L131-138, 7 line) → `assignmentEventHelper.cascadeRevoke(active, "USER_DEACTIVATED", cmd.actorId(), now)` 단일 호출. `revokedIds` 받기. `Instant now = clock.instant()` 의 이동 (또는 호출 전 1회 선언). |
| `apps/admin-service/src/main/java/com/wms/admin/application/role/RoleService.java` | `deactivate` 의 cascade-revoke loop (L137-144, 7 line) → `assignmentEventHelper.cascadeRevoke(active, "ROLE_DEACTIVATED", cmd.actorId(), now)` 단일 호출. 동일 패턴. |

## Out of Scope

- **F-L5-3** (`InventoryProjectionService.onAdjusted` 50 LOC split into 2 sub-methods) — Cohort C3 분리 후보, 별 task (BE-298 또는 LATER).
- **F-L6-2** (4 `*ProjectionConsumer` onMessage byte-identical body dedup) — Cohort C2 분리, medium risk + Kafka IT 의존, 별 task.
- **F-L1-1** (`OperationsController` direct repository import borderline) — Cohort C3 분리.
- **F-L2-1** (`RoleService.deserialisePermissions` silent catch) — Cohort C3 분리.
- **F-L3-1** (`InventoryProjectionService` `lowStock = qty <= 10` magic constant) — Cohort C3 분리.
- `clock.instant()` 의 이동 외 다른 시간 처리 변경 (예: now 를 두 번 재계산해서 다른 instant 사용하는 변경은 behavior change).
- `UserHasActiveAssignmentsException` / `RoleInUseException` 의 cascade-reason 분기 (force=false vs force=true) 의 의미적 변경.
- 새 unit test 작성 (기존 test 만 GREEN 유지). cascade-revoke 동작은 이미 `UserServiceTest.deactivate_*` + `RoleServiceTest.deactivate_*` 가 cover.

---

# Acceptance Criteria

- [ ] (A1) `AssignmentEventHelper.cascadeRevoke(List<UserRoleAssignment>, String, String, Instant): List<UUID>` 메서드 신규. `AssignmentRepository` field + constructor parameter 추가. javadoc 에 instant 보존 의무 명시.
- [ ] (A2) `UserService.deactivate` 의 cascade-revoke loop (L131-138) 가 `assignmentEventHelper.cascadeRevoke(...)` 단일 호출로 대체. for-each 루프 + `a.revoke` / `save` / `revokedIds.add` / `appendAssignmentRevokedEvent` 직접 호출 = 0 in `deactivate` body.
- [ ] (A3) `RoleService.deactivate` 의 cascade-revoke loop (L137-144) 도 동일하게 대체.
- [ ] (A4) cascadeReason 두 값 (`"USER_DEACTIVATED"` + `"ROLE_DEACTIVATED"`) 보존, 인자로 전달.
- [ ] (A5) Instant 보존 — caller 에서 `clock.instant()` 한 번 호출한 동일 instant 가 cascade-revoke + deactivate event 양쪽에 사용 (cascade-revoke loop 내 `revoke(now, actorId)` + `appendAssignmentRevokedEvent(...occurredAt=now)` + 후속 `existing.deactivate(now, actorId)` 의 same `now`).
- [ ] (A6) `./gradlew :projects:wms-platform:apps:admin-service:check --rerun-tasks` BUILD SUCCESSFUL — 단위 + slice + IT 모두 GREEN. baseline = main `54d048bc`.
- [ ] (A7) BE-017 회귀 0 (cross-project sweep 의 4 SCM AC 와 무관 — wms admin-service scope).
- [ ] (A8) contract / event schema 변경 0, `admin.assignment.revoked` 외부 event payload byte-identical (`assignmentId`/`userId`/`roleId`/`warehouseId`/`revokedAt`/`revokedBy`/`cascadeReason`).
- [ ] (A9) zero-retrofit invariant — `git diff --stat origin/main -- 'projects/{scm,global-account,erp,fan,ecommerce-microservices,finance,platform-console}-platform/' 'libs/'` = empty.

---

# Related Specs

> **Before reading Related Specs**: `platform/entrypoint.md` Step 0 (PROJECT.md = wms, domain=wms, traits=[transactional, integration-heavy]).

- `platform/refactoring-policy.md` — refactor 정의 (no behavior change).
- `platform/coding-rules.md` — duplication 제거 정책.
- `projects/wms-platform/specs/services/admin-service/architecture.md` § Architecture Style L72-104 (Layered override) + § Package Structure L115-... (api/application/domain/infra 4 layer). `AssignmentEventHelper` 는 `application/assignment/` 위치, helper extract 의 자연스러운 boundary.
- `rules/domains/wms.md` — Admin / Operations bounded context (UserRoleAssignment 가 admin-service 자체 aggregate).
- `rules/traits/transactional.md` — `@Transactional` 보존 의무 (cascade-revoke 호출이 caller 의 active transaction 안에서 실행).

# Related Skills

- `.claude/skills/backend/refactoring/SKILL.md` — Extract Method + Reduce Duplication patterns. § Baseline Check (compile + test GREEN before/after).

---

# Related Contracts

- `projects/wms-platform/specs/contracts/events/admin-events.md` (or sibling) — `admin.assignment.revoked` event envelope (변경 0건).

---

# Target Service

- `admin-service` (단일 service scope)

---

# Architecture

admin-service = Layered (architecture.md override). 본 task 는 **application layer 내부의 dedup** — `AssignmentEventHelper` 가 이미 application/assignment/ 위치에 존재, cascade-revoke 책임 추가가 자연스러운 boundary. inter-layer 변경 0.

helper 의 `AssignmentRepository` inject 추가 = repository interface 가 application/repository/ 에 위치 (Layered 의 표준 abstraction). `OutboxRepository` + `AdminEventEnvelopeBuilder` 와 동일 위치 → 동일 형식 inject. Spring DI 으로 자동 wiring.

---

# Implementation Notes

1. **Pre-verify (BE-301 패턴 6회째)**: impl 단계에서 dispatcher main session 이 직접 (a) `UserService.deactivate` / `RoleService.deactivate` 의 cascade loop 위치 grep / (b) `AssignmentEventHelper` field 구성 grep / (c) cascadeReason 두 문자열 정확성 grep 재실행. agent report 의 숫자/주장 불신, 직접 재검증.
2. **Method signature 권장**: `public List<UUID> cascadeRevoke(List<UserRoleAssignment> active, String cascadeReason, String actorId, Instant occurredAt)` — Instant 가 caller 의 `clock.instant()` 결과를 전달받는 형식. helper 내부에서 `clock` 을 다시 호출하지 말 것 (instant 일치 invariant).
3. **AssignmentRepository inject**: constructor 에 추가, field 로 보존. `OutboxRepository` 직후. Spring DI 가 자동 wiring.
4. **revokedIds 반환**: caller 의 `revokedIds` 변수가 List<UUID> 이므로 helper 반환값을 그대로 assign. 빈 list 처리: `active.isEmpty()` 면 빈 list 반환 (자연스러움).
5. **Test 보존**: 기존 `UserServiceTest.deactivate_force_revokesActiveAssignments` 같은 unit test 가 cascade 동작 cover. test 변경 불필요 (signature 가 caller 영향 없음).
6. **Branch**: `task/be-297-admin-cascade-revoke-dedup` (substring `master` 없음 확인 ✓).
7. **Spec PR + impl PR + close-chore PR** 3 분리 (PR Separation Rule).
8. (분석=Opus 4.7 / 구현 권장=Sonnet 4.6 — mechanical extract method + 2 caller site, low risk)

---

# Edge Cases

- `active.isEmpty()` (active assignments 없음) — caller 에서 `activeAssignments > 0` 가드 안에서만 helper 호출 → helper 의 빈 list 처리 케이스 미발생. 단 helper 자체는 빈 list 도 안전 처리해야 (테스트 단순화 위해).
- `force=true` + caller-not-superadmin 분기 — `AccessDeniedException` throw 가 helper 호출 전에 발생. helper 의 cascade 호출 시점은 권한 check 이후. 변경 무관.
- `assignment.warehouseId() == null` — payload 에서 null 변환은 helper 의 `appendAssignmentRevokedEvent` 가 이미 처리 (cascade-revoke 자체 변경 무관).

---

# Failure Scenarios

- `AssignmentRepository` inject 실패 (typo / Spring DI 누락) → ApplicationContext 시작 실패. CI `:check` 가 즉시 detection.
- cascadeReason 문자열 인자 누락 / 잘못 전달 → `admin.assignment.revoked` event payload 의 `cascadeReason` field 가 잘못된 값 → AC-8 (envelope byte-identical) 위반. helper signature 의 String 인자 강제로 mitigate.
- Instant 보존 위반 (helper 내부에서 `clock` 호출) → AC-5 invariant 위반 (revoke instant ≠ deactivate event instant). javadoc 명시 + AC-5 강제.
- 회귀 — `:check` GREEN 전체로 cover.

---

# Test Requirements

- baseline: main `54d048bc` (post-SCM-BE-018 close-chore) `./gradlew :projects:wms-platform:apps:admin-service:check --rerun-tasks` GREEN.
- post-impl: 동일 명령어 GREEN. 추가 test 작성 불요 (extract method = caller signature 무관, 기존 test 가 cover).
- CI Linux runner 가 Testcontainers IT 의 권위적 verify (per `project_testcontainers_docker_desktop_blocker.md`).

---

# Definition of Done

- [ ] (A1-A9) 모두 PASS.
- [ ] Branch: `task/be-297-admin-cascade-revoke-dedup` (substring `master` 검증).
- [ ] PR: `refactor(wms-admin):` impl PR + close-chore PR (PR Separation Rule).
- [ ] Lifecycle: `ready/` → `review/` → `done/`.
- [ ] BE-303 3-dim verify ALL GREEN per stage.
- [ ] (분석=Opus 4.7 / 구현 권장=Sonnet 4.6)
