import { chromium, type FullConfig } from '@playwright/test';
import { loginAsSuperAdmin } from './login';

/**
 * TASK-MONO-139 — Federation Hardening e2e global setup.
 *
 * Mints the SUPER_ADMIN session ONCE per CI run and persists the resulting
 * cookies to storageState. Every spec in the suite then reuses the persisted
 * state, avoiding repeated OIDC round trips.
 *
 * Mirrors projects/platform-console/apps/console-web/tests/e2e/fixtures/
 * global-setup.ts (PC-FE-022 pattern) — byte-identical aside from the
 * import path adjustment for the root-scoped harness.
 *
 * ADR-MONO-018 D2 — Playwright extended on PC-FE-019..031 harness; same
 * globalSetup + storageState pattern.
 */
export default async function globalSetup(config: FullConfig) {
  const project = config.projects[0];
  const storageStatePath =
    typeof project.use?.storageState === 'string'
      ? project.use.storageState
      : null;
  if (!storageStatePath) {
    throw new Error(
      'global-setup expected playwright.config.ts to declare a string storageState path',
    );
  }

  const browser = await chromium.launch();
  const context = await browser.newContext();
  await loginAsSuperAdmin(context);
  await context.storageState({ path: storageStatePath });
  await browser.close();
}
