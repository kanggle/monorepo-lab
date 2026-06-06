import { test, expect } from '@playwright/test';

/**
 * /login page render — public path 의 결정론적 렌더 + error code →
 * 한국어 메시지 mapping 검증. `apps/console-web/src/app/(auth)/login/page.tsx`
 * 의 `ERROR_MESSAGES` 4 항목과 일치 (provider_error / invalid_state /
 * state_mismatch / token_exchange_failed).
 *
 * `isAuthenticated()` 가 미인증 → fall-through 으로 페이지 렌더 (인증된 경우
 * 만 `/console` 으로 redirect). backend 호출 0.
 *
 * NOTE — Next.js prod build 가 `<div role="alert" id="__next-route-announcer__">`
 * 를 항상 inject 하므로 `getByRole('alert')` 는 strict-mode violation. error
 * 메시지는 `getByText` 로 직접 검증 (텍스트 mapping 자체가 spec 의 assertion).
 */
test.describe('/login render (backend 미기동)', () => {
  test('GET /login 기본 → CTA + 헤더 + 보조 텍스트 모두 visible', async ({ page }) => {
    await page.goto('/login');
    await expect(page.getByRole('heading', { name: 'Platform Console' })).toBeVisible();
    await expect(page.getByTestId('iam-login')).toBeVisible();
    await expect(
      page.getByText('IAM OIDC (Authorization Code + PKCE) 단일 로그인'),
    ).toBeVisible();
  });

  test('GET /login?error=provider_error → 한국어 에러 메시지 + CTA 보존', async ({
    page,
  }) => {
    await page.goto('/login?error=provider_error');
    await expect(
      page.getByText('IAM 로그인 중 오류가 발생했습니다. 다시 시도해주세요.'),
    ).toBeVisible();
    await expect(page.getByTestId('iam-login')).toBeVisible();
  });

  test('GET /login?error=invalid_state → mapping 적용', async ({ page }) => {
    await page.goto('/login?error=invalid_state');
    await expect(
      page.getByText('로그인 세션이 만료되었습니다. 다시 로그인해주세요.'),
    ).toBeVisible();
  });

  test('GET /login?error=state_mismatch → mapping 적용', async ({ page }) => {
    await page.goto('/login?error=state_mismatch');
    await expect(
      page.getByText('보안 검증에 실패했습니다. 다시 로그인해주세요.'),
    ).toBeVisible();
  });

  test('GET /login?error=token_exchange_failed → mapping 적용', async ({ page }) => {
    await page.goto('/login?error=token_exchange_failed');
    await expect(
      page.getByText('인증 서버에 연결할 수 없습니다. 잠시 후 다시 시도해주세요.'),
    ).toBeVisible();
  });
});
