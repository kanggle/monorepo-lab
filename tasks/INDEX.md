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

(empty)

## in-progress

(empty)

## review

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
