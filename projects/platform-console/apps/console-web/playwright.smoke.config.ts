import { defineConfig, devices } from '@playwright/test';

/**
 * Playwright config — smoke E2E (CI 전용, 백엔드 미기동).
 *
 * platform-console-web 는 인증을 GAP IdP (auth-service) 에 위임하고, 인증 후
 * 페이지는 console-bff + admin-service registry + token-exchange + per-domain
 * gateway 들에 의존한다. smoke 단계에서는 OIDC discovery / registry /
 * token-exchange / BFF URL 을 모두 도달 불가능한 closed loopback (127.0.0.1:1)
 * 으로 강제하고, public path (`/`, `/login`) 와 (console) layout 의 미인증
 * redirect 경로 (`isAuthenticated()` 가드) 만 결정론적으로 검증한다.
 *
 * 인증 후 페이지 / API 호출 검증은 TASK-PC-FE-019 의 nightly full-stack
 * harness (docker-compose.e2e.yml) 의 영역.
 *
 * 로컬: pnpm --filter console-web e2e:smoke
 * CI : `.github/workflows/ci.yml` `frontend-e2e-smoke` job 의 platform-console
 *      stage 가 동일 스크립트 호출 (TASK-PC-FE-021).
 *
 * Pattern reuse: ecommerce web-store + fan-platform-web 의 mature precedent
 * (`projects/{ecommerce-microservices-platform/apps/web-store,fan-platform/web/
 * fan-platform-web}/playwright.smoke.config.ts`) 의 형판 verbatim — closed-
 * loopback URL + `pnpm start` webServer + ko-KR locale + 1 worker + retain-on-
 * failure trace + chromium-only.
 */
export default defineConfig({
  testDir: './e2e-smoke',
  timeout: 30_000,
  expect: { timeout: 5_000 },
  fullyParallel: false,
  retries: process.env.CI ? 1 : 0,
  workers: 1,
  reporter: [['list'], ['html', { outputFolder: 'playwright-report-smoke', open: 'never' }]],
  outputDir: 'test-results-smoke',
  use: {
    baseURL: process.env.PLAYWRIGHT_BASE_URL ?? 'http://localhost:3000',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    locale: 'ko-KR',
  },
  webServer: {
    command: 'pnpm start',
    port: 3000,
    timeout: 60_000,
    reuseExistingServer: !process.env.CI,
    env: {
      // SSR fetch / OIDC discovery / registry / token-exchange / console-bff
      // 모두 즉시 ECONNREFUSED 로 실패하도록 강제. 미인증 BrowserContext +
      // (console)/layout.tsx 의 `isAuthenticated()` 미인증 → /login redirect
      // 가 backend 호출 없이 활성화. 클릭 트리거 (CTA → /api/auth/login) 는
      // smoke 가 실행하지 않으므로 OIDC 도달 불가 무관.
      OIDC_ISSUER_URL: 'http://127.0.0.1:1',
      OIDC_CLIENT_ID: 'platform-console-web',
      OIDC_REDIRECT_URI: 'http://localhost:3000/api/auth/callback',
      OIDC_SCOPE: 'openid profile email tenant.read',
      CONSOLE_REGISTRY_URL: 'http://127.0.0.1:1/api/admin/console/registry',
      CONSOLE_TOKEN_EXCHANGE_URL: 'http://127.0.0.1:1/api/admin/auth/token-exchange',
      CONSOLE_BFF_URL: 'http://127.0.0.1:1',
      NEXT_PUBLIC_APP_URL: 'http://localhost:3000',
    },
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
});
