import { defineConfig, devices } from '@playwright/test';

/**
 * Playwright 설정 — smoke E2E (CI 전용, 백엔드 미기동).
 *
 * 기존 e2e/ 시나리오는 docker compose 로 띄운 12개 백엔드 + 인프라를 가정하므로
 * 무겁고 느려 CI 잡으로 적합하지 않다. smoke config 는 별도 testDir 을 두어
 * Next.js prod build 만 띄우고, SSR fetch 가 도달 불가능한 호스트로 즉시
 * 실패하도록 환경변수를 강제한다.
 *
 * 로컬 실행: pnpm e2e:smoke (또는 모노레포 루트에서 pnpm e2e:smoke)
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
      // 모든 SSR / 클라이언트 사이드 API 호출이 즉시 ECONNREFUSED 로 실패하도록 강제.
      // 각 페이지의 fallback (`.catch(() => [])`, useRequireAuth 의 isAuthenticated=false
      // 분기 등) 이 활성화되어 백엔드 없이도 결정론적으로 테스트할 수 있다.
      API_URL_INTERNAL: 'http://127.0.0.1:1',
      NEXT_PUBLIC_API_URL: 'http://127.0.0.1:1',
    },
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
});
