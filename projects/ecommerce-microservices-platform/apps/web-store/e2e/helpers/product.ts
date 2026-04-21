import { expect, type Page } from '@playwright/test';

/**
 * /products 리스트에서 첫 상품 상세로 이동.
 * 검색/카테고리 필터를 거치지 않고 시드 데이터의 첫 카드를 클릭한다.
 */
export async function openFirstProductDetail(page: Page): Promise<void> {
  await page.goto('/products');
  await expect(page.getByRole('heading', { name: '전체 상품' })).toBeVisible();

  const productLinks = page.locator('a[href^="/products/"]').filter({
    hasNotText: '전체상품',
  });
  await expect(productLinks.first()).toBeVisible();
  await productLinks.first().click();
  await page.waitForURL(/\/products\/[0-9a-f-]{8,}$/i, { timeout: 15_000 });
}

/**
 * 상품 상세 페이지에서 variant 드롭다운을 열어 첫 번째 활성 옵션을 선택한다.
 * 옵션 미선택 시 "장바구니 담기" 버튼의 accessible name이 드롭다운 트리거와
 * 동일해지므로(둘 다 "옵션을 선택하세요"), ▾ 아이콘이 붙은 쪽을 정확히 매칭한다.
 */
export async function selectFirstVariant(page: Page): Promise<void> {
  const trigger = page.getByRole('button', { name: /^옵션을 선택하세요\s*▾$/ });
  await expect(trigger).toBeVisible();
  await trigger.click();

  const firstOption = page
    .locator('button:not([disabled])')
    .filter({ hasText: /재고\s+\d+/ })
    .first();
  await expect(firstOption).toBeVisible();
  await firstOption.click();
}

/**
 * 선택된 variant를 장바구니에 담고 토스트가 노출될 때까지 대기한다.
 */
export async function addToCart(page: Page): Promise<void> {
  const addBtn = page.getByRole('button', { name: '장바구니 담기' });
  await expect(addBtn).toBeEnabled();
  await addBtn.click();
  await expect(page.getByText('장바구니에 추가되었습니다.')).toBeVisible();
}
