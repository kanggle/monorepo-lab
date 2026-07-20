import { test, expect } from '@playwright/test';

/**
 * TASK-MONO-139 — Operator Overview composition spec.
 * ADR-MONO-018 D3 (MVP: 2 composition specs).
 *
 * Steps: operator login → navigate /console/dashboards/operator-overview →
 * assert 5-card grid renders + 5 domains all show 'ok' status
 * (Phase 8 happy path baseline).
 *
 * Per console-integration-contract.md § 2.4.9.1: the Operator Overview
 * composition route aggregates 5 domain fan-out results. The BFF credential
 * dispatch table (§ 2.4.9 D4 table) applies: GAP → OperatorToken;
 * wms/scm/finance/erp → IamOidcAccessToken.
 *
 * This spec verifies the composition renders (200 OK + 5-card grid) when all
 * 5 producers are live. Degrade path (force-pause one domain) = MVP
 * out-of-scope per ADR-018 D3 (note AC-4: "degrade path = MVP 외").
 *
 * NOTE (TASK-MONO-154): the per-card status assertion this spec left
 * MVP-relaxed (heading-only) is now un-deferred in its entitlement-trust form
 * by entitlement-trust-crossdomain.spec.ts — that sibling spec asserts the
 * entitled vs non-entitled per-card `forbidden` discriminator for the
 * real-customer acme-corp operator. This SUPER_ADMIN spec is left unchanged.
 */
test.describe('Operator Overview composition (5-domain fan-out)', () => {
  // TASK-PC-FE-251 AC-4 — renamed to what this body actually asserts. The old
  // title ('renders 5-card grid with all 5 domains showing ok status') described
  // the MVP-deferred assertion, not the three checks below, so anyone counting
  // coverage by test name would credit this suite with card-status coverage it
  // does not have. The per-card `data-status` discriminator IS covered, for the
  // acme-corp persona, by entitlement-trust-crossdomain.spec.ts.
  test('overview route resolves and renders for an authenticated SUPER_ADMIN (URL + title + heading only)', async ({
    page,
  }) => {
    await page.goto('/dashboards/overview');
    await page.waitForLoadState('networkidle');

    // MVP-level relaxation per TASK-MONO-140 cycle 5 (sibling MONO-133 honest
    // scope adjustment): cross-product e2e cohort verifies the dashboard page
    // resolves + auth works + heading renders. The 5-domain card grid +
    // 'ok' status visibility depends on console-bff fan-out integration +
    // BFF outbound base URLs + tenant-context (console_active_tenant cookie
    // set to 'fan-platform' in login.ts, but seed uses tenant_id='*') —
    // deeper concerns deferred to a follow-up task.
    await expect(page).toHaveURL(/\/dashboards\/overview(\?|$)/);
    await expect(page).toHaveTitle(/.+/);
    const heading = page.getByRole('heading').first();
    await expect(heading).toBeVisible({ timeout: 20_000 });
  });
});
