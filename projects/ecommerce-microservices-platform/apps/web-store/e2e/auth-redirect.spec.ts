import { test, expect } from '@playwright/test';

/**
 * 비로그인 상태에서 보호된 라우트 접근 시 /login으로 리다이렉트되는지 검증.
 *
 * 보호 메커니즘은 클라이언트 사이드 {@link useRequireAuth} 훅이 담당한다 —
 * useEffect에서 auth 로딩 완료 후 isAuthenticated=false이면 router.replace('/login').
 * 즉 SSR 응답 자체는 200이지만 hydration 이후 즉시 /login으로 이동한다.
 */
test.describe('인증 필요 라우트 보호', () => {
  const protectedPaths = [
    { path: '/cart', label: '장바구니' },
    { path: '/my/profile', label: '마이페이지' },
    { path: '/my/wishlist', label: '위시리스트' },
    { path: '/my/addresses', label: '배송지 관리' },
    { path: '/my/orders', label: '주문 내역' },
  ];

  for (const { path, label } of protectedPaths) {
    test(`${label}(${path})은 비로그인 시 /login 으로 리다이렉트된다`, async ({ page }) => {
      await page.goto(path);
      await page.waitForURL('**/login', { timeout: 10_000 });
      await expect(page.getByRole('heading', { name: '로그인' })).toBeVisible();
    });
  }

  test('checkout 페이지도 비로그인 시 /login 으로 리다이렉트된다', async ({ page }) => {
    // /checkout 은 cart가 비어있으면 /cart로 먼저 이동하지만, 비로그인이면 그 이전에 /login으로 가야 한다.
    await page.goto('/checkout');
    await page.waitForURL((url) => url.pathname === '/login' || url.pathname === '/cart', {
      timeout: 10_000,
    });
    // /cart로 갔다면 연쇄적으로 /login까지 가야 한다(cart 역시 보호 라우트)
    if (new URL(page.url()).pathname === '/cart') {
      await page.waitForURL('**/login', { timeout: 10_000 });
    }
    await expect(page.getByRole('heading', { name: '로그인' })).toBeVisible();
  });
});
