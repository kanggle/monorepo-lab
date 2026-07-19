import { mkdir } from 'node:fs/promises';
import path from 'node:path';

import type { BrowserContext, Page } from '@playwright/test';

/**
 * TASK-PC-FE-027 — CI-only Playwright trace path. Captures the
 * `driveOidcPkceLogin` chain (which runs inside `globalSetup`, where
 * `trace: 'on'` config-level setting does not reach because Playwright's
 * reporter + test-results writer abort before booting when globalSetup
 * errors). Path matches the workflow's `Upload Playwright report + trace`
 * step's `test-results/` glob (TASK-MONO-133).
 */
const TRACE_DIR = path.resolve(
  __dirname,
  '../../../test-results/global-setup-driveOidcPkceLogin',
);
const TRACE_PATH = path.join(TRACE_DIR, 'trace.zip');

/**
 * TASK-PC-FE-022 — Playwright login fixture for the platform-console e2e
 * harness. Returns a browser context already authenticated as the seeded
 * SUPER_ADMIN operator via the **production** OIDC Authorization Code + PKCE
 * flow.
 *
 * Replaces the TASK-PC-FE-019 first-cut fixture which used a
 * `client_credentials backdoor` (seed-extended grant on the platform-console-
 * web OAuth client + cookie injection via `fetch`). TASK-BE-309 shipped the
 * auth-service `/login` HTML form (Spring Security
 * `DefaultLoginPageGeneratingFilter`) which lets a headless browser drive the
 * full OIDC PKCE path end-to-end.
 *
 * Flow (step-by-step):
 *
 *   1. `page.goto('http://localhost:3000/api/auth/login?redirect=/')` —
 *      console-web Next.js route generates the PKCE verifier + state, stores
 *      them in HttpOnly cookies, 302s to SAS `/oauth2/authorize?...`.
 *
 *   2. SAS finds no authenticated session, 302s to its `/login` HTML form
 *      (BE-309 `WebLoginSecurityConfig.webLoginFilterChain`).
 *
 *   3. Playwright `page.fill('input[name="username"]', email)` +
 *      `page.fill('input[name="password"]', password)` +
 *      `page.click('button[type="submit"]')` against the
 *      `DefaultLoginPageGeneratingFilter` stock HTML form. CSRF token is the
 *      hidden `_csrf` input the framework emits — `form` submission picks it
 *      up automatically.
 *
 *   4. SAS authenticates via the BE-309 `CredentialAuthenticationProvider`
 *      (looks up `auth_db.credentials` by email, verifies Argon2id hash,
 *      sets `Authentication.details = { tenant_id, tenant_type, account_id }`
 *      as a HashMap so the Jackson SecurityJackson2Modules allowlist accepts
 *      it when SAS persists the OAuth2Authorization to DB). Session-fixation
 *      migration rotates JSESSIONID; the browser cookie store auto-tracks.
 *
 *   5. SAS 302s back to `/oauth2/authorize?...` (re-drive). This time the
 *      session is authenticated, so SAS issues the authorization code and
 *      302s to the registered redirect URI
 *      `http://localhost:3000/api/auth/callback?code=...&state=...`.
 *
 *   6. The console-web callback route exchanges the code (with PKCE verifier)
 *      at SAS `/oauth2/token`, then exchanges the resulting access token at
 *      admin-service `/api/admin/auth/token-exchange` (RFC 8693). The route
 *      sets the production 3 HttpOnly cookies (`console_access_token`,
 *      `console_refresh_token`, `console_operator_token`) — `console_active_
 *      tenant` is set by a subsequent client-side write.
 *
 *   7. Final redirect to the post-login path (`/`).
 *
 * Host / docker-network bridge — `OIDC_ISSUER_URL=http://auth-service:8081`
 * is set inside the docker network for JWT iss-claim and server-side fetches
 * (console-web container reaches auth-service via docker DNS). The browser
 * runs on the host (Playwright) and cannot resolve `auth-service`. To bridge
 * without changing production env, we use Playwright `context.route` to
 * intercept ANY navigation/subresource to `http://auth-service:8081/**`,
 * `route.fetch({ url: rewrittenLocalhost18081 })` the request against the
 * host-published port (`18081:8081`), then `route.fulfill({ response })`
 * with the upstream response. The browser's URL bar continues to show
 * `auth-service:8081`, so SAS's *relative* `/login` Location header
 * resolves naturally to `http://auth-service:8081/login` — also intercepted.
 * The JWT `iss=http://auth-service:8081` matches the console-web
 * `OIDC_ISSUER_URL` expectation (verified path-byte-identical).
 *
 * Replaces the TASK-PC-FE-019 backdoor entirely — `oauth_clients.platform-
 * console-web` is byte-identical to production V0015 (PKCE-mandatory PUBLIC
 * client, no client secret). The only e2e-environment-specific runtime data
 * lives in `auth_db.credentials` (a test-only e2e-super-admin user) and
 * `admin_db.admin_operators` (operator rows with `oidc_subject=email`).
 */

const DEFAULTS = {
  // Browser-facing OIDC issuer URL inside the docker network. The browser
  // resolves `auth-service` natively via the runner's `/etc/hosts` entry
  // (`127.0.0.1 auth-service`, added by the workflow at TASK-PC-FE-028
  // iter 7) — paired with docker-compose's `["8081:8081"]` (iter 4
  // realignment), the host port matches the container port so the URL bar
  // keeps the issuer-identical value end-to-end. The string MUST equal
  // `OIDC_ISSUER_URL` env on console-web for JWT `iss` validation to pass.
  oidcIssuerUrl:
    process.env.E2E_OIDC_ISSUER_URL ?? 'http://auth-service:8081',
  consoleOrigin: process.env.E2E_CONSOLE_ORIGIN ?? 'http://localhost:3000',
  // SUPER_ADMIN credential — matches the row inserted by tests/e2e/fixtures/
  // seed.sql (auth_db.credentials + admin_db.admin_operators where
  // oidc_subject=email). password is the fixed dev/test Argon2id-hashed
  // plaintext also used by IAM V0014 dev seed — value is hardcoded test data,
  // no production credential.
  superAdminEmail: 'e2e-super-admin@example.com',
  superAdminPassword: 'devpassword123!',
  defaultTenant: 'fan-platform',
};

/**
 * Drives the full OIDC PKCE login flow against the running stack and returns
 * once the post-login final redirect has settled. Production-identical path:
 * no programmatic token mint, no cookie injection — purely browser
 * navigation + form submission.
 *
 * <p>TASK-BE-311 iter 4 — the previous `bridgeAuthServiceHostname` (PC-FE-022)
 * was removed once the runner-side DNS path landed. PC-FE-028 iter 7 added a
 * `127.0.0.1 auth-service` entry to the runner's `/etc/hosts` and realigned
 * docker-compose to publish auth-service on host port 8081, so the browser
 * now resolves `auth-service:8081` natively to the host-published container
 * port. The bridge's `route.fetch`-based indirection had been breaking
 * session-fixation cookie continuity (see PC-FE-028 close chore + BE-311
 * iter 3 trace evidence).
 */
async function driveOidcPkceLogin(
  context: BrowserContext,
  email: string,
  password: string,
  // The active-tenant cookie to prime after login. Defaults to the SUPER_ADMIN's
  // `fan-platform`. `null` skips priming entirely, for a spec that drives the
  // tenant itself via the real `POST /api/tenant` switch. No current spec passes
  // `null` (TASK-PC-FE-248 removed the federation-operator specs — see
  // `tests/e2e/README.md`); the branch is kept because it is the correct
  // behaviour for any spec that asserts the assume-tenant re-scope.
  activeTenant: string | null = DEFAULTS.defaultTenant,
): Promise<void> {
  // TASK-PC-FE-027 — start tracing BEFORE bridgeAuthServiceHostname so the
  // `context.route` handler invocations are captured. CI-only to avoid dev
  // iteration overhead.
  const tracingEnabled = !!process.env.CI;
  if (tracingEnabled) {
    await mkdir(TRACE_DIR, { recursive: true });
    await context.tracing.start({
      screenshots: true,
      snapshots: true,
      sources: true,
    });
  }
  // TASK-BE-311 iter 4 — bridgeAuthServiceHostname removed. PC-FE-028 iter 7
  // added `127.0.0.1 auth-service` to the runner's /etc/hosts + realigned
  // docker-compose to `["8081:8081"]`, so the browser resolves
  // `auth-service:8081` natively to the host-published auth-service port
  // without Playwright interception. The bridge's `route.fetch` indirection
  // was breaking session-fixation cookie continuity (trace iter 3 showed
  // POST /login succeeding but the subsequent /oauth2/authorize?...&continue
  // redirected BACK to /login — Spring saw no SecurityContext on the
  // rotated JSESSIONID because the bridge's separate fetch instance is
  // not the same network identity as the browser). Removing the bridge
  // lets the browser drive every request natively.
  const page = await context.newPage();
  try {
    // Step 1 — kick the OIDC flow. `redirect=/` is the post-login target.
    await page.goto(`${DEFAULTS.consoleOrigin}/api/auth/login?redirect=/`);

    // Steps 2-3 — the browser follows the chain
    //   console-web 302 → SAS /oauth2/authorize 302 → auth-service /login.
    // Wait for the form to render. The `DefaultLoginPageGeneratingFilter`
    // emits inputs named exactly `username` + `password`.
    await page.waitForSelector('input[name="username"]');
    await page.fill('input[name="username"]', email);
    await page.fill('input[name="password"]', password);

    // Step 4 — submit. The form posts to /login (auth-service). SAS migrates
    // session, 302s back to /oauth2/authorize, which then 302s to the
    // redirect_uri (console-web /api/auth/callback). The callback handler
    // does the token + operator-token-exchange and sets the production
    // cookies, then 302s to `/`. `/` page.tsx then `redirect()`s to the
    // canonical console landing under `/dashboards/...` — see
    // `src/app/page.tsx`. TASK-PC-FE-034 (#1017) re-pointed that landing
    // from `/dashboards` to `/dashboards/overview` (5-domain overview as
    // home); the IAM 3-leg overview is now a drill-down still at
    // `/dashboards`. Playwright `waitForURL` matches the FINAL URL after
    // all redirects, so we match the `/dashboards` PREFIX (predicate, not
    // an exact string) — this lands whether the canonical home is
    // `/dashboards`, `/dashboards/overview`, or any future sub-route, and
    // never matches the intermediate `/`. TASK-BE-311 iter 7 first moved
    // this off the original `${consoleOrigin}/` assertion (which never
    // matched because Next.js dispatches the / → landing redirect before
    // any observable state); TASK-PC-FE-070 then made it prefix-tolerant
    // after the exact `/dashboards` string silently broke globalSetup.
    await Promise.all([
      page.waitForURL((url) => url.pathname.startsWith('/dashboards'), {
        timeout: 30_000,
      }),
      page.click('button[type="submit"]'),
    ]);

    // Step 7 — seed the `console_active_tenant` cookie. In production this
    // is set by the client-side tenant-switcher write that happens on first
    // page load. The harness primes it here so the 2 e2e specs land in the
    // expected tenant without an extra UI click. Skipped when
    // `activeTenant === null` — a spec that asserts the assume-tenant re-scope
    // must drive the tenant via the real `POST /api/tenant` switch instead.
    if (activeTenant !== null) {
      await context.addCookies([
        {
          name: 'console_active_tenant',
          value: activeTenant,
          domain: new URL(DEFAULTS.consoleOrigin).hostname,
          path: '/',
          httpOnly: true,
          secure: false,
          sameSite: 'Strict',
        },
      ]);
    }
  } finally {
    // TASK-PC-FE-027 — stop tracing FIRST so the trace.zip lands even if
    // the browser/page close throws. The MONO-133 workflow's `if: always()`
    // upload step then captures the artifact (whether the test passed or
    // failed in globalSetup).
    if (tracingEnabled) {
      try {
        await context.tracing.stop({ path: TRACE_PATH });
      } catch {
        // Tracing stop failure must not mask the original error.
      }
    }
    await page.close();
  }
}

/**
 * Programmatically authenticates a context as the seeded SUPER_ADMIN via the
 * production OIDC Authorization Code + PKCE flow.
 *
 * Idempotent: call once per Playwright `test.use({ storageState })` setup
 * (e.g. from `global-setup.ts`) and reuse the persisted state across spec
 * runs in a single CI invocation.
 */
export async function loginAsSuperAdmin(
  context: BrowserContext,
): Promise<void> {
  await driveOidcPkceLogin(
    context,
    DEFAULTS.superAdminEmail,
    DEFAULTS.superAdminPassword,
  );
}

/**
 * Convenience for specs that need a logged-in `page` (rather than the
 * context-level fixture). The two are equivalent — Playwright propagates
 * the context's cookies to every page within that context.
 */
export async function loginAsSuperAdminPage(page: Page): Promise<void> {
  await loginAsSuperAdmin(page.context());
}
