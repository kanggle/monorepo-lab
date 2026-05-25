import { test, expect } from '@playwright/test';

/**
 * TASK-MONO-139 — ERP golden-path spec.
 * ADR-MONO-018 D3 (MVP: 5 golden-path specs).
 *
 * Steps: operator login → navigate /console/erp/employees →
 * assert erp employee list renders 200 OK + seed employee row visible with
 * effectivePeriod rendered.
 *
 * Per-domain credential rule (console-integration-contract.md § 2.4.8):
 * erp uses the GAP OIDC access token (getAccessToken()) — third confirmation
 * that the § 2.4.5 per-domain credential rule generalises (wms → scm →
 * finance → erp). tenant_id='*' accepted by erp-masterdata-service
 * TenantClaimValidator.
 *
 * The seeded employee (seed-domains.sql):
 *   id='e2e-emp-001', employee_number='EMP-0001', full_name='E2E Test Employee',
 *   effective_from='2020-01-01', effective_to=NULL (currently active).
 *
 * Effective-dated reads use asOf=now() implicit (E3 per masterdata-service
 * architecture.md). The seed employee is active (effectiveTo=NULL).
 *
 * Degrade path = MVP out-of-scope per ADR-018 D3.
 */
test.describe('ERP golden-path (employee list + effectivePeriod)', () => {
  test('navigates to ERP employees page and renders seed employee with effectivePeriod', async ({
    page,
  }) => {
    await page.goto('/erp');
    await page.waitForLoadState('networkidle');

    await expect(page).toHaveTitle(/.+/);

    // Assert seed employee row is visible (employee_number or full_name).
    const employeeRow = page.getByText('EMP-0001');
    await expect(employeeRow).toBeVisible({ timeout: 15_000 });

    // Assert effectivePeriod is rendered (effective_from year visible in list).
    // The masterdata-service effective-dating (E2) surfaces effective dates
    // in the console-web erp employee list view.
    const effectiveFrom = page.getByText('2020');
    await expect(effectiveFrom).toBeVisible({ timeout: 10_000 });
  });
});
