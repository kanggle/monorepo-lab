# TASK-MONO-008 — extend root CI Build & Test to 9 passing ecommerce services

## Goal

Land the CI extension half of TASK-MONO-004 that was deliberately
deferred. After tag-and-filter plumbing is in place, the 9 ecommerce
services that already pass `:test` cleanly should be gated by root
`./gradlew check` so PRs touching them get caught at CI time. The 3
services that still fail (order, product, search) stay deferred until
their per-service follow-ups (TASK-MONO-005/006/007) land.

## Background

PR #58 fee426d explicitly enumerated the wms+libs task graph in the
build-and-test job because ecommerce's regular `test` task ran
Testcontainers tests the CI environment cannot provision. PR #70
(TASK-MONO-004) made the ecommerce default `test` task Docker-free
via `@Tag("integration")` + `excludeTags 'integration'`. 9 of 12
services pass cleanly under this filter today (auth, batch-worker,
gateway, notification, payment, promotion, review, shipping, user).
This task threads those 9 into the existing build-and-test gradle
invocation list.

The other 3 (order, product, search) have pre-existing failures
unrelated to plumbing — see TASK-MONO-004 Outcome and the
TASK-MONO-005/006/007 follow-ups. They join CI as their fixes land.

## Scope

**In scope:**

1. `.github/workflows/ci.yml` — append the following 9 task paths
   to the existing Build & Test gradle invocation:
   - `:projects:ecommerce-microservices-platform:apps:auth-service:test`
   - `:projects:ecommerce-microservices-platform:apps:batch-worker:test`
   - `:projects:ecommerce-microservices-platform:apps:gateway-service:test`
   - `:projects:ecommerce-microservices-platform:apps:notification-service:test`
   - `:projects:ecommerce-microservices-platform:apps:payment-service:test`
   - `:projects:ecommerce-microservices-platform:apps:promotion-service:test`
   - `:projects:ecommerce-microservices-platform:apps:review-service:test`
   - `:projects:ecommerce-microservices-platform:apps:shipping-service:test`
   - `:projects:ecommerce-microservices-platform:apps:user-service:test`
2. Update the step's comment block to call out the 3 deferred
   services and reference TASK-MONO-005/006/007 so the omission is
   discoverable.

**Out of scope:**

- order-service, product-service, search-service inclusion (gated
  by their respective fix tasks).
- The `Integration (master-service, Testcontainers)` job (still
  wms-only by design).
- A standalone "ecommerce integration" job — separate task once
  TASK-MONO-005/006/007 close out.

## Acceptance Criteria

1. `.github/workflows/ci.yml` Build & Test gradle invocation
   includes 9 ecommerce service `:test` tasks.
2. The step name reflects the new scope ("libs + wms-platform +
   ecommerce subset").
3. The comment block lists the 3 deferred services with task IDs.
4. CI Build & Test job is green on the PR.
5. CI Integration / Boot jars / E2E jobs are unchanged in behavior.

## Related Specs

- `tasks/done/TASK-MONO-004-...` — plumbing PR; sets the prerequisite.
- `tasks/done/TASK-MONO-003-...` — landscape inventory.

## Related Contracts

None.

## Edge Cases

- The Build & Test job already takes ~1.5 minutes today on wms+libs
  alone. Adding 9 ecommerce service `:test` tasks (mostly unit + slice,
  ≈229 unit-runnable test classes total per inventory) will likely
  push the runtime to ~3-4 min on a GitHub-hosted runner. Still well
  under the 30 min cap.
- If a future test in one of the 9 services starts to fail because of
  unrelated infrastructure flakiness, the failure should be triaged
  per service rather than rolled back as "the ecommerce extension was
  bad". The plumbing is sound; per-test issues are per-test.
- `./gradlew :projects:...:test` runs only the `test` task on the
  matching subproject — the `check` task aggregates more (e.g.,
  `jacocoTestReport`, lint). For consistency with the existing wms
  enumeration (which uses `:check`), this task uses `:check` too.

## Failure Scenarios

- **Job timeout**: Unlikely (still under 30 min cap), but if it
  happens, switch from sequential `--no-parallel` (default in CI
  step) to Gradle's default parallel mode, or split into two CI
  steps. Document the resolution in the Outcome section.
- **One of the 9 services regresses**: Roll back that service's task
  path from the gradle invocation as a hotfix and open a focused
  follow-up. Do not remove all 9 — that loses signal for the others.

## Outcome

(To be filled in after implementation.)
