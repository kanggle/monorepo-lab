import { test, expect } from '@playwright/test';

/**
 * 백엔드 미기동 환경에서 동작하는 smoke E2E.
 *
 * playwright.smoke.config.ts 의 webServer 가 API 베이스 URL 을 도달 불가능한
 * 호스트(127.0.0.1:1)로 강제 설정하므로 SSR fetch 는 즉시 ECONNREFUSED 로
 * 실패하고 각 페이지의 fallback 경로(`.catch(() => [])`, 클라이언트 사이드
 * auth 가드 훅)가 활성화된다. Next.js prod build 의 hydration·route guard·
 * 핵심 페이지 렌더가 살아 있는지를 결정론적으로 검증한다.
 */
test.describe('웹스토어 smoke (백엔드 없음)', () => {
  test('홈페이지가 200 으로 렌더링되고 인기 상품 섹션이 보인다', async ({ page }) => {
    const response = await page.goto('/');
    expect(response?.status()).toBe(200);
    await expect(page.getByRole('heading', { name: '인기 상품' })).toBeVisible();
  });

  test('/login 폼이 렌더링되고 이메일·비밀번호 입력이 노출된다', async ({ page }) => {
    await page.goto('/login');
    await expect(page.getByRole('heading', { name: '로그인' })).toBeVisible();
    await expect(page.getByLabel('이메일')).toBeVisible();
    await expect(page.getByLabel('비밀번호')).toBeVisible();
  });

  test('비로그인 상태에서 /cart 접근 시 /login 으로 리다이렉트된다', async ({ page }) => {
    await page.goto('/cart');
    await page.waitForURL('**/login', { timeout: 10_000 });
    await expect(page.getByRole('heading', { name: '로그인' })).toBeVisible();
  });
});
