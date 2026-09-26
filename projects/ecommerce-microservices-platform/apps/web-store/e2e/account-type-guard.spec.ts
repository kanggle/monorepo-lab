import { test, expect } from '@playwright/test';
import {
  loginAndExpectIamRefusal,
  loginAndExpectRoleGuardRejection,
  SEEDED_CROSS_TENANT_PRINCIPAL,
  SEEDED_NO_CUSTOMER_ROLE,
} from './helpers/auth';

/**
 * Who gets into the storefront — two layers, one test each.
 *
 * **1. web-store's role guard (restored by TASK-BE-605).** `signInCallback` admits only a token
 * carrying `CUSTOMER` (ADR-MONO-035 §4b-1). Under TASK-MONO-381 this lane drove a cross-tenant
 * principal (`'*'`) through the storefront client to make IAM mint a CUSTOMER-less token. That
 * road is closed at IAM now — BE-604 refuses the cross-tenant credential at the form, and BE-605
 * sends a cross-tenant IAM session back to the login — so the guard was left without a caller
 * (BE-604 § ⑥). It is exercised again with an `ecommerce` account whose stored roles lack
 * `CUSTOMER` (`SEEDED_NO_CUSTOMER_ROLE`): IAM admits it, the token carries
 * `roles:["ECOMMERCE_OPERATOR"]`, and web-store bounces it to its own
 * `/login?error=account_type_mismatch`. The guard is unchanged; only its input is realistic now —
 * the same-tenant account without a shopper role is the shape the guard still has to stop.
 *
 * **2. IAM's own refusal (TASK-BE-604, kept).** The platform-scope credential (`'*'`) logging
 * in through the storefront client is refused by IAM itself — `/login?error`, exactly like a
 * wrong password — and web-store never sees a token. Kept because it still holds and is the
 * only e2e evidence of decision D on the consumer side; the two tests are told apart by where
 * the browser ends up (IAM's form vs the web-store origin).
 *
 * **Still deliberately not asserted.** That an *ecommerce operator who is also a shopper* is
 * rejected — TASK-MONO-334 operators hold a storefront account in their home tenant, and with no
 * stored roles the platform seed gives them `CUSTOMER`. They are shoppers here.
 */
test.describe('storefront 입장 가드 (web-store)', () => {
  test('CUSTOMER 없는 ecommerce 계정은 IAM 로그인은 되지만 web-store 역할 가드가 거부한다', async ({ page }) => {
    await loginAndExpectRoleGuardRejection(page, SEEDED_NO_CUSTOMER_ROLE);

    // The bounce lands on web-store's /login with the mismatch code, and no session was established.
    await expect(page).toHaveURL(/\/login\?.*error=account_type_mismatch/);
    await expect(page.getByRole('button', { name: 'Global Account로 로그인' })).toBeVisible();
  });

  test('타 tenant principal(platform-scope)이 web-store client 로 로그인하면 IAM 이 거부한다', async ({ page }) => {
    await loginAndExpectIamRefusal(page, SEEDED_CROSS_TENANT_PRINCIPAL);

    // Still on IAM's credential form — no session, no hop back into web-store.
    await expect(page).toHaveURL(/\/login\?error$/);
    await expect(page.locator('#username')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Global Account로 로그인' })).toHaveCount(0);
  });
});
