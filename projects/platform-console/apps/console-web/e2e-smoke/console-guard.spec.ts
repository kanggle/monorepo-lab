import { test, expect } from '@playwright/test';

/**
 * `(console)` group guard — 보호 경로 (`/operators`, `/dashboards/overview`)
 * 미인증 접근 시 `apps/console-web/src/app/(console)/layout.tsx` 가 첫줄
 * `if (!(await isAuthenticated())) redirect('/login')` 로 /login 으로
 * server-side redirect.
 *
 * `isAuthenticated()` 는 `console_access_token` + `console_operator_token`
 * cookie 두개를 둘 다 요구 — 미인증 BrowserContext 는 즉시 false. backend
 * 호출 0.
 *
 * NOTE — 현재 layout 의 `redirect('/login')` 는 query 보존 (NextAuth-style
 * `from=<original>`) 안 함. 향후 UX 개선 시 query 보존 추가되면 본 spec 도
 * `from` 검증 추가.
 */
test.describe('(console) guard (backend 미기동)', () => {
  test('미인증 /operators → /login', async ({ page }) => {
    await page.goto('/operators');
    await page.waitForURL('**/login', { timeout: 10_000 });
    await expect(page.getByRole('heading', { name: 'Platform Console' })).toBeVisible();
  });

  test('미인증 /dashboards/overview → /login', async ({ page }) => {
    await page.goto('/dashboards/overview');
    await page.waitForURL('**/login', { timeout: 10_000 });
    await expect(page.getByRole('heading', { name: 'Platform Console' })).toBeVisible();
  });
});
