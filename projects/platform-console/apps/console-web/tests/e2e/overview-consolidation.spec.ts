import { test, expect } from '@playwright/test';

/**
 * TASK-PC-FE-034 — overview consolidation e2e.
 *
 * Runs against the full docker-compose.e2e.yml stack with the seeded
 * SUPER_ADMIN storageState (playwright.config.ts globalSetup). Verifies the
 * consolidated landing/nav hierarchy + the GAP-card drill-down:
 *
 *   - root `/` lands on the 5-domain cross-domain overview
 *     (`/dashboards/overview`);
 *   - the top nav has exactly one "개요" entry (nav-dashboards →
 *     /dashboards/overview); the old "통합 개요" (nav-operator-overview)
 *     entry is gone;
 *   - "도메인 상태" (nav-domain-health) is unchanged + still reachable;
 *   - a new "ERP 운영" (nav-erp → /erp) entry renders the ERP ops screen;
 *   - the home GAP card is an accessible drill-down link to `/dashboards`;
 *     activating it renders the GAP-only composed overview (re-framed as
 *     "GAP 상세 …") with a back link to /dashboards/overview.
 *
 * The seeded SUPER_ADMIN can read every leg, so the GAP card renders `ok`
 * and the drill-down affordance is present (AC-6).
 */
test.describe('@e2e overview consolidation (TASK-PC-FE-034)', () => {
  test('root / lands on the 5-domain cross-domain overview', async ({
    page,
  }) => {
    await page.goto('/');
    await page.waitForURL('**/dashboards/overview', { timeout: 15_000 });
    await expect(
      page.getByRole('heading', { name: '운영자 통합 개요' }),
    ).toBeVisible();
    await expect(page.getByTestId('operator-overview-cards')).toBeVisible();
  });

  test('top nav has a single "개요" entry → /dashboards/overview; no "통합 개요"', async ({
    page,
  }) => {
    await page.goto('/dashboards/overview');
    const dashboards = page.getByTestId('nav-dashboards');
    await expect(dashboards).toBeVisible();
    await expect(dashboards).toHaveAttribute('href', '/dashboards/overview');
    await expect(dashboards).toHaveText('개요');
    // The previous separate "통합 개요" entry is removed.
    await expect(page.getByTestId('nav-operator-overview')).toHaveCount(0);
    // No nav item points at the bare /dashboards (GAP detail = card-reached).
    await expect(
      page.locator('nav a[href="/dashboards"]'),
    ).toHaveCount(0);
  });

  test('"도메인 상태" nav entry is unchanged + reachable', async ({ page }) => {
    await page.goto('/dashboards/overview');
    const health = page.getByTestId('nav-domain-health');
    await expect(health).toBeVisible();
    await expect(health).toHaveAttribute('href', '/dashboards/health');
    await health.click();
    await page.waitForURL('**/dashboards/health', { timeout: 15_000 });
  });

  test('new "ERP 운영" nav entry → /erp renders the ERP ops screen', async ({
    page,
  }) => {
    await page.goto('/dashboards/overview');
    const erp = page.getByTestId('nav-erp');
    await expect(erp).toBeVisible();
    await expect(erp).toHaveAttribute('href', '/erp');
    await expect(erp).toHaveText('ERP 운영');
    await erp.click();
    await page.waitForURL('**/erp', { timeout: 15_000 });
    await expect(
      page.getByRole('heading', { name: 'ERP 운영' }),
    ).toBeVisible();
  });

  test('GAP card on the home overview drills down to the GAP detail (/dashboards)', async ({
    page,
  }) => {
    await page.goto('/dashboards/overview');
    const gapCard = page.getByTestId('operator-overview-card-gap');
    await expect(gapCard).toHaveAttribute('data-status', 'ok');

    const drilldown = page.getByTestId('operator-overview-card-gap-drilldown');
    await expect(drilldown).toBeVisible();
    await expect(drilldown).toHaveAttribute('href', '/dashboards');

    // Keyboard-focusable (AC-5 a11y): the link is reachable + activatable.
    await drilldown.focus();
    await expect(drilldown).toBeFocused();

    await drilldown.click();
    await page.waitForURL('**/dashboards', { timeout: 15_000 });
    // The GAP detail is re-framed as the drill-down + offers a back link.
    await expect(
      page.getByRole('heading', { name: 'GAP 상세 (계정 · 감사 · 운영자)' }),
    ).toBeVisible();
    const back = page.getByTestId('gap-detail-back-link');
    await expect(back).toBeVisible();
    await expect(back).toHaveAttribute('href', '/dashboards/overview');
  });
});
