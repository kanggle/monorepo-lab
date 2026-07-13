import { expect, type Page } from '@playwright/test';

/**
 * Playwright auth helpers for web-store, post TASK-FE-067 (NextAuth v5 + GAP
 * OIDC).
 *
 * The legacy self-service signup/login flow that POSTed directly to the
 * ecommerce auth-service has been retired. Authentication now flows through:
 *
 *   1. Click "로그인" → `/login`
 *   2. Click "Global Account 로 로그인" → NextAuth `/api/auth/signin/iam`
 *   3. Browser is redirected to GAP `/oauth2/authorize?...&prompt=...`
 *   4. GAP renders signup-or-login page → user submits credentials
 *   5. GAP `/oauth2/callback` → web-store `/api/auth/callback/iam`
 *   6. NextAuth completes session → redirects to `callbackUrl`
 *
 * Steps 3-5 are owned by GAP (see `projects/iam-platform/`),
 * so the helpers below need a running GAP container in the e2e stack
 * (TASK-MONO-014 docker-compose addition). For environments where GAP is
 * not yet running, set `process.env.SKIP_GAP_E2E=1` and tests requiring
 * sign-in will be skipped at runtime.
 */

export interface TestUser {
  name: string;
  email: string;
  password: string;
  /** GAP `account_type` claim — defaults to CONSUMER for web-store. */
  accountType?: 'CONSUMER' | 'OPERATOR';
}

export function uniqueUser(prefix = 'e2e'): TestUser {
  const ts = Date.now();
  const rand = Math.floor(Math.random() * 10_000).toString().padStart(4, '0');
  return {
    name: 'E2E 테스터',
    email: `${prefix}-${ts}-${rand}@example.com`,
    password: 'Passw0rd!E2e',
    accountType: 'CONSUMER',
  };
}

/** Returns true when GAP-dependent flows should be skipped in this env. */
export function shouldSkipGap(): boolean {
  return process.env.SKIP_GAP_E2E === '1';
}

/**
 * The CONSUMER credential seeded by `e2e/fixtures/iam-consumer-seed.sql` (run
 * against the GAP `auth_db` before the Playwright run). Password matches the
 * federation-e2e Argon2id seed. Used by `loginAsSeededConsumer` for the real
 * GAP-backed specs (TASK-INT-023).
 */
export const SEEDED_CONSUMER: TestUser = {
  name: 'E2E Consumer',
  email: 'e2e-consumer@example.com',
  password: 'devpassword123!',
  accountType: 'CONSUMER',
};

/**
 * Fill + submit GAP's `/login` credential form. GAP renders the Spring Security
 * DEFAULT login page (no custom template): `<input id="username">` +
 * `<input id="password">` + a hidden `_csrf` + a submit button, posting to
 * `/login`. (The legacy `completeGapSignIn` below assumes a richer
 * signup-or-login page that the current GAP does NOT render — after TASK-FE-074
 * the consumer CRUD specs use `loginAsSeededConsumer` (this form filler); the
 * legacy helper is retained only for the operator account-type-mismatch spec.)
 */
export async function fillGapCredentialForm(page: Page, user: TestUser): Promise<void> {
  await page.waitForURL(
    (url) => /\/login$/.test(url.pathname) || /\/oauth2\/authorize/.test(url.toString()),
    { timeout: 15_000 },
  );
  await page.locator('#username').fill(user.email);
  await page.locator('#password').fill(user.password);
  await page.locator('form[action="/login"] button[type="submit"]').click();
}

/**
 * Real GAP login as the SEEDED consumer: web-store `/login` → GAP button →
 * GAP credential form → back into web-store. No signup (GAP has no inline
 * signup); the credential must already exist (iam-consumer-seed.sql).
 */
export async function loginAsSeededConsumer(
  page: Page,
  user: TestUser = SEEDED_CONSUMER,
): Promise<void> {
  await page.goto('/login');
  const trigger = page.getByRole('button', { name: 'Global Account 로 로그인' });
  await expect(trigger).toBeEnabled();
  await trigger.click();
  await fillGapCredentialForm(page, user);
  // Back on the web-store origin (localhost), no longer on any /login form.
  await page.waitForURL(
    (url) => url.hostname === 'localhost' && !url.pathname.startsWith('/login'),
    { timeout: 30_000 },
  );
}

/**
 * The CROSS-TENANT credential seeded by `e2e/fixtures/iam-consumer-seed.sql` — platform
 * scope (`tenant_id='*'`, SUPER_ADMIN per ADR-002). TASK-MONO-381 uses it to prove the
 * role guard actually bites: its own tenant is not the storefront's platform, so the
 * aud-default seed does not fire and its token carries no `CUSTOMER` role.
 */
export const SEEDED_CROSS_TENANT_PRINCIPAL: TestUser = {
  name: 'E2E Platform Admin',
  email: 'e2e-platform-admin@example.com',
  password: 'devpassword123!',
};

/**
 * Cross-tenant role guard (TASK-MONO-381, amending ADR-MONO-035 §4b-iii).
 *
 * A principal whose own tenant is NOT the storefront's platform authenticates fine at IAM
 * (BE-507's credential lookup admits them) but receives NO `roles` claim, so web-store's
 * `signInCallback` downgrades them to anonymous and bounces to
 * `/login?error=account_type_mismatch` (the error-code string is retained for UI
 * compatibility; the legacy `account_type` claim was removed in ADR-MONO-032 D5 step 4).
 *
 * Uses `fillGapCredentialForm` — the same form filler the three working consumer specs use.
 * The old `completeGapSignIn` drove a signup-or-login page IAM does not render, which is one
 * of the two reasons this spec never ran (the other being that a CUSTOMER-less token was
 * unconstructible on the storefront client at all).
 */
export async function loginAndExpectRoleGuardRejection(
  page: Page,
  user: TestUser = SEEDED_CROSS_TENANT_PRINCIPAL,
): Promise<void> {
  await page.goto('/login');
  const trigger = page.getByRole('button', { name: 'Global Account 로 로그인' });
  await expect(trigger).toBeEnabled();
  await trigger.click();
  await fillGapCredentialForm(page, user);
  await page.waitForURL(
    (url) => url.pathname === '/login' && url.search.includes('account_type_mismatch'),
    { timeout: 30_000 },
  );
}
