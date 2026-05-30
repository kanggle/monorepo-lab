import { mkdir } from 'node:fs/promises';
import path from 'node:path';

import type { BrowserContext, Page } from '@playwright/test';

/**
 * TASK-MONO-139 — Federation Hardening e2e login fixture.
 *
 * Copied from projects/platform-console/apps/console-web/tests/e2e/fixtures/login.ts
 * (PC-FE-022 + PC-FE-027 + PC-FE-028 iter 7) and adjusted for the root-scoped
 * cross-product harness path. The OIDC PKCE flow, driveOidcPkceLogin, and
 * loginAsSuperAdmin helpers are intentionally byte-identical to the platform-
 * console harness — ADR-MONO-018 D2 (Playwright extended on PC-FE-019..031
 * harness, same OIDC PKCE driver).
 *
 * trace path adjusted: test-results are emitted to the root
 * tests/federation-hardening-e2e/test-results/ directory (matching the
 * federation-hardening-e2e.yml artifact upload glob).
 */

/**
 * TASK-PC-FE-027 — CI-only Playwright trace path (adjusted for root scope).
 */
const TRACE_DIR = path.resolve(
  __dirname,
  '../test-results/global-setup-driveOidcPkceLogin',
);
const TRACE_PATH = path.join(TRACE_DIR, 'trace.zip');

const DEFAULTS = {
  // PC-FE-028 iter 7 pattern — browser resolves auth-service:8081 natively
  // via runner /etc/hosts entry (127.0.0.1 auth-service) + docker-compose
  // auth-service.ports: ["8081:8081"]. The URL must equal OIDC_ISSUER_URL
  // env on console-web for JWT `iss` validation to pass.
  oidcIssuerUrl:
    process.env.E2E_OIDC_ISSUER_URL ?? 'http://auth-service:8081',
  consoleOrigin: process.env.E2E_CONSOLE_ORIGIN ?? 'http://localhost:3000',
  // SUPER_ADMIN credential — matches seed.sql (auth_db.credentials +
  // admin_db.admin_operators oidc_subject=email). Argon2id hash of
  // 'devpassword123!' (GAP V0014 dev seed stable value).
  superAdminEmail: 'e2e-super-admin@example.com',
  superAdminPassword: 'devpassword123!',
  // SUPER_ADMIN has tenant_id='*' (platform-scope sentinel). The cross-product
  // harness does not set console_active_tenant to a specific domain — the
  // wildcard token is accepted by all 5 producers (TenantClaimValidator accepts
  // '*' in addition to the domain-specific value, per seed.sql TASK-BE-312
  // note). Domain-specific navigation is done by the spec directly.
  defaultTenant: '*',
  // TASK-MONO-154 — real-customer acme-corp operator. Credential matches
  // seed.sql section 6/7 (auth_db.credentials tenant_id='acme-corp' +
  // admin_db.admin_operators oidc_subject=email). SAME Argon2id hash of
  // 'devpassword123!' as the SUPER_ADMIN row (same password). The token minted
  // for this operator carries tenant_id='acme-corp' + keystone-injected
  // entitled_domains=[finance,wms] (BE-324 reverse-lookup of the account_db
  // acme-corp → [finance,wms] subscriptions seeded by GAP Flyway V0019/V0020).
  acmeOperatorEmail: 'acme-operator@example.com',
  acmeOperatorPassword: 'devpassword123!',
  acmeTenant: 'acme-corp',
};

/**
 * Drives the full OIDC PKCE login flow against the running stack.
 * Production-identical path: no programmatic token mint, no cookie injection.
 *
 * PC-FE-028 iter 7 discipline: auth-service hostname resolves natively via
 * runner /etc/hosts entry — no context.route bridge needed.
 */
async function driveOidcPkceLogin(
  context: BrowserContext,
  email: string,
  password: string,
  activeTenant: string = DEFAULTS.defaultTenant,
  manageTracing: boolean = true,
): Promise<void> {
  // Manual tracing is for the global-setup context (no Playwright-managed
  // per-test trace there). When this helper is called from INSIDE a test
  // (e.g. loginAsAcmeOperator), the test context already has tracing started
  // by playwright.config `use.trace` — a second context.tracing.start() throws
  // "Tracing has been already started". In-test callers pass manageTracing=false
  // and rely on the test runner's own trace. (MONO-155 — fixes the MONO-154
  // double-start regression caught by the federation-e2e workflow_dispatch run.)
  const tracingEnabled = !!process.env.CI && manageTracing;
  if (tracingEnabled) {
    await mkdir(TRACE_DIR, { recursive: true });
    await context.tracing.start({
      screenshots: true,
      snapshots: true,
      sources: true,
    });
  }

  const page = await context.newPage();
  try {
    // Step 1 — kick the OIDC Authorization Code + PKCE flow.
    await page.goto(`${DEFAULTS.consoleOrigin}/api/auth/login?redirect=/`);

    // Steps 2-3 — browser follows chain:
    //   console-web 302 → SAS /oauth2/authorize 302 → auth-service /login.
    await page.waitForSelector('input[name="username"]');
    await page.fill('input[name="username"]', email);
    await page.fill('input[name="password"]', password);

    // Step 4 — submit. SAS authenticates, 302s back through /oauth2/authorize →
    // redirect_uri (console-web /api/auth/callback). Callback does token +
    // operator-token-exchange, sets production HttpOnly cookies, 302s to `/`.
    // Next.js page.tsx redirect()s to /dashboards.
    await Promise.all([
      page.waitForURL(`${DEFAULTS.consoleOrigin}/dashboards`, { timeout: 30_000 }),
      page.click('button[type="submit"]'),
    ]);

    // Seed console_active_tenant cookie. For the SUPER_ADMIN cross-product
    // suite the operator has tenant_id='*', accepted by all producers (cookie
    // value '*' signals platform-scope to console-web). For the MONO-154
    // acme-corp operator the cookie is 'acme-corp' (the real-customer slug) so
    // the BFF fan-out carries the acme-corp tenant context to every producer —
    // the entitlement gate then accepts finance/wms and rejects scm/erp.
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
  } finally {
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
 * Authenticates a context as the seeded SUPER_ADMIN via OIDC PKCE flow.
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
 * Convenience for specs that need a logged-in page.
 */
export async function loginAsSuperAdminPage(page: Page): Promise<void> {
  await loginAsSuperAdmin(page.context());
}

/**
 * TASK-MONO-154 — authenticates a context as the seeded real-customer
 * `acme-corp` operator via the SAME production-identical OIDC PKCE flow
 * (no programmatic token mint, no cookie-token injection). After login the
 * console_active_tenant cookie is set to 'acme-corp' so the BFF fan-out
 * carries the acme-corp tenant context to every producer.
 *
 * The minted token carries tenant_id='acme-corp' + keystone-injected
 * entitled_domains=[finance,wms] (BE-324). The downstream domain gates
 * (step 3) accept finance/wms (entitled) and reject scm/erp (not entitled) —
 * this is the entitlement-trust discriminator the new spec asserts.
 */
export async function loginAsAcmeOperator(
  context: BrowserContext,
): Promise<void> {
  await driveOidcPkceLogin(
    context,
    DEFAULTS.acmeOperatorEmail,
    DEFAULTS.acmeOperatorPassword,
    DEFAULTS.acmeTenant,
    // In-test caller — the test context already has Playwright-managed tracing.
    false,
  );
}

/**
 * Convenience for specs that need a logged-in page as the acme-corp operator.
 */
export async function loginAsAcmeOperatorPage(page: Page): Promise<void> {
  await loginAsAcmeOperator(page.context());
}
