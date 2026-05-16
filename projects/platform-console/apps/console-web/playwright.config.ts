import { defineConfig, devices } from '@playwright/test';

/**
 * E2E config for console-web critical journeys (login → catalog → tenant
 * switch + multi-tenant isolation regression). CI is authoritative: the full
 * GAP OIDC + admin-service registry stack must be reachable at the
 * `*.local` hostnames. Locally these specs may be skipped if the stack is down
 * (see tests/e2e/*).
 */
export default defineConfig({
  testDir: './tests/e2e',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  reporter: process.env.CI ? 'github' : 'list',
  use: {
    baseURL: process.env.CONSOLE_BASE_URL ?? 'http://console.local',
    trace: 'on-first-retry',
  },
  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
  ],
});
