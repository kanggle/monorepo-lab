import { test, expect } from '@playwright/test';

import {
  MULTI_OPERATOR,
  loginAsFederationOperator,
  shouldSkipFederation,
  switchActiveTenant,
} from './fixtures/federation';

/**
 * TASK-PC-FE-113 — promoted from the throwaway `verify-ecommerce-sellers-multi.mjs`
 * demo script (multi-operator tenant switch → ecommerce sellers render).
 *
 * Proves the UNIFIED-console path on the federation-hardening-e2e stack: a
 * multi-assignment operator logs in, switches the active tenant to `ecommerce`
 * via the real `POST /api/tenant` assume-tenant exchange (mints a token with
 * derived roles=[ADMIN] per BE-376), then the `/ecommerce/sellers` operator
 * surface (TASK-PC-FE-090, ADR-MONO-031 § 2.4.10) renders the seller list —
 * NOT the 403/degraded placeholder.
 *
 * Federation-gated (`PC_FEDERATION_E2E=1`): the ecommerce domain backend is not
 * part of the console-web CI e2e stack, so this SKIPS there and RUNS only
 * against the federation demo stack. See `fixtures/federation.ts`.
 */

// SUPER_ADMIN storageState (globalSetup) would land tenant=fan-platform with a
// wildcard scope and mask the per-tenant switch — override to empty and log in
// fresh as the multi-operator so the assume-tenant re-scope is the real path.
test.use({ storageState: { cookies: [], origins: [] } });

const EXPECTED_SELLERS = [
  'Default Seller',
  'Globex Online Store',
  'Initech Mart',
] as const;

test.describe('@federation multi-operator → switch ecommerce → sellers render (TASK-PC-FE-113)', () => {
  test.skip(
    shouldSkipFederation(),
    'PC_FEDERATION_E2E!=1 — ecommerce backend only on the federation demo stack',
  );

  test('switching to ecommerce renders the seller list (not forbidden/degraded)', async ({
    browser,
  }) => {
    // The federation demo stack's OIDC login + assume-tenant exchange is slow
    // (cold JIT / cross-service hops) — give the full login→switch→render chain
    // generous headroom over the 30s default.
    test.setTimeout(120_000);
    const context = await browser.newContext({
      storageState: { cookies: [], origins: [] },
    });
    try {
      await loginAsFederationOperator(context, MULTI_OPERATOR);
      const page = await context.newPage();

      // Real assume-tenant switch → token re-scoped to ecommerce.
      await switchActiveTenant(page, 'ecommerce');

      await page.goto('/ecommerce/sellers');
      await expect(
        page.getByRole('heading', { name: 'E-Commerce 셀러 운영' }),
      ).toBeVisible({ timeout: 20_000 });

      // The gate must NOT reject — assert the section did not degrade/forbid.
      await expect(page.getByTestId('seller-forbidden')).toHaveCount(0);
      await expect(page.getByTestId('seller-degraded')).toHaveCount(0);

      // The seeded sellers render in the table (replaces the demo script's
      // `document.body.innerText` substring match with the committed testid +
      // role-based cell assertions).
      await expect(page.getByTestId('seller-table')).toBeVisible();
      for (const name of EXPECTED_SELLERS) {
        await expect(
          page.getByRole('cell', { name, exact: true }),
        ).toBeVisible();
      }
    } finally {
      await context.close();
    }
  });
});
