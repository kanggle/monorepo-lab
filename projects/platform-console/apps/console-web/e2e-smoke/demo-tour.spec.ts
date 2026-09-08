import { test, expect } from '@playwright/test';

/**
 * 공개 둘러보기 `(demo)` — **미인증 브라우저에서, backend 하나도 안 뜬 채로** 실물로 선다.
 *
 * 이 설정은 OIDC discovery · registry · token-exchange · console-bff 를 전부 도달 불가
 * loopback(127.0.0.1:1)으로 고정한다(`playwright.smoke.config.ts`). 그 상태에서 이 화면이
 * 뜬다는 것이 «둘러보기가 백엔드에 의존하지 않는다» 의 **end-to-end 증거**다 — 유닛
 * 테스트의 fetch 스파이는 «모듈이 안 불렀다» 를 재고, 이 스펙은 «실제 프로덕션 빌드가
 * 네트워크 없이 렌더된다» 를 잰다. 둘은 다른 사실이다.
 */
test.describe('공개 둘러보기 (backend 미기동 · 미인증)', () => {
  test('GET /demo → 샘플 배너 + 개요 지표 + 도메인 카드', async ({ page }) => {
    await page.goto('/demo');
    await expect(
      page.getByRole('heading', { name: '운영자 콘솔 둘러보기' }),
    ).toBeVisible();
    await expect(page.getByTestId('demo-sample-banner')).toBeVisible();
    await expect(page.getByTestId('demo-overview-metrics')).toBeVisible();
    await expect(page.getByTestId('demo-grid-domain-health')).toBeVisible();
    await expect(page.getByTestId('demo-domain-card-ecommerce')).toBeVisible();
  });

  test('출처를 정직하게 말한다 — 샘플(합성) 데이터', async ({ page }) => {
    await page.goto('/demo');
    await expect(page.getByTestId('demo-provenance')).toContainText(
      '샘플(합성) 데이터',
    );
  });

  test('GET /demo/ecommerce → 표 + 기능 설명 + **비활성** 쓰기 버튼', async ({ page }) => {
    await page.goto('/demo/ecommerce');
    await expect(page.getByTestId('demo-grid-orders')).toBeVisible();
    await expect(page.getByTestId('demo-table-orders-description')).toContainText(
      '주문 상태',
    );

    const approve = page.getByTestId('demo-action-orders-환불 승인');
    await expect(approve).toBeVisible();
    await expect(approve).toBeDisabled();
    await expect(approve).toHaveAttribute('title', '실시간 기능 시작 후 로그인');
  });

  test('표 안 검색이 브라우저에서 돈다(네비게이션 없음)', async ({ page }) => {
    await page.goto('/demo/ecommerce');
    const rows = page.locator('[data-testid^="demo-row-orders-"]');
    // 🔴🔴 TASK-MONO-638 — **전체 행 수를 얼려 두지 않는다.** 예전 판은 4 를 적었는데,
    //    콘솔 표본이 늘자(표 행 30 → 65) 이 시험이 빨개졌다. 그 빨강의 사유는
    //    «검색이 고장났다» 가 아니라 «내가 제품 수치를 얼려 두었다» 였다.
    // 🔵 이 시험의 축은 «표 안 검색이 브라우저에서 돈다» 이지 «주문이 몇 건이다» 가 아니다.
    const before = await rows.count();
    // 🔴 비공허성 — 1행 이하면 «좁혀졌다» 는 아래 단언이 아무것도 시험하지 않는다.
    expect(before).toBeGreaterThan(1);

    await page.getByTestId('demo-search-orders').fill('0911');
    await expect(rows).toHaveCount(1);
    expect(before).toBeGreaterThan(1); // 좁혀지기 **전** 이 더 많았다는 사실을 남긴다
    // URL 이 안 바뀌었다 = 서버로 질의가 안 나갔다.
    expect(new URL(page.url()).pathname).toBe('/demo/ecommerce');
  });

  test('🔴 둘러보기는 보호 경로를 열지 않는다 — 실시간 경로는 텍스트일 뿐', async ({
    page,
  }) => {
    await page.goto('/demo');
    // 실시간 콘솔 경로가 링크였다면 클릭 가능한 <a> 여야 한다. 아니어야 한다.
    await expect(page.locator('a[href="/ecommerce/orders"]')).toHaveCount(0);
    await expect(page.getByTestId('demo-live-href-ecommerce')).toHaveText(
      '/ecommerce/orders',
    );

    // 그리고 보호 경로에 직접 들어가면 여전히 로그인으로 튕긴다(가드 그대로).
    await page.goto('/ecommerce/orders');
    await page.waitForURL(/\/login\?redirect=/, { timeout: 10_000 });
  });
});
