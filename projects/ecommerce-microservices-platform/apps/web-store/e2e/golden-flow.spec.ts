import { test, expect } from '@playwright/test';
import { signupAndLogin } from './helpers/auth';
import { openFirstProductDetail, selectFirstVariant, addToCart } from './helpers/product';

/**
 * Golden-flow E2E: 회원가입 → 로그인 → 상품 선택 → 장바구니 담기 → 결제 페이지 진입.
 *
 * 실제 결제(Toss) 단계는 외부 SDK 및 PG 콜백이 필요하므로 E2E 범위에서 제외.
 * 대신 주문 페이지(/checkout)가 렌더링되고 "결제하기" 버튼이 노출되는 지점까지 검증한다.
 *
 * 검색(search-service)은 Elasticsearch 의존으로 인해 flaky하므로 별도 E2E로 분리.
 * 골든 플로우는 /products 리스트의 시드 데이터에서 첫 상품을 선택해 결정론적으로 진행한다.
 */
test.describe('웹스토어 주문 골든 플로우', () => {
  test('회원가입 → 로그인 → 상품 선택 → 장바구니 담기 → 결제 페이지 진입', async ({ page }) => {
    await signupAndLogin(page);

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
