import { test, expect } from '@playwright/test';

/**
 * TASK-PC-FE-016 — full vertical slice e2e for the operator profile
 * self-serve UI ("Operator Overview" finance card MISSING_PREREQUISITE
 * resolution chain Phase 3 / Phase 4 = end-to-end activation).
 *
 * Activated by TASK-PC-FE-019: the platform-console Playwright e2e harness
 * (docker-compose.e2e.yml + seed.sql + login fixture + nightly CI job) is
 * now in place. The previous `test.skip(true, …)` guard has been removed.
 *
 * Identity — the global setup primes the BrowserContext with the seeded
 * SUPER_ADMIN cookies. Tenant = 'fan-platform' (matches the seeded finance
 * account row's tenant_id; the SUPER_ADMIN's platform-scope token can read
 * any tenant).
 *
 * Scenario:
 *   1. navigate to /operators
 *   2. enter the seeded finance account UUID into MyProfileForm
 *   3. click Save → success message visible
 *   4. navigate to /dashboards/overview
 *   5. assert the finance card renders `ok` (NOT `forbidden /
 *      MISSING_PREREQUISITE`).
 *
 * Failure modes (each maps to a documented architecture defect, not a
 * test flake — investigate at impl-PR cycle):
 *   - my-profile-success never visible → admin /api/admin/me/profile
 *     contract drift or the proxy route /api/operators/me/profile is
 *     mis-wired.
 *   - finance card stays `forbidden` after Save → the GAP registry's
 *     productItem[finance].operatorContext.defaultAccountId is not being
 *     refreshed (consumer caching surface).
 *   - finance card flips to `down` → check finance-account-service health
 *     in docker compose logs (the seeded account / balance row may be
 *     missing if seed.sql failed silently).
 */
test.describe('@e2e operators profile — finance default account self-serve', () => {
  test('operator can self-set finance default account → overview shows ok', async ({
    page,
  }) => {
    await page.goto('/operators');

    const input = page.getByTestId('my-profile-default-account-id');
    await expect(input).toBeVisible();
    await input.fill('01928c4a-7e9f-7c00-9a40-d2b1f5e8a000');
    await page.getByTestId('my-profile-save').click();
    await expect(page.getByTestId('my-profile-success')).toBeVisible();

    await page.goto('/dashboards/overview');
    // The finance card on overview must render `ok` with balance data
    // (NOT `forbidden / MISSING_PREREQUISITE`). The data-status attribute
    // is owned by features/operator-overview/components/DomainCard.tsx.
    // TASK-PC-FE-030 — testid uses the `operator-overview-card-<domain>`
    // convention the component established (sibling internal testids
    // `operator-overview-card-finance-status` / `-currency` follow the
    // same prefix). The original `domain-card-finance` name was the
    // `aria-labelledby` id, not the testid.
    const financeCard = page.getByTestId('operator-overview-card-finance');
    await expect(financeCard).toHaveAttribute('data-status', 'ok');
  });
});
