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

- `TASK-FIN-BE-002-enum-schema-validation-fix.md` — **Fix issue found in TASK-FIN-BE-001 — enum schema-validation** (spec PR #602 `cc318aca` + impl PR #604 `c4d33fd5`). Hibernate 6.6.4 + `MySQLDialect` mapped `@Enumerated(EnumType.STRING)` → native MySQL `ENUM(...)` for schema-validation vs Flyway `CHAR(3)`/`VARCHAR` → `SchemaManagementException` on `accounts.currency` → 4 IT/11 test context-load cascade. **cycle 1** (global `hibernate.type.preferred_enum_jdbc_type=VARCHAR`) **insufficient** (governs only un-annotated default enums, not explicit `@Enumerated(EnumType.STRING)`; CI-falsified, same error) → **cycle 2 definitive**: per-field `@JdbcTypeCode` exact-match DDL on all 13 enum fields (4 `SqlTypes.CHAR` for `currency` CHAR(3) + 9 `SqlTypes.VARCHAR`), application.yml net-reverted (dead config removed). evidence: GAP (same Hibernate 6.6.4/MySQL/ddl-auto:validate, IT green) proved VARCHAR-enum tolerant but CHAR(3) `currency` is the only mismatch; existing `@JdbcTypeCode(SqlTypes.JSON)` in AuditLog = working precedent. impl=backend-engineer(Sonnet dispatch cycle1) + **dispatcher BE-301 직접 cycle2·독립 재검증**: 13 `@Enumerated`↔`@JdbcTypeCode` pairing (4 CHAR/9 VARCHAR grep-confirmed), imports 6/6, NET diff=6 entity .java +23 (application.yml net-identical to main), `:check --rerun-tasks` 117/0/0/0 (11-XML re-parsed). **CI-PROVEN** via MONO-115 `finance-integration-tests` (run `26036483067`): `SchemaManagementException` ELIMINATED, ApplicationContext loads, **0→2 IT PASS** — the enum schema-validation scope is DELIVERED & CI-proven. **⚠️ 정직 (green-wash 금지)**: AC-1 ("4 IT/11 test green") **NOT met** — 9 IT still fail on **2 distinct pre-existing TASK-FIN-BE-001 defects** surfaced once context-load unblocked: (1) PiiEncryptor `owner_ref` persistence encrypt/decrypt asymmetry (×8, `Illegal base64 character 2d`), (2) test-profile JWKS unreachable for the cross-tenant HTTP+JWT path (×1). Both **OUT of FIN-BE-002 scope** (its own Failure Scenario B) → tracked as **TASK-FIN-BE-003** (user-approved strategy A: merge #604 + separate fix-task, no scope-expand). main `finance-integration-tests` remains **honest+isolated RED** (other 17 jobs + finance `:check` green) until FIN-BE-003 merges — NOT silently dropped, NOT green-washed. closed via close chore PR (this).
- `TASK-FIN-BE-001-account-service-bootstrap.md` — **finance-platform 첫 도메인 서비스 `account-service` Hexagonal 구현 (spec-first 2-PR + close chore)**. 분석=Opus 4.7 / 구현=backend-engineer(Opus dispatch) / 리뷰=Opus 4.7 (dispatcher BE-301 독립 F1-F8 재검증). **spec PR #597** (squash `5a4aae42`): architecture.md(Hexagonal, ADR-MONO-012 canonical, HARDSTOP-09/10; Service Type=`rest-api` single — outbox≠event-consumer, scm-procurement 선례·platform/service-types INDEX L48) + account-api.md + finance-account-events.md + platform/error-handling.md fintech codes(HARDSTOP-03 차단→precedent-exact 교정). **impl PR #598** (squash `ce2d16ce`): domain(Account/Balance/Transaction+2 상태기계+Money minor-units+KycGate, framework-free) / application(`AccountApplicationService` 단일 @Transactional 경계, gate→balance 순서, ComplianceFailureRecorder REQUIRES_NEW) / infra(JPA adapters+outbox+StubCompliance+PiiEncryptor AES-GCM+RS256) / presentation(controllers≡contract) / Flyway V1 MySQL InnoDB 11-table append-only audit. 116 file/7089+. **dispatcher BE-301 독립 재검증 11/11** (agent report 불신·재실측: F5 float=0 / F4 gate-before-balance 全 fund / F1·F2 단일 Tx·단일 writer / F3 immutable reversal-only / F6 append-only DDL / tenant {finance,*} / V1 MySQL valid / `:check` 117/0/0/0 XML-파싱 / scope clean / contract paths). **CI #598 all green** (Build&Test=finance `:check` 117 unit/slice + GAP/scm/master IT + E2E + boot jars). **⚠️ 정직 gap (green-wash 금지)**: finance ci.yml 은 Docker-free `:check` 만 wire — **5 Testcontainers IT 클래스(idempotency-concurrency exactly-once / cross-tenant 403 / audit append-only / SETTLED immutable, 실 MySQL+Redis+Kafka)는 CI 미실행** (scm 의 TASK-MONO-048 "Integration(scm) CI job" 동형 부재). IT 는 compile-clean + F1-F8 구조 독립검증됨이나 행위 증명은 CI 미수행 → **follow-up = TASK-MONO-115 candidate: "Integration (finance-platform, Testcontainers)" CI job 신설 (scm MONO-048 analog, `.github/workflows/ci.yml` monorepo-level)**. deviation 4건(topUp internal-only/gateway v1-deferred→Traefik direct/HoldExpirySweeper deferred-flag/reconciliation-queue v1 no-resolve) 全 architecture.md-compliant. closed via close chore PR (this).

## archive

(empty)
