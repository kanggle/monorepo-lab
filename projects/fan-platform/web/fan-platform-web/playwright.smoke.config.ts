import { defineConfig, devices } from '@playwright/test';

/**
 * Playwright config — smoke E2E (CI 전용, 백엔드 미기동).
 *
 * fan-platform-web 는 인증을 GAP IdP 에 위임하므로 smoke 단계에서는 OIDC 발신
 * 호스트를 도달 불가능한 closed loopback (127.0.0.1:1) 으로 강제하고,
 * 게이트웨이 베이스 URL 도 동일하게 강제한다. 페이지의 fallback 경로
 * (`.catch(() => null)`, middleware 의 `/login` redirect) 가 결정론적으로
 * 활성화되어 백엔드 / GAP 없이도 페이지 렌더 / 라우트 가드 / 로그인 진입
 * 흐름을 검증할 수 있다.
 *
 * 로컬: pnpm --filter fan-platform-web e2e:smoke
 * CI : `frontend-e2e-smoke` job 에서 동일 스크립트 호출.
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
    baseURL: process.env.PLAYWRIGHT_BASE_URL ?? 'http://localhost:3002',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    locale: 'ko-KR',
  },
  webServer: {
    command: 'pnpm start',
    port: 3002,
    timeout: 60_000,
    reuseExistingServer: !process.env.CI,
    env: {
      // SSR fetch / auth.js OIDC discovery 가 즉시 ECONNREFUSED 로 실패하도록
      // 강제. 미들웨어와 페이지의 fallback 경로 (`/login` redirect 등) 가
      // 활성화되어 백엔드·GAP 미기동에서도 결정론적으로 검증 가능.
      NEXT_PUBLIC_GATEWAY_URL: 'http://127.0.0.1:1',
      GATEWAY_URL_INTERNAL: 'http://127.0.0.1:1',
      OIDC_ISSUER_URL: 'http://127.0.0.1:1',
      OIDC_CLIENT_ID: 'fan-platform-user-flow-client',
      OIDC_CLIENT_SECRET: 'smoke-test-placeholder',
      NEXTAUTH_URL: 'http://localhost:3002',
      NEXTAUTH_SECRET: 'smoke-test-secret-32-bytes-min-OK',
      AUTH_TRUST_HOST: 'true',
    },
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
});
