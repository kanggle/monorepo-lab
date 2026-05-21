import { test, expect } from '@playwright/test';

/**
 * TASK-PC-FE-017 — full vertical slice e2e for the operator
 * admin-on-behalf-of profile-edit UI (cross-operator counterpart of
 * PC-FE-016 me/profile self-serve flow).
 *
 * TASK-PC-FE-018 visibility coverage — re-opening the dialog on a row whose
 * `currentDefaultAccountId` is non-null pre-populates the input with that
 * value (operatorContext extension from BE-308; consumer wiring from
 * PC-FE-018). The second `targetButton.click()` exercises this path after
 * the Save round-trip writes the value.
 *
 * Activated by TASK-PC-FE-019: the platform-console Playwright e2e harness
 * is now in place. The previous `test.skip(true, …)` guard has been
 * removed.
 *
 * Identity — the seeded SUPER_ADMIN cookies are primed by the global setup.
 * Active tenant = 'fan-platform' (matches the seeded `e2e-target-operator`
 * row's tenant_id so the operators-list query returns it).
 *
 * Scenario:
 *   1. navigate to /operators
 *   2. locate the seeded NON-SELF target row (operator_id
 *      `e2e-target-operator`)
 *   3. click the per-row "프로파일 편집" button (selector
 *      `action-edit-profile-${operatorId}`)
 *   4. dialog opens; input is empty on first open (target has
 *      `currentDefaultAccountId=null`)
 *   5. fill the value + reason; click Save → 204; dialog closes
 *   6. re-open the dialog → input is pre-populated with the new value
 *      (PC-FE-018 admin current-value visibility).
 *
 * Failure modes (each is an architecture defect, not a flake):
 *   - target row missing from /operators → check seed.sql ran (the
 *     console-web /api/operators table view's tenant filter must match
 *     the seeded operator's tenant_id='fan-platform').
 *   - Save returns 400 SELF_PROFILE_UPDATE_FORBIDDEN_VIA_ADMIN_PATH →
 *     the seeded SUPER_ADMIN's `operator_id` collides with the target's
 *     (should be different — `e2e-super-admin` vs `e2e-target-operator`).
 *   - re-open shows empty value → BE-308 `operatorContext` extension
 *     drift OR PC-FE-018 prop-wiring regression (assert at the IT layer
 *     first).
 */
test.describe('@e2e operators admin profile — SUPER_ADMIN sets target operator finance default', () => {
  test('SUPER_ADMIN can set another operator finance default → producer 204 + dialog re-open pre-populates', async ({
    page,
  }) => {
    await page.goto('/operators');

    // Locate the NON-SELF target row. The per-row Save-disabled gate (AC-8)
    // applies to the SELF row only — `e2e-target-operator` differs from the
    // logged-in `e2e-super-admin`, so its button is enabled.
    const targetButton = page.getByTestId(
      'action-edit-profile-e2e-target-operator',
    );
    await expect(targetButton).toBeEnabled();
    await targetButton.click();

    // Dialog opens. On first open the target's
    // `admin_operators.finance_default_account_id` is NULL (seed.sql), so
    // the consumer sees `currentDefaultAccountId=null` and the input is
    // empty.
    const dialog = page.getByTestId('operator-profile-edit-dialog');
    await expect(dialog).toBeVisible();
    const input = page.getByTestId('operator-profile-edit-value');
    await expect(input).toHaveValue('');
    await input.fill('01928c4a-7e9f-7c00-9a40-d2b1f5e8a000');

    const reason = page.getByTestId('operator-profile-edit-reason');
    await reason.fill('e2e admin-on-behalf-of provisioning');

    await page.getByTestId('operator-profile-edit-save').click();

    // Dialog closes on success (the per-row Save invalidates the
    // operators list query and clears the open dialog).
    await expect(
      page.getByTestId('operator-profile-edit-dialog'),
    ).toHaveCount(0);

    // PC-FE-018 visibility — re-open the dialog. The operators list query
    // re-fetched with the new BE-308 `operatorContext.defaultAccountId`,
    // so the dialog input should pre-populate with the value we just Saved.
    await page
      .getByTestId('action-edit-profile-e2e-target-operator')
      .click();
    await expect(
      page.getByTestId('operator-profile-edit-value'),
    ).toHaveValue('01928c4a-7e9f-7c00-9a40-d2b1f5e8a000');
  });
});
