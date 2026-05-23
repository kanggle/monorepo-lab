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
  reporter: process.env.CI ? 'github' : 'list',
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
    // TASK-PC-FE-028 — CI: map docker-internal OIDC issuer hostname to the
    // host-published port at the Chromium DNS resolver layer. Required because
    // `context.route` bridges (PC-FE-022) intercept resource subrequests but
    // NOT top-level navigation DNS resolution (PC-FE-027 trace evidence:
    // Network panel captured `http://auth-service:8081/oauth2/authorize?...`
    // as the second-and-final URL — browser DNS resolve failed BEFORE the
    // route handler was invoked). DNS-layer fix for DNS-layer cause. Dev runs
    // use the traefik path (console.local) and don't need this.
    ...(process.env.CI ? {
      launchOptions: {
        args: ['--host-resolver-rules=MAP auth-service:8081 127.0.0.1:18081'],
      },
    } : {}),
  },
  projects: [
    {
      name: 'chromium',
      use: {
        ...devices['Desktop Chrome'],
        // TASK-PC-FE-028 — explicit project-level repeat so the global
        // `use.launchOptions` is not lost during Playwright's project-level
        // merge (devices['Desktop Chrome'] spread overrides launchOptions
        // when the spread's resulting object omits the key; cf. dispatch
        // run 26325955313 showed global-only setting had no effect).
        ...(process.env.CI ? {
          launchOptions: {
            args: ['--host-resolver-rules=MAP auth-service:8081 127.0.0.1:18081'],
          },
        } : {}),
      },
    },
  ],
});
