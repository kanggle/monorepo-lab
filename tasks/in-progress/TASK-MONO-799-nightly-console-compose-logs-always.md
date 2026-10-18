# TASK-MONO-799 — the nightly console e2e job keeps the compose server logs on every run, not only when it fails

- **Status**: in-progress
- **Type**: ci (evidence)
- **Created**: 2026-10-18 UTC
- **Scope class**: monorepo-level (`.github/workflows/nightly-e2e.yml`)
- **Analysis model**: Opus 5.5 / **Implementation (recommended)**: Sonnet

# Goal

Job `platform-console-e2e-fullstack` dumps `docker compose … logs --tail=500` to the job log
only under `if: failure()`. A run that is flaky but green (Playwright `retries: 2` covers the
failed attempt) therefore leaves **no server log at all**. `TASK-PC-FE-341` AC-0 (2026-10-18
UTC, 56 run-attempts) hit exactly this: for every flaky run the question «which leg / phase
made the overview document take ~10 s» was `⚪ cannot be judged from the log`, because the
`console_composition_leg` lines (and the new `console_ssr_phase` lines) existed only inside a
container that was torn down right after.

Keep the whole run's compose log in the existing uploaded artifact, on every run, so the next
nightlies can be read after the fact.

# Scope

- `.github/workflows/nightly-e2e.yml`, job `platform-console-e2e-fullstack` only: one new step,
  `if: always()`, **before** «Stop docker compose stack», writing
  `docker compose -f docker-compose.e2e.yml logs --timestamps --no-color` to
  `projects/platform-console/apps/console-web/test-results/compose-logs.txt` (inside the
  already-uploaded `…/test-results/` path of artifact `platform-console-playwright-report-nightly`).
- The existing failure-only stdout dump stays as is.
- This task file + `tasks/INDEX.md`.
- `projects/<name>/` impact: none from this task (the paired console-side instrumentation is
  `TASK-PC-FE-341`).
- Out of scope: any other job, `--tail` tuning of existing dumps, log retention changes.

# Acceptance Criteria

- [ ] **AC-1** — On a green run the artifact `platform-console-playwright-report-nightly`
  contains `test-results/compose-logs.txt` with the whole run's compose output (no `--tail`),
  timestamped.
- [ ] **AC-2** — The step is `if: always()`, sits before «Stop docker compose stack», and cannot
  turn a green job red (`continue-on-error: true`).
- [ ] **AC-3** — The file's size on a real run is recorded here (reasonable for a ~1 minute test
  phase plus stack boot; if it is large enough to threaten the artifact quota, narrow it by
  service instead of dropping it).
- [ ] **AC-4** — No other job changes; no job or step that is a required-check context is
  renamed; the root guards (`scripts/check-*.sh`) pass on the staged tree.

# Related Specs

- `.github/workflows/nightly-e2e.yml` (job `platform-console-e2e-fullstack`)
- `platform/git-workflow-policy.md` (merge verification — the PR is not self-merged)
- `projects/platform-console/tasks/in-progress/TASK-PC-FE-341-nightly-sample-visitor-overview-flaky.md`

# Related Contracts

None (CI only).

# Edge Cases

- `test-results/` is Playwright's `outputDir`: it is cleaned at run **start**, and the new step
  runs after the tests, so the file survives. The step does `mkdir -p` so a run whose Playwright
  step never created the directory still writes the file.
- A run where `docker compose … logs` itself fails (stack never came up) must still reach
  «Stop docker compose stack» and the upload — hence `continue-on-error`.
- Containers log secrets only if the services log them; this step adds no new content, but the
  artifact is retained 7 days like the rest of that path.

# Failure Scenarios

- The step fails and masks a real test failure as the job's red cause — mitigated by
  `continue-on-error: true` (the Playwright step still decides the job's result).
- The log is large enough to slow the upload or hit the quota — measured in AC-3 before relying on it.
- The step is placed after «Stop docker compose stack» — `logs` then returns nothing; the order
  is part of AC-2.
