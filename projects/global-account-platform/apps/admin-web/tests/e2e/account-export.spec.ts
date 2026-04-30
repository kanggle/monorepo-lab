import { test, expect } from '@playwright/test';

/**
 * Account export journey: login → search → account detail → export button → download.
 *
 * Requires a running admin-web + admin-service stack.
 * Skip by default unless E2E_ENABLED=1.
 *
 * To execute:
 *   pnpm dev     # start admin-web
 *   E2E_ENABLED=1 pnpm e2e
 */

const OP_EMAIL = process.env.E2E_OP_EMAIL ?? 'admin@example.com';
const OP_PASSWORD = process.env.E2E_OP_PASSWORD ?? 'password123';
const TARGET_EMAIL = process.env.E2E_TARGET_EMAIL ?? 'user@example.com';

async function loginAsSuperAdmin(page: import('@playwright/test').Page) {
  await page.goto('/login');
  await page.getByLabel('운영자 ID').fill(OP_EMAIL);
  await page.getByLabel('비밀번호').fill(OP_PASSWORD);
  await page.getByRole('button', { name: /로그인/ }).click();
  await expect(page).toHaveURL(/\/accounts/);
}

test.describe('account data export', () => {
  test.skip(!process.env.E2E_ENABLED, 'Set E2E_ENABLED=1 with the stack running');

  test('SUPER_ADMIN sees export button on account detail page', async ({ page }) => {
    await loginAsSuperAdmin(page);

    await page.getByLabel('이메일').fill(TARGET_EMAIL);
    await page.getByRole('button', { name: '검색' }).click();

    await page.getByRole('link', { name: '상세' }).first().click();
    await expect(page.getByRole('button', { name: '데이터 내보내기' })).toBeVisible();
  });

  test('clicking export button triggers file download', async ({ page }) => {
    await loginAsSuperAdmin(page);

    await page.getByLabel('이메일').fill(TARGET_EMAIL);
    await page.getByRole('button', { name: '검색' }).click();
    await page.getByRole('link', { name: '상세' }).first().click();

    const downloadPromise = page.waitForEvent('download');
    await page.getByRole('button', { name: '데이터 내보내기' }).click();
    const download = await downloadPromise;

    expect(download.suggestedFilename()).toMatch(/^export-.+\.json$/);
  });
});
