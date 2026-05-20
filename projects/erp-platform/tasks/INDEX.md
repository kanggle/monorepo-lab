# Tasks Index — erp-platform

This document defines task lifecycle, naming, and move rules for the **erp-platform** project. Repo-root [tasks/INDEX.md](../../../tasks/INDEX.md) covers monorepo-level (cross-project) tasks; this file covers erp-platform-internal tasks only.

---

# Lifecycle

backlog → ready → in-progress → review → done → archive

Only tasks in `ready/` may be implemented.

---

# Task Types

- `TASK-ERP-BE-XXX`: backend (Spring Boot service implementations)
- `TASK-ERP-INT-XXX`: cross-service integration / E2E (Testcontainers · Docker compose)
- `TASK-ERP-FE-XXX`: frontend — declared for future use, erp v1 is backend-only (platform console renders erp per ADR-MONO-013 §3.3)

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
- Fix tasks must include the original task ID in their Goal section (e.g. "Fix issue found in TASK-ERP-BE-001").
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

- `TASK-ERP-BE-002-platform-console-operator-read-consumer-reconciliation.md` — erp-platform — platform-console operator read-consumer spec reconciliation (ADR-MONO-013 Phase 6 prerequisite, spec-only). **prerequisite for** `TASK-PC-FE-010` (platform-console, backlog) — FE-010's `backlog → ready` gated on this merge. **governed by** ADR-MONO-013 § D6 Phase 6 + ADR-MONO-016 § D3.1 (platform-console parity-slice binding UI decision). **(B) document/accept**, **no erp ADR**: existing GAP-RS256 + `tenant_id ∈ {erp,*}` + `X-Token-Type` capability is acknowledged as serving an external operator console consumer — no new client/code/route/auth-model change. **2-file shape, finance FIN-BE-005 subset** (NOT scm 3-file — erp gateway-service architecture spec not yet authored): edit `gap-integration.md` (add `## platform-console Operator Read Consumer (ADR-MONO-013)` section after `## Token 검증 규칙`) + `PROJECT.md` (clarifying bullet in § GAP IdP Integration, frontmatter byte-unchanged, no Service Map row). Console read surface = 10 v1-live GETs (`/api/erp/masterdata/{departments,employees,job-grades,cost-centers,business-partners}` list+detail × 5) with `?asOf=` point-in-time. Console excluded from 16 mutation endpoints + v2 `approval-service`/`read-model-service`/future `admin-service`. *"internal-only 경계"* (#6 / E7) **clarified, not weakened** — boundary = non-GAP-SSO traffic; GAP-authenticated console routed via internal Traefik is within SSO boundary. `masterdata-api.md` + `masterdata-service/architecture.md` + ADR-MONO-016 byte-unchanged. Sibling: FIN-BE-005 (identical shape) / SCM-BE-015 (3-file). 분석=Opus 4.7 / 구현 권장=Opus 4.7 (cross-project document/accept boundary call + erp-shape difference reasoning + #6 clarification phrasing).

## in-progress

(empty)

## review

(empty)

## done

- `TASK-ERP-BE-001-masterdata-service-bootstrap.md` — **erp-platform 첫 도메인 서비스 `masterdata-service` Hexagonal 구현 (spec-first 2-PR + close chore — erp INDEX 엄격 PR Separation Rule)**. 분석=Opus 4.7 / 구현=backend-engineer(Opus dispatch) / 리뷰=Opus 4.7 (dispatcher 독립 재검증). **spec PR #649** (squash `16a5d1fe`, mergedAt 2026-05-20T04:02:36Z): architecture.md(Hexagonal, ADR-MONO-012 canonical, HARDSTOP-09/10; Service Type=`rest-api` single — outbox≠event-consumer, finance-account / scm-procurement 선례·platform/service-types INDEX L48) + masterdata-api.md(26 endpoints + flat error envelope + `?asOf=` point-in-time read + `Idempotency-Key` on every mutation + data-scope contract) + erp-masterdata-events.md(5 `erp.masterdata.*.changed.v1` topics, libs/java-messaging BaseEventPublisher envelope, v1 PUBLISH 결정 build.gradle 증거 backing) + platform/error-handling.md erp section(HARDSTOP-03 clean, additive only — 6 unique erp codes + cross-refs to Platform-Common aliases). 4 files / 1449+ / 0−. **impl PR #650** (squash `b110e03f`, mergedAt 2026-05-20T04:48:59Z, 99 file/5996+/37−): domain(16 — 5 aggregate Department/Employee/JobGrade/CostCenter/BusinessPartner + EffectivePeriod VO half-open `[from, to)` + MasterStatusMachine + AuditLog append-only + AuthorizationDecision + DomainErrors, framework-free per architecture.md "JPA-on-domain exception") / application(14 — `MasterdataApplicationService` 단일 @Transactional 경계 authorize→repo→audit→event 순서; MasterdataEventPublisher extends BaseEventPublisher; AuthorizationPort/ClockPort/IdempotencyStore ports) / infra(19 — JPA adapters per aggregate + outbox + **DbIdempotencyStore FIN-BE-004 final form** (non-tx orchestrator catches `DataIntegrityViolationException` OUTSIDE REQUIRES_NEW, `IdempotencyKeyTx` 4 single-statement REQUIRES_NEW methods — cycle-1 broken form ABSENT) + **RoleScopeAuthorizationAdapter fail-CLOSED default** (denyRole/denyScope before allow, E6) + RS256 JWT chain `tenant_id ∈ {erp,*}` + JpaConfig/ClockConfig) / presentation(13 — 5 controllers ≡ masterdata-api.md + GlobalExceptionHandler maps every domain exception to platform/error-handling.md erp codes + TenantClaimEnforcer + IdempotentExecution + PublicPaths; **0 @Transactional on controllers** 실코드 grep 확인) / Flyway V1 MySQL InnoDB utf8mb4 9-table + **per-field `@JdbcTypeCode(SqlTypes.VARCHAR)` on every `@Enumerated`** (7↔7 paired, FIN-BE-002 cycle-2 form; cycle-1 global-config broken form ABSENT) + 2 `@JdbcTypeCode(SqlTypes.JSON)` for audit / tests(9 — 4 domain unit + 2 infra unit + 1 application unit + 1 slice + 3 IT `@Tag("integration")` Testcontainers MySQL+Kafka, H2 forbidden) + Dockerfile + docker-compose `erp.local` Traefik label active + backing service expose-only. **dispatcher 독립 재검증** (agent report 불신, 재실측): scope=99 files all under projects/erp-platform/ (0 leak: specs/platform/rules/ADR/libs/other-projects all byte-stable) · `getOperatorToken`/`getAccessToken` 부재 · `@Transactional` in presentation 부재(1 string=JavaDoc) · enum-paring 7↔7 · `actionExecuted.isEqualTo(1)` EXACT · `DataIntegrityViolationException` caught at DbIdempotencyStore L58/73/93 OUTSIDE any tx · `AuditLogRepository` port=`append()` ONLY 구조적 append-only · `RoleScopeAuthorizationAdapter` denyRole/denyScope-before-allow · `./gradlew :check --rerun-tasks` BUILD SUCCESSFUL 25s 36/0/0/0 (7 XML 파싱) · `docker compose config -q` exit=0. **⚠️ 정직 gap (green-wash 금지)**: erp ci.yml 은 Docker-free `:check` 만 wire — 3 Testcontainers IT 클래스 (`AbstractMasterdataIntegrationTest`+`MasterdataLifecycleIT`+`IdempotencyConcurrencyIT`, 실 MySQL+Kafka)는 CI 미실행 (scm MONO-048 / finance MONO-115 동형 부재). IT 는 compile-clean + E1-E8 구조 독립검증됨이나 행위 증명은 CI 미수행 → **follow-up = TASK-MONO-XXX candidate: "Integration (erp-platform, Testcontainers)" CI job 신설** (`.github/workflows/ci.yml` monorepo-level, scm MONO-048 / finance MONO-115 analog). 이는 finance v1 BE-001 의 동일 시나리오 (finance v1 honest gap-then-closure chain: BE-001→MONO-115→BE-002→003→004 가 패턴). erp v1 같은 cycle 가 follow-up 될 수 있음. closed via this close chore PR.

## archive

(empty)
