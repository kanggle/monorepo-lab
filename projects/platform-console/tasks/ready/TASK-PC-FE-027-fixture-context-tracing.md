# Task ID

TASK-PC-FE-027

# Title

console-web e2e fixture — wire fixture-level explicit `context.tracing.start/stop` in `bridgeAuthServiceHostname()` so the globalSetup-driven `driveOidcPkceLogin` chain produces a trace.zip on file system even when Playwright's reporter + test-results writer abort (TASK-MONO-133 AC-1 honest partial — Option A `trace: 'on'` config-level setting cannot reach the globalSetup virtual test wrapper; Option B was named in spec § Failure Scenarios as the escalation path). After this fix lands, the next dispatch's step-17 failure produces an inspectable `trace.zip` at a known path (uploaded by MONO-133's already-shipped `if: always()` upload step + the path's parent `test-results/` already in the workflow's upload `with.path:`). Once trace is captured, dispatcher inspects the redirect chain (Network panel) + decides root cause (cold-start race / `context.route` bridge missing navigation hostname / unknown second hostname) → next fix-task per evidence.

# Status

ready

# Owner

frontend

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- test
- diagnostic

---

# Dependency Markers

- **depends on**: TASK-MONO-133 (close `04e9fe49` — AC-1 honest partial; Option A trace 'on' inapplicable to globalSetup-error). MONO-133 secondary value (workflow `if: always()` upload + `if-no-files-found: warn` + `tail=500`) is the *infrastructure* this task's `trace.zip` will land in.
- **prerequisite of**: root-cause fix-task (named after trace evidence — likely PC-FE-028 if fixture/bridge issue, MONO-134 if workflow/env issue). Spec scope is *evidence capture only*; root cause fix is downstream.

---

# Goal

Make the next dispatch's `Run Playwright e2e (2 specs)` failure produce a `trace.zip` file in `test-results/` that MONO-133's `Upload Playwright report + trace` step uploads as the `platform-console-playwright-report-nightly` artifact. The artifact extracts to a non-empty directory; `pnpm exec playwright show-trace <path>` opens the trace; the Network panel shows every request from `page.goto('http://localhost:3000/api/auth/login?redirect=/')` through the entire redirect chain (console-web → SAS `/oauth2/authorize` → ... ) — including the exact hostname + status that triggers `ERR_NAME_NOT_RESOLVED`. The downstream root-cause fix-task can then write its spec § Root Cause with verbatim evidence rather than a hypothesis.

## Root cause of the diagnosis gap (verbatim quotes from CI artifacts)

- Dispatch run `26325249876` (TASK-MONO-133 impl branch workflow_dispatch, 2026-05-23T06:00:55Z) job `Platform Console E2E full-stack` step 20 `Upload Playwright report + trace` log:
  ```
  ##[warning]No files were found with the provided path: projects/platform-console/apps/console-web/playwright-report/
  projects/platform-console/apps/console-web/test-results/. No artifacts will be uploaded.
  ```
  → MONO-133's `if: always()` + `if-no-files-found: warn` rescue path (`Option A trace 'on'`) **did** fire — it didn't crash, it gracefully reported the empty-path condition. The warning is the evidence Option A was inapplicable: Playwright reporter / test-results writer aborts BEFORE booting when globalSetup throws (Playwright docs: globalSetup errors short-circuit the run; the test-results directory is never created).
- TASK-MONO-133 spec § Failure Scenarios verbatim:
  > **`trace: 'on'` doesn't actually capture globalSetup in current Playwright version** — fallback = Option B (fixture-level `context.tracing.start/stop`). Validated via running the impl PR's workflow_dispatch; if no trace in artifact, escalate to Option B in a follow-up task.
  
  This task IS that follow-up.
- TASK-MONO-133 spec § Decision authority **Option B** verbatim:
  > **B — fixture-level explicit `context.tracing.start/stop` in `bridgeAuthServiceHostname`** — Call `await context.tracing.start({ screenshots: true, snapshots: true, sources: true })` at the start of `bridgeAuthServiceHostname()` and `await context.tracing.stop({ path: '...' })` in a `try/finally`.
  
  Pros listed: "Most surgical — only the failing fixture path is traced. Independent of Playwright's globalSetup-retry quirk." Cons listed: "Couples diagnostic to fixture code (fixture mutation for trace); fixture would carry CI-only conditional; trace path management manual (vs Playwright's automatic test-results/<test>/trace.zip location)." MONO-133 chose A over B because the spec writing assumed Playwright globalSetup tracing API worked; AC-1 verify proved it doesn't. Option B's cons (fixture coupling + manual path) are accepted as the cost of getting evidence.
- Fixture source [projects/platform-console/apps/console-web/tests/e2e/fixtures/login.ts:111-126](../../apps/console-web/tests/e2e/fixtures/login.ts):
  ```ts
  async function bridgeAuthServiceHostname(
    context: BrowserContext,
  ): Promise<void> {
    const issuerUrl = new URL(DEFAULTS.oidcIssuerUrl);
    const localhostUrl = new URL(DEFAULTS.localhostAuthBaseUrl);
    const pattern = `${issuerUrl.protocol}//${issuerUrl.host}/**`;
    await context.route(pattern, async (route) => {
      const originalUrl = new URL(route.request().url());
      const rewritten = new URL(...);
      const response = await route.fetch({ url: rewritten.toString() });
      await route.fulfill({ response });
    });
  }
  ```
  Wrap the rest of `driveOidcPkceLogin` (lines 134-181) in a try/finally that starts tracing before `bridgeAuthServiceHostname` and stops it before `page.close()` in the finally. Stop path = `test-results/global-setup-driveOidcPkceLogin/trace.zip` (matches Playwright's documented test-results layout so MONO-133's upload path `test-results/` already covers it without additional workflow change).

## Decision authority — Option B (fixture-level explicit tracing)

Single option remains (Option A retired by AC-1 verify; Option C CLI flag was already rejected as bypassing source of truth).

- **What changes**: `projects/platform-console/apps/console-web/tests/e2e/fixtures/login.ts` — wrap `driveOidcPkceLogin` body in CI-conditional `context.tracing.start({...})` + `try { ... } finally { context.tracing.stop({ path }); page.close(); }`.
- **What does NOT change**: workflow file (MONO-133 already shipped `if: always()` upload + `test-results/` path); `playwright.config.ts` (MONO-133's `trace: 'on'` conditional stays — it covers spec-level tests for completeness, even though it didn't reach globalSetup); docker-compose; specs themselves.
- **CI-only conditional**: tracing wrap only in `process.env.CI` to avoid trace overhead in dev iteration. Same conditional pattern MONO-133's playwright.config uses.
- **Trace path naming**: `test-results/global-setup-driveOidcPkceLogin/trace.zip` (descriptive; matches Playwright's `test-results/<test-name>/trace.zip` convention so future inspect-via-show-trace path is predictable).

---

# Scope

## In Scope

- `projects/platform-console/apps/console-web/tests/e2e/fixtures/login.ts`:
  - Import: add `import { mkdir } from 'node:fs/promises'` + `import path from 'node:path'` at top.
  - `bridgeAuthServiceHostname()` (line 111-126) — no signature change; wrap routing registration in CI-conditional tracing start (place tracing start BEFORE the route registration so the route handler invocations are also captured).
  - `driveOidcPkceLogin()` (line 134-181) — wrap body in try/finally; finally calls `context.tracing.stop({ path: traceZipPath })` then `page.close()` (existing).
  - Top-of-file constant: `const TRACE_DIR = path.resolve(__dirname, '../../../test-results/global-setup-driveOidcPkceLogin')` + `const TRACE_PATH = path.join(TRACE_DIR, 'trace.zip')`.
  - Before tracing start: `await mkdir(TRACE_DIR, { recursive: true })` (defensive; mkdir is no-op if dir already exists).
- This task md + project `tasks/INDEX.md` ready entry.

## Out of Scope

- `playwright.config.ts` byte-diff (MONO-133's `trace: 'on'` conditional stays — it's harmless and covers the spec-level test path; AC-3).
- `.github/workflows/nightly-e2e.yml` byte-diff (MONO-133's `if: always()` + `test-results/` in path already covers this task's output; AC-4).
- `projects/global-account-platform/` byte-diff (AC-5 — GAP source byte-unchanged, 22회째 zero-retrofit).
- `projects/finance-platform/`, `projects/wms-platform/`, `projects/scm-platform/`, `projects/erp-platform/`, `projects/fan-platform/`, `projects/ecommerce-microservices-platform/` byte-diff (AC-6).
- `projects/platform-console/apps/console-bff/` byte-diff (AC-7 — D4 HARD INVARIANT).
- `projects/platform-console/docker-compose.e2e.yml`, `projects/platform-console/specs/`, spec test files byte-diff (AC-8 — login.ts only).
- Root cause fix for ERR_NAME_NOT_RESOLVED — separate downstream task once trace is captured.

---

# Acceptance Criteria

- [ ] **AC-1 (functional, primary)** — Next dispatch / push `Platform Console E2E full-stack` job's step-17 failure produces an uploaded `platform-console-playwright-report-nightly` artifact containing `test-results/global-setup-driveOidcPkceLogin/trace.zip` (non-zero size, openable). Verified via `gh api repos/.../actions/runs/<id>/artifacts` (artifact present) + `gh run download <id>` (extract) + `pnpm exec playwright show-trace <path>` (opens trace UI).
- [ ] **AC-2 (functional, evidence)** — Trace's Network panel shows every request from `page.goto('http://localhost:3000/api/auth/login?redirect=/')` through the chain. The dispatcher can identify the exact hostname + status that triggers ERR_NAME_NOT_RESOLVED (one of: cold-start race / `context.route` bridge missing navigation hostname / unknown second hostname). Evidence captured serves the next root-cause fix-task's spec § Root Cause section.
- [ ] **AC-3 (hard invariant — playwright.config.ts byte-unchanged)** — `git diff --stat origin/main -- projects/platform-console/apps/console-web/playwright.config.ts` = empty (MONO-133's `trace: 'on'` conditional stays).
- [ ] **AC-4 (hard invariant — workflow byte-unchanged)** — `git diff --stat origin/main -- .github/workflows/nightly-e2e.yml` = empty (MONO-133's upload always + path already covers this task's output).
- [ ] **AC-5 (hard invariant — GAP byte-unchanged)** — `git diff --stat origin/main -- projects/global-account-platform/` = empty.
- [ ] **AC-6 (hard invariant — 5 other producers byte-unchanged, 22회째 zero-retrofit)** — `git diff --stat origin/main -- 'projects/wms-platform/' 'projects/scm-platform/' 'projects/erp-platform/' 'projects/fan-platform/' 'projects/ecommerce-microservices-platform/' 'projects/finance-platform/'` = empty.
- [ ] **AC-7 (hard invariant — console-bff byte-unchanged)** — `git diff --stat origin/main -- projects/platform-console/apps/console-bff/` = empty.
- [ ] **AC-8 (hard invariant — other platform-console paths byte-unchanged)** — `git diff --stat origin/main -- projects/platform-console/specs/ projects/platform-console/docker-compose.e2e.yml projects/platform-console/apps/console-web/tests/e2e/specs/ projects/platform-console/apps/console-web/tests/e2e/fixtures/global-setup.ts` = empty (login.ts only).
- [ ] **AC-9 (CI-only conditional)** — `grep -n 'process.env.CI' projects/platform-console/apps/console-web/tests/e2e/fixtures/login.ts | wc -l` ≥ 1 (the tracing start/stop guarded by CI conditional).
- [ ] **AC-10 (trace path constant)** — `grep -n 'global-setup-driveOidcPkceLogin' projects/platform-console/apps/console-web/tests/e2e/fixtures/login.ts | wc -l` ≥ 1 (descriptive path constant present).
- [ ] **AC-11 (BE-303 3-dim objective merge verification)** — close-chore PR authored only after the impl PR's 3-dim verification passes.

---

# Related Specs

> Before reading Related Specs: follow `platform/entrypoint.md` Step 0 — read `PROJECT.md`, then load `rules/common.md` + `rules/domains/saas.md` + `rules/traits/{multi-tenant,integration-heavy,audit-heavy}.md`.

- [`projects/platform-console/PROJECT.md`](../../PROJECT.md).
- [`projects/platform-console/apps/console-web/tests/e2e/fixtures/login.ts`](../../apps/console-web/tests/e2e/fixtures/login.ts) — the file modified.
- [`projects/platform-console/apps/console-web/playwright.config.ts`](../../apps/console-web/playwright.config.ts) — read-only reference; MONO-133's `trace: 'on'` conditional stays.
- [`.github/workflows/nightly-e2e.yml`](../../../../.github/workflows/nightly-e2e.yml) — read-only reference; MONO-133's upload step's `test-results/` path already covers the trace path.
- [`tasks/done/TASK-MONO-133-pc-e2e-playwright-diagnostic-instrumentation.md`](../../../../tasks/done/TASK-MONO-133-pc-e2e-playwright-diagnostic-instrumentation.md) — predecessor; spec § Failure Scenarios + § Decision authority Option B verbatim defined this task's scope.

# Related Contracts

- None. Diagnostic-only.

# Related Skills

- None additional.

---

# Edge Cases

- **CI=false (dev runs)** — tracing wrap skipped via `process.env.CI` guard; dev experience unchanged.
- **Trace stop fails (rare Playwright bug)** — try/finally ensures `page.close()` still runs; partial trace better than zero.
- **`mkdir recursive: true` race** — Node.js docs guarantee no-op when dir exists; concurrent globalSetup runs impossible (Playwright runs globalSetup once per process).
- **Trace path collision with future second globalSetup test** — unlikely in current codebase (only one globalSetup); future addition would use distinct trace path.

# Failure Scenarios

- **Trace.zip generated but artifact upload still skips** — would indicate MONO-133's path glob doesn't match `test-results/global-setup-driveOidcPkceLogin/`. Diagnostic: check `gh api artifacts` + `Upload Playwright report + trace` step log. Fix: add explicit path to upload `with.path:` (separate task; expected NOT to happen since `test-results/` covers all subdirs).
- **Trace.zip captured but opens empty** — Playwright internal serialization bug; rare. Mitigation: capture screenshots inline via `page.screenshot({ path: ... })` as belt-and-suspenders (deferred).

---

# Test Requirements

- AC-1 + AC-2 verification = next dispatch / push of nightly e2e. `workflow_dispatch` on the impl branch recommended for faster signal.
- No new automated test needed.

---

# Definition of Done

- [ ] Three PRs landed in order: (1) spec PR (this task md + project `tasks/INDEX.md` ready entry), (2) impl PR (login.ts mutation + lifecycle), (3) close-chore PR with BE-303 3-dim verification + AC-1 verification dispatch result documented.
- [ ] AC-1 through AC-11 all checked off in the close-chore PR description.
- [ ] Trace artifact downloaded + opened by dispatcher; next root-cause fix-task (PC-FE-028 / MONO-134) authored with trace evidence in its spec § Root Cause.

---

# 메타 (intended)

① **MONO-133 spec § Failure Scenarios → PC-FE-027 spec § Goal pattern** — spec authoring 단계의 risk-modeling 이 *실제 escalation path* 으로 1:1 적중 (MONO-130 F2 spec prediction 과 동일 lesson 의 연장). 향후 spec § Failure Scenarios 의 fallback 명시 = future cycle 의 cheap option discovery.

② **scope shrinks as evidence accumulates** — MONO-133 = 3 file mod (composite root MONO); PC-FE-027 = 1 file mod (login.ts only, project-internal PC-FE). cycle pattern 의 *narrowing 패턴* — each fix narrower than the last, even when crossing layer boundaries.

③ **CI-only conditional fixture mutation is acceptable** — fixture purity 가 절대 룰 아님 (test code 라 production-isolated). CI-only `process.env.CI` guard 가 dev experience 보존 + production code 무영향 = 정직한 trade-off.

④ **Manual trace path management cost is small** — Playwright's automatic `test-results/<test>/trace.zip` location 못 쓰지만, 단일 명확 path (`global-setup-driveOidcPkceLogin`) = future inspect command 도 predictable.

⑤ **Evidence-first → spec-first → fix-first → verify-first chain** — diagnostic cycle (PC-FE-027 가 본 task) 가 evidence 를 capture → 다음 cycle (PC-FE-028 / MONO-134) 가 evidence-based spec 작성 → root cause fix → AC-1 verify → main GREEN restored. 5+ cycle layer 의 정직한 진행 패턴.

분석=Opus 4.7 / 구현 권장=Sonnet 4.6 (login.ts surgical mutation + Option B 이미 spec 단계에서 결정 + Playwright tracing API 사용은 mechanical).
