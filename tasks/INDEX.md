# Tasks Index — Monorepo Root

This document defines the task lifecycle for **monorepo-level** work — changes
that touch the shared library layer (`libs/`, `platform/`, `rules/`, `.claude/`,
`tasks/templates/`, `docs/guides/`), monorepo infrastructure (root
`build.gradle`, `settings.gradle`, `.github/workflows/`, `scripts/`,
`TEMPLATE.md`, `CLAUDE.md`), or cross-project workflows where no single
project under `projects/<name>/` is the natural owner.

Project-internal work (anything inside `projects/<name>/apps/`, `specs/`,
`tasks/`, `knowledge/`, `docs/`, `infra/`) continues to use the
**project-level** task lifecycle defined in
`projects/<name>/tasks/INDEX.md`.

---

# Lifecycle

ready → in-progress → review → done

Only tasks in `ready/` may be implemented.

---

# Task Types

- `TASK-MONO-XXX`: monorepo infrastructure (build, CI, scripts, rules library, agents/skills/commands, shared platform docs)

Sequence numbers are global within the `MONO` namespace, starting at `001`.

---

# Move Rules

## (writing a new task) → ready
A new monorepo-level task may land directly in `ready/` when:
- the work is scoped to repo-root paths or cross-project concerns
- acceptance criteria are clear
- impact on `projects/<name>/` is enumerated (or explicitly "none")
- if it changes `CLAUDE.md`, `TEMPLATE.md`, `rules/`, or other shared
  contracts, the affected paths are listed in the task

## ready → in-progress
Allowed only when implementation starts.

## in-progress → review
Allowed only when:
- implementation is complete
- tests are added (where applicable — some monorepo tasks are pure
  configuration or documentation; the task should declare what counts as
  "verification" up front)
- shared spec / contract / rule updates are completed if required

## review → done
Allowed only after review approval.

### Review Rules
- Tasks in `review/` must not be re-implemented directly.
- If review reveals a bug or missing requirement, create a new fix task in `ready/` referencing the original task ID.
- Fix tasks must include the original task ID in their Goal section (e.g. "Fix issue found in TASK-MONO-002").
- Do not modify a task file after it moves to `review/` or `done/`.

### PR Separation Rule (lifecycle ↔ PR boundary)
Each lifecycle transition lands in its own PR. **Never bundle task spec authoring with implementation in the same PR.**

| Stage | Recommended PR shape |
|---|---|
| `(writing) → ready` | **spec PR** — adds the task file to `ready/` + updates this `INDEX.md` ready list. No implementation code. Multiple specs may share one spec PR; spec + impl must NOT share one. |
| `ready → in-progress → review` | **impl PR** — moves the task file through `in-progress/` to `review/` and lands the implementation. Lifecycle moves and impl commits should be separate commits but live in one PR. |
| `review → done` | **chore PR** — moves merged task file(s) from `review/` to `done/` and updates the `INDEX.md` done list with one-line outcome summaries. May batch multiple merged tasks. |

**Why**: bundling spec authoring with implementation hides the `ready` lifecycle stage from `main` — external observers (other developers, AI sessions, audit) read the `ready/` queue to know what's available next. If a task only ever appears in `review/` because spec + impl shipped together, the queue signal is broken.

Established precedents: `chore: file follow-up tasks ... (#110)`, `chore: move merged TASK-MONO-022/023 from review/ to done/ (#114)`, `#118`, `#124`, `#133`, `#137`, `#138`. Same pattern applies to project-level lifecycle (`projects/<name>/tasks/`).

---

# When to Use Root vs Project Tasks

| Scope | Use this lifecycle |
|---|---|
| Changes to `libs/`, `platform/`, `rules/`, `.claude/`, `tasks/templates/`, `docs/guides/`, `CLAUDE.md`, `TEMPLATE.md` | Root `tasks/` |
| Changes to root `build.gradle`, `settings.gradle`, `.github/workflows/`, root `package.json`, `scripts/sync-portfolio.sh` | Root `tasks/` |
| Cross-project changes (PR touches more than one `projects/<name>/` and the change is structural rather than feature-level) | Root `tasks/` |
| Changes inside a single `projects/<name>/` (apps, specs, services, contracts, project-internal docs/infra/scripts) | That project's `projects/<name>/tasks/` |
| Changes that span project tasks + library promotion (e.g. extracting common content from a project into `rules/`) | Pair: a project task that drives the change + a root task that lands the library promotion |

**When in doubt**: if the file paths in the change are purely under
`projects/<name>/`, use the project task lifecycle. If any path lies outside
that, use the root task lifecycle.

---

# Rule

Tasks must not be implemented from `in-progress/`, `review/`, or `done/`.
The single exception is a self-bootstrapping task that creates the root
lifecycle itself — see `done/TASK-MONO-001-introduce-root-task-lifecycle.md`.

---

# Task List

## ready

(empty)

## in-progress

(empty)

## done

- `TASK-MONO-001-introduce-root-task-lifecycle.md` — bootstrap of this lifecycle and `CLAUDE.md` integration. Self-referential; landed in the same PR that introduced the lifecycle. 2026-04-26.
- `TASK-MONO-002-sync-portfolio-dry-run-verification.md` — verified `scripts/sync-portfolio.sh --dry-run` is clean for both projects after Phase 7/8 changes. Recommendation: keep current `SHARED_PATHS` unchanged (root `tasks/` lifecycle correctly excluded). Live extraction deferred to a future TASK-MONO. 2026-04-26.
- `TASK-MONO-003-ecommerce-test-landscape.md` — inventory of 276 ecommerce backend test classes across 12 services (200 unit / 29 slice / 47 Testcontainers). Recommended strategy (A) tag-and-filter retrofit. Implementation deferred to TASK-MONO-004. 2026-04-26.
- `TASK-MONO-004-ecommerce-tag-and-filter-integration-tests.md` — added `@Tag("integration")` to 47 Testcontainers test classes + `excludeTags 'integration'` filter in ecommerce build.gradle. Pre-existing OrderApiContractTest @SpringBootConfiguration ambiguity fixed in passing. 9/12 services pass cleanly; ci.yml extension dropped from this PR pending TASK-MONO-005/006/007 fixes for the remaining 3. 2026-04-26.
- `TASK-MONO-008-extend-ci-with-passing-ecommerce-services.md` — extended root `.github/workflows/ci.yml` Build & Test step with 9 ecommerce service `:check` tasks (auth, batch-worker, gateway, notification, payment, promotion, review, shipping, user). Step renamed to "libs + wms-platform + ecommerce subset" with comment block enumerating the 3 deferred services (TASK-MONO-005/006/007). PR #71 CI green: build-and-test 2m27s, integration & boot-jars pass. 2026-04-26.
- `TASK-MONO-005-order-api-contract-drift.md` — synced `specs/contracts/http/order-api.md` GET /api/orders + GET /api/admin/orders content[] with the `firstItemName` field already produced by `OrderSummary.from()` / `AdminOrderSummary`; extended `OrderApiContractTest` strict-set assertion; added order-service to root CI Build & Test gradle list. ecommerce backend coverage in root CI: 10/12. PR #73 CI green: build-and-test 1m3s. 2026-04-26.
- `TASK-MONO-006-product-service-context.md` — added missing `@MockitoBean` declarations (ProductImageService, MediaUrlResolver, VariantManagementService) to 3 web slice tests + `@ContextConfiguration` pin on ProductApiContractTest (same pattern as TASK-MONO-004 OrderApiContractTest fix). Extended GET /api/products/{productId} contract assertion with `thumbnailUrl` + `images` (already in spec). Added product-service to root CI Build & Test gradle list. ecommerce backend coverage in root CI: 11/12. PR #75 CI green: build-and-test 1m2s. 2026-04-26.
- `TASK-MONO-007-search-service-index-initializer.md` — aligned `IndexInitializerUnitTest` stubs with current production code: switched `create()` arg type from `Function.class` to `CreateIndexRequest.class`, and added the deep mock chain (`GetMappingResponse → IndexMappingRecord → TypeMapping → properties{name(text/nori_korean), thumbnailUrl}`) so `hasCurrentSpec()` returns true in the "exists ⇒ skipsCreation" scenario. Added search-service to root CI Build & Test gradle list and removed the deferred-services comment block — **all 12 ecommerce backend services now run in root CI**. PR #77 CI green: build-and-test 1m3s. 2026-04-26.
- `TASK-MONO-009-frontend-ci-phase1.md` — added `frontend-checks` job to ci.yml (Node 20, pnpm 9.15, frozen install, `turbo lint` + `turbo build`). Fixed pre-existing lint errors across ecommerce frontend: `eslint.config.mjs` flat-config shims for 4 packages, unused-var/any/displayName fixes in web-store + admin-dashboard + api-client. 2026-04-26.
- `TASK-MONO-010-frontend-unit-tests-ci.md` — added `frontend-unit-tests` job to ci.yml (`turbo test`); added `test` task to turbo.json + root package.json; fixed 6 pre-existing test failures (type mismatch, text mismatch, missing env fallback, wrong testid); `retry: 1` in admin-dashboard vitest.config for Windows parallelism flakiness. 2026-04-26.
- `TASK-MONO-011-allow-portfolio-sync-force-push.md` — relaxed `.claude/hooks/protect-main-branch.ps1` to allow force-push when cwd is under `/tmp/portfolio-sync/<project>/`. Removes the friction where `scripts/sync-portfolio.sh`'s final push step had to be hand-run from Git Bash outside the Claude session. 2026-04-26.
- `TASK-MONO-012-ecommerce-boot-jars-ci.md` — added `ecommerce-boot-jars` job to ci.yml: verify-only `:bootJar` packaging for all 12 ecommerce backend services. Catches Spring Boot autoconfig / fat-jar classpath issues that `:check` does not surface. Runs in parallel with `boot-jars` (both depend on build-and-test). 2026-04-26.
- `TASK-MONO-013-frontend-e2e-smoke-ci.md` — added `frontend-e2e-smoke` job to ci.yml: Playwright smoke suite for `web-store` (3 specs covering home / login / auth-guard redirect). Uses a separate `playwright.smoke.config.ts` with `testDir: e2e-smoke/` and a webServer that points API base URLs at a closed loopback so SSR fallbacks activate — no backend stack required, runs in extracted portfolio repos too. Full-stack Playwright deferred. 2026-04-27.
- `TASK-MONO-014-frontend-e2e-fullstack-ci.md` — wired the 4 existing full-stack Playwright specs (`golden-flow`, `cart-management`, `auth-redirect`, `wishlist`) into a new `frontend-e2e` CI job. Updated `ecommerce-boot-jars` to upload 12 fat JARs as an artifact; `frontend-e2e` downloads them, generates a synthetic `.env`, `docker compose up --build` (observability services excluded to fit runner RAM), waits for gateway health, then runs `pnpm --filter web-store run e2e`. `playwright.config.ts` gains a CI-only `webServer` block starting web-store on port 3000 with the gateway at localhost:18080. Job gated to `kanggle/monorepo-lab`. 2026-04-27.
- `TASK-MONO-015-e2e-docker-image-pull-warmup.md` — Docker 28 workaround for the `e2e-tests` job: replaced Testcontainers' `ImageFromDockerfile` (which hangs indefinitely on Docker 28's BuildKit-routed `/build` REST endpoint via the Docker Java client bundled in Testcontainers 1.21.3) with **pre-built service images via the `docker build` CLI**. Added "Download boot jars" / "Restore boot jar paths" / "Build service images for e2e" steps to the `e2e-tests` job, changed `needs` to `[build-and-test, boot-jars]`, and pass `-Dwms.e2e.masterImage=wms-master-service:e2e` + `-Dwms.e2e.gatewayImage=wms-gateway-service:e2e` to the Gradle e2eTest invocation so `E2EBase.java` skips `ImageFromDockerfile` and uses `GenericContainer` against the pre-built tags. Title kept for traceability; original `docker pull` warmup approach pivoted during implementation (see TASK-MONO-016 for the spec amendment record). 2026-04-29.
- `TASK-MONO-016-fix-TASK-MONO-015.md` — documentation-only fix: rewrote TASK-MONO-015 Goal, Scope, and Acceptance Criteria to reflect the actual Docker 28 workaround implementation (pre-built images via `docker build` CLI), and updated the `tasks/INDEX.md` done-list entry for TASK-MONO-015. No implementation files were changed. 2026-04-29.
- `TASK-MONO-019-wms-platform-oidc-resource-server-migration.md` — wms-platform 의 5 active service (master/gateway/inbound/inventory/outbound) 가 GAP 표준 OIDC token 을 OAuth2 Resource Server 로 검증. GAP V0010 seed 추가 (wms client 2건 + 9 scopes). admin/notification 은 placeholder. Cross-project atomic commit (`feat!: TASK-MONO-019`). Review 2026-05-02 APPROVED — Critical 0, Warning 0, Suggestion 1 (gateway `audiences: wms` dead config — 향후 정리). 2026-05-02.
- `TASK-MONO-022-traefik-hostname-routing-migration.md` — **Phase 1**: ADR-MONO-001 Option C 의 Traefik 인프라 신설. `infra/traefik/docker-compose.yml` (Traefik v3 + `traefik-net` external network) + `pnpm traefik:up/down/ps/logs` 스크립트 + `docs/guides/dev-tooling.md` (DB 도구 접근 3가지 방법) + `scripts/dev-setup.sh/.ps1` (hosts 파일 등록 idempotent) + 루트 README dev 환경 셋업 절차. 검증: `docker compose up -d` → healthy + dashboard HTTP 200. 기존 3 프로젝트 docker-compose 영향 없음. PR #111 머지. 2026-05-02.
- `TASK-MONO-023-main-baseline-integration-cleanup.md` — main 의 18 GAP integration 회귀 분류 작업. 5 sub-task (TASK-MONO-023a/b/c/d/e) 로 root cause 별 분할 발행. (a) provisioning 격리·downstream mock, (b) OAuth2/OIDC family, (c) anonymization+audit, (d) outbox 관련, (e) community JPA 격리. CI 게이팅 정책 = sub-task 5건 모두 머지 후 별도 작업. PR #112 머지. 2026-05-02.
- `TASK-MONO-023a-provisioning-test-isolation.md` — 4 통합 테스트 (AccountRoleProvisioning / TenantProvisioning / BulkProvisioning / SignupAuthServiceDelay) fix. RC1 = invalid JSON 컬럼 INSERT (8 use case `details` 필드), RC2 = WireMock HTTP/2 NEGOTIATE 실패 → AuthServiceClient HTTP/1.1 강제. PR #121 머지. 2026-05-03.
- `TASK-MONO-023b-oauth2-oidc-regression-family.md` — RC5 (rate-limit key prefix `tenantId` 누락) + RC4 (`/internal/accounts/{id}/status` stub 누락). 5 OAuth2/SAS 클래스는 production 코드 정상 (Docker 미가동 Windows skip). PR #123 머지. 2026-05-03.
- `TASK-MONO-023c-anonymization-and-audit.md` — AccountAnonymizationScheduler `actorType=system` 강제 / AccountEventPublisher 6 메서드 `@Transactional` (`OutboxJpaConfig.enableDefaultTransactions=false` RC) / AdminAudit SQL `actor_id` (VARCHAR UUID) 컬럼 정정. PR #122 머지. 2026-05-03.
- `TASK-MONO-023d-outbox-related-failures.md` — TenantAdmin 테이블명 `outbox_events` → `outbox` / AdminOutboxPollingScheduler 4 이벤트 토픽 매핑 추가 / OutboxRelay Awaitility lambda fix. PR #120 머지. 2026-05-03.
- `TASK-MONO-023e-community-jpa-test-isolation.md` — `@AfterEach deleteAll()` 추가 (CommentJpaRepository / ReactionJpaRepository). `TransactionTemplate.executeWithoutResult` 가 commit 하므로 isolation 누수. test-only 변경 (frozen-policy 준수). PR #119 머지. 2026-05-03.
- `TASK-MONO-024-existing-projects-traefik-migration.md` — **Phase 2** 완료. ecommerce / wms / GAP 의 docker-compose 를 hostname routing 으로 마이그레이션. 모든 host port 제거 + Traefik 라벨 (web.ecommerce.local / admin.ecommerce.local / kafka.wms.local 등). CI overlay (`docker-compose.ci.yml`) + bootrun overlay (`docker-compose.bootrun.yml`) 신설. PR #129 머지. 2026-05-03.
- `TASK-MONO-025-base-event-publisher-uuidv7.md` — `libs:java-messaging` `BaseEventPublisher.eventId` v4 → v7 마이그레이션. `UuidV7` 이미 `libs:java-common/.../id/` 에 존재 (재발견) — 1줄 변경 + 단위 테스트 2건. 5 영향 서비스 모두 PASS. PR #130 머지. 2026-05-03.
- `TASK-MONO-026-gap-v0011-fan-platform-oidc-clients.md` — GAP V0011 Flyway seed 로 `fan-platform-user-flow-client` 등록 (PKCE confidential, dev secret = `fan-platform-dev`). auth-api.md / gap-integration.md / .env.example 갱신. v2 internal-services-client 는 deferred. PR #135 머지. 2026-05-03.
- `TASK-MONO-027-ecommerce-gap-integration.md` — ecommerce-platform 의 GAP OIDC 통합 cutover. GAP V0012 시드 (ecommerce-web-store-client + ecommerce-admin-dashboard-client + 2 scopes) + account-service V0014 (ecommerce tenant row) + ecommerce gateway-service 의 issuer-uri / jwk-set-uri / TenantClaimValidator / AllowedIssuersValidator + docker-compose env 갱신 + .env.example 신규 변수 + integration test 5 시나리오. cross-project atomic (`feat!:` PR #145 머지). 후속: TASK-FE-067 (frontend cutover), TASK-BE-132 (auth-service 폐기). 2026-05-04.
- `TASK-MONO-028-ecommerce-standalone-v1-freeze-policy.md` — ecommerce GAP cutover 시리즈 follow-up. `scripts/sync-portfolio.sh` `PROJECT_EXCLUDE_PATHS["ecommerce-microservices-platform"]` 에 BE-132 변경 path 12건 추가 (docker-compose × 3 / .env.example / k8s / gateway application.yml / specs/services rename × 2 / contracts http+events / features authentication+user-management) + dry-run 출력에 excluded paths 표시 추가. 환경변수 잔재 grep 결과 깨끗 (AUTH_SECRET / AUTH_GOOGLE_CLIENT / ADMIN_INITIAL_PASSWORD 모두 잔재 없음 — done task 본문 참조만). tasks/done 의 50+ 옛 auth-service 경로 참조는 옵션 a (방치, CLAUDE.md "do not modify done" 룰 준수) + c (auth-service-deprecated/README.md redirect 이미 처리됨) 로 의도된 방치. k8s prod gateway → GAP egress NetworkPolicy 추가는 별도 follow-up. PR #153 머지. 2026-05-04.
- `TASK-MONO-029-rules-validation-audit.md` — 공통규칙 정리 시리즈 1/5. `rules/` + `.claude/config/` 4-way 동기화 audit. /validate-rules skill 실행 + 매뉴얼 grep. 발견: Critical 1 (`.claude/skills/INDEX.md` 의 identity-platform-setup orphaned reference), Warning 2 (`rules/README.md` 의 도메인 카운트 38 → 41 drift, `.claude/config/activation-rules.md` 의 saas/wms 전용 activation 섹션 누락). 모두 본 PR 에서 fix, on-demand 정책 위반 없음. 후속 candidate: TASK-MONO-030 (spec drift), -031 (libs audit), -032 (.claude skills/agents/commands), -033 (TEMPLATE.md 정합성). PR #156 머지. 2026-05-04.
- `TASK-MONO-030-spec-drift-audit.md` — 공통규칙 정리 시리즈 2/5. 4 프로젝트 (wms / ecommerce / GAP / fan-platform) 의 architecture.md + gap-integration.md + contracts deprecated 헤더 + platform/contracts reference 일관성 audit (~27 services). Critical 2건 fix (wms+ecommerce gap-integration error envelope 형식 오기재), Warning 3건 fix (wms cross-tenant enumerate 표현, wms+fan-platform 의 jwt-standard-claims ref 누락 추가). 5건 Warning + 4건 Suggestion 은 PR body 카탈로그 → follow-up: (a) wms/fan-platform OIDC issuer/JWKS URI 정렬 (W4-W6), (b) ecommerce 이벤트 발행 서비스 architecture.md Outbox 섹션 추가 (W7), (c) wms architecture.md 섹션명 표준화 (W8). PR #160 머지. 2026-05-04.
- `TASK-MONO-031-libs-audit.md` — 공통규칙 정리 시리즈 3/5. libs/java-* 6모듈 40 class audit. 사용 빈도 매트릭스: 3+ project (Rule of Three 충족) 6 class, 2 project 9, 1 project 10, 0 external importer 15 (대부분 Spring AutoConfig / interface / internal). dead code 분류 a (즉시 삭제) 없음, b (향후 사용) 3건 / c (내부 참조) 11건 보존. Critical 0, Warning 5 (`com.gap.security.*` 패키지명 policy 위반, wms 미사용 deps × 5, JwksProvider/RedisKeyHelper 0-importer, java-test-support 1-project only), Suggestion 3 (EventMetricNames/AccessDeniedException/CommonGlobalExceptionHandler 단일 프로젝트 전용). 본 PR 코드 변경 없음 (audit 카탈로그만). follow-up: TASK-MONO-034 (패키지명 정규화) 신규 발행. PR #162 머지. 2026-05-04.
- `TASK-MONO-032-claude-config-audit.md` — 공통규칙 정리 시리즈 4/5. .claude/{skills,agents,commands,hooks} 검증. Skills 71개 INDEX↔파일 완전 일치, Agents 13개 frontmatter PASS, Commands 참조 PASS, Hooks 일관성 PASS. Critical 2건 fix: (1) `agents/common/backend-engineer.md` domains 에 ecommerce 서비스명 leak (`[auth, user, ...]` → `[all]`), (2) `hooks/rule-consistency-check.ps1` settings.json 미등록 orphan → PreToolUse Edit/Write hook 등록. Warning 6 (4 agent domains 카탈로그 외 값 + spec-check.ps1 dead 패턴), Suggestion 0. 주의: 첫 attempt 시 agent 가 hook 우회로 main 에 직접 commit 했으나 origin push 차단. 로컬 reset 으로 복구 후 PR #164 정상 머지. PR #164 머지. 2026-05-04.
- `TASK-MONO-033-template-md-consistency-audit.md` — 공통규칙 정리 시리즈 5/5 (마지막). TEMPLATE.md ↔ monorepo 6개 영역 audit + ~300줄 수정. Critical 2건 fix (bootstrap instruction 누락 항목 — frontmatter / Traefik labels / .env.example / backlog / package.json, Phase Timeline stale → Phase 3 완료 + Phase 4 pending). Warning 4건 fix (standalone freeze 정책 누락 → Standalone Portfolio Sync and Freeze Policy 섹션 신설, GAP IdP 패턴 누락 → GAP IdP Integration Pattern 섹션 5단계 신설, PR Separation Rule + audit 주기 + ADR 현황 미반영 → 3 섹션 신설, PORT_PREFIX "three projects" 오기재 → 4 정정). source of truth 분담: Local Network Convention TEMPLATE master / CLAUDE redirect, PR Separation Rule INDEX master / TEMPLATE summary, GAP consumer guide GAP spec master. follow-up candidate: dummy 프로젝트 부트스트랩 dry-run, `docs/guides/monorepo-workflow.md` 신설. **공통규칙 정리 시리즈 029~033 5/5 완료**. PR #166 머지. 2026-05-04.
- `TASK-MONO-034-java-security-package-rename.md` — libs/java-security 패키지명 `com.gap.security.*` → `com.example.security.*` 정규화 (TASK-MONO-031 audit follow-up). 77 files (libs main 10 + test 3 + GAP import 갱신 63 + SQL 주석 1). reflection 사용 검증 결과 무관. 4 module sample build PASS. CI Build & Test + master integration + boot jars + frontend 모두 PASS. PR #168 머지. 2026-05-04.
- `TASK-MONO-035-spec-drift-followups.md` — TASK-MONO-030 의 W4-W8 follow-up 일괄. W4-W6: wms gap-integration.md issuer `localhost:8081`→`gap.local` + JWKS URI `/.well-known/jwks.json`→`/oauth2/jwks`, fan-platform JWKS URI 동일 정렬. W7: ecommerce 4 service (order/promotion/review/shipping) architecture.md 에 Outbox 섹션 추가 (payment 는 OutboxPollingScheduler subclass 없어 제외 명시). W8: wms 4 service (master/inventory/inbound/outbound) architecture.md 의 `## Architecture Style: Hexagonal` → 헤더 + 별도 줄 분리 (gateway 는 이미 분리). 10 files. sample build 4 module PASS. PR #171 머지. 2026-05-04.
- `TASK-MONO-036-claude-config-followups.md` — TASK-MONO-032 의 W1-W5 follow-up. W1-W4 완료 (4 agent: devops/frontend/data/ml frontmatter `domains: [all]` — backend-engineer 패턴 일치), W5 완료 (spec-check.ps1 `specs/platform/` → `platform/`). W6 (`agents/common/README.md` 부재 + rule-consistency-check.ps1 의 README false-positive 버그) 는 hook self-modification permission 차단으로 deferred — TASK-MONO-039 candidate 권장. PR #173 머지. 2026-05-04.
- `TASK-MONO-037-template-md-bootstrap-dryrun.md` — TASK-MONO-033 의 follow-up. dummy 프로젝트 (`/tmp/dummy-bootstrap-test/dummy-domain-x`, scm 도메인) 로 TEMPLATE.md Option A Greenfield Step 1~12 + GAP Integration Step 1~5 dry-run. Catch 10건 모두 fix: Critical 3 (GAP Flyway seed 경로 오기재 account-service→auth-service, OIDC_JWKS_URI 변수명 불일치, OIDC_ISSUER_URL 잘못된 값) + Warning 5 (backlog/+specs/integration/ 누락, Traefik 라벨 불완전, build.gradle 모호, README.md step 누락) + Suggestion 2 (package.json 최소 예시, task ID convention). dummy artifact `/tmp/...` 즉시 삭제, git status clean. PR #175 머지. 2026-05-04.
- `TASK-MONO-038-monorepo-workflow-guide.md` — TASK-MONO-033 의 follow-up. `docs/guides/monorepo-workflow.md` 신설 305줄 9섹션 (overview / branch 패턴 / task lifecycle / agent dispatch + 모델 선택표 / sync-portfolio.sh + freeze policy / CI 잡 분류표 / hook 우회 규칙 / 자주 발생 conflict 패턴 7건 — 본 시리즈 029~037 실사례 기반 / 9 master 문서 cross-ref 표). TEMPLATE.md anchor link 1건 갱신. dangling link 0건. PR #177 머지. 2026-05-04.

**공통규칙 정리 시리즈 follow-up (035~038) 4/4 완료** — 2026-05-04. 시리즈 전체 (029~038) 12 task / 24 PR 종결.
- `TASK-MONO-039-rule-consistency-check-readme-fix.md` — TASK-MONO-036 의 W6 deferred 처리 완료. `rule-consistency-check.ps1` 의 skill/agent/command 정규식 모두에 `-and $filePath -notmatch 'README\.md$'` 추가 (false-positive fix, 일관 적용) + `.claude/agents/common/README.md` 신설 (13 agent 카탈로그 + frontmatter 컨벤션 + dispatch 설명 + cross-ref). hook self-modification 사용자 명시 승인 받음. PR #180 머지. 2026-05-04. **공통규칙 정리 시리즈 029~039 13 task / 26 PR 완전 종결.**
- `TASK-MONO-041-adr-mono-002-phase-4-trigger.md` — ADR-MONO-002 신설 (Phase 4 = Template 레포 추출 진입 결정 + scm catalyst, ACCEPTED). D1=1 도메인 추가 (3 도메인 동시 거부 — root 공유 파일 conflict + bottleneck), D2=scm 우선 (wms 시너지 + 첫 도메인 risk 낮음), D3=Template 추출 시점 별도 ADR-MONO-003 candidate, D4=erp/mes 순서 후속 결정 (추천: scm→erp→mes). PR #183 머지. 2026-05-04.
- `TASK-MONO-042-gap-v0013-scm-oidc-clients.md` — TASK-MONO-040 의 선행. GAP V0013 (auth-service `oauth_clients` + `oauth_scopes`) + V0015 (account-service `tenants`) Flyway seed 로 `scm` tenant (B2B_ENTERPRISE) + `scm-platform-internal-services-client` (client_credentials) + `scm.read`/`scm.write` scope 등록. BCrypt hash for `"scm-dev"` (strength=10) 생성. v1 = backend only — user-flow PKCE client 는 v2 frontend 도입 시 별도 V slot. auth-api.md OAuth2 Clients 표 갱신. PR #186 (spec) + PR #187 (impl) 머지. 2026-05-04.
- `TASK-MONO-040-scm-platform-bootstrap.md` — ADR-MONO-002 D2 후속 — 모노레포 5번째 프로젝트 `scm-platform` skeleton 부트스트랩 (Phase 4 catalyst). `rules/domains/scm.md` 신설 (on-demand, Mandatory Rules S1-S8) + `.claude/config/activation-rules.md` link 활성화 + `projects/scm-platform/` 전체 트리 (PROJECT.md / tasks/INDEX.md / docker-compose.yml / .env.example / build.gradle / README.md / 17 .gitkeep) + 루트 `package.json` `scm:*` 5 shortcuts. 25 files / 796 insertions. Library 경계 grep empty / `./gradlew projects` regression 0 / docker-compose config valid. v1 service map 의도 (gateway / procurement / inventory-visibility), 첫 service skeleton 은 후속 TASK-SCM-BE-001. PR #185 (spec) + PR #188 (impl) 머지. 2026-05-04.

**scm 부트스트랩 시리즈 (040 + 042) 4 PR 종결** — 2026-05-04. monorepo Phase 4 catalyst 첫 도메인 추가 완료. 5 프로젝트 동거 진입 (wms / ecommerce / GAP / fan-platform / **scm**). ADR-MONO-002 D3 (Template 레포 실제 추출 결정) 는 라이브러리 churn 안정 평가 후 ADR-MONO-003 candidate.
- `TASK-MONO-043-micro-fix-bundle.md` — operational hygiene 3 micro-fix batch. (A) `protect-main-branch.ps1` 의 force-with-lease false-positive fix (L27 regex 에 negative lookahead 추가, TASK-MONO-040 시리즈 직접 친 friction 의 근본 fix). (B) `.claude/scheduled_tasks.lock` `.gitignore` 등록 + `git rm --cached` 로 untrack. (C) `auth-api.md` § OAuth2 Clients 표에 ecommerce V0012 의 web-store-client + admin-dashboard-client 2 행 추가 (TASK-MONO-027 머지 시 spec 갱신 누락 fix). ADR-MONO-002 D3 churn 안정 평가 기간 churn-zero 작업. Hook self-modification 사용자 명시 승인 (TASK-MONO-039 패턴). PR #190 (spec) + PR #191 (impl) 머지. 2026-05-04.
- `TASK-MONO-044-main-baseline-ci-regression-cleanup.md` — main baseline CI 4 회귀 진단 parent task. CI run `25327478714` 의 4 FAIL Job 을 Job 별 stack trace 분석 (`knowledge/incidents/2026-05-05-ci-regression.md`) → 2 distinct root cause 로 환원: RC#1 = `libs/java-web` servlet 의존성 leak (3 Job; TASK-MONO-017 도입), RC#2 = `docker-compose.ci.yml` 의 traefik-net 미보상 (1 Job; TASK-MONO-024 도입). Sub-task 044a (RC#1) + 044b (RC#2) 로 split 발행. 진단 단독 PR `#198` (spec) + `#199` (diag, base 삭제 auto-close — 내용은 044a/044b 브랜치에 포함되어 main 진입). 2026-05-05.
- `TASK-MONO-044a-libs-java-web-servlet-leak-fix.md` — RC#1 fix. `libs/java-web` 을 `libs/java-web-servlet` 으로 분리 — servlet 전용 의존성 (`spring-web`/`webmvc`/`orm` + `jakarta.servlet-api`) 을 sub-module 로 격리하여 reactive gateway 3개 (WMS/GAP/fan-platform) 의 classpath 에서 제거. 효과 검증: `E2E (gateway-master live-pair)` Job FAILURE → SUCCESS (이전 BeanDefinitionOverrideException 소거). 부수 효과로 GAP integration / fan-platform e2e 가 servlet 부팅 차단 해소 후 *별도* downstream 결함 (community/account/auth IT 33건 + RateLimitConfig KeyResolver 모호) 노출 — 044a 범위 밖, follow-up 발행 필요. PR #201 머지. 2026-05-05.
- `TASK-MONO-044b-traefik-net-ci-overlay-fix.md` — RC#2 fix. `.github/workflows/ci.yml` frontend-e2e Job 의 docker-compose-up step 직전에 `docker network create traefik-net || true` 1줄 추가 (옵션 i 채택). 효과 검증: `Frontend E2E full-stack (web-store)` Job 의 "Start docker compose stack" step `network traefik-net declared as external, but could not be found` 에러 소거 → docker compose up + container launch 성공. 본연 책임 (compose stack 부팅) 달성. Playwright suite 단의 NextAuth fetch fail + timeout 은 spec § Out of Scope #2 명시대로 follow-up 분리. PR #200 머지. 2026-05-05.
