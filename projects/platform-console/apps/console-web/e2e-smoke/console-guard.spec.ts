import { test, expect } from '@playwright/test';

/**
 * `(console)` group guard — **`ADR-MONO-074` 가 이 스펙의 기대값을 바꿨다**.
 *
 * 예전 판은 «미인증 `/operators`·`/dashboards/overview` → `/login?redirect=…`» 를 쟀다.
 * ADR-MONO-074 (A1 · A7) 가 `(console)/layout.tsx` 가드의 의미를 «익명은 들어올 수 없다»
 * 에서 «익명은 들어오지만 백엔드에 닿을 수 없다» 로 바꿨으므로, 미인증 브라우저(두 세션
 * 쿠키 모두 없음 = 샘플 방문자)는 이제 **같은 주소에 머물며** 샘플 셸을 본다.
 *
 * 🔵 목적지 보존(TASK-PC-FE-115, `?redirect=<원래경로>`)은 사라지지 않았다 — 가드의
 *    리다이렉트 대신 셸의 «로그인» 링크가 그 값을 싣는다. 이 스펙은 그 사실을 계속 잰다.
 *
 * 🔴 반쪽 세션(액세스만 / 운영자만) → `/login` 은 여기서 **측정되지 않는다**: smoke 는
 *    HttpOnly 세션 쿠키를 만들 수 없다(`root-redirect.spec.ts` 헤더). 그 축은 유닛
 *    테스트(`tests/unit/console-shell-sample-visitor-guard.test.tsx`)가 잰다.
 */
test.describe('(console) guard (backend 미기동)', () => {
  test('미인증 /operators → 샘플 셸에 머문다 + 로그인 링크가 목적지 보존', async ({ page }) => {
    await page.goto('/operators');
    await expect(page.getByTestId('sample-visitor-banner')).toBeVisible();
    expect(new URL(page.url()).pathname).toBe('/operators');
    await expect(page.getByTestId('sample-visitor-login')).toHaveAttribute(
      'href',
      `/login?redirect=${encodeURIComponent('/operators')}`,
    );
  });

  test('미인증 /dashboards/overview → 샘플 셸 + 로그인 링크가 목적지 보존', async ({ page }) => {
    await page.goto('/dashboards/overview');
    await expect(page.getByTestId('sample-visitor-banner')).toBeVisible();
    await expect(page.getByTestId('sample-visitor-login')).toHaveAttribute(
      'href',
      `/login?redirect=${encodeURIComponent('/dashboards/overview')}`,
    );
  });
});
