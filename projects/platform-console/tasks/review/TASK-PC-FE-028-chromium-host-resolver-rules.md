# Task ID

TASK-PC-FE-028

# Title

console-web e2e Playwright config — wire Chromium `--host-resolver-rules="MAP auth-service:8081 127.0.0.1:18081"` launch flag (CI-only) to fix `ERR_NAME_NOT_RESOLVED` on the docker-internal OIDC issuer hostname during browser navigation. PC-FE-027 trace evidence proved that `context.route` bridge cannot intercept top-level page navigation's native DNS resolution step (route handler invoked AFTER DNS resolves; if DNS fails, navigation aborts before handler). The trace's Network panel captured exactly 2 URLs (`localhost:3000/api/auth/login` + the 302 redirect target `http://auth-service:8081/oauth2/authorize?...`); the third URL was never attempted because the browser DNS resolve on `auth-service` failed first. Chromium's `--host-resolver-rules` flag operates at the DNS layer (before any navigation attempt), so `MAP auth-service:8081 127.0.0.1:18081` redirects the browser's resolution to the host-published port — and the browser fetches the response from the auth-service container without ever attempting to resolve `auth-service` natively.

# Status

review

# Owner

frontend

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- test
- fix

---

# Dependency Markers

- **depends on**: TASK-PC-FE-027 (close `233b26a2` — Option B fixture-level tracing captured the trace.zip evidence). PC-FE-027's AC-2 trace inspection narrowed 3 root-cause candidates → 1 (`context.route` DNS-step inapplicable for navigation), making PC-FE-028's option choice mechanical.
- **prerequisite of**: nightly main GREEN restoration. After this fix lands, `Platform Console E2E full-stack` step 17 `Run Playwright e2e (2 specs)` SUCCESS (or surfaces the *next* layer per cycle pattern — but evidence suggests this is the terminal layer because the redirect chain is the last globalSetup-bound failure point).

---

# Goal

Make the next dispatch / push `Platform Console E2E full-stack` job's step 17 `Run Playwright e2e (2 specs)` SUCCESS. After this fix lands, the browser resolves `auth-service:8081` to `127.0.0.1:18081` (the host-published port) at the DNS layer, navigation succeeds through the entire OIDC PKCE redirect chain (console-web → SAS `/oauth2/authorize` → SAS `/login` form → ... → console-web `/api/auth/callback` → `/`), the 2 specs (operators-profile + operators-admin-profile) execute, and the main nightly RED state is restored to GREEN.

## Root cause (verbatim quotes from PC-FE-027 trace evidence)

- Dispatch run `26325608932` (PC-FE-027 impl branch workflow_dispatch) job `Platform Console E2E full-stack` step 17 fail trace artifact `platform-console-playwright-report-nightly` (8938 bytes) → extracted `trace.network` — captured URLs (only 2):
  1. `http://localhost:3000/api/auth/login?redirect=/` — Playwright `page.goto`
  2. `http://auth-service:8081/oauth2/authorize?response_type=code&client_id=platform-console-web&redirect_uri=http%3A%2F%2Flocalhost%3A3000%2Fapi%2Fauth%2Fcallback&scope=openid+profile+email+tenant.read&code_challenge=U-4ztKGdGxb7UWl7h9yGnNbS2fB7HGXndgi1TaTp32s&code_challenge_method=S256&state=Sn_rQVYqHy2GZT4cKLeJ-A` — console-web's 302 redirect target
- **Third URL absent.** The browser attempted to resolve `auth-service` hostname → native DNS lookup failed → ERR_NAME_NOT_RESOLVED → navigation aborted → `context.route` handler never invoked (the route handler runs AFTER DNS resolves, not before).
- Fixture source [projects/platform-console/apps/console-web/tests/e2e/fixtures/login.ts:111-126](../../apps/console-web/tests/e2e/fixtures/login.ts):
  ```ts
  await context.route(pattern, async (route) => {
    const originalUrl = new URL(route.request().url());
    const rewritten = new URL(...);
    const response = await route.fetch({ url: rewritten.toString() });
    await route.fulfill({ response });
  });
  ```
  Bridge IS registered correctly (PC-FE-027 trace confirms `await context.route(pattern, ...)` was awaited before `page.goto`). The bridge works *only for resource subrequests that DO reach the route handler*. Top-level page navigation's URL undergoes DNS resolution FIRST; only when DNS resolves does Chromium hand the connection off to the route layer.
- Comparison: PR-time smoke environment (`playwright.smoke.config.ts:52` `OIDC_ISSUER_URL: 'http://127.0.0.1:1'`) never produced a redirect to `auth-service:8081` because the closed-loopback issuer returned an unreachable token URL on first call; the bridge was never invoked. The "DNS lookup 우회" lesson in PC-FE-022 memory was thus *PR-time-smoke-only* and inapplicable to nightly full-stack.
- Chromium `--host-resolver-rules` documentation: `MAP <hostname>[:<port>] <target>[:<target_port>]` — operates at the DNS resolver layer, BEFORE any connection attempt. The mapping is enforced for every network operation including top-level navigation. Cross-reference: PC-FE-022 spec § Decision authority listed this as "Option B (Chromium `--host-resolver-rules` + docker publish port 8081:8081)" but rejected at the time because "fixture-internal `context.route` 가 더 깔끔" — that conclusion was correct for PR-time smoke but wrong for nightly full-stack. PC-FE-028 elevates the rejected option to the chosen approach for the full-stack environment.

## Decision authority — Option A (Playwright `use.launchOptions.args` Chromium `--host-resolver-rules`)

Three real options were considered:

| Option | Description | Pros | Cons |
|---|---|---|---|
| **A — Playwright `use.launchOptions.args` Chromium `--host-resolver-rules` flag (CI-only)** | `use.launchOptions.args: ['--host-resolver-rules=MAP auth-service:8081 127.0.0.1:18081']` in `playwright.config.ts` (CI-only via `process.env.CI` conditional). | DNS-layer mapping = operates BEFORE navigation; covers top-level page navigation + redirects + subresources uniformly. Single config-file change (1 file mod). No fixture mutation. No docker-compose change. CI-only conditional preserves dev experience. Chromium-stable flag (long-documented; not Playwright-version-fragile). | Couples test config to docker-compose port mapping (`18081` hardcoded). If docker-compose changes the auth-service port mapping, this flag must update. |
| **B — docker-compose `extra_hosts: ["auth-service:host-gateway"]` on console-web** | Add `extra_hosts` to console-web service so the container's `/etc/hosts` resolves `auth-service` to host gateway IP; but the *browser* (host-side Playwright Chromium) doesn't read the container's hosts file. So this would only help server-side fetches inside the container, NOT the browser's hostname resolution. | None applicable. | Doesn't actually fix the browser-side DNS issue. |
| **C — Reverse PC-FE-027's `bridgeAuthServiceHostname` + fixture-internal token mint (revival of PC-FE-019 backdoor approach)** | Drop the OIDC PKCE browser-driven path; programmatically mint tokens via auth-service `/oauth2/token` from Node.js (Playwright's `request` context, which CAN resolve via host network) and inject cookies. | No browser DNS issue. | Reverts PC-FE-022's "true OIDC PKCE production-parity" win; drops the production-identical browser flow; loses click-sequence regression assertion. Not acceptable per ADR-MONO-017 e2e harness scope. |

**Chosen — Option A.** Rationale:

1. **DNS-layer fix matches DNS-layer cause.** PC-FE-027 trace evidence proves the error is at native DNS resolution; `context.route` bridge operates one layer too late. `--host-resolver-rules` operates at exactly the right layer.

2. **Surgical single-line config change.** No fixture mutation, no docker-compose change, no test code change. Adds ~1 line to `playwright.config.ts` `use.launchOptions.args`.

3. **Port hardcoding is a small cost.** `18081` is the host-published port for `auth-service:8081` (`docker-compose.e2e.yml:201` `ports: ["18081:8081"]`). The hostname-port pair is *already* hardcoded in 3 places in the codebase (compose port mapping, fixture `localhostAuthBaseUrl`, workflow `E2E_AUTH_BASE_URL` env); adding a 4th is acceptable for the DNS rule. Alternative `MAP auth-service 127.0.0.1` (port-agnostic) would map to `127.0.0.1:8081`, which the host doesn't publish; the port-specific mapping is necessary.

4. **CI-only conditional preserves dev.** Same conditional pattern PC-FE-027 + MONO-133 already established (`process.env.CI`).

5. **`context.route` bridge stays.** It's still useful for *non-navigation* subresources if any future spec adds them (currently none; harmless dead code). Removing it is a separate cleanup (deferred).

---

# Scope

## In Scope

- `projects/platform-console/apps/console-web/playwright.config.ts` — modify `use` block to add CI-conditional `launchOptions.args`:
  ```ts
  use: {
    baseURL: process.env.CONSOLE_BASE_URL ?? 'http://localhost:3000',
    storageState: STORAGE_STATE,
    trace: process.env.CI ? 'on' : 'on-first-retry',
    // TASK-PC-FE-028 — map docker-internal OIDC issuer hostname to the
    // host-published port at the Chromium DNS resolver layer. Required
    // because context.route bridges (PC-FE-022) intercept resource subrequests
    // but NOT top-level navigation DNS resolution (PC-FE-027 trace evidence).
    // CI-only because dev runs use the traefik path (console.local).
    ...(process.env.CI ? {
      launchOptions: {
        args: ['--host-resolver-rules=MAP auth-service:8081 127.0.0.1:18081'],
      },
    } : {}),
  },
  ```
- This task md + project `tasks/INDEX.md` ready entry.

## Out of Scope

- `projects/platform-console/apps/console-web/tests/e2e/fixtures/login.ts` byte-diff (AC-3 — fixture stays as-is; bridge becomes harmless dead code for navigation, still useful for any future non-navigation subrequest patterns).
- `projects/platform-console/docker-compose.e2e.yml` byte-diff (AC-4).
- `.github/workflows/nightly-e2e.yml` byte-diff (AC-5).
- `projects/global-account-platform/` + 5 other producers byte-diff (AC-6/7, 23회째 zero-retrofit).
- `projects/platform-console/apps/console-bff/` byte-diff (AC-8 — D4 HARD INVARIANT).
- Memory updates documenting the 7-layer cycle terminal (deferred to separate audit-memory task after this lands).

---

# Acceptance Criteria

- [ ] **AC-1 (functional, primary)** — Next dispatch / push `Platform Console E2E full-stack` job's step 17 `Run Playwright e2e (2 specs)` **SUCCESS**. Verified by either: (a) `gh run view <id>` step 17 conclusion = success + `gh run view <id>` overall conclusion = success (full job GREEN); OR (b) step 17 still fails but with a DIFFERENT error (not ERR_NAME_NOT_RESOLVED) — meaning DNS issue is resolved and a downstream-layer fix becomes the next cycle.
- [ ] **AC-2 (functional, secondary)** — Main nightly RED state restored to GREEN. Verified via post-merge nightly push run on main.
- [ ] **AC-3 (hard invariant — fixture byte-unchanged)** — `git diff --stat origin/main -- projects/platform-console/apps/console-web/tests/e2e/fixtures/` = empty.
- [ ] **AC-4 (hard invariant — docker-compose byte-unchanged)** — `git diff --stat origin/main -- projects/platform-console/docker-compose.e2e.yml` = empty.
- [ ] **AC-5 (hard invariant — workflow byte-unchanged)** — `git diff --stat origin/main -- .github/workflows/` = empty.
- [ ] **AC-6 (hard invariant — GAP byte-unchanged)** — `git diff --stat origin/main -- projects/global-account-platform/` = empty.
- [ ] **AC-7 (hard invariant — 5 other producers byte-unchanged, 23회째 zero-retrofit)** — `git diff --stat origin/main -- 'projects/wms-platform/' 'projects/scm-platform/' 'projects/erp-platform/' 'projects/fan-platform/' 'projects/ecommerce-microservices-platform/' 'projects/finance-platform/'` = empty.
- [ ] **AC-8 (hard invariant — console-bff byte-unchanged)** — `git diff --stat origin/main -- projects/platform-console/apps/console-bff/` = empty.
- [ ] **AC-9 (CI-only conditional)** — `grep -c 'process.env.CI' projects/platform-console/apps/console-web/playwright.config.ts` ≥ 2 (one for `trace`, one for `launchOptions`).
- [ ] **AC-10 (host-resolver-rules literal)** — `grep -c 'host-resolver-rules' projects/platform-console/apps/console-web/playwright.config.ts` = 1.
- [ ] **AC-11 (BE-303 3-dim objective merge verification)** — close-chore PR authored only after the impl PR's 3-dim verification passes.

---

# Related Specs

> Before reading Related Specs: follow `platform/entrypoint.md` Step 0 — read `PROJECT.md`, then load `rules/common.md` + `rules/domains/saas.md` + `rules/traits/{multi-tenant,integration-heavy,audit-heavy}.md`.

- [`projects/platform-console/PROJECT.md`](../../PROJECT.md).
- [`projects/platform-console/apps/console-web/playwright.config.ts`](../../apps/console-web/playwright.config.ts) — the file modified.
- [`projects/platform-console/apps/console-web/tests/e2e/fixtures/login.ts`](../../apps/console-web/tests/e2e/fixtures/login.ts) — read-only reference; bridge stays (AC-3 byte-unchanged); becomes harmless dead code for navigation.
- [`projects/platform-console/docker-compose.e2e.yml`](../../docker-compose.e2e.yml) — read-only reference; `auth-service` `ports: ["18081:8081"]` (line 201) defines the host port mapped by `--host-resolver-rules`.
- [`tasks/done/TASK-PC-FE-027-fixture-context-tracing.md`](../done/TASK-PC-FE-027-fixture-context-tracing.md) — predecessor; trace evidence in its closure entry.
- [`tasks/done/TASK-PC-FE-022-fixture-oidc-pkce-form-fill-migration.md`](../done/TASK-PC-FE-022-fixture-oidc-pkce-form-fill-migration.md) — read-only reference; original `context.route` bridge author; spec § Decision authority listed Chromium `--host-resolver-rules` as Option B (rejected at the time for PR-time smoke context). PC-FE-028 elevates this rejected option for the full-stack context.

# Related Contracts

- None.

# Related Skills

- None additional.

---

# Edge Cases

- **`auth-service:8081` host-port mapping changes** — would require updating both the `--host-resolver-rules` value and the docker-compose port (currently `["18081:8081"]`). Coordinated change.
- **Multiple host-resolver-rules in the future** — comma-separated within one flag (e.g., `MAP auth-service:8081 127.0.0.1:18081, MAP admin-service:8085 127.0.0.1:18085`); current scope = auth-service only (admin-service URL is server-side fetch from console-web container, not browser-side).
- **Playwright Chromium version compatibility** — `--host-resolver-rules` is a Chromium-stable flag (documented since Chromium 4.x, equivalent across modern versions); Playwright passes args directly to Chromium without sanitization.
- **Dev runs (CI=false)** — flag skipped via conditional; dev uses `console.local` Traefik path which already resolves via host DNS.

# Failure Scenarios

- **Step 17 still fails with ERR_NAME_NOT_RESOLVED** — would indicate `--host-resolver-rules` syntax error or Playwright Chromium passthrough issue. Mitigation: trace inspection (PC-FE-027 mechanism still active) shows whether the request reached the mapped target.
- **Step 17 fails with a DIFFERENT error (not DNS)** — DNS fix worked; new layer surfaced (e.g., SAS form rendering issue, callback handler issue, spec assertion drift). AC-1 (b) clause accepts this as success of the DNS fix; next cycle addresses the new layer.
- **Step 17 succeeds but step 18~19 (Playwright spec assertions) fail** — DNS fix opens the chain; spec-level assertion drift surfaces as the 8th layer. Separate cycle.

---

# Test Requirements

- AC-1 verification = next `workflow_dispatch` on impl branch (recommended for ≤30min signal); AC-2 verification = post-merge nightly push run on main.
- No new automated test needed.

---

# Definition of Done

- [ ] Three PRs landed in order: (1) spec PR (this task md + project `tasks/INDEX.md` ready entry), (2) impl PR (playwright.config.ts mutation + lifecycle), (3) close-chore PR with BE-303 3-dim + AC-1 dispatch verify result + AC-2 post-merge nightly push run result.
- [ ] AC-1 through AC-11 all checked off in the close-chore PR description.
- [ ] If AC-1 (b) (different error after DNS fix), name the next cycle's task (PC-FE-029 / MONO-134) in the close chore narrative.

---

# 메타 (intended)

① **Evidence-based option selection** — PC-FE-027 trace narrowed 3 candidates → 1 (DNS-layer cause). PC-FE-028 fix matches at the same DNS layer. Cycle pattern's evidence-first → fix-first chain works as designed.

② **PC-FE-022 Option B revival** — PC-FE-022 spec rejected Chromium `--host-resolver-rules` as "less elegant" for PR-time smoke context; PC-FE-028 elevates it for nightly full-stack context. Same fix, different environmental context. Lesson: option choice depends on environment, not just elegance.

③ **`context.route` bridge becomes harmless** — fixture stays byte-unchanged (AC-3); bridge no longer invoked for navigation (DNS fix bypasses route layer) but still works for non-navigation subresources. Removing it is a separate cleanup task (deferred). Cycle pattern's narrowing — fix one thing at a time.

④ **Port-coupled config (acceptable cost)** — `--host-resolver-rules=MAP auth-service:8081 127.0.0.1:18081` hardcodes host port `18081`. Already a 4th hardcoding of the pair; consistent with existing convention (no abstraction needed for 1 mapping).

⑤ **Cycle pattern 7th + likely terminal layer** — PC-FE-023 → 024 → MONO-132 → 025 → 026 → MONO-133 (diagnostic) → PC-FE-027 (diagnostic) → **PC-FE-028 (this root-cause fix)**. 8 cycle steps across 3 days (2026-05-21 → 23); 5 progressive surface + 2 cache-masked latent + 2 diagnostic + 1 root-cause = 10 PRs after PC-FE-028 if it terminates the chain. Quality signal of cycle pattern operating at its designed scale.

⑥ **memory update post-cycle** — after PC-FE-028 close, audit-memory cycle to capture: PC-FE-022 ㉚ correction (DNS bridge PR-time-smoke-only), PC-FE-027 trace inspection mechanism (Option B fixture-tracing as standard for globalSetup-bound failures), PC-FE-028 root-cause fix pattern (DNS-layer fix for DNS-layer cause), cycle pattern 7+ layer realization.

분석=Opus 4.7 / 구현 권장=Sonnet 4.6 (Option A 결정 spec, playwright.config.ts surgical 1-line conditional 추가; mechanical).
