import { test, expect } from '@playwright/test';

/**
 * TASK-MONO-139 — SCM golden-path spec.
 * ADR-MONO-018 D3 (MVP: 5 golden-path specs).
 * TASK-MONO-173 — hardened from MVP (URL + heading only) to assert the scm
 * leg actually composes: the PO list renders the seed row, NOT the degraded
 * panel. A producer-side error (the SCM-BE-020-class PO decimal parse failure,
 * which throws in listPurchaseOrders → getScmSectionState degrades the whole
 * section) now turns this gate RED instead of slipping past — the gap that let
 * the MONO-170 demo-surfaced drift wave reach main (the deferred "seed-row
 * rendering" follow-up named in the prior MVP comment).
 *
 * Steps: operator login → navigate /scm →
 * assert scm PO list renders (scm-po-table + seed PO-E2E-001) and NO error/
 * degraded panel.
 *
 * Per-domain credential rule (console-integration-contract.md § 2.4.6):
 * scm uses the GAP OIDC access token (getAccessToken()) — reuse of the
 * § 2.4.5 per-domain credential rule confirmed for scm. tenant_id='*'
 * accepted by scm TenantClaimValidator.
 *
 * The seeded PO (seed-scm.sql, tenant_id='*'): po_number=PO-E2E-001. The page
 * renders <ScmOpsScreen> only when PO **and** snapshot **and** staleness all
 * returned 200 (getScmSectionState Promise.all). For tenant_id='*' the snapshot
 * is 0 rows (the seeded inventory-visibility rows are globex-scoped) → still a
 * valid 200 → scm-snap-empty, non-degraded. So asserting scm-po-table proves
 * all three legs composed; PO-E2E-001 proves the PO leg returned the seed.
 *
 * NOTE: the MONO-171/SCM-BE-021 snapshot-422 class lives ONLY on the globex
 * assumed-tenant path (this '*' path has 0 snapshot rows and never hit the
 * malformed data) — that class is gated by tenant-switch-rescope.spec.ts
 * (scm card data-status='ok'), not here. This spec gates the PO-leg class.
 */
test.describe('SCM golden-path (purchase order list)', () => {
  test('navigates to SCM purchase orders page and renders seed PO row (not degraded)', async ({
    page,
  }) => {
    await page.goto('/scm');
    await page.waitForLoadState('networkidle');

    await expect(page).toHaveURL(/\/scm(\?|$)/);
    await expect(page).toHaveTitle(/.+/);

    // The scm leg must COMPOSE — no error/degraded panel. Any of these
    // rendering means a producer-side failure (PO/snapshot/staleness) degraded
    // the section; the MONO-170 drift wave is exactly this surface.
    await expect(
      page.getByTestId('scm-degraded'),
      'scm section must not be degraded (a PO/snapshot/staleness leg failed)',
    ).toHaveCount(0);
    await expect(page.getByTestId('scm-not-eligible')).toHaveCount(0);
    await expect(page.getByTestId('scm-forbidden')).toHaveCount(0);
    await expect(page.getByTestId('scm-ratelimited')).toHaveCount(0);
    await expect(page.getByTestId('scm-po-degraded')).toHaveCount(0);

    // The PO list renders the seed row — proves the procurement PO leg returned
    // 200 with parseable data (SCM-BE-020 decimal-string contract regression
    // gate: a parse failure would have degraded the section above).
    await expect(
      page.getByTestId('scm-po-table'),
      'scm PO table must render (PO leg composed)',
    ).toBeVisible({ timeout: 15_000 });
    await expect(
      page.getByText('PO-E2E-001'),
      'seed PO row PO-E2E-001 must be visible',
    ).toBeVisible({ timeout: 15_000 });
  });
});
