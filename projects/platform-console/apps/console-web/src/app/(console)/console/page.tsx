import { redirect } from 'next/navigation';
import { getCatalog, ServiceCatalog } from '@/features/catalog';
import type { TileTone } from '@/features/catalog';
import { getDomainHealthState, healthTone } from '@/features/domain-health';
import { getActiveTenant } from '@/shared/lib/session';
import type { ProductKey } from '@/shared/api/registry-types';
import { ApiError } from '@/shared/api/errors';

export const dynamic = 'force-dynamic';

/**
 * Authenticated catalog home (replaces the Phase-1 static placeholder).
 *
 * Server component — the data-driven catalog is fetched server-side from the
 * IAM registry with the HttpOnly operator token. Tiles render strictly from
 * the registry response (no hardcoded list); a registry timeout / 5xx yields
 * a degraded-but-usable catalog (task Acceptance). An auth failure forces a
 * clean re-login.
 *
 * TASK-PC-FE-064 — also composes the per-domain health (for the product-header
 * status dot) + the active tenant (for the tenant-filter initial scope). Both
 * are best-effort: a null/degraded health simply yields no dots, and no active
 * tenant means the grid starts unfiltered (the catalog never blanks on either).
 */
export default async function ConsoleHomePage() {
  let catalog;
  try {
    catalog = await getCatalog();
  } catch (err) {
    if (err instanceof ApiError && err.status === 401) redirect('/login');
    throw err;
  }

  const [healthState, activeTenant] = await Promise.all([
    getDomainHealthState(),
    getActiveTenant(),
  ]);

  // ProductKey ↔ domain-health domain key is 1:1 (iam/wms/scm/finance/erp).
  const healthByDomain: Partial<Record<ProductKey, TileTone>> = {};
  if (healthState.health) {
    for (const card of healthState.health.cards) {
      healthByDomain[card.domain as ProductKey] = healthTone(card);
    }
  }

  return (
    <ServiceCatalog
      catalog={catalog}
      healthByDomain={healthByDomain}
      activeTenant={activeTenant}
    />
  );
}
