import { test, expect } from '@playwright/test';

/**
 * `TASK-MONO-686` — 은퇴한 콘솔 둘러보기(`/demo`)의 308 리다이렉트가 **실제로 뜬 서버**에서
 * 동작하는지. `next.config.mjs` 의 `redirects()` 는 프로덕션 빌드에서만 적용되는 서버
 * 라우팅이라 unit 테스트(`tests/unit/legacy-demo-tour-redirects.test.ts`, 도메인 7개 전부의
 * 매핑을 정확히 잰다)만으로는 **배선이 실제로 켜졌는지**를 증명하지 못한다 — 여기서 대표
 * 몇 칸만 라이브로 확인한다(전수는 unit 몫).
 */
test.describe('/demo → 308 (backend 미기동)', () => {
  test('맨살 /demo → /dashboards/overview (실제 개요, 샘플 배너)', async ({ page }) => {
    const res = await page.goto('/demo');
    expect(new URL(res!.url()).pathname).toBe('/dashboards/overview');
    await expect(page.getByTestId('sample-visitor-banner')).toBeVisible();
  });

  test('/demo/ecommerce → /ecommerce/orders (해당 도메인의 실제 화면)', async ({ page }) => {
    const res = await page.goto('/demo/ecommerce');
    expect(new URL(res!.url()).pathname).toBe('/ecommerce/orders');
  });

  test('모르는 /demo/no-such-domain → 개요로 (404 가 아니다)', async ({ page }) => {
    const res = await page.goto('/demo/no-such-domain');
    expect(new URL(res!.url()).pathname).toBe('/dashboards/overview');
    expect(res!.status()).toBeLessThan(400);
  });
});
