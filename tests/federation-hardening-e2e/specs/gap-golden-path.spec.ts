import { test, expect } from '@playwright/test';

/**
 * TASK-MONO-139 — GAP golden-path spec.
 * ADR-MONO-018 D3 (MVP: 7 spec files, 5 golden-path + 2 composition).
 *
 * Steps: operator login (SUPER_ADMIN via stored storageState) →
 * navigate /console/gap/operators → assert page renders + 1+ row.
 *
 * Per-domain credential rule (console-integration-contract.md § 2.4.1–2.4.4):
 * GAP uses the exchanged operator token (getOperatorToken()) — handled by
 * console-web server-side routes. This spec does not assert header shapes
 * directly; it asserts the composition renders (which requires the credential
 * to be correct end-to-end).
 *
 * Degrade path (force-pause one domain) = MVP out-of-scope per ADR-018 D3.
 */
test.describe('GAP golden-path (operator registry list)', () => {
  test('navigates to GAP operators page and renders registry list with at least one row', async ({
    page,
  }) => {
    // storageState already loaded from globalSetup (SUPER_ADMIN session).
    await page.goto('/operators');

    // Wait for the page to stabilize — operators list panel
    await page.waitForLoadState('networkidle');

    // Assert page title / heading is present (not a blank page or error).
    // The GAP operators page renders a list of admin operators.
    await expect(page).toHaveTitle(/.+/);

    // Assert at least one operator row is visible.
    // The seeded SUPER_ADMIN row (e2e-super-admin) + e2e-target-operator row
    // should appear. Use data-testid or a table row locator — the exact
    // selector depends on console-web's operator list component.
    // Using a permissive text match for the seeded operator email.
    const operatorRow = page.getByText('e2e-super-admin@example.com');
    await expect(operatorRow).toBeVisible({ timeout: 15_000 });
  });
});
