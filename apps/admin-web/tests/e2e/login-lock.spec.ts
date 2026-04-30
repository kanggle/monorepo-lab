import { test, expect } from '@playwright/test';

/**
 * Critical journey: login → account search → lock → audit confirmation.
 *
 * This test assumes a running admin-web + admin-service (see docker-compose).
 * It is intentionally skipped by default when the stack is not up.
 *
 * To execute:
 *   pnpm dev     # start admin-web
 *   pnpm e2e     # in another terminal
 */

test.describe('operator critical path', () => {
  test.skip(!process.env.E2E_ENABLED, 'Set E2E_ENABLED=1 with the stack running');

  test('login → search → lock → audit', async ({ page }) => {
    await page.goto('/login');
    await page.getByLabel('운영자 ID').fill(process.env.E2E_OP_EMAIL ?? 'admin@example.com');
    await page.getByLabel('비밀번호').fill(process.env.E2E_OP_PASSWORD ?? 'password123');
    await page.getByRole('button', { name: /로그인/ }).click();

    await expect(page).toHaveURL(/\/accounts/);

    await page.getByLabel('이메일').fill(process.env.E2E_TARGET_EMAIL ?? 'user@example.com');
    await page.getByRole('button', { name: '검색' }).click();

    await page.getByRole('link', { name: '상세' }).first().click();
    await page.getByRole('button', { name: '잠금' }).click();
    await page.getByLabel('사유 (필수)').fill('e2e lock');
    await page.getByRole('button', { name: '잠금' }).last().click();

    await page.goto('/audit');
    await page.getByLabel('Action Code').fill('ACCOUNT_LOCK');
    await page.getByRole('button', { name: '조회' }).click();
    await expect(page.getByText('ACCOUNT_LOCK').first()).toBeVisible();
  });
});
