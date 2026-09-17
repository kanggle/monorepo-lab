import { test, expect } from '@playwright/test';

/**
 * Root redirect — `/` 는 **누구든** `/dashboards/overview`.
 *
 * 🔴 이 기대는 `ADR-MONO-074` (A8) 가 **바꿨다** — «빨개져서 고친» 것이 아니다.
 *    예전 판은 «미인증 `/` → `/demo`(공개 둘러보기)» 를 쟀다. 그 분기는 «익명은 `(console)`
 *    에 못 들어온다» 는 전제 위에 있었고, ADR-MONO-074 가 그 전제를 «익명은 들어오지만
 *    백엔드에 닿을 수 없다» 로 바꿨다. 그래서 익명도 **같은 주소의 실제 개요 화면**에 착지하고,
 *    값은 샘플이다. 🔵 `TASK-MONO-686` 이 `/demo` 자체(둘러보기 UI)를 은퇴시켰다 —
 *    지금은 `/demo` 가 308 로 이 화면(들)에 되돌아온다(§ `redirects()` in `next.config.mjs`).
 *
 * 🔵 이 설정은 백엔드(OIDC · registry · token-exchange · console-bff)를 전부 도달 불가
 *    loopback 으로 고정한다(`playwright.smoke.config.ts`). 그 상태에서 개요가 **숫자와 함께**
 *    선다는 것이 «샘플 화면은 네트워크 없이 선다» 의 end-to-end 증거다
 *    (`sample-visitor.spec.ts` 가 화면 쪽을 더 잰다).
 *
 * 인증된 운영자
 *   `/` → `/dashboards/overview` (예전과 동일).
 *   🔴 smoke 는 세션을 만들 수 없다 — `isAuthenticated()` 가 요구하는 두 쿠키는 HttpOnly
 *   이고 IAM OIDC 왕복 + RFC 8693 교환의 산물인데, 이 설정은 두 엔드포인트를 도달 불가
 *   loopback 으로 고정한다. **그래서 이 축은 여기서 «측정되지 않는다»** — 유닛 테스트
 *   (`tests/unit/root-redirect.test.ts`, `TASK-MONO-686` 이전 이름
 *   `demo-tour-root-redirect.test.ts`)가 두 갈래를 잰다. 가짜 쿠키를 심어
 *   «인증된 것처럼» 재면 그것은 인증 경로가 아니라 쿠키 파싱을 재는 것이다.
 */
test.describe('root redirect (backend 미기동)', () => {
  test('미인증 GET / → /dashboards/overview + 샘플 배너', async ({ page }) => {
    await page.goto('/');
    await page.waitForURL(/\/dashboards\/overview(\?|$)/, { timeout: 10_000 });
    await expect(page.getByTestId('sample-visitor-banner')).toBeVisible();
    await expect(
      page.getByRole('heading', { name: '운영자 통합 개요' }),
    ).toBeVisible();
  });

  test('🔴 /demo 로 가지 않는다 — 옛 분기(TASK-MONO-686 이전)가 되살아나지 않았다', async ({ page }) => {
    await page.goto('/');
    await page.waitForURL(/\/dashboards\/overview(\?|$)/, { timeout: 10_000 });
    expect(new URL(page.url()).pathname).not.toMatch(/^\/demo/);
  });

  test('실시간 콘솔 로그인 진입점 — 원래 경로를 redirect 로 싣는다', async ({ page }) => {
    await page.goto('/');
    await page.waitForURL(/\/dashboards\/overview(\?|$)/, { timeout: 10_000 });
    const login = page.getByTestId('sample-visitor-login');
    await expect(login).toHaveAttribute(
      'href',
      `/login?redirect=${encodeURIComponent('/dashboards/overview')}`,
    );
    await login.click();
    await page.waitForURL(/\/login(\?|$)/, { timeout: 10_000 });
    await expect(page.getByTestId('iam-login')).toBeVisible();
  });
});
