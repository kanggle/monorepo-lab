# Task ID

TASK-BE-288

# Title

admin-service operator/totp port 도입 — 9 application file infra import 정리 (behavior 0)

# Status

review

# Owner

backend

# Task Tags

- code

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

admin-service 의 retrofit-era artifact 인 application→`infrastructure.persistence.rbac.*` / `infrastructure.persistence.AdminOperatorTotp*` 직접 의존을 port 레이어 뒤로 격리한다. 기존 5 port (AdminRefreshTokenPort / OperatorLookupPort / BulkLockIdempotencyPort / TokenBlacklistPort / TenantProvisioningPort) 의 패턴(application/port + inner Record projection + narrow surface)을 답습하여 두 신규 port 를 추가:

1. **`AdminOperatorPort`** — `admin_operators` + `admin_roles` + `admin_operator_roles` 의 read/write 통합 port. operator entity 와 role binding 의 mutating 호출을 모두 캡슐화.
2. **`AdminOperatorTotpPort`** — `admin_operator_totp` row 의 read/write port. optimistic-lock 기반 recovery-code 소비 패스 포함.

목적: production behavior 0 변경, JPA entity 가 application 레이어를 통과하지 않음, 9 application file 의 `infrastructure.*` import 가 port 패키지로 정리, retrofit-era sweep status 메모리 (`project_refactor_sweep_status.md` § "GAP services sweep dry-run") 의 admin-service MID GO 신호 해소.

---

# Scope

## In Scope

- `application/port/AdminOperatorPort.java` 신설 (interface + `OperatorView` / `NewOperator` / `RoleView` / `NewRoleBinding` / `OperatorPage` Record projections).
- `application/port/AdminOperatorTotpPort.java` 신설 (interface + `TotpRow` / `NewTotpRow` Record projections).
- `infrastructure/persistence/rbac/JpaAdminOperatorAdapter.java` (또는 동치) `@Component` adapter — 기존 `AdminOperatorJpaRepository` / `AdminRoleJpaRepository` / `AdminOperatorRoleJpaRepository` 위임.
- `infrastructure/persistence/JpaAdminOperatorTotpAdapter.java` `@Component` adapter — 기존 `AdminOperatorTotpJpaRepository` 위임 + optimistic lock 캡슐화.
- 9 application file 의 호출부 port 경유 전환:
  - `AdminLoginService`
  - `CreateOperatorUseCase`
  - `ChangeMyPasswordUseCase`
  - `PatchOperatorStatusUseCase`
  - `PatchOperatorRoleUseCase`
  - `OperatorQueryService`
  - `OperatorRoleResolver` (helper — port adapter 안으로 흡수 또는 port 사용으로 전환)
  - `TotpEnrollmentService`
  - `AdminActionAuditor` (operator 해석만 — 기존 `OperatorLookupPort` 로 전환, `AdminOperator*JpaEntity/Repo` import 제거)
- 영향받는 unit test 의 `@Mock`/`@MockBean` 대상 교체 (`*JpaRepository` → port). Integration test 는 실제 JPA 경로 그대로 유지.
- 기존 5 port 의 javadoc + 명명 규약 답습.

## Out of Scope

- HTTP contract / Kafka event envelope 변경. `admin-api.md`, `admin-events.md` 본 task 미 touch.
- DB schema / Flyway migration / 인덱스 변경.
- `AdminActionJpaEntity` / `AdminActionJpaRepository` (audit ledger) port 화 — architecture.md L80-91 가 audit ledger 를 infrastructure/persistence 의 핵심 책임으로 선언, 본 task scope 외. `AuditQueryUseCase` 의 admin_actions 접근은 그대로 유지.
- `TotpGenerator` / `TotpSecretCipher` stateless crypto utility 의 port 화 — infrastructure.security 의 stateless 빈은 application 의 직접 의존 허용 (architecture.md Allowed Dependencies 충족).
- `JwtSigner` / `BootstrapTokenService` / `AdminJwtKeyStore` 등 JWT/bootstrap infrastructure — operator/totp scope 외.
- `AccountServiceClient` / `AuthServiceClient` / `SecurityServiceClient` (HTTP client) — architecture.md `presentation → application → infrastructure/client` 가 명시적으로 허용.
- `CachingPermissionEvaluator` — 본 task 는 invalidate 호출만 유지하고 별 port 화하지 않음 (cache 는 infrastructure-only concern).
- 신규 production behavior 추가, validation rule 변경, audit row shape 변경, JWT claim 변경.

---

# Acceptance Criteria

- [ ] `application/port/AdminOperatorPort.java` 가 존재하고 인터페이스 + Record projection 으로만 구성됨 (JPA 의존 0).
- [ ] `application/port/AdminOperatorTotpPort.java` 가 존재하고 인터페이스 + Record projection 으로만 구성됨 (JPA 의존 0).
- [ ] 2 신규 port 의 adapter 가 `infrastructure/persistence/` (또는 `rbac/`) 하위에 `@Component` 로 등록됨.
- [ ] 9 application file 모두 `import com.example.admin.infrastructure.persistence.rbac.AdminOperatorJpaEntity` / `*JpaRepository` 등 RBAC JPA 직접 import 제거 (`AdminActionAuditor` 는 `OperatorLookupPort` 사용으로 전환).
- [ ] 9 application file 모두 `import com.example.admin.infrastructure.persistence.AdminOperatorTotpJpaEntity` / `AdminOperatorTotpJpaRepository` 제거.
- [ ] `application/` 디렉토리 전체에서 `import com.example.admin.infrastructure.persistence.rbac.` grep 결과 — adapter 위치 변경 가능성 제외하고 `AdminOperator*`/`AdminRole*` 항목 0 회.
- [ ] `application/` 디렉토리 전체에서 `import com.example.admin.infrastructure.persistence.AdminOperatorTotp` grep 결과 0 회.
- [ ] `./gradlew :projects:global-account-platform:apps:admin-service:compileJava` 성공.
- [ ] `./gradlew :projects:global-account-platform:apps:admin-service:test` 성공 (unit + light slice). Testcontainers 가 요구되는 integration test 는 별도 — local 환경 한계 시 명시.
- [ ] HTTP API 응답 / Kafka outbox envelope / audit row 컬럼 값 byte-identical (mutation 경로 timing/optimistic-lock 동작 보존).
- [ ] LOC delta: net 감소 (port 추가 LOC ≤ import 정리 + entity 인라인 mutation 제거 LOC).

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — `PROJECT.md` (domain=saas, traits=[transactional, regulated, audit-heavy, integration-heavy, multi-tenant]), `rules/common.md`, `rules/domains/saas.md`, 5 trait files.

- `specs/services/admin-service/architecture.md` — Internal Structure Rule (L52-92), Allowed Dependencies (L96-104). Thin Layered (Command Gateway) 스타일 — application→infrastructure 직접 import 가 spec 상 허용되지만, 기존 5 port 와 동일한 precedent 로 port 도 허용. **본 task 는 spec 변경 없음** (additive — Thin Layered 가 ports 를 ban 하지 않음).
- `specs/services/admin-service/data-model.md` — `admin_operators` / `admin_roles` / `admin_role_permissions` / `admin_operator_roles` / `admin_operator_totp` 가 canonical local state (L7-9, L19-85).
- `specs/services/admin-service/rbac.md` — role-driven 2FA 강제, permission evaluator, JWT claim 의 invariant 보존.
- `specs/services/admin-service/security.md` — operator session lifecycle, refresh-token rotation, blacklist. **본 task 영향 없음** (port 추가만, 동작 유지).
- `rules/traits/audit-heavy.md` A2/A3/A10 — audit row 의 actor/permission/outcome 필드, append-only, fail-closed.
- `rules/traits/transactional.md` T5 — `admin_operators.version` 낙관적 락 보존.

# Related Skills

- `.claude/skills/backend/refactoring/SKILL.md` — sweep operational rules (Baseline Check, Worktree Dispatch Verification).
- `.claude/skills/implement-task` — 표준 task 진행 워크플로우.

---

# Related Contracts

- `specs/contracts/http/admin-api.md` — operator login / logout / refresh / 2FA / password change / status patch / role patch / create operator 엔드포인트의 응답 shape 보존.
- `specs/contracts/events/admin-events.md` (없으면 data-model.md §`admin.action.performed` Event Envelope) — outbox envelope byte-identical.

---

# Target Service

- `admin-service`

---

# Architecture

Follow:

- `specs/services/admin-service/architecture.md` — Thin Layered (Command Gateway). 본 task 는 architecture style 변경 아님. 기존 5 port (TASK-BE-030-fix / TASK-BE-040-fix / TASK-BE-249) 의 precedent 답습.

---

# Implementation Notes

1. **Port 명명**: 기존 5 port 의 명명 답습 — `XxxPort` 접미사. inner Record projection 은 `View` / `New*` / `Page` 접미사 (`AdminRefreshTokenPort.TokenRecord` / `OperatorLookupPort.OperatorSummary` 참조).
2. **Mutating 경로**: application 이 entity 의 mutator (`changeStatus` / `changePasswordHash` / `markUsed` / `replaceRecoveryHashes` / `replaceSecret`) 를 호출하던 경로는 port 메서드(`changeStatus(operatorInternalId, newStatus, at)`) 로 변환. adapter 가 `findById → setter → save` 시퀀스 캡슐화.
3. **Optimistic lock**: `AdminLoginService.consumeRecoveryCode` 의 2-attempt 재시도 루프 — port `tryUpdateRecoveryHashes(operatorInternalId, expectedVersion, hashedJson, at)` 반환 boolean (성공/version-conflict). 호출부의 retry 루프는 application 잔존.
4. **OperatorLookupPort 재사용**: `AdminActionAuditor.resolveOperator` 의 `(pk, tenantId)` 조회는 이미 `OperatorLookupPort.OperatorSummary(internalId, operatorId, tenantId)` 와 매핑됨. 신규 port 불필요 — 기존 port 호출로 전환.
5. **OperatorRoleResolver inline**: 본 helper 의 3 메서드(`resolveRoles` / `resolveActorInternalId` / `normalizeReason`) 중 앞 둘은 `AdminOperatorPort` 메서드로 흡수. `normalizeReason` 은 순수 함수이므로 application/`AuditReasons` 등 유틸로 분리 또는 호출부 직접 표현. 이름 충돌 (`OperatorEndpointAccessResolver`) 회피.
6. **Adapter package**: `infrastructure/persistence/rbac/JpaAdminOperatorAdapter` 와 `infrastructure/persistence/JpaAdminOperatorTotpAdapter` (기존 JPA entity 위치 답습). `@Component`, constructor-injected.
7. **Test 영향**: `@MockBean AdminOperatorJpaRepository` 패턴이 ~10 unit test 에 분포. 각 test 의 mock 대상을 port 로 전환하고 stub 의 반환 타입을 `OperatorView` 로 변경. integration test (real JPA) 는 unchanged.
8. **JPA entity 잔존**: 본 task 는 application/ 의 entity import 만 제거. JPA entity 정의(`AdminOperatorJpaEntity` 등)는 infrastructure 잔존 + adapter 가 사용. entity 의 factory/mutator 는 그대로 유지(adapter 안에서 호출).
9. **AdminOperator domain 객체 보존**: `domain/rbac/AdminOperator` (TASK-BE-249 의 PLATFORM_TENANT_ID 상수) 는 그대로 유지.
10. **Behavior preservation 체크리스트**:
    - timing-leveled login dummy verify 보존
    - optimistic lock retry 정확 동일 (단일 retry)
    - recovery code 소비 시 `lastUsedAt` stamp 보존
    - `last_login_at` mutation 보존
    - permission cache invalidate 호출 보존
    - audit row INSERT 순서 보존
    - tenant scope 검증 흐름 보존 (`CreateOperatorUseCase` defense-in-depth)

---

# Edge Cases

- operator 가 존재하지 않을 때 `OperatorNotFoundException` / `OperatorUnauthorizedException` 동일 분기 유지.
- `existsByTenantIdAndEmail` race (concurrent create) 시 `DataIntegrityViolationException` → `OperatorEmailConflictException` 변환 경로 보존.
- recovery code 소비 중 `OptimisticLockException`/`ObjectOptimisticLockingFailureException` 재시도 1회.
- `bulkLoadEnrolledTotpIds` 의 N+1 회피 (`findByOperatorIdIn`) 보존.
- TOTP secret byte[] 의 `Arrays.fill(secret, (byte) 0)` zeroize 패턴 보존 (port 가 byte[] 노출).
- null tenantId fallback ("fan-platform") 보존.
- `OperatorContext` 의 `operatorId == null` 또는 actor null 시 `resolveActorInternalId` 가 null 반환하는 분기 보존.

---

# Failure Scenarios

- adapter 의 `@Component` 이중 빈 등록 (예: 기존 JpaRepository 와 신규 adapter 의 동일 인터페이스 노출) → Spring context fail-fast 로 회피.
- port 메서드의 transactional boundary 변경 risk — adapter 단독 `@Transactional` 추가 금지, application 의 `@Transactional` 가 propagate 되도록 함 (`AdminLoginService` `@Transactional` 경계 보존).
- test 의 `@MockBean AdminOperatorJpaRepository` 잔존 시 unused stub 경고 또는 검증 누락 — 모든 영향 test 의 mock 교체 검증.
- `OperatorRoleResolver` 흡수 시 (`package-private` → public 노출) 의 의도치 않은 API 노출 — port adapter 안 private static helper 로 흡수하거나 `OperatorRoleResolver` 를 thin facade 로 보존하고 내부에서 port 호출.
- TotpRow record 의 `byte[]` 필드는 record 의 equals/hashCode 가 reference equality 이므로 직접 비교 사용 금지 (현재 코드도 비교 안 함, 보존).

---

# Test Requirements

- 영향받는 unit test 의 mock 대상 교체 (port). 기존 단언(assertion) shape 그대로.
- `AdminLoginServiceTest` 또는 동치의 timing-leveled dummy verify 시나리오 보존.
- `RecoveryCodeRegenerateIntegrationTest` 의 real JPA 경로 GREEN (Testcontainers 가용 시).
- 추가 신규 port-only unit test: 새 port 의 adapter 가 실제 JPA repository 와 round-trip 하는지의 slice/IT — 가벼운 `@DataJpaTest` 또는 어쩌면 기존 `AdminOperatorJpaRepositoryTest` 가 underlying JPA 를 이미 검증하므로 생략 가능.
- `./gradlew :projects:global-account-platform:apps:admin-service:test` GREEN.

---

# Definition of Done

- [ ] Port 2개 + adapter 2개 + 9 application file migration 모두 완료
- [ ] Spec 변경 없음 (architecture.md / data-model.md 모두 unchanged)
- [ ] Unit test pass, integration test 영향 분석 + GREEN
- [ ] Contract 변경 없음 (admin-api.md / admin-events.md unchanged)
- [ ] Branch: `task/be-288-admin-operator-totp-port-refactor` (substring `master` 금지)
- [ ] Single PR (squash-merge), commit prefix `refactor(gap-admin):`
- [ ] Ready for review
