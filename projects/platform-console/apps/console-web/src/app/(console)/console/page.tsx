import { redirect } from 'next/navigation';
import { getCatalog, ServiceCatalog } from '@/features/catalog';
import type { TileTone } from '@/features/catalog';
import { getDomainHealthState, healthTone } from '@/features/domain-health';
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
 * TASK-PC-FE-064/065 — also composes the per-domain health (for the product
 * header + per-tenant status dots). Best-effort: a null/degraded health simply
 * yields no dots (the catalog never blanks). The grid always shows the full
 * product list (no tenant filter); selecting a tenant navigates to that
 * product's domain operations (TASK-PC-FE-065).
 */
export default async function ConsoleHomePage() {
  let catalog;
  try {
    catalog = await getCatalog();
  } catch (err) {
    if (err instanceof ApiError && err.status === 401) redirect('/login');
    throw err;
  }

  const healthState = await getDomainHealthState();

  // ProductKey ↔ domain-health domain key is 1:1 (iam/wms/scm/finance/erp).
  const healthByDomain: Partial<Record<ProductKey, TileTone>> = {};
  if (healthState.health) {
    for (const card of healthState.health.cards) {
      healthByDomain[card.domain as ProductKey] = healthTone(card);
    }
  }

  return <ServiceCatalog catalog={catalog} healthByDomain={healthByDomain} />;
}
