# TASK-MONO-012 — extend CI with ecommerce boot-jar packaging verification

**Status**: done
**Completed**: 2026-04-26

## Goal

Add an `ecommerce-boot-jars` job to `.github/workflows/ci.yml` that runs
`:bootJar` on all 12 ecommerce backend services. Verify-only — no
artifact upload — because no downstream e2e or docker job currently
consumes the ecommerce jars.

## Background

The existing `boot-jars` job builds + uploads `master-service.jar` and
`gateway-service.jar`; those are consumed by the `e2e-tests` live-pair
job. The ecommerce side has had jar-level coverage missing entirely —
`:check` validates compile + unit tests but does not surface failures
that only appear at Spring Boot fat-jar packaging time:

- Spring Boot autoconfiguration wiring conflicts
- Fat-jar classpath collisions (duplicate classes, MANIFEST merge)
- Missing runtime-scope dependencies (e.g. wrong `provided` scope)
- Spring Boot Layered Jar (`BOOT-INF/`) layout breakage

Asymmetric coverage matters more for ecommerce because the surface area
is larger (12 services vs 2 wms boot jars).

## Scope

**In scope:**

1. New `ecommerce-boot-jars` job in `.github/workflows/ci.yml`:
   - `needs: build-and-test` (runs only after compile+test passes)
   - Builds `:bootJar` for all 12 ecommerce backend services in a single
     gradle invocation
   - No `actions/upload-artifact` step — verify-only

**Out of scope:**

- Uploading ecommerce jars (no consumer yet — add later if a docker
  image build or ecommerce e2e job lands).
- Adding `bootJar` for the `web-store` / `admin-dashboard` apps (those
  are Next.js frontends, not Spring Boot services).
- Replacing the existing `boot-jars` job — keep wms separate so its
  artifact upload stays scoped to e2e consumption.

## Acceptance Criteria

1. `.github/workflows/ci.yml` contains an `ecommerce-boot-jars` job
   listing all 12 services' `:bootJar` task paths.
2. The job has `needs: build-and-test` and no `upload-artifact` step.
3. CI run on the implementing PR shows `ecommerce-boot-jars` green.
4. Existing jobs (`build-and-test`, `boot-jars`, `frontend-*`,
   `integration-tests`, `e2e-tests`) are unchanged in behavior.

## Related Specs

None (CI plumbing only).

## Related Contracts

None.

## Edge Cases

- **Job runtime**: 12 sequential bootJar tasks ≈ 3-5 min on a
  GitHub-hosted runner with warm Gradle cache. Well under the 15-min
  cap.
- **Parallel with `boot-jars`**: both ecommerce-boot-jars and
  boot-jars depend on `build-and-test` only, so they run in parallel.
  Wall-clock CI time does not regress.
- **Dependency cycle / resolution**: gradle resolves the libs/* graph
  identically to `:check` and `boot-jars`, so any classpath issue
  surfaces in this job at jar-build time, not at runtime.

## Failure Scenarios

- **One service fails to package**: triage that service's
  build.gradle (Spring Boot autoconfig conflict, missing runtime dep).
  Fix in a focused follow-up; do not roll back the entire ecommerce
  enumeration.
- **Job timeout**: unlikely (~5 min nominal vs 15 min cap), but if it
  happens, split into two jobs (e.g., 6 services each) or move heavy
  services to a separate job. Document the resolution in the Outcome
  section.

## Outcome (2026-04-26)

`.github/workflows/ci.yml` extended with an `ecommerce-boot-jars` job
listing all 12 ecommerce backend services
(auth/batch-worker/gateway/notification/order/payment/product/
promotion/review/search/shipping/user). Job has `needs: build-and-test`
and no upload step — verify-only.

Local sanity:
`./gradlew :projects:ecommerce-microservices-platform:apps:auth-service:bootJar`
produces `auth-service.jar` cleanly. CI on the implementing PR exercises
the cold path across all 12.

Acceptance criteria 1, 2 met by the diff. AC 3 / 4 verified once the PR's
CI run completes.
