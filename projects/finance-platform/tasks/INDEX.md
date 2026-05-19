# Tasks Index — finance-platform

This document defines task lifecycle, naming, and move rules for the **finance-platform** project. Repo-root [tasks/INDEX.md](../../../tasks/INDEX.md) covers monorepo-level (cross-project) tasks; this file covers finance-platform-internal tasks only.

---

# Lifecycle

backlog → ready → in-progress → review → done → archive

Only tasks in `ready/` may be implemented.

---

# Task Types

- `TASK-FIN-BE-XXX`: backend (Spring Boot service implementations)
- `TASK-FIN-INT-XXX`: cross-service integration / E2E (Testcontainers · Docker compose)
- `TASK-FIN-FE-XXX`: frontend — declared for future use, finance v1 is backend-only (platform console renders finance per ADR-MONO-013 §3.3)

---

# Move Rules

## backlog → ready
Allowed only when:
- related specs exist (`specs/services/<service>/architecture.md`, `specs/contracts/...`)
- related contracts are identified
- acceptance criteria are clear
- task template is complete

## ready → in-progress
Allowed only when implementation starts.

## in-progress → review
Allowed only when:
- implementation is complete
- tests are added
- contract / spec updates are completed if required

## review → done
Allowed only after review approval.

### Review Rules
- Tasks in `review/` must not be re-implemented directly.
- If a review reveals a bug or missing requirement, create a new fix task in `ready/` referencing the original task.
- Fix tasks must include the original task ID in their Goal section (e.g. "Fix issue found in TASK-FIN-BE-001").
- Do not modify a task file after it moves to `review/` or `done/`.

### PR Separation Rule (lifecycle ↔ PR boundary)

Each lifecycle transition lands in its own PR. **Never bundle task spec authoring with implementation in the same PR.**

| Stage | Recommended PR shape |
|---|---|
| `(writing) → ready` | **spec PR** — adds the task file to `ready/` + updates this `INDEX.md` ready list. No implementation code. |
| `ready → in-progress → review` | **impl PR** — moves the task file through `in-progress/` to `review/` and lands the implementation. Lifecycle moves and impl commits should be separate commits but live in one PR. |
| `review → done` | **chore PR** — moves merged task file(s) from `review/` to `done/` + updates the `INDEX.md` done list. May batch multiple merged tasks. |

The repo-root [tasks/INDEX.md](../../../tasks/INDEX.md) is the authoritative definition. This summary applies the same rule at the project level.

## done → archive
Allowed when no further active change is expected.

---

# Rule

Tasks must not be implemented from `backlog/`, `in-progress/`, `review/`, `done/`, or `archive/`.

---

# Task List

## backlog

(empty)

## ready

(empty)

## in-progress

(empty)

## review

(empty)

## done

- `TASK-FIN-BE-004-idempotency-concurrency-exactly-once-fix.md` — **Fix issue found in TASK-FIN-BE-001 — concurrent same-Idempotency-Key fund movement exactly-once** (spec PR #609 `e26f1d9b` + impl PR #610 `18892d4e`; surfaced by MONO-115 run `26071043853`, finance IT 11/12 residual after FIN-BE-003). **Root cause (BE-301, neither clean H1 nor H2 = production design gap)**: `IdempotentExecution.run` was non-atomic check-then-act → an 8-thread same-key burst let N threads MISS then each move funds; `architecture.md` already specified "Redis primary (SET NX-EX)" atomic claim the original impl never realised. **Fix = claim-before-execute, DB-authoritative**: port `claim()→{EXECUTE|REPLAY|CONFLICT|IN_PROGRESS}`+`complete()`+`release()`; `IdempotencyKeyJpaRepository` native atomic `insertClaim` (composite PK `(idempotency_key,endpoint,tenant_id)` = the concurrency gate; `save` merges so can't fail-on-dup) + `markCompleted` + `delete`; `RedisOrDbIdempotencyStore` DB-PK-authoritative atomic gate (sentinel `response_status=0`=IN_PROGRESS, ≥200=REPLAY, dup→CONFLICT/REPLAY/IN_PROGRESS, expired sentinel reclaimable, Redis=best-effort REPLAY accelerator never the authority, fail-CLOSED on DB down → 503); `IdempotentExecution.run` (signature UNCHANGED → 4 controller call sites untouched) claim-before-execute loop (EXECUTE→action→complete(2xx)/release(fail|non-2xx); REPLAY→stored; CONFLICT→409; IN_PROGRESS→bounded poll ≤5s≪req timeout then re-claim, stall→409 deterministic no-re-movement, never faked success); IT rewritten to drive the **real** wrapper ×8 threads, assertion **STRENGTHENED** to `actionExecuted==1` exact (green only via the genuine prod guarantee — never weakened; green-wash prohibited). **2-cycle CI history (cycle-burn discipline, BE-301 root-caused not guessed)**: cycle-1 (#610 run `26073035851`) — atomic gate worked (MySQL 1062 Duplicate entry) but `@Transactional(REQUIRES_NEW)` on `claim()` + catching `DataIntegrityViolationException` *inside* that tx and continuing JPA work poisoned the rollback-only tx → `UnexpectedRollbackException`; cycle-2 fix = new `IdempotencyKeyTx` separate `@Component` with single-statement `REQUIRES_NEW` methods (dup propagates OUT), non-`@Transactional` `RedisOrDbIdempotencyStore` orchestrator catches the dup *outside* any tx then reads in a fresh tx (ComplianceFailureRecorder REQUIRES_NEW precedent). impl=Opus 직접(correctness-critical, agent 미사용) / **dispatcher BE-301**: scope=port+repo+IdempotencyKeyTx+store+IdempotentExecution+IT only (no V1/migration/contract/`architecture.md`[impl now COMPLIES with existing SET-NX-EX spec]/ADR/ci.yml/controller/FIN-BE-002·003); cycle-2 NET diff = 2 files `infrastructure/idempotency/**`; `:check --rerun-tasks` 117/0/0/0 (11-XML). **CI-PROVEN** via MONO-115 `finance-integration-tests` run `26073397866` = conclusion **success, 12/12 ALL PASSED** (AccountLifecycle ×5, AuditAndImmutability F3/F6, CrossTenant 401+403, IdempotencyConcurrency exactly-once + CONFLICT/REPLAY, OwnerRefEncryptionAtRest F7) — FIN-BE-002/003 not regressed. Merge ⇒ main `finance-integration-tests` RED→GREEN = the honest green-wash chain **TRUE TERMINAL**: FIN-BE-001 honest gap → MONO-115 CI built → FIN-BE-002 schema → FIN-BE-003 behavioural → **FIN-BE-004 concurrency** — each step surfaced, tracked, user-approved (strategy A), never silently dropped, never green-washed; finance v1 behavioural-proof gap fully closed. closed via this close chore PR.
- `TASK-FIN-BE-003-pii-roundtrip-and-it-jwks-fix.md` — **Fix issues found in TASK-FIN-BE-001 — owner_ref encrypt-at-rest round-trip + cross-tenant IT JWKS** (spec PR #606 `f24e4fa3` + impl PR #607 `83c8b398`; surfaced by MONO-115 run `26036483067` post-FIN-BE-002). **D1** (×8, prod): `AccountRepositoryAdapter` set plaintext back onto the JPA-managed `saved` entity post-`save()` → tx-commit dirty-flush re-persisted plaintext over the ciphertext envelope (F7 violation) → later `decryptFromString(plaintext-with-hyphen)` threw `Illegal base64 character 2d`. Fix = `jpa.saveAndFlush` then `EntityManager.detach` BEFORE restoring plaintext for the caller; `decrypt()` detaches BEFORE writing the decrypted value → column always the `v1:` envelope, caller gets a detached plaintext domain object. **D2** (×1, test): base `AbstractAccountIntegrationTest` registered a dead `localhost:9` `jwk-set-uri` that non-deterministically shadowed `CrossTenantHttpIntegrationTest`'s duplicate registration. Fix = base owns the ONLY `jwk-set-uri` registration → reachable MockWebServer serving a mutable JWK set; CrossTenant publishes its public JWK via new `publishJwks(...)` in `@BeforeAll`. **AC-3**: new `OwnerRefEncryptionAtRestIntegrationTest` (raw `owner_ref` = `v1:` envelope; hyphen-bearing ref exercises the exact `2d` regression). impl=Opus 직접(correctness-critical, agent 미사용) / **dispatcher BE-301**: scope=adapter(prod)+2 IT+1 new IT(test) only, no V1/migration/contract/architecture.md/ADR/enum-mapping/ci.yml; `:check --rerun-tasks` 117/0/0/0. **CI-PROVEN** via MONO-115 run `26071043853`: **9 fail → 1 fail (11/12 GREEN)** — D1/D2/AC-3 all green (AccountLifecycle 5/5, AuditImmutability F3/F6, CrossTenant 403+401, F7 raw-column, Idempotency CONFLICT). **⚠️ 정직 (green-wash 금지)**: AC-1 ("4 IT/12 test green") **NOT met** — 1 remaining: `IdempotencyConcurrencyIntegrationTest.concurrentSameKeyMovesFundsOnce` L92 `performed>1` (concurrent same-key `placeHold` not exactly-once) = **distinct concurrency defect class**, NOT a regression (the test had never run to completion before — masked by the prior context-load/decrypt cascade), OUT of FIN-BE-003 scope per its own **Failure Scenario A** → tracked as **TASK-FIN-BE-004** (user-approved strategy A: merge #607 + separate fix-task, no scope-expand). main `finance-integration-tests` remains **honest+isolated RED** (1/12; other 17 jobs + finance `:check` green) until FIN-BE-004 — NOT silently dropped, NOT green-washed. closed via close chore PR (this).
- `TASK-FIN-BE-002-enum-schema-validation-fix.md` — **Fix issue found in TASK-FIN-BE-001 — enum schema-validation** (spec PR #602 `cc318aca` + impl PR #604 `c4d33fd5`). Hibernate 6.6.4 + `MySQLDialect` mapped `@Enumerated(EnumType.STRING)` → native MySQL `ENUM(...)` for schema-validation vs Flyway `CHAR(3)`/`VARCHAR` → `SchemaManagementException` on `accounts.currency` → 4 IT/11 test context-load cascade. **cycle 1** (global `hibernate.type.preferred_enum_jdbc_type=VARCHAR`) **insufficient** (governs only un-annotated default enums, not explicit `@Enumerated(EnumType.STRING)`; CI-falsified, same error) → **cycle 2 definitive**: per-field `@JdbcTypeCode` exact-match DDL on all 13 enum fields (4 `SqlTypes.CHAR` for `currency` CHAR(3) + 9 `SqlTypes.VARCHAR`), application.yml net-reverted (dead config removed). evidence: GAP (same Hibernate 6.6.4/MySQL/ddl-auto:validate, IT green) proved VARCHAR-enum tolerant but CHAR(3) `currency` is the only mismatch; existing `@JdbcTypeCode(SqlTypes.JSON)` in AuditLog = working precedent. impl=backend-engineer(Sonnet dispatch cycle1) + **dispatcher BE-301 직접 cycle2·독립 재검증**: 13 `@Enumerated`↔`@JdbcTypeCode` pairing (4 CHAR/9 VARCHAR grep-confirmed), imports 6/6, NET diff=6 entity .java +23 (application.yml net-identical to main), `:check --rerun-tasks` 117/0/0/0 (11-XML re-parsed). **CI-PROVEN** via MONO-115 `finance-integration-tests` (run `26036483067`): `SchemaManagementException` ELIMINATED, ApplicationContext loads, **0→2 IT PASS** — the enum schema-validation scope is DELIVERED & CI-proven. **⚠️ 정직 (green-wash 금지)**: AC-1 ("4 IT/11 test green") **NOT met** — 9 IT still fail on **2 distinct pre-existing TASK-FIN-BE-001 defects** surfaced once context-load unblocked: (1) PiiEncryptor `owner_ref` persistence encrypt/decrypt asymmetry (×8, `Illegal base64 character 2d`), (2) test-profile JWKS unreachable for the cross-tenant HTTP+JWT path (×1). Both **OUT of FIN-BE-002 scope** (its own Failure Scenario B) → tracked as **TASK-FIN-BE-003** (user-approved strategy A: merge #604 + separate fix-task, no scope-expand). main `finance-integration-tests` remains **honest+isolated RED** (other 17 jobs + finance `:check` green) until FIN-BE-003 merges — NOT silently dropped, NOT green-washed. closed via close chore PR (this).
- `TASK-FIN-BE-001-account-service-bootstrap.md` — **finance-platform 첫 도메인 서비스 `account-service` Hexagonal 구현 (spec-first 2-PR + close chore)**. 분석=Opus 4.7 / 구현=backend-engineer(Opus dispatch) / 리뷰=Opus 4.7 (dispatcher BE-301 독립 F1-F8 재검증). **spec PR #597** (squash `5a4aae42`): architecture.md(Hexagonal, ADR-MONO-012 canonical, HARDSTOP-09/10; Service Type=`rest-api` single — outbox≠event-consumer, scm-procurement 선례·platform/service-types INDEX L48) + account-api.md + finance-account-events.md + platform/error-handling.md fintech codes(HARDSTOP-03 차단→precedent-exact 교정). **impl PR #598** (squash `ce2d16ce`): domain(Account/Balance/Transaction+2 상태기계+Money minor-units+KycGate, framework-free) / application(`AccountApplicationService` 단일 @Transactional 경계, gate→balance 순서, ComplianceFailureRecorder REQUIRES_NEW) / infra(JPA adapters+outbox+StubCompliance+PiiEncryptor AES-GCM+RS256) / presentation(controllers≡contract) / Flyway V1 MySQL InnoDB 11-table append-only audit. 116 file/7089+. **dispatcher BE-301 독립 재검증 11/11** (agent report 불신·재실측: F5 float=0 / F4 gate-before-balance 全 fund / F1·F2 단일 Tx·단일 writer / F3 immutable reversal-only / F6 append-only DDL / tenant {finance,*} / V1 MySQL valid / `:check` 117/0/0/0 XML-파싱 / scope clean / contract paths). **CI #598 all green** (Build&Test=finance `:check` 117 unit/slice + GAP/scm/master IT + E2E + boot jars). **⚠️ 정직 gap (green-wash 금지)**: finance ci.yml 은 Docker-free `:check` 만 wire — **5 Testcontainers IT 클래스(idempotency-concurrency exactly-once / cross-tenant 403 / audit append-only / SETTLED immutable, 실 MySQL+Redis+Kafka)는 CI 미실행** (scm 의 TASK-MONO-048 "Integration(scm) CI job" 동형 부재). IT 는 compile-clean + F1-F8 구조 독립검증됨이나 행위 증명은 CI 미수행 → **follow-up = TASK-MONO-115 candidate: "Integration (finance-platform, Testcontainers)" CI job 신설 (scm MONO-048 analog, `.github/workflows/ci.yml` monorepo-level)**. deviation 4건(topUp internal-only/gateway v1-deferred→Traefik direct/HoldExpirySweeper deferred-flag/reconciliation-queue v1 no-resolve) 全 architecture.md-compliant. closed via close chore PR (this).

## archive

(empty)
