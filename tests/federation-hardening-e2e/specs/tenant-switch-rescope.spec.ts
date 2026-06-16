import { test, expect, type Page } from '@playwright/test';
import { loginAsMultiOperator } from '../fixtures/login';
import { gotoOverview, switchTenant } from '../fixtures/console-helpers';

/**
 * TASK-MONO-158 — ADR-MONO-020 § 3.3 step 3 (D4) capstone.
 *
 * Active-tenant switcher → assume-tenant flow A↔B re-scope discriminator spec.
 * Proves, on the full federation-hardening-e2e stack (GAP + finance/wms/scm/erp
 * + console-bff + console-web), that switching a MULTI-ASSIGNMENT operator
 * between two customers with COMPLEMENTARY entitlements re-scopes the SIGNED
 * domain-facing token (`tenant_id` + `entitled_domains`), so the federated
 * domain entitlement gates FOLLOW the selection. This is the single defining
 * proof of D4: setting X-Tenant-Id alone does nothing — the switch MUST mint
 * the assumed token (the assume-tenant RFC 8693 exchange), which is what gates
 * the cards.
 *
 * The logic chain this capstone closes end-to-end:
 *   seed → assignment (BE-326): the operator has two D1
 *          operator_tenant_assignment rows (acme-corp + globex-corp), so the
 *          ConsoleRegistry effective scope surfaces both customers.
 *   switch → assume (BE-327 producer / MONO-158 console consumer): the real
 *          POST /api/tenant {tenant:T} drives the server-side assume-tenant
 *          exchange (subject = base GAP OIDC token, audience = T) → a token
 *          scoped to T (tenant_id=T + entitled_domains=T's ACTIVE subs).
 *   gate accept/reject (ADR-019 D5): each domain's TenantClaimValidator
 *          dual-accepts (slug OR entitled_domains). For acme-corp:
 *          finance/wms ACCEPT, scm/erp REJECT. For globex-corp: the INVERSE
 *          (scm/erp ACCEPT, finance/wms REJECT).
 *   BFF pass-through (ADR-017 D6 / PC-BE-007): console-bff forwards the assumed
 *          token unchanged on the per-domain fan-out, so the per-card status
 *          reflects the REAL gate decision for the selected customer.
 *
 * acme-corp = [finance,wms] (GAP Flyway V0019/V0020); globex-corp = [scm,erp]
 * (e2e fixture seed.sql sections 9–13). The COMPLEMENTARY (disjoint) sets make
 * the A↔B flip unambiguous: the SAME operator sees finance/wms when on
 * acme-corp and scm/erp when on globex-corp.
 *
 * Assertion strategy = Option 1 (overview card statuses), identical to the
 * MONO-154 entitlement-trust spec: DomainCard.tsx `data-testid=
 * "operator-overview-card-{domain}"` + `data-status` + the inner
 * "operator-overview-card-{domain}-forbidden" placeholder. The negative side
 * (forbidden) is asserted strongly (data-status="forbidden" + placeholder);
 * the positive side (entitled) is asserted NOT-forbidden (the gate did not
 * reject — ok with live data or at minimum degraded, but never the rejection).
 *
 * Session isolation: empty storageState + a fresh OIDC PKCE login as the
 * multi-operator; the active-tenant cookie is NOT pre-set (the switch sets it
 * via the real route + mints the assumed token).
 *
 * Verification channel: federation-hardening-e2e is nightly + workflow_dispatch
 * (NOT PR-triggered) — verified post-merge via
 * `gh workflow run federation-hardening-e2e.yml` (AC-5).
 */

// Override the suite-wide SUPER_ADMIN storageState — the multi-operator must
// log in fresh (the '*' wildcard would be accepted by all producers and defeat
// the discriminator), and start with NO active-tenant selection.
test.use({ storageState: { cookies: [], origins: [] } });

const ACME = 'acme-corp';
const GLOBEX = 'globex-corp';

/** Assert the entitled domains are NOT forbidden and the non-entitled ones ARE
 *  forbidden (the per-customer gate decision). */
async function assertEntitlement(
  page: Page,
  entitled: readonly string[],
  nonEntitled: readonly string[],
): Promise<void> {
  // Non-entitled → gate REJECTS → forbidden card + placeholder (strong).
  for (const domain of nonEntitled) {
    const card = page.getByTestId(`operator-overview-card-${domain}`);
    await expect(
      card,
      `${domain} card should be present`,
    ).toBeVisible({ timeout: 20_000 });
    await expect(
      card,
      `${domain} is NOT entitled for the selected customer — gate must reject (forbidden)`,
    ).toHaveAttribute('data-status', 'forbidden');
    await expect(
      page.getByTestId(`operator-overview-card-${domain}-forbidden`),
      `${domain} forbidden placeholder must render`,
    ).toBeVisible();
  }

  // Entitled → gate ACCEPTS → NOT forbidden (ok/degraded, never rejection).
  for (const domain of entitled) {
    const card = page.getByTestId(`operator-overview-card-${domain}`);
    await expect(
      card,
      `${domain} card should be present`,
    ).toBeVisible({ timeout: 20_000 });
    await expect(
      card,
      `${domain} IS entitled for the selected customer — gate must NOT reject (not forbidden)`,
    ).not.toHaveAttribute('data-status', 'forbidden');
    await expect(
      page.getByTestId(`operator-overview-card-${domain}-forbidden`),
    ).toHaveCount(0);
  }
}

test.describe('Active-tenant switch re-scope (A↔B: acme-corp [finance,wms] ↔ globex-corp [scm,erp])', () => {
  test('switching customer flips which domain cards are entitled (assumed token re-scopes the signed claims)', async ({
    browser,
  }) => {
    const context = await browser.newContext({
      storageState: { cookies: [], origins: [] },
    });
    try {
      // Fresh OIDC PKCE login as the multi-assignment operator (NO pre-set
      // active tenant — the switch drives it).
      await loginAsMultiOperator(context);
      const page = await context.newPage();

      // ── A: switch to acme-corp → finance/wms entitled, scm/erp forbidden ──
      await switchTenant(context, ACME);
      await gotoOverview(page);
      await assertEntitlement(page, ['finance', 'wms'], ['scm', 'erp']);

      // ── B: switch to globex-corp → the INVERSE (the discriminator) ────────
      // The SAME operator, SAME session — only the assumed token changed. If
      // the switch merely set X-Tenant-Id (no re-scope), the cards would NOT
      // flip; the flip proves the assumed token re-scoped the SIGNED claims.
      await switchTenant(context, GLOBEX);
      await gotoOverview(page);
      await assertEntitlement(page, ['scm', 'erp'], ['finance', 'wms']);

      // TASK-MONO-173 — scm leg producer-health gate. The generic
      // assertEntitlement tolerates 'degraded' on the entitled side (line ~44:
      // "ok with live data OR at minimum degraded"), which let the
      // MONO-171 / SCM-BE-021 regression slip past: a producer-side
      // inventory-visibility /snapshot 422 on the globex assumed-tenant
      // degraded the scm card but did NOT make it forbidden, so this gate
      // stayed GREEN. Tighten scm specifically to data-status='ok' so a
      // producer-side scm error turns this gate RED (this '*'-vs-globex path is
      // the ONLY one that hits the seeded inventory-visibility rows).
      const scmCard = page.getByTestId('operator-overview-card-scm');
      await expect(
        scmCard,
        'scm leg must be HEALTHY (ok) on globex, not merely not-forbidden — MONO-171/SCM-BE-021 producer-side snapshot-422 regression gate',
      ).toHaveAttribute('data-status', 'ok');
      await expect(
        page.getByTestId('operator-overview-card-scm-degraded'),
        'scm degraded placeholder must NOT render on globex (producer leg healthy)',
      ).toHaveCount(0);
    } finally {
      await context.close();
    }
  });
});
