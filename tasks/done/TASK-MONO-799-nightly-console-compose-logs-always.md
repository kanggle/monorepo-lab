# TASK-MONO-799 — the nightly console e2e job keeps the compose server logs on every run, not only when it fails

- **Status**: done
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

- [x] **AC-1** — On a green run the artifact `platform-console-playwright-report-nightly`
  contains `test-results/compose-logs.txt` with the whole run's compose output (no `--tail`),
  timestamped.
  - ✅ 2026-10-18 UTC — branch nightly run 39128709878 (console job green, 12 expected · 0 flaky):
    the downloaded artifact holds `test-results/compose-logs.txt`, every line prefixed with a
    docker timestamp; it carries 151 `console_ssr_phase` lines (TASK-PC-FE-341).
- [x] **AC-2** — The step is `if: always()`, sits before «Stop docker compose stack», and cannot
  turn a green job red (`continue-on-error: true`). — ✅ diff of `nightly-e2e.yml`: one new step
  «Save docker compose logs (every run)» directly before «Stop docker compose stack».
- [x] **AC-3** — The file's size on a real run is recorded here (reasonable for a ~1 minute test
  phase plus stack boot; if it is large enough to threaten the artifact quota, narrow it by
  service instead of dropping it). — ✅ run 39128709878: **812,430 bytes** (~0.8 MB, retention 7
  days) — no narrowing needed.
- [x] **AC-4** — No other job changes; no job or step that is a required-check context is
  renamed; the root guards (`scripts/check-*.sh`) pass on the staged tree. — ✅ the workflow diff
  touches only `platform-console-e2e-fullstack`; 36/38 guards rc=0 (2 need Docker /
  `DEMO_API_BASE`, untouched by this change); after the rebase the required 3 +
  `check-ls-files-guard-count` + `check-required-check-names` rc=0. PR #4410 checks 42 pass · 0 fail.

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

# 닫기 (2026-10-18 UTC) — in-progress → done

- (a) `gh pr view 4410`: `state=MERGED`, `mergedAt` 2026-10-18T13:11:28Z, `mergeCommit` `b74e05ab7`.
- (b) `b74e05ab7` 는 `origin/main` 의 tip 이다(닫기 시점).
- (c) 머지 직전 `gh pr checks 4410`: pass 42 · skipping 28 · fail 0 · pending 0. `statusCheckRollup` 도 SUCCESS 42 · SKIPPED 28 이다.
- (d) AC-1~AC-4 를 열어 확인했다. 전부 ✅ 이고, 증거는 위 각 AC 줄에 있다(브랜치 nightly 39128709878 · 812,430 B · 다른 잡 무변경).
- `review/` 단계는 거치지 않고 바로 `done/`: 구현 PR 은 머지 시점에 검토됐고, AC 는 머지 전에 모두 닫혔다.
