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

- `TASK-ERP-BE-005-tenant-gate-entitlement-trust-dual-accept.md` — **READY** (ADR-MONO-019 § 3.3 step 3 복제 1/N, erp). 분석=Opus 4.8 / **구현 권장=Opus** (격리 게이트). finance 파일럿(FIN-BE-006) blueprint 의 erp 복제 — erp masterdata `TenantClaimValidator`(decode)+`TenantClaimEnforcer`(filter)를 `tenant_id == erp` 고정 → **entitlement-trust dual-accept**: legacy(`tenant_id ∈ {erp,*}`) ∪ 서명 토큰 `entitled_domains ∋ erp`, 거부=둘 다 불충족(공유 `isEntitled` 헬퍼). **net-zero**(claim 부재 시 legacy만). erp validator 는 finance 와 byte-identical 구조 → 동일 편집 + 격리 IT + architecture.md. GAP `entitled_domains` populate = 별 shared follow-up. depends on FIN-BE-006(#960 `df1efa5a`).

## in-progress

(empty)

## review

(empty)

## done

- `TASK-ERP-BE-004-masterdata-cross-tenant-http-it.md` — **DONE** (3-PR sequence per erp PR Separation Rule, BE-303 3-dim objectively merge-verified). 분석=Opus 4.7 / 구현=Opus 4.7 (isolation → Opus, ADR-013 § D6 row 8; 2-cycle CI). **Implements ADR-MONO-018 D5 federation isolation regression — erp slice** (lean gap-fill: the D5 audit found wms `OidcAuthIntegrationTest` / scm `MultiTenantIsolationIntegrationTest` / finance `CrossTenantHttpIntegrationTest` / GAP `AdminAuditTenantScopeIntegrationTest` already cover their domains + console-bff slice = TASK-PC-BE-006 DONE; erp had only a `TenantClaimValidator` **unit** + controller **slice** test — no HTTP-layer IT exercising the gate end-to-end). **spec PR #909** (squash `316b8229`, markdown fast-lane). **impl PR #910** (squash `ded56253`, +~130 across 3 files — new IT + task review-move + INDEX; **"Integration (erp-platform, Testcontainers)" CI job PASS 1m25s** = CI-Linux real-execution; Docker unavailable locally → `compileTestJava` only locally, honestly flagged). **`CrossTenantHttpIntegrationTest`** (MockMvc + Testcontainers MySQL/Kafka + JWKS MockWebServer, mirrors finance pattern) on `GET /api/erp/masterdata/employees`: `tenant_id=scm` (foreign) → **403** `$.code=TENANT_FORBIDDEN` (AC-3); `tenant_id=*` → **2xx** (platform-scope wildcard accepted — erp-specific edge, AC-4); `tenant_id=erp` → **2xx** (AC-5); no token → **401**. **AC-6** `git diff origin/main -- 'projects/erp-platform/apps/masterdata-service/src/main/**'` = empty (test-only) ✓. **2 CI cycles**: ① cross-tenant 403 + no-token 401 PASS, but erp/`*` → 403 not 2xx — root cause = token `roles=[ERP_READ]` did not satisfy `RoleScopeAuthorizationAdapter` READ (fail-closed: requires scope `erp.read`/`erp.write`/operator) → 403 PERMISSION_DENIED at the **authorization** layer (the tenant gate passed; scm fails earlier at the decode tenant gate, so its 403 is TENANT_FORBIDDEN); ② fix = mint token with `scope=erp.read` (the employees list passes targetDepartmentId=null → no data-scope check) → 4/4 PASS. **BE-303 3-dim**: (a) state=MERGED + mergeCommit `ded56253`; (b) `git log origin/main` tip `ded56253` 일치; (c) pre-merge `gh pr checks 910` failing=0. **BE-299 re-stage**: staged done/ blob Status=done ✓. **메타**: ① **tenant gate vs authorization layer 분리 진단** — cross-tenant(scm)는 JWT decode 의 TenantClaimValidator 에서 403 TENANT_FORBIDDEN; same/wildcard tenant 는 tenant gate 통과 후 RoleScopeAuthorizationAdapter(fail-closed)가 별도 평가. IT 토큰은 read scope 를 가져야 2xx 도달 — tenant 단언과 authorization 단언의 layering 을 정확히 구분. ② **ADR-018 D5 lean gap-fill 종결** — console-bff(PC-BE-006) + erp(this) 2 신규 IT + wms/scm/finance/GAP 기존 attestation = 6-point isolation surface 충족. **ADR-MONO-018 Phase 8 (D4 observability federation + D5 isolation regression) 전체 종결.** ③ **next** = 외부 trigger (새 federation hardening scope 또는 새 phase).

- `TASK-ERP-BE-002-platform-console-operator-read-consumer-reconciliation.md` — **DONE** (3-PR sequence per erp strict PR Separation Rule, each stage objectively merge-verified). 분석=Opus 4.7 / 구현=Opus 4.7 / 리뷰=Opus 4.7. **spec PR #655** (squash `09d4cb2a`, mergedAt 2026-05-20T05:28:45Z) — task author to `ready/` (185-line spec, FIN-BE-005 2-file finance subset answer, NOT scm 3-file). **impl PR #656** (squash `083c744b`, mergedAt 2026-05-20T05:33:06Z, +27/-3) — (1) `projects/erp-platform/specs/integration/gap-integration.md` 새 `## platform-console Operator Read Consumer (ADR-MONO-013)` 섹션 (Token 검증 규칙↔Error Responses 사이; +23/-0) + (2) `projects/erp-platform/PROJECT.md` § GAP IdP Integration clarifying bullet (+1/-0, frontmatter L1-L10 byte-unchanged 검증 — diff hunk @L79 시작) + lifecycle ready→review. **(B) document/accept of existing capability**: GAP RS256 + JWKS + issuer + `tenant_id ∈ {erp,*}` + `X-Token-Type` chain (§ Token 검증 규칙 #1/#3/#4) 가 이미 허용하던 것을 명시화. *"internal-only 경계"* #6/E7 **CLARIFIED, not weakened** — 경계 = 비-GAP-SSO 트래픽 (raw public internet); GAP-authenticated console routed via internal Traefik 는 SSO 경계 **내부**. #6 byte-identical. **불변 검증**: `masterdata-api.md` + `masterdata-service/architecture.md` + ADR-MONO-016 + § Token 검증 규칙 #1–#6 + `PROJECT.md` frontmatter (domain=erp / traits=[internal-system,transactional,audit-heavy] / service_types=[rest-api] / data_sensitivity=confidential) 모두 byte-unchanged. **read surface**: 10 v1-live GETs (5 masters × {list, detail}: `/api/erp/masterdata/{departments,employees,job-grades,cost-centers,business-partners}`) with `?asOf=<ISO-8601>` point-in-time (architecture.md E3 half-open `[from, to)`). **excluded**: 16 mutation endpoints (5×create/5×patch/5×retire/1×move-parent) + v2 `approval-service`/`read-model-service`/future `admin-service` (ADR-MONO-016 § D3). **erp internal-system producer obligations cross-ref** (신규 erp 요구 아님): confidential `data_sensitivity` + audit-heavy + E1 (reference integrity)/E2 (effective dating)/E3 (asOf point-in-time)/E8 (append-only audit) — 콘솔이 producer-authoritative 사실을 충실히 렌더. NO new OAuth client / NO new gateway-service route / NO auth-model change / NO erp ADR. **finance FIN-BE-005 vs erp ERP-BE-002 shape 일치 (honest)**: 두 task 모두 2-file (`gap-integration.md` + `PROJECT.md`); 동일 shape 의 이유 = 둘 다 `gateway-public-routes.md` 미존재 (finance gateway v1-deferred / erp gateway v1-IN 선언이나 architecture spec 미작성). scm 3-file 답습 force-fit 거부 — 정직 2-file. **close chore (this PR)** — `git mv review/ → done/` + Status `review → done` + INDEX move; BE-299 re-stage check (`git show :<done-path>` reads `done`) verified before commit; BE-303 객관 머지 검증 (impl PR #656 mergeCommit `083c744b` + `git log origin/main` tip 일치) before close chore start. Scope across 3 PRs = `projects/erp-platform/specs/integration/gap-integration.md` + `PROJECT.md` + task lifecycle/INDEX only (no code under apps/, no other project, no other ADR). **erp-side ADR-MONO-013 Phase 6 prerequisite SATISFIED** — TASK-PC-FE-010 (platform-console erp console section) 의 `backlog → ready` move 가 이제 unblocked. closed via this close chore PR.
- `TASK-ERP-BE-001-masterdata-service-bootstrap.md` — **erp-platform 첫 도메인 서비스 `masterdata-service` Hexagonal 구현 (spec-first 2-PR + close chore — erp INDEX 엄격 PR Separation Rule)**. 분석=Opus 4.7 / 구현=backend-engineer(Opus dispatch) / 리뷰=Opus 4.7 (dispatcher 독립 재검증). **spec PR #649** (squash `16a5d1fe`, mergedAt 2026-05-20T04:02:36Z): architecture.md(Hexagonal, ADR-MONO-012 canonical, HARDSTOP-09/10; Service Type=`rest-api` single — outbox≠event-consumer, finance-account / scm-procurement 선례·platform/service-types INDEX L48) + masterdata-api.md(26 endpoints + flat error envelope + `?asOf=` point-in-time read + `Idempotency-Key` on every mutation + data-scope contract) + erp-masterdata-events.md(5 `erp.masterdata.*.changed.v1` topics, libs/java-messaging BaseEventPublisher envelope, v1 PUBLISH 결정 build.gradle 증거 backing) + platform/error-handling.md erp section(HARDSTOP-03 clean, additive only — 6 unique erp codes + cross-refs to Platform-Common aliases). 4 files / 1449+ / 0−. **impl PR #650** (squash `b110e03f`, mergedAt 2026-05-20T04:48:59Z, 99 file/5996+/37−): domain(16 — 5 aggregate Department/Employee/JobGrade/CostCenter/BusinessPartner + EffectivePeriod VO half-open `[from, to)` + MasterStatusMachine + AuditLog append-only + AuthorizationDecision + DomainErrors, framework-free per architecture.md "JPA-on-domain exception") / application(14 — `MasterdataApplicationService` 단일 @Transactional 경계 authorize→repo→audit→event 순서; MasterdataEventPublisher extends BaseEventPublisher; AuthorizationPort/ClockPort/IdempotencyStore ports) / infra(19 — JPA adapters per aggregate + outbox + **DbIdempotencyStore FIN-BE-004 final form** (non-tx orchestrator catches `DataIntegrityViolationException` OUTSIDE REQUIRES_NEW, `IdempotencyKeyTx` 4 single-statement REQUIRES_NEW methods — cycle-1 broken form ABSENT) + **RoleScopeAuthorizationAdapter fail-CLOSED default** (denyRole/denyScope before allow, E6) + RS256 JWT chain `tenant_id ∈ {erp,*}` + JpaConfig/ClockConfig) / presentation(13 — 5 controllers ≡ masterdata-api.md + GlobalExceptionHandler maps every domain exception to platform/error-handling.md erp codes + TenantClaimEnforcer + IdempotentExecution + PublicPaths; **0 @Transactional on controllers** 실코드 grep 확인) / Flyway V1 MySQL InnoDB utf8mb4 9-table + **per-field `@JdbcTypeCode(SqlTypes.VARCHAR)` on every `@Enumerated`** (7↔7 paired, FIN-BE-002 cycle-2 form; cycle-1 global-config broken form ABSENT) + 2 `@JdbcTypeCode(SqlTypes.JSON)` for audit / tests(9 — 4 domain unit + 2 infra unit + 1 application unit + 1 slice + 3 IT `@Tag("integration")` Testcontainers MySQL+Kafka, H2 forbidden) + Dockerfile + docker-compose `erp.local` Traefik label active + backing service expose-only. **dispatcher 독립 재검증** (agent report 불신, 재실측): scope=99 files all under projects/erp-platform/ (0 leak: specs/platform/rules/ADR/libs/other-projects all byte-stable) · `getOperatorToken`/`getAccessToken` 부재 · `@Transactional` in presentation 부재(1 string=JavaDoc) · enum-paring 7↔7 · `actionExecuted.isEqualTo(1)` EXACT · `DataIntegrityViolationException` caught at DbIdempotencyStore L58/73/93 OUTSIDE any tx · `AuditLogRepository` port=`append()` ONLY 구조적 append-only · `RoleScopeAuthorizationAdapter` denyRole/denyScope-before-allow · `./gradlew :check --rerun-tasks` BUILD SUCCESSFUL 25s 36/0/0/0 (7 XML 파싱) · `docker compose config -q` exit=0. **⚠️ 정직 gap (green-wash 금지)**: erp ci.yml 은 Docker-free `:check` 만 wire — 3 Testcontainers IT 클래스 (`AbstractMasterdataIntegrationTest`+`MasterdataLifecycleIT`+`IdempotencyConcurrencyIT`, 실 MySQL+Kafka)는 CI 미실행 (scm MONO-048 / finance MONO-115 동형 부재). IT 는 compile-clean + E1-E8 구조 독립검증됨이나 행위 증명은 CI 미수행 → **follow-up = TASK-MONO-XXX candidate: "Integration (erp-platform, Testcontainers)" CI job 신설** (`.github/workflows/ci.yml` monorepo-level, scm MONO-048 / finance MONO-115 analog). 이는 finance v1 BE-001 의 동일 시나리오 (finance v1 honest gap-then-closure chain: BE-001→MONO-115→BE-002→003→004 가 패턴). erp v1 같은 cycle 가 follow-up 될 수 있음. closed via this close chore PR.

## archive

(empty)
