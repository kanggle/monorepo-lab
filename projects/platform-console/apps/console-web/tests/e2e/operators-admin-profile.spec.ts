import { test, expect } from '@playwright/test';

/**
 * TASK-PC-FE-017 — full vertical slice e2e for the operator
 * admin-on-behalf-of profile-edit UI (cross-operator counterpart of
 * PC-FE-016 me/profile self-serve flow). Closes the admin-on-behalf-of
 * 4-leg vertical slice: BE-307 producer column reuse + BE-307 producer
 * endpoint + admin proxy (this task) + per-row dialog (this task).
 *
 * Scenario:
 *   1. operator login as SUPER_ADMIN (seeded test fixture)
 *   2. navigate to /operators
 *   3. locate a NON-SELF operator row in the operators table
 *   4. click the per-row "프로파일 편집" button
 *   5. enter a known-good finance account UUID + a reason
 *   6. click Save → 204 + dialog closes
 *   7. (optional) re-open the dialog on the same row to confirm the input
 *      is empty again per v1 design (no current-value pre-population)
 *
 * SKIPPED by default: the platform-console e2e folder is empty in this
 * repo (no historical Playwright suite yet — `playwright.config.ts` is
 * configured but unused). Standing up the full GAP + finance + console
 * docker-compose stack with TWO seeded `admin_operators` rows (the
 * SUPER_ADMIN caller + a non-self target) + a known finance account UUID
 * is a substantial fixture cycle that is OUT OF SCOPE of this impl PR.
 * The unit + integration coverage already exercises every vertical-slice
 * leg:
 *
 *   - OperatorProfileEditDialog behaviour (form-level): tests/unit/
 *     features/operators/OperatorProfileEditDialog.test.tsx (~12 cases —
 *     initial empty / typed-value Save / Clear toggle / reason-required /
 *     whitespace-reject / error / pending / cancel);
 *   - proxy route (/api/operators/[operatorId]/profile): tests/unit/api/
 *     operators/admin-profile-route.test.ts (~10 cases — 204 / 422 /
 *     SELF_PROFILE_UPDATE_FORBIDDEN_VIA_ADMIN_PATH passthrough / 503 /
 *     NO active tenant / explicit null / `.strict()`);
 *   - api fn call-shape (HEADER MATRIX § 2.4.3 row 7 — AC-7 defense):
 *     tests/unit/features/operators/operators-api-set-profile.test.ts
 *     (3 cases — call shape with reason ONLY (NO Idempotency-Key) +
 *     null body + operatorId URL encoding);
 *   - parity matrix row 18 (operators: admin-set-profile, mutation/
 *     reason-only): tests/unit/parity-matrix.ts + parity-verification.
 *     test.ts (auto-iterated — operator-token bearer + tenant + header
 *     obligation 'reason-only' honored across the row, ABSENT operator
 *     token ⇒ 401 with NO fetch, NO active tenant ⇒ NO fetch);
 *   - producer side (BE-307 admin-api § PATCH {operatorId}/profile) IT
 *     already covers self-via-admin rejection + audit row writes.
 *
 * The producer-side `SELF_PROFILE_UPDATE_FORBIDDEN_VIA_ADMIN_PATH`
 * fail-safe is observable via the proxy route test (passthrough 400);
 * the UI's self-row Save-disabled gate is observable via the UI prop
 * `selfOperatorId` (`OperatorsScreen.tsx`).
 *
 * The remaining e2e gap is the manual browser click sequence — a
 * follow-up "platform-console e2e harness" task can light up this spec
 * along with login/catalog/tenant-switch / PC-FE-016 self-serve specs
 * the playwright.config already anticipates.
 *
 * To run locally once the harness is up:
 *   pnpm --filter console-web exec playwright test operators-admin-profile
 */
test.describe('@e2e operators admin profile — SUPER_ADMIN sets target operator finance default', () => {
  test.skip(
    true,
    'platform-console e2e harness not yet stood up (see file comment);'
      + ' vertical slice coverage is provided by unit + integration tests'
      + ' (PC-FE-017 dialog + admin proxy route + api fn + parity matrix'
      + ' row 18 + BE-307 producer IT).',
  );

  test('SUPER_ADMIN can set another operator finance default → producer 204', async ({
    page,
  }) => {
    await page.goto('/login');
    // TODO: login fixture (seeded SUPER_ADMIN operator); see playwright.config.ts.
    await page.goto('/operators');

    // Locate a NON-SELF target row (the per-row button is disabled on the
    // self row per the AC-8 UI gate). The exact selector is owned by
    // OperatorsScreen.tsx: `action-edit-profile-${operatorId}`.
    const targetButton = page.getByTestId('action-edit-profile-op-target');
    await expect(targetButton).toBeEnabled();
    await targetButton.click();

    // Dialog opens empty (no current-value pre-population in v1).
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
  });
});
