import type { BrowserContext, Page } from '@playwright/test';

import { loginAsOperator } from './login';

/**
 * TASK-PC-FE-113 — fixtures for the federation-stack-gated console specs.
 *
 * The two flows promoted from the throwaway `verify-*.mjs` demo scripts
 * (multi-operator → ecommerce sellers; omni-operator → 5-domain overview)
 * exercise DOMAIN backends (ecommerce/scm/wms/erp/finance + the omni-corp
 * 5-domain tenant) that the console-web CI e2e stack (`docker-compose.e2e.yml`
 * = IAM + finance-account + console-bff + console-web) does NOT contain. They
 * only run against the root `federation-hardening-e2e` demo stack
 * (`localhost:3000`, see project memory `env_console_demo_local_redeploy`).
 *
 * So these specs are **env-gated** (mirroring web-store's `shouldSkipGap`):
 * skipped unless `PC_FEDERATION_E2E=1`. In the console-web CI e2e run the flag
 * is unset → the specs SKIP (never fail on the absent backends); pointed at the
 * federation demo stack with the flag set, they RUN. The operator personas the
 * specs need are seeded by the committed `fixtures/seed-federation-personas.sql`
 * applied to that stack (AC-3) rather than typed by hand.
 */

/** Run the federation-stack specs only when explicitly pointed at that stack. */
export function shouldSkipFederation(): boolean {
  return process.env.PC_FEDERATION_E2E !== '1';
}

/** Fixed dev/test Argon2id plaintext — IAM V0014 dev seed, hardcoded test data. */
const PASSWORD = 'devpassword123!';

/**
 * Multi-assignment operator: holds operator_tenant_assignment rows that let the
 * active-tenant switcher reach `ecommerce` (derived roles=[ADMIN] per BE-376).
 */
export const MULTI_OPERATOR = {
  email: 'multi-operator@example.com',
  password: PASSWORD,
} as const;

/**
 * Omni operator: assigned to the all-in-one `omni-corp` tenant whose
 * subscription entitles all 5 domains (ecommerce/scm/wms/erp/finance), see
 * project memory `project_omni_all_domain_test_tenant`.
 */
export const OMNI_OPERATOR = {
  email: 'omni-operator@example.com',
  password: PASSWORD,
} as const;

/**
 * Fresh OIDC PKCE login as a federation operator with NO active-tenant primed —
 * the spec drives the tenant via {@link switchActiveTenant} so the assume-tenant
 * re-scope is part of what is asserted.
 */
export async function loginAsFederationOperator(
  context: BrowserContext,
  persona: { email: string; password: string },
): Promise<void> {
  await loginAsOperator(context, persona.email, persona.password, null);
}

/**
 * Drives the real active-tenant switch (`POST /api/tenant`), which performs the
 * server-side assume-tenant (RFC 8693) exchange and mints a token scoped to
 * `tenant`. Throws if the switch is rejected so a mis-seeded persona fails loud.
 *
 * Edge case (task § Edge Cases): two consecutive switches to the SAME tenant
 * reuse the cached assumed token (idempotent exchange). Each spec here switches
 * exactly once from a fresh login to a distinct tenant, so no away-and-back
 * dance is needed.
 */
export async function switchActiveTenant(
  page: Page,
  tenant: string,
): Promise<void> {
  const res = await page.request.post('/api/tenant', { data: { tenant } });
  if (!res.ok()) {
    throw new Error(
      `active-tenant switch to '${tenant}' failed: HTTP ${res.status()} ${await res.text()}`,
    );
  }
}
