# TASK-MONO-004 — ecommerce: tag-and-filter integration tests + extend root CI

## Goal

Apply strategy (A) recommended in TASK-MONO-003: separate ecommerce
unit-runnable tests from Testcontainers-backed integration tests using
class-level `@Tag("integration")` and a single `subprojects.test`
filter at the ecommerce-platform `build.gradle`. Then extend the root
CI Build & Test job to cover ecommerce backend unit tests so 229 tests
(200 unit + 29 slice) start gating PRs at root.

## Background

PR #58 (commit fee426d) deliberately scoped the root Build & Test job
to wms-platform + libs because ecommerce's regular `test` task ran
Testcontainers tests that the CI environment cannot provision.
TASK-MONO-003 confirmed:

- 276 ecommerce backend test classes / 12 services
- 200 unit + 29 slice (CI-runnable today, just gated out by scope)
- 47 Testcontainers (need Docker, deferred — separate CI job follow-up)
- 0 services use `@Tag` or custom `unitTest` source set
- ecommerce-platform `build.gradle` already has a `subprojects { ... }`
  block that applies java/jacoco/lombok/test config to all 12 apps —
  the natural single insertion point for the test filter.

wms uses class-level `@Tag("integration")` plus `test {
useJUnitPlatform { excludeTags 'integration' } }`. That convention is
imported here.

## Scope

**In scope:**

1. Add `@Tag("integration")` to all 47 ecommerce backend test classes
   that carry `@Testcontainers`, identified by grep over
   `projects/ecommerce-microservices-platform/apps/*/src/test/java/`.
   For each match, ensure the JUnit `@Tag` import is present.
2. Modify
   `projects/ecommerce-microservices-platform/build.gradle`'s
   `subprojects` block: change
   `test { useJUnitPlatform() ... }` to
   `test { useJUnitPlatform { excludeTags 'integration' } ... }`.
   Single point of change for all 12 apps.
3. Extend `.github/workflows/ci.yml` Build & Test job's gradle
   invocation: add the 12 ecommerce app `:test` tasks to the existing
   list. Comment in the YAML explains why each task is enumerated
   (matches the existing wms enumeration style).
4. Locally verify with at least one representative service's
   `:test` task (e.g. auth-service which has the most varied test
   types) that the unit + slice tests run and the Testcontainers
   tests are skipped with a clear log line.

**Out of scope:**

- Registering an `integrationTest` task per ecommerce service. The 47
  Testcontainers tests stay opt-in via the standard `test` task with
  `--info`-level filter logs; running them in CI requires either a
  new `Integration (ecommerce, ...)` job or self-hosted runners,
  which is a separate task.
- Touching wms test discipline (already correct).
- Adjusting `.github/workflows/ci.yml`'s Integration / Boot jars /
  E2E jobs (still wms-only, which is correct).

## Acceptance Criteria

1. All 47 Testcontainers test classes carry `@Tag("integration")`
   together with the JUnit `org.junit.jupiter.api.Tag` import.
2. `projects/ecommerce-microservices-platform/build.gradle` `test {
   ... }` block uses `excludeTags 'integration'`.
3. `.github/workflows/ci.yml` Build & Test job runs the 12 ecommerce
   `:projects:ecommerce-microservices-platform:apps:<service>:test`
   tasks in addition to the current libs + wms-platform list.
4. Local `./gradlew :projects:ecommerce-microservices-platform:apps:auth-service:test`
   completes successfully with 45 of the 55 test classes executed
   (10 Testcontainers excluded by tag).
5. Local `./gradlew :projects:ecommerce-microservices-platform:apps:order-service:test`
   completes successfully with 36 of the 46 test classes executed
   (10 Testcontainers excluded by tag).
6. CI Build & Test job is green on the PR.

## Related Specs

- `tasks/done/TASK-MONO-003-ecommerce-test-landscape.md` — inventory
  and strategy A recommendation (this task implements it).
- `projects/wms-platform/apps/master-service/build.gradle` lines 72-99
  — pattern reference (per-service variant; we centralise here).
- `.github/workflows/ci.yml` — Build & Test job (currently libs +
  wms-platform).

## Related Contracts

None.

## Edge Cases

- A test class might already extend a base class that itself carries
  test-level annotations. `@Tag` can stack on subclasses without
  conflict, so adding it directly to the test class is safe even when
  the base also tags. (TASK-MONO-003 inventory found 0 base-class
  tagging in ecommerce, so this is theoretical.)
- A class might carry `@Testcontainers` but only as a marker (no
  active container fields). It still loads testcontainers-junit, which
  needs Docker; tag it.
- A class might be named `*IntegrationTest.java` but use `@MockBean` +
  `@DataJpaTest` (slice, no Docker). Inventory categorised these as
  Slice, not Testcontainers — verify by searching for `@Testcontainers`
  literally, not by filename.
- The `subprojects { test { ... } }` change in ecommerce-platform's
  `build.gradle` only applies to direct subprojects of
  `:projects:ecommerce-microservices-platform`. It does NOT affect
  wms or libs. (Confirmed by inspecting the project tree.)

## Failure Scenarios

- If CI's Build & Test job times out after adding 229 tests, evaluate
  parallelism (`./gradlew --parallel`) or per-service job split. The
  inventory suggests these are mostly fast unit tests; a doubling of
  the current ~1-2 min budget is realistic, well under the 30-min
  cap.
- If any ecommerce service's `test` task discovers a previously
  hidden compile error or test failure (because that service's
  `test` was never invoked from root before), the failure is real
  and requires a fix in this PR — record in Outcome.

## Outcome

(To be filled in after implementation.)
