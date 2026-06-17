import { test, expect } from '@playwright/test';

import {
  OMNI_OPERATOR,
  loginAsFederationOperator,
  shouldSkipFederation,
  switchActiveTenant,
} from './fixtures/federation';

/**
 * TASK-PC-FE-113 — promoted from the throwaway `verify-omni-all-domains.mjs`
 * demo script (omni-corp 5-domain reachability).
 *
 * Proves that the all-in-one `omni-corp` tenant (a single tenant subscribed to
 * all five business domains — see project memory
 * `project_omni_all_domain_test_tenant`) re-scopes the operator's signed token
 * so EVERY domain card on the cross-domain overview is entitled. The omni
 * operator switches the active tenant to `omni-corp` via the real
 * `POST /api/tenant` assume-tenant exchange, then the `/dashboards/overview`
 * composition route's per-domain cards must all be NOT forbidden.
 *
 * Replaces the demo script's "visit every section page + innerText substring"
 * sweep with the overview-card assertion (same discriminator the federation
 * entitlement-trust spec uses): `data-status` per `operator-overview-card-{domain}`
 * + the inner `-forbidden` placeholder. Cleaner and stable — the gate decision
 * is observed directly rather than via per-page error strings + hard sleeps.
 *
 * Federation-gated (`PC_FEDERATION_E2E=1`): the 5 domain backends + omni-corp
 * seed exist only on the federation demo stack. See `fixtures/federation.ts`.
 */

// Override the SUPER_ADMIN storageState — the omni operator must log in fresh so
// its omni-corp assume-tenant token drives the per-domain gate (the wildcard
// SUPER_ADMIN scope would mask the entitlement decision).
test.use({ storageState: { cookies: [], origins: [] } });

// The 5 business-domain cards the overview renders (operator-overview-types.ts).
// omni-corp entitles all five, so none may be `forbidden`.
const OMNI_DOMAINS = ['ecommerce', 'scm', 'wms', 'erp', 'finance'] as const;

test.describe('@federation omni-operator → switch omni-corp → all 5 domain cards entitled (TASK-PC-FE-113)', () => {
  test.skip(
    shouldSkipFederation(),
    'PC_FEDERATION_E2E!=1 — 5 domain backends + omni-corp seed only on the federation demo stack',
  );

  test('every domain card is entitled (not forbidden) under the omni-corp assumed token', async ({
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
      await loginAsFederationOperator(context, OMNI_OPERATOR);
      const page = await context.newPage();

      // Real assume-tenant switch → token re-scoped to omni-corp (all 5 domains).
      await switchActiveTenant(page, 'omni-corp');

      await page.goto('/dashboards/overview');
      await expect(page.getByTestId('operator-overview-cards')).toBeVisible({
        timeout: 20_000,
      });

      for (const domain of OMNI_DOMAINS) {
        const card = page.getByTestId(`operator-overview-card-${domain}`);
        await expect(card, `${domain} card should be present`).toBeVisible({
          timeout: 20_000,
        });
        // Entitled → the gate must NOT reject. Assert not-forbidden (ok with
        // live data or at minimum degraded, but never the gate rejection),
        // identical to the federation entitlement-trust discriminator.
        await expect(
          card,
          `${domain} is entitled for omni-corp — gate must not reject (not forbidden)`,
        ).not.toHaveAttribute('data-status', 'forbidden');
        await expect(
          page.getByTestId(`operator-overview-card-${domain}-forbidden`),
        ).toHaveCount(0);
      }
    } finally {
      await context.close();
    }
  });
});
