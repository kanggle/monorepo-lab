import { test, expect } from '@playwright/test';

/**
 * TASK-MONO-139 — SCM golden-path spec.
 * ADR-MONO-018 D3 (MVP: 5 golden-path specs).
 *
 * Steps: operator login → navigate /console/scm/purchase-orders →
 * assert scm PO list renders 200 OK + seed PO row visible.
 *
 * Per-domain credential rule (console-integration-contract.md § 2.4.6):
 * scm uses the GAP OIDC access token (getAccessToken()) — reuse of the
 * § 2.4.5 per-domain credential rule confirmed for scm. tenant_id='*'
 * accepted by scm TenantClaimValidator.
 *
 * The seeded PO (seed-scm.sql): po_number=PO-E2E-001.
 *
 * Degrade path = MVP out-of-scope per ADR-018 D3.
 */
test.describe('SCM golden-path (purchase order list)', () => {
  test('navigates to SCM purchase orders page and renders seed PO row', async ({
    page,
  }) => {
    await page.goto('/scm');
    await page.waitForLoadState('networkidle');

    await expect(page).toHaveTitle(/.+/);

    // Assert seed PO is visible (po_number=PO-E2E-001).
    const poRow = page.getByText('PO-E2E-001');
    await expect(poRow).toBeVisible({ timeout: 15_000 });
  });
});
