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
 * NOTE — layout 가드는 이제 의도한 목적지를 보존한다 (TASK-PC-FE-115,
 * consumer-integration-guide § Phase 4.5 F6): `/login?redirect=<원래경로>`.
 * `redirect` 파라미터는 `/api/auth/login` 이 OAuth state 쿠키로 왕복시켜
 * 로그인 후 원래 경로로 복귀시킨다.
 */
test.describe('(console) guard (backend 미기동)', () => {
  test('미인증 /operators → /login (목적지 보존)', async ({ page }) => {
    await page.goto('/operators');
    await page.waitForURL(/\/login\?redirect=/, { timeout: 10_000 });
    expect(page.url()).toContain('redirect=%2Foperators');
    await expect(page.getByRole('heading', { name: 'Platform Console' })).toBeVisible();
  });

  test('미인증 /dashboards/overview → /login (목적지 보존)', async ({ page }) => {
    await page.goto('/dashboards/overview');
    await page.waitForURL(/\/login\?redirect=/, { timeout: 10_000 });
    expect(page.url()).toContain('redirect=%2Fdashboards%2Foverview');
    await expect(page.getByRole('heading', { name: 'Platform Console' })).toBeVisible();
  });
});
