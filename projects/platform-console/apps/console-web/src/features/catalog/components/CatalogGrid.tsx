'use client';

import { useTenantSwitch } from '@/features/tenant';
import type { RegistryProduct, ProductKey } from '@/shared/api/registry-types';
import { ServiceTile, type TileTone } from './ServiceTile';

/**
 * TASK-PC-FE-064/065/067 — the interactive catalog grid.
 *
 *  - ALWAYS renders the full product list (no tenant filter — the catalog is a
 *    discovery surface; TASK-PC-FE-065 dropped the PC-FE-064 filter).
 *  - Each product header (and each tenant row) shows the product's domain-health
 *    status dot (`healthByDomain`, keyed by `productKey`; absent ⇒ no dot —
 *    domain health is per-domain/global, so a tenant's dot == its product's).
 *  - Clicking a tenant inside a product card SETS it as the active tenant via
 *    the assume-tenant flow (`useTenantSwitch` → `/api/tenant`) and, once the
 *    switch lands, navigates to that product's domain-operations screen
 *    (`resolveConsoleRoute(product)`, passed up as `productRoute`) scoped to the
 *    selected tenant.
 */
export interface CatalogGridProps {
  products: RegistryProduct[];
  healthByDomain?: Partial<Record<ProductKey, TileTone>>;
}

export function CatalogGrid({ products, healthByDomain }: CatalogGridProps) {
  const switchTenant = useTenantSwitch();

  const onSelectTenant = (tenant: string, productRoute: string) => {
    switchTenant.mutate(tenant, {
      // TASK-PC-FE-067 — HARD navigation (full document load), NOT router.push.
      // The active tenant lives in an httpOnly cookie that ONLY the server
      // `(console)` layout reads to render the top TenantSwitcher. Next.js does
      // NOT re-render a shared layout on client-side navigation between routes
      // under it, and a prefetched/cached RSC payload for the destination was
      // rendered with the PREVIOUS tenant — so a SPA router.push lands on the
      // domain ops with a STALE top switcher (PC-FE-066's prop-sync useEffect
      // can't help: the stale prop never changes). A full load re-runs the
      // layout server component against the now-current cookie, so the switch is
      // reflected on the destination AND in the top switcher deterministically
      // (no Router-Cache / refresh-ordering race).
      onSuccess: () => {
        window.location.assign(productRoute);
      },
    });
  };

  return (
    <ul
      className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3"
      data-testid="catalog-grid"
    >
      {products.map((product) => (
        <ServiceTile
          key={product.productKey}
          product={product}
          tone={healthByDomain?.[product.productKey]}
          onSelectTenant={onSelectTenant}
        />
      ))}
    </ul>
  );
}
