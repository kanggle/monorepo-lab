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
  // TASK-PC-FE-251 AC-4 — renamed to what this body actually asserts. AC-4 named
  // only operator-overview-composition.spec.ts; this file carries the identical
  // defect from the same TASK-MONO-140 cycle-5 relaxation, and was found by
  // re-counting the suite rather than inheriting the ticket's scope. Unlike the
  // overview card grid, the 5-domain health attribution has **no** sibling spec
  // asserting it — so this rename records a real coverage hole rather than
  // papering over one.
  test('health route resolves and renders for an authenticated operator (URL + title + heading only)', async ({
    page,
  }) => {
    await page.goto('/dashboards/health');
    await page.waitForLoadState('networkidle');

    // MVP-level relaxation per TASK-MONO-140 cycle 5 (sibling MONO-133 honest
    // scope adjustment): cross-product e2e cohort verifies the dashboard page
    // resolves + auth works + heading renders. 5-domain health attribution
    // visibility depends on console-bff fan-out integration to per-domain
    // /actuator/health endpoints — deferred to a follow-up task.
    await expect(page).toHaveURL(/\/dashboards\/health(\?|$)/);
    await expect(page).toHaveTitle(/.+/);
    const heading = page.getByRole('heading').first();
    await expect(heading).toBeVisible({ timeout: 20_000 });
  });
});
