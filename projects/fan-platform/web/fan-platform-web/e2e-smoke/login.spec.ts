import { test, expect } from '@playwright/test';

/**
 * 로그인 페이지 자체가 200 으로 렌더되고 GAP 로그인 버튼이 노출되는지 검증.
 *
 * 실제 GAP redirect 는 닫힌 loopback 호스트로 향하므로 클릭 자체는 검증하지
 * 않는다 — 버튼 노출과 form action 만 확인.
 */
test('/login 페이지가 200 으로 렌더되고 GAP 로그인 버튼이 노출된다', async ({ page }) => {
  const response = await page.goto('/login');
  expect(response?.status()).toBe(200);
  await expect(page.getByRole('heading', { name: '로그인' })).toBeVisible();
  await expect(page.getByTestId('oidc-signin')).toBeVisible();
  await expect(page.getByTestId('oidc-signin')).toHaveText(/GAP 로 로그인/);
});
