import { chromium, type FullConfig } from '@playwright/test';
import { loginAsSuperAdmin } from './login';

/**
 * Playwright global setup — mints the SUPER_ADMIN session ONCE per CI run
 * and persists the resulting cookies to `storageState`. Every spec in the
 * suite then reuses the persisted state, avoiding 1 token-mint round trip
 * per spec (the cost of the mint is ~200 ms; the persistence keeps the
 * Playwright wall-clock budget healthy as the spec count grows).
 *
 * The `storageState` path is derived from `playwright.config.ts` —
 * `tests/e2e/fixtures/.storage-state.json` is gitignored.
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
