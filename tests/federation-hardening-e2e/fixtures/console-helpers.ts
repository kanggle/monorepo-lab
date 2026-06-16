import { expect, type BrowserContext, type Page } from '@playwright/test';

/**
 * TASK-MONO-280 — shared console-web route-driving helpers for the
 * federation-hardening-e2e operator-overview / tenant-switch specs.
 *
 * `gotoOverview` was byte-identical in entitlement-trust-crossdomain.spec.ts
 * (MONO-154) and tenant-switch-rescope.spec.ts (MONO-158); `switchTenant` drove
 * the real /api/tenant assume-tenant exchange in both tenant-switch-rescope.spec.ts
 * (MONO-158) and subscription-plane-separation.spec.ts (MONO-207). Behavior-
 * preserving extraction (the per-customer entitlement assertions stay in each
 * spec). NOTE: the `switchTenant` failure-diagnostic message is unified here; the
 * 200-or-bust outcome is unchanged.
 */

/** Navigate to the Operator Overview composition route and wait for the 5-card
 *  grid to render (the 200 path). */
export async function gotoOverview(page: Page): Promise<void> {
  await page.goto('/dashboards/overview');
  await page.waitForLoadState('networkidle');
  await expect(page).toHaveURL(/\/dashboards\/overview(\?|$)/);
  await expect(page.getByTestId('operator-overview-cards')).toBeVisible({ timeout: 20_000 });
}

/** Drive the real active-tenant switcher → server-side assume-tenant (RFC 8693)
 *  exchange. A 200 also asserts the IAM-plane operator_tenant_assignment is intact
 *  (the assume-tenant D2 gate would 403 otherwise). */
export async function switchTenant(ctx: BrowserContext, tenant: string): Promise<void> {
  const res = await ctx.request.post('/api/tenant', { data: { tenant } });
  expect(
    res.status(),
    `switch to ${tenant} should succeed (operator_tenant_assignment present → assume-tenant minted)`,
  ).toBe(200);
  expect((await res.json()).activeTenant).toBe(tenant);
}
