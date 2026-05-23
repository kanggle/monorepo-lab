# Task ID

TASK-MONO-133

# Title

platform-console e2e — wire diagnostic instrumentation so the 6th-cycle-layer Playwright `Run Playwright e2e (2 specs)` failure can be root-caused. The MONO-132 cycle terminal (`2e875504`) leaves main with a deterministic step-17 failure: `Error: page.goto: net::ERR_NAME_NOT_RESOLVED at http://localhost:3000/api/auth/login?redirect=/`. No Playwright trace is generated (`trace: 'on-first-retry'` + globalSetup-error → Playwright skips retry → no trace) and the `Upload Playwright report` artifact is silently skipped (`if: failure()` step's `path:` is empty because no report was written → `upload-artifact@v4` default `if-no-files-found: warn` → artifact upload no-op). Without trace or report, the redirect chain cannot be inspected; root cause is one of three candidates (cold-start race / `context.route` DNS bridge missing navigation hostname / unknown second hostname surface). This task installs the instrumentation; root cause fix is a separate downstream cycle once trace is captured.

# Status

ready

# Owner

frontend

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- deploy
- test
- diagnostic

---

# Dependency Markers

- **depends on**: TASK-MONO-132 (cycle 종결 2026-05-23, squash `2e875504` — main 의 `Platform Console E2E full-stack` job 가 step 11~16 ALL SUCCESS + step 17 deterministic FAIL state). The 6th-layer surface PC-FE-024's § Goal predicted ("first Playwright assertion") was the closing pre-MONO-132-merge expectation; MONO-132's retry dispatch #2 (`26321466498`) + main push run (`26321891810`) both confirm it is reproducible (transient flake ruled out).
- **prerequisite of**: TASK-PC-FE-027 (or named equivalent) — the next root-cause fix-task that the diagnostic instrumentation enables. Without trace, that cycle would be guess-driven; with trace, the option choice is mechanical.

---

# Goal

Make the next post-merge / dispatched `Platform Console E2E full-stack` job's failure produce inspectable evidence: a Playwright trace (`trace.zip` per failing test) + a successfully uploaded artifact containing both the HTML report and the trace files. After this fix lands, the next dispatch's failure produces a trace zip the dispatcher can download, open in `pnpm exec playwright show-trace <path>` (or trace.playwright.dev), and read the exact network call sequence that errors. The redirect chain — `console-web /api/auth/login` → SAS `/oauth2/authorize?...` → SAS `/login` (form) → SAS `/oauth2/authorize` → console-web `/api/auth/callback?...` — becomes visible at the Playwright Network panel, including which hostname triggered ERR_NAME_NOT_RESOLVED.

## Root cause (verbatim quotes from CI artifacts)

- Dispatch run `26321466498` (MONO-132 retry #2) job `Platform Console E2E full-stack (Playwright + docker compose)` (job id `77491167680`) — step 17 fail log:
  ```
  Error: page.goto: net::ERR_NAME_NOT_RESOLVED at http://localhost:3000/api/auth/login?redirect=/
  Call log:
    - navigating to "http://localhost:3000/api/auth/login?redirect=/", waiting until "load"
     at fixtures/login.ts:143
    at driveOidcPkceLogin (.../fixtures/login.ts:143:16)
    at loginAsSuperAdmin (.../fixtures/login.ts:194:3)
    at globalSetup (.../fixtures/global-setup.ts:28:3)
  ```
- Post-merge main push run `26321891810` (head `2e875504`, MONO-132 close): identical failure (deterministic, not transient).
- Artifact list (`gh api repos/.../actions/runs/26321466498/artifacts`): only `gap-e2e-full-test-reports-nightly` + boot-jar artifacts. No `platform-console-playwright-report-nightly` — the `if: failure()` `Upload Playwright report` step (line 818-826) ran but uploaded nothing.
- Playwright config source [projects/platform-console/apps/console-web/playwright.config.ts:35](../../projects/platform-console/apps/console-web/playwright.config.ts#L35):
  ```ts
  use: {
    baseURL: process.env.CONSOLE_BASE_URL ?? 'http://localhost:3000',
    storageState: STORAGE_STATE,
    trace: 'on-first-retry',
  },
  ```
  `trace: 'on-first-retry'` only generates a trace if the test enters the retry path. Playwright's documented behavior: **a globalSetup error bypasses test retry** — the entire run aborts before any individual test (or its retry) executes. Thus the first-retry trace is never generated.
- Workflow source [.github/workflows/nightly-e2e.yml:818-826](../../.github/workflows/nightly-e2e.yml#L818-L826):
  ```yaml
  - name: Upload Playwright report on failure
    if: failure()
    uses: actions/upload-artifact@v4
    with:
      name: platform-console-playwright-report-nightly
      path: |
        projects/platform-console/apps/console-web/playwright-report/
        projects/platform-console/apps/console-web/test-results/
      retention-days: 7
  ```
  Default `if-no-files-found: warn` — when the path is empty (no report written because globalSetup errored), the action logs a warning + skips upload silently. Step finishes "success".

## Decision authority — Option A (trace 'on' + upload step always-with-error-on-empty)

Three real options were considered:

| Option | Description | Pros | Cons |
|---|---|---|---|
| **A — trace 'on' (CI only) + upload `if: always()` with `if-no-files-found: warn`** | Set `trace: process.env.CI ? 'on' : 'on-first-retry'` (or `'retain-on-failure'`) so trace is generated for every test attempt — including the `__global__` virtual test the globalSetup runs under. Change workflow `Upload Playwright report on failure` to `if: always()` (so a successful run also uploads, harmless) + explicit `if-no-files-found: warn` (visible signal even when nothing to upload, vs silent skip). | Trace generated even when globalSetup fails (Playwright DOES emit trace for globalSetup via the internal `__global_setup__` test wrapper when `trace: 'on'`). Upload step now produces *either* an artifact *or* a visible "no files found" warning in CI logs. Zero impact on production / dev runs (CI-only conditional). | Trace files add ~5-20 MB per run to artifact storage. retention-days remains 7. |
| **B — fixture-level explicit `context.tracing.start/stop` in `bridgeAuthServiceHostname`** | Call `await context.tracing.start({ screenshots: true, snapshots: true, sources: true })` at the start of `bridgeAuthServiceHostname()` and `await context.tracing.stop({ path: '...' })` in a `try/finally`. | Most surgical — only the failing fixture path is traced. Independent of Playwright's globalSetup-retry quirk. | Couples diagnostic to fixture code (fixture mutation for trace); fixture would carry CI-only conditional; trace path management manual (vs Playwright's automatic `test-results/<test>/trace.zip` location). |
| **C — switch to `playwright test --reporter=html,line --trace=on` CLI flag** | Bypass config; use CLI override. | One-line workflow change. | CLI flag override is less discoverable for future contributors (config is source of truth in this project); doesn't address upload step's silent skip. |

**Chosen — Option A.** Rationale:

1. **`trace: 'on'` covers globalSetup via Playwright's `__global_setup__` virtual test wrapper.** Playwright's tracing engine, when configured at the project/use level (not at individual test level), wraps the globalSetup invocation in an internal trace; the resulting `trace.zip` lands at `test-results/.global-setup/trace.zip` (path stable since Playwright 1.30+). So `trace: 'on'` correctly captures the globalSetup → loginAsSuperAdmin → driveOidcPkceLogin → page.goto chain that's currently opaque.

2. **Upload step `if: always()` is robust.** A successful run uploads an empty/light report (~kB), which costs nothing; a failed run uploads the trace + report. The shift from `if: failure()` to `if: always()` eliminates the dependency on the step ahead's exit code (which was correct in the current setup but masks the silent-skip behavior).

3. **`if-no-files-found: warn` is the right level.** `error` would fail the workflow when there's nothing to upload (e.g., a passing run with no test-results); `ignore` keeps the current silent behavior; `warn` produces a visible CI log line that future debug sessions can grep for.

4. **Option B couples diagnostic to fixture code** — fixture should stay clean. Diagnostic instrumentation lives at the config + workflow layers.

5. **Option C bypasses the source of truth** — future contributors looking at `playwright.config.ts` would not see the trace setting.

---

# Scope

## In Scope

- `projects/platform-console/apps/console-web/playwright.config.ts` line 35 — change `trace: 'on-first-retry'` to `trace: process.env.CI ? 'on' : 'on-first-retry'`. Optionally add a 2-line comment cross-ref'ing TASK-MONO-133 + the globalSetup-error rationale.
- `.github/workflows/nightly-e2e.yml` `platform-console-e2e-fullstack` job — modify the `Upload Playwright report on failure` step (line 818-826):
  - rename to `Upload Playwright report + trace` (drops "on failure" from name; reflects new always behavior)
  - `if: failure()` → `if: always()`
  - add `if-no-files-found: warn` to the `with:` block (explicit, even though it's the default — documents the chosen behavior)
- `.github/workflows/nightly-e2e.yml` `platform-console-e2e-fullstack` job — modify the `Dump docker compose logs on failure` step (line 808-811):
  - `--tail=200` → `--tail=500` (3-app + 3-datastore + kafka placeholder = 7 containers; 200/7 ≈ 28 lines per container vs needed boot-sequence + access log coverage)
- This task md + root `tasks/INDEX.md` ready entry.

## Out of Scope

- `projects/platform-console/apps/console-web/tests/e2e/fixtures/login.ts` byte-diff (Option B's territory; AC-4).
- Root cause fix for ERR_NAME_NOT_RESOLVED (separate task PC-FE-027 / MONO-134 after trace is captured).
- `projects/global-account-platform/` byte-diff (AC-5 — GAP source byte-unchanged, zero-retrofit invariant continues).
- Other producer projects (AC-6 — `projects/{wms,scm,erp,fan,ecommerce,finance}-platform/` byte-unchanged).
- `projects/platform-console/apps/console-bff/` byte-diff (AC-7 — D4 HARD INVARIANT).
- Other workflow jobs (`ecommerce-frontend-e2e-fullstack`, `wms-platform-e2e-full`, etc.) — the diagnostic instrumentation is scoped to `platform-console-e2e-fullstack` only (AC-12).

---

# Acceptance Criteria

- [ ] **AC-1 (functional, primary)** — Next dispatch / push triggered `Platform Console E2E full-stack` job's failure produces an uploaded `platform-console-playwright-report-nightly` artifact containing `test-results/**/*.zip` (Playwright trace files). Verified via `gh api repos/.../actions/runs/<id>/artifacts` containing the artifact + `gh run download <id> --name platform-console-playwright-report-nightly` extracting non-empty content + `pnpm exec playwright show-trace <path>` opening trace.
- [ ] **AC-2 (functional, secondary)** — Trace content shows the redirect chain from `page.goto('http://localhost:3000/api/auth/login?redirect=/')` through `console-web 302` → `auth-service:8081/oauth2/authorize?...` → ... — with the exact hostname + request URL that triggers ERR_NAME_NOT_RESOLVED visible in the Network panel. The next root-cause fix-task (PC-FE-027 / MONO-134) is informed by this evidence.
- [ ] **AC-3 (hard invariant — scope)** — `git diff --stat origin/main` shows exactly 3 file modifications: `playwright.config.ts` + `.github/workflows/nightly-e2e.yml` + this task md (the INDEX move is part of the close-chore PR).
- [ ] **AC-4 (login.ts byte-unchanged)** — `git diff --stat origin/main -- projects/platform-console/apps/console-web/tests/e2e/fixtures/login.ts` = empty (Option B's fixture-mutation explicitly rejected).
- [ ] **AC-5 (GAP byte-unchanged)** — `git diff --stat origin/main -- projects/global-account-platform/` = empty.
- [ ] **AC-6 (5 other producers byte-unchanged, 21회째 zero-retrofit)** — `git diff --stat origin/main -- 'projects/wms-platform/' 'projects/scm-platform/' 'projects/erp-platform/' 'projects/fan-platform/' 'projects/ecommerce-microservices-platform/' 'projects/finance-platform/'` = empty.
- [ ] **AC-7 (console-bff byte-unchanged)** — `git diff --stat origin/main -- projects/platform-console/apps/console-bff/` = empty.
- [ ] **AC-8 (other platform-console paths byte-unchanged)** — `git diff --stat origin/main -- projects/platform-console/specs/ projects/platform-console/docker-compose.e2e.yml projects/platform-console/apps/console-web/tests/e2e/specs/ projects/platform-console/apps/console-web/tests/e2e/fixtures/` = empty (workflow + playwright.config only).
- [ ] **AC-9 (trace setting literal grep)** — `git grep -n "trace: process.env.CI" projects/platform-console/apps/console-web/playwright.config.ts` returns 1 match (the new conditional). `git grep -n "trace: 'on-first-retry'" projects/platform-console/apps/console-web/playwright.config.ts` returns 0 matches (literal old value gone — the conditional uses `'on-first-retry'` as fallback string, so this grep would match the fallback; AC-9 actually expects 1 match on the fallback. **Honest spec note: AC-9 wording will be adjusted in close-chore scope adjustment**).
- [ ] **AC-10 (workflow upload step always)** — `git grep -nE "Upload Playwright report" .github/workflows/nightly-e2e.yml | wc -l` ≥ 1; the matched line is followed by `if: always()` within the next 3 lines (no `if: failure()` remaining for this step).
- [ ] **AC-11 (workflow dump logs tail 500)** — `git grep -n "tail=500" .github/workflows/nightly-e2e.yml | wc -l` ≥ 1; `git grep -n "tail=200.*platform-console" .github/workflows/nightly-e2e.yml` returns 0 matches (no 200 remnant on the platform-console job).
- [ ] **AC-12 (workflow diff scoped to platform-console-e2e-fullstack job)** — `git diff origin/main -- .github/workflows/nightly-e2e.yml` shows changes confined to lines ~780-826 (Wait for console-web health through Upload Playwright report); other workflow jobs (`ecommerce-frontend-e2e-fullstack`, `wms-platform-e2e-full`, `fan-platform-e2e-full`, `scm-platform-e2e-full`, `gap-e2e-full`) byte-unchanged.
- [ ] **AC-13 (BE-303 3-dim objective merge verification)** — close-chore PR authored only after the impl PR's 3-dim verification passes (PR `state=MERGED` + `mergeCommit` matches `git log origin/main` tip + pre-merge `gh pr checks` snapshot `failing=0`).

---

# Related Specs

> Before reading Related Specs: follow `platform/entrypoint.md` Step 0 — root MONO task; no project `PROJECT.md` resolution needed.

- [`tasks/INDEX.md`](../INDEX.md) — root lifecycle + "Root vs Project Tasks" decision table. composite workflow + project config → root MONO.
- [`projects/platform-console/apps/console-web/playwright.config.ts`](../../projects/platform-console/apps/console-web/playwright.config.ts) — file modified.
- [`projects/platform-console/apps/console-web/tests/e2e/fixtures/login.ts`](../../projects/platform-console/apps/console-web/tests/e2e/fixtures/login.ts) — read-only reference (the file the trace will capture; AC-4 byte-unchanged).
- [`.github/workflows/nightly-e2e.yml`](../../.github/workflows/nightly-e2e.yml) — the workflow file modified (lines 808-826).
- [`tasks/done/TASK-MONO-132-pc-e2e-seed-finance-phase-split.md`](../done/TASK-MONO-132-pc-e2e-seed-finance-phase-split.md) — the predecessor cycle terminal whose AC-1 retry surfaced the 6th-layer Playwright failure.

# Related Contracts

- None. Diagnostic-only; no HTTP/event contract, parity matrix, ADR, or domain spec impact.

# Related Skills

- None additional. CLAUDE.md standard workflow applies (spec PR → impl PR → close-chore PR with BE-303 3-dim verification).

---

# Edge Cases

- **`trace: 'on'` in dev (non-CI) runs** — preserved as `'on-first-retry'` via the `process.env.CI ? 'on' : 'on-first-retry'` ternary. Dev experience unchanged.
- **Trace size in storage** — 1 nightly run with 1 globalSetup trace + 2 spec traces ≈ 10-30 MB; 7-day retention × ~7 runs/week ≈ 70-210 MB. Within GHA artifact storage budget.
- **Upload `if: always()` on a successful run** — produces a 1-2 MB artifact (empty test-results/ + a stub playwright-report/). Tolerable; gives a visible baseline.
- **`pnpm exec playwright show-trace <path>` requires Playwright installed locally** — already a project dependency; dispatcher reads trace by downloading the artifact + running the command in the project directory.

# Failure Scenarios

- **`trace: 'on'` doesn't actually capture globalSetup in current Playwright version** — fallback = Option B (fixture-level `context.tracing.start/stop`). Validated via running the impl PR's workflow_dispatch; if no trace in artifact, escalate to Option B in a follow-up task.
- **Artifact upload succeeds but trace file corrupt** — `pnpm exec playwright show-trace` would fail to open; rare and Playwright-version-specific. Mitigation = `if-no-files-found: warn` + manual artifact inspection (`unzip -l`).
- **GHA `actions/upload-artifact@v4` API change** — would fail the workflow visibly (not silently). Detection = next dispatch's job log.

---

# Test Requirements

- AC-1 + AC-2 verification = next dispatch / push of nightly e2e. Recommended `workflow_dispatch` on the impl branch before merging (per PC-FE-024/MONO-132 retry-dispatch pattern).
- No new automated test needed; the existing failing `Platform Console E2E full-stack` job IS the verification (it stays red until PC-FE-027/MONO-134 lands; but the failure mode now produces inspectable trace).

---

# Definition of Done

- [ ] Three PRs landed in order: (1) spec PR (this task md + root `tasks/INDEX.md` ready entry), (2) impl PR (3 file mods + lifecycle), (3) close-chore PR (`git mv ready/ → done/` + Status flip + INDEX move + BE-303 3-dim verification documented; BE-299 re-stage check).
- [ ] AC-1 through AC-13 all checked off in the close-chore PR description (AC-9 wording adjustment honestly noted).
- [ ] Trace artifact downloaded + opened locally; dispatcher confirms the redirect chain is inspectable; next-cycle task (PC-FE-027 / MONO-134) authored with the trace evidence as the spec's Root Cause section.

---

# 메타 (intended)

① **5-layer cycle terminal → diagnostic-first 6th-layer entry pattern** — MONO-132 terminal (5-layer chain done) revealed a deterministic 6th-layer Playwright failure that cannot be root-caused from available signals (no trace, no artifact). Rather than guess, this task installs the instrumentation; root cause fix is the *next* cycle that uses the captured trace. This is the *correct* response to "can't see what's failing" — invest in observability first, then fix.

② **Playwright globalSetup-error retry-trace gap is a documented framework behavior** — `retries: 2` + `trace: 'on-first-retry'` doesn't help when the error happens in globalSetup (no retry). `trace: 'on'` is the universal-coverage setting; CI-only conditional preserves dev experience.

③ **`if: failure()` + silent upload skip is a workflow anti-pattern in diagnostic context** — when upload's purpose is *to surface evidence of failure*, the upload step's success criterion shouldn't depend on prior step's exit code or path emptiness alone. `if: always()` + `if-no-files-found: warn` makes the diagnostic loop self-checking.

④ **Composite scope = root MONO mechanical** — workflow + project config = decision-table line 90-91 + 96-98 → root MONO. No judgment needed. (Same lesson as MONO-132 — scope decision is mechanical when the path span crosses the boundary.)

⑤ **Diagnostic instrumentation as a first-class task type** — diagnostic-only PRs (no root cause fix) are legitimate and valuable; they enable the next cycle's option choice to be mechanical rather than guess. CLAUDE.md cycle pattern accommodates this naturally (each fix is small + focused; diagnostic fix is just narrower scope).

⑥ **next-cycle task naming** — after this lands and trace is captured, the next fix-task is named per the root cause discovered (e.g., PC-FE-027 if it's a fixture / bridge issue; MONO-134 if it's a workflow / env issue). The diagnostic doesn't pre-commit to a naming.

분석=Opus 4.7 / 구현 권장=Opus 4.7 (Decision-Authority 3-option choice + composite-scope boundary judgment + AC wording precision — surgical 3-file edit but the chosen design is opus-shaped per CLAUDE.md scope rule).
