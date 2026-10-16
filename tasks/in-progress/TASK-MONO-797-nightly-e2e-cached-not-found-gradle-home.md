# TASK-MONO-797 — nightly e2e lanes stay red on a Gradle «not found» record carried in the shared gradle-home cache

- **Status**: in-progress
- **Type**: fix (CI)
- **Created**: 2026-10-16 UTC
- **Scope class**: monorepo-level (`.github/workflows/`)

# Goal

`main`'s `nightly-e2e.yml` went red at `cf36d1609` (run `38863691082`) and stayed red at
`2eef1d91f` (run `38883805350`, auto-issue #4386): the `E2E full (scm-platform v1
cross-service, Testcontainers) / e2e` and `E2E full (fan-platform v1 live-trio,
Testcontainers) / e2e` lanes fail at compile with
`Could not find resilience4j-annotations-2.2.0.jar (io.github.resilience4j:resilience4j-annotations:2.2.0)`,
while the jar is served (HTTP 200) on Maven Central and same-commit `ci.yml` is green.
No build file changed between the last green nightly (`baff0fd45`) and `cf36d1609`.

Measure why, then fix it where it starts, so that a single bad answer from the
repository cannot keep these lanes red on every later run without anyone seeing why.

# Scope

- `.github/workflows/_platform-e2e.yml` (the reusable e2e lane used by the wms / fan / scm
  full and smoke lanes).
- This task file + `tasks/INDEX.md`.
- `projects/<name>/` impact: **none** (no project code, build file or spec changes).
- Out of scope: Gradle version bumps, dependency changes, the other workflows' caches
  (recorded as ⚪ if relevant).

# Acceptance Criteria

- **AC-0 — measure the cause before changing anything.** From the failing job logs and
  the Actions cache list, record: how the lanes restore Gradle state (action, key,
  read/write mode, which jobs share the entry), why `ci.yml` resolves the jar and these
  lanes do not, what changed between `baff0fd45` and `cf36d1609`, and the code path
  Gradle took to report the jar as not found. If the evidence only supports a
  hypothesis, say so.
- **AC-1 — fix at the measured cause.** A cache-key bump alone is acceptable only if
  that is the cause, and even then the failure mode must become self-healing or
  detectable.
- **AC-2 — prove it on the branch.** `gh workflow run nightly-e2e.yml --ref
  task/mono-nightly-gradle-jar`: both lanes green from their logs, and the other nightly
  jobs do not regress. Record whether the run actually exercised the new path (a run that
  restores a clean cache proves nothing about the fix).
- **AC-3 — guards.** `scripts/check-required-check-names.sh` and the three required
  guards (index queue drift, task-id collision, walkthrough ledger drift) pass on a staged
  tree; no job is renamed.

# Related Specs

- `platform/git-workflow-policy.md` (merge verification — this PR is not self-merged).
- `platform/testing-strategy.md` (e2e lanes).

# Related Contracts

None (CI only).

# Edge Cases

- The bad record is in an entry shared by the wms / fan / scm full lanes (one key
  prefix), so one lane's bad answer reaches its siblings on the next run.
- A genuine dependency mistake (wrong coordinate) must still fail red, not be retried
  into green.
- Test failures in the e2e step must not trigger a second full test run.

# Failure Scenarios

- Fix retries everything → genuine compile/test failures double the lane's runtime and a
  flaky test can be retried into green. Mitigation: retry only on Gradle's not-found
  resolution exceptions.
- Branch run restores a cache without the bad record → the new path is never exercised
  and a green run is not proof. Record it as ⚪ in that case.
- The repository answers not-found again on the retry → the lane stays red, but now with
  an annotation that names the mechanism instead of a silent repeat.
