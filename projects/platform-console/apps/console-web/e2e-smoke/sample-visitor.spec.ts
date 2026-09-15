import { test, expect } from '@playwright/test';

/**
 * 익명 방문자가 **실제 콘솔 화면**을 샘플 데이터로 본다 — 백엔드 하나도 안 뜬 채로
 * (`ADR-MONO-074` R3ⓐ — `TASK-PC-FE-282` AC-10).
 *
 * `playwright.smoke.config.ts` 는 OIDC · registry · token-exchange · console-bff 를 전부
 * 도달 불가 loopback(127.0.0.1:1)으로 고정한다. 그 상태에서 개요 카드에 **샘플 숫자**가
 * 서면 «샘플 방문자의 요청은 네트워크에 닿지 않는다» 가 실제 프로덕션 빌드로 증명된다 —
 * 하나라도 백엔드로 나갔다면 그 카드는 degrade 상태로 떨어졌을 것이다.
 *
 * 🔴 숫자를 단언하는 이유: 배너만 재면 «셸은 섰는데 데이터는 전부 degrade» 도 초록이다.
 *    `operator-overview-card-iam-total` 의 128 은 `shared/sample/fixtures/dashboards.ts`
 *    에만 있는 값이다.
 */
test.describe('샘플 방문자 — 실제 개요 (backend 미기동 · 미인증)', () => {
  test('GET /dashboards/overview → 배너 + 개요 카드 + 샘플 지표', async ({ page }) => {
    await page.goto('/dashboards/overview');
    await expect(page.getByTestId('sample-visitor-banner')).toContainText(
      '샘플 데이터로 보는 실제 콘솔 화면입니다 · 로그인하면 실제 데이터',
    );
    await expect(page.getByTestId('operator-overview-cards')).toBeVisible();
    await expect(page.getByTestId('operator-overview-card-iam-total')).toHaveText('128');
    await expect(page.getByTestId('operator-overview-card-ecommerce-products')).toHaveText('342');
    // ready 화면에는 «준비 중» 이 없다.
    await expect(page.getByTestId('sample-screen-not-ready')).toHaveCount(0);
  });

  test('테넌트 스위처는 샘플 테넌트 하나(읽기 전용)', async ({ page }) => {
    await page.goto('/dashboards/overview');
    await expect(page.getByTestId('tenant-single')).toContainText('sample');
  });

  test('🔴 pending 화면 → 셸은 서고 «이 화면의 샘플 데이터는 준비 중입니다»', async ({ page }) => {
    await page.goto('/wms');
    await expect(page.getByTestId('sample-visitor-banner')).toBeVisible();
    await expect(page.getByTestId('sample-screen-not-ready')).toHaveText(
      '이 화면의 샘플 데이터는 준비 중입니다',
    );
    expect(new URL(page.url()).pathname).toBe('/wms');
  });
});
