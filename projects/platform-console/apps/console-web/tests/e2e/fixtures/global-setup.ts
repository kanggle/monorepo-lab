import { chromium, type FullConfig } from '@playwright/test';
import { shouldSkipFederation } from './federation';
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

  // TASK-PC-FE-113 — in federation mode (PC_FEDERATION_E2E=1) the SUPER_ADMIN
  // (e2e-super-admin) is not provisioned on the federation demo stack and its
  // callback would fail with `operator_exchange_unavailable`. The
  // federation-gated specs log in fresh per-spec (overriding storageState), so
  // skip the SUPER_ADMIN mint and persist an empty state so the config's
  // storageState path still exists. Normal runs (flag unset) are unchanged.
  if (!shouldSkipFederation()) {
    await context.storageState({ path: storageStatePath });
    await browser.close();
    return;
  }

  await loginAsSuperAdmin(context);
  await context.storageState({ path: storageStatePath });
  await browser.close();
}
