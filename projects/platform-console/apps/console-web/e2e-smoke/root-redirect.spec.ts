import { test, expect } from '@playwright/test';

/**
 * Root redirect — `/` 의 착지점은 **방문자가 누구냐**에 따라 갈린다.
 *
 * 미인증 BrowserContext
 *   `/` → `app/page.tsx` 가 `isAuthenticated()` 거짓을 보고 **`/demo`** 로 redirect →
 *   공개 둘러보기(`(demo)` 라우트 그룹, 인증 가드 없음)가 렌더. 값은 저장소에 커밋된
 *   합성 샘플에서 오므로 **backend 미기동에서도 실물로 뜬다** (fetch 0).
 *
 *   🔴 이 기대는 예전과 **다르다**. 예전에는 `/` → `/dashboards/overview` →
 *   `(console)` 가드 → `/login?redirect=…` 였고, 즉 방문자가 이 앱에서 처음 보는 화면이
 *   로그인 폼이었다. 바뀐 것은 «익명의 착지점» 하나뿐이고, `(console)` 가드는 그대로다 —
 *   그 사실은 `console-guard.spec.ts` 가 계속 잰다(이 파일이 그 축을 재는 것이 아니다).
 *
 * 인증된 운영자
 *   `/` → `/dashboards/overview` (TASK-PC-FE-034 랜딩, 오늘과 동일).
 *   🔴 smoke 는 세션을 만들 수 없다 — `isAuthenticated()` 가 요구하는 두 쿠키는 HttpOnly
 *   이고 IAM OIDC 왕복 + RFC 8693 교환의 산물인데, 이 설정은 두 엔드포인트를 도달 불가
 *   loopback 으로 고정한다. **그래서 이 축은 여기서 «측정되지 않는다»** — 대신 유닛
 *   테스트(`tests/unit/demo-tour-root-redirect.test.ts`)가 두 갈래를 모두 잰다. 여기에
 *   가짜 쿠키를 심어 «인증된 것처럼» 재면 그것은 인증 경로가 아니라 쿠키 파싱을 재는
 *   것이고, 그 초록은 아무것도 뜻하지 않는다.
 */
test.describe('root redirect (backend 미기동)', () => {
  test('미인증 GET / → /demo 로 redirect + 둘러보기 렌더', async ({ page }) => {
    await page.goto('/');
    await page.waitForURL(/\/demo(\?|$)/, { timeout: 10_000 });
    await expect(
      page.getByRole('heading', { name: '운영자 콘솔 둘러보기' }),
    ).toBeVisible();
    await expect(page.getByTestId('demo-sample-banner')).toBeVisible();
  });

  test('둘러보기에서 실시간 콘솔 로그인으로 가는 진입점이 보인다', async ({ page }) => {
    await page.goto('/');
    await page.waitForURL(/\/demo(\?|$)/, { timeout: 10_000 });
    await expect(page.getByTestId('demo-header-login')).toHaveAttribute(
      'href',
      '/login',
    );

    await page.getByTestId('demo-header-login').click();
    await page.waitForURL(/\/login(\?|$)/, { timeout: 10_000 });
    await expect(page.getByTestId('iam-login')).toBeVisible();
  });
});
