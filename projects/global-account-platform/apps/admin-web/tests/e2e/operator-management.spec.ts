import { test, expect } from '@playwright/test';

/**
 * Operator management journey: login → /operators → list → create.
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

async function loginAsSuperAdmin(page: import('@playwright/test').Page) {
  await page.goto('/login');
  await page.getByLabel('운영자 ID').fill(OP_EMAIL);
  await page.getByLabel('비밀번호').fill(OP_PASSWORD);
  await page.getByRole('button', { name: /로그인/ }).click();
  await expect(page).toHaveURL(/\/accounts/);
}

test.describe('operator management', () => {
  test.skip(!process.env.E2E_ENABLED, 'Set E2E_ENABLED=1 with the stack running');

  test('SUPER_ADMIN can view operator list at /operators', async ({ page }) => {
    await loginAsSuperAdmin(page);

    await page.goto('/operators');
    await expect(page).toHaveURL(/\/operators/);
    await expect(page.getByRole('heading', { name: '운영자 관리' })).toBeVisible();

    await expect(page.getByRole('button', { name: '운영자 추가' })).toBeVisible();
  });

  test('SUPER_ADMIN can open create operator dialog and submit', async ({ page }) => {
    await loginAsSuperAdmin(page);
    await page.goto('/operators');

    await page.getByRole('button', { name: '운영자 추가' }).click();
    await expect(page.getByRole('dialog')).toBeVisible();
    await expect(page.getByText('신규 운영자 계정 정보와 초기 역할을 입력하세요.')).toBeVisible();

    const uniqueEmail = `e2e-op-${Date.now()}@example.com`;
    await page.getByLabel('이메일').fill(uniqueEmail);
    await page.getByLabel('표시 이름').fill('E2E Test Operator');
    await page.getByLabel('초기 비밀번호').fill('Passw0rd!e2e');

    // Wait for the operator list GET refetch that is triggered by React Query
    // invalidateQueries(['operators']) after the successful POST. Without this
    // wait, the following `getByText(uniqueEmail)` can run before the list is
    // rerendered, producing a flaky assertion.
    const listRefetch = page.waitForResponse(
      (resp) =>
        resp.url().includes('/api/admin/operators') &&
        resp.request().method() === 'GET' &&
        resp.status() === 200,
    );

    await page.getByRole('button', { name: '생성' }).click();

    await expect(page.getByText('운영자를 생성했습니다.')).toBeVisible();
    await listRefetch;
    await expect(page.getByText(uniqueEmail)).toBeVisible();
  });
});
