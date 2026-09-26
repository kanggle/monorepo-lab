import { test, expect } from '@playwright/test';
import { loginAndExpectIamRefusal, SEEDED_CROSS_TENANT_PRINCIPAL } from './helpers/auth';

/**
 * Cross-tenant login through the storefront — TASK-MONO-381, then TASK-BE-604.
 *
 * **What this spec asserts (since TASK-BE-604).** A principal whose own tenant is not the
 * storefront's (here: platform scope `tenant_id='*'`, i.e. SUPER_ADMIN — see
 * `iam-consumer-seed.sql`) is refused by IAM itself when it logs in through the web-store
 * client: IAM answers `/login?error`, exactly like a wrong password, and web-store never sees
 * a token. BE-604 (owner decision D) limited the form-login cross-tenant credential fallback to
 * the operator console client; a consumer client's scoped miss is a failed login.
 *
 * **What it asserted before, and why that changed.** Under TASK-MONO-381 the same principal
 * authenticated at IAM (the fallback admitted it) and was bounced by web-store's role guard
 * (`/login?error=account_type_mismatch`) because its token carried no `CUSTOMER` role. That
 * premise — "authenticates at IAM" — is gone on this path. The role guard itself is unchanged;
 * this lane no longer drives a CUSTOMER-less storefront token, so it no longer exercises it
 * (recorded as a follow-up on TASK-BE-604 — the one remaining way to mint such a token is an
 * IAM browser session reused across clients).
 *
 * **Still deliberately not asserted.** That an *ecommerce operator* is rejected — they hold a
 * storefront account in their home tenant (TASK-MONO-334), so they are shoppers here.
 */
test.describe('cross-tenant 로그인 (web-store)', () => {
  test('타 tenant principal(platform-scope)이 web-store client 로 로그인하면 IAM 이 거부한다', async ({ page }) => {
    await loginAndExpectIamRefusal(page, SEEDED_CROSS_TENANT_PRINCIPAL);

    // Still on IAM's credential form — no session, no hop back into web-store.
    await expect(page).toHaveURL(/\/login\?error$/);
    await expect(page.locator('#username')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Global Account로 로그인' })).toHaveCount(0);
  });
});
