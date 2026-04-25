# TASK-MONO-003 — ecommerce backend test landscape discovery

## Goal

Characterize what ecommerce-microservices-platform's `test` task actually
contains, what each test class depends on at runtime (Docker /
Testcontainers / EmbeddedKafka / nothing), and what it would cost to
split unit tests from integration tests cleanly. Output a concrete
recommendation for a CI strategy that brings ecommerce backend under root
`./gradlew check` without forcing CI to provision Docker stacks for every
ecommerce service.

## Background

PR #58 (Phase 7 libs consolidation) inadvertently expanded root CI
`./gradlew check` to cover ecommerce subprojects when ecommerce switched
from composite-build to direct-include. The Build & Test job started
running ecommerce integration tests that need Docker (Postgres + Kafka +
Redis per service) and failed in 17 min. PR #58 fix (commit fee426d)
restored CI by explicitly enumerating the wms+libs task graph and
excluding ecommerce.

The wms baseline pattern: `@Tag("integration")` excludes integration
tests from the `unitTest` task (and conversely, the regular `test` task
runs only unit tests). ecommerce does NOT follow this discipline — its
`*IntegrationTest.java` classes carry `@Testcontainers` /
`@SpringBootTest` / `@EmbeddedKafka` directly, no `@Tag`, no separate
test source set, all under the standard `test` task.

This means the gap is not "add a CI step"; it is structural — ecommerce
needs a way to distinguish unit-runnable tests from integration tests
before any CI strategy can be designed.

## Scope

**In scope:**

1. Enumerate ecommerce backend services (12 apps in
   `projects/ecommerce-microservices-platform/apps/`), and for each:
   - Count `*Test.java` classes
   - Identify which have `@Testcontainers`, `@SpringBootTest`,
     `@EmbeddedKafka`, or other Spring infrastructure annotations
   - Identify which are pure unit (Mockito / JUnit only)
2. For each ecommerce service, determine if `build.gradle` defines a
   custom `integrationTest` source set, `unitTest` task, or any test
   filtering. (None expected, but verify.)
3. Tabulate the count of unit-runnable tests vs integration tests.
4. Estimate the cost of three CI strategies:
   - **(A) Tag-and-filter retrofit**: add `@Tag("integration")` to all
     ecommerce integration tests (one annotation per class) + a
     `unitTest` task in each ecommerce service `build.gradle` that
     excludes the integration tag. Mirrors wms.
   - **(B) Source-set split**: create `src/integrationTest/` folders,
     move integration test classes there, configure Gradle source set
     and task. More invasive but matches Spring Boot guides.
   - **(C) Provision Docker for ecommerce in CI**: keep tests as-is;
     extend `.github/workflows/ci.yml` to run a docker-compose
     environment before invoking `gradle test`. Ecommerce's existing
     `docker-compose.yml` already starts the needed services.
5. Produce a recommendation between A / B / C with reasoning.

**Out of scope:**

- Implementing the chosen strategy. That belongs to a follow-up
  TASK-MONO-004 once the recommendation is approved.
- Frontend (Next.js) test coverage. Tracked separately (memory
  follow-up #6, second half).
- Docker image build verification. Same.
- Any change to wms test discipline.

## Acceptance Criteria

1. A per-service inventory table is produced listing test class counts
   by category (unit / Testcontainers / EmbeddedKafka /
   SpringBootTest-only / other) for all 12 ecommerce backend services.
2. The build.gradle audit confirms (or refutes) the assumption that no
   ecommerce service has a custom `unitTest`/`integrationTest`
   separation.
3. A cost estimate (lines of change / number of files touched) is
   given for each of strategies A, B, C.
4. A recommendation is given with rationale, including which CI
   change(s) it would require in a follow-up task.
5. No code change to ecommerce sources or build.gradle in this task —
   it is read-only discovery.

## Related Specs

- `tasks/INDEX.md` § "When to Use Root vs Project Tasks"
- `projects/ecommerce-microservices-platform/docs/migration-notes.md`
  (PR #58 CI scope context)
- `projects/wms-platform/apps/master-service/build.gradle`
  (the existing pattern to compare against)

## Related Contracts

None.

## Edge Cases

- Some services may have zero test classes (skeleton apps); handle
  the empty case in the inventory table.
- A test class might use `@MockBean` heavily but still annotate
  `@SpringBootTest` — these load Spring context and can be slow on
  CI but do not need Docker. Distinguish "Spring context required" vs
  "infrastructure container required" in the categorization.
- `@DataJpaTest` / `@WebMvcTest` slice tests with embedded H2 or
  Mockito — count as unit-runnable, not integration.

## Failure Scenarios

- If the inventory reveals that the integration tests are evenly
  spread across 12 services (no clean cluster), strategy A becomes
  more expensive (12+ × N classes of annotation churn). Surface this
  in the recommendation.
- If any service has tests that are inherently mixed (one class with
  both unit-style and integration-style methods), strategy A's
  class-level `@Tag` is too coarse — note it as a per-method tag
  follow-up.

## Outcome (2026-04-26)

### Inventory

276 test classes across 12 backend services.

| Service | Total | Unit | Slice | Testcontainers | Notes |
|---|---|---|---|---|---|
| auth-service | 55 | 39 | 6 | 10 | Redis 통합 다수 |
| batch-worker | 9 | 8 | 0 | 1 | |
| gateway-service | 7 | 6 | 0 | 1 | |
| notification-service | 19 | 16 | 2 | 1 | 거의 unit |
| order-service | 46 | 33 | 3 | 10 | Kafka + DB 의존 |
| payment-service | 20 | 16 | 2 | 2 | |
| product-service | 28 | 19 | 4 | 5 | |
| promotion-service | 14 | 8 | 2 | 4 | Docker 의존 비율 최고 (28%) |
| review-service | 13 | 9 | 2 | 2 | |
| search-service | 23 | 17 | 2 | 4 | Elasticsearch container |
| shipping-service | 9 | 7 | 1 | 1 | |
| user-service | 33 | 22 | 5 | 6 | |
| **Total** | **276** | **200 (73%)** | **29 (10%)** | **47 (17%)** | |

`SpringBoot-only`, `EmbeddedKafka-only`, `Mixed` columns are all 0.

### build.gradle audit
Zero of 12 services define `unitTest` / `integrationTest` source sets or
filter tasks. All test classes share the standard `test` task.

### `@Tag` audit
Zero classes carry `@Tag("integration")` or any `@Tag` value. The
distinction between unit-runnable and Testcontainers-required is
encoded only in class-level annotations (`@Testcontainers`,
`@SpringBootTest` with container fields).

### Cost estimates

| Strategy | Files touched | Approx. line delta | Risk |
|---|---|---|---|
| **(A) Tag-and-filter retrofit** | 47 test classes (one-line `@Tag("integration")` each) + 12 service `build.gradle` (small `unitTest` task block each) → **~59 files** | ~150 lines added | **Low** — additive only, no test logic changed, mirrors wms pattern |
| **(B) Source-set split** | 47 test classes moved to `src/integrationTest/java/...` (directory moves break IDE module config and require careful `dependencies { ... }` re-wiring) + 12 `build.gradle` source-set blocks (each ~25-30 lines) → **~59 files** | ~360 lines + 47 file moves | **Medium** — well-understood Gradle idiom but bigger blast radius. Each service grows a new source set. |
| **(C) Provision Docker for ecommerce in CI** | `.github/workflows/ci.yml` (one new step + container provisioning) → **1 file** | ~30 lines | **High** — full ecommerce docker-compose stack on a GitHub-hosted runner is 12+ services + Postgres × N + Kafka + Redis + Elasticsearch. Memory and timing budgets do not realistically fit. The same constraint that forced wms e2e past the 30-min timeout would apply to ecommerce backend tests, with worse multipliers. |

### Recommendation: Strategy (A) — tag-and-filter retrofit

Reasoning:

1. Smallest churn (≈59 files, single-line annotations + small build
   stubs). Lowest risk because it does not move any test code.
2. Matches wms's existing pattern, so the monorepo retains a single
   convention for separating unit-runnable from infrastructure-bound
   tests. New contributors learn one thing.
3. Unblocks 229 (200 unit + 29 slice) ecommerce tests for root CI
   immediately. The remaining 47 Testcontainers tests can be handled
   exactly like wms handles its master-service integration suite —
   a separate CI job that provisions Docker only when needed.
4. The 47 Testcontainers tests are unevenly distributed (auth + order
   carry 20 of them; promotion is highest at 28% but only 4 absolute
   classes), but the distribution does not block strategy A — every
   class can take the tag independently.

Implementation plan for the follow-up task (TASK-MONO-004):

| Service | Classes to tag | Build.gradle additions |
|---|---:|---|
| auth-service | 10 | `unitTest` task excluding `integration` tag |
| batch-worker | 1 | same |
| gateway-service | 1 | same |
| notification-service | 1 | same |
| order-service | 10 | same |
| payment-service | 2 | same |
| product-service | 5 | same |
| promotion-service | 4 | same |
| review-service | 2 | same |
| search-service | 4 | same |
| shipping-service | 1 | same |
| user-service | 6 | same |
| **Total** | **47** | **12** |

After TASK-MONO-004 lands the tags + tasks, a small follow-up
(TASK-MONO-005 or amended) extends `.github/workflows/ci.yml`
build-and-test job's gradle invocation list to include each ecommerce
service's `unitTest` task. The 47 Testcontainers tests stay deferred —
they would need either a new CI job (analogous to wms's
`integration-tests` job) or a self-hosted runner. That is a separate
question and out of TASK-MONO-004's scope.

### Notes / risks for TASK-MONO-004

- The 47 Testcontainers test classes need to be programmatically
  identified (grep `@Testcontainers` over `src/test/java/**`); doing it
  by hand in a 47-file PR would be error-prone. A simple shell loop
  during the implementation PR is sufficient.
- Each service's `build.gradle` needs the `unitTest` task to exclude
  the `integration` tag. There is no place to declare it once at the
  monorepo root — root `build.gradle` does not currently apply a
  `subprojects` block scoped to ecommerce only. Either (i) duplicate
  the task block in 12 `build.gradle` files (matches wms), or (ii)
  add a `subprojects` filter at the ecommerce-project level
  `build.gradle` (`projects/ecommerce-microservices-platform/build.gradle`)
  that applies the `unitTest` task to all `apps:*` subprojects. (ii)
  is cleaner but requires the project-level `build.gradle` to know
  about test conventions, which currently it does not. Pick at
  TASK-MONO-004 design time.
- The `slice` tests (29) are unit-runnable. They will continue to live
  on the regular `test` task and run in `unitTest`. No tagging needed.
