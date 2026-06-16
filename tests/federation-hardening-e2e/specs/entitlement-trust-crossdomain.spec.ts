import { test, expect } from '@playwright/test';
import { loginAsAcmeOperator } from '../fixtures/login';
import { gotoOverview } from '../fixtures/console-helpers';

/**
 * TASK-MONO-154 — ADR-MONO-019 runtime activation capstone.
 *
 * Entitlement-trust cross-domain discriminator spec. Proves, on the full
 * federation-hardening-e2e stack (GAP + finance/wms/scm/erp + console-bff +
 * console-web), that a REAL customer `acme-corp` operator's token passes the
 * finance/wms domain gates (entitled) and is rejected by scm/erp (not
 * entitled) — i.e. entitlement-trust is a demonstrable runtime behaviour, not
 * just a unit-tested contract.
 *
 * The logic chain this capstone closes end-to-end:
 *   seed → reverse-lookup (BE-325): account_db `acme-corp` → [finance,wms]
 *          subscriptions (GAP Flyway V0019/V0020).
 *   issue → claim (BE-324 keystone): the OIDC token minted for the acme-corp
 *          operator carries tenant_id='acme-corp' + entitled_domains=
 *          [finance,wms].
 *   gate accept/reject (step 3): each domain's TenantClaimValidator
 *          dual-accepts (legacy slug OR entitled_domains). For acme-corp:
 *          finance/wms ACCEPT (in entitled set), scm/erp REJECT (acme-corp is
 *          neither their slug nor in their entitled set).
 *   BFF pass-through (PC-BE-007): console-bff forwards the token unchanged on
 *          the per-domain fan-out, so the per-card status reflects the REAL
 *          gate decision.
 *
 * This un-defers the card-status assertion that operator-overview-composition
 * .spec.ts left MVP-relaxed (heading-only) — here the entitled vs non-entitled
 * per-card `forbidden` difference is the discriminator, asserted via the
 * Operator Overview composition route's per-card DOM (DomainCard.tsx:
 * data-testid="operator-overview-card-{domain}" + data-status + the inner
 * "operator-overview-card-{domain}-forbidden" placeholder).
 *
 * Assertion strategy = Option 1 (overview card statuses). The /dashboards/
 * overview route renders the operator-overview feature's OperatorOverviewScreen
 * → 5 DomainCards, each with a stable `data-testid` + `data-status` attribute
 * and an explicit "{domain}-forbidden" inner element on the forbidden branch.
 * These selectors are reliable (unlike the MVP-relaxed per-domain golden-path
 * pages which only assert URL/title/heading). The negative (scm/erp forbidden)
 * is asserted strongly (data-status="forbidden" + the forbidden placeholder);
 * the positive (finance/wms) is asserted as NOT-forbidden (data-status is not
 * "forbidden" — ok with live data, or at minimum degraded, but never the gate
 * rejection).
 *
 * Session isolation: this spec deliberately does NOT reuse the global
 * SUPER_ADMIN storageState (tenant_id='*' is accepted by all producers and
 * would mask the entitlement gate). It overrides storageState to empty and
 * drives a fresh OIDC PKCE login as the acme-corp operator.
 *
 * Verification channel: federation-hardening-e2e is nightly + workflow_dispatch
 * (NOT PR-triggered) — verified post-merge via
 * `gh workflow run federation-hardening-e2e.yml` (AC-5).
 */

// Override the suite-wide SUPER_ADMIN storageState — the acme-corp operator
// must log in fresh so its tenant_id='acme-corp' + entitled_domains claim
// drives the gate (the '*' wildcard would defeat the discriminator).
test.use({ storageState: { cookies: [], origins: [] } });

const ENTITLED_DOMAINS = ['finance', 'wms'] as const;
const NON_ENTITLED_DOMAINS = ['scm', 'erp'] as const;

test.describe('Entitlement-trust cross-domain (acme-corp: finance/wms entitled, scm/erp denied)', () => {
  test('non-entitled domains (scm, erp) are gate-rejected; entitled domains (finance, wms) are not', async ({
    browser,
  }) => {
    // Fresh context → real OIDC PKCE login as the acme-corp operator →
    // console_active_tenant='acme-corp'. Production-identical token path.
    const context = await browser.newContext({
      storageState: { cookies: [], origins: [] },
    });
    try {
      await loginAsAcmeOperator(context);
      const page = await context.newPage();
      await gotoOverview(page);

      // ── CORE DISCRIMINATOR (negative side — asserted strongly) ──────────
      // scm + erp are NOT in acme-corp's entitled_domains; the step-3 gate
      // REJECTS the token, the BFF surfaces a `forbidden` card. Assert the
      // card carries data-status="forbidden" AND renders the forbidden
      // placeholder (DomainCard.tsx forbidden branch). The forbidden reason
      // is one of PERMISSION_DENIED / TENANT_FORBIDDEN — both are valid gate
      // rejections; we assert the forbidden STATE (not a specific reason
      // string) for robustness.
      for (const domain of NON_ENTITLED_DOMAINS) {
        const card = page.getByTestId(`operator-overview-card-${domain}`);
        await expect(
          card,
          `${domain} card should be present`,
        ).toBeVisible({ timeout: 20_000 });
        await expect(
          card,
          `${domain} is NOT entitled for acme-corp — gate must reject (forbidden)`,
        ).toHaveAttribute('data-status', 'forbidden');
        await expect(
          page.getByTestId(`operator-overview-card-${domain}-forbidden`),
          `${domain} forbidden placeholder must render`,
        ).toBeVisible();
      }

      // ── CORE DISCRIMINATOR (positive side — asserted NOT-forbidden) ─────
      // finance + wms ARE in acme-corp's entitled_domains; the gate ACCEPTS
      // the token. The card must NOT be `forbidden`. We assert NOT-forbidden
      // (the gate did not reject) rather than strictly `ok`, since a transient
      // producer hiccup could yield `degraded` without contradicting the
      // entitlement decision. finance has live acme-corp balance data seeded
      // (seed-domains.sql), so finance is expected `ok` in the steady state;
      // wms entitled-accept is proven by a non-forbidden card even if empty.
      for (const domain of ENTITLED_DOMAINS) {
        const card = page.getByTestId(`operator-overview-card-${domain}`);
        await expect(
          card,
          `${domain} card should be present`,
        ).toBeVisible({ timeout: 20_000 });
        await expect(
          card,
          `${domain} IS entitled for acme-corp — gate must NOT reject (not forbidden)`,
        ).not.toHaveAttribute('data-status', 'forbidden');
        // Belt-and-braces: the forbidden placeholder must be absent.
        await expect(
          page.getByTestId(`operator-overview-card-${domain}-forbidden`),
        ).toHaveCount(0);
      }
    } finally {
      await context.close();
    }
  });
});
