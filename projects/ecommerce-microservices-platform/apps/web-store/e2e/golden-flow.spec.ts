import { test, expect } from '@playwright/test';
import { loginAsSeededConsumer, shouldSkipGap } from './helpers/auth';
import { openFirstProductDetail, selectFirstVariant, addToCart } from './helpers/product';

/**
 * Golden-flow E2E: GAP 로그인 → 상품 선택 → 장바구니 담기 → 결제 페이지 진입.
 *
 * Toss 결제 단계는 외부 SDK / PG 콜백이 필요하므로 E2E 범위 외 — /checkout 페이지가
 * 렌더링되고 "결제하기" 버튼이 노출되는 지점까지 검증한다.
 *
 * GAP 컨테이너가 e2e 환경에 없으면 (SKIP_GAP_E2E=1) 본 테스트는 자동 skip 된다 —
 * TASK-MONO-014 frontend-e2e 잡이 GAP 컨테이너를 docker-compose 에 추가한 이후에는
 * 자동 활성화된다.
 */
test.describe('웹스토어 주문 골든 플로우 (GAP)', () => {
  test.skip(shouldSkipGap(), 'SKIP_GAP_E2E=1 — GAP 컨테이너 미가용');

  test('GAP 로그인 → 상품 선택 → 장바구니 담기 → 결제 페이지 진입', async ({ page }) => {
    await loginAsSeededConsumer(page);

    await openFirstProductDetail(page);
    await selectFirstVariant(page);
    await addToCart(page);

    await page.goto('/cart');
    await expect(page.getByRole('heading', { name: '장바구니' })).toBeVisible();

    const selectAll = page.getByLabel('전체선택');
    if (!(await selectAll.isChecked())) {
      await selectAll.check();
    }

    const orderLink = page.getByRole('link', { name: '주문하기' });
    await expect(orderLink).toBeVisible();
    await orderLink.click();
    await page.waitForURL('**/checkout**', { timeout: 15_000 });

    await expect(page.getByRole('heading', { name: '주문하기' })).toBeVisible();
    await expect(page.getByRole('button', { name: /결제하기/ })).toBeVisible();
  });
});
