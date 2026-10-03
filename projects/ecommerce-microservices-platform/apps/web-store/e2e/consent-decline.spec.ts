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
 * MEASURED (nightly run 37094305963, before any copy change): `?error=OAuthCallbackError`, alert =
 * the generic «로그인 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.» — NOT `AccessDenied` / the
 * operator message the ticket suspected, but still wrong: it told a person who declined to «retry
 * later» as if something broke. Fix: `OAuthCallbackError` → `provider_error` (F5 vocabulary «IdP 가
 * callback 에서 error 반환») with copy that says the login did not finish and how to continue.
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
    // LoginForm's alert — not Next's empty route announcer, which also carries role="alert"
    // (1st nightly run 37093051748: a bare getByRole('alert') hit both, strict mode).
    const alert = page.locator('div.alert-error[role="alert"]');
    await expect(alert).toBeVisible();
    const alertText = (await alert.innerText()).trim();

    test.info().annotations.push(
      { type: 'consent-decline-error', description: String(error) },
      { type: 'consent-decline-alert', description: alertText },
    );
    console.log(`[TASK-BE-619 AC-2] consent decline -> web-store /login?error=${error} | alert=${alertText}`);

    expect(error, 'web-store must say WHY the login ended (never a silent /login)').not.toBeNull();
    // Pinned after the measurement: the value NextAuth v5 actually produces for a declined consent.
    expect(error).toBe('OAuthCallbackError');
    await expect(alert).toContainText('IAM 로그인이 끝나지 않았습니다');
    await expect(alert).toContainText('동의하지 않으셨다면');
    // 🔴 The two wrong readings this ticket exists for.
    await expect(alert).not.toContainText('operator');
    await expect(alert).not.toContainText('잠시 후 다시 시도');
    // No session was established — the store's entry button is still there.
    await expect(page.getByRole('button', { name: 'IAM 로그인' })).toBeVisible();
  });
});
