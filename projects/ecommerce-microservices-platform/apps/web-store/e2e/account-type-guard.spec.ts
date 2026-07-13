import { test, expect } from '@playwright/test';
import { loginAndExpectRoleGuardRejection, SEEDED_CROSS_TENANT_PRINCIPAL } from './helpers/auth';

/**
 * Cross-tenant role guard (web-store) — TASK-MONO-381, amending ADR-MONO-035 §4b-iii.
 *
 * **What this spec asserts.** A principal whose own tenant is not the storefront's platform
 * (here: platform scope `tenant_id='*'`, i.e. SUPER_ADMIN — see `iam-consumer-seed.sql`)
 * authenticates successfully at IAM but is bounced out of web-store, because its token carries
 * no `CUSTOMER` role. The ecommerce gateway will not stop it — `acceptAnyWellFormedTenant`
 * admits `'*'` — so this role guard is the only thing that does.
 *
 * **Why it never ran until now.** The guard was structurally unable to fire. `RoleSeedPolicy`
 * was keyed on the CLIENT's platform alone, so *every* credential authenticating through the
 * web-store client received `CUSTOMER` — including with account-service mocked out (roles 404 →
 * fail-soft → seed). A CUSTOMER-less token was unconstructible on this path, so no seeding of
 * any kind could have made this spec meaningful. MONO-381 narrowed the seed to fire only when
 * the principal's own tenant IS the client's platform, which is what gives the guard something
 * to bite. (The old helper `completeGapSignIn` was broken too — it drove a signup-or-login page
 * IAM does not render — but that was the lesser of the two problems.)
 *
 * **What this spec deliberately does NOT assert.** That an *ecommerce operator* is rejected.
 * They are not, and cannot be: TASK-MONO-334 requires an operator to already hold a signed-up
 * account in their home tenant, and post-TASK-BE-507 a signup lands in the tenant of the client
 * it came through — so being creatable as an ecommerce operator means having registered through
 * the storefront, i.e. being a shopper. The guard is a cross-tenant guard, never an operator
 * guard; ADR-MONO-035 §4b-iii is amended to say so.
 */
test.describe('cross-tenant role 가드 (web-store)', () => {
  test('타 tenant principal(platform-scope)이 web-store 에 로그인하면 거부된다', async ({ page }) => {
    await loginAndExpectRoleGuardRejection(page, SEEDED_CROSS_TENANT_PRINCIPAL);

    // The bounce lands on /login with the mismatch code, and no session was established.
    await expect(page).toHaveURL(/\/login\?.*error=account_type_mismatch/);
    await expect(page.getByRole('button', { name: 'Global Account 로 로그인' })).toBeVisible();
  });
});
