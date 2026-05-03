import { expect, type Page } from '@playwright/test';

/**
 * Playwright auth helpers for web-store, post TASK-FE-067 (NextAuth v5 + GAP
 * OIDC).
 *
 * The legacy self-service signup/login flow that POSTed directly to the
 * ecommerce auth-service has been retired. Authentication now flows through:
 *
 *   1. Click "로그인" → `/login`
 *   2. Click "Global Account 로 로그인" → NextAuth `/api/auth/signin/gap`
 *   3. Browser is redirected to GAP `/oauth2/authorize?...&prompt=...`
 *   4. GAP renders signup-or-login page → user submits credentials
 *   5. GAP `/oauth2/callback` → web-store `/api/auth/callback/gap`
 *   6. NextAuth completes session → redirects to `callbackUrl`
 *
 * Steps 3-5 are owned by GAP (see `projects/global-account-platform/`),
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
 * Drive the GAP signup-or-login page. The exact selectors depend on GAP's
 * own login UI (defined in `projects/global-account-platform/`); when the
 * GAP UI changes, update this helper.
 *
 * Defensive selectors: GAP's login page exposes accessible labels in Korean
 * for the email/password/account-type fields (per the consumer-integration-
 * guide § Phase 2 mockups). The submit button is identified by its visible
 * text "로그인 / 가입".
 */
export async function completeGapSignIn(page: Page, user: TestUser): Promise<void> {
  // Wait until we're on a GAP host (URL contains the issuer host).
  await page.waitForURL((url) => /\/oauth2\/authorize/.test(url.toString()) || /\/login/.test(url.pathname), { timeout: 15_000 });

  // GAP's login page accepts an existing user OR creates a new one when the
  // email is unknown (consumer-integration-guide § Phase 2 "create-or-login"
  // mode). Fill the visible form fields; ignore optional name/account_type
  // inputs if GAP's page does not render them in this environment.
  const emailField = page.getByLabel(/이메일|email/i).first();
  const passwordField = page.getByLabel(/비밀번호|password/i).first();
  await emailField.fill(user.email);
  await passwordField.fill(user.password);

  const nameField = page.getByLabel(/이름|name/i).first();
  if (await nameField.isVisible({ timeout: 1_000 }).catch(() => false)) {
    await nameField.fill(user.name);
  }
  const accountTypeRadio = page.getByLabel(user.accountType ?? 'CONSUMER', { exact: false });
  if (await accountTypeRadio.isVisible({ timeout: 1_000 }).catch(() => false)) {
    await accountTypeRadio.check();
  }

  const submit = page.getByRole('button', { name: /로그인|가입|continue|sign in/i }).first();
  await expect(submit).toBeEnabled({ timeout: 10_000 });
  await submit.click();
}

/**
 * Sign in via GAP — clicks the web-store `/login` GAP button, completes the
 * GAP authorize flow, and waits for redirect back into web-store.
 */
export async function signupAndLogin(page: Page, user: TestUser = uniqueUser()): Promise<TestUser> {
  await page.goto('/login');
  const trigger = page.getByRole('button', { name: 'Global Account 로 로그인' });
  await expect(trigger).toBeEnabled();
  await trigger.click();

  await completeGapSignIn(page, user);

  // After successful GAP callback the user lands on `/` (or the original
  // `from=` callbackUrl). Just confirm we're no longer on /login.
  await page.waitForURL((url) => !url.pathname.startsWith('/login'), { timeout: 30_000 });
  return user;
}

/**
 * Cross-app guard: an OPERATOR who completes GAP login should be rejected
 * by web-store and bounced back to `/login?error=account_type_mismatch`.
 */
export async function loginAsOperatorAndExpectMismatch(page: Page, user: TestUser): Promise<void> {
  const operator: TestUser = { ...user, accountType: 'OPERATOR' };
  await page.goto('/login');
  await page.getByRole('button', { name: 'Global Account 로 로그인' }).click();
  await completeGapSignIn(page, operator);
  await page.waitForURL((url) => url.pathname === '/login' && url.search.includes('account_type_mismatch'), {
    timeout: 30_000,
  });
}
