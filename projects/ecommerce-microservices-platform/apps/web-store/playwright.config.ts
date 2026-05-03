import { defineConfig, devices } from '@playwright/test';

/**
 * Playwright configuration for web-store full-stack E2E tests.
 *
 * Local usage: start the full stack with `docker compose up -d` from the
 * ecommerce-microservices-platform root (PORT_PREFIX defaults to 1, so
 * web-store is at localhost:13000 and gateway at localhost:18080).
 * Then run `pnpm e2e` or `pnpm --filter web-store run e2e`.
 *
 * CI usage (frontend-e2e job, kanggle/monorepo-lab only): docker compose
 * boots the backend stack without the observability services; Playwright's
 * webServer starts web-store via `pnpm start` and points it at the gateway
 * on localhost:18080 (PORT_PREFIX=1 default).
 */

// PORT_PREFIX defaults to "1" in docker-compose.yml; gateway host port is 18080.
const GATEWAY_URL = `http://localhost:${process.env.GATEWAY_PORT ?? '18080'}`;

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
  // In CI: docker compose starts the backend stack; Playwright starts web-store.
  // Locally: `docker compose up` starts everything including web-store; no webServer needed.
  webServer: process.env.CI
    ? {
        command: 'pnpm start --port 3000',
        port: 3000,
        timeout: 120_000,
        reuseExistingServer: false,
        env: {
          API_URL_INTERNAL: GATEWAY_URL,
          NEXT_PUBLIC_API_URL: GATEWAY_URL,
          // NextAuth v5 boot guard — without a `secret` even public pages
          // hit MissingSecret because middleware reads the JWT cookie.
          // Full-stack e2e currently runs with SKIP_GAP_E2E=1 (GAP container
          // not yet in the docker-compose), so the values below only need to
          // satisfy NextAuth's "config exists" check.
          NEXTAUTH_SECRET: process.env.NEXTAUTH_SECRET ?? 'fullstack-test-secret-not-real',
          OIDC_ISSUER_URL: process.env.OIDC_ISSUER_URL ?? 'http://127.0.0.1:1',
          ECOMMERCE_WEB_STORE_CLIENT_ID:
            process.env.ECOMMERCE_WEB_STORE_CLIENT_ID ?? 'ecommerce-web-store-client',
          ECOMMERCE_WEB_STORE_CLIENT_SECRET:
            process.env.ECOMMERCE_WEB_STORE_CLIENT_SECRET ?? 'ecommerce-dev',
          // Skip GAP-dependent specs by default — flip to '0' once the e2e
          // docker-compose adds the GAP container per TASK-MONO-014.
          SKIP_GAP_E2E: process.env.SKIP_GAP_E2E ?? '1',
        },
      }
    : undefined,
});
