import { readFileSync } from 'node:fs';
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
 *   AC-2 roles — (owner decision 2026-10-15 UTC, «시험 하나 추가») the SAME run walks real auth and real admin:
 *          create → the new tenant's assume token carries NO `roles` (0 subscriptions — control) → SUPER_ADMIN
 *          subscribes it to `wms` → a NEW assume exchange → `roles` = the whole wms catalog, and no role of
 *          `fan` or any other unsubscribed domain. The creator's `ORIGIN` set is `all-domains-full` (the whole
 *          catalog, `fan` included — read here from admin), so what keeps `fan` out is auth's `∩ entitled` alone.
 *   AC-7 — logout from the non-operator shell ends the IdP session: the next login shows the IdP form, and a
 *          different account (the SUPER_ADMIN) signs in — no store tab involved.
 *
 * 🔴 These run only in `nightly-e2e.yml` (job `platform-console-e2e-fullstack`), never in `ci.yml`.
 */

test.use({ storageState: { cookies: [], origins: [] } });

const SHELL_ACCOUNT = 'e2e-pool-shell@example.com';
const CREATOR_ACCOUNT = 'e2e-pool-creator@example.com';
const ROLES_ACCOUNT = 'e2e-pool-roles@example.com';
const ADMIN_BASE = process.env.E2E_ADMIN_BASE_URL ?? 'http://localhost:18085';
const CONSOLE_ORIGIN = process.env.E2E_CONSOLE_ORIGIN ?? 'http://localhost:3000';

async function cookie(context: BrowserContext, name: string): Promise<string | undefined> {
  return (await context.cookies()).find((c) => c.name === name)?.value;
}

/** The payload of a compact JWS — decode only (the issuer already verified it; we read what it signed). */
function jwtClaims(token: string): Record<string, unknown> {
  const parts = token.split('.');
  expect(parts, 'a compact JWS has three parts').toHaveLength(3);
  return JSON.parse(Buffer.from(parts[1], 'base64url').toString('utf8')) as Record<string, unknown>;
}

/** The `roles` claim; an ABSENT claim is «no role» (auth-api: an empty result is omitted, never `[]`). */
function rolesClaim(claims: Record<string, unknown>): string[] {
  const roles = claims.roles;
  if (roles === undefined) return [];
  expect(Array.isArray(roles), '`roles` is an array when present').toBe(true);
  return roles as string[];
}

const sorted = (xs: Iterable<string>): string[] => [...new Set(xs)].sort();

/**
 * The global-setup SUPER_ADMIN operator token (the storageState every other spec inherits — this file opts out
 * of it for its own browser, so it is read from the file the config names).
 */
function superAdminOperatorToken(): string {
  const path = test.info().project.use.storageState;
  if (typeof path !== 'string') throw new Error('expected playwright.config.ts to declare a string storageState path');
  const state = JSON.parse(readFileSync(path, 'utf8')) as { cookies: Array<{ name: string; value: string }> };
  const token = state.cookies.find((c) => c.name === 'console_operator_token')?.value;
  if (!token) throw new Error('global-setup storageState has no console_operator_token');
  return token;
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

  test('AC-2 roles — create → assume roles none → SUPER_ADMIN subscribes wms → re-assume → roles = the wms catalog, fan 0', async ({
    page,
    context,
  }) => {
    test.setTimeout(120_000);
    const admin = { Authorization: `Bearer ${superAdminOperatorToken()}` };

    // The catalog — read from its single owner (admin-service DomainRoleCatalog, admin-api § GET /domain-roles),
    // not typed here. Non-vacuity: the wms half is non-empty and the «other» half contains fan.
    const catalogRes = await page.request.get(`${ADMIN_BASE}/api/admin/domain-roles`, { headers: admin });
    expect(catalogRes.status(), await catalogRes.text()).toBe(200);
    const catalog = ((await catalogRes.json()) as { items: Array<{ domainKey: string; role: string }> }).items;
    const wmsCatalog = sorted(catalog.filter((e) => e.domainKey === 'wms').map((e) => e.role));
    const otherCatalog = sorted(
      catalog.filter((e) => e.domainKey !== 'wms' && !wmsCatalog.includes(e.role)).map((e) => e.role),
    );
    const wholeCatalog = sorted(catalog.map((e) => e.role));
    console.log(
      `[AC-2 roles] catalog ${catalog.length} entries · wms ${wmsCatalog.length} [${wmsCatalog.join(',')}] · ` +
        `other ${otherCatalog.length} [${otherCatalog.join(',')}]`,
    );
    expect(wmsCatalog.length, 'the wms catalog is non-empty').toBeGreaterThan(0);
    expect(otherCatalog, 'the «unsubscribed» half has fan in it').toContain('FAN_OPERATOR');

    // 1. A personal account creates a tenant through the real onboarding path (a CI retry may find it already an
    //    operator — same form, a second company; its new tenant is the one measured).
    await loginAsPoolAccount(
      context,
      ROLES_ACCOUNT,
      undefined,
      (url) => url.pathname === '/new-tenant' || url.pathname.startsWith('/dashboards'),
    );
    const slug = `e2e-r-${Date.now().toString(36)}`;
    await page.goto('/new-tenant');
    await page.getByTestId('onboarding-tenant-id').fill(slug);
    await page.getByTestId('onboarding-org-name').fill('E2E 773 Roles Company');
    await page.getByTestId('onboarding-submit').click();
    await page.waitForURL((url) => url.pathname.startsWith('/dashboards'), { timeout: 30_000 });
    expect(await cookie(context, 'console_active_tenant')).toBe(slug);

    // The admin half: the creator's ORIGIN `all-domains-full` row in the new tenant = the WHOLE catalog before
    // auth's ∩ (so the ∩ below has something to cut — fan is assigned, only the subscription keeps it out).
    const creatorOperatorToken = await cookie(context, 'console_operator_token');
    expect(creatorOperatorToken, 'creation made the account an operator').toBeTruthy();
    const operatorId = String(jwtClaims(creatorOperatorToken!).sub);
    const accessRes = await page.request.get(`${ADMIN_BASE}/api/admin/operators/${operatorId}/domain-access`, {
      headers: { ...admin, 'X-Tenant-Id': slug },
    });
    expect(accessRes.status(), await accessRes.text()).toBe(200);
    const access = (await accessRes.json()) as {
      roles: Array<{ role: string }>;
      sets: Array<{ systemKey: string | null; sources: Array<{ source: string; tenantId: string }> }>;
    };
    expect(access.sets.map((s) => `${s.systemKey}:${s.sources.map((x) => x.source).join('+')}`)).toEqual([
      'all-domains-full:ORIGIN',
    ]);
    expect(sorted(access.roles.map((r) => r.role)), 'assigned (pre-∩) = the whole catalog').toEqual(wholeCatalog);
    // Another tenant: the creator is not a member there, so it can hold no set row there (admin-api: set rows
    // only in a membership tenant). Row-level «0 rows elsewhere» is S3's IT; this is the same fact over the wire.
    const elsewhere = await page.request.get(`${ADMIN_BASE}/api/admin/operators/${operatorId}/domain-access`, {
      headers: { ...admin, 'X-Tenant-Id': 'fan-platform' },
    });
    expect(elsewhere.status(), 'no membership — so no set — in a tenant it did not create').toBe(404);

    // 2. Control — 0 subscriptions ⇒ no role, on BOTH the creation exchange and a fresh switch exchange (the same
    //    path step 4 uses, so the only difference between control and measurement is the subscription).
    //    `expect.soft`: a red here still lets steps 3-5 report, so one run shows every assertion that bites.
    const atCreation = jwtClaims((await cookie(context, 'console_assumed_token'))!);
    expect(atCreation.tenant_id).toBe(slug);
    console.log(`[AC-2 roles] at creation: roles=${JSON.stringify(atCreation.roles ?? null)} entitled=${JSON.stringify(atCreation.entitled_domains ?? null)}`);
    expect.soft(rolesClaim(atCreation), 'right after creation the new tenant has no subscription ⇒ no role').toEqual([]);

    const before = await page.request.post(`${CONSOLE_ORIGIN}/api/tenant`, { data: { tenant: slug } });
    expect(before.status(), await before.text()).toBe(200);
    const beforeToken = (await cookie(context, 'console_assumed_token'))!;
    const beforeClaims = jwtClaims(beforeToken);
    console.log(`[AC-2 roles] before subscription: roles=${JSON.stringify(beforeClaims.roles ?? null)} entitled=${JSON.stringify(beforeClaims.entitled_domains ?? null)}`);
    expect(beforeClaims.tenant_id).toBe(slug);
    expect.soft(rolesClaim(beforeClaims), 'a fresh exchange before any subscription ⇒ still no role').toEqual([]);

    // 3. SUPER_ADMIN subscribes the new tenant to wms — the real admin API (admin-api § POST /subscriptions).
    const subscribe = await page.request.post(`${ADMIN_BASE}/api/admin/subscriptions`, {
      headers: { ...admin, 'X-Tenant-Id': slug, 'X-Operator-Reason': 'e2e-TASK-MONO-773-AC-2' },
      data: { tenantId: slug, domainKey: 'wms' },
    });
    expect(subscribe.status(), await subscribe.text()).toBe(201);

    // 4. A NEW exchange (the switch always mints — no waiting out the 300 s TTL; revocation lag is irrelevant to
    //    a grant that widens).
    const after = await page.request.post(`${CONSOLE_ORIGIN}/api/tenant`, { data: { tenant: slug } });
    expect(after.status(), await after.text()).toBe(200);
    const afterToken = (await cookie(context, 'console_assumed_token'))!;
    expect(afterToken, 'a new assume token, not the cached one').not.toBe(beforeToken);
    const afterClaims = jwtClaims(afterToken);
    const roles = rolesClaim(afterClaims);
    console.log(
      `[AC-2 roles] after wms subscription: roles ${roles.length} [${roles.join(',')}] entitled=${JSON.stringify(afterClaims.entitled_domains ?? null)}`,
    );

    // 5. roles = the whole wms catalog; nothing from fan or any other unsubscribed domain.
    expect(afterClaims.tenant_id).toBe(slug);
    expect(afterClaims.entitled_domains, 'the subscription is visible to auth').toEqual(['wms']);
    expect(sorted(roles), 'roles = the whole wms catalog').toEqual(wmsCatalog);
    expect(roles.filter((r) => otherCatalog.includes(r)), 'no role of an unsubscribed domain').toEqual([]);
    expect(roles).not.toContain('FAN_OPERATOR');
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
