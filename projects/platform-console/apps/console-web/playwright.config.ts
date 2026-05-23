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
  },
  projects: [
    {
      name: 'chromium',
      use: {
        ...devices['Desktop Chrome'],
        // TASK-PC-FE-028 (iter 6) — CI: comma-separated host-resolver rules
        // with BOTH auth-service mapping AND explicit localhost mapping.
        // Iter 4 (port-agnostic) and iter 5 (port-specific narrow) both
        // failed with `ERR_NAME_NOT_RESOLVED at http://localhost:3000/...`
        // (URL #1 — never previously failed in iter 1-3 where the flag was
        // not applied due to duplicated global+project launchOptions).
        // Theory: enabling `--host-resolver-rules` switches Chromium to its
        // built-in DNS resolver, which bypasses the runner's /etc/hosts
        // (where `localhost → 127.0.0.1` lives by default). Per Chromium
        // HostMappingRules syntax, multiple comma-separated rules add to
        // the rewrite table; explicit `MAP localhost 127.0.0.1` makes the
        // built-in resolver land localhost on the loopback IP without
        // depending on /etc/hosts. The auth-service port-specific rule
        // remains for the SAS hostname-port mapping. Full Chrome for
        // Testing channel retained.
        ...(process.env.CI ? {
          channel: 'chromium',
          launchOptions: {
            args: ['--host-resolver-rules=MAP auth-service:8081 127.0.0.1:8081, MAP localhost 127.0.0.1'],
          },
        } : {}),
      },
    },
  ],
});
