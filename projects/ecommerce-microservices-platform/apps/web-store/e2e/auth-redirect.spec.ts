import { test, expect } from '@playwright/test';

/**
 * 비로그인 상태에서 보호된 라우트 접근 시 /login 으로 리다이렉트되는지 검증.
 *
 * After TASK-FE-067, redirect is enforced server-side via NextAuth middleware
 * (`src/middleware.ts`) — protected pages return 307 to /login?from=...
 * The login page itself surfaces a "Global Account 로 로그인" button (no more
 * email/password form on web-store side).
 */
test.describe('인증 필요 라우트 보호 (NextAuth + GAP)', () => {
  const protectedPaths = [
    { path: '/cart', label: '장바구니' },
    { path: '/my/profile', label: '마이페이지' },
    { path: '/my/wishlist', label: '위시리스트' },
    { path: '/my/addresses', label: '배송지 관리' },
    { path: '/my/orders', label: '주문 내역' },
  ];

  for (const { path, label } of protectedPaths) {
    test(`${label}(${path}) 은 비로그인 시 /login 으로 리다이렉트된다`, async ({ page }) => {
      await page.goto(path);
      await page.waitForURL('**/login**', { timeout: 10_000 });
      await expect(page.getByRole('heading', { name: '로그인' })).toBeVisible();
      // GAP 로그인 트리거 버튼이 보여야 한다 — 자체 email/password form 은 더 이상 없다.
      await expect(page.getByRole('button', { name: 'Global Account 로 로그인' })).toBeVisible();
    });
  }

  test('checkout 페이지도 비로그인 시 /login 으로 리다이렉트된다', async ({ page }) => {
    await page.goto('/checkout');
    await page.waitForURL((url) => url.pathname === '/login' || url.pathname === '/cart', {
      timeout: 10_000,
    });
    if (new URL(page.url()).pathname === '/cart') {
      await page.waitForURL('**/login**', { timeout: 10_000 });
    }
    await expect(page.getByRole('heading', { name: '로그인' })).toBeVisible();
  });

  test('로그인 페이지에 ?error=account_type_mismatch 가 있으면 안내가 표시된다', async ({ page }) => {
    await page.goto('/login?error=account_type_mismatch');
    // Next.js App Router injects a hidden `<div role="alert"
    // id="__next-route-announcer__">` on every page for accessibility-driven
    // route change announcements; pairing the bare `getByRole('alert')` with
    // the LoginForm's account_type_mismatch banner triggers strict-mode
    // resolution to 2 elements. Scope explicitly to the LoginForm error
    // banner — which carries `class="alert-error"` — to keep the assertion
    // deterministic regardless of Next.js's announcer presence.
    await expect(page.locator('[role="alert"].alert-error')).toContainText('admin');
  });
});
