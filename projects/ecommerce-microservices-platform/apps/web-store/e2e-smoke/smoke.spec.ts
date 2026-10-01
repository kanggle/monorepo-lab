import { test, expect } from '@playwright/test';

/**
 * 백엔드 미기동 환경에서 동작하는 smoke E2E.
 *
 * playwright.smoke.config.ts 의 webServer 가 API 베이스 URL 을 도달 불가능한
 * 호스트(127.0.0.1:1)로 강제 설정하므로 SSR fetch 는 즉시 ECONNREFUSED 로
 * 실패하고 각 페이지의 fallback 경로(`.catch(() => [])`, 클라이언트 사이드
 * auth 가드 훅)가 활성화된다. Next.js prod build 의 hydration·route guard·
 * 핵심 페이지 렌더가 살아 있는지를 결정론적으로 검증한다.
 *
 * Post TASK-FE-067 — auth flow is GAP OIDC + NextAuth, so the login page no
 * longer hosts an email/password form. Smoke now asserts the GAP signin
 * trigger button is visible.
 */
test.describe('웹스토어 smoke (백엔드 없음)', () => {
  test('홈페이지가 200 으로 렌더링되고 인기 상품 섹션이 보인다', async ({ page }) => {
    const response = await page.goto('/');
    expect(response?.status()).toBe(200);
    await expect(page.getByRole('heading', { name: '인기 상품' })).toBeVisible();
  });

  test('/login 페이지가 렌더링되고 GAP 로그인 트리거 버튼이 노출된다', async ({ page }) => {
    await page.goto('/login');
    await expect(page.getByRole('heading', { name: '로그인' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'IAM 로그인' })).toBeVisible();
  });

  // TASK-FE-102 — was: anonymous /cart → /login (UC-0 EF-2, retired). The guest cart is
  // usable logged out; ordering is not.
  test('비로그인 상태에서도 /cart 가 열리고 빈 장바구니를 보여 준다', async ({ page }) => {
    const response = await page.goto('/cart');
    expect(response?.status()).toBe(200);
    await expect(page.getByText('장바구니가 비어있습니다.')).toBeVisible();
    expect(new URL(page.url()).pathname).toBe('/cart');
  });

  test('비로그인 장바구니(cart:guest)에 담긴 상품이 /cart 와 헤더 뱃지에 보인다', async ({ page }) => {
    await page.addInitScript(() => {
      window.localStorage.setItem(
        'cart:guest',
        JSON.stringify([
          { productId: 'p-smoke', variantId: 'v-smoke', productName: '스모크 상품', optionName: '기본', price: 12000, quantity: 2 },
        ]),
      );
    });
    await page.goto('/cart');
    await expect(page.getByText('스모크 상품')).toBeVisible();
    await expect(page.getByLabel('장바구니')).toContainText('2');
  });

  test('비로그인 상태에서 /checkout 은 여전히 /login 으로 리다이렉트된다', async ({ page }) => {
    await page.goto('/checkout');
    await page.waitForURL('**/login**', { timeout: 10_000 });
    await expect(page.getByRole('heading', { name: '로그인' })).toBeVisible();
  });
});
