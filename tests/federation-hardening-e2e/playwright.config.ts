import { defineConfig, devices } from '@playwright/test';
import path from 'node:path';

/**
 * TASK-MONO-139 — Phase 8 Federation Hardening cross-product e2e suite config.
 * ADR-MONO-018 D1 (location: root tests/), D2 (harness: Playwright on PC-FE-019..031),
 * D3 (scope: 7 spec files — 5 golden-path + 2 composition).
 *
 * Mirrors projects/platform-console/apps/console-web/playwright.config.ts
 * with cross-product adjustments: testDir = ./specs, baseURL = CONSOLE_BASE_URL.
 *
 * CI trigger: .github/workflows/federation-hardening-e2e.yml (nightly cron
 * 0 19 * * * UTC + workflow_dispatch). No push-trigger (ADR-MONO-018 D1 explicit).
 *
 * trace: 'on' — per TASK-MONO-133 always-upload baseline. The globalSetup
 * OIDC PKCE trace is handled separately in fixtures/login.ts (CI-only
 * manual tracing, same PC-FE-027 pattern).
 */
const STORAGE_STATE = path.join(
  __dirname,
  'fixtures/.storage-state.json',
);

export default defineConfig({
  testDir: './specs',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  reporter: process.env.CI ? 'github' : 'list',
  // Run global-setup before any spec; mint SUPER_ADMIN tokens once + persist cookies.
  globalSetup: require.resolve('./fixtures/global-setup.ts'),
  use: {
    baseURL: process.env.CONSOLE_BASE_URL ?? 'http://localhost:3000',
    storageState: STORAGE_STATE,
    // TASK-MONO-133 pattern — trace 'on' in CI; on-first-retry locally.
    trace: process.env.CI ? 'on' : 'on-first-retry',
  },
  projects: [
    {
      name: 'chromium',
      use: {
        ...devices['Desktop Chrome'],
      },
    },
  ],
});
