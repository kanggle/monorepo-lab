import { test, expect } from '@playwright/test';
import { loginAsSuperAdmin } from './fixtures/login';

/**
 * 익명으로 연 뒤 **같은 브라우저**로 로그인하면 샘플 문자열이 0 이다
 * (`ADR-MONO-074` A10 — `TASK-PC-FE-282` AC-14).
 *
 * 🔴 `(console)` 은 이미 `force-dynamic` 이지만, «같은 주소가 쿠키에 따라 다른 본문» 이 되는
 *    첫 설계다. 그래서 «빌드/CDN/React 캐시가 샘플 응답을 로그인한 운영자에게 되돌려주지
 *    않는다» 를 **full-stack** 에서 잰다 — smoke 는 세션을 만들 수 없다
 *    (`e2e-smoke/root-redirect.spec.ts` 헤더).
 *
 * 이 스펙은 전역 storageState(로그인됨)를 쓰지 않는다: 빈 컨텍스트에서 익명으로 시작해
 * 샘플을 **먼저 본 뒤**, 그 컨텍스트 그대로 실제 OIDC PKCE 로그인을 태운다.
 */
test.use({ storageState: { cookies: [], origins: [] } });

test.describe('@e2e sample visitor → login in the same browser (TASK-PC-FE-282 AC-14)', () => {
  test('anonymous sees samples; after login the same URL carries zero sample strings', async ({
    page,
    context,
  }) => {
    // 1 — anonymous: the real overview, sample data.
    await page.goto('/dashboards/overview');
    await expect(page.getByTestId('sample-visitor-banner')).toBeVisible();
    await expect(page.getByTestId('operator-overview-cards')).toBeVisible();
    // Non-vacuity: the anonymous render really carried sample strings.
    await expect(page.getByText('(샘플)').first()).toBeAttached();

    // 2 — log in within the SAME context (production OIDC PKCE flow).
    await loginAsSuperAdmin(context);

    // 3 — same URL, now authenticated: no banner, no sample strings anywhere.
    await page.goto('/dashboards/overview');
    await expect(page.getByTestId('operator-overview-cards')).toBeVisible();
    await expect(page.getByTestId('sample-visitor-banner')).toHaveCount(0);
    await expect(page.getByTestId('sample-visitor-login')).toHaveCount(0);
    expect(await page.content()).not.toContain('(샘플)');
  });
});
