import { defineConfig, devices } from '@playwright/test';
import path from 'node:path';

/**
 * E2E config for console-web critical journeys (operator profile self-set +
 * SUPER_ADMIN admin-on-behalf-of profile edit). CI is authoritative — see
 * `.github/workflows/nightly-e2e.yml` job `platform-console-e2e-fullstack`
 * for the docker-compose orchestration + Playwright invocation.
 *
 * baseURL — local docker-compose.e2e.yml publishes console-web on host port
 * 3000 (TASK-PC-FE-019). The dev `http://console.local/` Traefik path
 * remains the human dev convention (override via `CONSOLE_BASE_URL` env).
 *
 * Global setup — fixtures/global-setup.ts mints the SUPER_ADMIN session ONCE
 * and persists cookies to storageState (`fixtures/.storage-state.json`).
 * Every spec inherits the storageState; specs that need a different identity
 * override via `test.use({ storageState: ... })`.
 */
const STORAGE_STATE = path.join(
  __dirname,
  'tests/e2e/fixtures/.storage-state.json',
);

export default defineConfig({
  testDir: './tests/e2e',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  // TASK-PC-FE-341 — CI keeps the annotations AND writes a machine-readable report: the `json`
  // reporter carries every attempt's `startTime` / `duration` / `workerIndex` / `retry`, which the
  // `github` (dot) reporter's job log does not — that is how a later reader reconstructs which spec
  // ran on the other worker while a flaky one failed. Path: the nightly uploads `test-results/`
  // (artifact `platform-console-playwright-report-nightly`).
  // 🔴 `test-results/` is also Playwright's `outputDir` (cleaned at run START, not after), so a
  // report file written at run END survives.
  reporter: process.env.CI
    ? [['github'], ['json', { outputFile: 'test-results/e2e-results.json' }]]
    : 'list',
  // Run global-setup before any spec; mint tokens once + persist cookies.
  globalSetup: require.resolve('./tests/e2e/fixtures/global-setup.ts'),
  use: {
    baseURL: process.env.CONSOLE_BASE_URL ?? 'http://localhost:3000',
    storageState: STORAGE_STATE,
    // TASK-MONO-133 — CI: trace every test (including the globalSetup
    // virtual test wrapper, which `'on-first-retry'` cannot reach because
    // Playwright skips retry on globalSetup errors). Dev: keep retry-only
    // tracing for normal iteration speed.
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
