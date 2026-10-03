import { test, expect } from '@playwright/test';
import { fillGapCredentialForm, SEEDED_POOL_NOT_STORE_MEMBER } from './helpers/auth';

/**
 * TASK-BE-619 AC-2 — what web-store shows when a consumer-pool account DECLINES the store's
 * first-visit consent (ADR-MONO-078 A · TASK-BE-616).
 *
 * IAM answers a decline with `error=access_denied` (+ `error_description`, `state`) on the store's
 * registered callback (`/api/auth/callback/iam`). What NextAuth v5 turns that into on web-store's
 * `/login?error=…` was never run (TASK-BE-616 § 후속): if it becomes `AccessDenied`, `LoginForm`'s
 * `normalizeErrorCode` folds it into `role_denied` and the shopper reads «operator 계정으로는 …» — a
 * wrong instruction. The ticket forbids changing the copy before this is measured.
 *
 * This spec is the measurement. It records the `error` value and the alert text (annotation + a
 * `[TASK-BE-619 AC-2]` stdout line, both in the JSON report) and pins the outcome the ticket fixed
 * after reading it.
 *
 * Pair: `account-type-guard.spec.ts` (the same «IAM bounces back to web-store /login» shape).
 */
test.describe('사이트 첫 방문 동의 거절 (web-store)', () => {
  test('스토어 멤버가 아닌 풀 계정이 동의를 거절하면 스토어 /login 으로 돌아와 거절 문구를 본다', async ({ page }) => {
    await page.goto('/login');
    const trigger = page.getByRole('button', { name: 'IAM 로그인' });
    await expect(trigger).toBeEnabled();
    await trigger.click();
    await fillGapCredentialForm(page, SEEDED_POOL_NOT_STORE_MEMBER);

    // IAM's one-screen consent (TASK-BE-616) — not a token, not the login form again.
    await page.waitForURL((url) => url.pathname === '/consent', { timeout: 30_000 });
    await page.getByRole('button', { name: '동의하지 않음' }).click();

    // Back on the web-store origin, on its own /login (NextAuth pages.signIn = pages.error = '/login').
    await page.waitForURL(
      (url) => url.hostname === 'localhost' && url.pathname === '/login',
      { timeout: 30_000 },
    );
    const error = new URL(page.url()).searchParams.get('error');
    const alert = page.getByRole('alert');
    await expect(alert).toBeVisible();
    const alertText = (await alert.innerText()).trim();

    test.info().annotations.push(
      { type: 'consent-decline-error', description: String(error) },
      { type: 'consent-decline-alert', description: alertText },
    );
    console.log(`[TASK-BE-619 AC-2] consent decline -> web-store /login?error=${error} | alert=${alertText}`);

    expect(error, 'web-store must say WHY the login ended (never a silent /login)').not.toBeNull();
    // No session was established — the store's entry button is still there.
    await expect(page.getByRole('button', { name: 'IAM 로그인' })).toBeVisible();
  });
});
