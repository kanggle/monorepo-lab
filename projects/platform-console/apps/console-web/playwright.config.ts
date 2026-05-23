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
        // TASK-PC-FE-028 (iter 5) — CI: port-SPECIFIC DNS mapping at the
        // Chromium resolver layer paired with docker-compose host-port 1:1
        // publishing (`8081:8081`). Iter 4 (port-agnostic
        // `MAP auth-service 127.0.0.1`) confirmed the flag IS now applied
        // (single project-level launchOptions, no global-use override) but
        // the port-agnostic form over-matched into localhost resolution —
        // dispatch run 26327356967 failed with
        // `ERR_NAME_NOT_RESOLVED at http://localhost:3000/api/auth/login`
        // (the FIRST URL, never reached in iter 1-3). Iter 5 narrows the
        // rule with explicit port specificity: `MAP <hostname>:<src_port>
        // <target>:<dst_port>` matches only `auth-service:8081` hostport,
        // leaving localhost lookups to the default resolver. Combined with
        // docker-compose `["8081:8081"]` (TASK-PC-FE-028 iter 4 change),
        // the target `127.0.0.1:8081` lands on the published host port.
        // Full Chrome for Testing channel retained.
        ...(process.env.CI ? {
          channel: 'chromium',
          launchOptions: {
            args: ['--host-resolver-rules=MAP auth-service:8081 127.0.0.1:8081'],
          },
        } : {}),
      },
    },
  ],
});
