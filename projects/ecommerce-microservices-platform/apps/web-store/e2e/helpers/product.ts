import { expect, type Page } from '@playwright/test';
import { SELECTABLE_VARIANT_OPTION } from '../../src/widgets/product-detail-with-cart/variant-option-testid';

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
 * 상품 상세 페이지에서 variant 드롭다운을 열어 첫 번째 **선택 가능한** 옵션을 선택한다.
 * 옵션 미선택 시 "장바구니 담기" 버튼의 accessible name이 드롭다운 트리거와
 * 동일해지므로(둘 다 "옵션을 선택하세요"), ▾ 아이콘이 붙은 쪽을 정확히 매칭한다.
 *
 * 🔴 이 헬퍼는 «재고 숫자» 로 옵션을 고르지 않는다 — 그 화면은 재고를 **모를 수 있고**,
 *    모르는 것을 안 그리는 것이 설계다(ADR-MONO-070 § 재고). 술어와 그 사유는
 *    `src/widgets/product-detail-with-cart/variant-option-testid.ts` 에 한 번만 있다.
 *    거기서 import 하므로 이 문자열은 대조군 단위 테스트가 재는 것과 **같은 값**이다.
 */
export async function selectFirstVariant(page: Page): Promise<void> {
  const trigger = page.getByRole('button', { name: /^옵션을 선택하세요\s*▾$/ });
  await expect(trigger).toBeVisible();
  await trigger.click();

  const firstOption = page.locator(SELECTABLE_VARIANT_OPTION).first();
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
