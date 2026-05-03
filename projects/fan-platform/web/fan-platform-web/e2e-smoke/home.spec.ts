import { test, expect } from '@playwright/test';

/**
 * 백엔드 / GAP 미기동 환경에서도 결정론적으로 통과해야 하는 smoke.
 *
 * playwright.smoke.config.ts 는 OIDC_ISSUER_URL / NEXT_PUBLIC_GATEWAY_URL 을
 * 닫힌 loopback 으로 강제하므로 SSR fetch 는 즉시 실패하고 next-auth 의 세션
 * 쿠키도 존재하지 않아 middleware 가 / → /login 으로 redirect 한다.
 */
test('비로그인 상태에서 / 접근 시 /login 으로 리다이렉트된다', async ({ page }) => {
  await page.goto('/');
  await page.waitForURL('**/login**', { timeout: 10_000 });
  await expect(page.getByRole('heading', { name: 'fan-platform' })).toBeVisible();
});
