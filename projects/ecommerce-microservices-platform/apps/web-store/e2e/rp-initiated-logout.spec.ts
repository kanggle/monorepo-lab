import { test, expect } from '@playwright/test';
import { shouldSkipGap, loginAsSeededConsumer } from './helpers/auth';

/**
 * TASK-INT-023 / TASK-FE-070 AC-1 — RP-initiated OIDC logout (GAP `end_session`).
 *
 * Proves the defect the FE-070 fix closes: web-store logout must terminate the
 * GAP (SAS) IdP session, so the NEXT sign-in re-presents the GAP credential
 * form instead of silently re-authenticating. Before the fix, logout only
 * cleared the local NextAuth cookie → the GAP session survived → clicking the
 * GAP button again bounced straight back, authenticated, with no form.
 *
 * Requires a real GAP container (gates on SKIP_GAP_E2E). See
 * `docker-compose.iam-e2e.yml` + `e2e/fixtures/iam-consumer-seed.sql`.
 */
test.describe('RP-initiated OIDC logout (GAP end_session)', () => {
  test.skip(shouldSkipGap(), 'requires a running GAP container (SKIP_GAP_E2E=1)');

  test('logout terminates the IdP session — re-login re-presents the GAP credential form', async ({
    page,
  }) => {
    // 1. Log in as the seeded consumer through the real GAP OIDC flow.
    await loginAsSeededConsumer(page);

    // 2. Authenticated: the header profile menu (avatar) is present.
    const profileMenu = page.getByRole('button', { name: '프로필 메뉴' });
    await expect(profileMenu).toBeVisible({ timeout: 15_000 });

    // 3. Logout via the profile dropdown. The client logout() fetches the GAP
    //    end_session URL, clears the NextAuth session, then hard-navigates the
    //    browser to GAP /connect/logout. We race a BEST-EFFORT observation of
    //    that outbound nav to the GAP host, but deliberately tolerate a miss
    //    (`.catch`) rather than assert on it: the hard-navigation can be too
    //    brief for waitForURL to latch reliably, and the AUTHORITATIVE proof that
    //    the IdP session was terminated is step 6 (re-login re-presents the GAP
    //    #username form). So this tolerance is documented, not a silent swallow —
    //    a surviving IdP session is still caught loudly by the step-6 assertion.
    await profileMenu.click();
    await Promise.all([
      page
        .waitForURL((url) => url.hostname === 'auth-service', { timeout: 15_000 })
        .catch(() => undefined),
      page.getByRole('button', { name: '로그아웃' }).click(),
    ]);

    // 4. GAP terminates its session and redirects back to the registered
    //    post_logout_redirect_uri (app root) — land back on the web-store origin.
    await page.waitForURL((url) => url.hostname === 'localhost', { timeout: 30_000 });

    // 5. Re-login: click the GAP button again.
    await page.goto('/login');
    await page.getByRole('button', { name: 'Global Account 로 로그인' }).click();

    // 6. THE assertion: the IdP session was terminated, so GAP re-renders its
    //    credential form (Spring-default #username) instead of silently
    //    re-authenticating. If the bug were present, /oauth2/authorize would
    //    issue a code without a form and we'd never see #username.
    await expect(page.locator('#username')).toBeVisible({ timeout: 15_000 });
  });
});
