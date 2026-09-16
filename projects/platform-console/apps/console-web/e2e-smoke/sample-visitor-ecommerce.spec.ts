import { test, expect } from '@playwright/test';

/**
 * 익명 방문자가 E-Commerce 화면을 샘플 데이터로 본다 — 백엔드 하나도 안 뜬 채로
 * (`ADR-MONO-074` — `TASK-PC-FE-284` AC-6).
 *
 * `playwright.smoke.config.ts` 는 OIDC · registry · token-exchange · console-bff
 * 를 전부 도달 불가 loopback(127.0.0.1:1)으로 고정한다. 그 상태에서 `/ecommerce/orders`
 * 가 배너 + 표로 서고, 주문 1건의 상세로 들어갈 수 있으면 「실제 화면이 네트워크
 * 없이 선다」가 프로덕션 빌드로 증명된다.
 */
test.describe('샘플 방문자 — E-Commerce 주문 운영 화면 (backend 미기동 · 미인증)', () => {
  test('GET /ecommerce/orders → 배너 + 표', async ({ page }) => {
    await page.goto('/ecommerce/orders');
    await expect(page.getByTestId('sample-visitor-banner')).toContainText(
      '샘플 데이터로 보는 실제 콘솔 화면입니다 · 로그인하면 실제 데이터',
    );
    await expect(page.getByTestId('order-table')).toBeVisible();
    await expect(page.getByTestId('order-row-0')).toBeVisible();
    // ready 화면에는 «준비 중» 이 없다.
    await expect(page.getByTestId('sample-screen-not-ready')).toHaveCount(0);
  });

  test('주문 1건의 상세로 진입할 수 있다', async ({ page }) => {
    await page.goto('/ecommerce/orders');
    await page.getByTestId('order-detail-0').click();
    await expect(page).toHaveURL(/\/ecommerce\/orders\/order-sample-\d+/);
    await expect(page.getByTestId('order-detail')).toBeVisible();
    await expect(page.getByTestId('order-items')).toBeVisible();
  });
});
