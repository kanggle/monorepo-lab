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

- `TASK-ERP-BE-008-org-scope-subtree-containment-and-read-filter.md` — **erp `org_scope` 소비 — masterdata subtree containment + read-model read 필터 (BE-337 `["*"]` 브리지의 v2 erp 측)**. 분석=Opus 4.8 / 구현 권장=Opus. ADR-MONO-020 D3 amendment(2026-06-05) + masterdata architecture.md E6 point 3 v2(spec PR 동반) 실행: 운영자 토큰 `org_scope`=부서 subtree-root(TASK-BE-338 도출) → masterdata `RoleScopeAuthorizationAdapter` 가 root 를 자기 부서트리로 **descendant 확장**(현재 flat `contains`→subtree-aware) + read-model `ReadAuthorizationGate`/query 가 동일 subtree 로 org-view **read 필터**(write·read 데이터-스코프 대칭). `"*"`/미설정=현행(net-zero, bypass). **BE-338 net-zero 라 순서 무관.** claim 은 root 만(GAP 이 erp 트리 모름), erp 가 자기 트리로 확장. 사용자 "per-operator org_scope" 선택.

## in-progress

(empty)

## review

(empty)

## done

- `TASK-ERP-BE-007-read-model-service-org-view-first-increment.md` — **DONE (2026-06-04, 3-dim verified)**. erp `read-model-service` 첫 증분 — 마스터 변경 이벤트 전파의 **비어 있던 소비자 고리**를 닫음("v1 consumers = none" 해소). 3-PR: **spec PR #1089** (`04723776`, markdown-only: read-model-service spec 3종 + task→ready + PROJECT.md frontmatter `[rest-api]`→`[rest-api,event-consumer]` + Service Map + ADR-016 §D3 amendment + erp-masterdata-events.md consumer note + masterdata forward-ref) → **impl PR #1090** (`e35d429c`, 신규 `apps/read-model-service` Hexagonal rest-api+event-consumer: 4 `@KafkaListener` + `@RetryableTopic` 3-retry+DLT + `processed_events` dedupe T8 + 4 MySQL projection[`erp_read_model_db`] + 읽기 시점 employee org-view[부서 ancestry path+비용센터+직급] + read-only REST 2종 + entitlement-trust dual-accept + READ fail-closed; erp docker-compose bitnami/kafka KRaft 브로커 신설+masterdata `KAFKA_BOOTSTRAP` 배선+`erp_read_model_db` initdb; **no-outbox/no-publish E5 terminal** — `OutboxAutoConfiguration` exclude) → **close chore (this)**. **구현=backend-engineer(Opus dispatch)+dispatcher 독립 재검증**(BE-001 패턴): scope leak 0 · E5 main publish/outbox grep 0(`KafkaTemplate`=`@RetryableTopic` DLT plumbing) · `:check --rerun-tasks` BUILD SUCCESSFUL(55 unit+slice) · `docker compose config -q` exit 0. **기능 게이트 = CI "Integration (erp-platform, Testcontainers)" 1m23s PASS**(실 Kafka+MySQL+JWKS consume→project→read end-to-end + dedupe/DLT/unresolved/RETIRED+asOf/보안 4분기). **3-dim**: (a) MERGED+`e35d429c`; (b) origin/main tip 일치; (c) pre-merge failing required=0(19 pass/1 skipping). 청사진=scm `inventory-visibility-service`. **메타**: ① producer 이미 완성(BE-001)·강화 지점=비어 있던 소비자(forward-decl 실행이라 신규 ADR 불요) ② OutboxAutoConfiguration exclude(lib↔spec `processed_events` 테이블명 충돌+`ddl-auto=validate` 공존 불가) ③ **라이브 스모크 정직 deferral**(가동 중 e2e 스택 `erp.local` 라우터 충돌 위험 → 미실행; CI IT 가 기능 경로 커버 — green-wash 아님). **follow-up**: ① console "통합 조회" 카드(TASK-PC-FE) ② business-partner+풀 통합조회+per-operator org_scope read 필터(v2) ③ 풀 스택 로컬 라이브 스모크.

- `TASK-ERP-BE-006-masterdata-response-effectiveperiod-contract-conformance.md` — **DONE (2026-06-03, 3-dim verified)**. masterdata-service response **contract conformance**: the 5 master `*View` records emitted FLAT `effectiveFrom`/`effectiveTo`/`createdAt`/`updatedAt`, violating `masterdata-api.md` § Common shapes (and the platform-console PC-FE-010 consumer) which require nested `effectivePeriod: { effectiveFrom, effectiveTo }` (+ detail `audit`). **Surfaced by TASK-MONO-170** live demo: console ERP 운영 got 200 from all 5 masters but every response failed the consumer Zod parse (`erp_ok`→`erp_error`) → degraded. Latent because the console client had only mocked unit tests + the producer slice test asserted only `$.data.code`/`status`. **Fix**: new `EffectivePeriodDto` + `AuditDto`; 5 Views reshaped flat→nested + `from()` factories; `DepartmentControllerSliceTest` constructor + **nested-shape regression assertion** (`$.data.effectivePeriod.effectiveFrom` exists, `$.data.effectiveFrom` doesNotExist); `MasterdataLifecycleIntegrationTest` accessors. **NO domain/persistence/auth/contract/ADR change** (contract already specifies nested; producer brought into conformance). impl PR #1036 squash `466c4903`. **3-dim**: (a) MERGED+`466c4903`; (b) origin/main tip chain; (c) pre-merge failing=0 (19 SUCCESS/1 SKIPPED incl. erp Integration Testcontainers + slice test = real-Jackson nested-shape proof). 라이브 재배포(fed-e2e healthy). **메타: per-domain ops 콘솔은 mock 단위테스트만 있어 producer↔계약 nested-shape drift 잠복 — 회귀 단언이 실 직렬화를 단언해야 재발 차단.** 분석=Opus 4.8 / 구현=Opus(직접).

- `TASK-ERP-BE-005-tenant-gate-entitlement-trust-dual-accept.md` — **DONE** (ADR-MONO-019 § 3.3 step 3 복제 1/N, erp; impl bundled branch + close chore; BE-303 3-dim). 분석=Opus 4.8 / 구현=Opus (dispatch). **impl PR #962** (squash `b75fbed1`). finance 파일럿(FIN-BE-006) blueprint 의 erp masterdata-service 복제. `TenantClaimValidator`(decode)+`TenantClaimEnforcer`(filter, erp 에 신규 EnforcerTest)를 `tenant_id==erp` 고정 → **entitlement-trust dual-accept**: legacy(`tenant_id ∈ {erp,*}`) ∪ 서명 `entitled_domains ∋ erp`, 공유 정적 `isEntitled` 양 지점, **거부=!legacyOk && !entitled**(fail-closed). **net-zero**(claim 부재 시 legacy만). 격리 IT entitled(wms+[erp])→2xx / non-entitled(scm)→403 TENANT_FORBIDDEN. architecture.md § Multi-tenancy + Failure #3. **BE-303 3-dim**: (a) MERGED `b75fbed1`; (b) tip 일치; (c) pre-merge failing=0. **BE-299 re-stage** ✓. **CI 1-pass** erp Integration GREEN 1m39s. **scope-lock** erp masterdata 만. erp validator=finance byte-identical → 복제 near-mechanical(blueprint 일반화 확인). **남은 step 3**: scm/wms(6-svc)/gap+console-bff + GAP `entitled_domains` populate(shared).

- `TASK-ERP-BE-004-masterdata-cross-tenant-http-it.md` — **DONE** (3-PR sequence per erp PR Separation Rule, BE-303 3-dim objectively merge-verified). 분석=Opus 4.7 / 구현=Opus 4.7 (isolation → Opus, ADR-013 § D6 row 8; 2-cycle CI). **Implements ADR-MONO-018 D5 federation isolation regression — erp slice** (lean gap-fill: the D5 audit found wms `OidcAuthIntegrationTest` / scm `MultiTenantIsolationIntegrationTest` / finance `CrossTenantHttpIntegrationTest` / GAP `AdminAuditTenantScopeIntegrationTest` already cover their domains + console-bff slice = TASK-PC-BE-006 DONE; erp had only a `TenantClaimValidator` **unit** + controller **slice** test — no HTTP-layer IT exercising the gate end-to-end). **spec PR #909** (squash `316b8229`, markdown fast-lane). **impl PR #910** (squash `ded56253`, +~130 across 3 files — new IT + task review-move + INDEX; **"Integration (erp-platform, Testcontainers)" CI job PASS 1m25s** = CI-Linux real-execution; Docker unavailable locally → `compileTestJava` only locally, honestly flagged). **`CrossTenantHttpIntegrationTest`** (MockMvc + Testcontainers MySQL/Kafka + JWKS MockWebServer, mirrors finance pattern) on `GET /api/erp/masterdata/employees`: `tenant_id=scm` (foreign) → **403** `$.code=TENANT_FORBIDDEN` (AC-3); `tenant_id=*` → **2xx** (platform-scope wildcard accepted — erp-specific edge, AC-4); `tenant_id=erp` → **2xx** (AC-5); no token → **401**. **AC-6** `git diff origin/main -- 'projects/erp-platform/apps/masterdata-service/src/main/**'` = empty (test-only) ✓. **2 CI cycles**: ① cross-tenant 403 + no-token 401 PASS, but erp/`*` → 403 not 2xx — root cause = token `roles=[ERP_READ]` did not satisfy `RoleScopeAuthorizationAdapter` READ (fail-closed: requires scope `erp.read`/`erp.write`/operator) → 403 PERMISSION_DENIED at the **authorization** layer (the tenant gate passed; scm fails earlier at the decode tenant gate, so its 403 is TENANT_FORBIDDEN); ② fix = mint token with `scope=erp.read` (the employees list passes targetDepartmentId=null → no data-scope check) → 4/4 PASS. **BE-303 3-dim**: (a) state=MERGED + mergeCommit `ded56253`; (b) `git log origin/main` tip `ded56253` 일치; (c) pre-merge `gh pr checks 910` failing=0. **BE-299 re-stage**: staged done/ blob Status=done ✓. **메타**: ① **tenant gate vs authorization layer 분리 진단** — cross-tenant(scm)는 JWT decode 의 TenantClaimValidator 에서 403 TENANT_FORBIDDEN; same/wildcard tenant 는 tenant gate 통과 후 RoleScopeAuthorizationAdapter(fail-closed)가 별도 평가. IT 토큰은 read scope 를 가져야 2xx 도달 — tenant 단언과 authorization 단언의 layering 을 정확히 구분. ② **ADR-018 D5 lean gap-fill 종결** — console-bff(PC-BE-006) + erp(this) 2 신규 IT + wms/scm/finance/GAP 기존 attestation = 6-point isolation surface 충족. **ADR-MONO-018 Phase 8 (D4 observability federation + D5 isolation regression) 전체 종결.** ③ **next** = 외부 trigger (새 federation hardening scope 또는 새 phase).

- `TASK-ERP-BE-002-platform-console-operator-read-consumer-reconciliation.md` — **DONE** (3-PR sequence per erp strict PR Separation Rule, each stage objectively merge-verified). 분석=Opus 4.7 / 구현=Opus 4.7 / 리뷰=Opus 4.7. **spec PR #655** (squash `09d4cb2a`, mergedAt 2026-05-20T05:28:45Z) — task author to `ready/` (185-line spec, FIN-BE-005 2-file finance subset answer, NOT scm 3-file). **impl PR #656** (squash `083c744b`, mergedAt 2026-05-20T05:33:06Z, +27/-3) — (1) `projects/erp-platform/specs/integration/gap-integration.md` 새 `## platform-console Operator Read Consumer (ADR-MONO-013)` 섹션 (Token 검증 규칙↔Error Responses 사이; +23/-0) + (2) `projects/erp-platform/PROJECT.md` § GAP IdP Integration clarifying bullet (+1/-0, frontmatter L1-L10 byte-unchanged 검증 — diff hunk @L79 시작) + lifecycle ready→review. **(B) document/accept of existing capability**: GAP RS256 + JWKS + issuer + `tenant_id ∈ {erp,*}` + `X-Token-Type` chain (§ Token 검증 규칙 #1/#3/#4) 가 이미 허용하던 것을 명시화. *"internal-only 경계"* #6/E7 **CLARIFIED, not weakened** — 경계 = 비-GAP-SSO 트래픽 (raw public internet); GAP-authenticated console routed via internal Traefik 는 SSO 경계 **내부**. #6 byte-identical. **불변 검증**: `masterdata-api.md` + `masterdata-service/architecture.md` + ADR-MONO-016 + § Token 검증 규칙 #1–#6 + `PROJECT.md` frontmatter (domain=erp / traits=[internal-system,transactional,audit-heavy] / service_types=[rest-api] / data_sensitivity=confidential) 모두 byte-unchanged. **read surface**: 10 v1-live GETs (5 masters × {list, detail}: `/api/erp/masterdata/{departments,employees,job-grades,cost-centers,business-partners}`) with `?asOf=<ISO-8601>` point-in-time (architecture.md E3 half-open `[from, to)`). **excluded**: 16 mutation endpoints (5×create/5×patch/5×retire/1×move-parent) + v2 `approval-service`/`read-model-service`/future `admin-service` (ADR-MONO-016 § D3). **erp internal-system producer obligations cross-ref** (신규 erp 요구 아님): confidential `data_sensitivity` + audit-heavy + E1 (reference integrity)/E2 (effective dating)/E3 (asOf point-in-time)/E8 (append-only audit) — 콘솔이 producer-authoritative 사실을 충실히 렌더. NO new OAuth client / NO new gateway-service route / NO auth-model change / NO erp ADR. **finance FIN-BE-005 vs erp ERP-BE-002 shape 일치 (honest)**: 두 task 모두 2-file (`gap-integration.md` + `PROJECT.md`); 동일 shape 의 이유 = 둘 다 `gateway-public-routes.md` 미존재 (finance gateway v1-deferred / erp gateway v1-IN 선언이나 architecture spec 미작성). scm 3-file 답습 force-fit 거부 — 정직 2-file. **close chore (this PR)** — `git mv review/ → done/` + Status `review → done` + INDEX move; BE-299 re-stage check (`git show :<done-path>` reads `done`) verified before commit; BE-303 객관 머지 검증 (impl PR #656 mergeCommit `083c744b` + `git log origin/main` tip 일치) before close chore start. Scope across 3 PRs = `projects/erp-platform/specs/integration/gap-integration.md` + `PROJECT.md` + task lifecycle/INDEX only (no code under apps/, no other project, no other ADR). **erp-side ADR-MONO-013 Phase 6 prerequisite SATISFIED** — TASK-PC-FE-010 (platform-console erp console section) 의 `backlog → ready` move 가 이제 unblocked. closed via this close chore PR.
- `TASK-ERP-BE-001-masterdata-service-bootstrap.md` — **erp-platform 첫 도메인 서비스 `masterdata-service` Hexagonal 구현 (spec-first 2-PR + close chore — erp INDEX 엄격 PR Separation Rule)**. 분석=Opus 4.7 / 구현=backend-engineer(Opus dispatch) / 리뷰=Opus 4.7 (dispatcher 독립 재검증). **spec PR #649** (squash `16a5d1fe`, mergedAt 2026-05-20T04:02:36Z): architecture.md(Hexagonal, ADR-MONO-012 canonical, HARDSTOP-09/10; Service Type=`rest-api` single — outbox≠event-consumer, finance-account / scm-procurement 선례·platform/service-types INDEX L48) + masterdata-api.md(26 endpoints + flat error envelope + `?asOf=` point-in-time read + `Idempotency-Key` on every mutation + data-scope contract) + erp-masterdata-events.md(5 `erp.masterdata.*.changed.v1` topics, libs/java-messaging BaseEventPublisher envelope, v1 PUBLISH 결정 build.gradle 증거 backing) + platform/error-handling.md erp section(HARDSTOP-03 clean, additive only — 6 unique erp codes + cross-refs to Platform-Common aliases). 4 files / 1449+ / 0−. **impl PR #650** (squash `b110e03f`, mergedAt 2026-05-20T04:48:59Z, 99 file/5996+/37−): domain(16 — 5 aggregate Department/Employee/JobGrade/CostCenter/BusinessPartner + EffectivePeriod VO half-open `[from, to)` + MasterStatusMachine + AuditLog append-only + AuthorizationDecision + DomainErrors, framework-free per architecture.md "JPA-on-domain exception") / application(14 — `MasterdataApplicationService` 단일 @Transactional 경계 authorize→repo→audit→event 순서; MasterdataEventPublisher extends BaseEventPublisher; AuthorizationPort/ClockPort/IdempotencyStore ports) / infra(19 — JPA adapters per aggregate + outbox + **DbIdempotencyStore FIN-BE-004 final form** (non-tx orchestrator catches `DataIntegrityViolationException` OUTSIDE REQUIRES_NEW, `IdempotencyKeyTx` 4 single-statement REQUIRES_NEW methods — cycle-1 broken form ABSENT) + **RoleScopeAuthorizationAdapter fail-CLOSED default** (denyRole/denyScope before allow, E6) + RS256 JWT chain `tenant_id ∈ {erp,*}` + JpaConfig/ClockConfig) / presentation(13 — 5 controllers ≡ masterdata-api.md + GlobalExceptionHandler maps every domain exception to platform/error-handling.md erp codes + TenantClaimEnforcer + IdempotentExecution + PublicPaths; **0 @Transactional on controllers** 실코드 grep 확인) / Flyway V1 MySQL InnoDB utf8mb4 9-table + **per-field `@JdbcTypeCode(SqlTypes.VARCHAR)` on every `@Enumerated`** (7↔7 paired, FIN-BE-002 cycle-2 form; cycle-1 global-config broken form ABSENT) + 2 `@JdbcTypeCode(SqlTypes.JSON)` for audit / tests(9 — 4 domain unit + 2 infra unit + 1 application unit + 1 slice + 3 IT `@Tag("integration")` Testcontainers MySQL+Kafka, H2 forbidden) + Dockerfile + docker-compose `erp.local` Traefik label active + backing service expose-only. **dispatcher 독립 재검증** (agent report 불신, 재실측): scope=99 files all under projects/erp-platform/ (0 leak: specs/platform/rules/ADR/libs/other-projects all byte-stable) · `getOperatorToken`/`getAccessToken` 부재 · `@Transactional` in presentation 부재(1 string=JavaDoc) · enum-paring 7↔7 · `actionExecuted.isEqualTo(1)` EXACT · `DataIntegrityViolationException` caught at DbIdempotencyStore L58/73/93 OUTSIDE any tx · `AuditLogRepository` port=`append()` ONLY 구조적 append-only · `RoleScopeAuthorizationAdapter` denyRole/denyScope-before-allow · `./gradlew :check --rerun-tasks` BUILD SUCCESSFUL 25s 36/0/0/0 (7 XML 파싱) · `docker compose config -q` exit=0. **⚠️ 정직 gap (green-wash 금지)**: erp ci.yml 은 Docker-free `:check` 만 wire — 3 Testcontainers IT 클래스 (`AbstractMasterdataIntegrationTest`+`MasterdataLifecycleIT`+`IdempotencyConcurrencyIT`, 실 MySQL+Kafka)는 CI 미실행 (scm MONO-048 / finance MONO-115 동형 부재). IT 는 compile-clean + E1-E8 구조 독립검증됨이나 행위 증명은 CI 미수행 → **follow-up = TASK-MONO-XXX candidate: "Integration (erp-platform, Testcontainers)" CI job 신설** (`.github/workflows/ci.yml` monorepo-level, scm MONO-048 / finance MONO-115 analog). 이는 finance v1 BE-001 의 동일 시나리오 (finance v1 honest gap-then-closure chain: BE-001→MONO-115→BE-002→003→004 가 패턴). erp v1 같은 cycle 가 follow-up 될 수 있음. closed via this close chore PR.

## archive

(empty)
