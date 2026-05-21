import { test, expect } from '@playwright/test';

/**
 * TASK-PC-FE-016 — full vertical slice e2e for the operator profile
 * self-serve UI (`Operator Overview` finance card MISSING_PREREQUISITE
 * resolution chain Phase 3 / Phase 4 = end-to-end activation).
 *
 * Scenario:
 *   1. operator login (seeded test fixture)
 *   2. navigate to /operators
 *   3. enter a known-good finance account UUID into MyProfileForm
 *   4. click Save → 204 (or "저장되었습니다" success message)
 *   5. navigate to /dashboards/overview
 *   6. assert the finance card renders `ok` with balance data
 *      (NOT `forbidden / MISSING_PREREQUISITE`)
 *
 * SKIPPED by default: the platform-console e2e folder is empty in this
 * repo (no historical Playwright suite yet — `playwright.config.ts` is
 * configured but unused). Standing up the full GAP+finance+console
 * docker-compose stack with a seeded `admin_operators` row + a known
 * finance account UUID is a substantial fixture cycle that is OUT OF
 * SCOPE of this impl PR. The unit + integration coverage already
 * exercises every vertical-slice leg:
 *
 *   - MyProfileForm behaviour (form-level): tests/unit/features/operators/
 *     MyProfileForm.test.tsx (10 cases — initial / save / clear /
 *     whitespace / server-error / success / pending);
 *   - proxy route (/api/operators/me/profile): tests/unit/api/operators/
 *     me-profile-route.test.ts (9 cases — 204 / 422 / 409 / 503 / NO
 *     active tenant / explicit null);
 *   - api fn call-shape (HEADER MATRIX § 2.4.3 row 6): tests/unit/
 *     features/operators/operators-api-update-profile.test.ts (2 cases —
 *     string and null body);
 *   - parity matrix row 17 (operators: change-profile, mutation/none):
 *     tests/unit/parity-matrix.ts + parity-verification.test.ts
 *     (auto-iterated — operator-token bearer + tenant + header obligation
 *     `none` honored across the row, ABSENT operator token ⇒ 401 with
 *     NO fetch, NO active tenant ⇒ NO fetch);
 *   - registry consumer (PC-FE-014 already merged) parses
 *     `operatorContext.defaultAccountId` cleanly.
 *
 * The finance-card `ok / balances` round-trip after save is observable
 * via the existing TASK-PC-FE-014 integration test (BE side: console-bff
 * IT) plus the FE consumer test
 * (tests/unit/features/operator-overview/finance-option-a-render.test.tsx).
 * The remaining e2e gap is the manual browser click sequence — a
 * follow-up "platform-console e2e harness" task can light up this spec
 * along with login/catalog/tenant-switch e2e specs the playwright.config
 * already anticipates.
 *
 * To run locally once the harness is up:
 *   pnpm --filter console-web exec playwright test operators-profile
 */
test.describe('@e2e operators profile — finance default account self-serve', () => {
  test.skip(
    true,
    'platform-console e2e harness not yet stood up (see file comment);'
      + ' vertical slice coverage is provided by unit + integration tests'
      + ' (PC-FE-016 + PC-FE-014 + BE-306 IT).',
  );

  test('operator can self-set finance default account → overview shows ok', async ({
    page,
  }) => {
    await page.goto('/login');
    // TODO: login fixture (seeded operator); see playwright.config.ts.
    await page.goto('/operators');
    const input = page.getByTestId('my-profile-default-account-id');
    await input.fill('01928c4a-7e9f-7c00-9a40-d2b1f5e8a000');
    await page.getByTestId('my-profile-save').click();
    await expect(page.getByTestId('my-profile-success')).toBeVisible();

    await page.goto('/dashboards/overview');
    // The finance card on overview must render `ok` with balance data
    // (NOT `forbidden / MISSING_PREREQUISITE`). The exact selector is
    // owned by features/operator-overview/components/DomainCard.tsx.
    const financeCard = page.getByTestId('domain-card-finance');
    await expect(financeCard).toHaveAttribute('data-status', 'ok');
  });
});
