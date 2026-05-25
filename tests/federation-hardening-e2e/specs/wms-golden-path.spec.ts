import { test, expect } from '@playwright/test';

/**
 * TASK-MONO-139 — WMS golden-path spec.
 * ADR-MONO-018 D3 (MVP: 5 golden-path specs).
 *
 * Steps: operator login → navigate /console/wms/warehouses →
 * assert wms warehouse list renders 200 OK + seed warehouse row visible.
 *
 * Per-domain credential rule (console-integration-contract.md § 2.4.5):
 * wms uses the GAP OIDC access token (getAccessToken()) — NOT the operator
 * token. The SUPER_ADMIN JWT with tenant_id='*' is accepted by wms
 * TenantClaimValidator (wildcard sentinel per TenantClaimValidator contract).
 *
 * The seeded warehouse (seed-wms.sql): code=E2E-WH-01, name='E2E Test Warehouse'.
 *
 * Degrade path = MVP out-of-scope per ADR-018 D3.
 */
test.describe('WMS golden-path (warehouse list)', () => {
  test('navigates to WMS warehouses page and renders seed warehouse row', async ({
    page,
  }) => {
    await page.goto('/console/wms/warehouses');
    await page.waitForLoadState('networkidle');

    // Assert page is not a blank shell or error page.
    await expect(page).toHaveTitle(/.+/);

    // Assert seed warehouse is visible.
    // wms warehouse list should render the seeded row (code=E2E-WH-01).
    const warehouseRow = page.getByText('E2E-WH-01');
    await expect(warehouseRow).toBeVisible({ timeout: 15_000 });
  });
});
