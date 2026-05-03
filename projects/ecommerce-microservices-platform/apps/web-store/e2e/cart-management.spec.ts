import { test, expect } from '@playwright/test';
import { signupAndLogin, shouldSkipGap } from './helpers/auth';
import { openFirstProductDetail, selectFirstVariant, addToCart } from './helpers/product';

test.skip(shouldSkipGap, 'SKIP_GAP_E2E=1 — GAP 컨테이너 미가용');

/**
 * 장바구니 조작 E2E: 수량 증가/감소, 전체선택 → 선택 삭제.
 *
 * QuantityControl의 +/− 버튼은 aria-label 없이 글자만으로 표현되고,
 * /cart 페이지에는 상품 행 외에도 전체선택 토글 등이 있으므로
 * 수량 버튼은 cart item row 내부로 scope해서 선택한다.
 */
test.describe('장바구니 조작', () => {
  test('수량 증가/감소 + 선택 삭제 후 장바구니가 비워진다', async ({ page }) => {
    await signupAndLogin(page);

    // 한 상품을 장바구니에 담는다
    await openFirstProductDetail(page);
    await selectFirstVariant(page);
    await addToCart(page);

    await page.goto('/cart');
    await expect(page.getByRole('heading', { name: '장바구니' })).toBeVisible();

    // 장바구니 행 하나에 대해 수량 조작
    const row = page.locator('div').filter({ has: page.locator('input[type="checkbox"]') }).filter({
      has: page.getByRole('button', { name: '+' }),
    }).first();
    await expect(row).toBeVisible();

    const increaseBtn = row.getByRole('button', { name: '+' });
    const decreaseBtn = row.getByRole('button', { name: '−' });

    // 초기 수량 1 → +2회 → 3
    await increaseBtn.click();
    await increaseBtn.click();
    await expect(row).toContainText('3');

    // −1회 → 2
    await decreaseBtn.click();
    await expect(row).toContainText('2');

    // 전체선택 후 선택 삭제
    const selectAll = page.getByLabel('전체선택');
    if (!(await selectAll.isChecked())) {
      await selectAll.check();
    }

    const deleteBtn = page.getByRole('button', { name: '선택 삭제' });
    await expect(deleteBtn).toBeEnabled();
    await deleteBtn.click();

    // 빈 장바구니 상태로 전환
    await expect(page.getByText('장바구니가 비어있습니다.')).toBeVisible();
  });
});
