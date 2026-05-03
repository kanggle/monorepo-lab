import { test, expect } from '@playwright/test';

/**
 * Auth guard — 보호 경로 (/artists, /posts/:id) 에 비인증 상태로 접근하면
 * middleware 가 /login 으로 redirect 하고 `from` 쿼리에 원래 URL 을 보존한다.
 */
test.describe('auth guard', () => {
  test('/artists 비인증 접근 → /login?from=/artists', async ({ page }) => {
    await page.goto('/artists');
    await page.waitForURL((url) => url.pathname === '/login', { timeout: 10_000 });
    expect(new URL(page.url()).searchParams.get('from')).toBe('/artists');
  });

  test('/posts/:id 비인증 접근 → /login redirect', async ({ page }) => {
    await page.goto('/posts/abc-123');
    await page.waitForURL((url) => url.pathname === '/login', { timeout: 10_000 });
    expect(new URL(page.url()).searchParams.get('from')).toContain('/posts/abc-123');
  });
});
