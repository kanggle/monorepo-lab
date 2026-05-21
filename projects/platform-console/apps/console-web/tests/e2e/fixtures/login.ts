import type { BrowserContext, Page } from '@playwright/test';

/**
 * TASK-PC-FE-019 — Playwright login fixture for the platform-console e2e
 * harness. Returns a browser context already authenticated as the seeded
 * SUPER_ADMIN operator.
 *
 * Strategy — programmatic token mint, NOT a browser-driven OIDC PKCE round
 * trip. Rationale:
 *
 *   - The auth-service (Spring Authorization Server) does not ship an HTML
 *     login form. The SAS `LoginUrlAuthenticationEntryPoint` redirects
 *     unauthenticated browser requests to `/api/auth/login`, but that endpoint
 *     is a JSON-only POST (returns access/refresh JWT directly) — there is
 *     no `formLogin()` HTML page to drive with Playwright `.fill()` /
 *     `.click()`. The OIDC PKCE *authorization_code* path is operationally
 *     incomplete for a headless browser today.
 *
 *   - Workaround that preserves every BFF / admin-service trust boundary:
 *     extend the existing `platform-console-web` OAuth client to ALSO allow
 *     `grant_type=client_credentials` with a known client secret (seed.sql
 *     applies this UPDATE at runtime, NOT to GAP source — AC-3 byte-diff
 *     invariant preserved). The fixture POSTs to `/oauth2/token` to obtain
 *     a real SAS-issued JWT with `iss=auth-service URL`, `aud=platform-
 *     console-web`, `sub=platform-console-web`. The admin token-exchange
 *     resolves that subject to the seeded `e2e-super-admin` row
 *     (oidc_subject='platform-console-web') and mints an operator token.
 *
 *   - The browser context is then primed with the same 3 cookies that the
 *     production `/api/auth/callback` handler would set after a successful
 *     OIDC + token-exchange round trip:
 *       `console_access_token`   ← SAS access token (Bearer used by
 *                                  console-bff resource server)
 *       `console_operator_token` ← admin-service operator token (the
 *                                  `/api/admin/**` credential)
 *       `console_active_tenant`  ← initial tenant slug ('fan-platform' so
 *                                  the finance read leg can resolve the
 *                                  seeded account row).
 *
 *   - Replaced when auth-service ships an HTML form (future task — see
 *     `tasks/ready/` for a candidate follow-up to add a SAS HTML login
 *     surface so the OIDC PKCE flow is fully browser-drivable). At that
 *     point this fixture is rewritten to drive `/login → /oauth2/authorize
 *     → /login form → /api/auth/callback` end-to-end.
 *
 * Inside docker-compose.e2e.yml the targets are reachable on the host via
 * the published ports (18081 for auth-service, 18085 for admin-service).
 * Playwright runs on the host (CI: ubuntu-latest job); console-web is also
 * published on host:3000 (Playwright baseURL).
 */

const DEFAULTS = {
  authBaseUrl: process.env.E2E_AUTH_BASE_URL ?? 'http://localhost:18081',
  adminBaseUrl: process.env.E2E_ADMIN_BASE_URL ?? 'http://localhost:18085',
  consoleOrigin: process.env.E2E_CONSOLE_ORIGIN ?? 'http://localhost:3000',
  oidcClientId: 'platform-console-web',
  oidcClientSecret: 'secret',
  defaultTenant: 'fan-platform',
};

interface MintedTokens {
  accessToken: string;
  operatorToken: string;
  operatorTtlSeconds: number;
}

async function mintTokens(): Promise<MintedTokens> {
  // 1. SAS /oauth2/token — client_credentials grant against the seeded
  //    `platform-console-web` client (seed.sql extends grant_types).
  const tokenBody = new URLSearchParams();
  tokenBody.set('grant_type', 'client_credentials');
  tokenBody.set('scope', 'openid');

  const basic = Buffer.from(
    `${DEFAULTS.oidcClientId}:${DEFAULTS.oidcClientSecret}`,
    'utf8',
  ).toString('base64');

  const tokenRes = await fetch(`${DEFAULTS.authBaseUrl}/oauth2/token`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded',
      Authorization: `Basic ${basic}`,
      Accept: 'application/json',
    },
    body: tokenBody.toString(),
  });
  if (!tokenRes.ok) {
    const text = await tokenRes.text();
    throw new Error(
      `SAS /oauth2/token failed (${tokenRes.status}): ${text.slice(0, 500)}`,
    );
  }
  const tokenPayload = (await tokenRes.json()) as { access_token?: string };
  if (!tokenPayload.access_token) {
    throw new Error('SAS /oauth2/token returned no access_token');
  }

  // 2. admin-service /api/admin/auth/token-exchange — RFC 8693. Validates
  //    iss/aud against `ADMIN_OIDC_*` env (set in docker-compose.e2e.yml),
  //    resolves admin_operators by oidc_subject, mints the operator token.
  const exchangeRes = await fetch(
    `${DEFAULTS.adminBaseUrl}/api/admin/auth/token-exchange`,
    {
      method: 'POST',
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded',
        Accept: 'application/json',
      },
      body: new URLSearchParams({
        grant_type: 'urn:ietf:params:oauth:grant-type:token-exchange',
        subject_token: tokenPayload.access_token,
        subject_token_type: 'urn:ietf:params:oauth:token-type:access_token',
      }).toString(),
    },
  );
  if (!exchangeRes.ok) {
    const text = await exchangeRes.text();
    throw new Error(
      `admin /token-exchange failed (${exchangeRes.status}): ${text.slice(0, 500)}`,
    );
  }
  const exchangePayload = (await exchangeRes.json()) as {
    access_token?: string;
    expires_in?: number;
  };
  if (!exchangePayload.access_token) {
    throw new Error('admin /token-exchange returned no access_token');
  }

  return {
    accessToken: tokenPayload.access_token,
    operatorToken: exchangePayload.access_token,
    operatorTtlSeconds: exchangePayload.expires_in ?? 1800,
  };
}

function cookieDomain(): string {
  // Cookies set on a Playwright BrowserContext are matched by domain. We
  // strip the scheme/port from `consoleOrigin` to derive the cookie host.
  return new URL(DEFAULTS.consoleOrigin).hostname;
}

/**
 * Programmatically authenticates a context as the seeded SUPER_ADMIN.
 *
 * Idempotent: call once per Playwright `test.use({ storageState })` setup
 * (e.g. from `global-setup.ts`) and reuse the persisted state across spec
 * runs in a single CI invocation.
 */
export async function loginAsSuperAdmin(
  context: BrowserContext,
): Promise<void> {
  const tokens = await mintTokens();
  // Match the production cookie shape (`shared/lib/session.ts` constants):
  //   - httpOnly true (server-side only — never read by client JS).
  //   - secure false (Playwright tests run over http://localhost — the
  //     production `secure: true` is only viable behind TLS termination).
  //   - sameSite 'Strict' (production parity).
  //   - path '/'.
  await context.addCookies([
    {
      name: 'console_access_token',
      value: tokens.accessToken,
      domain: cookieDomain(),
      path: '/',
      httpOnly: true,
      secure: false,
      sameSite: 'Strict',
    },
    {
      name: 'console_operator_token',
      value: tokens.operatorToken,
      domain: cookieDomain(),
      path: '/',
      httpOnly: true,
      secure: false,
      sameSite: 'Strict',
    },
    {
      name: 'console_active_tenant',
      value: DEFAULTS.defaultTenant,
      domain: cookieDomain(),
      path: '/',
      httpOnly: true,
      secure: false,
      sameSite: 'Strict',
    },
  ]);
}

/**
 * Convenience for specs that need a logged-in `page` (rather than the
 * context-level fixture). The two are equivalent — Playwright propagates
 * the context's cookies to every page within that context.
 */
export async function loginAsSuperAdminPage(page: Page): Promise<void> {
  await loginAsSuperAdmin(page.context());
}
