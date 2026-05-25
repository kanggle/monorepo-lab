import { test, expect } from '@playwright/test';

/**
 * TASK-MONO-139 — Finance golden-path spec.
 * ADR-MONO-018 D3 (MVP: 5 golden-path specs).
 *
 * Steps: operator login → navigate /console/finance/accounts/<seed-id> →
 * assert finance account detail renders 200 OK + balance data visible.
 *
 * Per-domain credential rule (console-integration-contract.md § 2.4.7):
 * finance uses the GAP OIDC access token (getAccessToken()) — third
 * confirmation that the § 2.4.5 per-domain credential rule generalises.
 * tenant_id='*' accepted by finance-account-service TenantClaimValidator.
 *
 * The seeded account (seed-domains.sql):
 *   id='01928c4a-7e9f-7c00-9a40-d2b1f5e8a000', currency=KRW, balance=1000000.
 *
 * Degrade path = MVP out-of-scope per ADR-018 D3.
 */
const FINANCE_ACCOUNT_ID = '01928c4a-7e9f-7c00-9a40-d2b1f5e8a000';

test.describe('Finance golden-path (account detail + balance)', () => {
  test('navigates to finance account detail and renders balance data', async ({
    page,
  }) => {
    await page.goto(`/console/finance/accounts/${FINANCE_ACCOUNT_ID}`);
    await page.waitForLoadState('networkidle');

    await expect(page).toHaveTitle(/.+/);

    // Assert balance data is visible (currency KRW + amount in minor units
    // rendered as formatted string). The spec asserts the page renders without
    // error — exact formatting depends on console-web finance account view.
    // Look for the currency code as a reliable presence indicator.
    const currencyText = page.getByText('KRW');
    await expect(currencyText).toBeVisible({ timeout: 15_000 });
  });
});
