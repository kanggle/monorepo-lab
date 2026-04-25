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

## Outcome

(To be filled in once the inventory is gathered.)
