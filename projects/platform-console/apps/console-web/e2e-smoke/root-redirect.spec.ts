import { test, expect } from '@playwright/test';

/**
 * Root redirect — 미인증 BrowserContext 가 `/` 에 접근하면 (a) root page 가
 * `/dashboards/overview` (5도메인 통합 개요 = 콘솔 랜딩, TASK-PC-FE-034) 로
 * redirect → (b) `(console)/layout.tsx` 의 `isAuthenticated()` 가드가 false →
 * `/login?redirect=<landing>` 으로 server-side redirect → (c) login page 가 렌더.
 * (TASK-PC-FE-115 / Phase 4.5 F6: 가드가 의도 목적지를 `redirect` 로 보존하므로
 * 쿼리스트링이 붙는다 — `/login` pathname 기준으로 검증.)
 *
 * backend 미기동: `isAuthenticated()` 는 cookies 만 읽으므로 fetch 0; redirect
 * chain 전체가 deterministic.
 */
test.describe('root redirect (backend 미기동)', () => {
  test('미인증 GET / → /login 으로 redirect + 로그인 페이지 렌더', async ({ page }) => {
    await page.goto('/');
    await page.waitForURL(/\/login(\?|$)/, { timeout: 10_000 });
    await expect(page.getByRole('heading', { name: 'Platform Console' })).toBeVisible();
    await expect(page.getByTestId('iam-login')).toBeVisible();
  });
});
