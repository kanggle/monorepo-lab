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
 *   - "도메인 상태" has NO sidebar entry (TASK-PC-FE-068) — reached from the
 *     개요 "도메인 상태 요약" card "전체 보기 →"; the page has a back link;
 *   - a new "ERP 운영" (nav-erp → /erp) entry renders the ERP ops screen;
 *   - the home IAM card is an accessible drill-down link to `/dashboards`;
 *     activating it renders the GAP-only composed overview (re-framed as
 *     "IAM 상세 …") with a back link to /dashboards/overview.
 *
 * The seeded SUPER_ADMIN can read every leg, so the IAM card renders `ok`
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
    // No nav item points at the bare /dashboards (IAM detail = card-reached).
    await expect(
      page.locator('nav a[href="/dashboards"]'),
    ).toHaveCount(0);
  });

  test('"도메인 상태" is reached from the 개요 "도메인 상태 요약" card (no sidebar entry) + the page has a back link (TASK-PC-FE-068)', async ({
    page,
  }) => {
    await page.goto('/dashboards/overview');
    // The top-level sidebar "도메인 상태" entry is removed.
    await expect(page.getByTestId('nav-domain-health')).toHaveCount(0);
    // It is reached via the 도메인 상태 요약 card's "전체 보기 →" link (PC-FE-061).
    const viewAll = page.getByTestId('domain-health-summary-viewall');
    await expect(viewAll).toBeVisible();
    await expect(viewAll).toHaveAttribute('href', '/dashboards/health');
    await viewAll.click();
    await page.waitForURL('**/dashboards/health', { timeout: 15_000 });
    // The page carries a back link to the 통합 개요.
    const back = page.getByTestId('domain-health-back');
    await expect(back).toBeVisible();
    await expect(back).toHaveAttribute('href', '/dashboards/overview');
    await back.click();
    await page.waitForURL('**/dashboards/overview', { timeout: 15_000 });
  });

  test('new "ERP 운영" nav entry → /erp renders the ERP ops screen', async ({
    page,
  }) => {
    await page.goto('/dashboards/overview');
    const erp = page.getByTestId('nav-erp');
    await expect(erp).toBeVisible();
    await expect(erp).toHaveAttribute('href', '/erp');
    await expect(erp).toHaveText('ERP');
    await erp.click();
    await page.waitForURL('**/erp', { timeout: 15_000 });
    await expect(
      page.getByRole('heading', { name: 'ERP 운영' }),
    ).toBeVisible();
  });

  test('IAM card on the home overview drills down to the IAM detail (/dashboards)', async ({
    page,
  }) => {
    await page.goto('/dashboards/overview');
    const gapCard = page.getByTestId('operator-overview-card-iam');
    await expect(gapCard).toHaveAttribute('data-status', 'ok');

    const drilldown = page.getByTestId('operator-overview-card-iam-drilldown');
    await expect(drilldown).toBeVisible();
    await expect(drilldown).toHaveAttribute('href', '/dashboards');

    // Keyboard-focusable (AC-5 a11y): the link is reachable + activatable.
    await drilldown.focus();
    await expect(drilldown).toBeFocused();

    await drilldown.click();
    await page.waitForURL('**/dashboards', { timeout: 15_000 });
    // The IAM detail is re-framed as the drill-down + offers a back link.
    await expect(
      page.getByRole('heading', { name: 'IAM 상세 (계정 · 감사 · 운영자)' }),
    ).toBeVisible();
    const back = page.getByTestId('iam-detail-back-link');
    await expect(back).toBeVisible();
    await expect(back).toHaveAttribute('href', '/dashboards/overview');
  });
});
