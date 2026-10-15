import { test, expect, type BrowserContext } from '@playwright/test';
import { loginAsPoolAccount, SUPER_ADMIN_CREDENTIAL } from './fixtures/login';

/**
 * TASK-MONO-773 S6 — the «logged in · not an operator» console shell, in a browser, against the real IAM stack
 * (console-integration-contract § 2.6.4; IAM 773 S3 trust gate + S5 console shell token).
 *
 * Identities (seeded by `fixtures/seed.sql` § TASK-MONO-773 S6): two PERSONAL (consumer-pool) accounts with a
 * verified email and NO operator facet. Each spec starts from an EMPTY browser (no SUPER_ADMIN storageState),
 * so no IdP session leaks between them.
 *
 *   AC-1 — the shell opens; the sidebar is «가이드 · 테넌트 생성» only; there is NO operator token; the console
 *          token is refused by the admin API (control); an operator screen's direct URL lands on /new-tenant.
 *   AC-5 — `/onboarding` (the old address) lands on the new screen.
 *   AC-2 — create → the console re-exchanges (now an operator) → the overview, inside the new tenant.
 *   AC-7 — logout from the non-operator shell ends the IdP session: the next login shows the IdP form, and a
 *          different account (the SUPER_ADMIN) signs in — no store tab involved.
 *
 * 🔴 These run only in `nightly-e2e.yml` (job `platform-console-e2e-fullstack`), never in `ci.yml`.
 */

test.use({ storageState: { cookies: [], origins: [] } });

const SHELL_ACCOUNT = 'e2e-pool-shell@example.com';
const CREATOR_ACCOUNT = 'e2e-pool-creator@example.com';
const ADMIN_BASE = process.env.E2E_ADMIN_BASE_URL ?? 'http://localhost:18085';

async function cookie(context: BrowserContext, name: string): Promise<string | undefined> {
  return (await context.cookies()).find((c) => c.name === name)?.value;
}

test.describe('@e2e TASK-MONO-773 S6 — non-operator shell', () => {
  test('AC-1 · AC-5 — shell with two sidebar items, no operator token, admin API refuses, old address redirects', async ({
    page,
    context,
  }) => {
    await loginAsPoolAccount(context, SHELL_ACCOUNT);

    await page.goto('/new-tenant');
    await expect(page.getByTestId('new-tenant-heading')).toHaveText('테넌트 생성');

    // Sidebar — exactly the two entries, in order.
    const nav = page.getByRole('navigation', { name: '콘솔 내비게이션' });
    await expect(nav.locator('a')).toHaveCount(2);
    await expect(nav.locator('a').nth(0)).toHaveAttribute('href', '/guide');
    await expect(nav.locator('a').nth(1)).toHaveAttribute('href', '/new-tenant');
    await expect(page.getByTestId('notification-bell-trigger')).toHaveCount(0);

    // 🔴 No operator token; the explicit marker is what put us here.
    expect(await cookie(context, 'console_operator_token')).toBeUndefined();
    expect(await cookie(context, 'console_non_operator')).toBe('1');

    // 🔴 Control — the console (IAM) token is NOT an admin credential.
    const access = await cookie(context, 'console_access_token');
    expect(access, 'the IAM access cookie is present').toBeTruthy();
    const refused = await page.request.get(`${ADMIN_BASE}/api/admin/operators`, {
      headers: { Authorization: `Bearer ${access}` },
    });
    expect(refused.status()).toBe(401);

    // Empty state: received invitations (none for this address) → the ask-your-admin copy → the form.
    await expect(page.getByTestId('received-invitations')).toBeVisible();
    await expect(page.getByTestId('new-tenant-ask-admin')).toBeVisible();
    await expect(page.getByTestId('onboarding-submit')).toBeVisible();

    // An operator screen's direct URL never renders — it lands on the new screen.
    await page.goto('/operators');
    await expect(page).toHaveURL(/\/new-tenant$/);

    // AC-5 — the old address.
    await page.goto('/onboarding');
    await expect(page).toHaveURL(/\/new-tenant$/);
  });

  test('AC-2 — create a tenant → re-exchange → the overview inside the new tenant', async ({ page, context }) => {
    // A CI retry may find this account already an operator (it created a tenant on the first attempt): it then
    // lands on the operator shell, and `/new-tenant` offers the same self form (second company — 773 AC-3).
    await loginAsPoolAccount(
      context,
      CREATOR_ACCOUNT,
      undefined,
      (url) => url.pathname === '/new-tenant' || url.pathname.startsWith('/dashboards'),
    );

    const slug = `e2e-${Date.now().toString(36)}`;
    await page.goto('/new-tenant');
    await page.getByTestId('onboarding-tenant-id').fill(slug);
    await page.getByTestId('onboarding-org-name').fill('E2E 773 Company');
    await page.getByTestId('onboarding-submit').click();

    await page.waitForURL((url) => url.pathname.startsWith('/dashboards'), { timeout: 30_000 });

    expect(await cookie(context, 'console_operator_token'), 'the re-exchange minted an operator token').toBeTruthy();
    expect(await cookie(context, 'console_non_operator'), 'the marker is gone').toBeUndefined();
    expect(await cookie(context, 'console_active_tenant')).toBe(slug);
    expect(await cookie(context, 'console_assumed_token'), 'the new tenant is ASSUMED, not just selected').toBeTruthy();
  });

  test('AC-7 — logout ends the IdP session → the login form → another account (operator) signs in', async ({
    page,
    context,
  }) => {
    await loginAsPoolAccount(context, SHELL_ACCOUNT);
    await page.goto('/new-tenant');

    await page.getByTestId('account-menu-trigger').click();
    await page.getByTestId('logout-button').click();
    await page.waitForURL((url) => url.pathname === '/login', { timeout: 30_000 });
    expect(await cookie(context, 'console_access_token')).toBeUndefined();
    expect(await cookie(context, 'console_non_operator')).toBeUndefined();

    // 🔴 The IdP session ended: starting a login shows the IdP credential form (no silent SSO back into the
    // same personal account).
    await page.goto('/api/auth/login?redirect=/');
    await expect(page.locator('input[name="username"]')).toBeVisible({ timeout: 30_000 });

    await page.fill('input[name="username"]', SUPER_ADMIN_CREDENTIAL.email);
    await page.fill('input[name="password"]', SUPER_ADMIN_CREDENTIAL.password);
    await Promise.all([
      page.waitForURL((url) => url.pathname.startsWith('/dashboards'), { timeout: 30_000 }),
      page.click('button[type="submit"]'),
    ]);
    expect(await cookie(context, 'console_operator_token')).toBeTruthy();
    expect(await cookie(context, 'console_non_operator')).toBeUndefined();
  });
});
