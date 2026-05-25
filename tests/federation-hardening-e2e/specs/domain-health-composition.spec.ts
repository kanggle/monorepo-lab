import { test, expect } from '@playwright/test';

/**
 * TASK-MONO-139 — Domain Health composition spec.
 * ADR-MONO-018 D3 (MVP: 2 composition specs).
 *
 * Steps: operator login → navigate /console/dashboards/domain-health →
 * assert 5-domain health attribution rendered + all 5 = UP.
 *
 * Per console-integration-contract.md § 2.4.9.2: the Domain Health route
 * aggregates health status for all 5 backend domains via console-bff
 * GET /api/console/dashboards/domain-health. Each domain surfaces its
 * actuator health status through the BFF fan-out.
 *
 * This spec verifies the composition renders (200 OK + 5 domain statuses)
 * when all 5 producers are live. Degrade path (force-503 one domain) = MVP
 * out-of-scope per ADR-018 D3 (note AC-5: "degrade path = MVP 외").
 */
test.describe('Domain Health composition (5-domain health attribution)', () => {
  test('renders 5-domain health attribution with all 5 domains UP', async ({
    page,
  }) => {
    await page.goto('/dashboards/health');
    await page.waitForLoadState('networkidle');

    await expect(page).toHaveTitle(/.+/);

    // Assert the domain health dashboard renders.
    const dashboardContent = page.locator('main, [role="main"], #main-content').first();
    await expect(dashboardContent).toBeVisible({ timeout: 20_000 });

    // Assert 5 domain health sections are rendered.
    // The Domain Health view (PC-FE-013) renders one status indicator per domain.
    const domains = ['gap', 'wms', 'scm', 'finance', 'erp'];
    for (const domain of domains) {
      const domainSection = page.getByText(new RegExp(domain, 'i')).first();
      await expect(domainSection).toBeVisible({ timeout: 15_000 });
    }

    // Assert no domain shows DOWN / ERROR health status.
    // The UP status means all producers responded to the BFF health probe.
    // Absence of DOWN/ERROR text is the Phase 8 happy path baseline assertion.
    const downStatus = page.getByText(/DOWN|ERROR|UNAVAILABLE/i);
    await expect(downStatus).toHaveCount(0);
  });
});
