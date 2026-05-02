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

- `TASK-MONO-023a-provisioning-test-isolation.md` — TASK-MONO-023 sub-task. 4 통합 테스트 (AccountRoleProvisioning / TenantProvisioning / BulkProvisioning / SignupAuthServiceDelay) 의 격리 + downstream mock 회귀 fix.
- `TASK-MONO-023b-oauth2-oidc-regression-family.md` — TASK-MONO-023 sub-task. 7 OAuth2/OIDC 통합 테스트 (OAuth2 4건 + OAuthLogin + AuthIntegration + RateLimit) 회귀 family fix. PR #107 SAS 도입 이후 발생한 401/REDIRECTION/503 회귀 분석.
- `TASK-MONO-023c-anonymization-and-audit.md` — TASK-MONO-023 sub-task. AccountAnonymizationScheduler (actorType=user vs system) + AccountEventPublisher (No EntityManager) + AdminAuditTenantScope (cross-tenant deny audit row 미구현) 3 회귀 fix.
- `TASK-MONO-023d-outbox-related-failures.md` — TASK-MONO-023 sub-task. TenantAdmin (outbox_events 테이블 누락 — Flyway) + OutboxRelay (outbox 행 미발행 — transaction commit timing) 2 회귀 fix.
- `TASK-MONO-023e-community-jpa-test-isolation.md` — TASK-MONO-023 sub-task. CommentJpaRepository / ReactionJpaRepository 격리 회귀 (count 누수 / unique constraint 우회) fix. community-service frozen 정책 예외 (회귀 fix 만).
- `TASK-MONO-024-existing-projects-traefik-migration.md` — **Phase 2**: 기존 3 프로젝트 (ecommerce / wms / GAP) 의 docker-compose 를 `${PORT_PREFIX}XXXX:YYYY` 패턴에서 `expose:` + Traefik 라벨로 일괄 마이그레이션. 전제: TASK-MONO-022 머지 완료. atomic cross-project commit. `frontend-e2e` CI 잡 수정 포함.

## in-progress

(empty)

## review

(empty — TASK-MONO-022 + TASK-MONO-023 reviewed and moved to done on 2026-05-02)

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
