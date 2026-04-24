import { defineConfig, devices } from '@playwright/test';

/**
 * Playwright configuration for web-store E2E tests.
 *
 * Assumes:
 * - web-store is reachable at http://localhost:3000
 * - gateway/backends are running (docker compose up)
 *
 * For CI, set PLAYWRIGHT_BASE_URL and ensure the stack is up before invoking `playwright test`.
 */
export default defineConfig({
  testDir: './e2e',
  timeout: 60_000,
  expect: { timeout: 10_000 },
  fullyParallel: false,
  retries: process.env.CI ? 1 : 0,
  workers: 1,
  reporter: [['list'], ['html', { outputFolder: 'playwright-report', open: 'never' }]],
  use: {
    baseURL: process.env.PLAYWRIGHT_BASE_URL ?? 'http://localhost:3000',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
    locale: 'ko-KR',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
});
