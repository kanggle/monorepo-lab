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

# Implementation Record (2026-10-16 UTC)

## AC-0 — measured cause

Sources: job logs via `gh api repos/kanggle/monorepo-lab/actions/jobs/<id>/logs`,
`gh cache list --key 'gradle-home-v1|Linux-X64|e2e'`, `git diff --stat`.

1. **How the lanes restore Gradle state.** `_platform-e2e.yml` → composite
   `.github/actions/setup-java-gradle` → `gradle/actions/setup-gradle@v4` with defaults
   (`cache-read-only: false`, `cache-cleanup: on-success`). gradle-home key =
   `gradle-home-v1|Linux-X64|e2e[<hash>]-<sha>`, restored by prefix. The reusable job id is
   always `e2e`, so **the wms, fan and scm full lanes share one prefix**
   (`e2e[e9cc7a5f…]`); ci.yml's smoke lanes have another (`e2e[b8a35c2f…]`). Dependency
   jars live in a separate content-keyed `gradle-dependencies-v1-<hash>` entry; the
   resolution metadata (including Gradle's "not found" records) lives in gradle-home.
   setup-gradle **saves gradle-home even from a failed build** — only cache-cleanup is
   skipped (`Not performing cache-cleanup due to build failure`, then `Saved cache entry
   with key …-cf36d16…`).
   🔴 Reading aid: setup-gradle's `Restored cache entry with key X` prints the *requested*
   key; the entry actually restored is the one on the `Cache hit for restore-key: Y` line.
2. **The first failure was a live answer.** Run `38863691082` fan lane (job
   `116851552946`, 06:53:07Z): stack frame
   `ExternalResourceResolver$RemoteRepositoryAccess.resolveArtifact` → `notFound`, i.e.
   Gradle asked Maven Central and was told not found. The scm lane of the **same run**
   (job `116851552729`) restored the **same** entries (gradle-home from `baff0fd45`,
   `gradle-dependencies-v1-4c46…`) and compiled `procurement-service` successfully ten
   seconds later. ⇒ the cache state at `baff0fd45` was not bad; one request was.
3. **Every later failure was the cached record.** Fan saved gradle-home `…-cf36d16…` at
   06:53:16Z after failing. Run `38883805350` (both lanes, jobs `116916572250` /
   `116916572324`) restored exactly that entry and failed from frame
   `CachingModuleComponentRepository$LocateInCacheRepositoryAccess.resolveArtifactFromCache`
   — Gradle's "known missing" branch, which reports the attempted URL **without asking
   the repository again**. It re-saved the record as `…-2eef1d91…`. Run `38890453134`
   (`08e10cc37`) failed the same two lanes again. The wms full lane restored the same
   entry each time and stayed green because it never resolves that artifact.
4. **Why ci.yml stays green.** Its Build & Test job and its smoke `e2e` lanes use
   different gradle-home key hashes, so they never restored the record.
5. **What changed `baff0fd45..cf36d1609`.** No build, Gradle or workflow file
   (`git diff --stat` empty for `*.gradle*`, `.github`, `gradle`, `gradle.properties`).
6. ⚪ **Not measured: why Maven Central answered not found at 06:53:07Z.** The jar is
   served now; Gradle gives no status code at this log level. Recorded as a transient
   origin event, not explained.
7. ⚪ **Not measured: the record's own lifetime.** Gradle keeps missing-artifact records
   for a bounded time (believed 24 h by default), which would have let this heal by itself
   around 2026-10-17 06:53Z — not verified, and in any case silent.

⇒ The cause is not the cache key. A key bump would have cleared this instance and
re-armed the same trap (any lane can re-poison the shared prefix from a failed build).

## AC-1 — fix

`.github/actions/setup-java-gradle/gradlew-retry-not-found.sh` wraps both Gradle calls
in `_platform-e2e.yml` (the in-job boot-jar build and the e2e run). It runs Gradle once;
**only** if the output carries Gradle's `ArtifactNotFoundException` /
`ModuleVersionNotFoundException` it emits a `::warning::` naming the coordinate and which
origin it was (cached record vs live answer), writes a step-summary block, and retries once
with `--refresh-dependencies`. A success overwrites the record, so the gradle-home that job
saves is clean; a second failure emits `::error::` ("the repository says so now too").
Any other failure (compile error, failing tests) exits with its own code after one run. It
refuses to run without `--stacktrace`, because the class-name predicate needs it.

Local bite (fake `./gradlew`, six cases): cached-missing then OK → rc 0, 2 calls, two
warnings · compile error → rc 1, 1 call, no annotation · still missing after refresh →
rc 1, 2 calls, warning + error · success → rc 0, 1 call · no `--stacktrace` → rc 2, 0
calls · test failure rc 7 → rc 7, 1 call.

## AC-2 — branch proof

`gh workflow run nightly-e2e.yml --ref task/mono-nightly-gradle-jar` → run
**`38891217884`** (commit `e692bf7d3`): **15/15 jobs success**. 🔵 **It exercised the new
path** — the fix was not proven by a clean cache: both lanes restored main's poisoned
entry (`Cache hit for restore-key: …e2e[e9cc7a5f…]-08e10cc37…`), failed the first attempt
from `LocateInCacheRepositoryAccess.resolveArtifactFromCache`, logged the warning, and
recovered on `--refresh-dependencies`:

| lane | job | 1st attempt | retry | e2eFullTest | baseline (green run) |
|---|---|---|---|---|---|
| scm full | `116940919243` | FAILED 15s (cached record) | SUCCESSFUL 29s | 11 / 0 fail | 11 (`116851552729`) |
| fan full | `116940919199` | FAILED 24s (cached record) | SUCCESSFUL 31s | 4 / 0 fail | 4 (`116739394620`) |
| wms full | `116940918996` | SUCCESSFUL (no retry) | — | 2 / 0 fail | — |

The other 12 jobs (iam, ecommerce×wms, web-store / console full-stack, GAP logout, image
liveness, packaging, AMI generation, launcher freshness, deployed fan surface, red-nightly
issue) succeeded.

After this run, only the wording of the "recovered" warning and the script header
changed (they now say that saving a clean entry is not guaranteed; see ⚪ below). The
re-dispatch on that commit is recorded below.

⚪ **Not provable on a branch: that main saves a clean gradle-home.** On a non-default
branch setup-gradle is `cache-read-only: true` (job log line `cache-read-only: true`), so
this run saved nothing. On main the lanes race for one key; the wms lane restores the
record, never resolves that artifact, and so never refreshes it — if it saves first the
record survives and the next run retries again (green, with the warning). The remaining
check is the next main nightly after merge: it should be green and auto-close #4386;
whether it still shows the warning tells which lane won the save.

## AC-3 — guards

On the staged tree: `check-required-check-names.sh`, `check-index-queue-drift.sh`,
`check-task-id-collision.sh`, `check-walkthrough-ledger-drift.sh`,
`check-message-backticks.sh`, `check-outside-module-input-filter-coverage.sh`,
`check-libs-ci-coverage.sh`, `check-artifact-retention.sh`,
`check-claude-reference-integrity.sh` — all rc=0. No job renamed; nothing added or deleted
under `scripts/` (the wrapper sits under `.github/actions/setup-java-gradle/`).
