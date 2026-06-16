import { test, expect } from '@playwright/test';
import { loginAsSeededConsumer, shouldSkipGap } from './helpers/auth';
import { openFirstProductDetail } from './helpers/product';

test.skip(shouldSkipGap(), 'SKIP_GAP_E2E=1 — GAP 컨테이너 미가용');

/**
 * 위시리스트 추가/조회/제거 E2E.
 *
 * 상품 상세에서 위시리스트 버튼 토글 → `/my/wishlist`에서 조회 → 제거까지 검증.
 * 위시리스트 버튼의 accessible name은 상태에 따라
 * "위시리스트에 추가" ↔ "위시리스트에서 제거"로 바뀌므로 이를 통해 상태 전이를 관찰한다.
 */
test.describe('위시리스트', () => {
  test('상품 상세에서 찜 추가 → 위시리스트 목록 노출 → 제거', async ({ page }) => {
    await loginAsSeededConsumer(page);

    await openFirstProductDetail(page);

    // 방어적 베이스라인: 단일 SEEDED_CONSUMER 의 위시리스트는 서버상태로 영속된다
    // (cart 와 달리 localStorage 가 아님 — features/wishlist API). 이전 런이 중간
    // 실패해 이 상품이 이미 찜된 채 남아있으면 아래 "추가" 단언이 깨지므로, 먼저
    // 제거해 깨끗한 상태로 되돌린다(uniqueUser 폐기로 사라진 per-run 격리 대체).
    const staleRemove = page.getByRole('button', { name: '위시리스트에서 제거' });
    if (await staleRemove.isVisible().catch(() => false)) {
      await staleRemove.click();
      await expect(page.getByRole('button', { name: '위시리스트에 추가' })).toBeVisible();
    }

    // 1) 찜 추가 — 상품 상세의 위시리스트 버튼(헤딩 옆에 1개만 존재)
    const addBtn = page.getByRole('button', { name: '위시리스트에 추가' });
    await expect(addBtn).toBeVisible();
    await expect(addBtn).toBeEnabled();
    await addBtn.click();

    // 2) 상태 전이: 같은 버튼의 accessible name이 "제거"로 바뀜
    await expect(page.getByRole('button', { name: '위시리스트에서 제거' })).toBeVisible();

    // 3) 위시리스트 페이지 이동 — 최소 한 건이 목록에 노출되어야 한다
    await page.goto('/my/wishlist');
    await expect(page.getByRole('heading', { name: '위시리스트' })).toBeVisible();

    const removeBtnOnList = page.getByRole('button', { name: '위시리스트에서 제거' }).first();
    await expect(removeBtnOnList).toBeVisible();

    // 4) 목록에서 제거 — 리스트가 비거나 해당 카드가 사라질 때까지 대기
    await removeBtnOnList.click();
    await expect(page.getByText('위시리스트가 비어 있습니다.')).toBeVisible({ timeout: 10_000 });
  });
});
