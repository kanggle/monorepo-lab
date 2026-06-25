# TASK-MONO-307 — Add an ecommerce Testcontainers integration CI lane (Phase 1: order-service + payment-service)

**Status:** ready

**Type:** TASK-MONO (monorepo-level — shared `.github/workflows/ci.yml`)
**Analysis model:** Opus 4.8 / **Recommended impl model:** Opus (CI topology + never-run-IT triage risk; not a mechanical config add)

> **Motivation / origin.** TASK-BE-435 (order auto-cancel + payment money-safe compensation) authored AC-2/AC-3 money-safety integration tests (both `OrderCancelled`↔`PaymentCompleted` orderings) + `OrderStuckRecoveryIT`. During that work it was confirmed that **ecommerce `@Tag("integration")` ITs run nowhere in automation**: ecommerce `:check` carries `excludeTags 'integration'` unless `-PrunIntegration` ([`projects/ecommerce-microservices-platform/build.gradle`](../../projects/ecommerce-microservices-platform/build.gradle) ~L42), **and** — unlike wms / iam / fan / scm / finance / erp / platform-console, which each have a dedicated `Integration (…, Testcontainers)` CI job running `:…:integrationTest` — there is **no ecommerce integration CI job at all**. Combined with the local Windows Testcontainers blocker ([`project_testcontainers_docker_desktop_blocker`](../../../README.md)), ecommerce ITs only ever **compile**; their Spring/Postgres/Kafka behaviour is never executed. This task gives them a real automation gate.

---

## Goal

Add an `Integration (ecommerce, Testcontainers)` job to [`.github/workflows/ci.yml`](../../.github/workflows/ci.yml), mirroring the existing sibling pattern (scm/finance/erp jobs at ci.yml ~L1091–1248), so ecommerce `@Tag("integration")` ITs execute on the Docker-capable Linux runner.

**Phased — start with the two BE-435 services.** ecommerce carries ~72 integration-tagged IT files across 13 services, **none of which has ever run in CI**, so a single all-services lane risks a large latent-failure cleanup and a big CI-time jump. Phase 1 scopes the lane to **order-service + payment-service** (the BE-435 money-safety ITs + their pre-existing ITs — ~18 IT files), proving the lane and closing the BE-435 gate. Later phases (this task's Follow-ups) expand service-by-service as each service's ITs are shown green.

## Scope

**In scope (Phase 1):**

1. **`.github/workflows/ci.yml`** — new job `ecommerce-integration-tests` (`name: Integration (ecommerce, Testcontainers)`), `runs-on: ubuntu-latest`, `needs: [changes, build-and-test]`, `timeout-minutes: 30`. Gate (mirror scm/finance):
   ```
   if: github.repository == 'kanggle/monorepo-lab' &&
       (github.event_name == 'push' ||
        needs.changes.outputs.libs == 'true' ||
        needs.changes.outputs.workflows == 'true' ||
        needs.changes.outputs.ecommerce == 'true')
   ```
   Steps mirror the sibling jobs: checkout → setup JDK 21 Temurin → setup Gradle → `docker info` (Testcontainers prerequisite) → run the ecommerce IT suite → upload reports on failure.
2. **Gradle invocation** — ecommerce has **no separate `integrationTest` task**; ITs are `@Tag("integration")` in `src/test`, re-included via `-PrunIntegration`. Run the integration-tagged tests for the two services, e.g.:
   ```
   ./gradlew \
     :projects:ecommerce-microservices-platform:apps:order-service:test \
     :projects:ecommerce-microservices-platform:apps:payment-service:test \
     -PrunIntegration --no-daemon --stacktrace
   ```
   (Confirm whether re-running `test -PrunIntegration` double-runs the unit tests already covered by `build-and-test`'s `:check`; if that is wasteful or causes ordering issues, scope with `--tests "*IT"`/`--tests "*IntegrationTest"` — but prefer running each service's whole tagged suite over cherry-picking individual classes, so the lane is a genuine gate.)
3. **Failure-report artifact upload** (`if: failure()`) for `projects/ecommerce-microservices-platform/apps/{order-service,payment-service}/build/reports/tests/` + `build/test-results/`.
4. **Confirm the `changes.outputs.ecommerce` filter** already exists (it does — ci.yml ~L132/L165) and the new job consumes it. No path-filter change needed for Phase 1.

**Out of scope (this task):**
- The other 11 ecommerce services' ITs (auth/product/user/promotion/search/shipping/settlement/review/notification/gateway/batch-worker) — phased follow-ups.
- Any change to ecommerce `build.gradle` IT gating mechanism (keep `-PrunIntegration`).
- nightly-e2e.yml (separate workflow; this is the PR-lane CI).
- Fixing product code — if an IT reveals a real product bug, that is a separate fix task referencing it.

## Acceptance Criteria

- **AC-1** — A new `Integration (ecommerce, Testcontainers)` job exists in `ci.yml`, gated on `ecommerce`/`libs`/`workflows`/push, `needs: [changes, build-and-test]`, mirroring the sibling jobs' shape (Docker verify + report upload).
- **AC-2** — The job runs the **order-service + payment-service** integration-tagged ITs on the Linux runner with Docker available (`-PrunIntegration`).
- **AC-3 (the real gate)** — The job is **GREEN on CI** — i.e. the BE-435 AC-2/AC-3 money-safety ITs + `OrderStuckRecoveryIT` + all other order/payment integration-tagged ITs actually pass on CI Linux. **This is verified by observing the CI run on the PR** (local execution is impossible per the Windows Docker blocker). If never-run ITs fail, triage: environmental fixes (container config, ports, waits) land here; genuine product bugs spin a separate fix task and the offending IT may be quarantined with a tracked TODO so the lane goes green without masking the bug.
- **AC-4** — `build-and-test` (the Docker-free `:check`) is unchanged and still green; the new lane is additive (does not replace the unit gate).
- **AC-5** — CI-time impact noted in the PR (the new job's wall-clock), and the YAML validates (no workflow syntax error — the `changes`/`build-and-test` jobs still schedule).

## Related Specs / References

- [`.github/workflows/ci.yml`](../../.github/workflows/ci.yml) — sibling integration jobs (scm ~L1091, finance ~L1143, erp ~L1195) are the template.
- [`projects/ecommerce-microservices-platform/build.gradle`](../../projects/ecommerce-microservices-platform/build.gradle) ~L35–43 — `-PrunIntegration` gating.
- [`platform/testing-strategy.md`](../../platform/testing-strategy.md) — Testcontainers-only IT policy; PR-fast-lane vs full distinction.
- `docs/adr/ADR-MONO-005` § 2.6 D6 + TASK-BE-435 (the ITs this lane gates).

## Related Contracts

- None (CI infrastructure).

## Dependencies / Prior Work

- **TASK-BE-435** (merged) — authored the order/payment money-safety ITs this lane is meant to gate.
- **TASK-MONO-048 / TASK-MONO-115** — the scm / finance integration-lane additions this job mirrors.
- CI path-filter discipline: [`project_ci_path_filter_074_075_quirk`] — pure-positive `code-changed` filter, AND-composed; the `ecommerce` output already follows it.

## Edge Cases

- **`-PrunIntegration` re-runs units** — running `:order-service:test -PrunIntegration` re-executes the unit tests too (already covered in `build-and-test`). Acceptable (small) or scope with `--tests "*IT"`; do not cherry-pick individual classes in a way that silently drops ITs.
- **Never-run ITs fail on CI Linux** — the whole point is they have never executed; expect possible first-run failures (container waits, fixed ports, `@BeforeAll` container pitfalls, Kafka topic timing). These are triaged in AC-3. Budget for it — this is not guaranteed to be a one-shot green.
- **CI minutes** — Testcontainers boot for two services adds wall-clock; the job runs only on ecommerce/libs/workflows changes (gated), so it does not tax unrelated PRs.

## Failure Scenarios

- **F1 — lane added but never actually green** — merging a lane that is allowed to fail (e.g. `continue-on-error`) would be theatre. Do NOT use `continue-on-error`; the job must be a required, genuinely-green gate (AC-3). If an IT can't be made green this phase, quarantine it explicitly with a tracked follow-up rather than soft-failing the whole job.
- **F2 — masks a real bug** — quarantining a failing IT to go green could hide a product defect. Any quarantine must reference a new fix task and state what it suppresses (AC-3).
- **F3 — scope creep into all 13 services** — attempting the full ecommerce IT surface in one PR risks an unbounded cleanup. Phase 1 is deliberately two services; resist expanding mid-PR.
